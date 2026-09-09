from pathlib import Path

P = Path('buildsrc/store-app/src/main/java/com/attendpro/store/SystemManagement1971Activity.kt')
if not P.exists():
    raise SystemExit(f'missing management activity: {P}')
s = P.read_text(encoding='utf-8')

if 'import android.widget.CheckBox' not in s:
    s = s.replace('import android.widget.Button\n', 'import android.widget.Button\nimport android.widget.CheckBox\n', 1)
if 'import android.widget.HorizontalScrollView' not in s:
    s = s.replace('import android.widget.EditText\n', 'import android.widget.EditText\nimport android.widget.HorizontalScrollView\n', 1)

# UI text only. Product versioning is handled by Gradle in the release workflow.
s = s.replace('1.9.72', '1.9.73')


def replace_between(start, end, new):
    global s
    a = s.find(start)
    b = s.find(end, a + 1)
    if a < 0 or b < 0:
        raise SystemExit(f'anchor missing: {start} -> {end}')
    s = s[:a] + new.rstrip() + '\n\n' + s[b:]

home_and_helpers = r'''    private fun sectionButton(label: String, key: String, current: String): Button = Button(this).apply {
        text = if (key == current) "● $label" else label
        textSize = 13f
        isAllCaps = false
        minWidth = dp(118)
        setPadding(dp(12), dp(5), dp(12), dp(5))
        if (key == current) {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(37, 99, 235))
        } else {
            setTextColor(ink)
            setBackgroundColor(Color.WHITE)
        }
        setOnClickListener { showHome(key) }
    }

    private fun addSectionBar(root: LinearLayout, current: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        val sections = mutableListOf(
            Triple("لوحة العرض", "OVERVIEW", "▦"),
            Triple("المشتركون", "SUBSCRIBERS", "🏪")
        )
        if (mode == "OWNER") sections += Triple("الوكلاء", "AGENTS", "👥")
        sections += Triple("الصلاحيات والأمان", "SECURITY", "🛡")
        sections += Triple("الإشعارات", "NOTIFICATIONS", "🔔")
        sections += Triple("السجل", "AUDIT", "☷")
        for ((label, key, icon) in sections) {
            row.addView(sectionButton("$icon $label", key, current), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun metricBox(title: String, value: String, icon: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(9), dp(12), dp(9), dp(12))
        setBackgroundColor(Color.WHITE)
        addView(TextView(this@SystemManagement1971Activity).apply {
            text = "$icon  $value"
            textSize = 21f
            setTextColor(ink)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        addView(TextView(this@SystemManagement1971Activity).apply {
            text = title
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, 0)
        })
    }

    private fun metricRow(target: LinearLayout, firstTitle: String, firstValue: String, firstIcon: String,
                          secondTitle: String, secondValue: String, secondIcon: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        row.addView(metricBox(firstTitle, firstValue, firstIcon), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        })
        row.addView(metricBox(secondTitle, secondValue, secondIcon), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        })
        target.addView(row)
    }

    private fun showPermissionsGuide() {
        val msg = if (mode == "OWNER") {
            "مالك النظام يملك التحكم الكامل. الوكيل لا ينفذ إلا الصلاحيات التي تمنح له، وعملياته مقيدة بعملائه وتُسجل في سجل التدقيق.\n\nالحذف النهائي ونقل العملاء بين الوكلاء يظلان لمالك النظام فقط."
        } else {
            "صلاحيات الوكيل يحددها مالك النظام. لا يمكنك تنفيذ عملية على عميل خارج نطاقك، وكل عملية إدارية مسجلة."
        }
        AlertDialog.Builder(this).setTitle("🛡 الصلاحيات والأمان").setMessage(msg).setPositiveButton("إغلاق", null).show()
    }

    private fun showHome(section: String = "OVERVIEW") {
        val owner = mode == "OWNER"
        val root = base(if (owner) "إدارة النظام 1.9.73" else "بوابة الوكيل 1.9.73", "مركز الإدارة النهائي • منظم • واضح • سريع")
        addSectionBar(root, section)

        when (section) {
            "OVERVIEW" -> {
                val metrics = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
                card(root, "▦ ملخص الإدارة", "لوحة عرض للحالة العامة وأهم مؤشرات النظام") {
                    addView(metrics)
                    addView(action("↻ تحديث لوحة العرض") { loadDashboard(metrics) })
                }
                card(root, "وصول سريع", "أكثر الوظائف استخدامًا") {
                    addActionRow(this, "🏪 المشتركـون", { showHome("SUBSCRIBERS") }, "🔔 الإشعارات", { showHome("NOTIFICATIONS") })
                    if (owner) addActionRow(this, "👥 الوكلاء", { showHome("AGENTS") }, "🛡 الصلاحيات والأمان", { showHome("SECURITY") })
                    else addActionRow(this, "✓ طلبات التفعيل", { showAgentActivations() }, "🛡 الصلاحيات", { showHome("SECURITY") })
                }
                loadDashboard(metrics)
            }
            "SUBSCRIBERS" -> {
                card(root, "🏪 إدارة المشتركين", if (owner) "تحكم فردي وجماعي مع حذف نهائي منظم" else "إدارة العملاء ضمن نطاقك") {
                    addActionRow(this, "عرض المشتركين", { showSubscribers() }, "☑ الإدارة الجماعية", { showBulkSelection() })
                }
                card(root, "طريقة العمل", "اختر العرض الفردي للتعامل مع حساب واحد، أو الإدارة الجماعية لتحديد عدة حسابات وستظهر أدوات التحكم في نفس الصفحة مباشرة.") {
                    addView(TextView(this@SystemManagement1971Activity).apply {
                        text = if (owner) "الجماعي: إيقاف • إعادة تفعيل • أرشفة • تمديد • إشعار • حذف نهائي" else "الجماعي: إيقاف • إعادة تفعيل • أرشفة • تمديد • إشعار"
                        textSize = 14f; setTextColor(ink); gravity = Gravity.RIGHT
                    })
                }
            }
            "AGENTS" -> {
                if (owner) {
                    card(root, "👥 الوكلاء والصلاحيات", "إدارة الوكيل، مستوى الثقة، رمز الدخول ونطاق العمل") {
                        addActionRow(this, "إدارة الوكلاء", { showAgents() }, "＋ إضافة وكيل", { createAgentDialog() })
                    }
                    card(root, "ضبط الصلاحيات", "كل وكيل يعمل فقط بالصلاحيات الممنوحة له") {
                        addActionRow(this, "شرح الصلاحيات", { showPermissionsGuide() }, "سجل العمليات", { showAudit() })
                    }
                }
            }
            "SECURITY" -> {
                card(root, "🛡 الصلاحيات والأمان", "مركز التحكم في السلطة الإدارية وحماية العمليات") {
                    if (owner) {
                        addActionRow(this, "صلاحيات الوكلاء", { showAgents() }, "سجل التدقيق", { showAudit() })
                        addActionRow(this, "إعداد اتصال الخادم", { configureOwnerConnection() }, "دليل الصلاحيات", { showPermissionsGuide() })
                    } else {
                        addActionRow(this, "دليل صلاحياتي", { showPermissionsGuide() }, "سجل عملي", { showAudit() })
                        addActionRow(this, "طلبات التفعيل", { showAgentActivations() }, "استلام طلب", { claimActivationDialog() })
                    }
                }
                card(root, "قواعد الحماية", "الصلاحيات تُفرض من الخادم وليست من شكل الواجهة فقط") {
                    addView(TextView(this@SystemManagement1971Activity).apply {
                        text = "• كل عملية إدارية مسجلة\n• الوكيل مقيد بعملائه وصلاحياته\n• الحذف النهائي لمالك النظام فقط\n• ملفات الارتباط والحضور مستقلة عن إدارة النظام"
                        textSize = 14f; setTextColor(ink); gravity = Gravity.RIGHT
                    })
                }
            }
            "NOTIFICATIONS" -> {
                card(root, "🔔 مركز الإشعارات", "التفعيل • الوكلاء • التسجيل • الاشتراكات • الأجهزة • الأمان • النظام") {
                    addActionRow(this, "فتح مركز الإشعارات", { showNotifications() }, "تحديث", { showNotifications() })
                }
            }
            "AUDIT" -> {
                card(root, "☷ سجل العمليات", "راجع من نفذ العملية وعلى أي حساب ومتى") {
                    addActionRow(this, "فتح السجل", { showAudit() }, "📘 دليل الإدارة", { showUserGuide() })
                }
            }
        }

        root.addView(action("إغلاق إدارة النظام") { finish() })
        display(root)
    }
'''
replace_between('    private fun showHome()', '    private fun loadDashboard(', home_and_helpers)

