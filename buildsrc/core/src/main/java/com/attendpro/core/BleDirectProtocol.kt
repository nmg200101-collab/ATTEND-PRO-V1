package com.attendpro.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Authenticated BLE GATT control protocol.
 *
 * Presence is considered connected end-to-end only after signed PING -> signed ACK -> signed ACK-confirm.
 * A transport-level GATT write success is never treated as proof of employee presence.
 */
object BleDirectProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("a7730001-7a11-4b4d-8a90-77a1a7730001")
    val COMMAND_UUID: UUID = UUID.fromString("a7730002-7a11-4b4d-8a90-77a1a7730002")

    private const val VERSION: Byte = 2
    private const val TYPE_PING: Byte = 1
    private const val TYPE_CHALLENGE: Byte = 2
    private const val TYPE_CONFIG: Byte = 3
    private const val TYPE_ACK: Byte = 4
    private const val TYPE_ACK_CONFIRM: Byte = 5
    private const val MAX_CLOCK_SKEW_SECONDS = 90L
    private const val AUTH_MAC_BYTES = 8

    data class Ping(val epochSeconds: Long, val nonce: Int)
    data class Challenge(val challengeId: String, val method: AttendanceMethod, val expiresAt: Long, val action: AttendanceAction)
    data class Config(
        val latitude: Double, val longitude: Double, val radiusMeters: Int,
        val gpsConfigured: Boolean, val voicePromptsEnabled: Boolean, val geoAlertsEnabled: Boolean,
        val shiftStartHour: Int, val shiftStartMinute: Int, val shiftEndHour: Int, val shiftEndMinute: Int
    )

    fun encodePing(secret: ByteArray, now: Long = System.currentTimeMillis(), nonce: Int = SecureRandom().nextInt()): ByteArray {
        val epoch = epochSeconds(now)
        val unsigned = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN).apply {
            put(VERSION); put(TYPE_PING); putInt(epoch); putInt(nonce)
        }.array()
        return unsigned + hmac(secret, unsigned).copyOf(AUTH_MAC_BYTES)
    }

    fun decodePing(payload: ByteArray, secret: ByteArray, now: Long = System.currentTimeMillis()): Ping? {
        if (!validSignedFrame(payload, TYPE_PING, 10, secret)) return null
        val epoch = ByteBuffer.wrap(payload, 2, 4).order(ByteOrder.BIG_ENDIAN).int.toLong()
        if (abs(now / 1000L - epoch) > MAX_CLOCK_SKEW_SECONDS) return null
        val nonce = ByteBuffer.wrap(payload, 6, 4).order(ByteOrder.BIG_ENDIAN).int
        return Ping(epoch, nonce)
    }

    fun verifyPing(payload: ByteArray, secret: ByteArray, now: Long = System.currentTimeMillis()): Boolean =
        decodePing(payload, secret, now) != null

    fun encodeAck(secret: ByteArray, pingNonce: Int, now: Long = System.currentTimeMillis()): ByteArray {
        val unsigned = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN).apply {
            put(VERSION); put(TYPE_ACK); putInt(epochSeconds(now)); putInt(pingNonce)
        }.array()
        return unsigned + hmac(secret, unsigned).copyOf(AUTH_MAC_BYTES)
    }

    fun verifyAck(payload: ByteArray, secret: ByteArray, expectedPingNonce: Int, now: Long = System.currentTimeMillis()): Boolean {
        if (!validSignedFrame(payload, TYPE_ACK, 10, secret)) return false
        val epoch = ByteBuffer.wrap(payload, 2, 4).order(ByteOrder.BIG_ENDIAN).int.toLong()
        if (abs(now / 1000L - epoch) > MAX_CLOCK_SKEW_SECONDS) return false
        val nonce = ByteBuffer.wrap(payload, 6, 4).order(ByteOrder.BIG_ENDIAN).int
        return nonce == expectedPingNonce
    }

    /** Store -> Employee confirmation proving the signed ACK was actually received and verified. */
    fun encodeAckConfirm(secret: ByteArray, pingNonce: Int, now: Long = System.currentTimeMillis()): ByteArray {
        val unsigned = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN).apply {
            put(VERSION); put(TYPE_ACK_CONFIRM); putInt(epochSeconds(now)); putInt(pingNonce)
        }.array()
        return unsigned + hmac(secret, unsigned).copyOf(AUTH_MAC_BYTES)
    }

    fun verifyAckConfirm(payload: ByteArray, secret: ByteArray, expectedPingNonce: Int, now: Long = System.currentTimeMillis()): Boolean {
        if (!validSignedFrame(payload, TYPE_ACK_CONFIRM, 10, secret)) return false
        val epoch = ByteBuffer.wrap(payload, 2, 4).order(ByteOrder.BIG_ENDIAN).int.toLong()
        if (abs(now / 1000L - epoch) > MAX_CLOCK_SKEW_SECONDS) return false
        val nonce = ByteBuffer.wrap(payload, 6, 4).order(ByteOrder.BIG_ENDIAN).int
        return nonce == expectedPingNonce
    }

    fun encodeChallenge(secret: ByteArray, method: AttendanceMethod, expiresAt: Long, requestToken: Int = SecureRandom().nextInt(), action: AttendanceAction = AttendanceAction.CHECK_IN): ByteArray {
        val expiry = epochSeconds(expiresAt)
        val nonce = requestToken
        val encodedMethod = method.ordinal or if (action == AttendanceAction.CHECK_OUT) 0x80 else 0
        val unsigned = ByteBuffer.allocate(11).order(ByteOrder.BIG_ENDIAN).apply {
            put(VERSION); put(TYPE_CHALLENGE); put(encodedMethod.toByte()); putInt(expiry); putInt(nonce)
        }.array()
        return unsigned + hmac(secret, unsigned).copyOf(AUTH_MAC_BYTES)
    }

    fun decodeChallenge(payload: ByteArray, secret: ByteArray, now: Long = System.currentTimeMillis()): Challenge? {
        if (!validSignedFrame(payload, TYPE_CHALLENGE, 11, secret)) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        buf.position(2)
        val encodedMethod = buf.get().toInt() and 0xff
        val action = if (encodedMethod and 0x80 != 0) AttendanceAction.CHECK_OUT else AttendanceAction.CHECK_IN
        val method = AttendanceMethod.values().getOrNull(encodedMethod and 0x7f) ?: return null
        val expirySec = buf.int.toLong()
        val nonce = buf.int
        val nowSec = now / 1000L
        if (expirySec < nowSec - 10L || expirySec > nowSec + 180L) return null
        return Challenge("REQ-${nonce.toUInt().toString(16)}", method, expirySec * 1000L, action)
    }

    /**
     * Configuration is intentionally accepted by the employee only inside a recently authenticated
     * GATT session. Keeping this frame at 17 bytes also preserves operation on the default ATT MTU.
     */
    fun encodeConfig(config: Config): ByteArray = ByteBuffer.allocate(17).order(ByteOrder.BIG_ENDIAN).apply {
        put(VERSION); put(TYPE_CONFIG)
        putFloat(config.latitude.toFloat()); putFloat(config.longitude.toFloat())
        putShort(config.radiusMeters.coerceIn(20, 5000).toShort())
        var flags = 0
        if (config.voicePromptsEnabled) flags = flags or 0x01
        if (config.geoAlertsEnabled) flags = flags or 0x02
        if (config.gpsConfigured) flags = flags or 0x04
        put(flags.toByte())
        put(config.shiftStartHour.coerceIn(0, 23).toByte()); put(config.shiftStartMinute.coerceIn(0, 59).toByte())
        put(config.shiftEndHour.coerceIn(0, 23).toByte()); put(config.shiftEndMinute.coerceIn(0, 59).toByte())
    }.array()

    fun decodeConfig(payload: ByteArray): Config? {
        if (payload.size != 17 || payload[0] != VERSION || payload[1] != TYPE_CONFIG) return null
        val b = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN); b.position(2)
        val lat = b.float.toDouble(); val lon = b.float.toDouble(); val radius = b.short.toInt() and 0xffff
        val flags = b.get().toInt() and 0xff
        val sh = b.get().toInt() and 0xff; val sm = b.get().toInt() and 0xff
        val eh = b.get().toInt() and 0xff; val em = b.get().toInt() and 0xff
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || radius !in 20..5000) return null
        if (sh !in 0..23 || eh !in 0..23 || sm !in 0..59 || em !in 0..59) return null
        return Config(lat, lon, radius, flags and 0x04 != 0, flags and 0x01 != 0, flags and 0x02 != 0, sh, sm, eh, em)
    }

    private fun validSignedFrame(payload: ByteArray, type: Byte, unsignedSize: Int, secret: ByteArray): Boolean {
        if (secret.isEmpty() || payload.size != unsignedSize + AUTH_MAC_BYTES || payload[0] != VERSION || payload[1] != type) return false
        val expected = hmac(secret, payload.copyOfRange(0, unsignedSize)).copyOf(AUTH_MAC_BYTES)
        return MessageDigest.isEqual(expected, payload.copyOfRange(unsignedSize, payload.size))
    }

    private fun epochSeconds(timeMillis: Long): Int = (timeMillis / 1000L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private fun hmac(secret: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec(secret, "HmacSHA256"))
    }.doFinal(data)
}
