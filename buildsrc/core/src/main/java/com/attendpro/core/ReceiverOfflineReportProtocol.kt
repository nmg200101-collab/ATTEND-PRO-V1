package com.attendpro.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * V139 receiver-report local protocol.
 *
 * Independent from employee pairing/BLE protocols. ReportProtocol payload remains opaque.
 * This envelope binds local delivery to receiverId + storeId + transferId and authenticates it.
 */
object ReceiverOfflineReportProtocol {
    private const val ENVELOPE_PREFIX = "APRL1:"
    private const val DISCOVERY_PROBE = "APRD1"
    private const val DISCOVERY_REPLY = "APRA1"
    private const val ACK_PREFIX = "APACK1"
    private const val MAX_PACKAGE_BYTES = 900_000
    private const val MAX_ID_BYTES = 512
    private const val MAC_BYTES = 16

    data class Envelope(
        val receiverId: String,
        val storeId: String,
        val transferId: String,
        val packageText: String,
        val createdAt: Long
    )

    data class DiscoveryProbe(val receiverId: String, val nonce: String)

    fun encodeEnvelope(
        receiverId: String,
        storeId: String,
        transferId: String,
        packageText: String,
        secret: String,
        createdAt: Long = System.currentTimeMillis()
    ): String {
        require(receiverId.isNotBlank() && storeId.isNotBlank() && transferId.isNotBlank())
        val packageBytes = packageText.toByteArray(Charsets.UTF_8)
        require(packageBytes.size <= MAX_PACKAGE_BYTES)
        val body = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeByte(1)
                out.writeLong(createdAt)
                writeSmall(out, receiverId.trim())
                writeSmall(out, storeId.trim())
                writeSmall(out, transferId.trim())
                out.writeInt(packageBytes.size)
                out.write(packageBytes)
            }
            bytes.toByteArray()
        }
        val mac = hmac(secret, body).copyOf(MAC_BYTES)
        return ENVELOPE_PREFIX + b64(body + mac)
    }

    fun decodeEnvelope(raw: String, expectedReceiverId: String, secret: String): Envelope? = runCatching {
        val clean = raw.trim()
        if (!clean.startsWith(ENVELOPE_PREFIX)) return@runCatching null
        val all = b64d(clean.removePrefix(ENVELOPE_PREFIX))
        if (all.size <= MAC_BYTES) return@runCatching null
        val body = all.copyOfRange(0, all.size - MAC_BYTES)
        val actual = all.copyOfRange(all.size - MAC_BYTES, all.size)
        val expected = hmac(secret, body).copyOf(MAC_BYTES)
        if (!MessageDigest.isEqual(actual, expected)) return@runCatching null

        DataInputStream(ByteArrayInputStream(body)).use { input ->
            if (input.readUnsignedByte() != 1) return@runCatching null
            val createdAt = input.readLong()
            val receiverId = readSmall(input)
            val storeId = readSmall(input)
            val transferId = readSmall(input)
            val packageSize = input.readInt()
            if (packageSize !in 0..MAX_PACKAGE_BYTES || input.available() != packageSize) return@runCatching null
            val packageBytes = ByteArray(packageSize)
            input.readFully(packageBytes)
            if (!receiverId.equals(expectedReceiverId.trim(), true) || storeId.isBlank() || transferId.isBlank()) {
                return@runCatching null
            }
            Envelope(receiverId, storeId, transferId, String(packageBytes, Charsets.UTF_8), createdAt)
        }
    }.getOrNull()

    fun newNonce(): String = b64(ByteArray(12).also { SecureRandom().nextBytes(it) })

    fun discoveryProbe(receiverId: String, nonce: String = newNonce()): String =
        listOf(DISCOVERY_PROBE, receiverId.trim(), nonce).joinToString("|")

    fun parseDiscoveryProbe(raw: String): DiscoveryProbe? {
        val p = raw.trim().split('|')
        if (p.size != 3 || p[0] != DISCOVERY_PROBE || p[1].isBlank() || p[2].length < 8) return null
        return DiscoveryProbe(p[1], p[2])
    }

    fun discoveryReply(receiverId: String, nonce: String, port: Int, secret: String): String {
        val unsigned = listOf(DISCOVERY_REPLY, receiverId.trim(), nonce, port.coerceIn(1, 65535).toString()).joinToString("|")
        return "$unsigned|${macText(secret, unsigned)}"
    }

    fun verifyDiscoveryReply(raw: String, receiverId: String, nonce: String, secret: String): Int? {
        val p = raw.trim().split('|')
        if (p.size != 5 || p[0] != DISCOVERY_REPLY || !p[1].equals(receiverId.trim(), true) || p[2] != nonce) return null
        val port = p[3].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val unsigned = p.take(4).joinToString("|")
        return if (secureEquals(p[4], macText(secret, unsigned))) port else null
    }

    fun ack(receiverId: String, storeId: String, transferId: String, transport: String, secret: String): String {
        val unsigned = listOf(
            ACK_PREFIX, receiverId.trim(), storeId.trim(), transferId.trim(),
            transport.trim().uppercase().take(16)
        ).joinToString("|")
        return "$unsigned|${macText(secret, unsigned)}"
    }

    fun verifyAck(
        raw: String,
        receiverId: String,
        storeId: String,
        transferId: String,
        secret: String
    ): String? {
        val p = raw.trim().split('|')
        if (p.size != 6 || p[0] != ACK_PREFIX) return null
        if (!p[1].equals(receiverId.trim(), true) || p[2] != storeId.trim() || p[3] != transferId.trim()) return null
        val unsigned = p.take(5).joinToString("|")
        if (!secureEquals(p[5], macText(secret, unsigned))) return null
        return p[4]
    }

    fun receiverHash(receiverId: String): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest(receiverId.trim().uppercase().toByteArray(Charsets.UTF_8))
            .copyOf(8)

    private fun writeSmall(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..MAX_ID_BYTES)
        out.writeShort(bytes.size)
        out.write(bytes)
    }

    private fun readSmall(input: DataInputStream): String {
        val size = input.readUnsignedShort()
        require(size in 1..MAX_ID_BYTES)
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun macText(secret: String, text: String): String =
        b64(hmac(secret, text.toByteArray(Charsets.UTF_8)).copyOf(MAC_BYTES))

    private fun hmac(secret: String, data: ByteArray): ByteArray {
        require(secret.isNotBlank()) { "Receiver secret required" }
        return Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        }.doFinal(data)
    }

    private fun secureEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    private fun b64(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun b64d(text: String): ByteArray =
        Base64.getUrlDecoder().decode(text)
}
