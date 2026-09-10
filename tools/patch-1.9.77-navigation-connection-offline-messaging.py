from pathlib import Path


def must_replace(text: str, old: str, new: str, label: str, count: int = 1) -> str:
    if text.count(old) < count:
        raise SystemExit(f"missing anchor: {label}")
    return text.replace(old, new, count)

ROOT = Path("buildsrc")

def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")

def write(rel: str, text: str) -> None:
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")

protocol = r'''package com.attendpro.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * ATTEND-PRO 1.9.77 local Store -> Employee message protocol.
 * It rides only on an already authenticated BleDirectProtocol session.
 * Every frame stays <= 20 bytes and is independently HMAC-authenticated.
 */
object BleLocalMessageProtocol1977 {
    private const val MAGIC: Byte = 0x71
    private const val HEADER_BYTES = 7
    private const val MAC_BYTES = 6
    const val MAX_FRAME_BYTES = 20
    private const val MAX_CHUNK_BYTES = MAX_FRAME_BYTES - HEADER_BYTES - MAC_BYTES
    private const val MAX_PARTS = 180
    private const val FLAG_VOICE = 0x01
    private const val FLAG_PRIORITY_SHIFT = 1
    private const val FLAG_COMPRESSED = 0x08

    data class Fragment(val messageId: Int, val index: Int, val total: Int, val flags: Int, val payload: ByteArray)
    data class EncodedMessage(val messageId: String, val numericMessageId: Int, val frames: List<ByteArray>)
    data class DecodedMessage(val title: String, val body: String, val priority: String, val voiceEnabled: Boolean)

    fun encodeMessage(secret: ByteArray, title: String, body: String, priority: String, voiceEnabled: Boolean): EncodedMessage {
        require(secret.isNotEmpty()) { "Pairing secret required" }
        val safeTitle = title.trim().ifBlank { "رسالة من إدارة المحل" }.take(60)
        val safeBody = body.trim().take(500)
        require(safeBody.isNotBlank()) { "Message body required" }
        val titleBytes = safeTitle.toByteArray(Charsets.UTF_8)
        val bodyBytes = safeBody.toByteArray(Charsets.UTF_8)
        require(titleBytes.size <= 180) { "Title too long" }
        val raw = ByteBuffer.allocate(1 + titleBytes.size + bodyBytes.size).apply {
            put(titleBytes.size.toByte()); put(titleBytes); put(bodyBytes)
        }.array()
        val compressed = deflate(raw)
        val useCompressed = compressed.size + 2 < raw.size
        val transfer = if (useCompressed) compressed else raw
        val total = ((transfer.size + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES).coerceAtLeast(1)
        require(total <= MAX_PARTS) { "Message too large for local BLE channel" }
        val numericId = SecureRandom().nextInt(0x10000)
        var flags = if (voiceEnabled) FLAG_VOICE else 0
        flags = flags or ((priorityCode(priority) and 0x03) shl FLAG_PRIORITY_SHIFT)
        if (useCompressed) flags = flags or FLAG_COMPRESSED
        val frames = (0 until total).map { index ->
            val from = index * MAX_CHUNK_BYTES
            val to = minOf(transfer.size, from + MAX_CHUNK_BYTES)
            encodeFragment(secret, numericId, index, total, flags, transfer.copyOfRange(from, to))
        }
        return EncodedMessage("L${numericId.toString(16).padStart(4, '0')}", numericId, frames)
    }

    fun decodeFragment(frame: ByteArray, secret: ByteArray): Fragment? {
        if (secret.isEmpty() || frame.size !in (HEADER_BYTES + MAC_BYTES)..MAX_FRAME_BYTES) return null
        if (frame[0] != MAGIC) return null
        val dataLen = frame[6].toInt() and 0xff
        if (dataLen > MAX_CHUNK_BYTES || frame.size != HEADER_BYTES + dataLen + MAC_BYTES) return null
        val unsigned = frame.copyOfRange(0, HEADER_BYTES + dataLen)
        val expected = hmac(secret, unsigned).copyOf(MAC_BYTES)
        val actual = frame.copyOfRange(HEADER_BYTES + dataLen, frame.size)
        if (!MessageDigest.isEqual(expected, actual)) return null
        val id = ((frame[1].toInt() and 0xff) shl 8) or (frame[2].toInt() and 0xff)
        val index = frame[3].toInt() and 0xff
        val total = frame[4].toInt() and 0xff
        val flags = frame[5].toInt() and 0xff
        if (total !in 1..MAX_PARTS || index !in 0 until total) return null
        return Fragment(id, index, total, flags, frame.copyOfRange(HEADER_BYTES, HEADER_BYTES + dataLen))
    }

    fun decodeCompleted(fragments: List<Fragment>): DecodedMessage? {
        if (fragments.isEmpty()) return null
        val first = fragments.first()
        if (fragments.size != first.total || fragments.any { it.messageId != first.messageId || it.total != first.total || it.flags != first.flags }) return null
        val ordered = fragments.sortedBy { it.index }
        if (ordered.map { it.index } != (0 until first.total).toList()) return null
        val transfer = ordered.fold(ByteArray(0)) { acc, f -> acc + f.payload }
        val raw = if (first.flags and FLAG_COMPRESSED != 0) inflate(transfer) ?: return null else transfer
        if (raw.isEmpty()) return null
        val titleLen = raw[0].toInt() and 0xff
        if (titleLen <= 0 || 1 + titleLen > raw.size) return null
        val title = raw.copyOfRange(1, 1 + titleLen).toString(Charsets.UTF_8).trim()
        val body = raw.copyOfRange(1 + titleLen, raw.size).toString(Charsets.UTF_8).trim()
        if (body.isBlank()) return null
        val priority = when ((first.flags shr FLAG_PRIORITY_SHIFT) and 0x03) { 2 -> "URGENT"; 1 -> "IMPORTANT"; else -> "NORMAL" }
        return DecodedMessage(title.ifBlank { "رسالة من إدارة المحل" }, body, priority, first.flags and FLAG_VOICE != 0)
    }

    private fun encodeFragment(secret: ByteArray, id: Int, index: Int, total: Int, flags: Int, data: ByteArray): ByteArray {
        require(data.size <= MAX_CHUNK_BYTES)
        val unsigned = ByteBuffer.allocate(HEADER_BYTES + data.size).order(ByteOrder.BIG_ENDIAN).apply {
            put(MAGIC); putShort((id and 0xffff).toShort()); put(index.toByte()); put(total.toByte()); put(flags.toByte()); put(data.size.toByte()); put(data)
        }.array()
        return unsigned + hmac(secret, unsigned).copyOf(MAC_BYTES)
    }

    private fun priorityCode(value: String): Int = when (value.uppercase()) { "URGENT" -> 2; "IMPORTANT" -> 1; else -> 0 }
    private fun deflate(data: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_SPEED); d.setInput(data); d.finish()
        val out = ByteArray(data.size + 64); val count = d.deflate(out); d.end(); return out.copyOf(count)
    }
    private fun inflate(data: ByteArray): ByteArray? = runCatching {
        val i = Inflater(); i.setInput(data); val out = ByteArray(4096); val count = i.inflate(out)
        if (!i.finished()) throw IllegalArgumentException("Compressed message exceeds limit")
        i.end(); out.copyOf(count)
    }.getOrNull()
    private fun hmac(secret: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec(secret, "HmacSHA256"))
    }.doFinal(data)
}
'''
write("core/src/main/java/com/attendpro/core/BleLocalMessageProtocol1977.kt", protocol)

