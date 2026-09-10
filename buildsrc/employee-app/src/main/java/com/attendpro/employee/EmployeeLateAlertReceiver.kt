package com.attendpro.employee

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.attendpro.core.EmployeeIdentityStore

/** Delivers the employee-side late notification even when the store Internet connection is absent. */
class EmployeeLateAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val identity = EmployeeIdentityStore(context)
        if (intent?.action != EmployeeLateAlertScheduler.ACTION_LATE_ALERT || !identity.isConfigured || !identity.lateAlertEnabled) return
        val now = System.currentTimeMillis()
        val window = EmployeeLateAlertScheduler.windowFor(identity, now)
        if (identity.lastPresenceProofAt in window.proofCutoff()..now) {
            EmployeeLateAlertScheduler.sync(context, identity, now)
            return
        }
        val due = EmployeeLateAlertScheduler.dueForWindow(identity, window)
        val end = window.end + EmployeeLateAlertScheduler.ALERT_TAIL_MILLIS
        if (now < due || now > end) {
            EmployeeLateAlertScheduler.sync(context, identity, now)
            return
        }

        val prefs = context.getSharedPreferences("attend_employee_late_state", Context.MODE_PRIVATE)
        val dayKey = EmployeeLateAlertScheduler.operationalDayKey(window)
        val countKey = "count_$dayKey"
        val lastKey = "last_$dayKey"
        val count = prefs.getInt(countKey, 0)
        val max = identity.lateAlertCount.coerceIn(1, 10)
        val repeatMillis = identity.lateRepeatMinutes.coerceIn(5, 180) * 60_000L
        val last = prefs.getLong(lastKey, 0L)
        if (count >= max || (last > 0L && now - last < repeatMillis - 5_000L)) {
            EmployeeLateAlertScheduler.sync(context, identity, now)
            return
        }
        prefs.edit().putInt(countKey, count + 1).putLong(lastKey, now).apply()

        val nearStore = now - identity.lastBleDirectSeenAt <= 20_000L ||
            now - identity.lastLanStoreSeenAt <= 20_000L ||
            now - identity.lastGpsInsideAt <= 5 * 60_000L
        notifyEmployee(context, identity, nearStore, count + 1, max)

        if (identity.employeeVoicePromptsEnabled && identity.lateAlertMode == "NOTIFICATION_VOICE") {
            val voice = if (nearStore) identity.employeeMissingProofVoiceText else identity.employeeLateVoiceText
            val text = voice.replace("{name}", identity.displayName).trim()
            val service = Intent(context, PresenceService::class.java)
                .setAction(PresenceService.ACTION_LATE_ALERT)
                .putExtra(PresenceService.EXTRA_VOICE_TEXT, text)
                .putExtra(PresenceService.EXTRA_BACKGROUND_RESTART, true)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service) else context.startService(service)
            }
        }
        EmployeeLateAlertScheduler.sync(context, identity, now)
    }

    private fun notifyEmployee(context: Context, identity: EmployeeIdentityStore, nearStore: Boolean, sequence: Int, max: Int) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "تنبيه موعد الدوام", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تنبيه محلي إذا بدأ الدوام ولم يثبت الموظف حضوره"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300, 150, 500)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        }
        val open = PendingIntent.getActivity(
            context, 49392,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_late_attendance", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (nearStore) "أنت قريب من المحل ولم تثبت حضورك" else "بدأ موعد دوامك"
        val text = if (nearStore)
            "تم اكتشاف وجودك قرب ${identity.trustedStoreName.ifBlank { "المحل" }}، يرجى إثبات الحضور الآن"
        else "موعد دوامك بدأ ولم يتم إثبات حضورك • تنبيه $sequence/$max"
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(context)
        @Suppress("DEPRECATION")
        val notification = builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setPriority(Notification.PRIORITY_MAX).setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC).setAutoCancel(true).setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_view, "إثبات الحضور", open).build()
        manager.notify(49392, notification)
    }

    companion object { private const val CHANNEL = "attend_employee_late_alerts" }
}
