from pathlib import Path

MGMT = Path('buildsrc/store-app/src/main/java/com/attendpro/store/SystemManagement1971Activity.kt')
OWNER = Path('buildsrc/store-app/src/main/java/com/attendpro/store/SystemSettingsActivity.kt')


def replace_between(text, start, end, new):
    a = text.find(start)
    b = text.find(end, a + 1)
    if a < 0 or b < 0:
        raise SystemExit(f'anchor missing: {start!r} -> {end!r}')
    return text[:a] + new.rstrip() + '\n\n' + text[b:]

# ---------------- central management polish ----------------
s = MGMT.read_text(encoding='utf-8')
if 'import android.graphics.drawable.GradientDrawable' not in s:
    s = s.replace('import android.graphics.Color\n', 'import android.graphics.Color\nimport android.graphics.drawable.GradientDrawable\n', 1)

s = s.replace('private val bg = Color.rgb(246, 248, 251)\n    private val ink = Color.rgb(26, 36, 48)\n    private val muted = Color.rgb(92, 106, 120)',
'''private val bg = Color.rgb(244, 247, 246)
    private val surface = Color.rgb(253, 254, 254)
    private val soft = Color.rgb(235, 242, 240)
    private val accent = Color.rgb(63, 101, 94)
    private val accentDark = Color.rgb(45, 78, 72)
    private val line = Color.rgb(218, 228, 225)
    private val danger = Color.rgb(154, 67, 67)
    private val ink = Color.rgb(32, 45, 45)
    private val muted = Color.rgb(94, 110, 108)''')

base_start = '    private fun base(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {'
card_start = '    private fun card(root: LinearLayout, title: String, description: String = "", block: LinearLayout.() -> Unit) {'
base_new = r'''    private fun rounded(fill: Int, stroke: Int = line, radius: Int = 16): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun base(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(16), dp(14), dp(16), dp(36))
        setBackgroundColor(bg)
        window.statusBarColor = bg

        val top = LinearLayout(this@SystemManagement1971Activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = rounded(surface, line, 18)
            setPadding(dp(10), dp(8), dp(12), dp(8))
        }
        val more = Button(this@SystemManagement1971Activity).apply {
            text = "⋮"
            textSize = 24f
            isAllCaps = false
            minWidth = dp(50)
            setTextColor(accentDark)
            background = rounded(soft, line, 14)
            setOnClickListener { showOverflowMenu(this) }
        }
        val titles = LinearLayout(this@SystemManagement1971Activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.RIGHT
            setPadding(dp(8), 0, dp(8), 0)
            addView(TextView(this@SystemManagement1971Activity).apply {
                text = title
                textSize = 22f
                setTextColor(ink)
                gravity = Gravity.RIGHT
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@SystemManagement1971Activity).apply {
                text = subtitle
                textSize = 12.5f
                setTextColor(muted)
                gravity = Gravity.RIGHT
                setPadding(0, dp(3), 0, 0)
            })
        }
        top.addView(more, LinearLayout.LayoutParams(dp(54), dp(48)))
        top.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(top, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(View(this@SystemManagement1971Activity), LinearLayout.LayoutParams(1, dp(10)))
    }

    private fun addActionRow(container: LinearLayout, first: String, firstAction: () -> Unit, second: String, secondAction: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER
        }
        row.addView(action(first, firstAction), LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        row.addView(action(second, secondAction), LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        container.addView(row)
    }
'''
s = replace_between(s, base_start, card_start, base_new)

card_new = r'''    private fun card(root: LinearLayout, title: String, description: String = "", block: LinearLayout.() -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(surface, line, 17)
        }
        box.addView(TextView(this).apply {
            text = title
            textSize = 17.5f
            setTextColor(ink)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.RIGHT
        })
        if (description.isNotBlank()) box.addView(TextView(this).apply {
            text = description
            textSize = 12.5f
            setTextColor(muted)
            gravity = Gravity.RIGHT
            setPadding(0, dp(4), 0, dp(8))
        })
        box.block()
        root.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, dp(10))
        })
    }

    private fun action(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 14f
        isAllCaps = false
        gravity = Gravity.CENTER
        setTextColor(if (text.contains("حذف")) danger else accentDark)
        background = rounded(if (text.contains("حذف")) Color.rgb(252, 243, 243) else soft, if (text.contains("حذف")) Color.rgb(231, 200, 200) else line, 13)
        setPadding(dp(8), dp(4), dp(8), dp(4))
        setOnClickListener { onClick() }
    }

    private fun display(root: LinearLayout) {
        setContentView(ScrollView(this).apply { isFillViewport = true; setBackgroundColor(bg); addView(root) })
    }
'''
s = replace_between(s, card_start, '    private fun authToken()', card_new)

