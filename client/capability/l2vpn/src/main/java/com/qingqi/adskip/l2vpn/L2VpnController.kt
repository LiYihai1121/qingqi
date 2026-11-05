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

    private var state = L2VpnState.DISABLED
    private var networkRestoreRequired = true
    private var allowed = 0L
    private var blocked = 0L
    private var bypassed = 0L

    fun snapshot(): L2Snapshot = L2Snapshot(state, allowed, blocked, bypassed, networkRestoreRequired)

    fun isRunning(): Boolean = state == L2VpnState.RUNNING

    /** 用户必须先在系统中显式同意 VPN 授权；否则一律拒绝启动。 */
    fun requestStart(consentGranted: Boolean): Boolean {
        if (!consentGranted) return false
        state = L2VpnState.AWAITING_CONSENT
        networkRestoreRequired = true
        return true
    }

    /** 只有已通过授权流程才允许进入 RUNNING。 */
    fun onVpnEstablished(): Boolean {
        if (state != L2VpnState.AWAITING_CONSENT) return false
        state = L2VpnState.RUNNING
        networkRestoreRequired = false
        return true
    }

    /** 用户停止 / 服务销毁 / 一键暂停：返回 true 表示调用方必须关闭 tun 描述符。 */
    fun stop(): Boolean {
        state = L2VpnState.STOPPED
        networkRestoreRequired = true
        return true
    }

    /** 异常退出与主动停止走同一条恢复路径。 */
    fun onUnexpectedExit(): Boolean = stop()

    fun record(outcome: DnsFilterOutcome) {
        when (outcome.decision) {
            DnsDecision.ALLOW -> allowed++
            DnsDecision.BLOCK -> blocked++
            DnsDecision.BYPASS -> bypassed++
        }
    }

    fun resetCounters() {
        allowed = 0L
        blocked = 0L
        bypassed = 0L
    }

    companion object {
        const val DEFAULT_ENABLED = false
    }
}
