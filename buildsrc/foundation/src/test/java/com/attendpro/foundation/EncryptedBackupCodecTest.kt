package com.attendpro.foundation

import com.attendpro.foundation.backup.EncryptedBackupCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedBackupCodecTest {
    @Test fun roundTripPreservesArabicDataAndMetadata() {
        val payload = JSONObject().put("store", "فرع بغداد").put("events", 37).toString()
        val password = "رمز-نسخ-قوي-2026".toCharArray()
        val encrypted = EncryptedBackupCodec.encrypt(payload, password, "STORE", "STORE-123", 1_780_000_000_000L)
        val metadata = EncryptedBackupCodec.inspect(encrypted)

        assertEquals("STORE", metadata.role)
        assertEquals(1_780_000_000_000L, metadata.createdAt)
        assertEquals(EncryptedBackupCodec.sourceIdHash("STORE-123"), metadata.sourceIdHash)
        assertEquals(payload, EncryptedBackupCodec.decrypt(encrypted, password, "STORE"))
        assertNotEquals(payload, encrypted)
        password.fill('\u0000')
    }

    @Test fun wrongPasswordAndTamperingAreRejected() {
        val encrypted = EncryptedBackupCodec.encrypt("{\"ok\":true}", "correct-password".toCharArray(), "STORE", "STORE-1")
        assertTrue(runCatching { EncryptedBackupCodec.decrypt(encrypted, "incorrect-password".toCharArray(), "STORE") }.isFailure)

        val root = JSONObject(encrypted).put("role", "EMPLOYEE").toString()
        assertTrue(runCatching { EncryptedBackupCodec.decrypt(root, "correct-password".toCharArray()) }.isFailure)
    }

    @Test fun weakPasswordsAndWrongAppRoleAreRejected() {
        assertTrue(runCatching { EncryptedBackupCodec.encrypt("{}", "1234567".toCharArray(), "STORE", "STORE-1") }.isFailure)
        val encrypted = EncryptedBackupCodec.encrypt("{}", "a-secure-password".toCharArray(), "STORE", "STORE-1")
        assertTrue(runCatching { EncryptedBackupCodec.decrypt(encrypted, "a-secure-password".toCharArray(), "EMPLOYEE") }.isFailure)
    }
}
