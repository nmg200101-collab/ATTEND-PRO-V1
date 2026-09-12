#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')

def rep(path, old, new):
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'missing token in {path}: {old[:120]}')
    p.write_text(s.replace(old, new, 1), encoding='utf-8')

for path in ['buildsrc/store-app/build.gradle.kts','buildsrc/employee-app/build.gradle.kts']:
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    s = s.replace('versionCode = 97', 'versionCode = 98')
    s = s.replace('versionName = "2.0.0-RC7"', 'versionName = "2.0.0-RC8"')
    p.write_text(s, encoding='utf-8')

main = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt'

rep(main,
'''    @Volatile private var pendingPairingCode: String = ""
    private var pairingSessionDialog: AlertDialog? = null''',
'''    @Volatile private var pendingPairingCode: String = ""
    private var activePairingMode: PairingDiscovery.Mode = PairingDiscovery.Mode.ALL
    private var pairingRetryCount: Int = 0
    private val pairingRetryHandler = Handler(Looper.getMainLooper())
    private val pairingRetryTask = object : Runnable {
        override fun run() {
            if (!::identity.isInitialized || identity.isConfigured) return
            if (pairingRetryCount >= 3) {
                updatePairingSessionStatus("لم يكتمل ACK بعد 4 محاولات. أبقِ شاشة الربط مفتوحة وتأكد أن Bluetooth يعمل في الهاتفين، ثم أعد المحاولة.")
                return
            }
            pairingRetryCount += 1
            updatePairingSessionStatus("إعادة محاولة الربط ${pairingRetryCount + 1}/4 عبر ${if (activePairingMode == PairingDiscovery.Mode.BLUETOOTH_ONLY) "Bluetooth" else if (activePairingMode == PairingDiscovery.Mode.WIFI_HOTSPOT_ONLY) "Wi‑Fi/Hotspot" else "Bluetooth + Wi‑Fi"}…")
            pairingDiscovery.start(pendingPairingCode, activePairingMode)
            pairingRetryHandler.postDelayed(this, 15_000L)
        }
    }
    private var pairingSessionDialog: AlertDialog? = null''')

rep(main,
'''    private fun updatePairingSessionStatus(message: String) {
        pairingSessionStatus?.text = message
    }

    private fun showPairingCenter(){''',
'''    private fun updatePairingSessionStatus(message: String) {
        pairingSessionStatus?.text = message
    }

    private fun startPairingDiscoveryReliable(expectedCode: String = "", mode: PairingDiscovery.Mode = PairingDiscovery.Mode.ALL) {
        pendingPairingCode = expectedCode.replace(Regex("[^A-Za-z0-9]"), "").uppercase().take(8)
        activePairingMode = mode
        pairingRetryCount = 0
        pairingRetryHandler.removeCallbacks(pairingRetryTask)
        pairingDiscovery.start(pendingPairingCode, activePairingMode)
        pairingRetryHandler.postDelayed(pairingRetryTask, 15_000L)
    }

    private fun stopPairingDiscoveryReliable() {
        pairingRetryHandler.removeCallbacks(pairingRetryTask)
        pairingRetryCount = 0
        pairingDiscovery.stop()
    }

    private fun showPairingCenter(){''')

rep(main,
'''            pairingDiscovery.start(requestedMode = PairingDiscovery.Mode.BLUETOOTH_ONLY)''',
'''            startPairingDiscoveryReliable(mode = PairingDiscovery.Mode.BLUETOOTH_ONLY)''')
rep(main,
'''            pairingDiscovery.start(requestedMode = PairingDiscovery.Mode.WIFI_HOTSPOT_ONLY)''',
'''            startPairingDiscoveryReliable(mode = PairingDiscovery.Mode.WIFI_HOTSPOT_ONLY)''')
rep(main,
'''            pairingDiscovery.start(requestedMode = PairingDiscovery.Mode.ALL)''',
'''            startPairingDiscoveryReliable(mode = PairingDiscovery.Mode.ALL)''')
rep(main,
'''            pairingDiscovery.stop()
            pendingPairingCode = ""
            updatePairingSessionStatus("تم إيقاف البحث. اختر طريقة للبدء من جديد.")''',
'''            stopPairingDiscoveryReliable()
            pendingPairingCode = ""
            updatePairingSessionStatus("تم إيقاف البحث. اختر طريقة للبدء من جديد.")''')
rep(main,
'''                pairingDiscovery.stop()
                pendingPairingCode = ""''',
'''                stopPairingDiscoveryReliable()
                pendingPairingCode = ""''')

rep(main,
'''        pairingDiscovery.start(code)
        // Server is an optional fallback only.''',
'''        startPairingDiscoveryReliable(code, PairingDiscovery.Mode.ALL)
        // Server is an optional fallback only.''')

