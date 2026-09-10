package com.attendpro.employee

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.attendpro.core.AttendanceMethod
import com.attendpro.core.AttendanceAction
import com.attendpro.core.EmployeeIdentityStore
import com.attendpro.core.PhoneAttendancePolicy

object AttendanceRequestNotifier {
    const val CHANNEL_ID = "attend_presence_requests_v2"
    const val EXTRA_OPEN_CHALLENGE = "open_presence_challenge"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build()
        val channel = NotificationChannel(CHANNEL_ID, "طلبات إثبات الحضور", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "تنبيه فوري عند طلب المحل إثبات حضور أو انصراف"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 120, 250, 120, 400)
            setSound(sound, attrs)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyChallenge(context: Context, challengeId: String, method: AttendanceMethod, expiresAt: Long, action: AttendanceAction = AttendanceAction.CHECK_IN) {
        ensureChannel(context)
        val prefs = context.getSharedPreferences("attend_notify_dedupe", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastId = prefs.getString("challenge_id", "").orEmpty()
        val lastMethod = prefs.getString("method", "").orEmpty()
        val lastAction = prefs.getString("action", "").orEmpty()
        val lastAt = prefs.getLong("at", 0L)
        if ((lastId == challengeId || (lastMethod == method.name && lastAction == action.name)) && now - lastAt < 2500L) return
        prefs.edit().putString("challenge_id", challengeId).putString("method", method.name).putString("action", action.name).putLong("at", now).apply()
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_CHALLENGE, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            challengeId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ttl = ((expiresAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(1L)
        val storeName = EmployeeIdentityStore(context).trustedStoreName.ifBlank { "المحل" }
        val actionText = if (action == AttendanceAction.CHECK_IN) "حضور" else "انصراف"
        val title = "طلب إثبات $actionText — $storeName"
        val shortText = "$actionText • ${PhoneAttendancePolicy.label(method)} — وافق خلال ${ttl}ث"
        val longText = "$storeName يطلب إثبات $actionText الآن. الطريقة المطلوبة: ${PhoneAttendancePolicy.label(method)}. إذا وصل الطلب عبر BLE أو Wi‑Fi المحلي فإنه يعمل دون إنترنت."

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        @Suppress("DEPRECATION")
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(shortText)
            .setStyle(Notification.BigTextStyle().bigText(longText))
            .setPriority(Notification.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(pending)
            .addAction(android.R.drawable.ic_menu_view, "إثبات $actionText", pending)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(31000 + (challengeId.hashCode() and 0x3ff), notification)
    }
    fun notifyGeoArrival(context: Context, employeeName: String, storeName: String, distanceMeters: Int) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, 32001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = "تم التعرف على وصولك للمحل"
        val text = "${employeeName.ifBlank { "الموظف" }} داخل نطاق ${storeName.ifBlank { "المحل" }} • تقريبًا ${distanceMeters.coerceAtLeast(0)}م"
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL_ID) else @Suppress("DEPRECATION") Notification.Builder(context)
        @Suppress("DEPRECATION")
        val n = builder.setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title).setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText("$text. افتح ATTEND PRO وأكمل إثبات الحضور بالطريقة التي حددها صاحب المحل. لا يحتاج هذا التنبيه إلى إنترنت."))
            .setPriority(Notification.PRIORITY_HIGH).setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC).setAutoCancel(true).setContentIntent(pending).build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(32001, n)
    }

}
