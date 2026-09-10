package com.attendpro.employee

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.attendpro.core.EmployeeIdentityStore

class PresenceBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val identity = EmployeeIdentityStore(context)
        if (!identity.isConfigured) return
        EmployeeLateAlertScheduler.sync(context, identity)
        if (identity.autoPresence && intent?.action == android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED &&
            intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1) != android.bluetooth.BluetoothAdapter.STATE_ON) return
        val service = Intent(context, PresenceService::class.java).setAction(PresenceService.ACTION_START)
            .putExtra(PresenceService.EXTRA_BACKGROUND_RESTART, true)
        // Android 12+ may reject a foreground-service start from some background states/OEM policies.
        // Never crash the receiver; BLE/LAN will resume on the next allowed app/service start.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service) else context.startService(service)
        }
    }
}
