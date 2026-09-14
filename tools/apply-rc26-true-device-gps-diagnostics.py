#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
store_p = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
service_p = ROOT / 'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'
emp_ui_p = ROOT / 'buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt'

store = store_p.read_text(encoding='utf-8')
service = service_p.read_text(encoding='utf-8')
emp_ui = emp_ui_p.read_text(encoding='utf-8')

# RC26: Server reachability is transport diagnostics only. It must never be counted as
# a physically connected employee device. Physical recognition = authenticated BLE/LAN
# or a fresh GPS INSIDE/NEAR observation coming from that employee phone.
old_poll = '''                    if (link.linked && seenAt > 0L && System.currentTimeMillis() - seenAt <= 30_000L) {
                        serverPresenceAt[employee.employeeId] = seenAt
                        val previous = nearby[employee.employeeId]
                        val channels = previous?.channelTimes.orEmpty().toMutableMap().apply { put("Server • Heartbeat", seenAt) }
                        nearby[employee.employeeId] = NearbyPhone(
                            maxOf(previous?.seenAt ?: 0L, seenAt),
                            previous?.rssi ?: -127, channels, previous?.deviceName.orEmpty(), previous?.gpsInsideAt ?: 0L
                        )
                        repo.markCompanionLinked(employee.employeeId, "Server • Heartbeat", seenAt)
                    } else if (seenAt <= 0L || System.currentTimeMillis() - seenAt > 45_000L) {
'''
new_poll = '''                    if (link.linked && seenAt > 0L && System.currentTimeMillis() - seenAt <= 30_000L) {
                        // RC26: a server heartbeat proves only that the employee app reached the server.
                        // Do not promote it into nearby/connected-device state.
                        serverPresenceAt[employee.employeeId] = seenAt
                    } else if (seenAt <= 0L || System.currentTimeMillis() - seenAt > 45_000L) {
'''
if old_poll not in store:
    raise SystemExit('RC26: store server-heartbeat promotion anchor not found')
store = store.replace(old_poll, new_poll, 1)

old_actual = '''    private fun isEmployeeActuallyConnected(employeeId: String, now: Long = System.currentTimeMillis()): Boolean =
        isAuthenticatedPresenceConnected(employeeId, now) || isDirectBleUiConnected(employeeId, now) ||
            isLanConnected(employeeId, now) || isServerPresenceConnected(employeeId, now) ||
            isGpsRecognizedFresh(employeeId, now)
'''
new_actual = '''    private fun isEmployeeActuallyConnected(employeeId: String, now: Long = System.currentTimeMillis()): Boolean =
        isAuthenticatedPresenceConnected(employeeId, now) || isDirectBleUiConnected(employeeId, now) ||
            isLanConnected(employeeId, now) || isGpsRecognizedFresh(employeeId, now)
'''
if old_actual not in store:
    raise SystemExit('RC26: actual-connection predicate anchor not found')
store = store.replace(old_actual, new_actual, 1)

store = store.replace('''                    if (isServerPresenceConnected(id, now)) add("Server • Heartbeat")
''', '', 1)

old_gps_fresh = '''        return (state == "INSIDE" || state == "NEAR") && now - seen <= GPS_RECOGNITION_FRESH_MILLIS
'''
new_gps_fresh = '''        val age = now - seen
        return (state == "INSIDE" || state == "NEAR") && age in 0..GPS_RECOGNITION_FRESH_MILLIS
'''
if old_gps_fresh in store:
    store = store.replace(old_gps_fresh, new_gps_fresh, 1)

# Make linked-employee details explicit: server heartbeat is not physical connectivity.
store = store.replace('''                isServerPresenceConnected(e.employeeId, now) -> "Server Heartbeat"
                isGpsRecognizedFresh(e.employeeId, now) -> "GPS رصد فقط"
''', '''                isGpsRecognizedFresh(e.employeeId, now) -> "GPS مؤكد من هاتف الموظف"
                isServerPresenceConnected(e.employeeId, now) -> "الخادم فقط — لا يُعد جهازًا متصلًا"
''', 1)

# Employee service: persist end-to-end GPS diagnostics so the UI can tell exactly which stage failed.
old_sync_success = '''            result.onSuccess { cfg ->
                if (cfg.available && cfg.revision >= lastGpsConfigRevisionRc25) {
                    lastGpsConfigRevisionRc25 = cfg.revision
'''
new_sync_success = '''            result.onSuccess { cfg ->
                getSharedPreferences("gps_rc26_diag", MODE_PRIVATE).edit()
                    .putLong("config_pull_at", System.currentTimeMillis())
                    .putBoolean("config_available", cfg.available)
                    .putBoolean("config_enabled", cfg.enabled)
                    .putLong("config_revision", cfg.revision)
                    .putString("config_error", "")
                    .apply()
                if (cfg.available && cfg.revision >= lastGpsConfigRevisionRc25) {
                    lastGpsConfigRevisionRc25 = cfg.revision
'''
if old_sync_success not in service:
    raise SystemExit('RC26: GPS config success anchor not found after RC25')
