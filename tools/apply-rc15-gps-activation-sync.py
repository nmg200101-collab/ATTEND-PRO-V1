#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')

def rep(path, old, new, count=1):
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'missing token in {path}: {old[:180]}')
    p.write_text(s.replace(old, new, count), encoding='utf-8')

# RC15 is applied after RC14. Preserve the field-approved BLE connect/reconnect flow;
# only add a CONFIG resync command after an already authenticated session.
for path in ['buildsrc/store-app/build.gradle.kts','buildsrc/employee-app/build.gradle.kts']:
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    s = s.replace('versionCode = 104', 'versionCode = 105')
    s = s.replace('versionName = "2.0.0-RC14"', 'versionName = "2.0.0-RC15"')
    p.write_text(s, encoding='utf-8')

# -----------------------------------------------------------------------------
# Store BLE client: additive CONFIG resync only. No discovery/GATT/heartbeat/
# reconnect logic is changed.
# -----------------------------------------------------------------------------
client = 'buildsrc/store-app/src/main/java/com/attendpro/store/BleDirectLinkClient.kt'
rep(client,
'''    fun isConnected(employeeId: String): Boolean = sessions[employeeId]?.let {\n        it.authenticated && it.command != null && System.currentTimeMillis() - it.lastAckAt <= ACK_STALE_MILLIS\n    } == true\n\n    fun lastAckAt(employeeId: String): Long = sessions[employeeId]?.lastAckAt ?: 0L\n''',
'''    fun isConnected(employeeId: String): Boolean = sessions[employeeId]?.let {\n        it.authenticated && it.command != null && System.currentTimeMillis() - it.lastAckAt <= ACK_STALE_MILLIS\n    } == true\n\n    // RC15: resend Store policy/GPS config over an already authenticated GATT session.\n    // This is deliberately additive and does not alter connect, heartbeat, ACK or reconnect behavior.\n    fun pushConfig(employeeId: String, config: BleDirectProtocol.Config): Boolean {\n        val s = sessions[employeeId] ?: return false\n        s.config = config\n        if (!isConnected(employeeId)) return false\n        enqueue(s, Operation(BleDirectProtocol.encodeConfig(config), Kind.CONFIG))\n        return true\n    }\n\n    fun lastAckAt(employeeId: String): Long = sessions[employeeId]?.lastAckAt ?: 0L\n''')

main = 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'

# First-run setup must not be silently suppressed by a stale preference. If GPS is
# not configured, offer setup on every Store launch until it is actually saved.
rep(main,
'''    private fun maybeShowGpsFirstSetup() {\n        if (!repo.isCentralActivationActive() || employeeManagerMode) return\n        val prefs = getSharedPreferences("gps_first_setup_rc14", MODE_PRIVATE)\n        if (prefs.getBoolean("prompted", false)) return\n        if (repo.gpsRecognitionEnabled && repo.isGpsConfigured) {\n            prefs.edit().putBoolean("prompted", true).apply()\n            return\n        }\n        prefs.edit().putBoolean("prompted", true).apply()\n        nearbyRefreshHandler.postDelayed({ if (!isFinishing) showGpsSetupDialog(firstTime = true) }, 700L)\n    }\n''',
'''    private fun maybeShowGpsFirstSetup() {\n        if (!repo.isCentralActivationActive() || employeeManagerMode) return\n        if (repo.gpsRecognitionEnabled && repo.isGpsConfigured) return\n        nearbyRefreshHandler.postDelayed({\n            if (!isFinishing && !(repo.gpsRecognitionEnabled && repo.isGpsConfigured)) showGpsSetupDialog(firstTime = true)\n        }, 700L)\n    }\n''')

