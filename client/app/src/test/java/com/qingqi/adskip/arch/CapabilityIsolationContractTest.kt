package com.qingqi.adskip.arch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * L2/L3 隔离原型的边界契约（Issue #84 / DESIGN-ADS-FILTERING.md §9）。
 *
 * Phase 0 合规评审**尚未通过**：当前决定是「L2 只可做隔离原型；L3 暂缓」，
 * 且「未获批不得进入发布分支」。本测试把这句话升级为机器可执行的约束：
 * 两个能力层必须是可独立编译、可独立停用的 Gradle 模块，但**不得**被 :app 依赖
 * （不进主 APK）、不得声明 Hook 框架、不得解密 TLS、不得默认启用。
 *
 * 与 ArchitectureBoundaryTest 同属纯 JVM 文件系统扫描；能力层不在 :app 的编译
 * 类路径里，因此跨模块边界只能用源码/构建脚本文本来守护，不能靠 import 检查。
 *
 * 契约细则见 docs/planning/DESIGN-L2L3-ISOLATED-PROTOTYPE.md。
 */
class CapabilityIsolationContractTest {

    @Test
    fun `capability modules are registered but never linked into the app`() {
        val settings = gradleSettings()
        assertTrue("L2 模块未注册到 settings.gradle.kts", settings.contains("include(\":capability:l2vpn\")"))
        assertTrue("L3 模块未注册到 settings.gradle.kts", settings.contains("include(\":capability:l3shake\")"))

        // ProjectStructureTest.appModule() 取「第一个带 AndroidManifest.xml 的注册模块」，
        // :app 必须排在最前，否则包声明对位、导航契约等用例会扫到能力模块上。
        assertTrue(
            "include(\":app\") 必须排在能力模块之前（ProjectStructureTest.appModule 依赖该顺序）",
            settings.indexOf("include(\":app\")") < settings.indexOf("include(\":capability:l2vpn\")"),
        )

        val appBuild = File(repoRoot(), "client/app/build.gradle.kts").readText()
        listOf("capability:l2vpn", "capability:l3shake").forEach { module ->
            assertFalse(
                ":app 依赖了 $module —— 隔离原型不得进入主 APK 或发布分支",
                appBuild.contains(module),
            )
        }
    }

