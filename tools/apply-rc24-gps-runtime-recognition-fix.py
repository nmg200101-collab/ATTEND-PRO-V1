#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
emp_p = ROOT / 'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'
store_p = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'

emp = emp_p.read_text(encoding='utf-8')
store = store_p.read_text(encoding='utf-8')

# RC24: GPS recognition must be a runtime-independent presence channel.
# Do not touch pairing protocol or RC12 Bluetooth runtime files.

old_fields = '''    private var locationMonitoringAllowedForRun: Boolean = false
    private var lastLocalChallengeMethod: String = ""
'''
new_fields = '''    private var locationMonitoringAllowedForRun: Boolean = false
    private var backgroundRestartForRun: Boolean = false
    private var lastLocalChallengeMethod: String = ""
'''
if old_fields not in emp:
    raise SystemExit('RC24: PresenceService field anchor not found')
emp = emp.replace(old_fields, new_fields, 1)

anchor = '''    private fun startBleChannelsIfPermitted() {
'''
helpers = '''    private fun hasForegroundLocationPermissionRc24(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocationPermissionRc24(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun refreshLocationMonitoringAllowedRc24(): Boolean {
        val allowed = identity.isTrustedStoreGpsConfigured &&
            hasForegroundLocationPermissionRc24() &&
            (!backgroundRestartForRun || hasBackgroundLocationPermissionRc24())
        locationMonitoringAllowedForRun = allowed
        return allowed
    }

    private fun ensureLocationForegroundTypeRc24() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            startForeground(
                NOTIFICATION_ID,
                notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        }
    }

'''
if anchor not in emp:
    raise SystemExit('RC24: helper insertion anchor not found')
emp = emp.replace(anchor, helpers + anchor, 1)

old_start = '''        val hasForegroundLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBackgroundLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        val backgroundRestart = intent == null || intent.getBooleanExtra(EXTRA_BACKGROUND_RESTART, false)
        locationMonitoringAllowedForRun = identity.isTrustedStoreGpsConfigured && hasForegroundLocation &&
            (!backgroundRestart || hasBackgroundLocation)
'''
new_start = '''        backgroundRestartForRun = intent == null || intent.getBooleanExtra(EXTRA_BACKGROUND_RESTART, false)
        refreshLocationMonitoringAllowedRc24()
'''
if old_start not in emp:
    raise SystemExit('RC24: onStartCommand GPS permission anchor not found')
emp = emp.replace(old_start, new_start, 1)

old_ensure = '''        startBleChannelsIfPermitted()
        if (locationMonitoringAllowedForRun && !geoMonitor.isRunning()) geoMonitor.start()
        if (!locationMonitoringAllowedForRun && geoMonitor.isRunning()) geoMonitor.stop()
'''
new_ensure = '''        startBleChannelsIfPermitted()
        // Re-evaluate GPS every heartbeat. The store zone or location permission can become
        // available after this service was already started; BLE state must never gate GPS.
        val gpsAllowedNow = refreshLocationMonitoringAllowedRc24()
        if (gpsAllowedNow && !geoMonitor.isRunning()) {
            ensureLocationForegroundTypeRc24()
            geoMonitor.start()
        }
        if (!gpsAllowedNow && geoMonitor.isRunning()) geoMonitor.stop()
'''
if old_ensure not in emp:
    raise SystemExit('RC24: ensurePresenceChannels GPS anchor not found')
emp = emp.replace(old_ensure, new_ensure, 1)

# Harden the already-independent Store freshness check against invalid/future timestamps.
old_fresh = '''    private fun isGpsRecognizedFresh(employeeId: String, now: Long = System.currentTimeMillis()): Boolean {
        val state = serverGpsState[employeeId] ?: return false
        val seen = serverGpsSeenAt[employeeId] ?: return false
        return (state == "INSIDE" || state == "NEAR") && now - seen <= GPS_RECOGNITION_FRESH_MILLIS
    }
'''
new_fresh = '''    private fun isGpsRecognizedFresh(employeeId: String, now: Long = System.currentTimeMillis()): Boolean {
        val state = serverGpsState[employeeId] ?: return false
        val seen = serverGpsSeenAt[employeeId] ?: return false
        val age = now - seen
        return (state == "INSIDE" || state == "NEAR") && age in 0..GPS_RECOGNITION_FRESH_MILLIS
    }
'''
if old_fresh in store:
    store = store.replace(old_fresh, new_fresh, 1)
else:
    # The exact helper can vary across chained RC scripts. The 5-minute timeout already exists;
    # do not risk a broad UI rewrite when the safe helper anchor is absent.
    if 'GPS_RECOGNITION_FRESH_MILLIS = 5 * 60_000L' not in store:
        raise SystemExit('RC24: Store GPS freshness timeout not found')

emp_p.write_text(emp, encoding='utf-8')
store_p.write_text(store, encoding='utf-8')

# Static invariants for the field fix.
e = emp_p.read_text(encoding='utf-8')
s = store_p.read_text(encoding='utf-8')
assert 'refreshLocationMonitoringAllowedRc24()' in e
assert 'ensureLocationForegroundTypeRc24()' in e
assert 'BLE state must never gate GPS' in e
assert 'ACCESS_BACKGROUND_LOCATION' in e
assert 'GPS_RECOGNITION_FRESH_MILLIS = 5 * 60_000L' in s
assert s.count('GPS • إعداد موقع المحل') == 1
print('RC24 GPS runtime recognition fix applied')
