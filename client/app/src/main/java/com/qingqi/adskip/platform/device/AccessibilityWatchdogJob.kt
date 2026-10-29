package com.qingqi.adskip.platform.device

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.qingqi.adskip.R

/**
 * 无障碍服务守护（P0-2，架构审查项）：周期巡检服务的**有效状态**，失效时发通知引导恢复。
 *
 * 真机实证（Mi 9 / Android 11 / MIUI V125，2026-10-11）：系统会把 `accessibility_enabled`
 * 回置为 0、进程被杀也无任何用户可见提示——而「跳过在生效」是本应用对用户的核心承诺，
 * 静默失效等于产品承诺失效。此前唯一的暴露面是用户主动打开 App 看首页状态，不可接受。
 *
 * 三个角色：
 * - SkipAdService.onServiceConnected → [arm]：服务首次连上后武装周期巡检（跨重启持久）；
 * - SkipAdService.onUnbind → [scheduleConfirm]：用户**主动**关闭时延迟复核并撤销巡检——
 *   否则「故意关闭的用户」每 15 分钟被提醒一次；进程被杀不会走 onUnbind，
 *   巡检仍在，正好覆盖失效场景；
 * - 本 Job：[AccessibilityStatus.detect] 巡检，确认 [AccessibilityStatus.OFF] 时发恢复通知
 *   （contentIntent 直达系统无障碍设置页）；[AccessibilityStatus.UNKNOWN] 静默——不谎报。
 *
 * 状态检测复用 [AccessibilityStatus]（device/ 层内闭环，不 import service/，依赖边界同磁贴）。
 * 通知在 Android 13+ 需 POST_NOTIFICATIONS 运行时授权，未授权时静默丢弃——首页状态检测
 * 仍是兜底；本 Job 刻意不引入运行时权限请求流程（那是设置页的既有职责）。
 */
class AccessibilityWatchdogJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val status = AccessibilityStatus.detect(
            this,
            AccessibilityStatus.SKIP_AD_SERVICE_CLASS_NAME,
            processSignal = null,
        )
        if (status == AccessibilityStatus.OFF) notifyRecovery(this)
        return false // 同步完成，无需系统保活
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val JOB_WATCHDOG = 2102
        private const val JOB_CONFIRM = 2103
        private const val CONFIRM_DELAY_MS = 2 * 60 * 1000L // 主动关闭后的复核延迟
        private const val CHANNEL_ID = "watchdog"
        private const val NOTIFICATION_ID = 2102

        /** 服务连上后武装巡检（幂等；跨重启持久）。 */
        fun arm(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.allPendingJobs.any { it.id == JOB_WATCHDOG }) return
            val job = JobInfo.Builder(JOB_WATCHDOG, ComponentName(context, AccessibilityWatchdogJob::class.java))
                .setPeriodic(15 * 60 * 1000L) // JobScheduler 最小周期
                .setPersisted(true)
                .build()
            scheduler.schedule(job)
        }

        /** onUnbind 后延迟复核：服务仍处于关闭态则判定为「用户主动关闭」，撤销巡检。 */
        fun scheduleConfirm(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val job = JobInfo.Builder(JOB_CONFIRM, ComponentName(context, AccessibilityWatchdogJob::class.java))
                .setMinimumLatency(CONFIRM_DELAY_MS)
                .build()
            scheduler.schedule(job)
        }

        private fun notifyRecovery(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.watchdog_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            val intent = PendingIntent.getActivity(
                context,
                0,
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification: Notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(context.getString(R.string.watchdog_notify_title))
                .setContentText(context.getString(R.string.watchdog_notify_text))
                .setContentIntent(intent)
                .setAutoCancel(true)
                .build()
            // Android 13+ 未授予 POST_NOTIFICATIONS 时系统静默丢弃，属可接受降级（首页状态检测兜底）
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}
