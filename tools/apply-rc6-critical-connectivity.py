#!/usr/bin/env python3
from pathlib import Path

R = Path('.')

def patch(path, old, new, count=1):
    p = R / path
    s = p.read_text(encoding='utf-8')
    if s.count(old) < count:
        raise SystemExit(f'missing anchor in {path}: {old[:120]!r}')
    s = s.replace(old, new, count)
    p.write_text(s, encoding='utf-8')

# 1) BLE advertiser: keep the primary advertisement inside the legacy 31-byte budget.
patch(
    'buildsrc/employee-app/src/main/java/com/attendpro/employee/BlePresenceAdvertiser.kt',
    '''        val data = AdvertiseData.Builder()\n            .setIncludeDeviceName(false)\n            .setIncludeTxPowerLevel(false)\n            .addServiceUuid(BleProtocol.SERVICE_UUID)\n            .addManufacturerData(BleProtocol.MANUFACTURER_ID, payload)\n            .build()\n''',
    '''        // RC6: the authenticated manufacturer frame alone is kept in the primary legacy\n        // advertisement. Adding a 128-bit service UUID here can overflow the 31-byte budget on\n        // Samsung/Motorola and cause ADVERTISE_FAILED_DATA_TOO_LARGE. GATT UUID stays in scan response.\n        val data = AdvertiseData.Builder()\n            .setIncludeDeviceName(false)\n            .setIncludeTxPowerLevel(false)\n            .addManufacturerData(BleProtocol.MANUFACTURER_ID, payload)\n            .build()\n'''
)

# 2) Store scanner: fall back to unfiltered scan if OEM filtering produces no results.
patch(
    'buildsrc/store-app/src/main/java/com/attendpro/store/BleEmployeeScanner.kt',
    '    private var lastResultAt = 0L\n',
    '    private var lastResultAt = 0L\n    private var unfilteredFallback = false\n'
)
patch(
    'buildsrc/store-app/src/main/java/com/attendpro/store/BleEmployeeScanner.kt',
    '''                if (reference > 0L && now - reference > 25_000L) {\n                    restartScan("إعادة تنشيط مسح BLE تلقائيًا")\n                }\n''',
    '''                if (reference > 0L && now - reference > 8_000L && !unfilteredFallback) {\n                    unfilteredFallback = true\n                    restartScan("تفعيل مسح BLE المتوافق مع الأجهزة تلقائيًا")\n                } else if (reference > 0L && now - reference > 25_000L) {\n                    restartScan("إعادة تنشيط مسح BLE تلقائيًا")\n                }\n'''
)
patch(
    'buildsrc/store-app/src/main/java/com/attendpro/store/BleEmployeeScanner.kt',
    '''        runCatching { scanner.startScan(filters, settings, callback) }\n''',
    '''        runCatching { scanner.startScan(if (unfilteredFallback) null else filters, settings, callback) }\n'''
)
patch(
    'buildsrc/store-app/src/main/java/com/attendpro/store/BleEmployeeScanner.kt',
    '''        scanning = false\n    }\n\n\n\n    @SuppressLint("MissingPermission")\n    private fun restartScan''',
    '''        scanning = false\n        unfilteredFallback = false\n    }\n\n\n\n    @SuppressLint("MissingPermission")\n    private fun restartScan'''
)

