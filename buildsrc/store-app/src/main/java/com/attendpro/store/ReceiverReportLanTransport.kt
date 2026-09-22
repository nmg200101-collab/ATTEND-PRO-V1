package com.attendpro.store

import com.attendpro.core.ReceiverOfflineReportProtocol
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

data class ReceiverReportDeliveryResult(
    val success: Boolean,
    val transport: String,
    val detail: String
)

class ReceiverReportLanServer(
    private val receiverId: String,
    private val secret: String,
    private val onEnvelope: (ReceiverOfflineReportProtocol.Envelope) -> Boolean,
    private val onStatus: (String) -> Unit
) {
    companion object {
        const val DISCOVERY_PORT = 47730
        const val REPORT_PORT = 47731
        private const val MAX_ENVELOPE_BYTES = 1_200_000
    }

    @Volatile private var running = false
    private var udpSocket: DatagramSocket? = null
    private var tcpSocket: ServerSocket? = null
    private var discoveryWorker: Thread? = null
    private var reportWorker: Thread? = null

    fun start() {
        if (running) return
        running = true
        discoveryWorker = thread(name = "receiver-report-lan-discovery", isDaemon = true) { discoveryLoop() }
        reportWorker = thread(name = "receiver-report-lan-server", isDaemon = true) { reportLoop() }
    }

    fun stop() {
        running = false
        runCatching { udpSocket?.close() }
        runCatching { tcpSocket?.close() }
        udpSocket = null
        tcpSocket = null
        discoveryWorker?.interrupt()
        reportWorker?.interrupt()
        discoveryWorker = null
        reportWorker = null
    }

    fun isRunning(): Boolean = running

    private fun discoveryLoop() {
        try {
            DatagramSocket(null).also {
                udpSocket = it
                it.reuseAddress = true
                it.broadcast = true
                it.soTimeout = 900
                it.bind(InetSocketAddress(DISCOVERY_PORT))
            }.use { socket ->
                onStatus("LAN/Hotspot: جاهز للاكتشاف المحلي")
                val buffer = ByteArray(1024)
                while (running) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                        val probe = ReceiverOfflineReportProtocol.parseDiscoveryProbe(text) ?: continue
                        if (!probe.receiverId.equals(receiverId, true)) continue
                        val reply = ReceiverOfflineReportProtocol.discoveryReply(
                            receiverId, probe.nonce, REPORT_PORT, secret
                        ).toByteArray(Charsets.UTF_8)
                        socket.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                    } catch (_: SocketTimeoutException) {
                    } catch (_: Exception) {
                        if (!running) break
                    }
                }
            }
        } catch (t: Throwable) {
            if (running) onStatus("LAN/Hotspot: تعذر تشغيل الاكتشاف المحلي")
        } finally {
            udpSocket = null
            if (running) running = false
        }
    }

    private fun reportLoop() {
        try {
            ServerSocket().also {
                tcpSocket = it
                it.reuseAddress = true
                it.soTimeout = 1_000
                it.bind(InetSocketAddress(REPORT_PORT))
            }.use { server ->
                while (running) {
                    try {
                        val socket = server.accept()
                        handleClient(socket)
                    } catch (_: SocketTimeoutException) {
                    } catch (_: Exception) {
                        if (!running) break
                    }
                }
            }
        } catch (t: Throwable) {
            if (running) onStatus("LAN/Hotspot: تعذر تشغيل استقبال التقارير")
        } finally {
            tcpSocket = null
            if (running) running = false
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 8_000
            val input = DataInputStream(s.getInputStream())
            val length = input.readInt()
            if (length <= 0 || length > MAX_ENVELOPE_BYTES) return
            val bytes = ByteArray(length)
            input.readFully(bytes)
            val raw = String(bytes, Charsets.UTF_8)
            val envelope = ReceiverOfflineReportProtocol.decodeEnvelope(raw, receiverId, secret) ?: return
            val accepted = runCatching { onEnvelope(envelope) }.getOrDefault(false)
            if (!accepted) return
            val ack = ReceiverOfflineReportProtocol.ack(
                receiverId, envelope.storeId, envelope.transferId, "LAN", secret
            )
            DataOutputStream(s.getOutputStream()).use { out ->
                out.write((ack + "\n").toByteArray(Charsets.UTF_8))
                out.flush()
            }
            onStatus("LAN/Hotspot: تم استلام تقرير محلي ✓")
        }
    }
}