protocol_test = r'''package com.attendpro.core

import org.junit.Assert.*
import org.junit.Test

class BleLocalMessageProtocol1977Test {
    @Test fun roundTripArabicMessageAndFrameLimit() {
        val secret = ByteArray(32) { (it * 7 + 3).toByte() }
        val body = "رسالة مباشرة بدون إنترنت إلى الموظف. ".repeat(12)
        val encoded = BleLocalMessageProtocol1977.encodeMessage(secret, "تنبيه الدوام", body, "URGENT", true)
        assertTrue(encoded.frames.isNotEmpty())
        assertTrue(encoded.frames.all { it.size <= BleLocalMessageProtocol1977.MAX_FRAME_BYTES })
        val fragments = encoded.frames.map { BleLocalMessageProtocol1977.decodeFragment(it, secret)!! }
        val decoded = BleLocalMessageProtocol1977.decodeCompleted(fragments)!!
        assertEquals("تنبيه الدوام", decoded.title)
        assertEquals(body.trim().take(500), decoded.body)
        assertEquals("URGENT", decoded.priority)
        assertTrue(decoded.voiceEnabled)
    }
    @Test fun rejectsTamperedFragment() {
        val secret = ByteArray(32) { it.toByte() }
        val frame = BleLocalMessageProtocol1977.encodeMessage(secret, "A", "hello", "NORMAL", false).frames.first().clone()
        frame[7] = (frame[7].toInt() xor 1).toByte()
        assertNull(BleLocalMessageProtocol1977.decodeFragment(frame, secret))
    }
}
'''
write("core/src/test/java/com/attendpro/core/BleLocalMessageProtocol1977Test.kt", protocol_test)

bridge = r'''package com.attendpro.store

object StoreDirectLinkBridge1977 {
    @Volatile private var client: BleDirectLinkClient? = null
    fun bind(value: BleDirectLinkClient) { client = value }
    fun unbind(value: BleDirectLinkClient) { if (client === value) client = null }
    fun isConnected(employeeId: String): Boolean = client?.isConnected(employeeId) == true
    fun diagnosticState(employeeId: String): String = client?.diagnosticState(employeeId) ?: "غير متصل مباشر"
    fun sendMessage(employeeId: String, title: String, body: String, priority: String, voiceEnabled: Boolean, callback: (Boolean, String) -> Unit): Boolean {
        val c = client ?: return false
        return c.sendLocalMessage(employeeId, title, body, priority, voiceEnabled, callback) != null
    }
}
'''
write("store-app/src/main/java/com/attendpro/store/StoreDirectLinkBridge1977.kt", bridge)

local_store = r'''package com.attendpro.employee

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
'''
write("employee-app/src/main/java/com/attendpro/employee/EmployeeLocalMessageStore1977.kt", local_store)

