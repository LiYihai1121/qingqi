package com.qingqi.adskip.net

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.qingqi.adskip.core.AppExecutors
import com.qingqi.adskip.core.LogRing
import com.qingqi.adskip.data.CloudPkgRule
import com.qingqi.adskip.data.Prefs
import com.qingqi.adskip.data.RulesRepository
import com.qingqi.adskip.data.ServerEndpoint
import okhttp3.CertificatePinner
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 网络层：云端规则同步与跳过上报。
 *
 * 安全约束（A 链修复）：
 * - 规则完整性：服务端对 rules/latest 响应体签发 HMAC-SHA256，本类用
 *   [Prefs.getRulesSigningKey] 配的密钥验签后才允许落地；未配置密钥 = 拒绝
 *   载入（失败关闭），未通过验签的载荷一律丢弃。
 * - 证书固定：https 连接必须配置证书指纹（[Prefs.getCertPins]）才放行；
 *   未配置 = 拒绝连接，杜绝「声明了 pinner 却没有任何 pin」的空操作。
 * - 错误信息不回显 URL（可能携带 userinfo 凭据），日志只留主机名。
 */
object SyncClient {

    private const val TIMEOUT_MS = 6000

    /**
     * 响应体读取上限。
     *
     * 安全契约：响应来自网络对端（可能是被劫持的 AP / 恶意服务器），
     * 无上限的 `body.string()` 会让一个超大响应直接把应用内存打爆（DoS）。
     * 规则载荷合法规模远小于该值（服务端请求体上限 1MB），超限即拒绝。
     */
    private const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024

    /**
     * 云端规则载荷条目上限（与服务端 config 对齐：MAX_APPS=2000、
     * MAX_RULES_PER_APP=512、MAX_SELECTORS_PER_LIST=128、条目 256 字符）。
     * 服务端已清洗，但客户端不能假设对端可信（MITM/被劫持服务器）：
     * 超限条目直接丢弃、非法包名键直接跳过，防内存/偏好存储被打爆。
     */
    private const val MAX_CLOUD_APPS = 2000
    private const val MAX_LIST_ITEMS = 512
    private const val MAX_SELECTORS = 128
    private const val MAX_ITEM_LEN = 256

    /** 与服务端 PKG_RE 同源：包名键必须是合法安卓包名，否则不得进入偏好存储键空间 */
    private val PKG_KEY_RE = Regex("^[a-zA-Z][A-Za-z0-9_]*(\\.[a-zA-Z][A-Za-z0-9_]*)+$")

    /** 规则签名响应头（与服务端 utils/sign.ts 的 RULES_SIGNATURE_HEADER 同名同值） */
    private const val SIGNATURE_HEADER = "X-Rules-Signature"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executors by lazy { AppExecutors() }

    // ---------- 配置 ----------

    fun serverUrl(context: Context): String = Prefs.getServerUrl(context)
    fun saveServerUrl(context: Context, url: String) = Prefs.saveServerUrl(context, url)
    fun lastSyncAt(context: Context): Long = Prefs.getLastSyncAt(context)

    /** OkHttp 客户端按（host + pins）缓存：CertificatePinner.pin 是 host 维度，单例做不到按配置切换 */
    private val pinnedClients = ConcurrentHashMap<String, OkHttpClient>()

