package com.qingqi.adskip.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ReportQueue 纯逻辑单测（内存 fake store，不碰 Android）。
 * 覆盖 A1 契约：FIFO、容量丢最旧、7 天过期、seq/epoch 分配、编码往返。
 */
class ReportQueueTest {

    private class FakeStore : ReportQueue.ReportQueueStore {
        var items = mutableListOf<ReportQueue.Item>()
        var seqCounter = 0L
        var epochValue = "epoch-test-01"
        override fun load(): List<ReportQueue.Item> = items.toList()
        override fun save(items: List<ReportQueue.Item>) {
            this.items = items.toMutableList()
        }
        override fun epoch(): String = epochValue
        override fun nextSeq(): Long {
            seqCounter++
            return seqCounter
        }
    }

    private fun newQueue(
        store: FakeStore = FakeStore(),
        maxSize: Int = ReportQueue.DEFAULT_MAX_SIZE,
        maxAgeMs: Long = ReportQueue.DEFAULT_MAX_AGE_MS,
        now: () -> Long = System::currentTimeMillis,
    ) = ReportQueue(store, maxSize, maxAgeMs, now = now) to store

    @Test
    fun `enqueue assigns monotonic seq and persists FIFO order`() {
        val (queue, store) = newQueue()
        queue.enqueue("com.a.app", "A", "text", 1000L)
        queue.enqueue("com.b.app", "B", "text", 2000L)
        queue.enqueue("com.c.app", "C", "text", 3000L)

        assertEquals(3, queue.size())
        val batch = queue.peekBatch()
        assertEquals(listOf(1L, 2L, 3L), batch.map { it.seq })
        assertEquals("com.a.app", batch[0].pkg)
        // peek 不出队
        assertEquals(3, queue.size())
        // 持久化内容与内存一致
        assertEquals(3, store.items.size)
    }

    @Test
    fun `removeFirst dequeues oldest items only`() {
        val (queue, _) = newQueue()
        repeat(5) { queue.enqueue("com.app$it", "L$it", "text", it.toLong()) }
        queue.removeFirst(2)
        val batch = queue.peekBatch()
        assertEquals(listOf(3L, 4L, 5L), batch.map { it.seq })
        assertEquals(3, queue.size())
    }

    @Test
    fun `queue drops oldest when capacity exceeded`() {
        val (queue, store) = newQueue(maxSize = 3)
        queue.enqueue("com.a", "A", "text", 1L)
        queue.enqueue("com.b", "B", "text", 2L)
        queue.enqueue("com.c", "C", "text", 3L)
        queue.enqueue("com.d", "D", "text", 4L)

        assertEquals(3, queue.size())
        val batch = queue.peekBatch()
        // 最旧的 com.a 被丢
        assertEquals(listOf("com.b", "com.c", "com.d"), batch.map { it.pkg })
        assertEquals(3, store.items.size)
    }

    @Test
    fun `expired items are evicted on peek and enqueue`() {
        var now = 1_000_000L
        val (queue, store) = newQueue(maxAgeMs = 1000L, now = { now })
        queue.enqueue("com.old", "Old", "text", 1L)
        now += 2000L // 超过保留期
        queue.enqueue("com.new", "New", "text", 2L)

        val batch = queue.peekBatch()
        assertEquals(listOf("com.new"), batch.map { it.pkg })
        assertEquals(1, store.items.size)
    }

    @Test
    fun `epoch is stable and seq survives reload`() {
        val store = FakeStore()
        val q1 = ReportQueue(store)
        q1.enqueue("com.a", "A", "text", 1L)
        assertEquals("epoch-test-01", q1.epoch())

        // 模拟进程重启：新实例从 store 恢复
        val q2 = ReportQueue(store)
        assertEquals(1, q2.size())
        q2.enqueue("com.b", "B", "text", 2L)
        assertEquals(listOf(1L, 2L), q2.peekBatch().map { it.seq })
    }

    @Test
    fun `encode decode roundtrip preserves all fields`() {
        val items = listOf(
            ReportQueue.Item(seq = 7L, pkg = "com.x", label = "X 广告", channel = "viewId", ts = 123L, enqueuedAt = 456L),
            ReportQueue.Item(seq = 8L, pkg = "com.y", label = "", channel = "selector", ts = 0L, enqueuedAt = 789L),
        )
        val decoded = ReportQueue.decode(ReportQueue.encode(items))
        assertEquals(items, decoded)
    }

    @Test
    fun `decode tolerates corrupt payload`() {
        assertTrue(ReportQueue.decode(null).isEmpty())
        assertTrue(ReportQueue.decode("").isEmpty())
        assertTrue(ReportQueue.decode("not json").isEmpty())
        assertTrue(ReportQueue.decode("[]").isEmpty())
    }

    @Test
    fun `decode drops malformed items but keeps valid ones`() {
        val raw = """
            [
              {"seq":1,"pkg":"com.good","label":"G","channel":"text","ts":1,"enq":1},
              {"seq":-1,"pkg":"com.bad","label":"B","channel":"text","ts":1,"enq":1},
              {"seq":2,"pkg":"","label":"B","channel":"text","ts":1,"enq":1}
            ]
        """.trimIndent()
        val decoded = ReportQueue.decode(raw)
        assertEquals(1, decoded.size)
        assertEquals("com.good", decoded[0].pkg)
    }

    @Test
    fun `peekBatch respects batch size limit`() {
        val (queue, _) = newQueue()
        repeat(60) { queue.enqueue("com.app$it", "L", "text", it.toLong()) }
        assertEquals(ReportQueue.DEFAULT_BATCH_SIZE, queue.peekBatch().size)
        assertEquals(60, queue.size()) // peek 不出队
    }

    @Test
    fun `clear empties queue and store`() {
        val (queue, store) = newQueue()
        queue.enqueue("com.a", "A", "text", 1L)
        queue.clear()
        assertEquals(0, queue.size())
        assertTrue(store.items.isEmpty())
    }
}
