package com.attendpro.foundation.backup

import android.content.Context
import com.attendpro.core.SecureTokenVault
import com.attendpro.core.StoreRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Base64

data class StoreBackupSummary(
    val employeeCount: Int,
    val attendanceCount: Int,
    val settingsCount: Int,
    val faceFileCount: Int
)

data class StoreBackupArtifact(
    val envelope: String,
    val metadata: BackupEnvelopeMetadata,
    val summary: StoreBackupSummary,
    val includesFaceFiles: Boolean
)

data class StoreBackupPreview(
    val metadata: BackupEnvelopeMetadata,
    val sourceStoreId: String,
    val storeName: String,
    val appVersion: String,
    val summary: StoreBackupSummary,
    val includesFaceFiles: Boolean
)

data class StoreRestoreResult(
    val backupId: String,
    val restoredAt: Long,
    val summary: StoreBackupSummary
)

/**
 * Backs up the current production data stores without exporting device-bound
 * activation tokens, Android Keystore keys, central owner credentials or sessions.
 */
class StoreBackupManager(context: Context) {
    private val appContext = context.applicationContext
    private val vault = SecureTokenVault(appContext)

    fun createEncryptedBackup(password: CharArray, appVersion: String, includeFaceFiles: Boolean): StoreBackupArtifact {
        val repo = StoreRepository(appContext)
        val createdAt = System.currentTimeMillis()
        val preferenceSections = JSONArray()
        PREF_NAMES.forEach { name ->
            preferenceSections.put(PreferenceBackupCodec.capture(appContext, name) { key -> isEligible(name, key) })
        }
        val employeesJson = vault.get(EMPLOYEE_SECRET_KEY).ifBlank { "[]" }
        val employees = validateEmployees(employeesJson)
        val files = if (includeFaceFiles) captureFaceFiles() else JSONArray()
        val events = runCatching {
            JSONArray(appContext.getSharedPreferences(STORE_PREFS, Context.MODE_PRIVATE).getString("events", "[]") ?: "[]").length()
        }.getOrDefault(0)
        val settingsCount = (0 until preferenceSections.length()).sumOf {
            preferenceSections.getJSONObject(it).getJSONArray("entries").length()
        }
        val summary = StoreBackupSummary(employees.length(), events, settingsCount, files.length())
        val payload = JSONObject()
            .put("schemaVersion", PAYLOAD_SCHEMA_VERSION)
            .put("role", "STORE")
            .put("createdAt", createdAt)
            .put("sourceStoreId", repo.storeId)
            .put("storeName", repo.storeName)
            .put("branchId", repo.branchId)
            .put("applicationId", appContext.packageName)
            .put("appVersion", appVersion.take(80))
            .put("preferences", preferenceSections)
            .put("secrets", JSONObject().put("employeesJson", employees.toString()))
            .put("files", files)
            .put("summary", summary.toJson())
            .toString()
        val envelope = EncryptedBackupCodec.encrypt(payload, password, "STORE", repo.storeId, createdAt)
        return StoreBackupArtifact(envelope, EncryptedBackupCodec.inspect(envelope), summary, includeFaceFiles)
    }

    fun preview(envelope: String, password: CharArray): StoreBackupPreview {
        val decoded = decodeAndValidate(envelope, password)
        return StoreBackupPreview(
            decoded.metadata,
            decoded.payload.getString("sourceStoreId"),
            decoded.payload.optString("storeName", "ATTEND PRO"),
            decoded.payload.optString("appVersion", ""),
            decoded.summary,
            decoded.files.isNotEmpty()
        )
    }

    @Synchronized
    fun restore(envelope: String, password: CharArray): StoreRestoreResult {
        val decoded = decodeAndValidate(envelope, password)
        val currentStoreId = StoreRepository(appContext).storeId
        val sourceStoreId = decoded.payload.getString("sourceStoreId")
        require(sourceStoreId == currentStoreId) {
            "Backup belongs to another store. Transfer and activate that store on this phone before restoring its data."
        }

        val preferenceRollback = mutableMapOf<Pair<String, String>, Any?>()
        decoded.preferences.forEach { (name, entries) ->
            val prefs = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
            entries.forEach { entry -> preferenceRollback[name to entry.key] = prefs.all[entry.key] }
        }
        val oldEmployees = vault.get(EMPLOYEE_SECRET_KEY)
        val fileRollback = decoded.files.associate { file ->
            val target = File(faceDirectory(), file.name)
            file.name to target.takeIf { it.isFile }?.readBytes()
        }

        try {
            decoded.preferences.forEach { (name, entries) ->
                val editor = appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                entries.forEach { PreferenceBackupCodec.put(editor, it) }
                check(editor.commit()) { "Could not restore app settings" }
            }
            decoded.files.forEach { writeFaceFileAtomically(it.name, it.bytes) }
            val adjustedEmployees = rewriteFaceReferences(decoded.employees, decoded.files.map { it.name }.toSet())
            vault.put(EMPLOYEE_SECRET_KEY, adjustedEmployees)
            check(vault.get(EMPLOYEE_SECRET_KEY) == adjustedEmployees) { "Could not protect restored employee data" }
        } catch (failure: Throwable) {
            preferenceRollback.entries.groupBy { it.key.first }.forEach { (name, values) ->
                val editor = appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                values.forEach { (nameAndKey, value) -> PreferenceBackupCodec.putRaw(editor, nameAndKey.second, value) }
                editor.commit()
            }
            if (oldEmployees.isBlank()) vault.remove(EMPLOYEE_SECRET_KEY) else vault.put(EMPLOYEE_SECRET_KEY, oldEmployees)
            fileRollback.forEach { (name, bytes) ->
                val target = File(faceDirectory(), name)
                if (bytes == null) target.delete() else runCatching { writeFaceFileAtomically(name, bytes) }
            }
            throw failure
        }

        val restoredAt = System.currentTimeMillis()
        appContext.getSharedPreferences(BACKUP_STATE_PREFS, Context.MODE_PRIVATE).edit()
            .putString("lastRestoredBackupId", decoded.metadata.backupId)
            .putLong("lastRestoredAt", restoredAt)
            .commit()
        return StoreRestoreResult(decoded.metadata.backupId, restoredAt, decoded.summary)
    }

