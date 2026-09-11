package com.attendpro.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class EmployeeIdentityStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("employee_identity", Context.MODE_PRIVATE)
    private val secureVault = SecureTokenVault(appContext)

    var employeeId: String get() = prefs.getString("employeeId", "") ?: ""; set(value) = prefs.edit().putString("employeeId", value.trim()).apply()
    var displayName: String get() = prefs.getString("displayName", "") ?: ""; set(value) = prefs.edit().putString("displayName", value.trim()).apply()
    var branchId: String get() = prefs.getString("branchId", "MAIN") ?: "MAIN"; set(value) = prefs.edit().putString("branchId", value.trim().ifBlank { "MAIN" }).apply()
    var phone: String get() = prefs.getString("phone", "") ?: ""; set(value) = prefs.edit().putString("phone", value.trim()).apply()
    var jobTitle: String get() = prefs.getString("jobTitle", "") ?: ""; set(value) = prefs.edit().putString("jobTitle", value.trim()).apply()
    var pin: String get() = prefs.getString("pin", "") ?: ""; set(value) = prefs.edit().putString("pin", value.trim()).apply()
    var pairingSecret: String
        get() {
            val secured = secureVault.get("employee_pairing_secret_v1980")
            if (SecretCodec.isValid(secured)) return secured
            val legacy = prefs.getString("pairingSecret", "") ?: ""
            if (SecretCodec.isValid(legacy)) {
                secureVault.put("employee_pairing_secret_v1980", legacy)
                if (secureVault.get("employee_pairing_secret_v1980") == legacy) prefs.edit().remove("pairingSecret").apply()
                return legacy
            }
            val generated = SecretCodec.encode(SecretCodec.generate())
            secureVault.put("employee_pairing_secret_v1980", generated)
            return generated
        }
        set(value) {
            if (SecretCodec.isValid(value)) {
                secureVault.put("employee_pairing_secret_v1980", value)
                if (secureVault.get("employee_pairing_secret_v1980") == value) prefs.edit().remove("pairingSecret").apply()
            }
        }

    var trustedStoreId: String get() = prefs.getString("trustedStoreId", "") ?: ""; set(value) = prefs.edit().putString("trustedStoreId", value.trim()).apply()
    var trustedStoreName: String get() = prefs.getString("trustedStoreName", "") ?: ""; set(value) = prefs.edit().putString("trustedStoreName", value.trim()).apply()
    var trustedStoreBranch: String get() = prefs.getString("trustedStoreBranch", "") ?: ""; set(value) = prefs.edit().putString("trustedStoreBranch", value.trim()).apply()
    var trustedStoreLatitude: Double get() = java.lang.Double.longBitsToDouble(prefs.getLong("trustedStoreLatitude", java.lang.Double.doubleToRawLongBits(Double.NaN))); set(value) = prefs.edit().putLong("trustedStoreLatitude", java.lang.Double.doubleToRawLongBits(value)).apply()
    var trustedStoreLongitude: Double get() = java.lang.Double.longBitsToDouble(prefs.getLong("trustedStoreLongitude", java.lang.Double.doubleToRawLongBits(Double.NaN))); set(value) = prefs.edit().putLong("trustedStoreLongitude", java.lang.Double.doubleToRawLongBits(value)).apply()
    var trustedStoreGpsRadius: Int get() = prefs.getInt("trustedStoreGpsRadius", 25); set(value) = prefs.edit().putInt("trustedStoreGpsRadius", value.coerceIn(10, 5000)).apply()
    var allowedMethods: Set<String> get() = prefs.getStringSet("allowedMethods", emptySet())?.toSet() ?: emptySet(); set(value) = prefs.edit().putStringSet("allowedMethods", value).apply()
    var shiftStartHour: Int get() = prefs.getInt("shiftStartHour", 8); set(value) = prefs.edit().putInt("shiftStartHour", value.coerceIn(0,23)).apply()
    var shiftStartMinute: Int get() = prefs.getInt("shiftStartMinute", 0); set(value) = prefs.edit().putInt("shiftStartMinute", value.coerceIn(0,59)).apply()
    var shiftEndHour: Int get() = prefs.getInt("shiftEndHour", 16); set(value) = prefs.edit().putInt("shiftEndHour", value.coerceIn(0,23)).apply()
    var shiftEndMinute: Int get() = prefs.getInt("shiftEndMinute", 0); set(value) = prefs.edit().putInt("shiftEndMinute", value.coerceIn(0,59)).apply()
    val isTrustedStoreGpsConfigured: Boolean get() = trustedStoreLatitude.isFinite() && trustedStoreLongitude.isFinite()
    fun allows(method: AttendanceMethod): Boolean = allowedMethods.isEmpty() || method.name in allowedMethods
    var pendingNonce: String get() = prefs.getString("pendingNonce", "") ?: ""; set(value) = prefs.edit().putString("pendingNonce", value.trim()).apply()
    var pendingExpiresAt: Long get() = prefs.getLong("pendingExpiresAt", 0L); set(value) = prefs.edit().putLong("pendingExpiresAt", value).apply()
    var autoPresence: Boolean get() = prefs.getBoolean("autoPresence", true); set(value) = prefs.edit().putBoolean("autoPresence", value).apply()
    var confirmationPinHash: String get() = prefs.getString("confirmationPinHash", "") ?: ""; set(value) = prefs.edit().putString("confirmationPinHash", value).apply()
    var confirmationPasswordHash: String get() = prefs.getString("confirmationPasswordHash", "") ?: ""; set(value) = prefs.edit().putString("confirmationPasswordHash", value).apply()
    val installationId: String get() {
        val old = prefs.getString("installationId", "") ?: ""
        if (old.isNotBlank()) return old
        val id = "EMPDEV-${UUID.randomUUID()}"
        prefs.edit().putString("installationId", id).apply()
        return id
    }
    var serverUrl: String get() = prefs.getString("serverUrl", "https://attend-pro-central.nmg200101.workers.dev") ?: "https://attend-pro-central.nmg200101.workers.dev"; set(value) = prefs.edit().putString("serverUrl", value.trim()).apply()
    var serverLinked: Boolean get() = prefs.getBoolean("serverLinked", false); set(value) = prefs.edit().putBoolean("serverLinked", value).apply()
    var linkedAt: Long get() = prefs.getLong("linkedAt", 0L); set(value) = prefs.edit().putLong("linkedAt", value).apply()
    var localPairingConfirmedAt: Long get() = prefs.getLong("localPairingConfirmedAt", 0L); set(value) = prefs.edit().putLong("localPairingConfirmedAt", value.coerceAtLeast(0L)).apply()
    var localPairingChannel: String get() = prefs.getString("localPairingChannel", "") ?: ""; set(value) = prefs.edit().putString("localPairingChannel", value.trim()).apply()
    var pendingChallengeId: String get() = prefs.getString("pendingChallengeId", "") ?: ""; set(value) = prefs.edit().putString("pendingChallengeId", value).apply()
    var pendingChallengeMethod: String get() = prefs.getString("pendingChallengeMethod", "") ?: ""; set(value) = prefs.edit().putString("pendingChallengeMethod", value).apply()
    var pendingChallengeExpiresAt: Long get() = prefs.getLong("pendingChallengeExpiresAt", 0L); set(value) = prefs.edit().putLong("pendingChallengeExpiresAt", value).apply()
    var pendingChallengeAction: String get() = prefs.getString("pendingChallengeAction", AttendanceAction.CHECK_IN.name) ?: AttendanceAction.CHECK_IN.name; set(value) = prefs.edit().putString("pendingChallengeAction", value).apply()
    var lastBleDirectSeenAt: Long get() = prefs.getLong("lastBleDirectSeenAt", 0L); set(value) = prefs.edit().putLong("lastBleDirectSeenAt", value.coerceAtLeast(0L)).apply()
    var lastBleDirectState: String get() = prefs.getString("lastBleDirectState", "") ?: ""; set(value) = prefs.edit().putString("lastBleDirectState", value).apply()
    var lastBleAdvertisingState: String get() = prefs.getString("lastBleAdvertisingState", "") ?: ""; set(value) = prefs.edit().putString("lastBleAdvertisingState", value.take(300)).apply()
    var lastBleAdvertisingAt: Long get() = prefs.getLong("lastBleAdvertisingAt", 0L); set(value) = prefs.edit().putLong("lastBleAdvertisingAt", value.coerceAtLeast(0L)).apply()
    var presenceServiceHeartbeatAt: Long get() = prefs.getLong("presenceServiceHeartbeatAt", 0L); set(value) = prefs.edit().putLong("presenceServiceHeartbeatAt", value.coerceAtLeast(0L)).apply()
    var lastLanStoreSeenAt: Long get() = prefs.getLong("lastLanStoreSeenAt", 0L); set(value) = prefs.edit().putLong("lastLanStoreSeenAt", value.coerceAtLeast(0L)).apply()
    var lastLanState: String get() = prefs.getString("lastLanState", "") ?: ""; set(value) = prefs.edit().putString("lastLanState", value).apply()
    var lastGpsInsideAt: Long get() = prefs.getLong("lastGpsInsideAt", 0L); set(value) = prefs.edit().putLong("lastGpsInsideAt", value.coerceAtLeast(0L)).apply()
    var lastGpsDistanceMeters: Int get() = prefs.getInt("lastGpsDistanceMeters", -1); set(value) = prefs.edit().putInt("lastGpsDistanceMeters", value).apply()
    var lastGpsAccuracyMeters: Int get() = prefs.getInt("lastGpsAccuracyMeters", -1); set(value) = prefs.edit().putInt("lastGpsAccuracyMeters", value.coerceAtLeast(-1)).apply()
    var lastGpsObservedAt: Long get() = prefs.getLong("lastGpsObservedAt", 0L); set(value) = prefs.edit().putLong("lastGpsObservedAt", value.coerceAtLeast(0L)).apply()
    var lastGpsEnteredAt: Long get() = prefs.getLong("lastGpsEnteredAt", 0L); set(value) = prefs.edit().putLong("lastGpsEnteredAt", value.coerceAtLeast(0L)).apply()
    var lastGpsExitedAt: Long get() = prefs.getLong("lastGpsExitedAt", 0L); set(value) = prefs.edit().putLong("lastGpsExitedAt", value.coerceAtLeast(0L)).apply()
    var lastGpsState: String get() = prefs.getString("lastGpsState", "UNKNOWN") ?: "UNKNOWN"; set(value) = prefs.edit().putString("lastGpsState", value.take(20)).apply()
    var lastGpsServerUploadAt: Long get() = prefs.getLong("lastGpsServerUploadAt", 0L); set(value) = prefs.edit().putLong("lastGpsServerUploadAt", value.coerceAtLeast(0L)).apply()
    var employeeVoicePromptsEnabled: Boolean get() = prefs.getBoolean("employeeVoicePromptsEnabled", true); set(value) = prefs.edit().putBoolean("employeeVoicePromptsEnabled", value).apply()
    var geoArrivalAlertsEnabled: Boolean get() = prefs.getBoolean("geoArrivalAlertsEnabled", true); set(value) = prefs.edit().putBoolean("geoArrivalAlertsEnabled", value).apply()
    var employeeRequestVoiceText: String get() = prefs.getString("employeeRequestVoiceText", "{name}، يرجى إثبات حضورك") ?: ""; set(value) = prefs.edit().putString("employeeRequestVoiceText", value).apply()
    var employeeMissingProofVoiceText: String get() = prefs.getString("employeeMissingProofVoiceText", "{name}، لم يتم إثبات حضورك، يرجى إثبات الحضور الآن") ?: ""; set(value) = prefs.edit().putString("employeeMissingProofVoiceText", value).apply()
    var employeeLateVoiceText: String get() = prefs.getString("employeeLateVoiceText", "موعد دوامك بدأ ولم يتم إثبات حضورك") ?: ""; set(value) = prefs.edit().putString("employeeLateVoiceText", value).apply()
    var employeeVoiceRatePercent: Int get() = prefs.getInt("employeeVoiceRatePercent", 92); set(value) = prefs.edit().putInt("employeeVoiceRatePercent", value.coerceIn(50, 150)).apply()
    var employeeVoiceVolumePercent: Int get() = prefs.getInt("employeeVoiceVolumePercent", 100); set(value) = prefs.edit().putInt("employeeVoiceVolumePercent", value.coerceIn(0, 100)).apply()
    var employeeMessageNotificationsEnabled: Boolean get() = prefs.getBoolean("employeeMessageNotificationsEnabled", true); set(value) = prefs.edit().putBoolean("employeeMessageNotificationsEnabled", value).apply()
    fun wasMessageNotified1975(messageId: String): Boolean = prefs.getStringSet("messageNotified1975", emptySet())?.contains(messageId) == true
    fun markMessageNotified1975(messageId: String) {
        if (messageId.isBlank()) return
        val old = prefs.getStringSet("messageNotified1975", emptySet())?.toMutableSet() ?: mutableSetOf()
        old += messageId
        while (old.size > 120) old.remove(old.first())
        prefs.edit().putStringSet("messageNotified1975", old).apply()
    }
    var lateAlertEnabled: Boolean get() = prefs.getBoolean("lateAlertEnabled", true); set(value) = prefs.edit().putBoolean("lateAlertEnabled", value).apply()
    var lateGraceMinutes: Int get() = prefs.getInt("lateGraceMinutes", 10); set(value) = prefs.edit().putInt("lateGraceMinutes", value.coerceIn(0, 120)).apply()
    var lateFirstAlertDelayMinutes: Int get() = prefs.getInt("lateFirstAlertDelayMinutes", 0); set(value) = prefs.edit().putInt("lateFirstAlertDelayMinutes", value.coerceIn(0, 180)).apply()
    var lateAlertCount: Int get() = prefs.getInt("lateAlertCount", 3); set(value) = prefs.edit().putInt("lateAlertCount", value.coerceIn(1, 10)).apply()
    var lateRepeatMinutes: Int get() = prefs.getInt("lateRepeatMinutes", 15); set(value) = prefs.edit().putInt("lateRepeatMinutes", value.coerceIn(5, 180)).apply()
    var lateAlertMode: String get() = prefs.getString("lateAlertMode", "NOTIFICATION_VOICE") ?: "NOTIFICATION_VOICE"; set(value) = prefs.edit().putString("lateAlertMode", value).apply()
    var lastPresenceProofAt: Long get() = prefs.getLong("lastPresenceProofAt", 0L); set(value) = prefs.edit().putLong("lastPresenceProofAt", value.coerceAtLeast(0L)).apply()

    /** Persisted replay cache: a completed or accepted challenge ID cannot be accepted again before expiry. */
    @Synchronized fun acceptChallengeOnce(challengeId: String, expiresAt: Long): Boolean {
        val id = challengeId.trim()
        val now = System.currentTimeMillis()
        if (id.isBlank() || expiresAt <= now) return false
        val raw = prefs.getString("challengeReplayCache", "[]") ?: "[]"
        val items = mutableListOf<Pair<String, Long>>()
        runCatching {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val cachedId = o.optString("id", "")
                val exp = o.optLong("e", 0L)
                if (cachedId.isNotBlank() && exp > now) items += cachedId to exp
            }
        }
        if (items.any { it.first == id }) return false
        items += id to expiresAt
        val out = JSONArray()
        items.sortedByDescending { it.second }.take(64).forEach { (cachedId, exp) ->
            out.put(JSONObject().put("id", cachedId).put("e", exp))
        }
        prefs.edit().putString("challengeReplayCache", out.toString()).apply()
        return true
    }

    val isConfigured: Boolean get() = employeeId.isNotBlank() && displayName.isNotBlank() && trustedStoreId.isNotBlank()
    val hasPendingInvite: Boolean get() = trustedStoreId.isNotBlank() && pendingNonce.isNotBlank() && pendingExpiresAt > System.currentTimeMillis()

    fun applyProvision(provision: PairingProtocol.EmployeeProvision) {
        trustedStoreId = provision.storeId
        trustedStoreName = provision.storeName
        trustedStoreBranch = provision.branchId
        employeeId = provision.employeeId
        displayName = provision.displayName
        branchId = provision.branchId
        phone = provision.phone
        jobTitle = provision.jobTitle
        pairingSecret = provision.pairingSecret
        trustedStoreLatitude = provision.storeLatitude
        trustedStoreLongitude = provision.storeLongitude
        trustedStoreGpsRadius = provision.gpsRadiusMeters
        allowedMethods = provision.allowedMethods
        shiftStartHour = provision.shiftStartHour; shiftStartMinute = provision.shiftStartMinute; shiftEndHour = provision.shiftEndHour; shiftEndMinute = provision.shiftEndMinute
        employeeVoicePromptsEnabled = provision.employeeVoicePromptsEnabled; geoArrivalAlertsEnabled = provision.geoArrivalAlertsEnabled
        employeeRequestVoiceText = provision.employeeRequestVoiceText; employeeMissingProofVoiceText = provision.employeeMissingProofVoiceText; employeeLateVoiceText = provision.employeeLateVoiceText
        employeeVoiceRatePercent = provision.employeeVoiceRatePercent; employeeVoiceVolumePercent = provision.employeeVoiceVolumePercent
        lateAlertEnabled = provision.lateAlertEnabled; lateGraceMinutes = provision.lateGraceMinutes; lateFirstAlertDelayMinutes = provision.lateFirstAlertDelayMinutes
        lateAlertCount = provision.lateAlertCount; lateRepeatMinutes = provision.lateRepeatMinutes; lateAlertMode = provision.lateAlertMode
        // 1.9.40 transports the owner-configured password separately from deprecated PIN data.
        confirmationPasswordHash = provision.passwordHash
        confirmationPinHash = ""
        serverUrl = provision.serverUrl
        serverLinked = false
        pin = ""
        pendingNonce = ""
        pendingExpiresAt = 0L
    }

    fun acceptInvite(invite: PairingProtocol.StoreInvite) {
        trustedStoreId = invite.storeId; trustedStoreName = invite.storeName; trustedStoreBranch = invite.branchId
        pendingNonce = invite.nonce; pendingExpiresAt = invite.expiresAt
    }

    fun clearStoreLink() {
        trustedStoreId = ""; trustedStoreName = ""; trustedStoreBranch = ""; pendingNonce = ""; pendingExpiresAt = 0L
        trustedStoreLatitude = Double.NaN; trustedStoreLongitude = Double.NaN; trustedStoreGpsRadius = 25; allowedMethods = emptySet()
        shiftStartHour = 8; shiftStartMinute = 0; shiftEndHour = 16; shiftEndMinute = 0
        confirmationPinHash = ""; confirmationPasswordHash = ""
        serverLinked = false; linkedAt = 0L; localPairingConfirmedAt = 0L; localPairingChannel = ""
        pendingChallengeId = ""; pendingChallengeMethod = ""; pendingChallengeExpiresAt = 0L; pendingChallengeAction = AttendanceAction.CHECK_IN.name
        lastBleDirectSeenAt = 0L; lastBleDirectState = ""; lastBleAdvertisingState = ""; lastBleAdvertisingAt = 0L; presenceServiceHeartbeatAt = 0L
        lastLanStoreSeenAt = 0L; lastLanState = ""; lastGpsInsideAt = 0L; lastGpsDistanceMeters = -1; lastGpsAccuracyMeters = -1; lastGpsObservedAt = 0L; lastGpsEnteredAt = 0L; lastGpsExitedAt = 0L; lastGpsState = "UNKNOWN"; lastGpsServerUploadAt = 0L
        lastPresenceProofAt = 0L
        prefs.edit().remove("challengeReplayCache").apply()
    }
}


