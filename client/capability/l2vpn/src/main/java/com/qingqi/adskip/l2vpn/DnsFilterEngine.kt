package com.qingqi.adskip.l2vpn

/**
 * L2 的能力边界常量：本模块**只**做 DNS 层判定。
 *
 * 不解密 TLS、不安装用户 CA、不代理非 DNS 流量；DNS 只能按域名决策，
 * 无法区分同一域名下广告与正常业务（DESIGN-ADS-FILTERING.md §3）。
 */
const val DNS_ONLY = "dns-only"

enum class DnsDecision {
    ALLOW,
    BLOCK,
    BYPASS,
}

/** reason 只用于汇总日志定位，不含域名、不含用户查询明细。 */
data class DnsFilterOutcome(val decision: DnsDecision, val reason: String)

object DnsFilterEngine {

    /**
     * 任一前置条件不满足都返回 BYPASS（放行、不改写报文）：
     * 默认关闭、用户未授权、规则损坏、报文无法解析、非地址类查询。
     * 这是「崩溃与停止后网络恢复」在判定层的对应实现——失败必须 fail-open。
     */
    fun decide(
        dnsPayload: ByteArray,
        rules: FilterRuleSet?,
        enabled: Boolean,
        consentGranted: Boolean,
    ): DnsFilterOutcome {
        if (!enabled) return DnsFilterOutcome(DnsDecision.BYPASS, "disabled by default")
        if (!consentGranted) return DnsFilterOutcome(DnsDecision.BYPASS, "vpn consent not granted")
        if (rules == null) return DnsFilterOutcome(DnsDecision.BYPASS, "no valid rule set")
        val question = DnsQuestionParser.parse(dnsPayload)
            ?: return DnsFilterOutcome(DnsDecision.BYPASS, "unparsable query")
        if (question.qtype != DnsQuestionParser.TYPE_A && question.qtype != DnsQuestionParser.TYPE_AAAA) {
            return DnsFilterOutcome(DnsDecision.BYPASS, "non address query")
        }
        return when (rules.decide(question.qname)) {
            FilterDecision.BLOCK -> DnsFilterOutcome(DnsDecision.BLOCK, "matched rule")
            FilterDecision.ALLOW -> DnsFilterOutcome(DnsDecision.ALLOW, "not matched")
        }
    }
}
