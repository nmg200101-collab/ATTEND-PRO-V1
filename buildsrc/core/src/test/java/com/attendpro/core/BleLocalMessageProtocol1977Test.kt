package com.attendpro.core

import org.junit.Assert.*
import org.junit.Test

class BleLocalMessageProtocol1977Test {
    @Test fun roundTripArabicMessageAndFrameLimit() {
        val secret = ByteArray(32) { (it * 7 + 3).toByte() }
        val body = "رسالة مباشرة بدون إنترنت إلى الموظف. ".repeat(12)
        val encoded = BleLocalMessageProtocol1977.encodeMessage(secret, "تنبيه الدوام", body, "URGENT", true)
        assertTrue(encoded.frames.isNotEmpty())
        assertTrue(encoded.frames.all { it.size <= BleLocalMessageProtocol1977.MAX_FRAME_BYTES })
        val fragments = encoded.frames.map { BleLocalMessageProtocol1977.decodeFragment(it, secret)!! }
        val decoded = BleLocalMessageProtocol1977.decodeCompleted(fragments)!!
        assertEquals("تنبيه الدوام", decoded.title)
        assertEquals(body.trim().take(500), decoded.body)
        assertEquals("URGENT", decoded.priority)
        assertTrue(decoded.voiceEnabled)
    }
    @Test fun rejectsTamperedFragment() {
        val secret = ByteArray(32) { it.toByte() }
        val frame = BleLocalMessageProtocol1977.encodeMessage(secret, "A", "hello", "NORMAL", false).frames.first().clone()
        frame[7] = (frame[7].toInt() xor 1).toByte()
        assertNull(BleLocalMessageProtocol1977.decodeFragment(frame, secret))
    }
}
