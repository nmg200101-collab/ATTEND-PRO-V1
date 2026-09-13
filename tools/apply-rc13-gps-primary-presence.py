#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')

def rep(path, old, new, count=1):
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'missing token in {path}: {old[:180]}')
    p.write_text(s.replace(old, new, count), encoding='utf-8')

# RC13 is applied after RC8 + RC11 + RC12. Bluetooth RC12 sources are intentionally untouched.
for path in ['buildsrc/store-app/build.gradle.kts','buildsrc/employee-app/build.gradle.kts']:
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    s = s.replace('versionCode = 102', 'versionCode = 103')
    s = s.replace('versionName = "2.0.0-RC12"', 'versionName = "2.0.0-RC13"')
    p.write_text(s, encoding='utf-8')

# -----------------------------------------------------------------------------
# Employee LAN presence: piggy-back a signed GPS observation beside the normal
# presence heartbeat. This makes GPS local-first on Wi-Fi/Hotspot and independent
# of Internet/server availability. Bluetooth code is not changed.
# -----------------------------------------------------------------------------
broadcaster = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/NetworkPresenceBroadcaster.kt'
rep(broadcaster,
'''import com.attendpro.core.SecretCodec\n''',
'''import com.attendpro.core.SecretCodec\nimport com.attendpro.core.GeoPresenceProtocol\n''')
rep(broadcaster,
'''    @Volatile private var lastAckAt = 0L\n''',
'''    @Volatile private var lastAckAt = 0L\n    @Volatile private var geoState = "UNKNOWN"\n    @Volatile private var geoDistanceMeters = -1\n    @Volatile private var geoAccuracyMeters = -1\n    @Volatile private var geoObservedAt = 0L\n''')
rep(broadcaster,
'''                        broadcastAddresses().forEach { address ->\n                            runCatching { s.send(DatagramPacket(message, message.size, address, PORT)) }\n                        }\n\n                        var gotAck = false\n''',
'''                        val targets = broadcastAddresses()\n                        targets.forEach { address ->\n                            runCatching { s.send(DatagramPacket(message, message.size, address, PORT)) }\n                        }\n                        val geoFrame = currentGeoFrame()\n                        if (geoFrame != null) {\n                            targets.forEach { address ->\n                                runCatching { s.send(DatagramPacket(geoFrame, geoFrame.size, address, PORT)) }\n                            }\n                        }\n\n                        var gotAck = false\n''')
rep(broadcaster,
'''                                    runCatching { s.send(DatagramPacket(message, message.size, packet.address, packet.port)) }\n                                    continue\n''',
'''                                    runCatching { s.send(DatagramPacket(message, message.size, packet.address, packet.port)) }\n                                    currentGeoFrame()?.let { geo ->\n                                        runCatching { s.send(DatagramPacket(geo, geo.size, packet.address, packet.port)) }\n                                    }\n                                    continue\n''')
rep(broadcaster,
'''    fun markVerified(durationMillis: Long, proofType: Int) {\n''',
'''    fun updateGeoObservation(state: String, distanceMeters: Int, accuracyMeters: Int, observedAt: Long) {\n        geoState = state.uppercase().take(16)\n        geoDistanceMeters = distanceMeters\n        geoAccuracyMeters = accuracyMeters\n        geoObservedAt = observedAt.coerceAtLeast(0L)\n    }\n\n    private fun currentGeoFrame(now: Long = System.currentTimeMillis()): ByteArray? {\n        if (secret.isEmpty() || employeeId.isBlank() || geoObservedAt <= 0L || now - geoObservedAt > 120_000L) return null\n        return runCatching {\n            GeoPresenceProtocol.encode(employeeId, secret, geoState, geoDistanceMeters, geoAccuracyMeters, geoObservedAt)\n        }.getOrNull()\n    }\n\n    fun markVerified(durationMillis: Long, proofType: Int) {\n''')

# PresenceService feeds every GPS observation into the local broadcaster before server sync.
presence = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'
rep(presence,
'''            onObservation = { state, distance, accuracy, observedAt ->\n                maybeUploadGpsObservation(state, distance, accuracy, observedAt)\n            },\n''',
'''            onObservation = { state, distance, accuracy, observedAt ->\n                network.updateGeoObservation(state, distance, accuracy, observedAt)\n                maybeUploadGpsObservation(state, distance, accuracy, observedAt)\n            },\n''')

