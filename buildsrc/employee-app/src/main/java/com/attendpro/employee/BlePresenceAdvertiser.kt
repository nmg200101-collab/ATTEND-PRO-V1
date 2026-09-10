package com.attendpro.employee

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.attendpro.core.BleProtocol
import com.attendpro.core.BleDirectProtocol
import com.attendpro.core.SecretCodec

class BlePresenceAdvertiser(
    private val context: Context,
    private val onStatus: (String) -> Unit
) {
    companion object { const val REQUEST_BLUETOOTH = 4101 }

    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter get() = manager?.adapter
    private var running = false
    private var employeeId = ""
    private var secret = ByteArray(0)
    private var proofUntil = 0L
    private var proofFlags = 0
    private var advertising = false
    private var advertisedFlags = -1

    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            advertising = true
            advertisedFlags = currentFlags()
            onStatus(
                when {
                    advertisedFlags != 0 -> "تم بث إثبات التحقق للمحل عبر BLE"
                    secret.isEmpty() -> "BLE يعمل: الهاتف ظاهر للمحل، ويحتاج إعادة اقتران لإثبات الحضور"
                    else -> "BLE يعمل: الهاتف ظاهر للمحل وقناة التحقق جاهزة"
                }
            )
        }

        override fun onStartFailure(errorCode: Int) {
            advertising = false
            advertisedFlags = -1
            if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED) {
                advertising = true
                onStatus("BLE يعمل ويُبث حضور الهاتف للمحل")
            } else {
                onStatus("تعذر بث BLE (رمز $errorCode) — تستمر Hotspot/Wi‑Fi والربط المحلي")
            }
        }
    }

    private val proofWatcher = object : Runnable {
        override fun run() {
            if (!running) return
            val flags = currentFlags()
            if (!advertising || flags != advertisedFlags) restartInternal()
            handler.postDelayed(this, 1_000L)
        }
    }

    fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }


    fun start(employeeId: String, secretText: String) {
        if (employeeId.isBlank()) {
            onStatus("أكمل بيانات الموظف أولاً")
            return
        }
        this.employeeId = employeeId
        this.secret = SecretCodec.decode(secretText) ?: ByteArray(0)
        if (!hasPermissions()) {
            // Runtime permissions must be requested by MainActivity, never from a Service context.
            running = false
            onStatus("صلاحيات Bluetooth غير ممنوحة — Wi‑Fi/Hotspot والخادم يواصلان العمل")
            return
        }
        running = true
        handler.removeCallbacks(proofWatcher)
        restartInternal()
        handler.postDelayed(proofWatcher, 1_000L)
    }

    fun stop() {
        running = false
        advertising = false
        advertisedFlags = -1
        handler.removeCallbacks(proofWatcher)
        stopInternal()
        onStatus("تم إيقاف ظهور الهاتف للمحل")
    }

    fun markVerified(durationMillis: Long = 30_000L, proofType: Int = BleProtocol.FLAG_BIOMETRIC) {
        if (secret.isEmpty()) {
            proofUntil = 0L
            proofFlags = 0
            onStatus("الهاتف ظاهر عبر BLE، لكن يلزم إعادة الاقتران لإرسال إثبات مشفر")
            return
        }
        proofUntil = System.currentTimeMillis() + durationMillis
        proofFlags = BleProtocol.FLAG_VERIFIED or proofType
        if (running) restartInternal()
    }

    fun isRunning(): Boolean = running
    fun isAdvertising(): Boolean = advertising
    private fun currentFlags(): Int = if (secret.isNotEmpty() && System.currentTimeMillis() < proofUntil) proofFlags else 0

    @SuppressLint("MissingPermission")
    private fun restartInternal() {
        if (!running || !hasPermissions()) return
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            advertising = false
            onStatus("Bluetooth مغلق — تستمر Wi‑Fi/Hotspot بدون إنترنت")
            return
        }
        val advertiser = runCatching { bt.bluetoothLeAdvertiser }.getOrNull() ?: run {
            advertising = false
            onStatus("BLE Advertising غير متاح على هذا الهاتف — استخدم Hotspot/Wi‑Fi المحلي")
            return
        }
        runCatching { advertiser.stopAdvertising(callback) }
        advertising = false
        advertisedFlags = -1
        val flags = currentFlags()
        val payload = if (secret.isNotEmpty()) {
            BleProtocol.buildPayload(employeeId, secret, flags)
        } else {
            BleProtocol.buildDiscoveryPayload(employeeId)
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(BleProtocol.SERVICE_UUID)
            .addManufacturerData(BleProtocol.MANUFACTURER_ID, payload)
            .build()
        // Put the actual direct-GATT service UUID in scan response instead of the primary
        // 31-byte legacy advertisement. This keeps the authenticated 10-byte manufacturer
        // frame compact while making the connectable GATT endpoint explicit to OEM scanners.
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(BleDirectProtocol.SERVICE_UUID))
            .build()
        runCatching { advertiser.startAdvertising(settings, data, scanResponse, callback) }
            .onFailure {
                advertising = false
                onStatus("تعذر بدء BLE — ستتم إعادة المحاولة تلقائيًا؛ Hotspot/Wi‑Fi والخادم مستمران")
            }
    }

    @SuppressLint("MissingPermission")
    private fun stopInternal() {
        if (!hasPermissions()) return
        runCatching { adapter?.bluetoothLeAdvertiser?.stopAdvertising(callback) }
    }
}
