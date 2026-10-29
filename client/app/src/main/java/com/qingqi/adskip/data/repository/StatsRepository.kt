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
 * - 读接口使用逻辑计数，包含排队中和待重试增量，不受 IO 完成时机影响。
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

    private val lock = Any()
    private var totalCount = store.totalSkips()
    private val countsByPkg = mutableMapOf<String, Int>()
    private var pendingTotal = 0
    private val pendingByPkg = mutableMapOf<String, Int>()
    private var flushing = false

    private data class Batch(val total: Int, val byPkg: Map<String, Int>)

    private val flushRunnable = Runnable { flush() }

    /** 记录一次跳过，返回累计总数（含待落盘增量）。 */
    fun recordSkip(pkg: String, label: String): Int = synchronized(lock) {
        // 日志量小可同步写（日志不参与计数，见 StatsStore 契约）
        store.appendLog(pkg, label)

        // 计数先进内存
        countsByPkg[pkg] = (countsByPkg[pkg] ?: store.pkgSkips(pkg)) + 1
        totalCount++
        pendingTotal++
        pendingByPkg[pkg] = (pendingByPkg[pkg] ?: 0) + 1

        // 5 秒后落盘
        flushHandler.removeCallbacks(flushRunnable)
        flushHandler.postDelayed(flushRunnable, 5000)

        totalCount
    }

    /** 强制落盘（广播/退出时调用） */
    fun flush() {
        val batch = synchronized(lock) {
            if (flushing || pendingTotal == 0) return
            flushHandler.removeCallbacks(flushRunnable)
            flushing = true
            Batch(pendingTotal, pendingByPkg.toMap()).also {
                pendingTotal = 0
                pendingByPkg.clear()
            }
        }

        val action = Runnable {
            try {
                store.addSkips(batch.total, batch.byPkg)
            } catch (e: Exception) {
                restoreBatch(batch, e)
                return@Runnable
            }
            synchronized(lock) { flushing = false }
            // 本批写入期间产生的增量继续串行提交，不与前一批读改写交错。
            flush()
        }

        try {
            if (ioExecutor != null) ioExecutor.execute(action) else action.run()
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            restoreBatch(batch, e)
        }
    }

    private fun restoreBatch(batch: Batch, error: Exception) {
        synchronized(lock) {
            pendingTotal += batch.total
            for ((pkg, delta) in batch.byPkg) {
                pendingByPkg[pkg] = (pendingByPkg[pkg] ?: 0) + delta
            }
            flushing = false
            flushHandler.removeCallbacks(flushRunnable)
            flushHandler.postDelayed(flushRunnable, 5000)
        }
        LogRing.w("Stats", "flush retained for retry: ${error.message}")
    }

    fun total(): Int = synchronized(lock) { totalCount }

    fun lastApp(): String = store.lastApp()

    fun countFor(pkg: String): Int = synchronized(lock) {
        countsByPkg.getOrPut(pkg) { store.pkgSkips(pkg) }
    }

    fun logs(): List<LogEntry> = store.logs().map { LogEntry(it.first, it.second, it.third) }

    fun clearLogs() = store.clearLogs()

    /** 清空后的原样写回，仅供「清空后撤销」使用（见 [Prefs.restoreLogs]） */
    fun restoreLogs(entries: List<LogEntry>) = store.restoreLogs(
        entries.map { Triple(it.ts, it.pkg, it.label) },
    )
}
