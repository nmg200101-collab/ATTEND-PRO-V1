#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
client_p = ROOT / 'buildsrc/core/src/main/java/com/attendpro/core/CentralServerClient.kt'
store_p = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
emp_p = ROOT / 'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'

client = client_p.read_text(encoding='utf-8')
store = store_p.read_text(encoding='utf-8')
emp = emp_p.read_text(encoding='utf-8')

# RC25 rebuild principle:
# GPS configuration and telemetry must work end-to-end with Bluetooth OFF.
# Do not touch pairing protocol files or RC12 Bluetooth runtime files.

# -----------------------------------------------------------------------------
# CentralServerClient: Store publishes GPS zone; Employee pulls it using the
# already-linked employee credentials. This removes the hidden BLE CONFIG dependency.
# -----------------------------------------------------------------------------
data_anchor = '    data class EmployeePairingTicket(val code: String, val expiresAt: Long, val transportText: String)\n'
if 'data class StoreGpsConfigRc25' not in client:
    if data_anchor not in client:
        raise SystemExit('RC25: CentralServerClient data anchor not found')
    client = client.replace(data_anchor, '''    data class StoreGpsConfigRc25(
        val available: Boolean,
        val enabled: Boolean,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Int,
        val revision: Long,
        val updatedAt: Long
    )
''' + data_anchor, 1)

method_anchor = '    fun createEmployeePairingTicket(serverUrl: String, storeToken: String, storeId: String, identity: DeviceIdentity,\n'
if 'fun publishStoreGpsConfigRc25(' not in client:
    methods = '''    fun publishStoreGpsConfigRc25(
        serverUrl: String,
        storeToken: String,
        storeId: String,
        identity: DeviceIdentity,
        enabled: Boolean,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        revision: Long
    ): Result<Long> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/store/gps-config", "POST", JSONObject().apply {
            put("enabled", enabled)
            if (enabled) {
                put("latitude", latitude)
                put("longitude", longitude)
            }
            put("radiusMeters", radiusMeters.coerceIn(10, 5000))
            put("revision", revision.coerceAtLeast(0L))
        }, bearer = storeToken, deviceIdentity = identity, storeId = storeId)
        o.optLong("revision", revision)
    }

    fun pullEmployeeGpsConfigRc25(
        serverUrl: String,
        storeId: String,
        employeeId: String,
        pairingSecret: String,
        installationId: String
    ): Result<StoreGpsConfigRc25> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/employee/gps-config", "POST", JSONObject().apply {
            put("storeId", storeId)
            put("employeeId", employeeId)
            put("pairingSecret", pairingSecret)
            put("installationId", installationId)
        })
        StoreGpsConfigRc25(
            available = o.optBoolean("available", false),
            enabled = o.optBoolean("enabled", false),
            latitude = if (o.isNull("latitude")) Double.NaN else o.optDouble("latitude", Double.NaN),
            longitude = if (o.isNull("longitude")) Double.NaN else o.optDouble("longitude", Double.NaN),
            radiusMeters = o.optInt("radiusMeters", 25).coerceIn(10, 5000),
            revision = o.optLong("revision", 0L),
            updatedAt = o.optLong("updatedAt", 0L)
        )
    }

'''
    if method_anchor not in client:
        raise SystemExit('RC25: CentralServerClient method anchor not found')
    client = client.replace(method_anchor, methods + method_anchor, 1)

# -----------------------------------------------------------------------------
# Store: automatically publish the current local GPS zone to server on heartbeat.
# Existing RC24 installs therefore need no manual re-save of GPS settings.
# -----------------------------------------------------------------------------
store_field_anchor = '    private var lastServerPresencePollAt = 0L\n'
if 'gpsConfigPushInFlightRc25' not in store:
    if store_field_anchor not in store:
        raise SystemExit('RC25: Store field anchor not found')
    store = store.replace(store_field_anchor, store_field_anchor + '''    @Volatile private var gpsConfigPushInFlightRc25 = false
    private var lastGpsConfigPushAtRc25 = 0L
    private var lastGpsConfigRevisionRc25 = 0L
''', 1)

store_tick_anchor = '            pollServerPresenceIfDue()\n'
if 'publishGpsConfigIfDueRc25()' not in store:
    if store_tick_anchor not in store:
        raise SystemExit('RC25: Store heartbeat anchor not found')
    store = store.replace(store_tick_anchor, store_tick_anchor + '            publishGpsConfigIfDueRc25()\n', 1)

