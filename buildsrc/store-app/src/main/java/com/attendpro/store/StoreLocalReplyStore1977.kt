package com.attendpro.store

import android.content.Context
import com.attendpro.core.BleLocalMessageProtocol1977
import com.attendpro.core.CentralServerClient
import com.attendpro.core.StoreRepository
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class StoreLocalReplyStore1977(context: Context) {
    private val prefs = context.getSharedPreferences("attend_store_local_replies_1977", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<CentralServerClient.Message1975> = decode().sortedByDescending { it.createdAt }

    @Synchronized
    fun add(message: CentralServerClient.Message1975) {
        val items = decode().filterNot { it.messageId == message.messageId }.toMutableList()
        items.add(message)
        save(items.sortedByDescending { it.createdAt }.take(200))
    }

    @Synchronized
    fun markRead(messageId: String) {
        val now = System.currentTimeMillis()
        save(decode().map { if (it.messageId == messageId) it.copy(readAt = now) else it })
    }

    fun isLocal(messageId: String): Boolean = messageId.startsWith("LOCAL-REPLY-")

    private fun decode(): List<CentralServerClient.Message1975> {
        val raw = prefs.getString("messages", "[]") ?: "[]"
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                CentralServerClient.Message1975(
                    o.optString("id"), o.optString("store"), o.optString("employee"),
                    "EMPLOYEE", o.optString("employee"), "STORE", o.optString("store"),
                    o.optString("title"), o.optString("body"), o.optString("priority", "NORMAL"),
                    false, "", o.optLong("created", 0L), o.optLong("read", 0L)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save(messages: List<CentralServerClient.Message1975>) {
        val a = JSONArray()
        messages.forEach { m ->
            a.put(JSONObject().apply {
                put("id", m.messageId)
                put("store", m.storeId)
                put("employee", m.employeeId)
                put("title", m.title)
                put("body", m.body)
                put("priority", m.priority)
                put("created", m.createdAt)
                put("read", m.readAt)
            })
        }
        prefs.edit().putString("messages", a.toString()).apply()
    }
}

class StoreLocalReplyReceiver1977(context: Context) {
    data class AcceptResult(val accepted: Boolean, val completed: CentralServerClient.Message1975? = null)
    private data class Pending(val total: Int, val flags: Int, val parts: Array<ByteArray?>, var touchedAt: Long)

    private val repository = StoreRepository(context)
    private val store = StoreLocalReplyStore1977(context)
    private val pending = mutableMapOf<String, Pending>()

    @Synchronized
    fun accept(employeeId: String, frame: ByteArray, secret: ByteArray): AcceptResult {
        val fragment = BleLocalMessageProtocol1977.decodeFragment(frame, secret) ?: return AcceptResult(false)
        val now = System.currentTimeMillis()
        pending.entries.removeAll { now - it.value.touchedAt > 45_000L }
        val key = "$employeeId:${fragment.messageId}"
        val p = pending.getOrPut(key) { Pending(fragment.total, fragment.flags, arrayOfNulls(fragment.total), now) }
        if (p.total != fragment.total || p.flags != fragment.flags || fragment.index !in p.parts.indices) {
            pending.remove(key)
            return AcceptResult(false)
        }
        p.parts[fragment.index] = fragment.payload
        p.touchedAt = now
        if (p.parts.any { it == null }) return AcceptResult(true)

        val fragments = p.parts.mapIndexed { index, bytes ->
            BleLocalMessageProtocol1977.Fragment(fragment.messageId, index, p.total, p.flags, bytes!!)
        }
        pending.remove(key)
        val decoded = BleLocalMessageProtocol1977.decodeCompleted(fragments) ?: return AcceptResult(false)
        val digestBytes = MessageDigest.getInstance("SHA-256")
            .digest((employeeId + "\u0000" + decoded.title + "\u0000" + decoded.body).toByteArray(Charsets.UTF_8))
        val digest = digestBytes.take(4).joinToString("") { "%02x".format(it) }
        val employeeName = repository.employees().firstOrNull { it.employeeId.equals(employeeId, true) }?.displayName ?: employeeId
        val message = CentralServerClient.Message1975(
            "LOCAL-REPLY-${fragment.messageId.toString(16).padStart(4, '0')}-$digest",
            repository.storeId,
            employeeId,
            "EMPLOYEE",
            employeeId,
            "STORE",
            repository.storeId,
            decoded.title.ifBlank { "رد من $employeeName" },
            decoded.body,
            decoded.priority,
            false,
            "",
            now,
            0L
        )
        store.add(message)
        return AcceptResult(true, message)
    }
}
