from pathlib import Path

P = Path('buildsrc/store-app/src/main/java/com/attendpro/store/SystemManagement1971Activity.kt')
if not P.exists():
    raise SystemExit(f'missing management activity: {P}')
s = P.read_text(encoding='utf-8')

if 'import android.widget.PopupMenu' not in s:
    s = s.replace('import android.widget.LinearLayout\n', 'import android.widget.LinearLayout\nimport android.widget.PopupMenu\n', 1)


def replace_between(start, end, new):
    global s
    a = s.find(start)
    b = s.find(end, a + 1)
    if a < 0 or b < 0:
        raise SystemExit(f'anchor missing: {start} -> {end}')
    s = s[:a] + new.rstrip() + '\n\n' + s[b:]

base_and_helpers = r'''    private fun base(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(16), dp(12), dp(16), dp(34))
        setBackgroundColor(bg)

        val top = LinearLayout(this@SystemManagement1971Activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val more = Button(this@SystemManagement1971Activity).apply {
            text = "⋮"
            textSize = 25f
            isAllCaps = false
            minWidth = dp(52)
            setOnClickListener { showOverflowMenu(this) }
        }
        val titles = LinearLayout(this@SystemManagement1971Activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.RIGHT
            addView(TextView(this@SystemManagement1971Activity).apply {
                text = title
                textSize = 23f
                setTextColor(ink)
                gravity = Gravity.RIGHT
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@SystemManagement1971Activity).apply {
                text = subtitle
                textSize = 13f
                setTextColor(muted)
                gravity = Gravity.RIGHT
                setPadding(0, dp(3), 0, 0)
            })
        }
        top.addView(more, LinearLayout.LayoutParams(dp(58), LinearLayout.LayoutParams.WRAP_CONTENT))
        top.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(top, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(View(this@SystemManagement1971Activity).apply { setBackgroundColor(Color.rgb(224, 229, 235)) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { setMargins(0, dp(10), 0, dp(14)) })
    }

    private fun addActionRow(container: LinearLayout, first: String, firstAction: () -> Unit, second: String, secondAction: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER
        }
        row.addView(action(first, firstAction), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        row.addView(action(second, secondAction), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        container.addView(row)
    }

    private fun showOverflowMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "⌂ الرئيسية")
            menu.add(0, 2, 1, "🔔 مركز الإشعارات")
            menu.add(0, 3, 2, "📘 دليل إدارة النظام")
            menu.add(0, 4, 3, "🛡 سجل العمليات")
            menu.add(0, 5, 4, "↻ تحديث الصفحة")
            menu.add(0, 6, 5, "ⓘ حول إدارة النظام")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> showHome()
                    2 -> showNotifications()
                    3 -> showUserGuide()
                    4 -> showAudit()
                    5 -> showHome()
                    6 -> AlertDialog.Builder(this@SystemManagement1971Activity)
                        .setTitle("ATTEND-PRO • إدارة النظام 1.9.72")
                        .setMessage("واجهة إدارة محسنة للمشتركين والوكلاء والإشعارات والعمليات الجماعية، مع بقاء نظام الارتباط الأساسي دون تعديل.")
                        .setPositiveButton("حسنًا", null).show()
                }
                true
            }
            show()
        }
    }

    private fun showUserGuide() {
        val text = TextView(this).apply {
            textSize = 15f
            setTextColor(ink)
            gravity = Gravity.RIGHT
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(12), dp(18), dp(18))
            text = """دليل إدارة النظام

1) المشتركـون
• افتح «المشتركون» لمراجعة الاسم والحالة وتاريخ انتهاء الاشتراك والوكيل.
• من بطاقة المشترك تستطيع الإيقاف، إعادة التفعيل، الأرشفة، التمديد وإرسال إشعار.
• الحذف النهائي متاح لمالك النظام فقط، ويزيل حساب المشترك التشغيلي من الخادم مع الإبقاء على سجل التدقيق الإداري.

2) العمليات الجماعية
• اختر «تحديد متعدد» وحدد أي عدد من المشتركين.
• يمكنك الإيقاف أو إعادة التفعيل أو الأرشفة أو التمديد أو الإشعار دفعة واحدة.
• مالك النظام يستطيع حذف المحددين نهائيًا بعد رسالة تأكيد توضح العدد.

3) الوكلاء
• أنشئ وكيلًا وحدد مستوى الثقة والصلاحيات بدقة.
• الوكيل الموثوق يستطيع تنفيذ التفعيل المسموح له به ويصل إشعار لمالك النظام.

4) الإشعارات
• مركز الإشعارات مقسم حسب: التفعيل، الوكلاء، التسجيل، الاشتراكات، الأجهزة، الأمان والنظام.
• النقطة ● تعني إشعارًا غير مقروء، و○ تعني مقروءًا.

5) سجل العمليات
• راجع من نفذ العملية ومتى وعلى أي حساب.
• استخدم الأرشفة للحسابات التي قد تحتاجها لاحقًا، والحذف النهائي فقط عندما تكون متأكدًا.

6) قائمة ⋮
• للوصول السريع إلى الرئيسية، الإشعارات، الدليل، سجل العمليات والتحديث."""
        }
        AlertDialog.Builder(this).setTitle("📘 دليل إدارة النظام").setView(ScrollView(this).apply { addView(text) })
            .setPositiveButton("إغلاق", null).show()
    }

    private fun statusArabic(value: String): String = when (value.uppercase(Locale.ROOT)) {
        "ACTIVE" -> "نشط"
        "SUSPENDED" -> "موقوف"
        "ARCHIVED" -> "مؤرشف"
        "EXPIRED" -> "منتهي"
        else -> value.ifBlank { "غير محدد" }
    }

    private fun categoryArabic(value: String): String = when (value.uppercase(Locale.ROOT)) {
        "ACTIVATION" -> "التفعيل"
        "AGENT" -> "الوكلاء"
        "REGISTRATION" -> "التسجيل"
        "SUBSCRIPTION" -> "الاشتراكات"
        "DEVICE" -> "الأجهزة"
        "SECURITY" -> "الأمان"
        "SYSTEM" -> "النظام"
        else -> "عام"
    }

    private fun confirmPermanentDelete(count: Int, yes: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("🗑 حذف نهائي")
            .setMessage("سيتم حذف ${if (count == 1) "المشترك المحدد" else "$count مشتركين محددين"} من الحسابات التشغيلية على الخادم. لا يمكن التراجع عن هذه العملية.\n\nسجل التدقيق الإداري سيبقى محفوظًا.")
            .setPositiveButton("حذف نهائي") { _, _ -> yes() }
            .setNegativeButton("إلغاء", null)
            .show()
    }
'''
replace_between('    private fun base(', '    private fun card(', base_and_helpers)

