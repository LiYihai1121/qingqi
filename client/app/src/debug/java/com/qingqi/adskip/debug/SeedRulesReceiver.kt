package com.qingqi.adskip.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.qingqi.adskip.AdSkipApp
import com.qingqi.adskip.R
import com.qingqi.adskip.data.local.CloudPkgRule
import com.qingqi.adskip.domain.engine.RuleSet

/**
 * 仅 debug 构建打包的规则种子接收器（真机验证辅助，不进 release）。
 *
 * 背景：云端规则下发受签名密钥门禁（客户端验签失败即拒绝载入），真机验证
 * gestureFallback 时若暂时拿不到密钥，可经 adb 注入与云端完全同源的规则
 * 载荷——复用 [com.qingqi.adskip.data.repository.RulesRepository.applyCloudRules]
 * 的原子落地链路，运行时行为与云端下发一致（RuleSet 构建路径相同，仅跳过
 * SyncClient 的网络与验签环节，该环节由 SyncClientTest 单测覆盖）。
 *
 * 触发：adb shell am broadcast -n com.qingqi.adskip/.debug.SeedRulesReceiver
 */
class SeedRulesReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AdSkipApp
        val container = app.container
        val ok = container.rulesRepo.applyCloudRules(
            keywords = container.app.resources.getStringArray(R.array.default_keywords).toList(),
            viewIds = container.app.resources.getStringArray(R.array.default_view_ids).toList(),
            pkgRules = mapOf(
                "com.youku.phone" to CloudPkgRule(gestureFallback = true),
            ),
            schemaVersion = RuleSet.SCHEMA_VERSION,
        )
        Toast.makeText(
            context,
            if (ok) "debug seed: youku gestureFallback=true 已落地" else "debug seed 被拒（schema 过低）",
            Toast.LENGTH_LONG,
        ).show()
    }
}
