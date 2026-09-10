package com.attendpro.core

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Small authenticated pairing ACK that fits in the default BLE ATT payload.
 * It proves that the employee phone actually received the provision secret;
 * merely seeing a BLE advertisement or a UDP packet is never treated as pairing success.
 */
object PairingAckProtocol {
    private const val VERSION: Byte = 1
    private const val TYPE_ACK: Byte = 0x41
    private const val FRAME_SIZE = 20
    private const val BODY_SIZE = 14
    private const val MAC_SIZE = 6
    private const val MAX_CLOCK_SKEW_SECONDS = 180L
    const val TEXT_PREFIX = "APACK2:"

    fun encode(code: String, secret: ByteArray, nowMillis: Long = System.currentTimeMillis()): ByteArray {
        require(secret.isNotEmpty()) { "Pairing secret is required" }
        val body = ByteBuffer.allocate(BODY_SIZE).order(ByteOrder.BIG_ENDIAN).apply {
            put(VERSION)
            put(TYPE_ACK)
            putInt(codeHash(code))
            putInt((nowMillis / 1000L).toInt())
            putInt(SecureRandom().nextInt())
        }.array()
        return body + hmac(secret, body).copyOf(MAC_SIZE)
    }

    fun verify(frame: ByteArray, code: String, secret: ByteArray, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (secret.isEmpty() || frame.size != FRAME_SIZE) return false
        val body = frame.copyOfRange(0, BODY_SIZE)
        val supplied = frame.copyOfRange(BODY_SIZE, FRAME_SIZE)
        val buffer = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN)
        if (buffer.get() != VERSION || buffer.get() != TYPE_ACK) return false
        if (buffer.int != codeHash(code)) return false
        val epochSeconds = buffer.int.toLong() and 0xffffffffL
        val nowSeconds = nowMillis / 1000L
        if (kotlin.math.abs(nowSeconds - epochSeconds) > MAX_CLOCK_SKEW_SECONDS) return false
        val expected = hmac(secret, body).copyOf(MAC_SIZE)
        return MessageDigest.isEqual(expected, supplied)
    }

    fun toText(frame: ByteArray): String = TEXT_PREFIX + Base64.encodeToString(frame, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)

    fun fromText(text: String): ByteArray? = runCatching {
        val clean = text.trim()
        if (!clean.startsWith(TEXT_PREFIX)) return@runCatching null
        Base64.decode(clean.removePrefix(TEXT_PREFIX), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
            .takeIf { it.size == FRAME_SIZE }
    }.getOrNull()

    private fun codeHash(code: String): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(code.trim().uppercase().toByteArray(Charsets.UTF_8))
        return ByteBuffer.wrap(digest, 0, 4).order(ByteOrder.BIG_ENDIAN).int
    }

    private fun hmac(secret: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(secret, "HmacSHA256"))
            doFinal(data)
        }
}
