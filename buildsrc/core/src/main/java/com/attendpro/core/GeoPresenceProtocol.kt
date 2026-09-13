package com.attendpro.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated GPS-presence frame used only as an additional local telemetry channel.
 * It does not modify the field-proven BLE pairing/heartbeat protocol and it never records attendance.
 */
object GeoPresenceProtocol {
    private val MAGIC = byteArrayOf(0x41, 0x50, 0x47, 0x32) // APG2
    private const val VERSION: Byte = 1
    private const val MAC_BYTES = 12
    private const val MAX_EMPLOYEE_ID_BYTES = 64

    data class Observation(
        val employeeId: String,
        val state: String,
        val distanceMeters: Int,
        val accuracyMeters: Int,
        val observedAt: Long
    )

    fun looksLike(frame: ByteArray): Boolean =
        frame.size >= MAGIC.size + 1 + 1 + 1 + 4 + 4 + 8 + MAC_BYTES &&
            frame.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun peekEmployeeId(frame: ByteArray): String? {
        if (!looksLike(frame)) return null
        val idLength = frame[MAGIC.size + 1].toInt() and 0xff
        if (idLength !in 1..MAX_EMPLOYEE_ID_BYTES) return null
        val start = MAGIC.size + 2
        if (start + idLength > frame.size - (1 + 4 + 4 + 8 + MAC_BYTES)) return null
        return runCatching { frame.copyOfRange(start, start + idLength).toString(Charsets.UTF_8) }
            .getOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }

    fun encode(
        employeeId: String,
        secret: ByteArray,
        state: String,
        distanceMeters: Int,
        accuracyMeters: Int,
        observedAt: Long
    ): ByteArray {
        require(secret.isNotEmpty())
        val id = employeeId.trim().toByteArray(Charsets.UTF_8)
        require(id.size in 1..MAX_EMPLOYEE_ID_BYTES)
        val stateByte = when (state.uppercase()) {
            "INSIDE" -> 1
            "NEAR" -> 2
            "OUTSIDE" -> 3
            else -> 0
        }
        val body = ByteBuffer.allocate(MAGIC.size + 1 + 1 + id.size + 1 + 4 + 4 + 8)
            .order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC)
            .put(VERSION)
            .put(id.size.toByte())
            .put(id)
            .put(stateByte.toByte())
            .putInt(distanceMeters.coerceIn(-1, 1_000_000))
            .putInt(accuracyMeters.coerceIn(-1, 100_000))
            .putLong(observedAt.coerceAtLeast(0L))
            .array()
        return body + hmac(secret, body).copyOf(MAC_BYTES)
    }

    fun decode(frame: ByteArray, expectedEmployeeId: String, secret: ByteArray): Observation? {
        if (secret.isEmpty() || !looksLike(frame)) return null
        val id = peekEmployeeId(frame) ?: return null
        if (!id.equals(expectedEmployeeId.trim(), ignoreCase = true)) return null
        val bodySize = frame.size - MAC_BYTES
        if (bodySize <= 0) return null
        val body = frame.copyOfRange(0, bodySize)
        val suppliedMac = frame.copyOfRange(bodySize, frame.size)
        val expectedMac = hmac(secret, body).copyOf(MAC_BYTES)
        if (!MessageDigest.isEqual(suppliedMac, expectedMac)) return null

        return runCatching {
            val b = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN)
            val magic = ByteArray(MAGIC.size); b.get(magic)
            if (!magic.contentEquals(MAGIC) || b.get() != VERSION) return null
            val idLength = b.get().toInt() and 0xff
            if (idLength !in 1..MAX_EMPLOYEE_ID_BYTES) return null
            val idBytes = ByteArray(idLength); b.get(idBytes)
            val decodedId = idBytes.toString(Charsets.UTF_8).trim()
            val state = when (b.get().toInt() and 0xff) {
                1 -> "INSIDE"
                2 -> "NEAR"
                3 -> "OUTSIDE"
                else -> "UNKNOWN"
            }
            Observation(decodedId, state, b.int, b.int, b.long)
        }.getOrNull()
    }

    private fun hmac(secret: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(secret, "HmacSHA256"))
            doFinal(data)
        }
}
