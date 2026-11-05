package com.qingqi.adskip.l3shake

enum class ShakeGuardState {
    /** 独立可选组件未安装（当前状态）。 */
    NOT_INSTALLED,
    DISABLED,
    ARMED,
    ACTIVE,
}

enum class ShakeGuardAction {
    /** 不干预：默认路径。 */
    PASS_THROUGH,
    SUPPRESS_SHAKE,
}

/** 单独批准证据：必须能追溯到决策记录与日期，空证据不算批准。 */
data class ShakeApproval(val granted: Boolean, val decisionRef: String, val decidedAt: String)

/**
 * L3 能力契约（ADR-ADS-002：**Deferred / 未批准**）。
 *
 * Phase 0 当前决定是「L3 暂缓；除独立可选概念验证外不实现」。
 * 因此本模块只有状态机与可单测的判定骨架：
 * - 不安装、不启用、不注册任何 Android 组件；
 * - 不与支付、登录、授权、反作弊、安全校验或用户奖励流程发生关系；
 * - 任何动作都只返回「干预 / 不干预」建议，不向第三方进程写入任何东西。
 */
object ShakeGuardContract {

    const val DEFAULT_ENABLED = false
    const val REQUIRES_SEPARATE_APPROVAL = true

    private val PROTECTED_TOKENS = setOf(
        "bank", "pay", "wallet", "login", "auth", "oauth",
        "reward", "bonus", "security", "anticheat", "verify",
    )

    /** 保护包名按语义片段匹配，避免依赖一份永远滞后于 App 更新的白名单。 */
    fun isProtected(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return PROTECTED_TOKENS.any { lower.contains(it) }
    }
}

class ShakeGuard(private val detector: ShakeDetector = ShakeDetector()) {

    private var state = ShakeGuardState.NOT_INSTALLED

    fun state(): ShakeGuardState = state

    /** 只有带可追溯决策证据的单独批准才能把组件从 NOT_INSTALLED 推进到 ARMED。 */
    fun enable(approval: ShakeApproval): Boolean {
        if (!ShakeGuardContract.REQUIRES_SEPARATE_APPROVAL) return false
        if (!approval.granted || approval.decisionRef.isBlank() || approval.decidedAt.isBlank()) return false
        state = ShakeGuardState.ARMED
        return true
    }

    /** 用户在独立组件里显式开启；未经批准不得进入 ACTIVE。 */
    fun activate(): Boolean {
        if (state != ShakeGuardState.ARMED) return false
        state = ShakeGuardState.ACTIVE
        return true
    }

    /** 一键撤销：任何时刻都允许，且立即清空判定状态。 */
    fun disable() {
        state = ShakeGuardState.DISABLED
        detector.reset()
    }

    fun onCandidate(pkg: String, sample: ShakeSample): ShakeGuardAction {
        if (state != ShakeGuardState.ACTIVE) return ShakeGuardAction.PASS_THROUGH
        if (ShakeGuardContract.isProtected(pkg)) return ShakeGuardAction.PASS_THROUGH
        return if (detector.onSample(sample)) {
            ShakeGuardAction.SUPPRESS_SHAKE
        } else {
            ShakeGuardAction.PASS_THROUGH
        }
    }
}