rel = "store-app/src/main/java/com/attendpro/store/BleDirectLinkClient.kt"
s = read(rel)
s = s.replace("import com.attendpro.core.BleDirectProtocol\n", "import com.attendpro.core.BleDirectProtocol\nimport com.attendpro.core.BleLocalMessageProtocol1977\n", 1)
s = must_replace(s, "    private enum class Kind { HEARTBEAT, ACK_CONFIRM, CONFIG, CHALLENGE }\n    private data class Operation(val bytes: ByteArray, val kind: Kind, val pingNonce: Int? = null)\n", "    private enum class Kind { HEARTBEAT, ACK_CONFIRM, CONFIG, CHALLENGE, LOCAL_MESSAGE }\n    private data class Operation(\n        val bytes: ByteArray, val kind: Kind, val pingNonce: Int? = null,\n        val localMessageId: String = \"\", val localFinal: Boolean = false,\n        val localCallback: ((Boolean, String) -> Unit)? = null\n    )\n", "client operation")
anchor = '''    fun sendChallenge(
        employeeId: String,
        method: AttendanceMethod,
        expiresAt: Long = System.currentTimeMillis() + 60_000L,
        requestToken: Int = SecureRandom().nextInt(),
        action: AttendanceAction = AttendanceAction.CHECK_IN
    ): Boolean {
        val s = sessions[employeeId] ?: return false
        if (!isConnected(employeeId)) return false
        enqueue(s, Operation(BleDirectProtocol.encodeChallenge(s.secret, method, expiresAt, requestToken, action), Kind.CHALLENGE))
        return true
    }
'''
addition = anchor + '''

    fun sendLocalMessage(employeeId: String, title: String, body: String, priority: String, voiceEnabled: Boolean, callback: (Boolean, String) -> Unit): String? {
        val s = sessions[employeeId] ?: return null
        if (!isConnected(employeeId)) return null
        val encoded = runCatching { BleLocalMessageProtocol1977.encodeMessage(s.secret, title, body, priority, voiceEnabled) }.getOrNull() ?: return null
        encoded.frames.forEachIndexed { index, frame ->
            enqueue(s, Operation(frame, Kind.LOCAL_MESSAGE, localMessageId = encoded.messageId, localFinal = index == encoded.frames.lastIndex, localCallback = if (index == encoded.frames.lastIndex) callback else null))
        }
        return encoded.messageId
    }
'''
s = must_replace(s, anchor, addition, "client sendLocalMessage")
old_fail = '''            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (op?.kind == Kind.HEARTBEAT) failAndReconnect(session, "فشل heartbeat عبر Bluetooth")
                else {
                    synchronized(session) { session.queue.clear() }
                    onState(session.employeeId, isConnected(session.employeeId), "تعذر إرسال الطلب عبر Bluetooth المباشر")
                }
                return
            }
'''
new_fail = '''            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (op?.kind == Kind.HEARTBEAT) failAndReconnect(session, "فشل heartbeat عبر Bluetooth")
                else {
                    if (op?.kind == Kind.LOCAL_MESSAGE) handler.post { op.localCallback?.invoke(false, "فشل إرسال الرسالة المحلية عبر Bluetooth") }
                    synchronized(session) {
                        session.queue.filter { it.kind == Kind.LOCAL_MESSAGE && it.localFinal }.forEach { pending -> handler.post { pending.localCallback?.invoke(false, "انقطعت الرسالة المحلية قبل اكتمالها") } }
                        session.queue.clear()
                    }
                    onState(session.employeeId, isConnected(session.employeeId), "تعذر إرسال الطلب عبر Bluetooth المباشر")
                }
                return
            }
'''
s = must_replace(s, old_fail, new_fail, "client write failure")
s = must_replace(s, "                Kind.ACK_CONFIRM, Kind.CONFIG, Kind.CHALLENGE, null -> drain(session)\n", "                Kind.LOCAL_MESSAGE -> {\n                    if (op.localFinal) handler.post { op.localCallback?.invoke(true, \"تم تسليم الرسالة مباشرة عبر Bluetooth بدون إنترنت\") }\n                    drain(session)\n                }\n                Kind.ACK_CONFIRM, Kind.CONFIG, Kind.CHALLENGE, null -> drain(session)\n", "client message success")
write(rel, s)

rel = "employee-app/src/main/java/com/attendpro/employee/BleDirectLinkServer.kt"
s = read(rel)
s = must_replace(s, "    private val authenticatedDevices = mutableMapOf<String, AuthState>()\n", "    private val authenticatedDevices = mutableMapOf<String, AuthState>()\n    private val localMessageReceiver = EmployeeLocalMessageReceiver1977(context, identity)\n", "employee local receiver")
old_branch = '''                    } else {
                        val challenge = if (auth.confirmed) BleDirectProtocol.decodeChallenge(bytes, secret) else null
                        if (challenge != null) {
                            ok = true
                            identity.lastBleDirectState = "استقبل طلب إثبات عبر Bluetooth مباشر ضمن جلسة موثقة"
                            onChallenge("local:${challenge.challengeId}", challenge.method, challenge.expiresAt, challenge.action)
                        }
                    }
'''
new_branch = '''                    } else {
                        val challenge = if (auth.confirmed) BleDirectProtocol.decodeChallenge(bytes, secret) else null
                        if (challenge != null) {
                            ok = true
                            identity.lastBleDirectState = "استقبل طلب إثبات عبر Bluetooth مباشر ضمن جلسة موثقة"
                            onChallenge("local:${challenge.challengeId}", challenge.method, challenge.expiresAt, challenge.action)
                        } else if (auth.confirmed) {
                            val localMessage = localMessageReceiver.accept(bytes, secret)
                            if (localMessage.accepted) {
                                ok = true
                                auth.lastProtocolAt = System.currentTimeMillis()
                                localMessage.completed?.let { message ->
                                    identity.lastBleDirectSeenAt = auth.lastProtocolAt
                                    identity.lastBleDirectState = "متصل بالمحل • استلم رسالة محلية موثقة"
                                    if (identity.employeeMessageNotificationsEnabled) EmployeeMessageNotifier1975.notify(context, message)
                                    onStatus("✓ استلمت رسالة من إدارة المحل مباشرة بدون إنترنت")
                                }
                            }
                        }
                    }
'''
s = must_replace(s, old_branch, new_branch, "employee message decode")
write(rel, s)

