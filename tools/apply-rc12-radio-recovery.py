#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')

def rep(path, old, new, count=1):
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'missing token in {path}: {old[:160]}')
    p.write_text(s.replace(old, new, count), encoding='utf-8')

# RC12 is applied after RC8 + RC11. Keep all current UI/features.
for path in ['buildsrc/store-app/build.gradle.kts','buildsrc/employee-app/build.gradle.kts']:
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    s = s.replace('versionCode = 101', 'versionCode = 102')
    s = s.replace('versionName = "2.0.0-RC11"', 'versionName = "2.0.0-RC12"')
    p.write_text(s, encoding='utf-8')

# -----------------------------------------------------------------------------
# Employee: Bluetooth OFF -> ON must recreate advertiser + GATT immediately.
# Android/OEM stacks invalidate the old advertiser/GATT objects across adapter reset.
# -----------------------------------------------------------------------------
presence = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'
rep(presence,
'''import android.content.Intent\n''',
'''import android.content.Intent\nimport android.content.BroadcastReceiver\nimport android.content.Context\nimport android.content.IntentFilter\nimport android.bluetooth.BluetoothAdapter\n''')

rep(presence,
'''    private var networkCallback1981: ConnectivityManager.NetworkCallback? = null\n''',
'''    private var networkCallback1981: ConnectivityManager.NetworkCallback? = null\n    private var bluetoothStateReceiverRegistered = false\n    private val bluetoothRadioRestart = Runnable {\n        if (!identity.isConfigured || !identity.autoPresence) return@Runnable\n        // Adapter OFF/ON invalidates the old advertiser and GATT server on several OEM stacks.\n        bluetooth.stop()\n        directBle.stop()\n        BleChallengeInbox.stop()\n        handler.postDelayed({ if (identity.isConfigured && identity.autoPresence) startBleChannelsIfPermitted() }, 350L)\n    }\n    private val bluetoothStateReceiver = object : BroadcastReceiver() {\n        override fun onReceive(context: Context?, intent: Intent?) {\n            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return\n            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {\n                BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {\n                    handler.removeCallbacks(bluetoothRadioRestart)\n                    bluetooth.stop()\n                    directBle.stop()\n                    BleChallengeInbox.stop()\n                    identity.lastBleAdvertisingState = "Bluetooth مغلق — بانتظار تشغيله لإعادة البث تلقائيًا"\n                    identity.lastBleDirectState = "GATT متوقف مؤقتًا — سيعاد إنشاؤه عند تشغيل Bluetooth"\n                }\n                BluetoothAdapter.STATE_ON -> {\n                    identity.lastBleDirectState = "Bluetooth عاد للعمل — جارٍ إعادة إنشاء Advertising/GATT"\n                    handler.removeCallbacks(bluetoothRadioRestart)\n                    handler.postDelayed(bluetoothRadioRestart, 650L)\n                }\n            }\n        }\n    }\n''')

rep(presence,
'''        registerNetworkCallback1981()\n    }\n''',
'''        registerNetworkCallback1981()\n        val bluetoothFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)\n        runCatching {\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {\n                registerReceiver(bluetoothStateReceiver, bluetoothFilter, Context.RECEIVER_NOT_EXPORTED)\n            } else {\n                @Suppress("DEPRECATION")\n                registerReceiver(bluetoothStateReceiver, bluetoothFilter)\n            }\n        }.onSuccess { bluetoothStateReceiverRegistered = true }\n    }\n''')

rep(presence,
'''    override fun onDestroy(){\n        identity.presenceServiceHeartbeatAt = 0L\n        handler.removeCallbacks(challengePoller);network.stop();bluetooth.stop();localChallenges.stop();directBle.stop();geoMonitor.stop();BleChallengeInbox.stop();voicePrompter.shutdown()\n        unregisterNetworkCallback1981()\n        super.onDestroy()\n    }\n''',
'''    override fun onDestroy(){\n        identity.presenceServiceHeartbeatAt = 0L\n        handler.removeCallbacks(challengePoller)\n        handler.removeCallbacks(bluetoothRadioRestart)\n        if (bluetoothStateReceiverRegistered) runCatching { unregisterReceiver(bluetoothStateReceiver) }\n        bluetoothStateReceiverRegistered = false\n        network.stop();bluetooth.stop();localChallenges.stop();directBle.stop();geoMonitor.stop();BleChallengeInbox.stop();voicePrompter.shutdown()\n        unregisterNetworkCallback1981()\n        super.onDestroy()\n    }\n''')

