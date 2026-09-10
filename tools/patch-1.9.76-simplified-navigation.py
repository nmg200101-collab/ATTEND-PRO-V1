from pathlib import Path


def must_replace(text: str, old: str, new: str, label: str, count: int = 1) -> str:
    if text.count(old) < count:
        raise SystemExit(f'missing anchor for {label}')
    return text.replace(old, new, count)

# ---------------- Store settings: simple hub + advanced page kept intact ----------------
ss = Path('buildsrc/store-app/src/main/java/com/attendpro/store/StoreSettingsActivity.kt')
s = ss.read_text(encoding='utf-8')
if 'import android.widget.TextView' not in s:
    s = s.replace('import android.widget.ScrollView\n', 'import android.widget.ScrollView\nimport android.widget.TextView\n', 1)
s = must_replace(s, '    private fun showDashboard() {\n', '    private fun showAdvancedDashboard1975() {\n', 'rename old store dashboard')
anchor = '    private fun showAdvancedDashboard1975() {\n'
new_store_dashboard = r'''    private fun showDashboard() {
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
        hint.addView(UiKit.subtitle(this, p, "اختر القسم الذي تحتاجه فقط. جميع التفاصيل القديمة ما زالت موجودة داخل «الإعدادات المتقدمة» في قائمة ⋮.").apply { gravity = Gravity.CENTER })
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

    private fun showStoreTopMenu1976() {
        val items = arrayOf("الإشعارات", "دليل مستخدم إدارة المحل", "الإعدادات المتقدمة")
        AlertDialog.Builder(this).setTitle("القائمة").setItems(items) { _, which ->
            when (which) {
                0 -> startActivity(Intent(this, StoreMessages1975Activity::class.java))
                1 -> showStoreUserGuide1976()
                2 -> showAdvancedDashboard1975()
            }
        }.show()
    }

    private fun showStoreUserGuide1976() {
        AlertDialog.Builder(this)
            .setTitle("دليل مستخدم إدارة المحل")
            .setMessage("1. الموظفون: أضف الموظف بالبيانات الأساسية، ثم جهّز الوجه أو الهاتف أو أي طريقة تحقق تحتاجها.\n\n2. الحضور والتشغيل: عدّل الدوام والموقع وطرق الحضور من قسم واحد.\n\n3. الصوت والرسائل: تحكم في نطق جهاز المحل وتنبيه هاتف الموظف واستقبل الرسائل.\n\n4. التقارير والحماية: افتح التقارير وحماية الإدارة وفحص الجاهزية.\n\n5. قائمة ⋮ أعلى الشاشة: الإشعارات، دليل المستخدم، والإعدادات المتقدمة.\n\nملاحظة: إعدادات Bluetooth وQR والاقتران تعمل كما هي ولا تحتاج تعديلًا أثناء الاستخدام العادي.")
            .setPositiveButton("حسنًا", null)
            .show()
    }

    private fun showStoreOperations1976() {
        val items = arrayOf("طرق الحضور", "الدوام ودقائق السماح", "موقع المحل وGPS", "قارئ البصمة الخارجي")
        AlertDialog.Builder(this).setTitle("الحضور والتشغيل").setItems(items) { _, which ->
            when (which) {
                0 -> attendanceMethodsSettings()
                1 -> shiftSettings()
                2 -> gpsSettings()
                3 -> fingerprintSettings()
            }
        }.show()
    }

    private fun showStoreCommunication1976() {
        val items = arrayOf("التحكم الصوتي", "الرسائل والإشعارات")
        AlertDialog.Builder(this).setTitle("الصوت والرسائل").setItems(items) { _, which ->
            when (which) {
                0 -> startActivity(Intent(this, StoreVoiceControl1975Activity::class.java))
                1 -> startActivity(Intent(this, StoreMessages1975Activity::class.java))
            }
        }.show()
    }

    private fun showStoreReportsSecurity1976() {
        val items = arrayOf("التقارير والمشاركة", "هواتف استلام التقارير", if (repo.hasStoreAdminPin) "تغيير رمز إدارة المحل" else "إنشاء رمز حماية", "فحص جاهزية المحل", "المظهر والقوالب")
        AlertDialog.Builder(this).setTitle("التقارير والحماية").setItems(items) { _, which ->
            when (which) {
                0 -> startActivity(Intent(this, ReportsActivity::class.java).putExtra(ReportsActivity.EXTRA_STORE_ADMIN_SESSION, sessionToken))
                1 -> manageReportReceivers()
                2 -> changeStorePin()
                3 -> healthCheck()
                4 -> UiKit.showAppearancePicker(this)
            }
        }.show()
    }

'''
s = s.replace(anchor, new_store_dashboard + anchor, 1)
ss.write_text(s, encoding='utf-8')

