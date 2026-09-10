package com.attendpro.core

import org.json.JSONObject
import java.util.UUID

enum class AttendanceMethod {
    EXTERNAL_FINGERPRINT,
    PHONE_BLE_BIOMETRIC,
    PHONE_FINGERPRINT,
    PHONE_PROXIMITY,
    PHONE_BIOMETRIC,
    SHARED_DEVICE_FACE,
    VOICE_PHRASE,
    GPS,
    PIN,
    PASSWORD,
    PATTERN,
    SUPERVISOR_OVERRIDE,
    MANUAL_ADMIN
}

enum class AttendanceAction { CHECK_IN, CHECK_OUT }

data class PresenceEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val employeeId: String,
    val employeeName: String,
    val timestampEpochMillis: Long,
    val channel: String,
    val rssi: Int,
    val details: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("eventId", eventId); put("employeeId", employeeId); put("employeeName", employeeName)
        put("timestampEpochMillis", timestampEpochMillis); put("channel", channel); put("rssi", rssi); put("details", details)
    }
    companion object {
        fun fromJson(obj: JSONObject) = PresenceEvent(
            eventId = obj.optString("eventId", UUID.randomUUID().toString()), employeeId = obj.getString("employeeId"),
            employeeName = obj.optString("employeeName", ""), timestampEpochMillis = obj.getLong("timestampEpochMillis"),
            channel = obj.optString("channel", "غير محدد"), rssi = obj.optInt("rssi", -127), details = obj.optString("details", "")
        )
    }
}

data class AttendanceEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val employeeId: String,
    val employeeName: String = "",
    val branchId: String,
    val timestampEpochMillis: Long,
    val action: AttendanceAction,
    val method: AttendanceMethod,
    val deviceId: String,
    val verified: Boolean,
    val evidence: String = "",
    val synced: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("eventId", eventId)
        put("employeeId", employeeId)
        put("employeeName", employeeName)
        put("branchId", branchId)
        put("timestampEpochMillis", timestampEpochMillis)
        put("action", action.name)
        put("method", method.name)
        put("deviceId", deviceId)
        put("verified", verified)
        put("evidence", evidence)
        put("synced", synced)
    }

    companion object {
        fun fromJson(obj: JSONObject): AttendanceEvent = AttendanceEvent(
            eventId = obj.optString("eventId", UUID.randomUUID().toString()),
            employeeId = obj.getString("employeeId"),
            employeeName = obj.optString("employeeName", ""),
            branchId = obj.optString("branchId", "MAIN"),
            timestampEpochMillis = obj.getLong("timestampEpochMillis"),
            action = AttendanceAction.valueOf(obj.getString("action")),
            method = AttendanceMethod.valueOf(obj.getString("method")),
            deviceId = obj.optString("deviceId", "unknown"),
            verified = obj.optBoolean("verified", false),
            evidence = obj.optString("evidence", ""),
            synced = obj.optBoolean("synced", false)
        )
    }
}

data class PairedEmployee(
    val employeeId: String,
    val displayName: String,
    val branchId: String,
    val pairingSecret: String,
    val pin: String = "",
    val phone: String = "",
    val jobTitle: String = "",
    val externalFingerprintId: String = "",
    val faceProfileRef: String = "",
    val companionEnabled: Boolean = true,
    val active: Boolean = true,
    val department: String = "",
    val nationalId: String = "",
    val hireDate: String = "",
    val notes: String = "",
    val faceCapturedAt: Long = 0L,
    val passwordHash: String = "",
    val patternHash: String = "",
    val voicePhraseHash: String = "",
    val allowedMethods: Set<String> = emptySet(),
    val useCustomShift: Boolean = false,
    val shiftStartHour: Int = 8,
    val shiftStartMinute: Int = 0,
    val shiftEndHour: Int = 16,
    val shiftEndMinute: Int = 0,
    val faceTemplate: String = "",
    val faceQualityScore: Int = 0,
    val voiceTemplate: String = "",
    val voiceQualityScore: Int = 0,
    val voicePhraseText: String = "",
    // Smart late-attendance policy. Negative minute values inherit the Store defaults.
    val lateAlertEnabled: Boolean = true,
    val lateGraceMinutes: Int = -1,
    val lateFirstAlertDelayMinutes: Int = -1,
    val lateAlertCount: Int = 3,
    val lateRepeatMinutes: Int = -1,
    // NOTIFICATION | NOTIFICATION_VOICE
    val lateAlertMode: String = "NOTIFICATION_VOICE",
    // NONE | DIAL | AUTO_IF_ALLOWED
    val lateCallMode: String = "NONE",
    val presenceReminderMinutes: Int = 3
) {
    fun allows(method: AttendanceMethod): Boolean =
        allowedMethods.isEmpty() || method.name in allowedMethods
}

interface FingerprintDeviceAdapter {
    val vendorName: String
    suspend fun connect(): Result<Unit>
    suspend fun pullEvents(): Result<List<AttendanceEvent>>
    suspend fun disconnect()
}