    private data class FaceFile(val name: String, val bytes: ByteArray)
    private data class Decoded(
        val metadata: BackupEnvelopeMetadata,
        val payload: JSONObject,
        val preferences: List<Pair<String, List<PreferenceBackupCodec.Entry>>>,
        val employees: JSONArray,
        val files: List<FaceFile>,
        val summary: StoreBackupSummary
    )

    private fun decodeAndValidate(envelope: String, password: CharArray): Decoded {
        val metadata = EncryptedBackupCodec.inspect(envelope)
        val plain = EncryptedBackupCodec.decrypt(envelope, password, "STORE")
        val root = runCatching { JSONObject(plain) }.getOrElse { throw IllegalArgumentException("Invalid backup contents") }
        require(root.optInt("schemaVersion", -1) == PAYLOAD_SCHEMA_VERSION && root.optString("role") == "STORE") {
            "Unsupported ATTEND PRO data backup"
        }
        val sourceStoreId = root.optString("sourceStoreId").trim()
        require(sourceStoreId.startsWith("STORE-") && sourceStoreId.length <= 160) { "Invalid store identity in backup" }
        require(metadata.sourceIdHash == EncryptedBackupCodec.sourceIdHash(sourceStoreId)) { "Backup identity verification failed" }

        val preferencesJson = root.optJSONArray("preferences") ?: throw IllegalArgumentException("Backup settings are missing")
        require(preferencesJson.length() <= PREF_NAMES.size) { "Backup settings are invalid" }
        val usedNames = mutableSetOf<String>()
        val preferences = (0 until preferencesJson.length()).map { index ->
            PreferenceBackupCodec.decode(preferencesJson.getJSONObject(index), PREF_NAMES).also { (name, entries) ->
                require(usedNames.add(name)) { "Duplicate settings area in backup" }
                require(entries.all { isEligible(name, it.key) }) { "Backup attempts to restore protected device settings" }
            }
        }
        val employees = validateEmployees(root.optJSONObject("secrets")?.optString("employeesJson", "[]") ?: "[]")
        val files = validateFiles(root.optJSONArray("files") ?: JSONArray())
        val eventCount = preferences.firstOrNull { it.first == STORE_PREFS }?.second
            ?.firstOrNull { it.key == "events" && it.type == "STRING" }
            ?.let { runCatching { JSONArray(it.value as String).length() }.getOrDefault(0) } ?: 0
        val settingsCount = preferences.sumOf { it.second.size }
        val calculated = StoreBackupSummary(employees.length(), eventCount, settingsCount, files.size)
        val declared = root.optJSONObject("summary")?.let { json ->
            StoreBackupSummary(
                json.optInt("employees", -1),
                json.optInt("attendance", -1),
                json.optInt("settings", -1),
                json.optInt("faceFiles", -1)
            )
        }
        require(declared == null || declared == calculated) { "Backup summary validation failed" }
        return Decoded(metadata, root, preferences, employees, files, calculated)
    }

