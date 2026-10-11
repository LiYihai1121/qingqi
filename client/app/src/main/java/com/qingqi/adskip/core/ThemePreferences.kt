package com.qingqi.adskip.core

import android.content.Context

/**
 * 深色模式偏好（跨层共享的横切设施）。
 *
 * 为什么放在 `core` 而不是 `data/Prefs`：与 [LanguagePreferences] 同一结论——
 * 主题选择要被两个互不相通的层读取：
 * - `ui/`（设置页写、组合根读）不 import `data.`；
 * - 放进 `data.Prefs` 会让 `MainActivity.setContent` 这条最简读取路径
 *   被迫穿过 ViewModel 或容器，attach 早期还未必构建完成。
 *
 * `core` 两侧皆可依赖，把键与读写收在这里，`Prefs` 与 `SettingsRepository`
 * 只做委托，避免出现第二份真相。存储的是 **tag 字符串**（`light` / `dark` /
 * `null`=跟随系统），存储格式与业务枚举解耦。
 *
 * 为什么**不能**复用 `Prefs` 的旧文件名 `adskip_prefs`：那个名字是 data 层
 * 加密迁移的「遗留明文源」，`Prefs.sp()` 首次初始化时会把它的键搬进加密文件
 * 并**删除整个文件**——写在这里的偏好会在下次进程重启时被清掉（真机实测）。
 * 跨层明文偏好必须用独立文件名，见 core/PreferencesFileNameTest。
 */
object ThemePreferences {

    /** 独立文件名：不得与 [com.qingqi.adskip.data.Prefs] 的迁移源重名。 */
    internal const val SP_NAME = "adskip_theme_prefs"

    private const val KEY_THEME_MODE = "theme_mode"

    /** 已保存的主题 tag；`null` 表示跟随系统。 */
    fun themeTag(context: Context): String? = prefs(context).getString(KEY_THEME_MODE, null)

    /** 写入主题 tag；传 `null` 表示跟随系统（清除应用级覆盖）。 */
    fun setThemeTag(context: Context, tag: String?) {
        prefs(context).edit()
            .apply { if (tag == null) remove(KEY_THEME_MODE) else putString(KEY_THEME_MODE, tag) }
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
}