load_dashboard = r'''    private fun loadDashboard(target: LinearLayout) {
        target.removeAllViews()
        target.addView(TextView(this).apply { text = "جاري تحميل المؤشرات..."; textSize = 14f; setTextColor(muted); gravity = Gravity.CENTER })
        request("GET", "/api/v1/manage/dashboard", null) { result ->
            result.onSuccess { x ->
                target.removeAllViews()
                metricRow(target, "إجمالي المشتركين", x.optInt("totalSubscribers").toString(), "🏪", "المشتركون النشطون", x.optInt("active").toString(), "✓")
                metricRow(target, "الموقوفون", x.optInt("suspended").toString(), "⏸", "المؤرشفون", x.optInt("archived").toString(), "▣")
                metricRow(target, "المنتهية اشتراكاتهم", x.optInt("expired").toString(), "⌛", if (mode == "OWNER") "الوكلاء" else "إشعارات غير مقروءة", if (mode == "OWNER") x.optInt("agents").toString() else x.optInt("unreadNotifications").toString(), if (mode == "OWNER") "👥" else "🔔")
                if (mode == "OWNER") metricRow(target, "إشعارات غير مقروءة", x.optInt("unreadNotifications").toString(), "🔔", "حالة الإدارة", "جاهزة", "●")
            }.onFailure {
                target.removeAllViews()
                target.addView(TextView(this).apply { text = "تعذر تحميل لوحة العرض: ${it.message}"; textSize = 14f; setTextColor(ink); gravity = Gravity.RIGHT })
            }
        }
    }
'''
replace_between('    private fun loadDashboard(', '    private fun createAgentDialog(', load_dashboard)

