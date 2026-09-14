#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
store_p = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
store = store_p.read_text(encoding='utf-8')

# RC28 finalises connection UX without changing the proven pairing/BLE protocol.
# Server reachability is diagnostics only; GPS is zone recognition; BLE distance is approximate.

# Do not present GPS Store-point distance as exact phone-to-phone distance.
# Replace every UI interpolation of the GPS distance helper. The underlying telemetry is retained
# for diagnostics, but the normal user surface no longer shows the number as an exact distance.
store = store.replace('${gpsDistanceLabel(employeeId)}', 'موقع GPS')

# Approximate Bluetooth distance from RSSI. Insert beside an existing stable connection helper.
helper = '''    private fun bluetoothApproxDistanceRc28(rssi: Int): String {
        if (rssi >= 0 || rssi <= -127) return "غير متاح"
        val meters = Math.pow(10.0, (-59.0 - rssi.toDouble()) / 22.0).coerceIn(0.1, 50.0)
        return if (meters < 10.0) String.format(Locale.US, "حوالي %.1fم", meters) else "حوالي ${meters.toInt()}م"
    }

'''
if 'private fun bluetoothApproxDistanceRc28' not in store:
    anchors = [
        '    private fun isServerPresenceConnected(',
        '    private fun isEmployeeActuallyConnected(',
        '    private fun showLinkedEmployeesDetails(',
    ]
    for anchor in anchors:
        pos = store.find(anchor)
        if pos >= 0:
            store = store[:pos] + helper + store[pos:]
            break
    else:
        raise SystemExit('RC28: stable connection helper anchor missing')

# Add the estimate wherever the existing connected-device details print RSSI.
ble_candidates = [
    '" (${phone.rssi} dBm)"',
    '" • ${phone.rssi} dBm"',
]
for old in ble_candidates:
    if old in store:
        if old.startswith('" ('):
            new = '" (${phone.rssi} dBm • ${bluetoothApproxDistanceRc28(phone.rssi)} تقديري)"'
        else:
            new = '" • ${phone.rssi} dBm • ${bluetoothApproxDistanceRc28(phone.rssi)} تقديري"'
        store = store.replace(old, new, 1)
        break

# Hide low-level transport diagnostics behind one technical-diagnostics action.
old_diag_view = '        box.addView(UiKit.subtitle(this, p, diagnosticText).apply { gravity = Gravity.CENTER })\n'
if old_diag_view in store:
    store = store.replace(old_diag_view, '''        box.addView(UiKit.subtitle(this, p, "Bluetooth • Wi‑Fi/LAN • GPS • الخادم").apply { gravity = Gravity.CENTER })
        box.addView(UiKit.button(this, p, "التشخيص الفني", false).apply {
            setOnClickListener { info("التشخيص الفني للاتصال", diagnosticText) }
        })
''', 1)
else:
    marker = '        box.addView(UiKit.button(this, p, "إغلاق", false)'
    if marker in store and 'التشخيص الفني للاتصال' not in store:
        store = store.replace(marker, '''        box.addView(UiKit.button(this, p, "التشخيص الفني", false).apply {
            setOnClickListener { info("التشخيص الفني للاتصال", diagnosticText) }
        })
''' + marker, 1)

# Remove duplicated dashboard entry point; canonical access remains via the connection card/tab.
duplicate_button = '        nearbyCard.addView(UiKit.button(this, p, "فتح مركز الاتصال", false).apply { setOnClickListener { showConnectionCenter() } })\n'
store = store.replace(duplicate_button, '', 1)

# Consistent navigation naming.
store = store.replace('row.addView(tab("الاتصال") { showConnectionCenter() })', 'row.addView(tab("الأجهزة") { showConnectionCenter() })', 1)
store = store.replace('UiKit.title(this, p, "حالة الاتصال والأجهزة", 19f)', 'UiKit.title(this, p, "الاتصال والأجهزة", 19f)', 1)

store_p.write_text(store, encoding='utf-8')

s = store_p.read_text(encoding='utf-8')
# Anti-regression invariants from the real-device connection baseline.
assert 'isLanConnected(employeeId, now) || isGpsRecognizedFresh(employeeId, now)' in s
assert 'isLanConnected(employeeId, now) || isServerPresenceConnected' not in s
assert 'الخادم فقط — لا يُعد جهازًا متصلًا' in s
assert 'bluetoothApproxDistanceRc28' in s
assert 'فتح مركز الاتصال' not in s
assert '${gpsDistanceLabel(employeeId)}' not in s
print('RC28 applied: connection finalization + UI cleanup + anti-regression invariants')