service = service.replace(old_sync_success, new_sync_success, 1)

old_sync_end = '''            }
            gpsConfigPollInFlightRc25 = false
        }.apply { isDaemon = true }.start()
    }

    private fun ensurePresenceChannels() {
'''
new_sync_end = '''            }.onFailure { error ->
                getSharedPreferences("gps_rc26_diag", MODE_PRIVATE).edit()
                    .putLong("config_pull_at", System.currentTimeMillis())
                    .putString("config_error", error.message ?: "تعذر سحب إعداد GPS")
                    .apply()
            }
            gpsConfigPollInFlightRc25 = false
        }.apply { isDaemon = true }.start()
    }

    private fun ensurePresenceChannels() {
'''
if old_sync_end not in service:
    raise SystemExit('RC26: GPS config completion anchor not found after RC25')
service = service.replace(old_sync_end, new_sync_end, 1)

old_observe = '''        identity.lastGpsAccuracyMeters = accuracy
        if (!identity.isConfigured || identity.serverUrl.isBlank()) return
'''
new_observe = '''        identity.lastGpsAccuracyMeters = accuracy
        getSharedPreferences("gps_rc26_diag", MODE_PRIVATE).edit()
            .putLong("observation_at", observedAt)
            .putString("observation_state", state)
            .putInt("observation_distance", distance)
            .putInt("observation_accuracy", accuracy)
            .apply()
        if (!identity.isConfigured || identity.serverUrl.isBlank()) return
'''
if old_observe not in service:
    raise SystemExit('RC26: GPS observation diagnostic anchor not found')
service = service.replace(old_observe, new_observe, 1)

old_upload = '''            ).onSuccess { identity.serverLinked = true }.onFailure {
                // Do not mark the employee server link dead because a geo telemetry write failed.
                // Challenge polling/registration remains the authoritative server connection.
            }
'''
new_upload = '''            ).onSuccess {
                identity.serverLinked = true
                getSharedPreferences("gps_rc26_diag", MODE_PRIVATE).edit()
                    .putLong("upload_ok_at", System.currentTimeMillis())
                    .putString("upload_state", state)
                    .putString("upload_error", "")
                    .apply()
            }.onFailure { error ->
                getSharedPreferences("gps_rc26_diag", MODE_PRIVATE).edit()
                    .putLong("upload_fail_at", System.currentTimeMillis())
                    .putString("upload_error", error.message ?: "فشل رفع قراءة GPS")
                    .apply()
                // Do not mark the employee server link dead because a geo telemetry write failed.
                // Challenge polling/registration remains the authoritative server connection.
            }
'''
if old_upload not in service:
    raise SystemExit('RC26: GPS upload diagnostic anchor not found')
service = service.replace(old_upload, new_upload, 1)

# Employee UI: server reachability is displayed separately, while GPS INSIDE/NEAR can be the
# actual recognition channel. Add exact cloud-config / observation / upload pipeline diagnostics.
old_primary = '''        val serverFresh = ServerDiagnostics.snapshot().isFresh(now)
        val primary = when {
            directAge < 12_000L -> "🟢 Bluetooth مباشر • ACK ${directAge / 1000}ث"
            lanAge < 8_000L -> "🟢 Wi‑Fi/Hotspot • ACK ${lanAge / 1000}ث"
            serverFresh -> "🟢 الخادم • اتصال HTTP حديث"
            else -> "⚪ بانتظار اتصال مباشر"
        }
        val gpsAge = identity.lastGpsObservedAt.takeIf { it > 0L }?.let { now - it } ?: Long.MAX_VALUE
'''
new_primary = '''        val serverFresh = ServerDiagnostics.snapshot().isFresh(now)
        val gpsAge = identity.lastGpsObservedAt.takeIf { it > 0L }?.let { now - it } ?: Long.MAX_VALUE
        val gpsRecognized = gpsAge in 0 until 5 * 60_000L &&
            (identity.lastGpsState == OfflineGeoMonitor.STATE_INSIDE || identity.lastGpsState == OfflineGeoMonitor.STATE_NEAR)
        val primary = when {
            directAge < 12_000L -> "🟢 Bluetooth مباشر • ACK ${directAge / 1000}ث"
            lanAge < 8_000L -> "🟢 Wi‑Fi/Hotspot • ACK ${lanAge / 1000}ث"
            gpsRecognized -> "🟢 GPS • تعرّف فعلي من هذا الهاتف"
            else -> "⚪ لا يوجد اتصال/تعرّف جهاز مؤكد"
        }
'''
if old_primary not in emp_ui:
    raise SystemExit('RC26: employee connection summary anchor not found')
