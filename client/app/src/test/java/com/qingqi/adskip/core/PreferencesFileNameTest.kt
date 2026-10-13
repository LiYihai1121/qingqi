package com.qingqi.adskip.core

import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 跨层明文偏好的文件名契约。
 *
 * `data/Prefs` 的加密存储初始化时会执行「遗留明文迁移」：打开旧文件名
 * `adskip_prefs`，把键搬进加密文件，然后 **deleteSharedPreferences 删除整个
 * 明文文件**。`core` 层偏好若与该文件重名，写入的值会在下次进程重启时被
 * 迁移逻辑静默清掉（真机实测：切深色 → 重启回落跟随系统）。
 *
 * 语言/主题偏好现已统一走 [SecureStore] 加密存储（FOLLOW-UP #25），本身不再
 * 持有明文文件名；本契约守住 SecureStore 侧的三个文件名常量——谁把加密文件
 * 或迁移源改名回冲突值，这里就红。
 */
class PreferencesFileNameTest {

    @Test
    fun `secure store file names must not collide with the legacy migration source`() {
        assertNotEquals("adskip_prefs", SecureStore.SP_NAME)
        assertNotEquals(SecureStore.LEGACY_SP_NAME, SecureStore.SP_NAME)
    }
}
