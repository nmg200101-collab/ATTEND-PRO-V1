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

    data class ReceiverInvite(
        val receiverId: String,
        val name: String,
        val secret: String,
        val expiresAt: Long
    )

    data class ReportPackage(
        val receiverId: String,
        val transferId: String,
        val storeName: String,
        val branchId: String,
        val periodLabel: String,
        val createdAt: Long,
        val reportText: String,
        val confirmationCode: String
    )

    data class RemoteReceiverGrant(
        val receiverId: String,
        val serverUrl: String,
        val storeName: String,
        val branchId: String,
        val expiresAt: Long
    )

    fun encodeRemoteGrant(grant: RemoteReceiverGrant): String = REMOTE_GRANT_PREFIX + b64(JSONObject().apply {
        put("v", 1); put("r", grant.receiverId); put("u", grant.serverUrl); put("m", grant.storeName); put("b", grant.branchId); put("e", grant.expiresAt)
    }.toString().toByteArray(Charsets.UTF_8))

    fun decodeRemoteGrant(text: String, expectedReceiverId: String): RemoteReceiverGrant? = runCatching {
        val clean = text.trim(); if (!clean.startsWith(REMOTE_GRANT_PREFIX)) return@runCatching null
        val o = JSONObject(String(b64d(clean.removePrefix(REMOTE_GRANT_PREFIX)), Charsets.UTF_8))
        if (o.optString("r", "") != expectedReceiverId) return@runCatching null
        val grant = RemoteReceiverGrant(o.getString("r"), o.getString("u"), o.optString("m", "ATTEND PRO"), o.optString("b", "MAIN"), o.getLong("e"))
        if (grant.expiresAt < System.currentTimeMillis() || !grant.serverUrl.startsWith("https://")) null else grant
    }.getOrNull()

    fun newSecret(): String = Base64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)

    fun encodeInvite(invite: ReceiverInvite): String = INVITE_PREFIX + b64(JSONObject().apply {
        put("v", 1); put("r", invite.receiverId); put("n", invite.name); put("s", invite.secret); put("e", invite.expiresAt)
    }.toString().toByteArray(Charsets.UTF_8))

    fun decodeInvite(text: String): ReceiverInvite? = runCatching {
        val clean = text.trim()
        if (!clean.startsWith(INVITE_PREFIX)) return@runCatching null
        val o = JSONObject(String(b64d(clean.removePrefix(INVITE_PREFIX)), Charsets.UTF_8))
        ReceiverInvite(o.getString("r"), o.optString("n", "هاتف مراقبة"), o.getString("s"), o.getLong("e"))
    }.getOrNull()

    fun encodePackage(pkg: ReportPackage, secret: String): String {
        val plain = JSONObject().apply {
            put("v", 1); put("r", pkg.receiverId); put("t", pkg.transferId); put("m", pkg.storeName); put("b", pkg.branchId)
            put("p", pkg.periodLabel); put("c", pkg.createdAt); put("x", pkg.reportText); put("a", pkg.confirmationCode)
        }.toString().toByteArray(Charsets.UTF_8)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(secret), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain)
        return PACKAGE_PREFIX + b64(iv + encrypted)
    }

    fun decodePackage(text: String, expectedReceiverId: String, secret: String): ReportPackage? = runCatching {
        val clean = extractPackage(text) ?: return@runCatching null
        val all = b64d(clean.removePrefix(PACKAGE_PREFIX))
        if (all.size <= 12) return@runCatching null
        val iv = all.copyOfRange(0, 12); val encrypted = all.copyOfRange(12, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(secret), GCMParameterSpec(128, iv))
        val o = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
        if (o.getString("r") != expectedReceiverId) return@runCatching null
        ReportPackage(o.getString("r"), o.getString("t"), o.optString("m", "ATTEND PRO"), o.optString("b", "MAIN"),
            o.optString("p", "تقرير"), o.optLong("c", System.currentTimeMillis()), o.optString("x", ""), o.optString("a", ""))
    }.getOrNull()

    private fun extractPackage(text: String): String? {
        val idx = text.indexOf(PACKAGE_PREFIX)
        if (idx < 0) return null
        return text.substring(idx).lineSequence().firstOrNull()?.trim()
    }

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
    val confirmationCode: String
)

class ReportReceiverStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("report_receiver_store", Context.MODE_PRIVATE)
    private val secureVault = SecureTokenVault(appContext)
    val receiverId: String
        get() {
            val old = prefs.getString("receiverId", "") ?: ""
            if (old.isNotBlank()) return old
            val id = "RCV-" + UUID.randomUUID().toString().substring(0, 12).uppercase()
            prefs.edit().putString("receiverId", id).apply(); return id
        }
    val secret: String
        get() {
            secureVault.get("report_receiver_secret").takeIf { it.isNotBlank() }?.let { return it }
            val legacy = prefs.getString("secret", "").orEmpty()
            val value = legacy.ifBlank { ReportProtocol.newSecret() }
            secureVault.put("report_receiver_secret", value)
            if (legacy.isNotBlank()) prefs.edit().remove("secret").apply()
            return value
        }
    var receiverName: String get() = prefs.getString("receiverName", "هاتف صاحب المحل") ?: "هاتف صاحب المحل"; set(value) = prefs.edit().putString("receiverName", value.trim().ifBlank { "هاتف صاحب المحل" }).apply()
    var serverUrl: String get() = prefs.getString("serverUrl", "") ?: ""; set(value) = prefs.edit().putString("serverUrl", value.trim()).apply()
    var remoteDashboardText: String get() = prefs.getString("remoteDashboardText", "") ?: ""; set(value) = prefs.edit().putString("remoteDashboardText", value).apply()
    var remoteLastRefreshAt: Long get() = prefs.getLong("remoteLastRefreshAt", 0L); set(value) = prefs.edit().putLong("remoteLastRefreshAt", value).apply()

    fun newInvite(): ReportProtocol.ReceiverInvite = ReportProtocol.ReceiverInvite(receiverId, receiverName, secret, System.currentTimeMillis() + 10 * 60_000L)

    fun receivedReports(): List<ReceivedReport> {
        val a = JSONArray(prefs.getString("receivedReports", "[]") ?: "[]")
        return (0 until a.length()).mapNotNull { i -> runCatching {
            val o = a.getJSONObject(i)
            ReceivedReport(o.getString("transferId"), o.optString("storeName", "ATTEND PRO"), o.optString("branchId", "MAIN"), o.optString("periodLabel", "تقرير"),
                o.optLong("createdAt", 0L), o.optLong("receivedAt", 0L), o.optString("reportText", ""), o.optString("confirmationCode", ""))
        }.getOrNull() }.sortedByDescending { it.receivedAt }
    }

    fun receive(raw: String): ReceivedReport? {
        val pkg = ReportProtocol.decodePackage(raw, receiverId, secret) ?: return null
        val item = ReceivedReport(pkg.transferId, pkg.storeName, pkg.branchId, pkg.periodLabel, pkg.createdAt, System.currentTimeMillis(), pkg.reportText, pkg.confirmationCode)
        val existing = receivedReports().filterNot { it.transferId == item.transferId }
        val a = JSONArray()
        (listOf(item) + existing).take(100).forEach { r -> a.put(JSONObject().apply {
            put("transferId", r.transferId); put("storeName", r.storeName); put("branchId", r.branchId); put("periodLabel", r.periodLabel)
            put("createdAt", r.createdAt); put("receivedAt", r.receivedAt); put("reportText", r.reportText); put("confirmationCode", r.confirmationCode)
        }) }
        prefs.edit().putString("receivedReports", a.toString()).apply()
        return item
    }
}
