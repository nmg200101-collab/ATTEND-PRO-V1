package com.attendpro.employee

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.attendpro.core.AppLanguage
import com.attendpro.core.UiKit

class EmployeeUserGuideActivity : Activity() {
    private val p by lazy { UiKit.palette(this) }
    private fun t(ar: String, en: String) = AppLanguage.text(this, ar, en)

    private data class Topic(val titleAr: String, val titleEn: String, val bodyAr: String, val bodyEn: String)
    private data class Scenario(val titleAr: String, val titleEn: String, val subtitleAr: String, val subtitleEn: String, val topics: List<Topic>)

    private val scenarios by lazy {
        listOf(
            Scenario("ما الجديد في هذا الإصدار", "What's new in this version",
                "يتحدث هذا القسم مع كل إصدار للتطبيق.", "This section ships with and updates on every app release.",
                listOf(
                    Topic("الإصدار الحالي", "Current version",
                        "أنت تستخدم ATTEND PRO " + BuildConfig.VERSION_NAME + ". يتحدث هذا الدليل تلقائيًا مع كل تحديث للتطبيق ويشرح أي وظائف جديدة تخص الموظف.",
                        "You are using ATTEND PRO " + BuildConfig.VERSION_NAME + ". This guide updates with every app release and explains new Employee features."),
                    Topic("أهم التغييرات الحالية", "Current highlights",
                        "عرض الدوام بصيغة صباح/مساء، الرد بدون إنترنت عبر Bluetooth الموثق، وتحسين واجهة الرسائل واللغة الإنجليزية.",
                        "AM/PM shift display, offline replies over authenticated Bluetooth, and improved messaging and English UI.")
                )),
            Scenario("أريد ربط هاتفي بالمحل", "I want to pair my phone with the Store",
                "QR وBluetooth وكيف تعرف أن الربط نجح.", "QR, Bluetooth and how to confirm success.",
                listOf(
                    Topic("1. قبل الربط", "1. Before pairing",
                        "فعّل Bluetooth والموقع واسمح بالأجهزة القريبة. اطلب من مدير المحل فتح بيانات الموظف وإنشاء QR جديد. أبق التطبيق مفتوحًا أثناء الربط الأول.",
                        "Enable Bluetooth, Location and Nearby Devices. Ask Store Management to open your employee record and generate a fresh QR. Keep the app open during first pairing."),
                    Topic("2. مسح QR", "2. Scan QR",
                        "افتح «ربط الهاتف» في تطبيق الموظف وامسح QR قبل انتهاء صلاحيته. لا تعتمد على صورة قديمة. انتظر تأكيد النجاح، وليس مجرد «جاري الارتباط».",
                        "Open Pair phone in the Employee app and scan the QR before it expires. Do not reuse an old screenshot. Wait for confirmed success, not just “Pairing…”."),
                    Topic("3. بعد نجاح الربط", "3. After pairing",
                        "يستخدم التطبيق Heartbeat/ACK عبر BLE/GATT وقنوات أخرى عند توفرها. لا تحذف بيانات التطبيق ولا تعِد الربط إلا عند نقل الهاتف أو بطلب من إدارة المحل.",
                        "The app uses authenticated Heartbeat/ACK over BLE/GATT and other available channels. Do not clear app data or re-pair unless moving phones or instructed by Store Management.")
                )),
            Scenario("أريد تسجيل الحضور أو الانصراف", "I want to check in or check out",
                "اختيار الحركة ثم طريقة التحقق المسموحة.", "Choose the action and an allowed verification method.",
                listOf(
                    Topic("1. تسجيل الحركة", "1. Record attendance",
                        "من الرئيسية اختر حضورًا أو انصرافًا. سيعرض التطبيق فقط طرق التحقق المسموحة لك. أكمل الطريقة حتى تظهر رسالة نجاح.",
                        "From Home choose Check in or Check out. The app shows only verification methods allowed for you. Complete verification until success is shown."),
                    Topic("2. الموقع والقرب", "2. Location and proximity",
                        "قد يستخدم التطبيق Bluetooth وWi‑Fi وGPS لمعرفة قرب الهاتف. القرب وحده لا يسجل حضورًا تلقائيًا؛ يجب اكتمال طريقة التحقق التي اعتمدتها إدارة المحل.",
                        "Bluetooth, Wi‑Fi and GPS may be used to determine phone proximity. Proximity alone never records attendance; the Store-approved verification must complete."),
                    Topic("3. إثبات الوجود", "3. Presence proof",
                        "إذا وصلك طلب إثبات وجود افتحه ونفّذ الطريقة المحددة. إذا طلب المدير بصمة أو وجهًا فلا يكفي أن يكون الهاتف قريبًا.",
                        "When a presence-proof request arrives, open it and complete the required method. If biometrics or face are requested, phone proximity alone is not enough.")
                )),
            Scenario("أريد قراءة رسالة أو الرد بدون إنترنت", "I want to read or reply without Internet",
                "المراسلة المحلية عبر Bluetooth الموثق.", "Local messaging over authenticated Bluetooth.",
                listOf(
                    Topic("1. استلام رسالة محلية", "1. Receive a local message",
                        "إذا كان هاتفك متصلًا بجهاز المحل عبر Bluetooth الموثق، يمكن أن تصل رسالة إدارة المحل مباشرة بدون إنترنت وتبقى محفوظة في مركز الرسائل.",
                        "When your phone has an authenticated Bluetooth link to the Store, Store messages can arrive directly without Internet and remain saved in Messages."),
                    Topic("2. الرد بدون إنترنت", "2. Reply without Internet",
                        "افتح الرسالة واضغط «رد». إذا كانت جلسة Bluetooth الموثقة ما زالت متصلة، يرسل التطبيق الرد مباشرة إلى جهاز المحل بدون إنترنت. يظهر في صندوق رسائل المحل.",
                        "Open the message and tap Reply. If authenticated Bluetooth is still connected, the reply is sent directly to the Store without Internet and appears in the Store inbox."),
                    Topic("3. عند انقطاع Bluetooth", "3. When Bluetooth is unavailable",
                        "يحاول التطبيق استخدام الخادم إذا توفر الإنترنت. إذا لم توجد قناة Bluetooth موثقة ولا خادم متاح، سيخبرك أن الإرسال لم يكتمل بدل اعتبار الرد مرسلًا.",
                        "The app falls back to the server when Internet is available. If neither authenticated Bluetooth nor the server is available, it tells you sending failed instead of marking the reply sent.")
                )),
            Scenario("أريد فهم الموقع والعمل في الخلفية", "I want to understand background operation",
                "ما الذي يفعله الموقع وBluetooth عندما يكون التطبيق مغلقًا.", "What Location and Bluetooth do while the app is not open.",
                listOf(
                    Topic("الموقع في الخلفية", "Background location",
                        "عند تفعيل نطاق المحل قد يستخدم ATTEND PRO الموقع عندما يكون التطبيق مغلقًا أو غير مستخدم لقياس القرب وإرسال حالة القرب والدقة والوقت إلى إدارة المحل. الموقع وحده لا يسجل حضورًا.",
                        "When Store geofencing is enabled, ATTEND PRO may use location while the app is closed or not in use to measure proximity and send proximity state, accuracy and observation time to Store Management. Location alone does not record attendance."),
                    Topic("الظهور التلقائي", "Automatic visibility",
                        "اترك الظهور التلقائي مفعّلًا حتى يستطيع جهاز المحل اكتشاف هاتفك وإعادة الاتصال عند القرب. ظهور الهاتف لا يعني تسجيل حضور.",
                        "Keep automatic visibility enabled so the Store can discover your phone and reconnect when nearby. Being visible does not mean attendance was recorded.")
                )),
            Scenario("لدي مشكلة", "I have a problem",
                "حلول الربط والاتصال والرسائل والصلاحيات.", "Pairing, connection, messaging and permission troubleshooting.",
                listOf(
                    Topic("الربط لا يكتمل", "Pairing does not complete",
                        "تأكد من Bluetooth والموقع والأجهزة القريبة، اطلب QR جديدًا، وأعد تشغيل Bluetooth إذا لزم. النجاح يحتاج تأكيد الربط/ACK.",
                        "Verify Bluetooth, Location and Nearby Devices, request a fresh QR, and restart Bluetooth if needed. Success requires pairing/ACK confirmation."),
                    Topic("لا يظهر الهاتف للمحل", "Store cannot see the phone",
                        "افتح التطبيق مرة وتأكد من الصلاحيات والظهور التلقائي. أوقف Bluetooth وشغله إذا لم تعد القناة تلقائيًا.",
                        "Open the app once, check permissions and automatic visibility, then toggle Bluetooth if the channel does not recover."),
                    Topic("الرد بدون إنترنت لا يعمل", "Offline reply does not work",
                        "تأكد أن جهاز المحل يظهر متصلًا عبر Bluetooth الموثق. الرد المحلي يحتاج اتصال BLE/GATT حيًا؛ إذا انقطع انتظر إعادة الاتصال ثم أعد الإرسال.",
                        "Confirm the Store has an authenticated Bluetooth connection. Offline reply requires a live BLE/GATT session; if it drops, wait for reconnect and send again."),
                    Topic("قبل حذف البيانات", "Before clearing app data",
                        "لا تحذف بيانات التطبيق أو تعِد تثبيته كحل أول. تواصل مع إدارة المحل أولًا لأن الربط والإعدادات المحلية محفوظة على الجهاز.",
                        "Do not clear app data or reinstall as a first troubleshooting step. Contact Store Management first because pairing and local settings are stored on the device.")
                ))
        )
    }

