package com.qingqi.adskip.l2vpn

/**
 * 服务端 `GET /api/v1/filter-rules` 解析后的载荷。
 *
 * 本模块刻意**不引入 JSON 解析库或 HTTP 客户端**：Phase 0 未批准前不新增第三方依赖，
 * 且能力层不被 :app 依赖，无法也不应该自己发起网络请求。由具备 JSON 能力的集成层
 * 反序列化后填充本对象，再交给 [FilterRuleSourceVerifier] 校验。
 */
data class FilterRuleSourcePayload(
    val schemaVersion: Int,
    val enabledByDefault: Boolean,
    val phase0Approved: Boolean,
    val prototypeOnly: Boolean,
    val source: String,
    val license: String,
    val version: String,
    val updatedAt: String,
    val sha256: String,
    val domains: List<String>,
    val allowlist: List<String>,
    /** 参与服务端规范化摘要，集成层不得省略，否则客户端复算必然不匹配。 */
    val note: String,
)

/**
 * 规则源客户端校验（纯 JVM）。
 *
 * 补齐原型文档第 9.4 项的「校验与注入链路」：服务端只负责下发，客户端必须独立
 * 复核来源、许可证、校验和**内容**、默认关闭状态与 Phase 0 标记，才能把规则交给数据面。
 * 摘要由 [FilterRuleDigest] 复算，只检查 sha256 的格式不算复核完整性。
 *
 * 两条硬约束：
 * 1. 任何不一致或异常一律返回 null（fail-open），绝不抛给调用方、绝不「尽力解析」；
 * 2. [AUTO_ENABLE_ALLOWED] 恒为 false——Phase 0 未批准前，规则源**永远不得**
 *    驱动 VPN 自动启用。启用只能来自独立的用户显式授权流程。
 */
object FilterRuleSourceVerifier {

    /** Phase 0 合规评审通过并留下书面记录之前，本常量不得改为 true。 */
    const val AUTO_ENABLE_ALLOWED = false

    fun mayAutoEnableVpn(): Boolean = AUTO_ENABLE_ALLOWED

    /** 校验通过返回可注入的规则集；任一不一致返回 null，调用方必须清空规则并全放行。 */
    fun verify(payload: FilterRuleSourcePayload): FilterRuleSet? = try {
        check(payload.schemaVersion == 1) { "unsupported schemaVersion" }
        check(!payload.enabledByDefault) { "rule source must be disabled by default" }
        check(!payload.phase0Approved) { "phase 0 is not approved; refusing an approved-looking payload" }
        check(payload.prototypeOnly) { "only prototype payloads are accepted" }
        check(payload.source.isNotBlank()) { "source is blank" }
        check(payload.license.isNotBlank()) { "license is blank" }
        check(payload.version.isNotBlank()) { "version is blank" }
        check(payload.updatedAt.isNotBlank()) { "updatedAt is blank" }
        check(HEX_256.matches(payload.sha256)) { "sha256 must be 64 lowercase hex chars" }
        // 格式合法 ≠ 内容可信：域名列表被改而摘要字段未变时，必须拒绝而不是放行这份规则。
        check(FilterRuleDigest.compute(payload) == payload.sha256) { "checksum mismatch" }
        FilterRuleSet(
            meta = FilterRuleSourceMeta(
                source = payload.source,
                license = payload.license,
                version = payload.version,
                updatedAt = payload.updatedAt,
                sha256 = payload.sha256,
            ),
            blockedDomains = normalizeAll(payload.domains),
            allowlist = normalizeAll(payload.allowlist),
        )
    } catch (error: IllegalStateException) {
        null
    } catch (error: IllegalArgumentException) {
        null
    }

    /** 与 hosts 解析共用同一套域名合法性口径，拒绝通配、路径与单标签域名。 */
    private fun normalizeAll(values: List<String>): Set<String> =
        values.map { value -> HostsRuleParser.normalize(value) ?: error("非法域名：$value") }.toSet()

    private val HEX_256 = Regex("[a-f0-9]{64}")
}
