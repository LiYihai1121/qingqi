package com.qingqi.adskip.l2vpn

import java.security.MessageDigest

/**
 * 与服务端 `server/src/utils/filterRuleSource.ts#computeRuleDigest` 对齐的规范化摘要。
 *
 * 客户端不能只检查 `sha256` 是否为 64 位十六进制：那样「域名列表被改、摘要字段仍是
 * 合法字符串」的载荷照样能通过，完整性校验就退化成了格式校验。本对象用纯 JVM 复算
 * 一遍摘要（不引入 JSON 库或第三方依赖），保证客户端能独立复核服务端下发的规则内容。
 *
 * 规范化形式必须与服务端逐字节一致：固定字段顺序、数组按字典序排序、字符串按
 * `JSON.stringify` 的转义口径输出，再对 UTF-8 字节做 SHA-256。`prototypeOnly` 与
 * `sha256` 本身不参与摘要（服务端下发时才追加 `prototypeOnly`）。
 * `L2PrototypeTest` 用 `server/seed/filter-rules.json` 的已知摘要做跨语言常量断言，
 * 任一侧改动规范化形式都会立刻红灯。
 */
object FilterRuleDigest {

    private const val HEX = "0123456789abcdef"

    fun compute(payload: FilterRuleSourcePayload): String {
        val canonical = buildString {
            append('{')
            append("\"schemaVersion\":").append(payload.schemaVersion).append(',')
            append("\"source\":").append(quote(payload.source)).append(',')
            append("\"license\":").append(quote(payload.license)).append(',')
            append("\"version\":").append(quote(payload.version)).append(',')
            append("\"updatedAt\":").append(quote(payload.updatedAt)).append(',')
            append("\"domains\":").append(jsonArray(payload.domains)).append(',')
            append("\"allowlist\":").append(jsonArray(payload.allowlist)).append(',')
            append("\"enabledByDefault\":").append(payload.enabledByDefault).append(',')
            append("\"phase0Approved\":").append(payload.phase0Approved).append(',')
            append("\"note\":").append(quote(payload.note))
            append('}')
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xFF
                append(HEX[value ushr 4])
                append(HEX[value and 0xF])
            }
        }
    }

    private fun jsonArray(values: List<String>): String = values.sorted()
        .joinToString(separator = ",", prefix = "[", postfix = "]") { quote(it) }

    /** 按 `JSON.stringify` 口径转义；非 ASCII 字符原样输出，与 UTF-8 编码结果对齐。 */
    private fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { ch -> append(escape(ch)) }
        append('"')
    }

    private fun escape(ch: Char): String = when (ch) {
        '"' -> "\\\""
        '\\' -> "\\\\"
        '\b' -> "\\b"
        '\u000C' -> "\\f"
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        else -> if (ch.code < 0x20) "\\u" + ch.code.toString(16).padStart(4, '0') else ch.toString()
    }
}