rel = "store-app/src/main/java/com/attendpro/store/MainActivity.kt"
s = read(rel)
s = must_replace(s, '''        directBle = BleDirectLinkClient(this) { employeeId, connected, message ->
            runOnUiThread { markDirectBleState(employeeId, connected, message) }
        }
''', '''        directBle = BleDirectLinkClient(this) { employeeId, connected, message ->
            runOnUiThread { markDirectBleState(employeeId, connected, message) }
        }
        StoreDirectLinkBridge1977.bind(directBle)
''', "bind direct bridge")
s = must_replace(s, "    override fun onDestroy(){nearbyRefreshHandler.removeCallbacks(nearbyRefreshTask);scanner.stop();networkListener.stop();directBle.stop();voiceAnnouncer.shutdown();super.onDestroy()}\n", "    override fun onDestroy(){nearbyRefreshHandler.removeCallbacks(nearbyRefreshTask);scanner.stop();networkListener.stop();StoreDirectLinkBridge1977.unbind(directBle);directBle.stop();voiceAnnouncer.shutdown();super.onDestroy()}\n", "unbind direct bridge")
menu_anchor = "    private fun showStoreMainMenu1976() {\n"
layer_helper = r'''    private fun showLayeredMenu1977(title: String, items: List<Pair<String, () -> Unit>>) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 8))
        }
        items.forEach { (label, action) -> box.addView(UiKit.button(this, p, label, false).apply { setOnClickListener { action() } }) }
        AlertDialog.Builder(this).setTitle(title).setView(box).setNegativeButton("رجوع", null).show()
    }

'''
s = s.replace(menu_anchor, layer_helper + menu_anchor, 1)
old_store_menu = '''    private fun showStoreMainMenu1976() {
        val items = arrayOf("الإشعارات", "دليل مستخدم إدارة المحل", "الإعدادات")
        AlertDialog.Builder(this).setTitle("القائمة").setItems(items) { _, which ->
            when (which) {
                0 -> startActivity(Intent(this, StoreMessages1975Activity::class.java))
                1 -> showStoreUserGuide1976()
                2 -> startActivity(Intent(this, StoreSettingsActivity::class.java))
            }
        }.show()
    }
'''
new_store_menu = '''    private fun showStoreMainMenu1976() {
        showLayeredMenu1977("القائمة", listOf(
            "الإشعارات" to { startActivity(Intent(this, StoreMessages1975Activity::class.java)) },
            "دليل مستخدم إدارة المحل" to { showStoreUserGuide1976() },
            "الإعدادات" to { startActivity(Intent(this, StoreSettingsActivity::class.java)) }
        ))
    }
'''
s = must_replace(s, old_store_menu, new_store_menu, "store main layered menu")
write(rel, s)

rel = "store-app/src/main/java/com/attendpro/store/StoreMessages1975Activity.kt"
s = read(rel)
old_compose = '''        val mode = repo.employeeMessageVoiceMode(employeeId)
        val defaultVoice = when (mode) { "VOICE_NOTIFICATION" -> true; "NOTIFICATION_ONLY", "SILENT" -> false; else -> repo.employeeMessageVoiceDefaultEnabled }
        val voice = CheckBox(this).apply { text = "قراءة الرسالة بصوت على هاتف الموظف"; isChecked = defaultVoice; gravity = Gravity.RIGHT; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        box.addView(title); box.addView(message); box.addView(priority); box.addView(voice)
        if (mode == "SILENT") box.addView(UiKit.subtitle(this, p, "تنبيه: إعداد هذا الموظف مضبوط على صامت. يمكنك إرسال الرسالة، لكن لن يتم تشغيل النطق تلقائيًا."))
'''
new_compose = '''        val mode = repo.employeeMessageVoiceMode(employeeId)
        val defaultVoice = when (mode) { "VOICE_NOTIFICATION" -> true; "NOTIFICATION_ONLY", "SILENT" -> false; else -> repo.employeeMessageVoiceDefaultEnabled }
        val voice = CheckBox(this).apply { text = "قراءة الرسالة بصوت على هاتف الموظف"; isChecked = defaultVoice; gravity = Gravity.RIGHT; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        box.addView(title); box.addView(message); box.addView(priority); box.addView(voice)
        val directReady = StoreDirectLinkBridge1977.isConnected(employeeId)
        box.addView(UiKit.subtitle(this, p, if (directReady) "● الهاتف متصل مباشرة الآن — ستُرسل الرسالة أولًا عبر Bluetooth الموثق بدون إنترنت." else "○ لا توجد قناة Bluetooth موثقة الآن — سيستخدم التطبيق الخادم عند توفره."))
        if (mode == "SILENT") box.addView(UiKit.subtitle(this, p, "تنبيه: إعداد هذا الموظف مضبوط على صامت. يمكنك إرسال الرسالة، لكن لن يتم تشغيل النطق تلقائيًا."))
'''
s = must_replace(s, old_compose, new_compose, "message direct status")
old_send = '''                d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                Thread {
                    val r = CentralServerClient.sendStoreMessageToEmployee(repo.serverUrl, repo.centralAccessToken, repo.storeId, identity, employeeId, title.text.toString(), body, pr, voice.isChecked && mode != "SILENT")
                    runOnUiThread {
                        r.onSuccess { toast("تم إرسال الرسالة إلى $name"); d.dismiss(); showInbox() }
                            .onFailure { toast("تعذر الإرسال: ${it.message}"); d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true }
                    }
                }.start()
'''
new_send = '''                d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                val voiceEnabled = voice.isChecked && mode != "SILENT"
                fun sendServerFallback(reason: String) {
                    if (!repo.hasCentralCredentials()) { toast("$reason — ولا يوجد اتصال خادم مهيأ"); d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true; return }
                    Thread {
                        val r = CentralServerClient.sendStoreMessageToEmployee(repo.serverUrl, repo.centralAccessToken, repo.storeId, identity, employeeId, title.text.toString(), body, pr, voiceEnabled)
                        runOnUiThread { r.onSuccess { toast("تم إرسال الرسالة عبر الخادم إلى $name"); d.dismiss(); showInbox() }.onFailure { toast("تعذر الإرسال: ${it.message}"); d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true } }
                    }.start()
                }
                val localStarted = StoreDirectLinkBridge1977.sendMessage(employeeId, title.text.toString(), body, pr, voiceEnabled) { success, localStatus ->
                    runOnUiThread { if (success) { toast("✓ $localStatus — $name"); d.dismiss(); showInbox() } else sendServerFallback(localStatus) }
                }
                if (!localStarted) sendServerFallback("لا توجد قناة اتصال محلية موثقة مع هاتف $name")
'''
s = must_replace(s, old_send, new_send, "local first send")
write(rel, s)

