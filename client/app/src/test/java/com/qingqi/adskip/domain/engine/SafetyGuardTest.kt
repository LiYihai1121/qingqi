package com.qingqi.adskip.domain.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SafetyGuard JVM 单测：验证黑名单/合法性护栏。
 */
class SafetyGuardTest {

    /**
     * 断言用的是**发布包名**（与 `applicationId` 一致），不是带 `.debug` 后缀的
     * 并行安装包：2026-10-11 的复测曾用不同包名绕过护栏，把「已修复」判成了假阳性。
     */
    @Test
    fun `self package blocked by default`() {
        val node = FakeAdNode.node(text = "跳过")
        assertFalse(SafetyGuard.canClick(node, "com.qingqi.adskip"))
    }

    @Test
    fun `self package allowed only when caller opts in`() {
        val node = FakeAdNode.node(text = "跳过", clickable = true)
        assertTrue(SafetyGuard.canClick(node, "com.qingqi.adskip", allowSelfPackage = true))
    }

    /** 放行只对自身包生效，不会顺手放过支付/系统等其它底线。 */
    @Test
    fun `self test opt in does not weaken other guards`() {
        assertFalse(SafetyGuard.canClick(FakeAdNode.node(text = "确认支付"), "com.eg.android.AlipayGphone", true))
        assertFalse(SafetyGuard.canClick(FakeAdNode.node(text = "跳过"), "com.android.settings", true))
        assertFalse(SafetyGuard.canClick(FakeAdNode.node(text = "跳过", visible = false), "com.qingqi.adskip", true))
    }

    @Test
    fun `deny word in text blocked`() {
        val node = FakeAdNode.node(text = "确认支付")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `deny word in desc blocked`() {
        val node = FakeAdNode.node(text = "跳过", desc = "点击授权")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `deny word 付款 blocked`() {
        val node = FakeAdNode.node(text = "立即付款")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `deny word 同意 blocked`() {
        val node = FakeAdNode.node(text = "同意并继续")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `deny word 登录 blocked`() {
        val node = FakeAdNode.node(text = "登录")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `deny word 购买 blocked`() {
        val node = FakeAdNode.node(text = "购买会员")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `deny word 安装 blocked`() {
        val node = FakeAdNode.node(text = "安装应用")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `invisible node blocked`() {
        val node = FakeAdNode.node(text = "跳过", visible = false)
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `zero width bounds blocked`() {
        val node = FakeAdNode.node(text = "跳过", width = 0)
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `zero height bounds blocked`() {
        val node = FakeAdNode.node(text = "跳过", height = 0)
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `valid node allowed`() {
        val node = FakeAdNode.node(text = "跳过", clickable = true, width = 100, height = 50)
        assertTrue(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `skip text not blocked`() {
        val node = FakeAdNode.node(text = "跳过广告", clickable = true)
        assertTrue(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `english skip not blocked`() {
        val node = FakeAdNode.node(text = "Skip", clickable = true)
        assertTrue(SafetyGuard.canClick(node, "com.example.app"))
    }

    // ---------- 可点击路径护栏（DESIGN-ARCH-REVIEW §6.3） ----------

    /**
     * 核心契约：纯文本行（自身不可点击、父链也无任何可点击祖先）不得通过护栏。
     * 2026-10-11 实测的误跳即此类——此前会落入坐标手势兜底把任意 UI 点掉。
     */
    @Test
    fun `plain text row without click path blocked`() {
        val node = FakeAdNode.node(text = "跳过")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    /**
     * 不回归：命中关键词的文本节点本身不可点击、但容器可点击时（真实跳过按钮
     * 的常见形态），父链点击路径有效，护栏放行——与 clickNode 的点击链一致。
     */
    @Test
    fun `text node with clickable ancestor allowed`() {
        val button = FakeAdNode.node(text = "跳过", clickable = false)
        val container = FakeAdNode.node(clickable = true, children = listOf(button))
        assertTrue(SafetyGuard.canClick(container.children().first(), "com.example.app"))
    }

    /** 可点击祖先在 3 层内：放行（clickableParent 自检起共 4 次判定的上限内）。 */
    @Test
    fun `clickable ancestor within three levels allowed`() {
        val target = FakeAdNode.node(text = "跳过")
        var current: FakeAdNode = target
        repeat(2) { current = FakeAdNode.node(children = listOf(current)) }
        // 可点击祖先距 target 第 3 层
        FakeAdNode.node(clickable = true, children = listOf(current))
        assertTrue(SafetyGuard.canClick(target, "com.example.app"))
    }

    /** 可点击祖先距 target 第 4 层起不再计入（与 FrameworkAdNode.clickableParent 的 4 次判定上限一致）。 */
    @Test
    fun `clickable ancestor beyond three levels not counted`() {
        val target = FakeAdNode.node(text = "跳过")
        var current: FakeAdNode = target
        repeat(3) { current = FakeAdNode.node(children = listOf(current)) }
        // 可点击祖先距 target 第 4 层，超出上限 → 视为无点击路径
        FakeAdNode.node(clickable = true, children = listOf(current))
        assertFalse(SafetyGuard.canClick(target, "com.example.app"))
    }

    @Test
    fun `english deny word blocked`() {
        val node = FakeAdNode.node(text = "Install Now")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `english deny word case insensitive blocked`() {
        val node = FakeAdNode.node(text = "ALLOW")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `sensitive system packages blocked`() {
        val node = FakeAdNode.node(text = "跳过")
        assertFalse(SafetyGuard.canClick(node, "com.android.permissioncontroller"))
        assertFalse(SafetyGuard.canClick(node, "com.android.packageinstaller"))
        assertFalse(SafetyGuard.canClick(node, "com.android.settings"))
    }

    @Test
    fun `deny word in parent chain blocked`() {
        // 无标签图标按钮 + 敏感文案在父容器：只看目标自身会被绕过
        val button = FakeAdNode.node()
        val dialog = FakeAdNode.node(text = "要允许安装此应用吗？", children = listOf(button))
        val target = dialog.children().first()
        assertFalse(SafetyGuard.canClick(target, "com.example.app"))
    }

    @Test
    fun `unlabeled button without sensitive ancestors allowed`() {
        val button = FakeAdNode.node(clickable = true)
        val card = FakeAdNode.node(text = "限时秒杀", children = listOf(button))
        assertTrue(SafetyGuard.canClick(card.children().first(), "com.example.app"))
    }
}
