package com.qingqi.adskip.core

/**
 * 应用内深色模式选择。
 *
 * 与 [LanguageMode] 同构的建模理由：`SYSTEM`（跟随系统）与「具体取值」语义不同——
 * 前者要交给 `isSystemInDarkTheme()` 现场判定，后者是显式覆盖。用可空字符串或
 * 布尔值表达都会把「跟随系统」与「未设置」混为一谈。
 *
 * 放在 `core` 而不是 `ui/theme`：它需要被 `ui/`（设置页读写）、`ui/MainActivity`
 * （组合根选色板）共同读取，`core` 是双方都允许依赖的横切层；且不引用任何
 * Android Compose 类型，可在纯 JVM 单测里穷举。
 */
enum class ThemeMode(val tag: String?) {
    /** 跟随系统深色模式。 */
    SYSTEM(null),

    /** 始终浅色。 */
    LIGHT("light"),

    /** 始终深色。 */
    DARK("dark"),
    ;

    companion object {

        /** 默认值：跟随系统。写死具体模式会让首次安装的用户观感与系统不符。 */
        val DEFAULT = SYSTEM

        /** 从持久化的字符串还原；无法识别时回落到 [DEFAULT]（旧值/损坏值不应崩溃）。 */
        fun fromTag(tag: String?): ThemeMode = entries.firstOrNull { it.tag != null && it.tag == tag } ?: DEFAULT
    }
}