# ---------------- Store main: top menu + simple employee editor ----------------
sm = Path('buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt')
s = sm.read_text(encoding='utf-8')
header_anchor = '        val header = UiKit.heroCard(this, p, 11)\n        header.addView(UiKit.title(this, p, "ATTEND PRO", 21f).apply {\n'
header_new = '''        val header = UiKit.heroCard(this, p, 11)\n        header.addView(TextView(this).apply {\n            text = "⋮"\n            textSize = 28f\n            gravity = Gravity.CENTER\n            setTextColor(android.graphics.Color.WHITE)\n            contentDescription = "القائمة"\n            layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@MainActivity, 48), UiKit.dp(this@MainActivity, 44)).apply { gravity = Gravity.END }\n            setOnClickListener { showStoreMainMenu1976() }\n        })\n        header.addView(UiKit.title(this, p, "ATTEND PRO", 21f).apply {\n'''
s = must_replace(s, header_anchor, header_new, 'store main top menu')

menu_anchor = '    private fun buildEmployeeManagerUi() {\n'
store_menu_helpers = r'''    private fun showStoreMainMenu1976() {
        val items = arrayOf("الإشعارات", "دليل مستخدم إدارة المحل", "الإعدادات")
        AlertDialog.Builder(this).setTitle("القائمة").setItems(items) { _, which ->
            when (which) {
                0 -> startActivity(Intent(this, StoreMessages1975Activity::class.java))
                1 -> showStoreUserGuide1976()
                2 -> startActivity(Intent(this, StoreSettingsActivity::class.java))
            }
        }.show()
    }

    private fun showStoreUserGuide1976() {
        AlertDialog.Builder(this)
            .setTitle("دليل مستخدم إدارة المحل")
            .setMessage("• الشاشة الرئيسية: لمتابعة الحضور والأجهزة المتصلة فقط.\n\n• إدارة المحل: افتحها لإضافة الموظفين أو تعديل الدوام والصوت والتقارير.\n\n• الإشعارات: رسائل إدارة النظام وردود الموظفين تظهر في مركز الرسائل.\n\n• الربط: يتم من مركز الاتصال عند الحاجة، وبعد نجاحه لا تحتاج للدخول إليه يوميًا.\n\n• قائمة ⋮: منها الإشعارات والدليل والإعدادات.")
            .setPositiveButton("حسنًا", null)
            .show()
    }

'''
s = s.replace(menu_anchor, store_menu_helpers + menu_anchor, 1)
s = must_replace(s, '''        val header = UiKit.card(this, p)
        header.addView(UiKit.sectionLabel(this, p, "إدارة المحل"))
        header.addView(UiKit.title(this, p, "الموظفون وملفات التعرف", 24f))
        header.addView(UiKit.subtitle(this, p, "إضافة وتعديل الموظفين وتحديد طرق الحضور النهائية: الوجه، بصمة الصوت، كلمة المرور، بصمة/وجه هاتف الموظف، وQR مباشر. GPS والقرب طبقات حماية إضافية."))
''', '''        val header = UiKit.card(this, p)
        header.addView(TextView(this).apply {
            text = "⋮"; textSize = 28f; gravity = Gravity.CENTER; setTextColor(p.text); contentDescription = "القائمة"
            layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@MainActivity, 48), UiKit.dp(this@MainActivity, 44)).apply { gravity = Gravity.END }
            setOnClickListener { showStoreMainMenu1976() }
        })
        header.addView(UiKit.sectionLabel(this, p, "إدارة المحل"))
        header.addView(UiKit.title(this, p, "إدارة الموظفين", 23f))
        header.addView(UiKit.subtitle(this, p, "ابدأ بالبيانات الأساسية. افتح إعدادات التحقق والربط فقط للموظف الذي تريد تعديله."))
''', 'simplify employee manager header')

