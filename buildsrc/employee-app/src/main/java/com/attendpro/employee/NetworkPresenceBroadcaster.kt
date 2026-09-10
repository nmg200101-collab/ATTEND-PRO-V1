package com.attendpro.employee

import com.attendpro.core.BleProtocol
import com.attendpro.core.LanAckProtocol
import com.attendpro.core.LanConfirmProtocol
import com.attendpro.core.SecretCodec
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

class NetworkPresenceBroadcaster(
    private val onStatus: (String) -> Unit,
    private val onVerifiedAck: (Long) -> Unit = {}
) {
    companion object {
        const val PORT = 47717
        const val LOCAL_PORT = 47718
        private val STORE_PROBE = byteArrayOf(0x41, 0x50, 0x50, 0x52, 0x31) // APPR1
    }

    @Volatile private var running = false
    @Volatile private var employeeId = ""
    @Volatile private var secret = ByteArray(0)
    @Volatile private var proofUntil = 0L
    @Volatile private var proofFlags = 0
    @Volatile private var lastAckAt = 0L
    private var worker: Thread? = null
    private var socket: DatagramSocket? = null
    private var lastStatus = ""

    fun start(id: String, encodedSecret: String) {
        if (id.isBlank()) return
        employeeId = id
        secret = SecretCodec.decode(encodedSecret) ?: ByteArray(0)
        if (running) return
        running = true
        report("جاري اكتشاف جهاز المحل عبر الشبكة المحلية/Hotspot…")
        worker = thread(name = "attend-lan-presence", isDaemon = true) {
            runCatching {
                DatagramSocket(null).also {
                    socket = it
                    it.reuseAddress = true
                    it.broadcast = true
                    it.soTimeout = 650
                    it.bind(InetSocketAddress(LOCAL_PORT))
                }.use { s ->
                    val ackBuffer = ByteArray(160)
                    while (running) {
                        val payload = if (secret.isNotEmpty()) {
                            BleProtocol.buildPayload(employeeId, secret, currentFlags())
                        } else {
                            BleProtocol.buildDiscoveryPayload(employeeId)
                        }
                        val message = byteArrayOf(0x41, 0x50, 0x4c, 0x31) + payload
                        broadcastAddresses().forEach { address ->
                            runCatching { s.send(DatagramPacket(message, message.size, address, PORT)) }
                        }

                        var gotAck = false
                        val until = System.currentTimeMillis() + 650L
                        while (running && System.currentTimeMillis() < until) {
                            try {
                                val packet = DatagramPacket(ackBuffer, ackBuffer.size)
                                s.receive(packet)
                                val ack = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                                if (ack.contentEquals(STORE_PROBE)) {
                                    // Store-initiated discovery: reply directly to the Store's source port.
                                    // This bypasses Android/OEM hotspot configurations that block client broadcasts.
                                    runCatching { s.send(DatagramPacket(message, message.size, packet.address, packet.port)) }
                                    continue
                                }
                                val parsedPayload = BleProtocol.parse(payload)
                                if (secret.isNotEmpty() && parsedPayload != null && LanAckProtocol.verify(
                                        ack,
                                        BleProtocol.employeeHash(employeeId),
                                        parsedPayload.token,
                                        secret
                                    )) {
                                    lastAckAt = System.currentTimeMillis()
                                    onVerifiedAck(lastAckAt)
                                    gotAck = true
                                    // Complete the two-way LAN handshake: the Store only promotes LAN
                                    // to "connected" after receiving this authenticated confirmation.
                                    val confirm = LanConfirmProtocol.encode(parsedPayload.employeeHash, parsedPayload.token, secret, lastAckAt)
                                    runCatching { s.send(DatagramPacket(confirm, confirm.size, packet.address, PORT)) }
                                    break
                                }
                            } catch (_: SocketTimeoutException) {
                                break
                            } catch (_: Exception) {
                                break
                            }
                        }

                        val age = System.currentTimeMillis() - lastAckAt
                        when {
                            gotAck || (lastAckAt > 0L && age < 3_500L) -> report("متصل بالمحل عبر Wi‑Fi/Hotspot المحلي ✓")
                            lastAckAt > 0L -> report("انقطع الاتصال المحلي بالمحل — جارٍ إعادة الاكتشاف")
                            else -> report(if (secret.isEmpty()) "الهاتف يبث محليًا — بانتظار جهاز المحل لإكمال الاقتران" else "الهاتف ظاهر محليًا — بانتظار تأكيد جهاز المحل")
                        }
                        try { Thread.sleep(350L) } catch (_: InterruptedException) { break }
                    }
                }
            }.onFailure {
                if (running) report("تعذر تشغيل قناة Hotspot/Wi‑Fi المحلية؛ ستتم إعادة المحاولة تلقائيًا وBLE/الخادم مستمران")
                // A socket/bind failure must not leave this channel marked as alive forever.
                // PresenceService polls channel health and will recreate it on the next cycle.
                running = false
                socket = null
            }
        }
    }

    fun markVerified(durationMillis: Long, proofType: Int) {
        if (secret.isEmpty()) {
            proofUntil = 0L
            proofFlags = 0
            return
        }
        proofUntil = System.currentTimeMillis() + durationMillis
        proofFlags = BleProtocol.FLAG_VERIFIED or proofType
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        socket = null
        worker?.interrupt()
        worker = null
        lastAckAt = 0L
    }

    fun isRunning(): Boolean = running

    fun isConnected(now: Long = System.currentTimeMillis()): Boolean =
        lastAckAt > 0L && now - lastAckAt < 3_500L

    fun lastAckAt(): Long = lastAckAt

    private fun report(value: String) {
        if (lastStatus != value) {
            lastStatus = value
            onStatus(value)
        }
    }

    private fun currentFlags(): Int = if (secret.isNotEmpty() && System.currentTimeMillis() < proofUntil) proofFlags else 0

    private fun broadcastAddresses(): Set<InetAddress> {
        val addresses = linkedSetOf<InetAddress>()
        fun add(text: String) { runCatching { addresses.add(InetAddress.getByName(text)) } }
        add("255.255.255.255")
        add("192.168.43.255")
        add("192.168.232.255")
        add("192.168.137.255")
        add("192.168.49.255")
        add("192.168.4.255")
        // Common Android hotspot gateways. Unicast is often allowed even when broadcast is filtered.
        add("192.168.43.1")
        add("192.168.232.1")
        add("192.168.137.1")
        add("192.168.49.1")
        add("192.168.4.1")
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.filter { it.isUp && !it.isLoopback }?.forEach { network ->
                network.interfaceAddresses.forEach { item ->
                    val broadcast = item.broadcast
                    if (broadcast is Inet4Address) addresses.add(broadcast)
                    val local = item.address
                    if (local is Inet4Address && !local.isLoopbackAddress) {
                        val b = local.address.clone()
                        b[3] = 0xff.toByte()
                        runCatching { addresses.add(InetAddress.getByAddress(b)) }
                        b[3] = 1
                        runCatching { addresses.add(InetAddress.getByAddress(b)) }
                    }
                }
            }
        }
        return addresses
    }
}
