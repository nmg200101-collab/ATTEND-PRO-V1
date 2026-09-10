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
    private val p by lazy { UiKit.palette(this) }
    @Volatile private var inboxLoadInFlight1981 = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = StoreRepository(this)
        identity = DeviceIdentity(this)
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
        val root = base("الرسائل والإشعارات", "رسائل إدارة النظام وردود الموظفين ورسائل المحل")
        val actions = UiKit.card(this, p)
        actions.addView(UiKit.button(this, p, "✉ إرسال رسالة لموظف").apply { setOnClickListener { composeEmployee() } })
        actions.addView(UiKit.button(this, p, "↻ تحديث الوارد", false).apply { setOnClickListener { loadMessages(root) } })
        root.addView(actions)
        loadMessages(root)
    }

    private fun loadMessages(root: LinearLayout) {
        if (inboxLoadInFlight1981) return
        inboxLoadInFlight1981 = true
        Thread {
            try {
                val result = CentralServerClient.storeMessagesInbox(repo.serverUrl, repo.centralAccessToken, repo.storeId, identity, 100)
                runOnUiThread {
                    result.onSuccess { messages -> renderMessages(root, messages) }
                        .onFailure { toast("تعذر تحميل الرسائل: ${it.message}") }
                }
            } finally {
                inboxLoadInFlight1981 = false
            }
        }.apply { isDaemon = true }.start()
    }

    private fun renderMessages(root: LinearLayout, messages: List<CentralServerClient.Message1975>) {
        while (root.childCount > 2) root.removeViewAt(2)
        val card = UiKit.card(this, p)
        card.addView(UiKit.sectionLabel(this, p, "الوارد"))
        if (messages.isEmpty()) card.addView(UiKit.subtitle(this, p, "لا توجد رسائل حاليًا."))
        messages.forEach { message ->
            val unread = message.readAt <= 0L
            val sender = when (message.senderType) { "SYSTEM_OWNER" -> "إدارة النظام"; "EMPLOYEE" -> "الموظف ${message.employeeId}"; else -> "إدارة المحل" }
            val title = TextView(this).apply {
                text = "${if (unread) "● " else ""}${message.title.ifBlank { "رسالة" }} • $sender"
                textSize = 16f; gravity = Gravity.RIGHT; setTextColor(p.text)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, UiKit.dp(this@StoreMessages1975Activity, 8), 0, 2)
            }
            card.addView(title)
            card.addView(UiKit.subtitle(this, p, "${message.body}\n${time(message.createdAt)} • ${priorityArabic(message.priority)}"))
            if (message.senderType == "EMPLOYEE" && message.employeeId.isNotBlank()) {
                card.addView(UiKit.button(this, p, "↩ رد على الموظف", false).apply {
                    setOnClickListener {
                        val employeeName = repo.employees().firstOrNull { it.employeeId.equals(message.employeeId, true) }?.displayName ?: message.employeeId
                        composeFor(message.employeeId, employeeName)
                    }
                })
            }
            card.addView(UiKit.button(this, p, if (unread) "تعليم كمقروء" else "مقروء ✓", false).apply {
                isEnabled = unread
                setOnClickListener { markRead(message.messageId) }
            })
        }
        root.addView(card)
        val back = UiKit.card(this, p)
        back.addView(UiKit.button(this, p, "رجوع", false).apply { setOnClickListener { finish() } })
        root.addView(back)
        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
    }

    private fun composeEmployee() {
        val employees = repo.employees().filter { it.active }
        if (employees.isEmpty()) { toast("لا يوجد موظفون نشطون"); return }
        val names = employees.map { "${it.displayName} • ${it.employeeId}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("اختر الموظف").setItems(names) { _, index -> composeFor(employees[index].employeeId, employees[index].displayName) }.setNegativeButton("إلغاء", null).show()
    }

    private fun composeFor(employeeId: String, name: String) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(28, 8, 28, 0) }
        val title = UiKit.field(this, p, "عنوان الرسالة").apply { setText("رسالة من إدارة المحل") }
        val message = UiKit.field(this, p, "اكتب الرسالة التي تريد إرسالها").apply { minLines = 3 }
        val priority = Spinner(this).apply { adapter = ArrayAdapter(this@StoreMessages1975Activity, android.R.layout.simple_spinner_dropdown_item, arrayOf("عادية", "مهمة", "عاجلة")) }
        val mode = repo.employeeMessageVoiceMode(employeeId)
        val defaultVoice = when (mode) { "VOICE_NOTIFICATION" -> true; "NOTIFICATION_ONLY", "SILENT" -> false; else -> repo.employeeMessageVoiceDefaultEnabled }
        val voice = CheckBox(this).apply { text = "قراءة الرسالة بصوت على هاتف الموظف"; isChecked = defaultVoice; gravity = Gravity.RIGHT; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        box.addView(title); box.addView(message); box.addView(priority); box.addView(voice)
        val directReady = StoreDirectLinkBridge1977.isConnected(employeeId)
        box.addView(UiKit.subtitle(this, p, if (directReady) "● الهاتف متصل مباشرة الآن — ستُرسل الرسالة أولًا عبر Bluetooth الموثق بدون إنترنت." else "○ لا توجد قناة Bluetooth موثقة الآن — سيستخدم التطبيق الخادم عند توفره."))
        if (mode == "SILENT") box.addView(UiKit.subtitle(this, p, "تنبيه: إعداد هذا الموظف مضبوط على صامت. يمكنك إرسال الرسالة، لكن لن يتم تشغيل النطق تلقائيًا."))
        val d = AlertDialog.Builder(this).setTitle("رسالة إلى $name").setView(box).setPositiveButton("إرسال", null).setNegativeButton("إلغاء", null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val body = message.text.toString().trim()
                if (body.isBlank()) { message.error = "اكتب الرسالة"; return@setOnClickListener }
                val pr = when (priority.selectedItemPosition) { 2 -> "URGENT"; 1 -> "IMPORTANT"; else -> "NORMAL" }
                d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
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
            }
        }
        d.show()
    }

    private fun markRead(messageId: String) {
        Thread {
            CentralServerClient.markStoreMessageRead(repo.serverUrl, repo.centralAccessToken, repo.storeId, identity, messageId)
            runOnUiThread { showInbox() }
        }.start()
    }

    private fun base(title: String, subtitle: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; gravity = Gravity.CENTER_HORIZONTAL
        setPadding(UiKit.dp(this@StoreMessages1975Activity,16), UiKit.dp(this@StoreMessages1975Activity,18), UiKit.dp(this@StoreMessages1975Activity,16), UiKit.dp(this@StoreMessages1975Activity,30)); setBackgroundColor(p.bg)
        val hero = UiKit.heroCard(this@StoreMessages1975Activity, p)
        hero.addView(UiKit.title(this@StoreMessages1975Activity, p, title, 24f).apply { gravity=Gravity.CENTER; setTextColor(android.graphics.Color.WHITE) })
        hero.addView(UiKit.subtitle(this@StoreMessages1975Activity, p, subtitle).apply { gravity=Gravity.CENTER; setTextColor(android.graphics.Color.argb(225,255,255,255)) })
        addView(hero)
    }

    private fun priorityArabic(v: String) = when(v) { "URGENT" -> "عاجلة"; "IMPORTANT" -> "مهمة"; else -> "عادية" }
    private fun time(ms: Long) = if (ms <= 0L) "" else SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(ms))
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
