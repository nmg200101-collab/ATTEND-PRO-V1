package com.attendpro.foundation.repository

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.attendpro.foundation.domain.AttendanceAction
import com.attendpro.foundation.domain.AttendanceEvent
import com.attendpro.foundation.domain.AttendanceState
import com.attendpro.foundation.domain.Employee
import com.attendpro.foundation.domain.Message
import com.attendpro.foundation.domain.MessageChannel
import com.attendpro.foundation.domain.MessageStatus
import com.attendpro.foundation.domain.SettingValue
import com.attendpro.foundation.domain.SystemRole
import com.attendpro.foundation.domain.VerificationMethod
import java.util.Base64

typealias DatabaseProvider = () -> SQLiteDatabase

class SqliteEmployeeRepository(private val database: DatabaseProvider) : EmployeeRepository {
    override fun find(id: String): Employee? = database().query(
        "employees", EMPLOYEE_COLUMNS, "id=?", arrayOf(id), null, null, null, "1"
    ).use { if (it.moveToFirst()) it.toEmployee() else null }

    override fun upsert(value: Employee) {
        require(value.id.isNotBlank() && value.storeId.isNotBlank() && value.branchId.isNotBlank())
        val values = value.toValues()
        database().writeTransaction {
            if (update("employees", values, "id=?", arrayOf(value.id)) == 0) {
                check(insertOrThrow("employees", null, values) != -1L)
            }
        }
    }

    override fun forStore(storeId: String): List<Employee> = database().query(
        "employees", EMPLOYEE_COLUMNS, "store_id=?", arrayOf(storeId), null, null, "name COLLATE NOCASE, id"
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toEmployee()) } }

    override fun setEnabled(id: String, enabled: Boolean, updatedAt: Long): Boolean {
        val values = ContentValues().apply { put("enabled", enabled.flag); put("updated_at", updatedAt) }
        return database().update("employees", values, "id=?", arrayOf(id)) == 1
    }
}

class SqliteAttendanceRepository(
    private val database: DatabaseProvider,
    private val cipher: PayloadCipher
) : AttendanceRepository {
    override fun find(id: String): AttendanceEvent? = query("id=?", arrayOf(id), "1").firstOrNull()

    override fun insert(value: AttendanceEvent): Boolean {
        require(value.id.isNotBlank() && value.employeeId.isNotBlank())
        val evidence = value.evidence.takeIf(String::isNotEmpty)?.let(::encryptText)
        val values = ContentValues().apply {
            put("id", value.id)
            put("employee_id", value.employeeId)
            put("store_id", value.storeId)
            put("branch_id", value.branchId)
            put("action", value.action.name)
            put("state", value.state.name)
            put("method", value.method.name)
            put("occurred_at", value.occurredAt)
            put("shift_start", value.shiftStart)
            put("shift_end", value.shiftEnd)
            put("verified", value.verified.flag)
            if (evidence == null) putNull("evidence_ciphertext") else put("evidence_ciphertext", evidence)
            put("synced", value.synced.flag)
        }
        return database().insertWithOnConflict("attendance", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    override fun forEmployee(id: String, fromInclusive: Long, toExclusive: Long): List<AttendanceEvent> {
        require(fromInclusive >= 0 && toExclusive >= fromInclusive)
        return query(
            "employee_id=? AND occurred_at>=? AND occurred_at<?",
            arrayOf(id, fromInclusive.toString(), toExclusive.toString()),
            null
        )
    }

    override fun pendingSync(limit: Int): List<AttendanceEvent> = query(
        "synced=0", emptyArray(), limit.coerceIn(1, 1_000).toString(), "occurred_at, id"
    )

    override fun markSynced(id: String): Boolean = database().update(
        "attendance", ContentValues().apply { put("synced", 1) }, "id=? AND synced=0", arrayOf(id)
    ) == 1

    private fun query(
        selection: String,
        arguments: Array<String>,
        limit: String?,
        order: String = "occurred_at DESC, id"
    ): List<AttendanceEvent> = database().query(
        "attendance", ATTENDANCE_COLUMNS, selection, arguments, null, null, order, limit
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toAttendance(cipher)) } }

    private fun encryptText(value: String): ByteArray {
        val plain = value.toByteArray(Charsets.UTF_8)
        return try { cipher.encrypt(plain) } finally { plain.fill(0) }
    }
}

