package com.attendpro.core
import org.junit.Assert.*
import org.junit.Test
class PairingProtocolCompatibility1952Test {
    @Test fun ap4pRoundTripPreservesProvision() {
        val secret = SecretCodec.encode(ByteArray(32) { (it + 1).toByte() })
        val p = PairingProtocol.EmployeeProvision(
            storeId="store-1", storeName="Store", employeeId="emp-1", displayName="Employee",
            branchId="B1", phone="", jobTitle="", pairingSecret=secret, pinHash="",
            expiresAt=System.currentTimeMillis()+600000L,
            allowedMethods=setOf(AttendanceMethod.PHONE_BLE_BIOMETRIC.name), serverUrl="https://example.invalid"
        )
        val compact=PairingProtocol.encodeEmployeeProvisionCompact(p)
        assertTrue(compact.startsWith("AP4P:"))
        val d=PairingProtocol.decodeEmployeeProvision(compact)
        assertNotNull(d); assertEquals(p.employeeId,d!!.employeeId); assertEquals(p.storeId,d.storeId)
        assertEquals(p.pairingSecret,d.pairingSecret); assertEquals(p.allowedMethods,d.allowedMethods)
    }
}
