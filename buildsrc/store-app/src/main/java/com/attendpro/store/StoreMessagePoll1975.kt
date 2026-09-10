package com.attendpro.store

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.attendpro.core.CentralServerClient
import com.attendpro.core.DeviceIdentity
import com.attendpro.core.StoreRepository

class StoreMessagePoll1975 : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                val repo = StoreRepository(context)
                if (!repo.isCentralActivationActive()) return@Thread
                val result = CentralServerClient.storeMessagesInbox(repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(context), 50)
                result.getOrNull().orEmpty().filter { it.readAt <= 0L }.forEach { message -> notify(context, repo, message) }
            } finally {
                schedule(context); pending.finish()
            }
        }.start()
    }


    private fun notify(context: Context, repo: StoreRepository, message: CentralServerClient.Message1975) {
        if (repo.wasStoreMessageNotified1975(message.messageId)) return
        repo.markStoreMessageNotified1975(message.messageId)
        val nm=context.getSystemService(NotificationManager::class.java)?:return
        val channel="attend_store_messages_1975"
        if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(NotificationChannel(channel,"رسائل إدارة المحل",NotificationManager.IMPORTANCE_HIGH))
        val open=PendingIntent.getActivity(context,message.messageId.hashCode(),Intent(context,StoreMessages1975Activity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val sender=if(message.senderType=="SYSTEM_OWNER")"إدارة النظام" else "الموظف ${message.employeeId}"
        val b=if(Build.VERSION.SDK_INT>=26) Notification.Builder(context,channel) else @Suppress("DEPRECATION") Notification.Builder(context)
        nm.notify(58000+(message.messageId.hashCode() and 0x7fff),b.setSmallIcon(R.drawable.ic_attend_pro).setContentTitle("${message.title.ifBlank{"رسالة"}} • $sender").setContentText(message.body).setStyle(Notification.BigTextStyle().bigText(message.body)).setAutoCancel(true).setContentIntent(open).setPriority(Notification.PRIORITY_HIGH).build())
    }

    companion object {
        private const val ACTION="com.attendpro.store.ACTION_MESSAGE_POLL_1975"
        fun schedule(context: Context) {
            val am=context.getSystemService(AlarmManager::class.java)?:return
            val intent=Intent(context,StoreMessagePoll1975::class.java).setAction(ACTION)
            val pi=PendingIntent.getBroadcast(context,1975,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val at=System.currentTimeMillis()+5*60_000L
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi) }.onFailure { am.set(AlarmManager.RTC_WAKEUP,at,pi) }
        }
    }
}
