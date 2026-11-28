package com.qingqi.adskip.l3shake

import kotlin.math.abs
import kotlin.math.sqrt

/** 一次传感器采样。时间由调用方给出，保证判定逻辑可在纯 JVM 下复现。 */
data class ShakeSample(val atMs: Long, val x: Float, val y: Float, val z: Float)

/**
 * 「摇一摇」候选检测器（纯 JVM 算法骨架，L3 PoC）。
 *
 * 这里只把加速度采样判定为一次候选摇动，**不读取、不替换、不注入任何
 * 第三方 App 的传感器回调**。真实交互拦截需要 Root 框架且必须通过
 * Phase 0 的单独评审（ADR-ADS-002 状态：Deferred），本模块不包含该能力。
 *
 * 阈值口径来自 DESIGN-ADS-FILTERING.md §5：可解释、可调、可一键关闭。
 */
class ShakeDetector(
    private val accelerationThreshold: Float = DEFAULT_THRESHOLD,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val minPeaks: Int = DEFAULT_MIN_PEAKS,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {
    private val peaks = ArrayDeque<Long>()
    private var lastTriggerAtMs = Long.MIN_VALUE

    /** 返回 true 表示窗口内出现了一次足够强的往复运动。 */
    fun onSample(sample: ShakeSample): Boolean {
        val magnitude = sqrt(sample.x * sample.x + sample.y * sample.y + sample.z * sample.z)
        if (abs(magnitude - GRAVITY) < accelerationThreshold) return false

        peaks.addLast(sample.atMs)
        while (peaks.isNotEmpty() && sample.atMs - peaks.first() > windowMs) {
            peaks.removeFirst()
        }
        if (peaks.size < minPeaks) return false
        if (lastTriggerAtMs != Long.MIN_VALUE && sample.atMs - lastTriggerAtMs < cooldownMs) return false

        lastTriggerAtMs = sample.atMs
        peaks.clear()
        return true
    }

    fun reset() {
        peaks.clear()
        lastTriggerAtMs = Long.MIN_VALUE
    }

    private companion object {
        const val GRAVITY = 9.81f
        const val DEFAULT_THRESHOLD = 12f
        const val DEFAULT_WINDOW_MS = 800L
        const val DEFAULT_MIN_PEAKS = 2
        const val DEFAULT_COOLDOWN_MS = 1200L
    }
}
