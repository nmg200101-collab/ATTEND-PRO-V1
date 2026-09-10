package com.attendpro.store

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.attendpro.core.AppIntegrity1982
import com.attendpro.core.StoreRepository
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SystemManagement1971Activity : Activity() {
    private lateinit var repo: StoreRepository
    private var mode: String = "OWNER"
    private var agentToken: String = ""
    private var serverUrl: String = ""
    private val selectedStores = linkedSetOf<String>()

    private val bg = Color.rgb(244, 247, 246)
    private val surface = Color.rgb(253, 254, 254)
    private val soft = Color.rgb(235, 242, 240)
    private val accent = Color.rgb(63, 101, 94)
    private val accentDark = Color.rgb(45, 78, 72)
    private val line = Color.rgb(218, 228, 225)
    private val danger = Color.rgb(154, 67, 67)
    private val ink = Color.rgb(32, 45, 45)
    private val muted = Color.rgb(94, 110, 108)

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
        mode = intent.getStringExtra("mode")?.uppercase(Locale.ROOT) ?: "OWNER"
        serverUrl = repo.serverUrl.trimEnd('/')
        if (mode == "OWNER") {
            if (serverUrl.isBlank() || repo.centralOwnerApiKey.isBlank()) {
                configureOwnerConnection()
            } else {
                showHome()
            }
        } else {
            agentLogin()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, stroke: Int = line, radius: Int = 16): GradientDrawable = GradientDrawable().apply {
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

    private fun showOverflowMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "⌂ الرئيسية")
            menu.add(0, 2, 1, "🔔 مركز الإشعارات")
            menu.add(0, 3, 2, "📘 دليل إدارة النظام")
            menu.add(0, 4, 3, "🛡 سجل العمليات")
            menu.add(0, 5, 4, "↻ تحديث الصفحة")
            menu.add(0, 6, 5, "＋ إجراء سريع")
            menu.add(0, 7, 6, "ⓘ حول إدارة النظام")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> showHome()
                    2 -> showNotifications()
                    3 -> showUserGuide()
                    4 -> showAudit()
                    5 -> showHome()
                    6 -> showQuickActions()
                    7 -> AlertDialog.Builder(this@SystemManagement1971Activity)
                        .setTitle("ATTEND-PRO • إدارة النظام 1.9.75")
                        .setMessage("واجهة الإدارة النهائية بألوان هادئة، بحث وفلاتر، عمليات جماعية، إشعارات منظمة، سجل عمليات، ووصول سريع للأدوات. نظام الارتباط الأساسي بقي دون تعديل.")
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

1) لوحة العرض
• تعرض ملخص المشتركين والوكلاء والحالات الرئيسية بسرعة.
• استخدم الأقسام للانتقال بين المشتركين والوكلاء والصلاحيات والإشعارات والسجل.

2) المشتركـون
• استخدم البحث للوصول بالاسم أو الهاتف أو المعرّف أو الوكيل.
• استخدم الفلاتر لعرض النشط أو الموقوف أو المنتهي أو المؤرشف.
• افتح الحساب للتحكم الفردي في حالته واشتراكه.

3) العمليات الجماعية
• افتح «إدارة جماعية» وحدد الحسابات المطلوبة.
• تظهر أدوات الإيقاف وإعادة التفعيل والأرشفة والتمديد والإشعار والحذف للمالك.
• الحذف النهائي يحتاج تأكيدًا واضحًا ولا يمكن التراجع عنه.

4) الوكلاء والصلاحيات
• أنشئ الوكيل وحدد مستوى الثقة والصلاحيات المسموحة له.
• صلاحيات الوكيل تُطبّق على نطاق العملاء المسموح به فقط.

5) الإشعارات
• مركز الإشعارات مرتب حسب النوع وحالة القراءة.
• يمكن تعليم الإشعارات غير المقروءة كمقروءة دفعة واحدة.

6) سجل العمليات
• ابحث بالإجراء أو المستخدم أو الحساب لمراجعة النشاط الإداري.