rel = "employee-app/src/main/java/com/attendpro/employee/EmployeeMessages1975Activity.kt"
s = read(rel)
s = s.replace("    private val p by lazy { UiKit.palette(this) }\n", "    private val p by lazy { UiKit.palette(this) }\n    private lateinit var localStore: EmployeeLocalMessageStore1977\n", 1)
s = s.replace("        identity = EmployeeIdentityStore(this)\n        load()\n", "        identity = EmployeeIdentityStore(this)\n        localStore = EmployeeLocalMessageStore1977(this)\n        load()\n", 1)
start = s.index("    private fun load() {")
end = s.index("\n    private fun markRead", start)
new_load = r'''    private fun load() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(UiKit.dp(this@EmployeeMessages1975Activity,16), UiKit.dp(this@EmployeeMessages1975Activity,18), UiKit.dp(this@EmployeeMessages1975Activity,16), UiKit.dp(this@EmployeeMessages1975Activity,30)); setBackgroundColor(p.bg)
        }
        val hero = UiKit.heroCard(this, p)
        hero.addView(UiKit.title(this,p,"الرسائل والإشعارات",24f).apply { gravity=Gravity.CENTER; setTextColor(android.graphics.Color.WHITE) })
        hero.addView(UiKit.subtitle(this,p,"رسائل الخادم والرسائل المباشرة من جهاز المحل").apply { gravity=Gravity.CENTER; setTextColor(android.graphics.Color.argb(225,255,255,255)) })
        root.addView(hero)
        val loading = UiKit.card(this,p).apply { addView(UiKit.subtitle(this@EmployeeMessages1975Activity,p,"يجري تحميل الرسائل…")) }
        root.addView(loading); setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
        val localMessages = localStore.all()
        Thread {
            val remoteResult = CentralServerClient.employeeMessages(identity.serverUrl, identity.trustedStoreId, identity.employeeId, identity.pairingSecret, identity.installationId, true, 100)
            val remote = remoteResult.getOrDefault(emptyList())
            val messages = (localMessages + remote).distinctBy { it.messageId }.sortedByDescending { it.createdAt }
            runOnUiThread {
                root.removeView(loading)
                if (remoteResult.isFailure) root.addView(UiKit.card(this,p).apply { addView(UiKit.subtitle(this@EmployeeMessages1975Activity,p,"لا يوجد اتصال بالخادم الآن؛ الرسائل المباشرة المستلمة من جهاز المحل تبقى متاحة بدون إنترنت.")) })
                if (messages.isEmpty()) root.addView(UiKit.card(this,p).apply { addView(UiKit.subtitle(this@EmployeeMessages1975Activity,p,"لا توجد رسائل حاليًا.")) })
                messages.forEach { m ->
                    val local = localStore.isLocal(m.messageId); val card = UiKit.card(this,p)
                    val sender = if (m.senderType == "SYSTEM_OWNER") "إدارة نظام ATTEND PRO" else "إدارة المحل"
                    card.addView(UiKit.sectionLabel(this,p,(if (m.readAt <= 0L) "● " else "") + sender + if (local) " • مباشر بدون إنترنت" else ""))
                    card.addView(UiKit.title(this,p,m.title.ifBlank { "رسالة" },18f)); card.addView(UiKit.subtitle(this,p,"${m.body}\n${time(m.createdAt)} • ${priorityArabic(m.priority)}"))
                    if (m.readAt <= 0L) card.addView(UiKit.button(this,p,"تعليم كمقروء",false).apply { setOnClickListener { markRead(m.messageId) } })
                    if (!local) card.addView(UiKit.button(this,p,"رد",false).apply { setOnClickListener { reply(m) } }) else card.addView(UiKit.subtitle(this,p,"الرد على الرسائل المباشرة يُرسل عبر الخادم عند توفر الإنترنت."))
                    root.addView(card)
                }
                root.addView(UiKit.card(this,p).apply {
                    addView(UiKit.button(this@EmployeeMessages1975Activity,p,"تحديث",false).apply { setOnClickListener { load() } })
                    addView(UiKit.button(this@EmployeeMessages1975Activity,p,"رجوع",false).apply { setOnClickListener { finish() } })
                })
            }
        }.start()
    }
'''
s = s[:start] + new_load + s[end:]
old_mark = '''    private fun markRead(id: String) {
        Thread {
            CentralServerClient.markEmployeeMessageRead(identity.serverUrl, identity.trustedStoreId, identity.employeeId, identity.pairingSecret, identity.installationId, id)
            runOnUiThread { load() }
        }.start()
    }
'''
new_mark = '''    private fun markRead(id: String) {
        if (localStore.isLocal(id)) { localStore.markRead(id); load(); return }
        Thread { CentralServerClient.markEmployeeMessageRead(identity.serverUrl, identity.trustedStoreId, identity.employeeId, identity.pairingSecret, identity.installationId, id); runOnUiThread { load() } }.start()
    }
'''
s = must_replace(s, old_mark, new_mark, "local mark read")
write(rel, s)

