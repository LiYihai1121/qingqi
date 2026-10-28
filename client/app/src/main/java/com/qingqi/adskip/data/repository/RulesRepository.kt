package com.qingqi.adskip.data.repository

import com.qingqi.adskip.data.local.CloudPkgRule
import com.qingqi.adskip.data.local.RulesStore
import com.qingqi.adskip.domain.engine.RuleSet
import com.qingqi.adskip.domain.engine.selector.SelectorParser

/**
 * 规则仓库：所有「规则」读写的唯一入口。
 *
 * v2.2 增强：
 * - 按 `(pkg → version)` 缓存合并结果，事件高频路径上只剩查表
 * - 仓库维护 `version` 计数，任何落地操作 `+1` 使缓存失效
 * - 云规则落地前校验 schemaVersion
 * - 云规则落地走 [RulesStore.replaceAllRules] 单次原子提交（§6.4）：
 *   此前按 关键词→viewIds→selectors→清旧包规则→逐包保存→禁用列表 六次独立
 *   提交，进程在任两次之间死亡就留下「半新半旧」混合规则且无法自愈
 *
 * 缓存用纯 Kotlin [LinkedHashMap]（accessOrder）而非 `android.util.LruCache`：
 * 领域层不依赖 android.jar，`invalidate()` 的语义（落地后立即读到新规则）
 * 才能在纯 JVM 单测里真实断言——isReturnDefaultValues 下的 LruCache 是空壳，
 * get 恒返回 null，缓存陈旧这类回归在单测里永远抓不到。
 */
class RulesRepository(private val store: RulesStore) {

    private var version = 0

    /** LRU 容量 64：覆盖「安装包数 × 版本」的实际并发查询量即可，内存敏感路径不贪大。 */
    private val cache = object : LinkedHashMap<String, RuleSet>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, RuleSet>?): Boolean = size > 64
    }

    /** 合并某应用生效的完整规则集；被禁用时返回空规则。 */
    fun ruleSetFor(pkg: String): RuleSet {
        if (store.isPackageDisabled(pkg)) {
            return RuleSet(emptyList(), emptyList(), disabled = true)
        }
        val cacheKey = "$pkg:$version"
        cache[cacheKey]?.let { return it }

        val keywords = (store.globalKeywords() + store.pkgKeywords(pkg))
            .filter { it.isNotBlank() }
        val viewIds = (store.globalViewIds() + store.pkgViewIds(pkg))
            .filter { it.length >= 3 }
        val selectors = (store.globalSelectors() + store.pkgSelectors(pkg))
            .filter { it.isNotBlank() }
            .mapNotNull { SelectorParser.parse(it) }
        val ruleSet = RuleSet(keywords, viewIds, selectors)
        cache[cacheKey] = ruleSet
        return ruleSet
    }

    // ---------- 全局关键词（主页编辑） ----------
    fun keywords(): MutableList<String> = store.globalKeywords().toMutableList()
    fun saveKeywords(list: List<String>) {
        store.saveKeywords(list)
        invalidate()
    }

    // ---------- 应用开关 ----------
    fun isDisabled(pkg: String): Boolean = store.isPackageDisabled(pkg)
    fun setDisabled(pkg: String, disabled: Boolean) {
        store.setPackageDisabled(pkg, disabled)
        invalidate()
    }

    // ---------- 云端规则落地（校验 schemaVersion，单次原子提交） ----------
    fun applyCloudRules(
        keywords: List<String>?,
        viewIds: List<String>?,
        pkgRules: Map<String, CloudPkgRule>,
        schemaVersion: Int = RuleSet.SCHEMA_VERSION,
        selectors: List<String>? = null,
    ): Boolean {
        // 校验 schemaVersion：低于客户端支持的版本拒载
        if (schemaVersion < RuleSet.MIN_SCHEMA_VERSION) {
            return false
        }
        // 云端未下发的通道沿用本地当前值，整体一次原子覆盖，不留半新半旧
        store.replaceAllRules(
            keywords = keywords ?: store.globalKeywords(),
            viewIds = viewIds ?: store.globalViewIds(),
            selectors = selectors ?: store.globalSelectors(),
            pkgRules = pkgRules,
            disabled = pkgRules.filterValues { it.disabled }.keys.toList(),
        )
        invalidate()
        return true
    }

    /** 使缓存失效 */
    private fun invalidate() {
        version++
        // LinkedHashMap 非线程安全，但本仓库的所有写入都在主线程（UI 编辑/同步回调），
        // 读路径 ruleSetFor 亦同线程；service 侧高频读取经 SafetyGuard 串行消费，无并发写。
        synchronized(cache) { cache.clear() }
    }
}
