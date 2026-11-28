package com.qingqi.adskip.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 架构边界守护测试：扫描 `src/main/java` 源码的 import 关系，
 * 强制执行 ARCHITECTURE.md 第 2.1 节「边界契约」，违反即测试失败。
 *
 * 纯 JVM 源码扫描（不依赖 Android SDK），随 `testDebugUnitTest` 门禁运行——
 * 把边界从文档约定升级为 CI 可执行规则。
 *
 * **分层模型（五层，依赖只允许自上而下）**
 *
 * ```
 * presentation/  界面层：Compose Screen / ViewModel / 导航 / 设计系统
 *      │  （可读 platform/device 的系统入口，不可碰无障碍服务与原始存储）
 *      ▼
 * platform/      系统集成层：AccessibilityService / TileService / 厂商保活
 *      │
 *      ▼
 * data/          数据层：local（存储原语）/ remote（HTTP）/ repository（仓库）/ sync（调度）
 *      │
 *      ▼
 * domain/        业务逻辑层：纯 Kotlin 匹配引擎与安全护栏（零 Android 依赖）
 *
 * core/          公共层：时钟/线程/日志/事件/加密存储/语言偏好，被各层使用但不依赖任何业务包
 * di/            组合根：唯一允许装配全部依赖的位置
 * ```
 */
class ArchitectureBoundaryTest {

    @Test
    fun `service checks window ownership before rules and again before clicking`() {
        val source = File(sourceRoot(), "platform/accessibility/SkipAdService.kt").readText()
        val scan = source.substringAfter("private fun trySkip(pkg: String)")
            .substringBefore("private fun isInDoNotDisturbPeriod")
        val ownership = scan.indexOf("if (root.packageName?.toString() != pkg) return")
        val rules = scan.indexOf("rulesRepo.ruleSetFor(pkg)")
        val windowGuard = "if (activeRoot.packageName?.toString() != pkg || " +
            "activeRoot.windowId != root.windowId) return"
        val recheck = scan.indexOf(windowGuard)
        // 点击入口允许携带额外参数（如爱奇艺的 preferGesture），但必须是同一处调用点。
        val click = scan.indexOf("clickNode(target")
        assertTrue("事件包必须与实际窗口一致，才能选择规则", ownership >= 0 && ownership < rules)
        assertTrue("点击前必须再次确认包名及窗口身份", recheck > rules && recheck < click)
    }

    // ---------- domain：纯 JVM，保证引擎测试可在纯 JVM 运行、可拆分 module ----------

    @Test
    fun `domain depends only on itself and the JVM`() = assertNoBannedImports(
        pkgDir = "domain",
        banned = listOf("android.", "androidx.", "com.qingqi.adskip."),
        allowed = listOf("com.qingqi.adskip.domain."),
    )

    // ---------- core：零业务包依赖（事件总线/时钟/日志/存储均为横切设施） ----------

    @Test
    fun `core depends on no other business package`() = assertNoBannedImports(
        pkgDir = "core",
        banned = listOf("com.qingqi.adskip."),
        allowed = listOf("com.qingqi.adskip.core."),
    )

    // ---------- presentation：不直连无障碍服务 / 原始存储 / 网络 / 后台调度 ----------

    @Test
    fun `presentation never imports the service layer raw storage network or sync`() = assertNoBannedImports(
        pkgDir = "presentation",
        banned = listOf(
            "com.qingqi.adskip.platform.accessibility.",
            "com.qingqi.adskip.data.local.",
            "com.qingqi.adskip.data.remote.",
            "com.qingqi.adskip.data.sync.",
        ),
    )

    // ---------- 下层永不反向依赖 presentation（依赖倒置：经 StateFlow/组合根向上供值） ----------

    @Test
    fun `data never imports presentation or platform`() = assertNoBannedImports(
        pkgDir = "data",
        banned = listOf("com.qingqi.adskip.presentation.", "com.qingqi.adskip.platform."),
    )

    @Test
    fun `remote data source never imports presentation or platform`() = assertNoBannedImports(
        pkgDir = "data/remote",
        banned = listOf("com.qingqi.adskip.presentation.", "com.qingqi.adskip.platform."),
    )

    @Test
    fun `sync scheduling never imports presentation or platform`() = assertNoBannedImports(
        pkgDir = "data/sync",
        banned = listOf("com.qingqi.adskip.presentation.", "com.qingqi.adskip.platform."),
    )

    @Test
    fun `platform never imports presentation`() = assertNoBannedImports(
        pkgDir = "platform",
        banned = listOf("com.qingqi.adskip.presentation."),
    )

    @Test
    fun `accessibility service never imports presentation`() = assertNoBannedImports(
        pkgDir = "platform/accessibility",
        banned = listOf("com.qingqi.adskip.presentation."),
    )

    // ---------- platform/device：系统入口层可读系统状态，不可碰原始存储与网络 ----------

    @Test
    fun `device entry points never import presentation raw storage or network`() = assertNoBannedImports(
        pkgDir = "platform/device",
        banned = listOf(
            "com.qingqi.adskip.presentation.",
            "com.qingqi.adskip.data.local.",
            "com.qingqi.adskip.data.remote.",
            "com.qingqi.adskip.data.sync.",
        ),
    )

    // ---------- 工具 ----------

    private fun assertNoBannedImports(pkgDir: String, banned: List<String>, allowed: List<String> = emptyList()) {
        val root = sourceRoot()
        val dir = File(root, pkgDir)
        assertTrue(
            "边界契约违规：源码目录不存在 $dir（目录结构变化需同步更新本测试与 ARCHITECTURE.md 2.1）",
            dir.isDirectory,
        )

        val violations = mutableListOf<String>()
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEach { line ->
                    val imp = IMPORT_REGEX.find(line)?.groupValues?.get(1) ?: return@forEach
                    val bannedHit = banned.any { imp.startsWith(it) } &&
                        allowed.none { imp.startsWith(it) }
                    if (bannedHit) violations += "${file.nameWithoutExtension} → import $imp"
                }
            }

        assertTrue(
            "边界契约违规（ARCHITECTURE.md 第 2.1 节，包 $pkgDir/ 禁止以下依赖）：\n" +
                violations.joinToString("\n") + "\n" +
                "若确为合理演进，请先更新 ARCHITECTURE.md 边界契约与本测试规则。",
            violations.isEmpty(),
        )
    }

    private fun sourceRoot(): File {
        var dir: File? = System.getProperty("user.dir")?.let { File(it) }
        while (dir != null) {
            val candidate = File(dir, "src/main/java/com/qingqi/adskip")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("未定位到 src/main/java/com/qingqi/adskip：无法执行架构边界守护")
    }

    private companion object {
        val IMPORT_REGEX = Regex("""^import\s+([\w.]+)""")
    }
}
