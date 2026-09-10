package com.attendpro.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanHandshakeProtocolTest {
    @Test fun ackAndConfirmRequireMatchingTokenAndSecret() {
        val secret = ByteArray(32) { (it + 11).toByte() }
        val other = ByteArray(32) { (it + 17).toByte() }
        val hash = 0x12345678
        val token = 0x13572468
        val now = 1_780_000_000_000L

        val ack = LanAckProtocol.encode(hash, token, secret, now)
        assertTrue(LanAckProtocol.verify(ack, hash, token, secret, now))
        assertFalse(LanAckProtocol.verify(ack, hash, token + 1, secret, now))
        assertFalse(LanAckProtocol.verify(ack, hash, token, other, now))

        val confirm = LanConfirmProtocol.encode(hash, token, secret, now)
        assertTrue(LanConfirmProtocol.verify(confirm, hash, token, secret, now))
        assertFalse(LanConfirmProtocol.verify(confirm, hash, token + 1, secret, now))
        assertFalse(LanConfirmProtocol.verify(confirm, hash, token, other, now))
    }

    @Test fun staleConfirmIsRejected() {
        val secret = ByteArray(32) { 7 }
        val hash = 77
        val token = 91
        val issued = 1_780_000_000_000L
        val confirm = LanConfirmProtocol.encode(hash, token, secret, issued)
        assertFalse(LanConfirmProtocol.verify(confirm, hash, token, secret, issued + 31_000L))
    }
}
