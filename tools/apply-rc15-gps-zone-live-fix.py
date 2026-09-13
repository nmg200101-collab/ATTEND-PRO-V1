#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')

def rep(path, old, new, count=1):
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'missing token in {path}: {old[:160]}')
    p.write_text(s.replace(old, new, count), encoding='utf-8')

# RC15 applies after RC14. Do not touch protected pairing or RC12 BLE radio/scanner/server files.
for path in ['buildsrc/store-app/build.gradle.kts','buildsrc/employee-app/build.gradle.kts']:
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    s = s.replace('versionCode = 104', 'versionCode = 105')
    s = s.replace('versionName = "2.0.0-RC14"', 'versionName = "2.0.0-RC15"')
    p.write_text(s, encoding='utf-8')

store = 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
sp = ROOT / store
s = sp.read_text(encoding='utf-8')

# RC14 could permanently suppress the first-run prompt before GPS was actually configured.
s = s.replace('''        val prefs = getSharedPreferences("gps_first_setup_rc14", MODE_PRIVATE)\n        if (prefs.getBoolean("prompted", false)) return\n        if (repo.gpsRecognitionEnabled && repo.isGpsConfigured) {\n            prefs.edit().putBoolean("prompted", true).apply()\n            return\n        }\n        prefs.edit().putBoolean("prompted", true).apply()\n        nearbyRefreshHandler.postDelayed({ if (!isFinishing) showGpsSetupDialog(firstTime = true) }, 700L)\n''', '''        if (repo.gpsRecognitionEnabled && repo.isGpsConfigured) return\n        nearbyRefreshHandler.postDelayed({ if (!isFinishing && !repo.isGpsConfigured) showGpsSetupDialog(firstTime = true) }, 700L)\n''')

# Reject stale last-known fixes; request a fresh fix instead.
s = s.replace('''                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)\n                val best = providers.mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }\n                    .maxByOrNull { it.time }\n''', '''                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)\n                val nowFix = System.currentTimeMillis()\n                val best = providers.mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }\n                    .filter { nowFix - it.time <= 15 * 60_000L }\n                    .maxByOrNull { it.time }\n''')

# After saving/changing the Store GPS zone, rebuild only the Store-side direct client object.
# This forces the already-approved GATT handshake to resend its CONFIG to previously paired employees
# without changing advertising, scanner, GATT server, protected protocol, or pairing code.
s = s.replace('''                    repo.storeLatitude = best.latitude\n                    repo.storeLongitude = best.longitude\n                    if (::status.isInitialized) status.text = "✓ GPS مفعّل • موقع المحل محفوظ • النطاق ${meters}م"\n                    dialog.dismiss()\n                    refreshDashboard()\n                    return@setOnClickListener\n''', '''                    repo.storeLatitude = best.latitude\n                    repo.storeLongitude = best.longitude\n                    if (::status.isInitialized) status.text = "✓ GPS مفعّل • موقع المحل محفوظ • النطاق ${meters}م • جاري مزامنة المنطقة مع هاتف الموظف"\n                    dialog.dismiss()\n                    refreshGpsConfigToEmployeesRc15()\n                    refreshDashboard()\n                    return@setOnClickListener\n''')
s = s.replace('''                            if (::status.isInitialized) status.text = "✓ GPS مفعّل • تم تحديد موقع المحل • النطاق ${meters}م"\n                            if (dialog.isShowing) dialog.dismiss()\n                            refreshDashboard()\n''', '''                            if (::status.isInitialized) status.text = "✓ GPS مفعّل • تم تحديد موقع المحل • النطاق ${meters}م • جاري مزامنة المنطقة مع هاتف الموظف"\n                            if (dialog.isShowing) dialog.dismiss()\n                            refreshGpsConfigToEmployeesRc15()\n                            refreshDashboard()\n''')

