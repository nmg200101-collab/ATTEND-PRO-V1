package com.attendpro.store

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

class StoreUserGuideActivity : Activity() {
    private val p by lazy { UiKit.palette(this) }
    private fun t(ar: String, en: String) = AppLanguage.text(this, ar, en)

    private data class Topic(val id: String, val titleAr: String, val titleEn: String, val bodyAr: String, val bodyEn: String)
    private data class Scenario(val id: String, val titleAr: String, val titleEn: String, val subtitleAr: String, val subtitleEn: String, val topics: List<Topic>)

    private val scenarios by lazy {
        listOf(
            Scenario("release", "ما الجديد في هذا الإصدار", "What's new in this version",
                "يتحدث هذا القسم مع كل إصدار للتطبيق.", "This section ships with and updates on every app release.",
                listOf(
                    Topic("version", "الإصدار الحالي", "Current version",
                        "أنت تستخدم ATTEND PRO " + BuildConfig.VERSION_NAME + ".\n\nأبرز تحديثات هذا الإصدار: عرض الدوام بصيغة صباح/مساء في جميع المسارات، تكبير منطقة الحضور/الانصراف، تحسين إدارة بيانات دخول الوكلاء واستعادة المشترك، الرد بدون إنترنت، صلاحيات هاتف الاستلام، وتحسين الإنجليزية والواجهة.",
                        "You are using ATTEND PRO " + BuildConfig.VERSION_NAME + ".\n\nHighlights: AM/PM shift display across the app, a larger attendance action area, improved agent credential and subscriber-recovery management, offline replies, receiver-phone permissions, and improved English/UI coverage."),
                    Topic("guide_policy", "كيف يتحدث الدليل؟", "How does the guide update?",
                        "الدليل جزء من نفس إصدار التطبيق. عند تثبيت تحديث جديد، تصل معه أقسام الشرح الجديدة والتغييرات المرتبطة بالميزات الجديدة تلقائيًا؛ لذلك لا يحتاج تنزيل دليل منفصل.",
                        "The guide is bundled with the app release. Installing a new app update automatically brings the matching feature instructions, so no separate guide download is required.")
                )),
            Scenario("pair", "أريد إضافة موظف وربط هاتفه", "I want to add and pair an employee",
                "من إنشاء الموظف حتى ظهور «تم الارتباط» الحقيقي.", "From creating the employee to confirmed pairing.",
                listOf(
                    Topic("add", "1. إضافة الموظف", "1. Add the employee",
                        "افتح: إعدادات مدير المحل ← الموظفون ← إضافة موظف. أدخل الاسم والبيانات المطلوبة، ثم حدد طرق التحقق المسموحة له. احفظ الموظف أولًا قبل بدء الربط.\n\nالنتيجة المتوقعة: يظهر الموظف في قائمة الموظفين وله معرف ثابت.",
                        "Open Store Manager Settings → Employees → Add employee. Enter the required information, choose the verification methods allowed for that employee, then save before pairing.\n\nExpected result: the employee appears in the employee list with a stable ID."),
                    Topic("qr", "2. الربط عبر QR", "2. Pair with QR",
                        "من الموظف اختر «ربط الهاتف» واعرض QR جديدًا. في تطبيق الموظف افتح الربط وامسح الرمز قبل انتهاء صلاحيته. اترك Bluetooth والموقع والأجهزة القريبة مفعلة أثناء العملية.\n\nلا تعتبر «جاري الارتباط» نجاحًا. النجاح هو ظهور تأكيد الربط وحفظ الهاتف المرتبط في جهاز المحل.",
                        "Open the employee → Pair phone and generate a fresh QR. On the Employee app, open Pairing and scan it before it expires. Keep Bluetooth, Location and Nearby Devices enabled.\n\n“Pairing…” is not success. Success is the confirmed link and the phone being saved by the Store app."),
                    Topic("bt", "3. Bluetooth المباشر وBLE/GATT", "3. Direct Bluetooth and BLE/GATT",
                        "بعد الربط يستخدم ATTEND PRO جلسة Bluetooth موثقة مع Heartbeat وACK. ظهور الهاتف في المسح فقط لا يعني أنه متصل. إذا انقطع Bluetooth يعيد التطبيق الاتصال تلقائيًا عند ظهور الهاتف مجددًا.\n\nإذا لم يكتمل الربط: تحقق من صلاحية الأجهزة القريبة والموقع، ثم أعد تشغيل Bluetooth وأنشئ QR جديدًا.",
                        "After pairing, ATTEND PRO uses an authenticated Bluetooth session with Heartbeat and ACK. Discovery alone does not mean connected. If Bluetooth drops, ATTEND PRO reconnects automatically when the phone is discovered again.\n\nIf pairing does not finish, verify Nearby Devices and Location permissions, restart Bluetooth, and generate a new QR."),
                    Topic("wifi", "4. Wi‑Fi / Hotspot", "4. Wi‑Fi / Hotspot",
                        "يمكن أن يعمل الهاتفان على نفس الشبكة المحلية أو نقطة اتصال. هذه قناة مساعدة مستقلة عن Bluetooth. تغيير القناة لا يغير طرق التحقق المسموحة للموظف.",
                        "Both phones may communicate on the same LAN or hotspot. This is an independent supporting channel. Changing transport does not change the employee’s allowed verification methods.")
                )),
            Scenario("attendance", "أريد تشغيل الحضور والانصراف يوميًا", "I want to run daily attendance",
                "الحضور، الانصراف، الدوام، وإثبات الوجود.", "Check-in, check-out, shifts and presence proof.",
                listOf(
                    Topic("daily", "1. تسجيل حضور أو انصراف", "1. Record check-in or check-out",
                        "من بطاقة «الحضور والانصراف» الكبيرة في الرئيسية اختر حضورًا أو انصرافًا، ثم اختر الموظف وطريقة التحقق المسموحة له. نفّذ التحقق حتى تظهر نتيجة النجاح. اكتشاف الهاتف أو GPS وحده لا يسجل الحضور تلقائيًا.",
                        "From the prominent Attendance card on Home choose Check in or Check out, select the employee and an allowed verification method, then complete verification. Phone discovery or GPS alone does not automatically record attendance."),
                    Topic("shift", "2. ضبط الدوام صباح/مساء", "2. Set AM/PM working hours",
                        "افتح إعدادات مدير المحل ← الدوام. اختر البداية والنهاية بصيغة صباح/مساء. يدعم النظام دوامًا يعبر منتصف الليل مثل 10:00 م إلى 6:00 ص. لا تجعل البداية والنهاية متساويتين. يمكن ضبط دقائق السماح من 0 إلى 120.",
                        "Open Store Manager Settings → Working hours. Choose start and end using AM/PM. Overnight shifts such as 10:00 PM–6:00 AM are supported. Start and end cannot be identical. Grace time can be set from 0 to 120 minutes."),
                    Topic("proof", "3. طلب إثبات الوجود", "3. Request presence proof",
                        "اختر الموظف ثم أرسل طلب إثبات وجود بالطريقة التي تريدها من الطرق المسموحة. يصل الطلب لتطبيق الموظف، ويجب عليه تنفيذ الطريقة المطلوبة. Bluetooth أو GPS لا يستبدلان التحقق إذا طلب المدير بصمة/وجه/طريقة أخرى.",
                        "Choose the employee and request presence proof using an allowed method. The request reaches the Employee app and the employee must complete that exact verification. Bluetooth or GPS proximity does not replace a requested biometric or other method."),
                    Topic("reports", "4. مراجعة السجل والتقارير", "4. Review logs and reports",
                        "افتح التقارير لمراجعة اليوم أو آخر 7 أيام أو الشهر. راجع عدد الحضور والانصراف والتأخير ثم شارك التقرير أو أرسله إلى هاتف استلام مصرح له.",
                        "Open Reports to review today, the last 7 days or the month. Check attendance, checkout and late counts, then share the report or send it to an authorized receiver phone.")
                )),
            Scenario("messages", "أريد مراسلة الموظف", "I want to message an employee",
                "مع الإنترنت أو بدونه.", "With or without Internet.",
                listOf(
                    Topic("storemsg", "1. إرسال من المحل بدون إنترنت", "1. Send from Store without Internet",
                        "افتح الرسائل ← إرسال رسالة لموظف. إذا كان هاتف الموظف متصلًا بجلسة Bluetooth الموثقة، يحاول التطبيق التسليم محليًا أولًا بدون إنترنت. عند عدم توفر القناة المحلية يستخدم الخادم إذا كان متاحًا.",
                        "Open Messages → Send message to employee. If the Employee phone has an authenticated Bluetooth session, ATTEND PRO tries local delivery first without Internet. If the local channel is unavailable, it falls back to the server when available."),
                    Topic("reply", "2. رد الموظف بدون إنترنت", "2. Employee reply without Internet",
                        "عندما تصل الرسالة محليًا إلى الموظف، يمكنه فتحها والضغط على «رد». إذا كانت جلسة Bluetooth الموثقة ما زالت متصلة، يُرسل الرد مباشرة إلى جهاز المحل بدون إنترنت ويظهر في صندوق رسائل المحل.",
                        "When the employee receives a local message, they can open it and tap Reply. If the authenticated Bluetooth session is still connected, the reply is sent directly to the Store without Internet and appears in the Store inbox."),
                    Topic("fallback", "3. ماذا يحدث عند انقطاع Bluetooth؟", "3. What if Bluetooth disconnects?",
                        "إذا لم توجد قناة Bluetooth موثقة، يحاول التطبيق استخدام الخادم. إذا لم يوجد إنترنت أيضًا، لن يعتبر الرد مرسلًا وسيظهر تنبيه واضح للمستخدم بدل فقدان الرسالة بصمت.",
                        "If authenticated Bluetooth is unavailable, the app tries the server. If Internet is also unavailable, the reply is not marked sent and the user gets a clear error instead of silently losing it.")
                )),
            Scenario("receiver", "أريد إعداد هاتف الإدارة/استلام التقارير", "I want to configure a management/receiver phone",
                "تقارير ورسائل وإعدادات، كل ميزة بصلاحية مستقلة.", "Reports, messaging and settings with independent permissions.",
                listOf(
                    Topic("grant", "1. إضافة هاتف الاستلام", "1. Add a receiver phone",
                        "في هاتف الاستلام افتح «استلام التقارير» واعرض QR منح الصلاحية. في هاتف المحل افتح إعدادات مدير المحل ← هواتف الاستلام والصلاحيات ← إضافة هاتف، ثم امسح QR. افتراضيًا يحصل الهاتف الجديد على صلاحية التقارير فقط.",
                        "On the receiver phone open Report Receiver and show the authorization QR. On the Store phone open Store Manager Settings → Receiver phones and permissions → Add phone and scan it. New phones receive Reports permission only by default."),
                    Topic("perms", "2. منح الصلاحيات بشكل مستقل", "2. Grant independent permissions",
                        "اختر الهاتف من قائمة هواتف الاستلام ثم «تعديل الصلاحيات». يمكنك تشغيل أو إيقاف كل ميزة وحدها: استلام التقارير، مراسلة الموظفين، إعدادات مدير المحل. يمكنك أيضًا إيقاف الهاتف بالكامل أو حذفه.",
                        "Select the receiver phone and choose Edit permissions. Independently enable Receive reports, Message employees, or Store Manager settings. You can also disable the whole phone or remove it."),
                    Topic("rmsg", "3. مراسلة الموظفين من هاتف الاستلام", "3. Message employees from receiver phone",
                        "هذه الميزة لا تظهر إلا إذا منح هاتف المحل صلاحية «مراسلة الموظفين». يختار هاتف الاستلام الموظف ثم يرسل عبر الخادم. إذا رد الموظف على هذه الرسالة، يعود الرد إلى هاتف الاستلام الذي أرسلها.",
                        "This section appears only when the Store grants Message employees. The receiver selects an employee and sends through the server. If the employee replies to that message, the reply returns to the receiver phone that sent it."),
                    Topic("remote", "4. إعدادات مدير المحل عن بُعد", "4. Remote Store Manager settings",
                        "لا تظهر إلا عند منح صلاحية «إعدادات مدير المحل». يمكن تعديل الدوام، دقائق السماح، بعض إعدادات الصوت، تنبيهات النطاق والمزامنة. يرفع الهاتف تعديلًا برقم مراجعة، ثم يطبقه هاتف المحل المعتمد عند اتصاله بالخادم ويؤكد تطبيق المراجعة. لا تُمنح صلاحيات النظام العليا أو مفاتيح المالك لهاتف الاستلام.",
                        "This section appears only with Store Manager settings permission. The receiver can adjust working hours, grace period, selected voice settings, geofence alerts and synchronization. Each change gets a revision; the authorized Store device applies it when online and acknowledges the revision. System-owner secrets and master keys are never delegated.")
                )),
            Scenario("protect", "أريد النسخ الاحتياطي والحماية والتحديث", "I want backup, security and updates",
                "نسخة الهاتف، نسخة الخادم، القفل، والاستعادة.", "Phone backup, server backup, locks and recovery.",
                listOf(
                    Topic("backup", "1. النسخ الاحتياطي", "1. Backup",
                        "من إعدادات مدير المحل افتح النسخ الاحتياطي. اختر نسخة مشفرة للهاتف أو رفع نسخة مشفرة للخادم. احتفظ بكلمة النسخة في مكان آمن، ولا تبدأ الاستعادة قبل مراجعة ملخص النسخة.",
                        "Open Backup from Store Manager Settings. Create an encrypted phone backup or upload an encrypted server backup. Keep the backup password safe and review the backup summary before restoration."),
                    Topic("lock", "2. قفل التطبيق والإدارة", "2. App and management lock",
                        "قفل التطبيق يحمي فتح ATTEND PRO. رمز إدارة المحل يحمي وظائف المدير. بعد إنهاء العمل أغلق جلسة الإدارة حتى لا تبقى الأدوات الحساسة مفتوحة.",
                        "App Lock protects opening ATTEND PRO. The Store Management PIN protects manager functions. Close the management session when finished so sensitive tools are not left open."),
                    Topic("agent_credential", "3. رمز دخول الوكيل", "3. Agent sign-in credential",
                        "من إدارة النظام افتح تفاصيل الوكيل ثم «بيانات الدخول». لا يعرض ATTEND PRO كلمة مرور قديمة ولا يفك أي hash. عند الحاجة اضغط «إظهار / إنشاء رمز دخول جديد». ينشئ الخادم رمزًا جديدًا ويلغي السابق، ويظهر الرمز كاملًا مرة واحدة فقط. انسخه واحفظه قبل إغلاق النافذة.",
                        "In System Administration open the agent details, then Sign-in credentials. ATTEND PRO never reveals an old password or reverses a hash. Choose Show / create new sign-in code when needed. The server rotates the credential, invalidates the previous one, and displays the new value only once. Copy and save it before closing the dialog."),
                    Topic("subscriber_recovery", "4. بيانات دخول واستعادة المشترك", "4. Subscriber access and recovery",
                        "من إدارة النظام ← المشتركين افتح المشترك ثم «بيانات الدخول والاستعادة». لا يتم عرض Access Token الدائم. مالك النظام فقط يستطيع إنشاء رمز استعادة جديد محدود الغرض. الرمز مؤقت، يُخزن في الخادم كـhash، يبطل الرمز السابق غير المستخدم، ويُستهلك مرة واحدة عند استعادة الجهاز. احفظ الرمز فور ظهوره لأنه لا يعرض مرة أخرى.",
                        "In System Administration → Subscribers open the subscriber, then Access and recovery. The permanent Access Token is never displayed. Only the system owner can issue a new purpose-limited recovery code. It is temporary, stored server-side only as a hash, invalidates the previous unused code, and is consumed once during device recovery. Save it when shown because it is not displayed again."),
                    Topic("update", "5. التحديث", "5. Update",
                        "نسخة Google Play تتحدث عبر Google Play. النسخة المباشرة تتحقق من HTTPS والحزمة والتوقيع وSHA‑256 ثم تطلب موافقة المستخدم. لا يوجد تثبيت صامت.",
                        "The Google Play build updates through Google Play. The Direct build validates HTTPS, package, signature and SHA‑256 and then asks the user to approve installation. There is no silent install.")
                )),
            Scenario("problem", "لدي مشكلة وأريد معرفة الحل", "I have a problem",
                "أكثر الأعطال شيوعًا وكيف تعرف أن الحل نجح.", "Common problems and how to verify the fix.",
                listOf(
                    Topic("pending", "الربط يبقى «جاري الارتباط»", "Pairing stays on “Pairing…”",
                        "1) تأكد أن Bluetooth والموقع والأجهزة القريبة مسموحة في الهاتفين.\n2) ألغِ الرمز القديم وأنشئ QR جديدًا.\n3) أبقِ التطبيقين في الواجهة أثناء الربط الأول.\n4) النجاح لا يُقاس بظهور الهاتف؛ انتظر تأكيد ACK وحفظ الربط.",
                        "1) Verify Bluetooth, Location and Nearby Devices on both phones.\n2) Discard the old code and generate a fresh QR.\n3) Keep both apps open during first pairing.\n4) Seeing the phone is not enough; wait for ACK confirmation and saved pairing."),
                    Topic("offline", "الهاتف يظهر لكن لا يتصل", "Phone is visible but not connected",
                        "انتظر إعادة الاتصال التلقائي عدة ثوانٍ. إن لم يعد ACK، أوقف Bluetooth وشغله في الهاتفين. لا تحذف بيانات التطبيق لأن الربط والتفعيل محفوظان.",
                        "Allow a few seconds for automatic reconnect. If ACK does not return, toggle Bluetooth on both phones. Do not clear app data because pairing and activation state are stored there."),
                    Topic("gps", "GPS لا يظهر أو غير دقيق", "GPS is missing or inaccurate",
                        "تأكد من تفعيل الموقع ودقة الموقع ومنح الصلاحية المطلوبة. وجود GPS يعني رصد قرب فقط ولا يعني تسجيل حضور تلقائيًا.",
                        "Enable Location and high accuracy and grant the required permission. GPS proximity is observation only and does not automatically record attendance.")
                ))
        )
    }

