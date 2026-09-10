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
import com.attendpro.core.CentralServerClient
import com.attendpro.core.EmployeeIdentityStore
import com.attendpro.core.UiKit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmployeeMessages1975Activity : Activity() {
    private lateinit var identity: EmployeeIdentityStore
    private val p by lazy { UiKit.palette(this) }
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
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(UiKit.dp(this@EmployeeMessages1975Activity,16), UiKit.dp(this@EmployeeMessages1975Activity,18), UiKit.dp(this@EmployeeMessages1975Activity,16), UiKit.dp(this@EmployeeMessages1975Activity,30)); setBackgroundColor(p.bg)
        }
        val hero = UiKit.heroCard(this, p)
        hero.addView(UiKit.title(this,p,"الرسائل والإشعارات",24f).apply { gravity=Gravity.CENTER; setTextColor(android.graphics.Color.WHITE) })
        hero.addView(UiKit.subtitle(this,p,"مركز التواصل مع إدارة المحل وإدارة النظام").apply { gravity=Gravity.CENTER; setTextColor(android.graphics.Color.argb(225,255,255,255)) })
        root.addView(hero)
        val actions = UiKit.card(this,p,10)
        actions.addView(UiKit.button(this,p,"＋ رسالة جديدة إلى إدارة المحل").apply { setOnClickListener { composeToStore1978() } })
        actions.addView(UiKit.subtitle(this,p,"يمكنك بدء رسالة لإدارة المحل، أو الرد على أي رسالة واردة. الرد على رسالة إدارة النظام يعود إلى إدارة النظام.").apply { gravity=Gravity.CENTER })
        root.addView(actions)
        val loading = UiKit.card(this,p).apply { addView(UiKit.subtitle(this@EmployeeMessages1975Activity,p,"يجري تحميل الرسائل…")) }
        root.addView(loading); setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
        val localMessages = localStore.all()
        Thread {
            val remoteResult = CentralServerClient.employeeMessages(identity.serverUrl, identity.trustedStoreId, identity.employeeId, identity.pairingSecret, identity.installationId, true, 100)
            val remote = remoteResult.getOrDefault(emptyList())
            val messages = (localMessages + remote).distinctBy { it.messageId }.sortedByDescending { it.createdAt }
            runOnUiThread {
                loadInFlight1981 = false
                root.removeView(loading)
                if (remoteResult.isFailure) root.addView(UiKit.card(this,p).apply { addView(UiKit.subtitle(this@EmployeeMessages1975Activity,p,"لا يوجد اتصال بالخادم الآن؛ الرسائل المباشرة المستلمة من جهاز المحل تبقى متاحة بدون إنترنت.")) })
                if (messages.isEmpty()) root.addView(UiKit.card(this,p).apply { addView(UiKit.subtitle(this@EmployeeMessages1975Activity,p,"لا توجد رسائل حاليًا.")) })
                messages.forEach { m ->
                    val local = localStore.isLocal(m.messageId); val card = UiKit.card(this,p)
                    val sender = if (m.senderType == "SYSTEM_OWNER") "إدارة نظام ATTEND PRO" else "إدارة المحل"
                    card.addView(UiKit.sectionLabel(this,p,(if (m.readAt <= 0L) "● " else "") + sender + if (local) " • مباشر بدون إنترنت" else ""))
                    card.addView(UiKit.title(this,p,m.title.ifBlank { "رسالة" },18f)); card.addView(UiKit.subtitle(this,p,"${m.body}\n${time(m.createdAt)} • ${priorityArabic(m.priority)}"))
                    if (m.readAt <= 0L) card.addView(UiKit.button(this,p,"تعليم كمقروء",false).apply { setOnClickListener { markRead(m.messageId) } })
                    card.addView(UiKit.button(this,p,"رد على الرسالة",false).apply { setOnClickListener { reply(m) } })
                    if (local) card.addView(UiKit.subtitle(this,p,"وصلت هذه الرسالة مباشرة بدون إنترنت. إرسال الرد يحتاج اتصالًا بالخادم حاليًا."))
                    root.addView(card)
                }
                root.addView(UiKit.card(this,p).apply {
                    addView(UiKit.button(this@EmployeeMessages1975Activity,p,"تحديث",false).apply { setOnClickListener { load() } })
                    addView(UiKit.button(this@EmployeeMessages1975Activity,p,"رجوع",false).apply { setOnClickListener { finish() } })
                })
            }
        }.start()
    }

    private fun markRead(id: String) {
        if (localStore.isLocal(id)) { localStore.markRead(id); load(); return }
        Thread { CentralServerClient.markEmployeeMessageRead(identity.serverUrl, identity.trustedStoreId, identity.employeeId, identity.pairingSecret, identity.installationId, id); runOnUiThread { load() } }.start()
    }

    private fun composeToStore1978() {
        if (!identity.isConfigured) { toast("اربط تطبيق الموظف بالمحل أولًا"); return }
        val field = UiKit.field(this,p,"اكتب رسالتك إلى إدارة المحل").apply { minLines=4 }
        val d = AlertDialog.Builder(this).setTitle("رسالة جديدة إلى إدارة المحل").setView(field).setPositiveButton("إرسال",null).setNegativeButton("إلغاء",null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val message=field.text.toString().trim(); if(message.isBlank()){field.error="اكتب الرسالة";return@setOnClickListener}
                d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
                Thread {
                    val r=CentralServerClient.replyEmployeeMessage(identity.serverUrl,identity.trustedStoreId,identity.employeeId,identity.pairingSecret,identity.installationId,"",message)
                    runOnUiThread { r.onSuccess { toast("تم إرسال الرسالة إلى إدارة المحل");d.dismiss();load() }.onFailure { toast("تعذر الإرسال الآن. تحقق من الإنترنت ثم أعد المحاولة");d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true } }
                }.start()
            }
        };d.show()
    }

    private fun reply(parent: CentralServerClient.Message1975) {
        val field = com.attendpro.core.UiKit.field(this,p,"اكتب ردك").apply { minLines=3 }
        val target = if (parent.senderType == "SYSTEM_OWNER") "إدارة النظام" else "إدارة المحل"
        val d = AlertDialog.Builder(this).setTitle("رد إلى $target").setView(field).setPositiveButton("إرسال",null).setNegativeButton("إلغاء",null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val message = field.text.toString().trim(); if (message.isBlank()) { field.error="اكتب الرد"; return@setOnClickListener }
                Thread {
                    val r=CentralServerClient.replyEmployeeMessage(identity.serverUrl,identity.trustedStoreId,identity.employeeId,identity.pairingSecret,identity.installationId,parent.messageId,message)
                    runOnUiThread { r.onSuccess { toast("تم إرسال الرد"); d.dismiss(); markRead(parent.messageId) }.onFailure { toast("تعذر إرسال الرد: ${it.message}") } }
                }.start()
            }
        }; d.show()
    }

    private fun priorityArabic(v:String)=when(v){"URGENT"->"عاجلة";"IMPORTANT"->"مهمة";else->"عادية"}
    private fun time(ms:Long)=if(ms<=0L)"" else SimpleDateFormat("dd/MM HH:mm",Locale.getDefault()).format(Date(ms))
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
