package com.qingqi.adskip

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
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

    /** 网络恢复即冲刷上报队列（A1 触发点之二）；进程生命周期内常驻 */
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, this)
        registerReportFlushOnNetworkAvailable()
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

    /**
     * 监听默认网络可用事件：飞行模式/断网期间积压的上报事件在网络恢复后
     * 立即补报，不必等 12 小时 Job 或下一次跳过。registerDefaultNetworkCallback
     * 不需要任何权限；注册失败（极少数 ROM 限制）静默降级——
     * JobService 与下次跳过仍会触发 flush。
     */
    private fun registerReportFlushOnNetworkAvailable() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val url = Prefs.getServerUrl(this@AdSkipApp)
                    if (url.isBlank()) return
                    container.syncClient.flushReportQueue(
                        url,
                        Prefs.getDeviceId(this@AdSkipApp),
                        Prefs.getCertPins(this@AdSkipApp),
                        container.reportQueue,
                    )
                }
            }
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: Exception) {
            LogRing.w("App", "network callback register failed: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        fun get(context: Context): AppContainer = (context.applicationContext as AdSkipApp).container
    }
}
