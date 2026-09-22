package com.attendpro.store

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.attendpro.core.CentralServerClient
import com.attendpro.core.ReportReceiverStore
import com.attendpro.core.ReceiverOfflineReportProtocol
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * V140 always-on receiver transport.
 *
 * Keeps nearby LAN/BLE receiving alive after the UI closes and polls the central inbox
 * for every active report-enabled Store binding. It does not touch employee BLE/pairing.
 */
class ReceiverReportService : Service() {
    companion object {
        private const val CHANNEL_ID = "receiver_reports_v140"
        private const val NOTIFICATION_ID = 2140
        private const val POLL_MS = 12_000L
        const val PREFS = "receiver_report_service_v140"
        const val KEY_NEAR_PAIRING_UNTIL = "near_pairing_until"
        private const val KEY_PENDING_UNLINKS = "pending_unlinks"

        fun ensureStarted(context: Context) {
            val intent = Intent(context, ReceiverReportService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stopIfUnused(context: Context) {
            val receiver = ReportReceiverStore(context)
            val pairingUntil = context.getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_NEAR_PAIRING_UNTIL, 0L)
            if (receiver.storeBindings().none { it.active } && pairingUntil <= System.currentTimeMillis()) {
                context.stopService(Intent(context, ReceiverReportService::class.java))
            }
        }

        fun enableNearbyPairing(context: Context, durationMs: Long = 120_000L): Long {
            val until = System.currentTimeMillis() + durationMs.coerceIn(30_000L, 300_000L)
            context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(KEY_NEAR_PAIRING_UNTIL, until).apply()
            ensureStarted(context)
            return until
        }

        fun nearbyPairingActive(context: Context): Boolean =
            context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getLong(KEY_NEAR_PAIRING_UNTIL, 0L) > System.currentTimeMillis()

        fun queueServerUnlink(context: Context, serverUrl: String, storeId: String) {
            if (serverUrl.isBlank() || storeId.isBlank()) return
            val prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE)
            val old = prefs.getStringSet(KEY_PENDING_UNLINKS, emptySet()).orEmpty()
            prefs.edit().putStringSet(KEY_PENDING_UNLINKS, old + "$serverUrl\t$storeId").apply()
            ensureStarted(context)
        }
    }

    private lateinit var receiver: ReportReceiverStore
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var lanServer: ReceiverReportLanServer? = null
    private var bleServer: ReceiverReportBleServer? = null

    override fun onCreate() {
        super.onCreate()
        receiver = ReportReceiverStore(this)
        startForeground(NOTIFICATION_ID, buildNotification("جاهز لاستلام التقارير"))
        running.set(true)
        startTransports()
        worker = thread(name = "receiver-report-v140", isDaemon = true) { loop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startTransports()
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        worker?.interrupt()
        worker = null
        lanServer?.stop()
        bleServer?.stop()
        lanServer = null
        bleServer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTransports() {
        if (lanServer?.isRunning() != true) {
            lanServer?.stop()
            lanServer = ReceiverReportLanServer(
                receiver.receiverId,
                receiver.secret,
                ::acceptOfflineReport,
                ::nearbyInvite
            ) { updateStatus(it) }.also { it.start() }
        }

        if (ReceiverReportBluetoothSupport.hasPermissions(this) &&
            ReceiverReportBluetoothSupport.bluetoothEnabled(this) &&
            bleServer?.isRunning() != true
        ) {
            bleServer?.stop()
            bleServer = ReceiverReportBleServer(
                this,
                receiver.receiverId,
                receiver.secret,
                ::acceptOfflineReport,
                ::nearbyInvite
            ) { updateStatus(it) }.also { it.start() }
        }
    }

    private fun nearbyInvite(): String? {
        if (!nearbyPairingActive(this)) return null
        return com.attendpro.core.ReportProtocol.encodeInvite(receiver.newInvite())
    }

    private fun acceptOfflineReport(envelope: ReceiverOfflineReportProtocol.Envelope): Boolean {
        val binding = receiver.storeBindings().firstOrNull {
            it.storeId == envelope.storeId && it.active && it.canReceiveReports
        } ?: return false
        val item = receiver.receive(envelope.packageText, envelope.storeId) ?: return false
        updateStatus("تم استلام تقرير من ${binding.storeName} عبر القرب ✓")
        return item.transferId == envelope.transferId
    }

    private fun loop() {
        while (running.get()) {
            runCatching {
                startTransports()
                flushPendingUnlinks()
                pollServerReports()
                val hasBindings = receiver.storeBindings().any { it.active }
                val pairing = nearbyPairingActive(this)
                if (!hasBindings && !pairing) {
                    stopSelf()
                    return
                }
            }
            try {
                Thread.sleep(POLL_MS)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    private fun flushPendingUnlinks() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val pending = prefs.getStringSet(KEY_PENDING_UNLINKS, emptySet()).orEmpty().toSet()
        if (pending.isEmpty()) return
        val completed = mutableSetOf<String>()
        pending.forEach { row ->
            val parts = row.split('\t', limit = 2)
            if (parts.size != 2) {
                completed += row
                return@forEach
            }
            val result = CentralServerClient.receiverUnlinkStore(
                parts[0], receiver.receiverId, receiver.secret, parts[1]
            )
            if (result.isSuccess) completed += row
        }
        if (completed.isNotEmpty()) {
            prefs.edit().putStringSet(KEY_PENDING_UNLINKS, pending - completed).apply()
        }
    }

    private fun pollServerReports() {
        receiver.storeBindings()
            .filter { it.active && it.canReceiveReports && it.storeId.isNotBlank() && it.serverUrl.isNotBlank() }
            .forEach { binding ->
                val result = CentralServerClient.receiverInbox(
                    binding.serverUrl, receiver.receiverId, receiver.secret, binding.storeId
                )
                if (result.isFailure) return@forEach
                var receivedCount = 0
                result.getOrThrow().forEach { remote ->
                    val item = receiver.receive(remote.packageText, binding.storeId) ?: return@forEach
                    receivedCount++
                    CentralServerClient.confirmRemoteReport(
                        binding.serverUrl,
                        receiver.receiverId,
                        receiver.secret,
                        item.transferId,
                        item.confirmationCode,
                        binding.storeId
                    )
                }
                if (receivedCount > 0) updateStatus("وصل $receivedCount تقرير عبر الخادم ✓")
            }
    }

    private fun updateStatus(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "استلام تقارير ATTEND PRO",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val open = PendingIntent.getActivity(
            this,
            140,
            Intent(this, ReportReceiverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder
            .setSmallIcon(com.attendpro.store.R.drawable.ic_attend_pro)
            .setContentTitle("ATTEND PRO — هاتف الاستلام")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }
}

class ReceiverReportBootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val receiver = ReportReceiverStore(context)
            if (receiver.storeBindings().any { it.active }) {
                ReceiverReportService.ensureStarted(context)
            }
        }
    }
}
