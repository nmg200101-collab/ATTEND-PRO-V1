package com.attendpro.store

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import androidx.core.content.FileProvider
import android.location.Location
import android.location.LocationManager
import android.location.LocationListener
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.attendpro.core.AppIntegrity1982
import com.attendpro.core.AppLanguage
import com.attendpro.core.AttendanceAction
import com.attendpro.core.AttendanceEvent
import com.attendpro.core.AttendanceMethod
import com.attendpro.core.BleProtocol
import com.attendpro.core.BleDirectProtocol
import com.attendpro.core.LanAckProtocol
import com.attendpro.core.LanConfirmProtocol
import com.attendpro.core.CentralServerClient
import com.attendpro.core.ServerDiagnostics
import com.attendpro.core.DeviceIdentity
import com.attendpro.core.NetworkTools
import com.attendpro.core.PairedEmployee
import com.attendpro.core.PairingProtocol
import com.attendpro.core.PresenceEvent
import com.attendpro.core.QrCodeTools
import com.attendpro.core.SecretCodec
import com.attendpro.core.ShiftWindow
import com.attendpro.core.StoreRepository
import com.attendpro.core.UiKit
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {

    private var connectionRecoveryCallback1927: android.net.ConnectivityManager.NetworkCallback? = null

    private fun installConnectionRecovery1927() {
        if (connectionRecoveryCallback1927 != null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return
        val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                runOnUiThread { autoSyncIfReady(); refreshDashboard() }
            }
            override fun onLost(network: android.net.Network) { runOnUiThread { refreshDashboard() } }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }.onSuccess { connectionRecoveryCallback1927 = cb }
    }

    private lateinit var repo: StoreRepository
    private lateinit var scanner: BleEmployeeScanner
    private lateinit var networkListener: NetworkPresenceListener
    private lateinit var directBle: BleDirectLinkClient
    private lateinit var voiceAnnouncer: StoreVoiceAnnouncer
    private lateinit var lateAlerts: SmartLateAlertManager
    private lateinit var counts: TextView
    private lateinit var status: TextView
    private lateinit var nearbyView: TextView
    private lateinit var activityTimelineView: TextView
    private lateinit var attendanceTodayView: TextView
    private lateinit var storeSummary: TextView
    private lateinit var connectionSummaryView: TextView
    private lateinit var linkedEmployeesSummaryView: TextView
    private lateinit var recentAttendanceSummaryView: TextView
    private var pendingFaceEmployeeId: String? = null
    private var pendingFaceRecognition: Boolean = false
    private var pendingFaceCapturePath: String? = null
    private val faceEnrollmentTemplates = mutableListOf<String>()
    private val faceEnrollmentQualities = mutableListOf<Int>()
    private var pendingLivenessEmployeeId: String? = null
    private var pendingLivenessFirstTemplate: String? = null
    private var pendingLivenessFirstScore: Float = 0f
    private var pendingVoiceEmployeeId: String? = null
    private var pendingVoiceChallenge: String? = null
    private var pendingVoicePrintScore: Float = -1f
    private var pendingFaceAttendanceAction: AttendanceAction? = null
    private var pendingVoiceAttendanceAction: AttendanceAction? = null
    private var pendingVoiceEnrollmentEmployeeId: String? = null
    private val voiceEnrollmentTemplates = mutableListOf<String>()
    private val voiceEnrollmentQualities = mutableListOf<Int>()
    private val autoPresenceRecorded = mutableSetOf<String>()
    @Volatile private var syncInProgress = false
    private var employeeManagerMode = false
    private var recoveryAttempted = false
    private var recoveryRetryCount = 0
    private var lastLateScheduleSyncAt = 0L
    private data class NearbyPhone(
        val seenAt: Long,
        val rssi: Int,
        val channelTimes: Map<String, Long>,
        val deviceName: String = "",
        val gpsInsideAt: Long = 0L
    )
    private val nearby = ConcurrentHashMap<String, NearbyPhone>()
    private val authenticatedPresenceAt = ConcurrentHashMap<String, Long>()
    private val lanConfirmedAt = ConcurrentHashMap<String, Long>()
    private val lanPendingTokens = ConcurrentHashMap<String, Pair<Int, Long>>()
    private val serverPresenceAt = ConcurrentHashMap<String, Long>()
    private val serverGpsState = ConcurrentHashMap<String, String>()
    private val serverGpsDistance = ConcurrentHashMap<String, Int>()
    private val serverGpsAccuracy = ConcurrentHashMap<String, Int>()
    private val serverGpsSeenAt = ConcurrentHashMap<String, Long>()
    @Volatile private var serverPresencePollInFlight = false
    private var lastServerPresencePollAt = 0L
    private var activePairingBeacon: PairingBeacon? = null
    private var activePairingCode: String = ""
    private var activePairingProvision: String = ""
    private var activePairingMode: PairingBeacon.Mode = PairingBeacon.Mode.ALL
    private val lastPresenceLogAt = ConcurrentHashMap<String, Long>()
    private val presenceUnprovedSince = mutableMapOf<String, Long>()
    private val presenceReminderSentAt = mutableMapOf<String, Long>()
    private val nearbyRefreshHandler = Handler(Looper.getMainLooper())
    private val nearbyRefreshTask = object : Runnable {
        override fun run() {
            // The compact dashboard does not create the legacy nearbyView.
            if (::counts.isInitialized || ::connectionSummaryView.isInitialized) refreshDashboard()
            ensurePresenceDiscoveryRunning()
            pollServerPresenceIfDue()
            autoSyncIfReady()
            if (::lateAlerts.isInitialized) lateAlerts.tick()
            checkConnectedWithoutProof()
            val lateNow = System.currentTimeMillis()
            if (lateNow - lastLateScheduleSyncAt >= 60_000L) {
                LateAlertScheduler.sync(this@MainActivity, repo, lateNow)
                lastLateScheduleSyncAt = lateNow
            }
            nearbyRefreshHandler.postDelayed(this, 5_000L)
        }
    }
    private val p by lazy { UiKit.palette(this) }

    private fun t(arabic: String, english: String): String = AppLanguage.text(this, arabic, english)

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
        repo = StoreRepository(this)
        val employeeManagerRequested = intent?.getBooleanExtra(EXTRA_EMPLOYEE_MANAGER, false) == true
        employeeManagerMode = employeeManagerRequested && repo.validateStoreAdminSession(intent?.getStringExtra(EXTRA_STORE_ADMIN_SESSION).orEmpty())
        pendingFaceEmployeeId = savedInstanceState?.getString("pendingFaceEmployeeId")
        pendingFaceRecognition = savedInstanceState?.getBoolean("pendingFaceRecognition", false) ?: false
        pendingFaceCapturePath = savedInstanceState?.getString("pendingFaceCapturePath")
        pendingLivenessEmployeeId = savedInstanceState?.getString("pendingLivenessEmployeeId")
        pendingLivenessFirstTemplate = savedInstanceState?.getString("pendingLivenessFirstTemplate")
        pendingLivenessFirstScore = savedInstanceState?.getFloat("pendingLivenessFirstScore", 0f) ?: 0f
        pendingVoiceEmployeeId = savedInstanceState?.getString("pendingVoiceEmployeeId")
        pendingVoiceChallenge = savedInstanceState?.getString("pendingVoiceChallenge")
        pendingVoicePrintScore = savedInstanceState?.getFloat("pendingVoicePrintScore", -1f) ?: -1f
        pendingFaceAttendanceAction = savedInstanceState?.getString("pendingFaceAttendanceAction")?.let { runCatching { AttendanceAction.valueOf(it) }.getOrNull() }
        pendingVoiceAttendanceAction = savedInstanceState?.getString("pendingVoiceAttendanceAction")?.let { runCatching { AttendanceAction.valueOf(it) }.getOrNull() }
        pendingVoiceEnrollmentEmployeeId = savedInstanceState?.getString("pendingVoiceEnrollmentEmployeeId")
        voiceAnnouncer = StoreVoiceAnnouncer(this, repo)
        lateAlerts = SmartLateAlertManager(this, repo, voiceAnnouncer, onStatus = { message -> runOnUiThread { if (::status.isInitialized) status.text = message } })
        directBle = BleDirectLinkClient(this) { employeeId, connected, message ->
            runOnUiThread { markDirectBleState(employeeId, connected, message) }
        }
        StoreDirectLinkBridge1977.bind(directBle)
        scanner = BleEmployeeScanner(this, { payload, rssi, device -> handleBlePayload(payload, rssi, "Bluetooth", device); Unit }) { msg ->
            runOnUiThread { if (::status.isInitialized) status.text = msg }
        }
        networkListener = NetworkPresenceListener(
            { payload, rssi -> handleBlePayload(payload, rssi, "Wi‑Fi/Hotspot") },
            { frame, rssi -> handleLanConfirm(frame, rssi) }
        ) { msg ->
            runOnUiThread { if (::status.isInitialized) status.text = msg }
        }
        DistributionUpdateManager.check(this, repo.serverUrl.ifBlank { DistributionUpdateManager.DEFAULT_SERVER }, "store")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingFaceEmployeeId?.let { outState.putString("pendingFaceEmployeeId", it) }
        outState.putBoolean("pendingFaceRecognition", pendingFaceRecognition)
        pendingFaceCapturePath?.let { outState.putString("pendingFaceCapturePath", it) }
        pendingLivenessEmployeeId?.let { outState.putString("pendingLivenessEmployeeId", it) }
        pendingLivenessFirstTemplate?.let { outState.putString("pendingLivenessFirstTemplate", it) }
        outState.putFloat("pendingLivenessFirstScore", pendingLivenessFirstScore)
        pendingVoiceEmployeeId?.let { outState.putString("pendingVoiceEmployeeId", it) }
        pendingVoiceChallenge?.let { outState.putString("pendingVoiceChallenge", it) }
        outState.putFloat("pendingVoicePrintScore", pendingVoicePrintScore)
        pendingFaceAttendanceAction?.let { outState.putString("pendingFaceAttendanceAction", it.name) }
        pendingVoiceAttendanceAction?.let { outState.putString("pendingVoiceAttendanceAction", it.name) }
        pendingVoiceEnrollmentEmployeeId?.let { outState.putString("pendingVoiceEnrollmentEmployeeId", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        installConnectionRecovery1927()
        autoSyncIfReady()
        repo.allowPatternFallback = false
        repo.allowPinFallback = false
        // Legacy PIN/Pattern data is kept readable for backward compatibility, but these methods
        // remain disabled and are never offered by the current attendance UI.
        super.onResume()
        DistributionUpdateManager.resume(this)
        nearbyRefreshHandler.removeCallbacks(nearbyRefreshTask)
        nearbyRefreshHandler.post(nearbyRefreshTask)
        LateAlertScheduler.sync(this, repo)
        if (!repo.isCentralActivationActive()) {
            runCatching { scanner.stop() }
            runCatching { networkListener.stop() }
            buildActivationLockUi()
            if (repo.hasCentralCredentials() && repo.serverUrl.isNotBlank()) validateCentralActivation(silent = true)
            else if (!recoveryAttempted && repo.serverUrl.isNotBlank()) {
                recoveryAttempted = true
                recoverCentralActivation(silent = true)
            }
            return
        }
        if (employeeManagerMode) {
            buildEmployeeManagerUi()
            refreshDashboard()
            validateCentralActivation(silent = true)
            return
        }
        buildElegantUi()
        refreshDashboard()
        ensurePresenceDiscoveryRunning()
        validateCentralActivation(silent = true)
        autoSyncIfReady()
    }

    private fun ensurePresenceDiscoveryRunning() {
        if (!repo.isCentralActivationActive() || !repo.allowEmployeeCompanion ||
            repo.employees().none { it.active && it.companionEnabled }) return
        if (!repo.autoScan) repo.autoScan = true
        if (!scanner.isScanning()) runCatching { scanner.start() }.onFailure {
            if (::status.isInitialized) status.text = "تعذر تشغيل BLE: ${it.message ?: "خطأ"}"
        }
        if (!networkListener.isRunning()) runCatching { networkListener.start() }.onFailure {
            if (::status.isInitialized) status.text = "تعذر تشغيل اكتشاف Wi‑Fi: ${it.message ?: "خطأ"}"
        }
    }

    private fun pollServerPresenceIfDue() {
        val now = System.currentTimeMillis()
        if (serverPresencePollInFlight || now - lastServerPresencePollAt < 10_000L) return
        if (!repo.hasCentralCredentials() || repo.serverUrl.isBlank()) return
        val employees = repo.employees().filter { it.active && it.companionEnabled }
        if (employees.isEmpty()) return
        lastServerPresencePollAt = now
        serverPresencePollInFlight = true
        Thread {
            val identity = DeviceIdentity(this@MainActivity)
            try {
                employees.forEach { employee ->
                    val link = CentralServerClient.employeeLinkStatus(
                        repo.serverUrl, repo.centralAccessToken, repo.storeId, identity, employee.employeeId
                    ).getOrNull() ?: return@forEach
                    val seenAt = link.lastSeenAt
                    if (link.linked && seenAt > 0L && System.currentTimeMillis() - seenAt <= 30_000L) {
                        serverPresenceAt[employee.employeeId] = seenAt
                        val previous = nearby[employee.employeeId]
                        val channels = previous?.channelTimes.orEmpty().toMutableMap().apply { put("Server • Heartbeat", seenAt) }
                        nearby[employee.employeeId] = NearbyPhone(
                            maxOf(previous?.seenAt ?: 0L, seenAt),
                            previous?.rssi ?: -127, channels, previous?.deviceName.orEmpty(), previous?.gpsInsideAt ?: 0L
                        )
                        repo.markCompanionLinked(employee.employeeId, "Server • Heartbeat", seenAt)
                    } else if (seenAt <= 0L || System.currentTimeMillis() - seenAt > 45_000L) {
                        serverPresenceAt.remove(employee.employeeId)
                    }
                    applyServerGpsObservation(employee, link)
                }
            } finally {
                serverPresencePollInFlight = false
                runOnUiThread { if (!isFinishing) refreshDashboard() }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun applyServerGpsObservation(employee: PairedEmployee, link: CentralServerClient.EmployeeLinkResult) {
        if (!repo.gpsRecognitionEnabled || link.gpsSeenAt <= 0L) return
        val state = link.gpsState.uppercase(Locale.US).let { if (it in setOf("INSIDE", "NEAR", "OUTSIDE", "UNKNOWN")) it else "UNKNOWN" }
        val employeeId = employee.employeeId
        serverGpsState[employeeId] = state
        serverGpsDistance[employeeId] = link.gpsDistanceMeters
        serverGpsAccuracy[employeeId] = link.gpsAccuracyMeters
        serverGpsSeenAt[employeeId] = link.gpsSeenAt

        val recognized = state == "INSIDE" || state == "NEAR"
        val previous = nearby[employeeId]
        if (recognized && System.currentTimeMillis() - link.gpsSeenAt <= GPS_RECOGNITION_FRESH_MILLIS) {
            val channels = previous?.channelTimes.orEmpty().toMutableMap().apply { put("GPS • تعرّف", link.gpsSeenAt) }
            nearby[employeeId] = NearbyPhone(
                maxOf(previous?.seenAt ?: 0L, link.gpsSeenAt),
                previous?.rssi ?: -127, channels, previous?.deviceName.orEmpty(), link.gpsSeenAt
            )
        }

        val gpsPrefs = getSharedPreferences("gps_owner_recognition_state", MODE_PRIVATE)
        val key = "state_$employeeId"
        val oldState = gpsPrefs.getString(key, "UNKNOWN") ?: "UNKNOWN"
        if (oldState != state) {
            gpsPrefs.edit().putString(key, state).apply()
            val detail = "${gpsStateLabel(state)} • ${gpsDistanceLabel(employeeId)}"
            repo.addPresenceEvent(PresenceEvent(
                employeeId = employeeId, employeeName = employee.displayName, timestampEpochMillis = link.gpsSeenAt,
                channel = "GPS • مراقبة", rssi = -127, details = "$detail • ليس إثبات حضور"
            ))
            if (recognized && repo.gpsNotifyOwnerEnabled) notifyOwnerGpsRecognition(employee, detail)
        }
    }

    private fun gpsStateLabel(state: String): String = when (state.uppercase(Locale.US)) {
        "INSIDE" -> "داخل نطاق المحل"
        "NEAR" -> "قريب من نطاق المحل"
        "OUTSIDE" -> "خارج نطاق المحل"
        else -> "GPS غير مؤكد"
    }

    private fun gpsDistanceLabel(employeeId: String): String {
        val distance = serverGpsDistance[employeeId] ?: -1
        val accuracy = serverGpsAccuracy[employeeId] ?: -1
        return buildString {
            if (distance >= 0) append("${distance}م") else append("المسافة غير متاحة")
            if (accuracy >= 0) append(" • دقة ±${accuracy}م")
        }
    }

    private fun gpsRecognitionLine(employeeId: String, now: Long = System.currentTimeMillis()): String {
        val seen = serverGpsSeenAt[employeeId] ?: return "GPS: لا توجد قراءة"
        val state = serverGpsState[employeeId] ?: "UNKNOWN"
        val age = ((now - seen).coerceAtLeast(0L) / 1000L)
        return "GPS: ${gpsStateLabel(state)} • ${gpsDistanceLabel(employeeId)} • منذ ${age}ث • مراقبة فقط"
    }

    private fun isGpsRecognizedFresh(employeeId: String, now: Long = System.currentTimeMillis()): Boolean {
        val state = serverGpsState[employeeId] ?: return false
        val seen = serverGpsSeenAt[employeeId] ?: return false
        return (state == "INSIDE" || state == "NEAR") && now - seen <= GPS_RECOGNITION_FRESH_MILLIS
    }

    private fun notifyOwnerGpsRecognition(employee: PairedEmployee, detail: String) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                GPS_RECOGNITION_CHANNEL, "التعرف على الموظفين عبر GPS", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "تنبيه صاحب المحل عند رصد هاتف موظف قرب المحل؛ لا يسجل حضورًا" })
        }
        val open = PendingIntent.getActivity(
            this, employee.employeeId.hashCode() xor 0x4750, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, GPS_RECOGNITION_CHANNEL)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        @Suppress("DEPRECATION")
        val notification = builder.setSmallIcon(R.drawable.ic_attend_pro)
            .setContentTitle("تم التعرف عبر GPS — ${employee.displayName}")
            .setContentText("$detail • للمراقبة فقط ولا يُسجل حضورًا")
            .setAutoCancel(true).setContentIntent(open).setPriority(Notification.PRIORITY_DEFAULT).build()
        manager.notify(47000 + (employee.employeeId.hashCode() and 0x7ff), notification)
    }

    private fun isServerPresenceConnected(employeeId: String, now: Long = System.currentTimeMillis()): Boolean =
        serverPresenceAt[employeeId]?.let { it > 0L && now - it <= 30_000L } == true

    private fun buildActivationLockUi() {
        window.statusBarColor = p.bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 16), UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 16), UiKit.dp(this@MainActivity, 30))
            setBackgroundColor(p.bg)
        }

        val hero = UiKit.heroCard(this, p)
        hero.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_attend_pro)
            layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@MainActivity, 72), UiKit.dp(this@MainActivity, 72)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        hero.addView(UiKit.title(this, p, "ATTEND PRO", 28f).apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.WHITE) })
        hero.addView(UiKit.subtitle(this, p, "تفعيل مركزي محمي • الإصدار ${attendProVersionName()}").apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.argb(225,255,255,255)) })
        root.addView(hero)

        val lock = UiKit.card(this, p)
        lock.addView(UiKit.statusBadge(this, p, "المحل غير مفعّل", false))
        lock.addView(UiKit.title(this, p, "يتطلب موافقة إدارة النظام", 23f).apply { gravity = Gravity.CENTER })
        val reason = when {
            repo.centralClockRollbackDetected() -> "تم اكتشاف اختلاف غير آمن في ساعة الجهاز. صحح التاريخ والوقت ثم أعد التحقق من الخادم."
            repo.centralServerStatus == "SUSPENDED" -> "تم تعليق هذا المحل مركزيًا من إدارة النظام. لا يمكن تسجيل حضور أو إدارة الموظفين حتى إعادة التفعيل."
            repo.centralServerStatus == "EXPIRED" -> "انتهت مدة التفعيل المركزي لهذا المحل. يلزم تجديدها من إدارة النظام."
            repo.hasCentralCredentials() && repo.centralLeaseUntil <= System.currentTimeMillis() -> "انتهت مهلة العمل دون اتصال. يلزم الاتصال بالخادم للتحقق من صلاحية المحل."
            repo.centralActivationRequestId.isNotBlank() -> "تم إرسال طلب التفعيل وهو بانتظار اعتماد إدارة النظام."
            else -> "يجري التطبيق تلقائيًا البحث عن تفعيل سابق لنفس الجهاز. إذا تعرّف الخادم عليه فسيستعيد التفعيل مباشرة، ما لم تكن الاستعادة موقوفة من إدارة النظام."
        }
        lock.addView(UiKit.subtitle(this, p, reason).apply { gravity = Gravity.CENTER; setPadding(0, UiKit.dp(this@MainActivity, 8), 0, 0) })
        root.addView(lock)

        val identity = UiKit.card(this, p)
        identity.addView(UiKit.sectionLabel(this, p, "بيانات طلب التفعيل"))
        identity.addView(UiKit.title(this, p, repo.storeName, 20f))
        identity.addView(UiKit.subtitle(this, p, "الفرع: ${repo.branchId}\nمعرف الجهاز: ${repo.storeId}\nالخادم: ${repo.serverUrl.ifBlank { "غير محدد" }}"))
        identity.addView(UiKit.button(this, p, "إعداد بيانات المحل والخادم", false).apply { setOnClickListener { editActivationSetup() } })
        root.addView(identity)

        val activation = UiKit.card(this, p)
        activation.addView(UiKit.sectionLabel(this, p, "التفعيل المركزي"))
        if (repo.centralActivationRequestId.isBlank() && !repo.hasCentralCredentials()) {
            activation.addView(UiKit.button(this, p, "استعادة تفعيل هذا الجهاز", false).apply { setOnClickListener { recoverCentralActivation(silent = false) } })
            activation.addView(UiKit.button(this, p, "استعادة بتصريح صاحب النظام", false).apply { setOnClickListener { showApprovedRecoveryDialog() } })
            activation.addView(UiKit.button(this, p, "تسجيل محل جديد مباشرة — تجربة 3 أيام").apply {
                setOnClickListener { requestCentralActivation(selfRegister = true) }
            })
            activation.addView(UiKit.button(this, p, "إرسال طلب التفعيل لإدارة النظام").apply { setOnClickListener { requestCentralActivation() } })
        } else if (repo.centralActivationRequestId.isNotBlank()) {
            activation.addView(UiKit.button(this, p, "فحص موافقة إدارة النظام").apply { setOnClickListener { checkCentralActivation() } })
            activation.addView(UiKit.subtitle(this, p, "رقم الطلب: ${repo.centralActivationRequestId}"))
        }
        if (repo.hasCentralCredentials()) {
            activation.addView(UiKit.button(this, p, "التحقق من صلاحية المحل الآن").apply { setOnClickListener { validateCentralActivation(silent = false) } })
        }
        status = TextView(this).apply { text = "بانتظار التفعيل المركزي"; textSize = 15f; setTextColor(p.muted); gravity = Gravity.CENTER; setPadding(0, UiKit.dp(this@MainActivity, 8), 0, 0) }
        activation.addView(status)
        activation.addView(UiKit.button(this, p, "فحص تحديث التطبيق", false).apply {
            setOnClickListener { DistributionUpdateManager.check(this@MainActivity, repo.serverUrl.ifBlank { DistributionUpdateManager.DEFAULT_SERVER }, "store", manual = true) }
        })
        root.addView(activation)

        val receiverCard = UiKit.card(this, p, 13)
        receiverCard.addView(UiKit.sectionLabel(this, p, "هاتف المراقبة"))
        receiverCard.addView(UiKit.subtitle(this, p, "يمكن استخدام هذا الهاتف لاستلام تقارير محل آخر دون تفعيله كجهاز محل."))
        receiverCard.addView(UiKit.button(this, p, "فتح استلام التقارير", false).apply { setOnClickListener { startActivity(Intent(this@MainActivity, ReportReceiverActivity::class.java)) } })
        root.addView(receiverCard)

        val advancedAdmin = TextView(this).apply {
            text = "إدارة النظام"; textSize = 11f; gravity = Gravity.CENTER; setTextColor(p.muted)
            setPadding(0, UiKit.dp(this@MainActivity, 8), 0, UiKit.dp(this@MainActivity, 8))
            setOnClickListener { startActivity(Intent(this@MainActivity, SystemSettingsActivity::class.java).putExtra("OWNER_ONLY_1978", true)) }
        }
        root.addView(advancedAdmin)

        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
    }

    private fun editActivationSetup() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(UiKit.dp(this@MainActivity, 20), UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 20), 0) }
        val name = UiKit.field(this, p, "اسم المحل").apply { setText(if (repo.storeName == "جهاز المحل") "" else repo.storeName) }
        val branch = UiKit.field(this, p, "اسم / رمز الفرع").apply { setText(repo.branchId) }
        val server = UiKit.field(this, p, "https://your-server.example.com").apply { setText(repo.serverUrl) }
        box.addView(name); box.addView(branch); box.addView(server)
        val d = AlertDialog.Builder(this).setTitle("إعداد التفعيل المركزي").setView(box).setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val n = name.text.toString().trim(); val b = branch.text.toString().trim(); val u = server.text.toString().trim()
            if (n.length < 2) { name.error = "أدخل اسم المحل"; return@setOnClickListener }
            if (b.isBlank()) { branch.error = "أدخل الفرع"; return@setOnClickListener }
            if (!u.startsWith("https://")) { server.error = "الخادم يجب أن يستخدم HTTPS"; return@setOnClickListener }
            repo.storeName = n; repo.branchId = b; repo.serverUrl = u
            d.dismiss(); buildActivationLockUi()
        } }
        d.show()
    }

    private fun validateCentralActivation(silent: Boolean) {
        if (repo.serverUrl.isBlank() || !repo.hasCentralCredentials()) { if (!silent) info("التفعيل المركزي", "بيانات الخادم أو الترخيص المركزي غير مكتملة."); return }
        if (::status.isInitialized && !silent) status.text = "جاري التحقق الآمن من الخادم..."
        Thread {
            val identity = DeviceIdentity(this)
            val result = CentralServerClient.validateStore(repo.serverUrl, repo.centralAccessToken, repo.storeId, identity)
            if (result.getOrNull()?.status.equals("ACTIVE", true)) {
                CentralServerClient.enrollRecovery(repo.serverUrl, repo.centralAccessToken, repo.storeId, identity)
            }
            runOnUiThread {
                if (result.isFailure) {
                    if (!silent && ::status.isInitialized) status.text = "تعذر التحقق: ${result.exceptionOrNull()?.message ?: "خطأ اتصال"}"
                    return@runOnUiThread
                }
                val v = result.getOrThrow()
                if (v.status.equals("ACTIVE", true)) {
                    val wasLocked = !repo.isCentralActivationActive()
                    repo.refreshCentralLease("ACTIVE", v.expiresAt, v.maxEmployees, v.leaseUntil, v.serverTime)
                    if (!repo.isCentralActivationActive()) {
                        repo.markCentralInactive("VALIDATION_REQUIRED", v.serverTime)
                        buildActivationLockUi()
                        if (!silent) info("التفعيل المركزي", "استجابة الخادم لم تمنح مهلة تشغيل صالحة. حدّث الخادم المركزي إلى الإصدار المتوافق مع 1.9.2.")
                    } else if (wasLocked || !silent) {
                        employeeManagerMode = false
                        buildElegantUi(); refreshDashboard()
                        if (!silent) info("التفعيل المركزي", "تم التحقق من صلاحية المحل بنجاح.")
                    }
                } else {
                    repo.markCentralInactive(v.status, v.serverTime)
                    runCatching { scanner.stop() }
                    runCatching { networkListener.stop() }
                    buildActivationLockUi()
                    if (!silent) info("تم إيقاف التشغيل", v.reason.ifBlank { "حالة المحل على الخادم: ${v.status}" })
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun buildUi() {
        window.statusBarColor = p.bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 14), UiKit.dp(this@MainActivity, 14), UiKit.dp(this@MainActivity, 14), UiKit.dp(this@MainActivity, 30))
            setBackgroundColor(p.bg)
        }

        val header = UiKit.heroCard(this, p, 16).apply { gravity = Gravity.CENTER_HORIZONTAL }
        header.addView(TextView(this).apply {
            text="⋮"; textSize=28f; gravity=Gravity.CENTER; setTextColor(android.graphics.Color.WHITE); contentDescription="القائمة"
            layoutParams=LinearLayout.LayoutParams(UiKit.dp(this@MainActivity,48),UiKit.dp(this@MainActivity,44)).apply{gravity=Gravity.END}
            setOnClickListener{showStoreMainMenu1976()}
        })
        header.addView(homeTemplateChip1978().apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity=Gravity.START } })
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_attend_pro)
            layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@MainActivity, 52), UiKit.dp(this@MainActivity, 52))
        })
        header.addView(UiKit.title(this, p, "ATTEND PRO", 25f).apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.WHITE) })
        header.addView(UiKit.subtitle(this, p, "نظام حضور المحل • الإصدار ${attendProVersionName()}").apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.argb(225,255,255,255)) })
        storeSummary = UiKit.subtitle(this, p, storeSummaryText()).apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.argb(235,255,255,255)); setPadding(0, UiKit.dp(this@MainActivity, 5), 0, 0) }
        header.addView(storeSummary)
        root.addView(header)
        addStoreTabs1978(root)

        if (!repo.isStoreProfileComplete) {
            val setup = UiKit.card(this, p, 13)
            setup.addView(UiKit.sectionLabel(this, p, "إكمال إعداد المحل"))
            setup.addView(UiKit.subtitle(this, p, "أكمل معلومات المحل من «إدارة المحل» قبل التشغيل الفعلي.").apply { gravity = Gravity.CENTER })
            setup.addView(UiKit.button(this, p, "فتح إدارة المحل", false).apply { setOnClickListener { startActivity(Intent(this@MainActivity, StoreSettingsActivity::class.java)) } })
            root.addView(setup)
        }

        status = TextView(this).apply { text = "النظام جاهز"; textSize = 14f; setTextColor(p.muted); gravity = Gravity.CENTER }

        val connectionCard = UiKit.card(this, p, 14).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { showConnectionCenter() }
        }
        val connectionTitle = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        connectionTitle.addView(UiKit.title(this, p, "حالة الاتصال والأجهزة", 19f).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        connectionTitle.addView(UiKit.statusBadge(this, p, "● مباشر", true).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) })
        connectionCard.addView(connectionTitle)
        connectionSummaryView = UiKit.subtitle(this, p, "جاري فحص الأجهزة المتصلة…").apply { textSize = 15f; setTextColor(p.text); setPadding(0, UiKit.dp(this@MainActivity, 6), 0, UiKit.dp(this@MainActivity, 5)) }
        connectionCard.addView(connectionSummaryView)
        connectionCard.addView(UiKit.subtitle(this, p, "اضغط لعرض أسماء الأجهزة ونوع الاتصال وآخر وقت ظهور"))
        root.addView(connectionCard)

        val attendanceHub = UiKit.card(this, p, 18).apply { gravity = Gravity.CENTER_HORIZONTAL }
        attendanceHub.addView(UiKit.title(this, p, "تسجيل الحضور والانصراف", 22f).apply { gravity = Gravity.CENTER })
        attendanceHub.addView(UiKit.subtitle(this, p, "اضغط على البصمة واختر طريقة التحقق المعتمدة للموظف").apply { gravity = Gravity.CENTER })
        val attendanceButton = TextView(this).apply {
            text = "◎\nتسجيل الحضور"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(p.primary)
                setStroke(UiKit.dp(this@MainActivity, 4), p.accent)
            }
            layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@MainActivity, 190), UiKit.dp(this@MainActivity, 190)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = UiKit.dp(this@MainActivity, 14)
                bottomMargin = UiKit.dp(this@MainActivity, 14)
            }
            setOnClickListener { showAttendanceMethods() }
        }
        attendanceHub.addView(attendanceButton)
        attendanceHub.addView(UiKit.subtitle(this, p, "وجه • صوت • بصمة الهاتف • GPS • رمز شخصي • جهاز بصمة").apply { gravity = Gravity.CENTER })
        val attendanceActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        attendanceActions.addView(UiKit.button(this, p, "كل طرق التحقق", false).apply { layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(this@MainActivity, 50), 1f).apply { marginEnd = UiKit.dp(this@MainActivity, 4) }; setOnClickListener { showAttendanceMethods() } })
        attendanceActions.addView(UiKit.button(this, p, "إثبات الوجود", false).apply { layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(this@MainActivity, 50), 1f).apply { marginStart = UiKit.dp(this@MainActivity, 4) }; setOnClickListener { showPresenceChallenge() } })
        attendanceHub.addView(attendanceActions)
        root.addView(attendanceHub)

        val dash = UiKit.card(this, p, 14)
        dash.addView(UiKit.sectionLabel(this, p, "ملخص اليوم"))
        counts = TextView(this).apply { textSize = 17f; setTextColor(p.text); setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f) }
        dash.addView(counts)
        root.addView(dash)

        val nearbyCard = UiKit.card(this, p, 14)
        nearbyCard.addView(UiKit.sectionLabel(this, p, "الأجهزة الموجودة الآن"))
        nearbyView = TextView(this).apply { textSize = 15f; setTextColor(p.text); setPadding(0, UiKit.dp(this@MainActivity, 10), 0, UiKit.dp(this@MainActivity, 8)); gravity = Gravity.CENTER }
        nearbyCard.addView(nearbyView)
        nearbyCard.addView(UiKit.button(this, p, "فتح مركز الاتصال", false).apply { setOnClickListener { showConnectionCenter() } })
        root.addView(nearbyCard)

        val attendanceListCard = UiKit.card(this, p, 14)
        attendanceListCard.addView(UiKit.sectionLabel(this, p, "آخر عمليات الحضور اليوم"))
        attendanceTodayView = TextView(this).apply {
            textSize = 15f; setTextColor(p.text); setPadding(0, UiKit.dp(this@MainActivity, 10), 0, UiKit.dp(this@MainActivity, 8)); gravity = Gravity.START
        }
        attendanceListCard.addView(attendanceTodayView)
        attendanceListCard.addView(UiKit.button(this, p, "عرض سجل الحضور والاتصال", false).apply { setOnClickListener { showConnectionAttendanceHistory() } })
        root.addView(attendanceListCard)

        val services = UiKit.card(this, p, 14)
        services.addView(UiKit.sectionLabel(this, p, "الخدمات والإدارة"))
        fun serviceRow(first: Pair<String, () -> Unit>, second: Pair<String, () -> Unit>) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
            listOf(first, second).forEach { item -> row.addView(UiKit.button(this, p, item.first, false).apply { layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(this@MainActivity, 54), 1f).apply { marginStart = UiKit.dp(this@MainActivity, 4); marginEnd = UiKit.dp(this@MainActivity, 4); bottomMargin = UiKit.dp(this@MainActivity, 8) }; setOnClickListener { item.second() } }) }
            services.addView(row)
        }
        serviceRow("الموظفون والإعدادات" to { startActivity(Intent(this, StoreSettingsActivity::class.java)) }, "التقارير" to { startActivity(Intent(this, ReportsActivity::class.java)) })
        serviceRow("استلام التقارير" to { startActivity(Intent(this, ReportReceiverActivity::class.java)) }, "بيانات التفعيل" to { showLicenseDetails() })
        serviceRow("فحص التحديث" to { DistributionUpdateManager.check(this, repo.serverUrl.ifBlank { DistributionUpdateManager.DEFAULT_SERVER }, "store", manual = true) }, "إثبات وجود" to { showPresenceChallenge() })
        services.addView(status)
        root.addView(services)

        val timelineCard = UiKit.card(this, p, 12)
        timelineCard.addView(UiKit.sectionLabel(this, p, "آخر نشاط للنظام"))
        activityTimelineView = TextView(this).apply {
            textSize = 14f; setTextColor(p.text); setPadding(0, UiKit.dp(this@MainActivity, 10), 0, UiKit.dp(this@MainActivity, 8)); gravity = Gravity.START
        }
        timelineCard.addView(activityTimelineView)
        root.addView(timelineCard)

        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
    }

    private fun storeHomeTemplate1978(): String = getSharedPreferences("attend_home_template_1978", MODE_PRIVATE)
        .getString("store_template", "MAIN") ?: "MAIN"

    private fun setStoreHomeTemplate1978(value: String) {
        getSharedPreferences("attend_home_template_1978", MODE_PRIVATE).edit().putString("store_template", value).apply()
    }

    private fun storeTemplateTitle1978(value: String = storeHomeTemplate1978()): String = when (value) {
        "SECTIONS" -> "الأقسام"
        "CLASSIC" -> "الكلاسيكي"
        else -> "الرئيسي"
    }

    private fun buildElegantUi() {
        when (storeHomeTemplate1978()) {
            "SECTIONS" -> buildStoreSectionsTemplate1978()
            "CLASSIC" -> buildUi()
            else -> buildStoreMainTemplate1978()
        }
    }

    private fun homeTemplateChip1978(): TextView = TextView(this).apply {
        text = "النموذج: ${storeTemplateTitle1978()} ▾"
        textSize = 12.5f
        gravity = Gravity.CENTER
        setTextColor(android.graphics.Color.WHITE)
        setPadding(UiKit.dp(this@MainActivity, 12), UiKit.dp(this@MainActivity, 7), UiKit.dp(this@MainActivity, 12), UiKit.dp(this@MainActivity, 7))
        background = GradientDrawable().apply {
            cornerRadius = UiKit.dp(this@MainActivity, 18).toFloat()
            setColor(android.graphics.Color.argb(38,255,255,255))
            setStroke(UiKit.dp(this@MainActivity, 1), android.graphics.Color.argb(110,255,255,255))
        }
        setOnClickListener { showStoreHomeTemplatePicker1978() }
    }

    private fun showStoreHomeTemplatePicker1978() {
        val ids = arrayOf("MAIN", "SECTIONS", "CLASSIC")
        val labels = arrayOf(
            "الرئيسي — لوحة الحضور اليومية المتوازنة",
            "الأقسام — وصول سريع للخدمات على شكل أقسام",
            "الكلاسيكي — عرض تفصيلي تقليدي"
        )
        val current = ids.indexOf(storeHomeTemplate1978()).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("نمط الشاشة الرئيسية")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                setStoreHomeTemplate1978(ids[which]); dialog.dismiss(); buildElegantUi(); refreshDashboard()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun addStoreTabs1978(root: LinearLayout) {
        val box = UiKit.card(this, p, 7)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; gravity = Gravity.CENTER }
        fun tab(label: String, selected: Boolean = false, action: () -> Unit): TextView = TextView(this).apply {
            text = label; textSize = 12.2f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (selected) android.graphics.Color.WHITE else p.text)
            setPadding(UiKit.dp(this@MainActivity, 5), UiKit.dp(this@MainActivity, 10), UiKit.dp(this@MainActivity, 5), UiKit.dp(this@MainActivity, 10))
            background = GradientDrawable().apply { cornerRadius=UiKit.dp(this@MainActivity,14).toFloat(); setColor(if(selected) p.primary else p.surface2) }
            layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(this@MainActivity, 42), 1f).apply { marginStart=UiKit.dp(this@MainActivity,2); marginEnd=UiKit.dp(this@MainActivity,2) }
            setOnClickListener { action() }
        }
        row.addView(tab("الرئيسية", true) { })
        row.addView(tab("الحضور") { showAttendanceMethods("تسجيل الحضور والانصراف") })
        row.addView(tab("الاتصال") { showConnectionCenter() })
        row.addView(tab("الإدارة") { requireStoreOwner("إدارة المحل") { showStoreOwnerHub() } })
        box.addView(row); root.addView(box)
    }

    private fun buildStoreSectionsTemplate1978() {
        window.statusBarColor = p.bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity,12),UiKit.dp(this@MainActivity,12),UiKit.dp(this@MainActivity,12),UiKit.dp(this@MainActivity,30)); setBackgroundColor(p.bg)
        }
        val header = UiKit.heroCard(this,p,12)
        header.addView(TextView(this).apply {
            text="⋮"; textSize=28f; gravity=Gravity.CENTER; setTextColor(android.graphics.Color.WHITE); contentDescription="القائمة"
            layoutParams=LinearLayout.LayoutParams(UiKit.dp(this@MainActivity,48),UiKit.dp(this@MainActivity,44)).apply{gravity=Gravity.END}
            setOnClickListener{showStoreMainMenu1976()}
        })
        header.addView(homeTemplateChip1978().apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity=Gravity.START } })
        header.addView(UiKit.title(this,p,"ATTEND PRO",22f).apply{gravity=Gravity.CENTER;setTextColor(android.graphics.Color.WHITE)})
        storeSummary=UiKit.subtitle(this,p,storeSummaryText()).apply{gravity=Gravity.CENTER;setTextColor(android.graphics.Color.WHITE)}
        header.addView(storeSummary)
        header.addView(UiKit.subtitle(this,p,"لوحة الأقسام • ${attendProVersionName()}").apply{gravity=Gravity.CENTER;setTextColor(android.graphics.Color.argb(220,255,255,255))})
        root.addView(header)
        addStoreTabs1978(root)

        status=TextView(this).apply{text="النظام جاهز";textSize=12.5f;setTextColor(p.muted);gravity=Gravity.CENTER}
        counts=TextView(this).apply{textSize=15.5f;setTextColor(p.text);setTypeface(typeface,Typeface.BOLD);gravity=Gravity.CENTER;setLineSpacing(0f,1.12f)}
        val today=UiKit.card(this,p,10); today.addView(UiKit.sectionLabel(this,p,"ملخص اليوم")); today.addView(counts); root.addView(today)

        fun sectionTile(title:String,subtitle:String,action:()->Unit)=UiKit.card(this,p,11).apply{
            addView(UiKit.title(this@MainActivity,p,title,16.5f).apply{gravity=Gravity.CENTER})
            addView(UiKit.subtitle(this@MainActivity,p,subtitle).apply{gravity=Gravity.CENTER;maxLines=3})
            UiKit.makeInteractive(this,this@MainActivity,p);setOnClickListener{action()}
        }
        linkedEmployeesSummaryView=UiKit.subtitle(this,p,"جاري تحميل الحضور…")
        connectionSummaryView=UiKit.subtitle(this,p,"جاري فحص الاتصال…")
        val row1=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;gravity=Gravity.TOP}
        val a=sectionTile("الحضور الآن","الموظفون الموجودون وحالتهم"){showLiveAttendanceNow()}; a.addView(linkedEmployeesSummaryView.apply{gravity=Gravity.CENTER})
        val b=sectionTile("الأجهزة والاتصال","Bluetooth • Wi‑Fi • الخادم"){showConnectionCenter()}; b.addView(connectionSummaryView.apply{gravity=Gravity.CENTER})
        a.layoutParams=LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply{marginEnd=UiKit.dp(this@MainActivity,4)}
        b.layoutParams=LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply{marginStart=UiKit.dp(this@MainActivity,4)}
        row1.addView(a);row1.addView(b);root.addView(row1)

        val operations=UiKit.card(this,p,10); operations.addView(UiKit.sectionLabel(this,p,"الأقسام"))
        val row2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
        row2.addView(UiKit.button(this,p,"الموظفون",false).apply{layoutParams=LinearLayout.LayoutParams(0,UiKit.dp(this@MainActivity,50),1f).apply{marginEnd=4};setOnClickListener{requireStoreOwner("الموظفون"){startActivity(Intent(this@MainActivity,StoreSettingsActivity::class.java))}}})
        row2.addView(UiKit.button(this,p,"الرسائل",false).apply{layoutParams=LinearLayout.LayoutParams(0,UiKit.dp(this@MainActivity,50),1f).apply{marginStart=4};setOnClickListener{startActivity(Intent(this@MainActivity,StoreMessages1975Activity::class.java))}})
        operations.addView(row2)
        val row3=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
        row3.addView(UiKit.button(this,p,"التقارير",false).apply{layoutParams=LinearLayout.LayoutParams(0,UiKit.dp(this@MainActivity,50),1f).apply{marginEnd=4};setOnClickListener{startActivity(Intent(this@MainActivity,ReportsActivity::class.java))}})
        row3.addView(UiKit.button(this,p,"إدارة المحل",false).apply{layoutParams=LinearLayout.LayoutParams(0,UiKit.dp(this@MainActivity,50),1f).apply{marginStart=4};setOnClickListener{requireStoreOwner("إدارة المحل"){showStoreOwnerHub()}}})
        operations.addView(row3);root.addView(operations)

        recentAttendanceSummaryView=UiKit.subtitle(this,p,"لا توجد عملية اليوم").apply{gravity=Gravity.CENTER}
        val recent=UiKit.card(this,p,8);recent.addView(UiKit.sectionLabel(this,p,"آخر حركة"));recent.addView(recentAttendanceSummaryView);UiKit.makeInteractive(recent,this,p);recent.setOnClickListener{showConnectionAttendanceHistory()};root.addView(recent)
        root.addView(UiKit.card(this,p,7).apply{addView(status)})
        setContentView(ScrollView(this).apply{isFillViewport=true;setBackgroundColor(p.bg);addView(root)})
    }

    private fun buildStoreMainTemplate1978() {
        window.statusBarColor = p.bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = if (AppLanguage.isEnglish(this@MainActivity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
            setPadding(
                UiKit.dp(this@MainActivity, 12),
                UiKit.dp(this@MainActivity, 12),
                UiKit.dp(this@MainActivity, 12),
                UiKit.dp(this@MainActivity, 30)
            )
            setBackgroundColor(p.bg)
        }

        fun compactPanel(title: String, subtitle: String, detail: TextView, action: () -> Unit): LinearLayout =
            UiKit.card(this, p, 11).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                minimumHeight = UiKit.dp(this@MainActivity, 120)
                addView(UiKit.title(this@MainActivity, p, title, 16.4f).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                })
                addView(UiKit.subtitle(this@MainActivity, p, subtitle).apply {
                    gravity = Gravity.CENTER
                    textSize = 11.6f
                    maxLines = 2
                })
                detail.gravity = Gravity.CENTER
                detail.textSize = 12.5f
                detail.maxLines = 4
                detail.setPadding(0, UiKit.dp(this@MainActivity, 7), 0, 0)
                addView(detail)
                UiKit.makeInteractive(this, this@MainActivity, p)
                setOnClickListener { action() }
            }

        val header = UiKit.heroCard(this, p, 11)
        header.addView(TextView(this).apply {
            text = "⋮"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            contentDescription = t("القائمة", "Menu")
            layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@MainActivity, 48), UiKit.dp(this@MainActivity, 44)).apply { gravity = Gravity.END }
            setOnClickListener { showStoreMainMenu1976() }
        })
        header.addView(homeTemplateChip1978().apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.START } })
        header.addView(UiKit.title(this, p, "ATTEND PRO", 21f).apply {
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
        })
        storeSummary = UiKit.subtitle(this, p, storeSummaryText()).apply {
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
        }
        header.addView(storeSummary)
        header.addView(UiKit.subtitle(this, p, "${getString(R.string.store_dashboard_title)} • ${attendProVersionName()}").apply {
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.argb(220, 255, 255, 255))
        })
        root.addView(header)
        addStoreTabs1978(root)

        status = TextView(this).apply {
            text = getString(R.string.system_ready)
            textSize = 12.6f
            setTextColor(p.muted)
            gravity = Gravity.CENTER
        }

        linkedEmployeesSummaryView = UiKit.subtitle(this, p, "جاري تحميل الحضور…")
        connectionSummaryView = UiKit.subtitle(this, p, "جاري فحص الاتصال…")
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.TOP
        }
        val presentPanel = compactPanel(
            getString(R.string.present_employees),
            getString(R.string.actual_attendance_now),
            linkedEmployeesSummaryView
        ) { showLiveAttendanceNow() }
        val connectedPanel = compactPanel(
            getString(R.string.connected_devices),
            getString(R.string.connection_channels),
            connectionSummaryView
        ) { showConnectionCenter() }
        presentPanel.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = UiKit.dp(this@MainActivity, 4)
        }
        connectedPanel.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = UiKit.dp(this@MainActivity, 4)
        }
        top.addView(presentPanel)
        top.addView(connectedPanel)
        root.addView(top)

        val attendanceCard = UiKit.card(this, p, 13).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(UiKit.title(this@MainActivity, p, getString(R.string.attendance_checkout), 17.5f).apply {
                gravity = Gravity.CENTER
            })
            addView(UiKit.subtitle(this@MainActivity, p, getString(R.string.attendance_instruction)).apply {
                gravity = Gravity.CENTER
                textSize = 11.8f
            })
            val fingerView = FingerprintActionView(this@MainActivity, p.primary).apply {
                contentDescription = getString(R.string.attendance_checkout)
                layoutParams = LinearLayout.LayoutParams(
                    UiKit.dp(this@MainActivity, 132),
                    UiKit.dp(this@MainActivity, 142)
                ).apply { gravity = Gravity.CENTER_HORIZONTAL }
                setOnClickListener { showAttendanceMethods(t("اختر طريقة التحقق", "Choose a verification method")) }
            }
            addView(fingerView)

            val attendanceActions = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.CENTER
            }
            val checkIn = UiKit.button(this@MainActivity, p, getString(R.string.check_in)).apply {
                layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(this@MainActivity, 52), 1f).apply {
                    marginEnd = UiKit.dp(this@MainActivity, 4)
                }
                setOnClickListener {
                    showAttendanceMethods(t("تسجيل حضور — اختر طريقة التحقق", "Check in — choose a verification method"), AttendanceAction.CHECK_IN)
                }
            }
            val checkOut = UiKit.button(this@MainActivity, p, getString(R.string.check_out), false).apply {
                layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(this@MainActivity, 52), 1f).apply {
                    marginStart = UiKit.dp(this@MainActivity, 4)
                }
                setOnClickListener {
                    showAttendanceMethods(t("تسجيل انصراف — اختر طريقة التحقق", "Check out — choose a verification method"), AttendanceAction.CHECK_OUT)
                }
            }
            attendanceActions.addView(checkIn)
            attendanceActions.addView(checkOut)
            addView(attendanceActions)
        }
        root.addView(attendanceCard)

        val summary = UiKit.card(this, p, 8)
        counts = TextView(this).apply {
            textSize = 13.7f
            setTextColor(p.text)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.08f)
        }
        summary.addView(counts)
        root.addView(summary)

        recentAttendanceSummaryView = UiKit.subtitle(this, p, getString(R.string.no_operation_today)).apply {
            gravity = Gravity.CENTER
            textSize = 12f
            maxLines = 3
        }

        val ownerCard = UiKit.card(this, p, 11).apply {
            gravity = Gravity.CENTER
            addView(UiKit.title(this@MainActivity, p, getString(R.string.owner_settings), 16.5f).apply {
                gravity = Gravity.CENTER
            })
            addView(UiKit.subtitle(this@MainActivity, p,
                getString(R.string.owner_settings_subtitle)).apply {
                gravity = Gravity.CENTER
                textSize = 11.8f
                maxLines = 2
            })
            UiKit.makeInteractive(this, this@MainActivity, p)
            setOnClickListener { requireStoreOwner(t("إدارة المحل", "Store Management")) { showStoreOwnerHub() } }
        }
        val lastMovement = UiKit.card(this, p, 8).apply {
            addView(UiKit.sectionLabel(this@MainActivity, p, getString(R.string.last_movement)))
            addView(recentAttendanceSummaryView)
            UiKit.makeInteractive(this, this@MainActivity, p)
            setOnClickListener { showConnectionAttendanceHistory() }
        }
        root.addView(lastMovement)

        if (!repo.hasStoreAdminPin || repo.hasActiveStoreAdminSession()) {
            root.addView(ownerCard)
            addOwnerShortcutCard(root)
        }

        val footer = UiKit.card(this, p, 7)
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
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (AppLanguage.isEnglish(this@MainActivity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 8))
        }
        items.forEach { (label, action) -> box.addView(UiKit.button(this, p, label, false).apply { setOnClickListener { action() } }) }
        AlertDialog.Builder(this).setTitle(title).setView(box).setNegativeButton(t("رجوع", "Back"), null).show()
    }

    private fun showStoreMainMenu1976() {
        showLayeredMenu1977(t("القائمة", "Menu"), listOf(
            t("الإشعارات", "Notifications") to { startActivity(Intent(this, StoreMessages1975Activity::class.java)) },
            t("نمط الشاشة الرئيسية", "Home layout") to { showStoreHomeTemplatePicker1978() },
            getString(R.string.user_guide) to { showStoreUserGuide1976() },
            getString(R.string.language) to { AppLanguage.showPicker(this) { recreate() } },
            t("الخصوصية والبيانات", "Privacy and data") to { startActivity(Intent(this, com.attendpro.core.PrivacyDataActivity::class.java)) },
            t("الإعدادات", "Settings") to { startActivity(Intent(this, StoreSettingsActivity::class.java)) },
            t("منطقة إدارة النظام", "System administration") to { startActivity(Intent(this, SystemSettingsActivity::class.java).putExtra("OWNER_ONLY_1978", true)) }
        ))
    }

    private fun showStoreUserGuide1976() {
        startActivity(Intent(this, StoreUserGuideActivity::class.java))
    }

    private fun buildEmployeeManagerUi() {
        window.statusBarColor = p.bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 16), UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 16), UiKit.dp(this@MainActivity, 28)); setBackgroundColor(p.bg)
        }
        val header = UiKit.card(this, p)
        header.addView(TextView(this).apply {
            text = "⋮"; textSize = 28f; gravity = Gravity.CENTER; setTextColor(p.text); contentDescription = "القائمة"
            layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@MainActivity, 48), UiKit.dp(this@MainActivity, 44)).apply { gravity = Gravity.END }
            setOnClickListener { showStoreMainMenu1976() }
        })
        header.addView(UiKit.sectionLabel(this, p, "إدارة المحل"))
        header.addView(UiKit.title(this, p, "إدارة الموظفين", 23f))
        header.addView(UiKit.subtitle(this, p, "ابدأ بالبيانات الأساسية. افتح إعدادات التحقق والربط فقط للموظف الذي تريد تعديله."))
        root.addView(header)

        val summaryCard = UiKit.card(this, p)
        counts = TextView(this).apply { textSize = 17f; setTextColor(p.text); setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER }
        summaryCard.addView(counts)
        root.addView(summaryCard)

        val actions = UiKit.card(this, p)
        actions.addView(UiKit.button(this, p, "＋ إضافة موظف جديد").apply { setOnClickListener { showEmployeeEditor(null) } })
        actions.addView(UiKit.button(this, p, "عرض الموظفين وتعديلهم", false).apply { setOnClickListener { showEmployeesDialog() } })
        actions.addView(UiKit.button(this, p, "شرح طرق التعرف", false).apply { setOnClickListener { showFaceEngineInfo() } })
        root.addView(actions)

        val state = UiKit.card(this, p, 10)
        status = TextView(this).apply { text = "جاهز لإدارة الموظفين"; textSize = 15f; setTextColor(p.text); gravity = Gravity.CENTER }
        nearbyView = TextView(this).apply { text = ""; textSize = 13f; setTextColor(p.muted); gravity = Gravity.CENTER }
        state.addView(status); state.addView(nearbyView)
        state.addView(UiKit.button(this, p, "رجوع إلى إعدادات المحل", false).apply { setOnClickListener { finish() } })
        root.addView(state)
        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
    }

    private fun autoSyncIfReady() {
        if (!repo.reportAutoSync || syncInProgress || repo.serverUrl.isBlank() || !repo.isCentralActivationActive()) return
        val pending = repo.pendingEvents()
        syncInProgress = true
        Thread {
            val identity = DeviceIdentity(this)
            val result = CentralServerClient.syncEventsCentral(repo.serverUrl, repo.centralAccessToken, repo.storeId, identity, pending.take(200))
            val pulled = if (result.isSuccess) CentralServerClient.pullEmployeeAttendance(
                repo.serverUrl, repo.centralAccessToken, repo.storeId, identity, repo.attendancePullCursor
            ) else Result.failure<CentralServerClient.AttendancePull>(result.exceptionOrNull() ?: IllegalStateException("تعذر رفع العمليات المحلية"))
            if (repo.isCentralActivationActive()) {
                CentralServerClient.confirmedTransfers(repo.serverUrl, repo.centralAccessToken, repo.storeId, identity).getOrNull()?.forEach { repo.confirmReportTransferTrusted(it) }
            }
            runOnUiThread {
                syncInProgress = false
                if (result.isSuccess) {
                    repo.markSynced(result.getOrThrow())
                    val pull = pulled.getOrNull()
                    val received = if (pull != null) repo.mergeServerEvents(pull.events) else 0
                    if (pull != null) repo.attendancePullCursor = pull.cursor
                    repo.lastSyncAt = System.currentTimeMillis()
                    repo.lastSyncMessage = "تمت المزامنة"
                    if (::status.isInitialized && (result.getOrThrow().isNotEmpty() || received > 0)) status.text =
                        "✓ تمت المزامنة${if (received > 0) " • وصل $received تسجيل من هواتف الموظفين" else ""}"
                    refreshDashboard()
                } else {
                    val message = result.exceptionOrNull()?.message ?: "خطأ"
                    repo.lastSyncMessage = "فشلت: $message"
                    if (message.contains("موقوف") || message.contains("غير صالح") || message.contains("انتهت")) {
                        repo.markCentralInactive(if (message.contains("انتهت")) "EXPIRED" else "SUSPENDED")
                        runCatching { scanner.stop() }
                        runCatching { networkListener.stop() }
                        buildActivationLockUi()
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun storeSummaryText(): String {
        val parts = mutableListOf<String>()
        parts.add(repo.storeName)
        parts.add("فرع ${repo.branchId}")
        if (repo.storePhone.isNotBlank()) parts.add(repo.storePhone)
        return parts.joinToString(" • ")
    }

    private fun showActivationCenter() {
        val items = mutableListOf<String>()
        if (repo.centralActivationRequestId.isBlank() && !repo.hasCentralCredentials()) items.add("إرسال طلب تفعيل مركزي")
        if (repo.centralActivationRequestId.isNotBlank()) items.add("فحص موافقة إدارة النظام")
        if (repo.hasCentralCredentials()) items.add("التحقق من صلاحية المحل الآن")
        if (repo.isCentralActivationActive()) items.add("عرض بيانات التفعيل المركزي")
        items.add("إعداد بيانات المحل والخادم")
        items.add("نسخ معرّف جهاز المحل")
        AlertDialog.Builder(this).setTitle("التفعيل المركزي — ATTEND PRO").setItems(items.toTypedArray()) { _, which ->
            when (items[which]) {
                "إرسال طلب تفعيل مركزي" -> requestCentralActivation()
                "فحص موافقة إدارة النظام" -> checkCentralActivation()
                "التحقق من صلاحية المحل الآن" -> validateCentralActivation(silent = false)
                "عرض بيانات التفعيل المركزي" -> showLicenseDetails()
                "إعداد بيانات المحل والخادم" -> editActivationSetup()
                "نسخ معرّف جهاز المحل" -> copyActivationText(repo.storeId, "تم نسخ معرّف الجهاز")
            }
        }.setNegativeButton("إغلاق", null).show()
    }

    private fun requestCentralActivation(selfRegister: Boolean = false) {
        if (repo.serverUrl.isBlank()) {
            val field = UiKit.field(this, p, "https://server.example.com")
            val d = AlertDialog.Builder(this).setTitle("عنوان الخادم المركزي").setMessage("أدخل رابط HTTPS الذي زودك به مدير نظام ATTEND PRO.").setView(field).setPositiveButton("متابعة", null).setNegativeButton("إلغاء", null).create()
            d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = field.text.toString().trim()
                if (!url.startsWith("https://")) { field.error = "يجب استخدام HTTPS"; return@setOnClickListener }
                repo.serverUrl = url; d.dismiss(); showActivationRequestForm(selfRegister)
            } }
            d.show(); return
        }
        showActivationRequestForm(selfRegister)
    }

    private fun showActivationRequestForm(selfRegister: Boolean = false) {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 12, 28, 4) }
        scroll.addView(box)
        val owner = UiKit.field(this, p, "اسم صاحب المحل").apply { setText(repo.storeManagerName) }
        val store = UiKit.field(this, p, "اسم المحل").apply { setText(repo.storeName.takeUnless { it == "محلي" }.orEmpty()) }
        val subscription = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("اشتراك شهري", "اشتراك سنوي", "تجربة 3 أيام"))
        }
        val phone = UiKit.field(this, p, "رقم الهاتف — اختياري").apply { setText(repo.storePhone) }
        val address = UiKit.field(this, p, "العنوان — اختياري").apply { setText(repo.storeAddress) }
        val commercial = UiKit.field(this, p, "السجل / الرقم التجاري — اختياري").apply { setText(repo.storeCommercialId) }
        val notes = UiKit.field(this, p, "معلومات إضافية — اختياري").apply { setText(repo.storeNotes); minLines = 2 }
        box.addView(UiKit.subtitle(this, p, if (selfRegister) "أنشئ حساب المحل مباشرة وابدأ تجربة 3 أيام دون انتظار موافقة. سيصل إشعار لإدارة النظام." else "أدخل البيانات الأساسية للطلب. يبدأ الاشتراك بعد اعتماد إدارة النظام."))
        listOf(owner, store).forEach { box.addView(it) }
        box.addView(UiKit.sectionLabel(this, p, "نوع الاشتراك")); box.addView(subscription)
        listOf(phone, address, commercial, notes).forEach { box.addView(it) }
        val dialog = AlertDialog.Builder(this).setTitle(if (selfRegister) "تسجيل محل جديد" else "طلب تفعيل ATTEND PRO").setView(scroll)
            .setPositiveButton(if (selfRegister) "تسجيل وبدء التجربة" else "إرسال الطلب", null).setNegativeButton("إلغاء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val ownerName = owner.text.toString().trim(); val storeName = store.text.toString().trim()
                if (ownerName.isBlank() || storeName.isBlank()) {
                    owner.error = if (ownerName.isBlank()) "اسم صاحب المحل مطلوب" else null
                    store.error = if (storeName.isBlank()) "اسم المحل مطلوب" else null
                    return@setOnClickListener
                }
                val type = arrayOf("MONTHLY", "YEARLY", "TRIAL_3_DAYS")[subscription.selectedItemPosition]
                repo.storeManagerName = ownerName; repo.storeName = storeName; repo.storePhone = phone.text.toString().trim()
                repo.storeAddress = address.text.toString().trim(); repo.storeCommercialId = commercial.text.toString().trim(); repo.storeNotes = notes.text.toString().trim()
                dialog.dismiss()
                val applicant = CentralServerClient.ActivationApplicant(ownerName, storeName, if (selfRegister) "TRIAL_3_DAYS" else type,
                    repo.storePhone, repo.storeAddress, repo.storeCommercialId, repo.storeNotes)
                if (selfRegister) submitSelfRegistration(applicant) else submitCentralActivationRequest(applicant)
            }
        }
        dialog.show()
    }

    private fun recoverCentralActivation(silent: Boolean) {
        if (::status.isInitialized) status.text = if (silent) "جاري التعرف تلقائيًا على تفعيل سابق لهذا الجهاز..." else "جاري البحث عن تفعيل سابق لهذا الجهاز..."
        Thread {
            val identity = DeviceIdentity(this)
            val result = CentralServerClient.recoverActivation(repo.serverUrl, identity)
            runOnUiThread {
                if (result.isFailure) {
                    if (silent && !repo.isCentralActivationActive() && recoveryRetryCount < 2) {
                        recoveryRetryCount += 1
                        if (::status.isInitialized) status.text = "لم يكتمل التعرف بعد • ستتم إعادة المحاولة تلقائيًا"
                        nearbyRefreshHandler.postDelayed({
                            if (!repo.isCentralActivationActive() && repo.serverUrl.isNotBlank()) recoverCentralActivation(silent = true)
                        }, if (recoveryRetryCount == 1) 5_000L else 15_000L)
                    } else if (!silent) {
                        info("استعادة التفعيل", result.exceptionOrNull()?.message ?: "لا يوجد تفعيل سابق مطابق وآمن لهذا الجهاز أو أن الاستعادة موقوفة من إدارة النظام")
                    }
                    return@runOnUiThread
                }
                recoveryRetryCount = 0
                val x = result.getOrThrow()
                repo.adoptRecoveredStore(x.storeId, x.storeName, x.branchId)
                repo.saveCentralActivation(x.licenseId, x.accessToken, x.expiresAt, x.maxEmployees, x.leaseUntil, x.serverTime)
                buildElegantUi(); refreshDashboard()
                info("تمت استعادة التفعيل تلقائيًا ✓", "تعرف الخادم على ${identity.deviceLabel()} كتثبيت سابق لنفس الجهاز، واستعاد الاشتراك وبيانات المحل دون إنشاء تفعيل جديد. تم تسجيل العملية في إشعارات إدارة النظام.")
            }
        }.apply { isDaemon = true }.start()
    }

    private fun showApprovedRecoveryDialog() {
        val code = UiKit.field(this, p, "رمز تصريح الاستعادة").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            addView(UiKit.subtitle(this@MainActivity, p,
                "يصدر صاحب النظام هذا الرمز من إدارة التفعيلات. الرمز يستخدم مرة واحدة فقط وينتهي تلقائيًا."))
            addView(code)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("استعادة بتصريح صاحب النظام")
            .setView(box)
            .setPositiveButton("استعادة", null)
            .setNegativeButton("إلغاء", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = code.text.toString().trim()
                if (value.length < 8) {
                    code.error = "أدخل رمز التصريح الصادر من صاحب النظام"
                    return@setOnClickListener
                }
                dialog.dismiss()
                recoverCentralActivationWithGrant(value)
            }
        }
        dialog.show()
    }

    private fun recoverCentralActivationWithGrant(code: String) {
        if (::status.isInitialized) status.text = "جاري استعادة التفعيل بتصريح صاحب النظام..."
        Thread {
            val result = CentralServerClient.recoverActivationWithGrant(repo.serverUrl, code, DeviceIdentity(this))
            runOnUiThread {
                if (result.isFailure) {
                    info("تعذر استعادة التفعيل", result.exceptionOrNull()?.message ?: "التصريح غير صالح أو منتهي")
                    return@runOnUiThread
                }
                val x = result.getOrThrow()
                repo.adoptRecoveredStore(x.storeId, x.storeName, x.branchId)
                repo.saveCentralActivation(x.licenseId, x.accessToken, x.expiresAt, x.maxEmployees, x.leaseUntil, x.serverTime)
                buildElegantUi()
                refreshDashboard()
                info("تمت استعادة التفعيل ✓",
                    "تم نقل اعتماد الاشتراك إلى هذا التثبيت بتصريح صاحب النظام، مع بقاء مدة الاشتراك وبيانات المحل.")
            }
        }.apply { isDaemon = true }.start()
    }

    private fun submitSelfRegistration(applicant: CentralServerClient.ActivationApplicant) {
        status.text = "جاري تسجيل المحل وبدء التجربة..."
        Thread {
            val identity = DeviceIdentity(this)
            val request = CentralServerClient.requestActivation(repo.serverUrl, repo.storeId, repo.branchId, applicant, identity)
            if (request.isFailure) {
                runOnUiThread {
                    status.text = "تعذر إنشاء طلب التسجيل"
                    info("تعذر التسجيل", request.exceptionOrNull()?.message ?: "خطأ في الاتصال بالخادم")
                }
                return@Thread
            }
            val pending = request.getOrThrow()
            repo.centralActivationRequestId = pending.requestId
            repo.centralActivationPollSecret = pending.pollSecret
            val registration = CentralServerClient.selfRegisterActivation(
                repo.serverUrl, pending.requestId, pending.pollSecret, repo.storeId, identity
            )
            if (registration.isFailure) {
                runOnUiThread {
                    status.text = "لم يكتمل التسجيل المباشر"
                    info("تعذر التسجيل المباشر", (registration.exceptionOrNull()?.message ?: "خطأ") + "\n\nتم الاحتفاظ بطلب التفعيل ويمكن لإدارة النظام اعتماده يدويًا.")
                    buildActivationLockUi()
                }
                return@Thread
            }
            val activation = CentralServerClient.activationStatus(
                repo.serverUrl, pending.requestId, pending.pollSecret, repo.storeId, identity
            )
            runOnUiThread {
                if (activation.isFailure) {
                    status.text = "تم التسجيل • يلزم فحص حالة التفعيل"
                    info("تم التسجيل", "تم إنشاء حساب المحل وبدء التجربة على الخادم. اضغط «فحص موافقة إدارة النظام» لاستلام اعتماد التشغيل على هذا الجهاز.")
                    buildActivationLockUi()
                    return@runOnUiThread
                }
                val x = activation.getOrThrow()
                if (x.accessToken.isBlank() || x.licenseId.isBlank() || x.leaseUntil <= System.currentTimeMillis() || x.serverTime <= 0L) {
                    status.text = "تم التسجيل • استجابة التفعيل غير مكتملة"
                    info("تم التسجيل", "تم تسجيل المحل على الخادم، لكن اعتماد التشغيل لم يصل كاملًا. استخدم فحص حالة التفعيل.")
                    buildActivationLockUi()
                    return@runOnUiThread
                }
                repo.saveCentralActivation(x.licenseId, x.accessToken, x.expiresAt, x.maxEmployees, x.leaseUntil, x.serverTime)
                repo.centralActivationRequestId = ""
                repo.centralActivationPollSecret = ""
                status.text = "✓ تم تسجيل المحل وبدء التجربة"
                buildElegantUi()
                refreshDashboard()
                val trial = registration.getOrThrow()
                info("تم التسجيل وبدء التجربة ✓", "تم إنشاء حساب المحل مباشرة وبدأت تجربة ${trial.trialDays} أيام. تم إشعار إدارة النظام تلقائيًا دون الحاجة إلى انتظار الموافقة.")
            }
        }.apply { isDaemon = true }.start()
    }

    private fun submitCentralActivationRequest(applicant: CentralServerClient.ActivationApplicant) {
        status.text = "جاري إرسال طلب التفعيل المركزي..."
        Thread {
            val r = CentralServerClient.requestActivation(repo.serverUrl, repo.storeId, repo.branchId, applicant, DeviceIdentity(this))
            runOnUiThread {
                if (r.isSuccess) {
                    val x = r.getOrThrow(); repo.centralActivationRequestId = x.requestId; repo.centralActivationPollSecret = x.pollSecret
                    status.text = "✓ تم إرسال طلب التفعيل المركزي"
                    AlertDialog.Builder(this).setTitle("تم إرسال الطلب").setMessage("رقم الطلب: ${x.requestId}\n\nسيظهر الطلب في لوحة إدارة النظام. بعد اعتماده اضغط «فحص حالة طلب التفعيل المركزي». لا ترسل سر الطلب لأي شخص.").setPositiveButton("حسنًا", null).show()
                    buildActivationLockUi()
                } else {
                    status.text = "تعذر إرسال طلب التفعيل"
                    AlertDialog.Builder(this).setTitle("تعذر الاتصال بالخادم").setMessage(r.exceptionOrNull()?.message ?: "خطأ").setPositiveButton("حسنًا", null).show()
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun checkCentralActivation() {
        val requestId = repo.centralActivationRequestId; val pollSecret = repo.centralActivationPollSecret
        if (requestId.isBlank() || pollSecret.isBlank()) { AlertDialog.Builder(this).setTitle("التفعيل المركزي").setMessage("لا يوجد طلب مركزي محفوظ على هذا الجهاز.").setPositiveButton("حسنًا", null).show(); return }
        status.text = "جاري التحقق من التفعيل..."
        Thread {
            val r = CentralServerClient.activationStatus(repo.serverUrl, requestId, pollSecret, repo.storeId, DeviceIdentity(this))
            runOnUiThread {
                if (r.isFailure) { status.text = "تعذر فحص التفعيل"; AlertDialog.Builder(this).setTitle("الخادم المركزي").setMessage(r.exceptionOrNull()?.message ?: "خطأ").setPositiveButton("حسنًا", null).show(); return@runOnUiThread }
                val x = r.getOrThrow()
                when (x.status.uppercase(Locale.US)) {
                    "APPROVED", "ACTIVE" -> {
                        if (x.accessToken.isBlank() || x.licenseId.isBlank() || x.leaseUntil <= System.currentTimeMillis() || x.serverTime <= 0L) {
                            status.text = "استجابة تفعيل غير مكتملة"
                            AlertDialog.Builder(this).setTitle("تحديث الخادم مطلوب").setMessage("الخادم لم يرسل مهلة تشغيل آمنة. حدّث خدمة ATTEND PRO المركزية إلى واجهة API المتوافقة.").setPositiveButton("حسنًا", null).show()
                            return@runOnUiThread
                        }
                        repo.saveCentralActivation(x.licenseId, x.accessToken, x.expiresAt, x.maxEmployees, x.leaseUntil, x.serverTime)
                        status.text = "✓ تم تفعيل المحل مركزيًا"; buildElegantUi(); refreshDashboard()
                        AlertDialog.Builder(this).setTitle("تم التفعيل المركزي ✓").setMessage("أصبح هذا الجهاز مرتبطًا بالخادم. ستعمل مزامنة الحضور والتقارير عن بعد مع استمرار التسجيل محليًا عند انقطاع الإنترنت.").setPositiveButton("حسنًا", null).show()
                    }
                    "REJECTED", "SUSPENDED" -> { status.text = "طلب التفعيل غير نشط"; AlertDialog.Builder(this).setTitle("حالة التفعيل").setMessage(x.reason.ifBlank { x.status }).setPositiveButton("حسنًا", null).show() }
                    else -> { status.text = "طلب التفعيل بانتظار اعتماد إدارة النظام"; AlertDialog.Builder(this).setTitle("بانتظار الاعتماد").setMessage("لم يعتمد إدارة النظام الطلب بعد.").setPositiveButton("حسنًا", null).show() }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun showLicenseDetails() {
        if (!repo.hasCentralCredentials()) {
            AlertDialog.Builder(this).setTitle("حالة التفعيل").setMessage("لا يوجد تفعيل مركزي محفوظ على هذا الجهاز.").setPositiveButton("حسنًا", null).show()
            return
        }
        val expires = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(repo.centralActivationExpiresAt))
        val lease = if (repo.centralLeaseUntil > 0L) SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(repo.centralLeaseUntil)) else "غير متاح"
        AlertDialog.Builder(this).setTitle("بيانات التفعيل المركزي")
            .setMessage("المحل: ${repo.storeName}\nالفرع: ${repo.branchId}\nرقم الترخيص: ${repo.centralLicenseId}\nالحالة: ${repo.centralServerStatus}\nانتهاء الاشتراك: $expires\nمهلة العمل دون اتصال: $lease\nحد الموظفين: ${repo.centralMaxEmployees}\nالجهاز: ${repo.storeId}\nالخادم: ${repo.serverUrl}\n\nرمز الوصول محفوظ مشفرًا داخل Android Keystore ولا يظهر في الواجهة.")
            .setPositiveButton("تحقق الآن") { _, _ -> validateCentralActivation(silent = false) }
            .setNegativeButton("إغلاق", null).show()
    }

    private fun copyActivationText(text: String, message: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("ATTEND PRO", text))
        if (::status.isInitialized) status.text = message
    }

    private fun requireStoreOwner(title: String, action: () -> Unit) {
        if (!repo.hasStoreAdminPin) { action(); return }
        if (repo.storeAdminLockUntil > System.currentTimeMillis()) {
            val remaining = ((repo.storeAdminLockUntil - System.currentTimeMillis()) / 1000L).coerceAtLeast(1)
            info(title, "إدارة صاحب العمل مقفلة مؤقتًا. حاول بعد $remaining ثانية.")
            return
        }
        val pin = UiKit.field(this, p, "رمز صاحب العمل", true).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("هذا الخيار من صلاحيات صاحب العمل. أدخل رمز إدارة المحل للمتابعة.")
            .setView(pin)
            .setPositiveButton("دخول", null)
            .setNegativeButton("إلغاء", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (repo.verifyStoreAdminPin(pin.text.toString())) {
                    repo.issueStoreAdminSession()
                    dialog.dismiss()
                    action()
                } else {
                    pin.error = if (repo.storeAdminLockUntil > System.currentTimeMillis()) "تم القفل لمدة دقيقة" else "الرمز غير صحيح"
                }
            }
        }
        dialog.show()
    }

    private data class OwnerShortcut(val id: String, val title: String)

    private fun ownerShortcutCatalog(): List<OwnerShortcut> = listOf(
        OwnerShortcut("employees", "الموظفون وملفات التعرف"),
        OwnerShortcut("pair_employee", "ربط جهاز موظف"),
        OwnerShortcut("presence_challenge", "طلب إثبات حضور"),
        OwnerShortcut("attendance_policy", "الدوام وسياسات الحضور"),
        OwnerShortcut("smart_attendance", "الدوام والتنبيهات الذكية"),
        OwnerShortcut("live_attendance", "الحضور الآن"),
        OwnerShortcut("today_log", "سجل اليوم"),
        OwnerShortcut("reports", "تقارير الحضور"),
        OwnerShortcut("report_receiver", "استلام التقارير"),
        OwnerShortcut("connections", "الأجهزة والاتصال"),
        OwnerShortcut("app_settings", "إعدادات التطبيق"),
        OwnerShortcut("license", "التفعيل والاشتراك")
    )

    private fun ownerShortcutIds(): Set<String> =
        getSharedPreferences("store_owner_ui", MODE_PRIVATE)
            .getStringSet("home_shortcuts", emptySet())?.toSet().orEmpty()

    private fun saveOwnerShortcutIds(ids: Set<String>) {
        val valid = ownerShortcutCatalog().map { it.id }.toSet()
        getSharedPreferences("store_owner_ui", MODE_PRIVATE)
            .edit().putStringSet("home_shortcuts", ids.filter { it in valid }.toSet()).apply()
    }

    private fun runOwnerShortcut(id: String) {
        when (id) {
            "employees" -> startActivity(Intent(this, StoreSettingsActivity::class.java))
            "pair_employee" -> showPairEmployeePicker()
            "presence_challenge" -> showPresenceChallenge()
            "attendance_policy" -> PhoneAttendanceSettingsDialog1928.show(this, repo)
            "smart_attendance" -> showSmartAttendanceControl()
            "live_attendance" -> showLiveAttendanceNow()
            "today_log" -> showConnectionAttendanceHistory()
            "reports" -> startActivity(Intent(this, ReportsActivity::class.java))
            "report_receiver" -> startActivity(Intent(this, ReportReceiverActivity::class.java))
            "connections" -> showConnectionCenter()
            "app_settings" -> showAppSettingsHub()
            "license" -> showLicenseDetails()
        }
    }

    private fun showOwnerShortcutPicker() {
        val catalog = ownerShortcutCatalog()
        val selected = ownerShortcutIds().toMutableSet()
        val checked = BooleanArray(catalog.size) { catalog[it].id in selected }
        AlertDialog.Builder(this)
            .setTitle("اختصارات الشاشة الرئيسية")
            .setMultiChoiceItems(catalog.map { it.title }.toTypedArray(), checked) { _, which, isChecked ->
                if (isChecked) selected += catalog[which].id else selected -= catalog[which].id
            }
            .setPositiveButton("حفظ") { _, _ ->
                saveOwnerShortcutIds(selected)
                buildElegantUi()
                refreshDashboard()
                status.text = if (selected.isEmpty()) "تم إخفاء اختصارات إدارة المحل من الرئيسية" else "تم تحديث اختصارات إدارة المحل"
            }
            .setNeutralButton("إزالة الكل") { _, _ ->
                saveOwnerShortcutIds(emptySet())
                buildElegantUi()
                refreshDashboard()
                status.text = "تم إزالة اختصارات إدارة المحل من الرئيسية"
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun addOwnerShortcutCard(root: LinearLayout) {
        val selectedIds = ownerShortcutIds()
        if (selectedIds.isEmpty()) return
        val shortcuts = ownerShortcutCatalog().filter { it.id in selectedIds }
        if (shortcuts.isEmpty()) return

        val card = UiKit.card(this, p, 10).apply {
            addView(UiKit.sectionLabel(this@MainActivity, p, "اختصارات إدارة المحل"))
            addView(UiKit.subtitle(this@MainActivity, p, "الوصول السريع للوظائف التي اختارها مدير المحل. تبقى صلاحيات الإدارة محمية.").apply {
                gravity = Gravity.CENTER
                textSize = 11.5f
            })
        }

        shortcuts.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.CENTER
            }
            pair.forEachIndexed { index, shortcut ->
                val button = UiKit.button(this, p, shortcut.title, false).apply {
                    textSize = 12f
                    maxLines = 2
                    layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(this@MainActivity, 54), 1f).apply {
                        if (index == 0) marginEnd = UiKit.dp(this@MainActivity, 4) else marginStart = UiKit.dp(this@MainActivity, 4)
                    }
                    setOnClickListener {
                        requireStoreOwner(shortcut.title) { runOwnerShortcut(shortcut.id) }
                    }
                }
                row.addView(button)
            }
            if (pair.size == 1) {
                row.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(this@MainActivity, 54), 1f).apply {
                        marginStart = UiKit.dp(this@MainActivity, 4)
                    }
                })
            }
            card.addView(row)
        }

        card.addView(UiKit.button(this, p, "تعديل اختصارات الرئيسية", false).apply {
            setOnClickListener { requireStoreOwner("اختصارات إدارة المحل") { showOwnerShortcutPicker() } }
        })
        root.addView(card)
    }

    private fun showStoreOwnerHub() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(
                UiKit.dp(this@MainActivity, 10),
                UiKit.dp(this@MainActivity, 6),
                UiKit.dp(this@MainActivity, 10),
                UiKit.dp(this@MainActivity, 6)
            )
            addView(UiKit.subtitle(this@MainActivity, p,
                "اختر الوظيفة مباشرة. ويمكنك تثبيت أي وظيفة تختارها كاختصار في الشاشة الرئيسية.").apply {
                gravity = Gravity.CENTER
                textSize = 11.8f
                setPadding(0, 0, 0, UiKit.dp(this@MainActivity, 8))
            })
        }
        val scroll = ScrollView(this).apply { addView(content) }
        var hubDialog: AlertDialog? = null

        ownerShortcutCatalog().forEach { shortcut ->
            content.addView(UiKit.button(this, p, shortcut.title, false).apply {
                setOnClickListener {
                    hubDialog?.dismiss()
                    runOwnerShortcut(shortcut.id)
                }
            })
        }
        content.addView(UiKit.button(this, p, "تخصيص اختصارات الشاشة الرئيسية").apply {
            setOnClickListener {
                hubDialog?.dismiss()
                showOwnerShortcutPicker()
            }
        })

        hubDialog = AlertDialog.Builder(this)
            .setTitle("إدارة المحل")
            .setView(scroll)
            .setNegativeButton("إغلاق", null)
            .create()
        hubDialog.show()
    }

    private fun showPairEmployeePicker() {
        val employees = repo.employees().filter { it.active && it.companionEnabled }
        if (employees.isEmpty()) {
            info("ربط جهاز موظف", "لا يوجد موظف نشط مفعّل له ربط الهاتف. افتح قسم الموظفين أولًا وفعّل ربط تطبيق الموظف.")
            return
        }
        if (employees.size == 1) { showProvisionQr(employees.first()); return }
        AlertDialog.Builder(this)
            .setTitle("ربط جهاز موظف")
            .setItems(employees.map { "${it.displayName} • ${it.employeeId}" }.toTypedArray()) { _, which -> showProvisionQr(employees[which]) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showLinkedEmployeesDetails() {
        val now = System.currentTimeMillis()
        val employees = repo.employees().filter { it.active && it.companionEnabled }
        if (employees.isEmpty()) { info("الموظفون المرتبطون", "لا يوجد موظف مرتبط بتطبيق الموظف حاليًا."); return }
        val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
        val lines = employees.map { e ->
            val linkedAt = repo.companionLinkedAt(e.employeeId)
            val channel = repo.companionLinkedChannel(e.employeeId).ifBlank { "لم يُؤكد الربط بعد" }
            val live = isEmployeeActuallyConnected(e.employeeId, now)
            val connection = when {
                directBle.isConnected(e.employeeId) -> "Bluetooth ACK"
                isLanConnected(e.employeeId, now) -> "Wi‑Fi/Hotspot ACK"
                isServerPresenceConnected(e.employeeId, now) -> "Server Heartbeat"
                isGpsRecognizedFresh(e.employeeId, now) -> "GPS رصد فقط"
                else -> "غير متصل الآن"
            }
            buildString {
                append(if (live) "● " else "○ ")
                append("${e.displayName} (${e.employeeId})\n")
                append("الحالة: $connection")
                if (linkedAt > 0L) append("\nآخر ربط مؤكد: ${fmt.format(Date(linkedAt))} • $channel")
                else append("\nالربط: $channel")
            }
        }
        AlertDialog.Builder(this)
            .setTitle("الموظفون المرتبطون (${employees.size})")
            .setMessage(lines.joinToString("\n\n"))
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun showAppSettingsHub() {
        val items = arrayOf(
            "المظهر وطريقة العرض",
            "طرق الحضور والتحقق",
            "سياسات الحضور الذكي",
            "مركز الاتصال والأجهزة",
            "إعدادات صاحب العمل الكاملة",
            "فحص تحديث التطبيق"
        )
        AlertDialog.Builder(this).setTitle("إعدادات التطبيق").setItems(items) { _, which ->
            when (which) {
                0 -> UiKit.showAppearancePicker(this)
                1 -> showAttendanceMethods("طرق الحضور والتحقق")
                2 -> PhoneAttendanceSettingsDialog1928.show(this, repo)
                3 -> showConnectionCenter()
                4 -> startActivity(Intent(this, StoreSettingsActivity::class.java))
                5 -> DistributionUpdateManager.check(this, repo.serverUrl.ifBlank { DistributionUpdateManager.DEFAULT_SERVER }, "store", manual = true)
            }
        }.setNegativeButton("إغلاق", null).show()
    }

    private fun showAttendanceMethods(
        dialogTitle: String = "اختر طريقة الحضور",
        forcedAction: AttendanceAction? = null
    ){
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (repo.allowFaceEnrollment) actions += "◉ التعرف بالوجه" to { requestFaceRecognition(forcedAction) }
        if (repo.allowVoiceVerification) actions += "◖ بصمة الصوت" to { showVoiceDialog(forcedAction) }
        if (repo.allowPasswordFallback) actions += "▣ كلمة المرور" to { showPasswordDialog(forcedAction) }
        if (repo.allowEmployeeCompanion && repo.requirePhoneBiometric) actions += "◎ بصمة/وجه هاتف الموظف" to { showPresenceChallenge(forcedAction) }
        if (repo.allowQrAttendance) actions += "▦ QR مباشر" to { showDirectQrAttendance1937(forcedAction) }
        if (actions.isEmpty()) {
            info("طرق الحضور", "لا توجد طريقة مفعلة. افتح إدارة المحل ← إعدادات التطبيق ← طرق الحضور والتحقق.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showFaceEngineInfo(){
        AlertDialog.Builder(this).setTitle("التعرف بالوجه على جهاز المحل")
            .setMessage("في الإصدار الحالي يُسجل الوجه من خمس وضعيات، ويُطلب تحدٍ حركي وصورة ثانية قبل قبول الحضور. هذا فحص حيوية تفاعلي محلي محسّن، وليس نظام anti-spoof تجاريًا معتمدًا.")
            .setPositiveButton("حسنًا", null).show()
    }

    private fun quickFingerprintCheck(){
        if(repo.fingerprintHost.isBlank()){status.text="أدخل بيانات قارئ البصمة من إدارة المحل أولًا";return}
        status.text="جاري اختبار قارئ البصمة..."
        Thread{val r=NetworkTools.probeTcp(repo.fingerprintHost,repo.fingerprintPort);runOnUiThread{
            status.text=if(r.isSuccess)"✓ قارئ البصمة متصل — استيراد البصمات يحتاج Driver الموديل" else "تعذر الاتصال بالقارئ: ${r.exceptionOrNull()?.message ?: "خطأ"}"
        }}.start()
    }

    private fun showEmployeesDialog(){
        val employees=repo.employees()
        val labels=mutableListOf("＋ إضافة موظف جديد")
        labels.addAll(employees.map{"${if(it.active)"●" else "○"} ${it.displayName} (${it.employeeId})${if(it.jobTitle.isNotBlank())" • ${it.jobTitle}" else ""}"})
        AlertDialog.Builder(this).setTitle("الموظفون (${employees.size})").setItems(labels.toTypedArray()){_,which->
            if(which==0) showEmployeeEditor(null) else showEmployeeDetails(employees[which-1])
        }.setNegativeButton("إغلاق",null).show()
    }

    private fun showEmployeeEditor(existing: PairedEmployee?) {
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

    private fun showEmployeeEditorAdvanced(existing: PairedEmployee?) {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 10) }
        scroll.addView(box)

        box.addView(UiKit.subtitle(this, p, "أدخل البيانات الأساسية أولًا، ثم أكمل ملف التعرف: خمس وضعيات للوجه، خمس عينات لنبرة الصوت، وربط هاتف الموظف واختباره. الحقول الفارغة عند التعديل تُبقي البيانات الحالية."))
        val id = UiKit.field(this, p, "رقم الموظف").apply { setText(existing?.employeeId.orEmpty()); isEnabled = existing == null }
        val name = UiKit.field(this, p, "اسم الموظف").apply { setText(existing?.displayName.orEmpty()) }
        val phone = UiKit.field(this, p, "رقم الهاتف - اختياري").apply { setText(existing?.phone.orEmpty()) }
        val job = UiKit.field(this, p, "المسمى الوظيفي").apply { setText(existing?.jobTitle.orEmpty()) }
        val department = UiKit.field(this, p, "القسم / الإدارة - اختياري").apply { setText(existing?.department.orEmpty()) }
        val nationalId = UiKit.field(this, p, "رقم الهوية - اختياري").apply { setText(existing?.nationalId.orEmpty()) }
        val hireDate = UiKit.field(this, p, "تاريخ التعيين").apply { setText(existing?.hireDate.orEmpty()); isFocusable = false; isClickable = true; setOnClickListener { FormPickerHelper.pickDate(this@MainActivity, this) } }
        val branch = UiKit.field(this, p, "الفرع").apply { setText(existing?.branchId ?: repo.branchId) }
        listOf(id, name, phone, job, department, nationalId, hireDate, branch).forEach { box.addView(it) }

        box.addView(UiKit.sectionLabel(this, p, "وقت دوام الموظف"))
        val customShift = CheckBox(this).apply {
            text = "استخدام دوام مستقل لهذا الموظف"; setTextColor(p.text); isChecked = existing?.useCustomShift ?: false
        }
        val startHour = UiKit.field(this, p, "ساعة بداية الدوام").apply { setText((existing?.shiftStartHour ?: repo.shiftHour).toString()); isFocusable = false; isClickable = true; setOnClickListener { FormPickerHelper.pickNumber(this@MainActivity, this, 0, 23, "ساعة بداية الدوام") } }
        val startMinute = UiKit.field(this, p, "دقيقة البداية").apply { setText((existing?.shiftStartMinute ?: repo.shiftMinute).toString()); isFocusable = false; isClickable = true; setOnClickListener { FormPickerHelper.pickNumber(this@MainActivity, this, 0, 59, "دقيقة البداية") } }
        val endHour = UiKit.field(this, p, "ساعة انتهاء الدوام").apply { setText((existing?.shiftEndHour ?: repo.shiftEndHour).toString()); isFocusable = false; isClickable = true; setOnClickListener { FormPickerHelper.pickNumber(this@MainActivity, this, 0, 23, "ساعة انتهاء الدوام") } }
        val endMinute = UiKit.field(this, p, "دقيقة الانتهاء").apply { setText((existing?.shiftEndMinute ?: repo.shiftEndMinute).toString()); isFocusable = false; isClickable = true; setOnClickListener { FormPickerHelper.pickNumber(this@MainActivity, this, 0, 59, "دقيقة الانتهاء") } }
        box.addView(customShift); listOf(startHour, startMinute, endHour, endMinute).forEach { box.addView(it) }
        box.addView(UiKit.subtitle(this, p, "إذا لم تفعل الدوام المستقل سيستخدم الموظف وقت الدوام العام للمحل."))

        box.addView(UiKit.sectionLabel(this, p, "التنبيه الذكي لهذا الموظف"))
        val lateEnabled = CheckBox(this).apply { text = "تفعيل تنبيه التأخر لهذا الموظف"; setTextColor(p.text); isChecked = existing?.lateAlertEnabled ?: true }
        val lateGrace = UiKit.field(this, p, "مدة السماح بالدقائق").apply { setText((existing?.lateGraceMinutes?.takeIf { it >= 0 } ?: repo.graceMinutes).toString()); isFocusable=false; isClickable=true; setOnClickListener { FormPickerHelper.pickNumber(this@MainActivity, this, 0, 60, "مدة السماح") } }
        val lateFirst = UiKit.field(this, p, "دقائق التأخير قبل أول تنبيه").apply { setText((existing?.lateFirstAlertDelayMinutes?.takeIf { it >= 0 } ?: repo.lateAlertDelayMinutes).toString()); isFocusable=false; isClickable=true; setOnClickListener { FormPickerHelper.pickNumber(this@MainActivity, this, 0, 120, "قبل أول تنبيه") } }
        val lateCount = UiKit.field(this, p, "عدد مرات التنبيه").apply { setText((existing?.lateAlertCount ?: 3).toString()); isFocusable=false; isClickable=true; setOnClickListener { FormPickerHelper.pickNumber(this@MainActivity, this, 1, 10, "عدد مرات التنبيه") } }
        val lateRepeat = UiKit.field(this, p, "الفاصل بين التنبيهات بالدقائق").apply { setText((existing?.lateRepeatMinutes?.takeIf { it >= 5 } ?: repo.lateAlertRepeatMinutes).toString()); isFocusable=false; isClickable=true; setOnClickListener { FormPickerHelper.pickNumber(this@MainActivity, this, 5, 120, "الفاصل بين التنبيهات") } }
        val presenceReminder = UiKit.field(this, p, "مهلة إثبات الحضور بعد اكتشاف الهاتف بالدقائق").apply { setText((existing?.presenceReminderMinutes ?: 3).toString()); isFocusable=false; isClickable=true; setOnClickListener { FormPickerHelper.pickNumber(this@MainActivity, this, 1, 15, "مهلة إثبات الحضور") } }
        val lateModeLabels = arrayOf("إشعار فقط", "إشعار + ناطق صوتي")
        val lateModeValues = arrayOf("NOTIFICATION", "NOTIFICATION_VOICE")
        val lateMode = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, lateModeLabels); setSelection(if ((existing?.lateAlertMode ?: "NOTIFICATION_VOICE") == "NOTIFICATION") 0 else 1) }
        val callModeLabels = arrayOf("بدون اتصال", "فتح شاشة الاتصال", "اتصال تلقائي إذا سمح Android")
        val callModeValues = arrayOf("NONE", "DIAL", "AUTO_IF_ALLOWED")
        val resolvedCallMode = existing?.lateCallMode ?: if (repo.lateAutoCallEnabled) "AUTO_IF_ALLOWED" else "NONE"
        val callMode = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, callModeLabels); setSelection(callModeValues.indexOf(resolvedCallMode).coerceAtLeast(0)) }
        box.addView(lateEnabled)
        listOf(lateGrace, lateFirst, lateCount, lateRepeat, presenceReminder).forEach { box.addView(it) }
        box.addView(UiKit.subtitle(this, p, "نوع التنبيه")); box.addView(lateMode)
        box.addView(UiKit.subtitle(this, p, "الاتصال عند التأخر — الوضع التلقائي يخضع لصلاحيات وقيود Android، وعند منعه يبقى زر الاتصال في الإشعار")); box.addView(callMode)

        box.addView(UiKit.sectionLabel(this, p, "1 — التحقق والملفات"))
        val password = UiKit.field(this, p, "كلمة مرور جديدة - اتركها فارغة للإبقاء الحالي").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val voicePhrase = UiKit.field(this, p, "العبارة الأساسية للصوت (مثال: أنا حاضر للعمل) - اتركها فارغة للإبقاء الحالي")
        val fingerprint = UiKit.field(this, p, "معرف الموظف في قارئ البصمة - اختياري").apply { setText(existing?.externalFingerprintId.orEmpty()) }
        listOf(password, voicePhrase, fingerprint).forEach { box.addView(it) }
        box.addView(UiKit.subtitle(this, p, "العبارة المكتوبة وحدها لا تكفي: بعد الحفظ ستظهر شاشة إعداد واضحة لتسجيل نبرة الموظف ثلاث مرات. عند الحضور يطابق النظام النبرة والعبارة وكلمة تحدٍ متغيرة."))

        box.addView(UiKit.sectionLabel(this, p, "2 — طرق الحضور المسموح بها"))
        fun currentAllowed(method: AttendanceMethod, globalEnabled: Boolean): Boolean {
            if (!globalEnabled) return false
            return existing?.let { it.allowedMethods.isEmpty() || it.allows(method) } ?: true
        }
        fun methodCheck(label: String, method: AttendanceMethod, globalEnabled: Boolean): CheckBox = CheckBox(this).apply {
            text = label; setTextColor(p.text); isChecked = currentAllowed(method, globalEnabled); isEnabled = globalEnabled
            if (!globalEnabled) text = "$label — معطل من إعدادات المحل"
        }
        // The five primary visible verification methods requested for day-to-day attendance.
        val methodChecks = linkedMapOf(
            AttendanceMethod.PASSWORD to methodCheck("▣ كلمة المرور", AttendanceMethod.PASSWORD, repo.allowPasswordFallback),
            AttendanceMethod.VOICE_PHRASE to methodCheck("◖ بصمة الصوت", AttendanceMethod.VOICE_PHRASE, repo.allowVoiceVerification),
            AttendanceMethod.SHARED_DEVICE_FACE to methodCheck("◉ التعرف بالوجه على جهاز المحل", AttendanceMethod.SHARED_DEVICE_FACE, repo.allowFaceEnrollment),
            AttendanceMethod.PHONE_BLE_BIOMETRIC to methodCheck("◎ بصمة/وجه هاتف الموظف", AttendanceMethod.PHONE_BLE_BIOMETRIC, repo.allowEmployeeCompanion),
            AttendanceMethod.PHONE_PROXIMITY to methodCheck("▦ QR مباشر", AttendanceMethod.PHONE_PROXIMITY, repo.allowEmployeeCompanion && repo.allowQrAttendance)
        )
        methodChecks.values.forEach { box.addView(it) }
        // Preserve the existing optional external reader without mixing it into the five primary methods.
        box.addView(UiKit.sectionLabel(this, p, "تكامل قارئ البصمة الخارجي — اختياري"))
        val externalFingerprintMethod = methodCheck("◎ استخدام قارئ البصمة الخارجي", AttendanceMethod.EXTERNAL_FINGERPRINT, repo.allowExternalFingerprint)
        box.addView(externalFingerprintMethod)

        val faceEnroll = CheckBox(this).apply {
            text = if (existing?.faceProfileRef.isNullOrBlank()) "تسجيل بصمة الوجه بعد الحفظ" else "تحديث بصمة الوجه بعد الحفظ"
            setTextColor(p.text); isChecked = existing == null && repo.allowFaceEnrollment; isEnabled = repo.allowFaceEnrollment
        }
        val voiceEnroll = CheckBox(this).apply {
            text = if (existing?.voiceTemplate.isNullOrBlank()) "تسجيل بصمة نبرة الصوت بعد الحفظ (5 تسجيلات)" else "تحديث بصمة نبرة الصوت بعد الحفظ (5 تسجيلات)"
            setTextColor(p.text); isChecked = existing == null && repo.allowVoiceVerification; isEnabled = repo.allowVoiceVerification
        }
        val companion = CheckBox(this).apply {
            text = "السماح بربط تطبيق الموظف بهذا الهاتف"; setTextColor(p.text)
            isChecked = (existing?.companionEnabled ?: true) && repo.allowEmployeeCompanion
            isEnabled = repo.allowEmployeeCompanion
        }
        box.addView(UiKit.sectionLabel(this, p, "3 — تجهيز ملفات التعرف والربط"))
        box.addView(faceEnroll); box.addView(voiceEnroll); box.addView(companion)
        box.addView(UiKit.subtitle(this, p, "بعد الحفظ يفتح مركز تجهيز الموظف. منه تستطيع أخذ صور الوجه، تسجيل نبرة الصوت، إنشاء QR لهاتف الموظف، ثم اختبار ظهوره فعليًا عبر Bluetooth وWi‑Fi/نقطة الاتصال. GPS يعمل كطبقة حماية للموقع في الخلفية ولا يظهر كطريقة حضور مستقلة."))
        val notes = UiKit.field(this, p, "ملاحظات - اختياري").apply { setText(existing?.notes.orEmpty()); minLines = 2 }
        box.addView(notes)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "إضافة موظف" else "تعديل الموظف")
            .setView(scroll).setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val employeeId = id.text.toString().trim(); val displayName = name.text.toString().trim()
                if (employeeId.isBlank() || displayName.isBlank()) {
                    id.error = if (employeeId.isBlank()) "مطلوب" else null; name.error = if (displayName.isBlank()) "مطلوب" else null; return@setOnClickListener
                }
                if (existing == null && repo.employees().size >= repo.effectiveEmployeeLimit()) {
                    AlertDialog.Builder(this).setTitle("حد الموظفين").setMessage("الترخيص الحالي يسمح بحد أقصى ${repo.effectiveEmployeeLimit()} موظفين.").setPositiveButton("حسنًا", null).show(); return@setOnClickListener
                }
                if (existing == null && repo.employees().any { it.employeeId.equals(employeeId, true) }) { id.error = "رقم الموظف مستخدم بالفعل"; return@setOnClickListener }
                val newPassword = password.text.toString()
                if (newPassword.isNotBlank() && newPassword.length < 4) { password.error = "استخدم 4 أحرف/أرقام على الأقل"; return@setOnClickListener }
                val sh = startHour.text.toString().toIntOrNull(); val sm = startMinute.text.toString().toIntOrNull()
                val eh = endHour.text.toString().toIntOrNull(); val em = endMinute.text.toString().toIntOrNull()
                if (sh == null || sh !in 0..23) { startHour.error = "0 إلى 23"; return@setOnClickListener }
                if (sm == null || sm !in 0..59) { startMinute.error = "0 إلى 59"; return@setOnClickListener }
                if (eh == null || eh !in 0..23) { endHour.error = "0 إلى 23"; return@setOnClickListener }
                if (em == null || em !in 0..59) { endMinute.error = "0 إلى 59"; return@setOnClickListener }
                val selectedMethods = buildSet {
                    addAll(methodChecks.filterValues { it.isChecked }.keys.map { it.name })
                    if (externalFingerprintMethod.isChecked) add(AttendanceMethod.EXTERNAL_FINGERPRINT.name)
                }
                if (selectedMethods.isEmpty()) { info("طرق الحضور", "فعّل طريقة حضور واحدة على الأقل لهذا الموظف."); return@setOnClickListener }
                val lateGraceValue = lateGrace.text.toString().toIntOrNull()?.coerceIn(0, 60) ?: repo.graceMinutes
                val lateFirstValue = lateFirst.text.toString().toIntOrNull()?.coerceIn(0, 120) ?: repo.lateAlertDelayMinutes
                val lateCountValue = lateCount.text.toString().toIntOrNull()?.coerceIn(1, 10) ?: 3
                val lateRepeatValue = lateRepeat.text.toString().toIntOrNull()?.coerceIn(5, 120) ?: repo.lateAlertRepeatMinutes
                val presenceReminderValue = presenceReminder.text.toString().toIntOrNull()?.coerceIn(1, 15) ?: 3

                val secret = existing?.pairingSecret?.takeIf { SecretCodec.isValid(it) } ?: SecretCodec.encode(SecretCodec.generate())
                val pinHash = existing?.pin.orEmpty()
                val passwordHash = if (newPassword.isBlank()) existing?.passwordHash.orEmpty() else PairingProtocol.pinHash(newPassword)
                val patternHash = existing?.patternHash.orEmpty()
                val phraseNormalized = normalizeVoicePhrase(voicePhrase.text.toString())
                val voiceHash = if (phraseNormalized.isBlank()) existing?.voicePhraseHash.orEmpty() else PairingProtocol.pinHash(phraseNormalized)
                if (AttendanceMethod.PASSWORD.name in selectedMethods && passwordHash.isBlank()) { password.error = "أدخل كلمة مرور لأن هذه الطريقة مفعلة"; return@setOnClickListener }
                if (AttendanceMethod.VOICE_PHRASE.name in selectedMethods && voiceHash.isBlank()) { voicePhrase.error = "أدخل العبارة الصوتية لأن هذه الطريقة مفعلة"; return@setOnClickListener }
                if (AttendanceMethod.EXTERNAL_FINGERPRINT.name in selectedMethods && fingerprint.text.toString().trim().isBlank()) { fingerprint.error = "أدخل معرف البصمة الخارجية أو عطّل هذه الطريقة مؤقتًا"; return@setOnClickListener }
                val phoneMethodSelected = AttendanceMethod.PHONE_BLE_BIOMETRIC.name in selectedMethods || AttendanceMethod.PHONE_FINGERPRINT.name in selectedMethods || AttendanceMethod.PHONE_PROXIMITY.name in selectedMethods
                if (phoneMethodSelected && !companion.isChecked) { info("هاتف الموظف", "فعّل «السماح بربط تطبيق الموظف» لأن إحدى طرق الهاتف مفعلة لهذا الموظف."); return@setOnClickListener }
                val employee = PairedEmployee(
                    employeeId = employeeId, displayName = displayName,
                    branchId = branch.text.toString().trim().ifBlank { repo.branchId }, pairingSecret = secret,
                    pin = pinHash, phone = phone.text.toString().trim(), jobTitle = job.text.toString().trim(),
                    externalFingerprintId = fingerprint.text.toString().trim(), faceProfileRef = existing?.faceProfileRef.orEmpty(),
                    companionEnabled = companion.isChecked, active = existing?.active ?: true,
                    department = department.text.toString().trim(), nationalId = nationalId.text.toString().trim(), hireDate = hireDate.text.toString().trim(),
                    notes = notes.text.toString().trim(), faceCapturedAt = existing?.faceCapturedAt ?: 0L,
                    passwordHash = passwordHash, patternHash = patternHash, voicePhraseHash = voiceHash, allowedMethods = selectedMethods,
                    useCustomShift = customShift.isChecked, shiftStartHour = sh, shiftStartMinute = sm, shiftEndHour = eh, shiftEndMinute = em,
                    faceTemplate = existing?.faceTemplate.orEmpty(), faceQualityScore = existing?.faceQualityScore ?: 0,
                    voiceTemplate = existing?.voiceTemplate.orEmpty(), voiceQualityScore = existing?.voiceQualityScore ?: 0,
                    voicePhraseText = if (phraseNormalized.isBlank()) existing?.voicePhraseText.orEmpty() else phraseNormalized,
                    lateAlertEnabled = lateEnabled.isChecked,
                    lateGraceMinutes = lateGraceValue,
                    lateFirstAlertDelayMinutes = lateFirstValue,
                    lateAlertCount = lateCountValue,
                    lateRepeatMinutes = lateRepeatValue,
                    lateAlertMode = lateModeValues[lateMode.selectedItemPosition.coerceIn(0, lateModeValues.lastIndex)],
                    lateCallMode = callModeValues[callMode.selectedItemPosition.coerceIn(0, callModeValues.lastIndex)],
                    presenceReminderMinutes = presenceReminderValue
                )
                repo.upsertEmployee(employee); status.text = "✓ تم حفظ ${employee.displayName}"; refreshDashboard(); dialog.dismiss()
                showEmployeeEnrollmentCenter(employee, faceEnroll.isChecked, voiceEnroll.isChecked)
            }
        }
        dialog.show()
    }

    private fun showEmployeeEnrollmentCenter(employee: PairedEmployee, suggestFace: Boolean = false, suggestVoice: Boolean = false) {
        val current = repo.employees().firstOrNull { it.employeeId.equals(employee.employeeId, true) } ?: employee
        val recentlySeen = nearby[current.employeeId]?.seenAt?.let { System.currentTimeMillis() - it < 90_000L } == true
        val locallyPaired = repo.companionLinkedAt(current.employeeId) > 0L
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 20), UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 20), UiKit.dp(this@MainActivity, 12))
        }
        scroll.addView(box)
        box.addView(UiKit.subtitle(this, p, buildString {
            append("أكمل تجهيز ${current.displayName} بالترتيب التالي:\n")
            append("الوجه: ${if (current.faceTemplate.isNotBlank()) "✓ جاهز (${current.faceQualityScore}/100)" else "غير مسجل"}\n")
            append("نبرة الصوت: ${if (current.voiceTemplate.isNotBlank()) "✓ جاهزة (${current.voiceQualityScore}/100)" else "غير مسجلة"}\n")
            append("تطبيق الموظف: ${if (!current.companionEnabled) "معطل" else if (recentlySeen) "✓ متصل الآن" else if (locallyPaired) "✓ ربط محلي مؤكد عبر ${repo.companionLinkedChannel(current.employeeId)}" else "بانتظار الربط"}")
        }))
        lateinit var dialog: AlertDialog
        fun action(label: String, primary: Boolean = false, block: () -> Unit) {
            box.addView(UiKit.button(this, p, label, primary).apply { setOnClickListener { dialog.dismiss(); block() } })
        }
        if (repo.allowFaceEnrollment) action(if (current.faceTemplate.isBlank()) "1 — تسجيل الوجه من خمس وضعيات" else "تحديث صور وبصمة الوجه", suggestFace) { requestFaceCapture(current) }
        if (repo.allowVoiceVerification) action(if (current.voiceTemplate.isBlank()) "2 — تسجيل نبرة الصوت (3 عينات)" else "تحديث نبرة الصوت (3 عينات)", suggestVoice && !suggestFace) { requestVoiceEnrollment(current) }
        if (current.companionEnabled) {
            action("3 — عرض QR ونقل ملف الموظف إلى هاتفه") { showProvisionQr(current) }
            action("4 — اختبار ارتباط الهاتف الآن") { testEmployeePhoneLink(current) }
        }
        dialog = AlertDialog.Builder(this).setTitle("تجهيز الموظف — ${current.displayName}").setView(scroll).setNegativeButton("إغلاق", null).create()
        dialog.show()
    }

    private fun testEmployeePhoneLink(employee: PairedEmployee) {
        if (!employee.companionEnabled) { info("هاتف الموظف", "ربط تطبيق الموظف معطل لهذا الموظف."); return }
        nearby.remove(employee.employeeId)
        runCatching { scanner.start() }
        runCatching { networkListener.start() }
        status.text = "اختبار ${employee.displayName}: افتح تطبيق الموظف وشغّل Bluetooth واتصل بنفس Wi‑Fi أو نقطة الاتصال…"
        info("اختبار ارتباط هاتف الموظف", "افتح تطبيق ATTEND PRO للموظف بعد مسح QR، وفعّل Bluetooth والموقع. اجعل الهاتفين على شبكة Wi‑Fi واحدة أو اربط جهاز المحل بنقطة اتصال هاتف الموظف. سيُفحص الارتباط لمدة 20 ثانية.")
        Handler(Looper.getMainLooper()).postDelayed({
            val seen = nearby[employee.employeeId]
            if (seen != null && System.currentTimeMillis() - seen.seenAt < 25_000L) {
                status.text = "✓ تم اكتشاف وربط هاتف ${employee.displayName} فعليًا"
                info("نجح اختبار الهاتف ✓", "تم التحقق من هوية الربط السرية عبر ${seen.channelTimes.keys.joinToString(" + ")}. قوة الإشارة ${seen.rssi} dBm. أصبح الهاتف جاهزًا للتعرف التلقائي وتأكيد الوجه/البصمة الأصلية.")
            } else {
                status.text = "لم يظهر هاتف ${employee.displayName} في الاختبار"
                info("لم يكتمل اختبار الهاتف", "تحقق من مسح QR الصحيح، وفتح تطبيق الموظف، ومنح الأجهزة القريبة والموقع، وإلغاء تقييد البطارية. فعّل Bluetooth واجعل الهاتفين على Wi‑Fi واحدة أو استخدم نقطة اتصال الموظف، ثم أعد الاختبار.")
            }
        }, 20_000L)
    }

    private fun askShowProvision(e:PairedEmployee){
        AlertDialog.Builder(this).setTitle("تم حفظ الموظف").setMessage("هل تريد الآن عرض QR تطبيق الموظف؟ هذه الخطوة اختيارية؛ الموظف مسجل في جهاز المحل حتى لو لم يستخدم التطبيق.")
            .setPositiveButton("عرض QR"){_,_->showProvisionQr(e)}.setNegativeButton("لاحقًا",null).show()
    }

    private fun showEmployeeDetails(e: PairedEmployee) {
        val faceDate = if (e.faceCapturedAt > 0L) SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(e.faceCapturedAt)) else ""
        val msg = buildString {
            append("الرقم: ${e.employeeId}\nالاسم: ${e.displayName}\nالفرع: ${e.branchId}")
            append("\nالدوام: ${employeeShiftLabel(e)}")
            if (e.jobTitle.isNotBlank()) append("\nالمسمى: ${e.jobTitle}")
            if (e.department.isNotBlank()) append("\nالقسم: ${e.department}")
            if (e.phone.isNotBlank()) append("\nالهاتف: ${e.phone}")
            if (e.nationalId.isNotBlank()) append("\nالهوية: ${e.nationalId}")
            if (e.hireDate.isNotBlank()) append("\nتاريخ التعيين: ${e.hireDate}")
            
            append("\nكلمة المرور: ${if (e.passwordHash.isBlank()) "غير مضافة" else "مضافة"}")
            
            append("\nالتحقق الصوتي: ${if (e.voiceTemplate.isBlank()) "غير مسجل" else "مسجل • جودة ${e.voiceQualityScore}/100"}")
            append("\nقارئ البصمة: ${e.externalFingerprintId.ifBlank { "غير مربوط" }}")
            append("\nالوجه: ${if (e.faceProfileRef.isBlank()) "غير مسجل" else "مسجل${if (faceDate.isNotBlank()) " • $faceDate" else ""}${if (e.faceTemplate.isNotBlank()) " • قالب جاهز ${e.faceQualityScore}/100" else " • يحتاج تحديث"}"}")
            append("\nتطبيق الموظف: ${if (e.companionEnabled) "مسموح" else "معطل"}")
            val linkedAt = repo.companionLinkedAt(e.employeeId)
            if (linkedAt > 0L) append("\nآخر ربط محلي مؤكد: ${repo.companionLinkedChannel(e.employeeId)} • ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(linkedAt))}")
            append("\nطرق الحضور: ${allowedMethodsArabic(e)}")
            append("\nالحالة: ${if (e.active) "نشط" else "موقوف"}")
            if (e.notes.isNotBlank()) append("\nملاحظات: ${e.notes}")
        }

        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 20), UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 20), UiKit.dp(this@MainActivity, 12))
        }
        scroll.addView(box)
        box.addView(UiKit.subtitle(this, p, msg).apply {
            setPadding(0, 0, 0, UiKit.dp(this@MainActivity, 10))
        })

        lateinit var dialog: AlertDialog
        fun addAction(label: String, primary: Boolean = false, action: () -> Unit) {
            box.addView(UiKit.button(this, p, label, primary).apply {
                setOnClickListener {
                    dialog.dismiss()
                    action()
                }
            })
        }

        addAction("تعديل البيانات الأساسية", true) { showEmployeeEditor(e) }
        addAction("إعداد الوجه والصوت وهاتف الموظف", true) { showEmployeeEnrollmentCenter(e) }
        addAction("إعدادات الموظف المتقدمة") { showEmployeeEditorAdvanced(e) }
        addAction("إجراءات أخرى") { showEmployeeOtherActions1976(e) }

        dialog = AlertDialog.Builder(this)
            .setTitle("ملف الموظف — ${e.displayName}")
            .setView(scroll)
            .setNegativeButton("إغلاق", null)
            .create()
        dialog.show()
    }

    private fun showEmployeeOtherActions1976(e: PairedEmployee) {
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

    private fun requestFaceCapture(employee: PairedEmployee) {
        faceEnrollmentTemplates.clear()
        faceEnrollmentQualities.clear()
        pendingFaceEmployeeId = employee.employeeId
        pendingFaceRecognition = false
        pendingFaceAttendanceAction = null
        AlertDialog.Builder(this).setTitle("تسجيل الوجه من 5 وضعيات")
            .setMessage("سنلتقط خمس صور: أمامية، يمين، يسار، لأعلى قليلًا، ولأسفل قليلًا. انزع النظارات الداكنة واجعل الوجه واضحًا وفي إضاءة ثابتة.")
            .setPositiveButton("بدء") { _, _ -> ensureCameraAndLaunch() }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun requestFaceRecognition(forcedAction: AttendanceAction? = null) {
        val candidates = repo.employees().filter { it.active && it.allows(AttendanceMethod.SHARED_DEVICE_FACE) && it.faceTemplate.isNotBlank() }
        if (candidates.isEmpty()) {
            info("بصمة الوجه", "لا يوجد موظف لديه قالب وجه جاهز. افتح إدارة المحل ← الموظفون ثم حدّث وجه الموظف.")
            return
        }
        pendingFaceAttendanceAction = forcedAction
        pendingFaceEmployeeId = null
        pendingFaceRecognition = true
        pendingLivenessEmployeeId = null
        pendingLivenessFirstTemplate = null
        pendingLivenessFirstScore = 0f
        ensureCameraAndLaunch()
    }

    private fun ensureCameraAndLaunch() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            if (::status.isInitialized) status.text = "اسمح بالكاميرا لإكمال بصمة الوجه"
            return
        }
        launchFaceCamera()
    }

    private fun launchFaceCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val dir = File(cacheDir, "face_capture").apply { mkdirs() }
        val file = File(dir, "face_${System.currentTimeMillis()}.jpg")
        pendingFaceCapturePath = file.absolutePath
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        cameraIntent.clipData = ClipData.newRawUri("ATTEND PRO face capture", uri)
        cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // بعض تطبيقات الكاميرا تتجاهل اختيار العدسة؛ هذه الإشارة تفضّل الأمامية دون الاعتماد عليها.
        cameraIntent.putExtra("android.intent.extras.CAMERA_FACING", 1)
        runCatching { startActivityForResult(cameraIntent, REQUEST_FACE_CAPTURE) }.onFailure {
            clearPendingFaceCapture()
            if (::status.isInitialized) status.text = "تعذر فتح الكاميرا: ${it.message ?: "خطأ"}"
            info("تعذر تسجيل الوجه", "لم يتمكن Android من فتح تطبيق الكاميرا. تحقق من وجود تطبيق كاميرا ومنح الصلاحية ثم أعد المحاولة. لم تتأثر بيانات الموظف.")
        }
    }

    private fun clearPendingFaceCapture(deleteTemp: Boolean = true) {
        if (deleteTemp) pendingFaceCapturePath?.let { runCatching { File(it).delete() } }
        pendingFaceCapturePath = null
        pendingFaceEmployeeId = null
        pendingFaceRecognition = false
        pendingFaceAttendanceAction = null
    }

    private fun decodeFaceBitmap(path: String): Bitmap? {
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        val orientation = runCatching { android.media.ExifInterface(path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun showFaceReference(employee: PairedEmployee) {
        val path = employee.faceProfileRef
        val bitmap = if (path.isBlank()) null else runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        if (bitmap == null) {
            status.text = "صورة الوجه غير متاحة"
            return
        }
        val image = ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds = true; setPadding(12, 12, 12, 12) }
        AlertDialog.Builder(this).setTitle("بصمة الوجه — ${employee.displayName}").setView(image)
            .setMessage("هذه الصورة المرجعية محفوظة محليًا وتُستخدم مع قالب الوجه للمطابقة المحلية. جودة المطابقة تعتمد على الإضاءة وزاوية الوجه، ولا تُعد بديلًا عن محرك عصبي مع فحص حيوية في النسخة التجارية النهائية.")
            .setPositiveButton("إغلاق", null).show()
    }

    private fun deleteFaceReference(employee: PairedEmployee) {
        deleteFaceFile(employee.faceProfileRef)
        repo.upsertEmployee(employee.copy(faceProfileRef = "", faceCapturedAt = 0L, faceTemplate = "", faceQualityScore = 0))
        status.text = "تم حذف بصمة الوجه لـ ${employee.displayName}"
    }

    private fun deleteFaceFile(path: String) {
        if (path.isNotBlank()) runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    private fun showProvisionQr(employee:PairedEmployee){
        val provision = repo.newProvision(employee)
        val fullProvision = OfflinePairingCache.capture(provision)
        val localCode = UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.US)
        // 1.9.57: bind the visible QR to this exact open Store pairing session.
        // AP5Q carries both the trusted compact provision and the short session code, so the
        // Employee can send an authenticated ACK back before either UI declares success.
        val qrProvision = PairingProtocol.encodeQrPairingEnvelope(localCode, provision)
        status.text = "تم تجهيز ربط محلي آمن — لا يحتاج إنترنت"
        showPairingTicketDialog(employee, localCode, fullProvision, qrProvision)
    }

    private fun showPairingTicketDialog(employee:PairedEmployee, localCode:String, fullProvision:String, qrProvision:String){
        OfflinePairingCache.captureEncoded(fullProvision)
        val shortContent = "APPAIR:$localCode"
        val qr = runCatching { QrCodeTools.bitmap(qrProvision,760) }.getOrElse { status.text="تعذر إنشاء QR: ${it.message ?: "خطأ"}"; return }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 10), UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 18))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        val linkState = TextView(this).apply {
            text="جاهز: QR مستقل، Bluetooth مستقل، وWi‑Fi/Hotspot مستقل — كلها تعمل بدون إنترنت"
            textSize=15f; gravity=Gravity.CENTER; setTextColor(p.accent); setPadding(0,8,0,10)
        }
        lateinit var beacon: PairingBeacon
        beacon = PairingBeacon(this,{message->runOnUiThread{linkState.text=message}}) { employeeId, channel ->
            runOnUiThread {
                repo.markCompanionLinked(employeeId, channel)
                linkState.text = "✓ اكتمل ربط هاتف ${employee.displayName} عبر $channel — ACK مشفّر مؤكد"
                status.text = "✓ هاتف ${employee.displayName} مرتبط محليًا عبر $channel"
                refreshDashboard()
            }
        }
        activePairingBeacon = beacon
        activePairingCode = localCode
        activePairingProvision = fullProvision
        activePairingMode = PairingBeacon.Mode.ALL

        content.addView(UiKit.title(this,p,"ربط هاتف ${employee.displayName}",18f).apply { gravity = Gravity.CENTER })
        content.addView(UiKit.subtitle(this,p,"كل طريقة تعمل وحدها. لا يحتاج الربط المحلي إلى الإنترنت؛ إذا توفر الإنترنت تتم المزامنة لاحقًا فقط.").apply { gravity = Gravity.CENTER })
        content.addView(linkState)

        val qrCard = UiKit.card(this,p,10)
        qrCard.addView(UiKit.sectionLabel(this,p,"1 — QR / باركود الربط"))
        qrCard.addView(UiKit.subtitle(this,p,"يمسح الموظف هذا الرمز من تطبيق الموظف. يحتوي بيانات الربط محليًا ولا ينتظر الخادم.").apply { gravity = Gravity.CENTER })
        qrCard.addView(ImageView(this).apply {
            setImageBitmap(qr); adjustViewBounds=true
            layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this@MainActivity,300))
        })
        qrCard.addView(UiKit.button(this,p,"نسخ ملف QR للطوارئ",false).apply { setOnClickListener {
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("ATTEND PRO QR", qrProvision))
            status.text="تم نسخ ملف QR المحلي"
        } })
        content.addView(qrCard)

        val btCard = UiKit.card(this,p,10)
        btCard.addView(UiKit.sectionLabel(this,p,"2 — Bluetooth مباشر"))
        btCard.addView(UiKit.subtitle(this,p,"يستلم هاتف الموظف ملف الربط عبر GATT ثم يعيد ACK مشفّرًا. لا يحتاج Wi‑Fi ولا إنترنت.").apply { gravity = Gravity.CENTER })
        btCard.addView(UiKit.button(this,p,"ابدأ الربط عبر Bluetooth فقط").apply { setOnClickListener {
            activePairingMode = PairingBeacon.Mode.BLUETOOTH_ONLY
            beacon.start(localCode, fullProvision, activePairingMode)
        } })
        content.addView(btCard)

        val lanCard = UiKit.card(this,p,10)
        lanCard.addView(UiKit.sectionLabel(this,p,"3 — Wi‑Fi / نقطة اتصال"))
        lanCard.addView(UiKit.subtitle(this,p,"اجعل الهاتفين على نفس Wi‑Fi أو نقطة الاتصال. يتم اكتشاف جهاز المحل محليًا ثم Provision + ACK بدون خادم.").apply { gravity = Gravity.CENTER })
        lanCard.addView(UiKit.button(this,p,"ابدأ الربط عبر Wi‑Fi / Hotspot فقط").apply { setOnClickListener {
            activePairingMode = PairingBeacon.Mode.WIFI_HOTSPOT_ONLY
            beacon.start(localCode, fullProvision, activePairingMode)
        } })
        content.addView(lanCard)

        val autoCard = UiKit.card(this,p,10)
        autoCard.addView(UiKit.sectionLabel(this,p,"تشغيل تلقائي"))
        autoCard.addView(UiKit.button(this,p,"شغّل Bluetooth + Wi‑Fi معًا",false).apply { setOnClickListener {
            activePairingMode = PairingBeacon.Mode.ALL
            beacon.start(localCode, fullProvision, activePairingMode)
        } })
        autoCard.addView(UiKit.button(this,p,"نسخ رمز الربط القصير: $localCode",false).apply { setOnClickListener {
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("ATTEND PRO",shortContent))
            status.text="تم نسخ رمز ربط ${employee.displayName}"
        } })
        autoCard.addView(UiKit.button(this,p,"إرسال ملف الربط الكامل",false).apply { setOnClickListener {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,fullProvision) },"إرسال ربط الموظف"))
        } })
        content.addView(autoCard)

        val dialog = AlertDialog.Builder(this)
            .setTitle("ربط هاتف الموظف")
            .setView(scroll)
            .setPositiveButton("إغلاق",null)
            .create()
        dialog.setOnDismissListener{
            beacon.stop()
            if (activePairingBeacon === beacon) {
                activePairingBeacon = null; activePairingCode = ""; activePairingProvision = ""; activePairingMode = PairingBeacon.Mode.ALL
            }
        }
        dialog.setOnShowListener {
            // Default to both local radios for easiest first use; each dedicated button above can isolate a single transport.
            activePairingMode = PairingBeacon.Mode.ALL
            beacon.start(localCode, fullProvision, activePairingMode)
        }
        dialog.show()
    }

    private fun showPresenceChallenge(forcedAction: AttendanceAction? = null) {
        val employees = repo.employees().filter { it.active && it.companionEnabled }
        if (employees.isEmpty()) { info("إثبات الوجود", "لا يوجد موظف نشط مرتبط بتطبيق الموظف."); return }
        val names = employees.map { e ->
            val seen = nearby[e.employeeId]?.let { System.currentTimeMillis() - it.seenAt < 120_000L } == true
            "${e.displayName} ${if (seen) "• قريب الآن" else "• غير ظاهر حاليًا"}"
        }
        AlertDialog.Builder(this).setTitle("اختر الموظف").setItems(names.toTypedArray()) { _, i ->
            val e = employees[i]
            val options = mutableListOf<Pair<String, AttendanceMethod>>()
            if (e.allows(AttendanceMethod.PHONE_BLE_BIOMETRIC)) options += "◎ بصمة/وجه الهاتف" to AttendanceMethod.PHONE_BLE_BIOMETRIC
            else if (e.allows(AttendanceMethod.PHONE_FINGERPRINT)) options += "◎ بصمة إصبع الهاتف" to AttendanceMethod.PHONE_FINGERPRINT
            if (e.allows(AttendanceMethod.PASSWORD)) options += "▣ كلمة المرور" to AttendanceMethod.PASSWORD
            if (options.isEmpty()) { info("إثبات الوجود", "لا توجد طريقة تحقق للهاتف مفعلة لهذا الموظف."); return@setItems }
            AlertDialog.Builder(this).setTitle("إثبات وجود ${e.displayName}").setItems(options.map { it.first }.toTypedArray()) { _, j ->
                val chosen = options[j]
                val requestedAction = forcedAction ?: nextAction(e.employeeId)
                status.text = "جاري إرسال طلب إثبات ${if (requestedAction == AttendanceAction.CHECK_IN) "حضور" else "انصراف"} إلى هاتف ${e.displayName}…"
                Thread {
                    val secret = SecretCodec.decode(e.pairingSecret)
                    val requestToken = java.security.SecureRandom().nextInt()
                    val expiresAt = System.currentTimeMillis() + 60_000L
                    val directSent = if (secret != null) directBle.sendChallenge(e.employeeId, chosen.second, expiresAt, requestToken, requestedAction) else false
                    val lanSent = if (!directSent && secret != null && isLanConnected(e.employeeId)) LocalChallengeSender.send(e.employeeId, secret, chosen.second, System.currentTimeMillis(), requestToken, requestedAction) else false
                    val localSent = directSent || lanSent
                    // One request, one transport: prefer an already verified local channel.
                    // Only fall back to the server when neither BLE-ACK nor LAN-ACK could deliver it.
                    if (localSent) {
                        runOnUiThread {
                            repo.addPresenceEvent(PresenceEvent(employeeId=e.employeeId, employeeName=e.displayName, timestampEpochMillis=System.currentTimeMillis(), channel="طلب تحقق", rssi=nearby[e.employeeId]?.rssi?:-127, details="أُرسل عبر ${if (directSent) "Bluetooth GATT ACK" else "LAN ACK"}: ${chosen.first}"))
                            status.text = "✓ أُرسل الطلب محليًا إلى ${e.displayName}"
                            if (repo.attendanceVoiceAnnouncementEnabled) voiceAnnouncer.announceRequestSent(e.displayName)
                            refreshDashboard()
                            info("أُرسل محليًا ✓", "تم استخدام قناة واحدة موثقة فقط لمنع تكرار نفس طلب الإثبات.")
                        }
                    } else {
                        val result = if (repo.hasCentralCredentials()) {
                            CentralServerClient.createPresenceChallenge(repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this), e.employeeId, chosen.second, requestedAction)
                        } else Result.failure(IllegalStateException("لا توجد قناة محلية موثقة ولا ربط خادم مهيأ"))
                        runOnUiThread { result.onSuccess { challenge ->
                            repo.addPresenceEvent(PresenceEvent(employeeId=e.employeeId, employeeName=e.displayName, timestampEpochMillis=System.currentTimeMillis(), channel="طلب تحقق", rssi=nearby[e.employeeId]?.rssi?:-127, details="أُرسل عبر الخادم: ${chosen.first}"))
                            status.text = "✓ أُرسل طلب التحقق عبر الخادم إلى ${e.displayName}"
                            if (repo.attendanceVoiceAnnouncementEnabled) voiceAnnouncer.announceRequestSent(e.displayName)
                            monitorPresenceChallenge(e, challenge, requestedAction)
                            refreshDashboard()
                        }.onFailure { error ->
                            info("تعذر إرسال الطلب", error.message ?: "لا توجد قناة اتصال موثقة متاحة")
                        } }
                    }
                }.start()
            }.setNegativeButton("إلغاء", null).show()
        }.setNegativeButton("إلغاء", null).show()
}

    private fun showDirectQrAttendance1937(forcedAction: AttendanceAction? = null) {
        val employees = repo.employees().filter { it.active && it.companionEnabled }
        if (employees.isEmpty()) { info("QR مباشر", "لا يوجد موظف مرتبط بتطبيق الموظف."); return }
        AlertDialog.Builder(this).setTitle("QR مباشر — اختر الموظف").setItems(employees.map { it.displayName }.toTypedArray()) { _, which ->
            val e = employees[which]
            val secret = SecretCodec.decode(e.pairingSecret)
            if (secret == null) { info("QR مباشر", "مفتاح ربط هذا الموظف غير صالح. أعد ربط الهاتف."); return@setItems }
            if (!e.allows(AttendanceMethod.PHONE_PROXIMITY)) { info("QR مباشر", "QR غير مسموح لهذا الموظف من إعدادات طرق الحضور."); return@setItems }
            AttendanceQrPresenter.show(this, e.employeeId, secret, AttendanceMethod.PHONE_PROXIMITY, System.currentTimeMillis() + 90_000L, forcedAction ?: nextAction(e.employeeId))
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun monitorPresenceChallenge(employee: PairedEmployee, challenge: CentralServerClient.PresenceChallenge, requestedAction: AttendanceAction? = challenge.action) {
        val actionText = when (requestedAction) { AttendanceAction.CHECK_IN -> "حضور"; AttendanceAction.CHECK_OUT -> "انصراف"; null -> "حضور/انصراف" }
        info("أُرسل الطلب ✓","سيظهر إشعار إثبات $actionText في هاتف ${employee.displayName}. الطلب صالح لمدة دقيقتين ولن يُقبل إلا عبر ${methodLabel(challenge.requiredMethod)}.")
        Thread {
            repeat(24) {
                val current=CentralServerClient.presenceChallengeStatus(repo.serverUrl,repo.centralAccessToken,repo.storeId,DeviceIdentity(this),challenge.challengeId).getOrNull()
                if(current?.status=="VERIFIED") {
                    runOnUiThread {
                        repo.addPresenceEvent(PresenceEvent(employeeId=employee.employeeId,employeeName=employee.displayName,timestampEpochMillis=current.verifiedAt,channel="إثبات وجود",rssi=nearby[employee.employeeId]?.rssi?:-127,details="تم عبر ${methodLabel(current.requiredMethod)} • ${current.evidence}"))
                        val attendanceMethod = when (current.requiredMethod) {
                            AttendanceMethod.PASSWORD -> AttendanceMethod.PASSWORD
                            AttendanceMethod.PHONE_BLE_BIOMETRIC,
                            AttendanceMethod.PHONE_FINGERPRINT -> current.requiredMethod
                            // PHONE_BIOMETRIC is an old internal value; normalize it to the current
                            // native phone biometric method instead of exposing the legacy label.
                            AttendanceMethod.PHONE_BIOMETRIC -> AttendanceMethod.PHONE_BLE_BIOMETRIC
                            else -> null
                        }
                        if (attendanceMethod != null) {
                            val resolvedAction = current.action ?: requestedAction
                            val last = repo.lastEvent(employee.employeeId)
                            val alreadyRecordedLocally = last != null &&
                                last.timestampEpochMillis >= challenge.createdAt - 5_000L &&
                                (resolvedAction == null || last.action == resolvedAction)
                            if (alreadyRecordedLocally) {
                                status.text = "✓ تم تأكيد ${employee.displayName} عبر الخادم — الحركة المحلية المطابقة مسجلة مسبقًا"
                            } else {
                                recordVerified(employee, attendanceMethod, "إثبات challenge عبر الخادم • ${current.evidence}", resolvedAction)
                            }
                        } else {
                            status.text = "✓ تم إثبات وجود ${employee.displayName} عبر ${methodLabel(current.requiredMethod)} — لم تُسجل حركة حضور لأن هذه طريقة قديمة/غير معتمدة للحضور"
                        }
                        refreshDashboard()
                    };return@Thread
                }
                if(current?.status=="EXPIRED") { runOnUiThread{status.text="انتهت مهلة إثبات وجود ${employee.displayName}"};return@Thread }
                try{Thread.sleep(5_000L)}catch(_:InterruptedException){return@Thread}
            }
        }.start()
    }

    private fun recordManual(e:PairedEmployee){
        if (!ensureOperationalActivation()) return
        val action=nextAction(e.employeeId)
        repo.addEvent(AttendanceEvent(employeeId=e.employeeId,employeeName=e.displayName,branchId=e.branchId,timestampEpochMillis=System.currentTimeMillis(),action=action,method=AttendanceMethod.SUPERVISOR_OVERRIDE,deviceId=deviceId(),verified=true))
        status.text="✓ ${e.displayName}: ${if(action==AttendanceAction.CHECK_IN)"حضور" else "انصراف"} بإشراف";refreshDashboard()
    }

    private fun showPasswordDialog(forcedAction: AttendanceAction? = null) {
        showCredentialDialog("التسجيل بكلمة المرور", "كلمة المرور", AttendanceMethod.PASSWORD, forcedAction) { e -> e.passwordHash }
    }

    private fun showCredentialDialog(title: String, credentialLabel: String, method: AttendanceMethod, forcedAction: AttendanceAction? = null, stored: (PairedEmployee) -> String) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
        val id = UiKit.field(this, p, "رقم الموظف")
        val credential = UiKit.field(this, p, credentialLabel, true).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        box.addView(id); box.addView(credential)
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(box).setPositiveButton("تسجيل", null).setNegativeButton("إلغاء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val employee = findActiveEmployee(id.text.toString()) ?: run { id.error = "الموظف غير موجود أو موقوف"; return@setOnClickListener }
                if (!employee.allows(method)) { credential.error = "هذه الطريقة غير مسموحة لهذا الموظف"; return@setOnClickListener }
                val saved = stored(employee)
                if (saved.isBlank()) { credential.error = "لم يتم إعداد $credentialLabel لهذا الموظف"; return@setOnClickListener }
                if (!PairingProtocol.matchesPin(saved, credential.text.toString())) { credential.error = "$credentialLabel غير صحيح"; return@setOnClickListener }
                recordVerified(employee, method, forcedAction = forcedAction); dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun requestVoiceEnrollment(employee: PairedEmployee) {
        pendingVoiceEnrollmentEmployeeId = employee.employeeId
        voiceEnrollmentTemplates.clear(); voiceEnrollmentQualities.clear()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO_PERMISSION)
            return
        }
        promptVoiceEnrollmentSample()
    }

    private fun promptVoiceEnrollmentSample() {
        val employee = pendingVoiceEnrollmentEmployeeId?.let { id -> repo.employees().firstOrNull { it.employeeId.equals(id, true) } } ?: return
        val number = voiceEnrollmentTemplates.size + 1
        AlertDialog.Builder(this).setTitle("تسجيل نبرة الصوت $number/5")
            .setMessage("قل العبارة نفسها كاملة بصوت طبيعي. سجّل في وضعين هادئين ومسافتين قريبتين حتى يتعلم النظام اختلاف نبرة الصوت الطبيعية.")
            .setPositiveButton("ابدأ التسجيل") { _, _ ->
                status.text = "جارٍ تسجيل نبرة ${employee.displayName}…"
                VoiceSignatureEngine.capture { capture -> runOnUiThread {
                    if (capture.template.isBlank()) {
                        info("جودة التسجيل", capture.error.ifBlank { "تعذر تكوين بصمة صوتية" })
                        promptVoiceEnrollmentSample()
                        return@runOnUiThread
                    }
                    if (capture.quality < 42) {
                        info("جودة التسجيل منخفضة", "الجودة ${capture.quality}/100. أعد التسجيل في مكان أهدأ، واجعل الهاتف على بعد 20-40 سم، وتكلم بصوت طبيعي.")
                        promptVoiceEnrollmentSample()
                        return@runOnUiThread
                    }
                    voiceEnrollmentTemplates.add(capture.template); voiceEnrollmentQualities.add(capture.quality)
                    if (voiceEnrollmentTemplates.size < 5) {
                        status.text = "✓ تم التسجيل ${voiceEnrollmentTemplates.size}/5 • جودة ${capture.quality}/100"
                        promptVoiceEnrollmentSample()
                    } else {
                        val pairScores = listOf(
                            VoiceSignatureEngine.similarity(voiceEnrollmentTemplates[0], voiceEnrollmentTemplates[1]),
                            VoiceSignatureEngine.similarity(voiceEnrollmentTemplates[0], voiceEnrollmentTemplates[2]),
                            VoiceSignatureEngine.similarity(voiceEnrollmentTemplates[1], voiceEnrollmentTemplates[2]),
                            VoiceSignatureEngine.similarity(voiceEnrollmentTemplates[2], voiceEnrollmentTemplates[3]),
                            VoiceSignatureEngine.similarity(voiceEnrollmentTemplates[3], voiceEnrollmentTemplates[4])
                        )
                        if (pairScores.sortedDescending().take(3).average() < 0.44) {
                            voiceEnrollmentTemplates.clear(); voiceEnrollmentQualities.clear()
                            info("العينات غير متناسقة", "اختلفت التسجيلات كثيرًا. أعد العبارة نفسها خمس مرات بصوت طبيعي وتأكد من عدم تغطية الميكروفون.")
                            promptVoiceEnrollmentSample(); return@runOnUiThread
                        }
                        val packed = voiceEnrollmentTemplates.fold("") { acc, item -> VoiceSignatureEngine.appendTemplate(acc, item) }
                        val quality = voiceEnrollmentQualities.average().toInt()
                        repo.upsertEmployee(employee.copy(voiceTemplate = packed, voiceQualityScore = quality))
                        pendingVoiceEnrollmentEmployeeId = null; voiceEnrollmentTemplates.clear(); voiceEnrollmentQualities.clear()
                        status.text = "✓ تم حفظ بصمة نبرة ${employee.displayName} • جودة $quality/100"
                        info("تم تسجيل الصوت", "حُفظت 5 عينات صوتية محليًا. يستخدم النظام أفضل تطابقات مع فحص جودة الصوت ثم يتحقق من العبارة وكلمة التحدي المتغيرة.")
                    }
                } }
            }.setNegativeButton("إلغاء") { _, _ -> pendingVoiceEnrollmentEmployeeId = null; voiceEnrollmentTemplates.clear(); voiceEnrollmentQualities.clear() }.show()
    }

    private fun beginVoicePhraseRecognition(employee: PairedEmployee, dialog: AlertDialog? = null) {
        val challenge = listOf("شمس", "قمر", "نهر", "باب", "نور", "كتاب").random()
        pendingVoiceChallenge = challenge
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, resolvedSpeechLanguage())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 8)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "انطق عبارتك المسجلة ثم قل كلمة: $challenge")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        runCatching { startActivityForResult(intent, REQUEST_VOICE_VERIFY) }.onSuccess { dialog?.dismiss() }.onFailure {
            pendingVoiceEmployeeId = null; pendingVoicePrintScore = -1f; pendingVoiceAttendanceAction = null
            info("التحقق الصوتي", "لا توجد خدمة تعرف صوتي متاحة على هذا الجهاز: ${it.message ?: "خطأ"}")
        }
    }

    private fun captureVoiceForVerification(employee: PairedEmployee, dialog: AlertDialog? = null) {
        status.text = "قل عبارتك الآن — جارٍ تحليل الصوت لمدة 4 ثوانٍ…"
        VoiceSignatureEngine.capture { capture -> runOnUiThread {
            if (capture.template.isBlank()) { info("جودة الصوت", capture.error); return@runOnUiThread }
            if (capture.quality < 40) {
                pendingVoiceEmployeeId = null; pendingVoicePrintScore = -1f
                info("جودة الصوت منخفضة", "الجودة ${capture.quality}/100. أعد المحاولة في مكان أهدأ وعلى بعد 20-40 سم من الهاتف.")
                return@runOnUiThread
            }
            val score = VoiceSignatureEngine.similarity(capture.template, employee.voiceTemplate)
            val requiredVoiceScore = adaptiveVoiceThreshold(capture.quality, employee.voiceQualityScore)
            if (score < requiredVoiceScore) {
                pendingVoiceEmployeeId = null; pendingVoicePrintScore = -1f
                info("لم تتطابق نبرة الصوت", "درجة المطابقة ${(score * 100).toInt()}% (المطلوب ${(requiredVoiceScore*100).toInt()}%). تكلم طبيعيًا، أو حدّث بصمة الصوت من ملف الموظف.")
                return@runOnUiThread
            }
            pendingVoicePrintScore = score
            beginVoicePhraseRecognition(employee, dialog)
        } }
    }

    private fun showVoiceDialog(forcedAction: AttendanceAction? = null) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 0) }
        val id = UiKit.field(this, p, "رقم الموظف")
        box.addView(UiKit.subtitle(this, p, "يطابق النظام نبرة الموظف المسجلة أولًا، ثم يطلب العبارة وكلمة تحدٍ متغيرة. يلزم تسجيل 3 عينات صوتية في ملف الموظف."))
        box.addView(id)
        val dialog = AlertDialog.Builder(this).setTitle("العبارة الصوتية").setView(box).setPositiveButton("بدء الاستماع", null).setNegativeButton("إلغاء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val employee = findActiveEmployee(id.text.toString()) ?: run { id.error = "الموظف غير موجود أو موقوف"; return@setOnClickListener }
                if (!employee.allows(AttendanceMethod.VOICE_PHRASE)) { id.error = "التحقق الصوتي غير مسموح لهذا الموظف"; return@setOnClickListener }
                if (employee.voicePhraseHash.isBlank()) { id.error = "لم يتم إعداد عبارة صوتية لهذا الموظف"; return@setOnClickListener }
                if (employee.voiceTemplate.isBlank()) { id.error = "لم تُسجل بصمة نبرة هذا الموظف؛ حدّث ملفه وسجل 3 عينات"; return@setOnClickListener }
                if (!employee.voiceTemplate.contains("vs2:")) { id.error = "بصمة الصوت قديمة؛ حدّث ملف الموظف وسجل العينات الجديدة"; return@setOnClickListener }
                pendingVoiceEmployeeId = employee.employeeId
                pendingVoiceAttendanceAction = forcedAction
                pendingVoicePrintScore = -1f
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO_PERMISSION)
                } else captureVoiceForVerification(employee, dialog)
            }
        }
        dialog.show()
    }

    @Suppress("MissingPermission")
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

    private fun isMockLocation(location: Location): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else @Suppress("DEPRECATION") location.isFromMockProvider

    private fun findActiveEmployee(rawId: String): PairedEmployee? = repo.employees().firstOrNull { it.active && it.employeeId.equals(rawId.trim(), true) }


    private fun ensureOperationalActivation(): Boolean {
        if (repo.isCentralActivationActive()) return true
        runCatching { scanner.stop() }
        runCatching { networkListener.stop() }
        buildActivationLockUi()
        info("التشغيل موقوف", "يتطلب تسجيل الحضور تفعيلًا مركزيًا صالحًا من إدارة النظام. إذا كان الجهاز غير متصل بالإنترنت، أعد الاتصال للتحقق من الصلاحية.")
        return false
    }

    private fun recordVerified(employee: PairedEmployee, method: AttendanceMethod, extra: String = "", forcedAction: AttendanceAction? = null) {
        if (!ensureOperationalActivation()) return
        val now = System.currentTimeMillis()
        val previous = repo.lastEvent(employee.employeeId)
        if (previous != null && now - previous.timestampEpochMillis < 30_000L) {
            status.text = "تم تجاهل محاولة مكررة لـ ${employee.displayName} — انتظر 30 ثانية"
            return
        }
        val action = forcedAction ?: nextAction(employee.employeeId)
        if (action == AttendanceAction.CHECK_OUT && repo.checkoutSignatureEnabled) {
            requestCheckoutSignature(employee, method, extra)
            return
        }
        commitVerified(employee, method, extra, action)
    }

    private fun commitVerified(employee: PairedEmployee, method: AttendanceMethod, extra: String, action: AttendanceAction) {
        val now = System.currentTimeMillis()
        repo.addEvent(AttendanceEvent(employeeId = employee.employeeId, employeeName = employee.displayName, branchId = employee.branchId,
            timestampEpochMillis = now, action = action, method = method, deviceId = deviceId(), verified = true, evidence = extra))
        status.text = "✓ ${employee.displayName}: ${if (action == AttendanceAction.CHECK_IN) "حضور" else "انصراف"} — ${methodLabel(method)}${if (extra.isNotBlank()) " • $extra" else ""}"
        if (repo.attendanceVoiceAnnouncementEnabled) voiceAnnouncer.announceAttendance(employee.displayName, action)
        refreshDashboard()
    }

    private fun requestCheckoutSignature(employee: PairedEmployee, method: AttendanceMethod, extra: String) {
        val signature = SignaturePadView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0)
            addView(UiKit.subtitle(this@MainActivity, p, "وقّع داخل المساحة أدناه لتأكيد الانصراف. لا تُحفظ صورة التوقيع؛ يُحفظ إثبات رقمي مشفر فقط."))
            addView(signature, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this@MainActivity, 220)))
        }
        val dialog = AlertDialog.Builder(this).setTitle("توقيع الانصراف — ${employee.displayName}").setView(box)
            .setPositiveButton("تأكيد الانصراف", null).setNeutralButton("مسح", null).setNegativeButton("إلغاء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { signature.clear() }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!signature.hasSignature()) { info("التوقيع مطلوب", "وقّع داخل المساحة أولًا، أو عطّل خيار توقيع الانصراف من إعدادات طرق الحضور."); return@setOnClickListener }
                val evidence = listOf(extra, "توقيع انصراف SHA-256:${signature.digest()}").filter { it.isNotBlank() }.joinToString(" • ")
                dialog.dismiss(); commitVerified(employee, method, evidence, AttendanceAction.CHECK_OUT)
            }
        }
        dialog.show()
    }

    private fun showVoiceAnnouncerSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 8))
        }
        val enabled = CheckBox(this).apply { text = "تشغيل الناطق الصوتي للحضور"; setTextColor(p.text); isChecked = repo.attendanceVoiceAnnouncementEnabled }
        val modeLabels = arrayOf("الوضع الآلي", "الوضع المخصص")
        val mode = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, modeLabels); setSelection(if (repo.voiceMode == "CUSTOM") 1 else 0) }
        val rate = UiKit.field(this, p, "سرعة النطق %").apply { setText(repo.voiceRatePercent.toString()); isFocusable=false; isClickable=true; setOnClickListener { FormPickerHelper.pickNumber(this@MainActivity, this, 50, 150, "سرعة النطق %") } }
        val volume = UiKit.field(this, p, "مستوى الصوت %").apply { setText(repo.voiceVolumePercent.toString()); isFocusable=false; isClickable=true; setOnClickListener { FormPickerHelper.pickNumber(this@MainActivity, this, 0, 100, "مستوى الصوت %") } }
        val voices = voiceAnnouncer.availableVoices()
        val voiceValues = mutableListOf("").apply { addAll(voices.map { it.first }) }
        val voiceLabels = mutableListOf("الصوت الافتراضي في Android").apply { addAll(voices.map { it.second }) }
        val voice = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, voiceLabels)
            val index = voiceValues.indexOf(repo.voiceName).takeIf { it >= 0 } ?: 0; setSelection(index)
        }
        val sent = UiKit.field(this, p, "جملة إرسال الطلب — استخدم {name} لاسم الموظف").apply { setText(repo.voiceRequestSentText) }
        val missing = UiKit.field(this, p, "جملة موجود ولم يثبت الحضور").apply { setText(repo.voiceMissingProofText) }
        val late = UiKit.field(this, p, "جملة التأخر").apply { setText(repo.voiceLateText) }
        box.addView(enabled); box.addView(UiKit.subtitle(this, p, "وضع الناطق")); box.addView(mode)
        box.addView(rate); box.addView(volume); box.addView(UiKit.subtitle(this, p, if (voices.isEmpty()) "أصوات Android ستظهر بعد جاهزية محرك TTS؛ سيستخدم الصوت الافتراضي حاليًا" else "اختر صوت Android المتاح")); box.addView(voice)
        box.addView(UiKit.sectionLabel(this, p, "الجمل المخصصة")); listOf(sent, missing, late).forEach { box.addView(it) }
        AlertDialog.Builder(this).setTitle("الناطق الصوتي للحضور").setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("حفظ") { _, _ ->
                repo.attendanceVoiceAnnouncementEnabled = enabled.isChecked
                repo.voiceMode = if (mode.selectedItemPosition == 1) "CUSTOM" else "AUTO"
                repo.voiceRatePercent = rate.text.toString().toIntOrNull() ?: 90
                repo.voiceVolumePercent = volume.text.toString().toIntOrNull() ?: 100
                repo.voiceName = voiceValues.getOrElse(voice.selectedItemPosition) { "" }
                repo.voiceRequestSentText = sent.text.toString().trim().ifBlank { "تم إرسال طلب إثبات حضور إلى {name}" }
                repo.voiceMissingProofText = missing.text.toString().trim().ifBlank { "{name} لم يثبت الحضور" }
                repo.voiceLateText = late.text.toString().trim().ifBlank { "الموظف {name} لم يسجل الحضور في الموعد المحدد" }
                voiceAnnouncer.applyPreferences(); status.text = "✓ تم حفظ إعدادات الناطق الصوتي"
            }.setNeutralButton("تجربة") { _, _ -> voiceAnnouncer.speak("تجربة الناطق الصوتي لنظام الحضور") }.setNegativeButton("إلغاء", null).show()
    }

    private fun showReportsDialog(){
        val recent=repo.events().takeLast(30).reversed();val fmt=SimpleDateFormat("dd/MM HH:mm",Locale.getDefault())
        val text=if(recent.isEmpty())"لا توجد عمليات بعد" else buildString{recent.forEach{e->append("• ${e.employeeName.ifBlank{e.employeeId}} — ${if(e.action==AttendanceAction.CHECK_IN)"حضور" else "انصراف"} — ${fmt.format(Date(e.timestampEpochMillis))} — ${e.method.name}\n")}}
        AlertDialog.Builder(this).setTitle("السجل والتقارير").setMessage(text).setPositiveButton("مشاركة CSV"){_,_->shareTodayCsv()}.setNegativeButton("إغلاق",null).show()
    }

    private fun markDirectBleState(employeeId: String, connected: Boolean, message: String) {
        val employee = repo.employees().firstOrNull { it.employeeId.equals(employeeId, true) } ?: return
        val now = System.currentTimeMillis()
        val previous = nearby[employeeId]
        if (connected) {
            repo.markCompanionLinked(employeeId, "Bluetooth BLE • ACK", now)
            val channels = previous?.channelTimes.orEmpty().toMutableMap().apply { put("Bluetooth مباشر", now) }
            nearby[employeeId] = NearbyPhone(now, previous?.rssi ?: -60, channels, previous?.deviceName.orEmpty(), previous?.gpsInsideAt ?: 0L)
            val lastDirect = previous?.channelTimes?.get("Bluetooth مباشر") ?: 0L
            if (now - lastDirect > 10_000L) repo.addPresenceEvent(PresenceEvent(employeeId=employeeId, employeeName=employee.displayName, timestampEpochMillis=now, channel="Bluetooth مباشر", rssi=previous?.rssi?:-60, details="اتصال GATT مشفر ومؤكد مع هاتف الموظف"))
        }
        if (::status.isInitialized) status.text = "$message — ${employee.displayName}"
        refreshDashboard()
    }

    private fun directBleConfig(): BleDirectProtocol.Config {
        val gpsConfigured = repo.gpsRecognitionEnabled && repo.isGpsConfigured
        return BleDirectProtocol.Config(
            latitude = if (gpsConfigured) repo.storeLatitude else 0.0,
            longitude = if (gpsConfigured) repo.storeLongitude else 0.0,
            radiusMeters = repo.gpsRadiusMeters,
            gpsConfigured = gpsConfigured,
            voicePromptsEnabled = repo.employeeVoicePromptsEnabled,
            geoAlertsEnabled = repo.employeeGeoArrivalAlertsEnabled,
            shiftStartHour = repo.shiftHour,
            shiftStartMinute = repo.shiftMinute,
            shiftEndHour = repo.shiftEndHour,
            shiftEndMinute = repo.shiftEndMinute
        )
    }

    private fun handleBlePayload(payload:BleProtocol.Payload,rssi:Int,channel:String,device:android.bluetooth.BluetoothDevice?=null): ByteArray? {
        if(!repo.allowEmployeeCompanion) return null
        val employees=repo.employees().filter{it.active&&it.companionEnabled&&BleProtocol.employeeHash(it.employeeId)==payload.employeeHash}
        for(employee in employees){
            val secret=SecretCodec.decode(employee.pairingSecret)?:continue
            if(!BleProtocol.verify(payload,employee.employeeId,secret))continue
            // LAN is promoted to a real connection only when even a discovery frame carries
            // a valid rotating HMAC token. BLE discovery may remain unauthenticated because
            // the direct GATT heartbeat performs the authentication before "connected".
            val authenticatedTransport = BleProtocol.verifyAuthenticated(payload, employee.employeeId, secret)
            if (channel == "Wi‑Fi/Hotspot" && !authenticatedTransport) continue
            val detectedAt = System.currentTimeMillis()
            val previous = nearby[employee.employeeId]
            val channels = previous?.channelTimes.orEmpty().toMutableMap().apply {
                put(channel, detectedAt)
                if (authenticatedTransport) put("$channel • HMAC", detectedAt)
            }

            // 1.9.65: Bluetooth discovery and Bluetooth authentication are deliberately separate.
            // After QR pairing some phones advertise a discovery-only/legacy frame first. That frame
            // is enough to identify which linked employee owns the connectable BLE device, but it must
            // NEVER be treated as a connected session by itself. Start (or refresh) the direct GATT
            // client from every valid linked-employee BLE sighting; BleDirectLinkClient only reports
            // connected after the signed PING -> ACK -> ACK-confirm handshake succeeds.
            if (channel == "Bluetooth" && device != null) {
                if (!directBle.isConnected(employee.employeeId)) {
                    // Stop active scanning before GATT setup to reduce status=133 failures on Samsung,
                    // Motorola and other OEM stacks. Wi-Fi/Hotspot and Server remain independent.
                    scanner.pauseForGatt(3_000L)
                }
                directBle.touch(employee.employeeId, secret, device, directBleConfig())
            }

            if (authenticatedTransport) {
                authenticatedPresenceAt[employee.employeeId] = detectedAt
                repo.markCompanionLinked(employee.employeeId, "$channel • HMAC", detectedAt)
            }
            val canReadBluetoothName = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val resolvedDeviceName = if (channel == "Bluetooth" && device != null && canReadBluetoothName) {
                runCatching { device.name.orEmpty() }.getOrDefault("")
            } else previous?.deviceName.orEmpty()
            val gpsInsideAt = previous?.gpsInsideAt ?: 0L
            nearby[employee.employeeId]=NearbyPhone(detectedAt,maxOf(rssi, previous?.rssi ?: -127),channels,resolvedDeviceName,gpsInsideAt)

            // Presence broadcasts can arrive many times per second. Keep the live card fresh,
            // but throttle the persisted audit log so SharedPreferences does not grow without bound.
            val logKey = "${employee.employeeId}|$channel"
            if (detectedAt - (lastPresenceLogAt[logKey] ?: 0L) >= 10_000L) {
                lastPresenceLogAt[logKey] = detectedAt
                repo.addPresenceEvent(PresenceEvent(employeeId = employee.employeeId, employeeName = employee.displayName,
                    timestampEpochMillis = detectedAt, channel = channel, rssi = rssi,
                    details = if (authenticatedTransport) "تم التحقق من هوية الربط المشفرة" else "الهاتف قريب؛ بانتظار ACK مباشر موثق"))
            }
            if (payload.verified && payload.challengeProof) {
                repo.addPresenceEvent(PresenceEvent(employeeId=employee.employeeId,employeeName=employee.displayName,
                    timestampEpochMillis=detectedAt,channel="إثبات وجود محلي",rssi=rssi,
                    details="تم التحقق بالطريقة الإلزامية من هاتف الموظف"))
                val challengeMethod = when {
                    payload.credentialProof && employee.allows(AttendanceMethod.PASSWORD) -> AttendanceMethod.PASSWORD
                    payload.biometricProof && employee.allows(AttendanceMethod.PHONE_BLE_BIOMETRIC) -> AttendanceMethod.PHONE_BLE_BIOMETRIC
                    payload.biometricProof && employee.allows(AttendanceMethod.PHONE_FINGERPRINT) -> AttendanceMethod.PHONE_FINGERPRINT
                    else -> null
                }
                if (challengeMethod != null) {
                    maybeRecordPhoneProof(employee, payload.token, challengeMethod, "إثبات challenge محلي موثق", if (payload.checkoutProof) AttendanceAction.CHECK_OUT else AttendanceAction.CHECK_IN)
                } else {
                    runOnUiThread { status.text="✓ أثبت ${employee.displayName} وجوده محليًا" }
                }
            } else if (payload.verified) {
                when {
                    payload.qrProof && repo.allowQrAttendance && employee.allows(AttendanceMethod.PHONE_PROXIMITY) ->
                        maybeRecordPhoneProof(employee, payload.token, AttendanceMethod.PHONE_PROXIMITY, "QR مباشر موقّع", if (payload.checkoutProof) AttendanceAction.CHECK_OUT else AttendanceAction.CHECK_IN)
                    payload.credentialProof && employee.allows(AttendanceMethod.PASSWORD) ->
                        maybeRecordPhoneProof(employee, payload.token, AttendanceMethod.PASSWORD, "كلمة مرور هاتف الموظف")
                    payload.credentialProof && employee.allows(AttendanceMethod.PHONE_BIOMETRIC) ->
                        maybeRecordPhoneProof(employee, payload.token, AttendanceMethod.PHONE_BIOMETRIC, "اعتماد محلي قديم للتوافق")
                    (payload.biometricProof || payload.version == BleProtocol.LEGACY_VERSION) && employee.allows(AttendanceMethod.PHONE_BLE_BIOMETRIC) ->
                        maybeRecordPhoneProof(employee, payload.token, AttendanceMethod.PHONE_BLE_BIOMETRIC)
                }
            }
            runOnUiThread{refreshDashboard()}
            return if (channel == "Wi‑Fi/Hotspot" && authenticatedTransport) {
                lanPendingTokens[employee.employeeId] = payload.token to detectedAt
                LanAckProtocol.encode(payload.employeeHash, payload.token, secret, detectedAt)
            } else null
        }
        return null
    }

    private fun handleLanConfirm(frame: ByteArray, rssi: Int) {
        val hash = LanConfirmProtocol.employeeHash(frame) ?: return
        val employee = repo.employees().firstOrNull {
            it.active && it.companionEnabled && BleProtocol.employeeHash(it.employeeId) == hash
        } ?: return
        val secret = SecretCodec.decode(employee.pairingSecret) ?: return
        val pending = lanPendingTokens[employee.employeeId] ?: return
        val now = System.currentTimeMillis()
        if (now - pending.second > 5_000L || !LanConfirmProtocol.verify(frame, hash, pending.first, secret, now)) return
        lanPendingTokens.remove(employee.employeeId)
        lanConfirmedAt[employee.employeeId] = now
        repo.markCompanionLinked(employee.employeeId, "Wi‑Fi/Hotspot • ACK", now)
        val previous = nearby[employee.employeeId]
        val channels = previous?.channelTimes.orEmpty().toMutableMap().apply { put("Wi‑Fi/Hotspot • ACK", now) }
        nearby[employee.employeeId] = NearbyPhone(now, previous?.rssi ?: rssi, channels, previous?.deviceName.orEmpty(), previous?.gpsInsideAt ?: 0L)
        val key = "${employee.employeeId}|LAN_ACK"
        if (now - (lastPresenceLogAt[key] ?: 0L) >= 10_000L) {
            lastPresenceLogAt[key] = now
            repo.addPresenceEvent(PresenceEvent(employeeId = employee.employeeId, employeeName = employee.displayName,
                timestampEpochMillis = now, channel = "Wi‑Fi/Hotspot • ACK", rssi = rssi,
                details = "اتصال LAN ثنائي موثق: Store ACK ثم Employee confirmation"))
        }
        runOnUiThread {
            if (::status.isInitialized) status.text = "✓ Wi‑Fi/Hotspot مؤكد عبر ACK — ${employee.displayName}"
            refreshDashboard()
        }
    }

    private fun isLanConnected(employeeId: String, now: Long = System.currentTimeMillis()): Boolean =
        lanConfirmedAt[employeeId]?.let { it > 0L && now - it <= 8_000L } == true

    private fun isAuthenticatedPresenceConnected(employeeId: String, now: Long = System.currentTimeMillis()): Boolean =
        authenticatedPresenceAt[employeeId]?.let { it > 0L && now - it <= 12_000L } == true

    private fun isEmployeeActuallyConnected(employeeId: String, now: Long = System.currentTimeMillis()): Boolean =
        isAuthenticatedPresenceConnected(employeeId, now) || directBle.isConnected(employeeId) ||
            isLanConnected(employeeId, now) || isServerPresenceConnected(employeeId, now)

    private fun maybeRecordPhoneProof(employee:PairedEmployee,token:Int,method:AttendanceMethod, extraEvidence:String = "", forcedAction: AttendanceAction? = null){
        if (method == AttendanceMethod.GPS) return // 1.9.58: GPS is recognition/telemetry only, never attendance proof.
        if(!employee.allows(method)) return
        if(repo.lastProofToken(employee.employeeId)==token)return
        val last=repo.lastEvent(employee.employeeId)
        if(last!=null&&System.currentTimeMillis()-last.timestampEpochMillis<60_000L)return
        repo.setLastProofToken(employee.employeeId,token)
        val evidence = when (method) {
            AttendanceMethod.GPS -> "GPS من هاتف الموظف"
            AttendanceMethod.PHONE_FINGERPRINT -> "بصمة إصبع هاتف الموظف"
            AttendanceMethod.PHONE_BIOMETRIC -> "اعتماد محلي قديم من هاتف الموظف"
            AttendanceMethod.PHONE_PROXIMITY -> "QR مباشر موقّع وموافق عليه من هاتف الموظف"
            AttendanceMethod.PASSWORD -> "كلمة مرور هاتف الموظف"
            else -> "تحقق بيومتري من هاتف الموظف"
        }
        val fullEvidence = listOf(evidence, extraEvidence).filter { it.isNotBlank() }.joinToString(" • ")
        runOnUiThread{recordVerified(employee, method, fullEvidence, forcedAction) }
    }

    private fun nextAction(employeeId:String):AttendanceAction{val last=repo.lastEventToday(employeeId,dayStartMillis());return if(last?.action==AttendanceAction.CHECK_IN)AttendanceAction.CHECK_OUT else AttendanceAction.CHECK_IN}

    private fun refreshDashboard(){
        if (::storeSummary.isInitialized) storeSummary.text = storeSummaryText()
        val now=System.currentTimeMillis()
        val stale=nearby.filterValues{now-it.seenAt>12_000L}.keys.toList()
        stale.forEach { autoPresenceRecorded.remove(it) }
        val employees=repo.employees().filter{it.active}
        val events=repo.events().filter{it.timestampEpochMillis>=dayStartMillis()}
        val present=employees.mapNotNull{e->val last=events.lastOrNull{it.employeeId.equals(e.employeeId,true)};if(last?.action==AttendanceAction.CHECK_IN)e.employeeId else null}.toSet()
        val late=employees.mapNotNull{e->val first=events.firstOrNull{it.employeeId.equals(e.employeeId,true)&&it.action==AttendanceAction.CHECK_IN};if(first!=null&&first.timestampEpochMillis>shiftCutoffMillis(e, first.timestampEpochMillis))e.employeeId else null}.toSet()
        val early=employees.mapNotNull { e ->
            val lastOut = events.lastOrNull { it.employeeId.equals(e.employeeId, true) && it.action == AttendanceAction.CHECK_OUT }
            if (lastOut != null && lastOut.timestampEpochMillis < shiftEndMillis(e, lastOut.timestampEpochMillis)) e.employeeId else null
        }.toSet()
        if (::linkedEmployeesSummaryView.isInitialized) {
            val presentEmployees = employees.filter { present.contains(it.employeeId) }
            linkedEmployeesSummaryView.text = if (presentEmployees.isEmpty()) {
                "لا يوجد موظفون حاضرون الآن\nاضغط لعرض التفاصيل"
            } else {
                buildString {
                    append("${presentEmployees.size} حاضر الآن")
                    val names = presentEmployees.take(3).joinToString("، ") { it.displayName }
                    if (names.isNotBlank()) append("\n$names")
                    if (presentEmployees.size > 3) append(" +${presentEmployees.size - 3}")
                }
            }
        }
        if (::connectionSummaryView.isInitialized) {
            val connectedIds = employees.map { it.employeeId }.filter { isEmployeeActuallyConnected(it, now) }
            val connectedNames = connectedIds.map { id -> employees.firstOrNull { it.employeeId == id }?.displayName ?: id }
            val activeChannels = connectedIds.flatMap { id ->
                buildList {
                    if (directBle.isConnected(id)) add("Bluetooth BLE • ACK")
                    if (isLanConnected(id, now)) add("Wi‑Fi/Hotspot • ACK")
                    if (isServerPresenceConnected(id, now)) add("Server • Heartbeat")
                }
            }.distinct()
            val server = ServerDiagnostics.snapshot()
            val serverText = when {
                server.isFresh(now) -> "متصل فعليًا • HTTP ${server.lastHttpStatus}"
                repo.hasCentralCredentials() -> "غير مؤكد الآن"
                else -> "غير مهيأ"
            }
            val gpsRecognizedNames = employees.filter { isGpsRecognizedFresh(it.employeeId, now) }.map { it.displayName }
            connectionSummaryView.text = if (connectedNames.isEmpty()) {
                buildString {
                    append("لا توجد أجهزة موظفين متصلة فعليًا الآن • الخادم $serverText")
                    if (gpsRecognizedNames.isNotEmpty()) append("\nGPS رصد فقط: ${gpsRecognizedNames.take(3).joinToString("، ")}${if (gpsRecognizedNames.size > 3) " +${gpsRecognizedNames.size - 3}" else ""}")
                }
            } else {
                buildString {
                    append("${connectedNames.size} جهاز متصل فعليًا: ${connectedNames.take(3).joinToString("، ")}${if (connectedNames.size > 3) " +${connectedNames.size - 3}" else ""}")
                    append("\nACK نشط: ${activeChannels.joinToString(" + ").ifBlank { "لا يوجد" }} • الخادم $serverText")
                    if (gpsRecognizedNames.isNotEmpty()) append("\nGPS رصد فقط: ${gpsRecognizedNames.take(3).joinToString("، ")}")
                }
            }
        }
        if(::counts.isInitialized) counts.text="الموظفون ${employees.size}   •   الحاضرون ${present.size}\nمتأخرون ${late.size}   •   انصراف مبكر ${early.size}\nغير الحاضرين ${(employees.size-present.size).coerceAtLeast(0)}"
        if (::recentAttendanceSummaryView.isInitialized) {
            val last = events.maxByOrNull { it.timestampEpochMillis }
            recentAttendanceSummaryView.text = if (last == null) "لا توجد عملية اليوم\nاضغط لفتح السجل" else {
                val action = if (last.action == AttendanceAction.CHECK_IN) "حضور" else "انصراف"
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(last.timestampEpochMillis))
                "${last.employeeName.ifBlank { last.employeeId }}\n$action • $time • ${methodLabel(last.method)}"
            }
        }
        if(::nearbyView.isInitialized) {
            val livePhones = nearby.filterValues { now - it.seenAt <= 12_000L }
            nearbyView.text = if(livePhones.isEmpty()) {
                "لا توجد هواتف مرتبطة قريبة الآن\nشغّل Bluetooth في الهاتفين واترك تطبيق الموظف يعمل في الخلفية"
            } else livePhones.entries.joinToString("\n") { (id, phone) ->
                val name = employees.firstOrNull { it.employeeId == id }?.displayName ?: id
                val hasFreshBluetooth = phone.channelTimes.any { (channel, t) -> channel.startsWith("Bluetooth") && now - t <= 12_000L }
                val strength = if (!hasFreshBluetooth) "بدون قياس RSSI مباشر" else when { phone.rssi >= -55 -> "قريب جدًا"; phone.rssi >= -70 -> "قريب"; else -> "إشارة ضعيفة" }
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(phone.seenAt))
                val channels = phone.channelTimes.filterValues { t -> now - t <= 12_000L }.entries.sortedByDescending { it.value }.joinToString(" + ") { it.key }
                val actual = isEmployeeActuallyConnected(id, now)
                val gpsOnly = !actual && isGpsRecognizedFresh(id, now)
                "${if (actual) "● متصل موثق" else if (gpsOnly) "◎ مرصود عبر GPS فقط" else "◌ مكتشف فقط"} • $name • ${channels.ifBlank { "قناة محلية" }} • $strength${if (hasFreshBluetooth) " (${phone.rssi} dBm)" else ""}\n  آخر التقاط: $time${if (serverGpsSeenAt.containsKey(id)) "\n  ${gpsRecognitionLine(id, now)}" else ""}"
            }
        }
        if (::activityTimelineView.isInitialized) {
            activityTimelineView.text = activityHistoryLines(8).ifEmpty { listOf("لا توجد عمليات ارتباط أو حضور حتى الآن") }.joinToString("\n\n")
        }
        if (::attendanceTodayView.isInitialized) {
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            attendanceTodayView.text = if (events.isEmpty()) "لم يسجل أي موظف حضورًا اليوم" else
                events.sortedByDescending { it.timestampEpochMillis }.take(12).joinToString("\n\n") { event ->
                    val action = if (event.action == AttendanceAction.CHECK_IN) "حضور" else "انصراف"
                    val sync = if (event.synced) "وصل من الخادم" else "مسجل على جهاز المحل"
                    "${if (event.action == AttendanceAction.CHECK_IN) "🟢" else "🟠"} ${event.employeeName.ifBlank { event.employeeId }}\n$action • ${fmt.format(Date(event.timestampEpochMillis))} • ${methodLabel(event.method)} • $sync"
                }
        }
    }

    private fun activityHistoryLines(limit: Int): List<String> {
        val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
        val presence = repo.presenceEvents().map { event ->
            val employee = repo.employees().firstOrNull { it.employeeId.equals(event.employeeId, true) }
            val profile = listOfNotNull(employee?.jobTitle?.takeIf { it.isNotBlank() }, employee?.department?.takeIf { it.isNotBlank() }, employee?.branchId?.takeIf { it.isNotBlank() }).joinToString(" • ")
            event.timestampEpochMillis to "🔵 ${fmt.format(Date(event.timestampEpochMillis))} • ${event.employeeName.ifBlank { event.employeeId }}${if (profile.isNotBlank()) " • $profile" else ""}\nارتباط تلقائي: ${event.channel}${if (event.rssi > -127) " • ${event.rssi} dBm" else ""}${if (event.details.isNotBlank()) " • ${event.details}" else ""}"
        }
        val attendance = repo.events().map { event ->
            val action = if (event.action == AttendanceAction.CHECK_IN) "تسجيل حضور" else "تسجيل انصراف"
            val sync = if (event.synced) "متزامن" else "محفوظ محليًا"
            event.timestampEpochMillis to "🟢 ${fmt.format(Date(event.timestampEpochMillis))} • ${event.employeeName.ifBlank { event.employeeId }} • فرع ${event.branchId}\n$action • ${methodLabel(event.method)} • $sync${if (event.evidence.isNotBlank()) " • ${event.evidence}" else ""}"
        }
        return (presence + attendance).sortedByDescending { it.first }.take(limit).map { it.second }
    }

    private fun showConnectionAttendanceHistory() {
        val lines = activityHistoryLines(100)
        AlertDialog.Builder(this).setTitle("سجل الارتباط والحضور")
            .setMessage(if (lines.isEmpty()) "لا توجد عمليات بعد" else lines.joinToString("\n\n"))
            .setPositiveButton("إغلاق", null).show()
    }

    private fun showLiveAttendanceNow() {
        val now = System.currentTimeMillis()
        val today = repo.events().filter { it.timestampEpochMillis >= dayStartMillis() }
        val employees = repo.employees().filter { it.active }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 14), UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 14), UiKit.dp(this@MainActivity, 8))
        }
        if (employees.isEmpty()) box.addView(UiKit.subtitle(this, p, "لا يوجد موظفون نشطون"))
        employees.forEach { employee ->
            val phone = nearby[employee.employeeId]
            val age = phone?.let { now - it.seenAt } ?: Long.MAX_VALUE
            val directAckAge = directBle.lastAckAt(employee.employeeId).let { if (it > 0L) now - it else Long.MAX_VALUE }
            val liveChannels = phone?.channelTimes.orEmpty().filterValues { now - it <= 12_000L }.keys.toMutableList()
            if (directAckAge <= 11_000L && "Bluetooth BLE • ACK" !in liveChannels) liveChannels.add(0, "Bluetooth BLE • ACK")
            val lanAckAge = lanConfirmedAt[employee.employeeId]?.let { now - it } ?: Long.MAX_VALUE
            if (lanAckAge <= 8_000L && "Wi‑Fi/Hotspot • ACK" !in liveChannels) liveChannels.add("Wi‑Fi/Hotspot • ACK")
            val statusText = when {
                directAckAge <= 11_000L -> "● متصل ومؤكد عبر Bluetooth ACK"
                lanAckAge <= 8_000L -> "● متصل ومؤكد عبر LAN ACK"
                age <= 12_000L -> "◌ مكتشف/قريب فقط — بانتظار ACK"
                age <= 60_000L -> "◌ انقطع مؤخرًا"
                else -> "○ غير متصل"
            }
            val last = today.lastOrNull { it.employeeId.equals(employee.employeeId, true) }
            val attendanceText = if (last == null) "الحضور: لم يثبت بعد" else {
                val action = if (last.action == AttendanceAction.CHECK_IN) "تم إثبات الحضور" else "تم تسجيل الانصراف"
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(last.timestampEpochMillis))
                "✓ $action • $time • ${methodLabel(last.method)}"
            }
            val gps = phone?.gpsInsideAt?.takeIf { now - it <= 120_000L }?.let { "GPS: داخل النطاق" } ?: "GPS: لا يوجد تأكيد حديث"
            val deviceLine = phone?.deviceName?.takeIf { it.isNotBlank() }?.let { "الجهاز: $it\n" }.orEmpty()
            val lastSeen = phone?.seenAt?.let { if (now - it < 5_000L) "الآن" else SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it)) } ?: "غير متاح"
            val card = UiKit.card(this, p, 12)
            card.addView(UiKit.title(this, p, employee.displayName, 17f))
            card.addView(UiKit.subtitle(this, p, "$statusText\n${deviceLine}الاتصال: ${liveChannels.joinToString(" + ").ifBlank { "لا يوجد اتصال فعلي" }}\n$gps\nآخر اتصال: $lastSeen\n$attendanceText"))
            if (last?.action != AttendanceAction.CHECK_IN && employee.companionEnabled) {
                card.addView(UiKit.button(this, p, "إرسال طلب إثبات", false).apply { setOnClickListener { sendSmartPresenceRequest(employee, automatic = false) } })
            }
            box.addView(card)
        }
        AlertDialog.Builder(this).setTitle("الحضور الآن").setView(ScrollView(this).apply { addView(box) }).setPositiveButton("إغلاق", null).show()
    }

    private fun showSmartAttendanceControl() {
        val now = System.currentTimeMillis()
        val employees = repo.employees().filter { it.active }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 12), UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 12), UiKit.dp(this@MainActivity, 8))
        }
        val audience = when (repo.smartAlertAudience) {
            "OWNER_ONLY" -> "صاحب العمل فقط"
            "EMPLOYEE_ONLY" -> "الموظف فقط"
            else -> "صاحب العمل والموظف"
        }
        val header = UiKit.card(this, p, 11).apply {
            addView(UiKit.title(this@MainActivity, p, "لوحة الدوام والتنبيهات الذكية", 18f))
            addView(UiKit.subtitle(this@MainActivity, p,
                "التنبيهات: $audience • تذكير الانصراف ${if (repo.checkoutReminderEnabled) "مفعّل" else "متوقف"} • GPS للرصد فقط وليس إثبات حضور"))
            addView(UiKit.button(this@MainActivity, p, "إعدادات التنبيهات", false).apply {
                setOnClickListener { showSmartAlertSettings() }
            })
        }
        box.addView(header)
        if (employees.isEmpty()) box.addView(UiKit.subtitle(this, p, "لا يوجد موظفون نشطون"))
        val today = repo.events().filter { it.timestampEpochMillis >= dayStartMillis() }
        employees.forEach { employee ->
            val day = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK)
            val scheduled = repo.isEmployeeWorkDay(employee.employeeId, day)
            val events = today.filter { it.employeeId.equals(employee.employeeId, true) }
            val last = events.maxByOrNull { it.timestampEpochMillis }
            val checkIn = events.filter { it.action == AttendanceAction.CHECK_IN }.maxByOrNull { it.timestampEpochMillis }
            val checkOut = events.filter { it.action == AttendanceAction.CHECK_OUT }.maxByOrNull { it.timestampEpochMillis }
            val grace = employee.lateGraceMinutes.takeIf { it >= 0 } ?: repo.graceMinutes
            val shiftStart = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, if (employee.useCustomShift) employee.shiftStartHour else repo.shiftHour)
                set(Calendar.MINUTE, if (employee.useCustomShift) employee.shiftStartMinute else repo.shiftMinute)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val state = when {
                !scheduled -> "إجازة / غير مجدول اليوم"
                checkOut != null && (checkIn == null || checkOut.timestampEpochMillis > checkIn.timestampEpochMillis) -> "انصرف"
                checkIn != null -> "حاضر"
                now > shiftStart + grace * 60_000L -> "متأخر"
                else -> "لم يحضر بعد"
            }
            val connected = isEmployeeActuallyConnected(employee.employeeId, now)
            val phone = nearby[employee.employeeId]
            val lastSeenAt = listOfNotNull(
                phone?.seenAt,
                directBle.lastAckAt(employee.employeeId).takeIf { it > 0L },
                serverPresenceAt[employee.employeeId]?.takeIf { it > 0L },
                serverGpsSeenAt[employee.employeeId]?.takeIf { it > 0L }
            ).maxOrNull() ?: 0L
            val recognition = when {
                directBle.lastAckAt(employee.employeeId).let { it > 0L && now - it <= 11_000L } -> "Bluetooth ACK"
                isLanConnected(employee.employeeId, now) -> "Wi‑Fi / Hotspot ACK"
                isServerPresenceConnected(employee.employeeId, now) -> "Server Heartbeat"
                isGpsRecognizedFresh(employee.employeeId, now) -> "GPS رصد فقط"
                else -> "غير متصل الآن"
            }
            val seenText = if (lastSeenAt > 0L) SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastSeenAt)) else "لا يوجد"
            val attendanceLine = when {
                last == null -> "الحضور المثبت: لا يوجد"
                else -> {
                    val kind = if (last.action == AttendanceAction.CHECK_IN) "حضور" else "انصراف"
                    "$kind المثبت: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(last.timestampEpochMillis))} • ${methodLabel(last.method)}"
                }
            }
            val gpsLine = if (serverGpsSeenAt.containsKey(employee.employeeId)) gpsRecognitionLine(employee.employeeId, now) else "GPS: لا توجد قراءة حديثة • مراقبة فقط"
            val card = UiKit.card(this, p, 11).apply {
                addView(UiKit.title(this@MainActivity, p, "${employee.displayName} — $state", 16.5f))
                addView(UiKit.subtitle(this@MainActivity, p,
                    "الدوام: ${employeeShiftLabel(employee)} • السماح ${grace}د\nأيام العمل: ${workDaysLabel(repo.employeeWorkDays(employee.employeeId))}\nالاتصال: ${if (connected) "متصل موثق" else "غير متصل"} • $recognition\nآخر تعرف: $seenText\n$gpsLine\n$attendanceLine"))
                val actions = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
                actions.addView(UiKit.button(this@MainActivity, p, "أيام الدوام", false).apply {
                    layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(this@MainActivity, 48), 1f).apply { marginEnd = UiKit.dp(this@MainActivity, 3) }
                    setOnClickListener { showEmployeeWorkDaysPicker(employee) }
                })
                if (employee.companionEnabled) actions.addView(UiKit.button(this@MainActivity, p, "طلب إثبات الآن", false).apply {
                    layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(this@MainActivity, 48), 1f).apply { marginStart = UiKit.dp(this@MainActivity, 3) }
                    setOnClickListener { sendSmartPresenceRequest(employee, automatic = false) }
                })
                addView(actions)
            }
            box.addView(card)
        }
        AlertDialog.Builder(this).setTitle("الدوام والتنبيهات الذكية")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("إغلاق", null).show()
    }

    private fun showEmployeeWorkDaysPicker(employee: PairedEmployee) {
        val labels = arrayOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
        val calendarDays = intArrayOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY)
        val selected = repo.employeeWorkDays(employee.employeeId).toMutableSet()
        val checked = BooleanArray(labels.size) { calendarDays[it] in selected }
        AlertDialog.Builder(this)
            .setTitle("أيام دوام ${employee.displayName}")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                if (isChecked) selected += calendarDays[which] else selected -= calendarDays[which]
            }
            .setPositiveButton("حفظ") { _, _ ->
                if (selected.isEmpty()) {
                    info("أيام الدوام", "يجب اختيار يوم واحد على الأقل.")
                } else {
                    repo.setEmployeeWorkDays(employee.employeeId, selected)
                    if (::status.isInitialized) status.text = "تم حفظ أيام دوام ${employee.displayName}"
                    showSmartAttendanceControl()
                }
            }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun showSmartAlertSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 12), UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 12), UiKit.dp(this@MainActivity, 8))
        }
        val audienceLabels = arrayOf("صاحب العمل فقط", "الموظف فقط", "صاحب العمل والموظف")
        val audienceValues = arrayOf("OWNER_ONLY", "EMPLOYEE_ONLY", "BOTH")
        val audience = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, audienceLabels)
            setSelection(audienceValues.indexOf(repo.smartAlertAudience).takeIf { it >= 0 } ?: 2)
        }
        val checkout = CheckBox(this).apply { text = "تذكير عند نسيان تسجيل الانصراف"; setTextColor(p.text); isChecked = repo.checkoutReminderEnabled }
        val delay = UiKit.field(this, p, "بعد انتهاء الدوام بالدقائق").apply {
            setText(repo.checkoutReminderDelayMinutes.toString()); isFocusable = false; isClickable = true
            setOnClickListener { FormPickerHelper.pickNumber(this@MainActivity, this, 0, 180, "تأخير تذكير الانصراف") }
        }
        val server = CheckBox(this).apply { text = "إرسال تنبيهات الموظف عبر الخادم عند توفره"; setTextColor(p.text); isChecked = repo.lateNotifyEmployeeViaServer }
        box.addView(UiKit.subtitle(this, p, "جهة استلام تنبيهات التأخر ونسيان الانصراف")); box.addView(audience)
        box.addView(checkout); box.addView(delay); box.addView(server)
        box.addView(UiKit.subtitle(this, p, "GPS يبقى للرصد ومعرفة آخر تعرف فقط، ولا يتحول إلى إثبات حضور."))
        AlertDialog.Builder(this).setTitle("إعدادات التنبيهات الذكية").setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                repo.smartAlertAudience = audienceValues[audience.selectedItemPosition.coerceIn(0, audienceValues.lastIndex)]
                repo.checkoutReminderEnabled = checkout.isChecked
                repo.checkoutReminderDelayMinutes = delay.text.toString().toIntOrNull() ?: 15
                repo.lateNotifyEmployeeViaServer = server.isChecked
                if (::status.isInitialized) status.text = "تم حفظ إعدادات التنبيهات الذكية"
            }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun workDaysLabel(days: Set<Int>): String {
        val order = listOf(
            Calendar.SUNDAY to "أحد", Calendar.MONDAY to "اثن", Calendar.TUESDAY to "ثلا",
            Calendar.WEDNESDAY to "أربع", Calendar.THURSDAY to "خميس", Calendar.FRIDAY to "جمعة", Calendar.SATURDAY to "سبت"
        )
        return if (days.size == 7) "كل الأيام" else order.filter { it.first in days }.joinToString("، ") { it.second }
    }

    private fun checkConnectedWithoutProof() {
        if (!repo.isCentralActivationActive()) return
        val now = System.currentTimeMillis()
        val dayStart = dayStartMillis()
        val checkedIn = repo.events().filter { it.timestampEpochMillis >= dayStart && it.action == AttendanceAction.CHECK_IN }.map { it.employeeId.lowercase() }.toSet()
        repo.employees().filter { it.active && it.companionEnabled }.forEach { employee ->
            val connected = isEmployeeActuallyConnected(employee.employeeId, now)
            if (!connected || employee.employeeId.lowercase() in checkedIn) {
                presenceUnprovedSince.remove(employee.employeeId)
                if (employee.employeeId.lowercase() in checkedIn) presenceReminderSentAt.remove(employee.employeeId)
                return@forEach
            }
            val since = presenceUnprovedSince.getOrPut(employee.employeeId) { now }
            val due = employee.presenceReminderMinutes.coerceIn(1, 15) * 60_000L
            val lastAlert = presenceReminderSentAt[employee.employeeId] ?: 0L
            if (now - since >= due && now - lastAlert >= due) {
                presenceReminderSentAt[employee.employeeId] = now
                lateAlerts.notifyConnectedWithoutProof(employee)
                sendSmartPresenceRequest(employee, automatic = true)
            }
        }
    }

    private fun sendSmartPresenceRequest(employee: PairedEmployee, automatic: Boolean) {
        val method = when {
            employee.allows(AttendanceMethod.PHONE_BLE_BIOMETRIC) -> AttendanceMethod.PHONE_BLE_BIOMETRIC
            employee.allows(AttendanceMethod.PHONE_FINGERPRINT) -> AttendanceMethod.PHONE_FINGERPRINT
            employee.allows(AttendanceMethod.PASSWORD) -> AttendanceMethod.PASSWORD
            else -> null
        }
        if (method == null) {
            if (!automatic) info("إثبات الحضور", "لا توجد بصمة/وجه هاتف مفعلة لهذا الموظف؛ استخدم QR مباشر أو إحدى طرق التحقق الأخرى.")
            return
        }
        if (::status.isInitialized) status.text = "جاري إرسال طلب إثبات إلى ${employee.displayName}…"
        val requestedAction = nextAction(employee.employeeId)
        Thread {
            val secret = SecretCodec.decode(employee.pairingSecret)
            val requestToken = java.security.SecureRandom().nextInt()
            val expiresAt = System.currentTimeMillis() + 60_000L
            val direct = if (secret != null && directBle.isConnected(employee.employeeId)) directBle.sendChallenge(employee.employeeId, method, expiresAt, requestToken, requestedAction) else false
            val lan = if (!direct && secret != null && isLanConnected(employee.employeeId)) LocalChallengeSender.send(employee.employeeId, secret, method, System.currentTimeMillis(), requestToken, requestedAction) else false
            val legacyLocal = if (!direct && !lan && secret != null && isAuthenticatedPresenceConnected(employee.employeeId))
                ChallengeDispatch1928.send(this@MainActivity, employee.employeeId, secret, method, expiresAt, requestToken, requestedAction) else false
            val localSent = direct || lan || legacyLocal
            val server = if (!localSent && repo.hasCentralCredentials()) CentralServerClient.createPresenceChallenge(repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this), employee.employeeId, method, requestedAction) else null
            runOnUiThread {
                val serverSent = server?.isSuccess == true
                if (localSent || serverSent) {
                    val transport = when { direct -> "Bluetooth GATT ACK"; lan -> "LAN ACK"; legacyLocal -> "BLE/LAN HMAC"; serverSent -> "Server HTTP"; else -> "غير معروف" }
                    repo.addPresenceEvent(PresenceEvent(employeeId=employee.employeeId, employeeName=employee.displayName, timestampEpochMillis=System.currentTimeMillis(), channel="طلب إثبات", rssi=nearby[employee.employeeId]?.rssi?:-127, details=transport))
                    if (repo.attendanceVoiceAnnouncementEnabled && !automatic) voiceAnnouncer.announceRequestSent(employee.displayName)
                    if (::status.isInitialized) status.text = "✓ أُرسل طلب إثبات إلى ${employee.displayName}"
                } else if (!automatic) info("تعذر إرسال الطلب", "الهاتف غير متاح الآن عبر Bluetooth أو الشبكة المحلية أو الخادم.")
            }
        }.start()
    }

    private fun showConnectionCenter() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 18), 0)
        }
        val now = System.currentTimeMillis()
        val employees = repo.employees().filter { it.active }
        val live = nearby.filterValues { now - it.seenAt <= 8_000L }
        val actuallyConnected = employees.filter { isEmployeeActuallyConnected(it.employeeId, now) }
        val serverDiag = ServerDiagnostics.snapshot()
        fun diagTime(value: Long): String = if (value <= 0L) "لا يوجد" else SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(value))
        val serverText = when {
            serverDiag.isFresh(now) -> "متصل فعليًا • HTTP ${serverDiag.lastHttpStatus} • آخر نجاح ${diagTime(serverDiag.lastSuccessAt)} • ${serverDiag.lastSuccessPath.ifBlank { serverDiag.lastPath }}${if (serverDiag.lastErrorAt > 0L) " • آخر خطأ سابق ${diagTime(serverDiag.lastErrorAt)}" else ""}"
            repo.hasCentralCredentials() -> "غير مؤكد الآن • آخر نجاح ${diagTime(serverDiag.lastSuccessAt)} • آخر خطأ ${diagTime(serverDiag.lastErrorAt)}${serverDiag.lastError.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()}${serverDiag.lastErrorPath.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()}"
            else -> "غير مهيأ"
        }
        val btAdapter = getSystemService(BluetoothManager::class.java)?.adapter
        val blePermissions = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) true else
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val gpsText = if (repo.gpsRecognitionEnabled && repo.isGpsConfigured) "مفعّل للتعرّف على وجود هاتف الموظف • نطاق ${repo.gpsRadiusMeters}م • لا يسجل الحضور وحده" else "غير مهيأ"
        box.addView(UiKit.statusBadge(this, p, if (actuallyConnected.isEmpty()) "لا توجد أجهزة متصلة فعليًا" else "${actuallyConnected.size} جهاز متصل فعليًا", actuallyConnected.isNotEmpty()))
        val diagnosticText = buildString {
            appendLine("Bluetooth Adapter: ${if (btAdapter?.isEnabled == true) "ON" else "OFF"} • صلاحيات BLE: ${if (blePermissions) "ممنوحة" else "ناقصة"}")
            val pairing = activePairingBeacon
            appendLine("Advertising: ${when { pairing?.isAdvertising() == true -> "يعمل للربط"; pairing?.isRunning() == true -> "الربط نشط لكن الإعلان غير مؤكد"; else -> "غير مطلوب الآن (يعمل عند ربط موظف)" }}")
            appendLine("Scan: ${if (scanner.isScanning()) "يعمل" else "متوقف"} • GATT: ${if (actuallyConnected.any { directBle.isConnected(it.employeeId) }) "ACK مؤكد" else "لا يوجد ACK"}")
            appendLine("LAN Listener: ${if (networkListener.isRunning()) "يعمل" else "متوقف"} • LAN ACK: ${if (actuallyConnected.any { isLanConnected(it.employeeId, now) }) "مؤكد" else "لا يوجد"}")
            appendLine("Server: $serverText • أجهزة Heartbeat حديثة: ${employees.count { isServerPresenceConnected(it.employeeId, now) }}")
            append("GPS: $gpsText")
        }
        box.addView(UiKit.subtitle(this, p, diagnosticText).apply { gravity = Gravity.CENTER })
        box.addView(UiKit.sectionLabel(this, p, "الأجهزة الحالية"))
        val detailIds = employees.map { it.employeeId }.filter { id -> live.containsKey(id) || (serverGpsSeenAt[id]?.let { now - it <= GPS_RECOGNITION_FRESH_MILLIS } == true) }
        val detail = if (detailIds.isEmpty()) "لا يوجد هاتف موظف مكتشف الآن، ولا قراءة GPS حديثة." else detailIds.joinToString("\n\n") { id ->
            val phone = live[id]
            val employee = employees.firstOrNull { it.employeeId == id }
            val name = employee?.displayName ?: id
            val discovered = phone?.channelTimes?.filterValues { now - it <= 12_000L }?.keys?.joinToString(" + ").orEmpty().ifBlank { "لا يوجد اكتشاف محلي حديث" }
            val actual = buildList {
                if (isAuthenticatedPresenceConnected(id, now)) add("Presence HMAC • موثق")
                if (directBle.isConnected(id)) add("Bluetooth GATT • ACK")
                if (isLanConnected(id, now)) add("Wi‑Fi/Hotspot • ACK")
                if (isServerPresenceConnected(id, now)) add("Server • Heartbeat")
            }.joinToString(" + ").ifBlank { "لا يوجد اتصال موثق حديث" }
            val time = phone?.seenAt?.let { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it)) } ?: "—"
            val state = when {
                isEmployeeActuallyConnected(id, now) -> "● متصل فعليًا"
                isGpsRecognizedFresh(id, now) -> "◎ مرصود عبر GPS فقط"
                else -> "◌ مكتشف فقط"
            }
            buildString {
                append("$state • $name\n")
                append("GATT: ${directBle.diagnosticState(id)}\n")
                append("ACK: $actual\n")
                append("مكتشف عبر: $discovered • آخر ظهور $time")
                if (phone != null && phone.rssi > -127) append(" • ${phone.rssi} dBm")
                if (serverGpsSeenAt.containsKey(id)) append("\n${gpsRecognitionLine(id, now)}")
            }
        }
        box.addView(TextView(this).apply { text = detail; textSize = 15f; setTextColor(p.text); setPadding(0, UiKit.dp(this@MainActivity, 8), 0, UiKit.dp(this@MainActivity, 12)) })
        box.addView(UiKit.button(this, p, "إعادة تشغيل اكتشاف الأجهزة", false).apply { setOnClickListener {
            runCatching { scanner.stop() }; runCatching { networkListener.stop() }
            runCatching { scanner.start() }; runCatching { networkListener.start() }
            status.text = "يعمل البحث عبر Bluetooth وWi‑Fi/Hotspot"
            refreshDashboard()
        } })
        box.addView(UiKit.button(this, p, "عرض سجل الاتصال والحضور", false).apply { setOnClickListener { showConnectionAttendanceHistory() } })
        AlertDialog.Builder(this).setTitle("مركز الاتصال والأجهزة").setView(ScrollView(this).apply { addView(box) }).setPositiveButton("إغلاق", null).show()
    }

    private fun shareTodayCsv(){val events=repo.events().filter{it.timestampEpochMillis>=dayStartMillis()};val fmt=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);val csv=buildString{append("employee_id,employee_name,branch,time,action,method,synced\n");events.forEach{e->append("${e.employeeId},\"${e.employeeName.replace("\"","\"\"")}\",${e.branchId},${fmt.format(Date(e.timestampEpochMillis))},${e.action.name},${e.method.name},${e.synced}\n")}};startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_SUBJECT,"ATTEND PRO - تقرير اليوم");putExtra(Intent.EXTRA_TEXT,csv)},"مشاركة تقرير اليوم"))}
    private fun dayStartMillis():Long=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis
    private fun shiftWindow1980(employee: PairedEmployee, baseTime: Long = System.currentTimeMillis()): ShiftWindow.Window = ShiftWindow.resolve(
        baseTime,
        if (employee.useCustomShift) employee.shiftStartHour else repo.shiftHour,
        if (employee.useCustomShift) employee.shiftStartMinute else repo.shiftMinute,
        if (employee.useCustomShift) employee.shiftEndHour else repo.shiftEndHour,
        if (employee.useCustomShift) employee.shiftEndMinute else repo.shiftEndMinute
    )
    private fun shiftCutoffMillis(employee: PairedEmployee, baseTime: Long = System.currentTimeMillis()): Long =
        shiftWindow1980(employee, baseTime).start + repo.graceMinutes * 60_000L
    private fun shiftEndMillis(employee: PairedEmployee, baseTime: Long = System.currentTimeMillis()): Long =
        shiftWindow1980(employee, baseTime).end
    private fun employeeShiftLabel(employee: PairedEmployee): String {
        val sh = if (employee.useCustomShift) employee.shiftStartHour else repo.shiftHour
        val sm = if (employee.useCustomShift) employee.shiftStartMinute else repo.shiftMinute
        val eh = if (employee.useCustomShift) employee.shiftEndHour else repo.shiftEndHour
        val em = if (employee.useCustomShift) employee.shiftEndMinute else repo.shiftEndMinute
        return String.format(Locale.getDefault(), "%02d:%02d - %02d:%02d%s", sh, sm, eh, em, if (employee.useCustomShift) " • خاص" else " • عام")
    }
    private fun deviceId():String{val prefs=getSharedPreferences("device",MODE_PRIVATE);val old=prefs.getString("id",null);if(old!=null)return old;val id="STORE-${UUID.randomUUID()}";prefs.edit().putString("id",id).apply();return id}

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (!repo.isCentralActivationActive()) { buildActivationLockUi(); return }
        if (requestCode == REQUEST_VOICE_VERIFY) {
            if (!::status.isInitialized) { buildElegantUi(); refreshDashboard() }
            val employeeId = pendingVoiceEmployeeId
            val challenge = pendingVoiceChallenge
            val voicePrintScore = pendingVoicePrintScore
            val requestedAttendanceAction = pendingVoiceAttendanceAction
            pendingVoiceEmployeeId = null; pendingVoiceChallenge = null
            pendingVoicePrintScore = -1f
            pendingVoiceAttendanceAction = null
            if (resultCode != RESULT_OK || employeeId.isNullOrBlank() || challenge.isNullOrBlank()) { status.text = "تم إلغاء التحقق الصوتي"; return }
            val employee = findActiveEmployee(employeeId)
            if (employee == null || employee.voicePhraseHash.isBlank() || !employee.allows(AttendanceMethod.VOICE_PHRASE)) { status.text = "بيانات التحقق الصوتي غير متاحة"; return }
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
            val confidence = data?.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES)
            var acceptedConfidence = -1f
            val matched = results.indices.any { i ->
                val normalized = normalizeVoicePhrase(results[i])
                val challengeNorm = normalizeVoicePhrase(challenge)
                val words = normalized.split(' ').filter { it.isNotBlank() }
                val challengeIndex = words.indexOfFirst { voiceWordDistance(it, challengeNorm) <= 1 }
                if (challengeIndex < 0) return@any false
                val phraseWords = words.filterIndexed { index, _ -> index != challengeIndex }
                val candidates = mutableSetOf<String>()
                for (start in phraseWords.indices) for (end in start + 1..phraseWords.size) {
                    candidates.add(phraseWords.subList(start, end).joinToString(" "))
                }
                val phraseMatch = candidates.any { candidate ->
                    if (employee.voicePhraseText.isNotBlank()) voicePhraseSimilarity(candidate, employee.voicePhraseText) >= 0.60f
                    else voiceCandidateForms(candidate).any { PairingProtocol.matchesPin(employee.voicePhraseHash, it) }
                }
                val c = confidence?.getOrNull(i) ?: -1f
                val confidenceOk = c < 0f || c >= 0.25f
                if (phraseMatch && confidenceOk) { acceptedConfidence = c; true } else false
            }
            if (matched && voicePrintScore >= 0.46f) recordVerified(employee, AttendanceMethod.VOICE_PHRASE,
                "نبرة ${(voicePrintScore * 100).toInt()}% • ${if (acceptedConfidence >= 0f) "ثقة كلام ${(acceptedConfidence * 100).toInt()}%" else "تحدي صوتي"}",
                requestedAttendanceAction)
            else info("لم ينجح التحقق الصوتي", "لم تتطابق العبارة مع كلمة التحدي أو كانت ثقة التعرف منخفضة. حاول في مكان أقل ضوضاء.")
            return
        }
        if (requestCode != REQUEST_FACE_CAPTURE) return
        if (!::status.isInitialized) { buildElegantUi(); refreshDashboard() }
        val employeeId = pendingFaceEmployeeId
        val recognitionMode = pendingFaceRecognition
        val tempPath = pendingFaceCapturePath
        pendingFaceEmployeeId = null; pendingFaceRecognition = false; pendingFaceCapturePath = null
        if (resultCode != RESULT_OK || tempPath.isNullOrBlank()) {
            tempPath?.let { runCatching { File(it).delete() } }
            pendingFaceAttendanceAction = null
            status.text = "تم إلغاء بصمة الوجه — لم تتغير البيانات"
            return
        }
        val bitmap = runCatching { decodeFaceBitmap(tempPath) }.getOrNull()
        runCatching { File(tempPath).delete() }
        if (bitmap == null) { info("بصمة الوجه", "لم تصل صورة صالحة من الكاميرا. أعد المحاولة."); return }
        val analysis = FaceSignatureEngine.analyze(bitmap)
        if (analysis.template.isBlank()) {
            if (recognitionMode) pendingFaceAttendanceAction = null
            info("جودة الوجه", analysis.error.ifBlank { "تعذر تكوين قالب وجه صالح." }); return
        }
        if (recognitionMode) {
            val candidates = repo.employees().filter { it.active && it.allows(AttendanceMethod.SHARED_DEVICE_FACE) && it.faceTemplate.isNotBlank() }
            val livenessEmployee = pendingLivenessEmployeeId?.let { id -> candidates.firstOrNull { it.employeeId.equals(id, true) } }
            if (livenessEmployee != null && !pendingLivenessFirstTemplate.isNullOrBlank()) {
                val sameEmployeeScore = FaceSignatureEngine.similarity(analysis.template, livenessEmployee.faceTemplate)
                val movementScore = FaceSignatureEngine.similarity(analysis.template, pendingLivenessFirstTemplate.orEmpty())
                val firstScore = pendingLivenessFirstScore
                pendingLivenessEmployeeId = null; pendingLivenessFirstTemplate = null; pendingLivenessFirstScore = 0f
                if (sameEmployeeScore < 0.66f || movementScore > 0.995f || movementScore < 0.45f) {
                    pendingFaceAttendanceAction = null
                    info("فشل فحص الحيوية", "لم يثبت النظام حركة وجه طبيعية لنفس الموظف. مطابقة الهوية ${(sameEmployeeScore * 100).toInt()}% • الحركة ${(movementScore * 100).toInt()}%. أعد المحاولة ونفّذ الحركة المطلوبة بوضوح.")
                    return
                }
                val requestedAttendanceAction = pendingFaceAttendanceAction
                pendingFaceAttendanceAction = null
                recordVerified(livenessEmployee, AttendanceMethod.SHARED_DEVICE_FACE,
                    "وجه ${(minOf(firstScore, sameEmployeeScore) * 100).toInt()}% • فحص حيوية تفاعلي • جودة ${analysis.quality}/100",
                    requestedAttendanceAction)
                return
            }
            val ranked = candidates.map { it to FaceSignatureEngine.similarity(analysis.template, it.faceTemplate) }.sortedByDescending { it.second }
            val best = ranked.firstOrNull()
            val second = ranked.getOrNull(1)?.second ?: 0f
            if (best == null || best.second < 0.70f || (ranked.size > 1 && best.second - second < 0.04f)) {
                pendingFaceAttendanceAction = null
                info("لم يتم التعرف بثقة كافية", "لم يطابق الوجه أي موظف بدرجة آمنة. أفضل درجة ${(best?.second?.times(100)?.toInt() ?: 0)}%. أعد المحاولة بإضاءة جيدة أو حدّث ملف وجه الموظف.")
                return
            }
            pendingLivenessEmployeeId = best.first.employeeId
            pendingLivenessFirstTemplate = analysis.template
            pendingLivenessFirstScore = best.second
            pendingFaceRecognition = true
            val challenge = listOf("أدر وجهك قليلًا إلى اليمين", "أدر وجهك قليلًا إلى اليسار", "ارفع ذقنك قليلًا").random()
            AlertDialog.Builder(this).setTitle("فحص حيوية الوجه")
                .setMessage("تمت مطابقة ${best.first.displayName}. الآن $challenge ثم التقط الصورة الثانية. يجب أن يبقى الوجه نفسه ظاهرًا.")
                .setPositiveButton("التقاط الصورة الثانية") { _, _ -> ensureCameraAndLaunch() }
                .setNegativeButton("إلغاء") { _, _ ->
                    pendingLivenessEmployeeId = null
                    pendingLivenessFirstTemplate = null
                    pendingFaceAttendanceAction = null
                }.show()
            return
        }
        if (employeeId.isNullOrBlank()) return
        val employee = repo.employees().firstOrNull { it.employeeId.equals(employeeId, true) } ?: return
        faceEnrollmentTemplates.add(analysis.template)
        faceEnrollmentQualities.add(analysis.quality)
        if (faceEnrollmentTemplates.size < 5) {
            val nextPose = when (faceEnrollmentTemplates.size) {
                1 -> "التفت قليلًا إلى اليمين"
                2 -> "التفت قليلًا إلى اليسار"
                3 -> "ارفع وجهك قليلًا"
                else -> "اخفض وجهك قليلًا"
            }
            pendingFaceEmployeeId = employee.employeeId
            pendingFaceRecognition = false
            status.text = "تمت الصورة ${faceEnrollmentTemplates.size}/5 — $nextPose"
            AlertDialog.Builder(this).setTitle("الصورة التالية").setMessage("$nextPose مع إبقاء العينين ظاهرتين، ثم اضغط متابعة.")
                .setPositiveButton("متابعة") { _, _ -> ensureCameraAndLaunch() }
                .setNegativeButton("إلغاء") { _, _ -> faceEnrollmentTemplates.clear(); faceEnrollmentQualities.clear() }.show()
            return
        }
        runCatching {
            val dir = File(filesDir, "face_profiles").apply { mkdirs() }
            val safeId = employee.employeeId.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val file = File(dir, "$safeId.jpg")
            val saveBitmap = analysis.faceCrop ?: bitmap
            file.outputStream().use { out -> if (!saveBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) error("تعذر ضغط الصورة") }
            deleteFaceFile(employee.faceProfileRef.takeIf { it != file.absolutePath }.orEmpty())
            val multiTemplate = faceEnrollmentTemplates.fold("") { acc, template -> FaceSignatureEngine.appendTemplate(acc, template) }
            val averageQuality = faceEnrollmentQualities.average().toInt()
            repo.upsertEmployee(employee.copy(faceProfileRef = file.absolutePath, faceCapturedAt = System.currentTimeMillis(), faceTemplate = multiTemplate, faceQualityScore = averageQuality))
            status.text = "✓ تم إعداد وجه ${employee.displayName} من 5 وضعيات • جودة $averageQuality/100"
            info("تم إعداد الوجه من 5 وضعيات", "تم حفظ خمسة قوالب محلية لتحسين المطابقة عند اختلاف زاوية الوجه. هذه مطابقة محلية محسّنة، وليست بديلًا عن محرك تعرّف عصبي مع فحص حيوية تجاري.")
            faceEnrollmentTemplates.clear(); faceEnrollmentQualities.clear()
        }.onFailure { status.text = "تعذر حفظ بصمة الوجه: ${it.message ?: "خطأ"}" }
        return
    }

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        when(requestCode){
            BleEmployeeScanner.REQUEST_SCAN -> {
                if(grantResults.isNotEmpty()&&grantResults.all{it==PackageManager.PERMISSION_GRANTED}){status.text="تم منح صلاحيات BLE";ensurePresenceDiscoveryRunning()}
                else status.text="يلزم السماح بالأجهزة القريبة/البلوتوث"
            }
            PairingBeacon.REQUEST_ADVERTISE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    activePairingBeacon?.start(activePairingCode, activePairingProvision, activePairingMode)
                } else {
                    status.text = "Bluetooth للربط مرفوض؛ QR وHotspot المحليان يواصلان العمل بدون إنترنت"
                }
            }
            REQUEST_GPS_PERMISSION -> {
                status.text = if(grantResults.any{it==PackageManager.PERMISSION_GRANTED}) "تم منح صلاحية الموقع — أعد اختيار GPS لإكمال الحضور" else "لم يتم السماح بالموقع"
            }
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    status.text = "تم منح صلاحية الكاميرا"
                    launchFaceCamera()
                } else {
                    clearPendingFaceCapture()
                    status.text = "يلزم السماح بالكاميرا لاستخدام بصمة الوجه"
                }
            }
            REQUEST_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    val enrolling = pendingVoiceEnrollmentEmployeeId?.let { id -> repo.employees().firstOrNull { it.employeeId.equals(id, true) } }
                    val verifying = pendingVoiceEmployeeId?.let { id -> findActiveEmployee(id) }
                    when { enrolling != null -> promptVoiceEnrollmentSample(); verifying != null -> captureVoiceForVerification(verifying) }
                } else {
                    pendingVoiceEnrollmentEmployeeId = null; pendingVoiceEmployeeId = null; pendingVoiceAttendanceAction = null
                    info("صلاحية الميكروفون", "يلزم السماح بالميكروفون لتسجيل بصمة نبرة الصوت والتحقق منها.")
                }
            }
        }
    }
    private fun legacyVoicePhrase(value: String): String = value.trim().lowercase(Locale.getDefault())
        .replace(Regex("[\\p{Punct}\\s]+"), " ").trim()

    private fun normalizeVoicePhrase(value: String): String = legacyVoicePhrase(value)
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ؤ', 'و').replace('ئ', 'ي').replace('ة', 'ه')

    private fun voiceCandidateForms(value: String): Set<String> = setOf(legacyVoicePhrase(value), normalizeVoicePhrase(value)).filter { it.isNotBlank() }.toSet()

    private fun voicePhraseSimilarity(candidate: String, expected: String): Float {
        val a = normalizeVoicePhrase(candidate)
        val b = normalizeVoicePhrase(expected)
        if (a.isBlank() || b.isBlank()) return 0f
        if (a.contains(b) || b.contains(a)) return 1f
        val aw = a.split(' ').filter { it.isNotBlank() }.toSet()
        val bw = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (aw.isEmpty() || bw.isEmpty()) return 0f
        val exact = aw.intersect(bw).size.toFloat()
        val fuzzy = aw.count { word -> bw.any { voiceWordDistance(word, it) <= 1 } }.toFloat()
        return maxOf(exact, fuzzy) / maxOf(aw.size, bw.size)
    }

    private fun voiceWordDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > 1) return 2
        val dp = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            var previous = dp[0]; dp[0] = i + 1
            for (j in b.indices) {
                val old = dp[j + 1]
                dp[j + 1] = minOf(dp[j + 1] + 1, dp[j] + 1, previous + if (a[i] == b[j]) 0 else 1)
                previous = old
            }
        }
        return dp[b.length]
    }

    private fun resolvedSpeechLanguage(): String = when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
        "ar" -> "ar"
        else -> Locale.getDefault().toLanguageTag()
    }

    private fun methodLabel(method: AttendanceMethod): String = when(method){
        AttendanceMethod.PIN -> "طريقة قديمة محفوظة للسجل"
        AttendanceMethod.PASSWORD -> "كلمة المرور"
        AttendanceMethod.PATTERN -> "طريقة قديمة محفوظة للسجل"
        AttendanceMethod.VOICE_PHRASE -> "العبارة الصوتية"
        AttendanceMethod.GPS -> "تأكيد موقع قديم"
        AttendanceMethod.EXTERNAL_FINGERPRINT -> "بصمة خارجية"
        AttendanceMethod.SHARED_DEVICE_FACE -> "الوجه"
        AttendanceMethod.PHONE_BLE_BIOMETRIC -> "هاتف + بصمة/وجه"
        AttendanceMethod.PHONE_FINGERPRINT -> "بصمة إصبع الهاتف فقط"
        AttendanceMethod.PHONE_PROXIMITY -> "QR مباشر"
        AttendanceMethod.PHONE_BIOMETRIC -> "اعتماد هاتف قديم"
        AttendanceMethod.SUPERVISOR_OVERRIDE -> "بإشراف"
        AttendanceMethod.MANUAL_ADMIN -> "إداري"
    }

    private fun allowedMethodsArabic(employee: PairedEmployee): String {
        val methods = if (employee.allowedMethods.isEmpty()) {
            listOf(AttendanceMethod.PASSWORD, AttendanceMethod.VOICE_PHRASE, AttendanceMethod.SHARED_DEVICE_FACE,
                AttendanceMethod.EXTERNAL_FINGERPRINT, AttendanceMethod.PHONE_BLE_BIOMETRIC, AttendanceMethod.PHONE_PROXIMITY)
        } else employee.allowedMethods.mapNotNull { runCatching { AttendanceMethod.valueOf(it) }.getOrNull() }
        val visible = methods.filterNot { it == AttendanceMethod.PIN || it == AttendanceMethod.PATTERN || it == AttendanceMethod.GPS || it == AttendanceMethod.PHONE_BIOMETRIC }
        return visible.joinToString("، ") { methodLabel(it) }.ifBlank { "غير محددة" }
    }

    private fun info(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("حسنًا", null).show()
    }

    override fun onDestroy(){nearbyRefreshHandler.removeCallbacks(nearbyRefreshTask);scanner.stop();networkListener.stop();StoreDirectLinkBridge1977.unbind(directBle);directBle.stop();voiceAnnouncer.shutdown();super.onDestroy()}
    private fun adaptiveVoiceThreshold(captureQuality: Int, enrolledQuality: Int): Float {
        // High-quality samples stay strict. Moderate/noisy samples get a small tolerance,
        // while the spoken phrase + random challenge still provide the second factor.
        val q = minOf(captureQuality, enrolledQuality.takeIf { it > 0 } ?: captureQuality)
        return when {
            q >= 80 -> 0.54f
            q >= 65 -> 0.51f
            q >= 50 -> 0.48f
            else -> 0.46f
        }
    }

    companion object {
        private const val GPS_RECOGNITION_FRESH_MILLIS = 5 * 60_000L
        private const val GPS_RECOGNITION_CHANNEL = "attend_gps_recognition"
        const val EXTRA_EMPLOYEE_MANAGER = "open_employee_manager"
        const val EXTRA_STORE_ADMIN_SESSION = "store_admin_session"
        private const val REQUEST_FACE_CAPTURE = 6201
        private const val REQUEST_VOICE_VERIFY = 6203
        private const val REQUEST_GPS_PERMISSION = 6204
        private const val REQUEST_CAMERA_PERMISSION = 6205
        private const val REQUEST_AUDIO_PERMISSION = 6206
        private const val VOICE_PRINT_THRESHOLD = 0.52f
    }

}

private class SignaturePadView(context: android.content.Context) : View(context) {
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(18, 72, 82); style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val points = StringBuilder()
    private var strokes = 0

    init { setBackgroundColor(android.graphics.Color.rgb(248, 250, 250)) }

    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); canvas.drawPath(path, paint) }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.coerceIn(0f, width.toFloat()); val y = event.y.coerceIn(0f, height.toFloat())
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { path.moveTo(x, y); points.append('M'); strokes++; parent?.requestDisallowInterceptTouchEvent(true) }
            MotionEvent.ACTION_MOVE -> path.lineTo(x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
            else -> return true
        }
        points.append((x / width.coerceAtLeast(1) * 1000).toInt()).append(',')
            .append((y / height.coerceAtLeast(1) * 1000).toInt()).append(';')
        invalidate(); return true
    }

    fun hasSignature(): Boolean = strokes > 0 && points.length >= 30
    fun clear() { path.reset(); points.setLength(0); strokes = 0; invalidate() }
    fun digest(): String = MessageDigest.getInstance("SHA-256").digest(points.toString().toByteArray())
        .joinToString("") { "%02x".format(it) }
}