bulk_selection = r'''    private fun showBulkSelection() {
        request("GET", "/api/v1/manage/subscribers?limit=500", null) { result ->
            result.onSuccess { data ->
                val stores = data.optJSONArray("subscribers") ?: JSONArray()
                if (stores.length() == 0) { toast("لا يوجد مشتركون"); return@onSuccess }
                selectedStores.clear()
                val checks = mutableListOf<Pair<String, CheckBox>>()
                val root = base("☑ الإدارة الجماعية", "حدد المشتركين، وستبقى أدوات التحكم ظاهرة في نفس الصفحة")
                val selectedCount = TextView(this).apply {
                    text = "المحدد: 0 من ${stores.length()}"
                    textSize = 16f
                    setTextColor(ink)
                    gravity = Gravity.RIGHT
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }

                fun refreshCount() {
                    selectedCount.text = "المحدد: ${selectedStores.size} من ${stores.length()}"
                }
                fun requireSelected(run: () -> Unit) {
                    if (selectedStores.isEmpty()) toast("حدد مشتركًا واحدًا على الأقل") else run()
                }
                fun refreshAfter() { showBulkSelection() }

                card(root, "التحكم بالمحدد", "الأدوات هنا ثابتة ولا تختفي بعد التحديد") {
                    addView(selectedCount)
                    addActionRow(this, "✓ تحديد الكل", {
                        selectedStores.clear()
                        for ((id, cb) in checks) { selectedStores += id; cb.isChecked = true }
                        refreshCount()
                    }, "إلغاء الكل", {
                        selectedStores.clear()
                        for ((_, cb) in checks) cb.isChecked = false
                        refreshCount()
                    })
                    addActionRow(this, "⏸ إيقاف", { requireSelected { bulkRequest("SUSPEND") { refreshAfter() } } }, "▶ إعادة تفعيل", { requireSelected { bulkRequest("REACTIVATE") { refreshAfter() } } })
                    addActionRow(this, "▣ أرشفة", { requireSelected { confirm("أرشفة ${selectedStores.size} مشترك؟") { bulkRequest("ARCHIVE") { refreshAfter() } } } }, "＋ تمديد", { requireSelected { askDays { days -> bulkRequest("EXTEND", JSONObject().put("days", days)) { refreshAfter() } } } })
                    addView(action("🔔 إرسال إشعار للمحدد") {
                        requireSelected {
                            val message = EditText(this@SystemManagement1971Activity).apply { hint = "نص الإشعار"; minLines = 2 }
                            AlertDialog.Builder(this@SystemManagement1971Activity).setTitle("إشعار جماعي • ${selectedStores.size} مشترك")
                                .setView(message).setPositiveButton("إرسال") { _, _ ->
                                    bulkRequest("SEND_NOTIFICATION", JSONObject().put("title", "إشعار من إدارة النظام").put("message", message.text.toString())) { refreshAfter() }
                                }.setNegativeButton("إلغاء", null).show()
                        }
                    })
                    if (mode == "OWNER") {
                        addView(action("🗑 حذف المحددين نهائيًا") {
                            requireSelected {
                                confirmPermanentDelete(selectedStores.size) {
                                    bulkRequest("DELETE", JSONObject().put("confirm", "DELETE_SUBSCRIBER")) { refreshAfter() }
                                }
                            }
                        })
                    }
                }

                card(root, "المشتركون", "اضغط على المربع بجانب كل مشترك لتحديده") {
                    for (i in 0 until stores.length()) {
                        val store = stores.getJSONObject(i)
                        val id = store.optString("storeId")
                        val cb = CheckBox(this@SystemManagement1971Activity).apply {
                            text = subscriberLabel(store)
                            textSize = 14f
                            setTextColor(ink)
                            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                            layoutDirection = View.LAYOUT_DIRECTION_RTL
                            setPadding(dp(8), dp(8), dp(8), dp(8))
                            setOnCheckedChangeListener { _, checked ->
                                if (checked) selectedStores += id else selectedStores -= id
                                refreshCount()
                            }
                        }
                        checks += id to cb
                        addView(cb, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                        addView(View(this@SystemManagement1971Activity).apply { setBackgroundColor(Color.rgb(232, 236, 241)) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
                    }
                }
                root.addView(action("رجوع إلى قسم المشتركين") { showHome("SUBSCRIBERS") })
                display(root)
            }.onFailure { toast("تعذر تحميل المشتركين: ${it.message}") }
        }
    }
'''
replace_between('    private fun showBulkSelection()', '    private fun bulkActions(', bulk_selection)

