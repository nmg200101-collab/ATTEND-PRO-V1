package com.attendpro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalChallengeProtocolTest {
    @Test fun signedChallengeRoundTripAndTamperRejection() {
        val secret = ByteArray(32) { (it * 7).toByte() }
        val now = 1_800_000_000_000L
        val bytes = LocalChallengeProtocol.encode("employee-42", secret, AttendanceMethod.PHONE_FINGERPRINT, now)
        val decoded = LocalChallengeProtocol.decode(bytes, secret, now + 1_000L)
        assertEquals(BleProtocol.employeeHash("employee-42"), decoded?.employeeHash)
        assertEquals(AttendanceMethod.PHONE_FINGERPRINT, decoded?.method)
        bytes[10] = (bytes[10].toInt() xor 1).toByte()
        assertNull(LocalChallengeProtocol.decode(bytes, secret, now + 1_000L))
    }
}
