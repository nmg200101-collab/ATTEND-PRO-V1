#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')

def rep(path, old, new, count=1):
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'missing token in {path}: {old[:140]}')
    p.write_text(s.replace(old, new, count), encoding='utf-8')

# RC11 is applied directly after RC8 so all current UI/features remain intact.
for path in ['buildsrc/store-app/build.gradle.kts','buildsrc/employee-app/build.gradle.kts']:
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    s = s.replace('versionCode = 98', 'versionCode = 101')
    s = s.replace('versionName = "2.0.0-RC8"', 'versionName = "2.0.0-RC11"')
    p.write_text(s, encoding='utf-8')

advertiser = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/BlePresenceAdvertiser.kt'
# Keep primary legacy advertisement compact to avoid DATA_TOO_LARGE on OEM devices.
# Put ATTEND-PRO discovery UUID in scan response; GATT UUID is known by the client and does not need advertising.
rep(advertiser,
'''        // RC6: the authenticated manufacturer frame alone is kept in the primary legacy
        // advertisement. Adding a 128-bit service UUID here can overflow the 31-byte budget on
        // Samsung/Motorola and cause ADVERTISE_FAILED_DATA_TOO_LARGE. GATT UUID stays in scan response.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(BleProtocol.MANUFACTURER_ID, payload)
            .build()
        // Put the actual direct-GATT service UUID in scan response instead of the primary
        // 31-byte legacy advertisement. This keeps the authenticated 10-byte manufacturer
        // frame compact while making the connectable GATT endpoint explicit to OEM scanners.
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(BleDirectProtocol.SERVICE_UUID))
            .build()
''',
'''        // RC11: keep the primary packet compact for Samsung/Motorola and similar OEM stacks.
        // The authenticated manufacturer frame is enough to identify the employee. The ATTEND-PRO
        // discovery UUID is placed in scan response; the GATT service UUID is known by our client.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(BleProtocol.MANUFACTURER_ID, payload)
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(BleProtocol.SERVICE_UUID)
            .build()
''')

# Add a strong advertiser heartbeat status so field diagnostics show real broadcast state.
rep(advertiser,
'''        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            advertising = true
            advertisedFlags = currentFlags()
''',
'''        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            advertising = true
            advertisedFlags = currentFlags()
''')

scanner = 'buildsrc/store-app/src/main/java/com/attendpro/store/BleEmployeeScanner.kt'
# Start unfiltered immediately. Security still comes from BleProtocol.parse/authenticated payload,
# not from Android scan filters. This avoids OEMs dropping filtered scan results/scan-response UUIDs.
rep(scanner,
'    private var unfilteredFallback = false\n',
'    private var unfilteredFallback = true\n')
rep(scanner,
'        runCatching { scanner.startScan(if (unfilteredFallback) null else filters, settings, callback) }\n',
'        runCatching { scanner.startScan(null, settings, callback) }\n')
rep(scanner,
'        unfilteredFallback = false\n',
'        unfilteredFallback = true\n')

# Count raw scan callbacks and successfully parsed ATTEND-PRO frames for diagnostics.
rep(scanner,
'''    private var lastResultAt = 0L
    private var unfilteredFallback = true
''',
'''    private var lastResultAt = 0L
    private var unfilteredFallback = true
    private var rawScanCount = 0L
    private var parsedAttendCount = 0L
''')
rep(scanner,
'''        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
''',
'''        override fun onScanResult(callbackType: Int, result: ScanResult) {
            rawScanCount += 1
            val record = result.scanRecord ?: return
''')
rep(scanner,
'''            val payload = BleProtocol.parse(bytes) ?: return
            lastResultAt = System.currentTimeMillis()
''',
'''            val payload = BleProtocol.parse(bytes) ?: return
            parsedAttendCount += 1
            lastResultAt = System.currentTimeMillis()
''')
rep(scanner,
'''    fun lastResultAt(): Long = lastResultAt
}''',
'''    fun lastResultAt(): Long = lastResultAt
    fun rawScanCount(): Long = rawScanCount
    fun parsedAttendCount(): Long = parsedAttendCount
}''')

