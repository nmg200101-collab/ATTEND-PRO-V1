package com.attendpro.foundation.repository

import com.attendpro.foundation.domain.AttendanceEvent
import com.attendpro.foundation.domain.Employee
import com.attendpro.foundation.domain.Message
import com.attendpro.foundation.domain.MessageStatus
import com.attendpro.foundation.domain.SettingValue
import com.attendpro.foundation.domain.SystemRole

interface EmployeeRepository {
    fun find(id: String): Employee?
    fun upsert(value: Employee)
    fun forStore(storeId: String): List<Employee>
    fun setEnabled(id: String, enabled: Boolean, updatedAt: Long): Boolean
}

interface AttendanceRepository {
    fun find(id: String): AttendanceEvent?
    fun insert(value: AttendanceEvent): Boolean
    fun forEmployee(id: String, fromInclusive: Long, toExclusive: Long): List<AttendanceEvent>
    fun pendingSync(limit: Int = 100): List<AttendanceEvent>
    fun markSynced(id: String): Boolean
}

interface MessageRepository {
    fun find(id: String): Message?
    fun insert(value: Message): Boolean
    fun inbox(recipientId: String, limit: Int = 100): List<Message>
    fun updateStatus(id: String, status: MessageStatus, deliveredAt: Long? = null): Boolean
}

interface SettingsRepository {
    fun get(namespace: String, key: String): SettingValue?
    fun put(namespace: String, key: String, value: SettingValue, updatedAt: Long)
}

interface ActivationRepository {
    fun encryptedRecord(): ByteArray?
    fun replaceEncryptedRecord(value: ByteArray, updatedAt: Long)
}

interface RoleRepository {
    fun roleFor(subjectId: String, storeId: String): SystemRole?
    fun assign(subjectId: String, storeId: String, role: SystemRole, updatedAt: Long)
}

/** Implementations must use the existing Keystore/AES-GCM security layer. No plaintext fallback. */
interface PayloadCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(cipher: ByteArray): ByteArray
}
