#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')

def rep(path, old, new, count=1):
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'missing token in {path}: {old[:160]}')
    p.write_text(s.replace(old, new, count), encoding='utf-8')

presence = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'
store = 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
replies = 'buildsrc/store-app/src/main/java/com/attendpro/store/StoreLocalReplyStore1977.kt'

# Employee: send already-computed GPS observation over the existing authenticated local reply path.
rep(presence,
'''            onObservation = { state, distance, accuracy, observedAt ->
                network.updateGeoObservation(state, distance, accuracy, observedAt)
                maybeUploadGpsObservation(state, distance, accuracy, observedAt)
            },
''',
'''            onObservation = { state, distance, accuracy, observedAt ->
                network.updateGeoObservation(state, distance, accuracy, observedAt)
                if (EmployeeDirectReplyBridge1977.isAvailable()) {
                    EmployeeDirectReplyBridge1977.queueReply("GPS", "__APGPS1|$state|$distance|$accuracy|$observedAt")
                }
                maybeUploadGpsObservation(state, distance, accuracy, observedAt)
            },
''')

# Store local reply receiver: authenticated GPS telemetry is persisted separately and never shown as a chat message.
rep(replies,
'''    private val repository = StoreRepository(context)
    private val store = StoreLocalReplyStore1977(context)
    private val pending = mutableMapOf<String, Pending>()
''',
'''    private val repository = StoreRepository(context)
    private val store = StoreLocalReplyStore1977(context)
    private val gpsPrefs = context.getSharedPreferences("attend_ble_gps_telemetry", Context.MODE_PRIVATE)
    private val pending = mutableMapOf<String, Pending>()
''')
rep(replies,
'''        val decoded = BleLocalMessageProtocol1977.decodeCompleted(fragments) ?: return AcceptResult(false)
        val digestBytes = MessageDigest.getInstance("SHA-256")
''',
'''        val decoded = BleLocalMessageProtocol1977.decodeCompleted(fragments) ?: return AcceptResult(false)
        if (decoded.body.startsWith("__APGPS1|")) {
            val fields = decoded.body.split('|')
            if (fields.size == 5) {
                val state = fields[1].uppercase().let { if (it in setOf("INSIDE", "NEAR", "OUTSIDE", "UNKNOWN")) it else "UNKNOWN" }
                val distance = fields[2].toIntOrNull() ?: -1
                val accuracy = fields[3].toIntOrNull() ?: -1
                val observedAt = fields[4].toLongOrNull() ?: 0L
                if (observedAt > 0L && kotlin.math.abs(now - observedAt) <= 120_000L) {
                    val prefix = employeeId + "_"
                    gpsPrefs.edit()
                        .putString(prefix + "state", state)
                        .putInt(prefix + "distance", distance)
                        .putInt(prefix + "accuracy", accuracy)
                        .putLong(prefix + "seen", observedAt)
                        .apply()
                    return AcceptResult(true)
                }
            }
            return AcceptResult(false)
        }
        val digestBytes = MessageDigest.getInstance("SHA-256")
''')

# Store activity: apply recent BLE GPS telemetry to the same GPS presence maps used by LAN/server.
helper = '''    private fun applyBluetoothGpsTelemetryRc14() {
        if (!repo.gpsRecognitionEnabled) return
        val prefs = getSharedPreferences("attend_ble_gps_telemetry", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        repo.employees().filter { it.active && it.companionEnabled }.forEach { employee ->
            val prefix = employee.employeeId + "_"
            val seenAt = prefs.getLong(prefix + "seen", 0L)
            if (seenAt <= 0L || now - seenAt > 120_000L) return@forEach
            val state = prefs.getString(prefix + "state", "UNKNOWN") ?: "UNKNOWN"
            val distance = prefs.getInt(prefix + "distance", -1)
            val accuracy = prefs.getInt(prefix + "accuracy", -1)
            applyGpsObservation(employee, state, distance, accuracy, seenAt, "Bluetooth GPS")
        }
    }

'''
rep(store, '    private fun pollServerPresenceIfDue() {\n', helper + '    private fun pollServerPresenceIfDue() {\n')
rep(store,
'''            pollServerPresenceIfDue()
            syncRemoteControlIfDue()
''',
'''            applyBluetoothGpsTelemetryRc14()
            pollServerPresenceIfDue()
            syncRemoteControlIfDue()
''')

assert '__APGPS1|' in (ROOT/presence).read_text(encoding='utf-8')
assert 'attend_ble_gps_telemetry' in (ROOT/replies).read_text(encoding='utf-8')
assert 'Bluetooth GPS' in (ROOT/store).read_text(encoding='utf-8')
print('RC14 authenticated BLE GPS telemetry applied')