    @Test
    fun `client entry allowlist registers the capability directory`() {
        val structure = File(repoRoot(), "client/app/src/test/java/com/qingqi/adskip/arch/ProjectStructureTest.kt")
            .readText()
        assertTrue(
            "client/ 新增 capability/ 目录后，必须显式登记到 ProjectStructureTest.ALLOWED_CLIENT_ENTRIES",
            Regex("""ALLOWED_CLIENT_ENTRIES\s*=\s*setOf\([^)]*"capability"""", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(structure),
        )
    }

    @Test
    fun `l2 vpn permission is declared only inside the isolated module`() {
        val appManifest = File(repoRoot(), "client/app/src/main/AndroidManifest.xml").readText()
        assertFalse("主 APK 清单不得声明 VpnService 组件", appManifest.contains("VpnService"))
        assertFalse("主 APK 清单不得声明 BIND_VPN_SERVICE", appManifest.contains("BIND_VPN_SERVICE"))

        val l2Manifest = File(l2Root(), "src/main/AndroidManifest.xml")
        assertTrue("L2 独立模块应自带清单（组件只在模块内注册）", l2Manifest.isFile)
        val text = l2Manifest.readText()
        assertTrue("L2 VpnService 必须要求 BIND_VPN_SERVICE", text.contains("android.permission.BIND_VPN_SERVICE"))
        assertTrue("L2 VpnService 不得导出", text.contains("android:exported=\"false\""))
    }

    @Test
    fun `l2 never terminates tls or proxies non dns traffic`() {
        val sources = l2KotlinSources()
        assertTrue("未扫描到 L2 Kotlin 源码，本用例会退化为恒真", sources.isNotEmpty())
        val banned = listOf(
            "SSLSocket",
            "TrustManager",
            "KeyStore",
            "CertificateFactory",
            "HTTP_PROXY",
            "installCA",
            "MITM",
        )
        val hits = sources.flatMap { file ->
            banned.filter { token -> file.readText().contains(token) }
                .map { token -> "${file.name}: $token" }
        }
        assertTrue(
            "L2 原型禁止 TLS 解密 / 安装 CA / 代理非 DNS 流量：\n  ${hits.joinToString("\n  ")}",
            hits.isEmpty(),
        )
        assertTrue("L2 必须显式声明只处理 DNS", sources.any { it.readText().contains("DNS_ONLY") })
    }

    @Test
    fun `l2 is disabled by default and requires explicit consent`() {
        val controller = File(l2Root(), "src/main/java/com/qingqi/adskip/l2vpn/L2VpnController.kt").readText()
        assertTrue("L2 必须默认关闭", controller.contains("DEFAULT_ENABLED = false"))
        assertTrue("L2 启动前必须校验用户显式授权", controller.contains("consentGranted"))
        assertTrue("L2 停止后必须让系统网络恢复", controller.contains("networkRestoreRequired"))
    }

    @Test
    fun `l2 upstream dns socket is excluded from its own tunnel`() {
        val service = File(l2Root(), "src/main/java/com/qingqi/adskip/l2vpn/L2DnsVpnService.kt").readText()
        assertTrue(
            "上游 DNS socket 必须 protect()：上游地址已被 addRoute 路由进 TUN，否则查询会被自身重新捕获形成回环",
            service.contains("protect(upstream)"),
        )
        assertTrue(
            "L2 必须使用 setBlocking(false)，服务退出后由系统恢复网络而不是持续断网",
            service.contains("setBlocking(false)"),
        )
    }

    @Test
    fun `l2 rule source keeps provenance metadata`() {
        val rules = File(l2Root(), "src/main/java/com/qingqi/adskip/l2vpn/FilterRuleSet.kt").readText()
        listOf("source", "license", "version", "updatedAt", "sha256").forEach { field ->
            assertTrue("规则源缺少可追溯字段 $field", rules.contains("val $field:") || rules.contains("val $field"))
        }
    }

    @Test
    fun `l3 ships no hook framework and registers no component`() {
        val sources = l3KotlinSources()
        assertTrue("未扫描到 L3 Kotlin 源码，本用例会退化为恒真", sources.isNotEmpty())
        val banned = listOf(
            "de.robv.android.xposed",
            "XposedBridge",
            "XposedHelpers",
            "handleLoadPackage",
            "findAndHookMethod",
            "LSPosed",
            "lsposed",
        )
        val hits = sources.flatMap { file ->
            banned.filter { token -> file.readText().contains(token) }
                .map { token -> "${file.name}: $token" }
        }
        assertTrue(
            "L3 在 Phase 0 单独批准前不得包含任何 Hook 框架代码：\n  ${hits.joinToString("\n  ")}",
            hits.isEmpty(),
        )
        assertFalse(
            "L3 独立模块不得注册 Android 组件（不随主 APK 安装）",
            File(l3Root(), "src/main/AndroidManifest.xml").exists(),
        )
    }

    @Test
    fun `l3 is disabled by default and gated by a separate approval`() {
        val contract = File(l3Root(), "src/main/java/com/qingqi/adskip/l3shake/ShakeGuardContract.kt").readText()
        assertTrue("L3 必须默认禁用", contract.contains("DEFAULT_ENABLED = false"))
        assertTrue("L3 必须标注需要单独批准", contract.contains("REQUIRES_SEPARATE_APPROVAL = true"))
        assertTrue("L3 必须存在未安装状态", contract.contains("NOT_INSTALLED"))
        assertTrue("L3 必须保护支付/登录/授权等流程", contract.contains("isProtected"))
    }

    @Test
    fun `capability layers never depend on each other or on the app`() {
        listOf("l2vpn", "l3shake").forEach { name ->
            val build = File(repoRoot(), "client/capability/$name/build.gradle.kts").readText()
            assertFalse("$name 不得依赖 :app", build.contains("project(\":app\")"))
            assertFalse("$name 不得依赖另一能力层", build.contains("capability:l"))
        }
    }

    @Test
    fun `prototype doc declares that it is not releasable`() {
        val doc = File(repoRoot(), "docs/planning/DESIGN-L2L3-ISOLATED-PROTOTYPE.md")
        assertTrue("缺少隔离原型设计说明", doc.isFile)
        val text = doc.readText()
        assertTrue("原型文档必须写明不得进入发布分支", text.contains("不得进入发布分支"))
        assertTrue("原型文档必须引用 Phase 0 门禁", text.contains("Phase 0"))
        assertTrue("原型文档必须登记到文档地图", docsReadme().contains("planning/DESIGN-L2L3-ISOLATED-PROTOTYPE.md"))
    }

    @Test
    fun `filter rules endpoint is read only and exposes no dns query log`() {
        val api = File(repoRoot(), "server/src/api/filterRulesApi.ts")
        assertTrue("缺少 filter-rules 路由实现", api.isFile)
        val text = api.readText()
        assertFalse("filter-rules 必须是只读路由", text.contains("method === \"PUT\""))
        assertFalse("filter-rules 不得接收上报", text.contains("readBody("))
        listOf("queryLog", "dnsQueryHistory", "clientQueries").forEach { token ->
            assertFalse("路由不得暴露 DNS 查询明细：$token", text.contains(token))
        }
        val seed = File(repoRoot(), "server/seed/filter-rules.json").readText()
        assertTrue("规则源必须默认关闭", seed.contains("\"enabledByDefault\": false"))
    }

    // ---------- 工具 ----------

    private fun l2Root(): File = File(repoRoot(), "client/capability/l2vpn")

    private fun l3Root(): File = File(repoRoot(), "client/capability/l3shake")

    private fun l2KotlinSources(): List<File> = kotlinSources(l2Root())

    private fun l3KotlinSources(): List<File> = kotlinSources(l3Root())

    private fun kotlinSources(module: File): List<File> = File(module, "src/main/java")
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    private fun gradleSettings(): String = File(repoRoot(), "client/settings.gradle.kts").readText()

    private fun docsReadme(): String = File(repoRoot(), "docs/README.md").readText()

    /** 从当前工作目录向上定位仓库根（需同时包含 .gitignore 与 docs/README.md）。 */
    private fun repoRoot(): File {
        var dir: File? = System.getProperty("user.dir")?.let { File(it) }
        while (dir != null) {
            if (File(dir, ".gitignore").isFile && File(dir, "docs/README.md").isFile) return dir
            dir = dir.parentFile
        }
        error("未定位到仓库根（需同时包含 .gitignore 与 docs/README.md）：无法执行能力层隔离守护")
    }
}
