package com.attendpro.foundation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.attendpro.foundation.data.AttendProDatabase
import com.attendpro.foundation.data.EncodedLegacyPreference
import com.attendpro.foundation.data.LegacyPreferenceCodec
import com.attendpro.foundation.data.LegacyPreferenceType
import com.attendpro.foundation.data.LegacyPreferencesMigration
import com.attendpro.foundation.domain.AttendanceAction
import com.attendpro.foundation.domain.AttendanceEvent
import com.attendpro.foundation.domain.AttendanceState
import com.attendpro.foundation.domain.Employee
import com.attendpro.foundation.domain.Message
import com.attendpro.foundation.domain.MessageChannel
import com.attendpro.foundation.domain.SettingValue
import com.attendpro.foundation.domain.SystemRole
import com.attendpro.foundation.domain.VerificationMethod
import com.attendpro.foundation.repository.PayloadCipher
import com.attendpro.foundation.repository.SqliteActivationRepository
import com.attendpro.foundation.repository.SqliteAttendanceRepository
import com.attendpro.foundation.repository.SqliteEmployeeRepository
import com.attendpro.foundation.repository.SqliteMessageRepository
import com.attendpro.foundation.repository.SqliteRoleRepository
import com.attendpro.foundation.repository.SqliteSettingsRepository
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DataLayerIntegrationTest {
    private lateinit var context: Context
    private lateinit var helper: AttendProDatabase

    @Before fun openFreshDatabase() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(AttendProDatabase.NAME)
        helper = AttendProDatabase(context)
        helper.writableDatabase
    }

    @After fun closeDatabase() {
        helper.close()
        context.deleteDatabase(AttendProDatabase.NAME)
    }

    @Test fun schemaContainsEveryFoundationStore() {
        val names = helper.readableDatabase.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table'", null
        ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

        assertTrue(
            names.containsAll(
                setOf(
                    "employees", "attendance", "settings", "messages", "activation",
                    "role_assignments", "legacy_preferences", "migration_state"
                )
            )
        )
        assertEquals(AttendProDatabase.VERSION, helper.readableDatabase.version)
    }

    @Test fun sharedPreferencesMigrationIsLosslessIdempotentAndCopyFirst() {
        val prefs = context.getSharedPreferences("migration-test", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putString("text", "قيمة،with,delimiters")
            .putInt("int", 17)
            .putLong("long", 9_000_000_000L)
            .putFloat("float", 1.25f)
            .putBoolean("flag", true)
            .putStringSet("set", linkedSetOf("أ", "b,c", ""))
            .commit()
        val before = prefs.all.toMap()

        val migration = LegacyPreferencesMigration()
        assertTrue(migration.migrateOnce(helper.writableDatabase, "migration-test", prefs, 500L))
        assertFalse(migration.migrateOnce(helper.writableDatabase, "migration-test", prefs, 600L))
        assertEquals(before, prefs.all)

        val restored = linkedMapOf<String, Any?>()
        helper.readableDatabase.rawQuery(
            "SELECT key,value_type,value FROM legacy_preferences WHERE source=? ORDER BY key",
            arrayOf("migration-test")
        ).use { cursor ->
            while (cursor.moveToNext()) {
                restored[cursor.getString(0)] = LegacyPreferenceCodec.decode(
                    EncodedLegacyPreference(
                        LegacyPreferenceType.valueOf(cursor.getString(1)),
                        cursor.getString(2)
                    )
                )
            }
        }
        assertEquals(before, restored)

        helper.readableDatabase.rawQuery(
            "SELECT item_count,checksum_sha256 FROM migration_state WHERE source=?",
            arrayOf("migration-test")
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(before.size, it.getInt(0))
            assertEquals(64, it.getString(1).length)
        }
    }

    @Test fun repositoriesRoundTripDataAndNeverStorePlainSensitiveText() {
        val db = helper.writableDatabase
        val cipher = TestCipher()
        val employees = SqliteEmployeeRepository { db }
        val attendance = SqliteAttendanceRepository({ db }, cipher)
        val messages = SqliteMessageRepository({ db }, cipher)
        val settings = SqliteSettingsRepository { db }
        val activation = SqliteActivationRepository { db }
        val roles = SqliteRoleRepository { db }

        employees.upsert(Employee("e1", "s1", "MAIN", "Employee", enabled = true, updatedAt = 1L))
        assertEquals("Employee", employees.find("e1")?.name)

        val event = AttendanceEvent(
            id = "a1", employeeId = "e1", storeId = "s1", branchId = "MAIN",
            action = AttendanceAction.CHECK_IN, state = AttendanceState.ON_TIME,
            method = VerificationMethod.DEVICE_BIOMETRIC, occurredAt = 10L,
            shiftStart = 5L, shiftEnd = 20L, verified = true, evidence = "proof-secret"
        )
        assertTrue(attendance.insert(event))
        assertFalse(attendance.insert(event))
        assertEquals("proof-secret", attendance.find("a1")?.evidence)
        val storedEvidence = db.rawQuery("SELECT evidence_ciphertext FROM attendance WHERE id='a1'", null)
            .use { it.moveToFirst(); it.getBlob(0) }
        assertNotEquals("proof-secret", storedEvidence.toString(Charsets.UTF_8))

        val message = Message("m1", "owner", "e1", MessageChannel.LOCAL_BLE, "private-message", 30L)
        assertTrue(messages.insert(message))
        assertEquals("private-message", messages.find("m1")?.body)
        val storedBody = db.rawQuery("SELECT body_ciphertext FROM messages WHERE id='m1'", null)
            .use { it.moveToFirst(); it.getBlob(0) }
        assertNotEquals("private-message", storedBody.toString(Charsets.UTF_8))

        settings.put("store", "gps.enabled", SettingValue.Flag(true), 40L)
        assertEquals(SettingValue.Flag(true), settings.get("store", "gps.enabled"))
        roles.assign("owner", "s1", SystemRole.SYSTEM_OWNER, 50L)
        assertEquals(SystemRole.SYSTEM_OWNER, roles.roleFor("owner", "s1"))
        activation.replaceEncryptedRecord(byteArrayOf(1, 2, 3), 60L)
        assertArrayEquals(byteArrayOf(1, 2, 3), activation.encryptedRecord())
    }

    /** Test-only reversible cipher; production adapters must delegate to the existing AES-GCM vault. */
    private class TestCipher : PayloadCipher {
        override fun encrypt(plain: ByteArray): ByteArray = transform(plain)
        override fun decrypt(cipher: ByteArray): ByteArray = transform(cipher)
        private fun transform(value: ByteArray) = ByteArray(value.size) { index ->
            (value[index].toInt() xor 0x5a).toByte()
        }
    }
}