    private fun validateEmployees(raw: String): JSONArray {
        require(raw.toByteArray(Charsets.UTF_8).size <= 12 * 1024 * 1024) { "Employee data is too large" }
        val array = runCatching { JSONArray(raw) }.getOrElse { throw IllegalArgumentException("Invalid employee data") }
        require(array.length() <= 10_000) { "Employee limit exceeded" }
        val ids = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val id = item.optString("employeeId").trim()
            require(id.isNotBlank() && id.length <= 160 && ids.add(id.uppercase())) { "Invalid or duplicate employee in backup" }
        }
        return array
    }

    private fun captureFaceFiles(): JSONArray {
        val output = JSONArray()
        var total = 0
        val directory = faceDirectory()
        directory.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { file ->
            if (!safeFileName(file.name) || file.length() !in 1..MAX_FACE_FILE_BYTES.toLong()) return@forEach
            val bytes = file.readBytes()
            total += bytes.size
            require(total <= MAX_FACE_TOTAL_BYTES) { "Face profile files exceed the phone-backup limit" }
            output.put(JSONObject()
                .put("name", file.name)
                .put("size", bytes.size)
                .put("sha256", sha256Hex(bytes))
                .put("data", Base64.getEncoder().encodeToString(bytes)))
            bytes.fill(0)
        }
        return output
    }

    private fun validateFiles(array: JSONArray): List<FaceFile> {
        require(array.length() <= 2_000) { "Too many face files in backup" }
        var total = 0
        val names = mutableSetOf<String>()
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val name = item.optString("name")
            require(safeFileName(name) && names.add(name)) { "Unsafe face file name in backup" }
            val bytes = runCatching { Base64.getDecoder().decode(item.getString("data")) }
                .getOrElse { throw IllegalArgumentException("Invalid face file encoding") }
            require(bytes.size in 1..MAX_FACE_FILE_BYTES && item.optInt("size", -1) == bytes.size) { "Invalid face file size" }
            total += bytes.size
            require(total <= MAX_FACE_TOTAL_BYTES) { "Face files exceed the restore limit" }
            require(MessageDigest.isEqual(sha256Hex(bytes).toByteArray(), item.optString("sha256").toByteArray())) {
                "Face file integrity check failed"
            }
            FaceFile(name, bytes)
        }
    }

    private fun rewriteFaceReferences(employees: JSONArray, restoredNames: Set<String>): String {
        for (index in 0 until employees.length()) {
            val item = employees.getJSONObject(index)
            val old = item.optString("faceProfileRef")
            if (old.isBlank()) continue
            val name = File(old).name
            val target = File(faceDirectory(), name)
            if (safeFileName(name) && (name in restoredNames || target.isFile)) item.put("faceProfileRef", target.absolutePath)
            else item.put("faceProfileRef", "")
        }
        return employees.toString()
    }

    private fun writeFaceFileAtomically(name: String, bytes: ByteArray) {
        require(safeFileName(name))
        val directory = faceDirectory().apply { mkdirs() }
        val target = File(directory, name)
        val temporary = File(directory, ".$name.restore-${System.nanoTime()}")
        FileOutputStream(temporary).use { stream -> stream.write(bytes); stream.fd.sync() }
        check(temporary.renameTo(target) || runCatching {
            FileOutputStream(target).use { stream -> stream.write(bytes); stream.fd.sync() }
            temporary.delete()
            true
        }.getOrDefault(false)) { "Could not restore face file" }
    }

    private fun faceDirectory(): File = File(appContext.filesDir, "face_profiles")
    private fun safeFileName(name: String): Boolean = name.isNotBlank() && name.length <= 180 && name != "." && name != ".." &&
        '/' !in name && '\\' !in name && '\u0000' !in name

    private fun isEligible(preferenceName: String, key: String): Boolean {
        if (preferenceName != STORE_PREFS) return key.length <= 240
        if (key in PROTECTED_STORE_KEYS) return false
        if (PROTECTED_STORE_PREFIXES.any(key::startsWith)) return false
        if (key.startsWith("failed") || key.endsWith("LockUntil") || key.endsWith("SessionUntil") || key.endsWith("SessionToken")) return false
        return key.length <= 240
    }

    private fun StoreBackupSummary.toJson(): JSONObject = JSONObject()
        .put("employees", employeeCount).put("attendance", attendanceCount)
        .put("settings", settingsCount).put("faceFiles", faceFileCount)

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val PAYLOAD_SCHEMA_VERSION = 1
        private const val STORE_PREFS = "store_repository"
        private const val BACKUP_STATE_PREFS = "attend_pro_backup_state_v2"
        private const val EMPLOYEE_SECRET_KEY = "employees_json_v1980"
        private const val MAX_FACE_FILE_BYTES = 2 * 1024 * 1024
        private const val MAX_FACE_TOTAL_BYTES = 8 * 1024 * 1024
        private val PREF_NAMES = setOf(
            STORE_PREFS,
            "report_receiver_store",
            "attend_pro_appearance",
            "attend_home_template_1978",
            "store_owner_ui",
            "attend_owner_ui"
        )
        private val PROTECTED_STORE_KEYS = setOf(
            "storeId", "serverUrl", "tenantId", "pairingNonce", "pairingExpiresAt",
            "installationRole", "agentName", "approvalStatus", "ownerCodeHash",
            "systemPasswordHash", "lastSyncAt", "lastSyncMessage"
        )
        private val PROTECTED_STORE_PREFIXES = listOf(
            "central", "storeAdminSession", "ownerSession", "systemSession", "activation",
            "lastServer", "syncCursor", "serverAttendanceCursor"
        )

    }
}
