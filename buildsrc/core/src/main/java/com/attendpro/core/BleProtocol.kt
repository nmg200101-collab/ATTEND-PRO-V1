package com.attendpro.core

import android.os.ParcelUuid
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object BleProtocol {
    const val MANUFACTURER_ID: Int = 0x0771
    const val VERSION: Byte = 2
    const val LEGACY_VERSION: Int = 1
    const val FLAG_VERIFIED: Int = 0x01
    const val FLAG_BIOMETRIC: Int = 0x02
    const val FLAG_GPS: Int = 0x04
    const val FLAG_CREDENTIAL: Int = 0x08
    const val FLAG_CHALLENGE: Int = 0x10
    // Direct QR approval. Kept as a proof flag instead of a new AttendanceMethod so
    // existing enum ordinals, stored events and server protocol remain compatible.
    const val FLAG_QR: Int = 0x20
    // Carries the requested action for an authenticated challenge proof without changing frame size.
    const val FLAG_CHECK_OUT: Int = 0x40
    const val WINDOW_MILLIS: Long = 15_000L
    val SERVICE_UUID: ParcelUuid by lazy { ParcelUuid.fromString("0000A771-0000-1000-8000-00805F9B34FB") }

    data class Payload(
        val version: Int,
        val flags: Int,
        val employeeHash: Int,
        val token: Int
    ) {
        val verified: Boolean get() = flags and FLAG_VERIFIED != 0
        val biometricProof: Boolean get() = flags and FLAG_BIOMETRIC != 0
        val gpsProof: Boolean get() = flags and FLAG_GPS != 0
        val credentialProof: Boolean get() = flags and FLAG_CREDENTIAL != 0
        val challengeProof: Boolean get() = flags and FLAG_CHALLENGE != 0
        val qrProof: Boolean get() = flags and FLAG_QR != 0
        val checkoutProof: Boolean get() = flags and FLAG_CHECK_OUT != 0
        val discoveryOnly: Boolean get() = flags == 0
    }

    fun employeeHash(employeeId: String): Int {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(employeeId.trim().lowercase().toByteArray(Charsets.UTF_8))
        return ByteBuffer.wrap(digest.copyOfRange(0, 4)).int
    }

    fun currentWindow(nowMillis: Long = System.currentTimeMillis()): Long = nowMillis / WINDOW_MILLIS

    private fun tokenV2(secret: ByteArray, employeeHash: Int, flags: Int, window: Long): Int {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val data = ByteBuffer.allocate(13).putInt(employeeHash).putLong(window).put(flags.toByte()).array()
        val output = mac.doFinal(data)
        return ByteBuffer.wrap(output.copyOfRange(0, 4)).int
    }

    private fun tokenV1(secret: ByteArray, employeeHash: Int, verified: Boolean, window: Long): Int {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val data = ByteBuffer.allocate(13).putInt(employeeHash).putLong(window).put(if (verified) 1.toByte() else 0.toByte()).array()
        val output = mac.doFinal(data)
        return ByteBuffer.wrap(output.copyOfRange(0, 4)).int
    }

    fun buildDiscoveryPayload(employeeId: String): ByteArray {
        val hash = employeeHash(employeeId)
        return ByteBuffer.allocate(10)
            .put(VERSION)
            .put(0.toByte())
            .putInt(hash)
            .putInt(0)
            .array()
    }

    fun buildPayload(employeeId: String, secret: ByteArray, flags: Int = 0, nowMillis: Long = System.currentTimeMillis()): ByteArray {
        if (secret.isEmpty() && flags == 0) return buildDiscoveryPayload(employeeId)
        require(secret.isNotEmpty()) { "Pairing secret is required for authenticated BLE proof" }
        val hash = employeeHash(employeeId)
        val safeFlags = flags and 0xFF
        val token = tokenV2(secret, hash, safeFlags, currentWindow(nowMillis))
        return ByteBuffer.allocate(10).put(VERSION).put(safeFlags.toByte()).putInt(hash).putInt(token).array()
    }

    fun buildPayload(employeeId: String, secret: ByteArray, verified: Boolean, nowMillis: Long = System.currentTimeMillis()): ByteArray =
        buildPayload(employeeId, secret, if (verified) FLAG_VERIFIED or FLAG_BIOMETRIC else 0, nowMillis)

    fun parse(bytes: ByteArray?): Payload? {
        if (bytes == null || bytes.size < 10) return null
        val buffer = ByteBuffer.wrap(bytes)
        return Payload(
            version = buffer.get().toInt() and 0xFF,
            flags = buffer.get().toInt() and 0xFF,
            employeeHash = buffer.int,
            token = buffer.int
        )
    }

    /**
     * Verifies the rotating HMAC token even for a discovery-only frame.
     * Use this when a transport (for example LAN ACK) is going to be promoted
     * from merely "visible" to an authenticated connection.
     */
    fun verifyAuthenticated(payload: Payload, employeeId: String, secret: ByteArray, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (payload.version != VERSION.toInt() && payload.version != LEGACY_VERSION) return false
        val expectedHash = employeeHash(employeeId)
        if (payload.employeeHash != expectedHash || secret.isEmpty()) return false
        val window = currentWindow(nowMillis)
        for (candidate in (window - 1)..(window + 1)) {
            val expected = if (payload.version == LEGACY_VERSION) {
                tokenV1(secret, expectedHash, payload.verified, candidate)
            } else {
                tokenV2(secret, expectedHash, payload.flags, candidate)
            }
            if (payload.token == expected) return true
        }
        return false
    }

    fun verify(payload: Payload, employeeId: String, secret: ByteArray, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (payload.version != VERSION.toInt() && payload.version != LEGACY_VERSION) return false
        val expectedHash = employeeHash(employeeId)
        if (payload.employeeHash != expectedHash) return false

        // Discovery must not depend on a previously synchronized pairing secret.
        // Attendance proof/challenge remains cryptographically authenticated below.
        if (payload.flags == 0) return true
        if (secret.isEmpty()) return false

        val window = currentWindow(nowMillis)
        for (candidate in (window - 1)..(window + 1)) {
            val expected = if (payload.version == LEGACY_VERSION) {
                tokenV1(secret, expectedHash, payload.verified, candidate)
            } else {
                tokenV2(secret, expectedHash, payload.flags, candidate)
            }
            if (payload.token == expected) return true
        }
        return false
    }
}