store_method_anchor = '    private fun pollServerPresenceIfDue() {\n'
if 'private fun publishGpsConfigIfDueRc25()' not in store:
    helper = '''    private fun publishGpsConfigIfDueRc25() {
        val now = System.currentTimeMillis()
        if (gpsConfigPushInFlightRc25 || now - lastGpsConfigPushAtRc25 < 30_000L) return
        if (!repo.hasCentralCredentials() || repo.serverUrl.isBlank()) return
        val revision = getSharedPreferences("gps_config_sync", MODE_PRIVATE).getLong("revision", 0L)
            .takeIf { it > 0L } ?: maxOf(lastGpsConfigRevisionRc25, now)
        val enabled = repo.gpsRecognitionEnabled && repo.isGpsConfigured
        val latitude = repo.storeLatitude
        val longitude = repo.storeLongitude
        val radius = repo.gpsRadiusMeters.coerceIn(10, 5000)
        if (enabled && (!latitude.isFinite() || !longitude.isFinite())) return
        lastGpsConfigPushAtRc25 = now
        gpsConfigPushInFlightRc25 = true
        Thread {
            val result = CentralServerClient.publishStoreGpsConfigRc25(
                repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this@MainActivity),
                enabled, latitude, longitude, radius, revision
            )
            result.onSuccess {
                lastGpsConfigRevisionRc25 = it
                getSharedPreferences("gps_config_sync", MODE_PRIVATE).edit()
                    .putLong("serverRevisionRc25", it)
                    .putLong("serverPushAtRc25", System.currentTimeMillis())
                    .apply()
            }
            gpsConfigPushInFlightRc25 = false
        }.apply { isDaemon = true }.start()
    }

'''
    if store_method_anchor not in store:
        raise SystemExit('RC25: Store method anchor not found')
    store = store.replace(store_method_anchor, helper + store_method_anchor, 1)

# -----------------------------------------------------------------------------
# Employee: poll Store GPS zone directly from server independent from BLE.
# When config arrives, write the same trusted GPS fields used by OfflineGeoMonitor,
# then RC24 runtime logic starts/stops LocationManager dynamically.
# -----------------------------------------------------------------------------
emp_field_anchor = '    private var backgroundRestartForRun: Boolean = false\n'
if 'gpsConfigPollInFlightRc25' not in emp:
    if emp_field_anchor not in emp:
        raise SystemExit('RC25: Employee RC24 field anchor not found')
    emp = emp.replace(emp_field_anchor, emp_field_anchor + '''    @Volatile private var gpsConfigPollInFlightRc25 = false
    private var lastGpsConfigPollAtRc25 = 0L
    private var lastGpsConfigRevisionRc25 = 0L
''', 1)

emp_tick_anchor = '            identity.presenceServiceHeartbeatAt = System.currentTimeMillis()\n            ensurePresenceChannels()\n'
if 'syncGpsConfigFromServerRc25()' not in emp:
    if emp_tick_anchor not in emp:
        raise SystemExit('RC25: Employee heartbeat anchor not found')
    emp = emp.replace(emp_tick_anchor, '            identity.presenceServiceHeartbeatAt = System.currentTimeMillis()\n            syncGpsConfigFromServerRc25()\n            ensurePresenceChannels()\n', 1)

emp_method_anchor = '    private fun ensurePresenceChannels() {\n'
if 'private fun syncGpsConfigFromServerRc25()' not in emp:
    helper = '''    private fun syncGpsConfigFromServerRc25() {
        val now = System.currentTimeMillis()
        if (gpsConfigPollInFlightRc25 || now - lastGpsConfigPollAtRc25 < 20_000L) return
        if (!identity.isConfigured || identity.serverUrl.isBlank()) return
        lastGpsConfigPollAtRc25 = now
        gpsConfigPollInFlightRc25 = true
        Thread {
            val result = CentralServerClient.pullEmployeeGpsConfigRc25(
                identity.serverUrl,
                identity.trustedStoreId,
                identity.employeeId,
                identity.pairingSecret,
                identity.installationId
            )
            result.onSuccess { cfg ->
                if (cfg.available && cfg.revision >= lastGpsConfigRevisionRc25) {
                    lastGpsConfigRevisionRc25 = cfg.revision
                    if (cfg.enabled && cfg.latitude.isFinite() && cfg.longitude.isFinite()) {
                        identity.trustedStoreLatitude = cfg.latitude
                        identity.trustedStoreLongitude = cfg.longitude
                        identity.trustedStoreGpsRadius = cfg.radiusMeters
                    } else if (!cfg.enabled) {
                        identity.trustedStoreLatitude = Double.NaN
                        identity.trustedStoreLongitude = Double.NaN
                    }
                    // Re-evaluate immediately; no Bluetooth state is consulted here.
                    refreshGpsRuntimeRc21()
                }
            }
            gpsConfigPollInFlightRc25 = false
        }.apply { isDaemon = true }.start()
    }

'''
    if emp_method_anchor not in emp:
        raise SystemExit('RC25: Employee ensurePresenceChannels anchor not found')
    emp = emp.replace(emp_method_anchor, helper + emp_method_anchor, 1)

client_p.write_text(client, encoding='utf-8')
store_p.write_text(store, encoding='utf-8')
emp_p.write_text(emp, encoding='utf-8')

c = client_p.read_text(encoding='utf-8')
s = store_p.read_text(encoding='utf-8')
e = emp_p.read_text(encoding='utf-8')
assert '/api/v1/store/gps-config' in c
assert '/api/v1/employee/gps-config' in c
assert 'publishGpsConfigIfDueRc25()' in s
assert 'publishStoreGpsConfigRc25' in s
assert 'syncGpsConfigFromServerRc25()' in e
assert 'pullEmployeeGpsConfigRc25' in e
assert 'refreshGpsRuntimeRc21()' in e
assert 'BlePresenceAdvertiser' not in Path(__file__).read_text(encoding='utf-8')
print('RC25 GPS cloud-config rebuild applied: Store -> Server -> Employee -> GPS telemetry -> Store')
