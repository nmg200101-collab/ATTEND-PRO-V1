package com.attendpro.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * ATTEND-PRO 1.9.77 local Store -> Employee message protocol.
 * It rides only on an already authenticated BleDirectProtocol session.
 * Every frame stays <= 20 bytes and is independently HMAC-authenticated.
 */
object BleLocalMessageProtocol1977 {
    private const val MAGIC: Byte = 0x71
    private const val HEADER_BYTES = 7
    private const val MAC_BYTES = 6
    const val MAX_FRAME_BYTES = 20
    private const val MAX_CHUNK_BYTES = MAX_FRAME_BYTES - HEADER_BYTES - MAC_BYTES
    private const val MAX_PARTS = 180
    private const val FLAG_VOICE = 0x01
    private const val FLAG_PRIORITY_SHIFT = 1
    private const val FLAG_COMPRESSED = 0x08

    data class Fragment(val messageId: Int, val index: Int, val total: Int, val flags: Int, val payload: ByteArray)
    data class EncodedMessage(val messageId: String, val numericMessageId: Int, val frames: List<ByteArray>)
    data class DecodedMessage(val title: String, val body: String, val priority: String, val voiceEnabled: Boolean)

    fun encodeMessage(secret: ByteArray, title: String, body: String, priority: String, voiceEnabled: Boolean): EncodedMessage {
        require(secret.isNotEmpty()) { "Pairing secret required" }
        val safeTitle = title.trim().ifBlank { "رسالة من إدارة المحل" }.take(60)
        val safeBody = body.trim().take(500)
        require(safeBody.isNotBlank()) { "Message body required" }
        val titleBytes = safeTitle.toByteArray(Charsets.UTF_8)
        val bodyBytes = safeBody.toByteArray(Charsets.UTF_8)
        require(titleBytes.size <= 180) { "Title too long" }
        val raw = ByteBuffer.allocate(1 + titleBytes.size + bodyBytes.size).apply {
            put(titleBytes.size.toByte()); put(titleBytes); put(bodyBytes)
        }.array()
        val compressed = deflate(raw)
        val useCompressed = compressed.size + 2 < raw.size
        val transfer = if (useCompressed) compressed else raw
        val total = ((transfer.size + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES).coerceAtLeast(1)
        require(total <= MAX_PARTS) { "Message too large for local BLE channel" }
        val numericId = SecureRandom().nextInt(0x10000)
        var flags = if (voiceEnabled) FLAG_VOICE else 0
        flags = flags or ((priorityCode(priority) and 0x03) shl FLAG_PRIORITY_SHIFT)
        if (useCompressed) flags = flags or FLAG_COMPRESSED
        val frames = (0 until total).map { index ->
            val from = index * MAX_CHUNK_BYTES
            val to = minOf(transfer.size, from + MAX_CHUNK_BYTES)
            encodeFragment(secret, numericId, index, total, flags, transfer.copyOfRange(from, to))
        }
        return EncodedMessage("L${numericId.toString(16).padStart(4, '0')}", numericId, frames)
    }

    fun decodeFragment(frame: ByteArray, secret: ByteArray): Fragment? {
        if (secret.isEmpty() || frame.size !in (HEADER_BYTES + MAC_BYTES)..MAX_FRAME_BYTES) return null
        if (frame[0] != MAGIC) return null
        val dataLen = frame[6].toInt() and 0xff
        if (dataLen > MAX_CHUNK_BYTES || frame.size != HEADER_BYTES + dataLen + MAC_BYTES) return null
        val unsigned = frame.copyOfRange(0, HEADER_BYTES + dataLen)
        val expected = hmac(secret, unsigned).copyOf(MAC_BYTES)
        val actual = frame.copyOfRange(HEADER_BYTES + dataLen, frame.size)
        if (!MessageDigest.isEqual(expected, actual)) return null
        val id = ((frame[1].toInt() and 0xff) shl 8) or (frame[2].toInt() and 0xff)
        val index = frame[3].toInt() and 0xff
        val total = frame[4].toInt() and 0xff
        val flags = frame[5].toInt() and 0xff
        if (total !in 1..MAX_PARTS || index !in 0 until total) return null
        return Fragment(id, index, total, flags, frame.copyOfRange(HEADER_BYTES, HEADER_BYTES + dataLen))
    }

    fun decodeCompleted(fragments: List<Fragment>): DecodedMessage? {
        if (fragments.isEmpty()) return null
        val first = fragments.first()
        if (fragments.size != first.total || fragments.any { it.messageId != first.messageId || it.total != first.total || it.flags != first.flags }) return null
        val ordered = fragments.sortedBy { it.index }
        if (ordered.map { it.index } != (0 until first.total).toList()) return null
        val transfer = ordered.fold(ByteArray(0)) { acc, f -> acc + f.payload }
        val raw = if (first.flags and FLAG_COMPRESSED != 0) inflate(transfer) ?: return null else transfer
        if (raw.isEmpty()) return null
        val titleLen = raw[0].toInt() and 0xff
        if (titleLen <= 0 || 1 + titleLen > raw.size) return null
        val title = raw.copyOfRange(1, 1 + titleLen).toString(Charsets.UTF_8).trim()
        val body = raw.copyOfRange(1 + titleLen, raw.size).toString(Charsets.UTF_8).trim()
        if (body.isBlank()) return null
        val priority = when ((first.flags shr FLAG_PRIORITY_SHIFT) and 0x03) { 2 -> "URGENT"; 1 -> "IMPORTANT"; else -> "NORMAL" }
        return DecodedMessage(title.ifBlank { "رسالة من إدارة المحل" }, body, priority, first.flags and FLAG_VOICE != 0)
    }

    private fun encodeFragment(secret: ByteArray, id: Int, index: Int, total: Int, flags: Int, data: ByteArray): ByteArray {
        require(data.size <= MAX_CHUNK_BYTES)
        val unsigned = ByteBuffer.allocate(HEADER_BYTES + data.size).order(ByteOrder.BIG_ENDIAN).apply {
            put(MAGIC); putShort((id and 0xffff).toShort()); put(index.toByte()); put(total.toByte()); put(flags.toByte()); put(data.size.toByte()); put(data)
        }.array()
        return unsigned + hmac(secret, unsigned).copyOf(MAC_BYTES)
    }

    private fun priorityCode(value: String): Int = when (value.uppercase()) { "URGENT" -> 2; "IMPORTANT" -> 1; else -> 0 }
    private fun deflate(data: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_SPEED); d.setInput(data); d.finish()
        val out = ByteArray(data.size + 64); val count = d.deflate(out); d.end(); return out.copyOf(count)
    }
    private fun inflate(data: ByteArray): ByteArray? = runCatching {
        val i = Inflater(); i.setInput(data); val out = ByteArray(4096); val count = i.inflate(out)
        if (!i.finished()) throw IllegalArgumentException("Compressed message exceeds limit")
        i.end(); out.copyOf(count)
    }.getOrNull()
    private fun hmac(secret: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec(secret, "HmacSHA256"))
    }.doFinal(data)
}