# Permanent visible GPS card on the Store home screen, not hidden only in Connection Center.
rep(main,
'''        status = TextView(this).apply { text = "النظام جاهز"; textSize = 14f; setTextColor(p.muted); gravity = Gravity.CENTER }\n\n        val connectionCard = UiKit.card(this, p, 14).apply {\n''',
'''        status = TextView(this).apply { text = "النظام جاهز"; textSize = 14f; setTextColor(p.muted); gravity = Gravity.CENTER }\n\n        val gpsQuickCard = UiKit.card(this, p, 14)\n        gpsQuickCard.addView(UiKit.sectionLabel(this, p, "GPS • منطقة التعرف على الهاتف"))\n        val gpsReady = repo.gpsRecognitionEnabled && repo.isGpsConfigured\n        gpsQuickCard.addView(UiKit.statusBadge(this, p, if (gpsReady)\n            "مفعّل • النطاق ${repo.gpsRadiusMeters} متر" else "غير مهيأ — يلزم تحديد موقع المحل والنطاق", gpsReady))\n        gpsQuickCard.addView(UiKit.subtitle(this, p, "يستخدم GPS لمعرفة هل هاتف الموظف داخل نطاق المحل ويعرض المسافة بالمتر. لا يسجل حضورًا تلقائيًا."))\n        gpsQuickCard.addView(UiKit.button(this, p, if (gpsReady) "تعديل GPS والنطاق" else "تفعيل GPS وتحديد المنطقة").apply {\n            setOnClickListener { showGpsSetupDialog(firstTime = !gpsReady) }\n        })\n        root.addView(gpsQuickCard)\n\n        val connectionCard = UiKit.card(this, p, 14).apply {\n''')

# Add explicit config sync helper. Existing authenticated Bluetooth path is reused.
rep(main,
'''    private fun directBleConfig(): BleDirectProtocol.Config {\n''',
'''    private fun syncGpsConfigToConnectedEmployees() {\n        val config = directBleConfig()\n        var pushed = 0\n        repo.employees().filter { it.active && it.companionEnabled }.forEach { employee ->\n            if (directBle.pushConfig(employee.employeeId, config)) pushed++\n        }\n        if (::status.isInitialized) {\n            status.text = if (pushed > 0)\n                "✓ تم حفظ GPS والنطاق ومزامنته مع $pushed هاتف عبر Bluetooth"\n            else "✓ تم حفظ GPS والنطاق — سيصل للهاتف تلقائيًا عند أول اتصال Bluetooth موثق"\n        }\n    }\n\n    private fun directBleConfig(): BleDirectProtocol.Config {\n''')

# After either cached or live Store-location capture, immediately push new coordinates/radius.
rep(main,
'''                    if (::status.isInitialized) status.text = "✓ GPS مفعّل • موقع المحل محفوظ • النطاق ${meters}م"\n                    dialog.dismiss()\n                    refreshDashboard()\n''',
'''                    syncGpsConfigToConnectedEmployees()\n                    dialog.dismiss()\n                    refreshDashboard()\n''')
rep(main,
'''                            if (::status.isInitialized) status.text = "✓ GPS مفعّل • تم تحديد موقع المحل • النطاق ${meters}م"\n                            if (dialog.isShowing) dialog.dismiss()\n                            refreshDashboard()\n''',
'''                            syncGpsConfigToConnectedEmployees()\n                            if (dialog.isShowing) dialog.dismiss()\n                            refreshDashboard()\n''')

# If owner disables GPS, also push the disabled config so employee stops geo monitoring.
rep(main,
'''                    repo.gpsRecognitionEnabled = false\n                    if (::status.isInitialized) status.text = "تم إيقاف التعرف عبر GPS"\n                    dialog.dismiss()\n''',
'''                    repo.gpsRecognitionEnabled = false\n                    syncGpsConfigToConnectedEmployees()\n                    if (::status.isInitialized) status.text = "تم إيقاف التعرف عبر GPS"\n                    dialog.dismiss()\n''')

