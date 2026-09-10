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
        val pin = UiKit.field(this, p, "رمز حماية إدارة المحل", true)
        val dialog = AlertDialog.Builder(this).setTitle("التقارير محمية").setMessage("أدخل رمز مالك العمل لعرض التقارير والمشاركة.")
            .setView(pin).setPositiveButton("دخول", null).setNegativeButton("إلغاء") { _, _ -> finish() }.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (repo.verifyStoreAdminPin(pin.text.toString())) { authorized = true; repo.issueStoreAdminSession(); dialog.dismiss(); buildUi() }
                else pin.error = if (repo.storeAdminLockUntil > System.currentTimeMillis()) "تم القفل مؤقتًا" else "الرمز غير صحيح"
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
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@ReportsActivity, 16), UiKit.dp(this@ReportsActivity, 20), UiKit.dp(this@ReportsActivity, 16), UiKit.dp(this@ReportsActivity, 28)); setBackgroundColor(p.bg)
        }
        val card = UiKit.card(this, p)
        card.addView(UiKit.statusBadge(this, p, "التفعيل المركزي مطلوب", false))
        card.addView(UiKit.title(this, p, "التقارير التشغيلية موقوفة", 23f).apply { gravity = Gravity.CENTER })
        card.addView(UiKit.subtitle(this, p, "لا يمكن عرض أو مشاركة تقارير هذا المحل قبل وجود تفعيل مركزي صالح من إدارة النظام.").apply { gravity = Gravity.CENTER })
        card.addView(UiKit.button(this, p, "رجوع", false).apply { setOnClickListener { finish() } })
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
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@ReportsActivity, 16), UiKit.dp(this@ReportsActivity, 18), UiKit.dp(this@ReportsActivity, 16), UiKit.dp(this@ReportsActivity, 28))
            setBackgroundColor(p.bg)
        }

        val header = UiKit.heroCard(this, p)
        header.addView(UiKit.title(this, p, "تقارير ATTEND PRO", 25f).apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.WHITE) })
        header.addView(UiKit.subtitle(this, p, "${repo.storeName} • ${period.title} • الفرع ${repo.branchId}").apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.argb(225,255,255,255)) })
        root.addView(header)

        val filter = UiKit.card(this, p, 12)
        filter.addView(UiKit.sectionLabel(this, p, "الفترة"))
        filter.addView(UiKit.button(this, p, "تقرير اليوم", period == Period.TODAY).apply { setOnClickListener { period = Period.TODAY; buildUi() } })
        filter.addView(UiKit.button(this, p, "آخر 7 أيام", period == Period.WEEK).apply { setOnClickListener { period = Period.WEEK; buildUi() } })
        filter.addView(UiKit.button(this, p, "هذا الشهر", period == Period.MONTH).apply { setOnClickListener { period = Period.MONTH; buildUi() } })
        filter.addView(UiKit.button(this, p, "كل السجلات", period == Period.ALL).apply { setOnClickListener { period = Period.ALL; buildUi() } })
        root.addView(filter)

        val events = selectedEvents()
        val summary = summary(events)
        val stats = UiKit.card(this, p)
        stats.addView(UiKit.sectionLabel(this, p, "الملخص"))
        stats.addView(UiKit.title(this, p, "${summary.uniqueEmployees} موظف • ${events.size} حركة", 20f))
        stats.addView(UiKit.subtitle(this, p, "حضور ${summary.checkIns} • انصراف ${summary.checkOuts} • حالات تأخير ${summary.lateEmployees} • انصراف مبكر ${summary.earlyDepartures}"))
        root.addView(stats)

        val recent = UiKit.card(this, p)
        recent.addView(UiKit.sectionLabel(this, p, "السجل"))
        recent.addView(UiKit.subtitle(this, p, reportPreview(events)))
        root.addView(recent)

        val share = UiKit.card(this, p)
        share.addView(UiKit.sectionLabel(this, p, "المشاركة المؤكدة"))
        share.addView(UiKit.subtitle(this, p, "يمكن لمالك العمل منح صلاحية لهواتف محددة. التقرير الموجه لهاتف مصرح يُشفّر بمفتاح ذلك الهاتف، ثم يظهر رمز تأكيد عند نجاح الاستلام."))
        share.addView(UiKit.button(this, p, "إرسال لهاتف مصرح له").apply { setOnClickListener { chooseAuthorizedReceiver(events) } })
        share.addView(UiKit.button(this, p, "تأكيد استلام مشاركة", false).apply { setOnClickListener { confirmReceiptDialog() } })
        share.addView(UiKit.button(this, p, "مشاركة ملخص عادي", false).apply { setOnClickListener { shareSummary(events) } })
        share.addView(UiKit.button(this, p, "مشاركة CSV", false).apply { setOnClickListener { shareCsv(events) } })
        share.addView(UiKit.subtitle(this, p, transferStatusText()))
        root.addView(share)

        val remote = UiKit.card(this, p)
        remote.addView(UiKit.sectionLabel(this, p, "المراقبة عن بُعد"))
        remote.addView(UiKit.subtitle(this, p, remoteStatusText()))
        val auto = CheckBox(this).apply {
            text = "المزامنة التلقائية عند توفر الخادم والإنترنت"
            setTextColor(p.text)
            isChecked = repo.reportAutoSync
            setOnCheckedChangeListener { _, checked -> repo.reportAutoSync = checked }
        }
        remote.addView(auto)
        remote.addView(UiKit.button(this, p, if (syncInProgress) "جاري المزامنة..." else "مزامنة الآن", false).apply {
            isEnabled = !syncInProgress
            setOnClickListener { syncNow() }
        })
        remote.addView(UiKit.subtitle(this, p, "المشاركة تعمل فورًا. أما مشاهدة صاحب المحل للبيانات تلقائيًا من هاتف آخر فتحتاج خادم ATTEND PRO مركزيًا؛ عند ربط الخادم ستنتقل أحداث الحضور إليه تلقائيًا حسب هذا الخيار."))
        root.addView(remote)

        val bottom = UiKit.card(this, p, 10)
        bottom.addView(UiKit.button(this, p, "رجوع", false).apply { setOnClickListener { finish() } })
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
        if (events.isEmpty()) return "لا توجد عمليات في الفترة المحددة."
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val rows = events.takeLast(80).reversed().joinToString("\n") { e ->
            val action = if (e.action == AttendanceAction.CHECK_IN) "حضور" else "انصراف"
            "• ${e.employeeName.ifBlank { e.employeeId }} — $action — ${fmt.format(Date(e.timestampEpochMillis))} — ${methodAr(e.method.name)}${if (e.evidence.isNotBlank()) " — ${e.evidence}" else ""}"
        }
        val more = (events.size - 80).coerceAtLeast(0)
        return if (more > 0) "$rows\n… و $more حركة أقدم" else rows
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
            info("لا توجد هواتف مصرح لها", "من إدارة المحل ← هواتف استلام التقارير، أضف هاتف المراقبة بمسح QR الخاص به أولًا.")
            return
        }
        AlertDialog.Builder(this).setTitle("اختر هاتف الاستلام").setItems(receivers.map { "${it.name} • ${it.receiverId}" }.toTypedArray()) { _, which ->
            shareToReceiver(events, receivers[which])
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun shareToReceiver(events: List<AttendanceEvent>, receiver: AuthorizedReportReceiver) {
        val (transfer, code) = repo.createReportTransfer(period.title, receiver.receiverId, receiver.name)
        val s = summary(events)
        val reportText = buildString {
            append("المحل: ${repo.storeName}\nالفرع: ${repo.branchId}\nالفترة: ${period.title}\n")
            append("الموظفون: ${s.uniqueEmployees} • الحضور: ${s.checkIns} • الانصراف: ${s.checkOuts} • حالات التأخير: ${s.lateEmployees} • الانصراف المبكر: ${s.earlyDepartures}\n\n")
            append(reportPreview(events))
        }
        val pkg = ReportProtocol.ReportPackage(receiver.receiverId, transfer.transferId, repo.storeName, repo.branchId, period.title, System.currentTimeMillis(), reportText, code)
        val encrypted = runCatching { ReportProtocol.encodePackage(pkg, receiver.secret) }.getOrElse { info("المشاركة", "تعذر تشفير التقرير: ${it.message ?: "خطأ"}"); return }
        repo.markReportReceiverUsed(receiver.receiverId)

        if (repo.isCentralActivationActive() && repo.serverUrl.isNotBlank()) {
            info("إرسال عبر الإنترنت", "جاري رفع التقرير المشفر إلى هاتف «${receiver.name}» عبر الخادم المركزي. سيبقى محتوى التقرير مشفرًا للحزمة الموجهة لهذا الهاتف.")
            Thread {
                val r = CentralServerClient.pushReport(repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this), receiver.receiverId, transfer.transferId, encrypted, transfer.confirmationCodeHash)
                runOnUiThread {
                    if (r.isSuccess) {
                        info("تم الإرسال عن بُعد ✓", "وصل التقرير إلى صندوق الهاتف المصرح له عبر الإنترنت. عند فتحه في «استلام التقارير» سيتم تأكيد الاستلام مركزيًا.\nرقم النقل: ${transfer.transferId}")
                    } else {
                        info("تعذر الإرسال عبر الخادم", "${r.exceptionOrNull()?.message ?: "خطأ"}\n\nسيتم فتح المشاركة اليدوية كخيار احتياطي.")
                        shareEncryptedFallback(receiver, encrypted)
                    }
                    buildUi()
                }
            }.apply { isDaemon = true }.start()
            return
        }
        shareEncryptedFallback(receiver, encrypted)
        info("تم تجهيز النقل", "لا يوجد تفعيل مركزي نشط، لذلك تم استخدام المشاركة اليدوية المشفرة.\nرقم النقل: ${transfer.transferId}")
        buildUi()
    }

    private fun shareEncryptedFallback(receiver: AuthorizedReportReceiver, encrypted: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ATTEND PRO - تقرير مشفر إلى ${receiver.name}")
            putExtra(Intent.EXTRA_TEXT, encrypted)
        }, "إرسال التقرير إلى ${receiver.name}"))
    }

    private fun confirmReceiptDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
        val id = UiKit.field(this, p, "رقم النقل مثل RPT-XXXXXXXX")
        val code = UiKit.field(this, p, "رمز تأكيد الاستلام", true)
        box.addView(id); box.addView(code)
        val dialog = AlertDialog.Builder(this).setTitle("تأكيد استلام المشاركة").setView(box).setPositiveButton("تأكيد", null).setNegativeButton("إلغاء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (repo.confirmReportTransfer(id.text.toString(), code.text.toString())) {
                    dialog.dismiss(); info("تم التأكيد", "تم تسجيل استلام التقرير بنجاح."); buildUi()
                } else code.error = "رقم النقل أو رمز التأكيد غير صحيح"
            }
        }
        dialog.show()
    }

    private fun transferStatusText(): String {
        val recent = repo.reportTransfers().take(5)
        if (recent.isEmpty()) return "لا توجد مشاركات مؤكدة بعد."
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        return recent.joinToString("\n") { r ->
            val state = if (r.status == "CONFIRMED") "✓ تم الاستلام" else "بانتظار التأكيد"
            "${r.transferId} • ${r.periodLabel}${if (r.receiverName.isNotBlank()) " • ${r.receiverName}" else ""} • $state • ${fmt.format(Date(r.createdAt))}"
        }
    }

    private fun shareSummary(events: List<AttendanceEvent>) {
        val s = summary(events)
        val text = buildString {
            append("ATTEND PRO — ${repo.storeName}\n")
            append("الفترة: ${period.title}\n")
            append("الفرع: ${repo.branchId}\n")
            append("الموظفون في التقرير: ${s.uniqueEmployees}\n")
            append("الحضور: ${s.checkIns}\n")
            append("الانصراف: ${s.checkOuts}\n")
            append("حالات التأخير: ${s.lateEmployees}\n")
            append("الانصراف المبكر: ${s.earlyDepartures}\n")
            append("إجمالي الحركات: ${events.size}\n")
            append("أُنشئ في: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}")
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ATTEND PRO - تقرير ${period.title}")
            putExtra(Intent.EXTRA_TEXT, text)
        }, "مشاركة التقرير"))
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
        }, "مشاركة CSV"))
    }

    private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun remoteStatusText(): String {
        val last = if (repo.lastSyncAt > 0L) SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(repo.lastSyncAt)) else "لم تتم بعد"
        val server = if (repo.serverUrl.isBlank()) "غير مربوط" else "HTTPS مضبوط"
        val license = if (repo.isCentralActivationActive()) "تفعيل مركزي نشط" else "التفعيل المركزي غير صالح"
        val receivers = repo.authorizedReportReceivers().count { it.active }
        return "الخادم: $server • $license • هواتف المراقبة: $receivers\nعمليات تنتظر المزامنة: ${repo.pendingEvents().size} • آخر مزامنة: $last${if (repo.lastSyncMessage.isNotBlank()) "\n${repo.lastSyncMessage}" else ""}"
    }

    private fun syncNow() {
        if (syncInProgress) return
        if (repo.serverUrl.isBlank()) {
            info("المزامنة", "لم يتم ربط خادم ATTEND PRO المركزي بعد. يمكنك استخدام مشاركة التقرير الآن، والمراقبة التلقائية ستعمل بعد ربط الخادم.")
            return
        }
        if (!repo.isActivationActive()) {
            info("المزامنة", "فعّل جهاز المحل أولًا قبل المزامنة المركزية.")
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
                    repo.lastSyncMessage = "تمت المزامنة بنجاح"
                    info("تمت المزامنة", if (result.getOrThrow().isEmpty()) "تم فحص الخادم ولا توجد حركات جديدة." else "تم إرسال ${result.getOrThrow().size} حركة إلى الخادم.")
                } else {
                    repo.lastSyncMessage = "فشلت المزامنة: ${result.exceptionOrNull()?.message ?: "خطأ"}"
                    info("تعذر المزامنة", repo.lastSyncMessage)
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
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("حسنًا", null).show()
    }
}
