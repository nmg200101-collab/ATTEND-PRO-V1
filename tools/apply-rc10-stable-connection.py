#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')

def rep(path, old, new, count=1):
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'missing token in {path}: {old[:140]}')
    p.write_text(s.replace(old, new, count), encoding='utf-8')

# Promote the field-recovery build to a distinct version.
for path in ['buildsrc/store-app/build.gradle.kts','buildsrc/employee-app/build.gradle.kts']:
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    s = s.replace('versionCode = 98', 'versionCode = 100')
    s = s.replace('versionName = "2.0.0-RC8"', 'versionName = "2.0.0-RC10"')
    p.write_text(s, encoding='utf-8')

# 1) Restore the BLE advertisement shape that was field-confirmed before RC6.
# The Store's stable scanner filters on BleProtocol.SERVICE_UUID and manufacturer payload.
advertiser = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/BlePresenceAdvertiser.kt'
rep(advertiser,
'''        // RC6: the authenticated manufacturer frame alone is kept in the primary legacy
        // advertisement. Adding a 128-bit service UUID here can overflow the 31-byte budget on
        // Samsung/Motorola and cause ADVERTISE_FAILED_DATA_TOO_LARGE. GATT UUID stays in scan response.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(BleProtocol.MANUFACTURER_ID, payload)
            .build()
''',
'''        // RC10: restore the exact field-stable primary advertisement used by the
        // previously successful device build: service UUID + authenticated manufacturer frame.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(BleProtocol.SERVICE_UUID)
            .addManufacturerData(BleProtocol.MANUFACTURER_ID, payload)
            .build()
''')

# 2) Restore Store BLE scanner behavior to the field-confirmed baseline.
scanner = 'buildsrc/store-app/src/main/java/com/attendpro/store/BleEmployeeScanner.kt'
rep(scanner, '    private var unfilteredFallback = false\n', '')
rep(scanner,
'''                if (reference > 0L && now - reference > 8_000L && !unfilteredFallback) {
                    unfilteredFallback = true
                    restartScan("تفعيل مسح BLE المتوافق مع الأجهزة تلقائيًا")
                } else if (reference > 0L && now - reference > 25_000L) {
                    restartScan("إعادة تنشيط مسح BLE تلقائيًا")
                }
''',
'''                if (reference > 0L && now - reference > 25_000L) {
                    restartScan("إعادة تنشيط مسح BLE تلقائيًا")
                }
''')
rep(scanner,
'        runCatching { scanner.startScan(if (unfilteredFallback) null else filters, settings, callback) }\n',
'        runCatching { scanner.startScan(filters, settings, callback) }\n')
rep(scanner, '        unfilteredFallback = false\n', '')

# 3) Keep BLE advertising self-healing. running=true is not sufficient when OEMs silently stop advertising.
presence = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'
rep(presence,
'            if (!bluetooth.isRunning()) bluetooth.start(identity.employeeId, identity.pairingSecret)\n',
'            if (!bluetooth.isRunning() || !bluetooth.isAdvertising()) bluetooth.start(identity.employeeId, identity.pairingSecret)\n')

# 4) GPS must become active when Store coordinates arrive later over authenticated GATT config.
rep(presence,
'''    private fun ensurePresenceChannels() {
        if (!identity.isConfigured || !identity.autoPresence) return
        if (!network.isRunning()) network.start(identity.employeeId, identity.pairingSecret)
        if (!localChallenges.isRunning()) localChallenges.start(identity.employeeId, identity.pairingSecret)
        startBleChannelsIfPermitted()
        if (locationMonitoringAllowedForRun && !geoMonitor.isRunning()) geoMonitor.start()
        if (!locationMonitoringAllowedForRun && geoMonitor.isRunning()) geoMonitor.stop()
    }
''',
'''    private fun ensurePresenceChannels() {
        if (!identity.isConfigured || !identity.autoPresence) return
        if (!network.isRunning()) network.start(identity.employeeId, identity.pairingSecret)
        if (!localChallenges.isRunning()) localChallenges.start(identity.employeeId, identity.pairingSecret)
        startBleChannelsIfPermitted()

        // Re-evaluate GPS every heartbeat. Store coordinates can arrive after service startup
        // through the authenticated BLE CONFIG frame, so a startup-only boolean leaves GPS dead.
        val hasLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val gpsAllowedNow = identity.isTrustedStoreGpsConfigured && hasLocation
        if (gpsAllowedNow && !locationMonitoringAllowedForRun) {
            locationMonitoringAllowedForRun = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    startForeground(
                        NOTIFICATION_ID,
                        notification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                }
            }
        } else if (!gpsAllowedNow) {
            locationMonitoringAllowedForRun = false
        }
        if (locationMonitoringAllowedForRun && !geoMonitor.isRunning()) geoMonitor.start()
        if (!locationMonitoringAllowedForRun && geoMonitor.isRunning()) geoMonitor.stop()
    }
''')

