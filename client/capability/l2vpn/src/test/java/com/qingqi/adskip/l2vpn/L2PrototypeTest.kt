package com.qingqi.adskip.l2vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    @Test
    fun `controller keeps consistent counters under concurrent recording`() {
        val controller = L2VpnController()
        val threads = 8
        val perThread = 500
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) {
            Thread {
                start.await()
                repeat(perThread) {
                    controller.record(DnsFilterOutcome(DnsDecision.BLOCK, "matched rule"))
                }
                done.countDown()
            }.start()
        }
        start.countDown()
        assertTrue("并发写入未能在超时前完成", done.await(10, TimeUnit.SECONDS))
        assertEquals((threads * perThread).toLong(), controller.snapshot().blocked)
        assertEquals(0L, controller.snapshot().allowed)
    }

    @Test
    fun `vpn config separates the virtual dns from the real upstream`() {
        val defaults = L2VpnConfig.DEFAULT
        assertTrue(L2VpnConfig.isIpv4Literal(defaults.virtualDns))
        assertTrue(L2VpnConfig.isIpv4Literal(defaults.tunAddress))
        assertTrue(L2VpnConfig.isIpv4Literal(defaults.upstreamDns))
        assertTrue("虚拟 DNS 与真实上游必须不同址，否则上游查询会回环进 TUN", defaults.virtualDns != defaults.upstreamDns)
        assertEquals(53, defaults.upstreamPort)

        listOf(
            "1.1.1",
            "1.1.1.256",
            "01.1.1.1",
            "dns.example.com",
            "::1",
            "1.1.1.1:53",
            "",
        ).forEach { assertFalse("$it 不应被当作 IPv4 字面量", L2VpnConfig.isIpv4Literal(it)) }

        assertRejected { L2VpnConfig(upstreamDns = L2VpnConfig.DEFAULT_VIRTUAL_DNS) }
        assertRejected { L2VpnConfig(virtualDns = "nope") }
        assertRejected { L2VpnConfig(upstreamPort = 0) }
        assertEquals("8.8.8.8", L2VpnConfig(upstreamDns = "8.8.8.8").upstreamDns)
    }

    @Test
    fun `verifier accepts a valid prototype payload and keeps allowlist precedence`() {
        val set = FilterRuleSourceVerifier.verify(payload(allowlist = listOf("login.example.com")))
        assertNotNull("合法载荷应通过校验", set)
        assertEquals(FilterDecision.BLOCK, set?.decide("ads.example.com"))
        assertEquals(FilterDecision.ALLOW, set?.decide("login.example.com"))
        assertEquals(FilterRuleDigest.compute(payload(allowlist = listOf("login.example.com"))), set?.meta?.sha256)
    }

    @Test
    fun `verifier is fail open and never lets a rule source auto enable the vpn`() {
        assertFalse(FilterRuleSourceVerifier.AUTO_ENABLE_ALLOWED)
        assertFalse(FilterRuleSourceVerifier.mayAutoEnableVpn())
        listOf(
            payload(enabledByDefault = true),
            payload(phase0Approved = true),
            payload(prototypeOnly = false),
            payload(schemaVersion = 2),
            payload(sha256 = "xyz"),
            payload(domains = listOf("*.example.com")),
            payload(allowlist = listOf("localhost")),
        ).forEach { assertNull("非法载荷必须回退为 null：\n$it", FilterRuleSourceVerifier.verify(it)) }
    }

    @Test
    fun `client digest matches the server canonical digest for the bundled seed`() {
        // 跨语言常量：server/seed/filter-rules.json 的 sha256 由 Bun computeRuleDigest 生成。
        // 任一侧改动字段顺序、转义或排序口径，这条用例立刻红灯，避免两侧静默漂移。
        val fromSeed = FilterRuleSourcePayload(
            schemaVersion = 1,
            enabledByDefault = false,
            phase0Approved = false,
            prototypeOnly = true,
            source = "bundled-prototype-placeholder",
            license = "project-own (no third-party rule set bundled)",
            version = "0.0.0-prototype",
            updatedAt = "2026-11-05",
            sha256 = "",
            domains = emptyList(),
            allowlist = emptyList(),
            note = "L2 隔离原型占位规则源：Phase 0 合规评审通过前不捆绑任何第三方规则集，默认关闭且不进入发布分支。",
        )
        assertEquals(
            "d5330fcee5bb70dd6004daef2cfd645977f8b423582995be554aeb67393f58e0",
            FilterRuleDigest.compute(fromSeed),
        )
    }

    @Test
    fun `verifier rejects domains changed after signing even when sha256 looks valid`() {
        val signed = payload(domains = listOf("ads.example.com"))
        assertEquals(64, signed.sha256.length)
        assertNull("格式合法的伪造摘要也必须被拒绝", FilterRuleSourceVerifier.verify(signed.copy(sha256 = "0".repeat(64))))
        assertNull(
            "域名列表被替换后摘要未变，必须拒绝",
            FilterRuleSourceVerifier.verify(signed.copy(domains = listOf("bank.example.com"))),
        )
        assertNull(
            "note 被改写后摘要未变，必须拒绝",
            FilterRuleSourceVerifier.verify(signed.copy(note = "tampered")),
        )
    }

    @Test
    fun `digest canonicalises array order like the server does`() {
        val ordered = FilterRuleDigest.compute(payload(domains = listOf("a.example.com", "b.example.com")))
        val shuffled = FilterRuleDigest.compute(payload(domains = listOf("b.example.com", "a.example.com")))
        assertEquals("两侧都必须按字典序排序后再摘要", ordered, shuffled)
    }

    // ---------- 测试夹具 ----------

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("非法配置应被拒绝")
        } catch (error: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    private fun payload(
        enabledByDefault: Boolean = false,
        phase0Approved: Boolean = false,
        prototypeOnly: Boolean = true,
        schemaVersion: Int = 1,
        domains: List<String> = listOf("ads.example.com"),
        allowlist: List<String> = emptyList(),
        sha256: String? = null,
        note: String = "prototype test fixture",
    ): FilterRuleSourcePayload {
        val unsigned = FilterRuleSourcePayload(
            schemaVersion = schemaVersion,
            enabledByDefault = enabledByDefault,
            phase0Approved = phase0Approved,
            prototypeOnly = prototypeOnly,
            source = "bundled-prototype-placeholder",
            license = "project-own",
            version = "0.0.0-prototype",
            updatedAt = "2026-11-05",
            sha256 = "",
            domains = domains,
            allowlist = allowlist,
            note = note,
        )
        // 摘要不覆盖自身字段，先构造无摘要载荷再回填，保证默认夹具是「已正确签名」的。
        return unsigned.copy(sha256 = sha256 ?: FilterRuleDigest.compute(unsigned))
    }

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