object ReceiverReportLanClient {
    fun send(
        receiverId: String,
        secret: String,
        storeId: String,
        transferId: String,
        envelope: String
    ): ReceiverReportDeliveryResult {
        if (receiverId.isBlank() || secret.isBlank() || storeId.isBlank()) {
            return ReceiverReportDeliveryResult(false, "LAN", "بيانات الربط المحلي غير مكتملة")
        }
        return runCatching {
            val nonce = ReceiverOfflineReportProtocol.newNonce()
            val probe = ReceiverOfflineReportProtocol.discoveryProbe(receiverId, nonce).toByteArray(Charsets.UTF_8)
            var targetAddress: InetAddress? = null
            var targetPort = 0

            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 250
                probeTargets().forEach { address ->
                    runCatching {
                        socket.send(
                            DatagramPacket(
                                probe, probe.size, address, ReceiverReportLanServer.DISCOVERY_PORT
                            )
                        )
                    }
                }

                val until = System.currentTimeMillis() + 1_600L
                val buffer = ByteArray(1024)
                while (System.currentTimeMillis() < until && targetAddress == null) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val raw = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                        val port = ReceiverOfflineReportProtocol.verifyDiscoveryReply(
                            raw, receiverId, nonce, secret
                        ) ?: continue
                        targetAddress = packet.address
                        targetPort = port
                    } catch (_: SocketTimeoutException) {
                        // Continue until the bounded discovery window closes.
                    }
                }
            }

            val address = targetAddress
                ?: return ReceiverReportDeliveryResult(false, "LAN", "لم يظهر هاتف الاستلام على الشبكة المحلية")
            val bytes = envelope.toByteArray(Charsets.UTF_8)
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, targetPort), 2_500)
                socket.soTimeout = 8_000
                val output = DataOutputStream(socket.getOutputStream())
                output.writeInt(bytes.size)
                output.write(bytes)
                output.flush()

                val ack = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8)).readLine().orEmpty()
                val transport = ReceiverOfflineReportProtocol.verifyAck(
                    ack, receiverId, storeId, transferId, secret
                ) ?: return ReceiverReportDeliveryResult(false, "LAN", "وصل النقل ولم يصل تأكيد موثوق")
                ReceiverReportDeliveryResult(true, transport, "تم الاستلام عبر Wi‑Fi/Hotspot المحلي")
            }
        }.getOrElse {
            ReceiverReportDeliveryResult(false, "LAN", it.message ?: "تعذر النقل المحلي")
        }
    }

    private fun probeTargets(): Set<InetAddress> {
        val result = linkedSetOf<InetAddress>()
        fun add(value: String) { runCatching { result.add(InetAddress.getByName(value)) } }
        add("255.255.255.255")
        listOf(
            "192.168.43.255", "192.168.232.255", "192.168.137.255", "192.168.49.255", "192.168.4.255",
            "192.168.43.1", "192.168.232.1", "192.168.137.1", "192.168.49.1", "192.168.4.1"
        ).forEach(::add)
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.filter { it.isUp && !it.isLoopback }?.forEach { network ->
                network.interfaceAddresses.forEach { item ->
                    (item.broadcast as? Inet4Address)?.let { result.add(it) }
                    val local = item.address as? Inet4Address
                    if (local != null && !local.isLoopbackAddress) {
                        val bytes = local.address.clone()
                        bytes[3] = 0xff.toByte()
                        runCatching { result.add(InetAddress.getByAddress(bytes)) }
                        bytes[3] = 1
                        runCatching { result.add(InetAddress.getByAddress(bytes)) }
                    }
                }
            }
        }
        return result
    }
}
