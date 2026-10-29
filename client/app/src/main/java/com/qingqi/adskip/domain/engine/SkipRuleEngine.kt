package com.qingqi.adskip.domain.engine

import com.qingqi.adskip.domain.engine.selector.SelectorMatcher
import java.util.Locale

/**
 * 命中通道：目标节点由哪条规则通道命中。
 *
 * 护栏据此决定「无可点击路径」时的兜底策略（见 [SafetyGuard.canClick]）：
 * 选择器通道逐条人工编写、快照驱动、可审计，允许坐标手势兜底；
 * 文本/ViewID 通道命中时维持原契约不放行。
 */
enum class MatchChannel { SELECTOR, TEXT, VIEW_ID }

/** 引擎命中结果：目标节点 + 命中通道。 */
data class MatchResult(val target: AdNode, val channel: MatchChannel)

/**
 * 跳过规则引擎（纯匹配逻辑）。
 *
 * v2.2 改造：依赖 [AdNode] 抽象而非 [android.view.accessibility.AccessibilityNodeInfo]，
 * 使引擎可跑 JVM 单测（注入 [FakeAdNode] 即可）。
 *
 * 职责单一：给定节点树与规则集，找出应点击的目标节点。
 * 与「如何点击」（AccessibilityService 动作/手势）完全解耦。
 *
 * 匹配通道（每节点按序测试，同 DFS 序内先命中先返回）：
 *  1. 选择器 —— 类 CSS 子集的上下文关系匹配（engine/selector，右到左求值）
 *  2. 文本 —— text / contentDescription 包含关键词，限短文本防误点
 *  3. ViewID —— 控件资源 ID 包含规则串，覆盖纯图片按钮
 */
class SkipRuleEngine(
    private val maxNodes: Int = 500,
    private val maxTextLen: Int = 12,
    private val minViewIdLen: Int = 3,
) {

    /** 深度优先遍历，返回第一个命中规则的目标节点及其通道；未命中返回 null。 */
    fun findTarget(root: AdNode, rules: RuleSet): MatchResult? {
        if (rules.isEmpty || rules.disabled) return null
        val stack = ArrayDeque<AdNode>()
        stack.addLast(root)
        var steps = 0
        while (stack.isNotEmpty() && steps < maxNodes) {
            val node = stack.removeLast()
            steps++
            val channel = channelOf(node, rules)
            if (channel != null) return MatchResult(node, channel)
            for (child in node.children()) {
                stack.addLast(child)
            }
        }
        return null
    }

    /** 判断单个节点是否命中规则。 */
    fun matches(node: AdNode, rules: RuleSet): Boolean = channelOf(node, rules) != null

    /** 求值单节点的命中通道：按 选择器 → 文本 → ViewID 顺序，未命中返回 null。 */
    private fun channelOf(node: AdNode, rules: RuleSet): MatchChannel? {
        if (!node.isVisible) return null
        if (node.isEditable) return null

        // 通道一：选择器规则（类 CSS 子集；解析已在上游完成，热路径零解析）
        for (selector in rules.selectors) {
            if (SelectorMatcher.matches(selector, node)) return MatchChannel.SELECTOR
        }

        // 通道二：文本 / 内容描述
        val text = node.text?.trim()
        val desc = node.desc?.trim()
        for (candidate in listOf(text, desc)) {
            if (candidate.isNullOrEmpty() || candidate.length > maxTextLen) continue
            val lower = candidate.lowercase(Locale.ROOT)
            for (kw in rules.keywords) {
                if (candidate.contains(kw) || lower.contains(kw.lowercase(Locale.ROOT))) return MatchChannel.TEXT
            }
        }

        // 通道三：ViewID（如 com.example:id/skip_view 命中 "skip"）
        val id = node.viewId
        if (id != null) {
            val lower = id.lowercase(Locale.ROOT)
            for (rule in rules.viewIds) {
                if (rule.length >= minViewIdLen && lower.contains(rule.lowercase(Locale.ROOT))) {
                    return MatchChannel.VIEW_ID
                }
            }
        }
        return null
    }
}
