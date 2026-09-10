package com.attendpro.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object BleChallengeProtocol {
    const val MANUFACTURER_ID = 0x0772
    private const val VERSION: Byte = 1
    private const val MAX_FUTURE_SECONDS = 180L
    private const val MAX_PAST_SECONDS = 15L

    data class Decoded(
        val challengeId: String,
        val method: AttendanceMethod,
        val expiresAt: Long,
        val action: AttendanceAction
    )

    fun encode(employeeId: String, secret: ByteArray, method: AttendanceMethod, expiresAt: Long, requestToken: Int = java.security.SecureRandom().nextInt(), action: AttendanceAction = AttendanceAction.CHECK_IN): ByteArray {
        require(secret.isNotEmpty()) { "Pairing secret is required for BLE challenge" }
        val employeeHash = MessageDigest.getInstance("SHA-256")
            .digest(employeeId.toByteArray(Charsets.UTF_8)).copyOfRange(0, 4)
        val expirySec = (expiresAt / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val nonce = requestToken
        val unsigned = ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN).apply {
            put(VERSION)
            put(employeeHash)
            put((method.ordinal or if (action == AttendanceAction.CHECK_OUT) 0x80 else 0).toByte())
            putInt(expirySec)
            putInt(nonce)
        }.array()
        val mac = hmac(secret, unsigned).copyOfRange(0, 8)
        return unsigned + mac
    }

    fun decode(payload: ByteArray, employeeId: String, secret: ByteArray, now: Long = System.currentTimeMillis()): Decoded? {
        if (secret.isEmpty()) return null
        if (payload.size != 22 || payload[0] != VERSION) return null
        val expectedHash = MessageDigest.getInstance("SHA-256")
            .digest(employeeId.toByteArray(Charsets.UTF_8)).copyOfRange(0, 4)
        if (!MessageDigest.isEqual(expectedHash, payload.copyOfRange(1, 5))) return null
        val expectedMac = hmac(secret, payload.copyOfRange(0, 14)).copyOfRange(0, 8)
        if (!MessageDigest.isEqual(expectedMac, payload.copyOfRange(14, 22))) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        buf.position(5)
        val encodedMethod = buf.get().toInt() and 0xff
        val action = if (encodedMethod and 0x80 != 0) AttendanceAction.CHECK_OUT else AttendanceAction.CHECK_IN
        val ordinal = encodedMethod and 0x7f
        val expirySec = buf.int.toLong()
        val nonce = buf.int
        val nowSec = now / 1000L
        if (expirySec < nowSec - MAX_PAST_SECONDS || expirySec > nowSec + MAX_FUTURE_SECONDS) return null
        val method = AttendanceMethod.values().getOrNull(ordinal) ?: return null
        val challengeId = "REQ-${nonce.toUInt().toString(16)}"
        return Decoded(challengeId, method, expirySec * 1000L, action)
    }

    private fun hmac(secret: ByteArray, data: ByteArray): ByteArray {
        return Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(secret, "HmacSHA256"))
        }.doFinal(data)
    }
}