s = must_replace(s, '    private fun showEmployeeEditor(existing: PairedEmployee?) {\n', '    private fun showEmployeeEditorAdvanced(existing: PairedEmployee?) {\n', 'rename advanced employee editor')
adv_anchor = '    private fun showEmployeeEditorAdvanced(existing: PairedEmployee?) {\n'
quick_editor = r'''    private fun showEmployeeEditor(existing: PairedEmployee?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 22), UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 22), UiKit.dp(this@MainActivity, 8))
        }
        box.addView(UiKit.subtitle(this, p, "البيانات الأساسية فقط. الدوام والتنبيهات وطرق التحقق التفصيلية موجودة في «إعدادات متقدمة» عند الحاجة."))
        val id = UiKit.field(this, p, "رقم الموظف").apply { setText(existing?.employeeId.orEmpty()); isEnabled = existing == null }
        val name = UiKit.field(this, p, "اسم الموظف").apply { setText(existing?.displayName.orEmpty()) }
        val phone = UiKit.field(this, p, "رقم الهاتف - اختياري").apply { setText(existing?.phone.orEmpty()) }
        val job = UiKit.field(this, p, "المسمى الوظيفي - اختياري").apply { setText(existing?.jobTitle.orEmpty()) }
        val department = UiKit.field(this, p, "القسم - اختياري").apply { setText(existing?.department.orEmpty()) }
        listOf(id, name, phone, job, department).forEach { box.addView(it) }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "إضافة موظف" else "تعديل سريع — ${existing.displayName}")
            .setView(box)
            .setPositiveButton("حفظ", null)
            .setNeutralButton("إعدادات متقدمة") { _, _ -> showEmployeeEditorAdvanced(existing) }
            .setNegativeButton("إلغاء", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val employeeId = id.text.toString().trim()
                val displayName = name.text.toString().trim()
                if (employeeId.isBlank() || displayName.isBlank()) {
                    id.error = if (employeeId.isBlank()) "مطلوب" else null
                    name.error = if (displayName.isBlank()) "مطلوب" else null
                    return@setOnClickListener
                }
                if (existing == null && repo.employees().size >= repo.effectiveEmployeeLimit()) {
                    info("حد الموظفين", "الترخيص الحالي يسمح بحد أقصى ${repo.effectiveEmployeeLimit()} موظفين.")
                    return@setOnClickListener
                }
                if (existing == null && repo.employees().any { it.employeeId.equals(employeeId, true) }) {
                    id.error = "رقم الموظف مستخدم بالفعل"
                    return@setOnClickListener
                }
                val employee = if (existing != null) {
                    existing.copy(
                        displayName = displayName,
                        phone = phone.text.toString().trim(),
                        jobTitle = job.text.toString().trim(),
                        department = department.text.toString().trim()
                    )
                } else {
                    val methods = buildSet<String> {
                        if (repo.allowFaceEnrollment) add(AttendanceMethod.SHARED_DEVICE_FACE.name)
                        if (repo.allowEmployeeCompanion) add(AttendanceMethod.PHONE_BLE_BIOMETRIC.name)
                        if (repo.allowEmployeeCompanion && repo.allowQrAttendance) add(AttendanceMethod.PHONE_PROXIMITY.name)
                        if (isEmpty()) add(AttendanceMethod.MANUAL_ADMIN.name)
                    }
                    PairedEmployee(
                        employeeId = employeeId,
                        displayName = displayName,
                        branchId = repo.branchId,
                        pairingSecret = SecretCodec.encode(SecretCodec.generate()),
                        phone = phone.text.toString().trim(),
                        jobTitle = job.text.toString().trim(),
                        department = department.text.toString().trim(),
                        companionEnabled = repo.allowEmployeeCompanion,
                        allowedMethods = methods,
                        shiftStartHour = repo.shiftHour,
                        shiftStartMinute = repo.shiftMinute,
                        shiftEndHour = repo.shiftEndHour,
                        shiftEndMinute = repo.shiftEndMinute
                    )
                }
                repo.upsertEmployee(employee)
                if (::status.isInitialized) status.text = "✓ تم حفظ ${employee.displayName}"
                refreshDashboard()
                dialog.dismiss()
                if (existing == null) showEmployeeEnrollmentCenter(employee, suggestFace = repo.allowFaceEnrollment, suggestVoice = false)
            }
        }
        dialog.show()
    }

'''
s = s.replace(adv_anchor, quick_editor + adv_anchor, 1)

