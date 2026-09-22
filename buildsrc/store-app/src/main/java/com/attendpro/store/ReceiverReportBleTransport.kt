package com.attendpro.store

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import com.attendpro.core.ReceiverOfflineReportProtocol
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

object ReceiverReportBluetoothSupport {
    fun missingPermissions(context: Context): List<String> {
        if (Build.VERSION.SDK_INT < 31) return emptyList()
        return listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ).filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }

    fun hasPermissions(context: Context): Boolean = missingPermissions(context).isEmpty()

    fun request(activity: Activity, requestCode: Int) {
        val missing = missingPermissions(activity)
        if (missing.isNotEmpty()) activity.requestPermissions(missing.toTypedArray(), requestCode)
    }

    @SuppressLint("MissingPermission")
    fun bluetoothEnabled(context: Context): Boolean {
        if (!hasPermissions(context)) return false
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        return manager.adapter?.isEnabled == true
    }
}

class ReceiverReportBleServer(
    context: Context,
    private val receiverId: String,
    private val secret: String,
    private val onEnvelope: (ReceiverOfflineReportProtocol.Envelope) -> Boolean,
    private val onStatus: (String) -> Unit
) {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000f139-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: UUID = UUID.fromString("0000f13a-0000-1000-8000-00805f9b34fb")
        val ACK_UUID: UUID = UUID.fromString("0000f13b-0000-1000-8000-00805f9b34fb")
        private const val MAX_PARTS = 12000
    }

    private data class Assembly(
        val session: Int,
        val total: Int,
        val parts: Array<ByteArray?>,
        var count: Int = 0
    )

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = manager.adapter
    private var gattServer: BluetoothGattServer? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private val assemblies = mutableMapOf<String, Assembly>()
    private val ackByDevice = mutableMapOf<String, ByteArray>()

    @Volatile private var running = false

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        if (!ReceiverReportBluetoothSupport.hasPermissions(appContext)) {
            onStatus("Bluetooth: الصلاحيات غير مكتملة")
            return false
        }
        val a = adapter
        if (a == null || !a.isEnabled) {
            onStatus("Bluetooth: غير مفعّل")
            return false
        }
        val server = manager.openGattServer(appContext, serverCallback) ?: run {
            onStatus("Bluetooth: تعذر فتح GATT Server")
            return false
        }
        gattServer = server
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                WRITE_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                ACK_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        if (!server.addService(service)) {
            server.close()
            gattServer = null
            onStatus("Bluetooth: تعذر تسجيل خدمة استقبال التقارير")
            return false
        }

        val advertiser = a.bluetoothLeAdvertiser ?: run {
            server.close(); gattServer = null
            onStatus("Bluetooth: المعلن BLE غير متاح")
            return false
        }
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                running = true
                onStatus("Bluetooth: هاتف الاستلام ظاهر وجاهز ✓")
            }

            override fun onStartFailure(errorCode: Int) {
                running = false
                onStatus("Bluetooth: فشل الإعلان (رمز $errorCode)")
            }
        }
        advertiseCallback = callback
        advertiser.startAdvertising(
            AdvertiseSettings.Builder()
                .setConnectable(true)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build(),
            AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .addServiceData(ParcelUuid(SERVICE_UUID), ReceiverOfflineReportProtocol.receiverHash(receiverId))
                .build(),
            callback
        )
        return true
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        running = false
        val a = adapter
        advertiseCallback?.let { cb -> runCatching { a?.bluetoothLeAdvertiser?.stopAdvertising(cb) } }
        advertiseCallback = null
        runCatching { gattServer?.close() }
        gattServer = null
        synchronized(assemblies) {
            assemblies.clear()
            ackByDevice.clear()
        }
    }

    fun isRunning(): Boolean = running

    private val serverCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                synchronized(assemblies) {
                    assemblies.remove(device.address)
                    ackByDevice.remove(device.address)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            var status = BluetoothGatt.GATT_FAILURE
            if (characteristic.uuid == WRITE_UUID && !preparedWrite && offset == 0) {
                status = if (acceptFrame(device, value)) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
            }
            if (responseNeeded) {
                runCatching { gattServer?.sendResponse(device, requestId, status, 0, null) }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid != ACK_UUID || offset != 0) {
                runCatching { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null) }
                return
            }
            val value = synchronized(assemblies) { ackByDevice[device.address] } ?: ByteArray(0)
            runCatching { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value) }
        }
    }

    private fun acceptFrame(device: BluetoothDevice, value: ByteArray): Boolean {
        if (value.size < 11 || value[0] != 0x41.toByte() || value[1] != 0x52.toByte()) return false
        val b = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN)
        b.position(2)
        val session = b.int
        val index = b.short.toInt() and 0xffff
        val total = b.short.toInt() and 0xffff
        val dataLen = b.get().toInt() and 0xff
        if (total !in 1..MAX_PARTS || index !in 0 until total || dataLen != value.size - 11) return false
        val data = ByteArray(dataLen)
        b.get(data)

        val completed: ByteArray? = synchronized(assemblies) {
            var assembly = assemblies[device.address]
            if (assembly == null || assembly.session != session || assembly.total != total) {
                assembly = Assembly(session, total, arrayOfNulls(total))
                assemblies[device.address] = assembly
            }
            if (assembly.parts[index] == null) {
                assembly.parts[index] = data
                assembly.count++
            }
            if (assembly.count == total) {
                val out = ByteArrayOutputStream()
                assembly.parts.forEach { part -> if (part != null) out.write(part) }
                assemblies.remove(device.address)
                out.toByteArray()
            } else null
        }

        if (completed != null) {
            val envelope = ReceiverOfflineReportProtocol.decodeEnvelope(
                String(completed, Charsets.UTF_8), receiverId, secret
            ) ?: return false
            if (!runCatching { onEnvelope(envelope) }.getOrDefault(false)) return false
            val ack = ReceiverOfflineReportProtocol.ack(
                receiverId, envelope.storeId, envelope.transferId, "BLE", secret
            ).toByteArray(Charsets.UTF_8)
            synchronized(assemblies) { ackByDevice[device.address] = ack }
            onStatus("Bluetooth: تم استلام تقرير محلي ✓")
        }
        return true
    }
}

