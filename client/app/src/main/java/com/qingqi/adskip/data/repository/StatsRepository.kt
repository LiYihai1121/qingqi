package com.qingqi.adskip.data.repository

import android.os.Handler
import android.os.Looper
import com.qingqi.adskip.core.common.LogRing
import com.qingqi.adskip.data.local.Prefs
import com.qingqi.adskip.data.local.StatsStore
import java.util.concurrent.ExecutorService

/**
 * 统计仓库：跳过计数（全局/按应用）与跳过日志的唯一入口。
 *
 * v2.2 增强：
 * - 合批落盘：计数先进内存，[Handler.postDelayed](5s) 延迟落盘，
 *   广播/退出时强制 flush，消除「每跳一次同步写 SP」。
 * - 读接口返回已落盘值 + 内存待落盘增量，保证实时一致。
 *
 * **计数的唯一写入者**：累加在本类，落盘经 [StatsStore.addSkips]，
 * 存储层（[Prefs]）只负责日志与「最近跳过应用」。此前 [Prefs] 也写计数、
 * 且 [flush] 落盘后不重置待落盘增量，导致单次跳过被记两次并随 flush 次数
 * 无界膨胀（2026-10-11 真机实测：首页 2、5 秒后 3，记录恒为 1）。
 * 这两条不变式由 `StatsRepositoryTest` 在纯 JVM 下断言。
 */
class StatsRepository(private val store: StatsStore, private val ioExecutor: ExecutorService? = null) {

    data class LogEntry(val ts: Long, val pkg: String, val label: String)

    private val flushHandler = Handler(Looper.getMainLooper())

    @Volatile private var pendingTotal = 0

    @Volatile private var pendingByPkg = mutableMapOf<String, Int>()

    @Volatile private var dirty = false

    private val flushRunnable = Runnable { flush() }

    /** 记录一次跳过，返回累计总数（含待落盘增量）。 */
    fun recordSkip(pkg: String, label: String): Int {
        // 日志量小可同步写（日志不参与计数，见 StatsStore 契约）
        store.appendLog(pkg, label)

        // 计数先进内存
        pendingTotal++
        pendingByPkg[pkg] = (pendingByPkg[pkg] ?: 0) + 1
        dirty = true

        // 5 秒后落盘
        flushHandler.removeCallbacks(flushRunnable)
        flushHandler.postDelayed(flushRunnable, 5000)

        return total()
    }

    /** 强制落盘（广播/退出时调用） */
    fun flush() {
        if (!dirty) return
        flushHandler.removeCallbacks(flushRunnable)
        val totalDelta = pendingTotal
        val pkgDeltas = pendingByPkg.toMap()
        // 先清空待落盘增量，再交给 IO 线程写：否则落盘完成后增量仍在内存里，
        // total() 会把它与已落盘值再加一遍，且每轮 flush 都把总数翻一倍。
        pendingTotal = 0
        pendingByPkg = mutableMapOf()
        dirty = false

        val action = Runnable {
            // 一次批量提交所有增量
            try {
                store.addSkips(totalDelta, pkgDeltas)
            } catch (e: Exception) {
                LogRing.w("Stats", "flush failed: ${e.message}")
            }
        }

        if (ioExecutor != null) ioExecutor.execute(action) else action.run()
    }

    fun total(): Int = store.totalSkips() + pendingTotal

    fun lastApp(): String = store.lastApp()

    fun countFor(pkg: String): Int = store.pkgSkips(pkg) + (pendingByPkg[pkg] ?: 0)

    fun logs(): List<LogEntry> = store.logs().map { LogEntry(it.first, it.second, it.third) }

    fun clearLogs() = store.clearLogs()

    /** 清空后的原样写回，仅供「清空后撤销」使用（见 [Prefs.restoreLogs]） */
    fun restoreLogs(entries: List<LogEntry>) = store.restoreLogs(
        entries.map { Triple(it.ts, it.pkg, it.label) },
    )
}
