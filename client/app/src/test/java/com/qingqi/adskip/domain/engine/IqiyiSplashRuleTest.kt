package com.qingqi.adskip.domain.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

class IqiyiSplashRuleTest {
    private val pkg = "com.qiyi.video"
    private val id = "$pkg:id/unused_res_a"

    // Mi 9 / 爱奇艺 17.10.5：混淆 ID，关闭文字不可点击，直接父容器可点击。
    private fun splash(
        text: String = "关闭",
        viewId: String = id,
        clickable: Boolean = true,
        buttonX: Float = 915f,
        buttonY: Float = 104.5f,
        buttonWidth: Int = 220,
        visible: Boolean = true,
        editable: Boolean = false,
        marker: String = "跳转至第三方应用或详情页面",
        markerVisible: Boolean = true,
        parentText: String? = null,
        closeVisible: Boolean = true,
        closeEditable: Boolean = false,
        closeWidth: Int = 78,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        twistLabel: String = "扭转手机",
        closeDesc: String? = null,
    ): Pair<AdNode, AdNode> {
        val close = FakeAdNode(
            text = text,
            desc = closeDesc,
            viewId = viewId,
            w = closeWidth,
            h = 52,
            cx = buttonX + offsetX,
            cy = buttonY + offsetY,
            isVisible = closeVisible,
            isEditable = closeEditable,
        )
        val button = FakeAdNode(
            text = parentText, viewId = id, isClickable = clickable, isVisible = visible, isEditable = editable,
            w = buttonWidth, h = 99, cx = buttonX + offsetX, cy = buttonY + offsetY, childList = listOf(close),
        )
        val header = FakeAdNode(viewId = id, w = 1080, h = 2221, cx = 540f, cy = 1110.5f, childList = listOf(button))
        val instructions = FakeAdNode(
            childList = listOf(
                FakeAdNode(text = twistLabel, viewId = id),
                FakeAdNode(text = marker, viewId = id, isVisible = markerVisible),
            ),
        )
        val overlay = FakeAdNode(
            viewId = id,
            isClickable = true,
            w = 1080,
            h = 2221,
            cx = 540f + offsetX,
            cy = 1110.5f + offsetY,
            childList = listOf(header, instructions),
        )
        val root =
            FakeAdNode(w = 1080, h = 2221, cx = 540f + offsetX, cy = 1110.5f + offsetY, childList = listOf(overlay))
        return root to close
    }

    @Test
    fun `observed splash close has a safe actionable target`() {
        val (root, close) = splash()
        assertSame(close.parent, IqiyiSplashRule.findTarget(root, pkg))
        assertTrue(SafetyGuard.canClick(close, pkg))
        assertNull(SkipRuleEngine().findTarget(root, RuleSet(listOf("跳过", "关闭广告"), listOf("skip", "jump"))))
    }

    @Test
    fun `other packages do not inherit the close rule`() {
        assertNull(IqiyiSplashRule.findTarget(splash().first, "com.other.video"))
    }

    @Test
    fun `observed flip phone ad variant also matches`() {
        val (root, close) = splash(twistLabel = "翻转手机", marker = "跳转至详情页面或第三方应用")
        assertSame(close.parent, IqiyiSplashRule.findTarget(root, pkg))
    }

    @Test
    fun `ordinary close without the ad landing instruction is ignored`() {
        assertNull(IqiyiSplashRule.findTarget(splash(marker = "普通页面").first, pkg))
        assertNull(IqiyiSplashRule.findTarget(splash(markerVisible = false).first, pkg))
        assertNull(IqiyiSplashRule.findTarget(splash(twistLabel = "普通页面").first, pkg))
    }

    @Test
    fun `only the exact observed close label and resource id match`() {
        assertNull(IqiyiSplashRule.findTarget(splash(text = "关闭自动续费").first, pkg))
        assertNull(IqiyiSplashRule.findTarget(splash(viewId = "$pkg:id/dialog_close").first, pkg))
    }

    @Test
    fun `close must have a visible noneditable directly clickable parent`() {
        assertNull(IqiyiSplashRule.findTarget(splash(clickable = false).first, pkg))
        assertNull(IqiyiSplashRule.findTarget(splash(visible = false).first, pkg))
        assertNull(IqiyiSplashRule.findTarget(splash(editable = true).first, pkg))
    }

    @Test
    fun `large ad surface and close outside top right are ignored`() {
        assertNull(IqiyiSplashRule.findTarget(splash(buttonWidth = 1080).first, pkg))
        assertNull(IqiyiSplashRule.findTarget(splash(buttonX = 100f).first, pkg))
        assertNull(IqiyiSplashRule.findTarget(splash(buttonY = 1600f).first, pkg))
    }

    @Test
    fun `ad instructions in a separate overlay cannot qualify a normal close`() {
        val (normal, _) = splash(marker = "普通页面")
        val (ad, _) = splash(text = "广告")
        val root =
            FakeAdNode(
                w = 1080,
                h = 2221,
                cx = 540f,
                cy = 1110.5f,
                childList = listOf(normal as FakeAdNode, ad as FakeAdNode),
            )
        assertNull(IqiyiSplashRule.findTarget(root, pkg))
    }

    @Test
    fun `existing sensitive label guard still rejects a matched close`() {
        val (root, close) = splash(parentText = "确认支付")
        val target = IqiyiSplashRule.findTarget(root, pkg)
        assertSame(close.parent, target)
        assertFalse(SafetyGuard.canClick(requireNotNull(target), pkg))
    }

    @Test
    fun `hidden editable empty or oversized labels are ignored`() {
        assertNull(IqiyiSplashRule.findTarget(splash(closeVisible = false).first, pkg))
        assertNull(IqiyiSplashRule.findTarget(splash(closeEditable = true).first, pkg))
        assertNull(IqiyiSplashRule.findTarget(splash(closeWidth = 0).first, pkg))
        assertNull(IqiyiSplashRule.findTarget(splash(closeWidth = 800).first, pkg))
    }

    @Test
    fun `clicking the parent cannot bypass a sensitive close description`() {
        assertNull(IqiyiSplashRule.findTarget(splash(closeDesc = "确认支付").first, pkg))
    }

    @Test
    fun `positions are relative to the active window`() {
        val (root, close) = splash(offsetX = 300f, offsetY = 200f)
        assertSame(close.parent, IqiyiSplashRule.findTarget(root, pkg))
    }

    @Test
    fun `captured iqiyi splash tree resolves to its clickable close container`() {
        val document = javaClass.getResourceAsStream("/iqiyi/splash-17.10.5.xml").use {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
        }
        fun node(element: Element): FakeAdNode {
            val bounds = Regex("-?\\d+").findAll(element.getAttribute("bounds")).map { it.value.toInt() }.toList()
            val children = (0 until element.childNodes.length).mapNotNull { element.childNodes.item(it) as? Element }
            return FakeAdNode(
                text = element.getAttribute("text"), desc = element.getAttribute("content-desc"),
                viewId = element.getAttribute("resource-id"), isClickable = element.getAttribute("clickable") == "true",
                w = bounds[2] - bounds[0], h = bounds[3] - bounds[1],
                cx = (bounds[0] + bounds[2]) / 2f, cy = (bounds[1] + bounds[3]) / 2f,
                childList = children.map(::node),
            )
        }
        val root = node(document.documentElement)
        val target = requireNotNull(IqiyiSplashRule.findTarget(root, pkg))
        assertTrue("必须点击容器，不点击会假报成功的文字节点", target.isClickable)
        assertTrue(target.children().any { it.text == "关闭" })
        assertTrue(SafetyGuard.canClick(target, pkg))
    }
}
