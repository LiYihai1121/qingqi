package com.qingqi.adskip.domain.engine

/**
 * 爱奇艺 17.10.5 开屏适配（#217，Mi 9 真机快照）。
 *
 * 按钮只有「关闭」，ID 被统一混淆为 unused_res_a，不能加入全局关键词。
 * 仅接受右上角的小型可点击父容器，且近祖先广告层同时包含两条实测引导文案。
 * ID/结构/文案变化时保守不匹配；返回后仍必须经过 SafetyGuard 和窗口归属复核。
 */
object IqiyiSplashRule {
    private const val PACKAGE = "com.qiyi.video"
    private const val VIEW_ID = "$PACKAGE:id/unused_res_a"

    fun findTarget(root: AdNode, pkg: String): AdNode? {
        if (pkg != PACKAGE || !usable(root)) return null
        val candidates = visibleNodes(root, 500)
            .filter { it.viewId == VIEW_ID && it.text?.trim() == "关闭" && it.desc.isNullOrBlank() }
            .take(8)
        for (close in candidates) {
            val button = close.parent ?: continue
            if (!usable(close) || !usable(button) || !button.isClickable || button.viewId != VIEW_ID) continue
            if (!topRightButton(button, root) || !contains(button, close)) continue
            // 实测结构：关闭文字 → 小型 LinearLayout → FrameLayout → 广告 RelativeLayout。
            // 不向更高祖先搜寻，防止另一弹窗借用页面上广告的标志。
            val overlay = button.parent?.parent ?: continue
            if (!usable(overlay) || overlay.viewId != VIEW_ID || !overlay.isClickable) continue
            if (overlay.boundsWidth() < root.boundsWidth() * 0.8f ||
                overlay.boundsHeight() < root.boundsHeight() * 0.8f || !contains(overlay, button)
            ) {
                continue
            }
            val labels = visibleNodes(overlay, 80)
                .filter { usable(it) && it.viewId == VIEW_ID }
                .mapNotNull { it.text?.trim() }
                .toSet()
            // 返回小型按钮容器作为手势落点；文字及父容器的 ACTION_CLICK 均可能假报成功。
            val twistAd = "扭转手机" in labels && "跳转至第三方应用或详情页面" in labels
            val flipAd = "翻转手机" in labels && "跳转至详情页面或第三方应用" in labels
            if (twistAd || flipAd) return button
        }
        return null
    }

    private fun usable(node: AdNode): Boolean =
        node.isVisible && !node.isEditable && node.boundsWidth() > 0 && node.boundsHeight() > 0

    private fun topRightButton(button: AdNode, root: AdNode): Boolean {
        val width = root.boundsWidth()
        val height = root.boundsHeight()
        return button.boundsWidth() <= width * 0.3f && button.boundsHeight() <= height * 0.1f &&
            left(button) >= left(root) + width * 0.65f && top(button) >= top(root) &&
            right(button) <= right(root) && bottom(button) <= top(root) + height * 0.2f
    }

    private fun contains(outer: AdNode, inner: AdNode): Boolean =
        left(inner) >= left(outer) && right(inner) <= right(outer) &&
            top(inner) >= top(outer) && bottom(inner) <= bottom(outer)

    private fun left(node: AdNode) = node.centerX() - node.boundsWidth() / 2f
    private fun right(node: AdNode) = node.centerX() + node.boundsWidth() / 2f
    private fun top(node: AdNode) = node.centerY() - node.boundsHeight() / 2f
    private fun bottom(node: AdNode) = node.centerY() + node.boundsHeight() / 2f

    private fun visibleNodes(root: AdNode, limit: Int): Sequence<AdNode> = sequence {
        val queue = ArrayDeque<AdNode>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < limit) {
            val node = queue.removeFirst()
            visited++
            if (!node.isVisible || node.isEditable) continue
            yield(node)
            node.children().take((limit - visited - queue.size).coerceAtLeast(0)).forEach(queue::addLast)
        }
    }
}
