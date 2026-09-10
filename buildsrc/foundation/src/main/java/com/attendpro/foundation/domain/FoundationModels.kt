package com.attendpro.foundation.domain

import java.time.ZoneId

enum class SystemRole { SYSTEM_OWNER, CENTRAL_AGENT, STORE_MANAGER, EMPLOYEE }

enum class AttendanceAction { CHECK_IN, CHECK_OUT, PRESENCE_PROOF }

enum class AttendanceState {
    ON_TIME,
    LATE,
    EARLY_DEPARTURE,
    COMPLETED,
    PROOF_ACCEPTED,
    REJECTED
}

/**
 * Domain names are persisted as text, never by ordinal, so adding a method cannot reinterpret
 * existing attendance records.
 */
enum class VerificationMethod {
    EXTERNAL_FINGERPRINT,
    DEVICE_BIOMETRIC,
    PHONE_FINGERPRINT,
    PHONE_BIOMETRIC,
    FACE,
    VOICE,
    PIN,
    PATTERN,
    PASSWORD,
    QR,
    BLE_PROXIMITY,
    WIFI_PROXIMITY,
    GPS_GEOFENCE,
    SUPERVISOR_OVERRIDE,
    MANUAL_ADMIN
}

enum class MessageChannel { CLOUD, LOCAL_BLE }
enum class MessageStatus { QUEUED, SENDING, SENT, DELIVERED, FAILED, REPLIED }

data class ShiftPolicy(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val graceMinutes: Int = 0,
    val checkoutTailMinutes: Int = 120,
    val zoneId: String
) {
    init {
        require(startHour in 0..23 && endHour in 0..23) { "Shift hour must be 0..23" }
        require(startMinute in 0..59 && endMinute in 0..59) { "Shift minute must be 0..59" }
        require(graceMinutes in 0..1_440) { "Grace must be 0..1440 minutes" }
        require(checkoutTailMinutes in 0..1_440) { "Checkout tail must be 0..1440 minutes" }
        require(zoneId.isNotBlank()) { "Zone ID is required" }
        require(runCatching { ZoneId.of(zoneId) }.isSuccess) { "Unknown Zone ID: $zoneId" }
    }
}

data class Employee(
    val id: String,
    val storeId: String,
    val branchId: String,
    val name: String,
    val phone: String = "",
    val jobTitle: String = "",
    val enabled: Boolean,
    val updatedAt: Long
)

data class AttendanceEvent(
    val id: String,
    val employeeId: String,
    val storeId: String,
    val branchId: String,
    val action: AttendanceAction,
    val state: AttendanceState,
    val method: VerificationMethod,
    val occurredAt: Long,
    val shiftStart: Long,
    val shiftEnd: Long,
    val verified: Boolean,
    /** Plain evidence exists only in memory; repositories must encrypt it before persistence. */
    val evidence: String = "",
    val synced: Boolean = false
)

data class Message(
    val id: String,
    val senderId: String,
    val recipientId: String,
    val channel: MessageChannel,
    /** Plain message text exists only in memory; repositories must encrypt it at rest. */
    val body: String,
    val createdAt: Long,
    val status: MessageStatus = MessageStatus.QUEUED,
    val deliveredAt: Long? = null,
    val repliedToId: String? = null
)

sealed interface SettingValue {
    data class Text(val value: String) : SettingValue
    data class Integer(val value: Int) : SettingValue
    data class LongNumber(val value: Long) : SettingValue
    data class Decimal(val value: Float) : SettingValue
    data class Flag(val value: Boolean) : SettingValue
    data class TextSet(val value: Set<String>) : SettingValue
}
