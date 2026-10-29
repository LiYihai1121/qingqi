package com.qingqi.adskip.platform.device

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 通知权限三态的判定真值表。
 *
 * 与 [BatteryExemptionTest] / [AccessibilityStatusTest] 同型：把「查得到 / 查不到」
 * 两种输入穷举成可断言的映射，防止有人日后把它改回布尔。
 */
class NotificationPermissionTest {

    @Test
    fun `granted maps to GRANTED`() {
        assertEquals(NotificationPermission.GRANTED, NotificationPermission.decide(true))
    }

    @Test
    fun `denied maps to DENIED`() {
        assertEquals(NotificationPermission.DENIED, NotificationPermission.decide(false))
    }

    @Test
    fun `an unavailable query is UNKNOWN rather than denied`() {
        // 关键断言：查询失败**不得**判成 DENIED。
        // 谎报「未授权」会让已经授权的用户反复被弹窗请求，而状态永远不变。
        assertEquals(NotificationPermission.UNKNOWN, NotificationPermission.decide(null))
    }

    @Test
    fun `all three states are reachable`() {
        val states = listOf(true, false, null).map { NotificationPermission.decide(it) }.toSet()
        assertEquals(NotificationPermission.entries.toSet(), states)
    }
}
