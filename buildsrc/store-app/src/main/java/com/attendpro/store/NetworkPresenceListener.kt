package com.attendpro.store

import com.attendpro.core.BleProtocol
import com.attendpro.core.LanConfirmProtocol
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

class NetworkPresenceListener(
    private val onPayload: (BleProtocol.Payload, Int) -> ByteArray?,
    private val onConfirm: (ByteArray, Int) -> Unit,
    private val onStatus: (String) -> Unit
) {
    companion object {
        const val PORT = 47717
        private val STORE_PROBE = byteArrayOf(0x41, 0x50, 0x50, 0x52, 0x31) // APPR1
    }

    @Volatile private var running = false
    private var socket: DatagramSocket? = null
    private var worker: Thread? = null

    fun start() {
        if (running) return
        running = true
        worker = thread(name = "attend-lan-listener", isDaemon = true) {
            runCatching {
                DatagramSocket(null).also {
                    socket = it
                    it.reuseAddress = true
                    it.broadcast = true
                    it.soTimeout = 1_200
                    it.bind(InetSocketAddress(PORT))
                }.use { s ->
                    onStatus("اكتشاف Wi‑Fi/Hotspot المحلي يعمل بدون إنترنت")
                    val buffer = ByteArray(256)
                    var lastProbeAt = 0L
                    while (running) {
                        val now = System.currentTimeMillis()
                        if (now - lastProbeAt >= 1_000L) {
                            probeTargets().forEach { target ->
                                runCatching { s.send(DatagramPacket(STORE_PROBE, STORE_PROBE.size, target, NetworkPresenceBroadcasterCompat.EMPLOYEE_PORT)) }
                            }
                            lastProbeAt = now
                        }
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            s.receive(packet)
                            val data = packet.data
                            val off = packet.offset
                            val full = data.copyOfRange(off, off + packet.length)
                            if (LanConfirmProtocol.looksLike(full)) {
                                onConfirm(full, -45)
                                continue
                            }
                            if (packet.length < 14) continue
                            if (data[off] != 0x41.toByte() || data[off + 1] != 0x50.toByte() || data[off + 2] != 0x4c.toByte() || data[off + 3] != 0x31.toByte()) continue
                            val raw = data.copyOfRange(off + 4, off + 14)
                            val parsed = BleProtocol.parse(raw) ?: continue
                            val ack = onPayload(parsed, -45)
                            if (ack != null) {
                                runCatching { s.send(DatagramPacket(ack, ack.size, packet.address, packet.port)) }
                            }
                        } catch (_: SocketTimeoutException) {
                        } catch (_: Exception) {
                        }
                    }
                }
            }.onFailure {
                if (running) onStatus("تعذر استقبال Wi‑Fi/Hotspot المحلي؛ ستتم إعادة المحاولة تلقائيًا وBLE/الخادم مستمران")
                // Do not keep a dead listener in the running state. MainActivity's health loop
                // sees isRunning()==false and recreates the UDP listener automatically.
                running = false
                socket = null
            }
        }
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        socket = null
        worker?.interrupt()
        worker = null
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
                        bytes[3] = 0xff.toByte(); runCatching { result.add(InetAddress.getByAddress(bytes)) }
                        bytes[3] = 1; runCatching { result.add(InetAddress.getByAddress(bytes)) }
                    }
                }
            }
        }
        return result
    }

    private object NetworkPresenceBroadcasterCompat {
        const val EMPLOYEE_PORT = 47718
    }

    fun isRunning(): Boolean = running
}
