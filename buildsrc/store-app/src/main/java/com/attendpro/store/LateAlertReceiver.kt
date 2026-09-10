package com.attendpro.store

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.attendpro.core.StoreRepository

/** Receives local late-attendance alarms and boot/update rescheduling broadcasts. */
class LateAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val repo = StoreRepository(context)
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> LateAlertScheduler.sync(context, repo)
            LateAlertScheduler.ACTION_LATE_TICK -> {
                val announcer = StoreVoiceAnnouncer(context, repo)
                // Background alarms use notification sound / optional telephone call. TTS is
                // reserved for the foreground store UI where Android can keep the speech engine alive.
                SmartLateAlertManager(context, repo, announcer, {}, allowSpeech = false).tick()
                announcer.shutdown()
                LateAlertScheduler.sync(context, repo)
            }
        }
    }
}
