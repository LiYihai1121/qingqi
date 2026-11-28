package com.qingqi.adskip.l2vpn

/**
 * L2 隔离原型的 TUN 与上游配置（纯 JVM 可校验，不依赖 Android API）。
 *
 * 关键设计：把「系统看到的 DNS 服务器」与「真实上游解析器」拆成两个地址。
 * - `virtualDns` 是本 VPN 专用的虚拟地址，只把它 /32 路由进 TUN；
 * - `upstreamDns` 是真实解析器，走受保护的 socket 直连真实网络。
 *
 * 早期版本直接把公共解析器（1.1.1.1/32）路由进 TUN，副作用是发往该 IP 的
 * **非 DNS 流量**也会被隧道捕获并被 `DnsTunnelAction.Unhandled` 丢弃。
 * 改为虚拟地址后，真实公网流量完全不经过本模块，`Unhandled` 的影响面收敛到
 * 「发往 10.233.0.1 的非 DNS 报文」——而该地址只由本 VPN 的 Builder 分配，
 * 正常应用不会访问，因此不再构成对普通流量的误伤。
 *
 * 上线前仍需评估 `upstreamDns` 的地域可用性、隐私政策与可配置入口；
 * 本原型不提供从网络下发配置的能力，只能由集成层显式调用 [copy] 构造。
 */
data class L2VpnConfig(
    val virtualDns: String = DEFAULT_VIRTUAL_DNS,
    val tunAddress: String = DEFAULT_TUN_ADDRESS,
    val upstreamDns: String = DEFAULT_UPSTREAM_DNS,
    val upstreamPort: Int = DEFAULT_DNS_PORT,
) {
    init {
        require(isIpv4Literal(virtualDns)) { "virtualDns 必须是合法 IPv4 字面量：$virtualDns" }
        require(isIpv4Literal(tunAddress)) { "tunAddress 必须是合法 IPv4 字面量：$tunAddress" }
        require(isIpv4Literal(upstreamDns)) { "upstreamDns 必须是合法 IPv4 字面量：$upstreamDns" }
        require(virtualDns != tunAddress) { "virtualDns 与 tunAddress 不能同址" }
        // 同址时 addRoute 会把上游 socket 的查询重新拉进 TUN，形成无限回环。
        require(virtualDns != upstreamDns) { "upstreamDns 不得与 virtualDns 同址，否则上游查询会回环进 TUN" }
        require(upstreamPort in 1..65535) { "upstreamPort 越界：$upstreamPort" }
    }

    companion object {
        const val DEFAULT_VIRTUAL_DNS = "10.233.0.1"
        const val DEFAULT_TUN_ADDRESS = "10.233.0.2"
        const val DEFAULT_UPSTREAM_DNS = "1.1.1.1"
        const val DEFAULT_DNS_PORT = 53

        val DEFAULT = L2VpnConfig()

        /** 严格的 IPv4 点分十进制字面量校验；不接受主机名、端口、前导零或 IPv6。 */
        fun isIpv4Literal(value: String): Boolean {
            if (value.isEmpty() || value.length > 15) return false
            val parts = value.split(".")
            if (parts.size != 4) return false
            return parts.all { part ->
                part.length in 1..3 &&
                    part.all { it in '0'..'9' } &&
                    (part.length == 1 || part[0] != '0') &&
                    part.toInt() <= 255
            }
        }
    }
}
