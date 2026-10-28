package com.qingqi.adskip.data.repository

import com.qingqi.adskip.data.local.StatsStore
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 跳过计数不变式单测（纯 JVM，用假 [StatsStore] 替代加密存储）。
 *
 * 断言来自一次真机实测缺陷（2026-10-11）：单次跳过首页显示 2、5 秒后变 3，
 * 跳过记录却恒为 1。两个根因都在本类与存储层的分工上——
 * 1. 计数被 [Prefs] 与 [StatsRepository] 各记一次（双重累加）；
 * 2. [StatsRepository.flush] 落盘后不重置待落盘增量，每轮 flush 再翻一倍。
 * 真机上这两个分支要等 5 秒定时器才走到，所以必须在 JVM 下直接断言。
 */
class StatsRepositoryTest {

    /** 内存版存储：与 [PrefsStatsStore] 同契约，并把「写入次数」暴露出来供断言。 */
    private class FakeStore : StatsStore {
        var total = 0
            private set
        val byPkg = mutableMapOf<String, Int>()
        val logs = mutableListOf<Triple<Long, String, String>>()
        var addSkipsCalls = 0
            private set
        private var last = ""

        override fun totalSkips(): Int = total

        override fun pkgSkips(pkg: String): Int = byPkg[pkg] ?: 0

        override fun addSkips(totalDelta: Int, pkgDeltas: Map<String, Int>) {
            addSkipsCalls++
            total += totalDelta
            for ((pkg, delta) in pkgDeltas) byPkg[pkg] = (byPkg[pkg] ?: 0) + delta
        }

        override fun appendLog(pkg: String, label: String) {
            logs += Triple(0L, pkg, label)
            last = label
        }

        override fun lastApp(): String = last

        override fun logs(): List<Triple<Long, String, String>> = logs.toList()

        override fun clearLogs() {
            logs.clear()
        }

        override fun restoreLogs(entries: List<Triple<Long, String, String>>) {
            logs.clear()
            logs.addAll(entries)
        }
    }

    /** ioExecutor 传 null：落盘同步执行，断言不必等线程。 */
    private fun newRepo(store: StatsStore) = StatsRepository(store, ioExecutor = null)

    @Test
    fun `one skip counts exactly once`() {
        val store = FakeStore()
        val repo = newRepo(store)
        assertEquals(1, repo.recordSkip("com.a", "A"))
        assertEquals(1, repo.total())
    }

    @Test
    fun `total stays correct after flush`() {
        val store = FakeStore()
        val repo = newRepo(store)
        repeat(3) { repo.recordSkip("com.a", "A") }
        assertEquals(3, repo.total())

        repo.flush()
        assertEquals("落盘后不得重复计数", 3, repo.total())
        assertEquals(3, store.total)

        // 无增量时再 flush 一次，任何值都不应变化
        repo.flush()
        assertEquals(3, repo.total())
        assertEquals(3, store.total)
    }

    @Test
    fun `counts do not inflate across flush cycles`() {
        val store = FakeStore()
        val repo = newRepo(store)
        repeat(2) { repo.recordSkip("com.a", "A") }
        repo.flush()
        repeat(3) { repo.recordSkip("com.a", "A") }
        repo.flush()
        assertEquals(5, repo.total())
        assertEquals(5, store.total)
        assertEquals(5, repo.countFor("com.a"))
        assertEquals(5, store.pkgSkips("com.a"))
    }

    @Test
    fun `per package counts are independent`() {
        val store = FakeStore()
        val repo = newRepo(store)
        repo.recordSkip("com.a", "A")
        repo.recordSkip("com.b", "B")
        repo.recordSkip("com.a", "A")
        assertEquals(3, repo.total())
        assertEquals(2, repo.countFor("com.a"))
        assertEquals(1, repo.countFor("com.b"))
        assertEquals(0, repo.countFor("com.c"))
    }

    @Test
    fun `log append does not touch counters`() {
        val store = FakeStore()
        val repo = newRepo(store)
        repo.recordSkip("com.a", "A")
        assertEquals(1, store.logs.size)
        assertEquals("A", repo.lastApp())
        // 写日志不得顺带加计数：加计数的只有 flush 的批量提交
        assertEquals(0, store.total)
        assertEquals(0, store.addSkipsCalls)
    }

    @Test
    fun `pending increments are flushed in a single batch`() {
        val store = FakeStore()
        val repo = newRepo(store)
        repeat(4) { repo.recordSkip("com.a", "A") }
        assertEquals("未到落盘时机不应写存储", 0, store.addSkipsCalls)
        repo.flush()
        assertEquals("多次跳过应合并为一次落盘", 1, store.addSkipsCalls)
    }

    @Test
    fun `flush without pending increments is a no-op`() {
        val store = FakeStore()
        val repo = newRepo(store)
        repo.flush()
        assertEquals(0, store.addSkipsCalls)
    }
}
