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
 * - 不解密、不安装用户 CA、不接管非 DNS 流量：Builder 只把上游 DNS 地址路由进 TUN；
 * - 默认关闭，且仅在调用方携带显式授权证据时启动；
 * - `setBlocking(false)`，服务停止/崩溃后由系统自行恢复网络，不启用「无 VPN 时阻断」；
 * - 组件只在 `capability/l2vpn` 模块的清单里声明，:app 不依赖本模块，故不进主 APK。
 *
 * **真机未验证**：IPv6、TCP DNS、Private DNS、VPN 冲突、网络切换与断网恢复
 * 均属 Phase 0 放行前的测试项（§7），本实现只保证纯 JVM 数据面可测、可回滚。
 */
class L2DnsVpnService : VpnService() {

    private val controller = L2VpnController()

    @Volatile
    private var rules: FilterRuleSet? = null

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

    fun snapshot(): L2Snapshot = controller.snapshot()

    private fun openTunnel(): ParcelFileDescriptor? = Builder()
        .setSession("轻启 L2 DNS 过滤（隔离原型）")
        .addAddress(PRIVATE_ADDRESS, 32)
        .addDnsServer(UPSTREAM_DNS)
        .addRoute(UPSTREAM_DNS, 32)
        .setBlocking(false)
        .establish()

    private fun pump(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buffer = ByteArray(BUFFER_SIZE)
        val upstream = DatagramSocket()
        try {
            // 关键：1.1.1.1/32 已被 addRoute 路由进 TUN，上游查询若不绕过 VPN，
            // 发出的包会被自己重新捕获，形成无限回环。protect() 让该 socket 走真实网络。
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
                        val dns = forwardToUpstream(upstream, action.dnsPayload)
                        if (dns != null) {
                            DnsTunnelLoop.wrapUpstreamResponse(packet, dns)?.let { output.write(it) }
                        }
                    }

                    // Builder 只把上游 DNS 地址路由进 TUN，其它报文不应出现；出现即不解析、
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

    private fun forwardToUpstream(socket: DatagramSocket, dns: ByteArray): ByteArray? = runCatching {
        socket.send(DatagramPacket(dns, dns.size, InetAddress.getByName(UPSTREAM_DNS), DNS_PORT))
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
        private const val PRIVATE_ADDRESS = "10.233.0.2"
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val DNS_PORT = 53
        private const val BUFFER_SIZE = 4096
        private const val UPSTREAM_TIMEOUT_MS = 3000
    }
}