section_new = r'''    private fun sectionButton(label: String, key: String, current: String): Button = Button(this).apply {
        text = if (key == current) "● $label" else label
        textSize = 12.5f
        isAllCaps = false
        minWidth = dp(116)
        setPadding(dp(11), dp(5), dp(11), dp(5))
        if (key == current) {
            setTextColor(Color.WHITE)
            background = rounded(accent, accent, 14)
        } else {
            setTextColor(accentDark)
            background = rounded(surface, line, 14)
        }
        setOnClickListener { showHome(key) }
    }
'''
s = replace_between(s, '    private fun sectionButton(', '    private fun addSectionBar(', section_new)

metric_new = r'''    private fun metricBox(title: String, value: String, icon: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(9), dp(13), dp(9), dp(13))
        background = rounded(soft, line, 15)
        addView(TextView(this@SystemManagement1971Activity).apply {
            text = "$icon  $value"
            textSize = 20f
            setTextColor(accentDark)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        addView(TextView(this@SystemManagement1971Activity).apply {
            text = title
            textSize = 11.7f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, 0)
        })
    }
'''
s = replace_between(s, '    private fun metricBox(', '    private fun metricRow(', metric_new)

quick = r'''    private fun showQuickActions() {
        val items = if (mode == "OWNER") arrayOf("بحث عن مشترك", "إدارة جماعية", "إضافة وكيل", "مركز الإشعارات", "سجل العمليات")
        else arrayOf("بحث عن مشترك", "إدارة جماعية", "طلبات التفعيل", "مركز الإشعارات", "سجل العمليات")
        AlertDialog.Builder(this).setTitle("＋ إجراء سريع").setItems(items) { _, which ->
            if (mode == "OWNER") when (which) {
                0 -> showSubscribers()
                1 -> showBulkSelection()
                2 -> createAgentDialog()
                3 -> showNotifications()
                4 -> showAudit()
            } else when (which) {
                0 -> showSubscribers()
                1 -> showBulkSelection()
                2 -> showAgentActivations()
                3 -> showNotifications()
                4 -> showAudit()
            }
        }.setNegativeButton("إلغاء", null).show()
    }
'''
s = s.replace('    private fun showPermissionsGuide() {', quick + '\n    private fun showPermissionsGuide() {', 1)

s = s.replace('menu.add(0, 6, 5, "ⓘ حول إدارة النظام")', 'menu.add(0, 6, 5, "＋ إجراء سريع")\n            menu.add(0, 7, 6, "ⓘ حول إدارة النظام")', 1)
s = s.replace('6 -> AlertDialog.Builder(this@SystemManagement1971Activity)', '6 -> showQuickActions()\n                    7 -> AlertDialog.Builder(this@SystemManagement1971Activity)', 1)

s = s.replace('1.9.73', '1.9.74')
s = s.replace('مركز الإدارة النهائي • منظم • واضح • سريع', 'إدارة هادئة وواضحة • أقسام مرتبة • وصول أسرع')