# -----------------------------------------------------------------------------
# Store: scanner must not stay logically "scanning" after Bluetooth adapter reset.
# Register for adapter changes and immediately create a fresh scanner on STATE_ON.
# -----------------------------------------------------------------------------
scanner = 'buildsrc/store-app/src/main/java/com/attendpro/store/BleEmployeeScanner.kt'
rep(scanner,
'''import android.content.pm.PackageManager\n''',
'''import android.content.pm.PackageManager\nimport android.content.BroadcastReceiver\nimport android.content.Context\nimport android.content.Intent\nimport android.content.IntentFilter\n''')

rep(scanner,
'''    private var parsedAttendCount = 0L\n''',
'''    private var parsedAttendCount = 0L\n    private var stateReceiverRegistered = false\n    private val adapterStateReceiver = object : BroadcastReceiver() {\n        @SuppressLint("MissingPermission")\n        override fun onReceive(context: Context?, intent: Intent?) {\n            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return\n            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {\n                BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {\n                    handler.removeCallbacks(retry)\n                    if (scanning && hasPermissions()) runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }\n                    scanning = false\n                    lastResultAt = 0L\n                    onStatus("Bluetooth توقف — سيعاد اكتشاف الأجهزة تلقائيًا فور تشغيله")\n                }\n                BluetoothAdapter.STATE_ON -> {\n                    // A BluetoothScanner obtained before adapter reset is not reusable reliably.\n                    scanning = false\n                    lastResultAt = 0L\n                    startedAt = 0L\n                    handler.removeCallbacks(retry)\n                    handler.postDelayed(retry, 500L)\n                    onStatus("Bluetooth عاد للعمل — إعادة تشغيل اكتشاف الموظفين الآن")\n                }\n            }\n        }\n    }\n''')

rep(scanner,
'''    fun start() {\n        wanted = true\n''',
'''    fun start() {\n        wanted = true\n        if (!stateReceiverRegistered) {\n            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)\n            runCatching {\n                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {\n                    activity.registerReceiver(adapterStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)\n                } else {\n                    @Suppress("DEPRECATION")\n                    activity.registerReceiver(adapterStateReceiver, filter)\n                }\n            }.onSuccess { stateReceiverRegistered = true }\n        }\n''')

# Watchdog also repairs any silent scanner death even if OEM does not send a useful callback.
rep(scanner,
'''        override fun run() {\n            if (wanted && scanning) {\n                val now = System.currentTimeMillis()\n                val reference = if (lastResultAt > 0L) lastResultAt else startedAt\n                if (reference > 0L && now - reference > 8_000L && !unfilteredFallback) {\n                    unfilteredFallback = true\n                    restartScan("تفعيل مسح BLE المتوافق مع الأجهزة تلقائيًا")\n                } else if (reference > 0L && now - reference > 25_000L) {\n                    restartScan("إعادة تنشيط مسح BLE تلقائيًا")\n                }\n            }\n            if (wanted) handler.postDelayed(this, 10_000L)\n        }\n''',
'''        override fun run() {\n            if (wanted && adapter?.isEnabled == true && !scanning) {\n                startInternal()\n            } else if (wanted && scanning) {\n                val now = System.currentTimeMillis()\n                val reference = if (lastResultAt > 0L) lastResultAt else startedAt\n                if (reference > 0L && now - reference > 12_000L) {\n                    restartScan("إعادة تنشيط مسح BLE تلقائيًا")\n                }\n            }\n            if (wanted) handler.postDelayed(this, 3_000L)\n        }\n''')

rep(scanner,
'''        handler.postDelayed(watchdog, 10_000L)\n''',
'''        handler.postDelayed(watchdog, 3_000L)\n''')

rep(scanner,
'''        scanning = false\n        unfilteredFallback = true\n    }\n''',
'''        scanning = false\n        unfilteredFallback = true\n        if (stateReceiverRegistered) runCatching { activity.unregisterReceiver(adapterStateReceiver) }\n        stateReceiverRegistered = false\n    }\n''')

rep(scanner,
'''        scanning = false\n        lastResultAt = System.currentTimeMillis()\n        onStatus(reason)\n''',
'''        scanning = false\n        lastResultAt = 0L\n        startedAt = 0L\n        onStatus(reason)\n''')

