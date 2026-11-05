package com.qingqi.adskip.l2vpn

enum class L2VpnState {
    DISABLED,
    AWAITING_CONSENT,
    RUNNING,
    STOPPED,
}

/** 状态快照：只含汇总计数，绝不携带 DNS 查询历史。 */
data class L2Snapshot(
    val state: L2VpnState,
    val allowed: Long,
    val blocked: Long,
    val bypassed: Long,
    val networkRestoreRequired: Boolean,
)

/**
 * L2 的启停状态机（纯 JVM，便于无真机验证）。
 *
 * 契约（DESIGN-ADS-FILTERING.md §3 / §7）：
 * - 默认关闭，用户未确认 VPN 授权时 start 必须拒绝；
 * - 停止、崩溃、升级后必须释放 tun 并让系统网络恢复；
 * - 只保留汇总计数，不上传、不保存查询明细。
 */
class L2VpnController {

    // 本对象被两类线程同时访问：TUN 工作线程（isRunning / record）与主线程
    // （requestStart / stop / snapshot）。不加同步会让计数丢失、快照撕裂，
    // 甚至出现「服务已 stop 但 pump 仍认为在 RUNNING」的竞态。Java 监视器
    // 可重入，onUnexpectedExit() 调用 stop() 不会自锁。
    private var state = L2VpnState.DISABLED
    private var networkRestoreRequired = true
    private var allowed = 0L
    private var blocked = 0L
    private var bypassed = 0L

    /** 单次调用内取到一致快照，不会读到「状态已变、计数未变」的中间态。 */
    @Synchronized
    fun snapshot(): L2Snapshot = L2Snapshot(state, allowed, blocked, bypassed, networkRestoreRequired)

    @Synchronized
    fun isRunning(): Boolean = state == L2VpnState.RUNNING

    /** 用户必须先在系统中显式同意 VPN 授权；否则一律拒绝启动。 */
    @Synchronized
    fun requestStart(consentGranted: Boolean): Boolean {
        if (!consentGranted) return false
        state = L2VpnState.AWAITING_CONSENT
        networkRestoreRequired = true
        return true
    }

    /** 只有已通过授权流程才允许进入 RUNNING。 */
    @Synchronized
    fun onVpnEstablished(): Boolean {
        if (state != L2VpnState.AWAITING_CONSENT) return false
        state = L2VpnState.RUNNING
        networkRestoreRequired = false
        return true
    }

    /** 用户停止 / 服务销毁 / 一键暂停：返回 true 表示调用方必须关闭 tun 描述符。 */
    @Synchronized
    fun stop(): Boolean {
        state = L2VpnState.STOPPED
        networkRestoreRequired = true
        return true
    }

    /** 异常退出与主动停止走同一条恢复路径。 */
    @Synchronized
    fun onUnexpectedExit(): Boolean = stop()

    @Synchronized
    fun record(outcome: DnsFilterOutcome) {
        when (outcome.decision) {
            DnsDecision.ALLOW -> allowed++
            DnsDecision.BLOCK -> blocked++
            DnsDecision.BYPASS -> bypassed++
        }
    }

    @Synchronized
    fun resetCounters() {
        allowed = 0L
        blocked = 0L
        bypassed = 0L
    }

    companion object {
        const val DEFAULT_ENABLED = false
    }
}
