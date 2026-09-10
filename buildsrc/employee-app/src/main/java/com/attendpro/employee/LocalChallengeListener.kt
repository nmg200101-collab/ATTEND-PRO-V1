package com.attendpro.employee

import com.attendpro.core.AttendanceMethod
import com.attendpro.core.AttendanceAction
import com.attendpro.core.BleProtocol
import com.attendpro.core.LocalChallengeProtocol
import com.attendpro.core.SecretCodec
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import kotlin.concurrent.thread

class LocalChallengeListener(private val onChallenge: (String, AttendanceMethod, Long, AttendanceAction) -> Unit) {
    @Volatile private var running = false
    private var socket: DatagramSocket? = null
    private var worker: Thread? = null

    fun start(employeeId: String, encodedSecret: String) {
        val secret = SecretCodec.decode(encodedSecret) ?: return
        if (running) return
        running = true
        worker = thread(name = "attend-local-challenge", isDaemon = true) {
            try {
                val local = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(LocalChallengeProtocol.PORT))
                    soTimeout = 5_000
                }
                socket = local
                val buffer = ByteArray(64)
                while (running) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        local.receive(packet)
                        val challenge = LocalChallengeProtocol.decode(buffer.copyOf(packet.length), secret) ?: continue
                        if (challenge.employeeHash != BleProtocol.employeeHash(employeeId)) continue
                        onChallenge("local:${challenge.challengeId}", challenge.method, challenge.expiresAt, challenge.action)
                    } catch (_: java.net.SocketTimeoutException) { }
                }
            } catch (_: Exception) { }
            finally { socket?.close(); socket = null; running = false }
        }
    }

    fun stop() { running = false; socket?.close(); worker?.interrupt(); worker = null }
    fun isRunning(): Boolean = running
}