# 3) GPS: monitoring is a core recognition channel, independent of optional arrival-alert UI.
patch(
    'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt',
    '''            onEntered = { distance, accuracy ->\n                AttendanceRequestNotifier.notifyGeoArrival(this, identity.displayName, identity.trustedStoreName, distance)\n                if (identity.employeeVoicePromptsEnabled) {\n                    voicePrompter.speak("يا ${identity.displayName}، تم التعرف على هاتفك بالقرب من ${identity.trustedStoreName} عبر الموقع. هذا لا يسجل الحضور تلقائيًا")\n                }\n                identity.lastGpsDistanceMeters = distance\n                identity.lastGpsAccuracyMeters = accuracy\n            },\n''',
    '''            onEntered = { distance, accuracy ->\n                if (identity.geoArrivalAlertsEnabled) {\n                    AttendanceRequestNotifier.notifyGeoArrival(this, identity.displayName, identity.trustedStoreName, distance)\n                    if (identity.employeeVoicePromptsEnabled) {\n                        voicePrompter.speak("يا ${identity.displayName}، تم التعرف على هاتفك بالقرب من ${identity.trustedStoreName} عبر الموقع. هذا لا يسجل الحضور تلقائيًا")\n                    }\n                }\n                identity.lastGpsDistanceMeters = distance\n                identity.lastGpsAccuracyMeters = accuracy\n            },\n'''
)
patch(
    'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt',
    '''        locationMonitoringAllowedForRun = identity.geoArrivalAlertsEnabled && identity.isTrustedStoreGpsConfigured && hasForegroundLocation &&\n            (!backgroundRestart || hasBackgroundLocation)\n''',
    '''        locationMonitoringAllowedForRun = identity.isTrustedStoreGpsConfigured && hasForegroundLocation &&\n            (!backgroundRestart || hasBackgroundLocation)\n'''
)
patch(
    'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt',
    '''    private fun maybeUploadGpsObservation(state: String, distance: Int, accuracy: Int, observedAt: Long) {\n        if (!identity.serverLinked || !identity.isConfigured || identity.serverUrl.isBlank()) return\n        val now = System.currentTimeMillis()\n        if (now - identity.lastGpsServerUploadAt < 20_000L) return\n        identity.lastGpsState = state\n        identity.lastGpsObservedAt = observedAt\n        identity.lastGpsDistanceMeters = distance\n        identity.lastGpsAccuracyMeters = accuracy\n        identity.lastGpsServerUploadAt = now\n        Thread {\n            CentralServerClient.sendEmployeeGeoObservation(\n''',
    '''    private fun maybeUploadGpsObservation(state: String, distance: Int, accuracy: Int, observedAt: Long) {\n        // Always persist the latest GPS recognition locally first. A stale serverLinked flag must not\n        // suppress fresh GPS telemetry after app/server restarts.\n        identity.lastGpsState = state\n        identity.lastGpsObservedAt = observedAt\n        identity.lastGpsDistanceMeters = distance\n        identity.lastGpsAccuracyMeters = accuracy\n        if (!identity.isConfigured || identity.serverUrl.isBlank()) return\n        val now = System.currentTimeMillis()\n        if (now - identity.lastGpsServerUploadAt < 20_000L) return\n        identity.lastGpsServerUploadAt = now\n        Thread {\n            CentralServerClient.sendEmployeeGeoObservation(\n'''
)
patch(
    'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt',
    '''                observedAt\n            ).onFailure {\n                // Do not mark the employee server link dead because a geo telemetry write failed.\n                // Challenge polling/registration remains the authoritative server connection.\n            }\n''',
    '''                observedAt\n            ).onSuccess { identity.serverLinked = true }.onFailure {\n                // Do not mark the employee server link dead because a geo telemetry write failed.\n                // Challenge polling/registration remains the authoritative server connection.\n            }\n'''
)

# 4) Employee activity: GPS permission is requested whenever the paired store has coordinates,
# not only when the optional arrival notification is enabled.
patch(
    'buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt',
    '''    private fun ensureGeoPermissionIfNeeded() {\n        if (!identity.geoArrivalAlertsEnabled || !identity.isTrustedStoreGpsConfigured) return\n''',
    '''    private fun ensureGeoPermissionIfNeeded() {\n        if (!identity.isTrustedStoreGpsConfigured) return\n'''
)
patch(
    'buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt',
    '''        startBackgroundPresence()\n        status.text = "الظهور التلقائي يعمل عبر Bluetooth وWi‑Fi/Hotspot والخادم"\n''',
    '''        startBackgroundPresence()\n        ensureGeoPermissionIfNeeded()\n        status.text = "الظهور التلقائي يعمل عبر Bluetooth وWi‑Fi/Hotspot والخادم؛ GPS يعمل عند منح الموقع"\n'''
)
patch(
    'buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt',
    '''                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && identity.geoArrivalAlertsEnabled && identity.isTrustedStoreGpsConfigured &&\n''',
    '''                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && identity.isTrustedStoreGpsConfigured &&\n'''
)

