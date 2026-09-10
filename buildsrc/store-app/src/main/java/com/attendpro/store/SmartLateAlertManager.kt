package com.attendpro.store

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import com.attendpro.core.AttendanceAction
import com.attendpro.core.AttendanceMethod
import com.attendpro.core.CentralServerClient
import com.attendpro.core.DeviceIdentity
import com.attendpro.core.PairedEmployee
import com.attendpro.core.StoreRepository
import com.attendpro.core.ShiftWindow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SmartLateAlertManager(
    private val context: Context,
    private val repo: StoreRepository,
    private val announcer: StoreVoiceAnnouncer,
    private val onStatus: (String) -> Unit,
    private val allowSpeech: Boolean = true
) {
    companion object { private const val CHANNEL = "attend_late_alerts" }
    private val prefs = context.getSharedPreferences("attend_late_alert_state", Context.MODE_PRIVATE)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "تنبيهات تأخر الموظفين", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تنبيه ذكي عند تجاوز موعد حضور الموظف"
                enableVibration(true)
            }
        )
    }

    fun tick(now: Long = System.currentTimeMillis()) {
        if (!repo.smartLateAlertsEnabled) return
        ensureChannel()
        val latestPresence = repo.presenceEvents().groupBy { it.employeeId.lowercase() }.mapValues { (_, list) -> list.maxOfOrNull { it.timestampEpochMillis } ?: 0L }
        repo.employees().filter { it.active && it.lateAlertEnabled }.forEach { employee ->
            val window = LateAlertScheduler.windowFor(employee, repo, now)
            val workDay = Calendar.getInstance().apply { timeInMillis = window.start }.get(Calendar.DAY_OF_WEEK)
            if (!repo.isEmployeeWorkDay(employee.employeeId, workDay)) return@forEach
            val hasCheckIn = repo.events().any { event ->
                event.employeeId.equals(employee.employeeId, true) &&
                    event.action == AttendanceAction.CHECK_IN &&
                    event.timestampEpochMillis in window.proofCutoff()..now
            }
            if (hasCheckIn) return@forEach
            val repeatMinutes = employee.lateRepeatMinutes.takeIf { it >= 5 } ?: repo.lateAlertRepeatMinutes
            val due = LateAlertScheduler.dueForWindow(employee, repo, window)
            val end = window.end + LateAlertScheduler.ALERT_TAIL_MILLIS
            if (now < due || now > end) return@forEach
            val dayKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(window.start))
            val key = "late_${dayKey}_${employee.employeeId}"
            val countKey = "late_count_${dayKey}_${employee.employeeId}"
            val last = prefs.getLong(key, 0L)
            val count = prefs.getInt(countKey, 0)
            if (count >= employee.lateAlertCount.coerceIn(1, 10)) return@forEach
            if (last > 0L && now - last < repeatMinutes * 60_000L) return@forEach
            prefs.edit().putLong(key, now).putInt(countKey, count + 1).apply()

            val connected = now - (latestPresence[employee.employeeId.lowercase()] ?: 0L) <= 20_000L
            if (allowSpeech && repo.attendanceVoiceAnnouncementEnabled && employee.lateAlertMode == "NOTIFICATION_VOICE") {
                if (connected) announcer.announceMissingProof(employee.displayName) else announcer.announceLate(employee.displayName)
            }
            if (repo.smartAlertAudience != "EMPLOYEE_ONLY") notifyLate(employee, connected, count + 1)
            if (repo.smartAlertAudience != "OWNER_ONLY" && repo.lateNotifyEmployeeViaServer) notifyEmployeeViaServer(employee, AttendanceAction.CHECK_IN, "تنبيه تأخر")
            repo.addPresenceEvent(com.attendpro.core.PresenceEvent(
                employeeId = employee.employeeId, employeeName = employee.displayName, timestampEpochMillis = now,
                channel = "تنبيه ذكي", rssi = -127, details = "تأخر عن الدوام • ${if (connected) "متصل بالمحل دون إثبات" else "غير مثبت الحضور"}"
            ))
            val callMode = employee.lateCallMode.ifBlank { if (repo.lateAutoCallEnabled) "AUTO_IF_ALLOWED" else "NONE" }
            if (callMode == "AUTO_IF_ALLOWED" && employee.phone.isNotBlank()) maybeAutoCall(employee, dayKey)
        }
        tickCheckoutReminders(now)
    }

    private fun tickCheckoutReminders(now: Long) {
        if (!repo.checkoutReminderEnabled) return
        repo.employees().filter { it.active }.forEach { employee ->
            val window = LateAlertScheduler.windowFor(employee, repo, now)
            val workDay = Calendar.getInstance().apply { timeInMillis = window.start }.get(Calendar.DAY_OF_WEEK)
            if (!repo.isEmployeeWorkDay(employee.employeeId, workDay)) return@forEach
            val due = window.end + repo.checkoutReminderDelayMinutes.coerceIn(0, 180) * 60_000L
            if (now < due || now > window.end + 12 * 60 * 60_000L) return@forEach
            val events = repo.events().filter { it.employeeId.equals(employee.employeeId, true) && it.timestampEpochMillis in window.start..now }
            val hasCheckIn = events.any { it.action == AttendanceAction.CHECK_IN }
            val last = events.maxByOrNull { it.timestampEpochMillis }
            if (!hasCheckIn || last?.action == AttendanceAction.CHECK_OUT) return@forEach
            val dayKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(window.start))
            val key = "checkout_${dayKey}_${employee.employeeId}"
            if (prefs.getBoolean(key, false)) return@forEach
            prefs.edit().putBoolean(key, true).apply()
            if (repo.smartAlertAudience != "EMPLOYEE_ONLY") notifyCheckoutReminder(employee)
            if (repo.smartAlertAudience != "OWNER_ONLY" && repo.lateNotifyEmployeeViaServer) notifyEmployeeViaServer(employee, AttendanceAction.CHECK_OUT, "تذكير انصراف")
            repo.addPresenceEvent(com.attendpro.core.PresenceEvent(
                employeeId = employee.employeeId, employeeName = employee.displayName, timestampEpochMillis = now,
                channel = "تنبيه ذكي", rssi = -127, details = "انتهى الدوام ولم يسجل الانصراف"
            ))
        }
    }

    fun notifyConnectedWithoutProof(employee: PairedEmployee) {
        ensureChannel()
        if (allowSpeech && repo.attendanceVoiceAnnouncementEnabled) announcer.announceMissingProof(employee.displayName)
        notifyPresenceReminder(employee)
    }

    private fun notifyPresenceReminder(employee: PairedEmployee) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, employee.employeeId.hashCode() xor 0x3311, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(context)
        @Suppress("DEPRECATION")
        val n = builder.setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("لم يثبت الحضور — ${employee.displayName}")
            .setContentText("الموظف متصل فعليًا بالمحل لكنه لم يثبت حضوره حتى الآن")
            .setPriority(Notification.PRIORITY_MAX).setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true).setContentIntent(open).build()
        context.getSystemService(NotificationManager::class.java)?.notify(43000 + (employee.employeeId.hashCode() and 0x7ff), n)
    }

    private fun notifyCheckoutReminder(employee: PairedEmployee) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, employee.employeeId.hashCode() xor 0x7722, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(context)
        @Suppress("DEPRECATION")
        val n = builder.setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("تذكير انصراف — ${employee.displayName}")
            .setContentText("انتهى وقت الدوام ولم يسجل الموظف الانصراف")
            .setPriority(Notification.PRIORITY_HIGH).setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true).setContentIntent(open).build()
        context.getSystemService(NotificationManager::class.java)?.notify(44000 + (employee.employeeId.hashCode() and 0x7ff), n)
    }

    private fun notifyLate(employee: PairedEmployee, connected: Boolean, sequence: Int) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, employee.employeeId.hashCode(), Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val phone = employee.phone.filter { it.isDigit() || it == '+' }
        val callIntent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))
        val call = PendingIntent.getActivity(context, employee.employeeId.hashCode() xor 0x55aa, callIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(context)
        val text = if (connected) "الموظف موجود ومتصل بالمحل لكنه لم يثبت حضوره" else "تجاوز وقت الدوام وفترة السماح ولم يُسجل حضورًا"
        @Suppress("DEPRECATION")
        val n = builder.setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("تنبيه حضور — ${employee.displayName}")
            .setContentText("$text • تنبيه $sequence/${employee.lateAlertCount.coerceIn(1, 10)}")
            .setPriority(Notification.PRIORITY_MAX).setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true).setContentIntent(open)
            .apply { if (phone.isNotBlank() && employee.lateCallMode != "NONE") addAction(android.R.drawable.sym_action_call, "فتح الاتصال", call) }
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(41000 + (employee.employeeId.hashCode() and 0x7ff), n)
    }


    private fun notifyEmployeeViaServer(employee: PairedEmployee, action: AttendanceAction, label: String) {
        if (!repo.hasCentralCredentials() || repo.serverUrl.isBlank() || !employee.companionEnabled) return
        val method = listOf(
            AttendanceMethod.PHONE_BLE_BIOMETRIC,
            AttendanceMethod.PHONE_FINGERPRINT,
            AttendanceMethod.PASSWORD
        ).firstOrNull { employee.allows(it) } ?: return
        Thread {
            val result = CentralServerClient.createPresenceChallenge(
                repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(context),
                employee.employeeId, method, action
            )
            result.onSuccess { onStatus("أُرسل $label إلى ${employee.displayName} عبر الخادم") }
                .onFailure { onStatus("تعذر إرسال تنبيه الخادم إلى ${employee.displayName}: ${it.message ?: "خطأ اتصال"}") }
        }.apply { isDaemon = true }.start()
    }

    private fun maybeAutoCall(employee: PairedEmployee, dayKey: String) {
        val calledKey = "called_${dayKey}_${employee.employeeId}"
        if (prefs.getBoolean(calledKey, false)) return
        val phone = employee.phone.filter { it.isDigit() || it == '+' }
        if (phone.length < 5) return
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            onStatus("تعذر الاتصال التلقائي بـ ${employee.displayName}: إذن المكالمات غير متاح؛ استخدم زر فتح الاتصال في التنبيه")
            return
        }
        val telecom = context.getSystemService(TelecomManager::class.java)
        val result = runCatching {
            requireNotNull(telecom)
            telecom.placeCall(Uri.fromParts("tel", phone, null), Bundle())
        }
        if (result.isSuccess) {
            prefs.edit().putBoolean(calledKey, true).apply()
            onStatus("تم طلب اتصال تنبيه تلقائي للموظف ${employee.displayName}")
        } else {
            onStatus("Android منع/تعذر الاتصال التلقائي بـ ${employee.displayName}; زر فتح الاتصال موجود في التنبيه")
        }
    }


}