# Employee home: make location readiness visible and directly actionable.
emp = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt'
rep(emp,
'''        state.addView(profile); state.addView(status)\n        root.addView(state)\n\n        val link = UiKit.card(this,p)\n''',
'''        state.addView(profile); state.addView(status)\n        root.addView(state)\n\n        if (identity.isConfigured) {\n            val gpsCard = UiKit.card(this,p)\n            gpsCard.addView(UiKit.sectionLabel(this,p,"GPS • التعرف ضمن نطاق المحل"))\n            val fgLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||\n                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED\n            val bgLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||\n                checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED\n            val storeGps = identity.isTrustedStoreGpsConfigured\n            gpsCard.addView(UiKit.statusBadge(this,p, when {\n                !storeGps -> "بانتظار استلام منطقة المحل من تطبيق المحل"\n                !fgLocation -> "موقع الهاتف غير مسموح — اضغط للتفعيل"\n                !bgLocation -> "GPS يعمل عند فتح التطبيق فقط — اسمح بالموقع دائمًا للخلفية"\n                else -> "جاهز • منطقة المحل مستلمة • GPS يعمل للتعرف على القرب"\n            }, storeGps && fgLocation))\n            gpsCard.addView(UiKit.subtitle(this,p, if (storeGps)\n                "نطاق المحل المستلم ${identity.trustedStoreGpsRadius} متر. ستظهر المسافة في تطبيق المحل عند وصول قراءة حديثة."\n                else "بعد أن يحدد مدير المحل موقع المحل والنطاق، تُرسل الإعدادات لهذا الهاتف تلقائيًا عبر Bluetooth الموثق."))\n            gpsCard.addView(UiKit.button(this,p,"تفعيل/فحص إذن GPS",false).apply {\n                setOnClickListener { requestLocationPermissionWithDisclosure("بعد منح الإذن سيبدأ GPS تلقائيًا عند استلام منطقة المحل") }\n            })\n            root.addView(gpsCard)\n        }\n\n        val link = UiKit.card(this,p)\n''')

# Visible version markers.
for pth in [main, emp]:
    p = ROOT / pth
    s = p.read_text(encoding='utf-8')
    s = s.replace('RC14 • Bluetooth RC12 محمي + إعداد GPS والنطاق', 'RC15 • Bluetooth ثابت + GPS فعلي ومزامنة النطاق')
    s = s.replace('RC14 • لوحة الأقسام', 'RC15 • لوحة الأقسام')
    s = s.replace('RC14 • GPS first-run setup + local-first presence', 'RC15 • GPS visible setup + live config sync')
    p.write_text(s, encoding='utf-8')

Path('RELEASE_NOTES_V2.0.0_RC15.md').write_text('''# ATTEND-PRO 2.0.0-RC15\n\nversionCode 105\n\n- Keeps the field-approved RC12 Bluetooth discovery/ACK/reconnect behavior.\n- Fixes hidden GPS onboarding: Store now shows a permanent GPS region card and repeats first-run setup until actually configured.\n- GPS setup captures Store location and radius (5-1000m).\n- New GPS/radius settings are pushed immediately to already authenticated Employee phones over the existing secure GATT CONFIG channel.\n- If the phone is offline/disconnected, the same config is sent automatically on the next authenticated BLE session.\n- Employee home now exposes GPS readiness and location-permission action.\n- GPS remains presence/monitoring only and never creates attendance by itself.\n''', encoding='utf-8')
Path('FIELD_TEST_CHECKLIST_RC15.md').write_text('''# RC15 field test\n1. Install Store + Employee RC15 over RC14 without clearing data.\n2. Verify Bluetooth still discovers and reconnects after OFF -> ON exactly as RC12.\n3. Store home must show "GPS • منطقة التعرف على الهاتف".\n4. Tap "تفعيل GPS وتحديد المنطقة", choose e.g. 25m, enable phone location and save Store current location.\n5. With Employee connected by Bluetooth, Store should say GPS config was synchronized to the phone.\n6. Employee home GPS card should change from waiting to "منطقة المحل مستلمة" and show the radius.\n7. Grant Employee location; for Android 10+ also allow background location if you need recognition while app is closed.\n8. Within seconds, Store should show GPS state + distance in meters + accuracy/source for Employee.\n9. Walk outside/inside radius and verify distance/state changes.\n10. GPS alone must never create check-in/out.\n''', encoding='utf-8')

store_text = (ROOT/main).read_text(encoding='utf-8')
client_text = (ROOT/client).read_text(encoding='utf-8')
emp_text = (ROOT/emp).read_text(encoding='utf-8')
assert 'versionCode = 105' in (ROOT/'buildsrc/store-app/build.gradle.kts').read_text()
assert 'GPS • منطقة التعرف على الهاتف' in store_text
assert 'syncGpsConfigToConnectedEmployees()' in store_text
assert 'fun pushConfig(employeeId: String, config: BleDirectProtocol.Config): Boolean' in client_text
assert 'GPS • التعرف ضمن نطاق المحل' in emp_text
print('RC15 GPS activation + live config sync applied')
