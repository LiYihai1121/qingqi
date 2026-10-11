package com.qingqi.adskip.core

import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 跨层明文偏好的文件名契约。
 *
 * `data/Prefs` 的加密存储初始化时会执行「遗留明文迁移」：打开旧文件名
 * `adskip_prefs`，把键搬进加密文件，然后 **deleteSharedPreferences 删除整个
 * 明文文件**。`core` 层的语言/主题偏好若与该文件重名，写入的值会在下次
 * 进程重启时被迁移逻辑静默清掉（真机实测：切深色 → 重启回落跟随系统）。
 *
 * 这三个文件名都是编译期常量，无法在 Robolectric 之外的 JVM 测试里用真实
 * SharedPreferences 验证，故以常量断言守住：谁把文件名改回去，这里就红。
 */
class PreferencesFileNameTest {

    @Test
    fun `core preferences must not collide with the legacy migration source`() {
        assertNotEquals("adskip_prefs", LanguagePreferences.SP_NAME)
        assertNotEquals("adskip_prefs", ThemePreferences.SP_NAME)
    }
}
