package com.qingqi.adskip.l2vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * L2 本地 DNS 过滤的 VpnService 薄封装（隔离原型）。
 *
 * 设计门禁（DESIGN-ADS-FILTERING.md §9 ADR-ADS-001）：
 * - 不解密、不安装用户 CA、不接管非 DNS 流量：Builder 只把**虚拟 DNS 地址**路由进 TUN；
 * - 默认关闭，且仅在调用方携带显式授权证据时启动；
 * - `setBlocking(false)`，服务停止/崩溃后由系统自行恢复网络，不启用「无 VPN 时阻断」；
 * - 组件只在 `capability/l2vpn` 模块的清单里声明，:app 不依赖本模块，故不进主 APK。
 *
 * **真机未验证**：IPv6、TCP DNS、Private DNS、VPN 冲突、网络切换与断网恢复
 * 均属 Phase 0 放行前的测试项，本实现只保证纯 JVM 数据面可测、可回滚。
 */
class L2DnsVpnService : VpnService() {

    private val controller = L2VpnController()

    @Volatile
    private var rules: FilterRuleSet? = null

    @Volatile
    private var config: L2VpnConfig = L2VpnConfig.DEFAULT

    @Volatile
    private var tunnel: ParcelFileDescriptor? = null

    @Volatile
    private var worker: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val consentGranted = intent?.getBooleanExtra(EXTRA_CONSENT_GRANTED, false) ?: false
        if (!consentGranted || !controller.requestStart(consentGranted)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val fd = openTunnel()
        if (fd == null || !controller.onVpnEstablished()) {
            fd?.closeQuietly()
            controller.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        tunnel = fd
        worker = Thread({ pump(fd) }, "l2-dns-tunnel").also { it.start() }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        stopSelf()
    }

    override fun onDestroy() {
        controller.stop()
        worker?.interrupt()
        worker = null
        tunnel?.closeQuietly()
        tunnel = null
        super.onDestroy()
    }

    /** 规则由调用方在校验来源、许可证与校验值后注入；损坏时传 null 即回退为全放行。 */
    fun setRules(ruleSet: FilterRuleSet?) {
        rules = ruleSet
    }

    /**
     * 校验并注入服务端下发的规则源载荷（原型文档第 9.4 项的注入链路）。
     *
     * **只注入规则，不启动 VPN、不改变授权状态**：启用仍须独立的显式授权流程，
     * `FilterRuleSourceVerifier.AUTO_ENABLE_ALLOWED` 恒为 false。
     * 校验失败会清空当前规则（fail-open 全放行）并返回 false。
     */
    fun applyVerifiedRules(payload: FilterRuleSourcePayload): Boolean {
        val verified = FilterRuleSourceVerifier.verify(payload)
        rules = verified
        return verified != null
    }

    /** 显式配置 TUN 与上游；不从网络读取，也不因规则源变化自动改写。 */
    fun configure(value: L2VpnConfig) {
        config = value
    }

    fun currentConfig(): L2VpnConfig = config

    fun snapshot(): L2Snapshot = controller.snapshot()

    private fun openTunnel(): ParcelFileDescriptor? {
        val cfg = config
        return Builder()
            .setSession(getString(R.string.l2_vpn_session))
            .addAddress(cfg.tunAddress, 32)
            .addDnsServer(cfg.virtualDns)
            .addRoute(cfg.virtualDns, 32)
            .setBlocking(false)
            .establish()
    }

    private fun pump(fd: ParcelFileDescriptor) {
        val cfg = config
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buffer = ByteArray(BUFFER_SIZE)
        val upstream = DatagramSocket()
        try {
            // 上游 socket 必须绕过本 VPN 与系统级 VPN 策略。虚拟 DNS 与真实上游分离后，
            // 上游地址本身已不在 addRoute 范围内，但 protect() 仍是显式安全边界，
            // 且 L2VpnConfig 禁止两者同址——同址会让上游查询再次被 TUN 捕获形成回环。
            if (!protect(upstream)) {
                controller.record(DnsFilterOutcome(DnsDecision.BYPASS, "vpn protect() failed"))
                return
            }
            upstream.soTimeout = UPSTREAM_TIMEOUT_MS
            while (controller.isRunning() && !Thread.currentThread().isInterrupted) {
                val read = input.read(buffer)
                if (read <= 0) break
                val packet = buffer.copyOf(read)
                val action = DnsTunnelLoop.handle(
                    packet,
                    rules,
                    enabled = controller.isRunning(),
                    consentGranted = true,
                )
                when (action) {
                    is DnsTunnelAction.ReplyLocally -> {
                        output.write(action.ipv4Packet)
                        controller.record(DnsFilterOutcome(DnsDecision.BLOCK, "matched rule"))
                    }

                    is DnsTunnelAction.ForwardToUpstream -> {
                        controller.record(DnsFilterOutcome(DnsDecision.ALLOW, "not matched"))
                        val dns = forwardToUpstream(upstream, action.dnsPayload, cfg)
                        if (dns != null) {
                            DnsTunnelLoop.wrapUpstreamResponse(packet, dns)?.let { output.write(it) }
                        }
                    }

                    // Builder 只把虚拟 DNS /32 路由进 TUN，其它报文不应出现；出现即不解析、
                    // 不改写、不回注，只累计 BYPASS（详见 DnsTunnelAction.Unhandled 注释）。
                    DnsTunnelAction.Unhandled ->
                        controller.record(DnsFilterOutcome(DnsDecision.BYPASS, "non dns packet"))
                }
            }
        } catch (error: IOException) {
            // 只记录汇总，不保存任何查询明细
            controller.record(DnsFilterOutcome(DnsDecision.BYPASS, "tunnel io closed: ${error.javaClass.simpleName}"))
        } finally {
            upstream.close()
            runCatching { input.close() }
            runCatching { output.close() }
            controller.onUnexpectedExit()
        }
    }

    private fun forwardToUpstream(socket: DatagramSocket, dns: ByteArray, cfg: L2VpnConfig): ByteArray? = runCatching {
        socket.send(DatagramPacket(dns, dns.size, InetAddress.getByName(cfg.upstreamDns), cfg.upstreamPort))
        val buffer = ByteArray(BUFFER_SIZE)
        val response = DatagramPacket(buffer, buffer.size)
        socket.receive(response)
        buffer.copyOf(response.length)
        // 上游不可达 / 超时时这次查询不被回应；不记录域名、不重试放大
    }.getOrNull()

    private fun ParcelFileDescriptor.closeQuietly() {
        runCatching { close() }
    }

    companion object {
        const val EXTRA_CONSENT_GRANTED = "com.qingqi.adskip.l2vpn.CONSENT_GRANTED"
        private const val BUFFER_SIZE = 4096
        private const val UPSTREAM_TIMEOUT_MS = 3000
    }
}
