package com.attendpro.core

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object ReportProtocol {
    private const val INVITE_PREFIX = "APRRI1:"
    private const val PACKAGE_PREFIX = "APRPT1:"
    private const val REMOTE_GRANT_PREFIX = "APRRG1:"
    private const val CLOCK_SKEW_GRACE_MS = 5 * 60_000L

    data class ReceiverInvite(val receiverId: String, val name: String, val secret: String, val expiresAt: Long)
    data class ReportPackage(val receiverId: String, val transferId: String, val storeName: String, val branchId: String, val periodLabel: String, val createdAt: Long, val reportText: String, val confirmationCode: String)
    data class RemoteReceiverGrant(
        val receiverId: String,
        val serverUrl: String,
        val storeName: String,
        val branchId: String,
        val expiresAt: Long,
        val storeId: String = ""
    )

    fun encodeRemoteGrant(grant: RemoteReceiverGrant): String = REMOTE_GRANT_PREFIX + b64(JSONObject().apply {
        put("v", 2); put("r", grant.receiverId.trim()); put("u", grant.serverUrl.trim()); put("m", grant.storeName); put("b", grant.branchId); put("e", grant.expiresAt); put("i", grant.storeId.trim())
    }.toString().toByteArray(Charsets.UTF_8))

    fun decodeRemoteGrant(text: String, expectedReceiverId: String): RemoteReceiverGrant? = runCatching {
        val clean = text.trim(); if (!clean.startsWith(REMOTE_GRANT_PREFIX)) return@runCatching null
        val o = JSONObject(String(b64d(clean.removePrefix(REMOTE_GRANT_PREFIX)), Charsets.UTF_8))
        if (o.optInt("v", 1) !in 1..2) return@runCatching null
        val encodedReceiverId = o.optString("r", "").trim()
        val expected = expectedReceiverId.trim()
        if (encodedReceiverId.isBlank() || expected.isBlank() || !encodedReceiverId.equals(expected, ignoreCase = true)) return@runCatching null
        val grant = RemoteReceiverGrant(
            encodedReceiverId,
            o.getString("u").trim(),
            o.optString("m", "ATTEND PRO"),
            o.optString("b", "MAIN"),
            o.getLong("e"),
            o.optString("i", "").trim()
        )
        val now = System.currentTimeMillis()
        if (grant.expiresAt + CLOCK_SKEW_GRACE_MS < now ||
            (grant.serverUrl.isNotBlank() && !grant.serverUrl.startsWith("https://", ignoreCase = true))
        ) null else grant
    }.getOrNull()

    fun newSecret(): String = Base64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)

    fun encodeInvite(invite: ReceiverInvite): String = INVITE_PREFIX + b64(JSONObject().apply {
        put("v", 1); put("r", invite.receiverId.trim()); put("n", invite.name); put("s", invite.secret); put("e", invite.expiresAt)
    }.toString().toByteArray(Charsets.UTF_8))

    fun decodeInvite(text: String): ReceiverInvite? = runCatching {
        val clean = text.trim(); if (!clean.startsWith(INVITE_PREFIX)) return@runCatching null
        val o = JSONObject(String(b64d(clean.removePrefix(INVITE_PREFIX)), Charsets.UTF_8))
        if (o.optInt("v", 1) != 1) return@runCatching null
        val receiverId = o.getString("r").trim(); val secret = o.getString("s").trim(); val expiresAt = o.getLong("e")
        if (receiverId.isBlank() || secret.isBlank() || expiresAt + CLOCK_SKEW_GRACE_MS < System.currentTimeMillis()) return@runCatching null
        ReceiverInvite(receiverId, o.optString("n", "هاتف مراقبة"), secret, expiresAt)
    }.getOrNull()

    fun encodePackage(pkg: ReportPackage, secret: String): String {
        val plain = JSONObject().apply {
            put("v", 1); put("r", pkg.receiverId); put("t", pkg.transferId); put("m", pkg.storeName); put("b", pkg.branchId)
            put("p", pkg.periodLabel); put("c", pkg.createdAt); put("x", pkg.reportText); put("a", pkg.confirmationCode)
        }.toString().toByteArray(Charsets.UTF_8)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(secret), GCMParameterSpec(128, iv))
        return PACKAGE_PREFIX + b64(iv + cipher.doFinal(plain))
    }

    fun decodePackage(text: String, expectedReceiverId: String, secret: String): ReportPackage? = runCatching {
        val clean = extractPackage(text) ?: return@runCatching null
        val all = b64d(clean.removePrefix(PACKAGE_PREFIX)); if (all.size <= 12) return@runCatching null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(secret), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        val o = JSONObject(String(cipher.doFinal(all.copyOfRange(12, all.size)), Charsets.UTF_8))
        if (!o.getString("r").trim().equals(expectedReceiverId.trim(), ignoreCase = true)) return@runCatching null
        ReportPackage(o.getString("r"), o.getString("t"), o.optString("m", "ATTEND PRO"), o.optString("b", "MAIN"), o.optString("p", "تقرير"), o.optLong("c", System.currentTimeMillis()), o.optString("x", ""), o.optString("a", ""))
    }.getOrNull()

    private fun extractPackage(text: String): String? { val idx = text.indexOf(PACKAGE_PREFIX); if (idx < 0) return null; return text.substring(idx).lineSequence().firstOrNull()?.trim() }
    private fun key(secret: String): SecretKeySpec = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8)), "AES")
    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    private fun b64d(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
}

