package com.qingqi.adskip

import android.app.Application
import android.content.Context
import com.qingqi.adskip.core.common.LogRing
import com.qingqi.adskip.data.local.Prefs
import com.qingqi.adskip.data.sync.SyncJobService
import com.qingqi.adskip.di.AppContainer

/**
 * Application 入口：初始化 [AppContainer] 手动 DI 容器（组合根）。
 *
 * UI / Service 层通过 `(context as AdSkipApp).container` 获取依赖。
 * 组合根同时负责进程级兜底（如周期同步 Job 重注册），
 * 使 ui 层不再直接依赖 data.Prefs / sync.SyncJobService。
 */
class AdSkipApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, this)
        // 启动容错（P0-1，架构审查项）：存储层故障不得炸掉应用。
        // 真机事故（Mi 9 / Android 11，#124 修复前的 MasterKey 异常）证明
        // Application.onCreate 裸调 Prefs 会把任何 AndroidKeyStore/SP 异常
        // 升级为「应用完全无法启动」。降级策略：同步 Job 跳过本次注册
        // （下次启动或 SyncJobService 自身触发时自然重试），功能降级但
        // 不阻断启动；错误经 LogRing 留痕，用户可在「记录」页导出可见。
        val autoSync = runCatching { Prefs.isAutoSyncEnabled(this) }
            .onFailure {
                LogRing.e(
                    "App",
                    "Prefs unreadable at startup; auto-sync scheduling skipped: ${it.message}",
                )
            }
            .getOrDefault(false)
        if (autoSync) SyncJobService.schedule(this)
        LogRing.d("App", "AdSkipApp initialized")
    }

    companion object {
        fun get(context: Context): AppContainer = (context.applicationContext as AdSkipApp).container
    }
}
