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
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import com.attendpro.core.AppLanguage
import com.attendpro.core.CentralServerClient
import com.attendpro.core.QrCodeTools
import com.attendpro.core.QrScannerActivity
import com.attendpro.core.ReportProtocol
import com.attendpro.core.ReportReceiverStore
import com.attendpro.core.ShiftTimeCodec
import com.attendpro.core.UiKit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportReceiverActivity : Activity() {
    private lateinit var receiver: ReportReceiverStore
    private val p by lazy { UiKit.palette(this) }
    private fun t(ar: String, en: String) = AppLanguage.text(this, ar, en)

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
        val dir = if (AppLanguage.isEnglish(this)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = dir
            setPadding(UiKit.dp(this@ReportReceiverActivity, 12), UiKit.dp(this@ReportReceiverActivity, 12), UiKit.dp(this@ReportReceiverActivity, 12), UiKit.dp(this@ReportReceiverActivity, 24))
            setBackgroundColor(p.bg)
        }

        val header = UiKit.heroCard(this, p, 10)
        header.addView(UiKit.title(this, p, t("هاتف الإدارة والاستلام", "Management & Receiver Phone"), 23f).apply {
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
        })
        header.addView(UiKit.subtitle(this, p, t(
            "هاتف مراقبة بصلاحيات يحددها جهاز المحل المعتمد",
            "A monitoring phone with permissions controlled by the authorized Store device"
        )).apply {
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.argb(225,255,255,255))
        })
        root.addView(header)

        val identity = UiKit.card(this, p, 9)
        identity.addView(UiKit.sectionLabel(this, p, t("هوية الهاتف والصلاحيات", "Phone identity and permissions")))
        identity.addView(UiKit.subtitle(this, p, t(
            "الاسم: ${receiver.receiverName}\nالمعرف: ${receiver.receiverId}",
            "Name: ${receiver.receiverName}\nID: ${receiver.receiverId}"
        )))
        val permText = listOf(
            "${if (receiver.canReceiveReports) "✓" else "○"} ${t("استلام التقارير", "Receive reports")}",
            "${if (receiver.canMessageEmployees) "✓" else "○"} ${t("مراسلة الموظفين", "Message employees")}",
            "${if (receiver.canManageStore) "✓" else "○"} ${t("إعدادات مدير المحل", "Store Manager settings")}"
        ).joinToString("\n")
        identity.addView(UiKit.subtitle(this, p, permText))
        identity.addView(UiKit.button(this, p, t("تغيير اسم هذا الهاتف", "Rename this phone"), false).apply { setOnClickListener { rename() } })
        identity.addView(UiKit.button(this, p, t("عرض QR منح الصلاحية", "Show permission QR")).apply { setOnClickListener { showInviteQr() } })
        identity.addView(UiKit.button(this, p, t("مسح QR ربط الخادم من جهاز المحل", "Scan server-link QR from Store device"), false).apply { setOnClickListener { scanRemoteGrant() } })
        root.addView(identity)

        if (receiver.serverUrl.isBlank()) {
            root.addView(UiKit.card(this, p, 9).apply {
                addView(UiKit.sectionLabel(this@ReportReceiverActivity, p, t("الربط بالخادم", "Server connection")))
                addView(UiKit.subtitle(this@ReportReceiverActivity, p, t("لم يتم ربط هذا الهاتف بالخادم بعد.", "This phone is not linked to the server yet.")))
                addView(UiKit.button(this@ReportReceiverActivity, p, t("إدخال رابط الخادم", "Enter server URL"), false).apply { setOnClickListener { editServerUrl() } })
            })
        } else {
            val remote = UiKit.card(this, p, 9)
            remote.addView(UiKit.sectionLabel(this, p, t("الحالة عن بُعد", "Remote status")))
            remote.addView(UiKit.subtitle(this, p, t(
                "الخادم: ${receiver.serverUrl}\n${receiver.remoteDashboardText.ifBlank { "اضغط تحديث لجلب الحالة." }}",
                "Server: ${receiver.serverUrl}\n${receiver.remoteDashboardText.ifBlank { "Tap refresh to load status." }}"
            )))
            remote.addView(UiKit.button(this, p, t("تحديث الصلاحيات والحالة", "Refresh permissions and status")).apply { setOnClickListener { refreshRemote() } })
            root.addView(remote)
        }

        if (receiver.canReceiveReports) {
            val reports = UiKit.card(this, p, 9)
            reports.addView(UiKit.sectionLabel(this, p, t("استلام التقارير", "Reports")))
            reports.addView(UiKit.button(this, p, t("عرض تقرير مباشر من الخادم", "Open live server report"), false).apply { setOnClickListener { showRemoteReportPicker() } })
            reports.addView(UiKit.button(this, p, t("لصق حزمة تقرير من الحافظة", "Paste encrypted report package"), false).apply { setOnClickListener { pastePackage() } })
            val stored = receiver.receivedReports()
            reports.addView(UiKit.subtitle(this, p, if (stored.isEmpty()) t("لا توجد تقارير مستلمة بعد.", "No received reports yet.")
                else t("التقارير المحفوظة: ${stored.size}", "Saved reports: ${stored.size}")))
            stored.take(8).forEach { item ->
                val whenText = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(item.receivedAt))
                reports.addView(UiKit.button(this, p, "${item.storeName} • ${item.periodLabel}\n$whenText", false).apply {
                    setOnClickListener { showReport(item.transferId) }
                })
            }
            root.addView(reports)
        }

        if (receiver.canMessageEmployees) {
            val messages = UiKit.card(this, p, 9)
            messages.addView(UiKit.sectionLabel(this, p, t("مراسلة الموظفين", "Employee messaging")))
            messages.addView(UiKit.subtitle(this, p, t(
                "هذه الصلاحية مستقلة عن التقارير. الرسائل تمر عبر الخادم إلى تطبيق الموظف.",
                "This permission is independent from reports. Messages are delivered through the server to the Employee app."
            )))
            messages.addView(UiKit.button(this, p, t("إرسال رسالة لموظف", "Send message to employee")).apply { setOnClickListener { chooseRemoteEmployee() } })
            messages.addView(UiKit.button(this, p, t("عرض ردود الموظفين", "View employee replies"), false).apply { setOnClickListener { showReceiverReplies() } })
            root.addView(messages)
        }

        if (receiver.canManageStore) {
            val management = UiKit.card(this, p, 9)
            management.addView(UiKit.sectionLabel(this, p, t("إعدادات مدير المحل", "Store Manager settings")))
            management.addView(UiKit.subtitle(this, p, t(
                "يمكن تعديل الدوام والسماح وبعض إعدادات الصوت والمزامنة. يصل التعديل إلى هاتف المحل عبر الخادم ويطبّقه التطبيق المعتمد.",
                "You can adjust shifts, grace period, selected voice settings and sync. Changes are delivered through the server and applied by the authorized Store app."
            )))
            management.addView(UiKit.button(this, p, t("فتح إعدادات المحل عن بُعد", "Open remote Store settings")).apply { setOnClickListener { editRemoteStoreSettings() } })
            root.addView(management)
        }

        root.addView(UiKit.card(this, p, 6).apply {
            addView(UiKit.button(this@ReportReceiverActivity, p, t("رجوع", "Back"), false).apply { setOnClickListener { finish() } })
        })
        setContentView(ScrollView(this).apply { isFillViewport = true; setBackgroundColor(p.bg); addView(root) })
    }

    private fun scanRemoteGrant() {
        runCatching {
            startActivityForResult(
                Intent(this, QrScannerActivity::class.java)
                    .putExtra(QrScannerActivity.EXTRA_PROMPT, t("امسح QR ربط هاتف الإدارة بالخادم", "Scan the server-link QR")),
                REQUEST_REMOTE_GRANT
            )
        }.onFailure { info("QR", t("تعذر فتح الماسح: ${it.message ?: "خطأ"}", "Unable to open scanner: ${it.message ?: "Error"}")) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_REMOTE_GRANT) return
        if (resultCode != RESULT_OK) {
            info(t("ربط الخادم", "Server link"), data?.getStringExtra(QrScannerActivity.EXTRA_ERROR) ?: t("تم إلغاء المسح", "Scan cancelled"))
            return
        }
        val raw = data?.getStringExtra(QrScannerActivity.EXTRA_RESULT).orEmpty()
        val grant = ReportProtocol.decodeRemoteGrant(raw, receiver.receiverId)
        if (grant == null) {
            info(t("QR غير صالح", "Invalid QR"), t("الرمز غير موجه لهذا الهاتف أو انتهت صلاحيته.", "This QR is not for this phone or has expired."))
            return
        }
        receiver.serverUrl = grant.serverUrl
        info(t("تم الربط ✓", "Linked ✓"), t("تم ربط هذا الهاتف بخادم ${grant.storeName}.", "This phone is linked to the ${grant.storeName} server."))
        refreshRemote(silent = true)
    }

    private fun refreshRemote(silent: Boolean = false) {
        if (receiver.serverUrl.isBlank()) { if (!silent) editServerUrl(); return }
        if (!silent) info(t("تحديث", "Refresh"), t("جاري جلب الصلاحيات والحالة من الخادم…", "Loading permissions and status from the server…"))
        Thread {
            val capabilities = CentralServerClient.receiverCapabilities(receiver.serverUrl, receiver.receiverId, receiver.secret)
            if (capabilities.isSuccess) {
                val c = capabilities.getOrThrow()
                receiver.canReceiveReports = c.canReceiveReports
                receiver.canMessageEmployees = c.canMessageEmployees
                receiver.canManageStore = c.canManageStore
                receiver.capabilityStoreName = c.storeName
                receiver.capabilityBranchId = c.branchId
            }
            var receivedCount = 0
            var dashboard: Result<CentralServerClient.RemoteDashboard>? = null
            if (receiver.canReceiveReports) {
                dashboard = CentralServerClient.remoteDashboard(receiver.serverUrl, receiver.receiverId, receiver.secret)
                val inbox = CentralServerClient.receiverInbox(receiver.serverUrl, receiver.receiverId, receiver.secret)
                if (inbox.isSuccess) {
                    inbox.getOrThrow().forEach { remote ->
                        val item = receiver.receive(remote.packageText)
                        if (item != null) {
                            receivedCount++
                            CentralServerClient.confirmRemoteReport(receiver.serverUrl, receiver.receiverId, receiver.secret, item.transferId, item.confirmationCode)
                        }
                    }
                }
                dashboard.getOrNull()?.let { d ->
                    receiver.remoteDashboardText = "${d.storeName} • ${d.branchId}\n${d.text}"
                    receiver.remoteLastRefreshAt = System.currentTimeMillis()
                }
            }
            runOnUiThread {
                if (!silent) {
                    when {
                        capabilities.isFailure -> info(t("تعذر التحديث", "Refresh failed"), capabilities.exceptionOrNull()?.message ?: t("خطأ", "Error"))
                        dashboard?.isFailure == true && receiver.canReceiveReports -> info(t("تم تحديث الصلاحيات", "Permissions refreshed"), t("تم تحديث الصلاحيات، لكن تعذر جلب لوحة التقارير.", "Permissions refreshed, but the report dashboard could not be loaded."))
                        else -> info(t("تم التحديث ✓", "Updated ✓"), t(
                            "تم تحديث الصلاحيات والحالة${if (receivedCount > 0) " واستلام $receivedCount تقرير جديد" else ""}.",
                            "Permissions and status updated${if (receivedCount > 0) " and $receivedCount new reports received" else ""}."
                        ))
                    }
                }
                buildUi()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun chooseRemoteEmployee() {
        if (receiver.serverUrl.isBlank()) { editServerUrl(); return }
        Thread {
            val result = CentralServerClient.receiverEmployees(receiver.serverUrl, receiver.receiverId, receiver.secret)
            runOnUiThread {
                if (result.isFailure) { info(t("تعذر جلب الموظفين", "Unable to load employees"), result.exceptionOrNull()?.message ?: t("خطأ", "Error")); return@runOnUiThread }
                val employees = result.getOrThrow()
                if (employees.isEmpty()) { info(t("الموظفون", "Employees"), t("لا يوجد موظفون مرتبطون بالخادم.", "No employees are linked on the server.")); return@runOnUiThread }
                val labels = employees.map { "${it.employeeName} • ${it.employeeId}" }.toTypedArray()
                AlertDialog.Builder(this).setTitle(t("اختر الموظف", "Choose employee")).setItems(labels) { _, which ->
                    composeRemoteEmployeeMessage(employees[which])
                }.setNegativeButton(t("إلغاء", "Cancel"), null).show()
            }
        }.start()
    }

    private fun composeRemoteEmployeeMessage(employee: CentralServerClient.ReceiverEmployee) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
        }
        val title = UiKit.field(this, p, t("عنوان الرسالة", "Message title")).apply {
            setText(t("رسالة من هاتف الإدارة", "Message from management phone"))
        }
        val body = UiKit.field(this, p, t("اكتب الرسالة", "Write the message")).apply { minLines = 4 }
        val priority = Spinner(this).apply {
            adapter = ArrayAdapter(this@ReportReceiverActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf(t("عادية", "Normal"), t("مهمة", "Important"), t("عاجلة", "Urgent")))
        }
        val voice = CheckBox(this).apply { text = t("تنبيه صوتي في هاتف الموظف", "Voice alert on Employee phone") }
        box.addView(title); box.addView(body); box.addView(priority); box.addView(voice)
        val dialog = AlertDialog.Builder(this)
            .setTitle(t("رسالة إلى ${employee.employeeName}", "Message to ${employee.employeeName}"))
            .setView(box).setPositiveButton(t("إرسال", "Send"), null).setNegativeButton(t("إلغاء", "Cancel"), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val message = body.text.toString().trim()
                if (message.isBlank()) { body.error = t("اكتب الرسالة", "Write a message"); return@setOnClickListener }
                val pr = when (priority.selectedItemPosition) { 2 -> "URGENT"; 1 -> "IMPORTANT"; else -> "NORMAL" }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                Thread {
                    val result = CentralServerClient.receiverSendEmployeeMessage(
                        receiver.serverUrl, receiver.receiverId, receiver.secret, employee.employeeId,
                        title.text.toString(), message, pr, voice.isChecked
                    )
                    runOnUiThread {
                        if (result.isSuccess) { dialog.dismiss(); info(t("تم الإرسال ✓", "Sent ✓"), t("تم إرسال الرسالة عبر الخادم.", "Message sent through the server.")) }
                        else { body.error = result.exceptionOrNull()?.message ?: t("تعذر الإرسال", "Unable to send"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true }
                    }
                }.start()
            }
        }
        dialog.show()
    }

    private fun showReceiverReplies() {
        Thread {
            val result = CentralServerClient.receiverMessagesInbox(receiver.serverUrl, receiver.receiverId, receiver.secret)
            runOnUiThread {
                if (result.isFailure) { info(t("الردود", "Replies"), result.exceptionOrNull()?.message ?: t("تعذر جلب الردود", "Unable to load replies")); return@runOnUiThread }
                val items = result.getOrThrow()
                if (items.isEmpty()) { info(t("الردود", "Replies"), t("لا توجد ردود من الموظفين حاليًا.", "No employee replies right now.")); return@runOnUiThread }
                val text = items.take(50).joinToString("\n\n") { m ->
                    "• ${m.employeeId}\n${m.body}\n${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(m.createdAt))}"
                }
                AlertDialog.Builder(this).setTitle(t("ردود الموظفين", "Employee replies")).setMessage(text).setPositiveButton(t("إغلاق", "Close"), null).show()
            }
        }.start()
    }

    private fun editRemoteStoreSettings() {
        Thread {
            val result = CentralServerClient.receiverStoreSettings(receiver.serverUrl, receiver.receiverId, receiver.secret)
            runOnUiThread {
                if (result.isFailure) { info(t("إعدادات المحل", "Store settings"), result.exceptionOrNull()?.message ?: t("تعذر جلب الإعدادات", "Unable to load settings")); return@runOnUiThread }
                showRemoteSettingsDialog(result.getOrThrow().settings)
            }
        }.start()
    }

    private fun showRemoteSettingsDialog(current: CentralServerClient.RemoteStoreSettings) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0) }
        val start = UiKit.field(this, p, t("بداية الدوام", "Shift start")).apply {
            isFocusable = false; isClickable = true; FormPickerHelper.setTime(this, current.shiftStartHour, current.shiftStartMinute)
            setOnClickListener { FormPickerHelper.pickTime(this@ReportReceiverActivity, this, current.shiftStartHour, current.shiftStartMinute) }
        }
        val end = UiKit.field(this, p, t("نهاية الدوام", "Shift end")).apply {
            isFocusable = false; isClickable = true; FormPickerHelper.setTime(this, current.shiftEndHour, current.shiftEndMinute)
            setOnClickListener { FormPickerHelper.pickTime(this@ReportReceiverActivity, this, current.shiftEndHour, current.shiftEndMinute) }
        }
        val grace = UiKit.field(this, p, t("دقائق السماح 0–120", "Grace minutes 0–120"), true).apply { setText(current.graceMinutes.toString()) }
        val storeVoice = CheckBox(this).apply { text = t("نطق جهاز المحل", "Store voice announcements"); isChecked = current.attendanceVoiceAnnouncementEnabled }
        val employeeVoice = CheckBox(this).apply { text = t("التنبيهات الصوتية للموظف", "Employee voice prompts"); isChecked = current.employeeVoicePromptsEnabled }
        val geo = CheckBox(this).apply { text = t("تنبيهات الوصول الجغرافي", "Geofence arrival alerts"); isChecked = current.geoArrivalAlertsEnabled }
        val sync = CheckBox(this).apply { text = t("المزامنة التلقائية", "Automatic synchronization"); isChecked = current.reportAutoSync }
        listOf(start,end,grace,storeVoice,employeeVoice,geo,sync).forEach { box.addView(it) }

        val dialog = AlertDialog.Builder(this)
            .setTitle(t("إعدادات مدير المحل عن بُعد", "Remote Store Manager settings"))
            .setView(box).setPositiveButton(t("إرسال التعديل", "Send changes"), null)
            .setNegativeButton(t("إلغاء", "Cancel"), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val a = FormPickerHelper.selectedTime(start, current.shiftStartHour, current.shiftStartMinute)
                val b = FormPickerHelper.selectedTime(end, current.shiftEndHour, current.shiftEndMinute)
                val g = grace.text.toString().toIntOrNull()
                if (a == b) { end.error = t("يجب أن يختلف وقت النهاية عن البداية", "End time must differ from start time"); return@setOnClickListener }
                if (g == null || g !in 0..120) { grace.error = t("اختر من 0 إلى 120", "Choose 0 to 120"); return@setOnClickListener }
                val settings = CentralServerClient.RemoteStoreSettings(
                    a.hour24, a.minute, b.hour24, b.minute, g,
                    storeVoice.isChecked, employeeVoice.isChecked, geo.isChecked, sync.isChecked
                )
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                Thread {
                    val result = CentralServerClient.receiverUpdateStoreSettings(receiver.serverUrl, receiver.receiverId, receiver.secret, settings)
                    runOnUiThread {
                        if (result.isSuccess) {
                            dialog.dismiss()
                            info(t("تم إرسال التعديل ✓", "Changes sent ✓"), t(
                                "سيطبق هاتف المحل الإعدادات عند اتصاله بالخادم. رقم المراجعة: ${result.getOrThrow()}",
                                "The Store device will apply the settings when it connects to the server. Revision: ${result.getOrThrow()}"
                            ))
                        } else {
                            grace.error = result.exceptionOrNull()?.message ?: t("تعذر إرسال التعديل", "Unable to send changes")
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        }
                    }
                }.start()
            }
        }
        dialog.show()
    }

    private fun showRemoteReportPicker() {
        if (receiver.serverUrl.isBlank()) { editServerUrl(); return }
        val labels = arrayOf(t("تقرير اليوم", "Today"), t("آخر 7 أيام", "Last 7 days"), t("هذا الشهر", "This month"))
        val periods = arrayOf("TODAY", "WEEK", "MONTH")
        AlertDialog.Builder(this).setTitle(t("تقرير مباشر من الخادم", "Live server report")).setItems(labels) { _, which ->
            Thread {
                val result = CentralServerClient.remoteReport(receiver.serverUrl, receiver.receiverId, receiver.secret, periods[which])
                runOnUiThread {
                    if (result.isSuccess) AlertDialog.Builder(this).setTitle(labels[which]).setMessage(result.getOrThrow()).setPositiveButton(t("إغلاق", "Close"), null).show()
                    else info(t("تعذر جلب التقرير", "Unable to load report"), result.exceptionOrNull()?.message ?: t("خطأ", "Error"))
                }
            }.start()
        }.setNegativeButton(t("إلغاء", "Cancel"), null).show()
    }

    private fun editServerUrl() {
        val field = UiKit.field(this, p, "https://server.example.com").apply { setText(receiver.serverUrl) }
        val dialog = AlertDialog.Builder(this).setTitle(t("الخادم المركزي", "Central server"))
            .setMessage(t("أدخل نفس رابط HTTPS المستخدم في جهاز المحل.", "Enter the same HTTPS URL used by the Store device."))
            .setView(field).setPositiveButton(t("حفظ", "Save"), null).setNegativeButton(t("إلغاء", "Cancel"), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = field.text.toString().trim()
                if (url.isNotBlank() && !url.startsWith("https://")) { field.error = t("يجب استخدام HTTPS", "HTTPS is required"); return@setOnClickListener }
                receiver.serverUrl = url
                dialog.dismiss()
                refreshRemote(silent = true)
            }
        }
        dialog.show()
    }

    private fun rename() {
        val field = UiKit.field(this, p, t("اسم الهاتف", "Phone name")).apply { setText(receiver.receiverName) }
        val dialog = AlertDialog.Builder(this).setTitle(t("اسم هاتف الاستلام", "Receiver phone name")).setView(field)
            .setPositiveButton(t("حفظ", "Save"), null).setNegativeButton(t("إلغاء", "Cancel"), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = field.text.toString().trim()
                if (value.length < 2) { field.error = t("أدخل اسمًا واضحًا", "Enter a clear name"); return@setOnClickListener }
                receiver.receiverName = value
                dialog.dismiss()
                buildUi()
            }
        }
        dialog.show()
    }

    private fun showInviteQr() {
        val invite = receiver.newInvite()
        val raw = ReportProtocol.encodeInvite(invite)
        val qr = runCatching { QrCodeTools.bitmap(raw, 700) }.getOrElse {
            info("QR", t("تعذر إنشاء QR: ${it.message ?: "خطأ"}", "Unable to create QR: ${it.message ?: "Error"}"))
            return
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(20, 12, 20, 8) }
        box.addView(UiKit.subtitle(this, p, t(
            "من هاتف المحل: إدارة المحل ← هواتف الاستلام والصلاحيات ← إضافة هاتف ← امسح هذا الرمز. الرمز صالح 10 دقائق.",
            "On the Store phone: Store Management → Receiver phones and permissions → Add phone → scan this QR. The QR is valid for 10 minutes."
        )).apply { gravity = Gravity.CENTER })
        box.addView(ImageView(this).apply {
            setImageBitmap(qr); adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this@ReportReceiverActivity, 330))
        })
        AlertDialog.Builder(this).setTitle(t("منح صلاحية لهذا الهاتف", "Authorize this phone")).setView(box).setPositiveButton(t("إغلاق", "Close"), null).show()
    }

    private fun pastePackage() {
        val clip = getSystemService(ClipboardManager::class.java).primaryClip
        val raw = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (raw.isBlank()) { info(t("الحافظة", "Clipboard"), t("لا توجد حزمة تقرير في الحافظة.", "There is no report package in the clipboard.")); return }
        receive(raw, true)
    }

    private fun receive(raw: String, showResult: Boolean) {
        val item = receiver.receive(raw)
        if (item == null) {
            if (showResult) info(t("رفض التقرير", "Report rejected"), t(
                "الحزمة غير صالحة أو لم تُرسل إلى هذا الهاتف. إذا تغيرت الصلاحية، أعد منحها ثم شارك تقريرًا جديدًا.",
                "The package is invalid or was not sent to this phone. If permissions changed, authorize the phone again and share a new report."
            ))
            return
        }
        if (showResult) {
            val message = t(
                "تم استلام تقرير ${item.storeName}.\nرقم النقل: ${item.transferId}\nرمز التأكيد: ${item.confirmationCode}",
                "Report received from ${item.storeName}.\nTransfer ID: ${item.transferId}\nConfirmation code: ${item.confirmationCode}"
            )
            AlertDialog.Builder(this).setTitle(t("✓ تم استلام التقرير", "✓ Report received")).setMessage(message)
                .setPositiveButton(t("نسخ رمز التأكيد", "Copy confirmation code")) { _, _ ->
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("ATTEND PRO confirmation", item.confirmationCode))
                }.setNegativeButton(t("إغلاق", "Close"), null).show()
        }
        buildUi()
    }

    private fun showReport(transferId: String) {
        val item = receiver.receivedReports().firstOrNull { it.transferId == transferId } ?: return
        AlertDialog.Builder(this).setTitle("${item.storeName} — ${item.periodLabel}")
            .setMessage(t(
                "الفرع: ${item.branchId}\nرقم النقل: ${item.transferId}\nرمز التأكيد: ${item.confirmationCode}\n\n${item.reportText}",
                "Branch: ${item.branchId}\nTransfer ID: ${item.transferId}\nConfirmation: ${item.confirmationCode}\n\n${item.reportText}"
            ))
            .setPositiveButton(t("نسخ رمز التأكيد", "Copy confirmation code")) { _, _ ->
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("ATTEND PRO confirmation", item.confirmationCode))
            }
            .setNegativeButton(t("إغلاق", "Close"), null).show()
    }

    private fun info(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton(t("حسنًا", "OK"), null).show()
    }

    companion object { private const val REQUEST_REMOTE_GRANT = 7301 }
}
