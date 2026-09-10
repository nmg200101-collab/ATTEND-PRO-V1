package com.attendpro.store

import com.attendpro.core.AttendanceMethod
import com.attendpro.core.AttendanceAction
import com.attendpro.core.LocalChallengeProtocol
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object LocalChallengeSender {
    fun send(employeeId: String, secret: ByteArray, method: AttendanceMethod, issuedAt: Long = System.currentTimeMillis(), requestToken: Int? = null, action: AttendanceAction = AttendanceAction.CHECK_IN): Boolean = runCatching {
        val data = LocalChallengeProtocol.encode(employeeId, secret, method, issuedAt, requestToken, action)
        val targets = linkedSetOf<InetAddress>(InetAddress.getByName("255.255.255.255"))
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.forEach { network ->
            network.interfaceAddresses.forEach { item -> if (item.broadcast is Inet4Address) targets.add(item.broadcast) }
        }
        var sent = false
        DatagramSocket().use { socket ->
            socket.broadcast = true
            repeat(3) {
                targets.forEach { target -> runCatching { socket.send(DatagramPacket(data, data.size, target, LocalChallengeProtocol.PORT)); sent = true } }
                Thread.sleep(120L)
            }
        }
        sent
    }.getOrDefault(false)
}
