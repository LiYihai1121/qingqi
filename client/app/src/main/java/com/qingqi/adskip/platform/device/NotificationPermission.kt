package com.qingqi.adskip.platform.device

import android.app.NotificationManager
import android.content.Context
import com.qingqi.adskip.core.common.LogRing

/**
 * 通知权限（`POST_NOTIFICATIONS`）的有效状态。
 *
 * 为什么它必须出现在「服务设置」的权限清单里：本应用的**服务守护**依赖通知把
 * 「跳过服务已停止」这条消息送达用户（见 [AccessibilityWatchdogJob]）。而
 * Android 13+ 上通知是**运行时权限**，用户未授权时系统会**静默丢弃**通知——
 * 不崩溃、不报错，用户只知道「服务莫名失效了」。
 *
 * 清单里已声明 `POST_NOTIFICATIONS`，但此前 App 内**没有任何入口**能查它、
 * 更没有入口能让用户去开启，等于一个「用户不知道自己没给」的权限缺口。
 *
 * 三态而非布尔：与 [AccessibilityStatus] / [BatteryExemption] 同一原则——
 * **不确定不等于未开启**。取不到 [NotificationManager] 或查询异常时落到
 * [UNKNOWN]，由 UI 引导用户手动核对，而不是谎报「未开启」。
 *
 * 依赖边界：本文件属 `device/` 层，不依赖 `ui/`、`data/`、`net/`、`sync/`。
 */
enum class NotificationPermission {
    /** 已确认通知可用。 */
    GRANTED,

    /** 已确认通知被拒绝（未授权，或用户在系统里关掉了本应用的通知）。 */
    DENIED,

    /** 无法确认（系统服务缺失或查询异常）。UI 应引导用户手动核对。 */
    UNKNOWN,
    ;

    companion object {

        /**
         * 纯决策函数：`null` 查询结果表示查询不可用，落到 [UNKNOWN]。
         *
         * 无 Android 依赖，可被 JVM 单测穷举（见 `NotificationPermissionTest`）。
         */
        fun decide(enabled: Boolean?): NotificationPermission = when (enabled) {
            true -> GRANTED
            false -> DENIED
            null -> UNKNOWN
        }

        /**
         * 查询系统真值。
         *
         * 用 [NotificationManager.areNotificationsEnabled] 而非
         * `checkSelfPermission`：前者同时覆盖「Android 13+ 未授予运行时权限」
         * 与「用户已授权但后来在系统设置里关掉了本应用通知」两种情况，
         * 后者只看运行时授权，漏掉用户主动关闭的那一半。
         *
         * @param manager 由调用方取得；`null` 表示系统服务不可用。
         */
        fun detect(manager: NotificationManager?): NotificationPermission {
            val enabled = if (manager == null) {
                null
            } else {
                try {
                    manager.areNotificationsEnabled()
                } catch (e: Exception) {
                    LogRing.w("NotificationPermission", "查询通知权限失败: ${e.message}")
                    null
                }
            }
            return decide(enabled)
        }

        /** 便捷入口：自行取系统服务后查询。 */
        fun detect(context: Context): NotificationPermission =
            detect(context.getSystemService(NotificationManager::class.java))
    }
}
