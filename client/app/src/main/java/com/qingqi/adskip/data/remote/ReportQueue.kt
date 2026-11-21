package com.qingqi.adskip.data.remote

import org.json.JSONArray
import org.json.JSONObject

/**
 * 上报离线队列（PLAN-OFFLINE-SYNC §3.3 / DESIGN-SERVER-MOBILE-READINESS §5.1 A1）。
 *
 * 纯逻辑类：持久化经 [ReportQueueStore] 抽象（生产实现走 Prefs），
 * 单测用内存 fake——不碰 Android 框架。
 *
 * 语义要点：
 *  - seq 由 store.nextSeq() 分配（epoch 内单调），epoch 由 store 持有
 *    （安装期随机，重装后 seq 从 1 重来但 epoch 变了，服务端高水位不串）
 *  - 容量 500，满员丢最旧（enqueuedAt 最小者）
 *  - 保留期 7 天：flush 前剔除过期项；enqueue 时同样剔除
 *  - 出队只在服务端 2xx 或「永久失败」状态码后发生（分类在 SyncClient）
 */
class ReportQueue(
    private val store: ReportQueueStore,
    private val maxSize: Int = DEFAULT_MAX_SIZE,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** 队列条目：seq 是 epoch 内单调序号，enqueuedAt 用于丢最旧判定 */
    data class Item(
        val seq: Long,
        val pkg: String,
        val label: String,
        val channel: String,
        val ts: Long,
        val enqueuedAt: Long,
    )

    /** 持久化 + 身份分配抽象：生产由 Prefs 实现，测试用内存 fake */
    interface ReportQueueStore {
        fun load(): List<Item>
        fun save(items: List<Item>)

        /** 安装期随机 epoch（8–64 位 [A-Za-z0-9_-]），首次调用时创建并持久化 */
        fun epoch(): String

        /** epoch 内单调递增的 seq，调用即消耗（持久化） */
        fun nextSeq(): Long
    }

    private val lock = Any()
    private var items: MutableList<Item>? = null

    private fun loaded(): MutableList<Item> {
        val cached = items
        if (cached != null) return cached
        val loaded = store.load().toMutableList()
        items = loaded
        return loaded
    }

    /** 入队一条上报事件；满员丢最旧、顺带剔除过期项 */
    fun enqueue(pkg: String, label: String, channel: String, ts: Long) {
        synchronized(lock) {
            val list = loaded()
            evictExpired(list)
            while (list.size >= maxSize) {
                // 丢最旧：enqueuedAt 最小者（同队列内 seq 序与时间序一致，取首元素亦可，
                // 这里显式按 enqueuedAt 找，防御未来乱序写入）
                val oldestIdx = list.indices.minByOrNull { list[it].enqueuedAt } ?: break
                list.removeAt(oldestIdx)
            }
            list.add(
                Item(
                    seq = store.nextSeq(),
                    pkg = pkg,
                    label = label,
                    channel = channel,
                    ts = ts,
                    enqueuedAt = now(),
                ),
            )
            store.save(list)
        }
    }

    /** 取最旧的至多 [batchSize] 条（FIFO），不出队 */
    fun peekBatch(): List<Item> = synchronized(lock) {
        val list = loaded()
        evictExpired(list)
        store.save(list)
        list.take(batchSize)
    }

    /** 出队最旧的 [count] 条（仅在服务端确认处理后调用） */
    fun removeFirst(count: Int) {
        synchronized(lock) {
            val list = loaded()
            val n = count.coerceIn(0, list.size)
            repeat(n) { list.removeAt(0) }
            store.save(list)
        }
    }

    fun size(): Int = synchronized(lock) { loaded().size }

    fun epoch(): String = store.epoch()

    fun clear() {
        synchronized(lock) {
            loaded().clear()
            store.save(emptyList())
        }
    }

    private fun evictExpired(list: MutableList<Item>) {
        val cutoff = now() - maxAgeMs
        list.removeAll { it.enqueuedAt < cutoff }
    }

    companion object {
        const val DEFAULT_MAX_SIZE = 500
        const val DEFAULT_MAX_AGE_MS = 7L * 24 * 3600 * 1000
        const val DEFAULT_BATCH_SIZE = 50

        private const val KEY_SEQ = "seq"
        private const val KEY_PKG = "pkg"
        private const val KEY_LABEL = "label"
        private const val KEY_CHANNEL = "channel"
        private const val KEY_TS = "ts"
        private const val KEY_ENQUEUED_AT = "enq"

        fun encode(items: List<Item>): String {
            val arr = JSONArray()
            for (it in items) {
                arr.put(
                    JSONObject()
                        .put(KEY_SEQ, it.seq)
                        .put(KEY_PKG, it.pkg)
                        .put(KEY_LABEL, it.label)
                        .put(KEY_CHANNEL, it.channel)
                        .put(KEY_TS, it.ts)
                        .put(KEY_ENQUEUED_AT, it.enqueuedAt),
                )
            }
            return arr.toString()
        }

        fun decode(raw: String?): List<Item> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(raw)
                val out = ArrayList<Item>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(
                        Item(
                            seq = o.optLong(KEY_SEQ, -1),
                            pkg = o.optString(KEY_PKG, ""),
                            label = o.optString(KEY_LABEL, ""),
                            channel = o.optString(KEY_CHANNEL, "text"),
                            ts = o.optLong(KEY_TS, 0),
                            enqueuedAt = o.optLong(KEY_ENQUEUED_AT, 0),
                        ),
                    )
                }
                out.filter { it.seq >= 0 && it.pkg.isNotEmpty() }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