start = s.find('        addAction("✎ تعديل بيانات الموظف", true) { showEmployeeEditor(e) }')
end = s.find('\n\n        dialog = AlertDialog.Builder(this)', start)
if start < 0 or end < 0:
    raise SystemExit('employee details actions anchor missing')
replacement = r'''        addAction("تعديل البيانات الأساسية", true) { showEmployeeEditor(e) }
        addAction("إعداد الوجه والصوت وهاتف الموظف", true) { showEmployeeEnrollmentCenter(e) }
        addAction("إعدادات الموظف المتقدمة") { showEmployeeEditorAdvanced(e) }
        addAction("إجراءات أخرى") { showEmployeeOtherActions1976(e) }'''
s = s[:start] + replacement + s[end:]

helper_anchor = '    private fun requestFaceCapture(employee: PairedEmployee) {\n'
other_actions = r'''    private fun showEmployeeOtherActions1976(e: PairedEmployee) {
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        fun add(label: String, action: () -> Unit) { labels.add(label); actions.add(action) }
        add("تسجيل يدوي بإشراف") { recordManual(e) }
        add(if (e.active) "إيقاف الموظف" else "تفعيل الموظف") {
            repo.upsertEmployee(e.copy(active = !e.active))
            if (::status.isInitialized) status.text = "تم تحديث حالة ${e.displayName}"
            refreshDashboard()
        }
        if (e.companionEnabled) add("عرض QR تطبيق الموظف") { showProvisionQr(e) }
        if (e.faceProfileRef.isNotBlank()) add("عرض صورة بصمة الوجه") { showFaceReference(e) }
        if (e.faceProfileRef.isNotBlank()) add("حذف بصمة الوجه") { deleteFaceReference(e) }
        add("حذف الموظف") {
            AlertDialog.Builder(this).setTitle("حذف الموظف").setMessage("حذف ${e.displayName} من جهاز المحل؟ لن تحذف سجلات الحضور السابقة.")
                .setPositiveButton("حذف") { _, _ ->
                    deleteFaceFile(e.faceProfileRef)
                    repo.removeEmployee(e.employeeId)
                    if (::status.isInitialized) status.text = "تم حذف الموظف"
                    refreshDashboard()
                }.setNegativeButton("إلغاء", null).show()
        }
        AlertDialog.Builder(this).setTitle("إجراءات أخرى — ${e.displayName}").setItems(labels.toTypedArray()) { _, which -> actions[which]() }.setNegativeButton("إغلاق", null).show()
    }

'''
s = s.replace(helper_anchor, other_actions + helper_anchor, 1)

old_recog = '''        val recognitions = UiKit.card(this, p)\n        val employees = repo.employees().filter { it.active }\n        recognitions.addView(UiKit.sectionLabel(this, p, "جاهزية طرق التعرف"))\n        recognitions.addView(UiKit.subtitle(this, p, "كلمة مرور ${employees.count { it.passwordHash.isNotBlank() }} • صوت ${employees.count { it.voicePhraseHash.isNotBlank() }} • وجه ${employees.count { it.faceTemplate.isNotBlank() }}\\nبصمة خارجية ${employees.count { it.externalFingerprintId.isNotBlank() }} • هاتف مرتبط ${employees.count { it.companionEnabled }} • QR ${if (repo.allowQrAttendance) "مفعّل" else "متوقف"}"))\n        root.addView(recognitions)\n\n'''
if old_recog in s:
    s = s.replace(old_recog, '', 1)
sm.write_text(s, encoding='utf-8')