data class ReceivedReport(
    val transferId: String,
    val storeName: String,
    val branchId: String,
    val periodLabel: String,
    val createdAt: Long,
    val receivedAt: Long,
    val reportText: String,
    val confirmationCode: String,
    val storeId: String = ""
)

data class ReceiverStoreBinding(
    val storeId: String,
    val serverUrl: String,
    val storeName: String,
    val branchId: String,
    val active: Boolean = true,
    val canReceiveReports: Boolean = true,
    val canMessageEmployees: Boolean = false,
    val canManageStore: Boolean = false,
    val linkedAt: Long = System.currentTimeMillis(),
    val lastServerRefreshAt: Long = 0L,
    val storeLastSeenAt: Long = 0L
)

data class ReceiverMessageReply(
    val messageId: String,
    val storeId: String,
    val employeeId: String,
    val title: String,
    val body: String,
    val priority: String,
    val createdAt: Long,
    val readAt: Long
)

data class ReceiverOutgoingMessage(
    val localId: String,
    val storeId: String,
    val employeeId: String,
    val title: String,
    val body: String,
    val priority: String,
    val createdAt: Long,
    val state: String = "PENDING",
    val attempts: Int = 0,
    val lastAttemptAt: Long = 0L,
    val nextAttemptAt: Long = 0L,
    val remoteMessageId: String = "",
    val lastError: String = ""
)

class ReportReceiverStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("report_receiver_store", Context.MODE_PRIVATE)
    private val secureVault = SecureTokenVault(appContext)
    private val stableReceiverId: String by lazy {
        val old = prefs.getString("receiverId", "").orEmpty().trim()
        if (old.isNotBlank()) old else ("RCV-" + UUID.randomUUID().toString().substring(0, 12).uppercase()).also { prefs.edit().putString("receiverId", it).commit() }
    }
    private val stableSecret: String by lazy {
        secureVault.get("report_receiver_secret").takeIf { it.isNotBlank() } ?: run {
            val legacy = prefs.getString("secret", "").orEmpty()
            val value = legacy.ifBlank { ReportProtocol.newSecret() }
            secureVault.put("report_receiver_secret", value)
            if (legacy.isNotBlank()) prefs.edit().remove("secret").apply()
            value
        }
    }
    val receiverId: String get() = stableReceiverId
    val secret: String get() = stableSecret
    var receiverName: String get() = prefs.getString("receiverName", "هاتف صاحب المحل") ?: "هاتف صاحب المحل"; set(value) = prefs.edit().putString("receiverName", value.trim().ifBlank { "هاتف صاحب المحل" }).apply()
    var serverUrl: String get() = prefs.getString("serverUrl", "") ?: ""; set(value) = prefs.edit().putString("serverUrl", value.trim()).apply()
    var remoteDashboardText: String get() = prefs.getString("remoteDashboardText", "") ?: ""; set(value) = prefs.edit().putString("remoteDashboardText", value).apply()
    var remoteLastRefreshAt: Long get() = prefs.getLong("remoteLastRefreshAt", 0L); set(value) = prefs.edit().putLong("remoteLastRefreshAt", value).apply()
    var canReceiveReports: Boolean get() = prefs.getBoolean("canReceiveReports", true); set(value) = prefs.edit().putBoolean("canReceiveReports", value).apply()
    var canMessageEmployees: Boolean get() = prefs.getBoolean("canMessageEmployees", false); set(value) = prefs.edit().putBoolean("canMessageEmployees", value).apply()
    var canManageStore: Boolean get() = false; set(value) { prefs.edit().putBoolean("canManageStore", false).apply() }
    var capabilityStoreName: String get() = prefs.getString("capabilityStoreName", "") ?: ""; set(value) = prefs.edit().putString("capabilityStoreName", value).apply()
    var capabilityBranchId: String get() = prefs.getString("capabilityBranchId", "") ?: ""; set(value) = prefs.edit().putString("capabilityBranchId", value).apply()

    private fun legacyBinding(): ReceiverStoreBinding? {
        val url = prefs.getString("serverUrl", "").orEmpty().trim()
        if (url.isBlank()) return null
        return ReceiverStoreBinding(
            storeId = "",
            serverUrl = url,
            storeName = prefs.getString("capabilityStoreName", "").orEmpty(),
            branchId = prefs.getString("capabilityBranchId", "MAIN").orEmpty().ifBlank { "MAIN" },
            canReceiveReports = prefs.getBoolean("canReceiveReports", true),
            canMessageEmployees = prefs.getBoolean("canMessageEmployees", false),
            canManageStore = false,
            linkedAt = prefs.getLong("legacyLinkedAt", System.currentTimeMillis()),
            lastServerRefreshAt = prefs.getLong("remoteLastRefreshAt", 0L)
        )
    }

    fun storeBindings(): List<ReceiverStoreBinding> {
        val raw = prefs.getString("storeBindingsV138", "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val items = (0 until array.length()).mapNotNull { i -> runCatching {
            val o = array.getJSONObject(i)
            ReceiverStoreBinding(
                storeId = o.optString("storeId", "").trim(),
                serverUrl = o.optString("serverUrl", "").trim(),
                storeName = o.optString("storeName", "ATTEND PRO"),
                branchId = o.optString("branchId", "MAIN").ifBlank { "MAIN" },
                active = o.optBoolean("active", true),
                canReceiveReports = o.optBoolean("canReceiveReports", true),
                canMessageEmployees = o.optBoolean("canMessageEmployees", false),
                canManageStore = false,
                linkedAt = o.optLong("linkedAt", System.currentTimeMillis()),
                lastServerRefreshAt = o.optLong("lastServerRefreshAt", 0L),
                storeLastSeenAt = o.optLong("storeLastSeenAt", 0L)
            )
        }.getOrNull() }.filter {
            (it.storeId.isNotBlank() && it.serverUrl.isBlank()) || it.serverUrl.startsWith("https://", true)
        }

        if (items.isNotEmpty()) return items.sortedByDescending { it.linkedAt }
        val migrated = legacyBinding() ?: return emptyList()
        saveStoreBindings(listOf(migrated))
        return listOf(migrated)
    }

    private fun saveStoreBindings(items: List<ReceiverStoreBinding>) {
        val array = JSONArray()
        items.take(50).forEach { item -> array.put(JSONObject().apply {
            put("storeId", item.storeId)
            put("serverUrl", item.serverUrl)
            put("storeName", item.storeName)
            put("branchId", item.branchId)
            put("active", item.active)
            put("canReceiveReports", item.canReceiveReports)
            put("canMessageEmployees", item.canMessageEmployees)
            put("canManageStore", false)
            put("linkedAt", item.linkedAt)
            put("lastServerRefreshAt", item.lastServerRefreshAt)
            put("storeLastSeenAt", item.storeLastSeenAt)
        }) }
        prefs.edit().putString("storeBindingsV138", array.toString()).apply()
    }

    var activeStoreId: String
        get() = prefs.getString("activeStoreIdV138", "").orEmpty()
        set(value) = prefs.edit().putString("activeStoreIdV138", value.trim()).apply()

    fun activeBinding(): ReceiverStoreBinding? {
        val items = storeBindings()
        val selected = activeStoreId
        return items.firstOrNull { selected.isNotBlank() && it.storeId == selected }
            ?: items.firstOrNull { it.active }
            ?: items.firstOrNull()
    }

    fun selectStore(storeId: String): Boolean {
        val target = storeBindings().firstOrNull { it.storeId == storeId && it.active } ?: return false
        activeStoreId = target.storeId
        syncLegacyView(target)
        return true
    }

    fun upsertBinding(grant: ReportProtocol.RemoteReceiverGrant): ReceiverStoreBinding {
        val items = storeBindings().toMutableList()
        val storeId = grant.storeId.trim()
        val index = when {
            storeId.isNotBlank() -> items.indexOfFirst { it.storeId == storeId }
            else -> items.indexOfFirst {
                it.serverUrl.equals(grant.serverUrl, true) &&
                    it.storeName == grant.storeName &&
                    it.branchId == grant.branchId
            }
        }
        val previous = items.getOrNull(index)
        val value = ReceiverStoreBinding(
            storeId = storeId.ifBlank { previous?.storeId.orEmpty() },
            serverUrl = grant.serverUrl.trim(),
            storeName = grant.storeName.ifBlank { previous?.storeName ?: "ATTEND PRO" },
            branchId = grant.branchId.ifBlank { previous?.branchId ?: "MAIN" },
            active = true,
            canReceiveReports = previous?.canReceiveReports ?: true,
            canMessageEmployees = previous?.canMessageEmployees ?: false,
            canManageStore = false,
            linkedAt = previous?.linkedAt ?: System.currentTimeMillis(),
            lastServerRefreshAt = previous?.lastServerRefreshAt ?: 0L,
            storeLastSeenAt = previous?.storeLastSeenAt ?: 0L
        )
        if (index >= 0) items[index] = value else items.add(0, value)
        saveStoreBindings(items)
        activeStoreId = value.storeId
        syncLegacyView(value)
        return value
    }

    fun updateActiveBinding(
        storeId: String,
        storeName: String,
        branchId: String,
        canReceiveReports: Boolean,
        canMessageEmployees: Boolean,
        canManageStore: Boolean,
        lastServerRefreshAt: Long,
        storeLastSeenAt: Long = 0L
    ): ReceiverStoreBinding? {
        val items = storeBindings().toMutableList()
        val current = activeBinding() ?: return null
        var index = items.indexOfFirst {
            if (current.storeId.isNotBlank()) it.storeId == current.storeId
            else it.storeId.isBlank() && it.serverUrl == current.serverUrl
        }
        if (index < 0) return null
        val updated = current.copy(
            storeId = storeId.ifBlank { current.storeId },
            storeName = storeName.ifBlank { current.storeName },
            branchId = branchId.ifBlank { current.branchId },
            canReceiveReports = canReceiveReports,
            canMessageEmployees = canMessageEmployees,
            canManageStore = false,
            lastServerRefreshAt = lastServerRefreshAt,
            storeLastSeenAt = storeLastSeenAt
        )
        if (updated.storeId.isNotBlank()) {
            val duplicate = items.indexOfFirst { it.storeId == updated.storeId && it !== items.getOrNull(index) }
            if (duplicate >= 0 && duplicate != index) {
                items.removeAt(duplicate)
                if (duplicate < index) index--
            }
        }
        items[index] = updated
        saveStoreBindings(items)
        if (updated.storeId.isNotBlank()) activeStoreId = updated.storeId
        syncLegacyView(updated)
        return updated
    }

    fun syncBindingsFromServer(remote: List<ReceiverStoreBinding>) {
        if (remote.isEmpty()) return
        val existing = storeBindings().associateBy { it.storeId }.toMutableMap()
        remote.forEach { item ->
            val old = existing[item.storeId]
            existing[item.storeId] = item.copy(linkedAt = old?.linkedAt ?: item.linkedAt)
        }
        val merged = existing.values.filter { it.storeId.isNotBlank() }.sortedByDescending { it.linkedAt }
        saveStoreBindings(merged)
        if (activeStoreId.isBlank() || merged.none { it.storeId == activeStoreId && it.active }) {
            activeStoreId = merged.firstOrNull { it.active }?.storeId.orEmpty()
        }
        activeBinding()?.let { syncLegacyView(it) }
    }

    fun removeStoreBinding(storeId: String) {
        val remaining = storeBindings().filterNot { it.storeId == storeId }
        saveStoreBindings(remaining)
        if (activeStoreId == storeId) activeStoreId = remaining.firstOrNull { it.active }?.storeId.orEmpty()
        val active = activeBinding()
        if (active != null) syncLegacyView(active) else {
            prefs.edit()
                .putString("serverUrl", "")
                .putString("capabilityStoreName", "")
                .putString("capabilityBranchId", "")
                .putBoolean("canReceiveReports", true)
                .putBoolean("canMessageEmployees", false)
                .putBoolean("canManageStore", false)
                .apply()
        }
    }

    private fun syncLegacyView(binding: ReceiverStoreBinding) {
        prefs.edit()
            .putString("serverUrl", binding.serverUrl)
            .putString("capabilityStoreName", binding.storeName)
            .putString("capabilityBranchId", binding.branchId)
            .putBoolean("canReceiveReports", binding.canReceiveReports)
            .putBoolean("canMessageEmployees", binding.canMessageEmployees)
            .putBoolean("canManageStore", false)
            .putLong("remoteLastRefreshAt", binding.lastServerRefreshAt)
            .apply()
    }

    fun newInvite(): ReportProtocol.ReceiverInvite = ReportProtocol.ReceiverInvite(receiverId, receiverName, secret, System.currentTimeMillis() + 10 * 60_000L)

    fun receivedMessageReplies(storeId: String = ""): List<ReceiverMessageReply> {
        val raw = prefs.getString("receiverMessageRepliesV141", "[]") ?: "[]"
        val a = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val all = (0 until a.length()).mapNotNull { i -> runCatching {
            val o = a.getJSONObject(i)
            ReceiverMessageReply(
                messageId = o.optString("messageId", ""),
                storeId = o.optString("storeId", ""),
                employeeId = o.optString("employeeId", ""),
                title = o.optString("title", ""),
                body = o.optString("body", ""),
                priority = o.optString("priority", "NORMAL"),
                createdAt = o.optLong("createdAt", 0L),
                readAt = o.optLong("readAt", 0L)
            )
        }.getOrNull() }
            .filter { it.messageId.isNotBlank() && it.body.isNotBlank() }
            .sortedByDescending { it.createdAt }
        return if (storeId.isBlank()) all else all.filter { it.storeId == storeId }
    }

    fun cacheMessageReplies(storeId: String, messages: List<CentralServerClient.Message1975>): Int {
        if (storeId.isBlank() || messages.isEmpty()) return 0
        val existing = receivedMessageReplies().associateBy { it.messageId }.toMutableMap()
        var added = 0
        messages.forEach { m ->
            if (m.messageId.isBlank() || m.body.isBlank()) return@forEach
            if (!existing.containsKey(m.messageId)) added++
            existing[m.messageId] = ReceiverMessageReply(
                messageId = m.messageId,
                storeId = storeId,
                employeeId = m.employeeId.ifBlank { m.senderId },
                title = m.title,
                body = m.body,
                priority = m.priority,
                createdAt = m.createdAt,
                readAt = m.readAt
            )
        }
        val merged = existing.values.sortedByDescending { it.createdAt }.take(300)
        val a = JSONArray()
        merged.forEach { m -> a.put(JSONObject().apply {
            put("messageId", m.messageId)
            put("storeId", m.storeId)
            put("employeeId", m.employeeId)
            put("title", m.title)
            put("body", m.body)
            put("priority", m.priority)
            put("createdAt", m.createdAt)
            put("readAt", m.readAt)
        }) }
        prefs.edit().putString("receiverMessageRepliesV141", a.toString()).apply()
        return added
    }

    fun outgoingMessages(storeId: String = ""): List<ReceiverOutgoingMessage> {
        val raw = prefs.getString("receiverOutgoingMessagesV142", "[]") ?: "[]"
        val a = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val all = (0 until a.length()).mapNotNull { i -> runCatching {
            val o = a.getJSONObject(i)
            ReceiverOutgoingMessage(
                localId = o.optString("localId", ""),
                storeId = o.optString("storeId", ""),
                employeeId = o.optString("employeeId", ""),
                title = o.optString("title", ""),
                body = o.optString("body", ""),
                priority = o.optString("priority", "NORMAL"),
                createdAt = o.optLong("createdAt", 0L),
                state = o.optString("state", "PENDING"),
                attempts = o.optInt("attempts", 0),
                lastAttemptAt = o.optLong("lastAttemptAt", 0L),
                nextAttemptAt = o.optLong("nextAttemptAt", 0L),
                remoteMessageId = o.optString("remoteMessageId", ""),
                lastError = o.optString("lastError", "")
            )
        }.getOrNull() }
            .filter { it.localId.isNotBlank() && it.storeId.isNotBlank() && it.employeeId.isNotBlank() && it.body.isNotBlank() }
            .sortedByDescending { it.createdAt }
        return if (storeId.isBlank()) all else all.filter { it.storeId == storeId }
    }

    @Synchronized
    fun queueOutgoingMessage(
        storeId: String,
        employeeId: String,
        title: String,
        body: String,
        priority: String = "NORMAL"
    ): ReceiverOutgoingMessage? {
        if (storeId.isBlank() || employeeId.isBlank() || body.isBlank()) return null
        val item = ReceiverOutgoingMessage(
            localId = "RCVOUT-" + UUID.randomUUID().toString(),
            storeId = storeId,
            employeeId = employeeId,
            title = title.take(160),
            body = body.trim().take(2000),
            priority = priority.uppercase().takeIf { it in setOf("NORMAL", "IMPORTANT", "URGENT") } ?: "NORMAL",
            createdAt = System.currentTimeMillis()
        )
        saveOutgoingMessages(listOf(item) + outgoingMessages())
        return item
    }

    fun dueOutgoingMessages(now: Long = System.currentTimeMillis()): List<ReceiverOutgoingMessage> =
        outgoingMessages().filter {
            it.state != "SENT" && it.state != "CANCELLED" &&
                it.nextAttemptAt <= now && it.attempts < 12
        }.sortedBy { it.createdAt }

    @Synchronized
    fun markOutgoingAttempt(localId: String, error: String = "", remoteMessageId: String = ""): ReceiverOutgoingMessage? {
        val all = outgoingMessages().toMutableList()
        val index = all.indexOfFirst { it.localId == localId }
        if (index < 0) return null
        val old = all[index]
        val now = System.currentTimeMillis()
        val attempts = old.attempts + 1
        val sent = remoteMessageId.isNotBlank()
        val retryDelay = when {
            sent -> 0L
            attempts <= 1 -> 2_500L
            attempts == 2 -> 5_000L
            attempts == 3 -> 10_000L
            attempts <= 5 -> 20_000L
            else -> 60_000L
        }
        val updated = old.copy(
            state = if (sent) "SENT" else if (attempts >= 12) "FAILED" else "RETRY",
            attempts = attempts,
            lastAttemptAt = now,
            nextAttemptAt = if (sent) 0L else now + retryDelay,
            remoteMessageId = remoteMessageId,
            lastError = if (sent) "" else error.take(300)
        )
        all[index] = updated
        saveOutgoingMessages(all)
        return updated
    }

    @Synchronized
    fun retryOutgoingMessage(localId: String): Boolean {
        val all = outgoingMessages().toMutableList()
        val index = all.indexOfFirst { it.localId == localId }
        if (index < 0) return false
        val old = all[index]
        all[index] = old.copy(state = "PENDING", attempts = 0, nextAttemptAt = 0L, lastError = "")
        saveOutgoingMessages(all)
        return true
    }

    @Synchronized
    private fun saveOutgoingMessages(items: List<ReceiverOutgoingMessage>) {
        val a = JSONArray()
        items.sortedByDescending { it.createdAt }.take(200).forEach { m -> a.put(JSONObject().apply {
            put("localId", m.localId)
            put("storeId", m.storeId)
            put("employeeId", m.employeeId)
            put("title", m.title)
            put("body", m.body)
            put("priority", m.priority)
            put("createdAt", m.createdAt)
            put("state", m.state)
            put("attempts", m.attempts)
            put("lastAttemptAt", m.lastAttemptAt)
            put("nextAttemptAt", m.nextAttemptAt)
            put("remoteMessageId", m.remoteMessageId)
            put("lastError", m.lastError)
        }) }
        prefs.edit().putString("receiverOutgoingMessagesV142", a.toString()).commit()
    }

    fun receivedReports(storeId: String = ""): List<ReceivedReport> {
        val a = JSONArray(prefs.getString("receivedReports", "[]") ?: "[]")
        val all = (0 until a.length()).mapNotNull { i -> runCatching {
            val o = a.getJSONObject(i)
            ReceivedReport(
                o.getString("transferId"),
                o.optString("storeName", "ATTEND PRO"),
                o.optString("branchId", "MAIN"),
                o.optString("periodLabel", "تقرير"),
                o.optLong("createdAt", 0L),
                o.optLong("receivedAt", 0L),
                o.optString("reportText", ""),
                o.optString("confirmationCode", ""),
                o.optString("storeId", "")
            )
        }.getOrNull() }.sortedByDescending { it.receivedAt }
        return if (storeId.isBlank()) all else all.filter { it.storeId == storeId || it.storeId.isBlank() }
    }

    fun receive(raw: String, storeId: String = activeStoreId): ReceivedReport? {
        val pkg = ReportProtocol.decodePackage(raw, receiverId, secret) ?: return null
        val all = receivedReports()
        val duplicate = all.firstOrNull {
            it.transferId == pkg.transferId &&
                (storeId.isBlank() || it.storeId.isBlank() || it.storeId == storeId)
        }
        if (duplicate != null) return duplicate

        val item = ReceivedReport(
            pkg.transferId, pkg.storeName, pkg.branchId, pkg.periodLabel, pkg.createdAt,
            System.currentTimeMillis(), pkg.reportText, pkg.confirmationCode, storeId
        )
        val a = JSONArray()
        (listOf(item) + all).take(100).forEach { r -> a.put(JSONObject().apply {
            put("transferId", r.transferId); put("storeName", r.storeName); put("branchId", r.branchId)
            put("periodLabel", r.periodLabel); put("createdAt", r.createdAt); put("receivedAt", r.receivedAt)
            put("reportText", r.reportText); put("confirmationCode", r.confirmationCode); put("storeId", r.storeId)
        }) }
        prefs.edit().putString("receivedReports", a.toString()).apply()
        return item
    }
}
