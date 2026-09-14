#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
store_p = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
store = store_p.read_text(encoding='utf-8')

# RC28: finalise connection UX without touching the proven pairing/BLE protocol.
# 1) Keep server reachability separate from employee-device presence (RC26 invariant).
# 2) GPS is location-zone recognition, not a precise phone-to-phone distance meter.
# 3) BLE RSSI may show only an explicitly approximate proximity estimate.
# 4) Technical transport details are hidden behind a diagnostics action.
# 5) Remove duplicate connection-center entry points from the dashboard.

# GPS display: do not present Store-point GPS distance as if it were exact phone-to-phone distance.
old_gps_line = '''        return "GPS: ${gpsStateLabel(state)} • ${gpsDistanceLabel(employeeId)} • منذ ${age}ث • مراقبة فقط"\n'''
new_gps_line = '''        val accuracy = serverGpsAccuracy[employeeId] ?: -1\n        val accuracyText = if (accuracy >= 0) "دقة الموقع ±${accuracy}م" else "دقة الموقع غير متاحة"\n        return "GPS: ${gpsStateLabel(state)} • $accuracyText • منذ ${age}ث • مراقبة فقط"\n'''
if old_gps_line not in store:
    raise SystemExit('RC28: GPS recognition line anchor missing')
store = store.replace(old_gps_line, new_gps_line, 1)

# Add a conservative RSSI-based estimate. This is labelled approximate by design.
helper_anchor = '    private fun gpsDistanceLabel(employeeId: String): String {\n'
helper = '''    private fun bluetoothApproxDistanceRc28(rssi: Int): String {\n        if (rssi >= 0 || rssi <= -127) return "غير متاح"\n        // Generic indoor BLE model: Tx ~= -59 dBm @1m, path-loss exponent ~= 2.2.\n        // This is deliberately an estimate; walls, body position and radio hardware affect RSSI.\n        val meters = Math.pow(10.0, (-59.0 - rssi.toDouble()) / 22.0).coerceIn(0.1, 50.0)\n        return if (meters < 10.0) String.format(Locale.US, "حوالي %.1fم", meters) else "حوالي ${meters.toInt()}م"\n    }\n\n'''
if 'private fun bluetoothApproxDistanceRc28' not in store:
    if helper_anchor not in store:
        raise SystemExit('RC28: BLE distance helper anchor missing')
    store = store.replace(helper_anchor, helper + helper_anchor, 1)

old_ble_suffix = '" (${phone.rssi} dBm)"'
new_ble_suffix = '" (${phone.rssi} dBm • ${bluetoothApproxDistanceRc28(phone.rssi)} تقديري)"'
if old_ble_suffix not in store:
    raise SystemExit('RC28: BLE RSSI display anchor missing')
store = store.replace(old_ble_suffix, new_ble_suffix, 1)

# Move low-level transport diagnostics out of the primary connection-center surface.
old_diag_view = '        box.addView(UiKit.subtitle(this, p, diagnosticText).apply { gravity = Gravity.CENTER })\n'
new_diag_view = '''        box.addView(UiKit.subtitle(this, p, "Bluetooth • Wi‑Fi/LAN • GPS • الخادم").apply { gravity = Gravity.CENTER })\n        box.addView(UiKit.button(this, p, "التشخيص الفني", false).apply {\n            setOnClickListener { info("التشخيص الفني للاتصال", diagnosticText) }\n        })\n'''
if old_diag_view not in store:
    raise SystemExit('RC28: technical diagnostics view anchor missing')
store = store.replace(old_diag_view, new_diag_view, 1)

# Dashboard already has a canonical clickable connection card and bottom navigation tab.
# Remove the extra repeated button from the nearby-devices card.
duplicate_button = '        nearbyCard.addView(UiKit.button(this, p, "فتح مركز الاتصال", false).apply { setOnClickListener { showConnectionCenter() } })\n'
if duplicate_button in store:
    store = store.replace(duplicate_button, '', 1)

# Make the canonical tab naming consistent.
store = store.replace('row.addView(tab("الاتصال") { showConnectionCenter() })', 'row.addView(tab("الأجهزة") { showConnectionCenter() })', 1)
store = store.replace('UiKit.title(this, p, "حالة الاتصال والأجهزة", 19f)', 'UiKit.title(this, p, "الاتصال والأجهزة", 19f)', 1)

store_p.write_text(store, encoding='utf-8')

# Final logical lock assertions. These intentionally fail a future build if a later patch
# reintroduces server-heartbeat-as-device, exposes misleading GPS distance, or removes the
# offline/independent GPS invariants established in RC26/RC27.
s = store_p.read_text(encoding='utf-8')
assert 'isLanConnected(employeeId, now) || isGpsRecognizedFresh(employeeId, now)' in s
assert 'isLanConnected(employeeId, now) || isServerPresenceConnected' not in s
assert 'الخادم فقط — لا يُعد جهازًا متصلًا' in s
assert 'GPS: ${gpsStateLabel(state)} • $accuracyText' in s
assert 'bluetoothApproxDistanceRc28' in s
assert 'التشخيص الفني للاتصال' in s
assert 'فتح مركز الاتصال' not in s
assert 'row.addView(tab("الأجهزة") { showConnectionCenter() })' in s
print('RC28 applied: connection finalization + UI cleanup + anti-regression invariants')