home = r'''    private fun showHome() {
        val owner = mode == "OWNER"
        val root = base(if (owner) "إدارة النظام 1.9.72" else "بوابة الوكيل 1.9.72", if (owner) "إدارة مركزية منظمة وسريعة" else "إدارة العملاء والصلاحيات ضمن نطاق الوكيل")
        val dashboardText = TextView(this).apply { text = "جاري تحميل الملخص..."; textSize = 15f; setTextColor(ink); gravity = Gravity.RIGHT }

        card(root, "▦ ملخص النظام", "الحالة العامة في نظرة واحدة") {
            addView(dashboardText)
            addView(action("↻ تحديث الملخص") { loadDashboard(dashboardText) })
        }

        if (owner) {
            card(root, "👥 الوكلاء", "إضافة الوكلاء وضبط الثقة والصلاحيات") {
                addActionRow(this, "إدارة الوكلاء", { showAgents() }, "＋ إضافة وكيل", { createAgentDialog() })
            }
        }

        card(root, "🏪 المشتركـون", if (owner) "الإدارة الفردية والجماعية والحذف المنظم" else "الحسابات التابعة لك فقط") {
            addActionRow(this, "عرض المشتركين", { showSubscribers() }, "☑ تحديد متعدد", { showBulkSelection() })
        }

        if (!owner) {
            card(root, "✓ طلبات التفعيل", "استلام الطلبات وتنفيذ الصلاحيات الممنوحة") {
                addActionRow(this, "الطلبات المستلمة", { showAgentActivations() }, "＋ استلام طلب", { claimActivationDialog() })
            }
        }

        card(root, "🔔 الإشعارات", "مركز واحد مرتب حسب نوع الإشعار وحالة القراءة") {
            addActionRow(this, "فتح المركز", { showNotifications() }, "تحديث", { showNotifications() })
        }

        card(root, "🛡 الرقابة والمساعدة", "سجل العمليات ودليل الاستخدام") {
            addActionRow(this, "سجل العمليات", { showAudit() }, "📘 دليل الاستخدام", { showUserGuide() })
        }

        root.addView(action("إغلاق إدارة النظام") { finish() })
        display(root)
        loadDashboard(dashboardText)
    }
'''
replace_between('    private fun showHome()', '    private fun loadDashboard(', home)

