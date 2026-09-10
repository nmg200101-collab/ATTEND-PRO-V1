package com.attendpro.employee

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.attendpro.core.CentralServerClient
import com.attendpro.core.EmployeeIdentityStore

object EmployeeMessageNotifier1975 {
    private const val CHANNEL = "attend_employee_messages_1975"

    fun notify(context: Context, message: CentralServerClient.Message1975) {
        val identity = EmployeeIdentityStore(context)
        if (identity.wasMessageNotified1975(message.messageId)) return
        identity.markMessageNotified1975(message.messageId)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "رسائل ATTEND PRO", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "رسائل إدارة النظام وإدارة المحل"
            })
        }
        val open = PendingIntent.getActivity(context, message.messageId.hashCode(), Intent(context, EmployeeMessages1975Activity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val sender = if (message.senderType == "SYSTEM_OWNER") "إدارة النظام" else "إدارة المحل"
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(context)
        val n = b.setSmallIcon(com.attendpro.employee.R.drawable.ic_attend_pro)
            .setContentTitle("${message.title.ifBlank { "رسالة" }} • $sender")
            .setContentText(message.body)
            .setStyle(Notification.BigTextStyle().bigText(message.body))
            .setAutoCancel(true).setContentIntent(open).setPriority(Notification.PRIORITY_HIGH).build()
        nm.notify(59000 + (message.messageId.hashCode() and 0x7fff), n)
        if (message.voiceEnabled && identity.employeeVoicePromptsEnabled) {
            EmployeeVoicePrompter(context).apply { speak("${message.title}. ${message.body}"); android.os.Handler(context.mainLooper).postDelayed({ shutdown() }, 8000) }
        }
    }
}
