package com.attendpro.employee

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.attendpro.core.AppLanguage
import com.attendpro.core.CentralServerClient
import com.attendpro.core.EmployeeIdentityStore
import com.attendpro.core.UiKit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmployeeMessages1975Activity : Activity() {
    private lateinit var identity: EmployeeIdentityStore
    private val p by lazy { UiKit.palette(this) }
    private fun t(ar: String, en: String) = AppLanguage.text(this, ar, en)
    private lateinit var localStore: EmployeeLocalMessageStore1977
    @Volatile private var loadInFlight1981 = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        identity = EmployeeIdentityStore(this)
        localStore = EmployeeLocalMessageStore1977(this)
        load()
    }

    private fun load() {
        if (loadInFlight1981) return
        loadInFlight1981 = true
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = if (AppLanguage.isEnglish(this@EmployeeMessages1975Activity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(UiKit.dp(this@EmployeeMessages1975Activity,16), UiKit.dp(this@EmployeeMessages1975Activity,18), UiKit.dp(this@EmployeeMessages1975Activity,16), UiKit.dp(this@EmployeeMessages1975Activity,30)); setBackgroundColor(p.bg)
        }
        val hero = UiKit.heroCard(this, p)
        hero.addView(UiKit.title(this,p,t("الرسائل والإشعارات", "Messages and notifications"),24f).apply { gravity=Gravity.CENTER; setTextColor(android.graphics.Color.WHITE) })
        hero.addView(UiKit.subtitle(this,p,t("مركز التواصل مع إدارة المحل وإدارة النظام", "Communication center for Store Management and system administration")).apply { gravity=Gravity.CENTER; setTextColor(android.graphics.Color.argb(225,255,255,255)) })
        root.addView(hero)
        val actions = UiKit.card(this,p,10)
        actions.addView(UiKit.button(this,p,t("＋ رسالة جديدة إلى إدارة المحل", "＋ New message to Store Management")).apply { setOnClickListener { composeToStore1978() } })
        actions.addView(UiKit.subtitle(this,p,t("يمكنك بدء رسالة لإدارة المحل، أو الرد على أي رسالة واردة. الرد على رسالة إدارة النظام يعود إلى إدارة النظام.", "You can start a message to Store Management or reply to any incoming message. Replies to system messages return to system administration.")).apply { gravity=Gravity.CENTER })
        root.addView(actions)
        val loading = UiKit.card(this,p).apply { addView(UiKit.subtitle(this@EmployeeMessages1975Activity,p,t("يجري تحميل الرسائل…", "Loading messages…"))) }
        root.addView(loading); setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
        val localMessages = localStore.all()
        Thread {
            val remoteResult = CentralServerClient.employeeMessages(identity.serverUrl, identity.trustedStoreId, identity.employeeId, identity.pairingSecret, identity.installationId, true, 100)
            val remote = remoteResult.getOrDefault(emptyList())
            val messages = (localMessages + remote).distinctBy { it.messageId }.sortedByDescending { it.createdAt }
            runOnUiThread {
                loadInFlight1981 = false
                root.removeView(loading)
                if (remoteResult.isFailure) root.addView(UiKit.card(this,p).apply { addView(UiKit.subtitle(this@EmployeeMessages1975Activity,p,t("لا يوجد اتصال بالخادم الآن؛ الرسائل المباشرة المستلمة من جهاز المحل تبقى متاحة بدون إنترنت.", "The server is currently unavailable; direct Store messages remain available offline."))) })
                if (messages.isEmpty()) root.addView(UiKit.card(this,p).apply { addView(UiKit.subtitle(this@EmployeeMessages1975Activity,p,t("لا توجد رسائل حاليًا.", "No messages right now."))) })
                messages.forEach { m ->
                    val local = localStore.isLocal(m.messageId); val card = UiKit.card(this,p)
                    val sender = if (m.senderType == "SYSTEM_OWNER") t("إدارة نظام ATTEND PRO", "ATTEND PRO system administration") else t("إدارة المحل", "Store Management")
                    card.addView(UiKit.sectionLabel(this,p,(if (m.readAt <= 0L) "● " else "") + sender + if (local) t(" • مباشر بدون إنترنت", " • direct offline") else ""))
                    card.addView(UiKit.title(this,p,m.title.ifBlank { t("رسالة", "Message") },18f)); card.addView(UiKit.subtitle(this,p,"${m.body}\n${time(m.createdAt)} • ${priorityArabic(m.priority)}"))
                    if (m.readAt <= 0L) card.addView(UiKit.button(this,p,t("تعليم كمقروء", "Mark as read"),false).apply { setOnClickListener { markRead(m.messageId) } })
                    card.addView(UiKit.button(this,p,t("رد على الرسالة", "Reply"),false).apply { setOnClickListener { reply(m) } })
                    if (local) card.addView(UiKit.subtitle(this,p,t("وصلت هذه الرسالة مباشرة بدون إنترنت. يمكنك الرد مباشرة عبر Bluetooth الموثق ما دام جهاز المحل متصلًا.", "This message arrived directly without Internet. You can reply through authenticated Bluetooth while the Store device is connected.")))
                    root.addView(card)
                }
                root.addView(UiKit.card(this,p).apply {
                    addView(UiKit.button(this@EmployeeMessages1975Activity,p,t("تحديث", "Refresh"),false).apply { setOnClickListener { load() } })
                    addView(UiKit.button(this@EmployeeMessages1975Activity,p,t("رجوع", "Back"),false).apply { setOnClickListener { finish() } })
                })
            }
        }.start()
    }

    private fun markRead(id: String) {
        if (localStore.isLocal(id)) { localStore.markRead(id); load(); return }
        Thread { CentralServerClient.markEmployeeMessageRead(identity.serverUrl, identity.trustedStoreId, identity.employeeId, identity.pairingSecret, identity.installationId, id); runOnUiThread { load() } }.start()
    }

    private fun composeToStore1978() {
        if (!identity.isConfigured) { toast(t("اربط تطبيق الموظف بالمحل أولًا", "Pair the Employee app with the Store first")); return }
        val field = UiKit.field(this,p,t("اكتب رسالتك إلى إدارة المحل", "Write your message to Store Management")).apply { minLines=4 }
        val d = AlertDialog.Builder(this).setTitle(t("رسالة جديدة إلى إدارة المحل", "New message to Store Management")).setView(field).setPositiveButton(t("إرسال", "Send"),null).setNegativeButton(t("إلغاء", "Cancel"),null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val message=field.text.toString().trim(); if(message.isBlank()){field.error=t("اكتب الرسالة", "Write a message");return@setOnClickListener}
                d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
                if (EmployeeDirectReplyBridge1977.queueReply("", message)) {
                    toast(t("✓ تم إرسال الرسالة مباشرة إلى جهاز المحل بدون إنترنت", "✓ Message sent directly to the Store without Internet"))
                    d.dismiss()
                    load()
                    return@setOnClickListener
                }
                Thread {
                    val r=CentralServerClient.replyEmployeeMessage(identity.serverUrl,identity.trustedStoreId,identity.employeeId,identity.pairingSecret,identity.installationId,"",message)
                    runOnUiThread { r.onSuccess { toast(t("تم إرسال الرسالة إلى إدارة المحل", "Message sent to Store Management"));d.dismiss();load() }.onFailure { toast(t("تعذر الإرسال: لا توجد قناة Bluetooth موثقة ولا اتصال خادم متاح", "Unable to send: no authenticated Bluetooth channel or server connection is available"));d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true } }
                }.start()
            }
        };d.show()
    }

    private fun reply(parent: CentralServerClient.Message1975) {
        val field = com.attendpro.core.UiKit.field(this,p,t("اكتب ردك", "Write your reply")).apply { minLines=3 }
        val target = if (parent.senderType == "SYSTEM_OWNER") t("إدارة النظام", "System administration") else t("إدارة المحل", "Store Management")
        val d = AlertDialog.Builder(this).setTitle(t("رد إلى $target", "Reply to $target")).setView(field).setPositiveButton(t("إرسال", "Send"),null).setNegativeButton(t("إلغاء", "Cancel"),null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val message = field.text.toString().trim(); if (message.isBlank()) { field.error=t("اكتب الرد", "Write a reply"); return@setOnClickListener }
                if (EmployeeDirectReplyBridge1977.queueReply(parent.messageId, message)) {
                    toast(t("✓ تم إرسال الرد مباشرة إلى جهاز المحل بدون إنترنت", "✓ Reply sent directly to the Store without Internet"))
                    d.dismiss()
                    if (localStore.isLocal(parent.messageId)) localStore.markRead(parent.messageId)
                    load()
                    return@setOnClickListener
                }
                Thread {
                    val r=CentralServerClient.replyEmployeeMessage(identity.serverUrl,identity.trustedStoreId,identity.employeeId,identity.pairingSecret,identity.installationId,parent.messageId,message)
                    runOnUiThread { r.onSuccess { toast(t("تم إرسال الرد", "Reply sent")); d.dismiss(); markRead(parent.messageId) }.onFailure { toast(t("تعذر إرسال الرد: لا توجد قناة Bluetooth موثقة ولا اتصال خادم متاح", "Unable to send reply: no authenticated Bluetooth channel or server connection is available")) } }
                }.start()
            }
        }; d.show()
    }

    private fun priorityArabic(v:String)=when(v){"URGENT"->t("عاجلة","Urgent");"IMPORTANT"->t("مهمة","Important");else->t("عادية","Normal")}
    private fun time(ms:Long)=if(ms<=0L)"" else SimpleDateFormat("dd/MM HH:mm",Locale.getDefault()).format(Date(ms))
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