emp_ui = emp_ui.replace(old_primary, new_primary, 1)

old_summary = '''        connectionSummary.text = "$primary\\n$gps"
'''
new_summary = '''        val serverLine = if (serverFresh) "☁ الخادم متاح • لا يُحسب كاتصال جهاز" else "☁ الخادم غير مؤكد الآن"
        connectionSummary.text = "$primary\\n$gps\\n$serverLine"
'''
if old_summary not in emp_ui:
    raise SystemExit('RC26: employee summary text anchor not found')
emp_ui = emp_ui.replace(old_summary, new_summary, 1)

old_diag_tail = '''            appendLine("Server: $serverState")
            appendLine("GPS: $gpsState")
            appendLine("آخر حالة BLE: ${identity.lastBleDirectState.ifBlank { "لا توجد" }}")
'''
new_diag_tail = '''            val gpsDiag = getSharedPreferences("gps_rc26_diag", MODE_PRIVATE)
            val cfgAt = gpsDiag.getLong("config_pull_at", 0L)
            val cfgAvailable = gpsDiag.getBoolean("config_available", false)
            val cfgEnabled = gpsDiag.getBoolean("config_enabled", false)
            val cfgRevision = gpsDiag.getLong("config_revision", 0L)
            val cfgError = gpsDiag.getString("config_error", "").orEmpty()
            val obsAt = gpsDiag.getLong("observation_at", 0L)
            val obsState = gpsDiag.getString("observation_state", "").orEmpty()
            val uploadOkAt = gpsDiag.getLong("upload_ok_at", 0L)
            val uploadFailAt = gpsDiag.getLong("upload_fail_at", 0L)
            val uploadError = gpsDiag.getString("upload_error", "").orEmpty()
            appendLine("Server: $serverState")
            appendLine("GPS: $gpsState")
            appendLine("GPS Config: ${if (cfgAvailable && cfgEnabled) "وصل من الخادم ✓ • revision=$cfgRevision" else if (cfgAt > 0L) "لم يصل إعداد فعال${if (cfgError.isNotBlank()) " • $cfgError" else ""}" else "لم تتم مزامنة الإعداد بعد"}")
            appendLine("GPS Observation: ${if (obsAt > 0L) "$obsState • منذ ${(now-obsAt).coerceAtLeast(0L)/1000}ث" else "لا توجد قراءة من حساس الموقع"}")
            appendLine("GPS Upload: ${if (uploadOkAt > 0L && uploadOkAt >= uploadFailAt) "وصل للخادم ✓ • منذ ${(now-uploadOkAt).coerceAtLeast(0L)/1000}ث" else if (uploadFailAt > 0L) "فشل • ${uploadError.ifBlank { "خطأ غير محدد" }}" else "لم تُرفع قراءة بعد"}")
            appendLine("آخر حالة BLE: ${identity.lastBleDirectState.ifBlank { "لا توجد" }}")
'''
if old_diag_tail not in emp_ui:
    raise SystemExit('RC26: employee diagnostic text anchor not found')
emp_ui = emp_ui.replace(old_diag_tail, new_diag_tail, 1)

store_p.write_text(store, encoding='utf-8')
service_p.write_text(service, encoding='utf-8')
emp_ui_p.write_text(emp_ui, encoding='utf-8')

# Static assertions for the exact bug reported from the real device screenshot.
s = store_p.read_text(encoding='utf-8')
e = service_p.read_text(encoding='utf-8')
u = emp_ui_p.read_text(encoding='utf-8')
assert 'isLanConnected(employeeId, now) || isGpsRecognizedFresh(employeeId, now)' in s
assert 'isLanConnected(employeeId, now) || isServerPresenceConnected' not in s
assert 'Do not promote it into nearby/connected-device state' in s
assert 'GPS Config:' in u and 'GPS Observation:' in u and 'GPS Upload:' in u
assert 'الخادم متاح • لا يُحسب كاتصال جهاز' in u
assert 'gps_rc26_diag' in e
print('RC26 applied: true employee-device count + independent GPS pipeline diagnostics')