# 5) Force automatic presence on once for already-linked installations upgraded to RC10.
main = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt'
rep(main,
'''        val rc7Migration = getSharedPreferences("attend_rc7_migration", MODE_PRIVATE)
        if (identity.isConfigured && !rc7Migration.getBoolean("presence_enabled_once", false)) {
            identity.autoPresence = true
            rc7Migration.edit().putBoolean("presence_enabled_once", true).apply()
        }
''',
'''        val rc7Migration = getSharedPreferences("attend_rc7_migration", MODE_PRIVATE)
        if (identity.isConfigured && !rc7Migration.getBoolean("presence_enabled_once", false)) {
            identity.autoPresence = true
            rc7Migration.edit().putBoolean("presence_enabled_once", true).apply()
        }
        val rc10Migration = getSharedPreferences("attend_rc10_connection_recovery", MODE_PRIVATE)
        if (identity.isConfigured && !rc10Migration.getBoolean("presence_restored_once", false)) {
            identity.autoPresence = true
            rc10Migration.edit().putBoolean("presence_restored_once", true).apply()
        }
''')

# Request GPS permission once when a linked employee already has trusted Store coordinates.
rep(main,
'''        buildElegantUi()
        refreshProfile()
''',
'''        buildElegantUi()
        val rc10Location = getSharedPreferences("attend_rc10_connection_recovery", MODE_PRIVATE)
        if (identity.isConfigured && identity.isTrustedStoreGpsConfigured &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            !rc10Location.getBoolean("location_prompted", false)) {
            rc10Location.edit().putBoolean("location_prompted", true).apply()
            Handler(Looper.getMainLooper()).postDelayed({
                requestLocationPermissionWithDisclosure("اسمح بالموقع لتفعيل تعرف GPS قرب المحل")
            }, 700L)
        }
        refreshProfile()
''')

# Visible markers so field testing cannot confuse this build with RC8.
p = ROOT / main
s = p.read_text(encoding='utf-8')
s = s.replace('RC8 • إصلاح نهائي لمسار الربط وACK', 'RC10 • استعادة الاتصال الميداني Bluetooth/GPS')
s = s.replace('RC7 • إصلاح Bluetooth/GPS', 'RC10 • استعادة الاتصال الميداني Bluetooth/GPS')
p.write_text(s, encoding='utf-8')

store_main = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
s = store_main.read_text(encoding='utf-8')
s = s.replace('RC8 • إصلاح مسار ربط الموظف', 'RC10 • استعادة اتصال Bluetooth/GPS')
s = s.replace('RC8 • لوحة الأقسام', 'RC10 • لوحة الأقسام')
s = s.replace('RC8 • employee pairing ACK repair', 'RC10 • stable Bluetooth/GPS recovery')
store_main.write_text(s, encoding='utf-8')

Path('RELEASE_NOTES_V2.0.0_RC10.md').write_text('''# ATTEND-PRO 2.0.0-RC10\n\nversionCode 100\n\n## Field-stable connection recovery\n- Restores the field-confirmed BLE primary advertisement: ATTEND-PRO service UUID plus authenticated manufacturer payload.\n- Restores Store BLE scanner filtering/watchdog behavior from the field-confirmed connection baseline.\n- BLE advertiser self-heals when an OEM silently stops advertising while the service remains marked running.\n- GPS eligibility is re-evaluated continuously so coordinates received later through authenticated GATT CONFIG start GPS without reinstall/re-pair service restart.\n- Existing linked installations get automatic presence re-enabled once on RC10.\n- One-time GPS permission disclosure is shown when trusted Store coordinates already exist but location permission is missing.\n- Protected pairing protocol files are unchanged.\n''', encoding='utf-8')
Path('FIELD_TEST_CHECKLIST_RC10.md').write_text('''# RC10 field test\n1. Install Store and Employee RC10 together over the existing official builds.\n2. Confirm both headers show RC10.\n3. Open Employee once and grant Nearby Devices; if requested, grant Location.\n4. Store Bluetooth diagnostics must discover the employee advertisement before GATT.\n5. Verify GATT reaches authenticated ACK/confirm.\n6. Keep both apps linked; Store CONFIG must synchronize GPS coordinates to Employee.\n7. Employee GPS observation must change from UNKNOWN when location is available; Store must display fresh GPS status after server sync.\n8. Protected pairing protocol remains identical to baseline.\n''', encoding='utf-8')

# Assertions: fail CI if the intended recovery is not really in the compiled source.
assert '.addServiceUuid(BleProtocol.SERVICE_UUID)' in (ROOT / advertiser).read_text(encoding='utf-8')
assert 'scanner.startScan(filters, settings, callback)' in (ROOT / scanner).read_text(encoding='utf-8')
assert '!bluetooth.isRunning() || !bluetooth.isAdvertising()' in (ROOT / presence).read_text(encoding='utf-8')
assert 'val gpsAllowedNow = identity.isTrustedStoreGpsConfigured && hasLocation' in (ROOT / presence).read_text(encoding='utf-8')
print('RC10 stable Bluetooth/GPS recovery applied')
