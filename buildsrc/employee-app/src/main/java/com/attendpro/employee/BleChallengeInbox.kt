package com.attendpro.employee

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.attendpro.core.AttendanceMethod
import com.attendpro.core.AttendanceAction
import com.attendpro.core.BleChallengeProtocol

object BleChallengeInbox {
    private var callback: ScanCallback? = null
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var lastChallenge = ""
    private var lastSeenAt = 0L

    fun start(
        context: Context,
        employeeId: String,
        secret: ByteArray,
        onChallenge: (String, AttendanceMethod, Long, AttendanceAction) -> Unit
    ) {
        if (callback != null || employeeId.isBlank() || secret.isEmpty()) return
        val canScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        if (!canScan) return
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: BluetoothAdapter.getDefaultAdapter()
            ?: return
        if (!adapter.isEnabled) return
        val bleScanner = adapter.bluetoothLeScanner ?: return
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val data = result.scanRecord?.getManufacturerSpecificData(BleChallengeProtocol.MANUFACTURER_ID) ?: return
                val decoded = BleChallengeProtocol.decode(data, employeeId, secret) ?: return
                val now = System.currentTimeMillis()
                if (decoded.challengeId == lastChallenge && now - lastSeenAt < 5000L) return
                lastChallenge = decoded.challengeId
                lastSeenAt = now
                onChallenge("local:${decoded.challengeId}", decoded.method, decoded.expiresAt, decoded.action)
            }
        }
        runCatching {
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            bleScanner.startScan(null, settings, cb)
            scanner = bleScanner
            callback = cb
        }
    }

    fun stop() {
        val cb = callback ?: return
        runCatching { scanner?.stopScan(cb) }
        callback = null
        scanner = null
    }
}
