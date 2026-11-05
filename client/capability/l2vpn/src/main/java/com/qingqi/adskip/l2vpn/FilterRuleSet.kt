package com.qingqi.adskip.l2vpn

/**
 * L2 规则集与来源元数据（纯 JVM）。
 *
 * DESIGN-ADS-FILTERING.md §3 要求「规则采用来源、许可证、更新时间、版本、校验值
 * 和变更记录可追溯的格式」。因此规则集不允许只带一个域名列表：缺任一元数据
 * 即构造失败，避免原型阶段就形成一份来源不明、无法复核许可证的黑名单。
 *
 * 本对象**不含任何用户 DNS 查询数据**，只保存静态规则。
 */
data class FilterRuleSourceMeta(
    val source: String,
    val license: String,
    val version: String,
    val updatedAt: String,
    val sha256: String,
)

enum class FilterDecision {
    ALLOW,
    BLOCK,
}

data class FilterRuleSet(val meta: FilterRuleSourceMeta, val blockedDomains: Set<String>, val allowlist: Set<String>) {
    init {
        require(meta.source.isNotBlank()) { "规则来源不可为空" }
        require(meta.license.isNotBlank()) { "规则许可证不可为空" }
        require(meta.version.isNotBlank()) { "规则版本不可为空" }
        require(meta.sha256.isNotBlank()) { "规则校验值不可为空" }
    }

    /**
     * 白名单优先于拦截规则：用户例外必须能一键恢复被误拦的域名。
     * 未命中任何规则一律放行（fail-open），DNS 过滤不承担「默认阻断」语义。
     */
    fun decide(domain: String): FilterDecision {
        val name = HostsRuleParser.normalize(domain) ?: return FilterDecision.ALLOW
        if (allowlist.any { matches(name, it) }) return FilterDecision.ALLOW
        if (blockedDomains.any { matches(name, it) }) return FilterDecision.BLOCK
        return FilterDecision.ALLOW
    }

    companion object {
        /** 规则命中自身或其任意子域；不按字符串包含匹配，避免拦截 notexample.com。 */
        fun matches(domain: String, rule: String): Boolean = domain == rule || domain.endsWith(".$rule")
    }
}
