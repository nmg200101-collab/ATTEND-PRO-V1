import os
from pathlib import Path

if os.environ.get('GITHUB_ACTIONS') == 'true':
    root = Path.cwd()

    def patch(path, old, new):
        p = root / path
        if not p.exists():
            return
        text = p.read_text(encoding='utf-8')
        if old in text:
            p.write_text(text.replace(old, new, 1), encoding='utf-8')

    employee = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt'
    presence = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'
    scanner = 'buildsrc/store-app/src/main/java/com/attendpro/store/BleEmployeeScanner.kt'

    patch(employee,
'''        if (identity.isConfigured) {
            if (identity.autoPresence) startPresence() else startBackgroundPresence()
        }
''',
'''        if (identity.isConfigured) {
            val recovery = getSharedPreferences("attend_rc8_ble_recovery", MODE_PRIVATE)
            if (!recovery.getBoolean("auto_presence_restored", false)) {
                identity.autoPresence = true
                recovery.edit().putBoolean("auto_presence_restored", true).apply()
            }
            if (identity.autoPresence) startPresence() else startBackgroundPresence()
        }
''')

    patch(employee,
'''        override fun run() {
            if (::connectionSummary.isInitialized && ::identity.isInitialized) updateConnectionSummary()
            connectionUiHandler.postDelayed(this, 3_000L)
        }
''',
'''        override fun run() {
            if (::connectionSummary.isInitialized && ::identity.isInitialized) updateConnectionSummary()
            if (::identity.isInitialized && identity.isConfigured && identity.autoPresence && hasRequiredBlePermissions()) {
                val heartbeatAge = System.currentTimeMillis() - identity.presenceServiceHeartbeatAt
                if (identity.presenceServiceHeartbeatAt <= 0L || heartbeatAge > 15_000L) startBackgroundPresence()
            }
            connectionUiHandler.postDelayed(this, 3_000L)
        }
''')

    patch(presence,
'''            if (!bluetooth.isRunning()) bluetooth.start(identity.employeeId, identity.pairingSecret)
            if (!directBle.isRunning()) directBle.start()
''',
'''            if (!bluetooth.isRunning() || !bluetooth.isAdvertising()) bluetooth.start(identity.employeeId, identity.pairingSecret)
            if (!directBle.isRunning()) directBle.start()
''')

    patch(scanner,
'    private var unfilteredFallback = false\n',
'    private var unfilteredFallback = true\n')

    patch(scanner,
'        unfilteredFallback = false\n',
'        unfilteredFallback = true\n')
