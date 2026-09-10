package com.attendpro.store

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.attendpro.core.BleProtocol
import com.attendpro.core.PairingAckProtocol
import com.attendpro.core.PairingProtocol
import com.attendpro.core.SecretCodec
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PairingBeacon(
    private val activity: Activity,
    private val status: (String) -> Unit,
    private val paired: (String, String) -> Unit = { _, _ -> }
) {
    enum class Mode { ALL, BLUETOOTH_ONLY, WIFI_HOTSPOT_ONLY }

    companion object {
        const val REQUEST_ADVERTISE = 5191
        const val UDP_PORT = 45993
        private const val MAX_GATT_CHUNK_SIZE = 360
        val PROVISION_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000A772-0000-1000-8000-00805F9B34FB")
    }

    private val manager = activity.getSystemService(BluetoothManager::class.java)
    private val adapter = manager?.adapter
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var mode = Mode.ALL
    private var socket: DatagramSocket? = null
    private var currentBleMessage = ByteArray(0)
    private var currentCode = ""
    private var provisionText = ""
    private var compactProvisionText = ""
    private var currentEmployeeId = ""
    private var currentSecret = ByteArray(0)
    private var fallbackStarted = false
    private var bleAdvertisingStarted = false
    private var serviceReady = false
    private var gattServer: BluetoothGattServer? = null
    private val requestedOffsets = ConcurrentHashMap<String, Int>()
    private val negotiatedMtu = ConcurrentHashMap<String, Int>()

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            bleAdvertisingStarted = true
            status("Bluetooth جاهز للربط الكامل بدون إنترنت — بانتظار هاتف الموظف وACK المشفّر")
        }
        override fun onStartFailure(errorCode: Int) {
            bleAdvertisingStarted = false
            if (!fallbackStarted && running && currentBleMessage.isNotEmpty()) startCompatibleBleFallback()
            else status("تعذر بث Bluetooth ($errorCode) — QR وWi‑Fi/Hotspot مستقلان ويظلان متاحين")
        }
    }

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(statusCode: Int, service: BluetoothGattService?) {
            if (!running || service?.uuid != BleProtocol.SERVICE_UUID.uuid || mode == Mode.WIFI_HOTSPOT_ONLY) return
            serviceReady = statusCode == BluetoothGatt.GATT_SUCCESS
            activity.runOnUiThread {
                if (serviceReady) {
                    status("قناة Bluetooth جاهزة — بدء الإعلان المحلي")
                    startPrimaryBleAdvertising()
                } else status("تعذر تجهيز قناة Bluetooth؛ QR وWi‑Fi/Hotspot لا يتأثران")
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice?, statusCode: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED || statusCode != BluetoothGatt.GATT_SUCCESS) device?.address?.let {
                requestedOffsets.remove(it); negotiatedMtu.remove(it)
            }
        }

        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            device?.address?.let { negotiatedMtu[it] = mtu.coerceIn(23, 517) }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean,
            responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            val server = gattServer ?: return
            var ok = false
            var completed = false
            if (characteristic?.uuid == PROVISION_CHARACTERISTIC_UUID && device != null && value != null) {
                val text = runCatching { value.toString(Charsets.UTF_8).trim() }.getOrDefault("")
                if (text.startsWith("APGET:")) {
                    val parts = text.split(':')
                    if (parts.size == 3 && parts[1].uppercase() == currentCode) {
                        val requested = parts[2].toIntOrNull() ?: -1
                        if (requested in 0..provisionText.toByteArray(Charsets.UTF_8).size) {
                            requestedOffsets[device.address] = requested
                            ok = true
                        }
                    }
                } else if (currentSecret.isNotEmpty() && PairingAckProtocol.verify(value, currentCode, currentSecret)) {
                    ok = true
                    completed = true
                    requestedOffsets.remove(device.address)
                }
            }
            if (responseNeeded) runCatching { server.sendResponse(device, requestId, if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, 0, null) }
            if (completed) activity.runOnUiThread {
                status("✓ اكتمل الربط عبر Bluetooth مع ACK مشفّر — لا يحتاج إنترنت")
                paired(currentEmployeeId, "Bluetooth")
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) {
            val server = gattServer ?: return
            if (device == null || characteristic?.uuid != PROVISION_CHARACTERISTIC_UUID || provisionText.isBlank()) {
                runCatching { server.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null) }; return
            }
            val all = provisionText.toByteArray(Charsets.UTF_8)
            val start = requestedOffsets[device.address] ?: 0
            if (start !in 0..all.size) { runCatching { server.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, 0, null) }; return }
            // ATT payload is MTU-3. Keep extra margin for OEM stacks and default to 18 bytes
            // when MTU negotiation never arrived; this prevents silent provision truncation.
            val chunkSize = ((negotiatedMtu[device.address] ?: 23) - 5).coerceIn(18, MAX_GATT_CHUNK_SIZE)
            val end = (start + chunkSize).coerceAtMost(all.size)
            runCatching { server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, all.copyOfRange(start, end)) }
        }
    }

    private fun hasBlePermissions(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || (
        activity.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        )

    @SuppressLint("MissingPermission")
    fun start(code: String, provision: String = "", requestedMode: Mode = Mode.ALL) {
        stop()
        running = true
        mode = requestedMode
        fallbackStarted = false; bleAdvertisingStarted = false; serviceReady = false
        currentCode = code.trim().uppercase().take(8)
        provisionText = provision.takeIf { PairingProtocol.decodeEmployeeProvision(it) != null } ?: OfflinePairingCache.activate(currentCode)
        val decoded = PairingProtocol.decodeEmployeeProvision(provisionText)
        currentEmployeeId = decoded?.employeeId.orEmpty()
        currentSecret = decoded?.pairingSecret?.let { SecretCodec.decode(it) } ?: ByteArray(0)
        compactProvisionText = if (provisionText.isNotBlank()) PairingProtocol.compactProvision(provisionText) else ""
        if (provisionText.startsWith("AP3P:")) OfflinePairingCache.captureEncoded(provisionText)
        currentBleMessage = "P$currentCode".toByteArray(Charsets.UTF_8)

        if (decoded == null || currentSecret.isEmpty() || currentCode.length != 8) {
            running = false
            status("ملف الربط المحلي غير صالح — أنشئ QR جديدًا لهذا الموظف")
            return
        }

        if (mode != Mode.BLUETOOTH_ONLY) startLanServer()
        if (mode != Mode.WIFI_HOTSPOT_ONLY) startBleServer()
    }

    private fun startLanServer() {
        Thread({
            runCatching {
                DatagramSocket(null).also {
                    socket = it; it.reuseAddress = true; it.broadcast = true; it.soTimeout = 220; it.bind(InetSocketAddress(UDP_PORT))
                }.use { s ->
                    val shortMessage = "APPAIR:$currentCode".toByteArray(Charsets.UTF_8)
                    val fullMessage = "APFULL:$currentCode:$compactProvisionText".toByteArray(Charsets.UTF_8)
                    val incoming = ByteArray(2048)
                    var lastBroadcastAt = 0L
                    while (running && mode != Mode.BLUETOOTH_ONLY) {
                        val now = System.currentTimeMillis()
                        if (now - lastBroadcastAt >= 650L) {
                            broadcastTargets().forEach { target -> runCatching { s.send(DatagramPacket(shortMessage, shortMessage.size, target, UDP_PORT)) } }
                            lastBroadcastAt = now
                        }
                        try {
                            val p = DatagramPacket(incoming, incoming.size)
                            s.receive(p)
                            val text = String(p.data, 0, p.length, Charsets.UTF_8).trim()
                            when {
                                text == "APGET:$currentCode" -> runCatching { s.send(DatagramPacket(fullMessage, fullMessage.size, p.address, p.port)) }
                                text.startsWith("APDISCOVER:") -> {
                                    val requested = text.removePrefix("APDISCOVER:").uppercase()
                                    if (requested == "*" || requested == currentCode) {
                                        runCatching { s.send(DatagramPacket(shortMessage, shortMessage.size, p.address, p.port)) }
                                        runCatching { s.send(DatagramPacket(fullMessage, fullMessage.size, p.address, p.port)) }
                                    }
                                }
                                text.startsWith(PairingAckProtocol.TEXT_PREFIX) -> {
                                    val remainder = text.removePrefix(PairingAckProtocol.TEXT_PREFIX)
                                    val split = remainder.indexOf(':')
                                    if (split > 0) {
                                        val code = remainder.substring(0, split).uppercase()
                                        val encoded = remainder.substring(split + 1)
                                        val frame = PairingAckProtocol.fromText(PairingAckProtocol.TEXT_PREFIX + encoded)
                                        if (code == currentCode && frame != null && PairingAckProtocol.verify(frame, currentCode, currentSecret)) {
                                            val ok = "APOK:$currentCode".toByteArray(Charsets.UTF_8)
                                            runCatching { s.send(DatagramPacket(ok, ok.size, p.address, p.port)) }
                                            activity.runOnUiThread {
                                                status("✓ اكتمل الربط عبر Wi‑Fi/Hotspot مع ACK مشفّر — لا يحتاج إنترنت")
                                                paired(currentEmployeeId, "Wi‑Fi/Hotspot")
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: SocketTimeoutException) {
                        } catch (_: Exception) {
                        }
                    }
                }
            }.onFailure { if (running) status("تعذر فتح قناة Wi‑Fi/Hotspot؛ Bluetooth وQR يظلان مستقلين") }
        }, "attend-pairing-hotspot").apply { isDaemon = true }.start()
        status("Wi‑Fi/Hotspot جاهز للربط الكامل بدون إنترنت — بانتظار هاتف الموظف")
    }

    @SuppressLint("MissingPermission")
    private fun startBleServer() {
        if (!hasBlePermissions()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) activity.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT), REQUEST_ADVERTISE)
            status("اسمح بالأجهزة القريبة لتفعيل Bluetooth؛ QR وWi‑Fi/Hotspot يظلان متاحين")
            return
        }
        if (adapter?.isEnabled != true) { status("Bluetooth مغلق — QR وWi‑Fi/Hotspot يعملان بدون إنترنت"); return }
        startGattServer()
    }

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        if (!hasBlePermissions() || provisionText.isBlank() || mode == Mode.WIFI_HOTSPOT_ONLY) return
        runCatching {
            val server = manager?.openGattServer(activity, gattCallback) ?: return
            gattServer = server
            val service = BluetoothGattService(BleProtocol.SERVICE_UUID.uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val characteristic = BluetoothGattCharacteristic(
                PROVISION_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            service.addCharacteristic(characteristic)
            if (!server.addService(service)) { status("تعذر تسجيل خدمة Bluetooth؛ QR وWi‑Fi/Hotspot لا يتأثران"); return }
            handler.postDelayed({
                if (running && mode != Mode.WIFI_HOTSPOT_ONLY && !bleAdvertisingStarted) {
                    if (!serviceReady) status("مهلة توافق Bluetooth انتهت — تشغيل الإعلان مع إبقاء GATT مفتوحًا")
                    startPrimaryBleAdvertising()
                }
            }, 900L)
        }.onFailure { status("تعذر بدء قناة GATT؛ QR وWi‑Fi/Hotspot يظلان متاحين") }
    }

    @SuppressLint("MissingPermission")
    private fun startPrimaryBleAdvertising() {
        if (!running || bleAdvertisingStarted || mode == Mode.WIFI_HOTSPOT_ONLY || !hasBlePermissions() || adapter?.isEnabled != true) return
        val advertiser = runCatching { adapter?.bluetoothLeAdvertiser }.getOrNull() ?: run { status("BLE Advertising غير مدعوم — استخدم Wi‑Fi/Hotspot أو QR"); return }
        // Advertise both the service UUID and the short-code service data. Some Android
        // scanners filter on the UUID list while others are more reliable with service-data
        // matching; carrying both makes Store discovery deterministic across OEM stacks.
        val data = AdvertiseData.Builder()
            .addServiceUuid(BleProtocol.SERVICE_UUID)
            .addServiceData(BleProtocol.SERVICE_UUID, currentBleMessage)
            .setIncludeDeviceName(false)
            .build()
        val settings = AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).setConnectable(true).setTimeout(0).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH).build()
        bleAdvertisingStarted = true
        runCatching { advertiser.startAdvertising(settings, data, advertiseCallback) }.onFailure { bleAdvertisingStarted = false; startCompatibleBleFallback() }
    }

    @SuppressLint("MissingPermission")
    private fun startCompatibleBleFallback() {
        if (!running || mode == Mode.WIFI_HOTSPOT_ONLY || !hasBlePermissions()) return
        fallbackStarted = true
        val advertiser = runCatching { adapter?.bluetoothLeAdvertiser }.getOrNull() ?: return
        val data = AdvertiseData.Builder().addManufacturerData(BleProtocol.MANUFACTURER_ID, currentBleMessage).setIncludeDeviceName(false).build()
        val settings = AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED).setConnectable(true).setTimeout(0).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH).build()
        runCatching { advertiser.startAdvertising(settings, data, advertiseCallback) }.onFailure { status("Bluetooth غير متاح على هذا الجهاز — استخدم Wi‑Fi/Hotspot أو QR") }
    }

    private fun broadcastTargets(): Set<InetAddress> {
        val result = linkedSetOf<InetAddress>()
        fun add(text: String) { runCatching { result.add(InetAddress.getByName(text)) } }
        add("255.255.255.255")
        listOf("192.168.43.255", "192.168.232.255", "192.168.137.255", "192.168.49.255", "192.168.4.255").forEach(::add)
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { network ->
                if (network.isUp && !network.isLoopback) network.interfaceAddresses.forEach { item ->
                    (item.broadcast as? Inet4Address)?.let { result.add(it) }
                    val local = item.address as? Inet4Address
                    if (local != null && !local.isLoopbackAddress) {
                        val b = local.address.clone(); b[3] = 0xff.toByte(); runCatching { result.add(InetAddress.getByAddress(b)) }
                    }
                }
            }
        }
        return result
    }

    fun isRunning(): Boolean = running
    fun isAdvertising(): Boolean = running && bleAdvertisingStarted
    fun isGattServiceReady(): Boolean = running && serviceReady

    @SuppressLint("MissingPermission")
    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        currentBleMessage = ByteArray(0); requestedOffsets.clear(); negotiatedMtu.clear(); bleAdvertisingStarted = false; serviceReady = false; fallbackStarted = false
        runCatching { socket?.close() }; socket = null
        if (hasBlePermissions()) runCatching { adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) }
        runCatching { gattServer?.clearServices() }; runCatching { gattServer?.close() }; gattServer = null
        currentSecret.fill(0); currentSecret = ByteArray(0); currentEmployeeId = ""; provisionText = ""; compactProvisionText = ""
        OfflinePairingCache.clearActive()
    }
}
