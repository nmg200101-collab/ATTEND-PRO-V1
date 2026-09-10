package com.attendpro.employee

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.attendpro.core.BleProtocol
import com.attendpro.core.PairingAckProtocol
import com.attendpro.core.PairingProtocol
import com.attendpro.core.SecretCodec
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.UUID

class PairingDiscovery(
    private val activity: Activity,
    private val found: (String, String) -> Unit,
    private val status: (String) -> Unit
) {
    enum class Mode { ALL, BLUETOOTH_ONLY, WIFI_HOTSPOT_ONLY }

    companion object {
        const val REQUEST_SCAN = 8110
        const val UDP_PORT = 45993
        private const val MAX_GATT_BUFFER = 16_384
        private val PROVISION_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000A772-0000-1000-8000-00805F9B34FB")
    }

    private val adapter = activity.getSystemService(BluetoothManager::class.java)?.adapter
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var socket: DatagramSocket? = null
    private var gatt: BluetoothGatt? = null
    private var pendingCode = ""
    private var targetCode = ""
    private var mode = Mode.ALL
    private var connectingGatt = false
    private var gattOffset = 0
    private val gattBuffer = ByteArrayOutputStream()
    private var lastGattDevice: BluetoothDevice? = null
    private var lastGattCode = ""
    private var gattAttempt = 0
    private var gattServicesRequested = false
    private var gattAwaitingAck = false
    private var gattProvisionReady = ""
    private var pendingLanProvision = ""
    private var pendingLanCode = ""
    private var bleScanner: android.bluetooth.le.BluetoothLeScanner? = null
    @Volatile private var bleScanActive = false
    private var bleRetryCount = 0
    private val bleScanRetry = Runnable {
        if (running && !connectingGatt && mode != Mode.WIFI_HOTSPOT_ONLY) startBleDiscovery()
    }

    private val gattTimeout = Runnable {
        if (!running || !connectingGatt) return@Runnable
        failGatt(gatt, "لم يكتمل تأكيد الربط عبر Bluetooth")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) {
            if (!running || mode == Mode.WIFI_HOTSPOT_ONLY) return
            val record = result.scanRecord ?: return
            val bytes = record.getServiceData(BleProtocol.SERVICE_UUID)
                ?: record.getManufacturerSpecificData(BleProtocol.MANUFACTURER_ID)
                ?: return
            val text = bytes.toString(Charsets.UTF_8)
            if (text.startsWith("P") && text.length >= 9) {
                val code = text.substring(1, 9).uppercase()
                if (targetCode.isNotBlank() && code != targetCode) return
                pendingCode = code
                // Several Android/OEM BLE stacks become unstable if low-latency scanning remains
                // active while opening GATT (common symptom: GATT 133 / found-but-never-links).
                stopBleScanForGatt()
                status("تم العثور على جهاز المحل عبر Bluetooth — تم تثبيت الجهاز وجارٍ فتح GATT…")
                requestProvisionOverGatt(result.device, code)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { onScanResult(0, it) }
        override fun onScanFailed(code: Int) {
            bleScanActive = false
            bleScanner = null
            bleRetryCount = (bleRetryCount + 1).coerceAtMost(8)
            val delay = (1_000L * bleRetryCount).coerceAtMost(6_000L)
            status("تعذر بحث Bluetooth ($code) — إعادة تشغيل Scanner تلقائيًا؛ Wi‑Fi/Hotspot وQR مستمران")
            handler.removeCallbacks(bleScanRetry)
            if (running && !connectingGatt && mode != Mode.WIFI_HOTSPOT_ONLY) handler.postDelayed(bleScanRetry, delay)
        }
    }

    private fun hasBlePermissions(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun start(expectedCode: String = "", requestedMode: Mode = Mode.ALL) {
        stop()
        mode = requestedMode
        targetCode = expectedCode.replace(Regex("[^A-Za-z0-9]"), "").uppercase().take(8)
        running = true
        status(
            when (mode) {
                Mode.BLUETOOTH_ONLY -> "جاري الربط عبر Bluetooth فقط — لا يحتاج إنترنت…"
                Mode.WIFI_HOTSPOT_ONLY -> "جاري الربط عبر Wi‑Fi/نقطة الاتصال فقط — لا يحتاج إنترنت…"
                Mode.ALL -> "جاري الربط المحلي عبر Bluetooth وWi‑Fi/Hotspot — لا يحتاج إنترنت…"
            }
        )

        if (mode != Mode.BLUETOOTH_ONLY) startLanDiscovery()
        if (mode != Mode.WIFI_HOTSPOT_ONLY) startBleDiscovery()
    }

    private fun startLanDiscovery() {
        Thread({
            runCatching {
                DatagramSocket(null).also {
                    socket = it
                    it.reuseAddress = true
                    it.broadcast = true
                    it.soTimeout = 280
                    it.bind(InetSocketAddress(UDP_PORT))
                }.use { s ->
                    val buffer = ByteArray(8192)
                    var lastProbeAt = 0L
                    while (running && mode != Mode.BLUETOOTH_ONLY) {
                        val now = System.currentTimeMillis()
                        if (now - lastProbeAt >= 550L) {
                            val requested = targetCode.ifBlank { "*" }
                            val discover = "APDISCOVER:$requested".toByteArray(Charsets.UTF_8)
                            discoveryTargets().forEach { address ->
                                runCatching { s.send(DatagramPacket(discover, discover.size, address, UDP_PORT)) }
                            }
                            lastProbeAt = now
                        }
                        try {
                            val p = DatagramPacket(buffer, buffer.size)
                            s.receive(p)
                            val text = String(p.data, 0, p.length, Charsets.UTF_8).trim()
                            when {
                                text.startsWith("APFULL:") -> handleLanProvision(s, p, text)
                                text.startsWith("APPAIR:") -> {
                                    val code = text.removePrefix("APPAIR:").take(8).uppercase()
                                    if (code.length == 8 && (targetCode.isBlank() || code == targetCode)) {
                                        pendingCode = code
                                        val request = "APGET:$code".toByteArray(Charsets.UTF_8)
                                        runCatching { s.send(DatagramPacket(request, request.size, p.address, p.port)) }
                                    }
                                }
                                text.startsWith("APOK:") -> {
                                    val code = text.removePrefix("APOK:").take(8).uppercase()
                                    if (pendingLanProvision.isNotBlank() && code == pendingLanCode) {
                                        finishFound(pendingLanProvision, "Wi‑Fi/Hotspot مباشر • ACK")
                                    }
                                }
                                text.startsWith("AP3P:") || text.startsWith("AP4P:") -> {
                                    // A complete Store-issued provision containing a valid pairing secret is the
                                    // trust-establishment artifact. Reverse ACK is useful for Store UI, but must
                                    // never make a valid local pairing fail on OEM UDP/BLE quirks.
                                    val provision = PairingProtocol.decodeEmployeeProvision(text)
                                    if (provision != null && SecretCodec.decode(provision.pairingSecret) != null) {
                                        val code = pendingCode.ifBlank { targetCode }
                                        if (code.isNotBlank()) sendLanAckBestEffort(s, p.address, p.port, code, provision.pairingSecret)
                                        finishFound(text, "Wi‑Fi/Hotspot مباشر • ملف موثق")
                                    }
                                }
                            }
                        } catch (_: SocketTimeoutException) {
                        } catch (_: Exception) {
                        }
                    }
                }
            }.onFailure { if (running) status("تعذر فتح قناة Wi‑Fi/Hotspot المحلية — Bluetooth وQR يظلان متاحين") }
        }, "attend-pairing-discovery").apply { isDaemon = true }.start()
    }

    private fun handleLanProvision(s: DatagramSocket, packet: DatagramPacket, text: String) {
        val first = text.indexOf(':', 7)
        if (first <= 7) return
        val code = text.substring(7, first).uppercase()
        val provisionText = text.substring(first + 1)
        if (targetCode.isNotBlank() && code != targetCode) return
        val provision = PairingProtocol.decodeEmployeeProvision(provisionText) ?: return
        if (SecretCodec.decode(provision.pairingSecret) == null) return
        pendingCode = code
        sendLanAckBestEffort(s, packet.address, packet.port, code, provision.pairingSecret)
        finishFound(provisionText, "Wi‑Fi/Hotspot مباشر • ملف موثق")
    }

    private fun sendLanAckBestEffort(s: DatagramSocket, address: InetAddress, port: Int, code: String, secretText: String) {
        val secret = SecretCodec.decode(secretText) ?: return
        val frame = PairingAckProtocol.encode(code, secret)
        val encoded = PairingAckProtocol.toText(frame).removePrefix(PairingAckProtocol.TEXT_PREFIX)
        val ack = "${PairingAckProtocol.TEXT_PREFIX}$code:$encoded".toByteArray(Charsets.UTF_8)
        runCatching { s.send(DatagramPacket(ack, ack.size, address, port)) }
    }

    @SuppressLint("MissingPermission")
    private fun startBleDiscovery() {
        if (!running || connectingGatt || mode == Mode.WIFI_HOTSPOT_ONLY || bleScanActive) return
        if (!hasBlePermissions()) {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            activity.requestPermissions(permissions, REQUEST_SCAN)
            status("اسمح بالأجهزة القريبة لإكمال Bluetooth؛ QR وWi‑Fi/Hotspot لا ينتظران الإنترنت")
            return
        }
        val bt = adapter
        if (bt?.isEnabled != true) {
            status("Bluetooth مغلق — سيعاد البحث تلقائيًا عند تشغيله؛ Wi‑Fi/Hotspot وQR يعملان")
            handler.removeCallbacks(bleScanRetry); handler.postDelayed(bleScanRetry, 2_000L)
            return
        }
        val scanner = bt.bluetoothLeScanner ?: run {
            status("BLE Scanner غير متاح الآن — إعادة المحاولة تلقائيًا")
            handler.removeCallbacks(bleScanRetry); handler.postDelayed(bleScanRetry, 2_000L)
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()
        // 1.9.57 compatibility rule: while the explicit pairing screen is open, scan without
        // controller-side filters and validate ATTEND-PRO frames in onScanResult().  A number of
        // OEM Bluetooth stacks incorrectly drop service-data/manufacturer filtered results even
        // though the same advertisement is visible to an unfiltered foreground scan.  Pairing is
        // short-lived, so the small temporary power cost is preferable to a false "no device".
        val started = runCatching { scanner.startScan(emptyList(), settings, scanCallback); true }.getOrDefault(false)
        if (started) {
            bleScanner = scanner
            bleScanActive = true
            bleRetryCount = 0
            status("Bluetooth Scanner يعمل — بانتظار جهاز المحل")
        } else {
            bleScanActive = false
            bleScanner = null
            bleRetryCount = (bleRetryCount + 1).coerceAtMost(8)
            status("تعذر بدء BLE — إعادة المحاولة تلقائيًا؛ Wi‑Fi/Hotspot وQR مستمران")
            handler.removeCallbacks(bleScanRetry); handler.postDelayed(bleScanRetry, (1_000L * bleRetryCount).coerceAtMost(6_000L))
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScanForGatt() {
        handler.removeCallbacks(bleScanRetry)
        if (hasBlePermissions()) runCatching { bleScanner?.stopScan(scanCallback) }
        bleScanActive = false
        bleScanner = null
    }

    @SuppressLint("MissingPermission")
    private fun requestProvisionOverGatt(device: BluetoothDevice, code: String) {
        if (!running || connectingGatt || !hasBlePermissions() || mode == Mode.WIFI_HOTSPOT_ONLY) return
        if (lastGattDevice?.address != device.address || !lastGattCode.equals(code, true)) gattAttempt = 0
        lastGattDevice = device
        lastGattCode = code
        gattAttempt += 1
        connectingGatt = true
        gattServicesRequested = false
        gattAwaitingAck = false
        gattProvisionReady = ""
        status("Bluetooth وجد جهاز المحل — ربط كامل (محاولة $gattAttempt/5)…")
        gattOffset = 0
        gattBuffer.reset()
        handler.removeCallbacks(gattTimeout)
        handler.postDelayed(gattTimeout, 12_000L)

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, newState: Int) {
                if (!running) { runCatching { g.close() }; return }
                if (statusCode == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    runCatching { g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
                    val mtuRequested = runCatching { g.requestMtu(247) }.getOrDefault(false)
                    handler.postDelayed({ discoverServicesOnce(g) }, if (mtuRequested) 900L else 250L)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED || statusCode != BluetoothGatt.GATT_SUCCESS) {
                    failGatt(g, "انقطع الربط عبر Bluetooth")
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, statusCode: Int) { discoverServicesOnce(g) }

            override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
                if (statusCode != BluetoothGatt.GATT_SUCCESS) { failGatt(g, "تعذر اكتشاف خدمة الربط عبر Bluetooth"); return }
                val service: BluetoothGattService = g.getService(BleProtocol.SERVICE_UUID.uuid)
                    ?: run { failGatt(g, "جهاز المحل لا يعرض خدمة الربط المطلوبة"); return }
                val characteristic = service.getCharacteristic(PROVISION_CHARACTERISTIC_UUID)
                    ?: run { failGatt(g, "قناة الربط غير متاحة عبر Bluetooth"); return }
                requestChunk(g, characteristic, code, 0)
            }

            override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, statusCode: Int) {
                if (characteristic.uuid != PROVISION_CHARACTERISTIC_UUID || !running) return
                if (statusCode != BluetoothGatt.GATT_SUCCESS) { failGatt(g, "رفض جهاز المحل رسالة الربط عبر Bluetooth"); return }
                runCatching { g.readCharacteristic(characteristic) }.onFailure { failGatt(g, "تعذر قراءة ملف الربط عبر Bluetooth") }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, statusCode: Int) {
                handleGattChunk(g, characteristic, characteristic.value ?: ByteArray(0), statusCode, code)
            }

            override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, statusCode: Int) {
                handleGattChunk(g, characteristic, value, statusCode, code)
            }
        }
        gatt = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) device.connectGatt(activity, false, callback, BluetoothDevice.TRANSPORT_LE)
            else device.connectGatt(activity, false, callback)
        }.getOrNull()
        if (gatt == null) { connectingGatt = false; handler.removeCallbacks(gattTimeout) }
    }

    @SuppressLint("MissingPermission")
    private fun discoverServicesOnce(g: BluetoothGatt) {
        if (!running || !connectingGatt || gattServicesRequested) return
        gattServicesRequested = true
        if (!runCatching { g.discoverServices() }.getOrDefault(false)) failGatt(g, "تعذر بدء اكتشاف خدمة Bluetooth")
    }

    @SuppressLint("MissingPermission")
    private fun requestChunk(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, code: String, offset: Int) {
        if (!running) return
        gattOffset = offset
        val command = "APGET:$code:$offset".toByteArray(Charsets.UTF_8)
        handler.removeCallbacks(gattTimeout)
        handler.postDelayed(gattTimeout, 7_000L)
        writeGatt(g, characteristic, command, "تعذر طلب ملف الربط عبر Bluetooth")
    }

    @SuppressLint("MissingPermission")
    private fun writeGatt(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, error: String) {
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = value
                @Suppress("DEPRECATION")
                g.writeCharacteristic(characteristic)
            }
        }.getOrDefault(false)
        if (!started) failGatt(g, error)
    }

    @SuppressLint("MissingPermission")
    private fun handleGattChunk(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, statusCode: Int, code: String) {
        if (!running || characteristic.uuid != PROVISION_CHARACTERISTIC_UUID) return
        if (statusCode != BluetoothGatt.GATT_SUCCESS || value.isEmpty()) { failGatt(g, "وصل جزء غير صالح من ملف الربط عبر Bluetooth"); return }
        gattBuffer.write(value)
        val text = gattBuffer.toByteArray().toString(Charsets.UTF_8)
        val provision = PairingProtocol.decodeEmployeeProvision(text)
        if (provision != null) {
            val secret = SecretCodec.decode(provision.pairingSecret) ?: run { failGatt(g, "مفتاح الربط المستلم غير صالح"); return }
            // Best-effort ACK for the Store pairing screen; do not make persistence depend on
            // a second characteristic-write round trip after the full authenticated provision arrived.
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeCharacteristic(characteristic, PairingAckProtocol.encode(code, secret), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = PairingAckProtocol.encode(code, secret)
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(characteristic)
                }
            }
            handler.removeCallbacks(gattTimeout)
            connectingGatt = false
            gattAttempt = 0
            finishFound(text, "Bluetooth مباشر • ملف موثق")
            return
        }
        if (gattBuffer.size() < MAX_GATT_BUFFER) requestChunk(g, characteristic, code, gattOffset + value.size)
        else failGatt(g, "ملف الربط عبر Bluetooth أكبر من الحد الآمن")
    }

    @SuppressLint("MissingPermission")
    private fun failGatt(g: BluetoothGatt?, message: String) {
        handler.removeCallbacks(gattTimeout)
        connectingGatt = false
        gattServicesRequested = false
        gattAwaitingAck = false
        gattProvisionReady = ""
        runCatching { g?.disconnect() }; runCatching { g?.close() }
        if (g === gatt) gatt = null
        val retryDevice = lastGattDevice
        val retryCode = lastGattCode
        if (running && gattAttempt < 5 && retryDevice != null && retryCode.isNotBlank() && mode != Mode.WIFI_HOTSPOT_ONLY) {
            // GATT 133 and other OEM stack failures usually require a real cooldown after close().
            // Rapid reconnect loops make them worse, so use progressive backoff.
            val delays = longArrayOf(1_200L, 2_500L, 4_500L, 7_000L, 9_000L)
            val delay = delays[(gattAttempt - 1).coerceIn(0, delays.lastIndex)]
            status("$message — إعادة محاولة GATT بعد ${delay / 1000.0}ث…")
            handler.postDelayed({ if (running && !connectingGatt) requestProvisionOverGatt(retryDevice, retryCode) }, delay)
        } else {
            status("$message — إعادة اكتشاف جهاز المحل من جديد عبر Bluetooth؛ Wi‑Fi/Hotspot وQR مستمران")
            lastGattDevice = null; lastGattCode = ""; gattAttempt = 0
            handler.removeCallbacks(bleScanRetry)
            if (running && mode != Mode.WIFI_HOTSPOT_ONLY) handler.postDelayed(bleScanRetry, 1_500L)
        }
    }

    private fun discoveryTargets(): Set<InetAddress> {
        val result = linkedSetOf<InetAddress>()
        fun add(value: String) { runCatching { result.add(InetAddress.getByName(value)) } }
        add("255.255.255.255")
        listOf("192.168.43.1", "192.168.232.1", "192.168.137.1", "192.168.49.1", "192.168.4.1").forEach(::add)
        listOf("192.168.43.255", "192.168.232.255", "192.168.137.255", "192.168.49.255", "192.168.4.255").forEach(::add)
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { network ->
                if (network.isUp && !network.isLoopback) network.interfaceAddresses.forEach { item ->
                    (item.broadcast as? Inet4Address)?.let { result.add(it) }
                    val local = item.address as? Inet4Address
                    if (local != null && !local.isLoopbackAddress) {
                        val bytes = local.address.clone()
                        bytes[3] = 0xff.toByte(); runCatching { result.add(InetAddress.getByAddress(bytes)) }
                        bytes[3] = 1; runCatching { result.add(InetAddress.getByAddress(bytes)) }
                    }
                }
            }
        }
        return result
    }

    private fun finishFound(value: String, channel: String) {
        if (!running) return
        running = false
        handler.removeCallbacks(gattTimeout)
        activity.runOnUiThread { stop(); found(value, channel) }
    }

    fun resumeAfterPermission() { val code = targetCode; val selected = mode; start(code, selected) }

    @SuppressLint("MissingPermission")
    fun stop() {
        running = false
        handler.removeCallbacks(gattTimeout)
        handler.removeCallbacks(bleScanRetry)
        runCatching { socket?.close() }; socket = null
        stopBleScanForGatt()
        runCatching { gatt?.disconnect() }; runCatching { gatt?.close() }; gatt = null
        connectingGatt = false; gattOffset = 0; gattBuffer.reset(); gattServicesRequested = false
        gattAwaitingAck = false; gattProvisionReady = ""
        lastGattDevice = null; lastGattCode = ""; gattAttempt = 0
        pendingCode = ""; targetCode = ""; pendingLanProvision = ""; pendingLanCode = ""
    }
}