show_subscribers = r'''    private fun showSubscribers() {
        request("GET", "/api/v1/manage/subscribers?limit=500", null) { result ->
            result.onSuccess { data ->
                val stores = data.optJSONArray("subscribers") ?: JSONArray()
                if (stores.length() == 0) { toast("لا يوجد مشتركون ضمن نطاقك"); return@onSuccess }

                val root = base("🏪 المشتركون", "بحث سريع وتصفية وفتح الحساب مباشرة")
                val query = EditText(this).apply {
                    hint = "بحث بالاسم أو الهاتف أو المعرّف أو الوكيل"
                    setSingleLine(true)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    background = rounded(surface, line, 13)
                }
                val resultLabel = TextView(this).apply { textSize = 13f; setTextColor(muted); gravity = Gravity.RIGHT }
                val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
                var status = "ALL"

                fun render() {
                    content.removeAllViews()
                    val q = query.text.toString().trim().lowercase(Locale.ROOT)
                    var shown = 0
                    for (i in 0 until stores.length()) {
                        val item = stores.getJSONObject(i)
                        val itemStatus = item.optString("status").uppercase(Locale.ROOT)
                        val statusMatch = status == "ALL" || itemStatus == status
                        val queryMatch = q.isBlank() || item.toString().lowercase(Locale.ROOT).contains(q)
                        if (!statusMatch || !queryMatch) continue
                        shown++
                        card(content, "🏪 ${item.optString("storeName").ifBlank { "مشترك" }}", subscriberLabel(item).substringAfter('\n', "")) {
                            addView(TextView(this@SystemManagement1971Activity).apply {
                                text = "الحالة: ${statusArabic(itemStatus)} • ${item.optString("agentName").ifBlank { "بدون وكيل" }}"
                                textSize = 13f; setTextColor(muted); gravity = Gravity.RIGHT
                            })
                            addView(action("فتح التحكم") { subscriberActions(item) })
                        }
                    }
                    resultLabel.text = "النتائج: $shown من ${stores.length()}"
                    if (shown == 0) card(content, "لا توجد نتائج", "غيّر البحث أو الفلتر ثم أعد المحاولة") { }
                }

                card(root, "البحث والتصفية", "يمكنك الوصول للحساب دون المرور بقائمة طويلة") {
                    addView(query)
                    addView(resultLabel.apply { setPadding(0, dp(7), 0, dp(4)) })
                    addActionRow(this, "بحث", { render() }, "مسح", { query.setText(""); status = "ALL"; render() })
                    addActionRow(this, "الكل", { status = "ALL"; render() }, "نشط", { status = "ACTIVE"; render() })
                    addActionRow(this, "موقوف", { status = "SUSPENDED"; render() }, "منتهي", { status = "EXPIRED"; render() })
                    addActionRow(this, "مؤرشف", { status = "ARCHIVED"; render() }, "☑ إدارة جماعية", { showBulkSelection() })
                }
                root.addView(content)
                root.addView(action("رجوع إلى قسم المشتركين") { showHome("SUBSCRIBERS") })
                display(root)
                render()
            }.onFailure { toast("تعذر تحميل المشتركين: ${it.message}") }
        }
    }
'''
s = replace_between(s, '    private fun showSubscribers()', '    private fun subscriberLabel(', show_subscribers)

s = s.replace('r.onSuccess { toast("تم تنفيذ العملية") }.onFailure { toast("فشلت العملية: ${it.message}") }',
'''r.onSuccess { toast("✓ تم تنفيذ العملية بنجاح") }.onFailure {
                AlertDialog.Builder(this).setTitle("تعذر تنفيذ العملية").setMessage(it.message ?: "خطأ غير معروف").setPositiveButton("إغلاق", null).show()
            }''', 1)

s = s.replace('toast("تمت العملية • نجاح $success • فشل $failure")\n                selectedStores.clear()',
'''if (failure == 0) toast("✓ اكتملت العملية على $success مشترك")
                else AlertDialog.Builder(this).setTitle("نتيجة العملية الجماعية")
                    .setMessage("نجح: $success\\nفشل: $failure\\n\\nيمكنك إعادة المحاولة للحسابات التي لم تُنفذ عليها العملية.")
                    .setPositiveButton("إغلاق", null).show()
                selectedStores.clear()''', 1)

helper = r'''    private fun markNotificationsRead(ids: List<String>, index: Int = 0, onDone: () -> Unit) {
        if (index >= ids.size) { onDone(); return }
        request("POST", "/api/v1/manage/notifications/${enc(ids[index])}/read", JSONObject()) {
            markNotificationsRead(ids, index + 1, onDone)
        }
    }
'''
s = s.replace('    private fun showNotifications() {', helper + '\n    private fun showNotifications() {', 1)

