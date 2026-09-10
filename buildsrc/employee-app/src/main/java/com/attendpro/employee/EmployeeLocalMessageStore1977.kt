package com.attendpro.employee

import android.content.Context
import com.attendpro.core.BleLocalMessageProtocol1977
import com.attendpro.core.CentralServerClient
import com.attendpro.core.EmployeeIdentityStore
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class EmployeeLocalMessageStore1977(context: Context) {
    private val prefs = context.getSharedPreferences("attend_employee_local_messages_1977", Context.MODE_PRIVATE)
    @Synchronized fun all(): List<CentralServerClient.Message1975> = decode().sortedByDescending { it.createdAt }
    @Synchronized fun add(message: CentralServerClient.Message1975) {
        val list = decode().filterNot { it.messageId == message.messageId }.toMutableList(); list.add(message)
        save(list.sortedByDescending { it.createdAt }.take(120))
    }
    @Synchronized fun markRead(messageId: String) {
        val now = System.currentTimeMillis(); save(decode().map { if (it.messageId == messageId) it.copy(readAt = now) else it })
    }
    fun isLocal(messageId: String): Boolean = messageId.startsWith("LOCAL-")
    private fun decode(): List<CentralServerClient.Message1975> {
        val raw = prefs.getString("messages", "[]") ?: "[]"
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                CentralServerClient.Message1975(o.optString("id"), o.optString("store"), o.optString("employee"), "STORE", "LOCAL_STORE", "EMPLOYEE", o.optString("employee"), o.optString("title"), o.optString("body"), o.optString("priority", "NORMAL"), o.optBoolean("voice", false), "", o.optLong("created", 0L), o.optLong("read", 0L))
            }
        }.getOrDefault(emptyList())
    }
    private fun save(messages: List<CentralServerClient.Message1975>) {
        val a = JSONArray(); messages.forEach { m ->
            a.put(JSONObject().apply {
                put("id", m.messageId); put("store", m.storeId); put("employee", m.employeeId); put("title", m.title); put("body", m.body)
                put("priority", m.priority); put("voice", m.voiceEnabled); put("created", m.createdAt); put("read", m.readAt)
            })
        }; prefs.edit().putString("messages", a.toString()).apply()
    }
}

class EmployeeLocalMessageReceiver1977(context: Context, private val identity: EmployeeIdentityStore) {
    data class AcceptResult(val accepted: Boolean, val completed: CentralServerClient.Message1975? = null)
    private data class Pending(val total: Int, val flags: Int, val parts: Array<ByteArray?>, var touchedAt: Long)
    private val store = EmployeeLocalMessageStore1977(context)
    private val pending = mutableMapOf<Int, Pending>()
    @Synchronized fun accept(frame: ByteArray, secret: ByteArray): AcceptResult {
        val fragment = BleLocalMessageProtocol1977.decodeFragment(frame, secret) ?: return AcceptResult(false)
        val now = System.currentTimeMillis(); pending.entries.removeAll { now - it.value.touchedAt > 45_000L }
        val p = pending.getOrPut(fragment.messageId) { Pending(fragment.total, fragment.flags, arrayOfNulls(fragment.total), now) }
        if (p.total != fragment.total || p.flags != fragment.flags || fragment.index !in p.parts.indices) { pending.remove(fragment.messageId); return AcceptResult(false) }
        p.parts[fragment.index] = fragment.payload; p.touchedAt = now
        if (p.parts.any { it == null }) return AcceptResult(true)
        val fragments = p.parts.mapIndexed { index, bytes -> BleLocalMessageProtocol1977.Fragment(fragment.messageId, index, p.total, p.flags, bytes!!) }
        pending.remove(fragment.messageId)
        val decoded = BleLocalMessageProtocol1977.decodeCompleted(fragments) ?: return AcceptResult(false)
        val digestBytes = MessageDigest.getInstance("SHA-256").digest((decoded.title + "\u0000" + decoded.body).toByteArray(Charsets.UTF_8))
        val digest = digestBytes.take(4).joinToString("") { "%02x".format(it) }
        val message = CentralServerClient.Message1975("LOCAL-${fragment.messageId.toString(16).padStart(4, '0')}-$digest", identity.trustedStoreId, identity.employeeId, "STORE", "LOCAL_STORE", "EMPLOYEE", identity.employeeId, decoded.title, decoded.body, decoded.priority, decoded.voiceEnabled, "", now, 0L)
        store.add(message); return AcceptResult(true, message)
    }
}
