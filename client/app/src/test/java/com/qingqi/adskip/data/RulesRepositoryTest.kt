package com.qingqi.adskip.data

import com.qingqi.adskip.engine.RuleSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 规则落地不变式单测（纯 JVM，用假 [RulesStore] 替代加密存储）。
 *
 * 断言来自 §6.4 的架构评审结论：云端规则落地原先按
 * 关键词→viewIds→selectors→清旧包规则→逐包保存→禁用列表六次独立提交，
 * 进程在任两次之间死亡就留下「半新半旧」的混合规则（新关键词配旧禁用列表），
 * 且无法自愈（下一次同步以 hash 相同为由跳过）。真机上这条路径要等整轮
 * 同步才走到，所以必须在 JVM 下直接断言：
 * 1. 整体覆盖只允许 [RulesStore.replaceAllRules] 一次提交，且载荷完整；
 * 2. schemaVersion 不达标时存储零写入；
 * 3. 落地后立即读到新规则（缓存失效不得滞后）。
 */
class RulesRepositoryTest {

    /**
     * 内存版存储：与 [PrefsRulesStore] 同契约，并把「写入次数/读取次数」暴露出来供断言。
     *
     * [replaceAllRules] 按真实语义整体替换（先清 pkgRules 再放新集合），
     * 以此在 JVM 下复现「原子覆盖」的读侧效果。
     */
    private class FakeStore : RulesStore {
        var globalKeywords = mutableListOf<String>()
        var globalViewIds = mutableListOf<String>()
        var globalSelectors = mutableListOf<String>()
        var pkgRules = mutableMapOf<String, CloudPkgRule>()
        var disabled = mutableSetOf<String>()

        var replaceAllRulesCalls = 0
            private set
        var readCount = 0
            private set

        override fun globalKeywords(): List<String> {
            readCount++
            return globalKeywords.toList()
        }

        override fun globalViewIds(): List<String> {
            readCount++
            return globalViewIds.toList()
        }

        override fun globalSelectors(): List<String> {
            readCount++
            return globalSelectors.toList()
        }

        override fun pkgKeywords(pkg: String): List<String> {
            readCount++
            return pkgRules[pkg]?.keywords ?: emptyList()
        }

        override fun pkgViewIds(pkg: String): List<String> {
            readCount++
            return pkgRules[pkg]?.viewIds ?: emptyList()
        }

        override fun pkgSelectors(pkg: String): List<String> {
            readCount++
            return pkgRules[pkg]?.selectors ?: emptyList()
        }

        override fun isPackageDisabled(pkg: String): Boolean = pkg in disabled

        override fun saveKeywords(list: List<String>) {
            globalKeywords = list.toMutableList()
        }

        override fun setPackageDisabled(pkg: String, disabled: Boolean) {
            if (disabled) this.disabled.add(pkg) else this.disabled.remove(pkg)
        }

        override fun replaceAllRules(
            keywords: List<String>,
            viewIds: List<String>,
            selectors: List<String>,
            pkgRules: Map<String, CloudPkgRule>,
            disabled: List<String>,
        ) {
            replaceAllRulesCalls++
            globalKeywords = keywords.toMutableList()
            globalViewIds = viewIds.toMutableList()
            globalSelectors = selectors.toMutableList()
            this.pkgRules = pkgRules.toMutableMap()
            this.disabled = disabled.toMutableSet()
        }
    }

    private fun newRepo(store: RulesStore) = RulesRepository(store)

    @Test
    fun `cloud apply commits every channel in a single write`() {
        // §6.4 核心契约：一次 replaceAllRules 携带全部通道，杜绝半新半旧
        val store = FakeStore()
        val repo = newRepo(store)

        val ok = repo.applyCloudRules(
            keywords = listOf("跳过"),
            viewIds = listOf("skip_btn"),
            selectors = listOf("""[text="跳过"]"""),
            pkgRules = mapOf(
                "com.test.app" to CloudPkgRule(
                    keywords = listOf("广告"),
                    viewIds = listOf("ad_close"),
                    selectors = listOf("""[text="广告"]"""),
                ),
                "com.test.disabled" to CloudPkgRule(keywords = listOf("x"), disabled = true),
            ),
        )

        assertTrue(ok)
        assertEquals("云端落地必须单次原子提交", 1, store.replaceAllRulesCalls)
        // 读侧：全局 + 包规则全部生效
        val ruleSet = repo.ruleSetFor("com.test.app")
        assertEquals(listOf("跳过", "广告"), ruleSet.keywords)
        assertEquals(listOf("skip_btn", "ad_close"), ruleSet.viewIds)
        assertEquals(2, ruleSet.selectors.size)
        // disabled 来自云端 pkgRules 的 disabled 标记
        assertTrue(store.isPackageDisabled("com.test.disabled"))
        assertTrue(repo.ruleSetFor("com.test.disabled").disabled)
    }

