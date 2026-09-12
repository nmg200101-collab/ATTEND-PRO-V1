package com.attendpro.store

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.location.Location
import android.location.LocationManager
import android.location.LocationListener
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.WindowManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.attendpro.core.AppLanguage
import com.attendpro.core.NetworkTools
import com.attendpro.core.AppLockGateActivity
import com.attendpro.core.AppLockSettingsDialog
import com.attendpro.core.CentralServerClient
import com.attendpro.core.DeviceIdentity
import com.attendpro.core.QrScannerActivity
import com.attendpro.core.QrCodeTools
import com.attendpro.core.ReportProtocol
import com.attendpro.core.StoreRepository
import com.attendpro.core.ShiftTimeCodec
import com.attendpro.core.UiKit
import com.attendpro.foundation.backup.EncryptedBackupCodec
import com.attendpro.foundation.backup.StoreBackupArtifact
import com.attendpro.foundation.backup.StoreBackupManager
import com.attendpro.foundation.backup.StoreBackupPreview
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StoreSettingsActivity : Activity() {
    private lateinit var repo: StoreRepository
    private val p by lazy { UiKit.palette(this) }

    private fun t(arabic: String, english: String): String = AppLanguage.text(this, arabic, english)
    private var authenticated = false
    private var sessionToken = ""
    private var advancedMode1977 = false
    private var pendingPhoneBackupEnvelope: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        repo = StoreRepository(this)
        if (!repo.isCentralActivationActive()) { showCentralActivationRequired(); return }
        authenticated = savedInstanceState?.getBoolean("authenticated", false) ?: false
        sessionToken = savedInstanceState?.getString("sessionToken").orEmpty()
        showGateOrDashboard()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("authenticated", authenticated)
        outState.putString("sessionToken", sessionToken)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::repo.isInitialized && !repo.isCentralActivationActive()) { showCentralActivationRequired(); return }
        if (authenticated || !repo.hasStoreAdminPin) showDashboard()
    }

    private fun showCentralActivationRequired() {
        window.statusBarColor = p.bg
        val root = baseRoot()
        val card = UiKit.card(this, p)
        card.addView(UiKit.statusBadge(this, p, "التفعيل المركزي مطلوب", false))
        card.addView(UiKit.title(this, p, "إدارة المحل موقوفة", 23f).apply { gravity = Gravity.CENTER })
        card.addView(UiKit.subtitle(this, p, "لا يمكن فتح إعدادات المحل أو إدارة الموظفين قبل اعتماد جهاز المحل من إدارة نظام ATTEND PRO عبر الخادم المركزي.").apply { gravity = Gravity.CENTER })
        card.addView(UiKit.button(this, p, "العودة لشاشة التفعيل", false).apply { setOnClickListener { finish() } })
        root.addView(card)
        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
    }

    private fun showGateOrDashboard() {
        if (!repo.hasStoreAdminPin) {
            authenticated = true
            sessionToken = repo.issueStoreAdminSession()
            showDashboard()
            return
        }
        if (repo.storeAdminLockUntil > System.currentTimeMillis()) {
            val remaining = ((repo.storeAdminLockUntil - System.currentTimeMillis()) / 1000L).coerceAtLeast(1)
            showLockedScreen("تم إيقاف المحاولات مؤقتًا. حاول بعد $remaining ثانية.")
            return
        }
        showLogin()
    }

    private fun showLogin() {
        window.statusBarColor = p.bg
        val root = baseRoot()
        val card = UiKit.card(this, p)
        card.addView(UiKit.sectionLabel(this, p, "إدارة المحل"))
        card.addView(UiKit.title(this, p, "دخول صاحب المحل", 24f))
        card.addView(UiKit.subtitle(this, p, "هذا القسم مخصص لمعلومات المحل والموظفين وطرق الحضور والتقارير. أدخل رمز الحماية للمتابعة."))
        val pin = UiKit.field(this, p, "رمز إدارة المحل", true).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        card.addView(pin)
        card.addView(UiKit.button(this, p, "دخول").apply {
            setOnClickListener {
                if (repo.verifyStoreAdminPin(pin.text.toString())) {
                    authenticated = true
                    sessionToken = repo.issueStoreAdminSession()
                    showDashboard()
                } else {
                    pin.error = if (repo.storeAdminLockUntil > System.currentTimeMillis()) "تم القفل لمدة دقيقة" else "الرمز غير صحيح"
                }
            }
        })
        card.addView(UiKit.button(this, p, "رجوع", false).apply { setOnClickListener { finish() } })
        root.addView(card)
        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
    }

    private fun showLockedScreen(message: String) {
        window.statusBarColor = p.bg
        val root = baseRoot()
        val card = UiKit.card(this, p)
        card.addView(UiKit.title(this, p, "إدارة المحل محمية", 23f))
        card.addView(UiKit.subtitle(this, p, message))
        card.addView(UiKit.button(this, p, "رجوع", false).apply { setOnClickListener { finish() } })
        root.addView(card)
        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
    }

    private fun showDashboard() {
        advancedMode1977 = false
        if (repo.hasStoreAdminPin && !repo.validateStoreAdminSession(sessionToken)) {
            authenticated = false
            sessionToken = ""
            showLogin()
            return
        }
        if (!repo.hasStoreAdminPin && !repo.validateStoreAdminSession(sessionToken)) sessionToken = repo.issueStoreAdminSession()
        StoreMessagePoll1975.schedule(this)
        window.statusBarColor = p.bg
        val root = baseRoot()
        val employees = repo.employees().filter { it.active }
        val linked = employees.count { it.companionEnabled }

        val header = UiKit.heroCard(this, p, 12)
        header.addView(TextView(this).apply {
            text = "⋮"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            contentDescription = "القائمة"
            layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@StoreSettingsActivity, 48), UiKit.dp(this@StoreSettingsActivity, 44)).apply { gravity = Gravity.END }
            setOnClickListener { showStoreTopMenu1976() }
        })
        header.addView(UiKit.title(this, p, repo.storeName, 24f).apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.WHITE) })
        header.addView(UiKit.subtitle(this, p, "إدارة المحل • ${employees.size} موظف • $linked هاتف مفعّل").apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.argb(225,255,255,255)) })
        root.addView(header)

        val hint = UiKit.card(this, p, 10)
        hint.addView(UiKit.subtitle(this, p, "هذه شاشة جميع إعدادات مدير المحل. اختر القسم المطلوب، أو افتح «الإعدادات المتقدمة» من قائمة ⋮ لعرض كل الخيارات التفصيلية القديمة.").apply { gravity = Gravity.CENTER })
        root.addView(hint)

        fun largeSection(title: String, subtitle: String, action: () -> Unit): LinearLayout = UiKit.card(this, p, 12).apply {
            addView(UiKit.title(this@StoreSettingsActivity, p, title, 18f).apply { gravity = Gravity.CENTER })
            addView(UiKit.subtitle(this@StoreSettingsActivity, p, subtitle).apply { gravity = Gravity.CENTER })
            UiKit.makeInteractive(this, this@StoreSettingsActivity, p)
            setOnClickListener { action() }
        }

        root.addView(largeSection("الموظفون", "إضافة موظف، تعديل بياناته، وتجهيز طرق التحقق") {
            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_EMPLOYEE_MANAGER, true).putExtra(MainActivity.EXTRA_STORE_ADMIN_SESSION, sessionToken))
        })
        root.addView(largeSection("الحضور والتشغيل", "طرق الحضور، الدوام، الموقع، والبصمة الخارجية") { showStoreOperations1976() })
        root.addView(largeSection("الصوت والرسائل", "التحكم الصوتي، إشعارات المحل، ورسائل الموظفين") { showStoreCommunication1976() })
        root.addView(largeSection("التقارير والحماية", "التقارير، الصلاحيات، حماية الإدارة، وجاهزية المحل") { showStoreReportsSecurity1976() })

        val footer = UiKit.card(this, p, 8)
        footer.addView(UiKit.subtitle(this, p, if (repo.isCentralActivationActive()) "● الخادم المركزي متصل والتفعيل نشط" else "● يحتاج التفعيل المركزي إلى مراجعة").apply { gravity = Gravity.CENTER })
        footer.addView(UiKit.button(this, p, "إغلاق إدارة المحل", false).apply {
            setOnClickListener { authenticated = false; repo.clearStoreAdminSession(); sessionToken = ""; finish() }
        })
        root.addView(footer)
        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
    }

    private fun showLayeredMenu1977(title: String, items: List<Pair<String, () -> Unit>>) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = if (AppLanguage.isEnglish(this@StoreSettingsActivity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@StoreSettingsActivity, 18), UiKit.dp(this@StoreSettingsActivity, 8), UiKit.dp(this@StoreSettingsActivity, 18), UiKit.dp(this@StoreSettingsActivity, 8))
        }
        items.forEach { (label, action) -> box.addView(UiKit.button(this, p, label, false).apply { setOnClickListener { action() } }) }
        AlertDialog.Builder(this).setTitle(AppLanguage.legacyUiText(this, title)).setView(box).setNegativeButton(t("رجوع", "Back"), null).show()
    }

    private fun showStoreTopMenu1976() {
        showLayeredMenu1977(t("القائمة", "Menu"), listOf(
            t("الإشعارات", "Notifications") to { startActivity(Intent(this, StoreMessages1975Activity::class.java)) },
            getString(R.string.user_guide) to { showStoreUserGuide1976() },
            getString(R.string.language) to { AppLanguage.showPicker(this) { recreate() } },
            t("الخصوصية والبيانات", "Privacy and data") to { startActivity(Intent(this, com.attendpro.core.PrivacyDataActivity::class.java)) },
            t("الإعدادات المتقدمة", "Advanced settings") to { showAdvancedDashboard1975() }
        ))
    }

    private fun showStoreUserGuide1976() {
        startActivity(Intent(this, StoreUserGuideActivity::class.java))
    }

    private fun showStoreOperations1976() {
        showLayeredMenu1977("الحضور والتشغيل", listOf(
            "طرق الحضور" to { attendanceMethodsSettings() },
            "الدوام ودقائق السماح" to { shiftSettings() },
            "موقع المحل وGPS" to { gpsSettings() },
            "قارئ البصمة الخارجي" to { fingerprintSettings() }
        ))
    }

    private fun showStoreCommunication1976() {
        showLayeredMenu1977("الصوت والرسائل", listOf(
            "التحكم الصوتي" to { startActivity(Intent(this, StoreVoiceControl1975Activity::class.java)) },
            "الرسائل والإشعارات" to { startActivity(Intent(this, StoreMessages1975Activity::class.java)) }
        ))
    }

    private fun showStoreReportsSecurity1976() {
        showLayeredMenu1977("التقارير والحماية", listOf(
            "التقارير والمشاركة" to { startActivity(Intent(this, ReportsActivity::class.java).putExtra(ReportsActivity.EXTRA_STORE_ADMIN_SESSION, sessionToken)) },
            "هواتف استلام التقارير" to { manageReportReceivers() },
            (if (repo.hasStoreAdminPin) "تغيير رمز إدارة المحل" else "إنشاء رمز حماية") to { changeStorePin() },
            "قفل التطبيق والبصمة" to { showAppLockSettings() },
            "النسخ الاحتياطي والاستعادة" to { showBackupCenter() },
            "فحص جاهزية المحل" to { healthCheck() },
            "المظهر والقوالب" to { UiKit.showAppearancePicker(this) }
        ))
    }

    private fun showAdvancedDashboard1975() {
        advancedMode1977 = true
        if (repo.hasStoreAdminPin && !repo.validateStoreAdminSession(sessionToken)) {
            authenticated = false
            sessionToken = ""
            showLogin()
            return
        }
        if (!repo.hasStoreAdminPin && !repo.validateStoreAdminSession(sessionToken)) sessionToken = repo.issueStoreAdminSession()
        StoreMessagePoll1975.schedule(this)
        window.statusBarColor = p.bg
        val root = baseRoot()
        val employees = repo.employees().filter { it.active }
        val linked = employees.count { it.companionEnabled }

        val header = UiKit.heroCard(this, p)
        header.addView(UiKit.title(this, p, repo.storeName, 25f).apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.WHITE) })
        header.addView(UiKit.subtitle(this, p, "إدارة المحل • الفرع ${repo.branchId} • الإصدار ${attendProVersionName()}").apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.argb(225,255,255,255)) })
        root.addView(header)

        val summary = UiKit.card(this, p)
        summary.addView(UiKit.sectionLabel(this, p, "لوحة إدارة المحل"))
        summary.addView(UiKit.title(this, p, "${employees.size} موظف نشط • $linked هاتف موظف مفعّل", 20f))
        summary.addView(UiKit.subtitle(this, p, recognitionSummary()))
        summary.addView(UiKit.subtitle(this, p, if (repo.isCentralActivationActive()) "● الخادم المركزي متصل والتفعيل نشط" else "● يحتاج التفعيل المركزي إلى مراجعة"))
        root.addView(summary)

        val store = UiKit.card(this, p)
        store.addView(UiKit.sectionLabel(this, p, "المحل والموظفون"))
        store.addView(UiKit.subtitle(this, p, "بيانات المنشأة وإدارة الموظفين من مكان واحد."))
        store.addView(UiKit.button(this, p, "معلومات المحل").apply { setOnClickListener { editStoreProfile() } })
        store.addView(UiKit.button(this, p, "إضافة وإدارة الموظفين", false).apply {
            setOnClickListener { startActivity(Intent(this@StoreSettingsActivity, MainActivity::class.java).putExtra(MainActivity.EXTRA_EMPLOYEE_MANAGER, true).putExtra(MainActivity.EXTRA_STORE_ADMIN_SESSION, sessionToken)) }
        })
        root.addView(store)

        val attendance = UiKit.card(this, p)
        attendance.addView(UiKit.sectionLabel(this, p, "الحضور والتحقق"))
        attendance.addView(UiKit.subtitle(this, p, "إعداد طرق الحضور والموقع والدوام والبصمة الخارجية. إعدادات الارتباط نفسها لم تتغير."))
        attendance.addView(UiKit.button(this, p, "تفعيل وتعطيل طرق الحضور").apply { setOnClickListener { attendanceMethodsSettings() } })
        attendance.addView(UiKit.button(this, p, "إعداد GPS وموقع المحل", false).apply { setOnClickListener { gpsSettings() } })
        attendance.addView(UiKit.button(this, p, "الدوام ودقائق السماح", false).apply { setOnClickListener { shiftSettings() } })
        attendance.addView(UiKit.button(this, p, "قارئ البصمة الخارجي", false).apply { setOnClickListener { fingerprintSettings() } })
        root.addView(attendance)

        val voice = UiKit.card(this, p)
        voice.addView(UiKit.sectionLabel(this, p, "الصوت والتنبيهات"))
        voice.addView(UiKit.subtitle(this, p, "تحكم عام في نطق جهاز المحل وتنبيهات هاتف الموظف، مع إعداد مستقل لكل موظف. لا يغيّر هذا القسم التحقق ببصمة الصوت."))
        voice.addView(UiKit.button(this, p, "فتح التحكم الصوتي").apply { setOnClickListener { startActivity(Intent(this@StoreSettingsActivity, StoreVoiceControl1975Activity::class.java)) } })
        root.addView(voice)

        val messages = UiKit.card(this, p)
        messages.addView(UiKit.sectionLabel(this, p, "الرسائل والإشعارات"))
        messages.addView(UiKit.subtitle(this, p, "استقبال رسائل صاحب النظام وردود الموظفين، وإرسال رسالة خاصة لأي موظف مع أولوية وخيار قراءة الرسالة بصوت."))
        messages.addView(UiKit.button(this, p, "فتح مركز الرسائل").apply { setOnClickListener { startActivity(Intent(this@StoreSettingsActivity, StoreMessages1975Activity::class.java)) } })
        root.addView(messages)

        val reports = UiKit.card(this, p)
        reports.addView(UiKit.sectionLabel(this, p, "التقارير والمراقبة"))
        reports.addView(UiKit.button(this, p, "فتح التقارير والمشاركة").apply { setOnClickListener { startActivity(Intent(this@StoreSettingsActivity, ReportsActivity::class.java).putExtra(ReportsActivity.EXTRA_STORE_ADMIN_SESSION, sessionToken)) } })
        reports.addView(UiKit.button(this, p, "هواتف استلام التقارير والصلاحيات", false).apply { setOnClickListener { manageReportReceivers() } })
        root.addView(reports)

        val security = UiKit.card(this, p, 13)
        security.addView(UiKit.sectionLabel(this, p, "الحماية والصلاحيات"))
        security.addView(UiKit.subtitle(this, p, if (repo.hasStoreAdminPin) "✓ إدارة المحل محمية برمز مستقل." else "القسم غير محمي حاليًا. أنشئ رمزًا لمنع وصول الموظفين إلى الإعدادات."))
        security.addView(UiKit.button(this, p, if (repo.hasStoreAdminPin) "تغيير رمز إدارة المحل" else "إنشاء رمز حماية", false).apply { setOnClickListener { changeStorePin() } })
        security.addView(UiKit.button(this, p, "قفل التطبيق والبصمة", false).apply { setOnClickListener { showAppLockSettings() } })
        if (repo.hasStoreAdminPin) security.addView(UiKit.button(this, p, "قفل إدارة المحل الآن", false).apply {
            setOnClickListener { authenticated = false; repo.clearStoreAdminSession(); sessionToken = ""; showLogin() }
        })
        root.addView(security)

        val appearance = UiKit.card(this, p)
        appearance.addView(UiKit.sectionLabel(this, p, "المظهر والصيانة"))
        appearance.addView(UiKit.subtitle(this, p, UiKit.appearanceSummary(this)))
        appearance.addView(UiKit.button(this, p, "المظهر والقوالب", false).apply { setOnClickListener { UiKit.showAppearancePicker(this@StoreSettingsActivity) } })
        appearance.addView(UiKit.button(this, p, "النسخ الاحتياطي والاستعادة", false).apply { setOnClickListener { showBackupCenter() } })
        appearance.addView(UiKit.button(this, p, "فحص جاهزية المحل", false).apply { setOnClickListener { healthCheck() } })
        appearance.addView(UiKit.button(this, p, "إدارة ATTEND PRO العليا", false).apply { setOnClickListener { startActivity(Intent(this@StoreSettingsActivity, SystemSettingsActivity::class.java)) } })
        appearance.addView(UiKit.button(this, p, "إغلاق إدارة المحل", false).apply { setOnClickListener { authenticated = false; repo.clearStoreAdminSession(); sessionToken = ""; finish() } })
        root.addView(appearance)

        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
    }

    private fun recognitionSummary(): String {
        val employees = repo.employees().filter { it.active }
        val face = employees.count { it.faceTemplate.isNotBlank() && it.faceQualityScore >= 45 }
        val password = employees.count { it.passwordHash.isNotBlank() }
        val voice = employees.count { it.voicePhraseHash.isNotBlank() }
        val phone = employees.count { it.companionEnabled }
        return "◉ الوجه $face • ◖ الصوت $voice • ▣ كلمة المرور $password\n" +
            "◎ هاتف الموظف $phone • ▦ QR ${if (repo.allowQrAttendance) "مفعّل" else "متوقف"} • حماية الموقع ${if (repo.isGpsConfigured) "جاهزة" else "غير مضبوطة"}"
}

    private fun editStoreProfile() {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 10) }
        scroll.addView(box)
        val name = UiKit.field(this, p, "اسم المحل").apply { setText(repo.storeName) }
        val branch = UiKit.field(this, p, "رمز الفرع").apply { setText(repo.branchId) }
        val phone = UiKit.field(this, p, "هاتف المحل - اختياري").apply { setText(repo.storePhone) }
        val address = UiKit.field(this, p, "العنوان - اختياري").apply { setText(repo.storeAddress) }
        val manager = UiKit.field(this, p, "اسم صاحب / مدير المحل - اختياري").apply { setText(repo.storeManagerName) }
        val commercial = UiKit.field(this, p, "السجل / المعرف التجاري - اختياري").apply { setText(repo.storeCommercialId) }
        val notes = UiKit.field(this, p, "ملاحظات المحل - اختياري").apply { setText(repo.storeNotes); minLines = 2 }
        listOf(name, branch, phone, address, manager, commercial, notes).forEach { box.addView(it) }
        val dialog = AlertDialog.Builder(this).setTitle("معلومات المحل").setView(scroll).setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = name.text.toString().trim(); val b = branch.text.toString().trim()
                if (n.isBlank() || b.isBlank()) {
                    name.error = if (n.isBlank()) "اسم المحل مطلوب" else null
                    branch.error = if (b.isBlank()) "رمز الفرع مطلوب" else null
                    return@setOnClickListener
                }
                repo.storeName = n; repo.branchId = b; repo.storePhone = phone.text.toString(); repo.storeAddress = address.text.toString()
                repo.storeManagerName = manager.text.toString(); repo.storeCommercialId = commercial.text.toString(); repo.storeNotes = notes.text.toString()
                repo.ensureCurrentStoreInManagement(); dialog.dismiss(); info("تم الحفظ", "تم تحديث معلومات المحل."); showDashboard()
            }
        }
        dialog.show()
    }

    private fun attendanceMethodsSettings() {
        PhoneAttendanceSettingsDialog1928.show(this, repo)
}

    private fun gpsSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
        val lat = UiKit.field(this, p, "خط العرض Latitude").apply { setText(if (repo.storeLatitude.isFinite()) repo.storeLatitude.toString() else "") }
        val lon = UiKit.field(this, p, "خط الطول Longitude").apply { setText(if (repo.storeLongitude.isFinite()) repo.storeLongitude.toString() else "") }
        val radius = UiKit.field(this, p, "نطاق التعرف GPS بالمتر", true).apply { setText(repo.gpsRadiusMeters.toString()) }
        box.addView(UiKit.subtitle(this, p, "أدخل إحداثيات المحل مرة واحدة. GPS في 1.9.58 للمراقبة فقط: يسجل متى أصبح الهاتف داخل/قرب/خارج النطاق مع المسافة والدقة. يمكن استخدام 10م للمحل الصغير، لكن التطبيق يعرض الدقة ولا يعتبر GPS إثبات حضور."))
        listOf(lat, lon, radius).forEach { box.addView(it) }
        box.addView(UiKit.button(this, p, "استخدام موقع هذا الجهاز الآن", false).apply {
            setOnClickListener {
                val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!fine && !coarse) {
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 9101)
                    info("صلاحية الموقع", "اسمح بالموقع ثم اضغط «استخدام موقع هذا الجهاز الآن» مرة أخرى.")
                    return@setOnClickListener
                }
                val manager = getSystemService(LocationManager::class.java) ?: return@setOnClickListener
                info("GPS", "يجري الآن التقاط موقع حديث من GPS والشبكة. قد يستغرق حتى 30 ثانية داخل المباني.")
                requestFreshLocation(manager) { location ->
                    if (location == null) { info("الموقع", "لم يصل موقع صالح. فعّل دقة الموقع العالية واقترب من نافذة أو مكان مفتوح ثم أعد المحاولة."); return@requestFreshLocation }
                    if (isMockLocation(location)) { info("الموقع", "تم رفض موقع تجريبي/مزيف."); return@requestFreshLocation }
                    lat.setText(location.latitude.toString()); lon.setText(location.longitude.toString())
                    info("تم التقاط الموقع", "تم التقاط موقع حديث بدقة ${location.accuracy.toInt()} متر. راجع نطاق التعرف ثم اضغط حفظ.")
                }
            }
        })
        val dialog = AlertDialog.Builder(this).setTitle("موقع المحل وGPS").setView(box).setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val la = lat.text.toString().toDoubleOrNull(); val lo = lon.text.toString().toDoubleOrNull(); val r = radius.text.toString().toIntOrNull()
                if (la == null || la !in -90.0..90.0) { lat.error = "خط عرض غير صالح"; return@setOnClickListener }
                if (lo == null || lo !in -180.0..180.0) { lon.error = "خط طول غير صالح"; return@setOnClickListener }
                if (r == null || r !in 10..5000) { radius.error = "استخدم 10 إلى 5000 متر"; return@setOnClickListener }
                repo.storeLatitude = la; repo.storeLongitude = lo; repo.gpsRadiusMeters = r
                dialog.dismiss(); info("تم", "تم حفظ موقع المحل ونطاق التعرف GPS. تُزامن الإعدادات أيضًا عبر Bluetooth عند الاتصال، وGPS لن يسجل حضورًا بمفرده."); showDashboard()
            }
        }
        dialog.show()
    }

    private fun shiftSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
        val start = UiKit.field(this, p, t("بداية الدوام", "Shift start")).apply {
            isFocusable = false
            isClickable = true
            FormPickerHelper.setTime(this, repo.shiftHour, repo.shiftMinute)
            setOnClickListener { FormPickerHelper.pickTime(this@StoreSettingsActivity, this, repo.shiftHour, repo.shiftMinute) }
        }
        val end = UiKit.field(this, p, t("نهاية الدوام", "Shift end")).apply {
            isFocusable = false
            isClickable = true
            FormPickerHelper.setTime(this, repo.shiftEndHour, repo.shiftEndMinute)
            setOnClickListener { FormPickerHelper.pickTime(this@StoreSettingsActivity, this, repo.shiftEndHour, repo.shiftEndMinute) }
        }
        val grace = UiKit.field(this, p, t("دقائق السماح", "Grace minutes")).apply {
            setText(repo.graceMinutes.toString())
            isFocusable = false
            isClickable = true
            setOnClickListener { FormPickerHelper.pickNumber(this@StoreSettingsActivity, this, 0, 120, t("دقائق السماح", "Grace minutes")) }
        }
        box.addView(UiKit.subtitle(this, p,
            t("اختر الساعة والدقائق وصباح/مساء. يُحفظ الوقت داخليًا بنظام 24 ساعة. يمكن للدوام أن يعبر منتصف الليل، مثل 10:00 م إلى 6:00 ص.", "Choose hour, minute and AM/PM. Time is stored internally in 24-hour format. Overnight shifts are supported, such as 10:00 PM to 6:00 AM.")))
        listOf(start, end, grace).forEach { box.addView(it) }
        val dialog = AlertDialog.Builder(this).setTitle(t("الدوام والسماح", "Shift and grace period")).setView(box)
            .setPositiveButton(t("حفظ", "Save"), null).setNegativeButton(t("إلغاء", "Cancel"), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val aTime = FormPickerHelper.selectedTime(start, repo.shiftHour, repo.shiftMinute)
                val bTime = FormPickerHelper.selectedTime(end, repo.shiftEndHour, repo.shiftEndMinute)
                val g = grace.text.toString().toIntOrNull()
                if (ShiftTimeCodec.same(aTime, bTime)) {
                    end.error = t("وقت نهاية الدوام يجب أن يختلف عن وقت البداية", "Shift end must differ from shift start")
                    return@setOnClickListener
                }
                if (g == null || g !in 0..120) {
                    grace.error = t("اختر من 0 إلى 120 دقيقة", "Choose from 0 to 120 minutes")
                    return@setOnClickListener
                }
                repo.shiftHour = aTime.hour24
                repo.shiftMinute = aTime.minute
                repo.shiftEndHour = bTime.hour24
                repo.shiftEndMinute = bTime.minute
                repo.graceMinutes = g
                val overnight = (bTime.hour24 * 60 + bTime.minute) < (aTime.hour24 * 60 + aTime.minute)
                dialog.dismiss()
                val suffix = if (overnight) t(" (دوام ليلي يعبر منتصف الليل)", " (overnight shift)") else ""
                info(t("تم", "Saved"), t("تم حفظ الدوام ", "Shift saved: ") + ShiftTimeCodec.format(aTime.hour24, aTime.minute) +
                    t(" إلى ", " to ") + ShiftTimeCodec.format(bTime.hour24, bTime.minute) + suffix + ".")
                showDashboard()
            }
        }
        dialog.show()
    }

    private fun manageReportReceivers() {
        val receivers = repo.authorizedReportReceivers()
        val labels = mutableListOf(t("＋ إضافة هاتف استلام عبر QR", "＋ Add receiver phone by QR"))
        labels.addAll(receivers.map {
            val permissions = buildList {
                if (it.canReceiveReports) add(t("تقارير", "Reports"))
                if (it.canMessageEmployees) add(t("رسائل", "Messages"))
                if (it.canManageStore) add(t("إدارة", "Management"))
            }.joinToString(" + ").ifBlank { t("بدون صلاحيات", "No permissions") }
            "${if (it.active) "●" else "○"} ${it.name} • $permissions"
        })
        AlertDialog.Builder(this)
            .setTitle(t("هواتف الاستلام والصلاحيات (${receivers.size})", "Receiver phones and permissions (${receivers.size})"))
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == 0) {
                    startActivityForResult(
                        Intent(this, QrScannerActivity::class.java)
                            .putExtra(QrScannerActivity.EXTRA_PROMPT, t("امسح QR هاتف استلام التقارير", "Scan the receiver phone QR")),
                        REQUEST_REPORT_RECEIVER_QR
                    )
                } else {
                    showReceiverControl(receivers[which - 1])
                }
            }
            .setNegativeButton(t("إغلاق", "Close"), null)
            .show()
    }

    private fun showReceiverControl(receiver: com.attendpro.core.AuthorizedReportReceiver) {
        val permissionsText = listOf(
            t("استلام التقارير", "Receive reports") to receiver.canReceiveReports,
            t("مراسلة الموظفين", "Message employees") to receiver.canMessageEmployees,
            t("إعدادات مدير المحل", "Store Manager settings") to receiver.canManageStore
        ).joinToString("\n") { (name, enabled) -> "${if (enabled) "✓" else "○"} $name" }
        AlertDialog.Builder(this)
            .setTitle(receiver.name)
            .setMessage(
                t("المعرف: ${receiver.receiverId}\nالحالة: ${if (receiver.active) "مسموح" else "موقوف"}\n\nالصلاحيات:\n$permissionsText",
                  "ID: ${receiver.receiverId}\nStatus: ${if (receiver.active) "Enabled" else "Disabled"}\n\nPermissions:\n$permissionsText")
            )
            .setPositiveButton(t("تعديل الصلاحيات", "Edit permissions")) { _, _ -> editReceiverPermissions(receiver) }
            .setNeutralButton(if (receiver.active) t("إيقاف الهاتف", "Disable phone") else t("إعادة التفعيل", "Enable phone")) { _, _ ->
                val next = !receiver.active
                repo.setReportReceiverActive(receiver.receiverId, next)
                if (repo.isCentralActivationActive() && repo.serverUrl.isNotBlank()) {
                    Thread {
                        CentralServerClient.setReceiverActive(
                            repo.serverUrl, repo.centralAccessToken, repo.storeId,
                            DeviceIdentity(this), receiver.receiverId, next
                        )
                    }.start()
                }
                manageReportReceivers()
            }
            .setNegativeButton(t("إغلاق", "Close"), null)
            .show()
    }

    private fun editReceiverPermissions(receiver: com.attendpro.core.AuthorizedReportReceiver) {
        val labels = arrayOf(
            t("استلام التقارير", "Receive reports"),
            t("مراسلة الموظفين عبر الخادم", "Message employees through server"),
            t("تعديل إعدادات مدير المحل عن بُعد", "Manage Store settings remotely")
        )
        val checked = booleanArrayOf(
            receiver.canReceiveReports,
            receiver.canMessageEmployees,
            receiver.canManageStore
        )
        AlertDialog.Builder(this)
            .setTitle(t("صلاحيات ${receiver.name}", "${receiver.name} permissions"))
            .setMultiChoiceItems(labels, checked) { _, which, value -> checked[which] = value }
            .setPositiveButton(t("حفظ الصلاحيات", "Save permissions")) { _, _ ->
                repo.setReportReceiverPermissions(receiver.receiverId, checked[0], checked[1], checked[2])
                if (repo.isCentralActivationActive() && repo.serverUrl.isNotBlank()) {
                    Thread {
                        val result = CentralServerClient.setReceiverPermissions(
                            repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this),
                            receiver.receiverId, checked[0], checked[1], checked[2]
                        )
                        runOnUiThread {
                            if (result.isFailure) info(
                                t("تم الحفظ محليًا فقط", "Saved locally only"),
                                t("تعذر تحديث صلاحيات الخادم: ${result.exceptionOrNull()?.message ?: "خطأ"}",
                                  "Unable to update server permissions: ${result.exceptionOrNull()?.message ?: "Error"}")
                            )
                        }
                    }.start()
                }
                manageReportReceivers()
            }
            .setNeutralButton(t("حذف الهاتف", "Remove phone")) { _, _ ->
                repo.setReportReceiverActive(receiver.receiverId, false)
                if (repo.isCentralActivationActive() && repo.serverUrl.isNotBlank()) {
                    Thread {
                        CentralServerClient.setReceiverActive(
                            repo.serverUrl, repo.centralAccessToken, repo.storeId,
                            DeviceIdentity(this), receiver.receiverId, false
                        )
                    }.start()
                }
                repo.removeReportReceiver(receiver.receiverId)
                manageReportReceivers()
            }
            .setNegativeButton(t("إلغاء", "Cancel"), null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_BACKUP_CREATE) {
            val envelope = pendingPhoneBackupEnvelope
            pendingPhoneBackupEnvelope = null
            if (resultCode != RESULT_OK || data?.data == null || envelope == null) return
            val destination = data.data ?: return
            Thread {
                val result = runCatching {
                    contentResolver.openOutputStream(destination, "w")?.use { stream ->
                        stream.write(envelope.toByteArray(Charsets.UTF_8)); stream.flush()
                    } ?: error("تعذر فتح ملف الحفظ")
                }
                runOnUiThread {
                    if (result.isSuccess) info("تم إنشاء النسخة ✓", "حُفظت نسخة الهاتف المشفرة بنجاح. احتفظ بكلمة النسخة في مكان آمن؛ لا يمكن استعادتها بدونها.")
                    else info("تعذر الحفظ", result.exceptionOrNull()?.message ?: "تعذر كتابة ملف النسخة")
                }
            }.apply { isDaemon = true }.start()
            return
        }
        if (requestCode == REQUEST_BACKUP_OPEN) {
            if (resultCode != RESULT_OK || data?.data == null) return
            importBackupFromPhone(data.data ?: return)
            return
        }
        if (requestCode != REQUEST_REPORT_RECEIVER_QR) return
        if (resultCode != RESULT_OK) { info("هواتف التقارير", data?.getStringExtra(QrScannerActivity.EXTRA_ERROR) ?: "تم إلغاء المسح"); return }
        val raw = data?.getStringExtra(QrScannerActivity.EXTRA_RESULT).orEmpty()
        val invite = ReportProtocol.decodeInvite(raw)
        if (invite == null || invite.expiresAt < System.currentTimeMillis()) { info("QR غير صالح", "رمز هاتف الاستلام غير صالح أو انتهت مدته."); return }
        if (!repo.authorizeReportReceiver(invite)) { info("تعذر منح الصلاحية", "لم يتم قبول رمز هاتف الاستلام."); return }
        if (repo.isCentralActivationActive() && repo.serverUrl.isNotBlank()) {
            info("جاري ربط الهاتف", "تمت الصلاحية محليًا، ويجري الآن تسجيل الهاتف في الخادم المركزي للاستلام عن بُعد.")
            Thread {
                val r = CentralServerClient.registerReceiver(repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this), invite.receiverId, invite.name, invite.secret)
                if (r.isSuccess) {
                    CentralServerClient.setReceiverPermissions(
                        repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this),
                        invite.receiverId, true, false, false
                    )
                }
                runOnUiThread {
                    if (r.isSuccess) showReceiverRemoteGrant(invite)
                    else info("صلاحية محلية فقط", "تم حفظ الهاتف محليًا، لكن تعذر تسجيله في الخادم: ${r.exceptionOrNull()?.message ?: "خطأ"}")
                }
            }.apply { isDaemon = true }.start()
        } else info("تم منح الصلاحية", "تم السماح للهاتف «${invite.name}» باستلام التقارير المشفرة محليًا. للاستلام عن بُعد فعّل المحل مركزيًا أولًا.")
    }

    private fun showReceiverRemoteGrant(invite: ReportProtocol.ReceiverInvite) {
        val grant = ReportProtocol.RemoteReceiverGrant(invite.receiverId, repo.serverUrl, repo.storeName, repo.branchId, System.currentTimeMillis() + 10 * 60_000L)
        val raw = ReportProtocol.encodeRemoteGrant(grant)
        val qr = runCatching { QrCodeTools.bitmap(raw, 700) }.getOrElse { info("تم منح الصلاحية ✓", "تم تسجيل الهاتف على الخادم، لكن تعذر إنشاء QR الربط. يمكن إدخال رابط الخادم يدويًا في هاتف المراقبة."); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(20, 12, 20, 8) }
        box.addView(UiKit.subtitle(this, p, "تم منح الصلاحية للهاتف «${invite.name}» محليًا وعبر الإنترنت. الآن من هاتف المالك: استلام التقارير ← مسح QR ربط الخادم.").apply { gravity = Gravity.CENTER })
        box.addView(ImageView(this).apply { setImageBitmap(qr); adjustViewBounds = true; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this@StoreSettingsActivity, 340)) })
        AlertDialog.Builder(this).setTitle("✓ ربط هاتف المراقبة بالخادم").setView(box).setPositiveButton("إغلاق", null).show()
    }

    private fun fingerprintSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
        val host = UiKit.field(this, p, "IP جهاز البصمة").apply { setText(repo.fingerprintHost) }
        val port = UiKit.field(this, p, "Port مثل 4370", true).apply { setText(repo.fingerprintPort.toString()) }
        box.addView(UiKit.subtitle(this, p, "الاتصال الشبكي هنا يختبر الوصول فقط. جلب قوالب البصمة والسجلات تلقائيًا يحتاج موصلًا خاصًا بموديل جهاز البصمة."))
        box.addView(host); box.addView(port)
        AlertDialog.Builder(this).setTitle("قارئ البصمة الخارجي").setView(box).setPositiveButton("حفظ واختبار") { _, _ ->
            repo.fingerprintHost = host.text.toString(); repo.fingerprintPort = port.text.toString().toIntOrNull() ?: 4370
            Thread {
                val result = NetworkTools.probeTcp(repo.fingerprintHost, repo.fingerprintPort)
                runOnUiThread { info("نتيجة الاتصال", if (result.isSuccess) "✓ تم الوصول إلى الجهاز على الشبكة." else "تعذر الاتصال: ${result.exceptionOrNull()?.message ?: "خطأ"}") }
            }.start()
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun changeStorePin() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
        val pin = UiKit.field(this, p, "رمز جديد من 4 إلى 8 أرقام", true).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        val confirm = UiKit.field(this, p, "تأكيد الرمز", true).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        box.addView(pin); box.addView(confirm)
        val builder = AlertDialog.Builder(this).setTitle(if (repo.hasStoreAdminPin) "تغيير رمز إدارة المحل" else "إنشاء رمز إدارة المحل").setView(box)
            .setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null)
        if (repo.hasStoreAdminPin) builder.setNeutralButton("إزالة الحماية") { _, _ -> repo.clearStoreAdminPin(); authenticated = true; info("تم", "تمت إزالة رمز الحماية."); showDashboard() }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val a = pin.text.toString(); val b = confirm.text.toString()
                if (a.length !in 4..8 || a.any { !it.isDigit() }) { pin.error = "استخدم 4 إلى 8 أرقام"; return@setOnClickListener }
                if (a != b) { confirm.error = "الرمزان غير متطابقين"; return@setOnClickListener }
                repo.setStoreAdminPin(a); authenticated = true; sessionToken = repo.issueStoreAdminSession(); dialog.dismiss(); info("تم", "تم حفظ رمز إدارة المحل."); showDashboard()
            }
        }
        dialog.show()
    }

    private fun showAppLockSettings() {
        AppLockSettingsDialog.show(this, onLockNow = {
            packageManager.getLaunchIntentForPackage(packageName)?.let { gate ->
                gate.putExtra(AppLockGateActivity.EXTRA_GATE_ONLY, true)
                startActivity(gate)
            }
        })
    }

    private fun showBackupCenter() {
        val state = getSharedPreferences("attend_pro_backup_state_v2", MODE_PRIVATE)
        val lastUpload = state.getLong("lastServerUploadAt", 0L)
        val lastRestore = state.getLong("lastRestoredAt", 0L)
        val statusText = buildString {
            append("نسخ الهاتف: بيانات كاملة مع صور ملفات الوجه المتاحة.\n")
            append("نسخ الخادم: بيانات الموظفين والحضور والقوالب بلا صور مرجعية، ومشفرة قبل الرفع.\n\n")
            append("آخر رفع للخادم: ${formatBackupTime(lastUpload)}\n")
            append("آخر استعادة: ${formatBackupTime(lastRestore)}")
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = if (AppLanguage.isEnglish(this@StoreSettingsActivity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@StoreSettingsActivity, 18), UiKit.dp(this@StoreSettingsActivity, 8), UiKit.dp(this@StoreSettingsActivity, 18), UiKit.dp(this@StoreSettingsActivity, 8))
        }
        box.addView(UiKit.subtitle(this, p, statusText))
        box.addView(UiKit.button(this, p, "إنشاء نسخة على الهاتف").apply { setOnClickListener { createPhoneBackup() } })
        box.addView(UiKit.button(this, p, "استعادة نسخة من الهاتف", false).apply { setOnClickListener { selectPhoneBackup() } })
        box.addView(UiKit.button(this, p, "رفع نسخة مشفرة إلى الخادم", false).apply { setOnClickListener { uploadServerBackup() } })
        box.addView(UiKit.button(this, p, "استعادة أحدث نسخة من الخادم", false).apply { setOnClickListener { downloadServerBackup() } })
        AlertDialog.Builder(this).setTitle("النسخ الاحتياطي والاستعادة").setView(box).setNegativeButton("إغلاق", null).show()
    }

    private fun createPhoneBackup() {
        promptBackupPassword(confirm = true, title = "كلمة نسخة الهاتف") { password ->
            info("جاري تجهيز النسخة", "يجري تشفير البيانات وصور ملفات الوجه على هذا الهاتف. اختر مكان الحفظ عند اكتمال التجهيز.")
            Thread {
                val result = runCatching { StoreBackupManager(this).createEncryptedBackup(password, BuildConfig.VERSION_NAME, includeFaceFiles = true) }
                password.fill('\u0000')
                runOnUiThread {
                    result.onSuccess { artifact ->
                        pendingPhoneBackupEnvelope = artifact.envelope
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(artifact.metadata.createdAt))
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/json"
                            putExtra(Intent.EXTRA_TITLE, "ATTEND-PRO-STORE-$stamp.apbackup")
                        }, REQUEST_BACKUP_CREATE)
                    }.onFailure { info("تعذر إنشاء النسخة", backupError(it)) }
                }
            }.apply { isDaemon = true }.start()
        }
    }

    private fun selectPhoneBackup() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }, REQUEST_BACKUP_OPEN)
    }

    private fun importBackupFromPhone(uri: Uri) {
        info("فحص النسخة", "يجري قراءة ملف النسخة المشفرة والتحقق من حجمه وصيغته.")
        Thread {
            val result = runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= EncryptedBackupCodec.MAX_ENVELOPE_BYTES) { "ملف النسخة أكبر من الحد الآمن" }
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                } ?: error("تعذر فتح ملف النسخة")
            }
            runOnUiThread {
                result.onSuccess { envelope -> requestBackupRestore(envelope, "الهاتف") }
                    .onFailure { info("ملف غير صالح", backupError(it)) }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun uploadServerBackup() {
        if (!repo.isCentralActivationActive() || repo.centralAccessToken.isBlank()) {
            info("التفعيل مطلوب", "يلزم تفعيل هذا المحل مركزيًا قبل رفع نسخة إلى الخادم.")
            return
        }
        promptBackupPassword(confirm = true, title = "كلمة نسخة الخادم") { password ->
            info("جاري الرفع", "يجري تشفير نسخة جديدة داخل الهاتف ثم رفع النص المشفر فقط إلى خادم ATTEND PRO.")
            Thread {
                val result = runCatching {
                    val artifact = StoreBackupManager(this).createEncryptedBackup(password, BuildConfig.VERSION_NAME, includeFaceFiles = false)
                    require(artifact.envelope.toByteArray(Charsets.UTF_8).size <= MAX_SERVER_BACKUP_BYTES) {
                        "حجم بيانات المحل أكبر من حد النسخ على الخادم؛ استخدم نسخة الهاتف الكاملة"
                    }
                    CentralServerClient.uploadEncryptedBackup(
                        repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this),
                        artifact.envelope, BuildConfig.VERSION_NAME
                    ).getOrThrow() to artifact
                }
                password.fill('\u0000')
                runOnUiThread {
                    result.onSuccess { (receipt, artifact) ->
                        getSharedPreferences("attend_pro_backup_state_v2", MODE_PRIVATE).edit()
                            .putLong("lastServerUploadAt", receipt.createdAt)
                            .putString("lastServerBackupId", receipt.backupId).apply()
                        info("تم رفع النسخة ✓", backupSummaryText(artifact) + "\nيحتفظ الخادم بآخر ${receipt.retainedCount} نسخة مشفرة لهذا المحل.")
                    }.onFailure { info("تعذر رفع النسخة", backupError(it)) }
                }
            }.apply { isDaemon = true }.start()
        }
    }

    private fun downloadServerBackup() {
        if (!repo.isCentralActivationActive() || repo.centralAccessToken.isBlank()) {
            info("التفعيل مطلوب", "انقل تفعيل المحل إلى هذا الهاتف واعتمده أولًا، ثم استعد نسخته المشفرة.")
            return
        }
        info("الاتصال بالخادم", "يجري طلب أحدث نسخة مشفرة خاصة بهذا المحل.")
        Thread {
            val result = CentralServerClient.downloadLatestEncryptedBackup(
                repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this)
            )
            runOnUiThread {
                result.onSuccess { remote ->
                    if (!remote.available) info("لا توجد نسخة", "لم يُرفع لهذا المحل أي نسخة احتياطية بعد.")
                    else requestBackupRestore(remote.envelope, "الخادم")
                }.onFailure { info("تعذر تنزيل النسخة", backupError(it)) }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun requestBackupRestore(envelope: String, sourceLabel: String) {
        promptBackupPassword(confirm = false, title = "فتح نسخة $sourceLabel") { password ->
            info("التحقق من النسخة", "يجري فك التشفير محليًا والتحقق من هوية المحل وسلامة جميع البيانات قبل عرضها.")
            Thread {
                val result = runCatching { StoreBackupManager(this).preview(envelope, password) }
                runOnUiThread {
                    result.onSuccess { preview -> confirmRestore(envelope, password, preview, sourceLabel) }
                        .onFailure { password.fill('\u0000'); info("تعذر فتح النسخة", backupError(it)) }
                }
            }.apply { isDaemon = true }.start()
        }
    }

    private fun confirmRestore(envelope: String, password: CharArray, preview: StoreBackupPreview, sourceLabel: String) {
        val details = "المحل: ${preview.storeName}\n" +
            "تاريخ النسخة: ${formatBackupTime(preview.metadata.createdAt)}\n" +
            "الإصدار: ${preview.appVersion.ifBlank { "غير محدد" }}\n" +
            "الموظفون: ${preview.summary.employeeCount}\n" +
            "سجلات الحضور: ${preview.summary.attendanceCount}\n" +
            "الإعدادات: ${preview.summary.settingsCount}\n" +
            "صور ملفات الوجه: ${preview.summary.faceFileCount}\n\n" +
            "سيبقى تفعيل هذا الهاتف ومفاتيحه الأمنية كما هما. ستستبدل بيانات العمل الحالية ببيانات النسخة."
        AlertDialog.Builder(this).setTitle("تأكيد الاستعادة من $sourceLabel").setMessage(details)
            .setPositiveButton("استعادة", null).setNegativeButton("إلغاء") { _, _ -> password.fill('\u0000') }.create().also { dialog ->
                dialog.setOnCancelListener { password.fill('\u0000') }
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        dialog.dismiss()
                        info("جاري الاستعادة", "لا تغلق التطبيق حتى يكتمل التحقق والكتابة.")
                        Thread {
                            val restored = runCatching { StoreBackupManager(this).restore(envelope, password) }
                            password.fill('\u0000')
                            runOnUiThread {
                                restored.onSuccess { result ->
                                    AlertDialog.Builder(this).setTitle("اكتملت الاستعادة ✓")
                                        .setMessage("تمت استعادة ${result.summary.employeeCount} موظف و${result.summary.attendanceCount} سجل حضور. سيُعاد فتح التطبيق لتحديث جميع الشاشات.")
                                        .setCancelable(false).setPositiveButton("إعادة فتح التطبيق") { _, _ ->
                                            val launch = packageManager.getLaunchIntentForPackage(packageName)
                                            finishAffinity()
                                            if (launch != null) startActivity(launch)
                                        }.show()
                                }.onFailure { info("لم تتم الاستعادة", backupError(it)) }
                            }
                        }.apply { isDaemon = true }.start()
                    }
                }
                dialog.show()
            }
    }

    private fun promptBackupPassword(confirm: Boolean, title: String, ready: (CharArray) -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 8, 28, 4) }
        box.addView(UiKit.subtitle(this, p, "استخدم عبارة قوية من 8 أحرف على الأقل. لا تُرسل هذه الكلمة إلى الخادم ولا يمكن استرجاعها إذا فُقدت."))
        val first = UiKit.field(this, p, "كلمة النسخة الاحتياطية", true).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        box.addView(first)
        val second = if (confirm) UiKit.field(this, p, "تأكيد كلمة النسخة", true).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            box.addView(this)
        } else null
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(box).setPositiveButton("متابعة", null).setNegativeButton("إلغاء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = first.text.toString()
                if (value.length < 8 || value.isBlank()) { first.error = "أدخل 8 أحرف على الأقل"; return@setOnClickListener }
                if (second != null && value != second.text.toString()) { second.error = "الكلمتان غير متطابقتين"; return@setOnClickListener }
                val password = value.toCharArray()
                first.text?.clear(); second?.text?.clear(); dialog.dismiss(); ready(password)
            }
        }
        dialog.show()
    }

    private fun backupSummaryText(artifact: StoreBackupArtifact): String =
        "الموظفون: ${artifact.summary.employeeCount} • الحضور: ${artifact.summary.attendanceCount} • الإعدادات: ${artifact.summary.settingsCount}\n" +
            "الحجم المشفر: ${artifact.metadata.sizeBytes / 1024} KB"

    private fun backupError(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("password", true) || raw.contains("modified", true) -> "كلمة النسخة غير صحيحة أو تم تعديل الملف."
            raw.contains("another store", true) -> "هذه النسخة تخص محلًا آخر. انقل تفعيل المحل نفسه إلى هذا الهاتف أولًا."
            raw.contains("HTTP 404") -> "خدمة النسخ على الخادم لم تُفعّل بعد على هذا الخادم."
            raw.isNotBlank() -> raw.take(300)
            else -> "حدث خطأ غير متوقع أثناء معالجة النسخة."
        }
    }

    private fun formatBackupTime(value: Long): String = if (value <= 0L) "لا يوجد" else
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(value))

    private fun healthCheck() {
        val bt = getSystemService(BluetoothManager::class.java)?.adapter
        val bluetooth = when { bt == null -> "غير مدعوم"; !bt.isEnabled -> "متوقف"; else -> "يعمل" }
        val employees = repo.employees()
        val syncState = if (repo.serverUrl.isBlank()) "الخادم غير مربوط" else "الخادم مضبوط • ${repo.pendingEvents().size} عملية معلقة"
        val active = employees.filter { it.active }
        val customShiftCount = active.count { it.useCustomShift }
        val faceReady = active.count { it.faceTemplate.isNotBlank() && it.faceQualityScore >= 45 }
        val receivers = repo.authorizedReportReceivers()
        val msg = "معلومات المحل: ${if (repo.isStoreProfileComplete) "مكتملة" else "تحتاج إكمال"}\n" +
            "Bluetooth: $bluetooth\nGPS: ${if (repo.isGpsConfigured) "مضبوط • ${repo.gpsRadiusMeters}م" else "غير مضبوط"}\n" +
            t("الدوام العام: ", "General shift: ") + ShiftTimeCodec.formatRange(repo.shiftHour, repo.shiftMinute, repo.shiftEndHour, repo.shiftEndMinute) +
            (if (ShiftTimeCodec.isOvernight(repo.shiftHour, repo.shiftMinute, repo.shiftEndHour, repo.shiftEndMinute)) t(" • ليلي", " • overnight") else "") +
            t(" • سماح ${repo.graceMinutes} د\n", " • ${repo.graceMinutes} min grace\n") +
            "الموظفون النشطون: ${active.size} • دوام خاص: $customShiftCount\n" +
            "كلمة مرور: ${active.count { it.passwordHash.isNotBlank() }} • صوت: ${active.count { it.voicePhraseHash.isNotBlank() }} • وجه: ${active.count { it.faceTemplate.isNotBlank() }}\n" +
            "تحقق صوتي: ${active.count { it.voicePhraseHash.isNotBlank() }} • قوالب وجه جاهزة: $faceReady • بصمة خارجية: ${active.count { it.externalFingerprintId.isNotBlank() }}\n" +
            "هواتف التقارير: ${receivers.count { it.active }} مصرح / ${receivers.size} مسجل\n" +
            "التفعيل: ${if (repo.isCentralActivationActive()) "مركزي نشط" else "موقوف • يتطلب اعتماد إدارة النظام عبر الخادم"}\n$syncState"
        info("فحص جاهزية المحل", msg)
    }

    private fun isMockLocation(location: Location): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else @Suppress("DEPRECATION") location.isFromMockProvider

    @Suppress("MissingPermission")
    private fun bestLastLocation(manager: LocationManager): Location? {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        return providers.mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }.maxByOrNull { it.time }
    }

    @Suppress("MissingPermission")
    private fun requestFreshLocation(manager: LocationManager, callback: (Location?) -> Unit) {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) { callback(null); return }
        var finished = false
        var best: Location? = bestLastLocation(manager)?.takeIf { System.currentTimeMillis() - it.time <= 5 * 60_000L }
        lateinit var listener: LocationListener
        fun finish(value: Location?) {
            if (finished) return
            finished = true
            runCatching { manager.removeUpdates(listener) }
            callback(value)
        }
        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (System.currentTimeMillis() - location.time > 2 * 60_000L) return
                if (best == null || location.accuracy < best!!.accuracy) best = location
                if (location.hasAccuracy() && location.accuracy <= 40f) finish(location)
            }
            @Deprecated("Deprecated in Android") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        providers.forEach { runCatching { manager.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) } }
        Handler(Looper.getMainLooper()).postDelayed({ finish(best) }, 30_000L)
    }

    private fun baseRoot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; layoutDirection = if (AppLanguage.isEnglish(this@StoreSettingsActivity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
        setPadding(UiKit.dp(this@StoreSettingsActivity, 16), UiKit.dp(this@StoreSettingsActivity, 18), UiKit.dp(this@StoreSettingsActivity, 16), UiKit.dp(this@StoreSettingsActivity, 28)); setBackgroundColor(p.bg)
    }

    private fun info(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(AppLanguage.legacyUiText(this, title)).setMessage(AppLanguage.legacyUiText(this, message)).setPositiveButton(t("حسنًا", "OK"), null).show()
    }

    override fun onBackPressed() {
        if (advancedMode1977) showDashboard() else super.onBackPressed()
    }

    companion object {
        private const val REQUEST_REPORT_RECEIVER_QR = 9201
        private const val REQUEST_BACKUP_CREATE = 9301
        private const val REQUEST_BACKUP_OPEN = 9302
        private const val MAX_SERVER_BACKUP_BYTES = 1_500_000
    }

}
