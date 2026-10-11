package com.qingqi.adskip.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [ThemeMode] 的映射真值表（与 device/LanguageModeTest 同一套关注点）。
 *
 * 重点是「跟随系统」那条：它必须携带**空** tag，让存储层走 remove 而不是
 * 写入占位值——否则「跟随系统」会被持久化成某个具体模式，系统切换深色时
 * 应用不再跟随，而且用户没有任何可见的开关状态可以解释这个行为。
 */
class ThemeModeTest {

    @Test
    fun `system mode carries no tag so callers can clear the override`() {
        assertNull(ThemeMode.SYSTEM.tag)
    }

    @Test
    fun `concrete modes carry their own tags`() {
        assertEquals("light", ThemeMode.LIGHT.tag)
        assertEquals("dark", ThemeMode.DARK.tag)
    }

    @Test
    fun `fromTag round trips and falls back to system for unknown values`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromTag(mode.tag))
        }
        // 旧值 / 损坏值不应崩溃，也不该被误判成某种固定模式。
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromTag(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromTag(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromTag("sepia"))
    }
}