# 5) Local challenge dispatch should work from scheduled BroadcastReceiver too.
patch(
    'buildsrc/store-app/src/main/java/com/attendpro/store/ChallengeDispatch1928.kt',
    'import android.app.Activity\n',
    'import android.content.Context\n'
)
patch(
    'buildsrc/store-app/src/main/java/com/attendpro/store/ChallengeDispatch1928.kt',
    '    fun send(activity: Activity, employeeId: String, secret: ByteArray, method: AttendanceMethod,',
    '    fun send(activity: Context, employeeId: String, secret: ByteArray, method: AttendanceMethod,'
)

# 6) Presence proof control: local BLE/LAN first, server in parallel when available.
patch(
    'buildsrc/store-app/src/main/java/com/attendpro/store/PresenceProofControlActivity.kt',
    'import com.attendpro.core.StoreRepository\n',
    'import com.attendpro.core.StoreRepository\nimport com.attendpro.core.SecretCodec\n'
)
patch(
    'buildsrc/store-app/src/main/java/com/attendpro/store/PresenceProofControlActivity.kt',
    '''    private fun sendNow(employeeId:String,name:String,method:AttendanceMethod,action:AttendanceAction) {\n        if(!repo.hasCentralCredentials() || repo.serverUrl.isBlank()) { AlertDialog.Builder(this).setMessage("الإرسال اليدوي المجدول يحتاج اتصال الخادم. يمكن استخدام زر إثبات الحضور في الشاشة الرئيسية للقناة المحلية.").setPositiveButton("حسنًا",null).show(); return }\n        Thread { val r=CentralServerClient.createPresenceChallenge(repo.serverUrl,repo.centralAccessToken,repo.storeId,DeviceIdentity(this),employeeId,method,action); runOnUiThread { AlertDialog.Builder(this).setMessage(if(r.isSuccess) "تم إرسال طلب الإثبات إلى $name." else "تعذر الإرسال: ${r.exceptionOrNull()?.message ?: "خطأ"}").setPositiveButton("حسنًا",null).show() } }.start()\n    }\n''',
    '''    private fun sendNow(employeeId:String,name:String,method:AttendanceMethod,action:AttendanceAction) {\n        val e = repo.employees().firstOrNull { it.employeeId == employeeId } ?: return\n        val secret = SecretCodec.decode(e.pairingSecret) ?: ByteArray(0)\n        val localSent = ChallengeDispatch1928.send(this, employeeId, secret, method, action = action)\n        if(!repo.hasCentralCredentials() || repo.serverUrl.isBlank()) {\n            AlertDialog.Builder(this).setMessage(if(localSent) "تم إرسال طلب الإثبات إلى $name محليًا عبر Bluetooth/Wi‑Fi." else "تعذر الإرسال المحلي. تأكد من Bluetooth أو اتصال الشبكة المحلية.").setPositiveButton("حسنًا",null).show(); return\n        }\n        Thread {\n            val r=CentralServerClient.createPresenceChallenge(repo.serverUrl,repo.centralAccessToken,repo.storeId,DeviceIdentity(this),employeeId,method,action)\n            runOnUiThread {\n                val message = when {\n                    r.isSuccess && localSent -> "تم إرسال طلب الإثبات إلى $name محليًا وعبر الخادم."\n                    r.isSuccess -> "تم إرسال طلب الإثبات إلى $name عبر الخادم."\n                    localSent -> "تم إرسال الطلب محليًا إلى $name؛ تعذر الخادم وسيستمر الطلب المحلي."\n                    else -> "تعذر الإرسال المحلي والخادم: ${r.exceptionOrNull()?.message ?: "خطأ"}"\n                }\n                AlertDialog.Builder(this).setMessage(message).setPositiveButton("حسنًا",null).show()\n            }\n        }.start()\n    }\n'''
)
old_receiver = '''        val pending=goAsync(); Thread { try { val repo=StoreRepository(context); val id=prefs.getString("employeeId","").orEmpty(); val e=repo.employees().firstOrNull{it.employeeId==id && it.active && it.companionEnabled}; if(e!=null && repo.hasCentralCredentials() && repo.serverUrl.isNotBlank()) { val method=runCatching{AttendanceMethod.valueOf(prefs.getString("method",AttendanceMethod.PHONE_BLE_BIOMETRIC.name)!!)}.getOrDefault(AttendanceMethod.PHONE_BLE_BIOMETRIC); val action=runCatching{AttendanceAction.valueOf(prefs.getString("action",AttendanceAction.CHECK_IN.name)!!)}.getOrDefault(AttendanceAction.CHECK_IN); CentralServerClient.createPresenceChallenge(repo.serverUrl,repo.centralAccessToken,repo.storeId,DeviceIdentity(context),e.employeeId,method,action) } } finally { PresenceProofScheduler.schedule(context); pending.finish() } }.start()\n'''
new_receiver = '''        val pending=goAsync(); Thread { try {\n            val repo=StoreRepository(context); val id=prefs.getString("employeeId","").orEmpty()\n            val e=repo.employees().firstOrNull{it.employeeId==id && it.active && it.companionEnabled}\n            if(e!=null) {\n                val method=runCatching{AttendanceMethod.valueOf(prefs.getString("method",AttendanceMethod.PHONE_BLE_BIOMETRIC.name)!!)}.getOrDefault(AttendanceMethod.PHONE_BLE_BIOMETRIC)\n                val action=runCatching{AttendanceAction.valueOf(prefs.getString("action",AttendanceAction.CHECK_IN.name)!!)}.getOrDefault(AttendanceAction.CHECK_IN)\n                val secret=SecretCodec.decode(e.pairingSecret) ?: ByteArray(0)\n                ChallengeDispatch1928.send(context,e.employeeId,secret,method,action=action)\n                if(repo.hasCentralCredentials() && repo.serverUrl.isNotBlank()) CentralServerClient.createPresenceChallenge(repo.serverUrl,repo.centralAccessToken,repo.storeId,DeviceIdentity(context),e.employeeId,method,action)\n            }\n        } finally { PresenceProofScheduler.schedule(context); pending.finish() } }.start()\n'''
patch('buildsrc/store-app/src/main/java/com/attendpro/store/PresenceProofControlActivity.kt', old_receiver, new_receiver)

