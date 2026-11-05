package com.qingqi.adskip.l2vpn

/**
 * TUN 数据面：只识别 IPv4 / UDP / 53 的 DNS 查询，其余一律不干预。
 *
 * 这是隔离原型的**最小可测实现**：IPv4 分片、IPv6、TCP DNS、加密 DNS 与
 * Private DNS 都不在本原型处理范围内（命中即原样放行或交给上游），
 * 真机矩阵验证见 DESIGN-ADS-FILTERING.md §7 与原型文档「未完成项」。
 *
 * 绝不改写非 DNS 流量，也不解析任何应用层内容。
 */
sealed interface DnsTunnelAction {

    /**
     * 无法识别的报文：原型不解析、不改写。
     *
     * 注意这不是「放行」：TUN 是三层接口，把出站报文原样写回会被协议栈当成入站包，
     * 因此这里选择不回注，仅累计 BYPASS。Builder 只把 1.1.1.1/32 路由进 TUN，
     * 普通应用流量不经过本模块；该限制列入原型未完成项，未在真机验证前不得视为发布就绪。
     */
    data object Unhandled : DnsTunnelAction

    /** 未命中规则 / fail-open：把原始查询交给上游解析。 */
    data class ForwardToUpstream(val dnsPayload: ByteArray, val sourcePort: Int) : DnsTunnelAction

    /** 命中规则：本地回一个 NXDOMAIN，不发起任何外部连接。 */
    data class ReplyLocally(val ipv4Packet: ByteArray) : DnsTunnelAction
}

private data class UdpDnsPacket(
    val headerLength: Int,
    val sourcePort: Int,
    val destinationPort: Int,
    val dnsPayload: ByteArray,
)

object DnsTunnelLoop {

    private const val PROTOCOL_UDP = 17
    private const val DNS_PORT = 53

    fun handle(
        ipv4Packet: ByteArray,
        rules: FilterRuleSet?,
        enabled: Boolean,
        consentGranted: Boolean,
    ): DnsTunnelAction {
        val udp = parseUdpDns(ipv4Packet) ?: return DnsTunnelAction.Unhandled
        val outcome = DnsFilterEngine.decide(udp.dnsPayload, rules, enabled, consentGranted)
        val nxdomain = if (outcome.decision == DnsDecision.BLOCK) {
            nxdomainResponse(udp.dnsPayload)
        } else {
            null
        }
        return if (nxdomain == null) {
            DnsTunnelAction.ForwardToUpstream(udp.dnsPayload, udp.sourcePort)
        } else {
            DnsTunnelAction.ReplyLocally(wrapReply(ipv4Packet, udp, nxdomain))
        }
    }

    /** 上游 DNS 响应也需要重新封装回 IPv4/UDP 才能写进 TUN。 */
    fun wrapUpstreamResponse(originalQuery: ByteArray, dnsResponse: ByteArray): ByteArray? {
        val udp = parseUdpDns(originalQuery) ?: return null
        return wrapReply(originalQuery, udp, dnsResponse)
    }

    /** 构造 NXDOMAIN：只保留问题段，清空回答/授权/附加计数。 */
    fun nxdomainResponse(query: ByteArray): ByteArray? {
        if (query.size < 12) return null
        val end = questionEnd(query) ?: return null
        val reply = query.copyOf(end)
        val flags = (u16(query, 2) and 0x0100) or 0x8000 or 0x0003
        put16(reply, 2, flags)
        put16(reply, 6, 0)
        put16(reply, 8, 0)
        put16(reply, 10, 0)
        return reply
    }

    /** 测试与上游响应解析共用：从查询方向（目的端口 53）取出 DNS 载荷。 */
    fun dnsPayloadOf(ipv4Packet: ByteArray): ByteArray? = parseUdpDns(ipv4Packet)?.dnsPayload

    /** 测试辅助：从本原型回写的响应包里取出 DNS 载荷（此时 53 是源端口）。 */
    fun dnsPayloadOfReply(ipv4Packet: ByteArray): ByteArray? = parseUdpDns(ipv4Packet, expectQuery = false)?.dnsPayload

    private fun parseUdpDns(packet: ByteArray, expectQuery: Boolean = true): UdpDnsPacket? {
        if (packet.size < 28) return null
        if ((packet[0].toInt() and 0xF0) ushr 4 != 4) return null
        val headerLength = (packet[0].toInt() and 0x0F) * 4
        if (headerLength < 20 || packet.size < headerLength + 8) return null
        if (packet[9].toInt() and 0xFF != PROTOCOL_UDP) return null
        if (u16(packet, 6) and 0x1FFF != 0) return null // 分片不处理
        val sourcePort = u16(packet, headerLength)
        val destinationPort = u16(packet, headerLength + 2)
        val dnsPort = if (expectQuery) destinationPort else sourcePort
        if (dnsPort != DNS_PORT) return null
        val udpLength = u16(packet, headerLength + 4)
        if (udpLength < 8 || headerLength + udpLength > packet.size) return null
        val dns = packet.copyOfRange(headerLength + 8, headerLength + udpLength)
        return UdpDnsPacket(headerLength, sourcePort, destinationPort, dns)
    }

    private fun wrapReply(original: ByteArray, udp: UdpDnsPacket, dns: ByteArray): ByteArray {
        val header = udp.headerLength
        val total = header + 8 + dns.size
        val reply = ByteArray(total)
        original.copyInto(reply, 0, 0, header)
        // 交换源/目的 IP：响应沿原路回到发起查询的虚拟地址
        for (i in 0 until 4) {
            reply[12 + i] = original[16 + i]
            reply[16 + i] = original[12 + i]
        }
        put16(reply, 2, total)
        put16(reply, 10, 0)
        reply[8] = 64 // TTL
        put16(reply, 10, ipChecksum(reply, header))
        put16(reply, header, udp.destinationPort)
        put16(reply, header + 2, udp.sourcePort)
        put16(reply, header + 4, 8 + dns.size)
        put16(reply, header + 6, 0) // UDP 校验和 0 = 不校验（IPv4 允许）
        dns.copyInto(reply, header + 8)
        return reply
    }

    private fun questionEnd(packet: ByteArray): Int? {
        var offset = 12
        while (offset < packet.size) {
            val length = packet[offset].toInt() and 0xFF
            if (length == 0) {
                offset++
                break
            }
            if (length and 0xC0 == 0xC0 || length > 63 || offset + 1 + length > packet.size) return null
            offset += 1 + length
        }
        return if (offset + 4 <= packet.size) offset + 4 else null
    }

    private fun ipChecksum(packet: ByteArray, headerLength: Int): Int {
        var sum = 0
        for (i in 0 until headerLength step 2) {
            sum += u16(packet, i)
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun u16(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)

    private fun put16(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = ((value ushr 8) and 0xFF).toByte()
        bytes[at + 1] = (value and 0xFF).toByte()
    }
}
