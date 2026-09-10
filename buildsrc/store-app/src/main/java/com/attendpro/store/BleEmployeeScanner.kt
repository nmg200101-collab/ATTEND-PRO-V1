package com.attendpro.store

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.attendpro.core.BleProtocol

class BleEmployeeScanner(
    private val activity: Activity,
    private val onPayload: (BleProtocol.Payload, Int, android.bluetooth.BluetoothDevice) -> Unit,
    private val onStatus: (String) -> Unit
) {
    companion object { const val REQUEST_SCAN = 8111 }

    private val handler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? get() = activity.getSystemService(BluetoothManager::class.java)?.adapter
    private var scanning = false
    private var wanted = false
    private var startedAt = 0L
    private var lastResultAt = 0L

    private val retry = Runnable { if (wanted) startInternal() }
    private val watchdog = object : Runnable {
        override fun run() {
            if (wanted && scanning) {
                val now = System.currentTimeMillis()
                val reference = if (lastResultAt > 0L) lastResultAt else startedAt
                if (reference > 0L && now - reference > 25_000L) {
                    restartScan("إعادة تنشيط مسح BLE تلقائيًا")
                }
            }
            if (wanted) handler.postDelayed(this, 10_000L)
        }
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            val bytes = record.getManufacturerSpecificData(BleProtocol.MANUFACTURER_ID)
                ?: record.getServiceData(BleProtocol.SERVICE_UUID)
                ?: return
            val payload = BleProtocol.parse(bytes) ?: return
            lastResultAt = System.currentTimeMillis()
            onPayload(payload, result.rssi, result.device)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            onStatus("تعذر مسح BLE (رمز $errorCode) — إعادة المحاولة تلقائيًا")
            if (wanted) {
                handler.removeCallbacks(retry)
                handler.postDelayed(retry, 1_500L)
            }
        }
    }

    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        activity.requestPermissions(permissions, REQUEST_SCAN)
    }

    fun start() {
        wanted = true
        if (!hasPermissions()) {
            requestPermissions()
            onStatus("اسمح بالأجهزة القريبة لتفعيل اكتشاف BLE")
            return
        }
        startInternal()
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, 10_000L)
    }

    @SuppressLint("MissingPermission")
    private fun startInternal() {
        if (!wanted || scanning) return
        val bt = adapter
        if (bt?.isEnabled != true) {
            onStatus("Bluetooth مغلق في جهاز المحل — Wi‑Fi/Hotspot المحلي يستمر")
            handler.removeCallbacks(retry)
            handler.postDelayed(retry, 2_000L)
            return
        }
        val scanner = runCatching { bt.bluetoothLeScanner }.getOrNull() ?: run {
            onStatus("BLE Scanner غير متاح — Wi‑Fi/Hotspot المحلي يستمر")
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(BleProtocol.SERVICE_UUID).build(),
            ScanFilter.Builder().setManufacturerData(
                BleProtocol.MANUFACTURER_ID, byteArrayOf(BleProtocol.VERSION), byteArrayOf(0xFF.toByte())
            ).build()
        )
        runCatching { scanner.startScan(filters, settings, callback) }
            .onSuccess {
                scanning = true
                startedAt = System.currentTimeMillis()
                if (lastResultAt <= 0L) lastResultAt = startedAt
                onStatus("اكتشاف BLE سريع يعمل • مراقبة الاستمرارية مفعلة")
            }
            .onFailure {
                scanning = false
                onStatus("تعذر بدء BLE — ستتم إعادة المحاولة")
                handler.postDelayed(retry, 1_500L)
            }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        wanted = false
        handler.removeCallbacks(retry)
        handler.removeCallbacks(watchdog)
        if (hasPermissions()) runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        scanning = false
    }



    @SuppressLint("MissingPermission")
    private fun restartScan(reason: String) {
        if (!wanted) return
        if (scanning && hasPermissions()) runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        scanning = false
        lastResultAt = System.currentTimeMillis()
        onStatus(reason)
        handler.removeCallbacks(retry)
        handler.postDelayed(retry, 600L)
    }

    @SuppressLint("MissingPermission")
    fun pauseForGatt(durationMillis: Long = 10_000L) {
        if (!wanted) return
        handler.removeCallbacks(retry)
        if (scanning && hasPermissions()) runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        scanning = false
        handler.postDelayed(retry, durationMillis.coerceIn(2_000L, 15_000L))
    }

    fun isScanning(): Boolean = scanning
    fun lastStartedAt(): Long = startedAt
    fun lastResultAt(): Long = lastResultAt
}
