#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')

def rep(path, old, new):
    p = ROOT / path
    s = p.read_text()
    if old not in s:
        raise SystemExit(f'missing token in {path}: {old[:100]}')
    p.write_text(s.replace(old, new, 1))

# Bump both apps to RC7.
for path in ['buildsrc/store-app/build.gradle.kts','buildsrc/employee-app/build.gradle.kts']:
    p=ROOT/path
    s=p.read_text()
    s=s.replace('versionCode = 96','versionCode = 97')
    s=s.replace('versionName = "2.0.0-RC6"','versionName = "2.0.0-RC7"')
    p.write_text(s)

# GPS recognition must not depend on optional arrival alert preference.
rep('buildsrc/employee-app/src/main/java/com/attendpro/employee/OfflineGeoMonitor.kt',
    'if (running || !identity.geoArrivalAlertsEnabled || !identity.isTrustedStoreGpsConfigured || !hasPermission()) return',
    'if (running || !identity.isTrustedStoreGpsConfigured || !hasPermission()) return')

# One-time migration: old saved switch state must not silently keep BLE/GPS disabled after upgrade.
rep('buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt',
    'identity = EmployeeIdentityStore(this)\n        advertiser = BlePresenceAdvertiser(this)',
    '''identity = EmployeeIdentityStore(this)\n        val rc7Migration = getSharedPreferences("attend_rc7_migration", MODE_PRIVATE)\n        if (identity.isConfigured && !rc7Migration.getBoolean("presence_enabled_once", false)) {\n            identity.autoPresence = true\n            rc7Migration.edit().putBoolean("presence_enabled_once", true).apply()\n        }\n        advertiser = BlePresenceAdvertiser(this)''')

# Make RC7 unmistakable on employee screen.
rep('buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt',
    'header.addView(UiKit.subtitle(this,p,"تطبيق الموظف المساند • الإصدار ${attendProVersionName()}").apply',
    'header.addView(UiKit.subtitle(this,p,"RC7 • إصلاح Bluetooth/GPS • الإصدار ${attendProVersionName()}").apply')

# Default owner shortcuts must no longer be empty on existing/fresh installs.
rep('buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt',
'''    private fun ownerShortcutIds(): Set<String> =\n        getSharedPreferences("store_owner_ui", MODE_PRIVATE)\n            .getStringSet("home_shortcuts", emptySet())?.toSet().orEmpty()''',
'''    private fun ownerShortcutIds(): Set<String> {\n        val prefs = getSharedPreferences("store_owner_ui", MODE_PRIVATE)\n        val stored = prefs.getStringSet("home_shortcuts", null)\n        return stored?.toSet() ?: setOf("all_settings", "presence_challenge", "presence_control", "connections")\n    }''')

# Make RC7 unmistakable on Store screens.
rep('buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt',
    'header.addView(UiKit.subtitle(this, p, "نظام حضور المحل • الإصدار ${attendProVersionName()}").apply',
    'header.addView(UiKit.subtitle(this, p, "RC7 • إصلاح الاتصال والإدارة • الإصدار ${attendProVersionName()}").apply')
rep('buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt',
    'header.addView(UiKit.subtitle(this,p,"لوحة الأقسام • ${attendProVersionName()}").apply',
    'header.addView(UiKit.subtitle(this,p,"RC7 • لوحة الأقسام • ${attendProVersionName()}").apply')
rep('buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt',
    't("نظام حضور المحل • ${attendProVersionName()}", "Store attendance system • ${attendProVersionName()}")',
    't("RC7 • إصلاح الاتصال والإدارة • ${attendProVersionName()}", "RC7 • connectivity/admin repair • ${attendProVersionName()}")')

# Actually place the owner shortcut card in each active home template.
rep('buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt',
    'timelineCard.addView(activityTimelineView)\n        root.addView(timelineCard)\n\n        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })',
    'timelineCard.addView(activityTimelineView)\n        root.addView(timelineCard)\n        addOwnerShortcutCard(root)\n\n        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })')
rep('buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt',
    'val recent=UiKit.card(this,p,8);recent.addView(UiKit.sectionLabel(this,p,"آخر حركة"));recent.addView(recentAttendanceSummaryView);UiKit.makeInteractive(recent,this,p);recent.setOnClickListener{showConnectionAttendanceHistory()};root.addView(recent)\n        root.addView(UiKit.card(this,p,7).apply{addView(status)})',
    'val recent=UiKit.card(this,p,8);recent.addView(UiKit.sectionLabel(this,p,"آخر حركة"));recent.addView(recentAttendanceSummaryView);UiKit.makeInteractive(recent,this,p);recent.setOnClickListener{showConnectionAttendanceHistory()};root.addView(recent)\n        addOwnerShortcutCard(root)\n        root.addView(UiKit.card(this,p,7).apply{addView(status)})')
rep('buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt',
    'root.addView(bottomRow)\n\n        root.addView(UiKit.card(this, p, 4).apply { addView(status) })',
    'root.addView(bottomRow)\n        addOwnerShortcutCard(root)\n\n        root.addView(UiKit.card(this, p, 4).apply { addView(status) })')

# Release notes/checklist.
Path('RELEASE_NOTES_V2.0.0_RC7.md').write_text('''# ATTEND-PRO 2.0.0-RC7\n\nversionCode 97\n\n## Field repair\n- GPS recognition no longer depends on the arrival-alert preference.\n- Existing paired Employee installs receive a one-time migration that re-enables automatic presence so saved legacy state cannot silently disable Bluetooth/GPS.\n- Store owner shortcuts are visible by default and are now actually inserted into active home layouts.\n- Presence Proof controls and connection center are directly visible from Store home.\n- Visible RC7 repair label is shown in both Store and Employee apps for field verification.\n- Protected pairing protocol files remain unchanged.\n\n## credential/security\nNo credential or pairing secret format was changed.\n''')
Path('FIELD_TEST_CHECKLIST_RC7.md').write_text('''# RC7 field test\n1. Confirm RC7 label appears on both Store and Employee headers.\n2. Keep Bluetooth ON on both devices; grant Nearby devices permissions.\n3. Confirm Store sees Employee over Bluetooth without Wi-Fi.\n4. Configure Store GPS, grant Employee location, and verify GPS state even when arrival alert toggle is OFF.\n5. Confirm Store home shows owner shortcuts: all settings, Presence Proof now, Presence Proof control, connections.\n6. Send a Presence Proof request and confirm Employee notification/dialog appears.\n''')
print('RC7 patch applied')
