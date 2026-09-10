package com.attendpro.core

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object LocalChallengeProtocol {
    const val PORT = 47719
    private val MAGIC_V1 = byteArrayOf(0x41, 0x50, 0x43, 0x31) // APC1 legacy
    private val MAGIC_V2 = byteArrayOf(0x41, 0x50, 0x43, 0x32) // APC2 shared request token

    data class Challenge(val employeeHash: Int, val method: AttendanceMethod, val issuedAt: Long, val requestToken: Int?, val action: AttendanceAction) {
        val challengeId: String get() = requestToken?.let { "REQ-${it.toUInt().toString(16)}" } ?: "local:$issuedAt"
        val expiresAt: Long get() = issuedAt + 120_000L
    }

    /**
     * V2 adds a shared 32-bit request token so GATT, BLE advertisement and LAN can carry
     * the same logical request ID.  When requestToken is null we emit the old V1 frame.
     */
    fun encode(
        employeeId: String,
        secret: ByteArray,
        method: AttendanceMethod,
        now: Long = System.currentTimeMillis(),
        requestToken: Int? = null,
        action: AttendanceAction = AttendanceAction.CHECK_IN
    ): ByteArray {
        require(secret.isNotEmpty()) { "Pairing secret is required for local challenge" }
        val hash = BleProtocol.employeeHash(employeeId)
        val body = if (requestToken == null) {
            ByteBuffer.allocate(17).put(MAGIC_V1).putInt(hash).put(method.ordinal.toByte()).putLong(now).array()
        } else {
            val encodedMethod = method.ordinal or if (action == AttendanceAction.CHECK_OUT) 0x80 else 0
            ByteBuffer.allocate(21).put(MAGIC_V2).putInt(hash).put(encodedMethod.toByte()).putLong(now).putInt(requestToken).array()
        }
        return body + signature(secret, body)
    }

    fun decode(bytes: ByteArray, secret: ByteArray, now: Long = System.currentTimeMillis()): Challenge? {
        if (secret.isEmpty()) return null
        val v1 = bytes.size == 25 && bytes.copyOfRange(0, 4).contentEquals(MAGIC_V1)
        val v2 = bytes.size == 29 && bytes.copyOfRange(0, 4).contentEquals(MAGIC_V2)
        if (!v1 && !v2) return null
        val bodySize = if (v2) 21 else 17
        val body = bytes.copyOfRange(0, bodySize)
        if (!MessageDigestCompat.equals(bytes.copyOfRange(bodySize, bytes.size), signature(secret, body))) return null
        val buffer = ByteBuffer.wrap(body).apply { position(4) }
        val employeeHash = buffer.int
        val encodedMethod = buffer.get().toInt() and 0xff
        val action = if (v2 && encodedMethod and 0x80 != 0) AttendanceAction.CHECK_OUT else AttendanceAction.CHECK_IN
        val ordinal = if (v2) encodedMethod and 0x7f else encodedMethod
        val issuedAt = buffer.long
        if (kotlin.math.abs(now - issuedAt) > 120_000L) return null
        val requestToken = if (v2) buffer.int else null
        val method = AttendanceMethod.values().getOrNull(ordinal) ?: return null
        return Challenge(employeeHash, method, issuedAt, requestToken, action)
    }

    private fun signature(secret: ByteArray, body: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(body).copyOfRange(0, 8)
    }

    private object MessageDigestCompat {
        fun equals(a: ByteArray, b: ByteArray): Boolean {
            if (a.size != b.size) return false
            var diff = 0
            for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
            return diff == 0
        }
    }
}
