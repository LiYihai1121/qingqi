package com.qingqi.adskip.l2vpn

/**
 * hosts 格式规则解析（纯 JVM，L2 隔离原型）。
 *
 * 刻意保守：只接受把域名指向本地黑洞地址的条目，任何其他 IP 映射、通配符、
 * 路径、ABP 风格表达式一律拒绝。理由见 DESIGN-ADS-FILTERING.md §3——原型
 * 不做通用内容过滤器，规则必须能被逐条审计；无法解析的规则应整份拒绝并回退
 * 到最后一份有效规则，而不是「尽力解析」后误伤正常域名。
 */
object HostsRuleParser {

    private val LABEL = Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")
    private val SINK_ADDRESSES = setOf("0.0.0.0", "127.0.0.1", "::")

    /** 域名归一化；不合法返回 null。单标签（如 localhost）不算可拦截域名。 */
    fun normalize(raw: String): String? {
        val domain = raw.trim().lowercase().removeSuffix(".")
        if (domain.isEmpty() || domain.length > 253) return null
        val labels = domain.split(".")
        if (labels.size < 2 || labels.any { it.length > 63 || !LABEL.matches(it) }) return null
        return domain
    }

    /** 解析 hosts 文本；任一非法条目抛 [IllegalArgumentException]。 */
    fun parse(input: String): Set<String> {
        val domains = LinkedHashSet<String>()
        for (rawLine in input.lineSequence()) {
            val line = rawLine.substringBefore("#").trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            val address = parts.first()
            require(address in SINK_ADDRESSES) { "不支持的 hosts 地址：$address" }
            require(parts.size > 1) { "hosts 规则缺少域名：$line" }
            for (name in parts.drop(1)) {
                domains += normalize(name) ?: throw IllegalArgumentException("非法域名：$name")
            }
        }
        return domains
    }

    /** 损坏规则的回退入口：解析失败返回 null，由调用方继续使用上一份有效规则。 */
    fun parseOrNull(input: String): Set<String>? = try {
        parse(input)
    } catch (error: IllegalArgumentException) {
        null
    }
}
