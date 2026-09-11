package com.attendpro.employee

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.attendpro.core.AttendanceMethod
import com.attendpro.core.AttendanceAction
import com.attendpro.core.BleDirectProtocol
import com.attendpro.core.BleLocalMessageProtocol1977
import com.attendpro.core.BleLocalReplyChannel1977
import com.attendpro.core.EmployeeIdentityStore
import com.attendpro.core.SecretCodec
import java.util.ArrayDeque

/** Employee-side GATT endpoint. Presence is confirmed only after signed PING -> ACK -> ACK-confirm. */
class BleDirectLinkServer(
    private val context: Context,
    private val identity: EmployeeIdentityStore,
    private val onStatus: (String) -> Unit,
    private val onChallenge: (String, AttendanceMethod, Long, AttendanceAction) -> Unit
) {
    private data class AuthState(var lastProtocolAt: Long, var lastPingNonce: Int, var confirmed: Boolean = false)
    private var server: BluetoothGattServer? = null
    private var running = false
    @Volatile private var serviceReady = false
    private val authenticatedDevices = mutableMapOf<String, AuthState>()
    private val localMessageReceiver = EmployeeLocalMessageReceiver1977(context, identity)
    private val localReplyFrames = ArrayDeque<ByteArray>()
    init { EmployeeDirectReplyBridge1977.bind(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val retryStart = Runnable { if (!running && identity.isConfigured && hasConnectPermission()) start() }
    private val staleTask = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            authenticatedDevices.entries.removeAll { now - it.value.lastProtocolAt > AUTH_SESSION_MILLIS }
            val last = identity.lastBleDirectSeenAt
            if (last > 0L && now - last > HEARTBEAT_STALE_MILLIS && identity.lastBleDirectState.contains("متصل")) {
                identity.lastBleDirectState = "انقطع ACK المباشر — يستمر BLE/Wi‑Fi"
                onStatus("انقطع Heartbeat/ACK من جهاز المحل — يستمر الاكتشاف التلقائي")
            }
            if (running) handler.postDelayed(this, 2_000L)
        }
    }

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        EmployeeDirectReplyBridge1977.bind(this)
        if (running || !identity.isConfigured || !hasConnectPermission()) return
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return
        val adapter = manager.adapter
        if (adapter?.isEnabled != true) {
            onStatus("Bluetooth مغلق — GATT سيبدأ تلقائيًا بعد تشغيله")
            handler.removeCallbacks(retryStart); handler.postDelayed(retryStart, 2_000L)
            return
        }
        val gatt = runCatching { manager.openGattServer(context, callback) }.getOrNull()
        if (gatt == null) {
            onStatus("تعذر فتح GATT Server في هاتف الموظف — إعادة المحاولة تلقائيًا")
            handler.removeCallbacks(retryStart); handler.postDelayed(retryStart, 2_000L)
            return
        }
        val service = BluetoothGattService(BleDirectProtocol.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val command = BluetoothGattCharacteristic(
            BleDirectProtocol.COMMAND_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(command)
        service.addCharacteristic(BluetoothGattCharacteristic(
            BleLocalReplyChannel1977.REPLY_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        ))
        if (!gatt.addService(service)) {
            gatt.close()
            handler.removeCallbacks(retryStart)
            handler.postDelayed(retryStart, 1_500L)
            onStatus("تعذر تسجيل خدمة Bluetooth المباشرة — ستتم إعادة المحاولة تلقائيًا")
            return
        }
        server = gatt; running = true; serviceReady = false
        handler.removeCallbacks(staleTask); handler.post(staleTask)
        onStatus("جارٍ تجهيز خدمة Bluetooth BLE المباشرة…")
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        running = false; serviceReady = false; handler.removeCallbacks(staleTask); handler.removeCallbacks(retryStart); authenticatedDevices.clear()
        EmployeeDirectReplyBridge1977.unbind(this)
        runCatching { server?.close() }; server = null
    }

    fun isRunning(): Boolean = running
    fun isServiceReady(): Boolean = running && serviceReady

    fun hasAuthenticatedStore(): Boolean {
        val now = System.currentTimeMillis()
        return running && serviceReady && authenticatedDevices.values.any {
            it.confirmed && now - it.lastProtocolAt <= AUTH_SESSION_MILLIS
        }
    }

    fun queueLocalReply(parentMessageId: String, message: String): Boolean {
        if (!hasAuthenticatedStore()) return false
        val secret = SecretCodec.decode(identity.pairingSecret) ?: return false
        val body = message.trim().take(500)
        if (body.isBlank()) return false
        val title = if (parentMessageId.isBlank()) "رسالة من الموظف" else "رد من الموظف"
        val encoded = runCatching {
            BleLocalMessageProtocol1977.encodeMessage(secret, title, body, "NORMAL", false)
        }.getOrNull() ?: return false
        synchronized(localReplyFrames) {
            if (localReplyFrames.size + encoded.frames.size > 720) return false
            encoded.frames.forEach { localReplyFrames.addLast(it) }
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun authFor(device: BluetoothDevice?): AuthState? {
        if (!hasConnectPermission()) return null
        val address = device?.address ?: return null
        val state = authenticatedDevices[address] ?: return null
        return state.takeIf { System.currentTimeMillis() - it.lastProtocolAt <= AUTH_SESSION_MILLIS }
    }

    @SuppressLint("MissingPermission")
    private val callback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (service?.uuid != BleDirectProtocol.SERVICE_UUID) return
            serviceReady = status == BluetoothGatt.GATT_SUCCESS
            if (serviceReady) {
                onStatus("Bluetooth BLE المباشر جاهز للتحقق عبر Heartbeat/ACK")
            } else {
                running = false
                serviceReady = false
                runCatching { server?.close() }
                server = null
                handler.removeCallbacks(retryStart)
                handler.postDelayed(retryStart, 1_500L)
                onStatus("تعذر تجهيز خدمة Bluetooth المباشرة — ستتم إعادة المحاولة تلقائيًا")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                identity.lastBleDirectState = "تم فتح Bluetooth — بانتظار Heartbeat موثق"
                onStatus("تم فتح قناة Bluetooth؛ لن تُعتبر متصلة قبل ACK الموثق")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                device?.address?.let { authenticatedDevices.remove(it) }
                identity.lastBleDirectState = "انقطع الاتصال المباشر — يستمر BLE/Wi‑Fi"
                onStatus("انقطع Bluetooth المباشر — يستمر BLE/Wi‑Fi تلقائيًا")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            val bytes = value ?: ByteArray(0)
            val secret = SecretCodec.decode(identity.pairingSecret) ?: ByteArray(0)
            var ok = false
            if (characteristic?.uuid == BleDirectProtocol.COMMAND_UUID && offset == 0 && secret.isNotEmpty()) {
                val ping = BleDirectProtocol.decodePing(bytes, secret)
                if (ping != null) {
                    ok = true
                    device?.address?.let { authenticatedDevices[it] = AuthState(System.currentTimeMillis(), ping.nonce, confirmed = false) }
                    identity.lastBleDirectState = "Heartbeat موثق • تم تجهيز ACK • بانتظار تأكيد الاستلام"
                    onStatus("Heartbeat موثق من جهاز المحل؛ ACK جاهز وبانتظار ACK-confirm")
                } else if (authFor(device) != null) {
                    val auth = authFor(device)!!
                    if (BleDirectProtocol.verifyAckConfirm(bytes, secret, auth.lastPingNonce)) {
                        ok = true
                        auth.lastProtocolAt = System.currentTimeMillis()
                        auth.confirmed = true
                        identity.lastBleDirectSeenAt = auth.lastProtocolAt
                        identity.lastBleDirectState = "متصل بالمحل عبر Bluetooth BLE • ACK + confirm موثق"
                        onStatus("✓ Bluetooth BLE مؤكد ثنائيًا عبر Heartbeat/ACK/confirm")
                    } else {
                    val config = if (auth.confirmed) BleDirectProtocol.decodeConfig(bytes) else null
                    if (config != null) {
                        ok = true
                        if (config.gpsConfigured) {
                            identity.trustedStoreLatitude = config.latitude; identity.trustedStoreLongitude = config.longitude
                        } else {
                            identity.trustedStoreLatitude = Double.NaN; identity.trustedStoreLongitude = Double.NaN
                        }
                        identity.trustedStoreGpsRadius = config.radiusMeters
                        identity.employeeVoicePromptsEnabled = config.voicePromptsEnabled
                        identity.geoArrivalAlertsEnabled = config.geoAlertsEnabled
                        identity.shiftStartHour = config.shiftStartHour; identity.shiftStartMinute = config.shiftStartMinute
                        identity.shiftEndHour = config.shiftEndHour; identity.shiftEndMinute = config.shiftEndMinute
                        onStatus("✓ تمت مزامنة إعدادات المحل عبر Bluetooth بدون إنترنت")
                    } else {
                        val challenge = if (auth.confirmed) BleDirectProtocol.decodeChallenge(bytes, secret) else null
                        if (challenge != null) {
                            ok = true
                            identity.lastBleDirectState = "استقبل طلب إثبات عبر Bluetooth مباشر ضمن جلسة موثقة"
                            onChallenge("local:${challenge.challengeId}", challenge.method, challenge.expiresAt, challenge.action)
                        } else if (auth.confirmed) {
                            val localMessage = localMessageReceiver.accept(bytes, secret)
                            if (localMessage.accepted) {
                                ok = true
                                auth.lastProtocolAt = System.currentTimeMillis()
                                localMessage.completed?.let { message ->
                                    identity.lastBleDirectSeenAt = auth.lastProtocolAt
                                    identity.lastBleDirectState = "متصل بالمحل • استلم رسالة محلية موثقة"
                                    if (identity.employeeMessageNotificationsEnabled) EmployeeMessageNotifier1975.notify(context, message)
                                    onStatus("✓ استلمت رسالة من إدارة المحل مباشرة بدون إنترنت")
                                }
                            }
                        }
                    }
                    }
                }
            }
            if (responseNeeded) runCatching {
                server?.sendResponse(device, requestId, if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) {
            val secret = SecretCodec.decode(identity.pairingSecret) ?: ByteArray(0)
            val auth = authFor(device)
            if (characteristic?.uuid == BleLocalReplyChannel1977.REPLY_UUID) {
                if (offset != 0 || auth?.confirmed != true || secret.isEmpty()) {
                    runCatching { server?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null) }
                    return
                }
                val payload = synchronized(localReplyFrames) {
                    if (localReplyFrames.isEmpty()) ByteArray(0) else localReplyFrames.removeFirst()
                }
                auth.lastProtocolAt = System.currentTimeMillis()
                runCatching { server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, payload) }
                return
            }
            val payload = if (characteristic?.uuid == BleDirectProtocol.COMMAND_UUID && secret.isNotEmpty() && auth != null) {
                BleDirectProtocol.encodeAck(secret, auth.lastPingNonce)
            } else ByteArray(0)
            val safeOffset = offset.coerceIn(0, payload.size)
            val status = if (payload.isNotEmpty()) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
            runCatching { server?.sendResponse(device, requestId, status, safeOffset, if (payload.isEmpty()) null else payload.copyOfRange(safeOffset, payload.size)) }
        }
    }

    companion object {
        private const val AUTH_SESSION_MILLIS = 15_000L
        private const val HEARTBEAT_STALE_MILLIS = 12_000L
    }
}
