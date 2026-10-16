package com.qingqi.adskip.data

import android.content.Context

/**
 * 跳过统计的持久化端口：**计数与日志的唯一落盘入口**。
 *
 * 抽成接口是为了让 [StatsRepository] 的累加语义能在纯 JVM 下单测——真机上
 * 跑不到的分支（合批落盘与读接口的交错）正是缺陷藏身处。背景是一次真机实测：
 * 单次跳过首页显示 2、5 秒后变 3，记录恒为 1；根因是计数被 [Prefs] 与
 * [StatsRepository] 各记一次，且落盘后不重置待落盘增量，计数还会无界膨胀。
 *
 * 因此本接口的契约是硬的：**计数只允许经 [addSkips] 写入**，[appendLog]
 * 不得顺带改计数。任何「顺手也加一」的实现都会让上面的缺陷复发。
 */
interface StatsStore {

    /** 已落盘的累计跳过数。 */
    fun totalSkips(): Int

    /** 已落盘的某应用跳过数。 */
    fun pkgSkips(pkg: String): Int

    /** 批量累加计数（单次提交），只由 [StatsRepository.flush] 调用。 */
    fun addSkips(totalDelta: Int, pkgDeltas: Map<String, Int>)

    /** 追加一条跳过日志，并更新「最近跳过应用」；**不得改动计数**。 */
    fun appendLog(pkg: String, label: String)

    /** 最近一次跳过的应用名。 */
    fun lastApp(): String

    /** 跳过日志（新在前）。 */
    fun logs(): List<Triple<Long, String, String>>

    fun clearLogs()

    /** 清空后原样写回，仅供「清空后撤销」使用。 */
    fun restoreLogs(entries: List<Triple<Long, String, String>>)
}

/** [StatsStore] 的生产实现：落在 [Prefs]（EncryptedSharedPreferences）上。 */
class PrefsStatsStore(private val context: Context) : StatsStore {

    override fun totalSkips(): Int = Prefs.getTotalSkips(context)

    override fun pkgSkips(pkg: String): Int = Prefs.getPkgSkipCount(context, pkg)

    override fun addSkips(totalDelta: Int, pkgDeltas: Map<String, Int>) =
        Prefs.addSkipCounts(context, totalDelta, pkgDeltas)

    override fun appendLog(pkg: String, label: String) = Prefs.appendSkipLog(context, pkg, label)

    override fun lastApp(): String = Prefs.getLastApp(context)

    override fun logs(): List<Triple<Long, String, String>> = Prefs.getLogs(context)

    override fun clearLogs() = Prefs.clearLogs(context)

    override fun restoreLogs(entries: List<Triple<Long, String, String>>) = Prefs.restoreLogs(context, entries)
}
