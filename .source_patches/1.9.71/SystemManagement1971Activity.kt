package com.attendpro.store

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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

    private val bg = Color.rgb(246, 248, 251)
    private val ink = Color.rgb(26, 36, 48)
    private val muted = Color.rgb(92, 106, 120)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    private fun base(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(18), dp(20), dp(18), dp(36))
        setBackgroundColor(bg)
        addView(TextView(this@SystemManagement1971Activity).apply {
            text = title
            textSize = 24f
            setTextColor(ink)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        addView(TextView(this@SystemManagement1971Activity).apply {
            text = subtitle
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(16))
        })
    }

    private fun card(root: LinearLayout, title: String, description: String = "", block: LinearLayout.() -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(Color.WHITE)
        }
        box.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(ink)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        if (description.isNotBlank()) box.addView(TextView(this).apply {
            text = description
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(4), 0, dp(8))
        })
        box.block()
        root.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, dp(12))
        })
    }

    private fun action(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 15f
        isAllCaps = false
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    private fun display(root: LinearLayout) {
        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(root) })
    }

    private fun authToken(): String = if (mode == "OWNER") repo.centralOwnerApiKey else agentToken

    private fun configureOwnerConnection() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), 0) }
        val url = EditText(this).apply { hint = "https://server.example.com"; setText(repo.serverUrl) }
        val key = EditText(this).apply { hint = "مفتاح إدارة الخادم"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        box.addView(url); box.addView(key)
        val dialog = AlertDialog.Builder(this).setTitle("ربط مركز إدارة 1.9.71").setView(box)
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

    private fun showHome() {
        val owner = mode == "OWNER"
        val root = base(if (owner) "إدارة النظام 1.9.71" else "بوابة الوكيل 1.9.71", if (owner) "إدارة مركزية • وكلاء • مشتركون • إشعارات • رقابة" else "عملاؤك وصلاحياتك وطلبات التفعيل")
        val dashboardText = TextView(this).apply { text = "جاري تحميل الملخص..."; textSize = 15f; setTextColor(ink) }
        card(root, "ملخص النظام") { addView(dashboardText); addView(action("تحديث") { loadDashboard(dashboardText) }) }

        if (owner) {
            card(root, "الوكلاء", "إنشاء الوكلاء وتحديد مستوى الثقة والصلاحيات") {
                addView(action("إدارة الوكلاء") { showAgents() })
                addView(action("إضافة وكيل") { createAgentDialog() })
            }
        }

        card(root, "المشتركون", if (owner) "كل الحسابات المسجلة على الخادم مع إجراءات فردية وجماعية" else "الحسابات التابعة لك فقط") {
            addView(action("عرض المشتركين") { showSubscribers() })
            addView(action("عمليات جماعية") { showBulkSelection() })
        }

        if (!owner) {
            card(root, "طلبات التفعيل", "استلم طلب عميل برقم الطلب ثم فعّله إذا كانت صلاحيتك تسمح") {
                addView(action("الطلبات المستلمة") { showAgentActivations() })
                addView(action("استلام طلب جديد") { claimActivationDialog() })
            }
        }

        card(root, "الإشعارات") {
            addView(action("مركز الإشعارات") { showNotifications() })
        }
        card(root, "الرقابة") {
            addView(action("سجل العمليات") { showAudit() })
        }
        root.addView(action("إغلاق") { finish() })
        display(root)
        loadDashboard(dashboardText)
    }

    private fun loadDashboard(target: TextView) {
        request("GET", "/api/v1/manage/dashboard", null) { result ->
            result.onSuccess { x ->
                target.text = "المشتركون ${x.optInt("totalSubscribers")} • نشط ${x.optInt("active")} • موقوف ${x.optInt("suspended")} • مؤرشف ${x.optInt("archived")} • منتهي ${x.optInt("expired")}\nالوكلاء ${x.optInt("agents")} • إشعارات غير مقروءة ${x.optInt("unreadNotifications")}" 
            }.onFailure { target.text = "تعذر تحميل الملخص: ${it.message}" }
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
        request("GET", "/api/v1/manage/subscribers?limit=300", null) { result ->
            result.onSuccess { data ->
                val stores = data.optJSONArray("subscribers") ?: JSONArray()
                if (stores.length() == 0) { toast("لا يوجد مشتركون ضمن نطاقك"); return@onSuccess }
                val labels = Array(stores.length()) { i -> subscriberLabel(stores.getJSONObject(i)) }
                AlertDialog.Builder(this).setTitle("المشتركون (${stores.length()})").setItems(labels) { _, which -> subscriberActions(stores.getJSONObject(which)) }
                    .setNegativeButton("إغلاق", null).show()
            }.onFailure { toast("تعذر تحميل المشتركين: ${it.message}") }
        }
    }

    private fun subscriberLabel(s: JSONObject): String {
        val expiry = s.optLong("expiresAt", 0L)
        val date = if (expiry > 0) SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(expiry)) else "-"
        val agent = s.optString("agentName").ifBlank { "بدون وكيل" }
        return "${s.optString("storeName")} • ${s.optString("status")} • حتى $date • $agent"
    }

    private fun subscriberActions(store: JSONObject) {
        val id = store.optString("storeId")
        val base = mutableListOf("إيقاف", "إعادة تفعيل", "أرشفة", "تمديد الاشتراك", "إرسال إشعار")
        if (mode == "OWNER") base += "نقل إلى وكيل"
        AlertDialog.Builder(this).setTitle(store.optString("storeName")).setMessage("المعرّف: $id\nالحالة: ${store.optString("status")}\nالوكيل: ${store.optString("agentName").ifBlank { "-" }}")
            .setItems(base.toTypedArray()) { _, which ->
                when (base[which]) {
                    "إيقاف" -> storeAction(id, "SUSPEND")
                    "إعادة تفعيل" -> storeAction(id, "REACTIVATE")
                    "أرشفة" -> confirm("أرشفة المشترك؟") { storeAction(id, "ARCHIVE") }
                    "تمديد الاشتراك" -> askDays { days -> storeAction(id, "EXTEND", JSONObject().put("days", days)) }
                    "إرسال إشعار" -> notificationDialog(id)
                    "نقل إلى وكيل" -> transferAgentDialog(id)
                }
            }.setNegativeButton("إغلاق", null).show()
    }

    private fun storeAction(storeId: String, action: String, extra: JSONObject = JSONObject()) {
        extra.put("action", action)
        request("POST", "/api/v1/manage/subscribers/${enc(storeId)}/action", extra) { r ->
            r.onSuccess { toast("تم تنفيذ العملية") }.onFailure { toast("فشلت العملية: ${it.message}") }
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
                val labels = Array(stores.length()) { i -> subscriberLabel(stores.getJSONObject(i)) }
                val checked = BooleanArray(stores.length()) { selectedStores.contains(stores.getJSONObject(it).optString("storeId")) }
                AlertDialog.Builder(this).setTitle("حدد المشتركين").setMultiChoiceItems(labels, checked) { _, which, value ->
                    val id = stores.getJSONObject(which).optString("storeId")
                    if (value) selectedStores += id else selectedStores -= id
                }.setPositiveButton("العمليات") { _, _ -> bulkActions() }.setNegativeButton("إلغاء", null).show()
            }.onFailure { toast("تعذر تحميل المشتركين: ${it.message}") }
        }
    }

    private fun bulkActions() {
        if (selectedStores.isEmpty()) { toast("لم تحدد مشتركين"); return }
        val actions = arrayOf("إيقاف", "إعادة تفعيل", "أرشفة", "تمديد الاشتراك", "إرسال إشعار")
        AlertDialog.Builder(this).setTitle("${selectedStores.size} مشترك").setItems(actions) { _, which ->
            when (which) {
                0 -> bulkRequest("SUSPEND")
                1 -> bulkRequest("REACTIVATE")
                2 -> confirm("أرشفة ${selectedStores.size} مشترك؟") { bulkRequest("ARCHIVE") }
                3 -> askDays { bulkRequest("EXTEND", JSONObject().put("days", it)) }
                4 -> {
                    val message = EditText(this).apply { hint = "نص الإشعار" }
                    AlertDialog.Builder(this).setTitle("إشعار جماعي").setView(message).setPositiveButton("إرسال") { _, _ ->
                        bulkRequest("SEND_NOTIFICATION", JSONObject().put("title", "إشعار من إدارة النظام").put("message", message.text.toString()))
                    }.setNegativeButton("إلغاء", null).show()
                }
            }
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun bulkRequest(action: String, extra: JSONObject = JSONObject()) {
        extra.put("action", action).put("storeIds", JSONArray(selectedStores.toList()))
        request("POST", "/api/v1/manage/subscribers/bulk", extra) { result ->
            result.onSuccess {
                toast("العملية ${it.optString("status")} • نجاح ${it.optInt("successCount")} • فشل ${it.optInt("failureCount")}")
                selectedStores.clear()
            }.onFailure { toast("فشلت العملية الجماعية: ${it.message}") }
        }
    }

    private fun showNotifications() {
        val categories = arrayOf("الكل", "التفعيل", "الوكلاء", "التسجيل", "الاشتراكات", "الأجهزة", "الأمان", "النظام")
        val values = arrayOf("", "ACTIVATION", "AGENT", "REGISTRATION", "SUBSCRIPTION", "DEVICE", "SECURITY", "SYSTEM")
        AlertDialog.Builder(this).setTitle("نوع الإشعارات").setItems(categories) { _, which -> loadNotifications(values[which]) }.show()
    }

    private fun loadNotifications(category: String) {
        val suffix = if (category.isBlank()) "" else "?category=${enc(category)}"
        request("GET", "/api/v1/manage/notifications$suffix", null) { result ->
            result.onSuccess { data ->
                val items = data.optJSONArray("notifications") ?: JSONArray()
                if (items.length() == 0) { toast("لا توجد إشعارات"); return@onSuccess }
                val labels = Array(items.length()) { i ->
                    val n = items.getJSONObject(i)
                    val mark = if (n.optLong("readAt") == 0L) "●" else "○"
                    "$mark ${n.optString("title")} • ${time(n.optLong("createdAt"))}"
                }
                AlertDialog.Builder(this).setTitle("الإشعارات (${items.length()})").setItems(labels) { _, which ->
                    val n = items.getJSONObject(which)
                    AlertDialog.Builder(this).setTitle(n.optString("title")).setMessage(n.optString("body"))
                        .setPositiveButton("تعليم كمقروء") { _, _ ->
                            request("POST", "/api/v1/manage/notifications/${enc(n.optString("notificationId"))}/read", JSONObject()) { }
                        }.setNegativeButton("إغلاق", null).show()
                }.setNegativeButton("إغلاق", null).show()
            }.onFailure { toast("تعذر تحميل الإشعارات: ${it.message}") }
        }
    }

    private fun showAudit() {
        request("GET", "/api/v1/manage/audit?limit=200", null) { result ->
            result.onSuccess { data ->
                val rows = data.optJSONArray("audit") ?: JSONArray()
                if (rows.length() == 0) { toast("لا يوجد سجل عمليات"); return@onSuccess }
                val text = StringBuilder()
                for (i in 0 until rows.length()) {
                    val a = rows.getJSONObject(i)
                    text.append("• ").append(a.optString("action")).append(" — ").append(a.optString("target_id")).append("\n")
                        .append(a.optString("actor_account_id")).append(" • ").append(time(a.optLong("created_at"))).append("\n\n")
                }
                AlertDialog.Builder(this).setTitle("سجل العمليات").setMessage(text.toString()).setPositiveButton("إغلاق", null).show()
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