    @Test
    fun `schema below minimum rejects without touching store`() {
        val store = FakeStore()
        store.globalKeywords = mutableListOf("旧词")
        val repo = newRepo(store)

        val ok = repo.applyCloudRules(
            keywords = listOf("新词"),
            viewIds = emptyList(),
            pkgRules = emptyMap(),
            schemaVersion = RuleSet.MIN_SCHEMA_VERSION - 1,
        )

        assertFalse(ok)
        assertEquals("拒载时存储零写入", 0, store.replaceAllRulesCalls)
        assertEquals(listOf("旧词"), repo.keywords())
    }

    @Test
    fun `cloud apply replaces stale pkg rules entirely`() {
        // 旧包规则键必须在同一次提交内清除：否则「旧包残留」会长期错误匹配
        val store = FakeStore()
        val repo = newRepo(store)

        repo.applyCloudRules(null, null, mapOf("com.old.app" to CloudPkgRule(keywords = listOf("旧"))))
        assertTrue(repo.ruleSetFor("com.old.app").keywords.contains("旧"))

        repo.applyCloudRules(null, null, mapOf("com.new.app" to CloudPkgRule(keywords = listOf("新"))))
        assertTrue(repo.ruleSetFor("com.new.app").keywords.contains("新"))
        assertTrue("旧包规则不得残留", repo.ruleSetFor("com.old.app").isEmpty)
    }

    @Test
    fun `ruleSetFor reflects new cloud rules immediately`() {
        // 缓存失效不得滞后：落地后同一次读就要拿到新规则，
        // 否则读侧呈现的正是「半新半旧」的镜像
        val store = FakeStore()
        val repo = newRepo(store)

        repo.applyCloudRules(listOf("旧词"), null, emptyMap())
        assertEquals(listOf("旧词"), repo.ruleSetFor("com.a").keywords)

        repo.applyCloudRules(listOf("新词"), null, emptyMap())
        assertEquals(listOf("新词"), repo.ruleSetFor("com.a").keywords)
    }

    @Test
    fun `null channels keep local values on cloud apply`() {
        val store = FakeStore()
        store.globalKeywords = mutableListOf("本地词")
        store.globalViewIds = mutableListOf("local_id")
        val repo = newRepo(store)

        val ok = repo.applyCloudRules(keywords = null, viewIds = null, pkgRules = emptyMap())

        assertTrue(ok)
        assertEquals("云端未下发的通道沿用本地值", listOf("本地词"), store.globalKeywords)
        assertEquals(listOf("local_id"), store.globalViewIds)
    }

    @Test
    fun `merge filters blank keywords short viewIds and unparsable selectors`() {
        val store = FakeStore()
        store.globalKeywords = mutableListOf("有效", " ", "")
        store.globalViewIds = mutableListOf("ab", "valid_id")
        store.globalSelectors = mutableListOf("""[text="有效"]""", "不是选择器{{{")
        val repo = newRepo(store)

        val ruleSet = repo.ruleSetFor("com.a")
        assertEquals(listOf("有效"), ruleSet.keywords)
        assertEquals(listOf("valid_id"), ruleSet.viewIds)
        assertEquals("不可解析的选择器应被丢弃", 1, ruleSet.selectors.size)
    }

    @Test
    fun `ruleSetFor caches per package and invalidates on edit`() {
        val store = FakeStore()
        store.globalKeywords = mutableListOf("词")
        val repo = newRepo(store)

        repo.ruleSetFor("com.a")
        val readsAfterFirst = store.readCount
        repo.ruleSetFor("com.a")
        assertEquals("同版本重复查询应命中缓存", readsAfterFirst, store.readCount)

        repo.saveKeywords(listOf("新词"))
        assertEquals(listOf("新词"), repo.ruleSetFor("com.a").keywords)

        repo.setDisabled("com.a", true)
        assertTrue(repo.ruleSetFor("com.a").disabled)
        assertTrue(repo.ruleSetFor("com.a").isEmpty)
    }
}
