package com.attendpro.store

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import com.attendpro.core.AppLanguage
import com.attendpro.core.CentralServerClient
import com.attendpro.core.DeviceIdentity
import com.attendpro.core.StoreRepository
import com.attendpro.core.UiKit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StoreMessages1975Activity : Activity() {
    private lateinit var repo: StoreRepository
    private lateinit var identity: DeviceIdentity
    private lateinit var localReplies: StoreLocalReplyStore1977
    private val p by lazy { UiKit.palette(this) }
    private fun t(ar: String, en: String) = AppLanguage.text(this, ar, en)
    @Volatile private var inboxLoadInFlight1981 = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = StoreRepository(this)
        identity = DeviceIdentity(this)
        localReplies = StoreLocalReplyStore1977(this)
        showInbox()
        ensureNotificationPermission1980()
        StoreMessagePoll1975.schedule(this)
    }

    private fun ensureNotificationPermission1980() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1980)
        }
    }

    private fun showInbox() {
        val root = base(t("الرسائل والإشعارات", "Messages and notifications"), t("رسائل إدارة النظام وردود الموظفين ورسائل المحل", "System messages, employee replies and Store messages"))
        val actions = UiKit.card(this, p)
        actions.addView(UiKit.button(this, p, t("✉ إرسال رسالة لموظف", "✉ Send a message to an employee")).apply { setOnClickListener { composeEmployee() } })
        actions.addView(UiKit.button(this, p, t("↻ تحديث الوارد", "↻ Refresh inbox"), false).apply { setOnClickListener { loadMessages(root) } })
        root.addView(actions)
        loadMessages(root)
    }

    private fun loadMessages(root: LinearLayout) {
        if (inboxLoadInFlight1981) return
        inboxLoadInFlight1981 = true
        Thread {
            try {
                val result = CentralServerClient.storeMessagesInbox(repo.serverUrl, repo.centralAccessToken, repo.storeId, identity, 100)
                val local = localReplies.all()
                runOnUiThread {
                    val remote = result.getOrDefault(emptyList())
                    val merged = (local + remote).distinctBy { it.messageId }.sortedByDescending { it.createdAt }
                    renderMessages(root, merged)
                    if (result.isFailure && local.isNotEmpty()) {
                        toast(t("الخادم غير متاح؛ الردود المحلية ما زالت ظاهرة.", "Server unavailable; local replies are still available."))
                    } else if (result.isFailure) {
                        toast(t("تعذر تحميل الرسائل: ${result.exceptionOrNull()?.message}", "Unable to load messages: ${result.exceptionOrNull()?.message}"))
                    }
                }
            } finally {
                inboxLoadInFlight1981 = false
            }
        }.apply { isDaemon = true }.start()
    }

    private fun renderMessages(root: LinearLayout, messages: List<CentralServerClient.Message1975>) {
        while (root.childCount > 2) root.removeViewAt(2)
        val card = UiKit.card(this, p)
        card.addView(UiKit.sectionLabel(this, p, t("الوارد", "Inbox")))
        if (messages.isEmpty()) card.addView(UiKit.subtitle(this, p, t("لا توجد رسائل حاليًا.", "No messages right now.")))
        messages.forEach { message ->
            val unread = message.readAt <= 0L
            val sender = when (message.senderType) { "SYSTEM_OWNER" -> t("إدارة النظام", "System administration"); "EMPLOYEE" -> t("الموظف ${message.employeeId}", "Employee ${message.employeeId}"); else -> t("إدارة المحل", "Store Management") }
            val title = TextView(this).apply {
                text = "${if (unread) "● " else ""}${message.title.ifBlank { t("رسالة", "Message") }} • $sender"
                textSize = 16f; gravity = Gravity.RIGHT; setTextColor(p.text)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, UiKit.dp(this@StoreMessages1975Activity, 8), 0, 2)
            }
            card.addView(title)
            card.addView(UiKit.subtitle(this, p, "${message.body}\n${time(message.createdAt)} • ${priorityArabic(message.priority)}"))
            if (message.senderType == "EMPLOYEE" && message.employeeId.isNotBlank()) {
                card.addView(UiKit.button(this, p, t("↩ رد على الموظف", "↩ Reply to employee"), false).apply {
                    setOnClickListener {
                        val employeeName = repo.employees().firstOrNull { it.employeeId.equals(message.employeeId, true) }?.displayName ?: message.employeeId
                        composeFor(message.employeeId, employeeName)
                    }
                })
            }
            card.addView(UiKit.button(this, p, if (unread) t("تعليم كمقروء", "Mark as read") else t("مقروء ✓", "Read ✓"), false).apply {
                isEnabled = unread
                setOnClickListener { markRead(message.messageId) }
            })
        }
        root.addView(card)
        val back = UiKit.card(this, p)
        back.addView(UiKit.button(this, p, t("رجوع", "Back"), false).apply { setOnClickListener { finish() } })
        root.addView(back)
        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
    }

    private fun composeEmployee() {
        val employees = repo.employees().filter { it.active }
        if (employees.isEmpty()) { toast(t("لا يوجد موظفون نشطون", "There are no active employees")); return }
        val names = employees.map { "${it.displayName} • ${it.employeeId}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle(t("اختر الموظف", "Choose employee")).setItems(names) { _, index -> composeFor(employees[index].employeeId, employees[index].displayName) }.setNegativeButton(t("إلغاء", "Cancel"), null).show()
    }

    private fun composeFor(employeeId: String, name: String) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = if (AppLanguage.isEnglish(this@StoreMessages1975Activity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL; setPadding(28, 8, 28, 0) }
        val title = UiKit.field(this, p, t("عنوان الرسالة", "Message title")).apply { setText(t("رسالة من إدارة المحل", "Message from Store Management")) }
        val message = UiKit.field(this, p, t("اكتب الرسالة التي تريد إرسالها", "Write the message you want to send")).apply { minLines = 3 }
        val priority = Spinner(this).apply { adapter = ArrayAdapter(this@StoreMessages1975Activity, android.R.layout.simple_spinner_dropdown_item, arrayOf(t("عادية", "Normal"), t("مهمة", "Important"), t("عاجلة", "Urgent"))) }
        val mode = repo.employeeMessageVoiceMode(employeeId)
        val defaultVoice = when (mode) { "VOICE_NOTIFICATION" -> true; "NOTIFICATION_ONLY", "SILENT" -> false; else -> repo.employeeMessageVoiceDefaultEnabled }
        val voice = CheckBox(this).apply { text = t("قراءة الرسالة بصوت على هاتف الموظف", "Read the message aloud on the employee phone"); isChecked = defaultVoice; gravity = if (AppLanguage.isEnglish(this@StoreMessages1975Activity)) Gravity.LEFT else Gravity.RIGHT; layoutDirection = if (AppLanguage.isEnglish(this@StoreMessages1975Activity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL }
        box.addView(title); box.addView(message); box.addView(priority); box.addView(voice)
        val directReady = StoreDirectLinkBridge1977.isConnected(employeeId)
        box.addView(UiKit.subtitle(this, p, if (directReady) t("● الهاتف متصل مباشرة الآن — ستُرسل الرسالة أولًا عبر Bluetooth الموثق بدون إنترنت.", "● The phone is directly connected — the message will be sent first over authenticated Bluetooth without Internet.") else t("○ لا توجد قناة Bluetooth موثقة الآن — سيستخدم التطبيق الخادم عند توفره.", "○ No authenticated Bluetooth channel is available — the server will be used when available.")))
        if (mode == "SILENT") box.addView(UiKit.subtitle(this, p, t("تنبيه: إعداد هذا الموظف مضبوط على صامت. يمكنك إرسال الرسالة، لكن لن يتم تشغيل النطق تلقائيًا.", "Notice: this employee is set to Silent. You can send the message, but voice playback will not run automatically.")))
        val d = AlertDialog.Builder(this).setTitle(t("رسالة إلى $name", "Message to $name")).setView(box).setPositiveButton(t("إرسال", "Send"), null).setNegativeButton(t("إلغاء", "Cancel"), null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val body = message.text.toString().trim()
                if (body.isBlank()) { message.error = t("اكتب الرسالة", "Write a message"); return@setOnClickListener }
                val pr = when (priority.selectedItemPosition) { 2 -> "URGENT"; 1 -> "IMPORTANT"; else -> "NORMAL" }
                d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                val voiceEnabled = voice.isChecked && mode != "SILENT"
                fun sendServerFallback(reason: String) {
                    if (!repo.hasCentralCredentials()) { toast(t("$reason — ولا يوجد اتصال خادم مهيأ", "$reason — no configured server connection is available")); d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true; return }
                    Thread {
                        val r = CentralServerClient.sendStoreMessageToEmployee(repo.serverUrl, repo.centralAccessToken, repo.storeId, identity, employeeId, title.text.toString(), body, pr, voiceEnabled)
                        runOnUiThread { r.onSuccess { toast(t("تم إرسال الرسالة عبر الخادم إلى $name", "Message sent to $name through the server")); d.dismiss(); showInbox() }.onFailure { toast(t("تعذر الإرسال: ${it.message}", "Send failed: ${it.message}")); d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true } }
                    }.start()
                }
                val localStarted = StoreDirectLinkBridge1977.sendMessage(employeeId, title.text.toString(), body, pr, voiceEnabled) { success, localStatus ->
                    runOnUiThread { if (success) { toast("✓ $localStatus — $name"); d.dismiss(); showInbox() } else sendServerFallback(localStatus) }
                }
                if (!localStarted) sendServerFallback(t("لا توجد قناة اتصال محلية موثقة مع هاتف $name", "No authenticated local channel is available with the employee phone: $name"))
            }
        }
        d.show()
    }

    private fun markRead(messageId: String) {
        if (localReplies.isLocal(messageId)) {
            localReplies.markRead(messageId)
            showInbox()
            return
        }
        Thread {
            CentralServerClient.markStoreMessageRead(repo.serverUrl, repo.centralAccessToken, repo.storeId, identity, messageId)
            runOnUiThread { showInbox() }
        }.start()
    }

    private fun base(title: String, subtitle: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; layoutDirection = if (AppLanguage.isEnglish(this@StoreMessages1975Activity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL; gravity = Gravity.CENTER_HORIZONTAL
        setPadding(UiKit.dp(this@StoreMessages1975Activity,16), UiKit.dp(this@StoreMessages1975Activity,18), UiKit.dp(this@StoreMessages1975Activity,16), UiKit.dp(this@StoreMessages1975Activity,30)); setBackgroundColor(p.bg)
        val hero = UiKit.heroCard(this@StoreMessages1975Activity, p)
        hero.addView(UiKit.title(this@StoreMessages1975Activity, p, title, 24f).apply { gravity=Gravity.CENTER; setTextColor(android.graphics.Color.WHITE) })
        hero.addView(UiKit.subtitle(this@StoreMessages1975Activity, p, subtitle).apply { gravity=Gravity.CENTER; setTextColor(android.graphics.Color.argb(225,255,255,255)) })
        addView(hero)
    }

    private fun priorityArabic(v: String) = when(v) { "URGENT" -> t("عاجلة", "Urgent"); "IMPORTANT" -> t("مهمة", "Important"); else -> t("عادية", "Normal") }
    private fun time(ms: Long) = if (ms <= 0L) "" else SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(ms))
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