# 7) Owner management: "all settings" opens the complete owner hub, hub is full-height, and
# the old StoreSettingsActivity remains available as advanced settings instead of masquerading as all settings.
patch(
    'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt',
    '        OwnerShortcut("all_settings", "جميع إعدادات مدير المحل"),\n',
    '        OwnerShortcut("all_settings", "عرض جميع إعدادات مدير المحل"),\n'
)
patch(
    'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt',
    '            "all_settings" -> startActivity(Intent(this, StoreSettingsActivity::class.java))\n',
    '            "all_settings" -> showStoreOwnerHub()\n'
)
patch(
    'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt',
    '        ownerShortcutCatalog().forEach { shortcut ->\n',
    '        ownerShortcutCatalog().filterNot { it.id == "all_settings" }.forEach { shortcut ->\n'
)
patch(
    'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt',
    '''        content.addView(UiKit.button(this, p, "تخصيص اختصارات الشاشة الرئيسية").apply {\n''',
    '''        content.addView(UiKit.button(this, p, "الإعدادات المتقدمة والسياسات", false).apply {\n            setOnClickListener {\n                hubDialog?.dismiss()\n                startActivity(Intent(this@MainActivity, StoreSettingsActivity::class.java))\n            }\n        })\n        content.addView(UiKit.button(this, p, "تخصيص اختصارات الشاشة الرئيسية").apply {\n'''
)
patch(
    'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt',
    '''        hubDialog.show()\n''',
    '''        hubDialog.show()\n        hubDialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)\n'''
)

