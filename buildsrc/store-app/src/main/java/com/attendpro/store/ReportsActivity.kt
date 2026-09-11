package com.attendpro.store

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import com.attendpro.core.AppLanguage
import com.attendpro.core.AttendanceAction
import com.attendpro.core.AttendanceEvent
import com.attendpro.core.AuthorizedReportReceiver
import com.attendpro.core.CentralServerClient
import com.attendpro.core.DeviceIdentity
import com.attendpro.core.ReportProtocol
import com.attendpro.core.ShiftWindow
import com.attendpro.core.StoreRepository
import com.attendpro.core.UiKit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReportsActivity : Activity() {
    companion object { const val EXTRA_STORE_ADMIN_SESSION = "store_admin_session" }
    private lateinit var repo: StoreRepository
    private val p by lazy { UiKit.palette(this) }
    private fun t(ar: String, en: String) = AppLanguage.text(this, ar, en)
    private fun periodLabel(value: Period = period): String = when (value) {
        Period.TODAY -> t("اليوم", "Today")
        Period.WEEK -> t("آخر 7 أيام", "Last 7 days")
        Period.MONTH -> t("هذا الشهر", "This month")
        Period.ALL -> t("كل السجلات", "All records")
    }
    private var period = Period.TODAY
    private var authorized = false
    @Volatile private var syncInProgress = false

    private enum class Period(val title: String) {
        TODAY("اليوم"), WEEK("آخر 7 أيام"), MONTH("هذا الشهر"), ALL("كل السجلات")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = StoreRepository(this)
        if (!repo.isCentralActivationActive()) { showCentralActivationRequired(); return }
        period = savedInstanceState?.getString("period")?.let { runCatching { Period.valueOf(it) }.getOrNull() } ?: Period.TODAY
        val passedSession = repo.validateStoreAdminSession(intent?.getStringExtra(EXTRA_STORE_ADMIN_SESSION).orEmpty())
        if (repo.hasStoreAdminPin && !passedSession) { showOwnerGate(); return }
        authorized = true
        buildUi()
    }

    private fun showOwnerGate() {
        val pin = UiKit.field(this, p, t("رمز حماية إدارة المحل", "Store Management PIN"), true)
        val dialog = AlertDialog.Builder(this).setTitle(t("التقارير محمية", "Reports are protected")).setMessage(t("أدخل رمز مالك العمل لعرض التقارير والمشاركة.", "Enter the Store owner PIN to view and share reports."))
            .setView(pin).setPositiveButton(t("دخول", "Continue"), null).setNegativeButton(t("إلغاء", "Cancel")) { _, _ -> finish() }.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (repo.verifyStoreAdminPin(pin.text.toString())) { authorized = true; repo.issueStoreAdminSession(); dialog.dismiss(); buildUi() }
                else pin.error = if (repo.storeAdminLockUntil > System.currentTimeMillis()) t("تم القفل مؤقتًا", "Temporarily locked") else t("الرمز غير صحيح", "Incorrect PIN")
            }
        }
        dialog.setOnCancelListener { finish() }; dialog.show()
    }

    override fun onResume() {
        super.onResume()
        if (::repo.isInitialized && !repo.isCentralActivationActive()) { showCentralActivationRequired(); return }
        if (authorized) buildUi()
    }

    private fun showCentralActivationRequired() {
        window.statusBarColor = p.bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; layoutDirection = if (AppLanguage.isEnglish(this@ReportsActivity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@ReportsActivity, 16), UiKit.dp(this@ReportsActivity, 20), UiKit.dp(this@ReportsActivity, 16), UiKit.dp(this@ReportsActivity, 28)); setBackgroundColor(p.bg)
        }
        val card = UiKit.card(this, p)
        card.addView(UiKit.statusBadge(this, p, t("التفعيل المركزي مطلوب", "Central activation required"), false))
        card.addView(UiKit.title(this, p, t("التقارير التشغيلية موقوفة", "Operational reports are unavailable"), 23f).apply { gravity = Gravity.CENTER })
        card.addView(UiKit.subtitle(this, p, t("لا يمكن عرض أو مشاركة تقارير هذا المحل قبل وجود تفعيل مركزي صالح من إدارة النظام.", "Reports cannot be viewed or shared until this Store has valid central activation.")).apply { gravity = Gravity.CENTER })
        card.addView(UiKit.button(this, p, t("رجوع", "Back"), false).apply { setOnClickListener { finish() } })
        root.addView(card)
        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("period", period.name)
        super.onSaveInstanceState(outState)
    }

    private fun buildUi() {
        window.statusBarColor = p.bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = if (AppLanguage.isEnglish(this@ReportsActivity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@ReportsActivity, 16), UiKit.dp(this@ReportsActivity, 18), UiKit.dp(this@ReportsActivity, 16), UiKit.dp(this@ReportsActivity, 28))
            setBackgroundColor(p.bg)
        }

        val header = UiKit.heroCard(this, p)
        header.addView(UiKit.title(this, p, t("تقارير ATTEND PRO", "ATTEND PRO Reports"), 25f).apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.WHITE) })
        header.addView(UiKit.subtitle(this, p, "${repo.storeName} • ${periodLabel()} • ${t("الفرع", "Branch")} ${repo.branchId}").apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.argb(225,255,255,255)) })
        root.addView(header)

        val filter = UiKit.card(this, p, 12)
        filter.addView(UiKit.sectionLabel(this, p, t("الفترة", "Period")))
        filter.addView(UiKit.button(this, p, t("تقرير اليوم", "Today"), period == Period.TODAY).apply { setOnClickListener { period = Period.TODAY; buildUi() } })
        filter.addView(UiKit.button(this, p, t("آخر 7 أيام", "Last 7 days"), period == Period.WEEK).apply { setOnClickListener { period = Period.WEEK; buildUi() } })
        filter.addView(UiKit.button(this, p, t("هذا الشهر", "This month"), period == Period.MONTH).apply { setOnClickListener { period = Period.MONTH; buildUi() } })
        filter.addView(UiKit.button(this, p, t("كل السجلات", "All records"), period == Period.ALL).apply { setOnClickListener { period = Period.ALL; buildUi() } })
        root.addView(filter)

        val events = selectedEvents()
        val summary = summary(events)
        val stats = UiKit.card(this, p)
        stats.addView(UiKit.sectionLabel(this, p, t("الملخص", "Summary")))
        stats.addView(UiKit.title(this, p, t("${summary.uniqueEmployees} موظف • ${events.size} حركة", "${summary.uniqueEmployees} employees • ${events.size} events"), 20f))
        stats.addView(UiKit.subtitle(this, p, t("حضور ${summary.checkIns} • انصراف ${summary.checkOuts} • حالات تأخير ${summary.lateEmployees} • انصراف مبكر ${summary.earlyDepartures}", "Check-ins ${summary.checkIns} • check-outs ${summary.checkOuts} • late ${summary.lateEmployees} • early departures ${summary.earlyDepartures}")))
        root.addView(stats)

        val recent = UiKit.card(this, p)
        recent.addView(UiKit.sectionLabel(this, p, t("السجل", "Log")))
        recent.addView(UiKit.subtitle(this, p, reportPreview(events)))
        root.addView(recent)

        val share = UiKit.card(this, p)
        share.addView(UiKit.sectionLabel(this, p, t("المشاركة المؤكدة", "Verified sharing")))
        share.addView(UiKit.subtitle(this, p, t("يمكن لمالك العمل منح صلاحية لهواتف محددة. التقرير الموجه لهاتف مصرح يُشفّر بمفتاح ذلك الهاتف، ثم يظهر رمز تأكيد عند نجاح الاستلام.", "The Store owner can authorize specific phones. A report sent to an authorized phone is encrypted for that phone and confirmed after successful receipt.")))
        share.addView(UiKit.button(this, p, t("إرسال لهاتف مصرح له", "Send to authorized phone")).apply { setOnClickListener { chooseAuthorizedReceiver(events) } })
        share.addView(UiKit.button(this, p, t("تأكيد استلام مشاركة", "Confirm receipt"), false).apply { setOnClickListener { confirmReceiptDialog() } })
        share.addView(UiKit.button(this, p, t("مشاركة ملخص عادي", "Share summary"), false).apply { setOnClickListener { shareSummary(events) } })
        share.addView(UiKit.button(this, p, t("مشاركة CSV", "Share CSV"), false).apply { setOnClickListener { shareCsv(events) } })
        share.addView(UiKit.subtitle(this, p, transferStatusText()))
        root.addView(share)

        val remote = UiKit.card(this, p)
        remote.addView(UiKit.sectionLabel(this, p, t("المراقبة عن بُعد", "Remote monitoring")))
        remote.addView(UiKit.subtitle(this, p, remoteStatusText()))
        val auto = CheckBox(this).apply {
            text = t("المزامنة التلقائية عند توفر الخادم والإنترنت", "Automatic sync when server and Internet are available")
            setTextColor(p.text)
            isChecked = repo.reportAutoSync
            setOnCheckedChangeListener { _, checked -> repo.reportAutoSync = checked }
        }
        remote.addView(auto)
        remote.addView(UiKit.button(this, p, if (syncInProgress) t("جاري المزامنة...", "Syncing...") else t("مزامنة الآن", "Sync now"), false).apply {
            isEnabled = !syncInProgress
            setOnClickListener { syncNow() }
        })
        remote.addView(UiKit.subtitle(this, p, t("المشاركة تعمل فورًا. أما مشاهدة صاحب المحل للبيانات تلقائيًا من هاتف آخر فتحتاج خادم ATTEND PRO مركزيًا؛ عند ربط الخادم ستنتقل أحداث الحضور إليه تلقائيًا حسب هذا الخيار.", "Sharing works immediately. Automatic remote viewing from another phone requires the ATTEND PRO central server; attendance events sync automatically when enabled.")))
        root.addView(remote)

        val bottom = UiKit.card(this, p, 10)
        bottom.addView(UiKit.button(this, p, t("رجوع", "Back"), false).apply { setOnClickListener { finish() } })
        root.addView(bottom)

        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
    }

    private data class Summary(val uniqueEmployees: Int, val checkIns: Int, val checkOuts: Int, val lateEmployees: Int, val earlyDepartures: Int)

    private fun summary(events: List<AttendanceEvent>): Summary {
        val employees = events.map { it.employeeId }.toSet().size
        val ins = events.count { it.action == AttendanceAction.CHECK_IN }
        val outs = events.count { it.action == AttendanceAction.CHECK_OUT }
        val employeeMap = repo.employees().associateBy { it.employeeId.lowercase(Locale.getDefault()) }
        fun employeeDayKey(e: AttendanceEvent): String = e.employeeId.lowercase(Locale.getDefault()) + ":" + dayStart(e.timestampEpochMillis)
        val late = events.filter { it.action == AttendanceAction.CHECK_IN }.groupBy(::employeeDayKey).count { (_, list) ->
            val first = list.minByOrNull { it.timestampEpochMillis }
            val employee = first?.let { employeeMap[it.employeeId.lowercase(Locale.getDefault())] }
            first != null && employee != null && first.timestampEpochMillis > cutoffFor(first.timestampEpochMillis, employee)
        }
        val early = events.filter { it.action == AttendanceAction.CHECK_OUT }.groupBy(::employeeDayKey).count { (_, list) ->
            val last = list.maxByOrNull { it.timestampEpochMillis }
            val employee = last?.let { employeeMap[it.employeeId.lowercase(Locale.getDefault())] }
            last != null && employee != null && last.timestampEpochMillis < shiftEndFor(last.timestampEpochMillis, employee)
        }
        return Summary(employees, ins, outs, late, early)
    }

    private fun selectedEvents(): List<AttendanceEvent> {
        val all = repo.events()
        val start = when (period) {
            Period.TODAY -> dayStart(System.currentTimeMillis())
            Period.WEEK -> dayStart(System.currentTimeMillis()) - 6L * 24L * 60L * 60L * 1000L
            Period.MONTH -> Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            Period.ALL -> Long.MIN_VALUE
        }
        return all.filter { it.timestampEpochMillis >= start }
    }

    private fun reportPreview(events: List<AttendanceEvent>): String {
        if (events.isEmpty()) return t("لا توجد عمليات في الفترة المحددة.", "No events in the selected period.")
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val rows = events.takeLast(80).reversed().joinToString("\n") { e ->
            val action = if (e.action == AttendanceAction.CHECK_IN) t("حضور", "Check-in") else t("انصراف", "Check-out")
            "• ${e.employeeName.ifBlank { e.employeeId }} — $action — ${fmt.format(Date(e.timestampEpochMillis))} — ${methodAr(e.method.name)}${if (e.evidence.isNotBlank()) " — ${e.evidence}" else ""}"
        }
        val more = (events.size - 80).coerceAtLeast(0)
        return if (more > 0) t("$rows\n… و $more حركة أقدم", "$rows\n… and $more older events") else rows
    }

    private fun methodAr(name: String): String = when (name) {
        "PIN" -> "طريقة قديمة محفوظة"
        "PASSWORD" -> "كلمة مرور"
        "PATTERN" -> "طريقة قديمة محفوظة"
        "VOICE_PHRASE" -> "تحقق صوتي"
        "GPS" -> "تأكيد موقع قديم"
        "PHONE_BLE_BIOMETRIC" -> "هاتف + بصمة/وجه"
        "PHONE_FINGERPRINT" -> "بصمة إصبع الهاتف"
        "PHONE_PROXIMITY" -> "QR مباشر / سجل قديم لقرب الهاتف"
        "PHONE_BIOMETRIC" -> "اعتماد هاتف قديم"
        "EXTERNAL_FINGERPRINT" -> "قارئ بصمة"
        "SHARED_DEVICE_FACE" -> "وجه جهاز المحل"
        "SUPERVISOR_OVERRIDE" -> "بإشراف"
        "MANUAL_ADMIN" -> "إداري"
        else -> name
    }

    private fun chooseAuthorizedReceiver(events: List<AttendanceEvent>) {
        val receivers = repo.authorizedReportReceivers().filter { it.active }
        if (receivers.isEmpty()) {
            info(t("لا توجد هواتف مصرح لها", "No authorized phones"), t("من إدارة المحل ← هواتف استلام التقارير، أضف هاتف المراقبة بمسح QR الخاص به أولًا.", "From Store Management → report receiver phones, add the monitoring phone by scanning its QR first."))
            return
        }
        AlertDialog.Builder(this).setTitle(t("اختر هاتف الاستلام", "Choose receiver phone")).setItems(receivers.map { "${it.name} • ${it.receiverId}" }.toTypedArray()) { _, which ->
            shareToReceiver(events, receivers[which])
        }.setNegativeButton(t("إلغاء", "Cancel"), null).show()
    }

    private fun shareToReceiver(events: List<AttendanceEvent>, receiver: AuthorizedReportReceiver) {
        val (transfer, code) = repo.createReportTransfer(periodLabel(), receiver.receiverId, receiver.name)
        val s = summary(events)
        val reportText = buildString {
            append(t("المحل: ${repo.storeName}\nالفرع: ${repo.branchId}\nالفترة: ${period.title}\n", "Store: ${repo.storeName}\nBranch: ${repo.branchId}\nPeriod: ${periodLabel()}\n"))
            append(t("الموظفون: ${s.uniqueEmployees} • الحضور: ${s.checkIns} • الانصراف: ${s.checkOuts} • حالات التأخير: ${s.lateEmployees} • الانصراف المبكر: ${s.earlyDepartures}\n\n", "Employees: ${s.uniqueEmployees} • check-ins: ${s.checkIns} • check-outs: ${s.checkOuts} • late: ${s.lateEmployees} • early departures: ${s.earlyDepartures}\n\n"))
            append(reportPreview(events))
        }
        val pkg = ReportProtocol.ReportPackage(receiver.receiverId, transfer.transferId, repo.storeName, repo.branchId, periodLabel(), System.currentTimeMillis(), reportText, code)
        val encrypted = runCatching { ReportProtocol.encodePackage(pkg, receiver.secret) }.getOrElse { info(t("المشاركة", "Sharing"), t("تعذر تشفير التقرير: ${it.message ?: "خطأ"}", "Unable to encrypt report: ${it.message ?: "Error"}")); return }
        repo.markReportReceiverUsed(receiver.receiverId)

        if (repo.isCentralActivationActive() && repo.serverUrl.isNotBlank()) {
            info(t("إرسال عبر الإنترنت", "Sending online"), t("جاري رفع التقرير المشفر إلى هاتف «${receiver.name}» عبر الخادم المركزي. سيبقى محتوى التقرير مشفرًا للحزمة الموجهة لهذا الهاتف.", "Uploading the encrypted report to ${receiver.name} through the central server. Report content remains encrypted for that phone."))
            Thread {
                val r = CentralServerClient.pushReport(repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this), receiver.receiverId, transfer.transferId, encrypted, transfer.confirmationCodeHash)
                runOnUiThread {
                    if (r.isSuccess) {
                        info(t("تم الإرسال عن بُعد ✓", "Remote delivery complete ✓"), t("وصل التقرير إلى صندوق الهاتف المصرح له عبر الإنترنت. عند فتحه في «استلام التقارير» سيتم تأكيد الاستلام مركزيًا.\nرقم النقل: ${transfer.transferId}", "The report reached the authorized phone inbox online. Receipt will be confirmed centrally when opened.\nTransfer ID: ${transfer.transferId}"))
                    } else {
                        info(t("تعذر الإرسال عبر الخادم", "Server delivery failed"), t("${r.exceptionOrNull()?.message ?: "خطأ"}\n\nسيتم فتح المشاركة اليدوية كخيار احتياطي.", "${r.exceptionOrNull()?.message ?: "Error"}\n\nManual sharing will open as a fallback."))
                        shareEncryptedFallback(receiver, encrypted)
                    }
                    buildUi()
                }
            }.apply { isDaemon = true }.start()
            return
        }
        shareEncryptedFallback(receiver, encrypted)
        info(t("تم تجهيز النقل", "Transfer prepared"), t("لا يوجد تفعيل مركزي نشط، لذلك تم استخدام المشاركة اليدوية المشفرة.\nرقم النقل: ${transfer.transferId}", "Central activation is unavailable, so encrypted manual sharing was used.\nTransfer ID: ${transfer.transferId}"))
        buildUi()
    }

    private fun shareEncryptedFallback(receiver: AuthorizedReportReceiver, encrypted: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, t("ATTEND PRO - تقرير مشفر إلى ${receiver.name}", "ATTEND PRO - Encrypted report to ${receiver.name}"))
            putExtra(Intent.EXTRA_TEXT, encrypted)
        }, t("إرسال التقرير إلى ${receiver.name}", "Send report to ${receiver.name}")))
    }

    private fun confirmReceiptDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
        val id = UiKit.field(this, p, t("رقم النقل مثل RPT-XXXXXXXX", "Transfer ID such as RPT-XXXXXXXX"))
        val code = UiKit.field(this, p, t("رمز تأكيد الاستلام", "Receipt confirmation code"), true)
        box.addView(id); box.addView(code)
        val dialog = AlertDialog.Builder(this).setTitle(t("تأكيد استلام المشاركة", "Confirm shared report receipt")).setView(box).setPositiveButton(t("تأكيد", "Confirm"), null).setNegativeButton(t("إلغاء", "Cancel"), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (repo.confirmReportTransfer(id.text.toString(), code.text.toString())) {
                    dialog.dismiss(); info(t("تم التأكيد", "Confirmed"), t("تم تسجيل استلام التقرير بنجاح.", "Report receipt was recorded successfully.")); buildUi()
                } else code.error = t("رقم النقل أو رمز التأكيد غير صحيح", "Transfer ID or confirmation code is incorrect")
            }
        }
        dialog.show()
    }

    private fun transferStatusText(): String {
        val recent = repo.reportTransfers().take(5)
        if (recent.isEmpty()) return t("لا توجد مشاركات مؤكدة بعد.", "No verified shares yet.")
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        return recent.joinToString("\n") { r ->
            val state = if (r.status == "CONFIRMED") t("✓ تم الاستلام", "✓ Received") else t("بانتظار التأكيد", "Awaiting confirmation")
            "${r.transferId} • ${r.periodLabel}${if (r.receiverName.isNotBlank()) " • ${r.receiverName}" else ""} • $state • ${fmt.format(Date(r.createdAt))}"
        }
    }

    private fun shareSummary(events: List<AttendanceEvent>) {
        val s = summary(events)
        val text = buildString {
            append("ATTEND PRO — ${repo.storeName}\n")
            append(t("الفترة: ${period.title}\n", "Period: ${periodLabel()}\n"))
            append(t("الفرع: ${repo.branchId}\n", "Branch: ${repo.branchId}\n"))
            append(t("الموظفون في التقرير: ${s.uniqueEmployees}\n", "Employees in report: ${s.uniqueEmployees}\n"))
            append(t("الحضور: ${s.checkIns}\n", "Check-ins: ${s.checkIns}\n"))
            append(t("الانصراف: ${s.checkOuts}\n", "Check-outs: ${s.checkOuts}\n"))
            append(t("حالات التأخير: ${s.lateEmployees}\n", "Late employees: ${s.lateEmployees}\n"))
            append(t("الانصراف المبكر: ${s.earlyDepartures}\n", "Early departures: ${s.earlyDepartures}\n"))
            append(t("إجمالي الحركات: ${events.size}\n", "Total events: ${events.size}\n"))
            append(t("أُنشئ في: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}", "Generated at: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}"))
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ATTEND PRO - ${periodLabel()}")
            putExtra(Intent.EXTRA_TEXT, text)
        }, t("مشاركة التقرير", "Share report")))
    }

    private fun shareCsv(events: List<AttendanceEvent>) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val csv = buildString {
            append("employee_id,employee_name,branch,time,action,method,evidence,verified,synced\n")
            events.forEach { e ->
                append(csvCell(e.employeeId)); append(',')
                append(csvCell(e.employeeName)); append(',')
                append(csvCell(e.branchId)); append(',')
                append(csvCell(fmt.format(Date(e.timestampEpochMillis)))); append(',')
                append(e.action.name); append(','); append(e.method.name); append(','); append(csvCell(e.evidence)); append(','); append(e.verified); append(','); append(e.synced); append('\n')
            }
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "ATTEND PRO - ${repo.storeName} - ${period.title}")
            putExtra(Intent.EXTRA_TEXT, csv)
        }, t("مشاركة CSV", "Share CSV")))
    }

    private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun remoteStatusText(): String {
        val last = if (repo.lastSyncAt > 0L) SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(repo.lastSyncAt)) else t("لم تتم بعد", "Not yet")
        val server = if (repo.serverUrl.isBlank()) t("غير مربوط", "Not linked") else t("HTTPS مضبوط", "HTTPS configured")
        val license = if (repo.isCentralActivationActive()) t("تفعيل مركزي نشط", "Central activation active") else t("التفعيل المركزي غير صالح", "Central activation invalid")
        val receivers = repo.authorizedReportReceivers().count { it.active }
        return t("الخادم: $server • $license • هواتف المراقبة: $receivers\nعمليات تنتظر المزامنة: ${repo.pendingEvents().size} • آخر مزامنة: $last${if (repo.lastSyncMessage.isNotBlank()) "\n${repo.lastSyncMessage}" else ""}", "Server: $server • $license • monitoring phones: $receivers\nPending sync events: ${repo.pendingEvents().size} • last sync: $last${if (repo.lastSyncMessage.isNotBlank()) "\n${repo.lastSyncMessage}" else ""}")
    }

    private fun syncNow() {
        if (syncInProgress) return
        if (repo.serverUrl.isBlank()) {
            info(t("المزامنة", "Sync"), t("لم يتم ربط خادم ATTEND PRO المركزي بعد. يمكنك استخدام مشاركة التقرير الآن، والمراقبة التلقائية ستعمل بعد ربط الخادم.", "The ATTEND PRO central server is not linked yet. Report sharing works now; automatic monitoring will work after server linking."))
            return
        }
        if (!repo.isActivationActive()) {
            info(t("المزامنة", "Sync"), t("فعّل جهاز المحل أولًا قبل المزامنة المركزية.", "Activate the Store device before central synchronization."))
            return
        }
        val pending = repo.pendingEvents()
        syncInProgress = true
        buildUi()
        Thread {
            val result = CentralServerClient.syncEventsCentral(repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this), pending.take(500))
            if (repo.isCentralActivationActive()) {
                CentralServerClient.confirmedTransfers(repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this)).getOrNull()?.forEach { repo.confirmReportTransferTrusted(it) }
            }
            runOnUiThread {
                syncInProgress = false
                if (result.isSuccess) {
                    repo.markSynced(result.getOrThrow())
                    repo.lastSyncAt = System.currentTimeMillis()
                    repo.lastSyncMessage = t("تمت المزامنة بنجاح", "Sync completed successfully")
                    info(t("تمت المزامنة", "Sync complete"), if (result.getOrThrow().isEmpty()) t("تم فحص الخادم ولا توجد حركات جديدة.", "Server checked; there are no new events.") else t("تم إرسال ${result.getOrThrow().size} حركة إلى الخادم.", "${result.getOrThrow().size} events were sent to the server."))
                } else {
                    repo.lastSyncMessage = t("فشلت المزامنة: ${result.exceptionOrNull()?.message ?: "خطأ"}", "Sync failed: ${result.exceptionOrNull()?.message ?: "Error"}")
                    info(t("تعذر المزامنة", "Unable to sync"), repo.lastSyncMessage)
                }
                buildUi()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun dayStart(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun shiftWindow1980(time: Long, employee: com.attendpro.core.PairedEmployee): ShiftWindow.Window = ShiftWindow.resolve(
        time,
        if (employee.useCustomShift) employee.shiftStartHour else repo.shiftHour,
        if (employee.useCustomShift) employee.shiftStartMinute else repo.shiftMinute,
        if (employee.useCustomShift) employee.shiftEndHour else repo.shiftEndHour,
        if (employee.useCustomShift) employee.shiftEndMinute else repo.shiftEndMinute
    )

    private fun cutoffFor(time: Long, employee: com.attendpro.core.PairedEmployee): Long =
        shiftWindow1980(time, employee).start + repo.graceMinutes * 60_000L

    private fun shiftEndFor(time: Long, employee: com.attendpro.core.PairedEmployee): Long =
        shiftWindow1980(time, employee).end

    private fun info(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton(t("حسنًا", "OK"), null).show()
    }
}