subscriber_label = r'''    private fun subscriberLabel(s: JSONObject): String {
        val expiry = s.optLong("expiresAt", 0L)
        val date = if (expiry > 0) SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(expiry)) else "-"
        val agent = s.optString("agentName").ifBlank { "بدون وكيل" }
        return "${s.optString("storeName").ifBlank { "مشترك" }} • ${statusArabic(s.optString("status"))}\nحتى $date • $agent"
    }
'''
replace_between('    private fun subscriberLabel(', '    private fun subscriberActions(', subscriber_label)

subscriber_actions = r'''    private fun subscriberActions(store: JSONObject) {
        val id = store.optString("storeId")
        val owner = mode == "OWNER"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(bg)
        }

        card(root, "🏪 ${store.optString("storeName").ifBlank { "مشترك" }}", "بيانات الحساب") {
            addView(TextView(this@SystemManagement1971Activity).apply {
                text = "الحالة: ${statusArabic(store.optString("status"))}\nالمعرّف: $id\nالوكيل: ${store.optString("agentName").ifBlank { "بدون وكيل" }}\nآخر اتصال: ${time(store.optLong("lastSeenAt"))}"
                textSize = 14f; setTextColor(ink); gravity = Gravity.RIGHT
            })
        }

        card(root, "إدارة الحالة والاشتراك", "إجراءات يومية سريعة") {
            addActionRow(this, "⏸ إيقاف", { storeAction(id, "SUSPEND") }, "▶ إعادة تفعيل", { storeAction(id, "REACTIVATE") })
            addActionRow(this, "▣ أرشفة", { confirm("أرشفة هذا المشترك؟") { storeAction(id, "ARCHIVE") } }, "＋ تمديد", { askDays { days -> storeAction(id, "EXTEND", JSONObject().put("days", days)) } })
        }

        card(root, "التواصل والتنظيم") {
            if (owner) {
                addActionRow(this, "🔔 إرسال إشعار", { notificationDialog(id) }, "⇄ نقل إلى وكيل", { transferAgentDialog(id) })
            } else {
                addView(action("🔔 إرسال إشعار") { notificationDialog(id) })
            }
        }

        if (owner) {
            card(root, "منطقة حساسة", "الحذف النهائي متاح لمالك النظام فقط") {
                addView(action("🗑 حذف المشترك نهائيًا") {
                    confirmPermanentDelete(1) {
                        storeAction(id, "DELETE", JSONObject().put("confirm", "DELETE_SUBSCRIBER"))
                    }
                })
            }
        }

        AlertDialog.Builder(this).setTitle("إدارة المشترك").setView(ScrollView(this).apply { addView(root) })
            .setNegativeButton("إغلاق", null).show()
    }
'''
replace_between('    private fun subscriberActions(', '    private fun storeAction(', subscriber_actions)

bulk_selection = r'''    private fun showBulkSelection() {
        request("GET", "/api/v1/manage/subscribers?limit=500", null) { result ->
            result.onSuccess { data ->
                val stores = data.optJSONArray("subscribers") ?: JSONArray()
                if (stores.length() == 0) { toast("لا يوجد مشتركون"); return@onSuccess }
                selectedStores.clear()
                val labels = Array(stores.length()) { i -> subscriberLabel(stores.getJSONObject(i)) }
                val checked = BooleanArray(stores.length())
                val dialog = AlertDialog.Builder(this).setTitle("☑ تحديد المشتركين للعمليات الجماعية")
                    .setMultiChoiceItems(labels, checked) { _, which, value ->
                        val id = stores.getJSONObject(which).optString("storeId")
                        if (value) selectedStores += id else selectedStores -= id
                    }
                    .setPositiveButton("متابعة") { _, _ -> bulkActions() }
                    .setNegativeButton("إلغاء", null)
                    .create()
                dialog.show()
            }.onFailure { toast("تعذر تحميل المشتركين: ${it.message}") }
        }
    }
'''
replace_between('    private fun showBulkSelection()', '    private fun bulkActions(', bulk_selection)

