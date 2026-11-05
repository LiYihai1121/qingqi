package com.qingqi.adskip.l2vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * L2 隔离原型的纯 JVM 回归：规则解析、判定、状态机与 TUN 数据面。
 * 真机矩阵（IPv6 / Private DNS / VPN 冲突 / 断网恢复）不在此覆盖，
 * 见 docs/planning/DESIGN-L2L3-ISOLATED-PROTOTYPE.md「未完成项」。
 */
class L2PrototypeTest {

    private fun rules(vararg domains: String, allowlist: Set<String> = emptySet()) = FilterRuleSet(
        meta = FilterRuleSourceMeta(
            source = "local-test-fixture",
            license = "project own test data",
            version = "1",
            updatedAt = "2026-11-05",
            sha256 = "test-only",
        ),
        blockedDomains = domains.toSet(),
        allowlist = allowlist,
    )

    @Test
    fun `hosts parser accepts only auditable sink rules`() {
        val parsed = HostsRuleParser.parse(
            "# comment\n0.0.0.0 Ads.Example.com tracking.example.com\n127.0.0.1 ads.example.com\n",
        )
        assertEquals(setOf("ads.example.com", "tracking.example.com"), parsed)

        val invalid = listOf(
            "1.2.3.4 example.com",
            "0.0.0.0 localhost",
            "0.0.0.0 *.example.com",
            "0.0.0.0 example.com/path",
            "||example.com^",
            "0.0.0.0",
        )
        invalid.forEach { assertNull("应拒绝：$it", HostsRuleParser.parseOrNull(it)) }
    }

    @Test
    fun `rule set honours allowlist and never matches lookalike domains`() {
        val set = rules("ads.example.com", allowlist = setOf("login.example.com"))
        assertEquals(FilterDecision.BLOCK, set.decide("ads.example.com"))
        assertEquals(FilterDecision.BLOCK, set.decide("img.ADS.example.com"))
        assertEquals(FilterDecision.ALLOW, set.decide("notads.example.com"))
        assertEquals(FilterDecision.ALLOW, set.decide("login.example.com"))
        assertEquals(FilterDecision.ALLOW, set.decide("example.com"))
    }

    @Test
    fun `dns question parser extracts qname and rejects malformed input`() {
        val query = dnsQuery("ads.example.com")
        val question = DnsQuestionParser.parse(query)
        assertEquals("ads.example.com", question?.qname)
        assertEquals(DnsQuestionParser.TYPE_A, question?.qtype ?: -1)
        assertNull(DnsQuestionParser.parse(ByteArray(4)))
        assertNull(DnsQuestionParser.parse(query.copyOf().also { it[2] = 0x80.toByte() }))
    }

    @Test
    fun `filter engine is fail open unless enabled and explicitly authorized`() {
        val query = dnsQuery("ads.example.com")
        val set = rules("ads.example.com")
        assertEquals(DnsDecision.BLOCK, DnsFilterEngine.decide(query, set, true, true).decision)
        assertEquals(DnsDecision.BYPASS, DnsFilterEngine.decide(query, set, false, true).decision)
        assertEquals(DnsDecision.BYPASS, DnsFilterEngine.decide(query, set, true, false).decision)
        assertEquals(DnsDecision.BYPASS, DnsFilterEngine.decide(query, null, true, true).decision)
        assertEquals(DnsDecision.BYPASS, DnsFilterEngine.decide(ByteArray(3), set, true, true).decision)
        assertEquals(
            DnsDecision.BYPASS,
            DnsFilterEngine.decide(dnsQuery("ads.example.com", qtype = 65), set, true, true).decision,
        )
    }

    @Test
    fun `controller refuses unauthorized start and restores network on stop`() {
        val controller = L2VpnController()
        assertEquals(L2VpnState.DISABLED, controller.snapshot().state)
        assertTrue(controller.snapshot().networkRestoreRequired)
        assertFalse(controller.requestStart(false))
        assertEquals(L2VpnState.DISABLED, controller.snapshot().state)

        assertTrue(controller.requestStart(true))
        assertEquals(L2VpnState.AWAITING_CONSENT, controller.snapshot().state)
        assertTrue(controller.onVpnEstablished())
        assertFalse(controller.snapshot().networkRestoreRequired)

        assertTrue(controller.stop())
        assertEquals(L2VpnState.STOPPED, controller.snapshot().state)
        assertTrue(controller.snapshot().networkRestoreRequired)
        assertTrue(controller.onUnexpectedExit())
        assertTrue(controller.snapshot().networkRestoreRequired)
    }

