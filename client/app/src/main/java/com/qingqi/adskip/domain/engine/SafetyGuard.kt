package com.qingqi.adskip.domain.engine

/**
 * 安全护栏：引擎找到目标后、执行点击前的硬底线。
 *
 * 无论云端规则怎么被污染，客户端都有硬底线：
 * - 防自触发死循环（默认不点击本应用自身的节点）；
 *   唯一例外是「模拟开屏广告自测」——浮层就在本应用内，此时由调用方显式传
 *   `allowSelfPackage = true` 放行（见 [canClick]）。**默认关闭**，避免任何
 *   调用方在无意间把护栏关掉。
 * - 关键词黑名单硬编码，云规则不可覆盖（防止污染规则误触敏感按钮）；
 *   英文一律小写后子串匹配，且覆盖**目标节点自身 + 父链（最多 [MAX_LABEL_DEPTH] 层）**：
 *   只看目标自身文案会被「无标签图标按钮」绕过——危险对话框里的确认按钮
 *   常常只有图标，敏感文案挂在其父容器上
 * - 包级黑名单硬编码：支付/银行/数字钥匙类应用整包不进引擎
 *   （关键词黑名单只挡按钮文案，挡不住「换一个不含黑名单词的文案」
 *   在支付页面里点的场景——那一类场景的代价是资金损失）
 * - 敏感系统界面整包拒绝（授权/安装/设置类界面不参与自动点击）
 * - 合法性校验（必须可见、面积 > 0）
 * - 可点击路径校验（自身可点击，或沿父链存在可点击祖先）——纯文本行不再被
 *   坐标手势兜底误点（2026-10-11 实测：设置页服务行被点落库即此类）
 */
object SafetyGuard {

    // 硬编码黑名单，云规则不可覆盖（英文一律小写后子串匹配）
    private val DENY_WORDS = listOf(
        // 中文敏感词
        "支付", "付款", "确认", "同意", "购买", "下单",
        "授权", "登录", "免密", "开通", "安装", "下载",
        "允许", "确定", "继续", "删除", "卸载", "清除", "重置", "格式化", "激活",
        // 英文敏感词（配合 lowercase 匹配）
        "pay", "purchase", "buy", "confirm", "agree", "allow", "grant",
        "install", "uninstall", "delete", "remove", "erase", "reset", "format",
        "login", "log in", "sign in", "password", "trust", "enable", "continue",
    )

    /**
     * 包级硬编码黑名单（精确包名）。
     *
     * 覆盖主流支付/银行/数字钥匙宿主：这些页面里的"跳过/继续"按钮
     * 出现在确认环节时会直接造成资金后果，宁可不跳过也不误触。
     */
    private val DENY_PACKAGES = listOf(
        "com.eg.android.AlipayGphone", // 支付宝
        "com.tencent.mm", // 微信（收付款/支付确认均在其中）
        "com.unionpay", // 云闪付
        "com.unionpay.tsmservice", // 云闪付卡服务
        "com.chinamworld.main", // 中国工商银行
        "com.chinamworld.lean", // 中国工商银行（旧版）
        "cmb.pb", // 招商银行
        "com.chinamworld.bocmbci", // 中国银行
        "com.android.bankabc", // 中国农业银行
        "com.icbc", // 工商银行（旧版）
        "com.baidu.wallet", // 度小满钱包
        "com.jd.payment", // 京东支付
    )

    /**
     * 包级黑名单的保守关键词标记。
     *
     * 只匹配包名**分段前缀**（以 `.` 或串首切分后的整段相等），不匹配
     * 任意子串——否则 `com.example.payment.demo` 这类正常应用会被误伤，
     * 而 `com.foo.pay` 这种与 `pay` 整段同名的包名才会命中。
     */
    private val DENY_PACKAGE_SEGMENTS = listOf(
        "alipay",
        "wallet",
        "bankabc",
        "unionpay",
        "purse",
    )

    /**
     * 整包拒绝清单：授权/安装/系统设置类界面的任何点击都可能是高危操作，
     * 与规则内容无关一律不点。云规则不可覆盖。
     */
    private val DENY_SYSTEM_PACKAGES = setOf(
        "android",
        "com.android.settings",
        "com.android.systemui",
        "com.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.packageinstaller",
        "com.google.android.permissioncontroller",
    )

    private const val SELF_PKG = "com.qingqi.adskip"

    /** 自身 + 三层祖先：必须覆盖 clickableParent 可能返回的全部点击容器。 */
    private const val MAX_LABEL_DEPTH = 4

    /**
     * 引擎找到目标后、执行点击前必须通过此检查。
     * @param node 引擎找到的目标节点
     * @param pkg 当前应用包名
     * @param allowSelfPackage 仅「模拟开屏广告自测」传 true：浮层在本应用内，
     *   自测态不存在「点自己触发自己」的回环，故放行自身包。默认 false。
     * @return true=可以点击, false=被安全护栏拦截
     */
    fun canClick(node: AdNode, pkg: String, allowSelfPackage: Boolean = false): Boolean {
        // 防自触发死循环
        if (pkg == SELF_PKG && !allowSelfPackage) return false

        // 包级硬底线：支付/银行类应用不进引擎（先于关键词检查）
        if (isPackageDenied(pkg)) return false

        // 敏感系统界面整包拒绝
        if (pkg in DENY_SYSTEM_PACKAGES) return false

        // 合法性校验：必须可见且面积 > 0
        if (!node.isVisible) return false
        if (node.boundsWidth() <= 0 || node.boundsHeight() <= 0) return false

        // 可点击路径校验：与 clickNode 的点击链一致——节点自身可点击，或沿父链
        // （最多 4 层）存在可点击祖先。纯文本行（自身不可点击且无任何可点击
        // 祖先）此前会落入坐标手势兜底、把任意位置的 UI 点掉；广告跳过按钮
        // 均为可点击节点或其容器可点击，正常跳过路径不受影响。
        val clickTarget = if (node.isClickable) node else node.clickableParent() ?: return false
        if (!clickTarget.isVisible || clickTarget.isEditable) return false
        if (clickTarget.boundsWidth() <= 0 || clickTarget.boundsHeight() <= 0) return false

        // 黑名单检查：目标自身 + 父链文本，任一层命中即拦
        var current: AdNode? = node
        var depth = 0
        while (current != null && depth < MAX_LABEL_DEPTH) {
            val label = ((current.text.orEmpty()) + (current.desc.orEmpty())).lowercase()
            if (DENY_WORDS.any { label.contains(it) }) return false
            current = current.parent
            depth++
        }
        return true
    }

    /**
     * 包级黑名单判定：精确名单命中，或包名任一**整段**等于保守关键词。
     * 服务在匹配入口调用，命中即整包跳过，不进入任何通道。
     */
    fun isPackageDenied(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        if (pkg in DENY_PACKAGES) return true
        val segments = pkg.split('.')
        return segments.any { it.lowercase() in DENY_PACKAGE_SEGMENTS }
    }
}