7) قائمة ⋮ والإجراء السريع
• للوصول السريع إلى الرئيسية والإشعارات والدليل والسجل والإجراءات المتكررة."""
        }
        AlertDialog.Builder(this)
            .setTitle("📘 دليل إدارة النظام")
            .setView(ScrollView(this).apply { addView(text) })
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun statusArabic(value: String): String = when (value.uppercase(Locale.ROOT)) {
        "ACTIVE" -> "نشط"
        "TRIAL" -> "تجريبي"
        "SUSPENDED" -> "موقوف"
        "ARCHIVED" -> "مؤرشف"
        "EXPIRED" -> "منتهي"
        "PENDING" -> "بانتظار الموافقة"
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

    private fun card(root: LinearLayout, title: String, description: String = "", block: LinearLayout.() -> Unit) {
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

    private fun authToken(): String = if (mode == "OWNER") repo.centralOwnerApiKey else agentToken

    private fun configureOwnerConnection() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), 0) }
        val url = EditText(this).apply { hint = "https://server.example.com"; setText(repo.serverUrl) }
        val key = EditText(this).apply { hint = "مفتاح إدارة الخادم"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        box.addView(url); box.addView(key)
        val dialog = AlertDialog.Builder(this).setTitle("ربط مركز إدارة 1.9.74").setView(box)
            .setPositiveButton("حفظ", null).setNegativeButton("إلغاء") { _, _ -> finish() }.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val u = url.text.toString().trim().trimEnd('/')
                val k = key.text.toString().trim()
                if (!u.startsWith("https://")) { url.error = "استخدم HTTPS"; return@setOnClickListener }
                if (k.length < 32) { key.error = "المفتاح قصير"; return@setOnClickListener }
                repo.serverUrl = u
                repo.centralOwnerApiKey = k
                serverUrl = u
                dialog.dismiss()
                showHome()
            }
        }
        dialog.show()
    }

    private fun agentLogin() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), 0) }
        val url = EditText(this).apply { hint = "رابط الخادم HTTPS"; setText(repo.serverUrl) }
        val token = EditText(this).apply { hint = "رمز الوكيل"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        box.addView(url); box.addView(token)
        val dialog = AlertDialog.Builder(this).setTitle("بوابة الوكيل المركزية").setView(box)
            .setPositiveButton("دخول", null).setNegativeButton("إلغاء") { _, _ -> finish() }.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val u = url.text.toString().trim().trimEnd('/')
                val t = token.text.toString().trim()
                if (!u.startsWith("https://")) { url.error = "استخدم HTTPS"; return@setOnClickListener }
                if (t.length < 24) { token.error = "رمز الوكيل غير صالح"; return@setOnClickListener }
                serverUrl = u
                agentToken = t
                // Agent token deliberately remains in memory only.
                request("GET", "/api/v1/manage/dashboard", null) { result ->
                    if (result.isFailure) {
                        toast("تعذر دخول الوكيل: ${result.exceptionOrNull()?.message ?: "خطأ"}")
                    } else {
                        repo.serverUrl = u
                        dialog.dismiss()
                        showHome()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun sectionButton(label: String, key: String, current: String): Button = Button(this).apply {
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

    private fun showQuickActions() {
        val items = if (mode == "OWNER") arrayOf("بحث عن مشترك", "إدارة جماعية", "إضافة وكيل", "مركز الإشعارات", "سجل العمليات", "✉ مراسلة محل / موظف")
        else arrayOf("بحث عن مشترك", "إدارة جماعية", "طلبات التفعيل", "مركز الإشعارات", "سجل العمليات")
        AlertDialog.Builder(this).setTitle("＋ إجراء سريع").setItems(items) { _, which ->
            if (mode == "OWNER") when (which) {
                0 -> showSubscribers()
                1 -> showBulkSelection()
                2 -> createAgentDialog()
                3 -> showNotifications()
                4 -> showAudit()
                5 -> showOwnerMessaging1975()
            } else when (which) {
                0 -> showSubscribers()
                1 -> showBulkSelection()
                2 -> showAgentActivations()
                3 -> showNotifications()
                4 -> showAudit()
            }
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun showOwnerMessaging1975() {
        if (mode != "OWNER") { toast("هذه الميزة لمالك النظام فقط"); return }
        AlertDialog.Builder(this).setTitle("✉ مراسلة المحلات والموظفين").setItems(arrayOf("رسالة إلى إدارة محل", "رسالة خاصة إلى موظف", "عرض ردود الموظفين")) { _, which ->
            when (which) {
                0 -> ownerMessageComposer1975(false)
                1 -> ownerMessageComposer1975(true)
                else -> ownerReplies1975()
            }
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun ownerMessageComposer1975(toEmployee: Boolean) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(18), dp(6), dp(18), 0) }
        val store = EditText(this).apply { hint = "معرّف المحل STORE-..."; setSingleLine(true) }
        val employee = EditText(this).apply { hint = "معرّف الموظف"; setSingleLine(true); visibility = if (toEmployee) View.VISIBLE else View.GONE }
        val title = EditText(this).apply { hint = "عنوان الرسالة"; setText(if (toEmployee) "رسالة من إدارة النظام" else "إشعار من إدارة النظام") }
        val message = EditText(this).apply { hint = "اكتب الرسالة"; minLines = 3 }
        val voice = CheckBox(this).apply { text = "قراءة الرسالة بصوت على هاتف الموظف"; isChecked = toEmployee; visibility = if (toEmployee) View.VISIBLE else View.GONE }
        box.addView(store); box.addView(employee); box.addView(title); box.addView(message); box.addView(voice)
        val d = AlertDialog.Builder(this).setTitle(if (toEmployee) "رسالة خاصة لموظف" else "رسالة لإدارة محل").setView(box).setPositiveButton("إرسال", null).setNegativeButton("إلغاء", null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val sid = store.text.toString().trim(); val eid = employee.text.toString().trim(); val text = message.text.toString().trim()
                if (sid.isBlank()) { store.error = "معرّف المحل مطلوب"; return@setOnClickListener }
                if (toEmployee && eid.isBlank()) { employee.error = "معرّف الموظف مطلوب"; return@setOnClickListener }
                if (text.isBlank()) { message.error = "اكتب الرسالة"; return@setOnClickListener }
                val path = if (toEmployee) "/api/v1/messages/owner/send-employee" else "/api/v1/messages/owner/send-store"
                val body = JSONObject().put("storeId", sid).put("title", title.text.toString()).put("message", text).put("priority", "IMPORTANT").put("voiceEnabled", voice.isChecked)
                if (toEmployee) body.put("employeeId", eid)
                request("POST", path, body) { r -> r.onSuccess { toast("تم إرسال الرسالة ✓"); d.dismiss() }.onFailure { toast("تعذر الإرسال: ${it.message}") } }
            }
        }
        d.show()
    }

    private fun ownerReplies1975() {
        request("POST", "/api/v1/messages/owner/inbox", JSONObject().put("limit", 100)) { result ->
            result.onSuccess { data ->
                val a = data.optJSONArray("messages") ?: JSONArray()
                val text = if (a.length() == 0) "لا توجد ردود حاليًا" else buildString {
                    for (i in 0 until a.length()) {
                        val m = a.optJSONObject(i) ?: continue
                        append("• ").append(m.optString("employeeId")).append(" — ").append(m.optString("body")).append("\n\n")
                    }
                }
                AlertDialog.Builder(this).setTitle("📨 ردود الموظفين").setMessage(text).setPositiveButton("إغلاق", null).show()
            }.onFailure { toast("تعذر تحميل الردود: ${it.message}") }
        }
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
        val root = base(if (owner) "إدارة النظام 1.9.75" else "بوابة الوكيل 1.9.75", "إدارة هادئة وواضحة • أقسام مرتبة • وصول أسرع")
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

    private fun loadDashboard(target: LinearLayout) {
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

    private fun createAgentDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(6), dp(20), 0) }
        val name = EditText(this).apply { hint = "اسم الوكيل" }
        val phone = EditText(this).apply { hint = "الهاتف"; inputType = InputType.TYPE_CLASS_PHONE }
        box.addView(name); box.addView(phone)
        val trustLabels = arrayOf("موثوق: تفعيل مباشر + إشعار المالك", "يحتاج موافقة المالك", "التفعيل معطل")
        val trustValues = arrayOf("AUTO_ACTIVATE_NOTIFY", "REQUIRE_APPROVAL", "ACTIVATION_DISABLED")
        var trust = 1
        AlertDialog.Builder(this).setTitle("إضافة وكيل").setView(box)
            .setSingleChoiceItems(trustLabels, trust) { _, which -> trust = which }
            .setPositiveButton("التالي") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isBlank()) { toast("اسم الوكيل مطلوب"); return@setPositiveButton }
                choosePermissions { permissions ->
                    val body = JSONObject().put("displayName", n).put("phone", phone.text.toString().trim())
                        .put("trustMode", trustValues[trust]).put("permissions", JSONArray(permissions))
                    request("POST", "/api/v1/manage/agents", body) { result ->
                        result.onSuccess { showOneTimeToken(it, "تم إنشاء الوكيل") }
                            .onFailure { toast("فشل إنشاء الوكيل: ${it.message}") }
                    }
                }
            }.setNegativeButton("إلغاء", null).show()
    }

    private val permissionLabels = linkedMapOf(
        "CUSTOMER_CREATE" to "إضافة عميل",
        "CUSTOMER_VIEW" to "عرض العملاء",
        "CUSTOMER_EDIT" to "تعديل بيانات العميل",
        "CUSTOMER_SUSPEND" to "إيقاف العميل",
        "CUSTOMER_REACTIVATE" to "إعادة التفعيل",
        "CUSTOMER_ARCHIVE" to "الأرشفة",
        "SUBSCRIPTION_ACTIVATE" to "تفعيل الاشتراك",
        "SUBSCRIPTION_EXTEND" to "تمديد الاشتراك",
        "DEVICE_MANAGE" to "إدارة الأجهزة",
        "NOTIFICATION_SEND" to "إرسال إشعارات",
        "REPORT_VIEW" to "عرض التقارير"
    )

    private fun choosePermissions(current: Set<String> = permissionLabels.keys.toSet(), done: (List<String>) -> Unit) {
        val keys = permissionLabels.keys.toList()
        val labels = permissionLabels.values.toTypedArray()
        val checked = BooleanArray(keys.size) { current.contains(keys[it]) }
        AlertDialog.Builder(this).setTitle("صلاحيات الوكيل").setMultiChoiceItems(labels, checked) { _, which, value -> checked[which] = value }
            .setPositiveButton("حفظ") { _, _ -> done(keys.indices.filter { checked[it] }.map { keys[it] }) }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun showAgents() {
        request("GET", "/api/v1/manage/agents", null) { result ->
            result.onSuccess { data ->
                val agents = data.optJSONArray("agents") ?: JSONArray()
                if (agents.length() == 0) { toast("لا يوجد وكلاء"); return@onSuccess }
                val labels = Array(agents.length()) { i ->
                    val a = agents.getJSONObject(i)
                    "${a.optString("displayName")} • ${trustArabic(a.optString("trustMode"))} • عملاء ${a.optInt("customerCount")}"
                }
                AlertDialog.Builder(this).setTitle("الوكلاء (${agents.length()})").setItems(labels) { _, which -> agentActions(agents.getJSONObject(which)) }
                    .setNegativeButton("إغلاق", null).show()
            }.onFailure { toast("تعذر تحميل الوكلاء: ${it.message}") }
        }
    }

    private fun agentActions(agent: JSONObject) {
        val id = agent.optString("agentId")
        val title = agent.optString("displayName")
        val actions = arrayOf("تعديل مستوى الثقة والصلاحيات", "تدوير رمز الوكيل", if (agent.optBoolean("active", true)) "تعطيل الوكيل" else "إعادة تنشيط الوكيل")
        AlertDialog.Builder(this).setTitle(title).setItems(actions) { _, which ->
            when (which) {
                0 -> editAgent(agent)
                1 -> request("POST", "/api/v1/manage/agents/${enc(id)}/token", JSONObject()) { r ->
                    r.onSuccess { showOneTimeToken(it, "رمز وكيل جديد") }.onFailure { toast("تعذر تدوير الرمز: ${it.message}") }
                }
                2 -> {
                    val body = JSONObject().put("active", !agent.optBoolean("active", true))
                    request("PATCH", "/api/v1/manage/agents/${enc(id)}", body) { r ->
                        r.onSuccess { toast("تم تحديث الوكيل") }.onFailure { toast("فشل التحديث: ${it.message}") }
                    }
                }
            }
        }.setNegativeButton("إغلاق", null).show()
    }

    private fun editAgent(agent: JSONObject) {
        val trustLabels = arrayOf("موثوق: تفعيل مباشر + إشعار", "يحتاج موافقة", "التفعيل معطل")
        val trustValues = arrayOf("AUTO_ACTIVATE_NOTIFY", "REQUIRE_APPROVAL", "ACTIVATION_DISABLED")
        var trust = trustValues.indexOf(agent.optString("trustMode")).let { if (it < 0) 1 else it }
        AlertDialog.Builder(this).setTitle("مستوى ثقة ${agent.optString("displayName")}")
            .setSingleChoiceItems(trustLabels, trust) { _, which -> trust = which }
            .setPositiveButton("الصلاحيات") { _, _ ->
                val current = mutableSetOf<String>()
                val arr = agent.optJSONArray("permissions") ?: JSONArray()
                for (i in 0 until arr.length()) current += arr.optString(i)
                choosePermissions(current) { permissions ->
                    val body = JSONObject().put("trustMode", trustValues[trust]).put("permissions", JSONArray(permissions))
                    request("PATCH", "/api/v1/manage/agents/${enc(agent.optString("agentId"))}", body) { r ->
                        r.onSuccess { toast("تم حفظ الصلاحيات") }.onFailure { toast("فشل الحفظ: ${it.message}") }
                    }
                }
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun showOneTimeToken(data: JSONObject, title: String) {
        val token = data.optString("apiToken")
        val message = if (token.isBlank()) data.toString(2) else "احفظ الرمز الآن؛ يعرض مرة واحدة فقط:\n\n$token"
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton("نسخ") { _, _ ->
                if (token.isNotBlank()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ATTEND-PRO agent token", token))
                    toast("تم النسخ")
                }
            }.setNegativeButton("إغلاق", null).show()
    }

    private fun showSubscribers() {
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

    private fun subscriberLabel(s: JSONObject): String {
        val expiry = s.optLong("expiresAt", 0L)
        val date = if (expiry > 0) SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(expiry)) else "-"
        val agent = s.optString("agentName").ifBlank { "بدون وكيل" }
        return "${s.optString("storeName").ifBlank { "مشترك" }} • ${statusArabic(s.optString("status"))}\nحتى $date • $agent"
    }

    private fun subscriberActions(store: JSONObject) {
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

    private fun storeAction(storeId: String, action: String, extra: JSONObject = JSONObject()) {
        extra.put("action", action)
        request("POST", "/api/v1/manage/subscribers/${enc(storeId)}/action", extra) { r ->
            r.onSuccess { toast("✓ تم تنفيذ العملية بنجاح") }.onFailure {
                AlertDialog.Builder(this).setTitle("تعذر تنفيذ العملية").setMessage(it.message ?: "خطأ غير معروف").setPositiveButton("إغلاق", null).show()
            }
        }
    }

    private fun notificationDialog(storeId: String) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(6), dp(20), 0) }
        val title = EditText(this).apply { hint = "العنوان" }
        val message = EditText(this).apply { hint = "نص الإشعار"; minLines = 3 }
        box.addView(title); box.addView(message)
        AlertDialog.Builder(this).setTitle("إرسال إشعار").setView(box).setPositiveButton("إرسال") { _, _ ->
            storeAction(storeId, "SEND_NOTIFICATION", JSONObject().put("title", title.text.toString()).put("message", message.text.toString()))
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun transferAgentDialog(storeId: String) {
        val input = EditText(this).apply { hint = "معرّف الوكيل AGT-..." }
        AlertDialog.Builder(this).setTitle("نقل المشترك إلى وكيل").setView(input).setPositiveButton("نقل") { _, _ ->
            storeAction(storeId, "TRANSFER_AGENT", JSONObject().put("agentId", input.text.toString().trim()))
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun showBulkSelection() {
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

    private fun bulkActions() {
        showBulkSelection()
    }

    private fun bulkRequest(action: String, extra: JSONObject = JSONObject(), after: () -> Unit = {}) {
        if (selectedStores.isEmpty()) { toast("لم تحدد مشتركين"); return }
        extra.put("action", action).put("storeIds", JSONArray(selectedStores.toList()))
        request("POST", "/api/v1/manage/subscribers/bulk", extra) { result ->
            result.onSuccess {
                val success = it.optInt("successCount")
                val failure = it.optInt("failureCount")
                if (failure == 0) toast("✓ اكتملت العملية على $success مشترك")
                else AlertDialog.Builder(this).setTitle("نتيجة العملية الجماعية")
                    .setMessage("نجح: $success\nفشل: $failure\n\nيمكنك إعادة المحاولة للحسابات التي لم تُنفذ عليها العملية.")
                    .setPositiveButton("إغلاق", null).show()
                selectedStores.clear()
                after()
            }.onFailure { toast("فشلت العملية الجماعية: ${it.message}") }
        }
    }

    private fun markNotificationsRead(ids: List<String>, index: Int = 0, onDone: () -> Unit) {
        if (index >= ids.size) { onDone(); return }
        request("POST", "/api/v1/manage/notifications/${enc(ids[index])}/read", JSONObject()) {
            markNotificationsRead(ids, index + 1, onDone)
        }
    }

    private fun showNotifications() {
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

    private fun showAudit() {
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

    private fun claimActivationDialog() {
        val input = EditText(this).apply { hint = "requestId" }
        AlertDialog.Builder(this).setTitle("استلام طلب تفعيل").setMessage("أدخل رقم طلب التفعيل الذي أرسله لك العميل. بعد الاستلام لن يستطيع وكيل آخر الاستحواذ عليه.")
            .setView(input).setPositiveButton("استلام") { _, _ ->
                val id = input.text.toString().trim()
                request("POST", "/api/v1/manage/activation/claim", JSONObject().put("requestId", id)) { r ->
                    r.onSuccess { toast("تم استلام طلب ${it.optString("storeName")}") }.onFailure { toast("تعذر الاستلام: ${it.message}") }
                }
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun showAgentActivations() {
        request("GET", "/api/v1/manage/activation/pending", null) { result ->
            result.onSuccess { data ->
                val rows = data.optJSONArray("requests") ?: JSONArray()
                if (rows.length() == 0) { toast("لا توجد طلبات مستلمة"); return@onSuccess }
                val labels = Array(rows.length()) { i ->
                    val r = rows.getJSONObject(i)
                    "${r.optString("storeName")} • ${r.optString("subscriptionType")} • ${r.optString("status")} • ${r.optString("requestId").takeLast(8)}"
                }
                AlertDialog.Builder(this).setTitle("طلبات التفعيل (${rows.length()})").setItems(labels) { _, which -> agentActivationActions(rows.getJSONObject(which)) }
                    .setNegativeButton("إغلاق", null).show()
            }.onFailure { toast("تعذر تحميل الطلبات: ${it.message}") }
        }
    }

    private fun agentActivationActions(requestRow: JSONObject) {
        val requestId = requestRow.optString("requestId")
        val actions = arrayOf("تفعيل الآن", "تحرير الطلب")
        AlertDialog.Builder(this).setTitle(requestRow.optString("storeName")).setMessage("المالك: ${requestRow.optString("ownerName")}\nالهاتف: ${requestRow.optString("phone")}")
            .setItems(actions) { _, which ->
                if (which == 0) askDays { days ->
                    request("POST", "/api/v1/manage/activation/approve", JSONObject().put("requestId", requestId).put("days", days)) { r ->
                        r.onSuccess { toast("تم التفعيل وإشعار المالك") }.onFailure { toast("تعذر التفعيل: ${it.message}") }
                    }
                } else {
                    request("POST", "/api/v1/manage/activation/release", JSONObject().put("requestId", requestId)) { r ->
                        r.onSuccess { toast("تم تحرير الطلب") }.onFailure { toast("تعذر تحرير الطلب: ${it.message}") }
                    }
                }
            }.setNegativeButton("إغلاق", null).show()
    }

    private fun askDays(done: (Int) -> Unit) {
        val input = EditText(this).apply { hint = "عدد الأيام"; inputType = InputType.TYPE_CLASS_NUMBER; setText("365") }
        AlertDialog.Builder(this).setTitle("مدة الاشتراك").setView(input).setPositiveButton("متابعة") { _, _ ->
            done(input.text.toString().toIntOrNull()?.coerceIn(1, 3650) ?: 365)
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun confirm(message: String, yes: () -> Unit) {
        AlertDialog.Builder(this).setTitle("تأكيد").setMessage(message).setPositiveButton("تأكيد") { _, _ -> yes() }.setNegativeButton("إلغاء", null).show()
    }

    private fun trustArabic(value: String): String = when (value) {
        "AUTO_ACTIVATE_NOTIFY" -> "موثوق"
        "ACTIVATION_DISABLED" -> "التفعيل معطل"
        else -> "يحتاج موافقة"
    }

    private fun time(ms: Long): String = if (ms <= 0L) "-" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))
    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun request(method: String, path: String, body: JSONObject?, done: (Result<JSONObject>) -> Unit) {
        val token = authToken()
        if (serverUrl.isBlank() || token.isBlank()) {
            done(Result.failure(IllegalStateException("بيانات الاتصال غير مكتملة")))
            return
        }
        Thread {
            val result = runCatching {
                val connection = (URL(serverUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 12_000
                    readTimeout = 18_000
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    doInput = true
                    if (body != null && method != "GET") doOutput = true
                }
                if (body != null && method != "GET") connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"
                val json = runCatching { JSONObject(text) }.getOrElse { JSONObject().put("raw", text) }
                if (code !in 200..299) throw IllegalStateException(json.optString("error").ifBlank { "HTTP $code" })
                json
            }
            runOnUiThread { done(result) }
        }.apply { isDaemon = true }.start()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