needle = '                val items = data.optJSONArray("notifications") ?: JSONArray()\n                if (items.length() == 0) {'
replacement = '''                val items = data.optJSONArray("notifications") ?: JSONArray()
                val unreadIds = mutableListOf<String>()
                for (i in 0 until items.length()) {
                    val n = items.getJSONObject(i)
                    if (n.optLong("readAt") == 0L && n.optString("notificationId").isNotBlank()) unreadIds += n.optString("notificationId")
                }
                if (items.length() > 0) card(content, "ملخص الإشعارات", "غير المقروء: ${unreadIds.size} • الإجمالي: ${items.length()}") {
                    if (unreadIds.isNotEmpty()) addView(action("✓ تعليم الكل كمقروء") {
                        markNotificationsRead(unreadIds) { toast("تم تعليم الإشعارات كمقروءة"); loadNotifications(category) }
                    })
                }
                if (items.length() == 0) {'''
if needle not in s:
    raise SystemExit('notification anchor missing')
s = s.replace(needle, replacement, 1)

show_audit = r'''    private fun showAudit() {
        request("GET", "/api/v1/manage/audit?limit=300", null) { result ->
            result.onSuccess { data ->
                val rows = data.optJSONArray("audit") ?: JSONArray()
                val root = base("☷ سجل العمليات", "بحث واضح في العمليات الإدارية ومنفذيها")
                val query = EditText(this).apply {
                    hint = "بحث بالإجراء أو المستخدم أو الحساب"
                    setSingleLine(true)
                    background = rounded(surface, line, 13)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                }
                val count = TextView(this).apply { textSize = 13f; setTextColor(muted); gravity = Gravity.RIGHT }
                val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }

                fun render() {
                    content.removeAllViews()
                    val q = query.text.toString().trim().lowercase(Locale.ROOT)
                    var shown = 0
                    for (i in 0 until rows.length()) {
                        val a = rows.getJSONObject(i)
                        if (q.isNotBlank() && !a.toString().lowercase(Locale.ROOT).contains(q)) continue
                        shown++
                        val actionName = a.optString("action").ifBlank { "عملية إدارية" }
                        val target = a.optString("target_id").ifBlank { a.optString("targetId") }
                        val actor = a.optString("actor_account_id").ifBlank { a.optString("actorAccountId") }
                        val at = a.optLong("created_at").takeIf { it > 0 } ?: a.optLong("createdAt")
                        card(content, actionName, "${time(at)}") {
                            addView(TextView(this@SystemManagement1971Activity).apply {
                                text = "المنفذ: ${actor.ifBlank { "SYSTEM" }}\\nالهدف: ${target.ifBlank { "-" }}"
                                textSize = 13f; setTextColor(ink); gravity = Gravity.RIGHT
                            })
                        }
                    }
                    count.text = "النتائج: $shown من ${rows.length()}"
                    if (shown == 0) card(content, "لا توجد نتائج", "لا توجد عمليات مطابقة للبحث") { }
                }

                card(root, "البحث في السجل", "اكتب جزءًا من اسم الإجراء أو المستخدم أو الحساب") {
                    addView(query)
                    addView(count.apply { setPadding(0, dp(7), 0, dp(3)) })
                    addActionRow(this, "بحث", { render() }, "مسح", { query.setText(""); render() })
                }
                root.addView(content)
                root.addView(action("رجوع") { showHome("AUDIT") })
                display(root)
                render()
            }.onFailure { toast("تعذر تحميل السجل: ${it.message}") }
        }
    }
'''
s = replace_between(s, '    private fun showAudit()', '    private fun claimActivationDialog()', show_audit)

MGMT.write_text(s, encoding='utf-8')

# ---------------- owner dashboard two models ----------------
o = OWNER.read_text(encoding='utf-8')
owner_funcs = r'''    private fun ownerDashboardMode(): String = getSharedPreferences("attend_owner_ui", MODE_PRIVATE)
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
        network.addView(UiKit.button(this, p, "إدارة الوكلاء").apply { setOnClickListener { showAgents() } })
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
                network.addView(UiKit.button(this, p, "إدارة الوكلاء", false).apply { setOnClickListener { showAgents() } })
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
'''
o = replace_between(o, '    private fun showOwnerDashboard()', '    private fun showCentralAdmin()', owner_funcs)
OWNER.write_text(o, encoding='utf-8')

print('patched 1.9.74 polish')
