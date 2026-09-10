package com.attendpro.store

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.attendpro.core.QrCodeTools
import com.attendpro.core.CentralServerClient
import com.attendpro.core.ReportProtocol
import com.attendpro.core.ReportReceiverStore
import com.attendpro.core.QrScannerActivity
import com.attendpro.core.UiKit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportReceiverActivity : Activity() {
    private lateinit var receiver: ReportReceiverStore
    private val p by lazy { UiKit.palette(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiver = ReportReceiverStore(this)
        handleIncoming(intent)
        buildUi()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) handleIncoming(intent)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (::receiver.isInitialized && receiver.serverUrl.isNotBlank()) refreshRemote(silent = true)
    }

    private fun handleIncoming(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val raw = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if (raw.isNotBlank()) receive(raw, showResult = false)
    }

    private fun buildUi() {
        window.statusBarColor = p.bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@ReportReceiverActivity, 16), UiKit.dp(this@ReportReceiverActivity, 18), UiKit.dp(this@ReportReceiverActivity, 16), UiKit.dp(this@ReportReceiverActivity, 28)); setBackgroundColor(p.bg)
        }
        val header = UiKit.heroCard(this, p)
        header.addView(UiKit.title(this, p, "استلام التقارير", 25f).apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.WHITE) })
        header.addView(UiKit.subtitle(this, p, "هاتف مراقبة مصرح • تقارير مشفرة • متابعة عن بُعد").apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.argb(225,255,255,255)) })
        root.addView(header)

        val identity = UiKit.card(this, p)
        identity.addView(UiKit.sectionLabel(this, p, "هوية هاتف الاستلام"))
        identity.addView(UiKit.subtitle(this, p, "الاسم: ${receiver.receiverName}\nالمعرف: ${receiver.receiverId}"))
        identity.addView(UiKit.button(this, p, "تغيير اسم هذا الهاتف", false).apply { setOnClickListener { rename() } })
        identity.addView(UiKit.button(this, p, "عرض QR منح الصلاحية").apply { setOnClickListener { showInviteQr() } })
        identity.addView(UiKit.button(this, p, "مسح QR ربط الخادم من جهاز المحل", false).apply { setOnClickListener { scanRemoteGrant() } })
        root.addView(identity)

        val remote = UiKit.card(this, p)
        remote.addView(UiKit.sectionLabel(this, p, "المراقبة عن بُعد عبر الإنترنت"))
        remote.addView(UiKit.subtitle(this, p, if (receiver.serverUrl.isBlank()) "لم يتم ربط هذا الهاتف بالخادم بعد." else "الخادم: ${receiver.serverUrl}\n${receiver.remoteDashboardText.ifBlank { "اضغط تحديث لجلب حالة المحل والتقارير." }}"))
        remote.addView(UiKit.button(this, p, "ربط / تغيير عنوان الخادم", false).apply { setOnClickListener { editServerUrl() } })
        remote.addView(UiKit.button(this, p, "تحديث الآن من الإنترنت").apply { setOnClickListener { refreshRemote() } })
        remote.addView(UiKit.button(this, p, "عرض تقرير مباشر من الخادم", false).apply { setOnClickListener { showRemoteReportPicker() } })
        root.addView(remote)

        val receiveCard = UiKit.card(this, p)
        receiveCard.addView(UiKit.sectionLabel(this, p, "تلقي تقرير"))
        receiveCard.addView(UiKit.button(this, p, "لصق حزمة تقرير من الحافظة").apply { setOnClickListener { pastePackage() } })
        receiveCard.addView(UiKit.subtitle(this, p, "يمكن إرسال حزمة التقرير من هاتف المحل عبر أي وسيلة مشاركة نصية. بعد وصولها انسخ النص ثم اضغط زر اللصق هنا."))
        root.addView(receiveCard)

        val inbox = UiKit.card(this, p)
        inbox.addView(UiKit.sectionLabel(this, p, "التقارير المستلمة"))
        val reports = receiver.receivedReports()
        if (reports.isEmpty()) inbox.addView(UiKit.subtitle(this, p, "لا توجد تقارير مستلمة بعد."))
        else reports.take(20).forEach { r ->
            val whenText = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(r.receivedAt))
            inbox.addView(UiKit.button(this, p, "${r.storeName} • ${r.periodLabel}\n$whenText", false).apply {
                setOnClickListener { showReport(r.transferId) }
            })
        }
        root.addView(inbox)

        root.addView(UiKit.card(this, p, 10).apply { addView(UiKit.button(this@ReportReceiverActivity, p, "رجوع", false).apply { setOnClickListener { finish() } }) })
        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
    }

    private fun scanRemoteGrant() {
        runCatching {
            startActivityForResult(Intent(this, QrScannerActivity::class.java).putExtra(QrScannerActivity.EXTRA_PROMPT, "امسح QR ربط هاتف المراقبة بالخادم"), REQUEST_REMOTE_GRANT)
        }.onFailure { info("QR", "تعذر فتح الماسح: ${it.message ?: "خطأ"}") }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_REMOTE_GRANT) return
        if (resultCode != RESULT_OK) { info("ربط الخادم", data?.getStringExtra(QrScannerActivity.EXTRA_ERROR) ?: "تم إلغاء المسح"); return }
        val raw = data?.getStringExtra(QrScannerActivity.EXTRA_RESULT).orEmpty()
        val grant = ReportProtocol.decodeRemoteGrant(raw, receiver.receiverId)
        if (grant == null) { info("QR غير صالح", "الرمز غير موجه لهذا الهاتف أو انتهت صلاحيته."); return }
        receiver.serverUrl = grant.serverUrl
        info("تم الربط ✓", "تم ربط هذا الهاتف بخادم ${grant.storeName}. يمكنك الآن استلام التقارير ومراقبة الحضور عن بُعد عبر الإنترنت.")
        buildUi()
    }

    private fun showRemoteReportPicker() {
        if (receiver.serverUrl.isBlank()) { editServerUrl(); return }
        val labels = arrayOf("تقرير اليوم", "آخر 7 أيام", "هذا الشهر")
        val periods = arrayOf("TODAY", "WEEK", "MONTH")
        AlertDialog.Builder(this).setTitle("تقرير مباشر من الخادم").setItems(labels) { _, which ->
            Thread {
                val r = CentralServerClient.remoteReport(receiver.serverUrl, receiver.receiverId, receiver.secret, periods[which])
                runOnUiThread { if (r.isSuccess) AlertDialog.Builder(this).setTitle(labels[which]).setMessage(r.getOrThrow()).setPositiveButton("إغلاق", null).show() else info("تعذر جلب التقرير", r.exceptionOrNull()?.message ?: "خطأ") }
            }.apply { isDaemon = true }.start()
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun editServerUrl() {
        val field = UiKit.field(this, p, "https://server.example.com").apply { setText(receiver.serverUrl) }
        val d = AlertDialog.Builder(this).setTitle("الخادم المركزي").setMessage("أدخل نفس رابط HTTPS المستخدم في جهاز المحل.").setView(field).setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = field.text.toString().trim()
                if (url.isNotBlank() && !url.startsWith("https://")) { field.error = "يجب استخدام HTTPS"; return@setOnClickListener }
                receiver.serverUrl = url; d.dismiss(); buildUi()
            }
        }
        d.show()
    }

    private fun refreshRemote(silent: Boolean = false) {
        if (receiver.serverUrl.isBlank()) { if (!silent) editServerUrl(); return }
        if (!silent) info("المراقبة عن بُعد", "جاري الاتصال بالخادم وجلب الحالة والتقارير الجديدة...")
        Thread {
            val dash = CentralServerClient.remoteDashboard(receiver.serverUrl, receiver.receiverId, receiver.secret)
            val inbox = CentralServerClient.receiverInbox(receiver.serverUrl, receiver.receiverId, receiver.secret)
            var receivedCount = 0
            if (inbox.isSuccess) {
                inbox.getOrThrow().forEach { remote ->
                    val item = receiver.receive(remote.packageText)
                    if (item != null) {
                        receivedCount++
                        CentralServerClient.confirmRemoteReport(receiver.serverUrl, receiver.receiverId, receiver.secret, item.transferId, item.confirmationCode)
                    }
                }
            }
            if (dash.isSuccess) {
                val d = dash.getOrThrow()
                receiver.remoteDashboardText = "${d.storeName} • ${d.branchId}\n${d.text}"
                receiver.remoteLastRefreshAt = System.currentTimeMillis()
            }
            runOnUiThread {
                if (!silent) {
                    if (dash.isFailure && inbox.isFailure) info("تعذر التحديث", dash.exceptionOrNull()?.message ?: inbox.exceptionOrNull()?.message ?: "خطأ")
                    else info("تم التحديث ✓", "تم تحديث المراقبة عن بُعد${if (receivedCount > 0) " واستلام $receivedCount تقرير جديد" else ""}.")
                }
                buildUi()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun rename() {
        val field = UiKit.field(this, p, "اسم الهاتف").apply { setText(receiver.receiverName) }
        val dialog = AlertDialog.Builder(this).setTitle("اسم هاتف الاستلام").setView(field).setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = field.text.toString().trim(); if (value.length < 2) { field.error = "أدخل اسمًا واضحًا"; return@setOnClickListener }
                receiver.receiverName = value; dialog.dismiss(); buildUi()
            }
        }
        dialog.show()
    }

    private fun showInviteQr() {
        val invite = receiver.newInvite()
        val content = ReportProtocol.encodeInvite(invite)
        val qr = runCatching { QrCodeTools.bitmap(content, 700) }.getOrElse { info("QR", "تعذر إنشاء QR: ${it.message ?: "خطأ"}"); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(20, 12, 20, 8) }
        box.addView(UiKit.subtitle(this, p, "من هاتف المحل: إدارة المحل ← هواتف استلام التقارير ← إضافة هاتف ← امسح هذا الرمز.\nالرمز صالح 10 دقائق.").apply { gravity = Gravity.CENTER })
        box.addView(ImageView(this).apply { setImageBitmap(qr); adjustViewBounds = true; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this@ReportReceiverActivity, 340)) })
        AlertDialog.Builder(this).setTitle("منح صلاحية لهذا الهاتف").setView(box).setPositiveButton("إغلاق", null).show()
    }

    private fun pastePackage() {
        val clip = getSystemService(ClipboardManager::class.java).primaryClip
        val raw = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (raw.isBlank()) { info("الحافظة", "لا توجد حزمة تقرير في الحافظة."); return }
        receive(raw, true)
    }

    private fun receive(raw: String, showResult: Boolean) {
        val item = receiver.receive(raw)
        if (item == null) {
            if (showResult) info("رفض التقرير", "الحزمة غير صالحة أو لم تُرسل إلى هذا الهاتف. إذا تغيرت صلاحية الهاتف، أعد منح الصلاحية ثم شارك تقريرًا جديدًا.")
            return
        }
        val message = "تم استلام تقرير ${item.storeName} بنجاح.\nرقم النقل: ${item.transferId}\nرمز تأكيد الاستلام: ${item.confirmationCode}\nأرسل رمز التأكيد إلى مالك العمل في هاتف المحل لإغلاق عملية النقل."
        if (showResult) AlertDialog.Builder(this).setTitle("✓ تم استلام التقرير").setMessage(message)
            .setPositiveButton("نسخ رمز التأكيد") { _, _ ->
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("ATTEND PRO confirmation", item.confirmationCode))
            }.setNegativeButton("إغلاق", null).show()
        buildUi()
    }

    private fun showReport(transferId: String) {
        val r = receiver.receivedReports().firstOrNull { it.transferId == transferId } ?: return
        AlertDialog.Builder(this).setTitle("${r.storeName} — ${r.periodLabel}")
            .setMessage("الفرع: ${r.branchId}\nرقم النقل: ${r.transferId}\nرمز التأكيد: ${r.confirmationCode}\n\n${r.reportText}")
            .setPositiveButton("نسخ رمز التأكيد") { _, _ -> getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("ATTEND PRO confirmation", r.confirmationCode)) }
            .setNegativeButton("إغلاق", null).show()
    }

    private fun info(title: String, message: String) { AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("حسنًا", null).show() }
    companion object { private const val REQUEST_REMOTE_GRANT = 7301 }

}
