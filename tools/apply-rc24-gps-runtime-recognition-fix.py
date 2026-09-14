#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
emp_p = ROOT / 'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'
store_p = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'

emp = emp_p.read_text(encoding='utf-8')
store = store_p.read_text(encoding='utf-8')

# RC24: GPS recognition must be runtime-independent from Bluetooth.
# Do not touch pairing protocol or RC12 Bluetooth runtime files.

if 'private var backgroundRestartForRun: Boolean = false' not in emp:
    field_anchor = '    private var locationMonitoringAllowedForRun: Boolean = false\n'
    if field_anchor not in emp:
        raise SystemExit('RC24: PresenceService location flag not found')
    emp = emp.replace(field_anchor, field_anchor + '    private var backgroundRestartForRun: Boolean = false\n', 1)

anchor = '    private fun startBleChannelsIfPermitted() {\n'
helpers = '''    private fun hasForegroundLocationPermissionRc24(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocationPermissionRc24(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun refreshLocationMonitoringAllowedRc24(): Boolean =
        identity.isTrustedStoreGpsConfigured &&
            hasForegroundLocationPermissionRc24() &&
            (!backgroundRestartForRun || hasBackgroundLocationPermissionRc24())

    private fun ensureLocationForegroundTypeRc24(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
    }.isSuccess

'''
if 'private fun refreshLocationMonitoringAllowedRc24()' not in emp:
    if anchor not in emp:
        raise SystemExit('RC24: helper insertion anchor not found')
    emp = emp.replace(anchor, helpers + anchor, 1)

# Replace the original one-shot permission calculation. Runtime re-check continues in
# refreshGpsRuntimeRc21(), which RC21 already calls every presence heartbeat.
old_start = '''        val hasForegroundLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBackgroundLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        val backgroundRestart = intent == null || intent.getBooleanExtra(EXTRA_BACKGROUND_RESTART, false)
        locationMonitoringAllowedForRun = identity.isTrustedStoreGpsConfigured && hasForegroundLocation &&
            (!backgroundRestart || hasBackgroundLocation)
'''
new_start = '''        backgroundRestartForRun = intent == null || intent.getBooleanExtra(EXTRA_BACKGROUND_RESTART, false)
        locationMonitoringAllowedForRun = refreshLocationMonitoringAllowedRc24()
'''
if old_start in emp:
    emp = emp.replace(old_start, new_start, 1)
elif new_start not in emp:
    raise SystemExit('RC24: onStartCommand GPS permission anchor not found')

# RC21/RC22 already own the dynamic GPS function. Patch only its permission decision and
# FGS promotion, leaving BLE advertising/scanning/direct-link code untouched.
old_runtime_permissions = '''        val hasLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val shouldMonitor = identity.isTrustedStoreGpsConfigured && hasLocation

        if (shouldMonitor && !locationMonitoringAllowedForRun) {
            // GPS configuration often arrives after the service was already started by BLE.
            // Promote the existing foreground service to include LOCATION immediately.
            val promoted = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification())
                }
            }.isSuccess
            if (promoted) locationMonitoringAllowedForRun = true
        }
'''
new_runtime_permissions = '''        // Re-evaluate GPS on every heartbeat. Receiving the Store zone, granting location,
        // or switching Bluetooth off must not require restarting PresenceService.
        val shouldMonitor = refreshLocationMonitoringAllowedRc24()

        if (shouldMonitor && !locationMonitoringAllowedForRun) {
            // Promote the existing foreground service to LOCATION only when Android runtime
            // prerequisites are currently satisfied. This is independent from BLE state.
            if (ensureLocationForegroundTypeRc24()) locationMonitoringAllowedForRun = true
        } else if (!shouldMonitor) {
            locationMonitoringAllowedForRun = false
        }
'''
if old_runtime_permissions in emp:
    emp = emp.replace(old_runtime_permissions, new_runtime_permissions, 1)
elif 'val shouldMonitor = refreshLocationMonitoringAllowedRc24()' not in emp:
    raise SystemExit('RC24: RC21 runtime GPS permission block not found')

# Harden Store freshness against stale/future timestamps while preserving the existing
# five-minute GPS visibility timeout and BLE-independent server presence state.
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
elif 'GPS_RECOGNITION_FRESH_MILLIS = 5 * 60_000L' not in store:
    raise SystemExit('RC24: Store GPS freshness timeout not found')

emp_p.write_text(emp, encoding='utf-8')
store_p.write_text(store, encoding='utf-8')

e = emp_p.read_text(encoding='utf-8')
s = store_p.read_text(encoding='utf-8')
assert 'refreshLocationMonitoringAllowedRc24()' in e
assert 'ensureLocationForegroundTypeRc24()' in e
assert 'val shouldMonitor = refreshLocationMonitoringAllowedRc24()' in e
assert 'This is independent from BLE state' in e
assert 'ACCESS_BACKGROUND_LOCATION' in e
assert 'GPS_RECOGNITION_FRESH_MILLIS = 5 * 60_000L' in s
assert s.count('GPS • إعداد موقع المحل') == 1
print('RC24 GPS runtime recognition fix applied')