# -----------------------------------------------------------------------------
# Store LAN listener: recognize signed GPS telemetry as its own frame type.
# -----------------------------------------------------------------------------
listener = 'buildsrc/store-app/src/main/java/com/attendpro/store/NetworkPresenceListener.kt'
rep(listener,
'''import com.attendpro.core.LanConfirmProtocol\n''',
'''import com.attendpro.core.LanConfirmProtocol\nimport com.attendpro.core.GeoPresenceProtocol\n''')
rep(listener,
'''    private val onPayload: (BleProtocol.Payload, Int) -> ByteArray?,\n    private val onConfirm: (ByteArray, Int) -> Unit,\n    private val onStatus: (String) -> Unit\n''',
'''    private val onPayload: (BleProtocol.Payload, Int) -> ByteArray?,\n    private val onConfirm: (ByteArray, Int) -> Unit,\n    private val onGeoFrame: (ByteArray) -> Unit,\n    private val onStatus: (String) -> Unit\n''')
rep(listener,
'''                            if (LanConfirmProtocol.looksLike(full)) {\n                                onConfirm(full, -45)\n                                continue\n                            }\n''',
'''                            if (LanConfirmProtocol.looksLike(full)) {\n                                onConfirm(full, -45)\n                                continue\n                            }\n                            if (GeoPresenceProtocol.looksLike(full)) {\n                                onGeoFrame(full)\n                                continue\n                            }\n''')

# -----------------------------------------------------------------------------
# Store UI/state: GPS becomes a first-class presence channel with exact meters,
# accuracy, freshness and transport source. It remains monitoring only, never proof.
# -----------------------------------------------------------------------------
main = 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
rep(main,
'''import com.attendpro.core.LanConfirmProtocol\n''',
'''import com.attendpro.core.LanConfirmProtocol\nimport com.attendpro.core.GeoPresenceProtocol\n''')
rep(main,
'''    private val serverGpsSeenAt = ConcurrentHashMap<String, Long>()\n''',
'''    private val serverGpsSeenAt = ConcurrentHashMap<String, Long>()\n    private val gpsObservationSource = ConcurrentHashMap<String, String>()\n''')
rep(main,
'''        networkListener = NetworkPresenceListener(\n            { payload, rssi -> handleBlePayload(payload, rssi, "Wi‑Fi/Hotspot") },\n            { frame, rssi -> handleLanConfirm(frame, rssi) }\n        ) { msg ->\n''',
'''        networkListener = NetworkPresenceListener(\n            { payload, rssi -> handleBlePayload(payload, rssi, "Wi‑Fi/Hotspot") },\n            { frame, rssi -> handleLanConfirm(frame, rssi) },\n            { frame -> handleLocalGpsFrame(frame) }\n        ) { msg ->\n''')

