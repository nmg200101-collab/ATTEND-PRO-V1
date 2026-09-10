package com.attendpro.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Employee -> Store confirmation proving the Store LAN ACK was actually received and verified. */
object LanConfirmProtocol {
    private val MAGIC = byteArrayOf(0x41, 0x50, 0x43, 0x46) // APCF
    private const val VERSION: Byte = 1
    private const val MAC_SIZE = 8
    private const val UNSIGNED_SIZE = 17
    const val FRAME_SIZE = UNSIGNED_SIZE + MAC_SIZE
    private const val MAX_SKEW_SECONDS = 30L

    fun encode(employeeHash: Int, proofToken: Int, secret: ByteArray, nowMillis: Long = System.currentTimeMillis()): ByteArray {
        require(secret.isNotEmpty()) { "Pairing secret is required for LAN confirmation" }
        val seconds = (nowMillis / 1000L).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        val unsigned = ByteBuffer.allocate(UNSIGNED_SIZE).order(ByteOrder.BIG_ENDIAN).apply {
            put(MAGIC); put(VERSION); putInt(employeeHash); putInt(proofToken); putInt(seconds)
        }.array()
        return unsigned + hmac(secret, unsigned).copyOfRange(0, MAC_SIZE)
    }

    fun looksLike(frame: ByteArray): Boolean = frame.size == FRAME_SIZE && frame.copyOfRange(0, 4).contentEquals(MAGIC) && frame[4] == VERSION

    fun employeeHash(frame: ByteArray): Int? {
        if (!looksLike(frame)) return null
        return ByteBuffer.wrap(frame, 5, 4).order(ByteOrder.BIG_ENDIAN).int
    }

    fun verify(frame: ByteArray, expectedEmployeeHash: Int, expectedProofToken: Int, secret: ByteArray, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (secret.isEmpty() || !looksLike(frame)) return false
        val unsigned = frame.copyOfRange(0, UNSIGNED_SIZE)
        val expectedMac = hmac(secret, unsigned).copyOfRange(0, MAC_SIZE)
        if (!MessageDigest.isEqual(expectedMac, frame.copyOfRange(UNSIGNED_SIZE, FRAME_SIZE))) return false
        val buffer = ByteBuffer.wrap(unsigned).order(ByteOrder.BIG_ENDIAN).apply { position(5) }
        if (buffer.int != expectedEmployeeHash) return false
        if (buffer.int != expectedProofToken) return false
        val epochSeconds = buffer.int.toLong()
        return kotlin.math.abs(nowMillis / 1000L - epochSeconds) <= MAX_SKEW_SECONDS
    }

    private fun hmac(secret: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }.doFinal(data)
}
