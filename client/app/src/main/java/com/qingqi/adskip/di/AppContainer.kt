package com.qingqi.adskip.di

import android.app.Application
import android.content.Context
import com.qingqi.adskip.core.common.AppExecutors
import com.qingqi.adskip.core.common.Clock
import com.qingqi.adskip.core.common.SystemClockAdapter
import com.qingqi.adskip.data.local.Prefs
import com.qingqi.adskip.data.local.PrefsRulesStore
import com.qingqi.adskip.data.local.PrefsStatsStore
import com.qingqi.adskip.data.remote.SyncClient
import com.qingqi.adskip.data.repository.RulesRepository
import com.qingqi.adskip.data.repository.SettingsRepository
import com.qingqi.adskip.data.repository.StatsRepository

/**
 * 手动 DI 容器：收口所有依赖，不引入任何第三方 DI 框架。
 *
 * UI 层经 `(application as AdSkipApp).container` 取依赖；
 * Service 层同理。ViewModel 经容器取仓库并暴露 StateFlow。
 */
class AppContainer(val app: Application, context: Context) {
    val clock: Clock = SystemClockAdapter
    val executors = AppExecutors()
    val prefs = Prefs // object 单例，不需构造

    // 规则的落盘端口显式注入：RulesRepository 只负责合并/校验/缓存失效，
    // 存储实现可替换（单测用假实现）；云端落地经 replaceAllRules 单次原子提交
    val rulesRepo = RulesRepository(PrefsRulesStore(context.applicationContext))

    // 统计的落盘端口显式注入：StatsRepository 只负责累加，存储实现可替换（单测用假实现）
    val statsRepo = StatsRepository(PrefsStatsStore(context.applicationContext), executors.io)
    val settingsRepo = SettingsRepository(context.applicationContext, rulesRepo)
    val syncClient = SyncClient // object 单例
}
