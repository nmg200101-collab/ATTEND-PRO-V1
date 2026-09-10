package com.attendpro.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

object CentralServerClient {
    data class ActivationApplicant(
        val ownerName: String,
        val storeName: String,
        val subscriptionType: String,
        val phone: String = "",
        val address: String = "",
        val commercialId: String = "",
        val notes: String = ""
    )
    data class ActivationRequestResult(val requestId: String, val pollSecret: String, val status: String)
    data class SelfRegistrationResult(
        val status: String,
        val trialDays: Int,
        val expiresAt: Long,
        val ownerApprovalRequired: Boolean,
        val ownerNotified: Boolean
    )
    data class ActivationRecovery(
        val storeId: String, val storeName: String, val branchId: String,
        val licenseId: String, val accessToken: String, val expiresAt: Long,
        val maxEmployees: Int, val leaseUntil: Long, val serverTime: Long
    )
    data class ActivationStatus(
        val status: String,
        val licenseId: String = "",
        val accessToken: String = "",
        val expiresAt: Long = 0L,
        val maxEmployees: Int = 10,
        val leaseUntil: Long = 0L,
        val serverTime: Long = 0L,
        val reason: String = ""
    )
    data class StoreValidation(
        val status: String,
        val expiresAt: Long,
        val maxEmployees: Int,
        val leaseUntil: Long,
        val serverTime: Long,
        val reason: String = ""
    )
    data class PendingActivation(
        val requestId: String, val storeId: String, val storeName: String, val branchId: String, val createdAt: Long,
        val ownerName: String = "", val subscriptionType: String = "YEARLY", val phone: String = "",
        val address: String = "", val commercialId: String = "", val notes: String = ""
    )
    data class CentralStore(
        val storeId: String, val storeName: String, val branchId: String, val status: String,
        val expiresAt: Long, val maxEmployees: Int, val lastSeenAt: Long,
        val ownerName: String = "", val subscriptionType: String = "YEARLY", val phone: String = "",
        val recoveryMode: String = "OWNER_APPROVAL", val lastRecoveredAt: Long = 0L,
        val createdAt: Long = 0L, val licenseId: String = ""
    )
    data class RecoveryGrant(val code: String, val expiresAt: Long)
    data class SystemOverview(
        val totalStores: Int, val activeStores: Int, val suspendedStores: Int, val archivedStores: Int,
        val expiredStores: Int, val pendingActivations: Int, val registeredEmployees: Int,
        val recoveryGrants: Int, val serverVersion: String, val serverTime: Long
    )
    data class ActivationRecord(
        val requestId: String, val storeId: String, val storeName: String, val ownerName: String,
        val subscriptionType: String, val status: String, val createdAt: Long, val approvedAt: Long,
        val expiresAt: Long, val reason: String, val recoveryMode: String = "",
        val storeStatus: String = "", val lastRecoveredAt: Long = 0L, val hiddenAt: Long = 0L
    )
    data class RecoveryNotice(
        val id: Long, val storeId: String, val storeName: String, val deviceLabel: String,
        val action: String, val createdAt: Long
    )
    data class ServerEmployeeRegistration(
        val employeeId: String, val employeeName: String, val branchId: String, val installationId: String,
        val linkedAt: Long, val lastSeenAt: Long
    )
    data class AuditRecord(val id: Long, val action: String, val storeId: String, val details: String, val createdAt: Long)
    data class RemoteInboxItem(val transferId: String, val packageText: String)
    data class RemoteDashboard(val storeName: String, val branchId: String, val text: String)
    data class EmployeeLinkResult(
        val linked: Boolean,
        val linkedAt: Long,
        val lastSeenAt: Long,
        val gpsState: String = "UNKNOWN",
        val gpsDistanceMeters: Int = -1,
        val gpsAccuracyMeters: Int = -1,
        val gpsSeenAt: Long = 0L
    )
    data class EmployeePairingTicket(val code: String, val expiresAt: Long, val transportText: String)
    data class AttendancePull(val events: List<AttendanceEvent>, val cursor: Long)
    data class PresenceChallenge(
        val challengeId: String, val employeeId: String, val requiredMethod: AttendanceMethod,
        val status: String, val createdAt: Long, val expiresAt: Long, val verifiedAt: Long = 0L,
        val evidence: String = "", val action: AttendanceAction? = null
    )

    data class Message1975(
        val messageId: String,
        val storeId: String,
        val employeeId: String,
        val senderType: String,
        val senderId: String,
        val recipientType: String,
        val recipientId: String,
        val title: String,
        val body: String,
        val priority: String,
        val voiceEnabled: Boolean,
        val parentMessageId: String,
        val createdAt: Long,
        val readAt: Long
    )