old_server = '''    private fun applyServerGpsObservation(employee: PairedEmployee, link: CentralServerClient.EmployeeLinkResult) {\n        if (!repo.gpsRecognitionEnabled || link.gpsSeenAt <= 0L) return\n        val state = link.gpsState.uppercase(Locale.US).let { if (it in setOf("INSIDE", "NEAR", "OUTSIDE", "UNKNOWN")) it else "UNKNOWN" }\n        val employeeId = employee.employeeId\n        serverGpsState[employeeId] = state\n        serverGpsDistance[employeeId] = link.gpsDistanceMeters\n        serverGpsAccuracy[employeeId] = link.gpsAccuracyMeters\n        serverGpsSeenAt[employeeId] = link.gpsSeenAt\n\n        val recognized = state == "INSIDE" || state == "NEAR"\n        val previous = nearby[employeeId]\n        if (recognized && System.currentTimeMillis() - link.gpsSeenAt <= GPS_RECOGNITION_FRESH_MILLIS) {\n            val channels = previous?.channelTimes.orEmpty().toMutableMap().apply { put("GPS • تعرّف", link.gpsSeenAt) }\n            nearby[employeeId] = NearbyPhone(\n                maxOf(previous?.seenAt ?: 0L, link.gpsSeenAt),\n                previous?.rssi ?: -127, channels, previous?.deviceName.orEmpty(), link.gpsSeenAt\n            )\n        }\n\n        val gpsPrefs = getSharedPreferences("gps_owner_recognition_state", MODE_PRIVATE)\n        val key = "state_$employeeId"\n        val oldState = gpsPrefs.getString(key, "UNKNOWN") ?: "UNKNOWN"\n        if (oldState != state) {\n            gpsPrefs.edit().putString(key, state).apply()\n            val detail = "${gpsStateLabel(state)} • ${gpsDistanceLabel(employeeId)}"\n            repo.addPresenceEvent(PresenceEvent(\n                employeeId = employeeId, employeeName = employee.displayName, timestampEpochMillis = link.gpsSeenAt,\n                channel = "GPS • مراقبة", rssi = -127, details = "$detail • ليس إثبات حضور"\n            ))\n            if (recognized && repo.gpsNotifyOwnerEnabled) notifyOwnerGpsRecognition(employee, detail)\n        }\n    }\n'''
new_server = '''    private fun applyServerGpsObservation(employee: PairedEmployee, link: CentralServerClient.EmployeeLinkResult) {\n        applyGpsObservation(\n            employee, link.gpsState, link.gpsDistanceMeters, link.gpsAccuracyMeters, link.gpsSeenAt, "Server"\n        )\n    }\n\n    private fun handleLocalGpsFrame(frame: ByteArray) {\n        val employeeId = GeoPresenceProtocol.peekEmployeeId(frame) ?: return\n        val employee = findActiveEmployee(employeeId)?.takeIf { it.companionEnabled } ?: return\n        val secret = SecretCodec.decode(employee.pairingSecret) ?: return\n        val observation = GeoPresenceProtocol.decode(frame, employee.employeeId, secret) ?: return\n        val now = System.currentTimeMillis()\n        if (observation.observedAt <= 0L || now - observation.observedAt > 120_000L) return\n        runOnUiThread {\n            applyGpsObservation(\n                employee, observation.state, observation.distanceMeters, observation.accuracyMeters,\n                observation.observedAt, "LAN محلي"\n            )\n            refreshDashboard()\n        }\n    }\n\n    private fun applyGpsObservation(\n        employee: PairedEmployee, rawState: String, distanceMeters: Int, accuracyMeters: Int,\n        seenAt: Long, source: String\n    ) {\n        if (!repo.gpsRecognitionEnabled || seenAt <= 0L) return\n        val state = rawState.uppercase(Locale.US).let { if (it in setOf("INSIDE", "NEAR", "OUTSIDE", "UNKNOWN")) it else "UNKNOWN" }\n        val employeeId = employee.employeeId\n        val previousSeen = serverGpsSeenAt[employeeId] ?: 0L\n        if (seenAt < previousSeen - 2_000L) return\n        serverGpsState[employeeId] = state\n        serverGpsDistance[employeeId] = distanceMeters\n        serverGpsAccuracy[employeeId] = accuracyMeters\n        serverGpsSeenAt[employeeId] = seenAt\n        gpsObservationSource[employeeId] = source\n\n        val recognized = state == "INSIDE" || state == "NEAR"\n        val previous = nearby[employeeId]\n        if (recognized && System.currentTimeMillis() - seenAt <= GPS_RECOGNITION_FRESH_MILLIS) {\n            val channels = previous?.channelTimes.orEmpty().toMutableMap().apply { put("GPS • $source", seenAt) }\n            nearby[employeeId] = NearbyPhone(\n                maxOf(previous?.seenAt ?: 0L, seenAt), previous?.rssi ?: -127, channels,\n                previous?.deviceName.orEmpty(), seenAt\n            )\n            repo.markCompanionLinked(employeeId, "GPS • $source", seenAt)\n        }\n\n        val gpsPrefs = getSharedPreferences("gps_owner_recognition_state", MODE_PRIVATE)\n        val key = "state_$employeeId"\n        val oldState = gpsPrefs.getString(key, "UNKNOWN") ?: "UNKNOWN"\n        if (oldState != state) {\n            gpsPrefs.edit().putString(key, state).apply()\n            val detail = "${gpsStateLabel(state)} • ${gpsDistanceLabel(employeeId)} • $source"\n            repo.addPresenceEvent(PresenceEvent(\n                employeeId = employeeId, employeeName = employee.displayName, timestampEpochMillis = seenAt,\n                channel = "GPS • $source", rssi = -127, details = "$detail • ليس إثبات حضور"\n            ))\n            if (recognized && repo.gpsNotifyOwnerEnabled) notifyOwnerGpsRecognition(employee, detail)\n        }\n    }\n'''
rep(main, old_server, new_server)
rep(main,
'''        return "GPS: ${gpsStateLabel(state)} • ${gpsDistanceLabel(employeeId)} • منذ ${age}ث • مراقبة فقط"\n''',
'''        val source = gpsObservationSource[employeeId]?.let { " • المصدر $it" }.orEmpty()\n        return "GPS: ${gpsStateLabel(state)} • ${gpsDistanceLabel(employeeId)} • منذ ${age}ث$source • قناة اتصال أساسية للرصد"\n''')
rep(main,
'''        val gpsText = if (repo.gpsRecognitionEnabled && repo.isGpsConfigured) "مفعّل للتعرّف على وجود هاتف الموظف • نطاق ${repo.gpsRadiusMeters}م • لا يسجل الحضور وحده" else "غير مهيأ"\n''',
'''        val gpsText = if (repo.gpsRecognitionEnabled && repo.isGpsConfigured) "قناة أساسية مفعّلة • نطاق الاتصال ${repo.gpsRadiusMeters}م • تعرض المسافة والدقة والمصدر • لا تسجل الحضور وحدها" else "غير مهيأ"\n''')

