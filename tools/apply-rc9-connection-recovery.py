#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')

def rep(path, old, new):
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'missing token in {path}: {old[:160]}')
    p.write_text(s.replace(old, new, 1), encoding='utf-8')

# RC9 is applied after the RC8 pairing repair during CI.
for path in ['buildsrc/store-app/build.gradle.kts', 'buildsrc/employee-app/build.gradle.kts']:
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    s = s.replace('versionCode = 98', 'versionCode = 99')
    s = s.replace('versionName = "2.0.0-RC8"', 'versionName = "2.0.0-RC9"')
    p.write_text(s, encoding='utf-8')

employee_main = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt'
rep(employee_main,
'''        if (identity.isConfigured) {
            if (identity.autoPresence) startPresence() else startBackgroundPresence()
        }
''',
'''        if (identity.isConfigured) {
            val rc9Recovery = getSharedPreferences("attend_rc9_connection_recovery", MODE_PRIVATE)
            if (!rc9Recovery.getBoolean("auto_presence_restored", false)) {
                identity.autoPresence = true
                rc9Recovery.edit().putBoolean("auto_presence_restored", true).apply()
            }
            if (identity.autoPresence) startPresence() else startBackgroundPresence()
        }
''')

rep(employee_main,
'''        override fun run() {
            if (::connectionSummary.isInitialized && ::identity.isInitialized) updateConnectionSummary()
            connectionUiHandler.postDelayed(this, 3_000L)
        }
''',
'''        override fun run() {
            if (::connectionSummary.isInitialized && ::identity.isInitialized) updateConnectionSummary()
            if (::identity.isInitialized && identity.isConfigured && identity.autoPresence && hasRequiredBlePermissions()) {
                val heartbeatAge = System.currentTimeMillis() - identity.presenceServiceHeartbeatAt
                if (identity.presenceServiceHeartbeatAt <= 0L || heartbeatAge > 15_000L) {
                    startBackgroundPresence()
                }
            }
            connectionUiHandler.postDelayed(this, 3_000L)
        }
''')

# Employee service: if the advertiser object is logically running but Android stopped the actual
# advertiser, restart it instead of waiting forever with Store scanning an empty airspace.
presence = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'
rep(presence,
'''            if (!bluetooth.isRunning()) bluetooth.start(identity.employeeId, identity.pairingSecret)
            if (!directBle.isRunning()) directBle.start()
''',
'''            if (!bluetooth.isRunning() || !bluetooth.isAdvertising()) bluetooth.start(identity.employeeId, identity.pairingSecret)
            if (!directBle.isRunning()) directBle.start()
''')

# Store scanner: start in OEM-compatible unfiltered foreground mode immediately. Payload validation
# still happens in onScanResult(), so this changes discovery compatibility, not trust semantics.
scanner = 'buildsrc/store-app/src/main/java/com/attendpro/store/BleEmployeeScanner.kt'
rep(scanner,
'''    private var unfilteredFallback = false
''',
'''    private var unfilteredFallback = true
''')
rep(scanner,
'''        unfilteredFallback = false
''',
'''        unfilteredFallback = true
''')

# Visible RC9 markers only; owner/admin features are intentionally left intact.
for path, pairs in {
    employee_main: [
        ('RC8 • إصلاح نهائي لمسار الربط وACK', 'RC9 • استعادة اتصال Bluetooth الميداني')
    ],
    'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt': [
        ('RC8 • إصلاح مسار ربط الموظف', 'RC9 • استعادة اتصال Bluetooth الميداني'),
        ('RC8 • لوحة الأقسام', 'RC9 • لوحة الأقسام'),
        ('RC8 • employee pairing ACK repair', 'RC9 • field Bluetooth recovery')
    ]
}.items():
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    for old, new in pairs:
        s = s.replace(old, new)
    p.write_text(s, encoding='utf-8')

Path('RELEASE_NOTES_V2.0.0_RC9.md').write_text('''# ATTEND-PRO 2.0.0-RC9\n\nversionCode 99\n\n## Field Bluetooth recovery\n- Restores automatic employee presence after update with a one-time RC9 migration.\n- Adds an Employee watchdog that restarts PresenceService when its heartbeat stops.\n- Restarts BLE advertising when the logical advertiser is running but Android/OEM has stopped the actual advertisement.\n- Store BLE scanning starts immediately in OEM-compatible unfiltered mode; ATTEND-PRO payload validation remains unchanged.\n- Keeps RC8 QR/ACK repair.\n- Keeps Store Owner full-settings hub and current management features.\n- Protected pairing protocol files are unchanged.\n''', encoding='utf-8')

Path('FIELD_TEST_CHECKLIST_RC9.md').write_text('''# RC9 field test\n1. Install Store + Employee RC9 over the current official builds without clearing data.\n2. Confirm Bluetooth and Nearby Devices are enabled on both phones.\n3. Open Employee once; automatic presence must be ON after the RC9 migration.\n4. Store > Connection Center must discover the Employee phone within seconds.\n5. GATT must advance to authenticated ACK; discovery alone is not treated as connected.\n6. Turn Wi-Fi off and verify Bluetooth-only discovery/ACK.\n7. Lock the Employee phone and verify presence survives/restarts through the foreground service watchdog.\n8. Confirm “عرض جميع إعدادات مدير المحل” still opens the full Owner hub.\n''', encoding='utf-8')

print('RC9 field Bluetooth recovery applied')