    fun createPresenceChallenge(serverUrl: String, storeToken: String, storeId: String, identity: DeviceIdentity,
                                employeeId: String, method: AttendanceMethod, action: AttendanceAction? = null): Result<PresenceChallenge> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/store/presence-challenge", "POST", JSONObject().apply {
            put("employeeId", employeeId); put("requiredMethod", method.name)
            if (action != null) put("action", action.name)
        }, bearer = storeToken, deviceIdentity = identity, storeId = storeId)
        parsePresenceChallenge(o)
    }

    fun presenceChallengeStatus(serverUrl: String, storeToken: String, storeId: String, identity: DeviceIdentity,
                                challengeId: String): Result<PresenceChallenge> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/store/presence-challenge/status", "POST", JSONObject().apply {
            put("challengeId", challengeId)
        }, bearer = storeToken, deviceIdentity = identity, storeId = storeId)
        parsePresenceChallenge(o)
    }

    fun pollEmployeePresenceChallenge(serverUrl: String, storeId: String, employeeId: String,
                                      pairingSecret: String, installationId: String): Result<PresenceChallenge?> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/employee/presence-challenge/poll", "POST", JSONObject().apply {
            put("storeId", storeId); put("employeeId", employeeId); put("pairingSecret", pairingSecret); put("installationId", installationId)
        })
        if (!o.optBoolean("available", false)) null else parsePresenceChallenge(o)
    }

    fun completeEmployeePresenceChallenge(serverUrl: String, storeId: String, employeeId: String,
                                          pairingSecret: String, installationId: String, challengeId: String,
                                          method: AttendanceMethod, evidence: String): Result<PresenceChallenge> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/employee/presence-challenge/complete", "POST", JSONObject().apply {
            put("storeId", storeId); put("employeeId", employeeId); put("pairingSecret", pairingSecret); put("installationId", installationId)
            put("challengeId", challengeId); put("method", method.name); put("evidence", evidence)
        })
        parsePresenceChallenge(o)
    }

    private fun parsePresenceChallenge(o: JSONObject) = PresenceChallenge(
        o.getString("challengeId"), o.getString("employeeId"), AttendanceMethod.valueOf(o.getString("requiredMethod")),
        o.optString("status", "PENDING"), o.optLong("createdAt", 0L), o.optLong("expiresAt", 0L),
        o.optLong("verifiedAt", 0L), o.optString("evidence", ""),
        o.optString("action", "").takeIf { it.isNotBlank() }?.let { runCatching { AttendanceAction.valueOf(it) }.getOrNull() }
    )

    fun registerEmployeePhone(serverUrl: String, storeId: String, employeeId: String, employeeName: String,
                              branchId: String, pairingSecret: String, installationId: String,
                              allowedMethods: Set<String>): Result<EmployeeLinkResult> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/employee/link", "POST", JSONObject().apply {
            put("storeId", storeId); put("employeeId", employeeId); put("employeeName", employeeName)
            put("branchId", branchId); put("pairingSecret", pairingSecret); put("installationId", installationId)
            put("allowedMethods", JSONArray().apply { allowedMethods.sorted().forEach { put(it) } })
        })
        EmployeeLinkResult(
            o.optBoolean("linked", false), o.optLong("linkedAt", 0L), o.optLong("lastSeenAt", 0L),
            o.optString("gpsState", "UNKNOWN"), o.optInt("gpsDistanceMeters", -1),
            o.optInt("gpsAccuracyMeters", -1), o.optLong("gpsSeenAt", 0L)
        )
    }

    fun sendEmployeeGeoObservation(
        serverUrl: String,
        storeId: String,
        employeeId: String,
        pairingSecret: String,
        installationId: String,
        state: String,
        distanceMeters: Int,
        accuracyMeters: Int,
        observedAt: Long
    ): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/employee/geo-observation", "POST", JSONObject().apply {
            put("storeId", storeId)
            put("employeeId", employeeId)
            put("pairingSecret", pairingSecret)
            put("installationId", installationId)
            put("state", state.take(20))
            put("distanceMeters", distanceMeters.coerceIn(-1, 100_000))
            put("accuracyMeters", accuracyMeters.coerceIn(-1, 10_000))
            put("observedAt", observedAt.coerceAtLeast(0L))
        })
        Unit
    }

    fun createEmployeePairingTicket(serverUrl: String, storeToken: String, storeId: String, identity: DeviceIdentity,
                                    employeeId: String, provision: String, pairingSecret: String): Result<EmployeePairingTicket> = runCatching {
        requireHttps(serverUrl)
        val o=request(serverUrl,"/api/v1/store/employee-pairing-ticket","POST",JSONObject().apply {
            put("employeeId",employeeId);put("provision",provision);put("pairingSecret",pairingSecret)
        },bearer=storeToken,deviceIdentity=identity,storeId=storeId)
        EmployeePairingTicket(o.getString("code"),o.getLong("expiresAt"),o.optString("transportText","APPAIR:${o.getString("code")}"))
    }

    fun claimEmployeePairingTicket(serverUrl: String, code: String, installationId: String): Result<String> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl,"/api/v1/employee/pairing-claim","POST",JSONObject().apply {
            put("code",code.replace(Regex("[^A-Za-z0-9]"),"").uppercase());put("installationId",installationId)
        }).getString("provision")
    }

    fun employeeLinkStatus(serverUrl: String, storeToken: String, storeId: String, identity: DeviceIdentity,
                           employeeId: String): Result<EmployeeLinkResult> = runCatching {
        requireHttps(serverUrl)
        val path = "/api/v1/store/employee-link"
        val o = request(serverUrl, path, "POST", JSONObject().apply { put("employeeId", employeeId) }, bearer = storeToken, deviceIdentity = identity, storeId = storeId)
        EmployeeLinkResult(
            o.optBoolean("linked", false), o.optLong("linkedAt", 0L), o.optLong("lastSeenAt", 0L),
            o.optString("gpsState", "UNKNOWN"), o.optInt("gpsDistanceMeters", -1),
            o.optInt("gpsAccuracyMeters", -1), o.optLong("gpsSeenAt", 0L)
        )
    }

    fun submitEmployeeAttendance(serverUrl: String, storeId: String, employeeId: String, employeeName: String,
                                 branchId: String, pairingSecret: String, installationId: String,
                                 action: AttendanceAction, method: AttendanceMethod, evidence: String = ""): Result<String> = runCatching {
        requireHttps(serverUrl)
        val eventId = java.util.UUID.randomUUID().toString()
        val o = request(serverUrl, "/api/v1/employee/attendance", "POST", JSONObject().apply {
            put("storeId", storeId); put("employeeId", employeeId); put("employeeName", employeeName)
            put("branchId", branchId); put("pairingSecret", pairingSecret); put("installationId", installationId)
            put("eventId", eventId); put("action", action.name); put("method", method.name)
            put("timestampEpochMillis", System.currentTimeMillis()); put("evidence", evidence)
        })
        o.optString("eventId", eventId)
    }

    fun pullEmployeeAttendance(serverUrl: String, storeToken: String, storeId: String, identity: DeviceIdentity,
                               afterCursor: Long): Result<AttendancePull> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/store/attendance/pull", "POST", JSONObject().apply {
            put("afterCursor", afterCursor); put("limit", 200)
        }, bearer = storeToken, deviceIdentity = identity, storeId = storeId)
        val rows = o.optJSONArray("events") ?: JSONArray()
        val events = (0 until rows.length()).mapNotNull { index ->
            runCatching { AttendanceEvent.fromJson(rows.getJSONObject(index)).copy(synced = true) }.getOrNull()
        }
        AttendancePull(events, o.optLong("cursor", afterCursor))
    }

    fun requestActivation(serverUrl: String, storeId: String, branchId: String, applicant: ActivationApplicant, identity: DeviceIdentity): Result<ActivationRequestResult> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/activation/request", "POST", JSONObject().apply {
            put("storeId", storeId); put("storeName", applicant.storeName); put("branchId", branchId)
            put("ownerName", applicant.ownerName); put("subscriptionType", applicant.subscriptionType)
            put("phone", applicant.phone); put("address", applicant.address)
            put("commercialId", applicant.commercialId); put("notes", applicant.notes)
            put("devicePublicKey", identity.publicKeyB64()); put("recoveryFingerprint", identity.recoveryFingerprint())
        })
        ActivationRequestResult(o.getString("requestId"), o.getString("pollSecret"), o.optString("status", "PENDING"))
    }

    fun selfRegisterActivation(
        serverUrl: String,
        requestId: String,
        pollSecret: String,
        storeId: String,
        identity: DeviceIdentity
    ): Result<SelfRegistrationResult> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/activation/self-register", "POST", JSONObject().apply {
            put("requestId", requestId)
            put("pollSecret", pollSecret)
        }, deviceIdentity = identity, storeId = storeId)
        SelfRegistrationResult(
            status = o.optString("status", "APPROVED"),
            trialDays = o.optInt("trialDays", 3).coerceIn(1, 30),
            expiresAt = o.optLong("expiresAt", 0L),
            ownerApprovalRequired = o.optBoolean("ownerApprovalRequired", false),
            ownerNotified = o.optBoolean("ownerNotified", true)
        )
    }

    fun recoverActivation(serverUrl: String, identity: DeviceIdentity): Result<ActivationRecovery> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/activation/recover", "POST", JSONObject().apply {
            put("devicePublicKey", identity.publicKeyB64())
            put("recoveryFingerprint", identity.recoveryFingerprint())
            put("deviceLabel", identity.deviceLabel())
        }, deviceIdentity = identity, storeId = "RECOVERY")
        ActivationRecovery(
            storeId = o.getString("storeId"), storeName = o.optString("storeName", "جهاز المحل"),
            branchId = o.optString("branchId", "MAIN"), licenseId = o.getString("licenseId"),
            accessToken = o.getString("accessToken"), expiresAt = o.getLong("expiresAt"),
            maxEmployees = o.optInt("maxEmployees", 10).coerceIn(1, 10000),
            leaseUntil = o.getLong("leaseUntil"), serverTime = o.getLong("serverTime")
        )
    }

    fun recoverActivationWithGrant(serverUrl: String, grantCode: String, identity: DeviceIdentity): Result<ActivationRecovery> = runCatching {
        requireHttps(serverUrl)
        val cleanCode = grantCode.trim().replace(Regex("[^A-Za-z0-9]"), "").uppercase()
        require(cleanCode.length in 8..16) { "رمز تصريح الاستعادة غير صالح" }
        val o = request(serverUrl, "/api/v1/activation/recover-approved", "POST", JSONObject().apply {
            put("code", cleanCode)
            put("devicePublicKey", identity.publicKeyB64())
            put("recoveryFingerprint", identity.recoveryFingerprint())
        }, deviceIdentity = identity, storeId = "RECOVERY")
        ActivationRecovery(
            storeId = o.getString("storeId"), storeName = o.optString("storeName", "جهاز المحل"),
            branchId = o.optString("branchId", "MAIN"), licenseId = o.getString("licenseId"),
            accessToken = o.getString("accessToken"), expiresAt = o.getLong("expiresAt"),
            maxEmployees = o.optInt("maxEmployees", 10).coerceIn(1, 10000),
            leaseUntil = o.getLong("leaseUntil"), serverTime = o.getLong("serverTime")
        )
    }

    fun activationStatus(serverUrl: String, requestId: String, pollSecret: String, storeId: String, identity: DeviceIdentity): Result<ActivationStatus> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/activation/status", "POST", JSONObject().apply {
            put("requestId", requestId); put("pollSecret", pollSecret)
        }, deviceIdentity = identity, storeId = storeId)
        ActivationStatus(
            status = o.optString("status", "PENDING"), licenseId = o.optString("licenseId", ""), accessToken = o.optString("accessToken", ""),
            expiresAt = o.optLong("expiresAt", 0L), maxEmployees = o.optInt("maxEmployees", 10).coerceIn(1, 10000),
            leaseUntil = o.optLong("leaseUntil", 0L), serverTime = o.optLong("serverTime", 0L), reason = o.optString("reason", "")
        )
    }

    fun validateStore(serverUrl: String, storeToken: String, storeId: String, identity: DeviceIdentity): Result<StoreValidation> = runCatching {
        requireHttps(serverUrl)
        require(storeToken.isNotBlank()) { "بيانات التفعيل المركزي غير مكتملة" }
        val o = request(serverUrl, "/api/v1/store/validate", "GET", null, bearer = storeToken, deviceIdentity = identity, storeId = storeId)
        StoreValidation(
            status = o.optString("status", "UNKNOWN"),
            expiresAt = o.optLong("expiresAt", 0L),
            maxEmployees = o.optInt("maxEmployees", 0).coerceAtLeast(0),
            leaseUntil = o.optLong("leaseUntil", 0L),
            serverTime = o.optLong("serverTime", 0L),
            reason = o.optString("reason", "")
        )
    }

    fun enrollRecovery(serverUrl: String, storeToken: String, storeId: String, identity: DeviceIdentity): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/store/recovery/enroll", "POST", JSONObject().apply {
            put("recoveryFingerprint", identity.recoveryFingerprint())
        }, bearer = storeToken, deviceIdentity = identity, storeId = storeId)
        Unit
    }

    fun listPendingActivations(serverUrl: String, ownerApiKey: String): Result<List<PendingActivation>> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/admin/activation/pending", "GET", null, bearer = ownerApiKey)
        val a = o.optJSONArray("requests") ?: JSONArray()
        (0 until a.length()).map { i -> val x = a.getJSONObject(i); PendingActivation(
            x.getString("requestId"), x.getString("storeId"), x.optString("storeName", "محل"), x.optString("branchId", "MAIN"), x.optLong("createdAt", 0L),
            x.optString("ownerName", ""), x.optString("subscriptionType", "YEARLY"), x.optString("phone", ""),
            x.optString("address", ""), x.optString("commercialId", ""), x.optString("notes", "")
        ) }
    }

    fun listCentralStores(serverUrl: String, ownerApiKey: String): Result<List<CentralStore>> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/admin/stores", "GET", null, bearer = ownerApiKey)
        val a = o.optJSONArray("stores") ?: JSONArray()
        (0 until a.length()).map { i ->
            val x = a.getJSONObject(i)
            CentralStore(
                x.getString("storeId"), x.optString("storeName", "محل"), x.optString("branchId", "MAIN"),
                x.optString("status", "ACTIVE"), x.optLong("expiresAt", 0L), x.optInt("maxEmployees", 10),
                x.optLong("lastSeenAt", 0L), x.optString("ownerName", ""), x.optString("subscriptionType", "YEARLY"),
                x.optString("phone", ""), x.optString("recoveryMode", "OWNER_APPROVAL"),
                x.optLong("lastRecoveredAt", 0L), x.optLong("createdAt", 0L), x.optString("licenseId", "")
            )
        }
    }

    fun systemOverview(serverUrl: String, ownerApiKey: String): Result<SystemOverview> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/admin/overview", "GET", null, bearer = ownerApiKey)
        SystemOverview(
            o.optInt("totalStores"), o.optInt("activeStores"), o.optInt("suspendedStores"), o.optInt("archivedStores"),
            o.optInt("expiredStores"), o.optInt("pendingActivations"), o.optInt("registeredEmployees"),
            o.optInt("recoveryGrants"), o.optString("serverVersion", ""), o.optLong("serverTime", 0L)
        )
    }

    fun listActivationHistory(serverUrl: String, ownerApiKey: String): Result<List<ActivationRecord>> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/admin/activation/all", "GET", null, bearer = ownerApiKey)
        val a = o.optJSONArray("requests") ?: JSONArray()
        (0 until a.length()).map { i -> val x = a.getJSONObject(i); ActivationRecord(
            x.getString("requestId"), x.getString("storeId"), x.optString("storeName", "محل"),
            x.optString("ownerName", ""), x.optString("subscriptionType", "YEARLY"), x.optString("status", "PENDING"),
            x.optLong("createdAt", 0L), x.optLong("approvedAt", 0L), x.optLong("expiresAt", 0L), x.optString("reason", "")
        ) }
    }

    fun listActivationRecordsAdmin(serverUrl: String, ownerApiKey: String, includeArchived: Boolean = false): Result<List<ActivationRecord>> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/admin/activation/records?includeArchived=${if (includeArchived) 1 else 0}", "GET", null, bearer = ownerApiKey)
        val a = o.optJSONArray("requests") ?: JSONArray()
        (0 until a.length()).map { i -> val x = a.getJSONObject(i); ActivationRecord(
            x.getString("requestId"), x.getString("storeId"), x.optString("storeName", "محل"),
            x.optString("ownerName", ""), x.optString("subscriptionType", "YEARLY"), x.optString("status", "PENDING"),
            x.optLong("createdAt", 0L), x.optLong("approvedAt", 0L), x.optLong("expiresAt", 0L), x.optString("reason", ""),
            x.optString("recoveryMode", ""), x.optString("storeStatus", ""), x.optLong("lastRecoveredAt", 0L), x.optLong("hiddenAt", 0L)
        ) }
    }

    fun archiveActivationRequest(serverUrl: String, ownerApiKey: String, requestId: String, archived: Boolean): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/admin/activation/archive-request", "POST", JSONObject().apply {
            put("requestId", requestId); put("archived", archived)
        }, bearer = ownerApiKey)
        Unit
    }

    fun deleteActivationRequest(serverUrl: String, ownerApiKey: String, requestId: String): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/admin/activation/delete-request", "POST", JSONObject().apply {
            put("requestId", requestId); put("confirm", "DELETE_ACTIVATION_REQUEST")
        }, bearer = ownerApiKey)
        Unit
    }

    fun setAllRecoveryModes(serverUrl: String, ownerApiKey: String, mode: String): Result<Int> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/admin/stores/recovery-mode-bulk", "POST", JSONObject().apply { put("mode", mode) }, bearer = ownerApiKey)
        o.optInt("updated", 0)
    }

    fun listRecoveryNotifications(serverUrl: String, ownerApiKey: String, afterId: Long = 0L, limit: Int = 100): Result<List<RecoveryNotice>> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/admin/recovery-notifications?after=${afterId.coerceAtLeast(0L)}&limit=${limit.coerceIn(1, 200)}", "GET", null, bearer = ownerApiKey)
        val a = o.optJSONArray("items") ?: JSONArray()
        (0 until a.length()).map { i -> val x = a.getJSONObject(i); RecoveryNotice(
            x.optLong("id", 0L), x.optString("storeId", ""), x.optString("storeName", "محل"),
            x.optString("deviceLabel", "Android"), x.optString("action", "ACTIVATION_AUTO_RECOVER_NOTICE"), x.optLong("createdAt", 0L)
        ) }
    }

    fun setActivationRequestStatus(serverUrl: String, ownerApiKey: String, requestId: String, status: String, reason: String = ""): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/admin/activation/status", "POST", JSONObject().apply {
            put("requestId", requestId); put("status", status); put("reason", reason.take(300))
        }, bearer = ownerApiKey)
        Unit
    }

    fun setRecoveryMode(serverUrl: String, ownerApiKey: String, storeId: String, mode: String): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/admin/stores/recovery-mode", "POST", JSONObject().apply {
            put("storeId", storeId); put("mode", mode)
        }, bearer = ownerApiKey)
        Unit
    }

    fun revokeRecoveryGrants(serverUrl: String, ownerApiKey: String, storeId: String): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/admin/stores/recovery-grants/revoke", "POST", JSONObject().apply { put("storeId", storeId) }, bearer = ownerApiKey)
        Unit
    }

    fun resetStoreDevice(serverUrl: String, ownerApiKey: String, storeId: String): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/admin/stores/device-reset", "POST", JSONObject().apply { put("storeId", storeId); put("confirm", "RESET_DEVICE") }, bearer = ownerApiKey)
        Unit
    }

    fun listServerEmployeeRegistrations(serverUrl: String, ownerApiKey: String, storeId: String): Result<List<ServerEmployeeRegistration>> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/admin/stores/employees/list", "POST", JSONObject().apply { put("storeId", storeId) }, bearer = ownerApiKey)
        val a = o.optJSONArray("employees") ?: JSONArray()
        (0 until a.length()).map { i -> val x = a.getJSONObject(i); ServerEmployeeRegistration(
            x.getString("employeeId"), x.optString("employeeName", x.getString("employeeId")),
            x.optString("branchId", "MAIN"), x.optString("installationId", ""), x.optLong("linkedAt", 0L), x.optLong("lastSeenAt", 0L)
        ) }
    }

    fun deleteServerEmployeeRegistration(serverUrl: String, ownerApiKey: String, storeId: String, employeeId: String): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/admin/stores/employees/delete", "POST", JSONObject().apply {
            put("storeId", storeId); put("employeeId", employeeId); put("confirm", "DELETE_EMPLOYEE_LINK")
        }, bearer = ownerApiKey)
        Unit
    }

    fun listSystemAudit(serverUrl: String, ownerApiKey: String, storeId: String = ""): Result<List<AuditRecord>> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/admin/audit/list", "POST", JSONObject().apply { put("storeId", storeId); put("limit", 200) }, bearer = ownerApiKey)
        val a = o.optJSONArray("items") ?: JSONArray()
        (0 until a.length()).map { i -> val x = a.getJSONObject(i); AuditRecord(
            x.optLong("id", 0L), x.optString("action", ""), x.optString("storeId", ""), x.optString("details", ""), x.optLong("createdAt", 0L)
        ) }
    }

    fun deleteStoreFromServer(serverUrl: String, ownerApiKey: String, storeId: String): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/admin/stores/delete", "POST", JSONObject().apply {
            put("storeId", storeId); put("confirm", "DELETE_STORE_AND_DATA")
        }, bearer = ownerApiKey)
        Unit
    }

    fun approveActivation(serverUrl: String, ownerApiKey: String, requestId: String, days: Int, maxEmployees: Int): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/admin/activation/approve", "POST", JSONObject().apply { put("requestId", requestId); put("days", days.coerceIn(1, 3650)); put("maxEmployees", maxEmployees.coerceIn(1, 10000)) }, bearer = ownerApiKey)
        Unit
    }

    fun createRecoveryGrant(serverUrl: String, ownerApiKey: String, storeId: String): Result<RecoveryGrant> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/admin/stores/recovery-grant", "POST", JSONObject().apply {
            put("storeId", storeId)
        }, bearer = ownerApiKey)
        RecoveryGrant(o.getString("code"), o.getLong("expiresAt"))
    }

    fun archiveStore(serverUrl: String, ownerApiKey: String, storeId: String, archived: Boolean): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/admin/stores/archive", "POST", JSONObject().apply {
            put("storeId", storeId)
            put("archived", archived)
        }, bearer = ownerApiKey)
        Unit
    }

    fun suspendStore(serverUrl: String, ownerApiKey: String, storeId: String, suspended: Boolean): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/admin/stores/status", "POST", JSONObject().apply { put("storeId", storeId); put("status", if (suspended) "SUSPENDED" else "ACTIVE") }, bearer = ownerApiKey)
        Unit
    }

    fun renewStore(serverUrl: String, ownerApiKey: String, storeId: String, days: Int, maxEmployees: Int): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/admin/stores/renew", "POST", JSONObject().apply {
            put("storeId", storeId)
            put("days", days.coerceIn(1, 3650))
            put("maxEmployees", maxEmployees.coerceIn(1, 10000))
        }, bearer = ownerApiKey)
        Unit
    }

    fun registerReceiver(serverUrl: String, storeToken: String, storeId: String, identity: DeviceIdentity, receiverId: String, name: String, secret: String): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/report-receivers/register", "POST", JSONObject().apply { put("receiverId", receiverId); put("name", name); put("secret", secret) }, bearer = storeToken, deviceIdentity = identity, storeId = storeId)
        Unit
    }

    fun setReceiverActive(serverUrl: String, storeToken: String, storeId: String, identity: DeviceIdentity, receiverId: String, active: Boolean): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/report-receivers/status", "POST", JSONObject().apply { put("receiverId", receiverId); put("active", active) }, bearer = storeToken, deviceIdentity = identity, storeId = storeId)
        Unit
    }

    fun pushReport(serverUrl: String, storeToken: String, storeId: String, identity: DeviceIdentity, receiverId: String, transferId: String, packageText: String, confirmationHash: String): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/reports/push", "POST", JSONObject().apply { put("receiverId", receiverId); put("transferId", transferId); put("packageText", packageText); put("confirmationHash", confirmationHash) }, bearer = storeToken, deviceIdentity = identity, storeId = storeId)
        Unit
    }

    fun receiverInbox(serverUrl: String, receiverId: String, secret: String): Result<List<RemoteInboxItem>> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/reports/inbox", "POST", JSONObject().apply { put("receiverId", receiverId); put("secret", secret) })
        val a = o.optJSONArray("reports") ?: JSONArray()
        (0 until a.length()).map { i -> val x = a.getJSONObject(i); RemoteInboxItem(x.getString("transferId"), x.getString("packageText")) }
    }

    fun confirmRemoteReport(serverUrl: String, receiverId: String, secret: String, transferId: String, confirmationCode: String): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/reports/confirm", "POST", JSONObject().apply { put("receiverId", receiverId); put("secret", secret); put("transferId", transferId); put("confirmationCode", confirmationCode) })
        Unit
    }

    fun confirmedTransfers(serverUrl: String, storeToken: String, storeId: String, identity: DeviceIdentity): Result<Set<String>> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/reports/confirmations", "GET", null, bearer = storeToken, deviceIdentity = identity, storeId = storeId)
        val a = o.optJSONArray("transferIds") ?: JSONArray()
        (0 until a.length()).map { a.getString(it) }.toSet()
    }

    fun remoteReport(serverUrl: String, receiverId: String, secret: String, period: String): Result<String> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/monitor/report", "POST", JSONObject().apply { put("receiverId", receiverId); put("secret", secret); put("period", period) })
        o.optString("reportText", "لا توجد بيانات")
    }

    fun remoteDashboard(serverUrl: String, receiverId: String, secret: String): Result<RemoteDashboard> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/monitor/summary", "POST", JSONObject().apply { put("receiverId", receiverId); put("secret", secret) })
        val recent = o.optJSONArray("recent") ?: JSONArray()
        val lines = mutableListOf<String>()
        lines += "الحضور اليوم: ${o.optInt("checkIns", 0)} • الانصراف: ${o.optInt("checkOuts", 0)}"
        lines += "حركات اليوم: ${o.optInt("todayEvents", 0)} • آخر اتصال: ${o.optString("lastSeen", "-")}"
        if (recent.length() > 0) {
            lines += ""; lines += "آخر الحركات:"
            for (i in 0 until minOf(recent.length(), 10)) { val e = recent.getJSONObject(i); lines += "• ${e.optString("employeeName", e.optString("employeeId", ""))} — ${e.optString("action", "")} — ${e.optString("time", "")}" }
        }
        RemoteDashboard(o.optString("storeName", "ATTEND PRO"), o.optString("branchId", "MAIN"), lines.joinToString("\n"))
    }

    fun syncEventsCentral(serverUrl: String, storeToken: String, storeId: String, identity: DeviceIdentity, events: List<AttendanceEvent>): Result<Set<String>> = runCatching {
        requireHttps(serverUrl)
        if (events.isEmpty()) return@runCatching emptySet()
        val body = JSONObject().apply { put("events", JSONArray().apply { events.forEach { event -> put(event.toJson()) } }) }
        val o = request(serverUrl, "/api/v1/attendance/events/batch", "POST", body, bearer = storeToken, deviceIdentity = identity, storeId = storeId)
        val a = o.optJSONArray("acceptedIds")
            ?: throw IllegalStateException("استجابة المزامنة غير مكتملة: acceptedIds مفقودة")
        val accepted = (0 until a.length()).mapNotNull { index ->
            a.optString(index).takeIf { it.isNotBlank() }
        }.toSet()
        val requestedIds = events.map { it.eventId }.toSet()
        require(accepted.all { it in requestedIds }) { "الخادم أعاد معرف عملية غير موجود في الدفعة" }
        accepted
    }

    private fun requireHttps(serverUrl: String) { require(serverUrl.trim().startsWith("https://")) { "عنوان الخادم يجب أن يبدأ بـ https://" } }

    private fun parseMessage1975(o: JSONObject) = Message1975(
        o.optString("messageId"), o.optString("storeId"), o.optString("employeeId"),
        o.optString("senderType"), o.optString("senderId"), o.optString("recipientType"), o.optString("recipientId"),
        o.optString("title"), o.optString("body"), o.optString("priority", "NORMAL"), o.optBoolean("voiceEnabled", false),
        o.optString("parentMessageId"), o.optLong("createdAt", 0L), o.optLong("readAt", 0L)
    )

    private fun parseMessages1975(o: JSONObject): List<Message1975> {
        val a = o.optJSONArray("messages") ?: JSONArray()
        return (0 until a.length()).mapNotNull { i -> runCatching { parseMessage1975(a.getJSONObject(i)) }.getOrNull() }
    }

    fun sendStoreMessageToEmployee(serverUrl: String, storeToken: String, storeId: String, identity: DeviceIdentity,
                                   employeeId: String, title: String, message: String, priority: String, voiceEnabled: Boolean): Result<String> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/messages/store/send-employee", "POST", JSONObject().apply {
            put("employeeId", employeeId); put("title", title); put("message", message); put("priority", priority); put("voiceEnabled", voiceEnabled)
        }, bearer = storeToken, deviceIdentity = identity, storeId = storeId).optString("messageId")
    }

    fun storeMessagesInbox(serverUrl: String, storeToken: String, storeId: String, identity: DeviceIdentity, limit: Int = 100): Result<List<Message1975>> = runCatching {
        requireHttps(serverUrl)
        parseMessages1975(request(serverUrl, "/api/v1/messages/store/inbox", "POST", JSONObject().put("limit", limit.coerceIn(1, 200)), bearer = storeToken, deviceIdentity = identity, storeId = storeId))
    }

    fun markStoreMessageRead(serverUrl: String, storeToken: String, storeId: String, identity: DeviceIdentity, messageId: String): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/messages/store/mark-read", "POST", JSONObject().put("messageId", messageId), bearer = storeToken, deviceIdentity = identity, storeId = storeId)
        Unit
    }

    fun employeeMessages(serverUrl: String, storeId: String, employeeId: String, pairingSecret: String, installationId: String,
                         includeRead: Boolean = false, limit: Int = 50): Result<List<Message1975>> = runCatching {
        requireHttps(serverUrl)
        parseMessages1975(request(serverUrl, "/api/v1/messages/employee/poll", "POST", JSONObject().apply {
            put("storeId", storeId); put("employeeId", employeeId); put("pairingSecret", pairingSecret); put("installationId", installationId)
            put("includeRead", includeRead); put("limit", limit.coerceIn(1, 100))
        }))
    }

    fun markEmployeeMessageRead(serverUrl: String, storeId: String, employeeId: String, pairingSecret: String, installationId: String, messageId: String): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/messages/employee/mark-read", "POST", JSONObject().apply {
            put("storeId", storeId); put("employeeId", employeeId); put("pairingSecret", pairingSecret); put("installationId", installationId); put("messageId", messageId)
        })
        Unit
    }

    fun replyEmployeeMessage(serverUrl: String, storeId: String, employeeId: String, pairingSecret: String, installationId: String,
                             parentMessageId: String, message: String): Result<String> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/messages/employee/reply", "POST", JSONObject().apply {
            put("storeId", storeId); put("employeeId", employeeId); put("pairingSecret", pairingSecret); put("installationId", installationId)
            put("parentMessageId", parentMessageId); put("message", message)
        }).optString("messageId")
    }

    private fun request(serverUrl: String, path: String, method: String, body: JSONObject?, bearer: String = "", deviceIdentity: DeviceIdentity? = null, storeId: String = ""): JSONObject {
        val bodyText = body?.toString().orEmpty()
        var connection: HttpURLConnection? = null
        var httpFailureRecorded = false
        try {
            connection = (URL(serverUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method; connectTimeout = 10_000; readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                if (bearer.isNotBlank()) setRequestProperty("Authorization", "Bearer $bearer")
                if (deviceIdentity != null && storeId.isNotBlank()) {
                    val ts = System.currentTimeMillis().toString()
                    val nonce = randomToken(18)
                    val bodyHash = sha256Hex(bodyText)
                    val canonical = "$method\n$path\n$ts\n$nonce\n$bodyHash"
                    setRequestProperty("X-AP-Store", storeId)
                    setRequestProperty("X-AP-Time", ts)
                    setRequestProperty("X-AP-Nonce", nonce)
                    setRequestProperty("X-AP-Body-SHA256", bodyHash)
                    setRequestProperty("X-AP-Signature", deviceIdentity.sign(canonical))
                }
                if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json; charset=utf-8") }
            }
            if (body != null) OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(bodyText) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() } }.orEmpty()
            val parsed = runCatching { JSONObject(text.ifBlank { "{}" }) }.getOrElse { JSONObject() }
            if (code !in 200..299) {
                val message = parsed.optString("error", "HTTP $code")
                ServerDiagnostics.failure(code, path, message)
                httpFailureRecorded = true
                error("HTTP $code: $message")
            }
            ServerDiagnostics.success(code, path)
            return parsed
        } catch (t: Throwable) {
            if (!httpFailureRecorded) ServerDiagnostics.failure(0, path, t.message ?: t.javaClass.simpleName)
            throw t
        } finally {
            connection?.disconnect()
        }
    }

    private fun randomToken(bytes: Int): String {
        val raw = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
    }
    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