class SqliteMessageRepository(
    private val database: DatabaseProvider,
    private val cipher: PayloadCipher
) : MessageRepository {
    override fun find(id: String): Message? = query("id=?", arrayOf(id), "1").firstOrNull()

    override fun insert(value: Message): Boolean {
        require(value.id.isNotBlank() && value.senderId.isNotBlank() && value.recipientId.isNotBlank())
        val plain = value.body.toByteArray(Charsets.UTF_8)
        val encrypted = try { cipher.encrypt(plain) } finally { plain.fill(0) }
        val values = ContentValues().apply {
            put("id", value.id)
            put("sender_id", value.senderId)
            put("recipient_id", value.recipientId)
            put("channel", value.channel.name)
            put("status", value.status.name)
            put("body_ciphertext", encrypted)
            put("created_at", value.createdAt)
            if (value.deliveredAt == null) putNull("delivered_at") else put("delivered_at", value.deliveredAt)
            if (value.repliedToId == null) putNull("replied_to_id") else put("replied_to_id", value.repliedToId)
        }
        return database().insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    override fun inbox(recipientId: String, limit: Int): List<Message> = query(
        "recipient_id=?", arrayOf(recipientId), limit.coerceIn(1, 1_000).toString()
    )

    override fun updateStatus(id: String, status: MessageStatus, deliveredAt: Long?): Boolean {
        val values = ContentValues().apply {
            put("status", status.name)
            if (deliveredAt != null) put("delivered_at", deliveredAt)
            else if (status == MessageStatus.QUEUED || status == MessageStatus.SENDING || status == MessageStatus.FAILED) putNull("delivered_at")
        }
        return database().update("messages", values, "id=?", arrayOf(id)) == 1
    }

    private fun query(selection: String, arguments: Array<String>, limit: String?): List<Message> = database().query(
        "messages", MESSAGE_COLUMNS, selection, arguments, null, null, "created_at DESC, id", limit
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toMessage(cipher)) } }
}

class SqliteSettingsRepository(private val database: DatabaseProvider) : SettingsRepository {
    override fun get(namespace: String, key: String): SettingValue? = database().query(
        "settings", arrayOf("value_type", "value"), "namespace=? AND key=?", arrayOf(namespace, key),
        null, null, null, "1"
    ).use {
        if (!it.moveToFirst()) null else SettingValueCodec.decode(it.getString(0), it.getString(1))
    }

    override fun put(namespace: String, key: String, value: SettingValue, updatedAt: Long) {
        require(namespace.isNotBlank() && key.isNotBlank() && updatedAt >= 0)
        val encoded = SettingValueCodec.encode(value)
        val values = ContentValues().apply {
            put("namespace", namespace)
            put("key", key)
            put("value_type", encoded.first)
            put("value", encoded.second)
            put("updated_at", updatedAt)
        }
        database().writeTransaction {
            if (update("settings", values, "namespace=? AND key=?", arrayOf(namespace, key)) == 0) {
                insertOrThrow("settings", null, values)
            }
        }
    }
}

class SqliteActivationRepository(private val database: DatabaseProvider) : ActivationRepository {
    override fun encryptedRecord(): ByteArray? = database().query(
        "activation", arrayOf("encrypted_record"), "id=1", null, null, null, null, "1"
    ).use { if (it.moveToFirst()) it.getBlob(0) else null }

    override fun replaceEncryptedRecord(value: ByteArray, updatedAt: Long) {
        require(value.isNotEmpty() && updatedAt >= 0)
        val values = ContentValues().apply { put("id", 1); put("encrypted_record", value); put("updated_at", updatedAt) }
        database().insertWithOnConflict("activation", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
}

class SqliteRoleRepository(private val database: DatabaseProvider) : RoleRepository {
    override fun roleFor(subjectId: String, storeId: String): SystemRole? = database().query(
        "role_assignments", arrayOf("role"), "subject_id=? AND store_id=?", arrayOf(subjectId, storeId),
        null, null, null, "1"
    ).use { if (it.moveToFirst()) enumValueOf<SystemRole>(it.getString(0)) else null }

    override fun assign(subjectId: String, storeId: String, role: SystemRole, updatedAt: Long) {
        require(subjectId.isNotBlank() && storeId.isNotBlank() && updatedAt >= 0)
        val values = ContentValues().apply {
            put("subject_id", subjectId); put("store_id", storeId); put("role", role.name); put("updated_at", updatedAt)
        }
        database().writeTransaction {
            if (update("role_assignments", values, "subject_id=? AND store_id=?", arrayOf(subjectId, storeId)) == 0) {
                insertOrThrow("role_assignments", null, values)
            }
        }
    }
}

private object SettingValueCodec {
    fun encode(value: SettingValue): Pair<String, String> = when (value) {
        is SettingValue.Text -> "STRING" to value.value
        is SettingValue.Integer -> "INTEGER" to value.value.toString()
        is SettingValue.LongNumber -> "LONG" to value.value.toString()
        is SettingValue.Decimal -> "FLOAT" to value.value.toRawBits().toString()
        is SettingValue.Flag -> "BOOLEAN" to if (value.value) "1" else "0"
        is SettingValue.TextSet -> "STRING_SET" to value.value.sorted().joinToString(",") {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8))
        }
    }

