package com.attendpro.store

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.attendpro.core.AttendanceAction
import com.attendpro.core.AttendanceMethod
import com.attendpro.core.BleDirectProtocol
import com.attendpro.core.BleLocalMessageProtocol1977
import java.security.SecureRandom
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Store-side authenticated GATT link.
 *
 * 1.9.58 keeps a remembered session after a transient GATT failure and reconnects with
 * progressive backoff while the employee advertisement is still fresh.  A phone is still
 * considered connected only after the signed PING -> ACK exchange succeeds; discovering an
 * advertisement alone never promotes the employee to "connected".
 */
class BleDirectLinkClient(
    private val context: Context,
    private val onState: (employeeId: String, connected: Boolean, message: String) -> Unit
) {
    private enum class Kind { HEARTBEAT, ACK_CONFIRM, CONFIG, CHALLENGE, LOCAL_MESSAGE }
    private data class Operation(
        val bytes: ByteArray, val kind: Kind, val pingNonce: Int? = null,
        val localMessageId: String = "", val localFinal: Boolean = false,
        val localCallback: ((Boolean, String) -> Unit)? = null
    )
    private data class Session(
        val employeeId: String,
        val secret: ByteArray,
        var device: BluetoothDevice,
        var deviceAddress: String,
        var config: BleDirectProtocol.Config,
        var gatt: BluetoothGatt? = null,
        var command: BluetoothGattCharacteristic? = null,
        var transportConnected: Boolean = false,
        var authenticated: Boolean = false,
        var lastAdvertisementAt: Long = 0L,
        var createdAt: Long = System.currentTimeMillis(),
        var stateChangedAt: Long = System.currentTimeMillis(),
        var lastAckAt: Long = 0L,
        var lastHeartbeatSentAt: Long = 0L,
        var writing: Boolean = false,
        var readingAck: Boolean = false,
        var activeOperation: Operation? = null,
        var awaitingPingNonce: Int? = null,
        var reconnectAttempt: Int = 0,
        var reconnectScheduled: Boolean = false,
        var generation: Int = 0,
        val queue: ArrayDeque<Operation> = ArrayDeque()
    )

    private val sessions = ConcurrentHashMap<String, Session>()
    private val diagnosticStates = ConcurrentHashMap<String, String>()
    private val handler = Handler(Looper.getMainLooper())
    private val random = SecureRandom()

    private val heartbeatTask = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            sessions.values.toList().forEach { s ->
                if (s.gatt != null && !s.transportConnected && now - s.createdAt > CONNECT_TIMEOUT_MILLIS) {
                    failAndReconnect(s, "مهلة فتح Bluetooth انتهت")
                } else if (s.transportConnected && s.command == null && now - s.stateChangedAt > SERVICE_TIMEOUT_MILLIS) {
                    failAndReconnect(s, "مهلة اكتشاف خدمة ATTEND-PRO انتهت")
                } else if (s.authenticated && now - s.lastAckAt > ACK_STALE_MILLIS) {
                    failAndReconnect(s, "انقطع ACK الحقيقي عبر Bluetooth")
                } else if (s.transportConnected && s.command != null && !s.writing && !s.readingAck && now - s.lastHeartbeatSentAt >= HEARTBEAT_INTERVAL_MILLIS) {
                    enqueueHeartbeat(s)
                } else if (s.gatt == null && !s.reconnectScheduled && now - s.lastAdvertisementAt <= ADVERTISEMENT_RECONNECT_WINDOW_MILLIS) {
                    scheduleReconnect(s, "استعادة Bluetooth تلقائيًا")
                }
            }
            handler.postDelayed(this, HEARTBEAT_TICK_MILLIS)
        }
    }

    init { handler.post(heartbeatTask) }

    private fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun touch(employeeId: String, secret: ByteArray, device: BluetoothDevice, config: BleDirectProtocol.Config) {
        if (employeeId.isBlank() || secret.isEmpty() || !hasPermission()) return
        val now = System.currentTimeMillis()
        val existing = sessions[employeeId]
        if (existing != null && existing.deviceAddress == device.address) {
            existing.device = device
            existing.lastAdvertisementAt = now
            existing.config = config
            if (existing.gatt == null && !existing.reconnectScheduled) {
                scheduleReconnect(existing, "تم رصد الهاتف مجددًا — إعادة اتصال Bluetooth", immediate = true)
            }
            return
        }

        existing?.let {
            sessions.remove(employeeId, it)
            closeTransport(it)
        }
        val session = Session(
            employeeId = employeeId,
            secret = secret.copyOf(),
            device = device,
            deviceAddress = device.address,
            config = config,
            lastAdvertisementAt = now,
            createdAt = now,
            stateChangedAt = now
        )
        sessions[employeeId] = session
        connectSession(session, "فتح Bluetooth مباشر")
    }

    fun diagnosticState(employeeId: String): String = diagnosticStates[employeeId] ?: "idle / not discovered"

    fun isConnected(employeeId: String): Boolean = sessions[employeeId]?.let {
        it.authenticated && it.command != null && System.currentTimeMillis() - it.lastAckAt <= ACK_STALE_MILLIS
    } == true

    fun lastAckAt(employeeId: String): Long = sessions[employeeId]?.lastAckAt ?: 0L

    fun sendChallenge(
        employeeId: String,
        method: AttendanceMethod,
        expiresAt: Long = System.currentTimeMillis() + 60_000L,
        requestToken: Int = SecureRandom().nextInt(),
        action: AttendanceAction = AttendanceAction.CHECK_IN
    ): Boolean {
        val s = sessions[employeeId] ?: return false
        if (!isConnected(employeeId)) return false
        enqueue(s, Operation(BleDirectProtocol.encodeChallenge(s.secret, method, expiresAt, requestToken, action), Kind.CHALLENGE))
        return true
    }


    fun sendLocalMessage(employeeId: String, title: String, body: String, priority: String, voiceEnabled: Boolean, callback: (Boolean, String) -> Unit): String? {
        val s = sessions[employeeId] ?: return null
        if (!isConnected(employeeId)) return null
        val encoded = runCatching { BleLocalMessageProtocol1977.encodeMessage(s.secret, title, body, priority, voiceEnabled) }.getOrNull() ?: return null
        encoded.frames.forEachIndexed { index, frame ->
            enqueue(s, Operation(frame, Kind.LOCAL_MESSAGE, localMessageId = encoded.messageId, localFinal = index == encoded.frames.lastIndex, localCallback = if (index == encoded.frames.lastIndex) callback else null))
        }
        return encoded.messageId
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        handler.removeCallbacks(heartbeatTask)
        sessions.values.toList().forEach { closeTransport(it) }
        sessions.clear()
    }

    @SuppressLint("MissingPermission")
    private fun connectSession(session: Session, reason: String) {
        if (sessions[session.employeeId] !== session || session.gatt != null || !hasPermission()) return
        session.reconnectScheduled = false
        session.generation += 1
        val generation = session.generation
        val now = System.currentTimeMillis()
        session.createdAt = now
        session.stateChangedAt = now
        session.transportConnected = false
        session.authenticated = false
        session.command = null
        session.writing = false
        session.readingAck = false
        session.activeOperation = null
        session.awaitingPingNonce = null
        synchronized(session) { session.queue.clear() }

        val attemptLabel = if (session.reconnectAttempt > 0) " • إعادة ${session.reconnectAttempt}" else ""
        diagnosticStates[session.employeeId] = "connecting$attemptLabel"
        onState(session.employeeId, false, "$reason$attemptLabel — بانتظار ACK")

        val gatt = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                session.device.connectGatt(context, false, callbackFor(session, generation), BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                session.device.connectGatt(context, false, callbackFor(session, generation))
            }
        }.getOrNull()
        session.gatt = gatt
        if (gatt == null) failAndReconnect(session, "تعذر فتح Bluetooth مباشر")
    }

    private fun callbackIsCurrent(session: Session, generation: Int, gatt: BluetoothGatt): Boolean =
        sessions[session.employeeId] === session && session.generation == generation && session.gatt === gatt

    @SuppressLint("MissingPermission")
    private fun callbackFor(session: Session, generation: Int) = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!callbackIsCurrent(session, generation, gatt)) {
                runCatching { gatt.close() }
                return
            }
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                session.transportConnected = true
                session.authenticated = false
                session.stateChangedAt = System.currentTimeMillis()
                diagnosticStates[session.employeeId] = "transport ✓ • discovering services"
                runCatching { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
                val discoveryStarted = runCatching { gatt.discoverServices() }.getOrDefault(false)
                if (!discoveryStarted) {
                    failAndReconnect(session, "تعذر اكتشاف خدمات Bluetooth")
                    return
                }
                onState(session.employeeId, false, "تم فتح Bluetooth — جارٍ التحقق من ACK")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                failAndReconnect(session, "انقطع Bluetooth المباشر (status=$status)")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!callbackIsCurrent(session, generation, gatt)) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failAndReconnect(session, "فشل اكتشاف خدمات Bluetooth")
                return
            }
            val service: BluetoothGattService = gatt.getService(BleDirectProtocol.SERVICE_UUID) ?: run {
                failAndReconnect(session, "خدمة ATTEND-PRO غير متاحة على الهاتف")
                return
            }
            session.command = service.getCharacteristic(BleDirectProtocol.COMMAND_UUID) ?: run {
                failAndReconnect(session, "قناة ATTEND-PRO المباشرة غير متاحة")
                return
            }
            session.stateChangedAt = System.currentTimeMillis()
            diagnosticStates[session.employeeId] = "services ✓ • sending heartbeat"
            enqueueHeartbeat(session)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (!callbackIsCurrent(session, generation, gatt) || characteristic.uuid != BleDirectProtocol.COMMAND_UUID) return
            val op = session.activeOperation
            session.writing = false
            session.activeOperation = null
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (op?.kind == Kind.HEARTBEAT) failAndReconnect(session, "فشل heartbeat عبر Bluetooth")
                else {
                    if (op?.kind == Kind.LOCAL_MESSAGE) handler.post { op.localCallback?.invoke(false, "فشل إرسال الرسالة المحلية عبر Bluetooth") }
                    synchronized(session) {
                        session.queue.filter { it.kind == Kind.LOCAL_MESSAGE && it.localFinal }.forEach { pending -> handler.post { pending.localCallback?.invoke(false, "انقطعت الرسالة المحلية قبل اكتمالها") } }
                        session.queue.clear()
                    }
                    onState(session.employeeId, isConnected(session.employeeId), "تعذر إرسال الطلب عبر Bluetooth المباشر")
                }
                return
            }
            when (op?.kind) {
                Kind.HEARTBEAT -> {
                    session.awaitingPingNonce = op.pingNonce
                    session.readingAck = true
                    diagnosticStates[session.employeeId] = "heartbeat sent • waiting ACK"
                    val started = runCatching {
                        @Suppress("DEPRECATION")
                        gatt.readCharacteristic(characteristic)
                    }.getOrDefault(false)
                    if (!started) failAndReconnect(session, "تعذر قراءة ACK عبر Bluetooth")
                }
                Kind.LOCAL_MESSAGE -> {
                    if (op.localFinal) handler.post { op.localCallback?.invoke(true, "تم تسليم الرسالة مباشرة عبر Bluetooth بدون إنترنت") }
                    drain(session)
                }
                Kind.ACK_CONFIRM, Kind.CONFIG, Kind.CHALLENGE, null -> drain(session)
            }
        }

        @Deprecated("Deprecated by Android 13 callback API")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (!callbackIsCurrent(session, generation, gatt)) return
            @Suppress("DEPRECATION")
            processAck(session, characteristic, characteristic.value ?: ByteArray(0), status)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (!callbackIsCurrent(session, generation, gatt)) return
            processAck(session, characteristic, value, status)
        }
    }

    private fun processAck(session: Session, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
        if (characteristic.uuid != BleDirectProtocol.COMMAND_UUID) return
        session.readingAck = false
        val nonce = session.awaitingPingNonce
        session.awaitingPingNonce = null
        if (status != BluetoothGatt.GATT_SUCCESS || nonce == null || !BleDirectProtocol.verifyAck(value, session.secret, nonce)) {
            failAndReconnect(session, "ACK Bluetooth غير صالح أو منقطع")
            return
        }
        val wasAuthenticated = session.authenticated
        session.authenticated = true
        session.reconnectAttempt = 0
        session.lastAckAt = System.currentTimeMillis()
        diagnosticStates[session.employeeId] = "ACK ✓ • connected • auto-reconnect armed"
        onState(session.employeeId, true, "✓ Bluetooth مباشر ثابت • Heartbeat/ACK مؤكد")
        enqueue(session, Operation(BleDirectProtocol.encodeAckConfirm(session.secret, nonce), Kind.ACK_CONFIRM))
        if (!wasAuthenticated) enqueue(session, Operation(BleDirectProtocol.encodeConfig(session.config), Kind.CONFIG))
        drain(session)
    }

    private fun enqueueHeartbeat(session: Session) {
        if (!session.transportConnected || session.command == null || session.readingAck || session.writing) return
        val nonce = random.nextInt()
        session.lastHeartbeatSentAt = System.currentTimeMillis()
        enqueue(session, Operation(BleDirectProtocol.encodePing(session.secret, nonce = nonce), Kind.HEARTBEAT, nonce))
    }

    private fun enqueue(session: Session, op: Operation) {
        synchronized(session) {
            if (op.kind == Kind.HEARTBEAT && session.queue.any { it.kind == Kind.HEARTBEAT }) return
            session.queue.addLast(op)
        }
        drain(session)
    }

    @SuppressLint("MissingPermission")
    private fun drain(session: Session) {
        val c = session.command ?: return
        val gatt = session.gatt ?: return
        val op: Operation = synchronized(session) {
            if (session.writing || session.readingAck || session.queue.isEmpty()) return
            if (!session.authenticated && session.queue.first().kind != Kind.HEARTBEAT) {
                val pendingHeartbeat = session.queue.firstOrNull { it.kind == Kind.HEARTBEAT }
                if (pendingHeartbeat != null) {
                    session.queue.remove(pendingHeartbeat)
                    session.queue.addFirst(pendingHeartbeat)
                } else {
                    val nonce = random.nextInt()
                    session.lastHeartbeatSentAt = System.currentTimeMillis()
                    session.queue.addFirst(Operation(BleDirectProtocol.encodePing(session.secret, nonce = nonce), Kind.HEARTBEAT, nonce))
                }
            }
            session.queue.removeFirst().also {
                session.activeOperation = it
                session.writing = true
            }
        }

        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(c, op.bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION") c.value = op.bytes
                @Suppress("DEPRECATION") gatt.writeCharacteristic(c)
            }
        }.getOrDefault(false)
        if (!started) {
            synchronized(session) {
                session.writing = false
                session.activeOperation = null
            }
            if (op.kind == Kind.HEARTBEAT) failAndReconnect(session, "تعذر بدء Heartbeat Bluetooth")
            else onState(session.employeeId, isConnected(session.employeeId), "تعذر بدء إرسال Bluetooth مباشر")
        }
    }

    private fun failAndReconnect(session: Session, message: String) {
        if (sessions[session.employeeId] !== session) return
        closeTransport(session)
        scheduleReconnect(session, message)
    }

    private fun scheduleReconnect(session: Session, reason: String, immediate: Boolean = false) {
        if (sessions[session.employeeId] !== session || session.reconnectScheduled || !hasPermission()) return
        val now = System.currentTimeMillis()
        if (now - session.lastAdvertisementAt > ADVERTISEMENT_RECONNECT_WINDOW_MILLIS) {
            diagnosticStates[session.employeeId] = "$reason • بانتظار إعادة اكتشاف الإعلان"
            onState(session.employeeId, false, "$reason — سيعود الاتصال تلقائيًا عند ظهور الهاتف")
            return
        }
        session.reconnectAttempt = (session.reconnectAttempt + 1).coerceAtMost(99)
        val index = (session.reconnectAttempt - 1).coerceIn(0, RECONNECT_BACKOFF_MILLIS.lastIndex)
        val delay = if (immediate) 250L else RECONNECT_BACKOFF_MILLIS[index]
        session.reconnectScheduled = true
        diagnosticStates[session.employeeId] = "reconnect ${session.reconnectAttempt} • ${delay}ms"
        onState(session.employeeId, false, "$reason — إعادة اتصال تلقائي ${session.reconnectAttempt} خلال ${String.format("%.1f", delay / 1000.0)}ث")
        handler.postDelayed({
            if (sessions[session.employeeId] !== session) return@postDelayed
            session.reconnectScheduled = false
            if (session.gatt == null && System.currentTimeMillis() - session.lastAdvertisementAt <= ADVERTISEMENT_RECONNECT_WINDOW_MILLIS) {
                connectSession(session, "إعادة اتصال Bluetooth تلقائي")
            }
        }, delay)
    }

    @SuppressLint("MissingPermission")
    private fun closeTransport(session: Session) {
        session.generation += 1 // immediately invalidates callbacks from the old Android GATT object
        session.authenticated = false
        session.transportConnected = false
        session.command = null
        session.writing = false
        session.readingAck = false
        session.activeOperation = null
        session.awaitingPingNonce = null
        synchronized(session) { session.queue.clear() }
        val old = session.gatt
        session.gatt = null
        runCatching { old?.disconnect() }
        runCatching { old?.close() }
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MILLIS = 4_000L
        private const val HEARTBEAT_TICK_MILLIS = 2_000L
        private const val ACK_STALE_MILLIS = 12_000L
        private const val CONNECT_TIMEOUT_MILLIS = 10_000L
        private const val SERVICE_TIMEOUT_MILLIS = 8_000L
        private const val ADVERTISEMENT_RECONNECT_WINDOW_MILLIS = 35_000L
        private val RECONNECT_BACKOFF_MILLIS = longArrayOf(1_500L, 3_000L, 5_000L, 8_000L, 12_000L)
    }
}