    private fun baseBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .writeTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)

    private fun clientForPins(host: String, pins: List<String>): OkHttpClient {
        val cacheKey = "$host|${pins.joinToString(",")}"
        return pinnedClients.getOrPut(cacheKey) {
            val pinner = CertificatePinner.Builder().apply {
                for (pin in pins) add(host, pin)
            }.build()
            baseBuilder().certificatePinner(pinner).build()
        }
    }

    // 测试用客户端（禁用证书锁定，允许 MockWebServer 自签名证书）
    internal var testClient: OkHttpClient? = null

    // ---------- 规则解析 ----------

    /** 解析结果（internal：单测直接覆盖解析边界） */
    internal data class RulesParseResult(
        val keywords: List<String>?,
        val viewIds: List<String>?,
        val selectors: List<String>?,
        val pkgRules: Map<String, CloudPkgRule>,
        val schemaVersion: Int,
        val hash: String,
        val version: Int,
    )

    /** v1/v0 规则响应统一解析；两端形状的差异在此收敛。internal 供单测覆盖。 */
    internal fun parseRulesResponse(body: String): RulesParseResult {
        val json = JSONObject(body)
        val schemaVersion = json.optInt("schemaVersion", 1)
        val hash = json.optString("hash", "")
        val version = json.optInt("version", 0)

        val rulesObj = json.optJSONObject("rules")
        val keywords: List<String>?
        val viewIds: List<String>?
        val selectors: List<String>?
        val pkgRules = mutableMapOf<String, CloudPkgRule>()

        if (rulesObj != null) {
            keywords = rulesObj.optJSONArray("globalKeywords")?.toStringList()
            viewIds = rulesObj.optJSONArray("globalViewIds")?.toStringList()
            selectors = rulesObj.optJSONArray("globalSelectors")?.toStringList(MAX_SELECTORS)
            val apps = rulesObj.optJSONObject("apps")
            if (apps != null) {
                val keys = apps.keys()
                while (keys.hasNext()) {
                    val pkg = keys.next()
                    if (pkgRules.size >= MAX_CLOUD_APPS) break
                    // 非法包名键不得进入偏好存储键空间（服务端 PKG_RE 同源校验）
                    if (!PKG_KEY_RE.matches(pkg)) continue
                    val rule = apps.optJSONObject(pkg) ?: continue
                    pkgRules[pkg] = CloudPkgRule(
                        keywords = rule.optJSONArray("keywords")?.toStringList() ?: emptyList(),
                        viewIds = rule.optJSONArray("viewIds")?.toStringList() ?: emptyList(),
                        selectors = rule.optJSONArray("selectors")?.toStringList(MAX_SELECTORS) ?: emptyList(),
                        disabled = rule.optBoolean("disabled", false),
                    )
                }
            }
        } else {
            keywords = json.optJSONArray("keywords")?.toStringList()
            viewIds = json.optJSONArray("viewIds")?.toStringList()
            selectors = json.optJSONArray("selectors")?.toStringList(MAX_SELECTORS)
            val packages = json.optJSONObject("packages")
            if (packages != null) {
                val keys = packages.keys()
                while (keys.hasNext()) {
                    val pkg = keys.next()
                    if (pkgRules.size >= MAX_CLOUD_APPS) break
                    if (!PKG_KEY_RE.matches(pkg)) continue
                    val rule = packages.optJSONObject(pkg) ?: continue
                    pkgRules[pkg] = CloudPkgRule(
                        keywords = rule.optJSONArray("keywords")?.toStringList() ?: emptyList(),
                        viewIds = rule.optJSONArray("viewIds")?.toStringList() ?: emptyList(),
                        selectors = rule.optJSONArray("selectors")?.toStringList(MAX_SELECTORS) ?: emptyList(),
                        disabled = rule.optBoolean("disabled", false),
                    )
                }
            }
        }

        return RulesParseResult(keywords, viewIds, selectors, pkgRules, schemaVersion, hash, version)
    }

    // ---------- 同步/上报 ----------

    /**
     * 拉取云端规则并落地（v1 协议：带 If-None-Match）。
     *
     * 失败关闭链：未配签名密钥 → 拒载；https 未配证书指纹 → 拒连；
     * 验签不通过 → 拒载。三者任一不满足都不触碰本地规则。
     *
     * @param onResult 主线程回调：(成功?, 提示信息)
     */
    fun syncRules(
        context: Context,
        serverUrl: String,
        rulesRepo: RulesRepository,
        onResult: (Boolean, String) -> Unit,
    ) {
        executors.io.execute {
            val result = runSync(context, serverUrl.trimEnd('/'), rulesRepo)
            mainHandler.post { onResult(result.first, result.second) }
        }
    }

    /** 单次同步的阻塞实现：验签/证书门禁 + 落地；失败一律不动本地规则 */
    private fun runSync(context: Context, base: String, rulesRepo: RulesRepository): Pair<Boolean, String> {
        val guard = securityGuard(context, base)
        if (guard != null) return Pair(false, guard)
        return try {
            val knownHash = Prefs.getRulesHash(context)
            val pins = Prefs.getCertPins(context)
            // v1 路由优先，回退 v0
            val (body, signature, notModified) =
                httpGetWithETagBlocking("$base/api/v1/rules/latest", knownHash, base, pins)
            if (notModified) {
                Prefs.setLastSyncAt(context, System.currentTimeMillis())
                Pair(true, "规则已是最新（304 Not Modified）")
            } else {
                val signingKey = Prefs.getRulesSigningKey(context)
                if (!verifyRulesSignature(signingKey, body, signature)) {
                    // 验签失败：载荷可能被中间人改写，直接丢弃
                    Pair(false, "规则签名校验失败，已拒绝载入（本地密钥与服务端不一致，或连接被劫持）")
                } else {
                    val parsed = parseRulesResponse(body)
                    val ok = rulesRepo.applyCloudRules(
                        parsed.keywords,
                        parsed.viewIds,
                        parsed.pkgRules,
                        parsed.schemaVersion,
                        parsed.selectors,
                    )
                    if (!ok) {
                        Pair(false, "规则协议版本过低，请升级客户端")
                    } else {
                        Prefs.setRulesHash(context, parsed.hash)
                        Prefs.setLastSyncAt(context, System.currentTimeMillis())
                        Pair(true, "同步成功：规则 v${parsed.version}，含 ${parsed.pkgRules.size} 个应用专属规则")
                    }
                }
            }
        } catch (e: Exception) {
            // 不回显 URL（可能含 userinfo 凭据），日志只留主机名
            LogRing.w("Sync", "sync failed for ${hostOf(base)}: ${e.javaClass.simpleName}")
            Pair(false, "同步失败：无法连接到服务器")
        }
    }

    /** 静默上报一次跳过（无 deviceId，仅保留以兼容旧调用方；优先使用带 deviceId 的重载）。 */
    @Deprecated("Use reportSkip(serverUrl, pkg, label, deviceId, pins)")
    fun reportSkip(serverUrl: String, pkg: String, label: String, pins: List<String>) {
        if (serverUrl.isBlank()) return
        // S1：上报同样走证书锁定——未配 pins 且非测试注入时拒发，防 MITM 窃取/篡改用户行为
        if (pins.isEmpty() && testClient == null) return
        executors.io.execute {
            try {
                val base = serverUrl.trimEnd('/')
                val v0Payload = JSONObject()
                v0Payload.put("pkg", pkg)
                v0Payload.put("label", label)
                httpPost("$base/api/skip", v0Payload.toString(), pins)
            } catch (e: Exception) {
                // 静默失败
            }
        }
    }

    /** 静默上报一次跳过（带 deviceId，v1 批量格式）。 */
    fun reportSkip(serverUrl: String, pkg: String, label: String, deviceId: String, pins: List<String>) {
        if (serverUrl.isBlank()) return
        // S1：上报同样走证书锁定——未配 pins 且非测试注入时拒发，防 MITM 窃取/篡改用户行为
        if (pins.isEmpty() && testClient == null) return
        executors.io.execute {
            try {
                val base = serverUrl.trimEnd('/')
                val payload = JSONObject()
                val event = JSONObject()
                event.put("pkg", pkg)
                event.put("label", label) // F1：v1 事件补 label，与服务端 cleanReportEvent 对齐
                event.put("channel", "text")
                event.put("ts", System.currentTimeMillis())
                payload.put("deviceId", deviceId)
                payload.put("events", JSONArray().put(event))
                try {
                    httpPost("$base/api/v1/reports/batch", payload.toString(), pins)
                } catch (e: IOException) {
                    // 仅网络层异常（连接失败/读写中断——服务端大概率未处理该请求）
                    // 才回退 v0 单条上报。HTTP 状态错误（429/4xx/5xx）说明服务端
                    // 已处理过请求，回退会让同一跳过事件双份入库、统计计数虚增
                    // （DESIGN-ARCH-REVIEW §6.5）。
                    try {
                        val v0Payload = JSONObject()
                        v0Payload.put("pkg", pkg)
                        v0Payload.put("label", label)
                        httpPost("$base/api/skip", v0Payload.toString(), pins)
                    } catch (e2: Exception) {
                        // 静默失败
                    }
                }
            } catch (e: Exception) {
                // 静默失败
            }
        }
    }

    /**
     * 静默同步规则（无 UI 回调，供 JobService 调用）。
     * 在调用方的 IO 线程中直接执行，不另起线程。
     */
    fun syncRulesSilently(context: Context, serverUrl: String, rulesRepo: RulesRepository): Boolean {
        return try {
            val base = serverUrl.trimEnd('/')
            val guard = securityGuard(context, base)
            if (guard != null) {
                LogRing.w("Sync", "silent sync refused: $guard")
                return false
            }
            val knownHash = Prefs.getRulesHash(context)
            val pins = Prefs.getCertPins(context)
            val (body, signature, notModified) =
                httpGetWithETagBlocking("$base/api/v1/rules/latest", knownHash, base, pins)
            if (notModified) {
                Prefs.setLastSyncAt(context, System.currentTimeMillis())
                return true
            }
            val signingKey = Prefs.getRulesSigningKey(context)
            if (!verifyRulesSignature(signingKey, body, signature)) {
                LogRing.w("Sync", "silent sync refused: signature mismatch")
                return false
            }
            val parsed = parseRulesResponse(body)
            val ok = rulesRepo.applyCloudRules(
                parsed.keywords,
                parsed.viewIds,
                parsed.pkgRules,
                parsed.schemaVersion,
                parsed.selectors,
            )
            if (ok) {
                Prefs.setRulesHash(context, parsed.hash)
                Prefs.setLastSyncAt(context, System.currentTimeMillis())
            }
            ok
        } catch (e: Exception) {
            // 不回显 URL，日志只留主机名
            LogRing.w("Sync", "syncRulesSilently failed for ${hostOf(serverUrl)}: ${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * 同步前的失败关闭门禁：未配签名密钥一律拒载；https 未配证书指纹一律拒连。
     * @return 拒绝原因；通过时返回 null。
     */
    private fun securityGuard(context: Context, base: String): String? {
        if (!ServerEndpoint.isValid(base)) {
            return "仅支持有效的 HTTPS 服务器地址，已拒绝明文连接"
        }
        if (Prefs.getRulesSigningKey(context).isBlank()) {
            return "未配置规则签名密钥，已拒绝云端规则（防篡改）。请在本页底部配置"
        }
        if (Prefs.getCertPins(context).isEmpty()) {
            return "未配置证书指纹，已拒绝 HTTPS 连接。请在本页底部填入 sha256/... 指纹"
        }
        return null
    }

    /** 从 URL 中去掉 scheme 与 userinfo，只留主机名（日志/提示不泄露完整地址） */
    internal fun hostOf(url: String): String = url.toHttpUrlOrNull()?.host ?: "invalid-url"

    // ---------- HTTP 原语 ----------

    private data class GetResult(val body: String, val signature: String?, val notModified: Boolean)

    /** GET 带 If-None-Match，返回 (body, 签名头, notModified) —— 同步阻塞版 */
    private fun httpGetWithETagBlocking(url: String, etag: String, baseUrl: String, pins: List<String>): GetResult {
        val host = hostOf(baseUrl)
        val httpClient = testClient ?: clientForPins(host, pins)
        val request = Request.Builder()
            .url(url)
            .header("If-None-Match", etag)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 304) return GetResult("", null, true)
            if (response.code !in 200..299) throw Exception("HTTP " + response.code)
            return GetResult(
                readBodyCapped(response),
                response.header(SIGNATURE_HEADER),
                false,
            )
        }
    }

    /** 读取响应体，超过 [MAX_RESPONSE_BYTES] 直接失败，避免超大响应耗尽内存（internal 供单测覆盖） */
    internal fun readBodyCapped(response: Response): String {
        val body = response.body ?: return ""
        if (body.contentLength() > MAX_RESPONSE_BYTES) throw Exception("response too large")
        val source = body.source()
        source.request(MAX_RESPONSE_BYTES + 1)
        if (source.buffer.size > MAX_RESPONSE_BYTES) throw Exception("response too large")
        return source.buffer.clone().readString(Charsets.UTF_8)
    }

    // ---------- 规则签名（HMAC-SHA256，与服务端 sign.ts 同算法同口径） ----------

    /** 对载荷原文计算 HMAC-SHA256 十六进制；服务端同样按最终字节串签发 */
    internal fun hmacSha256Hex(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /**
     * 验签：密钥为空、签名为空、长度不一致均判失败（失败关闭）。
     * 比较用常量时间算法，避免逐字节提前命中泄露前缀信息。
     */
    internal fun verifyRulesSignature(key: String, body: String, signature: String?): Boolean {
        if (key.isBlank() || signature.isNullOrBlank()) return false
        val presented = signature.trim().removePrefix("sha256=").lowercase()
        val expected = hmacSha256Hex(key, body)
        return constantTimeEquals(expected, presented)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private fun httpPost(url: String, body: String, pins: List<String>) {
        require(ServerEndpoint.isValid(url)) { "Only valid HTTPS URLs are allowed" }
        // S1：上报走证书锁定（与 httpGetWithETagBlocking 同构）；测试注入 testClient 时绕过 pin
        val host = hostOf(url)
        val httpClient = testClient ?: clientForPins(host, pins)
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP " + response.code)
        }
    }

    private fun JSONArray.toStringList(maxItems: Int = MAX_LIST_ITEMS): List<String> {
        val out = mutableListOf<String>()
        for (i in 0 until length()) {
            if (out.size >= maxItems) break
            val s = optString(i).trim().take(MAX_ITEM_LEN)
            if (s.isNotEmpty()) out.add(s)
        }
        return out
    }
}