# Add permanent GPS card to every Store home layout immediately before owner shortcuts.
helper = '''    private fun addGpsPresenceSetupCardRc15(root: LinearLayout) {\n        val card = UiKit.card(this, p, 14)\n        card.addView(UiKit.sectionLabel(this, p, "GPS • منطقة التعرف على هاتف الموظف"))\n        val state = when {\n            repo.gpsRecognitionEnabled && repo.isGpsConfigured -> "✓ مفعّل • نطاق ${repo.gpsRadiusMeters}م • موقع المحل محفوظ"\n            repo.gpsRecognitionEnabled -> "مفعّل لكن موقع المحل غير محدد"\n            else -> "غير مفعّل"\n        }\n        card.addView(UiKit.subtitle(this, p, "$state\\nحدد موقع المحل والنطاق هنا. بعد الحفظ تُعاد مزامنة المنطقة مع الهواتف المرتبطة تلقائيًا."))\n        card.addView(UiKit.button(this, p, "تفعيل GPS وتحديد منطقة المحل", false).apply {\n            setOnClickListener { showGpsSetupDialog(firstTime = !repo.isGpsConfigured) }\n        })\n        if (repo.gpsRecognitionEnabled && repo.isGpsConfigured) {\n            card.addView(UiKit.button(this, p, "إعادة إرسال منطقة GPS للهواتف", false).apply {\n                setOnClickListener { refreshGpsConfigToEmployeesRc15() }\n            })\n        }\n        root.addView(card)\n    }\n\n    private fun refreshGpsConfigToEmployeesRc15() {\n        if (!::directBle.isInitialized) return\n        StoreDirectLinkBridge1977.unbind(directBle)\n        runCatching { directBle.stop() }\n        directBle = BleDirectLinkClient(this) { employeeId, connected, message ->\n            runOnUiThread { markDirectBleState(employeeId, connected, message) }\n        }\n        StoreDirectLinkBridge1977.bind(directBle)\n        if (::status.isInitialized) status.text = "تم حفظ منطقة GPS — بانتظار الهاتف لإعادة المصادقة واستلام النطاق"\n        nearbyRefreshHandler.postDelayed({\n            if (!isFinishing) {\n                ensurePresenceDiscoveryRunning()\n                if (::status.isInitialized) status.text = "GPS جاهز • سيظهر الهاتف عند وصول قراءة موقع حديثة"\n            }\n        }, 600L)\n    }\n\n'''
anchor = '    private fun maybeShowGpsFirstSetup() {\n'
if helper not in s:
    if anchor not in s: raise SystemExit('missing RC14 GPS helper anchor')
    s = s.replace(anchor, helper + anchor, 1)

# Insert card in all current Store templates that contain owner shortcuts.
s = s.replace('        addOwnerShortcutCard(root)\n', '        addGpsPresenceSetupCardRc15(root)\n        addOwnerShortcutCard(root)\n')

# Make Connection Center wording explicit.
s = s.replace('"إعداد GPS وتحديد النطاق"', '"إعداد GPS وتحديد منطقة المحل والنطاق"')
s = s.replace('RC14 • Bluetooth RC12 محمي + إعداد GPS والنطاق', 'RC15 • Bluetooth RC12 محمي + GPS منطقة مباشرة')
s = s.replace('RC14 • لوحة الأقسام', 'RC15 • لوحة الأقسام')
s = s.replace('RC14 • GPS first-run setup + local-first presence', 'RC15 • visible GPS zone + live config resync')
sp.write_text(s, encoding='utf-8')