rep(main,
'''        // 1.9.57: QR is the discovery seed, not a silent one-way success.  The embedded AP5Q
        // session code is used to rediscover the exact open Store session over BLE/LAN.
        // PairingDiscovery then receives the Store-issued provision over that live channel and
        // sends the authenticated ACK that lets the Store mark this phone as really linked.
        pendingPairingCode = envelope.code
        status.text = "✓ تم التقاط QR الصحيح — جاري تأكيد نفس جلسة المحل عبر Bluetooth/Wi‑Fi وإرسال ACK…"
        updatePairingSessionStatus(status.text.toString())
        pairingDiscovery.stop()
        acceptProvision(envelope.provisionText, localChannel = "QR AP5Q")''',
'''        // RC8: AP5Q is a discovery seed, not a local-success shortcut. The old path stopped
        // PairingDiscovery and applied the provision immediately, so Employee could look linked
        // while Store never received the authenticated ACK. Keep the live pairing session running
        // until PairingDiscovery receives the Store provision and sends ACK, then acceptProvision().
        pendingPairingCode = envelope.code
        status.text = "✓ تم التقاط QR الصحيح — جاري تأكيد جلسة المحل وإرسال ACK موثّق…"
        updatePairingSessionStatus(status.text.toString())
        startPairingDiscoveryReliable(envelope.code, PairingDiscovery.Mode.ALL)''')

rep(main,
'''        pendingPairingCode = ""
        if (::pairingDiscovery.isInitialized) pairingDiscovery.stop()
        identity.applyProvision(p)''',
'''        pendingPairingCode = ""
        pairingRetryHandler.removeCallbacks(pairingRetryTask)
        if (::pairingDiscovery.isInitialized) pairingDiscovery.stop()
        identity.applyProvision(p)''')

rep(main,
'''    override fun onDestroy(){connectionUiHandler.removeCallbacks(connectionUiTask);advertiser.stop();networkPresence.stop();if(::pairingDiscovery.isInitialized)pairingDiscovery.stop();super.onDestroy()}''',
'''    override fun onDestroy(){connectionUiHandler.removeCallbacks(connectionUiTask);pairingRetryHandler.removeCallbacks(pairingRetryTask);advertiser.stop();networkPresence.stop();if(::pairingDiscovery.isInitialized)pairingDiscovery.stop();super.onDestroy()}''')

rep(main,
'''header.addView(UiKit.subtitle(this,p,"RC7 • إصلاح Bluetooth/GPS • الإصدار ${attendProVersionName()}").apply''',
'''header.addView(UiKit.subtitle(this,p,"RC8 • إصلاح نهائي لمسار الربط وACK • الإصدار ${attendProVersionName()}").apply''')

store_main = 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
for old, new in [
    ('RC7 • إصلاح الاتصال والإدارة • الإصدار ${attendProVersionName()}', 'RC8 • إصلاح مسار ربط الموظف • الإصدار ${attendProVersionName()}'),
    ('RC7 • لوحة الأقسام • ${attendProVersionName()}', 'RC8 • لوحة الأقسام • ${attendProVersionName()}'),
    ('RC7 • إصلاح الاتصال والإدارة • ${attendProVersionName()}', 'RC8 • إصلاح مسار ربط الموظف • ${attendProVersionName()}'),
    ('RC7 • connectivity/admin repair • ${attendProVersionName()}', 'RC8 • employee pairing ACK repair • ${attendProVersionName()}')
]:
    p = ROOT / store_main
    s = p.read_text(encoding='utf-8')
    if old in s:
        p.write_text(s.replace(old, new), encoding='utf-8')

Path('RELEASE_NOTES_V2.0.0_RC8.md').write_text('''# ATTEND-PRO 2.0.0-RC8\n\nversionCode 98\n\n## Employee pairing repair\n- Fixed AP5Q QR flow: Employee no longer stops PairingDiscovery and declares local success before Store receives authenticated ACK.\n- QR now keeps the live Bluetooth/Wi-Fi pairing session until Store provision + authenticated ACK completes.\n- Added automatic pairing retry up to four attempts to recover from OEM GATT transient failures.\n- Bluetooth-only, Wi-Fi-only, and automatic pairing buttons use the same reliable retry path.\n- Protected pairing protocol files remain unchanged.\n\n## Security\nNo pairing secret, ACK frame, or protected protocol format was changed.\n''', encoding='utf-8')
Path('FIELD_TEST_CHECKLIST_RC8.md').write_text('''# RC8 field test\n1. Install Store and Employee RC8 together and verify RC8 is shown in both headers.\n2. Open one employee pairing ticket on Store and leave it open.\n3. On Employee scan the AP5Q QR. Do not close either pairing screen.\n4. Store must show completed link only after authenticated ACK.\n5. Test Bluetooth-only with Wi-Fi off; Employee automatically retries up to four attempts.\n6. Confirm Employee profile and Store linked status both change only after the same completed pairing session.\n''', encoding='utf-8')

print('RC8 employee pairing fix applied')
