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
}