# 8) Version RC6.
for path in ['buildsrc/store-app/build.gradle.kts','buildsrc/employee-app/build.gradle.kts']:
    patch(path, 'versionCode = 95', 'versionCode = 96')
    patch(path, 'versionName = "2.0.0-RC5"', 'versionName = "2.0.0-RC6"')

# Guide contract.
contract = R/'tools/check-guide-release-contract.py'
s = contract.read_text(encoding='utf-8').replace('EXPECTED_CODE = 95','EXPECTED_CODE = 96').replace('EXPECTED_NAME = "2.0.0-RC5"','EXPECTED_NAME = "2.0.0-RC6"').replace('RELEASE_NOTES_V2.0.0_RC5.md','RELEASE_NOTES_V2.0.0_RC6.md').replace('versionCode 95','versionCode 96')
contract.write_text(s,encoding='utf-8')

# RC6 release notes + field checklist.
(R/'RELEASE_NOTES_V2.0.0_RC6.md').write_text('''# ATTEND-PRO 2.0.0-RC6\n\nversionCode 96\n\n## Critical fixes\n- Bluetooth presence advertising restored to a legacy-31-byte-safe authenticated frame for Samsung/Motorola compatibility.\n- Store BLE scanner adds an OEM-compatible unfiltered fallback when filtered scan yields no results.\n- GPS recognition no longer depends on the optional arrival-alert toggle or a stale local serverLinked flag.\n- GPS location permission is requested whenever the paired store has GPS coordinates.\n- Presence-proof requests can be sent locally over BLE/LAN and through the central server. Scheduled requests also use local channels.\n- Store Management now exposes the complete owner hub in a full-height scrollable view. Presence-proof controls are visible in that hub.\n- Protected pairing protocol files remain unchanged.\n\n## Safety\nGPS remains recognition/telemetry only and never records attendance by itself. Employee verification is still required for attendance proof.\n''',encoding='utf-8')
(R/'FIELD_TEST_CHECKLIST_RC6.md').write_text('''# RC6 field test\n\n1. Install Store + Employee RC6 over the current signed versions.\n2. Keep Wi-Fi off on both phones, turn Bluetooth on, grant Nearby devices. Confirm employee appears as Bluetooth BLE authenticated.\n3. Test Samsung and Motorola if available. Keep screen locked for at least 30 seconds and confirm rediscovery.\n4. Configure Store GPS, ensure Employee link contains Store GPS, grant location. Confirm INSIDE/NEAR appears in Store without recording attendance.\n5. Open Store > Management. Confirm all owner options are visible by scrolling, including Send proof now and Presence proof control.\n6. Send proof now with internet off while phones are near/on same LAN; confirm Employee notification arrives locally.\n7. Enable scheduled proof; confirm the scheduled request is dispatched locally and through server when internet exists.\n8. Re-test QR/Bluetooth pairing; protected pairing files must be unchanged.\n''',encoding='utf-8')

# Convert official build workflow to RC6 and rename it.
wf = R/'.github/workflows/build-v2-rc5.yml'
w = wf.read_text(encoding='utf-8')
w = w.replace('RC5','RC6').replace('rc5','rc6').replace('versionCode = 95','versionCode = 96').replace("versionCode='95'", "versionCode='96'").replace('version-code \"$PLAY_APK\")\" = \"95\"','version-code \"$PLAY_APK\")\" = \"96\"').replace('Version code: 95','Version code: 96')
# Handle simple remaining standalone expected 95 occurrences used by the workflow.
w = w.replace("versionCode='95'", "versionCode='96'")
w = w.replace('= "95"', '= "96"')
newwf = R/'.github/workflows/build-v2-rc6.yml'
newwf.write_text(w,encoding='utf-8')
wf.unlink()

print('RC6 critical fixes applied')