rel = "employee-app/src/main/java/com/attendpro/employee/MainActivity.kt"
s = read(rel)
menu_anchor = "    private fun showEmployeeMainMenu1976() {\n"
helper = r'''    private fun showLayeredMenu1977(title: String, items: List<Pair<String, () -> Unit>>) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 8))
        }
        items.forEach { (label, action) -> box.addView(UiKit.button(this, p, label, false).apply { setOnClickListener { action() } }) }
        AlertDialog.Builder(this).setTitle(title).setView(box).setNegativeButton("رجوع", null).show()
    }

'''
s = s.replace(menu_anchor, helper + menu_anchor, 1)
old_menu = '''    private fun showEmployeeMainMenu1976() {
        val items = arrayOf("الإشعارات", "دليل مستخدم الموظف", "الإعدادات")
        AlertDialog.Builder(this).setTitle("القائمة").setItems(items) { _, which ->
            when (which) {
                0 -> startActivity(Intent(this, EmployeeMessages1975Activity::class.java))
                1 -> showEmployeeUserGuide1976()
                2 -> showEmployeeSettings1976()
            }
        }.show()
    }
'''
new_menu = '''    private fun showEmployeeMainMenu1976() {
        showLayeredMenu1977("القائمة", listOf(
            "الإشعارات" to { startActivity(Intent(this, EmployeeMessages1975Activity::class.java)) },
            "دليل مستخدم الموظف" to { showEmployeeUserGuide1976() },
            "الإعدادات" to { showEmployeeSettings1976() }
        ))
    }
'''
s = must_replace(s, old_menu, new_menu, "employee layered main menu")
old_settings = '''    private fun showEmployeeSettings1976() {
        val items = arrayOf("المظهر وطريقة العرض", "كلمة مرور التطبيق", "فحص تحديث التطبيق", "إلغاء ربط الهاتف")
        AlertDialog.Builder(this).setTitle("الإعدادات").setItems(items) { _, which ->
            when (which) {
                0 -> UiKit.showAppearancePicker(this)
                1 -> setupLocalCredentials()
                2 -> AppUpdateManager.check(this, AppUpdateManager.DEFAULT_SERVER, "employee", manual = true)
                3 -> confirmUnlink()
            }
        }.setNegativeButton("إغلاق", null).show()
    }
'''
new_settings = '''    private fun showEmployeeSettings1976() {
        showLayeredMenu1977("الإعدادات", listOf(
            "إدارة الاتصال" to { showEmployeeConnectionControl1977() },
            "المظهر وطريقة العرض" to { UiKit.showAppearancePicker(this) },
            "كلمة مرور التطبيق" to { setupLocalCredentials() },
            "فحص تحديث التطبيق" to { AppUpdateManager.check(this, AppUpdateManager.DEFAULT_SERVER, "employee", manual = true) },
            "إلغاء ربط الهاتف" to { confirmUnlink() }
        ))
    }

    private fun showEmployeeConnectionControl1977() {
        val connected = identity.lastBleDirectSeenAt > 0L && System.currentTimeMillis() - identity.lastBleDirectSeenAt < 12_000L
        showLayeredMenu1977("إدارة الاتصال", listOf(
            (if (connected) "● Bluetooth المباشر متصل — عرض التفاصيل" else "○ عرض حالة الاتصال") to { showConnectionStatus() },
            "إعادة تشغيل الاتصال الآن" to { restartEmployeeConnection1977() },
            (if (identity.autoPresence) "إيقاف الظهور التلقائي مؤقتًا" else "تشغيل الظهور التلقائي") to {
                identity.autoPresence = !identity.autoPresence
                presenceSwitch.isChecked = identity.autoPresence
                if (identity.autoPresence) restartEmployeeConnection1977() else { stopBackgroundPresence(); status.text = "تم إيقاف الظهور التلقائي مؤقتًا" }
                updateConnectionSummary()
            },
            "فحص الجاهزية" to { showReadinessCheck() },
            "إعادة فتح مركز الربط" to { showPairingCenter() }
        ))
    }

    private fun restartEmployeeConnection1977() {
        if (!identity.isConfigured) { status.text = "اربط الهاتف بالمحل أولًا"; showPairingCenter(); return }
        identity.autoPresence = true
        if (::presenceSwitch.isInitialized) presenceSwitch.isChecked = true
        stopBackgroundPresence(); status.text = "جاري إعادة تشغيل قنوات الاتصال…"
        Handler(Looper.getMainLooper()).postDelayed({ startPresence(); updateConnectionSummary(); status.text = "تمت إعادة تشغيل Bluetooth وWi‑Fi/Hotspot؛ سيظهر ACK عند تأكيد الاتصال" }, 500L)
    }
'''
s = must_replace(s, old_settings, new_settings, "employee connection settings")
old_auto = '''        automatic.addView(presenceSwitch)
        automatic.addView(UiKit.subtitle(this, p, "يعمل عبر Bluetooth أو شبكة المحل حسب القناة المتاحة، ولا يسجل حضورًا بدون إثبات."))
        root.addView(automatic)
'''
new_auto = '''        automatic.addView(presenceSwitch)
        automatic.addView(UiKit.subtitle(this, p, "يعمل عبر Bluetooth أو شبكة المحل حسب القناة المتاحة، ولا يسجل حضورًا بدون إثبات."))
        automatic.addView(UiKit.button(this, p, "إدارة الاتصال", false).apply { setOnClickListener { showEmployeeConnectionControl1977() } })
        root.addView(automatic)
'''
s = must_replace(s, old_auto, new_auto, "employee connection card")
old_att = '    private fun showEmployeeAttendanceCenter(){\n        AlertDialog.Builder(this).setTitle("تسجيل الحضور والانصراف").setItems(arrayOf("تسجيل الحضور الآن","تسجيل الانصراف الآن")){_,which->chooseAttendanceMethod(if(which==0)AttendanceAction.CHECK_IN else AttendanceAction.CHECK_OUT)}.setNegativeButton("إغلاق",null).show()\n    }\n'
new_att = '    private fun showEmployeeAttendanceCenter(){\n        showLayeredMenu1977("تسجيل الحضور والانصراف", listOf("تسجيل الحضور الآن" to { chooseAttendanceMethod(AttendanceAction.CHECK_IN) }, "تسجيل الانصراف الآن" to { chooseAttendanceMethod(AttendanceAction.CHECK_OUT) }))\n    }\n'
s = must_replace(s, old_att, new_att, "employee attendance layered")
old_ver = '''        items += "اختبار بصمة/وجه الهاتف" to { testBiometricOnly() }
        AlertDialog.Builder(this).setTitle("طرق التحقق").setItems(items.map { it.first }.toTypedArray()){_,which->items[which].second.invoke()}.setNegativeButton("إغلاق",null).show()
    }
'''
new_ver = '''        items += "اختبار بصمة/وجه الهاتف" to { testBiometricOnly() }
        showLayeredMenu1977("طرق التحقق", items)
    }
'''
s = must_replace(s, old_ver, new_ver, "employee verification layered")
s = s.replace("4. الإشعارات: تستقبل رسائل إدارة النظام أو إدارة المحل ويمكنك الرد عليها.", "4. الإشعارات: تستقبل رسائل إدارة النظام وإدارة المحل؛ وإذا كان الهاتف متصلًا بالمحل عبر Bluetooth الموثق يمكن استقبال رسالة المحل مباشرة بدون إنترنت.", 1)
s = s.replace("5. قائمة ⋮: منها الإشعارات ودليل المستخدم والإعدادات.", "5. قائمة ⋮: منها الإشعارات ودليل المستخدم والإعدادات وإدارة الاتصال.", 1)
write(rel, s)