bulk_actions = r'''    private fun bulkActions() {
        showBulkSelection()
    }
'''
replace_between('    private fun bulkActions()', '    private fun bulkRequest(', bulk_actions)

bulk_request = r'''    private fun bulkRequest(action: String, extra: JSONObject = JSONObject(), after: () -> Unit = {}) {
        if (selectedStores.isEmpty()) { toast("لم تحدد مشتركين"); return }
        extra.put("action", action).put("storeIds", JSONArray(selectedStores.toList()))
        request("POST", "/api/v1/manage/subscribers/bulk", extra) { result ->
            result.onSuccess {
                val success = it.optInt("successCount")
                val failure = it.optInt("failureCount")
                toast("تمت العملية • نجاح $success • فشل $failure")
                selectedStores.clear()
                after()
            }.onFailure { toast("فشلت العملية الجماعية: ${it.message}") }
        }
    }
'''
replace_between('    private fun bulkRequest(', '    private fun showNotifications()', bulk_request)

# Keep the overflow/about text aligned with the final management revision.
s = s.replace('واجهة إدارة محسنة للمشتركين والوكلاء والإشعارات والعمليات الجماعية، مع بقاء نظام الارتباط الأساسي دون تعديل.',
              'الإصدار النهائي لقسم إدارة النظام: لوحة عرض، أقسام منظمة، تحكم جماعي ثابت، صلاحيات وأمان، إشعارات وسجل عمليات. نظام الارتباط الأساسي بقي دون تعديل.')

required = [
    'إدارة النظام 1.9.73',
    'لوحة العرض',
    'الصلاحيات والأمان',
    'الإدارة الجماعية',
    'التحكم بالمحدد',
    'حذف المحددين نهائيًا',
    'HorizontalScrollView',
    'CheckBox',
]
for marker in required:
    if marker not in s:
        raise SystemExit(f'1.9.73 UI marker missing: {marker}')

P.write_text(s, encoding='utf-8')
print('ATTEND-PRO 1.9.73 management final UI patch applied')
