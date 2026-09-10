package com.attendpro.employee

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.Manifest
import com.attendpro.core.PhoneAttendancePolicy
import com.attendpro.core.CentralServerClient
import com.attendpro.core.EmployeeIdentityStore
import com.attendpro.core.AttendanceMethod
import com.attendpro.core.AttendanceAction

class PresenceService : Service() {
    companion object {
        const val ACTION_START = "com.attendpro.employee.PRESENCE_START"
        const val ACTION_PROOF = "com.attendpro.employee.PRESENCE_PROOF"
        const val ACTION_STOP = "com.attendpro.employee.PRESENCE_STOP"
        const val ACTION_LATE_ALERT = "com.attendpro.employee.PRESENCE_LATE_ALERT"
        const val EXTRA_VOICE_TEXT = "voice_text"
        const val EXTRA_FLAGS = "flags"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_BACKGROUND_RESTART = "background_restart"
        private const val CHANNEL = "attend_presence"
        private const val NOTIFICATION_ID = 771
    }
    private lateinit var identity: EmployeeIdentityStore
    private lateinit var network: NetworkPresenceBroadcaster
    private lateinit var bluetooth: BlePresenceAdvertiser
    private lateinit var localChallenges: LocalChallengeListener
    private lateinit var directBle: BleDirectLinkServer
    private lateinit var geoMonitor: OfflineGeoMonitor
    private lateinit var voicePrompter: EmployeeVoicePrompter
    private val handler = Handler(Looper.getMainLooper())
    private var locationMonitoringAllowedForRun: Boolean = false
    private var lastLocalChallengeMethod: String = ""
    private var lastLocalChallengeAt: Long = 0L
    @Volatile private var serverLinkInFlight = false
    @Volatile private var challengePollInFlight1981 = false
    @Volatile private var messagePollInFlight1981 = false
    private var lastServerLinkAttemptAt = 0L
    private var lastMessagePollAt1975 = 0L
    private var nextChallengePollAt1981 = 0L
    private var nextMessagePollAt1981 = 0L
    private var challengeFailures1981 = 0
    private var messageFailures1981 = 0
    private var networkCallback1981: ConnectivityManager.NetworkCallback? = null
    private val challengePoller = object : Runnable {
        override fun run() {
            identity.presenceServiceHeartbeatAt = System.currentTimeMillis()
            ensurePresenceChannels()
            if (identity.isConfigured) {
                if (identity.serverLinked) {
                    val now = System.currentTimeMillis()
                    if (now >= nextChallengePollAt1981) pollChallenge()
                    if (now - lastMessagePollAt1975 >= 30_000L && now >= nextMessagePollAt1981) {
                        lastMessagePollAt1975 = now
                        pollMessages1975()
                    }
                } else ensureServerLinkWhenAvailable()
            }
            handler.postDelayed(this, 10_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AttendanceRequestNotifier.ensureChannel(this); identity = EmployeeIdentityStore(this)
        network = NetworkPresenceBroadcaster({ message ->
            identity.lastLanState = message
        }, { ackAt ->
            identity.lastLanStoreSeenAt = ackAt
        })
        bluetooth = BlePresenceAdvertiser(this) { message ->
            identity.lastBleAdvertisingState = message
            identity.lastBleAdvertisingAt = System.currentTimeMillis()
            if (message.contains("BLE يعمل")) identity.lastBleDirectState = identity.lastBleDirectState.ifBlank { "الهاتف يبث عبر BLE بانتظار اتصال المحل المباشر" }
        }
        localChallenges = LocalChallengeListener { id, method, expires, action -> receiveLocalChallenge(id, method, expires, action) }
        voicePrompter = EmployeeVoicePrompter(this)
        directBle = BleDirectLinkServer(this, identity, { message -> identity.lastBleDirectState = message }) { id, method, expires, action ->
            receiveLocalChallenge(id, method, expires, action)
        }
        geoMonitor = OfflineGeoMonitor(
            this,
            identity,
            onEntered = { distance, accuracy ->
                AttendanceRequestNotifier.notifyGeoArrival(this, identity.displayName, identity.trustedStoreName, distance)
                if (identity.employeeVoicePromptsEnabled) {
                    voicePrompter.speak("يا ${identity.displayName}، تم التعرف على هاتفك بالقرب من ${identity.trustedStoreName} عبر الموقع. هذا لا يسجل الحضور تلقائيًا")
                }
                identity.lastGpsDistanceMeters = distance
                identity.lastGpsAccuracyMeters = accuracy
            },
            onObservation = { state, distance, accuracy, observedAt ->
                maybeUploadGpsObservation(state, distance, accuracy, observedAt)
            },
            onStatus = { _ -> Unit }
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"اكتشاف جهاز المحل",NotificationManager.IMPORTANCE_LOW).apply {
            description = "يحافظ على التعرف الآمن على هاتف الموظف عبر الشبكة المحلية"
        })
        registerNetworkCallback1981()
    }

    private fun registerNetworkCallback1981() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || networkCallback1981 != null) return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handler.post {
                    nextChallengePollAt1981 = 0L
                    nextMessagePollAt1981 = 0L
                    lastServerLinkAttemptAt = 0L
                    if (identity.isConfigured && !identity.serverLinked) ensureServerLinkWhenAvailable()
                }
            }
        }
        if (runCatching { cm.registerDefaultNetworkCallback(callback) }.isSuccess) networkCallback1981 = callback
    }

    private fun unregisterNetworkCallback1981() {
        val callback = networkCallback1981 ?: return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        runCatching { cm.unregisterNetworkCallback(callback) }
        networkCallback1981 = null
    }

    private fun serverBackoff1981(failures: Int, baseMillis: Long, maxMillis: Long): Long {
        val shift = (failures - 1).coerceIn(0, 4)
        return (baseMillis * (1L shl shift)).coerceAtMost(maxMillis)
    }

    private fun canAdvertiseAndServeGatt(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || (
        checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        )

    private fun canScanBleChallenges(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        else -> checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun startBleChannelsIfPermitted() {
        // Keep the Bluetooth subchannels independent too: advertising/GATT only need nearby-device
        // advertise/connect on Android 12+, while challenge scanning needs SCAN (or location on 8-11).
        if (canAdvertiseAndServeGatt()) {
            if (!bluetooth.isRunning()) bluetooth.start(identity.employeeId, identity.pairingSecret)
            if (!directBle.isRunning()) directBle.start()
        } else {
            bluetooth.stop(); directBle.stop()
            identity.lastBleAdvertisingState = "Bluetooth متوقف: اسمح بالأجهزة القريبة من شاشة التطبيق"
            identity.lastBleDirectState = "GATT متوقف: صلاحية Bluetooth مطلوبة"
        }

        if (canScanBleChallenges()) {
            val secret = com.attendpro.core.SecretCodec.decode(identity.pairingSecret) ?: ByteArray(0)
            BleChallengeInbox.start(this, identity.employeeId, secret) { id, method, expires, action ->
                receiveLocalChallenge(id, method, expires, action)
            }
        } else {
            BleChallengeInbox.stop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        identity.presenceServiceHeartbeatAt = System.currentTimeMillis()
        if (intent?.action == ACTION_STOP || !identity.isConfigured) {
            network.stop(); bluetooth.stop(); localChallenges.stop(); directBle.stop(); geoMonitor.stop(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY
        }
        val hasForegroundLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBackgroundLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        val backgroundRestart = intent == null || intent.getBooleanExtra(EXTRA_BACKGROUND_RESTART, false)
        locationMonitoringAllowedForRun = identity.geoArrivalAlertsEnabled && identity.isTrustedStoreGpsConfigured && hasForegroundLocation &&
            (!backgroundRestart || hasBackgroundLocation)
        val foregroundStarted = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or if (locationMonitoringAllowedForRun) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
                startForeground(NOTIFICATION_ID, notification(), types)
            } else startForeground(NOTIFICATION_ID, notification())
        }.isSuccess
        if (!foregroundStarted) {
            // Android 14/15 or OEM policy may reject a background FGS when runtime prerequisites are unavailable.
            network.stop(); bluetooth.stop(); localChallenges.stop(); directBle.stop(); geoMonitor.stop(); stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_LATE_ALERT) {
            val voice = intent.getStringExtra(EXTRA_VOICE_TEXT).orEmpty().trim()
            if (voice.isNotBlank() && identity.employeeVoicePromptsEnabled) voicePrompter.speak(voice)
        }
        EmployeeLateAlertScheduler.sync(this, identity)
        if (identity.autoPresence) {
            // LAN/server are independent of Bluetooth and must never be killed by a denied BLE permission.
            network.start(identity.employeeId, identity.pairingSecret)
            localChallenges.start(identity.employeeId, identity.pairingSecret)
            startBleChannelsIfPermitted()
            if (locationMonitoringAllowedForRun) geoMonitor.start() else geoMonitor.stop()
        } else {
            network.stop(); bluetooth.stop(); localChallenges.stop(); directBle.stop(); geoMonitor.stop()
        }
        handler.removeCallbacks(challengePoller); handler.post(challengePoller)
        if (intent?.action == ACTION_PROOF) {
            val duration = intent.getLongExtra(EXTRA_DURATION,45_000L)
            val proof = intent.getIntExtra(EXTRA_FLAGS,0)
            network.markVerified(duration, proof)
            bluetooth.markVerified(duration, proof)
        }
        return START_STICKY
    }

    private fun ensurePresenceChannels() {
        if (!identity.isConfigured || !identity.autoPresence) return
        if (!network.isRunning()) network.start(identity.employeeId, identity.pairingSecret)
        if (!localChallenges.isRunning()) localChallenges.start(identity.employeeId, identity.pairingSecret)
        startBleChannelsIfPermitted()
        if (locationMonitoringAllowedForRun && !geoMonitor.isRunning()) geoMonitor.start()
        if (!locationMonitoringAllowedForRun && geoMonitor.isRunning()) geoMonitor.stop()
    }



    private fun receiveLocalChallenge(id: String, method: AttendanceMethod, expires: Long, action: AttendanceAction) {
        val now = System.currentTimeMillis()
        if (id == identity.pendingChallengeId || expires <= now) return
        if (!identity.acceptChallengeOnce(id, expires)) return
        // BLE direct, BLE advertisement and LAN may deliver the same user request nearly together.
        if (lastLocalChallengeMethod == method.name && now - lastLocalChallengeAt < 2_500L) return
        lastLocalChallengeMethod = method.name; lastLocalChallengeAt = now
        AttendanceRequestNotifier.notifyChallenge(this, id, method, expires, action)
        if (identity.employeeVoicePromptsEnabled) {
            val configured = identity.employeeRequestVoiceText.replace("{name}", identity.displayName).trim()
            voicePrompter.speak(configured.ifBlank { "${identity.displayName}، يرجى إثبات ${if (action == AttendanceAction.CHECK_IN) "حضورك" else "انصرافك"}" })
        }
        identity.pendingChallengeId = id
        identity.pendingChallengeMethod = method.name
        identity.pendingChallengeExpiresAt = expires
        identity.pendingChallengeAction = action.name
    }


    private fun maybeUploadGpsObservation(state: String, distance: Int, accuracy: Int, observedAt: Long) {
        if (!identity.serverLinked || !identity.isConfigured || identity.serverUrl.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - identity.lastGpsServerUploadAt < 20_000L) return
        identity.lastGpsState = state
        identity.lastGpsObservedAt = observedAt
        identity.lastGpsDistanceMeters = distance
        identity.lastGpsAccuracyMeters = accuracy
        identity.lastGpsServerUploadAt = now
        Thread {
            CentralServerClient.sendEmployeeGeoObservation(
                identity.serverUrl,
                identity.trustedStoreId,
                identity.employeeId,
                identity.pairingSecret,
                identity.installationId,
                state,
                distance,
                accuracy,
                observedAt
            ).onFailure {
                // Do not mark the employee server link dead because a geo telemetry write failed.
                // Challenge polling/registration remains the authoritative server connection.
            }
        }.apply { isDaemon = true }.start()
    }

    private fun ensureServerLinkWhenAvailable() {
        val now = System.currentTimeMillis()
        if (serverLinkInFlight || now - lastServerLinkAttemptAt < 45_000L || !identity.isConfigured) return
        lastServerLinkAttemptAt = now
        serverLinkInFlight = true
        Thread {
            val result = CentralServerClient.registerEmployeePhone(
                identity.serverUrl, identity.trustedStoreId, identity.employeeId, identity.displayName,
                identity.branchId, identity.pairingSecret, identity.installationId, identity.allowedMethods
            )
            result.onSuccess { link ->
                identity.serverLinked = link.linked
                if (link.linked) identity.linkedAt = link.linkedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
            }.onFailure {
                identity.serverLinked = false
            }
            serverLinkInFlight = false
        }.start()
    }

    private fun pollChallenge() {
        val now = System.currentTimeMillis()
        if (challengePollInFlight1981 || now < nextChallengePollAt1981) return
        challengePollInFlight1981 = true
        Thread {
            try {
                val result = CentralServerClient.pollEmployeePresenceChallenge(identity.serverUrl,identity.trustedStoreId,identity.employeeId,identity.pairingSecret,identity.installationId)
                if (result.isFailure) {
                    challengeFailures1981++
                    nextChallengePollAt1981 = System.currentTimeMillis() + serverBackoff1981(challengeFailures1981, 10_000L, 60_000L)
                    if (challengeFailures1981 >= 3) identity.serverLinked = false
                    return@Thread
                }
                challengeFailures1981 = 0
                nextChallengePollAt1981 = 0L
                identity.serverLinked = true
                val challenge = result.getOrNull() ?: return@Thread
                if(challenge.challengeId==identity.pendingChallengeId || challenge.expiresAt<=System.currentTimeMillis()) return@Thread
                if (!identity.acceptChallengeOnce(challenge.challengeId, challenge.expiresAt)) return@Thread
                val action = challenge.action ?: AttendanceAction.CHECK_IN
                identity.pendingChallengeId=challenge.challengeId;identity.pendingChallengeMethod=challenge.requiredMethod.name;identity.pendingChallengeExpiresAt=challenge.expiresAt;identity.pendingChallengeAction=action.name
                AttendanceRequestNotifier.notifyChallenge(this, challenge.challengeId, challenge.requiredMethod, challenge.expiresAt, action)
                if (identity.employeeVoicePromptsEnabled) {
                    val configured = identity.employeeRequestVoiceText.replace("{name}", identity.displayName).trim()
                    voicePrompter.speak(configured.ifBlank { "${identity.displayName}، يرجى إثبات ${if (action == AttendanceAction.CHECK_IN) "حضورك" else "انصرافك"}" })
                }
            } finally {
                challengePollInFlight1981 = false
            }
        }.apply { isDaemon = true }.start()
    }

    private fun pollMessages1975() {
        if (!identity.employeeMessageNotificationsEnabled) return
        val now = System.currentTimeMillis()
        if (messagePollInFlight1981 || now < nextMessagePollAt1981) return
        messagePollInFlight1981 = true
        Thread {
            try {
                val result = CentralServerClient.employeeMessages(identity.serverUrl, identity.trustedStoreId, identity.employeeId, identity.pairingSecret, identity.installationId, false, 20)
                if (result.isFailure) {
                    messageFailures1981++
                    nextMessagePollAt1981 = System.currentTimeMillis() + serverBackoff1981(messageFailures1981, 30_000L, 120_000L)
                    return@Thread
                }
                messageFailures1981 = 0
                nextMessagePollAt1981 = 0L
                result.getOrNull().orEmpty().forEach { message -> EmployeeMessageNotifier1975.notify(this, message) }
            } finally {
                messagePollInFlight1981 = false
            }
        }.apply { isDaemon = true }.start()
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_attend_pro)
            .setContentTitle("ATTEND PRO — هاتف الموظف")
            .setContentText(if(identity.autoPresence) "Bluetooth مباشر وBLE وWi‑Fi وGPS المحلي تعمل" else "طلبات إثبات الوجود مفعلة لهذا الهاتف")
            .setContentIntent(open).setOngoing(true).build()
    }

    override fun onDestroy(){
        identity.presenceServiceHeartbeatAt = 0L
        handler.removeCallbacks(challengePoller);network.stop();bluetooth.stop();localChallenges.stop();directBle.stop();geoMonitor.stop();BleChallengeInbox.stop();voicePrompter.shutdown()
        unregisterNetworkCallback1981()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
