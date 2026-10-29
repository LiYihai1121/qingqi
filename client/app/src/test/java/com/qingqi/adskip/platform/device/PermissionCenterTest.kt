package com.qingqi.adskip.platform.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 权限中心纯逻辑的真值表。
 *
 * 「权限与系统开关」此前分散在首页状态环、「我的」页无障碍卡片与设置页三处，
 * 用户无法一眼看出「到底还差什么才能正常工作」。本测试钉住合并后的判定规则。
 */
class PermissionCenterTest {

    @Test
    fun `accessibility and battery map their tri-state directly`() {
        assertEquals(
            PermissionStatus.READY,
            PermissionCenter.accessibilityStatus(AccessibilityStatus.ON),
        )
        assertEquals(
            PermissionStatus.NEEDS_ACTION,
            PermissionCenter.accessibilityStatus(AccessibilityStatus.OFF),
        )
        assertEquals(
            PermissionStatus.UNKNOWN,
            PermissionCenter.accessibilityStatus(AccessibilityStatus.UNKNOWN),
        )

        assertEquals(
            PermissionStatus.READY,
            PermissionCenter.batteryStatus(BatteryExemption.EXEMPT),
        )
        assertEquals(
            PermissionStatus.NEEDS_ACTION,
            PermissionCenter.batteryStatus(BatteryExemption.NOT_EXEMPT),
        )
        assertEquals(
            PermissionStatus.UNKNOWN,
            PermissionCenter.batteryStatus(BatteryExemption.UNKNOWN),
        )
    }

    @Test
    fun `notification permission maps its tri-state directly`() {
        assertEquals(
            PermissionStatus.READY,
            PermissionCenter.notificationStatus(NotificationPermission.GRANTED),
        )
        assertEquals(
            PermissionStatus.NEEDS_ACTION,
            PermissionCenter.notificationStatus(NotificationPermission.DENIED),
        )
        assertEquals(
            PermissionStatus.UNKNOWN,
            PermissionCenter.notificationStatus(NotificationPermission.UNKNOWN),
        )
    }

    @Test
    fun `vendor keep-alive is always UNKNOWN because the system exposes no query`() {
        // 这是刻意的：厂商自启动没有公开查询 API。谎报「未开启」会让已经开好的用户
        // 被反复引导去设置页，而状态永远不变——这正是本项必须停在 UNKNOWN 的理由。
        assertEquals(PermissionStatus.UNKNOWN, PermissionCenter.vendorKeepAliveStatus())
    }

    @Test
    fun `ready count only counts confirmed items`() {
        val items = PermissionCenter.build(
            accessibility = AccessibilityStatus.ON,
            battery = BatteryExemption.EXEMPT,
            notification = NotificationPermission.GRANTED,
        )
        assertEquals(4, items.size)
        // 无障碍、电池与通知已确认就绪，各计 1；厂商自启动恒为 UNKNOWN，不计入。
        assertEquals(3, PermissionCenter.readyCount(items))
        assertEquals(PermissionKeys.ACCESSIBILITY, items[0].key)
        assertEquals(PermissionStatus.READY, items[0].status)
        assertEquals(PermissionStatus.READY, items[1].status)
        assertEquals(PermissionStatus.READY, items[2].status)
        assertEquals(PermissionStatus.UNKNOWN, items[3].status)
    }

    @Test
    fun `the quick settings tile is no longer part of the checklist`() {
        // 磁贴功能已整体删除（TileService / 清单注册 / 图标 / 文案）。
        // 清单里若再出现该 key，UI 会渲染出一行点不动的死条目。
        val items = PermissionCenter.build(
            accessibility = AccessibilityStatus.ON,
            battery = BatteryExemption.EXEMPT,
            notification = NotificationPermission.GRANTED,
        )
        assertTrue(
            "权限清单不应再包含快捷磁贴项（功能已删除）",
            items.none { it.key == "quick_tile" },
        )
    }

    @Test
    fun `every item declares whether it needs an action and whether it is actionable`() {
        val items = PermissionCenter.build(
            accessibility = AccessibilityStatus.OFF,
            battery = BatteryExemption.UNKNOWN,
            notification = NotificationPermission.DENIED,
        )
        items.forEach { item ->
            // 判定层不持有资源 id，标题由 UI 层按 key 注入，故此处只保证 key 稳定非空。
            assertTrue("权限项缺少稳定 key", item.key.isNotBlank())
            // UNKNOWN 必须仍可操作：用户需要能去系统页自己核对，
            // 否则「无法确认」会变成死路。
            if (item.status == PermissionStatus.UNKNOWN) {
                assertTrue("${item.key} 为 UNKNOWN 时必须仍给出入口", item.actionable)
            }
        }
        assertEquals(
            PermissionStatus.NEEDS_ACTION,
            items.first { it.key == PermissionKeys.ACCESSIBILITY }.status,
        )
        assertEquals(
            PermissionStatus.NEEDS_ACTION,
            items.first { it.key == PermissionKeys.NOTIFICATION }.status,
        )
    }
}