rel = "store-app/src/main/java/com/attendpro/store/StoreSettingsActivity.kt"
s = read(rel)
s = s.replace('    private var sessionToken = ""\n', '    private var sessionToken = ""\n    private var advancedMode1977 = false\n', 1)
s = s.replace('    private fun showDashboard() {\n', '    private fun showDashboard() {\n        advancedMode1977 = false\n', 1)
s = s.replace('    private fun showAdvancedDashboard1975() {\n', '    private fun showAdvancedDashboard1975() {\n        advancedMode1977 = true\n', 1)
menu_anchor = "    private fun showStoreTopMenu1976() {\n"
helper = r'''    private fun showLayeredMenu1977(title: String, items: List<Pair<String, () -> Unit>>) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@StoreSettingsActivity, 18), UiKit.dp(this@StoreSettingsActivity, 8), UiKit.dp(this@StoreSettingsActivity, 18), UiKit.dp(this@StoreSettingsActivity, 8))
        }
        items.forEach { (label, action) -> box.addView(UiKit.button(this, p, label, false).apply { setOnClickListener { action() } }) }
        AlertDialog.Builder(this).setTitle(title).setView(box).setNegativeButton("رجوع", null).show()
    }

'''
s = s.replace(menu_anchor, helper + menu_anchor, 1)
old_top = '''    private fun showStoreTopMenu1976() {
        val items = arrayOf("الإشعارات", "دليل مستخدم إدارة المحل", "الإعدادات المتقدمة")
        AlertDialog.Builder(this).setTitle("القائمة").setItems(items) { _, which ->
            when (which) {
                0 -> startActivity(Intent(this, StoreMessages1975Activity::class.java))
                1 -> showStoreUserGuide1976()
                2 -> showAdvancedDashboard1975()
            }
        }.show()
    }
'''
new_top = '''    private fun showStoreTopMenu1976() {
        showLayeredMenu1977("القائمة", listOf(
            "الإشعارات" to { startActivity(Intent(this, StoreMessages1975Activity::class.java)) },
            "دليل مستخدم إدارة المحل" to { showStoreUserGuide1976() },
            "الإعدادات المتقدمة" to { showAdvancedDashboard1975() }
        ))
    }
'''
s = must_replace(s, old_top, new_top, "store settings top layered")
old_ops = '''    private fun showStoreOperations1976() {
        val items = arrayOf("طرق الحضور", "الدوام ودقائق السماح", "موقع المحل وGPS", "قارئ البصمة الخارجي")
        AlertDialog.Builder(this).setTitle("الحضور والتشغيل").setItems(items) { _, which ->
            when (which) {
                0 -> attendanceMethodsSettings()
                1 -> shiftSettings()
                2 -> gpsSettings()
                3 -> fingerprintSettings()
            }
        }.show()
    }
'''
new_ops = '''    private fun showStoreOperations1976() {
        showLayeredMenu1977("الحضور والتشغيل", listOf(
            "طرق الحضور" to { attendanceMethodsSettings() },
            "الدوام ودقائق السماح" to { shiftSettings() },
            "موقع المحل وGPS" to { gpsSettings() },
            "قارئ البصمة الخارجي" to { fingerprintSettings() }
        ))
    }
'''
s = must_replace(s, old_ops, new_ops, "store ops layered")
old_com = '''    private fun showStoreCommunication1976() {
        val items = arrayOf("التحكم الصوتي", "الرسائل والإشعارات")
        AlertDialog.Builder(this).setTitle("الصوت والرسائل").setItems(items) { _, which ->
            when (which) {
                0 -> startActivity(Intent(this, StoreVoiceControl1975Activity::class.java))
                1 -> startActivity(Intent(this, StoreMessages1975Activity::class.java))
            }
        }.show()
    }
'''
new_com = '''    private fun showStoreCommunication1976() {
        showLayeredMenu1977("الصوت والرسائل", listOf(
            "التحكم الصوتي" to { startActivity(Intent(this, StoreVoiceControl1975Activity::class.java)) },
            "الرسائل والإشعارات" to { startActivity(Intent(this, StoreMessages1975Activity::class.java)) }
        ))
    }
'''
s = must_replace(s, old_com, new_com, "store comm layered")
old_rep = '''    private fun showStoreReportsSecurity1976() {
        val items = arrayOf("التقارير والمشاركة", "هواتف استلام التقارير", if (repo.hasStoreAdminPin) "تغيير رمز إدارة المحل" else "إنشاء رمز حماية", "فحص جاهزية المحل", "المظهر والقوالب")
        AlertDialog.Builder(this).setTitle("التقارير والحماية").setItems(items) { _, which ->
            when (which) {
                0 -> startActivity(Intent(this, ReportsActivity::class.java).putExtra(ReportsActivity.EXTRA_STORE_ADMIN_SESSION, sessionToken))
                1 -> manageReportReceivers()
                2 -> changeStorePin()
                3 -> healthCheck()
                4 -> UiKit.showAppearancePicker(this)
            }
        }.show()
    }
'''
new_rep = '''    private fun showStoreReportsSecurity1976() {
        showLayeredMenu1977("التقارير والحماية", listOf(
            "التقارير والمشاركة" to { startActivity(Intent(this, ReportsActivity::class.java).putExtra(ReportsActivity.EXTRA_STORE_ADMIN_SESSION, sessionToken)) },
            "هواتف استلام التقارير" to { manageReportReceivers() },
            (if (repo.hasStoreAdminPin) "تغيير رمز إدارة المحل" else "إنشاء رمز حماية") to { changeStorePin() },
            "فحص جاهزية المحل" to { healthCheck() },
            "المظهر والقوالب" to { UiKit.showAppearancePicker(this) }
        ))
    }
'''
s = must_replace(s, old_rep, new_rep, "store reports layered")
s = must_replace(s, "    companion object { private const val REQUEST_REPORT_RECEIVER_QR = 9201 }\n", '''    override fun onBackPressed() {
        if (advancedMode1977) showDashboard() else super.onBackPressed()
    }

    companion object { private const val REQUEST_REPORT_RECEIVER_QR = 9201 }
''', "store settings back")
write(rel, s)

