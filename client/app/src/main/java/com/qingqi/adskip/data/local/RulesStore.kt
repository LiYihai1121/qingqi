package com.qingqi.adskip.data.local

import android.content.Context

/**
 * 规则存储端口：**规则读写的唯一落盘入口**。
 *
 * 抽成接口是为了让 [RulesRepository] 的「云端落地」语义能在纯 JVM 下单测。
 * 背景是一次真机暴露的隐患：cloud 同步落地时按 关键词→viewIds→selectors→
 * 清旧包规则→逐包保存→禁用列表 六次独立提交，进程在任两次之间死亡就会留下
 * 「半新半旧」的混合规则（新关键词配旧禁用列表），且无法自愈——下一次同步
 * 以 hash 相同为由直接跳过。真机上这条路径要等整轮同步才走到，缺陷一旦
 * 发生就是长期的错误匹配。
 *
 * 因此本接口的契约是硬的：
 * - 规则的**整体覆盖只允许经 [replaceAllRules] 一次原子提交**；
 * - 不存在「逐键分多次提交」的第二条路径——任何「顺手分几次写」的实现
 *   都会让上述半新半旧状态复发。
 */
interface RulesStore {

    /** 全局关键词（云端下发 + 主页编辑合并后的当前值）。 */
    fun globalKeywords(): List<String>

    /** 全局 viewId 列表。 */
    fun globalViewIds(): List<String>

    /** 全局选择器表达式列表。 */
    fun globalSelectors(): List<String>

    /** 某应用的云端关键词。 */
    fun pkgKeywords(pkg: String): List<String>

    /** 某应用的云端 viewId。 */
    fun pkgViewIds(pkg: String): List<String>

    /** 某应用的云端选择器表达式。 */
    fun pkgSelectors(pkg: String): List<String>

    /** 某应用是否被用户/云端禁用（命中即整包放行）。 */
    fun isPackageDisabled(pkg: String): Boolean

    /** 主页编辑：整体替换全局关键词。 */
    fun saveKeywords(list: List<String>)

    /** 用户开关：切换某应用的禁用态。 */
    fun setPackageDisabled(pkg: String, disabled: Boolean)

    /**
     * 云端规则整体覆盖——**唯一的多键规则写入路径**（由 [RulesRepository.applyCloudRules] 调用）。
     *
     * 实现必须单次原子提交全部键（全局三类 + 所有包规则 + 禁用列表），
     * 旧包规则键在同一提交内清除，杜绝「旧包残留」与「半新半旧」。
     */
    fun replaceAllRules(
        keywords: List<String>,
        viewIds: List<String>,
        selectors: List<String>,
        pkgRules: Map<String, CloudPkgRule>,
        disabled: List<String>,
    )
}

/** 云端下发的单应用规则载荷。 */
data class CloudPkgRule(
    val keywords: List<String> = emptyList(),
    val viewIds: List<String> = emptyList(),
    val disabled: Boolean = false,
    val selectors: List<String> = emptyList(),
)

/** [RulesStore] 的生产实现：落在 [Prefs]（EncryptedSharedPreferences）上。 */
class PrefsRulesStore(private val context: Context) : RulesStore {

    override fun globalKeywords(): List<String> = Prefs.getKeywords(context)

    override fun globalViewIds(): List<String> = Prefs.getViewIds(context)

    override fun globalSelectors(): List<String> = Prefs.getGlobalSelectors(context)

    override fun pkgKeywords(pkg: String): List<String> = Prefs.getPkgKeywords(context, pkg)

    override fun pkgViewIds(pkg: String): List<String> = Prefs.getPkgViewIds(context, pkg)

    override fun pkgSelectors(pkg: String): List<String> = Prefs.getPkgSelectors(context, pkg)

    override fun isPackageDisabled(pkg: String): Boolean = Prefs.isPackageDisabled(context, pkg)

    override fun saveKeywords(list: List<String>) = Prefs.saveKeywords(context, list)

    override fun setPackageDisabled(pkg: String, disabled: Boolean) = Prefs.setPackageDisabled(context, pkg, disabled)

    override fun replaceAllRules(
        keywords: List<String>,
        viewIds: List<String>,
        selectors: List<String>,
        pkgRules: Map<String, CloudPkgRule>,
        disabled: List<String>,
    ) = Prefs.replaceAllRules(context, keywords, viewIds, selectors, pkgRules, disabled)
}
