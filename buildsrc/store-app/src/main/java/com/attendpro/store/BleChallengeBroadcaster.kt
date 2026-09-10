package com.attendpro.store

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.attendpro.core.AttendanceMethod
import com.attendpro.core.AttendanceAction
import com.attendpro.core.BleChallengeProtocol

object BleChallengeBroadcaster {
    private val handler = Handler(Looper.getMainLooper())
    private var callback: AdvertiseCallback? = null

    fun broadcast(context: Context, employeeId: String, secret: ByteArray, method: AttendanceMethod, expiresAt: Long = System.currentTimeMillis() + 60_000L, requestToken: Int? = null, action: AttendanceAction = AttendanceAction.CHECK_IN): Boolean {
        if (employeeId.isBlank() || secret.isEmpty()) return false
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) return false
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: BluetoothAdapter.getDefaultAdapter() ?: return false
        if (!adapter.isEnabled || !adapter.isMultipleAdvertisementSupported) return false
        val advertiser = adapter.bluetoothLeAdvertiser ?: return false
        callback?.let { runCatching { advertiser.stopAdvertising(it) } }
        val payload = BleChallengeProtocol.encode(employeeId, secret, method, expiresAt, requestToken ?: java.security.SecureRandom().nextInt(), action)
        val data = AdvertiseData.Builder()
            .addManufacturerData(BleChallengeProtocol.MANUFACTURER_ID, payload)
            .setIncludeDeviceName(false)
            .build()
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val cb = object : AdvertiseCallback() {}
        return runCatching {
            advertiser.startAdvertising(settings, data, cb)
            callback = cb
            handler.postDelayed({
                if (callback === cb) {
                    runCatching { advertiser.stopAdvertising(cb) }
                    callback = null
                }
            }, 15_000L)
            true
        }.getOrDefault(false)
    }
}