# ---------------- Employee main: top menu; move help/settings off home ----------------
em = Path('buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt')
s = em.read_text(encoding='utf-8')
emp_header = '        val header = UiKit.heroCard(this, p, 14)\n        header.addView(ImageView(this).apply {\n'
emp_header_new = '''        val header = UiKit.heroCard(this, p, 14)\n        header.addView(TextView(this).apply {\n            text = "⋮"\n            textSize = 28f\n            gravity = Gravity.CENTER\n            setTextColor(android.graphics.Color.WHITE)\n            contentDescription = "القائمة"\n            layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@MainActivity, 48), UiKit.dp(this@MainActivity, 44)).apply { gravity = Gravity.END }\n            setOnClickListener { showEmployeeMainMenu1976() }\n        })\n        header.addView(ImageView(this).apply {\n'''
s = must_replace(s, emp_header, emp_header_new, 'employee top menu')

help_start = s.find('        section("أدوات المساعدة", "أدوات تستخدم عند الحاجة فقط ولا تؤثر في سجل الحضور.") { box ->')
footer_anchor = s.find('        val footer = UiKit.card(this, p, 9)', help_start)
if help_start < 0 or footer_anchor < 0:
    raise SystemExit('employee help/settings block anchor missing')
simple_hint = '''        val moreHint = UiKit.card(this, p, 8)\n        moreHint.addView(UiKit.subtitle(this, p, "الإشعارات ودليل المستخدم والإعدادات موجودة في قائمة ⋮ أعلى الشاشة.").apply { gravity = Gravity.CENTER })\n        root.addView(moreHint)\n\n'''
s = s[:help_start] + simple_hint + s[footer_anchor:]

func_anchor = '    private fun updatePairingSessionStatus(message: String) {\n'
emp_helpers = r'''    private fun showEmployeeMainMenu1976() {
        val items = arrayOf("الإشعارات", "دليل مستخدم الموظف", "الإعدادات")
        AlertDialog.Builder(this).setTitle("القائمة").setItems(items) { _, which ->
            when (which) {
                0 -> startActivity(Intent(this, EmployeeMessages1975Activity::class.java))
                1 -> showEmployeeUserGuide1976()
                2 -> showEmployeeSettings1976()
            }
        }.show()
    }

    private fun showEmployeeUserGuide1976() {
        AlertDialog.Builder(this)
            .setTitle("دليل مستخدم الموظف")
            .setMessage("1. الحضور والانصراف: اضغط الحركة المطلوبة ثم نفّذ طريقة التحقق التي سمحت بها إدارة المحل.\n\n2. حالة اتصالي: تعرض هل الهاتف مرتبط بالمحل والقناة المتاحة.\n\n3. الظهور التلقائي: اتركه مفعّلًا ليتمكن جهاز المحل من اكتشاف الهاتف عند القرب، لكنه لا يسجل حضورًا وحده.\n\n4. الإشعارات: تستقبل رسائل إدارة النظام أو إدارة المحل ويمكنك الرد عليها.\n\n5. قائمة ⋮: منها الإشعارات ودليل المستخدم والإعدادات.\n\nلا تغيّر إعدادات الربط بعد نجاحه إلا عند نقل الهاتف أو إعادة الربط بطلب من إدارة المحل.")
            .setPositiveButton("حسنًا", null)
            .show()
    }

    private fun showEmployeeSettings1976() {
        val items = arrayOf("المظهر وطريقة العرض", "كلمة مرور التطبيق", "فحص تحديث التطبيق", "إلغاء ربط الهاتف")
        AlertDialog.Builder(this).setTitle("الإعدادات").setItems(items) { _, which ->
            when (which) {
                0 -> UiKit.showAppearancePicker(this)
                1 -> setupLocalCredentials()
                2 -> AppUpdateManager.check(this, AppUpdateManager.DEFAULT_SERVER, "employee", manual = true)
                3 -> confirmUnlink()
            }
        }.setNegativeButton("إغلاق", null).show()
    }

'''
s = s.replace(func_anchor, emp_helpers + func_anchor, 1)
em.write_text(s, encoding='utf-8')

print('ATTEND-PRO 1.9.76 simplified navigation patch applied')