    private lateinit var contentBox: LinearLayout
    private lateinit var search: EditText
    private var activeScenario: Scenario? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguage.applyToResources(this)
        renderShell()
    }

    private fun renderShell() {
        val dir = if (AppLanguage.isEnglish(this)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = dir
            setPadding(UiKit.dp(this@EmployeeUserGuideActivity, 12), UiKit.dp(this@EmployeeUserGuideActivity, 12), UiKit.dp(this@EmployeeUserGuideActivity, 12), UiKit.dp(this@EmployeeUserGuideActivity, 24))
            setBackgroundColor(p.bg)
        }
        root.addView(UiKit.heroCard(this, p, 9).apply {
            addView(UiKit.title(this@EmployeeUserGuideActivity, p, t("دليل الموظف العملي", "Employee Practical Guide"), 21f).apply { gravity = Gravity.CENTER })
            addView(UiKit.subtitle(this@EmployeeUserGuideActivity, p, t("اختر ما تريد عمله ثم افتح الخطوات.", "Choose your task, then open the steps.")).apply { gravity = Gravity.CENTER })
        })
        search = EditText(this).apply {
            hint = t("ابحث: ربط، حضور، رسالة، موقع…", "Search: pairing, attendance, messages, location…")
            setSingleLine(true); textSize = 14.5f
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        contentBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(contentBox)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = showSearch(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        setContentView(ScrollView(this).apply { isFillViewport = true; setBackgroundColor(p.bg); addView(root) })
        showScenarios()
    }

    private fun showScenarios() {
        activeScenario = null
        if (::search.isInitialized && search.text.isNotEmpty()) { search.setText(""); return }
        contentBox.removeAllViews()
        contentBox.addView(UiKit.subtitle(this, p, t("ماذا تريد أن تفعل؟", "What do you want to do?")).apply { gravity = Gravity.CENTER })
        scenarios.forEach { scenario ->
            contentBox.addView(UiKit.actionTile(this, p, t(scenario.titleAr, scenario.titleEn), t(scenario.subtitleAr, scenario.subtitleEn)) { showScenario(scenario) })
        }
    }

    private fun showScenario(scenario: Scenario) {
        activeScenario = scenario
        contentBox.removeAllViews()
        contentBox.addView(UiKit.card(this, p, 8).apply {
            addView(UiKit.button(this@EmployeeUserGuideActivity, p, t("← كل الاستخدامات", "← All use cases"), false).apply { setOnClickListener { showScenarios() } })
            addView(UiKit.title(this@EmployeeUserGuideActivity, p, t(scenario.titleAr, scenario.titleEn), 18f))
            addView(UiKit.subtitle(this@EmployeeUserGuideActivity, p, t(scenario.subtitleAr, scenario.subtitleEn)))
        })
        scenario.topics.forEach { topic ->
            contentBox.addView(UiKit.actionTile(this, p, t(topic.titleAr, topic.titleEn), t("اضغط لعرض الشرح الكامل", "Tap for full instructions")) {
                showTopic(scenario, topic)
            })
        }
    }

    private fun showTopic(scenario: Scenario, topic: Topic) {
        contentBox.removeAllViews()
        contentBox.addView(UiKit.card(this, p, 9).apply {
            addView(UiKit.button(this@EmployeeUserGuideActivity, p, t("← رجوع للخطوات", "← Back to steps"), false).apply { setOnClickListener { showScenario(scenario) } })
            addView(UiKit.title(this@EmployeeUserGuideActivity, p, t(topic.titleAr, topic.titleEn), 18.5f))
            addView(UiKit.subtitle(this@EmployeeUserGuideActivity, p, t(topic.bodyAr, topic.bodyEn)).apply { textSize = 14.5f; setLineSpacing(0f, 1.18f) })
        })
    }

    private fun showSearch(query: String) {
        val q = query.trim().lowercase()
        if (q.isBlank()) { if (activeScenario != null) showScenario(activeScenario!!) else showScenarios(); return }
        contentBox.removeAllViews()
        val hits = scenarios.flatMap { scenario -> scenario.topics.map { scenario to it } }.filter { (scenario, topic) ->
            listOf(scenario.titleAr, scenario.titleEn, scenario.subtitleAr, scenario.subtitleEn, topic.titleAr, topic.titleEn, topic.bodyAr, topic.bodyEn)
                .any { it.lowercase().contains(q) }
        }
        if (hits.isEmpty()) {
            contentBox.addView(UiKit.card(this, p, 8).apply { addView(UiKit.subtitle(this@EmployeeUserGuideActivity, p, t("لا توجد نتيجة مطابقة.", "No matching result.")).apply { gravity = Gravity.CENTER }) })
            return
        }
        hits.forEach { (scenario, topic) ->
            contentBox.addView(UiKit.actionTile(this, p, t(topic.titleAr, topic.titleEn), t(scenario.titleAr, scenario.titleEn)) { showTopic(scenario, topic) })
        }
    }
}