data class AgentRecord(
    val agentId: String,
    val name: String,
    val codeHash: String,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class ManagedStoreRecord(
    val managedStoreId: String,
    val name: String,
    val branchId: String,
    val agentId: String = "",
    val status: String = "APPROVED",
    val createdAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = 0L,
    val usageCount: Int = 0,
    val licenseId: String = "",
    val licenseExpiresAt: Long = 0L,
    val maxEmployees: Int = 0,
    val activationIssuedAt: Long = 0L
)

data class ReportTransferRecord(
    val transferId: String,
    val confirmationCodeHash: String,
    val periodLabel: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "PENDING",
    val confirmedAt: Long = 0L,
    val receiverId: String = "",
    val receiverName: String = ""
)

data class AuthorizedReportReceiver(
    val receiverId: String,
    val name: String,
    val secret: String,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0L
)

class StoreRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("store_repository", Context.MODE_PRIVATE)
    private val secureVault = SecureTokenVault(appContext)

    val storeId: String
        get() {
            val old = prefs.getString("storeId", "") ?: ""
            if (old.isNotBlank()) return old
            val id = "STORE-${UUID.randomUUID()}"
            prefs.edit().putString("storeId", id).apply(); return id
        }
    fun adoptRecoveredStore(storeId: String, storeName: String, branchId: String) {
        require(storeId.startsWith("STORE-") && storeId.length <= 160)
        prefs.edit().putString("storeId", storeId)
            .putString("storeName", storeName.trim().ifBlank { "جهاز المحل" })
            .putString("branchId", branchId.trim().ifBlank { "MAIN" }).apply()
    }
    var storeName: String get() = prefs.getString("storeName", "جهاز المحل") ?: "جهاز المحل"; set(value) = prefs.edit().putString("storeName", value.trim().ifBlank { "جهاز المحل" }).apply()
    var branchId: String get() = prefs.getString("branchId", "MAIN") ?: "MAIN"; set(value) = prefs.edit().putString("branchId", value.trim().ifBlank { "MAIN" }).apply()
    var storePhone: String get() = prefs.getString("storePhone", "") ?: ""; set(value) = prefs.edit().putString("storePhone", value.trim()).apply()
    var storeAddress: String get() = prefs.getString("storeAddress", "") ?: ""; set(value) = prefs.edit().putString("storeAddress", value.trim()).apply()
    var storeManagerName: String get() = prefs.getString("storeManagerName", "") ?: ""; set(value) = prefs.edit().putString("storeManagerName", value.trim()).apply()
    var storeCommercialId: String get() = prefs.getString("storeCommercialId", "") ?: ""; set(value) = prefs.edit().putString("storeCommercialId", value.trim()).apply()
    var storeNotes: String get() = prefs.getString("storeNotes", "") ?: ""; set(value) = prefs.edit().putString("storeNotes", value.trim()).apply()
    val isStoreProfileComplete: Boolean get() = storeName.isNotBlank() && storeName != "جهاز المحل" && branchId.isNotBlank()
    var pairingNonce: String get() = prefs.getString("pairingNonce", "") ?: ""; set(value) = prefs.edit().putString("pairingNonce", value).apply()
    var pairingExpiresAt: Long get() = prefs.getLong("pairingExpiresAt", 0L); set(value) = prefs.edit().putLong("pairingExpiresAt", value).apply()
    fun markCompanionLinked(employeeId: String, channel: String, at: Long = System.currentTimeMillis()) {
        if (employeeId.isBlank()) return
        prefs.edit().putLong("companionLinkedAt:$employeeId", at).putString("companionLinkedChannel:$employeeId", channel.trim()).apply()
    }
    fun companionLinkedAt(employeeId: String): Long = prefs.getLong("companionLinkedAt:$employeeId", 0L)
    fun companionLinkedChannel(employeeId: String): String = prefs.getString("companionLinkedChannel:$employeeId", "") ?: ""

    var autoScan: Boolean get() = prefs.getBoolean("autoScan", true); set(value) = prefs.edit().putBoolean("autoScan", value).apply()

    // Store-owner local administration gate. This is intentionally separate from the ATTEND PRO
    // platform owner code so a merchant can protect day-to-day store administration independently.
    var storeAdminPinHash: String get() = prefs.getString("storeAdminPinHash", "") ?: ""; set(value) = prefs.edit().putString("storeAdminPinHash", value).apply()
    var failedStoreAdminAttempts: Int get() = prefs.getInt("failedStoreAdminAttempts", 0); set(value) = prefs.edit().putInt("failedStoreAdminAttempts", value.coerceAtLeast(0)).apply()
    var storeAdminLockUntil: Long get() = prefs.getLong("storeAdminLockUntil", 0L); set(value) = prefs.edit().putLong("storeAdminLockUntil", value).apply()
    val hasStoreAdminPin: Boolean get() = storeAdminPinHash.isNotBlank()
    private var storeAdminSessionToken: String get() = prefs.getString("storeAdminSessionToken", "") ?: ""; set(value) = prefs.edit().putString("storeAdminSessionToken", value).apply()
    private var storeAdminSessionUntil: Long get() = prefs.getLong("storeAdminSessionUntil", 0L); set(value) = prefs.edit().putLong("storeAdminSessionUntil", value).apply()

    fun issueStoreAdminSession(durationMillis: Long = 10 * 60_000L): String {
        val token = UUID.randomUUID().toString()
        storeAdminSessionToken = token
        storeAdminSessionUntil = System.currentTimeMillis() + durationMillis
        return token
    }

    fun validateStoreAdminSession(token: String): Boolean =
        token.isNotBlank() && token == storeAdminSessionToken && storeAdminSessionUntil > System.currentTimeMillis()

    fun hasActiveStoreAdminSession(): Boolean =
        storeAdminSessionToken.isNotBlank() && storeAdminSessionUntil > System.currentTimeMillis()

    fun clearStoreAdminSession() { storeAdminSessionToken = ""; storeAdminSessionUntil = 0L }

    fun verifyStoreAdminPin(entered: String): Boolean {
        if (!hasStoreAdminPin || storeAdminLockUntil > System.currentTimeMillis()) return false
        val currentHash = storeAdminPinHash
        val ok = CredentialHash1980.verify(currentHash, entered)
        if (ok && CredentialHash1980.needsUpgrade(currentHash)) storeAdminPinHash = CredentialHash1980.hash(entered)
        if (ok) {
            failedStoreAdminAttempts = 0
            storeAdminLockUntil = 0L
        } else {
            failedStoreAdminAttempts += 1
            if (failedStoreAdminAttempts >= 5) {
                storeAdminLockUntil = System.currentTimeMillis() + 60_000L
                failedStoreAdminAttempts = 0
            }
        }
        return ok
    }

    fun setStoreAdminPin(pin: String) {
        storeAdminPinHash = CredentialHash1980.hash(pin)
        failedStoreAdminAttempts = 0
        storeAdminLockUntil = 0L
    }

    fun clearStoreAdminPin() {
        storeAdminPinHash = ""
        failedStoreAdminAttempts = 0
        storeAdminLockUntil = 0L
        clearStoreAdminSession()
    }

    var reportAutoSync: Boolean get() = prefs.getBoolean("reportAutoSync", true); set(value) = prefs.edit().putBoolean("reportAutoSync", value).apply()
    var remoteMonitoringLabel: String get() = prefs.getString("remoteMonitoringLabel", "صاحب المحل") ?: "صاحب المحل"; set(value) = prefs.edit().putString("remoteMonitoringLabel", value.trim().ifBlank { "صاحب المحل" }).apply()

    // Legacy local admin password retained for backward compatibility with 1.2.x.
    var systemPasswordHash: String get() = prefs.getString("systemPasswordHash", "") ?: ""; set(value) = prefs.edit().putString("systemPasswordHash", value).apply()
    var failedSystemAttempts: Int get() = prefs.getInt("failedSystemAttempts", 0); set(value) = prefs.edit().putInt("failedSystemAttempts", value.coerceAtLeast(0)).apply()
    var systemLockUntil: Long get() = prefs.getLong("systemLockUntil", 0L); set(value) = prefs.edit().putLong("systemLockUntil", value).apply()
    var installationRole: String get() = prefs.getString("installationRole", "OWNER") ?: "OWNER"; set(value) = prefs.edit().putString("installationRole", value).apply()
    var agentName: String get() = prefs.getString("agentName", "") ?: ""; set(value) = prefs.edit().putString("agentName", value.trim()).apply()
    var approvalStatus: String get() = prefs.getString("approvalStatus", "APPROVED") ?: "APPROVED"; set(value) = prefs.edit().putString("approvalStatus", value).apply()

    // Owner control plane. The official initial owner code is stored only as a one-way SHA-256 hash.
    private val officialOwnerCodeHash = "sha256:JRMZgtCWaqTpeXe4VTkvZCiI_zmsdVe1MouRnpPLdok"
    var ownerCodeHash: String
        get() = prefs.getString("ownerCodeHash", "")?.takeIf { it.isNotBlank() } ?: officialOwnerCodeHash
        set(value) = prefs.edit().putString("ownerCodeHash", value).apply()
    var failedOwnerAttempts: Int get() = prefs.getInt("failedOwnerAttempts", 0); set(value) = prefs.edit().putInt("failedOwnerAttempts", value.coerceAtLeast(0)).apply()
    var ownerLockUntil: Long get() = prefs.getLong("ownerLockUntil", 0L); set(value) = prefs.edit().putLong("ownerLockUntil", value).apply()

    var allowEmployeeCompanion: Boolean get() = prefs.getBoolean("allowEmployeeCompanion", true); set(value) = prefs.edit().putBoolean("allowEmployeeCompanion", value).apply()
    var allowPinFallback: Boolean get() = prefs.getBoolean("allowPinFallback", true); set(value) = prefs.edit().putBoolean("allowPinFallback", value).apply()
    var allowPasswordFallback: Boolean get() = prefs.getBoolean("allowPasswordFallback", true); set(value) = prefs.edit().putBoolean("allowPasswordFallback", value).apply()
    var allowPatternFallback: Boolean get() = prefs.getBoolean("allowPatternFallback", true); set(value) = prefs.edit().putBoolean("allowPatternFallback", value).apply()
    var allowVoiceVerification: Boolean get() = prefs.getBoolean("allowVoiceVerification", true); set(value) = prefs.edit().putBoolean("allowVoiceVerification", value).apply()
    var allowGpsVerification: Boolean get() = prefs.getBoolean("allowGpsVerification", true); set(value) = prefs.edit().putBoolean("allowGpsVerification", value).apply()
    var allowFaceEnrollment: Boolean get() = prefs.getBoolean("allowFaceEnrollment", true); set(value) = prefs.edit().putBoolean("allowFaceEnrollment", value).apply()
    var allowExternalFingerprint: Boolean get() = prefs.getBoolean("allowExternalFingerprint", true); set(value) = prefs.edit().putBoolean("allowExternalFingerprint", value).apply()
    var checkoutSignatureEnabled: Boolean get() = prefs.getBoolean("checkoutSignatureEnabled", false); set(value) = prefs.edit().putBoolean("checkoutSignatureEnabled", value).apply()
    var requirePhoneBiometric: Boolean get() = prefs.getBoolean("requirePhoneBiometric", true); set(value) = prefs.edit().putBoolean("requirePhoneBiometric", value).apply()
    var allowQrAttendance: Boolean get() = prefs.getBoolean("allowQrAttendance", true); set(value) = prefs.edit().putBoolean("allowQrAttendance", value).apply()
    var attendanceVoiceAnnouncementEnabled: Boolean get() = prefs.getBoolean("attendanceVoiceAnnouncementEnabled", true); set(value) = prefs.edit().putBoolean("attendanceVoiceAnnouncementEnabled", value).apply()
    var employeeVoicePromptsEnabled: Boolean get() = prefs.getBoolean("employeeVoicePromptsEnabled", true); set(value) = prefs.edit().putBoolean("employeeVoicePromptsEnabled", value).apply()
    var employeeGeoArrivalAlertsEnabled: Boolean get() = prefs.getBoolean("employeeGeoArrivalAlertsEnabled", true); set(value) = prefs.edit().putBoolean("employeeGeoArrivalAlertsEnabled", value).apply()
    var smartLateAlertsEnabled: Boolean get() = prefs.getBoolean("smartLateAlertsEnabled", true); set(value) = prefs.edit().putBoolean("smartLateAlertsEnabled", value).apply()
    var lateAlertDelayMinutes: Int get() = prefs.getInt("lateAlertDelayMinutes", 5); set(value) = prefs.edit().putInt("lateAlertDelayMinutes", value.coerceIn(0, 180)).apply()
    var lateAlertRepeatMinutes: Int get() = prefs.getInt("lateAlertRepeatMinutes", 15); set(value) = prefs.edit().putInt("lateAlertRepeatMinutes", value.coerceIn(5, 180)).apply()
    var lateAutoCallEnabled: Boolean get() = prefs.getBoolean("lateAutoCallEnabled", false); set(value) = prefs.edit().putBoolean("lateAutoCallEnabled", value).apply()
    var voiceMode: String get() = prefs.getString("voiceMode", "AUTO") ?: "AUTO"; set(value) = prefs.edit().putString("voiceMode", value).apply()
    var voiceRatePercent: Int get() = prefs.getInt("voiceRatePercent", 90); set(value) = prefs.edit().putInt("voiceRatePercent", value.coerceIn(50, 150)).apply()
    var voiceVolumePercent: Int get() = prefs.getInt("voiceVolumePercent", 100); set(value) = prefs.edit().putInt("voiceVolumePercent", value.coerceIn(0, 100)).apply()
    var voiceName: String get() = prefs.getString("voiceName", "") ?: ""; set(value) = prefs.edit().putString("voiceName", value).apply()
    var voiceRequestSentText: String get() = prefs.getString("voiceRequestSentText", "تم إرسال طلب إثبات حضور إلى {name}") ?: ""; set(value) = prefs.edit().putString("voiceRequestSentText", value).apply()
    var voiceMissingProofText: String get() = prefs.getString("voiceMissingProofText", "{name} لم يثبت الحضور") ?: ""; set(value) = prefs.edit().putString("voiceMissingProofText", value).apply()
    var voiceLateText: String get() = prefs.getString("voiceLateText", "الموظف {name} لم يسجل الحضور في الموعد المحدد") ?: ""; set(value) = prefs.edit().putString("voiceLateText", value).apply()
    var storeVoiceAttendanceEnabled: Boolean get() = prefs.getBoolean("storeVoiceAttendanceEnabled", true); set(value) = prefs.edit().putBoolean("storeVoiceAttendanceEnabled", value).apply()
    var storeVoiceRequestEnabled: Boolean get() = prefs.getBoolean("storeVoiceRequestEnabled", true); set(value) = prefs.edit().putBoolean("storeVoiceRequestEnabled", value).apply()
    var storeVoiceLateEnabled: Boolean get() = prefs.getBoolean("storeVoiceLateEnabled", true); set(value) = prefs.edit().putBoolean("storeVoiceLateEnabled", value).apply()
    var storeVoiceMissingProofEnabled: Boolean get() = prefs.getBoolean("storeVoiceMissingProofEnabled", true); set(value) = prefs.edit().putBoolean("storeVoiceMissingProofEnabled", value).apply()
    var employeeMessageVoiceDefaultEnabled: Boolean get() = prefs.getBoolean("employeeMessageVoiceDefaultEnabled", true); set(value) = prefs.edit().putBoolean("employeeMessageVoiceDefaultEnabled", value).apply()
    fun employeeMessageVoiceMode(employeeId: String): String = prefs.getString("employeeMessageVoiceMode:${employeeId.lowercase()}", "INHERIT") ?: "INHERIT"
    fun setEmployeeMessageVoiceMode(employeeId: String, mode: String) {
        val safe = mode.takeIf { it in setOf("INHERIT","VOICE_NOTIFICATION","NOTIFICATION_ONLY","SILENT") } ?: "INHERIT"
        prefs.edit().putString("employeeMessageVoiceMode:${employeeId.lowercase()}", safe).apply()
    }
    fun wasStoreMessageNotified1975(messageId: String): Boolean = prefs.getStringSet("storeMessageNotified1975", emptySet())?.contains(messageId) == true
    fun markStoreMessageNotified1975(messageId: String) {
        if (messageId.isBlank()) return
        val old = prefs.getStringSet("storeMessageNotified1975", emptySet())?.toMutableSet() ?: mutableSetOf()
        old += messageId
        while (old.size > 120) old.remove(old.first())
        prefs.edit().putStringSet("storeMessageNotified1975", old).apply()
    }
    var employeeRequestVoiceText: String get() = prefs.getString("employeeRequestVoiceText", "{name}، يرجى إثبات حضورك") ?: ""; set(value) = prefs.edit().putString("employeeRequestVoiceText", value).apply()
    var employeeMissingProofVoiceText: String get() = prefs.getString("employeeMissingProofVoiceText", "{name}، لم يتم إثبات حضورك، يرجى إثبات الحضور الآن") ?: ""; set(value) = prefs.edit().putString("employeeMissingProofVoiceText", value).apply()
    var employeeLateVoiceText: String get() = prefs.getString("employeeLateVoiceText", "موعد دوامك بدأ ولم يتم إثبات حضورك") ?: ""; set(value) = prefs.edit().putString("employeeLateVoiceText", value).apply()
    var storeLatitude: Double get() = java.lang.Double.longBitsToDouble(prefs.getLong("storeLatitude", java.lang.Double.doubleToRawLongBits(Double.NaN))); set(value) = prefs.edit().putLong("storeLatitude", java.lang.Double.doubleToRawLongBits(value)).apply()
    var storeLongitude: Double get() = java.lang.Double.longBitsToDouble(prefs.getLong("storeLongitude", java.lang.Double.doubleToRawLongBits(Double.NaN))); set(value) = prefs.edit().putLong("storeLongitude", java.lang.Double.doubleToRawLongBits(value)).apply()
    var gpsRadiusMeters: Int get() = prefs.getInt("gpsRadiusMeters", 25); set(value) = prefs.edit().putInt("gpsRadiusMeters", value.coerceIn(10, 5000)).apply()
    var gpsRecognitionEnabled: Boolean get() = prefs.getBoolean("gpsRecognitionEnabled", true); set(value) = prefs.edit().putBoolean("gpsRecognitionEnabled", value).apply()
    var gpsNotifyOwnerEnabled: Boolean get() = prefs.getBoolean("gpsNotifyOwnerEnabled", true); set(value) = prefs.edit().putBoolean("gpsNotifyOwnerEnabled", value).apply()
    var lateNotifyEmployeeViaServer: Boolean get() = prefs.getBoolean("lateNotifyEmployeeViaServer", true); set(value) = prefs.edit().putBoolean("lateNotifyEmployeeViaServer", value).apply()
    var smartAlertAudience: String
        get() = prefs.getString("smartAlertAudience", "BOTH") ?: "BOTH"
        set(value) = prefs.edit().putString("smartAlertAudience", value.takeIf { it in setOf("OWNER_ONLY", "EMPLOYEE_ONLY", "BOTH") } ?: "BOTH").apply()
    var checkoutReminderEnabled: Boolean get() = prefs.getBoolean("checkoutReminderEnabled", true); set(value) = prefs.edit().putBoolean("checkoutReminderEnabled", value).apply()
    var checkoutReminderDelayMinutes: Int get() = prefs.getInt("checkoutReminderDelayMinutes", 15); set(value) = prefs.edit().putInt("checkoutReminderDelayMinutes", value.coerceIn(0, 180)).apply()

    /** 1=Sunday .. 7=Saturday, matching java.util.Calendar. Defaults to every day to preserve old behavior. */
    fun employeeWorkDays(employeeId: String): Set<Int> {
        val raw = prefs.getString("workDays:${employeeId.lowercase()}", "1,2,3,4,5,6,7") ?: "1,2,3,4,5,6,7"
        return raw.split(',').mapNotNull { it.toIntOrNull()?.takeIf { day -> day in 1..7 } }.toSet().ifEmpty { (1..7).toSet() }
    }

    fun setEmployeeWorkDays(employeeId: String, days: Set<Int>) {
        if (employeeId.isBlank()) return
        val safe = days.filter { it in 1..7 }.toSortedSet()
        prefs.edit().putString("workDays:${employeeId.lowercase()}", safe.joinToString(",")).apply()
    }

    fun isEmployeeWorkDay(employeeId: String, dayOfWeek: Int): Boolean = dayOfWeek in employeeWorkDays(employeeId)

    val isGpsConfigured: Boolean get() = storeLatitude.isFinite() && storeLongitude.isFinite()

    fun verifySystemPassword(entered: String): Boolean {
        if (systemPasswordHash.isBlank()) return false
        val currentHash = systemPasswordHash
        val ok = CredentialHash1980.verify(currentHash, entered)
        if (ok && CredentialHash1980.needsUpgrade(currentHash)) systemPasswordHash = CredentialHash1980.hash(entered)
        if (ok) { failedSystemAttempts = 0; systemLockUntil = 0L }
        else {
            failedSystemAttempts += 1
            if (failedSystemAttempts >= 5) { systemLockUntil = System.currentTimeMillis() + 60_000L; failedSystemAttempts = 0 }
        }
        return ok
    }

    fun setSystemPassword(password: String) { systemPasswordHash = CredentialHash1980.hash(password); failedSystemAttempts = 0; systemLockUntil = 0L }

    fun verifyOwnerCode(entered: String): Boolean {
        if (ownerLockUntil > System.currentTimeMillis()) return false
        val currentHash = ownerCodeHash
        val ok = CredentialHash1980.verify(currentHash, entered)
        if (ok && CredentialHash1980.needsUpgrade(currentHash)) ownerCodeHash = CredentialHash1980.hash(entered)
        if (ok) { failedOwnerAttempts = 0; ownerLockUntil = 0L }
        else {
            failedOwnerAttempts += 1
            if (failedOwnerAttempts >= 5) { ownerLockUntil = System.currentTimeMillis() + 60_000L; failedOwnerAttempts = 0 }
        }
        return ok
    }

    fun setOwnerCode(newCode: String) {
        ownerCodeHash = CredentialHash1980.hash(newCode)
        failedOwnerAttempts = 0
        ownerLockUntil = 0L
    }

    fun agents(): List<AgentRecord> {
        val array = JSONArray(prefs.getString("agents", "[]") ?: "[]")
        return (0 until array.length()).mapNotNull { i -> runCatching {
            val o = array.getJSONObject(i)
            AgentRecord(
                agentId = o.getString("agentId"),
                name = o.optString("name", "وكيل"),
                codeHash = o.optString("codeHash", ""),
                active = o.optBoolean("active", true),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }.getOrNull() }
    }

    fun upsertAgent(agent: AgentRecord) {
        val items = agents().toMutableList()
        val index = items.indexOfFirst { it.agentId == agent.agentId }
        if (index >= 0) items[index] = agent else items.add(agent)
        saveAgents(items)
    }

    fun removeAgent(agentId: String) = saveAgents(agents().filterNot { it.agentId == agentId })

    private fun saveAgents(items: List<AgentRecord>) {
        val array = JSONArray()
        items.forEach { a -> array.put(JSONObject().apply {
            put("agentId", a.agentId); put("name", a.name); put("codeHash", a.codeHash); put("active", a.active); put("createdAt", a.createdAt)
        }) }
        prefs.edit().putString("agents", array.toString()).apply()
    }

    fun findAgentByCode(code: String): AgentRecord? = agents().firstOrNull { it.active && PairingProtocol.matchesPin(it.codeHash, code) }

    fun managedStores(): List<ManagedStoreRecord> {
        val array = JSONArray(prefs.getString("managedStores", "[]") ?: "[]")
        return (0 until array.length()).mapNotNull { i -> runCatching {
            val o = array.getJSONObject(i)
            ManagedStoreRecord(
                managedStoreId = o.getString("managedStoreId"),
                name = o.optString("name", "محل"),
                branchId = o.optString("branchId", "MAIN"),
                agentId = o.optString("agentId", ""),
                status = o.optString("status", "APPROVED"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                lastSeenAt = o.optLong("lastSeenAt", 0L),
                usageCount = o.optInt("usageCount", 0),
                licenseId = o.optString("licenseId", ""),
                licenseExpiresAt = o.optLong("licenseExpiresAt", 0L),
                maxEmployees = o.optInt("maxEmployees", 0),
                activationIssuedAt = o.optLong("activationIssuedAt", 0L)
            )
        }.getOrNull() }
    }

    fun upsertManagedStore(store: ManagedStoreRecord) {
        val items = managedStores().toMutableList()
        val index = items.indexOfFirst { it.managedStoreId == store.managedStoreId }
        if (index >= 0) items[index] = store else items.add(store)
        saveManagedStores(items)
    }

    fun removeManagedStore(id: String) = saveManagedStores(managedStores().filterNot { it.managedStoreId == id })

    private fun saveManagedStores(items: List<ManagedStoreRecord>) {
        val array = JSONArray()
        items.forEach { m -> array.put(JSONObject().apply {
            put("managedStoreId", m.managedStoreId); put("name", m.name); put("branchId", m.branchId); put("agentId", m.agentId)
            put("status", m.status); put("createdAt", m.createdAt); put("lastSeenAt", m.lastSeenAt); put("usageCount", m.usageCount)
            put("licenseId", m.licenseId); put("licenseExpiresAt", m.licenseExpiresAt); put("maxEmployees", m.maxEmployees); put("activationIssuedAt", m.activationIssuedAt)
        }) }
        prefs.edit().putString("managedStores", array.toString()).apply()
    }

    fun ensureCurrentStoreInManagement() {
        val existing = managedStores().firstOrNull { it.managedStoreId == storeId }
        val snapshot = ManagedStoreRecord(
            managedStoreId = storeId,
            name = storeName,
            branchId = branchId,
            agentId = existing?.agentId.orEmpty(),
            status = existing?.status ?: "APPROVED",
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            lastSeenAt = System.currentTimeMillis(),
            usageCount = events().size,
            licenseId = existing?.licenseId.orEmpty(),
            licenseExpiresAt = existing?.licenseExpiresAt ?: 0L,
            maxEmployees = existing?.maxEmployees ?: 0,
            activationIssuedAt = existing?.activationIssuedAt ?: 0L
        )
        upsertManagedStore(snapshot)
    }

    private fun employeesJson1980(): String {
        val secured = secureVault.get("employees_json_v1980")
        if (secured.isNotBlank() && runCatching { JSONArray(secured) }.isSuccess) return secured
        val legacy = prefs.getString("employees", "[]") ?: "[]"
        if (runCatching { JSONArray(legacy) }.isSuccess) {
            secureVault.put("employees_json_v1980", legacy)
            if (secureVault.get("employees_json_v1980") == legacy) prefs.edit().remove("employees").apply()
            return legacy
        }
        return "[]"
    }

    fun employees(): List<PairedEmployee> {
        val array = JSONArray(employeesJson1980())
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                PairedEmployee(
                    employeeId = o.getString("employeeId"),
                    displayName = o.optString("displayName", o.getString("employeeId")),
                    branchId = o.optString("branchId", "MAIN"),
                    pairingSecret = o.optString("pairingSecret", SecretCodec.encode(SecretCodec.generate())),
                    pin = o.optString("pinHash", o.optString("pin", "")),
                    phone = o.optString("phone", ""),
                    jobTitle = o.optString("jobTitle", ""),
                    externalFingerprintId = o.optString("externalFingerprintId", ""),
                    faceProfileRef = o.optString("faceProfileRef", ""),
                    companionEnabled = o.optBoolean("companionEnabled", true),
                    active = o.optBoolean("active", true),
                    department = o.optString("department", ""),
                    nationalId = o.optString("nationalId", ""),
                    hireDate = o.optString("hireDate", ""),
                    notes = o.optString("notes", ""),
                    faceCapturedAt = o.optLong("faceCapturedAt", 0L),
                    passwordHash = o.optString("passwordHash", ""),
                    patternHash = o.optString("patternHash", ""),
                    voicePhraseHash = o.optString("voicePhraseHash", ""),
                    allowedMethods = o.optJSONArray("allowedMethods")?.let { methods ->
                        (0 until methods.length()).mapNotNull { idx -> methods.optString(idx).takeIf { it.isNotBlank() } }.toSet()
                    } ?: emptySet(),
                    useCustomShift = o.optBoolean("useCustomShift", false),
                    shiftStartHour = o.optInt("shiftStartHour", 8).coerceIn(0, 23),
                    shiftStartMinute = o.optInt("shiftStartMinute", 0).coerceIn(0, 59),
                    shiftEndHour = o.optInt("shiftEndHour", 16).coerceIn(0, 23),
                    shiftEndMinute = o.optInt("shiftEndMinute", 0).coerceIn(0, 59),
                    faceTemplate = o.optString("faceTemplate", ""),
                    faceQualityScore = o.optInt("faceQualityScore", 0).coerceIn(0, 100),
                    voiceTemplate = o.optString("voiceTemplate", ""),
                    voiceQualityScore = o.optInt("voiceQualityScore", 0).coerceIn(0, 100),
                    voicePhraseText = o.optString("voicePhraseText", ""),
                    lateAlertEnabled = o.optBoolean("lateAlertEnabled", true),
                    lateGraceMinutes = o.optInt("lateGraceMinutes", -1).coerceIn(-1, 180),
                    lateFirstAlertDelayMinutes = o.optInt("lateFirstAlertDelayMinutes", -1).coerceIn(-1, 180),
                    lateAlertCount = o.optInt("lateAlertCount", 3).coerceIn(1, 10),
                    lateRepeatMinutes = o.optInt("lateRepeatMinutes", -1).coerceIn(-1, 180),
                    lateAlertMode = o.optString("lateAlertMode", "NOTIFICATION_VOICE"),
                    lateCallMode = o.optString("lateCallMode", "NONE"),
                    presenceReminderMinutes = o.optInt("presenceReminderMinutes", 3).coerceIn(1, 30)
                )
            }.getOrNull()
        }
    }

    fun upsertEmployee(employee: PairedEmployee) {
        val items = employees().toMutableList()
        val index = items.indexOfFirst { it.employeeId.equals(employee.employeeId, true) }
        if (index >= 0) items[index] = employee else items.add(employee)
        saveEmployees(items)
    }

    fun removeEmployee(employeeId: String) = saveEmployees(employees().filterNot { it.employeeId.equals(employeeId, true) })

    private fun saveEmployees(items: List<PairedEmployee>) {
        val array = JSONArray()
        items.forEach { e -> array.put(JSONObject().apply {
            put("employeeId", e.employeeId); put("displayName", e.displayName); put("branchId", e.branchId)
            put("pairingSecret", e.pairingSecret); put("pinHash", e.pin); put("phone", e.phone); put("jobTitle", e.jobTitle)
            put("externalFingerprintId", e.externalFingerprintId); put("faceProfileRef", e.faceProfileRef)
            put("companionEnabled", e.companionEnabled); put("active", e.active)
            put("department", e.department); put("nationalId", e.nationalId); put("hireDate", e.hireDate); put("notes", e.notes); put("faceCapturedAt", e.faceCapturedAt)
            put("passwordHash", e.passwordHash); put("patternHash", e.patternHash); put("voicePhraseHash", e.voicePhraseHash)
            put("allowedMethods", JSONArray().apply { e.allowedMethods.sorted().forEach { put(it) } })
            put("useCustomShift", e.useCustomShift); put("shiftStartHour", e.shiftStartHour); put("shiftStartMinute", e.shiftStartMinute)
            put("shiftEndHour", e.shiftEndHour); put("shiftEndMinute", e.shiftEndMinute)
            put("faceTemplate", e.faceTemplate); put("faceQualityScore", e.faceQualityScore)
            put("voiceTemplate", e.voiceTemplate); put("voiceQualityScore", e.voiceQualityScore)
            put("voicePhraseText", e.voicePhraseText)
            put("lateAlertEnabled", e.lateAlertEnabled); put("lateGraceMinutes", e.lateGraceMinutes)
            put("lateFirstAlertDelayMinutes", e.lateFirstAlertDelayMinutes); put("lateAlertCount", e.lateAlertCount)
            put("lateRepeatMinutes", e.lateRepeatMinutes); put("lateAlertMode", e.lateAlertMode)
            put("lateCallMode", e.lateCallMode); put("presenceReminderMinutes", e.presenceReminderMinutes)
        }) }
        val payload = array.toString()
        secureVault.put("employees_json_v1980", payload)
        if (secureVault.get("employees_json_v1980") == payload) prefs.edit().remove("employees").apply()
    }

    fun ensureCompanionSecret(employee: PairedEmployee): PairedEmployee {
        if (SecretCodec.isValid(employee.pairingSecret)) return employee
        val updated = employee.copy(pairingSecret = SecretCodec.encode(SecretCodec.generate()))
        upsertEmployee(updated); return updated
    }

    fun newProvision(employee: PairedEmployee): PairingProtocol.EmployeeProvision {
        val e = ensureCompanionSecret(employee)
        val gpsProtectionAvailable = gpsRecognitionEnabled && isGpsConfigured
        return PairingProtocol.EmployeeProvision(
            storeId, storeName, e.employeeId, e.displayName, e.branchId, e.phone, e.jobTitle,
            e.pairingSecret, e.pin, System.currentTimeMillis() + 30 * 60_000L,
            if (gpsProtectionAvailable) storeLatitude else Double.NaN,
            if (gpsProtectionAvailable) storeLongitude else Double.NaN,
            gpsRadiusMeters,
            e.allowedMethods,
            if (e.useCustomShift) e.shiftStartHour else shiftHour,
            if (e.useCustomShift) e.shiftStartMinute else shiftMinute,
            if (e.useCustomShift) e.shiftEndHour else shiftEndHour,
            if (e.useCustomShift) e.shiftEndMinute else shiftEndMinute,
            employeeVoicePromptsEnabled,
            employeeGeoArrivalAlertsEnabled,
            serverUrl,
            employeeRequestVoiceText,
            employeeMissingProofVoiceText,
            employeeLateVoiceText,
            voiceRatePercent,
            voiceVolumePercent,
            e.lateAlertEnabled,
            (if (e.lateGraceMinutes >= 0) e.lateGraceMinutes else graceMinutes),
            (if (e.lateFirstAlertDelayMinutes >= 0) e.lateFirstAlertDelayMinutes else lateAlertDelayMinutes),
            e.lateAlertCount,
            (if (e.lateRepeatMinutes >= 0) e.lateRepeatMinutes else lateAlertRepeatMinutes),
            e.lateAlertMode,
            e.passwordHash
        )
    }

    fun newPairingInvite(): PairingProtocol.StoreInvite {
        pairingNonce = PairingProtocol.randomNonce(); pairingExpiresAt = System.currentTimeMillis() + PairingProtocol.DEFAULT_TTL_MILLIS
        return PairingProtocol.StoreInvite(storeId, storeName, branchId, pairingNonce, pairingExpiresAt)
    }

    fun isValidPairingResponse(response: PairingProtocol.EmployeeResponse): Boolean =
        response.storeId == storeId && response.nonce == pairingNonce && pairingExpiresAt > System.currentTimeMillis() && response.expiresAt > System.currentTimeMillis()

    fun events(): List<AttendanceEvent> {
        val array = JSONArray(prefs.getString("events", "[]") ?: "[]")
        return (0 until array.length()).mapNotNull { i -> runCatching { AttendanceEvent.fromJson(array.getJSONObject(i)) }.getOrNull() }.sortedBy { it.timestampEpochMillis }
    }
    fun addEvent(event: AttendanceEvent) {
        val current = events().toMutableList()
        val index = current.indexOfFirst { it.eventId == event.eventId }
        if (index >= 0) current[index] = event else current.add(event)
        saveEvents(current.takeLast(3000))
    }
    fun mergeServerEvents(incoming: List<AttendanceEvent>): Int {
        if (incoming.isEmpty()) return 0
        val current = events().associateBy { it.eventId }.toMutableMap()
        var added = 0
        incoming.forEach { event ->
            if (!current.containsKey(event.eventId)) added++
            current[event.eventId] = event.copy(synced = true)
        }
        saveEvents(current.values.sortedBy { it.timestampEpochMillis }.takeLast(3000))
        return added
    }
    var attendancePullCursor: Long
        get() = prefs.getLong("attendancePullCursor", 0L)
        set(value) = prefs.edit().putLong("attendancePullCursor", value.coerceAtLeast(0L)).apply()
    fun markSynced(ids: Set<String>) { saveEvents(events().map { if (it.eventId in ids) it.copy(synced = true) else it }) }
    fun pendingEvents(): List<AttendanceEvent> = events().filter { !it.synced }
    private fun saveEvents(items: List<AttendanceEvent>) { val array = JSONArray(); items.forEach { array.put(it.toJson()) }; prefs.edit().putString("events", array.toString()).apply() }
    fun lastEvent(employeeId: String): AttendanceEvent? = events().lastOrNull { it.employeeId.equals(employeeId, true) }
    fun lastEventToday(employeeId: String, dayStart: Long): AttendanceEvent? = events().lastOrNull { it.employeeId.equals(employeeId, true) && it.timestampEpochMillis >= dayStart }
    fun presenceEvents(): List<PresenceEvent> {
        val array = JSONArray(prefs.getString("presenceEvents", "[]") ?: "[]")
        return (0 until array.length()).mapNotNull { i -> runCatching { PresenceEvent.fromJson(array.getJSONObject(i)) }.getOrNull() }.sortedBy { it.timestampEpochMillis }
    }
    fun addPresenceEvent(event: PresenceEvent) {
        val previous = presenceEvents()
        val lastSame = previous.lastOrNull { it.employeeId.equals(event.employeeId, true) && it.channel == event.channel }
        if (lastSame != null && event.timestampEpochMillis - lastSame.timestampEpochMillis < 60_000L) return
        val items = (previous + event).takeLast(1000)
        val array = JSONArray(); items.forEach { array.put(it.toJson()) }
        prefs.edit().putString("presenceEvents", array.toString()).apply()
    }
    fun lastProofToken(employeeId: String): Int = prefs.getInt("proof_${employeeId.lowercase()}", Int.MIN_VALUE)
    fun setLastProofToken(employeeId: String, token: Int) = prefs.edit().putInt("proof_${employeeId.lowercase()}", token).apply()

    fun authorizedReportReceivers(): List<AuthorizedReportReceiver> {
        val array = JSONArray(prefs.getString("authorizedReportReceivers", "[]") ?: "[]")
        return (0 until array.length()).mapNotNull { i -> runCatching {
            val o = array.getJSONObject(i)
            AuthorizedReportReceiver(
                receiverId = o.getString("receiverId"),
                name = o.optString("name", "هاتف مراقبة"),
                secret = o.getString("secret"),
                active = o.optBoolean("active", true),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                lastUsedAt = o.optLong("lastUsedAt", 0L)
            )
        }.getOrNull() }.sortedByDescending { it.createdAt }
    }

    private fun saveAuthorizedReportReceivers(items: List<AuthorizedReportReceiver>) {
        val array = JSONArray()
        items.take(30).forEach { r -> array.put(JSONObject().apply {
            put("receiverId", r.receiverId); put("name", r.name); put("secret", r.secret); put("active", r.active)
            put("createdAt", r.createdAt); put("lastUsedAt", r.lastUsedAt)
        }) }
        prefs.edit().putString("authorizedReportReceivers", array.toString()).apply()
    }

    fun authorizeReportReceiver(invite: ReportProtocol.ReceiverInvite): Boolean {
        if (invite.expiresAt < System.currentTimeMillis() || invite.receiverId.isBlank() || invite.secret.isBlank()) return false
        val items = authorizedReportReceivers().toMutableList()
        val record = AuthorizedReportReceiver(invite.receiverId, invite.name.ifBlank { "هاتف مراقبة" }, invite.secret, true)
        val index = items.indexOfFirst { it.receiverId == invite.receiverId }
        if (index >= 0) items[index] = record.copy(createdAt = items[index].createdAt) else items.add(0, record)
        saveAuthorizedReportReceivers(items)
        return true
    }

    fun setReportReceiverActive(receiverId: String, active: Boolean) {
        saveAuthorizedReportReceivers(authorizedReportReceivers().map { if (it.receiverId == receiverId) it.copy(active = active) else it })
    }

    fun removeReportReceiver(receiverId: String) = saveAuthorizedReportReceivers(authorizedReportReceivers().filterNot { it.receiverId == receiverId })

    fun markReportReceiverUsed(receiverId: String) {
        saveAuthorizedReportReceivers(authorizedReportReceivers().map { if (it.receiverId == receiverId) it.copy(lastUsedAt = System.currentTimeMillis()) else it })
    }

    fun reportTransfers(): List<ReportTransferRecord> {
        val array = JSONArray(prefs.getString("reportTransfers", "[]") ?: "[]")
        return (0 until array.length()).mapNotNull { i -> runCatching {
            val o = array.getJSONObject(i)
            ReportTransferRecord(
                transferId = o.getString("transferId"),
                confirmationCodeHash = o.getString("confirmationCodeHash"),
                periodLabel = o.optString("periodLabel", "تقرير"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                status = o.optString("status", "PENDING"),
                confirmedAt = o.optLong("confirmedAt", 0L),
                receiverId = o.optString("receiverId", ""),
                receiverName = o.optString("receiverName", "")
            )
        }.getOrNull() }.sortedByDescending { it.createdAt }
    }

    private fun saveReportTransfers(items: List<ReportTransferRecord>) {
        val array = JSONArray()
        items.take(100).forEach { r -> array.put(JSONObject().apply {
            put("transferId", r.transferId); put("confirmationCodeHash", r.confirmationCodeHash); put("periodLabel", r.periodLabel)
            put("createdAt", r.createdAt); put("status", r.status); put("confirmedAt", r.confirmedAt)
            put("receiverId", r.receiverId); put("receiverName", r.receiverName)
        }) }
        prefs.edit().putString("reportTransfers", array.toString()).apply()
    }

    fun createReportTransfer(periodLabel: String, receiverId: String = "", receiverName: String = ""): Pair<ReportTransferRecord, String> {
        val transferId = "RPT-" + UUID.randomUUID().toString().substring(0, 8).uppercase()
        val code = (100000 + java.security.SecureRandom().nextInt(900000)).toString()
        val record = ReportTransferRecord(transferId, PairingProtocol.pinHash(code), periodLabel, receiverId = receiverId, receiverName = receiverName)
        saveReportTransfers(listOf(record) + reportTransfers())
        return record to code
    }

    fun confirmReportTransfer(transferId: String, code: String): Boolean {
        val items = reportTransfers().toMutableList()
        val index = items.indexOfFirst { it.transferId.equals(transferId.trim(), true) }
        if (index < 0) return false
        val current = items[index]
        if (!PairingProtocol.matchesPin(current.confirmationCodeHash, code.trim())) return false
        items[index] = current.copy(status = "CONFIRMED", confirmedAt = System.currentTimeMillis())
        saveReportTransfers(items)
        return true
    }

    fun confirmReportTransferTrusted(transferId: String): Boolean {
        val items = reportTransfers().toMutableList()
        val index = items.indexOfFirst { it.transferId.equals(transferId.trim(), true) }
        if (index < 0) return false
        val current = items[index]
        if (current.status == "CONFIRMED") return true
        items[index] = current.copy(status = "CONFIRMED", confirmedAt = System.currentTimeMillis())
        saveReportTransfers(items)
        return true
    }

    var shiftHour: Int get() = prefs.getInt("shiftHour", 8); set(value) = prefs.edit().putInt("shiftHour", value.coerceIn(0,23)).apply()
    var shiftMinute: Int get() = prefs.getInt("shiftMinute", 0); set(value) = prefs.edit().putInt("shiftMinute", value.coerceIn(0,59)).apply()
    var shiftEndHour: Int get() = prefs.getInt("shiftEndHour", 16); set(value) = prefs.edit().putInt("shiftEndHour", value.coerceIn(0,23)).apply()
    var shiftEndMinute: Int get() = prefs.getInt("shiftEndMinute", 0); set(value) = prefs.edit().putInt("shiftEndMinute", value.coerceIn(0,59)).apply()
    var graceMinutes: Int get() = prefs.getInt("graceMinutes", 10); set(value) = prefs.edit().putInt("graceMinutes", value.coerceIn(0,180)).apply()
    var serverUrl: String get() = prefs.getString("serverUrl", DEFAULT_CENTRAL_SERVER) ?: DEFAULT_CENTRAL_SERVER; set(value) = prefs.edit().putString("serverUrl", value.trim()).apply()
    var tenantId: String get() = prefs.getString("tenantId", "") ?: ""; set(value) = prefs.edit().putString("tenantId", value.trim()).apply()

    // Central activation (1.9+). Access and owner API tokens are encrypted with Android Keystore.
    var centralActivationRequestId: String get() = prefs.getString("centralActivationRequestId", "") ?: ""; set(value) = prefs.edit().putString("centralActivationRequestId", value.trim()).apply()
    var centralActivationPollSecret: String get() = secureVault.get("central_poll_secret"); set(value) = secureVault.put("central_poll_secret", value.trim())
    var centralLicenseId: String get() = prefs.getString("centralLicenseId", "") ?: ""; set(value) = prefs.edit().putString("centralLicenseId", value.trim()).apply()
    var centralActivationExpiresAt: Long get() = prefs.getLong("centralActivationExpiresAt", 0L); set(value) = prefs.edit().putLong("centralActivationExpiresAt", value).apply()
    var centralMaxEmployees: Int get() = prefs.getInt("centralMaxEmployees", 10); set(value) = prefs.edit().putInt("centralMaxEmployees", value.coerceIn(1, 10000)).apply()
    var centralLastValidatedAt: Long get() = prefs.getLong("centralLastValidatedAt", 0L); set(value) = prefs.edit().putLong("centralLastValidatedAt", value).apply()
    var centralLeaseUntil: Long get() = prefs.getLong("centralLeaseUntil", 0L); set(value) = prefs.edit().putLong("centralLeaseUntil", value).apply()
    var centralServerTime: Long get() = prefs.getLong("centralServerTime", 0L); set(value) = prefs.edit().putLong("centralServerTime", value).apply()
    var centralServerStatus: String get() = prefs.getString("centralServerStatus", "UNACTIVATED") ?: "UNACTIVATED"; set(value) = prefs.edit().putString("centralServerStatus", value.trim().uppercase()).apply()
    var centralAccessToken: String get() = secureVault.get("central_store_access"); set(value) = secureVault.put("central_store_access", value.trim())
    var centralOwnerApiKey: String get() = secureVault.get("central_owner_api_key"); set(value) = secureVault.put("central_owner_api_key", value.trim())

    fun hasCentralCredentials(): Boolean = centralLicenseId.isNotBlank() && centralAccessToken.isNotBlank()
    fun centralClockRollbackDetected(now: Long = System.currentTimeMillis()): Boolean = centralServerTime > 0L && now + 5 * 60_000L < centralServerTime
    fun isCentralActivationActive(now: Long = System.currentTimeMillis()): Boolean =
        hasCentralCredentials() && centralServerStatus == "ACTIVE" && centralActivationExpiresAt > now && centralLeaseUntil > now && !centralClockRollbackDetected(now)

    fun saveCentralActivation(licenseId: String, accessToken: String, expiresAt: Long, maxEmployees: Int, leaseUntil: Long, serverTime: Long) {
        centralLicenseId = licenseId
        centralAccessToken = accessToken
        centralActivationExpiresAt = expiresAt
        centralMaxEmployees = maxEmployees
        centralLeaseUntil = leaseUntil
        centralServerTime = serverTime
        centralLastValidatedAt = System.currentTimeMillis()
        centralServerStatus = "ACTIVE"
        tenantId = licenseId
        centralActivationRequestId = ""
        centralActivationPollSecret = ""
    }

    fun refreshCentralLease(status: String, expiresAt: Long, maxEmployees: Int, leaseUntil: Long, serverTime: Long) {
        centralServerStatus = status
        if (expiresAt > 0L) centralActivationExpiresAt = expiresAt
        if (maxEmployees > 0) centralMaxEmployees = maxEmployees
        centralLeaseUntil = leaseUntil
        if (serverTime > 0L) centralServerTime = serverTime
        centralLastValidatedAt = System.currentTimeMillis()
    }

    fun markCentralInactive(status: String, serverTime: Long = System.currentTimeMillis()) {
        centralServerStatus = status
        centralLeaseUntil = 0L
        centralLastValidatedAt = System.currentTimeMillis()
        if (serverTime > 0L) centralServerTime = serverTime
    }

    fun clearCentralActivation(clearCredentials: Boolean = true) {
        if (clearCredentials) { centralLicenseId = ""; centralAccessToken = "" }
        centralActivationExpiresAt = 0L
        centralLeaseUntil = 0L
        centralLastValidatedAt = 0L
        centralServerTime = 0L
        centralServerStatus = "UNACTIVATED"
    }
    fun syncAccessToken(): String = if (isCentralActivationActive()) centralAccessToken else ""
    fun syncTenantId(): String = if (isCentralActivationActive()) centralLicenseId else ""

    // Compatibility helpers used by the Store UI. They deliberately map only to the
    // central activation state; they do not restore the old offline/local license path.
    var lastSyncAt: Long
        get() = prefs.getLong("lastSyncAt", 0L)
        set(value) = prefs.edit().putLong("lastSyncAt", value.coerceAtLeast(0L)).apply()
    var lastSyncMessage: String
        get() = prefs.getString("lastSyncMessage", "") ?: ""
        set(value) = prefs.edit().putString("lastSyncMessage", value.take(300)).apply()
    fun isActivationActive(now: Long = System.currentTimeMillis()): Boolean = isCentralActivationActive(now)
    fun effectiveEmployeeLimit(now: Long = System.currentTimeMillis()): Int =
        if (isCentralActivationActive(now)) centralMaxEmployees else 0

    // Legacy local license fields remain removed. Operational access is central-only.

    var fingerprintHost: String get() = prefs.getString("fingerprintHost", "") ?: ""; set(value) = prefs.edit().putString("fingerprintHost", value.trim()).apply()
    var fingerprintPort: Int get() = prefs.getInt("fingerprintPort", 4370); set(value) = prefs.edit().putInt("fingerprintPort", value.coerceIn(1,65535)).apply()

    companion object {
        const val DEFAULT_CENTRAL_SERVER = "https://attend-pro-central.nmg200101.workers.dev"
    }
}