    @Test
    fun `controller keeps aggregate counters only`() {
        val controller = L2VpnController()
        controller.record(DnsFilterOutcome(DnsDecision.BLOCK, "matched rule"))
        controller.record(DnsFilterOutcome(DnsDecision.ALLOW, "not matched"))
        controller.record(DnsFilterOutcome(DnsDecision.BYPASS, "non dns packet"))
        val snapshot = controller.snapshot()
        assertEquals(1L, snapshot.blocked)
        assertEquals(1L, snapshot.allowed)
        assertEquals(1L, snapshot.bypassed)
        controller.resetCounters()
        assertEquals(0L, controller.snapshot().blocked)
    }

    @Test
    fun `tunnel loop returns NXDOMAIN for matched queries only`() {
        val set = rules("ads.example.com")
        val query = dnsQuery("ads.example.com")
        val packet = ipv4UdpDns(query)

        val blocked = DnsTunnelLoop.handle(packet, set, enabled = true, consentGranted = true)
        assertTrue(blocked is DnsTunnelAction.ReplyLocally)
        val replyDns = requireNotNull(
            DnsTunnelLoop.dnsPayloadOfReply((blocked as DnsTunnelAction.ReplyLocally).ipv4Packet),
        ) { "回写的响应包应包含可解析的 DNS 载荷" }
        assertEquals(3, u16(replyDns, 2) and 0xF) // RCODE = NXDOMAIN
        // 只保留问题段：原查询的问题段必须逐字节保留，不能篡改域名或 QTYPE
        assertArrayEquals(query.copyOfRange(12, query.size), replyDns.copyOfRange(12, replyDns.size))

        val allowed = DnsTunnelLoop.handle(
            ipv4UdpDns(dnsQuery("api.example.com")),
            set,
            enabled = true,
            consentGranted = true,
        )
        assertTrue(allowed is DnsTunnelAction.ForwardToUpstream)
    }

    @Test
    fun `tunnel loop never touches non dns traffic`() {
        val set = rules("ads.example.com")
        val http = ByteArray(60).also {
            it[0] = 0x45.toByte()
            it[9] = 6
        }
        assertTrue(
            DnsTunnelLoop.handle(http, set, true, true) is DnsTunnelAction.Unhandled,
        )
        val plainUdp = ipv4UdpDns(dnsQuery("ads.example.com")).also {
            it[22] = 0
            it[23] = 80.toByte()
        }
        assertTrue(
            DnsTunnelLoop.handle(plainUdp, set, true, true) is DnsTunnelAction.Unhandled,
        )
    }

    @Test
    fun `tunnel loop forwards when disabled or unapproved`() {
        val set = rules("ads.example.com")
        val packet = ipv4UdpDns(dnsQuery("ads.example.com"))
        assertTrue(DnsTunnelLoop.handle(packet, set, false, true) is DnsTunnelAction.ForwardToUpstream)
        assertTrue(DnsTunnelLoop.handle(packet, set, true, false) is DnsTunnelAction.ForwardToUpstream)
    }

    // ---------- 测试夹具 ----------

    private fun dnsQuery(qname: String, qtype: Int = DnsQuestionParser.TYPE_A): ByteArray {
        val out = ByteArrayOutputStream()
        fun u16(value: Int) {
            out.write(value ushr 8)
            out.write(value and 0xFF)
        }
        u16(0x1234) // ID
        u16(0x0100) // 标准查询，期望递归
        u16(1) // QDCOUNT
        u16(0)
        u16(0)
        u16(0)
        for (label in qname.split(".")) {
            out.write(label.length)
            out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.write(0)
        u16(qtype)
        u16(1) // CLASS IN
        return out.toByteArray()
    }

    private fun ipv4UdpDns(dns: ByteArray): ByteArray {
        val total = 20 + 8 + dns.size
        val packet = ByteArray(total)
        packet[0] = 0x45.toByte()
        packet[2] = ((total ushr 8) and 0xFF).toByte()
        packet[3] = (total and 0xFF).toByte()
        packet[8] = 64
        packet[9] = 17
        packet[12] = 10
        packet[15] = 2
        packet[16] = 1
        packet[17] = 1
        packet[18] = 1
        packet[19] = 1
        put16(packet, 20, 41000)
        put16(packet, 22, 53)
        put16(packet, 24, 8 + dns.size)
        dns.copyInto(packet, 28)
        return packet
    }

    private fun put16(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = ((value ushr 8) and 0xFF).toByte()
        bytes[at + 1] = (value and 0xFF).toByte()
    }

    private fun u16(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)
}