    fun decode(type: String, value: String): SettingValue = when (type) {
        "STRING" -> SettingValue.Text(value)
        "INTEGER" -> SettingValue.Integer(value.toInt())
        "LONG" -> SettingValue.LongNumber(value.toLong())
        "FLOAT" -> SettingValue.Decimal(Float.fromBits(value.toInt()))
        "BOOLEAN" -> SettingValue.Flag(when (value) {
            "1" -> true
            "0" -> false
            else -> error("Invalid encoded boolean setting")
        })
        "STRING_SET" -> SettingValue.TextSet(if (value.isEmpty()) emptySet() else value.split(',').mapTo(linkedSetOf()) {
            Base64.getUrlDecoder().decode(it).toString(Charsets.UTF_8)
        })
        else -> error("Unsupported setting type: $type")
    }
}

private fun Employee.toValues() = ContentValues().apply {
    put("id", id); put("store_id", storeId); put("branch_id", branchId); put("name", name)
    put("phone", phone); put("job_title", jobTitle); put("enabled", enabled.flag); put("updated_at", updatedAt)
}

private fun Cursor.toEmployee() = Employee(
    id = getString(0), storeId = getString(1), branchId = getString(2), name = getString(3),
    phone = getString(4), jobTitle = getString(5), enabled = getInt(6) == 1, updatedAt = getLong(7)
)

private fun Cursor.toAttendance(cipher: PayloadCipher): AttendanceEvent {
    val encrypted = if (isNull(11)) null else getBlob(11)
    val evidence = encrypted?.let { cipherText ->
        val plain = cipher.decrypt(cipherText)
        try { plain.toString(Charsets.UTF_8) } finally { plain.fill(0) }
    }.orEmpty()
    return AttendanceEvent(
        id = getString(0), employeeId = getString(1), storeId = getString(2), branchId = getString(3),
        action = enumValueOf<AttendanceAction>(getString(4)),
        state = enumValueOf<AttendanceState>(getString(5)),
        method = enumValueOf<VerificationMethod>(getString(6)),
        occurredAt = getLong(7), shiftStart = getLong(8), shiftEnd = getLong(9), verified = getInt(10) == 1,
        evidence = evidence, synced = getInt(12) == 1
    )
}

private fun Cursor.toMessage(cipher: PayloadCipher): Message {
    val plain = cipher.decrypt(getBlob(5))
    val body = try { plain.toString(Charsets.UTF_8) } finally { plain.fill(0) }
    return Message(
        id = getString(0), senderId = getString(1), recipientId = getString(2),
        channel = enumValueOf<MessageChannel>(getString(3)), status = enumValueOf<MessageStatus>(getString(4)),
        body = body, createdAt = getLong(6), deliveredAt = if (isNull(7)) null else getLong(7),
        repliedToId = if (isNull(8)) null else getString(8)
    )
}

private inline fun <T> SQLiteDatabase.writeTransaction(block: SQLiteDatabase.() -> T): T {
    beginTransaction()
    return try {
        val result = block()
        setTransactionSuccessful()
        result
    } finally {
        endTransaction()
    }
}

private val Boolean.flag: Int get() = if (this) 1 else 0

private val EMPLOYEE_COLUMNS = arrayOf("id", "store_id", "branch_id", "name", "phone", "job_title", "enabled", "updated_at")
private val ATTENDANCE_COLUMNS = arrayOf(
    "id", "employee_id", "store_id", "branch_id", "action", "state", "method", "occurred_at",
    "shift_start", "shift_end", "verified", "evidence_ciphertext", "synced"
)
private val MESSAGE_COLUMNS = arrayOf(
    "id", "sender_id", "recipient_id", "channel", "status", "body_ciphertext", "created_at", "delivered_at", "replied_to_id"
)
