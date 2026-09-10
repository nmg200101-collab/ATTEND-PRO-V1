package com.attendpro.core

import org.junit.Assert.*
import org.junit.Test

class PairingProtocolQrV5Test {
    private fun provision() = PairingProtocol.EmployeeProvision(
        storeId = "STORE-1", storeName = "Test Store", employeeId = "EMP-7", displayName = "Employee",
        branchId = "MAIN", phone = "", jobTitle = "", pairingSecret = SecretCodec.encode(SecretCodec.generate()),
        pinHash = "", expiresAt = System.currentTimeMillis() + 60_000L
    )

    @Test fun qrEnvelopeRoundTripsAndBindsCode() {
        val text = PairingProtocol.encodeQrPairingEnvelope("A1B2C3D4", provision())
        val decoded = PairingProtocol.decodeQrPairingEnvelope(text)
        assertNotNull(decoded)
        assertEquals("A1B2C3D4", decoded!!.code)
        assertNotNull(PairingProtocol.decodeEmployeeProvision(decoded.provisionText))
    }

    @Test fun qrEnvelopeRejectsMalformedCodeAndProvision() {
        assertNull(PairingProtocol.decodeQrPairingEnvelope("AP5Q:BAD:AP4P:x"))
        assertNull(PairingProtocol.decodeQrPairingEnvelope("AP5Q:A1B2C3D4:not-a-provision"))
    }
}
