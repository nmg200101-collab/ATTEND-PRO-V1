package com.attendpro.core

import org.junit.Assert.*
import org.junit.Test

class ReceiverOfflineReportProtocolTest {
    private val secret = "receiver-secret-0123456789"
    private val receiverId = "RCV-TEST-1"
    private val storeId = "STORE-TEST-1"
    private val transferId = "RPT-123456"

    @Test fun envelopeRoundTripAndTamperProtection() {
        val raw = ReceiverOfflineReportProtocol.encodeEnvelope(
            receiverId, storeId, transferId, "APRPT1:encrypted-package", secret, 123456L
        )
        val decoded = ReceiverOfflineReportProtocol.decodeEnvelope(raw, receiverId, secret)
        assertNotNull(decoded)
        assertEquals(storeId, decoded!!.storeId)
        assertEquals(transferId, decoded.transferId)
        assertEquals("APRPT1:encrypted-package", decoded.packageText)
        assertEquals(123456L, decoded.createdAt)

        val tampered = raw.dropLast(1) + if (raw.last() == 'A') "B" else "A"
        assertNull(ReceiverOfflineReportProtocol.decodeEnvelope(tampered, receiverId, secret))
        assertNull(ReceiverOfflineReportProtocol.decodeEnvelope(raw, "RCV-OTHER", secret))
    }

    @Test fun discoveryAndAckAreAuthenticated() {
        val nonce = ReceiverOfflineReportProtocol.newNonce()
        val probe = ReceiverOfflineReportProtocol.discoveryProbe(receiverId, nonce)
        val parsed = ReceiverOfflineReportProtocol.parseDiscoveryProbe(probe)
        assertEquals(receiverId, parsed!!.receiverId)
        assertEquals(nonce, parsed.nonce)

        val reply = ReceiverOfflineReportProtocol.discoveryReply(receiverId, nonce, 47731, secret)
        assertEquals(47731, ReceiverOfflineReportProtocol.verifyDiscoveryReply(reply, receiverId, nonce, secret))
        assertNull(ReceiverOfflineReportProtocol.verifyDiscoveryReply(reply, receiverId, nonce, "wrong-secret"))

        val ack = ReceiverOfflineReportProtocol.ack(receiverId, storeId, transferId, "LAN", secret)
        assertEquals("LAN", ReceiverOfflineReportProtocol.verifyAck(ack, receiverId, storeId, transferId, secret))
        assertNull(ReceiverOfflineReportProtocol.verifyAck(ack, receiverId, storeId, "RPT-OTHER", secret))
    }
}
