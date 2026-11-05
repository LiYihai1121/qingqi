package com.qingqi.adskip.l3shake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** L3 PoC 骨架的纯 JVM 回归：判定算法 + 启停与保护契约。 */
class L3PrototypeTest {

    private fun peak(atMs: Long) = ShakeSample(atMs, 25f, 0f, 0f)

    private fun still(atMs: Long) = ShakeSample(atMs, 9.81f, 0f, 0f)

    @Test
    fun `detector triggers only on repeated peaks inside the window`() {
        val detector = ShakeDetector()
        assertFalse(detector.onSample(still(0)))
        assertFalse(detector.onSample(peak(10)))
        assertTrue(detector.onSample(peak(90)))
    }

    @Test
    fun `detector respects cooldown and reset`() {
        val detector = ShakeDetector()
        detector.onSample(peak(0))
        assertTrue(detector.onSample(peak(100)))
        detector.onSample(peak(300))
        assertFalse(detector.onSample(peak(400))) // 冷却期内的往复不重复触发
        detector.reset()
        detector.onSample(peak(2000))
        assertTrue(detector.onSample(peak(2100)))
    }

    @Test
    fun `detector ignores peaks outside the time window`() {
        val detector = ShakeDetector(windowMs = 200)
        detector.onSample(peak(0))
        assertFalse(detector.onSample(peak(500)))
        assertTrue(detector.onSample(peak(600)))
    }

    @Test
    fun `guard stays disabled without a traceable separate approval`() {
        val guard = ShakeGuard()
        assertEquals(ShakeGuardState.NOT_INSTALLED, guard.state())
        assertEquals(
            ShakeGuardAction.PASS_THROUGH,
            guard.onCandidate("com.example.news", peak(0)),
        )
        assertFalse(guard.enable(ShakeApproval(false, "issue-84", "2026-11-05")))
        assertFalse(guard.enable(ShakeApproval(true, "", "2026-11-05")))
        assertFalse(guard.enable(ShakeApproval(true, "issue-84", "")))
        assertEquals(ShakeGuardState.NOT_INSTALLED, guard.state())
        assertFalse(guard.activate())
    }

    @Test
    fun `guard never acts on payment login authorization or reward flows`() {
        listOf("com.bank.app", "com.x.pay", "com.x.wallet", "com.x.login", "com.x.reward")
            .forEach { assertTrue("$it 应受保护", ShakeGuardContract.isProtected(it)) }

        val guard = ShakeGuard()
        assertTrue(guard.enable(ShakeApproval(true, "issue-84/ADR-ADS-002", "2026-11-05")))
        assertTrue(guard.activate())
        assertEquals(ShakeGuardState.ACTIVE, guard.state())
        assertEquals(
            ShakeGuardAction.PASS_THROUGH,
            guard.onCandidate("com.bank.app", peak(0)),
        )
        guard.onCandidate("com.example.news", peak(0))
        assertEquals(
            ShakeGuardAction.SUPPRESS_SHAKE,
            guard.onCandidate("com.example.news", peak(100)),
        )
    }

    @Test
    fun `guard can be revoked at any time`() {
        val guard = ShakeGuard()
        guard.enable(ShakeApproval(true, "issue-84/ADR-ADS-002", "2026-11-05"))
        guard.activate()
        guard.disable()
        assertEquals(ShakeGuardState.DISABLED, guard.state())
        guard.onCandidate("com.example.news", peak(0))
        assertEquals(
            ShakeGuardAction.PASS_THROUGH,
            guard.onCandidate("com.example.news", peak(100)),
        )
        assertFalse(guard.activate())
    }

    @Test
    fun `contract constants keep the prototype disabled and separately gated`() {
        assertFalse(ShakeGuardContract.DEFAULT_ENABLED)
        assertTrue(ShakeGuardContract.REQUIRES_SEPARATE_APPROVAL)
        assertEquals(ShakeGuardState.NOT_INSTALLED, ShakeGuard().state())
    }

    @Test
    fun `protected flows are matched case insensitively without false positives`() {
        listOf(
            "com.BANK.app",
            "com.x.PAY",
            "com.x.Wallet",
            "com.x.oauth",
            "com.x.AntiCheat",
            "com.x.VERIFY",
            "com.x.bonus",
            "com.x.security",
            "com.x.reward",
        ).forEach { assertTrue("$it 应受保护", ShakeGuardContract.isProtected(it)) }
        listOf("com.example.news", "com.example.reader", "com.example.market")
            .forEach { assertFalse("$it 不应被误判为受保护流程", ShakeGuardContract.isProtected(it)) }
    }

    @Test
    fun `armed but not activated guard never suppresses`() {
        val guard = ShakeGuard()
        assertTrue(guard.enable(ShakeApproval(true, "issue-84/ADR-ADS-002", "2026-11-05")))
        assertEquals(ShakeGuardState.ARMED, guard.state())
        assertEquals(ShakeGuardAction.PASS_THROUGH, guard.onCandidate("com.example.news", peak(0)))
        assertEquals(ShakeGuardAction.PASS_THROUGH, guard.onCandidate("com.example.news", peak(100)))
        assertEquals(ShakeGuardState.ARMED, guard.state())
    }

    @Test
    fun `protected flows never reach the detector even after a shake was detected`() {
        val guard = ShakeGuard()
        guard.enable(ShakeApproval(true, "issue-84/ADR-ADS-002", "2026-11-05"))
        guard.activate()
        guard.onCandidate("com.example.news", peak(0))
        assertEquals(
            ShakeGuardAction.SUPPRESS_SHAKE,
            guard.onCandidate("com.example.news", peak(100)),
        )
        // 冷却期已过，这两个峰值本可凑成一次摇动；保护流程必须无条件 PASS_THROUGH，
        // 连采样都不交给检测器，避免把「支付中被拦」变成状态残留的副作用。
        assertEquals(ShakeGuardAction.PASS_THROUGH, guard.onCandidate("com.bank.app", peak(2000)))
        assertEquals(ShakeGuardAction.PASS_THROUGH, guard.onCandidate("com.bank.app", peak(2100)))
    }

    @Test
    fun `detector never triggers on gravity-only samples`() {
        val detector = ShakeDetector()
        repeat(10) { index -> assertFalse(detector.onSample(still(index * 100L))) }
    }

    @Test
    fun `revoking clears the detector so a stale peak cannot suppress a new session`() {
        val guard = ShakeGuard()
        val approval = ShakeApproval(true, "issue-84/ADR-ADS-002", "2026-11-05")
        guard.enable(approval)
        guard.activate()
        guard.onCandidate("com.example.news", peak(0)) // 只积累一个峰值
        guard.disable()
        assertTrue(guard.enable(approval))
        assertTrue(guard.activate())
        // disable() 已 reset()：重新激活后的第一个峰值不能与旧峰值拼成一次摇动。
        assertEquals(ShakeGuardAction.PASS_THROUGH, guard.onCandidate("com.example.news", peak(2000)))
        assertEquals(ShakeGuardAction.SUPPRESS_SHAKE, guard.onCandidate("com.example.news", peak(2100)))
    }
}