# Employee: make GPS readiness/status visible in every home layout and actively request permission.
emp = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt'
ep = ROOT / emp
s = ep.read_text(encoding='utf-8')
emp_helper = '''    private fun addEmployeeGpsStatusCardRc15(root: LinearLayout) {\n        if (!identity.isConfigured) return\n        val lm = getSystemService(LocationManager::class.java)\n        val locationOn = lm != null && (runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ||\n            runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false))\n        val hasLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||\n            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED\n        val card = UiKit.card(this, p)\n        card.addView(UiKit.sectionLabel(this, p, "GPS • منطقة المحل"))\n        val zone = if (identity.isTrustedStoreGpsConfigured) "✓ تم استلام منطقة المحل • النطاق ${identity.trustedStoreGpsRadius}م" else "لم تصل منطقة المحل بعد — احفظها من تطبيق المحل ثم أبق Bluetooth مفعّلًا لثوانٍ"\n        val reading = if (identity.lastGpsObservedAt > 0L) "آخر قراءة: ${identity.lastGpsState} • ${identity.lastGpsDistanceMeters}م • دقة ±${identity.lastGpsAccuracyMeters}م" else "لا توجد قراءة GPS حديثة"\n        card.addView(UiKit.subtitle(this, p, "$zone\\nإذن الموقع: ${if (hasLocation) "مسموح" else "مطلوب"} • خدمة الموقع: ${if (locationOn) "تعمل" else "مغلقة"}\\n$reading"))\n        card.addView(UiKit.button(this, p, if (hasLocation) "تشغيل/تحديث GPS" else "السماح بالموقع وتشغيل GPS", false).apply {\n            setOnClickListener {\n                if (!hasLocation) {\n                    requestLocationPermissionWithDisclosure("بعد السماح سيبدأ GPS تلقائيًا عند استلام منطقة المحل")\n                } else if (!locationOn) {\n                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))\n                } else {\n                    identity.autoPresence = true\n                    startPresence()\n                    status.text = if (identity.isTrustedStoreGpsConfigured) "GPS جاهز — انتظار قراءة موقع حديثة" else "GPS جاهز — انتظار استلام منطقة المحل من جهاز المحل"\n                }\n            }\n        })\n        root.addView(card)\n    }\n\n'''
anchor2 = '    private fun buildUi() {\n'
if emp_helper not in s:
    if anchor2 not in s: raise SystemExit('missing employee buildUi anchor')
    s = s.replace(anchor2, emp_helper + anchor2, 1)
s = s.replace('        addEmployeeTabs1978(root)\n', '        addEmployeeTabs1978(root)\n        addEmployeeGpsStatusCardRc15(root)\n')
s = s.replace('RC14 • Bluetooth RC12 محمي + إعداد GPS والنطاق', 'RC15 • Bluetooth RC12 محمي + GPS منطقة مباشرة')
s = s.replace('RC14 • لوحة الأقسام', 'RC15 • لوحة الأقسام')
ep.write_text(s, encoding='utf-8')

Path('RELEASE_NOTES_V2.0.0_RC15.md').write_text('''# ATTEND-PRO 2.0.0-RC15\n\nversionCode 105\n\n- Keeps protected pairing files unchanged and preserves RC12 Bluetooth advertiser/scanner/GATT-server behavior.\n- Fixes RC14 first-run GPS prompt suppression when setup was not actually completed.\n- Adds a permanent visible Store home card for GPS zone setup in all current home templates.\n- Rejects stale Store location fixes older than 15 minutes.\n- After Store GPS zone/radius is saved, recreates only the Store-side direct client object so the existing authenticated handshake resends CONFIG to already-paired employee phones.\n- Adds permanent Employee GPS-zone/readiness card with zone, radius, permission, location-service state and last GPS reading.\n- GPS remains presence/monitoring only and cannot record attendance by itself.\n''', encoding='utf-8')
Path('FIELD_TEST_CHECKLIST_RC15.md').write_text('''# RC15 GPS field test\n1. Install Store + Employee RC15 over RC14 without clearing data.\n2. Store home must visibly show "GPS • منطقة التعرف على هاتف الموظف" in every home layout.\n3. Tap "تفعيل GPS وتحديد منطقة المحل", choose 25m (or desired radius), grant location, and save.\n4. Keep Bluetooth ON on both phones for 5-15 seconds; Employee must show "تم استلام منطقة المحل" and the selected radius.\n5. On Employee, allow Location and enable the phone Location service.\n6. Employee GPS card must show a recent state/distance/accuracy.\n7. Store Connection Center should show GPS state/distance when telemetry reaches it through LAN or server fallback.\n8. Bluetooth OFF->ON recovery must remain as RC12.\n9. GPS alone must never create attendance.\n''', encoding='utf-8')

assert 'versionCode = 105' in (ROOT/'buildsrc/store-app/build.gradle.kts').read_text()
assert 'GPS • منطقة التعرف على هاتف الموظف' in sp.read_text(encoding='utf-8')
assert 'refreshGpsConfigToEmployeesRc15' in sp.read_text(encoding='utf-8')
assert 'GPS • منطقة المحل' in ep.read_text(encoding='utf-8')
assert 'REQUEST_GPS_SETUP_LOCATION_RC14' in sp.read_text(encoding='utf-8')
print('RC15 GPS zone live fix applied')
