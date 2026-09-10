package com.attendpro.employee

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationManager
import android.location.LocationListener
import android.hardware.biometrics.BiometricPrompt
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.attendpro.core.AppIntegrity1982
import com.attendpro.core.AppLockGateActivity
import com.attendpro.core.AppLockSettingsDialog
import com.attendpro.core.AttendanceMethod
import com.attendpro.core.AttendanceAction
import com.attendpro.core.AppUpdateManager
import com.attendpro.core.BleProtocol
import com.attendpro.core.EmployeeIdentityStore
import com.attendpro.core.PairingProtocol
import com.attendpro.core.QrScannerActivity
import com.attendpro.core.SecretCodec
import com.attendpro.core.CentralServerClient
import com.attendpro.core.ServerDiagnostics
import com.attendpro.core.UiKit
import java.util.Locale

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_SCAN_PROVISION = 8103
        private const val REQUEST_GPS_PERMISSION = 8104
        private const val REQUEST_NOTIFICATIONS = 8105
        private val QR_REPLAY_LOCK_1928 = Any()
    }

    private lateinit var identity: EmployeeIdentityStore
    private lateinit var advertiser: BlePresenceAdvertiser
    private lateinit var networkPresence: NetworkPresenceBroadcaster
    private lateinit var pairingDiscovery: PairingDiscovery
    private lateinit var status: TextView
    private lateinit var profile: TextView
    private lateinit var connectionSummary: TextView
    private lateinit var presenceSwitch: Switch
    private val connectionUiHandler = Handler(Looper.getMainLooper())
    private val connectionUiTask = object : Runnable {
        override fun run() {
            if (::connectionSummary.isInitialized && ::identity.isInitialized) updateConnectionSummary()
            connectionUiHandler.postDelayed(this, 3_000L)
        }
    }
    private var pendingAction: AttendanceAction = AttendanceAction.CHECK_IN
    private var activeChallengeId: String = ""
    private var activeChallengeMethod: AttendanceMethod? = null
    private var activeChallengeAction: AttendanceAction = AttendanceAction.CHECK_IN
    @Volatile private var pendingPairingCode: String = ""
    private var pairingSessionDialog: AlertDialog? = null
    private var pairingSessionStatus: TextView? = null
    private val p by lazy { UiKit.palette(this) }

    private fun enforceOfficialBuild1982(): Boolean {
        if (!BuildConfig.ENFORCE_OFFICIAL_SIGNATURE) return true
        if (AppIntegrity1982.isOfficialPackageAndSignature(this) && !AppIntegrity1982.isDebuggable(this)) return true
        AlertDialog.Builder(this)
            .setTitle("نسخة غير رسمية")
            .setMessage("تعذر التحقق من توقيع ATTEND PRO الرسمي. لحماية بيانات الحضور لا يمكن تشغيل نسخة معاد توقيعها أو معدلة.")
            .setCancelable(false)
            .setPositiveButton("إغلاق") { _, _ -> finishAffinity() }
            .show()
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!enforceOfficialBuild1982()) return
        identity = EmployeeIdentityStore(this)
        advertiser = BlePresenceAdvertiser(this) { msg -> runOnUiThread { if (::status.isInitialized) status.text = msg } }
        networkPresence = NetworkPresenceBroadcaster(onStatus = { msg -> runOnUiThread { if (::status.isInitialized) status.text = msg } })
        pairingDiscovery = PairingDiscovery(this,{value,channel->
            runOnUiThread {
                if (::status.isInitialized) status.text="تم استلام ملف الربط عبر $channel مع ACK؛ جاري تثبيت الاقتران محليًا…"
                acceptProvision(value, localChannel = channel)
            }
        },{msg->runOnUiThread{
            if(::status.isInitialized) status.text=msg
            updatePairingSessionStatus(msg)
        }})
        buildElegantUi()
        refreshProfile()
        updateConnectionSummary()
        connectionUiHandler.removeCallbacks(connectionUiTask)
        connectionUiHandler.post(connectionUiTask)
        if (identity.isConfigured) {
            if (identity.autoPresence) startPresence() else startBackgroundPresence()
        }
        AppUpdateManager.check(this, AppUpdateManager.DEFAULT_SERVER, "employee")
        // Keep Android 13+ notification permission out of the Bluetooth permission transaction.
        // Requesting two runtime-permission dialogs at once is unreliable on some OEM builds.
        if (hasRequiredBlePermissions()) maybeRequestNotificationPermission()
        val shared=if(intent?.action==Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty() else ""
        if(shared.isNotBlank()) handleExternalPairing1980(shared.trim())
    }

    private fun handleExternalPairing1980(raw: String) {
        val value = raw.trim()
        val envelope = PairingProtocol.decodeQrPairingEnvelope(value)
        val provision = envelope?.let { PairingProtocol.decodeEmployeeProvision(it.provisionText) }
            ?: PairingProtocol.decodeEmployeeProvision(value)
        val shortCode = value.removePrefix("APPAIR:").replace(Regex("[^A-Za-z0-9]"), "").uppercase()
        if (provision == null && !(shortCode.length == 8 && (value.startsWith("APPAIR:") || value.matches(Regex("[A-Za-z0-9]{8}"))))) {
            AlertDialog.Builder(this).setTitle("محتوى غير مدعوم").setMessage("النص المُرسل إلى ATTEND PRO ليس رمز ربط صالحًا.").setPositiveButton("حسنًا", null).show()
            return
        }
        val replacing = provision != null && identity.isConfigured &&
            (identity.trustedStoreId != provision.storeId || identity.employeeId != provision.employeeId)
        val same = provision != null && identity.isConfigured &&
            identity.trustedStoreId == provision.storeId && identity.employeeId == provision.employeeId
        val details = if (provision != null) buildString {
            append("المحل: ${provision.storeName}\n")
            append("الموظف: ${provision.displayName} (${provision.employeeId})\n")
            append("الفرع: ${provision.branchId}")
            if (replacing) append("\n\nالارتباط الحالي: ${identity.trustedStoreName.ifBlank { identity.trustedStoreId }} / ${identity.displayName.ifBlank { identity.employeeId }}")
        } else "تم استلام رمز اقتران قصير من تطبيق آخر. تأكد أنه صادر من جهاز المحل أمامك."
        val title = when {
            replacing -> "استبدال ارتباط الهاتف؟"
            same -> "تحديث بيانات الارتباط؟"
            else -> "تأكيد ربط الهاتف"
        }
        val warning = if (replacing) "\n\nسيتم استبدال المحل/الموظف المرتبط حاليًا بهذا الهاتف. لا توافق إلا إذا كنت تنفذ إعادة ربط مقصودة." else "\n\nلا توافق إلا إذا كان الرمز صادرًا من جهاز المحل الذي تريد ربط الهاتف به."
        AlertDialog.Builder(this).setTitle(title).setMessage(details + warning)
            .setPositiveButton(if (replacing) "استبدال الارتباط" else "متابعة الربط") { _, _ -> handlePairingInput(value) }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun buildUi() {
        window.statusBarColor = p.bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity,18),UiKit.dp(this@MainActivity,22),UiKit.dp(this@MainActivity,18),UiKit.dp(this@MainActivity,30)); setBackgroundColor(p.bg)
        }
        val header = UiKit.heroCard(this,p).apply { gravity = Gravity.CENTER_HORIZONTAL }
        header.addView(TextView(this).apply { text="⋮";textSize=28f;gravity=Gravity.CENTER;setTextColor(android.graphics.Color.WHITE);contentDescription="القائمة";layoutParams=LinearLayout.LayoutParams(UiKit.dp(this@MainActivity,48),UiKit.dp(this@MainActivity,44)).apply{gravity=Gravity.END};setOnClickListener{showEmployeeMainMenu1976()} })
        header.addView(employeeTemplateChip1978().apply { layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{gravity=Gravity.START} })
        header.addView(ImageView(this).apply { setImageResource(R.drawable.ic_attend_pro); layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@MainActivity,76),UiKit.dp(this@MainActivity,76)) })
        header.addView(UiKit.title(this,p,"ATTEND PRO",27f).apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.WHITE) })
        header.addView(UiKit.subtitle(this,p,"تطبيق الموظف المساند • الإصدار ${attendProVersionName()}").apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.argb(225,255,255,255)) })
        header.addView(UiKit.subtitle(this,p,"اختياري — جهاز المحل هو النظام الأساسي").apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.argb(215,255,255,255)) })
        root.addView(header)
        addEmployeeTabs1978(root)

        val state = UiKit.card(this,p)
        state.addView(UiKit.sectionLabel(this,p,"الحساب المرتبط"))
        profile = TextView(this).apply { textSize = 16f; setTextColor(p.text); setTypeface(typeface,Typeface.BOLD); gravity = Gravity.CENTER }
        status = TextView(this).apply { text="جاهز"; textSize=14f; setTextColor(p.muted); gravity=Gravity.CENTER; setPadding(0,UiKit.dp(this@MainActivity,8),0,0) }
        state.addView(profile); state.addView(status)
        root.addView(state)

        val link = UiKit.card(this,p)
        link.addView(UiKit.sectionLabel(this,p,"الربط مع جهاز المحل"))
        link.addView(UiKit.subtitle(this,p,"اختر الطريقة المناسبة: QR/باركود الربط، الرمز القصير، أو Bluetooth/Hotspot. الربط المحلي يعمل بدون إنترنت، وتتم مزامنة الخادم لاحقًا تلقائيًا عند توفر الشبكة."))
        link.addView(UiKit.button(this,p,"مسح QR / باركود الربط من جهاز المحل").apply { setOnClickListener { scanProvision() } })
        link.addView(UiKit.button(this,p,"إدخال أو لصق رمز الربط",false).apply { setOnClickListener { showManualProvision() } })
        link.addView(UiKit.button(this,p,"اختيار ربط Bluetooth / Wi‑Fi / نقطة اتصال",false).apply { setOnClickListener { showPairingCenter() } })
        link.addView(UiKit.subtitle(this,p,"للاقتران المباشر: افتح رمز الموظف في جهاز المحل، ثم اضغط هذا الزر. سيُلتقط جهاز المحل تلقائيًا عبر Bluetooth أو عند اتصال الهاتفين بالشبكة/نقطة الاتصال نفسها."))
        link.addView(UiKit.button(this,p,"إلغاء الربط من هذا الهاتف",false).apply { setOnClickListener {
            AlertDialog.Builder(this@MainActivity).setTitle("إلغاء الربط").setMessage("سيظل الموظف موجودًا في جهاز المحل، وسيتم فقط فصل هذا الهاتف.")
                .setPositiveButton("إلغاء الربط"){_,_->advertiser.stop(); networkPresence.stop(); stopBackgroundPresence(); identity.clearStoreLink(); EmployeeLateAlertScheduler.sync(this@MainActivity, identity); refreshProfile(); status.text="تم فصل الهاتف"}
                .setNegativeButton("رجوع",null).show()
        }})
        root.addView(link)

        val attend = UiKit.card(this,p)
        attend.addView(UiKit.sectionLabel(this,p,"تسجيل الحضور من هاتف الموظف"))
        attend.addView(UiKit.subtitle(this,p,"بعد نجاح الربط اختر حضورًا أو انصرافًا، ثم استخدم إحدى الطرق التي سمح بها صاحب المحل."))
        attend.addView(UiKit.button(this,p,"تسجيل الحضور الآن").apply { setOnClickListener { chooseAttendanceMethod(AttendanceAction.CHECK_IN) } })
        attend.addView(UiKit.button(this,p,"تسجيل الانصراف الآن",false).apply { setOnClickListener { chooseAttendanceMethod(AttendanceAction.CHECK_OUT) } })
        presenceSwitch = Switch(this).apply {
            text="التعرف التلقائي عبر Wi‑Fi/نقطة الاتصال أو Bluetooth"; textSize=15f; setTextColor(p.text); isChecked=identity.autoPresence
            setOnCheckedChangeListener { _,checked -> identity.autoPresence=checked; if(checked) startPresence() else { advertiser.stop(); networkPresence.stop(); startBackgroundPresence() } }
        }
        attend.addView(presenceSwitch)
        attend.addView(UiKit.button(this,p,"التعرف الحيوي الأصلي للهاتف (وجه/بصمة)").apply { setOnClickListener { requestBiometric() } })
        attend.addView(UiKit.button(this,p,"فحص جاهزية الاتصال والبصمة", false).apply { setOnClickListener { showReadinessCheck() } })
        attend.addView(UiKit.button(this,p,"عرض حالة اتصالي بالمحل", false).apply { setOnClickListener { showConnectionStatus() } })
        attend.addView(UiKit.button(this,p,"اختبار بصمة/وجه الهاتف دون تسجيل حضور", false).apply { setOnClickListener { testBiometricOnly() } })
        attend.addView(UiKit.button(this,p,"إعادة تشغيل اكتشاف جهاز المحل", false).apply { setOnClickListener { advertiser.stop(); networkPresence.stop(); startPresence() } })
        attend.addView(UiKit.button(this,p,"تأكيد الحضور بكلمة المرور", false).apply { setOnClickListener { confirmWithLocalCredential() } })
        attend.addView(UiKit.button(this,p,"إعداد كلمة مرور الهاتف", false).apply { setOnClickListener { setupLocalCredentials() } })
        attend.addView(UiKit.button(this,p,"فحص التعرّف عبر GPS", false).apply { setOnClickListener { requestGpsProof() } })
        attend.addView(UiKit.button(this,p,"فحص تحديث التطبيق", false).apply {
            setOnClickListener { AppUpdateManager.check(this@MainActivity, AppUpdateManager.DEFAULT_SERVER, "employee", manual = true) }
        })
        attend.addView(UiKit.subtitle(this,p,"Bluetooth وWi‑Fi/نقطة الاتصال يتعرفان على الهاتف تلقائيًا. GPS يسجل قرب الهاتف ووقت أول/آخر تعرّف فقط، ولا يسجل الحضور ولا يثبت الهوية."))
        root.addView(attend)

        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
    }

    private fun employeeHomeTemplate1978(): String = getSharedPreferences("attend_home_template_1978", MODE_PRIVATE)
        .getString("employee_template", "MAIN") ?: "MAIN"

    private fun setEmployeeHomeTemplate1978(value: String) {
        getSharedPreferences("attend_home_template_1978", MODE_PRIVATE).edit().putString("employee_template", value).apply()
    }

    private fun employeeTemplateTitle1978(value: String = employeeHomeTemplate1978()): String = when(value) {
        "SECTIONS" -> "الأقسام"
        "CLASSIC" -> "الكلاسيكي"
        else -> "الرئيسي"
    }

    private fun buildElegantUi() {
        when(employeeHomeTemplate1978()) {
            "SECTIONS" -> buildEmployeeSectionsTemplate1978()
            "CLASSIC" -> buildUi()
            else -> buildEmployeeMainTemplate1978()
        }
    }

    private fun employeeTemplateChip1978(): TextView = TextView(this).apply {
        text="النموذج: ${employeeTemplateTitle1978()} ▾";textSize=12.5f;gravity=Gravity.CENTER;setTextColor(android.graphics.Color.WHITE)
        setPadding(UiKit.dp(this@MainActivity,12),UiKit.dp(this@MainActivity,7),UiKit.dp(this@MainActivity,12),UiKit.dp(this@MainActivity,7))
        background=GradientDrawable().apply{cornerRadius=UiKit.dp(this@MainActivity,18).toFloat();setColor(android.graphics.Color.argb(38,255,255,255));setStroke(UiKit.dp(this@MainActivity,1),android.graphics.Color.argb(110,255,255,255))}
        setOnClickListener{showEmployeeHomeTemplatePicker1978()}
    }

    private fun showEmployeeHomeTemplatePicker1978() {
        val ids=arrayOf("MAIN","SECTIONS","CLASSIC")
        val labels=arrayOf("الرئيسي — الحضور وحالة الاتصال اليومية","الأقسام — وصول سريع للحضور والرسائل والاتصال","الكلاسيكي — العرض التفصيلي التقليدي")
        val current=ids.indexOf(employeeHomeTemplate1978()).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("نمط الشاشة الرئيسية").setSingleChoiceItems(labels,current){dialog,which->
            setEmployeeHomeTemplate1978(ids[which]);dialog.dismiss();buildElegantUi();refreshProfile();updateConnectionSummary()
        }.setNegativeButton("إلغاء",null).show()
    }

    private fun addEmployeeTabs1978(root:LinearLayout) {
        val box=UiKit.card(this,p,7)
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;gravity=Gravity.CENTER}
        fun tab(label:String,selected:Boolean=false,action:()->Unit)=TextView(this).apply{
            text=label;textSize=12.2f;gravity=Gravity.CENTER;setTypeface(typeface,Typeface.BOLD);setTextColor(if(selected)android.graphics.Color.WHITE else p.text)
            setPadding(UiKit.dp(this@MainActivity,5),UiKit.dp(this@MainActivity,10),UiKit.dp(this@MainActivity,5),UiKit.dp(this@MainActivity,10))
            background=GradientDrawable().apply{cornerRadius=UiKit.dp(this@MainActivity,14).toFloat();setColor(if(selected)p.primary else p.surface2)}
            layoutParams=LinearLayout.LayoutParams(0,UiKit.dp(this@MainActivity,42),1f).apply{marginStart=UiKit.dp(this@MainActivity,2);marginEnd=UiKit.dp(this@MainActivity,2)};setOnClickListener{action()}
        }
        row.addView(tab("الرئيسية",true){})
        row.addView(tab("الحضور"){showEmployeeAttendanceCenter()})
        row.addView(tab("الرسائل"){startActivity(Intent(this@MainActivity,EmployeeMessages1975Activity::class.java))})
        row.addView(tab("الاتصال"){showEmployeeConnectionControl1977()})
        box.addView(row);root.addView(box)
    }

    private fun buildEmployeeSectionsTemplate1978() {
        window.statusBarColor=p.bg
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(UiKit.dp(this@MainActivity,13),UiKit.dp(this@MainActivity,13),UiKit.dp(this@MainActivity,13),UiKit.dp(this@MainActivity,28));setBackgroundColor(p.bg)}
        val header=UiKit.heroCard(this,p,12)
        header.addView(TextView(this).apply{text="⋮";textSize=28f;gravity=Gravity.CENTER;setTextColor(android.graphics.Color.WHITE);contentDescription="القائمة";layoutParams=LinearLayout.LayoutParams(UiKit.dp(this@MainActivity,48),UiKit.dp(this@MainActivity,44)).apply{gravity=Gravity.END};setOnClickListener{showEmployeeMainMenu1976()}})
        header.addView(employeeTemplateChip1978().apply{layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{gravity=Gravity.START}})
        header.addView(UiKit.title(this,p,"ATTEND PRO",22f).apply{gravity=Gravity.CENTER;setTextColor(android.graphics.Color.WHITE)})
        header.addView(UiKit.subtitle(this,p,"تطبيق الموظف • لوحة الأقسام • ${attendProVersionName()}").apply{gravity=Gravity.CENTER;setTextColor(android.graphics.Color.argb(225,255,255,255))})
        root.addView(header);addEmployeeTabs1978(root)
        status=TextView(this).apply{text="جاهز";textSize=13f;setTextColor(p.muted);gravity=Gravity.CENTER}
        profile=TextView(this).apply{textSize=14f;setTextColor(p.text);setTypeface(typeface,Typeface.BOLD);gravity=Gravity.CENTER;maxLines=4}
        connectionSummary=TextView(this).apply{textSize=13f;setTextColor(p.text);gravity=Gravity.CENTER;maxLines=4}
        val identityCard=UiKit.card(this,p,10);identityCard.addView(UiKit.sectionLabel(this,p,"حسابي"));identityCard.addView(profile);root.addView(identityCard)
        val attend=UiKit.card(this,p,12);attend.addView(UiKit.title(this,p,"الحضور والانصراف",18f).apply{gravity=Gravity.CENTER})
        val ar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
        ar.addView(UiKit.button(this,p,"تسجيل حضور").apply{layoutParams=LinearLayout.LayoutParams(0,UiKit.dp(this@MainActivity,50),1f).apply{marginEnd=4};setOnClickListener{chooseAttendanceMethod(AttendanceAction.CHECK_IN)}})
        ar.addView(UiKit.button(this,p,"تسجيل انصراف",false).apply{layoutParams=LinearLayout.LayoutParams(0,UiKit.dp(this@MainActivity,50),1f).apply{marginStart=4};setOnClickListener{chooseAttendanceMethod(AttendanceAction.CHECK_OUT)}})
        attend.addView(ar);root.addView(attend)
        val communication=UiKit.card(this,p,10);communication.addView(UiKit.sectionLabel(this,p,"التواصل والاتصال"));communication.addView(connectionSummary)
        communication.addView(UiKit.button(this,p,"مركز الرسائل",false).apply{setOnClickListener{startActivity(Intent(this@MainActivity,EmployeeMessages1975Activity::class.java))}})
        communication.addView(UiKit.button(this,p,"إدارة الاتصال",false).apply{setOnClickListener{showEmployeeConnectionControl1977()}})
        root.addView(communication)
        val auto=UiKit.card(this,p,9);presenceSwitch=Switch(this).apply{text="الظهور التلقائي لجهاز المحل";textSize=14f;setTextColor(p.text);isChecked=identity.autoPresence;setOnCheckedChangeListener{_,checked->identity.autoPresence=checked;if(checked)startPresence()else{advertiser.stop();networkPresence.stop();startBackgroundPresence()}}};auto.addView(presenceSwitch);root.addView(auto)
        root.addView(UiKit.card(this,p,7).apply{addView(status)})
        setContentView(ScrollView(this).apply{isFillViewport=true;setBackgroundColor(p.bg);addView(root)})
    }

    private fun buildEmployeeMainTemplate1978() {
        window.statusBarColor = p.bg
        val layoutMode = UiKit.currentLayout(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 13), UiKit.dp(this@MainActivity, 13), UiKit.dp(this@MainActivity, 13), UiKit.dp(this@MainActivity, 28))
            setBackgroundColor(p.bg)
        }

        fun tile(title: String, subtitle: String, action: () -> Unit): LinearLayout = UiKit.actionTile(this, p, title, subtitle, action)

        fun detailTile(title: String, subtitle: String, detail: TextView, action: () -> Unit): LinearLayout =
            UiKit.card(this, p, if (layoutMode == UiKit.LayoutMode.COMPACT) 10 else 12).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(UiKit.title(this@MainActivity, p, title, if (layoutMode == UiKit.LayoutMode.LARGE) 17.2f else 15.8f).apply { gravity = Gravity.CENTER })
                addView(UiKit.subtitle(this@MainActivity, p, subtitle).apply { gravity = Gravity.CENTER; textSize = 12.2f })
                detail.gravity = Gravity.CENTER
                detail.setPadding(0, UiKit.dp(this@MainActivity, 7), 0, 0)
                UiKit.makeInteractive(this, this@MainActivity, p)
                setOnClickListener { action() }
            }

        fun addPair(target: LinearLayout, first: LinearLayout, second: LinearLayout) {
            if (layoutMode == UiKit.LayoutMode.ORGANIZED) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                }
                first.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = UiKit.dp(this@MainActivity, 4) }
                second.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = UiKit.dp(this@MainActivity, 4) }
                row.addView(first)
                row.addView(second)
                target.addView(row)
            } else {
                first.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                second.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                target.addView(first)
                target.addView(second)
            }
        }

        fun section(title: String, subtitle: String, content: (LinearLayout) -> Unit) {
            val box = UiKit.card(this, p, if (layoutMode == UiKit.LayoutMode.COMPACT) 11 else 14)
            box.addView(UiKit.sectionLabel(this, p, title).apply { textSize = 14f })
            box.addView(UiKit.subtitle(this, p, subtitle).apply {
                textSize = 12.5f
                setPadding(0, 0, 0, UiKit.dp(this@MainActivity, 8))
            })
            content(box)
            root.addView(box)
        }

        val header = UiKit.heroCard(this, p, 14)
        header.addView(TextView(this).apply {
            text = "⋮"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            contentDescription = "القائمة"
            layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@MainActivity, 48), UiKit.dp(this@MainActivity, 44)).apply { gravity = Gravity.END }
            setOnClickListener { showEmployeeMainMenu1976() }
        })
        header.addView(employeeTemplateChip1978().apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.START } })
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_attend_pro)
            layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@MainActivity, 48), UiKit.dp(this@MainActivity, 48))
        })
        header.addView(UiKit.title(this, p, "ATTEND PRO", 23f).apply {
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
        })
        header.addView(UiKit.subtitle(this, p, "تطبيق الموظف • الإصدار ${attendProVersionName()}").apply {
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.argb(225, 255, 255, 255))
        })
        root.addView(header)
        addEmployeeTabs1978(root)

        status = TextView(this).apply {
            text = "جاهز"
            textSize = 13f
            setTextColor(p.muted)
            gravity = Gravity.CENTER
        }
        profile = TextView(this).apply {
            textSize = 13f
            setTextColor(p.text)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 4
        }

        val quick = UiKit.card(this, p, 10)
        quick.addView(UiKit.sectionLabel(this, p, "حالتي"))
        val profileTile = detailTile("بياناتي وجدولي", "المحل المرتبط ووقت الدوام", profile) { showEmployeeProfile() }
        connectionSummary = TextView(this).apply {
            textSize = 12.8f
            setTextColor(p.text)
            gravity = Gravity.CENTER
            maxLines = 4
        }
        val connectionTile = detailTile("حالة اتصالي", "القناة الفعلية وGPS", connectionSummary) { showConnectionStatus() }
        addPair(quick, profileTile, connectionTile)
        root.addView(quick)

        val attendance = UiKit.card(this, p, 14).apply { gravity = Gravity.CENTER_HORIZONTAL }
        attendance.addView(UiKit.title(this, p, "الحضور والانصراف", 19f).apply { gravity = Gravity.CENTER })
        attendance.addView(UiKit.subtitle(this, p, "اختر الحركة وسيظهر فقط التحقق المسموح لك به").apply { gravity = Gravity.CENTER })
        val finger = TextView(this).apply {
            text = "◎\nحضور"
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(p.primary, p.accent)).apply {
                shape = GradientDrawable.OVAL
                setStroke(UiKit.dp(this@MainActivity, 3), android.graphics.Color.argb(120, 255, 255, 255))
            }
            layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@MainActivity, 132), UiKit.dp(this@MainActivity, 132)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = UiKit.dp(this@MainActivity, 10)
                bottomMargin = UiKit.dp(this@MainActivity, 10)
            }
            elevation = UiKit.dp(this@MainActivity, 5).toFloat()
            setOnClickListener { showEmployeeAttendanceCenter() }
        }
        attendance.addView(finger)
        val inOut = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        inOut.addView(UiKit.button(this, p, "تسجيل الحضور").apply {
            layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(this@MainActivity, 50), 1f).apply { marginEnd = UiKit.dp(this@MainActivity, 4) }
            setOnClickListener { chooseAttendanceMethod(AttendanceAction.CHECK_IN) }
        })
        inOut.addView(UiKit.button(this, p, "تسجيل الانصراف", false).apply {
            layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(this@MainActivity, 50), 1f).apply { marginStart = UiKit.dp(this@MainActivity, 4) }
            setOnClickListener { chooseAttendanceMethod(AttendanceAction.CHECK_OUT) }
        })
        attendance.addView(inOut)
        root.addView(attendance)

        val automatic = UiKit.card(this, p, 10)
        automatic.addView(UiKit.sectionLabel(this, p, "الاتصال التلقائي"))
        presenceSwitch = Switch(this).apply {
            text = "الظهور التلقائي لجهاز المحل"
            textSize = 14.5f
            setTextColor(p.text)
            isChecked = identity.autoPresence
            setOnCheckedChangeListener { _, checked ->
                identity.autoPresence = checked
                if (checked) startPresence() else {
                    advertiser.stop()
                    networkPresence.stop()
                    startBackgroundPresence()
                }
            }
        }
        automatic.addView(presenceSwitch)
        automatic.addView(UiKit.subtitle(this, p, "يعمل عبر Bluetooth أو شبكة المحل حسب القناة المتاحة، ولا يسجل حضورًا بدون إثبات."))
        automatic.addView(UiKit.button(this, p, "إدارة الاتصال", false).apply { setOnClickListener { showEmployeeConnectionControl1977() } })
        root.addView(automatic)

        section("الاستخدام اليومي", "الأقسام الأساسية فقط؛ لا تحتاج للدخول إلى الإعدادات أثناء الدوام.") { box ->
            addPair(
                box,
                tile("طرق التحقق", "بصمة/وجه الهاتف وكلمة المرور وQR") { showEmployeeVerificationCenter() },
                tile("ربط الهاتف", "ربط هذا الهاتف بالمحل مرة واحدة") { showPairingCenter() }
            )
            addPair(
                box,
                tile("حالة الاتصال", "معرفة القناة الفعالة مع جهاز المحل") { showConnectionStatus() },
                tile("فحص الجاهزية", "البصمة والموقع والاتصال والصلاحيات") { showReadinessCheck() }
            )
        }

        val moreHint = UiKit.card(this, p, 8)
        moreHint.addView(UiKit.subtitle(this, p, "الإشعارات ودليل المستخدم والإعدادات موجودة في قائمة ⋮ أعلى الشاشة.").apply { gravity = Gravity.CENTER })
        root.addView(moreHint)

        val footer = UiKit.card(this, p, 9)
        footer.addView(status)
        root.addView(footer)
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(p.bg)
            addView(root)
        })
    }

    private fun showLayeredMenu1977(title: String, items: List<Pair<String, () -> Unit>>) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 8))
        }
        items.forEach { (label, action) -> box.addView(UiKit.button(this, p, label, false).apply { setOnClickListener { action() } }) }
        AlertDialog.Builder(this).setTitle(title).setView(box).setNegativeButton("رجوع", null).show()
    }

    private fun showEmployeeMainMenu1976() {
        showLayeredMenu1977("القائمة", listOf(
            "الرسائل والإشعارات" to { startActivity(Intent(this, EmployeeMessages1975Activity::class.java)) },
            "نمط الشاشة الرئيسية" to { showEmployeeHomeTemplatePicker1978() },
            "دليل مستخدم الموظف" to { showEmployeeUserGuide1976() },
            "الإعدادات" to { showEmployeeSettings1976() }
        ))
    }

    private fun showEmployeeUserGuide1976() {
        AlertDialog.Builder(this)
            .setTitle("دليل مستخدم الموظف")
            .setMessage("1. الحضور والانصراف: اضغط الحركة المطلوبة ثم نفّذ طريقة التحقق التي سمحت بها إدارة المحل.\n\n2. حالة اتصالي: تعرض هل الهاتف مرتبط بالمحل والقناة المتاحة.\n\n3. الظهور التلقائي: اتركه مفعّلًا ليتمكن جهاز المحل من اكتشاف الهاتف عند القرب، لكنه لا يسجل حضورًا وحده.\n\n4. الإشعارات: تستقبل رسائل إدارة النظام وإدارة المحل؛ وإذا كان الهاتف متصلًا بالمحل عبر Bluetooth الموثق يمكن استقبال رسالة المحل مباشرة بدون إنترنت.\n\n5. قائمة ⋮: منها الإشعارات ودليل المستخدم والإعدادات وإدارة الاتصال.\n\nلا تغيّر إعدادات الربط بعد نجاحه إلا عند نقل الهاتف أو إعادة الربط بطلب من إدارة المحل.")
            .setPositiveButton("حسنًا", null)
            .show()
    }

    private fun showEmployeeSettings1976() {
        showLayeredMenu1977("الإعدادات", listOf(
            "إدارة الاتصال" to { showEmployeeConnectionControl1977() },
            "المظهر وطريقة العرض" to { UiKit.showAppearancePicker(this) },
            "قفل التطبيق والبصمة" to { showAppLockSettings() },
            "كلمة مرور إثبات الحضور" to { setupLocalCredentials() },
            "فحص تحديث التطبيق" to { AppUpdateManager.check(this, AppUpdateManager.DEFAULT_SERVER, "employee", manual = true) },
            "إلغاء ربط الهاتف" to { confirmUnlink() }
        ))
    }

    private fun showAppLockSettings() {
        AppLockSettingsDialog.show(this, onLockNow = {
            packageManager.getLaunchIntentForPackage(packageName)?.let { gate ->
                gate.putExtra(AppLockGateActivity.EXTRA_GATE_ONLY, true)
                startActivity(gate)
            }
        })
    }

    private fun showEmployeeConnectionControl1977() {
        val connected = identity.lastBleDirectSeenAt > 0L && System.currentTimeMillis() - identity.lastBleDirectSeenAt < 12_000L
        showLayeredMenu1977("إدارة الاتصال", listOf(
            (if (connected) "● Bluetooth المباشر متصل — عرض التفاصيل" else "○ عرض حالة الاتصال") to { showConnectionStatus() },
            "إعادة تشغيل الاتصال الآن" to { restartEmployeeConnection1977() },
            (if (identity.autoPresence) "إيقاف الظهور التلقائي مؤقتًا" else "تشغيل الظهور التلقائي") to {
                identity.autoPresence = !identity.autoPresence
                presenceSwitch.isChecked = identity.autoPresence
                if (identity.autoPresence) restartEmployeeConnection1977() else { stopBackgroundPresence(); status.text = "تم إيقاف الظهور التلقائي مؤقتًا" }
                updateConnectionSummary()
            },
            "فحص الجاهزية" to { showReadinessCheck() },
            "إعادة فتح مركز الربط" to { showPairingCenter() }
        ))
    }

    private fun restartEmployeeConnection1977() {
        if (!identity.isConfigured) { status.text = "اربط الهاتف بالمحل أولًا"; showPairingCenter(); return }
        identity.autoPresence = true
        if (::presenceSwitch.isInitialized) presenceSwitch.isChecked = true
        stopBackgroundPresence(); status.text = "جاري إعادة تشغيل قنوات الاتصال…"
        Handler(Looper.getMainLooper()).postDelayed({ startPresence(); updateConnectionSummary(); status.text = "تمت إعادة تشغيل Bluetooth وWi‑Fi/Hotspot؛ سيظهر ACK عند تأكيد الاتصال" }, 500L)
    }

    private fun updatePairingSessionStatus(message: String) {
        pairingSessionStatus?.text = message
    }

    private fun showPairingCenter(){
        pairingSessionDialog?.takeIf { it.isShowing }?.let {
            updatePairingSessionStatus("اختر طريقة الربط، وستبقى هذه الشاشة مفتوحة حتى يظهر تقدم الربط")
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 10), UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 12))
        }
        val state = TextView(this).apply {
            text = "جاهز — اختر الطريقة. لن تغلق شاشة الربط أثناء البحث."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(p.accent)
            setPadding(0, UiKit.dp(this@MainActivity, 6), 0, UiKit.dp(this@MainActivity, 12))
        }
        pairingSessionStatus = state
        box.addView(state)
        fun add(label: String, action: () -> Unit) {
            box.addView(UiKit.button(this,p,label,false).apply { setOnClickListener { action() } })
        }
        add("▦ مسح QR / باركود الربط") {
            updatePairingSessionStatus("جاري فتح الكاميرا لالتقاط QR جلسة المحل…")
            scanProvision()
        }
        add("Bluetooth فقط — ابدأ البحث الآن") {
            updatePairingSessionStatus("بدء Bluetooth Scanner…")
            pairingDiscovery.start(requestedMode = PairingDiscovery.Mode.BLUETOOTH_ONLY)
        }
        add("Wi‑Fi / نقطة اتصال فقط — ابدأ البحث الآن") {
            updatePairingSessionStatus("بدء البحث المحلي عبر Wi‑Fi / Hotspot…")
            pairingDiscovery.start(requestedMode = PairingDiscovery.Mode.WIFI_HOTSPOT_ONLY)
        }
        add("Bluetooth + Wi‑Fi — اختيار تلقائي للأسرع") {
            updatePairingSessionStatus("بدء البحث المتزامن عبر Bluetooth وWi‑Fi…")
            pairingDiscovery.start(requestedMode = PairingDiscovery.Mode.ALL)
        }
        add("إدخال أو لصق رمز الربط") { showManualProvision() }
        add("إيقاف البحث") {
            pairingDiscovery.stop()
            pendingPairingCode = ""
            updatePairingSessionStatus("تم إيقاف البحث. اختر طريقة للبدء من جديد.")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("ربط هاتف الموظف")
            .setView(ScrollView(this).apply { addView(box) })
            .setNegativeButton("إغلاق") { _, _ ->
                pairingDiscovery.stop()
                pendingPairingCode = ""
            }
            .create()
        dialog.setOnDismissListener {
            pairingSessionDialog = null
            pairingSessionStatus = null
        }
        pairingSessionDialog = dialog
        dialog.show()
    }

    private fun showEmployeeAttendanceCenter(){
        showLayeredMenu1977("تسجيل الحضور والانصراف", listOf("تسجيل الحضور الآن" to { chooseAttendanceMethod(AttendanceAction.CHECK_IN) }, "تسجيل الانصراف الآن" to { chooseAttendanceMethod(AttendanceAction.CHECK_OUT) }))
    }

    private fun showEmployeeVerificationCenter(){
        val items = mutableListOf<Pair<String, () -> Unit>>()
        if (identity.allows(AttendanceMethod.PHONE_BLE_BIOMETRIC) || identity.allows(AttendanceMethod.PHONE_FINGERPRINT))
            items += "◎ بصمة/وجه الهاتف" to { requestBiometric() }
        if (identity.allows(AttendanceMethod.PASSWORD)) items += "▣ كلمة المرور" to { confirmWithLocalCredential() }
        if (identity.allows(AttendanceMethod.PHONE_PROXIMITY)) items += "▦ مسح QR للحضور" to { scanProvision() }
        items += "اختبار بصمة/وجه الهاتف" to { testBiometricOnly() }
        showLayeredMenu1977("طرق التحقق", items)
    }

    private fun showEmployeeProfile(){
        AlertDialog.Builder(this).setTitle("حساب الموظف").setMessage(profile.text).setPositiveButton("إغلاق",null).show()
    }

    private fun showEmployeeHelp(){
        AlertDialog.Builder(this).setTitle("طريقة الاستخدام").setMessage("اربط الهاتف بالمحل مرة واحدة، ثم فعّل الظهور التلقائي. عند الحضور اضغط البصمة واختر حضورًا أو انصرافًا، ولن يقبل النظام إلا طريقة التحقق التي اعتمدتها إدارة المحل.").setPositiveButton("حسنًا",null).show()
    }

    private fun confirmUnlink(){
        AlertDialog.Builder(this).setTitle("إلغاء الربط").setMessage("سيظل الموظف موجودًا في جهاز المحل، وسيتم فصل هذا الهاتف فقط.")
            .setPositiveButton("إلغاء الربط"){_,_->advertiser.stop();networkPresence.stop();stopBackgroundPresence();identity.clearStoreLink();refreshProfile();status.text="تم فصل الهاتف"}
            .setNegativeButton("رجوع",null).show()
    }

    private fun updateConnectionSummary() {
        if (!::connectionSummary.isInitialized) return
        if (!identity.isConfigured) {
            connectionSummary.text = "غير مرتبط بالمحل"
            return
        }
        val now = System.currentTimeMillis()
        val directAge = identity.lastBleDirectSeenAt.takeIf { it > 0L }?.let { now - it } ?: Long.MAX_VALUE
        val lanAge = identity.lastLanStoreSeenAt.takeIf { it > 0L }?.let { now - it } ?: Long.MAX_VALUE
        val serverFresh = ServerDiagnostics.snapshot().isFresh(now)
        val primary = when {
            directAge < 12_000L -> "🟢 Bluetooth مباشر • ACK ${directAge / 1000}ث"
            lanAge < 8_000L -> "🟢 Wi‑Fi/Hotspot • ACK ${lanAge / 1000}ث"
            serverFresh -> "🟢 الخادم • اتصال HTTP حديث"
            else -> "⚪ بانتظار اتصال مباشر"
        }
        val gpsAge = identity.lastGpsObservedAt.takeIf { it > 0L }?.let { now - it } ?: Long.MAX_VALUE
        val gps = if (gpsAge < 5 * 60_000L) {
            val label = when (identity.lastGpsState) {
                OfflineGeoMonitor.STATE_INSIDE -> "داخل النطاق"
                OfflineGeoMonitor.STATE_NEAR -> "قريب من النطاق"
                OfflineGeoMonitor.STATE_OUTSIDE -> "خارج النطاق"
                else -> "قراءة غير محددة"
            }
            "📍 GPS: $label • ${identity.lastGpsDistanceMeters}م • ±${identity.lastGpsAccuracyMeters}م"
        } else if (identity.isTrustedStoreGpsConfigured) {
            "📍 GPS: مهيأ • بانتظار قراءة حديثة"
        } else "📍 GPS: موقع المحل غير مهيأ"
        connectionSummary.text = "$primary\n$gps"
    }

    private fun refreshProfile() {
        profile.text = if (!identity.isConfigured) "غير مرتبط" else buildString {
            append(identity.displayName).append(" • ").append(identity.employeeId).append("\n")
            if(identity.jobTitle.isNotBlank()) append(identity.jobTitle).append(" • ")
            append(identity.trustedStoreName.ifBlank { "جهاز المحل" }).append(" / ").append(identity.branchId).append("\n")
            append(String.format(Locale.getDefault(), "الدوام %02d:%02d - %02d:%02d", identity.shiftStartHour, identity.shiftStartMinute, identity.shiftEndHour, identity.shiftEndMinute))
        }
    }

    private fun scanProvision() {
        val intent = Intent(this,QrScannerActivity::class.java).putExtra(QrScannerActivity.EXTRA_PROMPT,"امسح QR الموظف الذي أنشأه جهاز المحل")
        status.text="جاري فتح الكاميرا..."
        runCatching { startActivityForResult(intent,REQUEST_SCAN_PROVISION) }.onFailure { status.text="تعذر فتح الماسح: ${it.message ?: "خطأ"}" }
    }

    private fun showManualProvision() {
        val input = UiKit.field(this,p,"اكتب الرمز القصير من 8 خانات أو الصق رمز APPAIR / AP3P / AP4P").apply {
            minLines = 3; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            val clip = (getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()).orEmpty()
            if (clip.trim().startsWith("AP3P:") || clip.trim().startsWith("AP4P:") || clip.trim().startsWith("APPAIR:") || clip.trim().matches(Regex("[A-Za-z0-9]{8}"))) setText(clip.trim())
        }
        val dialog = AlertDialog.Builder(this).setTitle("الربط دون كاميرا").setView(input)
            .setPositiveButton("تحقق واربط",null).setNegativeButton("إلغاء",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = input.text.toString().trim()
            if (value.isBlank()) { input.error = "أدخل رمز الاقتران"; return@setOnClickListener }
            dialog.dismiss(); handlePairingInput(value)
        } }
        dialog.show()
    }

    private fun handlePairingInput(raw:String){
        if (com.attendpro.core.AttendanceQrProtocol.isAttendanceQr(raw)) { handleAttendanceQr1928(raw); return }
        val value=raw.trim()
        PairingProtocol.decodeQrPairingEnvelope(value)?.let { envelope ->
            beginAtomicQrPairing(envelope)
            return
        }
        if(value.startsWith("AP3P:") || value.startsWith("AP4P:")){
            // Legacy full-provision text remains supported, but a Store-screen QR now uses AP5Q
            // so the Store receives a real authenticated ACK before success is reported.
            acceptProvision(value, localChannel = "ملف محلي قديم")
            return
        }
        val code=value.removePrefix("APPAIR:").replace(Regex("[^A-Za-z0-9]"),"").uppercase()
        if(code.length!=8){status.text="رمز الاقتران غير صحيح؛ يجب أن يكون 8 خانات";return}
        pendingPairingCode = code
        status.text="أبحث عن جهاز المحل محليًا عبر Bluetooth/Wi‑Fi؛ وإن توفر الإنترنت سأستخدم الخادم كمسار إضافي…"
        pairingDiscovery.start(code)
        // Server is an optional fallback only. Local Bluetooth/Hotspot pairing never waits for it.
        Thread {
            val result = CentralServerClient.claimEmployeePairingTicket(AppUpdateManager.DEFAULT_SERVER, code, identity.installationId)
            runOnUiThread {
                if (pendingPairingCode != code) return@runOnUiThread
                result.onSuccess { provision -> acceptProvision(provision, serverValidated = true) }
                    .onFailure {
                        status.text = "لا يوجد اتصال بالخادم — يستمر البحث المحلي عبر Bluetooth/Wi‑Fi بدون إنترنت"
                    }
            }
        }.start()
    }

    private fun beginAtomicQrPairing(envelope: PairingProtocol.QrPairingEnvelope) {
        val provision = PairingProtocol.decodeEmployeeProvision(envelope.provisionText)
        if (provision == null || !SecretCodec.isValid(provision.pairingSecret)) {
            status.text = "QR الربط غير صالح؛ أنشئ رمزًا جديدًا من جهاز المحل"
            updatePairingSessionStatus(status.text.toString())
            return
        }
        if (provision.expiresAt <= System.currentTimeMillis()) {
            status.text = "انتهت صلاحية QR الربط؛ أنشئ رمزًا جديدًا من جهاز المحل"
            updatePairingSessionStatus(status.text.toString())
            return
        }

        // 1.9.57: QR is the discovery seed, not a silent one-way success.  The embedded AP5Q
        // session code is used to rediscover the exact open Store session over BLE/LAN.
        // PairingDiscovery then receives the Store-issued provision over that live channel and
        // sends the authenticated ACK that lets the Store mark this phone as really linked.
        pendingPairingCode = envelope.code
        status.text = "✓ تم التقاط QR الصحيح — جاري تأكيد نفس جلسة المحل عبر Bluetooth/Wi‑Fi وإرسال ACK…"
        updatePairingSessionStatus(status.text.toString())
        pairingDiscovery.stop()
        acceptProvision(envelope.provisionText, localChannel = "QR AP5Q")
    }

    private fun registerProvisionWithServer(p: PairingProtocol.EmployeeProvision) {
        Thread {
            var result: Result<CentralServerClient.EmployeeLinkResult>? = null
            for (attempt in 0 until 4) {
                result = CentralServerClient.registerEmployeePhone(p.serverUrl, p.storeId, p.employeeId, p.displayName, p.branchId,
                    p.pairingSecret, identity.installationId, p.allowedMethods)
                if (result?.getOrNull()?.linked == true) break
                if (attempt < 3) try { Thread.sleep(1_500L * (attempt + 1)) } catch (_: InterruptedException) { return@Thread }
            }
            runOnUiThread {
                result?.onSuccess { link ->
                    if (link.linked) {
                        identity.serverLinked = true
                        identity.linkedAt = link.linkedAt
                        if (::status.isInitialized) status.text = "✓ الهاتف مرتبط بالمحل والخادم"
                    }
                }?.onFailure {
                    identity.serverLinked = false
                    if (::status.isInitialized && identity.localPairingConfirmedAt > 0L)
                        status.text = "✓ الربط المحلي ثابت — ستُستكمل مزامنة الخادم لاحقًا"
                }
                refreshProfile()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun acceptProvision(text:String,serverValidated:Boolean=false, localChannel:String="محلي") {
        val p = PairingProtocol.decodeEmployeeProvision(text)
        if (p == null) { status.text = "هذا ليس رمز موظف ATTEND PRO صالحًا"; return }
        if (!serverValidated && p.expiresAt <= System.currentTimeMillis()) { status.text = "انتهت صلاحية ملف الربط. اطلب رمزًا جديدًا من جهاز المحل"; return }
        if (!SecretCodec.isValid(p.pairingSecret)) { status.text = "بيانات الربط غير صالحة"; return }

        // Local-first: the provision shown/transmitted by the store is enough to establish trusted local pairing.
        pendingPairingCode = ""
        if (::pairingDiscovery.isInitialized) pairingDiscovery.stop()
        identity.applyProvision(p)
        identity.localPairingConfirmedAt = System.currentTimeMillis()
        identity.localPairingChannel = if (serverValidated) "الخادم" else localChannel
        identity.autoPresence = true
        EmployeeLateAlertScheduler.sync(this, identity)
        refreshProfile()
        ensureGeoPermissionIfNeeded()
        if (identity.autoPresence) startPresence() else startBackgroundPresence()
        status.text = "✓ تم الربط محليًا عبر ${identity.localPairingChannel} — الهاتف جاهز دون إنترنت، والخادم للمزامنة فقط"
        updatePairingSessionStatus(status.text.toString())

        Thread {
            var result: Result<CentralServerClient.EmployeeLinkResult>? = null
            for (attempt in 0 until 4) {
                result = CentralServerClient.registerEmployeePhone(p.serverUrl, p.storeId, p.employeeId, p.displayName, p.branchId,
                    p.pairingSecret, identity.installationId, p.allowedMethods)
                if (result?.getOrNull()?.linked == true) break
                if (attempt < 3) try { Thread.sleep(1_500L * (attempt + 1)) } catch (_: InterruptedException) { return@Thread }
            }
            runOnUiThread {
                result?.onSuccess { link ->
                    if (link.linked) {
                        identity.serverLinked = true; identity.linkedAt = link.linkedAt
                        status.text = "✓ الهاتف مرتبط بالمحل والخادم"
                    }
                }?.onFailure {
                    identity.serverLinked = false
                    status.text = "✓ الربط المحلي يعمل بدون إنترنت — ستُستكمل مزامنة الخادم لاحقًا"
                }
                refreshProfile()
            }
        }.start()
}


    private fun chooseAttendanceMethod(action: AttendanceAction) {
        if (!identity.isConfigured) { status.text = "يجب ربط الهاتف بالمحل أولًا"; return }
        pendingAction = action
        val labels = mutableListOf<String>(); val methods = mutableListOf<AttendanceMethod>()
        if (identity.allows(AttendanceMethod.PHONE_BLE_BIOMETRIC)) { labels += "◎ بصمة الإصبع أو الوجه في الهاتف"; methods += AttendanceMethod.PHONE_BLE_BIOMETRIC }
        else if (identity.allows(AttendanceMethod.PHONE_FINGERPRINT)) { labels += "◎ بصمة الإصبع في الهاتف"; methods += AttendanceMethod.PHONE_FINGERPRINT }
        if (identity.allows(AttendanceMethod.PASSWORD)) { labels += "▣ كلمة المرور"; methods += AttendanceMethod.PASSWORD }
        if (methods.isEmpty()) { status.text = "لم يسمح صاحب المحل بطريقة حضور تعمل من هاتف الموظف"; return }
        AlertDialog.Builder(this).setTitle(if(action==AttendanceAction.CHECK_IN) "طريقة تسجيل الحضور" else "طريقة تسجيل الانصراف")
            .setItems(labels.toTypedArray()) { _, index -> when(methods[index]) {
                AttendanceMethod.PHONE_BLE_BIOMETRIC -> requestBiometric()
                AttendanceMethod.PHONE_FINGERPRINT -> requestFingerprintOnly()
                AttendanceMethod.PASSWORD, AttendanceMethod.PHONE_BIOMETRIC -> confirmWithLocalCredential()
                else -> Unit
            }}.setNegativeButton("إلغاء",null).show()
}

    private fun sendAttendanceToServer(method: AttendanceMethod, evidence: String) {
        status.text = "تم التحقق، جاري تسجيل العملية في الخادم..."
        Thread {
            val result = CentralServerClient.submitEmployeeAttendance(identity.serverUrl, identity.trustedStoreId,
                identity.employeeId, identity.displayName, identity.branchId, identity.pairingSecret,
                identity.installationId, pendingAction, method, evidence)
            runOnUiThread { result.onSuccess {
                status.text = "✓ تم تسجيل ${if(pendingAction==AttendanceAction.CHECK_IN) "الحضور" else "الانصراف"} في الخادم بنجاح"
            }.onFailure { e -> status.text = "تعذر الخادم الآن؛ تم بث الإثبات محليًا لجهاز المحل وستتم المزامنة عند عودة الإنترنت" } }
        }.start()
    }

    private fun requiredBlePermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else emptyArray()

    private fun hasRequiredBlePermissions(): Boolean = requiredBlePermissions().all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun startPresence() {
        if(!identity.isConfigured) { status.text="امسح QR / باركود الربط من جهاز المحل أولًا"; return }
        // Android 12+ Bluetooth runtime permission UI must be owned by the Activity and completed
        // before PresenceService is started.  If the user denies Nearby devices, the permission
        // callback starts the same service in LAN/server-only mode so one channel never gates another.
        if (!hasRequiredBlePermissions()) {
            val permissions = requiredBlePermissions()
            if (permissions.isNotEmpty()) requestPermissions(permissions, BlePresenceAdvertiser.REQUEST_BLUETOOTH)
            status.text = "الربط محفوظ؛ اسمح بالأجهزة القريبة لتشغيل Bluetooth، أو ارفض وسيستمر Wi‑Fi/Hotspot والخادم"
            return
        }
        startBackgroundPresence()
        status.text = "الظهور التلقائي يعمل عبر Bluetooth وWi‑Fi/Hotspot والخادم"
    }

    private fun startBackgroundPresence() {
        val intent = Intent(this, PresenceService::class.java).setAction(PresenceService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun stopBackgroundPresence() { startService(Intent(this,PresenceService::class.java).setAction(PresenceService.ACTION_STOP)) }

    private fun markPresenceVerified(durationMillis: Long, proofType: Int, verifiedMethod: AttendanceMethod? = null) {
        var effectiveProof = proofType
        val matchesActiveChallenge = activeChallengeId.isNotBlank() && (verifiedMethod == null || activeChallengeMethod == verifiedMethod)
        if (matchesActiveChallenge) {
            effectiveProof = effectiveProof or BleProtocol.FLAG_CHALLENGE
            if (activeChallengeAction == AttendanceAction.CHECK_OUT) effectiveProof = effectiveProof or BleProtocol.FLAG_CHECK_OUT
        }
        val intent = Intent(this,PresenceService::class.java).setAction(PresenceService.ACTION_PROOF)
            .putExtra(PresenceService.EXTRA_DURATION,durationMillis).putExtra(PresenceService.EXTRA_FLAGS,effectiveProof)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun requestBiometric() {
        if(!identity.isConfigured) { status.text="اربط الهاتف بجهاز المحل أولًا"; return }
        if(!identity.allows(AttendanceMethod.PHONE_BLE_BIOMETRIC)) { status.text="مالك العمل لم يسمح بالحضور ببصمة/وجه الهاتف لهذا الموظف"; return }
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.P) { status.text="يلزم Android 9 أو أحدث"; return }
        startPresence()
        runCatching {
            val prompt = BiometricPrompt.Builder(this).setTitle("تأكيد الحضور أو الانصراف").setSubtitle("استخدم وسيلة التعرف الحيوي المسجلة في نظام الهاتف")
                .setNegativeButton("إلغاء",mainExecutor){_,_->status.text="تم إلغاء التحقق"}.build()
            prompt.authenticate(CancellationSignal(),mainExecutor,object:BiometricPrompt.AuthenticationCallback(){
                override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult?) { markPresenceVerified(45_000L, BleProtocol.FLAG_BIOMETRIC, AttendanceMethod.PHONE_BLE_BIOMETRIC); completeChallengeOrAttendance(AttendanceMethod.PHONE_BLE_BIOMETRIC,"تحقق حيوي أصلي من Android") }
                override fun onAuthenticationFailed(){ status.text="لم يتم التعرف، حاول مرة أخرى" }
                override fun onAuthenticationError(errorCode:Int,errString:CharSequence?){ status.text="تعذر التحقق: ${errString ?: "لم تُسجل وسيلة حيوية في إعدادات الهاتف"}" }
            })
        }.onFailure { status.text = "تعذر فتح التعرف الحيوي: ${it.message ?: "سجّل بصمة أو وجهًا في إعدادات الهاتف"}" }
    }

    @Suppress("DEPRECATION")
    private fun requestFingerprintOnly() {
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.M){status.text="هذا الهاتف لا يدعم بصمة الإصبع المطلوبة";return}
        val manager=getSystemService(FingerprintManager::class.java)
        if(manager?.isHardwareDetected!=true || !manager.hasEnrolledFingerprints()){status.text="لا توجد بصمة إصبع مسجلة أو المستشعر غير متاح";return}
        val cancel=CancellationSignal()
        AlertDialog.Builder(this).setTitle("بصمة الإصبع فقط").setMessage("ضع إصبعك المسجل على المستشعر. لن يُقبل الوجه أو الرمز في هذا الطلب.").setNegativeButton("إلغاء"){_,_->cancel.cancel()}.show()
        manager.authenticate(null,cancel,0,object:FingerprintManager.AuthenticationCallback(){
            override fun onAuthenticationSucceeded(result:FingerprintManager.AuthenticationResult?){markPresenceVerified(45_000L,BleProtocol.FLAG_BIOMETRIC, AttendanceMethod.PHONE_FINGERPRINT);completeChallengeOrAttendance(AttendanceMethod.PHONE_FINGERPRINT,"بصمة إصبع Android موثقة")}
            override fun onAuthenticationFailed(){status.text="لم تتطابق بصمة الإصبع؛ أعد المحاولة"}
            override fun onAuthenticationError(code:Int,message:CharSequence?){status.text="تعذر فحص بصمة الإصبع: ${message?:"خطأ"}"}
        },null)
    }

    private fun completeChallengeOrAttendance(method: AttendanceMethod,evidence:String){
        val challengeId=activeChallengeId
        if(challengeId.isBlank()){sendAttendanceToServer(method,evidence);return}
        if(activeChallengeMethod!=method){status.text="هذا الطلب لا يقبل إلا ${challengeMethodLabel(activeChallengeMethod)}";return}
        if (challengeId.startsWith("local:")) {
            identity.lastPresenceProofAt = System.currentTimeMillis()
            EmployeeLateAlertScheduler.sync(this, identity)
            status.text="✓ تم إثبات ${if (activeChallengeAction == AttendanceAction.CHECK_IN) "الحضور" else "الانصراف"} محليًا وإرساله لجهاز المحل"
            activeChallengeId="";activeChallengeMethod=null;activeChallengeAction=AttendanceAction.CHECK_IN;identity.pendingChallengeId="";identity.pendingChallengeMethod="";identity.pendingChallengeExpiresAt=0L;identity.pendingChallengeAction=AttendanceAction.CHECK_IN.name
            return
        }
        status.text="تم التحقق، جاري إرسال إثبات الوجود للمحل…"
        Thread { val result=CentralServerClient.completeEmployeePresenceChallenge(identity.serverUrl,identity.trustedStoreId,identity.employeeId,identity.pairingSecret,identity.installationId,challengeId,method,evidence)
            runOnUiThread { result.onSuccess { identity.lastPresenceProofAt = System.currentTimeMillis(); EmployeeLateAlertScheduler.sync(this, identity); status.text="✓ تم إثبات ${if (activeChallengeAction == AttendanceAction.CHECK_IN) "الحضور" else "الانصراف"} لصاحب المحل";activeChallengeId="";activeChallengeMethod=null;activeChallengeAction=AttendanceAction.CHECK_IN;identity.pendingChallengeId="";identity.pendingChallengeMethod="";identity.pendingChallengeExpiresAt=0L;identity.pendingChallengeAction=AttendanceAction.CHECK_IN.name }
                .onFailure{status.text="فشل إرسال الإثبات: ${it.message?:"تحقق من الإنترنت"}"} }
        }.start()
    }

    private fun challengeMethodLabel(method:AttendanceMethod?):String=when(method){
        AttendanceMethod.PHONE_FINGERPRINT->"بصمة الإصبع"
        AttendanceMethod.PHONE_BLE_BIOMETRIC->"بصمة/وجه الهاتف"
        AttendanceMethod.PASSWORD, AttendanceMethod.PHONE_BIOMETRIC->"كلمة المرور"
        AttendanceMethod.GPS->"GPS للمراقبة فقط (غير مستخدم لإثبات الحضور)"
        else->"الطريقة المحددة"
    }

    private fun openPendingChallenge() {
        val id=identity.pendingChallengeId;val expires=identity.pendingChallengeExpiresAt
        val method=runCatching{AttendanceMethod.valueOf(identity.pendingChallengeMethod)}.getOrNull()
        val action=runCatching{AttendanceAction.valueOf(identity.pendingChallengeAction)}.getOrDefault(AttendanceAction.CHECK_IN)
        if(id.isBlank()||method==null||expires<=System.currentTimeMillis()||id==activeChallengeId)return
        activeChallengeId=id;activeChallengeMethod=method;activeChallengeAction=action;pendingAction=action
        val actionText=if(action==AttendanceAction.CHECK_IN) "الحضور" else "الانصراف"
        AlertDialog.Builder(this).setTitle("أثبت $actionText الآن").setMessage("طلب من ${identity.trustedStoreName}\nنوع الطلب: $actionText\nالطريقة الإلزامية: ${challengeMethodLabel(method)}\nلن يُقبل أي بديل آخر.")
            .setPositiveButton("ابدأ التحقق"){_,_->when(method){AttendanceMethod.PHONE_FINGERPRINT->requestFingerprintOnly();AttendanceMethod.PHONE_BLE_BIOMETRIC->requestBiometric();AttendanceMethod.GPS->{status.text="GPS للمراقبة فقط في 1.9.58 ولا يُقبل كإثبات حضور"};AttendanceMethod.PASSWORD,AttendanceMethod.PHONE_BIOMETRIC->confirmWithLocalCredential();else->{status.text="الطريقة المطلوبة غير مدعومة من الهاتف"}}}.setNegativeButton("لاحقًا",null).show()
    }

    private fun showConnectionStatus(){
        val cm=getSystemService(ConnectivityManager::class.java);val network=cm?.activeNetwork;val caps=network?.let{cm.getNetworkCapabilities(it)}
        val wifi=caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true
        val internet=caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true
        val bt=getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
        val now=System.currentTimeMillis()
        val directAge=if(identity.lastBleDirectSeenAt>0) now-identity.lastBleDirectSeenAt else Long.MAX_VALUE
        val lanAge=if(identity.lastLanStoreSeenAt>0) now-identity.lastLanStoreSeenAt else Long.MAX_VALUE
        val serviceAge=if(identity.presenceServiceHeartbeatAt>0) now-identity.presenceServiceHeartbeatAt else Long.MAX_VALUE
        val serviceAlive=serviceAge<25_000L
        val blePermissions = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) true else
            checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED
        val ble=when{
            bt==null->"غير مدعوم"
            !bt.isEnabled->"Adapter OFF"
            directAge<12_000L->"GATT/Heartbeat/ACK/confirm مؤكد ✓ • آخر ACK ${directAge/1000}ث"
            identity.lastBleDirectSeenAt>0->"GATT غير مؤكد الآن • آخر ACK ${directAge/1000}ث • جارٍ إعادة الربط"
            else->"لم يصل ACK مباشر بعد"
        }
        val lan=when{
            lanAge<8_000L->"ACK ثنائي مؤكد ✓ • آخر ACK ${lanAge/1000}ث"
            wifi->"الشبكة المحلية متاحة • بانتظار ACK"
            else->"غير متصل"
        }
        val server=ServerDiagnostics.snapshot()
        fun serverTime(value: Long): String = if (value <= 0L) "لا يوجد" else java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(value))
        val serverState=when{
            server.isFresh(now)->"متصل فعليًا ✓ • HTTP ${server.lastHttpStatus} • آخر نجاح ${serverTime(server.lastSuccessAt)} • ${server.lastSuccessPath.ifBlank { server.lastPath }}"
            identity.serverLinked && internet->"الربط مسجل لكن لا يوجد HTTP نجاح حديث • آخر نجاح ${serverTime(server.lastSuccessAt)} • آخر خطأ ${serverTime(server.lastErrorAt)} ${server.lastError.takeIf{it.isNotBlank()}?.let{"• $it"}.orEmpty()}"
            else->"غير متصل فعليًا • آخر نجاح ${serverTime(server.lastSuccessAt)} • آخر خطأ ${serverTime(server.lastErrorAt)} ${server.lastError.takeIf{it.isNotBlank()}?.let{"• $it"}.orEmpty()}"
        }
        val lm=getSystemService(LocationManager::class.java)
        val gpsEnabled=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P) lm?.isLocationEnabled==true else
            runCatching { lm?.isProviderEnabled(LocationManager.GPS_PROVIDER)==true || lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER)==true }.getOrDefault(false)
        val gpsAge = if (identity.lastGpsObservedAt > 0L) now - identity.lastGpsObservedAt else Long.MAX_VALUE
        val gpsState=when{
            !gpsEnabled->"متوقف"
            gpsAge < 5*60_000L -> {
                val label = when(identity.lastGpsState) {
                    OfflineGeoMonitor.STATE_INSIDE -> "داخل نطاق التعرف"
                    OfflineGeoMonitor.STATE_NEAR -> "قريب من نطاق التعرف"
                    OfflineGeoMonitor.STATE_OUTSIDE -> "خارج نطاق التعرف"
                    else -> "قراءة غير محددة"
                }
                "$label • ${identity.lastGpsDistanceMeters}م • دقة ±${identity.lastGpsAccuracyMeters}م • منذ ${gpsAge/1000}ث • للمراقبة فقط"
            }
            identity.isTrustedStoreGpsConfigured->"مفعّل للتعرّف على قرب الهاتف فقط • لا يسجل حضورًا"
            else->"مفعّل • موقع المحل غير محفوظ"
        }
        val diagnosticText = buildString {
            appendLine("خدمة الخلفية: ${if(serviceAlive) "تعمل ✓ • آخر نبض ${serviceAge/1000}ث" else "غير مؤكدة/متوقفة"}")
            appendLine("Bluetooth Adapter: ${if(bt?.isEnabled==true) "ON" else "OFF"}")
            appendLine("صلاحيات BLE: ${if(blePermissions) "ممنوحة ✓" else "ناقصة"}")
            appendLine("Advertising: ${identity.lastBleAdvertisingState.ifBlank { if(serviceAlive) "لا توجد نتيجة إعلان بعد" else "الخدمة غير مؤكدة" }}")
            appendLine("GATT: $ble")
            appendLine("LAN: $lan")
            appendLine("Server: $serverState")
            appendLine("GPS: $gpsState")
            appendLine("آخر حالة BLE: ${identity.lastBleDirectState.ifBlank { "لا توجد" }}")
            append("آخر حالة LAN: ${identity.lastLanState.ifBlank { "لا توجد" }}")
        }
        AlertDialog.Builder(this).setTitle("تشخيص الاتصال الحقيقي").setMessage(diagnosticText)
            .setPositiveButton("حسنًا",null).show()
    }

    private fun showReadinessCheck() {
        val bt = getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
        val now = System.currentTimeMillis()
        val serviceAlive = identity.presenceServiceHeartbeatAt > 0L && now - identity.presenceServiceHeartbeatAt < 25_000L
        val advertisingState = identity.lastBleAdvertisingState
        val bluetooth = when {
            bt == null -> "غير مدعوم"
            !bt.isEnabled -> "متوقف"
            !bt.isMultipleAdvertisementSupported -> "يعمل لكن الهاتف لا يدعم بث BLE"
            serviceAlive && advertisingState.contains("BLE يعمل") -> "Advertising يعمل من الخدمة ✓"
            serviceAlive -> "الخدمة تعمل • ${advertisingState.ifBlank { "بانتظار نتيجة Advertising" }}"
            else -> "Bluetooth متاح لكن خدمة الخلفية غير مؤكدة"
        }
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        val internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val local = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val lanVerified = identity.lastLanStoreSeenAt > 0L && now - identity.lastLanStoreSeenAt < 8_000L
        val biometric = biometricReadiness()
        AlertDialog.Builder(this).setTitle("فحص جاهزية هاتف الموظف").setMessage(
            "الربط بالخادم: ${if(ServerDiagnostics.snapshot().isFresh()) "HTTP مؤكد ✓" else "غير مؤكد الآن"}\n" +
            "Bluetooth: $bluetooth\n" +
            "Wi‑Fi/نقطة اتصال: ${when { lanVerified -> "LAN ACK مؤكد ✓"; local -> "الشبكة متاحة • بانتظار ACK من المحل"; else -> "غير متصل" }}\n" +
            "الإنترنت: ${if(internet) "متاح" else "غير متاح — يظل Bluetooth والشبكة المحلية يعملان"}\n" +
            "بصمة/وجه Android: $biometric\n\n" +
            "الحضور دون إنترنت يعتمد على قناة BLE أو LAN موثقة فعليًا. وجود Wi‑Fi أو Bluetooth وحده لا يعني اتصالًا. ومع الإنترنت لا يعتبر الخادم متصلًا إلا بعد نجاح HTTP حقيقي."
        ).setPositiveButton("حسنًا",null).show()
    }

    @Suppress("DEPRECATION")
    private fun biometricReadiness(): String = try {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val manager = getSystemService(android.hardware.biometrics.BiometricManager::class.java)
                if (manager?.canAuthenticate() == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS) "مسجل وجاهز ✓" else "غير مسجل أو غير متاح"
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                val manager = getSystemService(android.hardware.fingerprint.FingerprintManager::class.java)
                if (manager?.isHardwareDetected == true && manager.hasEnrolledFingerprints()) "بصمة مسجلة وجاهزة ✓" else "لا توجد بصمة مسجلة"
            }
            else -> "إصدار Android قديم"
        }
    } catch (_: Exception) { "تعذر فحص النظام" }

    private fun testBiometricOnly() {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.P) { status.text="يلزم Android 9 أو أحدث"; return }
        runCatching {
            val prompt = BiometricPrompt.Builder(this).setTitle("اختبار بصمة/وجه الهاتف").setSubtitle("هذا اختبار فقط ولن يسجل حضورًا")
                .setNegativeButton("إلغاء",mainExecutor){_,_->status.text="تم إلغاء الاختبار"}.build()
            prompt.authenticate(CancellationSignal(),mainExecutor,object:BiometricPrompt.AuthenticationCallback(){
                override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult?) { status.text="✓ نظام البصمة/الوجه في الهاتف يعمل بصورة صحيحة" }
                override fun onAuthenticationFailed(){ status.text="لم يتعرف الهاتف؛ نظف المستشعر أو أعد المحاولة" }
                override fun onAuthenticationError(errorCode:Int,errString:CharSequence?){ status.text="تعذر اختبار البصمة: ${errString ?: "غير متاحة"}" }
            })
        }.onFailure { status.text="تعذر فتح نظام البصمة/الوجه: ${it.message ?: "غير متاح"}" }
    }

    override fun onNewIntent(newIntent: Intent?) {
        super.onNewIntent(newIntent)
        if (newIntent != null) setIntent(newIntent)
        if (::identity.isInitialized && newIntent?.getBooleanExtra(AttendanceRequestNotifier.EXTRA_OPEN_CHALLENGE, false) == true) {
            newIntent.removeExtra(AttendanceRequestNotifier.EXTRA_OPEN_CHALLENGE)
            Handler(Looper.getMainLooper()).post { openPendingChallenge() }
        }
    }

    override fun onResume() {
        ensureCheckoutButton1927()
        NotificationPermissionHelper.ensure(this)
        if (intent?.getBooleanExtra(AttendanceRequestNotifier.EXTRA_OPEN_CHALLENGE, false) == true) { intent.removeExtra(AttendanceRequestNotifier.EXTRA_OPEN_CHALLENGE); android.os.Handler(android.os.Looper.getMainLooper()).post { openPendingChallenge() } }
        super.onResume()
        if (::identity.isInitialized && identity.isConfigured) {
            EmployeeLateAlertScheduler.sync(this, identity)
            if(identity.autoPresence) startPresence() else startBackgroundPresence()
        }
        if (intent?.getBooleanExtra("open_late_attendance", false) == true) {
            intent.removeExtra("open_late_attendance")
            status.text = "موعد الدوام بدأ — اختر تسجيل الحضور وأكمل طريقة الإثبات المسموح بها"
        }
        if(::identity.isInitialized) openPendingChallenge()
    }

    private fun setupLocalCredentials() {
        val input = android.widget.EditText(this).apply {
            hint = "كلمة مرور جديدة - 6 أحرف على الأقل"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("▣ إعداد كلمة مرور الهاتف")
            .setMessage("تُحفظ بصيغة مشفرة محليًا. أُلغي PIN والنقش من هذا الإصدار.")
            .setView(input)
            .setPositiveButton("حفظ", null)
            .setNegativeButton("إلغاء", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val password = input.text.toString()
                        if (password.length < 6) input.error = "أدخل 6 أحرف على الأقل"
                        else { identity.confirmationPasswordHash = com.attendpro.core.PairingProtocol.pinHash(password); identity.confirmationPinHash = ""; android.widget.Toast.makeText(this, "✓ تم حفظ كلمة المرور", android.widget.Toast.LENGTH_SHORT).show(); dialog.dismiss() }
                    }
                }
            }.show()
}

    private fun confirmWithLocalCredential() {
        if (identity.confirmationPasswordHash.isBlank()) {
            android.widget.Toast.makeText(this, "لم تُضبط كلمة مرور لهذا الهاتف", android.widget.Toast.LENGTH_LONG).show(); setupLocalCredentials(); return
        }
        val input = android.widget.EditText(this).apply { hint = "كلمة المرور"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        android.app.AlertDialog.Builder(this).setTitle("▣ تأكيد الحضور بكلمة المرور").setView(input).setPositiveButton("تأكيد", null).setNegativeButton("إلغاء", null).create().also { dialog ->
            dialog.setOnShowListener { dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (com.attendpro.core.PairingProtocol.pinHash(input.text.toString()) == identity.confirmationPasswordHash) { dialog.dismiss(); markPresenceVerified(45_000L, com.attendpro.core.BleProtocol.FLAG_CREDENTIAL, AttendanceMethod.PASSWORD); completeChallengeOrAttendance(com.attendpro.core.AttendanceMethod.PASSWORD, "PASSWORD_LOCAL") }
                else input.error = "كلمة المرور غير صحيحة"
            } }
        }.show()
}

    private fun ensureGeoPermissionIfNeeded() {
        if (!identity.geoArrivalAlertsEnabled || !identity.isTrustedStoreGpsConfigured) return
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_GPS_PERMISSION)
            status.text = "اسمح بالموقع لتفعيل التعرف المحلي عند دخول نطاق المحل بدون إنترنت"
        }
    }

    private fun requestGpsProof() {
        if (!identity.isConfigured) { status.text = "اربط الهاتف بجهاز المحل أولًا"; return }
        if (!identity.isTrustedStoreGpsConfigured) { status.text = "موقع المحل غير مضمّن في الربط. اطلب QR جديدًا بعد ضبط GPS في قسم مالك العمل"; return }
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_GPS_PERMISSION)
            status.text = "اسمح بالموقع ثم أعد الضغط على GPS"
            return
        }
        val manager = getSystemService(LocationManager::class.java)
        if (manager == null) { status.text = "خدمة الموقع غير متاحة على هذا الهاتف"; return }
        status.text = "جاري طلب موقع حديث ودقيق..."
        requestFreshLocation(manager) { location, error ->
            if (location == null) { status.text = error ?: "تعذر الحصول على موقع حديث"; return@requestFreshLocation }
            if (isMockLocation(location)) { status.text = "تم رفض موقع تجريبي/مزيف"; return@requestFreshLocation }
            val target = Location("ATTEND_PRO_STORE").apply { latitude = identity.trustedStoreLatitude; longitude = identity.trustedStoreLongitude }
            val distance = location.distanceTo(target)
            val accuracyAllowance = location.accuracy.takeIf { it.isFinite() }?.coerceIn(0f, 100f) ?: 0f
            if (distance > identity.trustedStoreGpsRadius + accuracyAllowance) {
                status.text = "أنت خارج نطاق المحل (${distance.toInt()}م، النطاق ${identity.trustedStoreGpsRadius}م، دقة الهاتف ${location.accuracy.toInt()}م)"
                return@requestFreshLocation
            }
            startPresence()
            val now = System.currentTimeMillis()
            identity.lastGpsInsideAt = now
            identity.lastGpsObservedAt = now
            identity.lastGpsEnteredAt = identity.lastGpsEnteredAt.takeIf { it > 0L } ?: now
            identity.lastGpsDistanceMeters = distance.toInt()
            identity.lastGpsAccuracyMeters = location.accuracy.toInt().coerceAtLeast(0)
            identity.lastGpsState = if (distance <= identity.trustedStoreGpsRadius) OfflineGeoMonitor.STATE_INSIDE else OfflineGeoMonitor.STATE_NEAR
            identity.lastGpsServerUploadAt = 0L
            status.text = "✓ تم التعرّف على الهاتف عبر GPS: ${distance.toInt()}م • دقة ±${location.accuracy.toInt()}م. هذه قراءة قرب فقط ولا تسجل حضورًا."
        }
    }

    private fun isMockLocation(location: Location): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else @Suppress("DEPRECATION") location.isFromMockProvider

    private fun bestLastLocation(manager: LocationManager): Location? {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        return providers.mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }.maxByOrNull { it.time }
    }

    @Suppress("MissingPermission")
    private fun requestFreshLocation(manager: LocationManager, callback: (Location?, String?) -> Unit) {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) { callback(null, "فعّل الموقع واختر الدقة العالية ثم أعد المحاولة"); return }
        val delivered = java.util.concurrent.atomic.AtomicBoolean(false)
        var best: Location? = null
        lateinit var listener: LocationListener
        fun finish(location: Location?, error: String? = null) {
            if (delivered.compareAndSet(false, true)) {
                runCatching { manager.removeUpdates(listener) }
                runOnUiThread { callback(location, error) }
            }
        }
        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (System.currentTimeMillis() - location.time > 2 * 60_000L) return
                if (best == null || location.accuracy < best!!.accuracy) best = location
                if (location.hasAccuracy() && location.accuracy <= 35f) finish(location)
            }
            @Deprecated("Deprecated in Android") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        providers.forEach { provider -> runCatching { manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) } }
        Handler(Looper.getMainLooper()).postDelayed({
            val fallback = best ?: bestLastLocation(manager)?.takeIf { System.currentTimeMillis() - it.time <= 10 * 60_000L }
            finish(fallback, if (fallback == null) "لم يصل موقع صالح. فعّل دقة الموقع العالية وافتح GPS خارج المبنى للحظات" else null)
        }, 25_000L)
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        if(requestCode==REQUEST_SCAN_PROVISION) {
            if(resultCode==RESULT_OK) {
                val scanned = data?.getStringExtra(QrScannerActivity.EXTRA_RESULT).orEmpty()
                updatePairingSessionStatus("تمت قراءة الرمز — جارٍ التحقق من جلسة الربط…")
                handlePairingInput(scanned)
            } else {
                status.text=data?.getStringExtra(QrScannerActivity.EXTRA_ERROR) ?: "تم إلغاء المسح"
                updatePairingSessionStatus(status.text.toString())
            }
            return
        }
        super.onActivityResult(requestCode,resultCode,data)
    }

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        when(requestCode) {
            BlePresenceAdvertiser.REQUEST_BLUETOOTH -> {
                // PresenceService is intentionally started only after this Activity-owned permission
                // result.  A denial does not block LAN/server; it merely disables BLE/GATT channels.
                if (identity.isConfigured) startBackgroundPresence()
                maybeRequestNotificationPermission()
                if(grantResults.isNotEmpty()&&grantResults.all{it==PackageManager.PERMISSION_GRANTED}) {
                    status.text = "✓ Bluetooth جاهز؛ جاري تشغيل الربط المباشر والظهور التلقائي"
                } else {
                    status.text="Bluetooth غير مسموح؛ Wi‑Fi/Hotspot والخادم يواصلان العمل"
                }
            }
            REQUEST_GPS_PERMISSION -> {
                if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                    status.text = "✓ تم منح الموقع — تفعيل مراقبة نطاق المحل محليًا"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && identity.geoArrivalAlertsEnabled && identity.isTrustedStoreGpsConfigured &&
                        checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        AlertDialog.Builder(this).setTitle("السماح بالموقع في الخلفية")
                            .setMessage("لكي يتعرف التطبيق على دخول نطاق المحل بدون إنترنت حتى عندما لا تكون الشاشة مفتوحة، اختر إذن الموقع «السماح طوال الوقت» من إعدادات التطبيق. إذا لم تمنحه، يستمر Bluetooth وWi‑Fi ويعمل GPS أثناء الاستخدام المسموح فقط.")
                            .setPositiveButton("فتح إعدادات التطبيق") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                            .setNegativeButton("لاحقًا", null).show()
                    }
                    if (identity.isConfigured) startPresence()
                } else status.text = "لم يتم السماح بالموقع؛ Bluetooth وWi‑Fi سيستمران"
            }
            PairingDiscovery.REQUEST_SCAN -> {
                if(grantResults.isNotEmpty()&&grantResults.all{it==PackageManager.PERMISSION_GRANTED}) pairingDiscovery.resumeAfterPermission()
                else {
                    status.text="Bluetooth مرفوض؛ يمكنك الاستمرار عبر Hotspot أو QR/الرمز القصير"
                    updatePairingSessionStatus(status.text.toString())
                }
            }
        }
    }

    override fun onDestroy(){connectionUiHandler.removeCallbacks(connectionUiTask);advertiser.stop();networkPresence.stop();if(::pairingDiscovery.isInitialized)pairingDiscovery.stop();super.onDestroy()}

    private fun handleAttendanceQr1928(value: String) {
        val employeeId = identity.employeeId
        val secretText = identity.pairingSecret
        if (employeeId.isBlank() || secretText.isBlank()) {
            android.widget.Toast.makeText(this, "اربط الهاتف بالمحل أولًا", android.widget.Toast.LENGTH_LONG).show(); return
        }
        val secret = com.attendpro.core.SecretCodec.decode(secretText)
        val decoded = if (secret == null) null else com.attendpro.core.AttendanceQrProtocol.decode(value, employeeId, secret)
        val now = System.currentTimeMillis()
        if (decoded == null || decoded.expiresAt <= now) {
            android.widget.Toast.makeText(this, "QR الحضور غير صالح أو منتهي", android.widget.Toast.LENGTH_LONG).show(); return
        }
        if (isQrReplay1928(decoded.challengeId, now)) {
            android.widget.Toast.makeText(this, "تم استخدام رمز الحضور هذا من قبل", android.widget.Toast.LENGTH_LONG).show(); return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(if (decoded.action == AttendanceAction.CHECK_IN) "▦ طلب إثبات حضور" else "▦ طلب إثبات انصراف")
            .setMessage("تم التحقق من رمز جهاز المحل. نوع الطلب: ${if (decoded.action == AttendanceAction.CHECK_IN) "حضور" else "انصراف"}. اضغط موافقة لإرسال إثبات مشفّر للمحل عبر Bluetooth أو Wi‑Fi، ولا يحتاج إنترنت.")
            .setPositiveButton("موافقة") { _, _ ->
                if (!consumeQrOnce1928(decoded.challengeId, decoded.expiresAt)) {
                    android.widget.Toast.makeText(this, "تم استخدام رمز الحضور هذا من قبل", android.widget.Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val qrFlags = com.attendpro.core.BleProtocol.FLAG_QR or if (decoded.action == AttendanceAction.CHECK_OUT) com.attendpro.core.BleProtocol.FLAG_CHECK_OUT else 0
                markPresenceVerified(45_000L, qrFlags, com.attendpro.core.AttendanceMethod.PHONE_PROXIMITY)
                identity.lastPresenceProofAt = System.currentTimeMillis()
                EmployeeLateAlertScheduler.sync(this, identity)
                status.text = "✓ تمت الموافقة على QR وإرسال الإثبات المشفّر لجهاز المحل"
                activeChallengeId = ""; activeChallengeMethod = null; activeChallengeAction = AttendanceAction.CHECK_IN
                identity.pendingChallengeId = ""; identity.pendingChallengeMethod = ""; identity.pendingChallengeExpiresAt = 0L; identity.pendingChallengeAction = AttendanceAction.CHECK_IN.name
            }
            .setNegativeButton("رفض", null).show()
    }


    private fun isQrReplay1928(challengeId: String, now: Long): Boolean {
        val prefs = getSharedPreferences("attend_qr_security", MODE_PRIVATE)
        val raw = prefs.getString("used_v2", "{}") ?: "{}"
        val obj = runCatching { org.json.JSONObject(raw) }.getOrElse { org.json.JSONObject() }
        var changed = false
        val keys = obj.keys().asSequence().toList()
        keys.forEach { key ->
            if (obj.optLong(key, 0L) <= now) { obj.remove(key); changed = true }
        }
        if (changed) prefs.edit().putString("used_v2", obj.toString()).apply()
        return obj.optLong(challengeId, 0L) > now
    }

    private fun consumeQrOnce1928(challengeId: String, expiresAt: Long): Boolean {
        val now = System.currentTimeMillis()
        if (challengeId.isBlank() || expiresAt <= now) return false
        val prefs = getSharedPreferences("attend_qr_security", MODE_PRIVATE)
        synchronized(QR_REPLAY_LOCK_1928) {
            val raw = prefs.getString("used_v2", "{}") ?: "{}"
            val obj = runCatching { org.json.JSONObject(raw) }.getOrElse { org.json.JSONObject() }
            obj.keys().asSequence().toList().forEach { key -> if (obj.optLong(key, 0L) <= now) obj.remove(key) }
            if (obj.optLong(challengeId, 0L) > now) return false
            obj.put(challengeId, expiresAt)
            if (obj.length() > 128) {
                val oldest = obj.keys().asSequence().toList().minByOrNull { obj.optLong(it, Long.MAX_VALUE) }
                if (oldest != null && oldest != challengeId) obj.remove(oldest)
            }
            prefs.edit().putString("used_v2", obj.toString()).apply()
            return true
        }
    }

    private fun ensureCheckoutButton1927() {
        val content = findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
        if (content.findViewWithTag<android.view.View>("ATTEND_PRO_CHECKOUT_1927") != null) return
        val button = android.widget.Button(this).apply { tag = "ATTEND_PRO_CHECKOUT_1927"; text = "تسجيل الانصراف"; textSize = 16f; isAllCaps = false; setOnClickListener { chooseAttendanceMethod(com.attendpro.core.AttendanceAction.CHECK_OUT) } }
        val density = resources.displayMetrics.density
        val params = android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, (54 * density).toInt()).apply { gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL; leftMargin=(18*density).toInt();rightMargin=(18*density).toInt();bottomMargin=(18*density).toInt() }
        content.addView(button, params)
    }

}
