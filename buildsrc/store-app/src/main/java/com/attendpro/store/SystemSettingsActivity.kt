package com.attendpro.store

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.attendpro.core.AppIntegrity1982
import com.attendpro.core.CentralServerClient
import com.attendpro.core.DeviceIdentity
import com.attendpro.core.AgentRecord
import com.attendpro.core.ManagedStoreRecord
import com.attendpro.core.NetworkTools
import com.attendpro.core.PairingProtocol
import com.attendpro.core.StoreRepository
import com.attendpro.core.UiKit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SystemSettingsActivity : Activity() {
    private var navigationScreen1977 = "GATEWAY"
    private lateinit var repo: StoreRepository
    private var ownerSessionCode: String? = null
    private val p by lazy { UiKit.palette(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (BuildConfig.ENFORCE_OFFICIAL_SIGNATURE && AppIntegrity1982.activeInstrumentationDetected()) {
            AlertDialog.Builder(this)
                .setTitle("تم إيقاف الإدارة الحساسة")
                .setMessage("تم اكتشاف جلسة فحص/تعديل نشطة. أُغلقت منطقة إدارة النظام لحماية الحسابات والصلاحيات.")
                .setCancelable(false)
                .setPositiveButton("إغلاق") { _, _ -> finish() }
                .show()
            return
        }
        repo = StoreRepository(this)
        repo.ensureCurrentStoreInManagement()
        if (intent?.getBooleanExtra("OWNER_ONLY_1978", false) == true) showOwnerOnlyEntry1978() else showGateway()
    }

    private fun baseRoot(title: String, subtitle: String): LinearLayout {
        window.statusBarColor = p.bg
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@SystemSettingsActivity, 18), UiKit.dp(this@SystemSettingsActivity, 20), UiKit.dp(this@SystemSettingsActivity, 18), UiKit.dp(this@SystemSettingsActivity, 34))
            setBackgroundColor(p.bg)
            addView(UiKit.title(this@SystemSettingsActivity, p, title, 25f).apply { gravity = Gravity.CENTER })
            addView(UiKit.subtitle(this@SystemSettingsActivity, p, subtitle).apply {
                gravity = Gravity.CENTER
                setPadding(0, UiKit.dp(this@SystemSettingsActivity, 4), 0, UiKit.dp(this@SystemSettingsActivity, 18))
            })
        }
    }

    private fun display(root: LinearLayout) {
        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
    }

    private fun showOwnerOnlyEntry1978() {
        navigationScreen1977 = "OWNER_GATE"
        val root = baseRoot("منطقة إدارة النظام", "وصول إداري محمي — مالك النظام أو الوكيل المعتمد")

        val ownerGate = UiKit.card(this, p, 12)
        ownerGate.addView(UiKit.sectionLabel(this, p, "إدارة النظام"))
        ownerGate.addView(UiKit.title(this, p, "مالك النظام", 19f))
        ownerGate.addView(UiKit.subtitle(this, p, "إدارة الوكلاء والمشتركين والصلاحيات والتفعيل المركزي. الدخول يتطلب رمز مالك النظام."))
        ownerGate.addView(UiKit.button(this, p, "دخول مالك النظام").apply { setOnClickListener { ownerLogin() } })
        root.addView(ownerGate)

        val agentGate = UiKit.card(this, p, 10)
        agentGate.addView(UiKit.sectionLabel(this, p, "بوابة الوكيل"))
        agentGate.addView(UiKit.title(this, p, "الوكيل المعتمد", 18f))
        agentGate.addView(UiKit.subtitle(this, p, "دخول منفصل بالرمز الممنوح من مالك النظام. تظهر للوكيل فقط الصلاحيات والعملاء المسموحون له."))
        agentGate.addView(UiKit.button(this, p, "دخول الوكيل المركزي", false).apply {
            setOnClickListener { startActivity(Intent(this@SystemSettingsActivity, SystemManagement1971Activity::class.java).putExtra("mode", "AGENT")) }
        })
        root.addView(agentGate)

        val note = UiKit.card(this, p, 8)
        note.addView(UiKit.subtitle(this, p, "هذه المنطقة مخفية عن الاستخدام اليومي للمحل، ولا تمنح أي صلاحية قبل التحقق من رمز المالك أو الوكيل."))
        root.addView(note)
        root.addView(UiKit.button(this, p, "رجوع", false).apply { setOnClickListener { finish() } })
        display(root)
    }

    private fun showGateway() {
        navigationScreen1977 = "GATEWAY"
        val root = baseRoot("إعدادات النظام", "منطقة الإدارة العليا — منفصلة عن التشغيل اليومي")

        val owner = UiKit.card(this, p)
        owner.addView(UiKit.sectionLabel(this, p, "مدير النظام"))
        owner.addView(UiKit.title(this, p, "مالك النظام", 21f))
        owner.addView(UiKit.subtitle(this, p, "تحكم كامل في الوكلاء والمحلات والصلاحيات وإعدادات التطبيق. الدخول برمز المالك فقط."))
        owner.addView(UiKit.button(this, p, "دخول إدارة النظام").apply { setOnClickListener { ownerLogin() } })
        root.addView(owner)

        val agent = UiKit.card(this, p)
        agent.addView(UiKit.sectionLabel(this, p, "بوابة الوكيل"))
        agent.addView(UiKit.title(this, p, "الوكيل", 21f))
        agent.addView(UiKit.subtitle(this, p, "يدخل الوكيل بالرمز الذي يمنحه المالك، ويستطيع تجهيز طلب محل جديد فقط وفق الصلاحيات المحددة."))
        agent.addView(UiKit.button(this, p, "دخول الوكيل المركزي", false).apply {
            setOnClickListener { startActivity(Intent(this@SystemSettingsActivity, SystemManagement1971Activity::class.java).putExtra("mode", "AGENT")) }
        })
        root.addView(agent)

        val note = UiKit.card(this, p, 13)
        note.addView(UiKit.subtitle(this, p, "رمز المالك لا يظهر داخل الواجهة ولا يُحفظ كنص صريح؛ يتم التحقق منه محليًا بصورة مشفرة، ويمكن تغييره من لوحة المالك."))
        root.addView(note)
        root.addView(UiKit.button(this, p, "رجوع", false).apply { setOnClickListener { finish() } })
        display(root)
    }

    private fun ownerLogin() {
        if (repo.ownerLockUntil > System.currentTimeMillis()) {
            toastDialog("حماية الدخول", "تم إيقاف محاولات دخول المالك مؤقتًا بعد محاولات خاطئة. حاول بعد دقيقة.")
            return
        }
        val input = UiKit.field(this, p, "رمز المالك").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        AlertDialog.Builder(this).setTitle("دخول المالك").setView(input).setPositiveButton("دخول") { _, _ ->
            val entered = input.text.toString()
            if (repo.verifyOwnerCode(entered)) { ownerSessionCode = entered; showOwnerDashboard() }
            else toastDialog("تعذر الدخول", "رمز المالك غير صحيح.")
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun ownerDashboardMode(): String = getSharedPreferences("attend_owner_ui", MODE_PRIVATE)
        .getString("dashboard_mode", "CLASSIC") ?: "CLASSIC"

    private fun setOwnerDashboardMode(value: String) {
        getSharedPreferences("attend_owner_ui", MODE_PRIVATE).edit().putString("dashboard_mode", value).apply()
    }

    private fun addOwnerDashboardModeSwitch(root: LinearLayout, current: String) {
        val box = UiKit.card(this, p, 10)
        box.addView(UiKit.sectionLabel(this, p, "شكل لوحة المالك"))
        box.addView(UiKit.subtitle(this, p, "يمكنك التبديل بين العرض الحالي وعرض الأقسام. يتم حفظ اختيارك تلقائيًا."))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        row.addView(UiKit.button(this, p, if (current == "CLASSIC") "● النموذج الحالي" else "النموذج الحالي", current != "CLASSIC").apply {
            setOnClickListener { setOwnerDashboardMode("CLASSIC"); showOwnerDashboardClassic() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(UiKit.button(this, p, if (current == "SECTIONS") "● نموذج الأقسام" else "نموذج الأقسام", current != "SECTIONS").apply {
            setOnClickListener { setOwnerDashboardMode("SECTIONS"); showOwnerDashboardSections() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(row)
        root.addView(box)
    }

    private fun showOwnerDashboard() {
        if (ownerDashboardMode() == "SECTIONS") showOwnerDashboardSections() else showOwnerDashboardClassic()
    }

    private fun showOwnerDashboardClassic() {
        navigationScreen1977 = "OWNER"
        repo.ensureCurrentStoreInManagement()
        val root = baseRoot("لوحة المالك", "مدير النظام الرئيسي • النموذج الحالي")
        addOwnerDashboardModeSwitch(root, "CLASSIC")

        val summary = UiKit.card(this, p)
        summary.addView(UiKit.sectionLabel(this, p, "ملخص الإدارة"))
        val activeAgents = repo.agents().count { it.active }
        val stores = repo.managedStores()
        val pending = stores.count { it.status == "PENDING" }
        summary.addView(UiKit.title(this, p, "الوكلاء $activeAgents   •   المحلات ${stores.size}", 18f))
        summary.addView(UiKit.subtitle(this, p, "طلبات بانتظار الموافقة: $pending   •   عمليات هذا الجهاز: ${repo.events().size}"))
        root.addView(summary)

        val access = UiKit.card(this, p)
        access.addView(UiKit.sectionLabel(this, p, "الصلاحيات والأمان"))
        access.addView(UiKit.button(this, p, "صلاحية مدير النظام (المالك)", false).apply { setOnClickListener { showOwnerAuthority() } })
        access.addView(UiKit.button(this, p, "تغيير رمز المالك", false).apply { setOnClickListener { changeOwnerCode() } })
        access.addView(UiKit.button(this, p, "إعادة تعيين رمز إدارة المحل", false).apply { setOnClickListener { resetStoreAdminPin() } })
        root.addView(access)

        val network = UiKit.card(this, p)
        network.addView(UiKit.sectionLabel(this, p, "الإدارة المركزية"))
        network.addView(UiKit.button(this, p, "إدارة الوكلاء المركزية").apply {
            setOnClickListener { startActivity(Intent(this@SystemSettingsActivity, SystemManagement1971Activity::class.java).putExtra("mode", "OWNER")) }
        })
        network.addView(UiKit.button(this, p, "إدارة النظام المركزية 1.9.74").apply {
            setOnClickListener { startActivity(Intent(this@SystemSettingsActivity, SystemManagement1971Activity::class.java).putExtra("mode", "OWNER")) }
        })
        network.addView(UiKit.button(this, p, "سجل المحلات والوكلاء (لا يفعّل التشغيل)", false).apply { setOnClickListener { showManagedStores() } })
        network.addView(UiKit.button(this, p, "مركز إدارة النظام والتفعيلات", false).apply { setOnClickListener { showCentralAdmin() } })
        root.addView(network)

        val app = UiKit.card(this, p)
        app.addView(UiKit.sectionLabel(this, p, "التحكم بالتطبيق"))
        app.addView(UiKit.button(this, p, "إعدادات التطبيق العامة", false).apply { setOnClickListener { showAppSettings() } })
        app.addView(UiKit.button(this, p, "المظهر والقوالب", false).apply { setOnClickListener { UiKit.showAppearancePicker(this@SystemSettingsActivity) } })
        app.addView(UiKit.button(this, p, "فتح إعدادات المحل", false).apply { setOnClickListener { startActivity(Intent(this@SystemSettingsActivity, StoreSettingsActivity::class.java)) } })
        app.addView(UiKit.button(this, p, "الخادم المركزي والمزامنة", false).apply { setOnClickListener { showSyncSettings() } })
        app.addView(UiKit.button(this, p, "فحص جاهزية النظام", false).apply { setOnClickListener { runHealthCheck() } })
        root.addView(app)

        root.addView(UiKit.button(this, p, "خروج من إدارة النظام", false).apply { setOnClickListener { showGateway() } })
        display(root)
    }

    private fun showOwnerDashboardSections(section: String = "SUMMARY") {
        navigationScreen1977 = "OWNER"
        repo.ensureCurrentStoreInManagement()
        val root = baseRoot("لوحة المالك", "مدير النظام الرئيسي • نموذج الأقسام")
        addOwnerDashboardModeSwitch(root, "SECTIONS")

        val nav = UiKit.card(this, p, 10)
        nav.addView(UiKit.sectionLabel(this, p, "أقسام لوحة المالك"))
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        row1.addView(UiKit.button(this, p, if (section == "SUMMARY") "● الملخص" else "الملخص", section != "SUMMARY").apply { setOnClickListener { showOwnerDashboardSections("SUMMARY") } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row1.addView(UiKit.button(this, p, if (section == "SECURITY") "● الصلاحيات والأمان" else "الصلاحيات والأمان", section != "SECURITY").apply { setOnClickListener { showOwnerDashboardSections("SECURITY") } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        row2.addView(UiKit.button(this, p, if (section == "CENTRAL") "● الإدارة المركزية" else "الإدارة المركزية", section != "CENTRAL").apply { setOnClickListener { showOwnerDashboardSections("CENTRAL") } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(UiKit.button(this, p, if (section == "APP") "● التطبيق" else "التطبيق", section != "APP").apply { setOnClickListener { showOwnerDashboardSections("APP") } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(row1); nav.addView(row2); root.addView(nav)

        when (section) {
            "SUMMARY" -> {
                val activeAgents = repo.agents().count { it.active }
                val stores = repo.managedStores()
                val pending = stores.count { it.status == "PENDING" }
                val summary = UiKit.card(this, p)
                summary.addView(UiKit.sectionLabel(this, p, "ملخص الإدارة"))
                summary.addView(UiKit.title(this, p, "${stores.size} محل   •   $activeAgents وكيل", 20f))
                summary.addView(UiKit.subtitle(this, p, "بانتظار الموافقة: $pending   •   عمليات هذا الجهاز: ${repo.events().size}"))
                summary.addView(UiKit.button(this, p, "فتح إدارة النظام المركزية").apply { setOnClickListener { startActivity(Intent(this@SystemSettingsActivity, SystemManagement1971Activity::class.java).putExtra("mode", "OWNER")) } })
                root.addView(summary)
            }
            "SECURITY" -> {
                val access = UiKit.card(this, p)
                access.addView(UiKit.sectionLabel(this, p, "الصلاحيات والأمان"))
                access.addView(UiKit.subtitle(this, p, "إدارة سلطة المالك ورموز الإدارة في مكان واحد."))
                access.addView(UiKit.button(this, p, "صلاحية مدير النظام (المالك)").apply { setOnClickListener { showOwnerAuthority() } })
                access.addView(UiKit.button(this, p, "تغيير رمز المالك", false).apply { setOnClickListener { changeOwnerCode() } })
                access.addView(UiKit.button(this, p, "إعادة تعيين رمز إدارة المحل", false).apply { setOnClickListener { resetStoreAdminPin() } })
                root.addView(access)
            }
            "CENTRAL" -> {
                val network = UiKit.card(this, p)
                network.addView(UiKit.sectionLabel(this, p, "الإدارة المركزية"))
                network.addView(UiKit.subtitle(this, p, "الوكلاء والمشتركون والتفعيلات والخادم المركزي."))
                network.addView(UiKit.button(this, p, "إدارة النظام المركزية 1.9.74").apply { setOnClickListener { startActivity(Intent(this@SystemSettingsActivity, SystemManagement1971Activity::class.java).putExtra("mode", "OWNER")) } })
                network.addView(UiKit.button(this, p, "إدارة الوكلاء المركزية", false).apply {
                    setOnClickListener { startActivity(Intent(this@SystemSettingsActivity, SystemManagement1971Activity::class.java).putExtra("mode", "OWNER")) }
                })
                network.addView(UiKit.button(this, p, "سجل المحلات والوكلاء", false).apply { setOnClickListener { showManagedStores() } })
                network.addView(UiKit.button(this, p, "مركز إدارة النظام والتفعيلات", false).apply { setOnClickListener { showCentralAdmin() } })
                root.addView(network)
            }
            "APP" -> {
                val app = UiKit.card(this, p)
                app.addView(UiKit.sectionLabel(this, p, "التحكم بالتطبيق"))
                app.addView(UiKit.subtitle(this, p, "الإعدادات العامة والمظهر والمزامنة وفحص الجاهزية."))
                app.addView(UiKit.button(this, p, "إعدادات التطبيق العامة").apply { setOnClickListener { showAppSettings() } })
                app.addView(UiKit.button(this, p, "المظهر والقوالب", false).apply { setOnClickListener { UiKit.showAppearancePicker(this@SystemSettingsActivity) } })
                app.addView(UiKit.button(this, p, "فتح إعدادات المحل", false).apply { setOnClickListener { startActivity(Intent(this@SystemSettingsActivity, StoreSettingsActivity::class.java)) } })
                app.addView(UiKit.button(this, p, "الخادم المركزي والمزامنة", false).apply { setOnClickListener { showSyncSettings() } })
                app.addView(UiKit.button(this, p, "فحص جاهزية النظام", false).apply { setOnClickListener { runHealthCheck() } })
                root.addView(app)
            }
        }

        root.addView(UiKit.button(this, p, "خروج من إدارة النظام", false).apply { setOnClickListener { showGateway() } })
        display(root)
    }

    private fun showCentralAdmin() {
        if (repo.serverUrl.isBlank() || repo.centralOwnerApiKey.isBlank()) {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
            val url = UiKit.field(this, p, "https://server.example.com").apply { setText(repo.serverUrl) }
            val key = UiKit.field(this, p, "مفتاح إدارة الخادم").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
            box.addView(UiKit.subtitle(this, p, "مفتاح إدارة الخادم مستقل عن رمز مالك التطبيق، ويجب إنشاؤه في إعدادات الخادم. يُحفظ مشفرًا في Android Keystore ولا يظهر بعد الحفظ."))
            box.addView(url); box.addView(key)
            val d = AlertDialog.Builder(this).setTitle("ربط لوحة إدارة النظام بالخادم").setView(box).setPositiveButton("حفظ ومتابعة", null).setNegativeButton("إلغاء", null).create()
            d.setOnShowListener {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val u = url.text.toString().trim(); val k = key.text.toString().trim()
                    if (!u.startsWith("https://")) { url.error = "يجب استخدام HTTPS"; return@setOnClickListener }
                    if (k.length < 32) { key.error = "استخدم مفتاح إدارة لا يقل عن 32 حرفًا"; return@setOnClickListener }
                    repo.serverUrl = u; repo.centralOwnerApiKey = k; d.dismiss(); showCentralAdminMenu()
                }
            }
            d.show(); return
        }
        showCentralAdminMenu()
    }

    private fun showCentralAdminMenu() = showSystemManagementCenter()

    private fun showSystemManagementCenter() {
        navigationScreen1977 = "CENTRAL"
        val root = baseRoot("مركز إدارة نظام البصمة", "كل الطلبات • التفعيل • الاستعادة التلقائية • المسجلون • سجل الإدارة")

        val summary = UiKit.card(this, p)
        summary.addView(UiKit.sectionLabel(this, p, "حالة النظام"))
        val summaryTitle = UiKit.title(this, p, "جاري تحميل ملخص الخادم...", 18f)
        val summaryText = UiKit.subtitle(this, p, "سيظهر هنا عدد الاشتراكات والأجهزة وطلبات التفعيل.")
        summary.addView(summaryTitle); summary.addView(summaryText)
        root.addView(summary)

        val activations = UiKit.card(this, p)
        activations.addView(UiKit.sectionLabel(this, p, "التفعيل والاشتراكات"))
        activations.addView(UiKit.button(this, p, "التفعيلات والاشتراكات").apply { setOnClickListener { loadCentralStores() } })
        activations.addView(UiKit.button(this, p, "طلبات التفعيل المعلقة", false).apply { setOnClickListener { loadCentralPendingRequests() } })
        activations.addView(UiKit.button(this, p, "سجل جميع طلبات التفعيل", false).apply { setOnClickListener { loadActivationHistory(false) } })
        activations.addView(UiKit.button(this, p, "الطلبات المؤرشفة / المحذوفة من العرض", false).apply { setOnClickListener { loadActivationHistory(true) } })
        root.addView(activations)

        val registry = UiKit.card(this, p)
        registry.addView(UiKit.sectionLabel(this, p, "المسجلون والاستعادة"))
        registry.addView(UiKit.subtitle(this, p, "يتعرف التطبيق تلقائيًا على نفس الجهاز المفعل سابقًا بواسطة بصمة جهاز ثابتة وآمنة. ويمكن لصاحب النظام إيقاف الاستعادة التلقائية أو تعطيل كل الاستعادة لأي اشتراك."))
        registry.addView(UiKit.button(this, p, "إدارة المسجلين على الخادم", false).apply { setOnClickListener { loadCentralStores() } })
        registry.addView(UiKit.button(this, p, "السياسة العامة للاستعادة", false).apply { setOnClickListener { showGlobalRecoveryPolicy() } })
        registry.addView(UiKit.button(this, p, "إشعارات الأجهزة التي تمت استعادتها", false).apply { setOnClickListener { loadRecoveryNotifications(showAll = true) } })
        root.addView(registry)

        val audit = UiKit.card(this, p)
        audit.addView(UiKit.sectionLabel(this, p, "الرقابة والأمان"))
        audit.addView(UiKit.button(this, p, "سجل عمليات إدارة النظام", false).apply { setOnClickListener { loadSystemAudit() } })
        audit.addView(UiKit.button(this, p, "تغيير رابط / مفتاح إدارة الخادم", false).apply {
            setOnClickListener { repo.centralOwnerApiKey = ""; showCentralAdmin() }
        })
        root.addView(audit)

        root.addView(UiKit.button(this, p, "رجوع إلى لوحة المالك", false).apply { setOnClickListener { showOwnerDashboard() } })
        display(root)
        loadRecoveryNotifications(showAll = false)

        Thread {
            val result = CentralServerClient.systemOverview(repo.serverUrl, repo.centralOwnerApiKey)
            runOnUiThread {
                if (result.isFailure) {
                    summaryTitle.text = "تعذر تحميل ملخص الخادم"
                    summaryText.text = result.exceptionOrNull()?.message ?: "تحقق من الاتصال ومفتاح الإدارة"
                } else {
                    val x = result.getOrThrow()
                    summaryTitle.text = "${x.activeStores} نشط • ${x.pendingActivations} طلب معلق • ${x.registeredEmployees} هاتف موظف"
                    summaryText.text = "الإجمالي ${x.totalStores} • موقوف ${x.suspendedStores} • مؤرشف ${x.archivedStores} • منتهي ${x.expiredStores} • تصاريح استعادة نشطة ${x.recoveryGrants}\nالخادم: ${x.serverVersion.ifBlank { "متصل" }}"
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun loadCentralPendingRequests() {
        toastDialog("الخادم المركزي", "جاري تحميل طلبات التفعيل المعلقة...")
        Thread {
            val result = CentralServerClient.listPendingActivations(repo.serverUrl, repo.centralOwnerApiKey)
            runOnUiThread {
                if (result.isFailure) { toastDialog("تعذر الاتصال بالخادم", result.exceptionOrNull()?.message ?: "خطأ"); return@runOnUiThread }
                val requests = result.getOrThrow()
                val labels = requests.map { "${it.storeName} • ${subscriptionArabic(it.subscriptionType)} • ${it.ownerName.ifBlank { "بلا اسم" }} • ${it.requestId.takeLast(8)}" }.toMutableList()
                if (requests.isEmpty()) labels.add("لا توجد طلبات تفعيل معلقة")
                AlertDialog.Builder(this).setTitle("طلبات التفعيل المركزي (${requests.size})").setItems(labels.toTypedArray()) { _, which ->
                    if (requests.isNotEmpty()) approveCentralRequest(requests[which])
                }.setPositiveButton("رجوع") { _, _ -> showCentralAdminMenu() }.setNegativeButton("إغلاق", null).show()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun loadCentralStores() {
        toastDialog("الخادم المركزي", "جاري تحميل المحلات المفعلة...")
        Thread {
            val result = CentralServerClient.listCentralStores(repo.serverUrl, repo.centralOwnerApiKey)
            runOnUiThread {
                if (result.isFailure) { toastDialog("تعذر الاتصال بالخادم", result.exceptionOrNull()?.message ?: "خطأ"); return@runOnUiThread }
                val stores = result.getOrThrow()
                val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val labels = stores.map { s ->
                    val state = if (s.status == "ACTIVE" && s.expiresAt > System.currentTimeMillis()) "● نشط" else "○ ${s.status}"
                    "$state • ${s.storeName} • ${s.branchId} • حتى ${if (s.expiresAt > 0) fmt.format(Date(s.expiresAt)) else "-"}"
                }.toMutableList()
                if (stores.isEmpty()) labels.add("لا توجد محلات مفعلة على الخادم")
                AlertDialog.Builder(this).setTitle("المحلات المركزية (${stores.size})").setItems(labels.toTypedArray()) { _, which ->
                    if (stores.isNotEmpty()) centralStoreActions(stores[which])
                }.setPositiveButton("رجوع") { _, _ -> showCentralAdminMenu() }.setNegativeButton("إغلاق", null).show()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun centralStoreActions(store: CentralServerClient.CentralStore) {
        val now = System.currentTimeMillis()
        val active = store.status == "ACTIVE" && store.expiresAt > now
        val archived = store.status == "ARCHIVED"
        val actions = arrayOf(
            "تفاصيل الاشتراك والجهاز",
            "تجديد / تعديل الاشتراك",
            "إصدار تصريح استعادة لمرة واحدة",
            "سياسة استعادة التفعيل",
            "المسجلون من أجهزة الموظفين",
            if (active) "تعليق الوصول مؤقتًا" else "إعادة تفعيل الوصول",
            if (archived) "إلغاء الأرشفة وإعادة التفعيل" else "أرشفة التفعيل القديم",
            "إلغاء جميع تصاريح الاستعادة الحالية",
            "فصل جهاز المحل من الخادم مع حفظ الاشتراك",
            "سجل عمليات هذا الاشتراك",
            "حذف المحل وبياناته نهائيًا من الخادم"
        )
        AlertDialog.Builder(this)
            .setTitle(store.storeName)
            .setMessage("الحالة: ${store.status} • الاستعادة: ${recoveryModeArabic(store.recoveryMode)}")
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showCentralStoreDetails(store)
                    1 -> renewCentralStore(store)
                    2 -> issueRecoveryGrant(store)
                    3 -> showRecoveryModePicker(store)
                    4 -> loadServerEmployees(store)
                    5 -> Thread {
                        val r = CentralServerClient.suspendStore(repo.serverUrl, repo.centralOwnerApiKey, store.storeId, suspended = active)
                        runOnUiThread { if (r.isSuccess) toastDialog("تم", if (active) "تم تعليق الوصول لهذا الاشتراك." else "تمت إعادة تفعيل الوصول.") else toastDialog("تعذر التحديث", r.exceptionOrNull()?.message ?: "خطأ") }
                    }.apply { isDaemon = true }.start()
                    6 -> if (!archived) {
                        AlertDialog.Builder(this).setTitle("أرشفة التفعيل القديم")
                            .setMessage("سيتم إيقاف الاستخدام التشغيلي مع الاحتفاظ بسجلات الحضور والتقارير.")
                            .setPositiveButton("أرشفة") { _, _ -> setStoreArchived(store, true) }
                            .setNegativeButton("إلغاء", null).show()
                    } else setStoreArchived(store, false)
                    7 -> revokeRecoveryGrants(store)
                    8 -> confirmResetStoreDevice(store)
                    9 -> loadSystemAudit(store.storeId)
                    10 -> confirmDeleteStore(store)
                }
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showCentralStoreDetails(store: CentralServerClient.CentralStore) {
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val text = buildString {
            append("المحل: ${store.storeName}\nالفرع: ${store.branchId}\nالمعرف: ${store.storeId}\n")
            append("صاحب الاشتراك: ${store.ownerName.ifBlank { "غير مذكور" }}\n")
            if (store.phone.isNotBlank()) append("الهاتف: ${store.phone}\n")
            append("نوع الاشتراك: ${subscriptionArabic(store.subscriptionType)}\nالحالة: ${store.status}\n")
            append("انتهاء الاشتراك: ${if (store.expiresAt > 0) fmt.format(Date(store.expiresAt)) else "-"}\n")
            append("حد الموظفين: ${store.maxEmployees}\n")
            append("سياسة الاستعادة: ${recoveryModeArabic(store.recoveryMode)}\n")
            append("آخر اتصال: ${if (store.lastSeenAt > 0) fmt.format(Date(store.lastSeenAt)) else "لم يسجل"}\n")
            append("آخر استعادة: ${if (store.lastRecoveredAt > 0) fmt.format(Date(store.lastRecoveredAt)) else "لا توجد"}\n")
            append("رقم الترخيص: ${store.licenseId.ifBlank { "-" }}")
        }
        toastDialog("تفاصيل التفعيل", text)
    }

    private fun recoveryModeArabic(mode: String): String = when (mode) {
        "TRUSTED_AUTO" -> "استعادة تلقائية للجهاز الموثوق"
        "DISABLED" -> "الاستعادة معطلة"
        else -> "بموافقة صاحب النظام فقط"
    }

    private fun showRecoveryModePicker(store: CentralServerClient.CentralStore) {
        val values = arrayOf("OWNER_APPROVAL", "TRUSTED_AUTO", "DISABLED")
        val labels = arrayOf("موافقة صاحب النظام فقط", "استعادة تلقائية للجهاز الموثوق", "تعطيل الاستعادة بالكامل")
        val checked = values.indexOf(store.recoveryMode).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("سياسة استعادة التفعيل — ${store.storeName}")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                Thread {
                    val r = CentralServerClient.setRecoveryMode(repo.serverUrl, repo.centralOwnerApiKey, store.storeId, values[which])
                    runOnUiThread { if (r.isSuccess) toastDialog("تم", "تم ضبط سياسة الاستعادة على: ${labels[which]}") else toastDialog("تعذر الحفظ", r.exceptionOrNull()?.message ?: "خطأ") }
                }.apply { isDaemon = true }.start()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun revokeRecoveryGrants(store: CentralServerClient.CentralStore) {
        AlertDialog.Builder(this).setTitle("إلغاء تصاريح الاستعادة")
            .setMessage("سيتم إلغاء جميع رموز الاستعادة غير المستخدمة لهذا الاشتراك. لن تتأثر بيانات المحل أو الاشتراك.")
            .setPositiveButton("إلغاء التصاريح") { _, _ ->
                Thread {
                    val r = CentralServerClient.revokeRecoveryGrants(repo.serverUrl, repo.centralOwnerApiKey, store.storeId)
                    runOnUiThread { if (r.isSuccess) toastDialog("تم", "أُلغيت تصاريح الاستعادة الحالية.") else toastDialog("تعذر الإلغاء", r.exceptionOrNull()?.message ?: "خطأ") }
                }.apply { isDaemon = true }.start()
            }.setNegativeButton("رجوع", null).show()
    }

    private fun loadServerEmployees(store: CentralServerClient.CentralStore) {
        toastDialog("المسجلون على الخادم", "جاري تحميل أجهزة الموظفين المسجلة...")
        Thread {
            val r = CentralServerClient.listServerEmployeeRegistrations(repo.serverUrl, repo.centralOwnerApiKey, store.storeId)
            runOnUiThread {
                if (r.isFailure) { toastDialog("تعذر التحميل", r.exceptionOrNull()?.message ?: "خطأ"); return@runOnUiThread }
                val rows = r.getOrThrow()
                val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                val labels = rows.map { x -> "${x.employeeName} • آخر ظهور ${if (x.lastSeenAt > 0) fmt.format(Date(x.lastSeenAt)) else "-"} • ${x.installationId.takeLast(8)}" }.toMutableList()
                if (rows.isEmpty()) labels += "لا توجد أجهزة موظفين مسجلة لهذا المحل"
                AlertDialog.Builder(this).setTitle("المسجلون — ${store.storeName} (${rows.size})").setItems(labels.toTypedArray()) { _, which ->
                    if (rows.isNotEmpty()) confirmDeleteEmployeeRegistration(store, rows[which])
                }.setPositiveButton("رجوع") { _, _ -> centralStoreActions(store) }.setNegativeButton("إغلاق", null).show()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun confirmDeleteEmployeeRegistration(store: CentralServerClient.CentralStore, employee: CentralServerClient.ServerEmployeeRegistration) {
        AlertDialog.Builder(this).setTitle("حذف تسجيل هاتف الموظف")
            .setMessage("الموظف: ${employee.employeeName}\nسيُحذف تسجيل الهاتف من الخادم فقط، وسيحتاج الهاتف إلى إعادة الربط. لا تُحذف سجلات الحضور السابقة.")
            .setPositiveButton("حذف التسجيل") { _, _ ->
                Thread {
                    val r = CentralServerClient.deleteServerEmployeeRegistration(repo.serverUrl, repo.centralOwnerApiKey, store.storeId, employee.employeeId)
                    runOnUiThread { if (r.isSuccess) toastDialog("تم", "تم حذف تسجيل هاتف ${employee.employeeName} من الخادم.") else toastDialog("تعذر الحذف", r.exceptionOrNull()?.message ?: "خطأ") }
                }.apply { isDaemon = true }.start()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun confirmResetStoreDevice(store: CentralServerClient.CentralStore) {
        val input = UiKit.field(this, p, "اكتب: فصل الجهاز")
        val d = AlertDialog.Builder(this).setTitle("فصل جهاز المحل من الخادم")
            .setMessage("سيتم إبطال رمز وصول الجهاز الحالي مع الاحتفاظ بالاشتراك، الموظفين، الحضور والتقارير. بعد ذلك يحتاج جهاز المحل إلى استعادة معتمدة أو تفعيل جديد لنفس الاشتراك.")
            .setView(input).setPositiveButton("فصل الجهاز", null).setNegativeButton("إلغاء", null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (input.text.toString().trim() != "فصل الجهاز") { input.error = "اكتب العبارة كما هي"; return@setOnClickListener }
            d.dismiss()
            Thread {
                val r = CentralServerClient.resetStoreDevice(repo.serverUrl, repo.centralOwnerApiKey, store.storeId)
                runOnUiThread { if (r.isSuccess) toastDialog("تم فصل الجهاز", "بقي الاشتراك وبياناته محفوظة على الخادم، وأصبح الجهاز السابق غير مصرح.") else toastDialog("تعذر الفصل", r.exceptionOrNull()?.message ?: "خطأ") }
            }.apply { isDaemon = true }.start()
        } }; d.show()
    }

    private fun confirmDeleteStore(store: CentralServerClient.CentralStore) {
        val input = UiKit.field(this, p, "اكتب: حذف نهائي")
        val d = AlertDialog.Builder(this).setTitle("حذف نهائي من الخادم")
            .setMessage("تحذير: هذا يحذف سجل المحل من الخادم وكل البيانات التابعة له على الخادم، بما فيها تسجيلات الموظفين والحضور والتقارير السحابية. استخدم الأرشفة أو فصل الجهاز بدلًا منه إذا كنت تريد الاحتفاظ بالبيانات.")
            .setView(input).setPositiveButton("حذف نهائي", null).setNegativeButton("إلغاء", null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (input.text.toString().trim() != "حذف نهائي") { input.error = "اكتب العبارة كما هي"; return@setOnClickListener }
            d.dismiss()
            Thread {
                val r = CentralServerClient.deleteStoreFromServer(repo.serverUrl, repo.centralOwnerApiKey, store.storeId)
                runOnUiThread { if (r.isSuccess) toastDialog("تم الحذف", "حُذف المحل وبياناته التابعة من الخادم.") else toastDialog("تعذر الحذف", r.exceptionOrNull()?.message ?: "خطأ") }
            }.apply { isDaemon = true }.start()
        } }; d.show()
    }

    private fun loadActivationHistory(includeArchived: Boolean = false) {
        toastDialog("سجل التفعيل", "جاري تحميل جميع طلبات التفعيل...")
        Thread {
            val r = CentralServerClient.listActivationRecordsAdmin(repo.serverUrl, repo.centralOwnerApiKey, includeArchived)
            runOnUiThread {
                if (r.isFailure) { toastDialog("تعذر التحميل", r.exceptionOrNull()?.message ?: "خطأ"); return@runOnUiThread }
                val rows = r.getOrThrow()
                val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                val labels = rows.map { x ->
                    val archived = if (x.hiddenAt > 0L) "[مؤرشف] " else ""
                    "$archived${x.storeName} • ${activationStatusArabic(x.status)} • ${subscriptionArabic(x.subscriptionType)} • ${if (x.createdAt > 0) fmt.format(Date(x.createdAt)) else "-"}"
                }.toMutableList()
                if (rows.isEmpty()) labels += "لا توجد طلبات في هذا السجل"
                AlertDialog.Builder(this).setTitle(if (includeArchived) "كل الطلبات بما فيها المؤرشفة (${rows.size})" else "سجل جميع الطلبات (${rows.size})")
                    .setItems(labels.toTypedArray()) { _, which -> if (rows.isNotEmpty()) activationRecordActions(rows[which]) }
                    .setPositiveButton("رجوع") { _, _ -> showSystemManagementCenter() }.setNegativeButton("إغلاق", null).show()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun activationStatusArabic(status: String): String = when (status.uppercase(Locale.US)) {
        "PENDING" -> "معلق"
        "APPROVED", "ACTIVE" -> "مقبول"
        "REJECTED" -> "مرفوض"
        "SUPERSEDED" -> "مستبدل"
        "CANCELLED" -> "ملغي"
        else -> status
    }

    private fun activationRecordActions(record: CentralServerClient.ActivationRecord) {
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val details = buildString {
            append("المحل: ${record.storeName}\nالمعرف: ${record.storeId}\nصاحب الاشتراك: ${record.ownerName.ifBlank { "غير مذكور" }}\n")
            append("النوع: ${subscriptionArabic(record.subscriptionType)}\nالحالة: ${activationStatusArabic(record.status)}\n")
            append("تاريخ الطلب: ${if (record.createdAt > 0) fmt.format(Date(record.createdAt)) else "-"}\n")
            append("سياسة الاستعادة: ${if (record.recoveryMode.isBlank()) "لا يوجد تفعيل مرتبط" else recoveryModeArabic(record.recoveryMode)}\n")
            append("حالة المحل: ${record.storeStatus.ifBlank { "-" }}\n")
            append("آخر استعادة: ${if (record.lastRecoveredAt > 0) fmt.format(Date(record.lastRecoveredAt)) else "لا توجد"}\n")
            append("السبب: ${record.reason.ifBlank { "-" }}")
        }
        val actions = mutableListOf<String>()
        actions += "عرض التفاصيل"
        if (record.status == "PENDING") actions += "رفض الطلب"
        if (record.storeStatus.isNotBlank()) actions += "إدارة الاشتراك والاستعادة"
        actions += if (record.hiddenAt > 0L) "إعادة الطلب إلى السجل" else "أرشفة الطلب من السجل"
        actions += "حذف الطلب نهائيًا من سجل الطلبات"
        AlertDialog.Builder(this).setTitle(record.storeName).setItems(actions.toTypedArray()) { _, which ->
            val action = actions[which]
            when (action) {
                "عرض التفاصيل" -> toastDialog("تفاصيل طلب التفعيل", details)
                "رفض الطلب" -> Thread {
                    val r = CentralServerClient.setActivationRequestStatus(repo.serverUrl, repo.centralOwnerApiKey, record.requestId, "REJECTED", "رفضه صاحب النظام")
                    runOnUiThread { if (r.isSuccess) toastDialog("تم", "تم رفض طلب التفعيل.") else toastDialog("تعذر الرفض", r.exceptionOrNull()?.message ?: "خطأ") }
                }.apply { isDaemon = true }.start()
                "إدارة الاشتراك والاستعادة" -> openStoreFromRecord(record.storeId)
                "أرشفة الطلب من السجل", "إعادة الطلب إلى السجل" -> {
                    val archived = action.startsWith("أرشفة")
                    Thread {
                        val r = CentralServerClient.archiveActivationRequest(repo.serverUrl, repo.centralOwnerApiKey, record.requestId, archived)
                        runOnUiThread { if (r.isSuccess) toastDialog("تم", if (archived) "تمت أرشفة الطلب مع بقاء سجل التدقيق." else "عاد الطلب إلى السجل.") else toastDialog("تعذر التحديث", r.exceptionOrNull()?.message ?: "خطأ") }
                    }.apply { isDaemon = true }.start()
                }
                "حذف الطلب نهائيًا من سجل الطلبات" -> confirmDeleteActivationRequest(record)
            }
        }.setNegativeButton("إغلاق", null).show()
    }

    private fun openStoreFromRecord(storeId: String) {
        Thread {
            val r = CentralServerClient.listCentralStores(repo.serverUrl, repo.centralOwnerApiKey)
            runOnUiThread {
                val store = r.getOrNull()?.firstOrNull { it.storeId == storeId }
                if (store == null) toastDialog("الاشتراك", "لا يوجد اشتراك حالي مرتبط بهذا الطلب.") else centralStoreActions(store)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun confirmDeleteActivationRequest(record: CentralServerClient.ActivationRecord) {
        val input = UiKit.field(this, p, "اكتب: حذف الطلب")
        val d = AlertDialog.Builder(this).setTitle("حذف طلب التفعيل من الخادم")
            .setMessage("سيتم حذف سجل الطلب المحدد فقط. لا يحذف هذا المحل أو سجلات الحضور. الطلب المقبول المرتبط باشتراك نشط لا يسمح الخادم بحذفه؛ استخدم الأرشفة بدلًا منه.")
            .setView(input).setPositiveButton("حذف", null).setNegativeButton("إلغاء", null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (input.text.toString().trim() != "حذف الطلب") { input.error = "اكتب العبارة كما هي"; return@setOnClickListener }
            d.dismiss()
            Thread {
                val r = CentralServerClient.deleteActivationRequest(repo.serverUrl, repo.centralOwnerApiKey, record.requestId)
                runOnUiThread { if (r.isSuccess) toastDialog("تم الحذف", "تم حذف طلب التفعيل من سجل الطلبات على الخادم.") else toastDialog("تعذر الحذف", r.exceptionOrNull()?.message ?: "خطأ") }
            }.apply { isDaemon = true }.start()
        } }; d.show()
    }

    private fun showGlobalRecoveryPolicy() {
        val modes = arrayOf("TRUSTED_AUTO", "OWNER_APPROVAL", "DISABLED")
        val labels = arrayOf("استعادة تلقائية لنفس الجهاز المفعل سابقًا", "الاستعادة بتصريح صاحب النظام فقط", "إيقاف كل الاستعادة")
        AlertDialog.Builder(this).setTitle("السياسة العامة للاستعادة")
            .setMessage("يطبق الخيار على جميع الاشتراكات الحالية. الاستعادة التلقائية تعتمد بصمة ثابتة خاصة بنفس الجهاز والتوقيع الرسمي، ولا تنقل الاشتراك إلى جهاز مختلف.")
            .setItems(labels) { _, which ->
                AlertDialog.Builder(this).setTitle("تأكيد السياسة العامة").setMessage("تطبيق: ${labels[which]} على جميع الاشتراكات الحالية؟")
                    .setPositiveButton("تطبيق") { _, _ ->
                        Thread {
                            val r = CentralServerClient.setAllRecoveryModes(repo.serverUrl, repo.centralOwnerApiKey, modes[which])
                            runOnUiThread { if (r.isSuccess) toastDialog("تم", "تم تحديث ${r.getOrThrow()} اشتراكًا.") else toastDialog("تعذر التحديث", r.exceptionOrNull()?.message ?: "خطأ") }
                        }.apply { isDaemon = true }.start()
                    }.setNegativeButton("إلغاء", null).show()
            }.setNegativeButton("إغلاق", null).show()
    }

    private fun loadRecoveryNotifications(showAll: Boolean) {
        val prefs = getSharedPreferences("attend_pro_system_admin", MODE_PRIVATE)
        val after = if (showAll) 0L else prefs.getLong("last_recovery_notice_id", 0L)
        Thread {
            val r = CentralServerClient.listRecoveryNotifications(repo.serverUrl, repo.centralOwnerApiKey, after, if (showAll) 100 else 20)
            runOnUiThread {
                if (r.isFailure) { if (showAll) toastDialog("تعذر تحميل إشعارات الاستعادة", r.exceptionOrNull()?.message ?: "خطأ"); return@runOnUiThread }
                val rows = r.getOrThrow()
                if (rows.isEmpty()) { if (showAll) toastDialog("إشعارات الاستعادة", "لا توجد عمليات استعادة مسجلة."); return@runOnUiThread }
                val maxId = rows.maxOf { it.id }
                prefs.edit().putLong("last_recovery_notice_id", maxId).apply()
                val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                val text = rows.sortedByDescending { it.id }.joinToString("\n\n") { x ->
                    "${x.storeName}\nالجهاز: ${x.deviceLabel.ifBlank { "Android" }}\nتمت الاستعادة: ${if (x.createdAt > 0) fmt.format(Date(x.createdAt)) else "الآن"}"
                }
                toastDialog(if (showAll) "سجل استعادة الأجهزة" else "تنبيه إدارة النظام — تمت استعادة جهاز", text)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun loadSystemAudit(storeId: String = "") {
        toastDialog("سجل الإدارة", "جاري تحميل آخر العمليات...")
        Thread {
            val r = CentralServerClient.listSystemAudit(repo.serverUrl, repo.centralOwnerApiKey, storeId)
            runOnUiThread {
                if (r.isFailure) { toastDialog("تعذر التحميل", r.exceptionOrNull()?.message ?: "خطأ"); return@runOnUiThread }
                val rows = r.getOrThrow()
                val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                val text = if (rows.isEmpty()) "لا توجد عمليات مسجلة" else rows.joinToString("\n\n") { x ->
                    "${if (x.createdAt > 0) fmt.format(Date(x.createdAt)) else "-"} • ${x.action}\n${x.details.take(180)}"
                }
                toastDialog(if (storeId.isBlank()) "سجل إدارة النظام" else "سجل الاشتراك", text)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun issueRecoveryGrant(store: CentralServerClient.CentralStore) {
        toastDialog("تصريح الاستعادة", "جاري إنشاء تصريح استخدام واحد...")
        Thread {
            val result = CentralServerClient.createRecoveryGrant(
                repo.serverUrl, repo.centralOwnerApiKey, store.storeId
            )
            runOnUiThread {
                if (result.isFailure) {
                    toastDialog("تعذر إنشاء التصريح", result.exceptionOrNull()?.message ?: "خطأ")
                    return@runOnUiThread
                }
                val grant = result.getOrThrow()
                val until = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(grant.expiresAt))
                AlertDialog.Builder(this)
                    .setTitle("تصريح استعادة — ${store.storeName}")
                    .setMessage(
                        "رمز الاستعادة:\n${grant.code}\n\n" +
                            "صالح حتى $until ويُستخدم مرة واحدة فقط.\n" +
                            "أدخل الرمز في جهاز المحل الجديد من «استعادة بتصريح صاحب النظام»."
                    )
                    .setPositiveButton("نسخ الرمز") { _, _ -> copyText(grant.code) }
                    .setNegativeButton("إغلاق", null)
                    .show()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun setStoreArchived(store: CentralServerClient.CentralStore, archived: Boolean) {
        Thread {
            val result = CentralServerClient.archiveStore(
                repo.serverUrl, repo.centralOwnerApiKey, store.storeId, archived
            )
            runOnUiThread {
                if (result.isSuccess) {
                    toastDialog(
                        "تم",
                        if (archived) "تمت أرشفة التفعيل مع الاحتفاظ بالسجلات."
                        else "تم إلغاء الأرشفة وإعادة التفعيل."
                    )
                } else toastDialog("تعذر تحديث الأرشفة", result.exceptionOrNull()?.message ?: "خطأ")
            }
        }.apply { isDaemon = true }.start()
    }

    private fun renewCentralStore(store: CentralServerClient.CentralStore) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
        val days = UiKit.field(this, p, "مدة التجديد بالأيام", true).apply { setText("365") }
        val max = UiKit.field(this, p, "الحد الأقصى للموظفين", true).apply { setText(store.maxEmployees.toString()) }
        box.addView(UiKit.subtitle(this, p, "سيبدأ التجديد من وقت الاعتماد الحالي ويعيد حالة المحل إلى ACTIVE."))
        box.addView(days); box.addView(max)
        val d = AlertDialog.Builder(this).setTitle("تجديد ${store.storeName}").setView(box).setPositiveButton("تجديد", null).setNegativeButton("إلغاء", null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val dd = days.text.toString().toIntOrNull()?.coerceIn(1, 3650) ?: 365
            val mm = max.text.toString().toIntOrNull()?.coerceIn(1, 10000) ?: store.maxEmployees.coerceAtLeast(1)
            d.dismiss()
            Thread {
                val r = CentralServerClient.renewStore(repo.serverUrl, repo.centralOwnerApiKey, store.storeId, dd, mm)
                runOnUiThread { if (r.isSuccess) toastDialog("تم التجديد ✓", "تم تجديد المحل وتحديث حد الموظفين. سيستلم جهاز المحل الصلاحية الجديدة عند التحقق التالي.") else toastDialog("تعذر التجديد", r.exceptionOrNull()?.message ?: "خطأ") }
            }.apply { isDaemon = true }.start()
        } }
        d.show()
    }

    private fun approveCentralRequest(req: CentralServerClient.PendingActivation) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
        val suggestedDays = when (req.subscriptionType) { "MONTHLY" -> 30; "TRIAL_3_DAYS" -> 3; else -> 365 }
        val days = UiKit.field(this, p, "مدة التفعيل بالأيام", true).apply { setText(suggestedDays.toString()) }
        val max = UiKit.field(this, p, "الحد الأقصى للموظفين", true).apply { setText("100") }
        box.addView(UiKit.subtitle(this, p, buildString {
            append("صاحب المحل: ${req.ownerName.ifBlank { "غير مذكور" }}\nالمحل: ${req.storeName}\nنوع الاشتراك: ${subscriptionArabic(req.subscriptionType)}\n")
            if (req.phone.isNotBlank()) append("الهاتف: ${req.phone}\n")
            if (req.address.isNotBlank()) append("العنوان: ${req.address}\n")
            if (req.commercialId.isNotBlank()) append("السجل التجاري: ${req.commercialId}\n")
            if (req.notes.isNotBlank()) append("ملاحظات: ${req.notes}\n")
            append("الفرع: ${req.branchId}\nمعرف الجهاز: ${req.storeId}\nرقم الطلب: ${req.requestId}")
        }))
        box.addView(days); box.addView(max)
        val d = AlertDialog.Builder(this).setTitle("اعتماد التفعيل عبر الخادم").setView(box).setPositiveButton("اعتماد", null).setNegativeButton("إلغاء", null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val dd = days.text.toString().toIntOrNull()?.coerceIn(1, 3650) ?: 365
                val mm = max.text.toString().toIntOrNull()?.coerceIn(1, 10000) ?: 100
                d.dismiss(); toastDialog("اعتماد التفعيل", "جاري اعتماد المحل على الخادم...")
                Thread {
                    val r = CentralServerClient.approveActivation(repo.serverUrl, repo.centralOwnerApiKey, req.requestId, dd, mm)
                    runOnUiThread {
                        if (r.isSuccess) {
                            repo.upsertManagedStore(ManagedStoreRecord(req.storeId, req.storeName, req.branchId, status = "APPROVED", maxEmployees = mm, activationIssuedAt = System.currentTimeMillis()))
                            toastDialog("تم الاعتماد ✓", "تم تفعيل المحل مركزيًا. على جهاز المحل الضغط على «فحص حالة طلب التفعيل المركزي» لاستلام الترخيص الآمن.")
                        } else toastDialog("تعذر الاعتماد", r.exceptionOrNull()?.message ?: "خطأ")
                    }
                }.apply { isDaemon = true }.start()
            }
        }
        d.show()
    }

    private fun subscriptionArabic(type: String): String = when (type) {
        "MONTHLY" -> "شهري"
        "TRIAL_3_DAYS" -> "تجربة 3 أيام"
        else -> "سنوي"
    }

    private fun showOwnerAuthority() {
        toastDialog("صلاحية مدير النظام — المالك", "• إضافة الوكلاء وإيقافهم وتغيير رموزهم\n• إضافة المحلات واعتماد طلبات الوكلاء وتعليقها\n• متابعة بيانات الاستخدام المتاحة من المزامنة\n• التحكم بطرق الحضور وBLE وتطبيق الموظف\n• التحكم بالدوام والفروع وأجهزة البصمة والخادم\n• تغيير رمز المالك\n\nلا يستطيع الوكيل تغيير صلاحيات المالك أو اعتماد نفسه.")
    }

    private fun changeOwnerCode() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
        val a = UiKit.field(this, p, "رمز مالك جديد — 8 أحرف/أرقام على الأقل").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val b = UiKit.field(this, p, "تأكيد الرمز الجديد").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        box.addView(a); box.addView(b)
        AlertDialog.Builder(this).setTitle("تغيير رمز المالك").setView(box).setPositiveButton("حفظ") { _, _ ->
            val code = a.text.toString().trim()
            if (code.length < 8 || code != b.text.toString().trim()) toastDialog("لم يتم التغيير", "يجب أن يتطابق الرمزان وألا يقل الرمز عن 8 أحرف/أرقام.")
            else { repo.setOwnerCode(code); ownerSessionCode = code; toastDialog("تم", "تم تغيير رمز المالك بنجاح. استخدم الرمز الجديد من الآن.") }
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun resetStoreAdminPin() {
        if (!repo.hasStoreAdminPin) {
            toastDialog("رمز إدارة المحل", "إدارة المحل غير محمية برمز حاليًا.")
            return
        }
        AlertDialog.Builder(this).setTitle("إعادة تعيين رمز إدارة المحل")
            .setMessage("سيتم حذف رمز الحماية المحلي لإدارة المحل. ويمكن لمالك المحل إنشاء رمز جديد من إعدادات المحل.")
            .setPositiveButton("إعادة التعيين") { _, _ -> repo.clearStoreAdminPin(); toastDialog("تم", "تمت إعادة تعيين رمز إدارة المحل.") }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun showAgents() {
        val agents = repo.agents()
        val labels = mutableListOf("＋ إضافة وكيل جديد")
        labels.addAll(agents.map { "${if (it.active) "●" else "○"} ${it.name}   •   ${it.agentId.takeLast(6)}" })
        AlertDialog.Builder(this).setTitle("إدارة الوكلاء (${agents.size})").setItems(labels.toTypedArray()) { _, which ->
            if (which == 0) addAgent() else agentDetails(agents[which - 1])
        }.setNegativeButton("إغلاق", null).show()
    }

    private fun addAgent() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
        val name = UiKit.field(this, p, "اسم الوكيل")
        val custom = UiKit.field(this, p, "رمز مخصص — اختياري، اتركه فارغًا للتوليد التلقائي")
        box.addView(name); box.addView(custom)
        AlertDialog.Builder(this).setTitle("إضافة وكيل").setView(box).setPositiveButton("إنشاء") { _, _ ->
            val n = name.text.toString().trim()
            if (n.isBlank()) { toastDialog("تنبيه", "اكتب اسم الوكيل."); return@setPositiveButton }
            val code = custom.text.toString().trim().ifBlank { generateAgentCode() }
            if (code.length < 6) { toastDialog("تنبيه", "رمز الوكيل يجب ألا يقل عن 6 أحرف/أرقام."); return@setPositiveButton }
            val record = AgentRecord("AG-${UUID.randomUUID()}", n, PairingProtocol.pinHash(code), true)
            repo.upsertAgent(record)
            showNewAgentCode(record, code)
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun generateAgentCode(): String = "AP-" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.US)

    private fun showNewAgentCode(agent: AgentRecord, code: String) {
        AlertDialog.Builder(this).setTitle("تم إنشاء الوكيل").setMessage("الوكيل: ${agent.name}\n\nرمز الوكيل:\n$code\n\nيظهر الرمز الكامل الآن فقط. أرسله للوكيل واحفظه في مكان آمن.")
            .setPositiveButton("نسخ الرمز") { _, _ -> copyText(code) }
            .setNegativeButton("إغلاق", null).show()
    }

    private fun agentDetails(agent: AgentRecord) {
        val assigned = repo.managedStores().count { it.agentId == agent.agentId }
        val items = arrayOf(
            "الحالة: ${if (agent.active) "نشط" else "موقوف"}",
            "المحلات المرتبطة: $assigned",
            if (agent.active) "إيقاف الوكيل" else "إعادة تفعيل الوكيل",
            "إنشاء رمز جديد للوكيل",
            "حذف الوكيل"
        )
        AlertDialog.Builder(this).setTitle(agent.name).setItems(items) { _, which ->
            when (which) {
                2 -> { repo.upsertAgent(agent.copy(active = !agent.active)); showAgents() }
                3 -> {
                    val code = generateAgentCode(); val updated = agent.copy(codeHash = PairingProtocol.pinHash(code), active = true)
                    repo.upsertAgent(updated); showNewAgentCode(updated, code)
                }
                4 -> AlertDialog.Builder(this).setTitle("حذف الوكيل؟").setMessage("لن تُحذف سجلات المحلات، لكن سيُحذف حساب الوكيل.").setPositiveButton("حذف") { _, _ -> repo.removeAgent(agent.agentId); showAgents() }.setNegativeButton("إلغاء", null).show()
            }
        }.setNegativeButton("إغلاق", null).show()
    }

    private fun agentLogin() {
        val code = UiKit.field(this, p, "رمز الوكيل").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        AlertDialog.Builder(this).setTitle("دخول الوكيل").setView(code).setPositiveButton("دخول") { _, _ ->
            val agent = repo.findAgentByCode(code.text.toString().trim())
            if (agent == null) toastDialog("تعذر الدخول", "رمز الوكيل غير صحيح أو أن الوكيل موقوف.") else showAgentDashboard(agent)
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun showAgentDashboard(agent: AgentRecord) {
        navigationScreen1977 = "AGENT"
        val root = baseRoot("بوابة الوكيل", "${agent.name} • صلاحيات محدودة بموافقة المالك")
        val info = UiKit.card(this, p)
        info.addView(UiKit.title(this, p, agent.name, 20f))
        info.addView(UiKit.subtitle(this, p, "يمكنك تجهيز طلب محل جديد ومتابعة المحلات التابعة لك. الاعتماد النهائي بيد المالك."))
        root.addView(info)
        val stores = repo.managedStores().filter { it.agentId == agent.agentId }
        val actions = UiKit.card(this, p)
        actions.addView(UiKit.button(this, p, "إضافة طلب محل جديد").apply { setOnClickListener { agentAddStore(agent) } })
        actions.addView(UiKit.button(this, p, "محلاتي (${stores.size})", false).apply { setOnClickListener { showAgentStores(agent) } })
        root.addView(actions)
        root.addView(UiKit.button(this, p, "خروج", false).apply { setOnClickListener { showGateway() } })
        display(root)
    }

    private fun agentAddStore(agent: AgentRecord) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
        val name = UiKit.field(this, p, "اسم المحل")
        val branch = UiKit.field(this, p, "رمز الفرع").apply { setText("MAIN") }
        box.addView(name); box.addView(branch)
        AlertDialog.Builder(this).setTitle("طلب محل جديد").setView(box).setPositiveButton("إرسال للمالك") { _, _ ->
            val n = name.text.toString().trim()
            if (n.isBlank()) { toastDialog("تنبيه", "اسم المحل مطلوب."); return@setPositiveButton }
            repo.upsertManagedStore(ManagedStoreRecord("REQ-${UUID.randomUUID()}", n, branch.text.toString().trim().ifBlank { "MAIN" }, agent.agentId, "PENDING"))
            toastDialog("تم", "تم إنشاء طلب المحل. حالته: بانتظار موافقة المالك.")
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun showAgentStores(agent: AgentRecord) {
        val stores = repo.managedStores().filter { it.agentId == agent.agentId }
        val text = if (stores.isEmpty()) "لا توجد محلات بعد" else stores.joinToString("\n\n") { "• ${it.name} / ${it.branchId}\nالحالة: ${storeStatusAr(it.status)}" }
        toastDialog("محلات ${agent.name}", text)
    }

    private fun showManagedStores() {
        repo.ensureCurrentStoreInManagement()
        val stores = repo.managedStores()
        val labels = mutableListOf("＋ إضافة محل بواسطة المالك")
        labels.addAll(stores.map { "${statusDot(it.status)} ${it.name} • ${it.branchId} • ${storeStatusAr(it.status)}" })
        AlertDialog.Builder(this).setTitle("سجل المحلات المحلي (${stores.size}) • لا يمنح تفعيلًا").setItems(labels.toTypedArray()) { _, which ->
            if (which == 0) ownerAddStore() else managedStoreDetails(stores[which - 1])
        }.setNegativeButton("إغلاق", null).show()
    }

    private fun ownerAddStore() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
        val name = UiKit.field(this, p, "اسم المحل")
        val branch = UiKit.field(this, p, "رمز الفرع").apply { setText("MAIN") }
        box.addView(name); box.addView(branch)
        AlertDialog.Builder(this).setTitle("إضافة محل").setView(box).setPositiveButton("إضافة واعتماد") { _, _ ->
            val n = name.text.toString().trim(); if (n.isBlank()) return@setPositiveButton
            repo.upsertManagedStore(ManagedStoreRecord("STORE-${UUID.randomUUID()}", n, branch.text.toString().trim().ifBlank { "MAIN" }, "", "APPROVED"))
            showManagedStores()
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun managedStoreDetails(store: ManagedStoreRecord) {
        val agent = repo.agents().firstOrNull { it.agentId == store.agentId }?.name ?: if (store.agentId.isBlank()) "المالك" else "وكيل غير موجود"
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val last = if (store.lastSeenAt > 0) fmt.format(Date(store.lastSeenAt)) else "لم تصل مزامنة استخدام بعد"
        val licenseState = when {
            store.licenseId.isBlank() -> "لم يصدر تفعيل"
            store.licenseExpiresAt <= System.currentTimeMillis() -> "الترخيص منتهي"
            else -> "الترخيص حتى ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(store.licenseExpiresAt))} • حد ${store.maxEmployees} موظف"
        }
        val items = mutableListOf(
            "الوكيل/المنشئ: $agent",
            "الحالة: ${storeStatusAr(store.status)}",
            "التفعيل: $licenseState",
            "آخر استخدام معروف: $last",
            "عدد العمليات المعروف: ${store.usageCount}"
        )
        if (store.status == "PENDING") items.add("✓ الموافقة على المحل")
        if (store.status == "APPROVED") items.add("تعليق سجل المحل")
        if (store.status == "SUSPENDED") items.add("إعادة تفعيل المحل")
        items.add("حذف سجل المحل")
        AlertDialog.Builder(this).setTitle(store.name).setItems(items.toTypedArray()) { _, which ->
            val selected = items[which]
            when {
                selected.startsWith("✓") -> { repo.upsertManagedStore(store.copy(status = "APPROVED")); toastDialog("تم اعتماد السجل", "تم اعتماد السجل الإداري المحلي فقط. تشغيل جهاز المحل لا يتم إلا من «مركز إدارة النظام والتفعيلات»."); showManagedStores() }
                selected == "تعليق سجل المحل" -> { repo.upsertManagedStore(store.copy(status = "SUSPENDED")); toastDialog("تم تعليق السجل", "تم تعليق السجل الإداري المحلي فقط. لإيقاف تشغيل جهاز محل بعيد استخدم إدارة الخادم المركزي."); showManagedStores() }
                selected == "إعادة تفعيل المحل" -> { repo.upsertManagedStore(store.copy(status = "APPROVED")); showManagedStores() }
                selected == "حذف سجل المحل" -> { repo.removeManagedStore(store.managedStoreId); showManagedStores() }
            }
        }.setNegativeButton("إغلاق", null).show()
    }

    private fun showLicensingCenter() {
        AlertDialog.Builder(this)
            .setTitle("التفعيل المركزي فقط")
            .setMessage("تم إلغاء إصدار التراخيص المحلية من التطبيق. اعتماد المحلات وتعليقها وتجديد صلاحيتها يتم حصريًا عبر الخادم المركزي باستخدام مفتاح إدارة النظام.")
            .setPositiveButton("فتح إدارة الخادم") { _, _ -> showCentralAdmin() }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showAppSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0) }
        val auto = CheckBox(this).apply { text = "⌁ تشغيل اكتشاف BLE/Wi‑Fi تلقائيًا"; setTextColor(p.text); isChecked = repo.autoScan }
        val companion = CheckBox(this).apply { text = "◎ السماح بتطبيق الموظف والهاتف المرتبط"; setTextColor(p.text); isChecked = repo.allowEmployeeCompanion }
        val bio = CheckBox(this).apply { text = "◎ اشتراط بصمة/وجه الهاتف عند استخدام التحقق البيومتري"; setTextColor(p.text); isChecked = repo.requirePhoneBiometric }
        val qr = CheckBox(this).apply { text = "▦ السماح بـ QR المباشر"; setTextColor(p.text); isChecked = repo.allowQrAttendance }
        listOf(auto, companion, bio, qr).forEach { box.addView(it) }
        box.addView(UiKit.button(this, p, "المظهر والقوالب", false).apply { setOnClickListener { UiKit.showAppearancePicker(this@SystemSettingsActivity) } })
        AlertDialog.Builder(this).setTitle("إعدادات التطبيق").setView(box).setPositiveButton("حفظ") { _, _ ->
            repo.autoScan = auto.isChecked; repo.allowEmployeeCompanion = companion.isChecked || qr.isChecked; repo.requirePhoneBiometric = bio.isChecked; repo.allowQrAttendance = qr.isChecked
            repo.allowPinFallback = false; repo.allowPatternFallback = false
            toastDialog("تم", "تم حفظ إعدادات التطبيق.")
        }.setNegativeButton("إلغاء", null).show()
}

    private fun showStoreSettings() {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 10) }
        scroll.addView(box)
        val name = UiKit.field(this, p, "اسم المحل").apply { setText(repo.storeName) }
        val branch = UiKit.field(this, p, "رمز الفرع").apply { setText(repo.branchId) }
        val phone = UiKit.field(this, p, "هاتف المحل - اختياري").apply { setText(repo.storePhone) }
        val address = UiKit.field(this, p, "العنوان - اختياري").apply { setText(repo.storeAddress) }
        val manager = UiKit.field(this, p, "اسم مدير المحل - اختياري").apply { setText(repo.storeManagerName) }
        val commercial = UiKit.field(this, p, "السجل / المعرف التجاري - اختياري").apply { setText(repo.storeCommercialId) }
        val notes = UiKit.field(this, p, "ملاحظات المحل - اختياري").apply { setText(repo.storeNotes); minLines = 2 }
        listOf(name, branch, phone, address, manager, commercial, notes).forEach { box.addView(it) }

        val dialog = AlertDialog.Builder(this).setTitle("معلومات المحل وإعداداته").setView(scroll)
            .setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val storeName = name.text.toString().trim()
                val branchId = branch.text.toString().trim()
                if (storeName.isBlank() || branchId.isBlank()) {
                    name.error = if (storeName.isBlank()) "اسم المحل مطلوب" else null
                    branch.error = if (branchId.isBlank()) "رمز الفرع مطلوب" else null
                    return@setOnClickListener
                }
                repo.storeName = storeName
                repo.branchId = branchId
                repo.storePhone = phone.text.toString()
                repo.storeAddress = address.text.toString()
                repo.storeManagerName = manager.text.toString()
                repo.storeCommercialId = commercial.text.toString()
                repo.storeNotes = notes.text.toString()
                repo.ensureCurrentStoreInManagement()
                dialog.dismiss()
                toastDialog("تم", "تم حفظ معلومات المحل بنجاح.")
            }
        }
        dialog.show()
    }

    private fun showShiftSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
        val hour = UiKit.field(this, p, "ساعة البداية 0-23", true).apply { setText(repo.shiftHour.toString()) }
        val minute = UiKit.field(this, p, "الدقيقة", true).apply { setText(repo.shiftMinute.toString()) }
        val grace = UiKit.field(this, p, "دقائق السماح", true).apply { setText(repo.graceMinutes.toString()) }
        listOf(hour, minute, grace).forEach { box.addView(it) }
        AlertDialog.Builder(this).setTitle("الدوام والسماح").setView(box).setPositiveButton("حفظ") { _, _ ->
            repo.shiftHour = hour.text.toString().toIntOrNull() ?: 8; repo.shiftMinute = minute.text.toString().toIntOrNull() ?: 0; repo.graceMinutes = grace.text.toString().toIntOrNull() ?: 10
            toastDialog("تم", "تم تحديث إعداد الدوام.")
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun showFingerprintSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
        val host = UiKit.field(this, p, "IP جهاز البصمة").apply { setText(repo.fingerprintHost) }
        val port = UiKit.field(this, p, "Port مثل 4370", true).apply { setText(repo.fingerprintPort.toString()) }
        box.addView(host); box.addView(port)
        AlertDialog.Builder(this).setTitle("قارئ البصمة الخارجي").setView(box).setPositiveButton("حفظ واختبار") { _, _ ->
            repo.fingerprintHost = host.text.toString(); repo.fingerprintPort = port.text.toString().toIntOrNull() ?: 4370
            Thread { val result = NetworkTools.probeTcp(repo.fingerprintHost, repo.fingerprintPort); runOnUiThread { toastDialog("نتيجة الاتصال", if (result.isSuccess) "تم الاتصال بالجهاز بنجاح. جلب السجلات يحتاج Driver حسب الشركة والموديل." else "تعذر الاتصال: ${result.exceptionOrNull()?.message ?: "خطأ"}") } }.start()
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun showSyncSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
        val url = UiKit.field(this, p, "https://your-server.example.com").apply { setText(repo.serverUrl) }
        box.addView(UiKit.subtitle(this, p, "هذا إعداد خاص بإدارة النظام. التفعيل لا يُصدر محليًا؛ جهاز المحل يستلم صلاحية مركزية مرتبطة بهويته من الخادم فقط."))
        box.addView(url)
        val status = TextView(this).apply {
            setTextColor(p.muted); textSize = 14f; gravity = Gravity.CENTER
            text = "غير مزامنة: ${repo.pendingEvents().size}${if (repo.lastSyncMessage.isNotBlank()) " • ${repo.lastSyncMessage}" else ""}"
            setPadding(0, 10, 0, 6)
        }
        box.addView(status)
        AlertDialog.Builder(this).setTitle("الخادم المركزي والمزامنة").setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                val value = url.text.toString().trim()
                if (value.isNotBlank() && !value.startsWith("https://")) toastDialog("تنبيه", "لأمان بيانات الحضور يجب أن يبدأ عنوان الخادم بـ https://")
                else { repo.serverUrl = value; toastDialog("تم", "تم حفظ عنوان الخادم. إذا كان الخادم منشورًا ومفعّلًا ستعمل المزامنة والتقارير عن بُعد مباشرة.") }
            }
            .setNeutralButton("مزامنة الآن") { _, _ -> syncNow() }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun syncNow() {
        if (repo.serverUrl.isBlank()) { toastDialog("المزامنة", "لم يتم تحديد خادم مركزي بعد."); return }
        if (!repo.isActivationActive()) { toastDialog("المزامنة", "فعّل هذا الجهاز أولًا قبل المزامنة المركزية."); return }
        val pending = repo.pendingEvents()
        Thread {
            val result = CentralServerClient.syncEventsCentral(repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this), pending)
            if (repo.isCentralActivationActive()) CentralServerClient.confirmedTransfers(repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this)).getOrNull()?.forEach { repo.confirmReportTransferTrusted(it) }
            runOnUiThread {
                if (result.isSuccess) {
                    repo.markSynced(result.getOrThrow())
                    repo.lastSyncAt = System.currentTimeMillis(); repo.lastSyncMessage = "تمت المزامنة"
                    toastDialog("تمت المزامنة", if (result.getOrThrow().isEmpty()) "تم فحص الخادم، ولا توجد عمليات جديدة." else "تم إرسال ${result.getOrThrow().size} عملية بنجاح.")
                } else {
                    repo.lastSyncMessage = "فشلت: ${result.exceptionOrNull()?.message ?: "خطأ"}"
                    toastDialog("تعذر المزامنة", repo.lastSyncMessage)
                }
            }
        }.start()
    }

    private fun runHealthCheck() {
        repo.ensureCurrentStoreInManagement()
        val bt = getSystemService(BluetoothManager::class.java)?.adapter
        val bluetooth = when { bt == null -> "غير مدعوم"; !bt.isEnabled -> "متوقف"; else -> "يعمل" }
        val employees = repo.employees()
        val faces = employees.count { it.faceProfileRef.isNotBlank() }
        val pins = employees.count { it.pin.isNotBlank() }
        val external = employees.count { it.externalFingerprintId.isNotBlank() }
        val msg = buildString {
            append("معلومات المحل: ${if (repo.isStoreProfileComplete) "مكتملة" else "تحتاج إكمال"}\n")
            append("Bluetooth: $bluetooth\n")
            append("الموظفون النشطون: ${employees.count { it.active }}\n")
            append("صور وجه أولية: $faces\n")
            append("كلمات المرور المفعلة: ${repo.employees().count { it.passwordHash.isNotBlank() }}\n")
            append("مرتبطون بقارئ بصمة: $external\n")
            append("الوكلاء النشطون: ${repo.agents().count { it.active }}\n")
            append("المحلات المسجلة: ${repo.managedStores().size}\n")
            append("أحداث غير مزامنة: ${repo.pendingEvents().size}\n")
            append("التفعيل المركزي: ${if (repo.isCentralActivationActive()) "نشط" else repo.centralServerStatus}\n")
            append("مهلة العمل دون اتصال: ${if (repo.centralLeaseUntil > System.currentTimeMillis()) "صالحة" else "تحتاج تحقق"}\n")
            append("الفرع: ${repo.branchId}")
        }
        toastDialog("فحص جاهزية النظام", msg)
    }

    private fun statusDot(status: String) = when (status) { "APPROVED" -> "●"; "PENDING" -> "◐"; "SUSPENDED" -> "○"; else -> "•" }
    private fun storeStatusAr(status: String) = when (status) { "APPROVED" -> "معتمد"; "PENDING" -> "بانتظار موافقة المالك"; "SUSPENDED" -> "معلق"; else -> status }

    private fun copyText(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("ATTEND PRO", text))
        toastDialog("تم النسخ", "تم نسخ الرمز.")
    }

    private fun toastDialog(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("حسنًا", null).show()
    }

    override fun onBackPressed() {
        val ownerOnly = intent?.getBooleanExtra("OWNER_ONLY_1978", false) == true
        when (navigationScreen1977) {
            "CENTRAL" -> showOwnerDashboard()
            "OWNER", "AGENT" -> if (ownerOnly) showOwnerOnlyEntry1978() else showGateway()
            "OWNER_GATE" -> finish()
            else -> super.onBackPressed()
        }
    }

}