bulk_actions = r'''    private fun bulkActions() {
        if (selectedStores.isEmpty()) { toast("لم تحدد مشتركين"); return }
        val actions = mutableListOf("⏸ إيقاف", "▶ إعادة تفعيل", "▣ أرشفة", "＋ تمديد الاشتراك", "🔔 إرسال إشعار")
        if (mode == "OWNER") actions += "🗑 حذف نهائي"
        AlertDialog.Builder(this).setTitle("عمليات جماعية • ${selectedStores.size} مشترك")
            .setMessage("اختر العملية التي تريد تنفيذها على جميع الحسابات المحددة.")
            .setItems(actions.toTypedArray()) { _, which ->
                when (which) {
                    0 -> bulkRequest("SUSPEND")
                    1 -> bulkRequest("REACTIVATE")
                    2 -> confirm("أرشفة ${selectedStores.size} مشترك؟") { bulkRequest("ARCHIVE") }
                    3 -> askDays { bulkRequest("EXTEND", JSONObject().put("days", it)) }
                    4 -> {
                        val message = EditText(this).apply { hint = "نص الإشعار"; minLines = 3 }
                        AlertDialog.Builder(this).setTitle("🔔 إشعار جماعي").setView(message)
                            .setPositiveButton("إرسال") { _, _ ->
                                bulkRequest("SEND_NOTIFICATION", JSONObject().put("title", "إشعار من إدارة النظام").put("message", message.text.toString()))
                            }.setNegativeButton("إلغاء", null).show()
                    }
                    5 -> if (mode == "OWNER") confirmPermanentDelete(selectedStores.size) {
                        bulkRequest("DELETE", JSONObject().put("confirm", "DELETE_SUBSCRIBER"))
                    }
                }
            }.setNegativeButton("إلغاء", null).show()
    }
'''
replace_between('    private fun bulkActions()', '    private fun bulkRequest(', bulk_actions)

notifications = r'''    private fun showNotifications() {
        loadNotifications("")
    }

    private fun loadNotifications(category: String) {
        val root = base("🔔 مركز الإشعارات", if (category.isBlank()) "كل الإشعارات مرتبة من الأحدث" else "القسم: ${categoryArabic(category)}")
        card(root, "التصفية حسب النوع", "اختر القسم المطلوب") {
            addActionRow(this, "الكل", { loadNotifications("") }, "التفعيل", { loadNotifications("ACTIVATION") })
            addActionRow(this, "الوكلاء", { loadNotifications("AGENT") }, "التسجيل", { loadNotifications("REGISTRATION") })
            addActionRow(this, "الاشتراكات", { loadNotifications("SUBSCRIPTION") }, "الأجهزة", { loadNotifications("DEVICE") })
            addActionRow(this, "الأمان", { loadNotifications("SECURITY") }, "النظام", { loadNotifications("SYSTEM") })
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        card(content, "جاري التحميل...", "يتم جلب أحدث الإشعارات") { }
        root.addView(action("← العودة إلى إدارة النظام") { showHome() })
        display(root)

        val suffix = if (category.isBlank()) "" else "?category=${enc(category)}"
        request("GET", "/api/v1/manage/notifications$suffix", null) { result ->
            content.removeAllViews()
            result.onSuccess { data ->
                val items = data.optJSONArray("notifications") ?: JSONArray()
                if (items.length() == 0) {
                    card(content, "لا توجد إشعارات", "لا يوجد محتوى في هذا القسم حاليًا") { }
                    return@onSuccess
                }
                for (i in 0 until items.length()) {
                    val n = items.getJSONObject(i)
                    val unread = n.optLong("readAt") == 0L
                    val mark = if (unread) "●" else "○"
                    val cat = categoryArabic(n.optString("category"))
                    card(content, "$mark ${n.optString("title").ifBlank { "إشعار" }}", "$cat • ${time(n.optLong("createdAt"))}") {
                        addView(TextView(this@SystemManagement1971Activity).apply {
                            text = n.optString("body")
                            textSize = 14f
                            setTextColor(ink)
                            gravity = Gravity.RIGHT
                            maxLines = 4
                        })
                        addView(action(if (unread) "فتح وتعليم كمقروء" else "فتح التفاصيل") {
                            AlertDialog.Builder(this@SystemManagement1971Activity)
                                .setTitle(n.optString("title").ifBlank { "إشعار" })
                                .setMessage("القسم: $cat\nالوقت: ${time(n.optLong("createdAt"))}\n\n${n.optString("body")}")
                                .setPositiveButton(if (unread) "تمت القراءة" else "إغلاق") { _, _ ->
                                    if (unread) request("POST", "/api/v1/manage/notifications/${enc(n.optString("notificationId"))}/read", JSONObject()) { loadNotifications(category) }
                                }.show()
                        })
                    }
                }
            }.onFailure {
                card(content, "تعذر تحميل الإشعارات", it.message ?: "خطأ غير معروف") { }
            }
        }
    }
'''
replace_between('    private fun showNotifications()', '    private fun showAudit(', notifications)

s = s.replace('إدارة النظام 1.9.71', 'إدارة النظام 1.9.72')
s = s.replace('بوابة الوكيل 1.9.71', 'بوابة الوكيل 1.9.72')
s = s.replace('ربط مركز إدارة 1.9.71', 'ربط مركز إدارة 1.9.72')

P.write_text(s, encoding='utf-8')
print('ATTEND-PRO 1.9.72 management UI patch applied')
