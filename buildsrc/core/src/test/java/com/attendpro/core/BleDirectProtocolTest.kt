package com.attendpro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleDirectProtocolTest {
    private val secret = ByteArray(32) { (it * 13 + 7).toByte() }
    private val wrongSecret = ByteArray(32) { (it * 11 + 3).toByte() }
    private val now = 1_800_000_000_000L

    @Test fun pingAuthenticatesOnlyWithCorrectSecretAndFreshClock() {
        val p = BleDirectProtocol.encodePing(secret, now)
        assertEquals(18, p.size)
        assertTrue(BleDirectProtocol.verifyPing(p, secret, now + 30_000L))
        assertFalse(BleDirectProtocol.verifyPing(p, wrongSecret, now))
        assertFalse(BleDirectProtocol.verifyPing(p, secret, now + 120_000L))
    }

    @Test fun ackConfirmRequiresMatchingNonceSecretAndFreshClock() {
        val nonce = 0x13572468
        val confirm = BleDirectProtocol.encodeAckConfirm(secret, nonce, now)
        assertEquals(18, confirm.size)
        assertTrue(BleDirectProtocol.verifyAckConfirm(confirm, secret, nonce, now + 30_000L))
        assertFalse(BleDirectProtocol.verifyAckConfirm(confirm, secret, nonce + 1, now))
        assertFalse(BleDirectProtocol.verifyAckConfirm(confirm, wrongSecret, nonce, now))
        assertFalse(BleDirectProtocol.verifyAckConfirm(confirm, secret, nonce, now + 120_000L))
    }

    @Test fun challengeRoundTripsAndRejectsWrongSecretOrExpiredData() {
        val expiry = now + 60_000L
        val p = BleDirectProtocol.encodeChallenge(secret, AttendanceMethod.PHONE_BLE_BIOMETRIC, expiry)
        assertEquals(19, p.size)
        val c = BleDirectProtocol.decodeChallenge(p, secret, now)
        assertEquals(AttendanceMethod.PHONE_BLE_BIOMETRIC, c?.method)
        assertEquals(expiry, c?.expiresAt)
        assertNull(BleDirectProtocol.decodeChallenge(p, wrongSecret, now))
        assertNull(BleDirectProtocol.decodeChallenge(p, secret, now + 90_000L))
    }

    @Test fun configFitsOneBlePacketAndRoundTripsFlagsAndSchedule() {
        val original = BleDirectProtocol.Config(
            15.35472, 44.20667, 180,
            true, true, true,
            8, 15, 16, 45
        )
        val p = BleDirectProtocol.encodeConfig(original)
        assertEquals(17, p.size)
        val d = BleDirectProtocol.decodeConfig(p)
        assertTrue(d != null)
        assertTrue(d!!.gpsConfigured)
        assertTrue(d.voicePromptsEnabled)
        assertTrue(d.geoAlertsEnabled)
        assertEquals(180, d.radiusMeters)
        assertEquals(8, d.shiftStartHour)
        assertEquals(15, d.shiftStartMinute)
        assertEquals(16, d.shiftEndHour)
        assertEquals(45, d.shiftEndMinute)
    }
}