# Visible version markers only. Preserve all current layouts and owner settings.
emp_main = ROOT / 'buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt'
s = emp_main.read_text(encoding='utf-8')
s = s.replace('RC12 • استعادة فورية Bluetooth/GPS بعد الفصل', 'RC13 • Bluetooth RC12 محمي + GPS أساسي بالمتر')
emp_main.write_text(s, encoding='utf-8')

store_main = ROOT / main
s = store_main.read_text(encoding='utf-8')
s = s.replace('RC12 • استعادة فورية Bluetooth/GPS بعد الفصل', 'RC13 • Bluetooth RC12 محمي + GPS أساسي بالمتر')
s = s.replace('RC12 • لوحة الأقسام', 'RC13 • لوحة الأقسام')
s = s.replace('RC12 • Bluetooth radio recovery + GPS fast fix', 'RC13 • GPS primary local-first presence')
store_main.write_text(s, encoding='utf-8')

Path('RELEASE_NOTES_V2.0.0_RC13.md').write_text('''# ATTEND-PRO 2.0.0-RC13\n\nversionCode 103\n\n- RC12 Bluetooth behavior is frozen and preserved byte-for-byte across this GPS update.\n- GPS is promoted to a primary phone-presence channel for recognition, while remaining non-attendance proof.\n- Exact distance in meters and accuracy are retained and shown in Store status.\n- GPS observations are signed with the employee pairing secret and delivered locally over Wi-Fi/Hotspot without Internet.\n- Server GPS remains a fallback/sync path.\n- Store shows the GPS source (LAN local or Server), freshness, state, distance and accuracy.\n- GPS inside/near range contributes to connected-phone recognition.\n''', encoding='utf-8')
Path('FIELD_TEST_CHECKLIST_RC13.md').write_text('''# RC13 GPS primary presence field test\n1. Confirm Bluetooth still behaves exactly like approved RC12, including repeated OFF -> ON recovery.\n2. Configure Store GPS coordinates and radius, e.g. 50m.\n3. Employee grants Location and keeps Location enabled.\n4. On the same Wi-Fi/Hotspot, GPS must appear in Store without Internet, with state, distance in meters, accuracy and source LAN local.\n5. With Internet available, Server may also update GPS; newest authenticated observation wins.\n6. Walk outside the configured radius: distance must increase and state become OUTSIDE; inside/near must count as phone presence.\n7. GPS never records attendance by itself.\n''', encoding='utf-8')

assert 'versionCode = 103' in (ROOT/'buildsrc/store-app/build.gradle.kts').read_text()
assert 'GeoPresenceProtocol' in (ROOT/broadcaster).read_text()
assert 'updateGeoObservation' in (ROOT/broadcaster).read_text()
assert 'GeoPresenceProtocol.looksLike' in (ROOT/listener).read_text()
assert 'handleLocalGpsFrame' in (ROOT/main).read_text()
assert 'قناة اتصال أساسية للرصد' in (ROOT/main).read_text()
print('RC13 GPS primary/local-first presence applied; RC12 Bluetooth untouched')