# -----------------------------------------------------------------------------
# GPS: consume a fresh last-known fix immediately, then continue live updates.
# This removes the long delay where GPS is enabled but no observation is shown.
# Weak fixes are persisted as UNKNOWN so diagnostics can still prove GPS is alive.
# -----------------------------------------------------------------------------
geo = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/OfflineGeoMonitor.kt'
rep(geo,
'''        providers.forEach { provider ->\n            runCatching { lm.requestLocationUpdates(provider, UPDATE_INTERVAL_MILLIS, MIN_DISTANCE_METERS, listener, Looper.getMainLooper()) }\n        }\n        onStatus("GPS يعمل — يتم التعرف على وجود الهاتف قرب المحل")\n''',
'''        providers.forEach { provider ->\n            runCatching { lm.requestLocationUpdates(provider, 5_000L, 2f, listener, Looper.getMainLooper()) }\n        }\n        // Do not wait tens of seconds for the first new fix. Use the freshest recent cached fix.\n        providers.mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }\n            .filter { System.currentTimeMillis() - it.time <= 10 * 60_000L }\n            .maxByOrNull { it.time }\n            ?.let { listener.onLocationChanged(it) }\n        onStatus("GPS يعمل — يتم التعرف على وجود الهاتف قرب المحل")\n''')

rep(geo,
'''            if (!accuracy.isFinite() || accuracy > MAX_ACCEPTED_ACCURACY_METERS) {\n                onStatus("GPS يعمل لكن دقة القراءة ضعيفة (${accuracy.toInt()}م)")\n                return\n            }\n''',
'''            if (!accuracy.isFinite() || accuracy > MAX_ACCEPTED_ACCURACY_METERS) {\n                identity.lastGpsObservedAt = now\n                identity.lastGpsAccuracyMeters = if (accuracy.isFinite()) accuracy.toInt().coerceAtLeast(0) else -1\n                identity.lastGpsDistanceMeters = -1\n                identity.lastGpsState = STATE_UNKNOWN\n                onObservation(STATE_UNKNOWN, -1, identity.lastGpsAccuracyMeters, now)\n                onStatus("GPS يعمل لكن دقة القراءة ضعيفة (${if (accuracy.isFinite()) accuracy.toInt() else -1}م)")\n                return\n            }\n''')

# Visible markers only; do not alter layout/settings behavior.
emp_main = ROOT / 'buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt'
s = emp_main.read_text(encoding='utf-8')
s = s.replace('RC11 • إصلاح Bluetooth/GPS بدون رجوع للتحديثات', 'RC12 • استعادة فورية Bluetooth/GPS بعد الفصل')
emp_main.write_text(s, encoding='utf-8')

store_main = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
s = store_main.read_text(encoding='utf-8')
s = s.replace('RC11 • إصلاح Bluetooth/GPS بدون رجوع', 'RC12 • استعادة فورية Bluetooth/GPS بعد الفصل')
s = s.replace('RC11 • لوحة الأقسام', 'RC12 • لوحة الأقسام')
s = s.replace('RC11 • live Bluetooth/GPS fix', 'RC12 • Bluetooth radio recovery + GPS fast fix')
store_main.write_text(s, encoding='utf-8')

Path('RELEASE_NOTES_V2.0.0_RC12.md').write_text('''# ATTEND-PRO 2.0.0-RC12\n\nversionCode 102\n\n- Keeps all current RC8/RC11 UI and management updates.\n- Employee PresenceService listens for Bluetooth adapter OFF/ON and recreates Advertising, GATT and challenge scan immediately after ON.\n- Store BLE scanner listens for adapter OFF/ON, clears stale scanner state and restarts within about 0.5s after ON.\n- Store watchdog repairs silent scanner death every 3 seconds instead of waiting a long stale window.\n- GPS consumes a recent last-known fix immediately and requests faster live updates.\n- Weak GPS fixes are recorded as UNKNOWN instead of looking like GPS never ran.\n- Protected pairing protocol sources remain unchanged.\n''', encoding='utf-8')
Path('FIELD_TEST_CHECKLIST_RC12.md').write_text('''# RC12 field test\n1. Install Store and Employee RC12 over the existing official apps; do not clear data.\n2. Confirm both show RC12 and all owner/settings updates remain.\n3. With Bluetooth ON, confirm Employee Advertising and Store discovery -> GATT -> authenticated ACK.\n4. Turn Bluetooth OFF on either phone, wait 3 seconds, turn ON. Discovery must return automatically without pressing restart.\n5. Repeat OFF/ON twice to prove recovery is repeatable.\n6. GPS: Location ON + permission + Store coordinates configured. A cached/recent or live fix must update GPS state promptly; weak fixes show UNKNOWN with accuracy rather than no reading.\n''', encoding='utf-8')

# Build-time assertions.
assert 'versionCode = 102' in (ROOT/'buildsrc/store-app/build.gradle.kts').read_text()
assert 'bluetoothStateReceiver' in (ROOT/presence).read_text()
assert 'BluetoothAdapter.STATE_ON' in (ROOT/scanner).read_text()
assert 'handler.postDelayed(retry, 500L)' in (ROOT/scanner).read_text()
assert 'getLastKnownLocation' in (ROOT/geo).read_text()
print('RC12 radio/GPS recovery applied')