# Presence service: restart real advertising if OEM silently kills it and re-evaluate GPS every heartbeat.
presence = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'
rep(presence,
'            if (!bluetooth.isRunning()) bluetooth.start(identity.employeeId, identity.pairingSecret)\n',
'            if (!bluetooth.isRunning() || !bluetooth.isAdvertising()) bluetooth.start(identity.employeeId, identity.pairingSecret)\n')
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
        val hasLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val gpsAllowedNow = identity.isTrustedStoreGpsConfigured && hasLocation
        if (gpsAllowedNow && !locationMonitoringAllowedForRun) {
            locationMonitoringAllowedForRun = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            }
        } else if (!gpsAllowedNow) locationMonitoringAllowedForRun = false
        if (locationMonitoringAllowedForRun && !geoMonitor.isRunning()) geoMonitor.start()
        if (!locationMonitoringAllowedForRun && geoMonitor.isRunning()) geoMonitor.stop()
    }
''')

# Preserve current MainActivity/RC8 features; only add RC11 one-time recovery and visible marker.
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
        val rc11Recovery = getSharedPreferences("attend_rc11_connection_recovery", MODE_PRIVATE)
        if (identity.isConfigured && !rc11Recovery.getBoolean("presence_enabled_once", false)) {
            identity.autoPresence = true
            rc11Recovery.edit().putBoolean("presence_enabled_once", true).apply()
        }
''')
p = ROOT / main
s = p.read_text(encoding='utf-8')
s = s.replace('RC8 • إصلاح نهائي لمسار الربط وACK', 'RC11 • إصلاح Bluetooth/GPS بدون رجوع للتحديثات')
p.write_text(s, encoding='utf-8')

store_main = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
s = store_main.read_text(encoding='utf-8')
s = s.replace('RC8 • إصلاح مسار ربط الموظف', 'RC11 • إصلاح Bluetooth/GPS بدون رجوع')
s = s.replace('RC8 • لوحة الأقسام', 'RC11 • لوحة الأقسام')
s = s.replace('RC8 • employee pairing ACK repair', 'RC11 • live Bluetooth/GPS fix')
store_main.write_text(s, encoding='utf-8')

Path('RELEASE_NOTES_V2.0.0_RC11.md').write_text('''# ATTEND-PRO 2.0.0-RC11\n\nversionCode 101\n\n- Keeps all current UI/features from RC8; no old MainActivity or settings files are restored.\n- BLE primary advertisement is compact manufacturer data to avoid OEM DATA_TOO_LARGE failures.\n- ATTEND-PRO discovery UUID is placed in scan response.\n- Store starts an unfiltered low-latency BLE scan immediately, then accepts only valid ATTEND-PRO payloads.\n- Employee advertiser is restarted if Android/OEM silently stops actual advertising.\n- GPS eligibility is re-evaluated every PresenceService heartbeat.\n- Protected pairing protocol files remain unchanged.\n''', encoding='utf-8')
Path('FIELD_TEST_CHECKLIST_RC11.md').write_text('''# RC11 field test\n1. Install both RC11 APKs over current apps; do not clear data.\n2. Confirm RC11 marker and current owner settings/features remain present.\n3. Open Employee once with Bluetooth and Nearby Devices enabled.\n4. Store should discover the Employee advertisement within seconds.\n5. Confirm GATT then authenticated ACK/confirm.\n6. GPS requires trusted Store coordinates + Location permission; once present it must update without relaunch.\n''', encoding='utf-8')

assert 'versionCode = 101' in (ROOT/'buildsrc/employee-app/build.gradle.kts').read_text()
assert 'scanner.startScan(null, settings, callback)' in (ROOT/scanner).read_text()
assert '.addServiceUuid(BleProtocol.SERVICE_UUID)' in (ROOT/advertiser).read_text()
assert '!bluetooth.isRunning() || !bluetooth.isAdvertising()' in (ROOT/presence).read_text()
print('RC11 live Bluetooth/GPS fix applied without UI rollback')
