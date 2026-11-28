package com.qingqi.adskip.l2vpn

/** 从 DNS 查询报文中提取的最小信息：只用于规则匹配，不保存、不上传。 */
data class DnsQuestion(val qname: String, val qtype: Int)

/**
 * DNS 查询段解析（纯 JVM）。
 *
 * 只处理「单个标准查询」；压缩指针、多问题、非 IN 类、响应报文一律返回 null，
 * 由调用方按「不干预」放行。宁可漏拦，也不要因为解析歧义误伤正常域名。
 */
object DnsQuestionParser {

    const val TYPE_A = 1
    const val TYPE_AAAA = 28

    private const val HEADER_SIZE = 12
    private const val CLASS_IN = 1
    private const val FLAG_RESPONSE = 0x8000

    fun parse(packet: ByteArray): DnsQuestion? {
        if (packet.size < HEADER_SIZE + 5) return null
        if (u16(packet, 2) and FLAG_RESPONSE != 0) return null
        if (u16(packet, 4) != 1) return null

        var offset = HEADER_SIZE
        val labels = mutableListOf<String>()
        while (offset < packet.size) {
            val length = packet[offset].toInt() and 0xFF
            if (length == 0) {
                offset++
                break
            }
            if (length and 0xC0 == 0xC0) return null
            if (length > 63 || offset + 1 + length > packet.size) return null
            labels += packet.copyOfRange(offset + 1, offset + 1 + length).toString(Charsets.US_ASCII)
            offset += 1 + length
        }
        if (labels.isEmpty() || offset + 4 > packet.size) return null
        if (u16(packet, offset + 2) != CLASS_IN) return null

        val name = labels.joinToString(".").lowercase()
        if (HostsRuleParser.normalize(name) == null) return null
        return DnsQuestion(name, u16(packet, offset))
    }

    private fun u16(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)
}