object ReceiverReportBleClient {
    private const val FRAME_HEADER = 11
    private const val DESIRED_MTU = 247

    @SuppressLint("MissingPermission")
    fun send(
        context: Context,
        receiverId: String,
        secret: String,
        storeId: String,
        transferId: String,
        envelope: String
    ): ReceiverReportDeliveryResult {
        if (!ReceiverReportBluetoothSupport.hasPermissions(context)) {
            return ReceiverReportDeliveryResult(false, "BLE", "صلاحيات Bluetooth غير ممنوحة")
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return ReceiverReportDeliveryResult(false, "BLE", "Bluetooth غير متاح")
        val adapter = manager.adapter
            ?: return ReceiverReportDeliveryResult(false, "BLE", "Bluetooth غير متاح")
        if (!adapter.isEnabled) return ReceiverReportDeliveryResult(false, "BLE", "Bluetooth غير مفعّل")
        val scanner = adapter.bluetoothLeScanner
            ?: return ReceiverReportDeliveryResult(false, "BLE", "BLE Scanner غير متاح")

        val wantedHash = ReceiverOfflineReportProtocol.receiverHash(receiverId)
        val parcel = ParcelUuid(ReceiverReportBleServer.SERVICE_UUID)
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        fun scanWindow(filters: List<ScanFilter>, timeoutMs: Long): BluetoothDevice? {
            val found = arrayOfNulls<BluetoothDevice>(1)
            val latch = CountDownLatch(1)
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val record = result.scanRecord ?: return
                    val hash = record.getServiceData(parcel) ?: return
                    if (hash.contentEquals(wantedHash)) {
                        found[0] = result.device
                        latch.countDown()
                    }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { result ->
                        val hash = result.scanRecord?.getServiceData(parcel) ?: return@forEach
                        if (hash.contentEquals(wantedHash) && found[0] == null) {
                            found[0] = result.device
                            latch.countDown()
                        }
                    }
                }
            }
            return try {
                scanner.startScan(filters, settings, callback)
                latch.await(timeoutMs, TimeUnit.MILLISECONDS)
                found[0]
            } finally {
                runCatching { scanner.stopScan(callback) }
            }
        }

        // Some Android/OEM Bluetooth stacks fail to return a device when a service UUID
        // filter is used even though the advertisement is visible. Try the efficient
        // filtered scan first, then an unfiltered fallback while still authenticating the
        // receiver by its advertised receiverId hash.
        val filtered = listOf(ScanFilter.Builder().setServiceUuid(parcel).build())
        val device = scanWindow(filtered, 2_500L)
            ?: scanWindow(emptyList(), 2_500L)
            ?: return ReceiverReportDeliveryResult(
                false, "BLE",
                "لم يظهر هاتف الاستلام عبر BLE بعد المسح المفلتر والاحتياطي"
            )