rel = "store-app/src/main/java/com/attendpro/store/SystemSettingsActivity.kt"
s = read(rel)
s = s.replace("class SystemSettingsActivity : Activity() {\n", "class SystemSettingsActivity : Activity() {\n    private var navigationScreen1977 = \"GATEWAY\"\n", 1)
s = s.replace("    private fun showGateway() {\n", "    private fun showGateway() {\n        navigationScreen1977 = \"GATEWAY\"\n", 1)
s = s.replace("    private fun showOwnerDashboardClassic() {\n", "    private fun showOwnerDashboardClassic() {\n        navigationScreen1977 = \"OWNER\"\n", 1)
s = s.replace("    private fun showOwnerDashboardSections(section: String = \"SUMMARY\") {\n", "    private fun showOwnerDashboardSections(section: String = \"SUMMARY\") {\n        navigationScreen1977 = \"OWNER\"\n", 1)
s = s.replace("    private fun showSystemManagementCenter() {\n", "    private fun showSystemManagementCenter() {\n        navigationScreen1977 = \"CENTRAL\"\n", 1)
s = s.replace("    private fun showAgentDashboard(agent: AgentRecord) {\n", "    private fun showAgentDashboard(agent: AgentRecord) {\n        navigationScreen1977 = \"AGENT\"\n", 1)
old_back = '''    override fun onBackPressed() {
        showGateway()
    }
'''
new_back = '''    override fun onBackPressed() {
        when (navigationScreen1977) {
            "CENTRAL" -> showOwnerDashboard()
            "OWNER", "AGENT" -> showGateway()
            else -> super.onBackPressed()
        }
    }
'''
s = must_replace(s, old_back, new_back, "system sequential back")
write(rel, s)

print("ATTEND-PRO 1.9.77 navigation + connection control + offline messaging patch applied")