    private var activeScenario: Scenario? = null
    private var activeTopic: Topic? = null
    private lateinit var contentBox: LinearLayout
    private lateinit var search: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguage.applyToResources(this)
        renderShell()
    }

    private fun renderShell() {
        val dir = if (AppLanguage.isEnglish(this)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = dir
            setPadding(UiKit.dp(this@StoreUserGuideActivity, 12), UiKit.dp(this@StoreUserGuideActivity, 12), UiKit.dp(this@StoreUserGuideActivity, 12), UiKit.dp(this@StoreUserGuideActivity, 24))
            setBackgroundColor(p.bg)
        }
        root.addView(UiKit.heroCard(this, p, 9).apply {
            addView(UiKit.title(this@StoreUserGuideActivity, p, t("دليل ATTEND PRO العملي", "ATTEND PRO Practical Guide"), 21f).apply { gravity = Gravity.CENTER })
            addView(UiKit.subtitle(this@StoreUserGuideActivity, p, t("اختر ما تريد فعله، ثم اتبع الخطوات.", "Choose what you want to do, then follow the steps.")).apply { gravity = Gravity.CENTER })
        })
        search = EditText(this).apply {
            hint = t("ابحث: ربط، رسائل، دوام، نسخة احتياطية…", "Search: pairing, messages, shifts, backup…")
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
        activeScenario = null; activeTopic = null
        if (::search.isInitialized && search.text.isNotEmpty()) { search.setText(""); return }
        contentBox.removeAllViews()
        contentBox.addView(UiKit.subtitle(this, p, t("ماذا تريد أن تفعل؟", "What do you want to do?")).apply { gravity = Gravity.CENTER })
        scenarios.forEach { scenario ->
            contentBox.addView(UiKit.actionTile(this, p, t(scenario.titleAr, scenario.titleEn), t(scenario.subtitleAr, scenario.subtitleEn)) {
                showScenario(scenario)
            })
        }
    }

    private fun showScenario(scenario: Scenario) {
        activeScenario = scenario; activeTopic = null
        contentBox.removeAllViews()
        contentBox.addView(UiKit.card(this, p, 8).apply {
            addView(UiKit.button(this@StoreUserGuideActivity, p, t("← كل الاستخدامات", "← All use cases"), false).apply { setOnClickListener { showScenarios() } })
            addView(UiKit.title(this@StoreUserGuideActivity, p, t(scenario.titleAr, scenario.titleEn), 18f))
            addView(UiKit.subtitle(this@StoreUserGuideActivity, p, t(scenario.subtitleAr, scenario.subtitleEn)))
        })
        scenario.topics.forEach { topic ->
            contentBox.addView(UiKit.actionTile(this, p, t(topic.titleAr, topic.titleEn), t("اضغط لعرض الشرح والخطوات", "Tap for full instructions")) {
                showTopic(scenario, topic)
            })
        }
    }

    private fun showTopic(scenario: Scenario, topic: Topic) {
        activeScenario = scenario; activeTopic = topic
        contentBox.removeAllViews()
        contentBox.addView(UiKit.card(this, p, 9).apply {
            addView(UiKit.button(this@StoreUserGuideActivity, p, t("← رجوع للخطوات", "← Back to steps"), false).apply { setOnClickListener { showScenario(scenario) } })
            addView(UiKit.title(this@StoreUserGuideActivity, p, t(topic.titleAr, topic.titleEn), 18.5f))
            addView(UiKit.subtitle(this@StoreUserGuideActivity, p, t(topic.bodyAr, topic.bodyEn)).apply {
                textSize = 14.5f; setLineSpacing(0f, 1.18f)
            })
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
            contentBox.addView(UiKit.card(this, p, 8).apply { addView(UiKit.subtitle(this@StoreUserGuideActivity, p, t("لا توجد نتيجة مطابقة.", "No matching result.")).apply { gravity = Gravity.CENTER }) })
            return
        }
        hits.forEach { (scenario, topic) ->
            contentBox.addView(UiKit.actionTile(this, p, t(topic.titleAr, topic.titleEn), t(scenario.titleAr, scenario.titleEn)) { showTopic(scenario, topic) })
        }
    }
}