        val sync = SyncGattCallback()
        val gatt = device.connectGatt(context, false, sync, BluetoothDevice.TRANSPORT_LE)
        try {
            if (!sync.awaitConnected(6_000)) return ReceiverReportDeliveryResult(false, "BLE", "تعذر الاتصال بهاتف الاستلام")
            if (!gatt.discoverServices() || !sync.awaitServices(6_000)) {
                return ReceiverReportDeliveryResult(false, "BLE", "تعذر اكتشاف خدمة تقارير Bluetooth")
            }
            gatt.requestMtu(DESIRED_MTU)
            sync.awaitMtu(1_500)

            val service = gatt.getService(ReceiverReportBleServer.SERVICE_UUID)
                ?: return ReceiverReportDeliveryResult(false, "BLE", "خدمة التقرير غير موجودة")
            val write = service.getCharacteristic(ReceiverReportBleServer.WRITE_UUID)
                ?: return ReceiverReportDeliveryResult(false, "BLE", "قناة إرسال التقرير غير موجودة")
            val ack = service.getCharacteristic(ReceiverReportBleServer.ACK_UUID)
                ?: return ReceiverReportDeliveryResult(false, "BLE", "قناة تأكيد التقرير غير موجودة")

            val bytes = envelope.toByteArray(Charsets.UTF_8)
            val mtu = sync.mtu.coerceAtLeast(23)
            val maxPayload = (mtu - 3 - FRAME_HEADER).coerceAtLeast(8).coerceAtMost(235)
            val maxAllowed = if (mtu >= 100) 180_000 else 24_000
            if (bytes.size > maxAllowed) {
                return ReceiverReportDeliveryResult(false, "BLE", "حجم التقرير كبير لقناة BLE الحالية؛ سيتم استخدام LAN أو الخادم")
            }
            val total = ceil(bytes.size.toDouble() / maxPayload.toDouble()).toInt().coerceAtLeast(1)
            if (total > 12000) return ReceiverReportDeliveryResult(false, "BLE", "عدد أجزاء BLE تجاوز الحد الآمن")
            val session = SecureRandom().nextInt()

            for (index in 0 until total) {
                val from = index * maxPayload
                val to = minOf(bytes.size, from + maxPayload)
                val payload = bytes.copyOfRange(from, to)
                val frame = ByteBuffer.allocate(FRAME_HEADER + payload.size).order(ByteOrder.BIG_ENDIAN).apply {
                    put(0x41.toByte()); put(0x52.toByte()); putInt(session); putShort(index.toShort()); putShort(total.toShort())
                    put(payload.size.toByte()); put(payload)
                }.array()
                if (!sync.write(gatt, write, frame, 2_500)) {
                    return ReceiverReportDeliveryResult(false, "BLE", "انقطع إرسال BLE عند الجزء ${index + 1} من $total")
                }
            }

            val ackBytes = sync.read(gatt, ack, 4_000)
                ?: return ReceiverReportDeliveryResult(false, "BLE", "لم يصل ACK من هاتف الاستلام")
            val transport = ReceiverOfflineReportProtocol.verifyAck(
                String(ackBytes, Charsets.UTF_8), receiverId, storeId, transferId, secret
            ) ?: return ReceiverReportDeliveryResult(false, "BLE", "ACK Bluetooth غير موثوق")
            return ReceiverReportDeliveryResult(true, transport, "تم الاستلام عبر Bluetooth")
        } catch (t: Throwable) {
            return ReceiverReportDeliveryResult(false, "BLE", t.message ?: "تعذر النقل عبر Bluetooth")
        } finally {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
    }

    private class SyncGattCallback : BluetoothGattCallback() {
        private val lock = Object()
        @Volatile var connected = false
        @Volatile var services = false
        @Volatile var mtu = 23
        private var writeDone = false
        private var writeStatus = BluetoothGatt.GATT_FAILURE
        private var readDone = false
        private var readStatus = BluetoothGatt.GATT_FAILURE
        private var readValue: ByteArray? = null
        private var mtuDone = false

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            synchronized(lock) {
                connected = status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED
                lock.notifyAll()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            synchronized(lock) {
                services = status == BluetoothGatt.GATT_SUCCESS
                lock.notifyAll()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            synchronized(lock) {
                if (status == BluetoothGatt.GATT_SUCCESS) this.mtu = mtu
                mtuDone = true
                lock.notifyAll()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            synchronized(lock) {
                writeStatus = status
                writeDone = true
                lock.notifyAll()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            synchronized(lock) {
                readStatus = status
                readValue = characteristic.value?.clone()
                readDone = true
                lock.notifyAll()
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            synchronized(lock) {
                readStatus = status
                readValue = value.clone()
                readDone = true
                lock.notifyAll()
            }
        }

        fun awaitConnected(timeout: Long): Boolean = await(timeout) { connected }
        fun awaitServices(timeout: Long): Boolean = await(timeout) { services }

        fun awaitMtu(timeout: Long) {
            await(timeout) { mtuDone }
        }

        @SuppressLint("MissingPermission")
        fun write(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, timeout: Long): Boolean {
            synchronized(lock) {
                writeDone = false
                writeStatus = BluetoothGatt.GATT_FAILURE
            }
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            val started = if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == 0
            } else {
                @Suppress("DEPRECATION")
                run {
                    characteristic.value = value
                    gatt.writeCharacteristic(characteristic)
                }
            }
            if (!started) return false
            return await(timeout) { writeDone } && writeStatus == BluetoothGatt.GATT_SUCCESS
        }

        @SuppressLint("MissingPermission")
        fun read(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, timeout: Long): ByteArray? {
            synchronized(lock) {
                readDone = false
                readStatus = BluetoothGatt.GATT_FAILURE
                readValue = null
            }
            if (!gatt.readCharacteristic(characteristic)) return null
            if (!await(timeout) { readDone } || readStatus != BluetoothGatt.GATT_SUCCESS) return null
            return readValue
        }

        private fun await(timeout: Long, condition: () -> Boolean): Boolean {
            val deadline = System.currentTimeMillis() + timeout
            synchronized(lock) {
                while (!condition()) {
                    val left = deadline - System.currentTimeMillis()
                    if (left <= 0L) break
                    try { lock.wait(left) } catch (_: InterruptedException) { break }
                }
                return condition()
            }
        }
    }
}
