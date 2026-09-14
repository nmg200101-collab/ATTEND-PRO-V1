#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
main = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
s = main.read_text(encoding='utf-8')

old = '''        if (repo.gpsRecognitionEnabled && repo.isGpsConfigured) return
        nearbyRefreshHandler.postDelayed({ if (!isFinishing && !repo.isGpsConfigured) showGpsSetupDialog(firstTime = true) }, 700L)
'''
new = '''        if (repo.gpsRecognitionEnabled && repo.isGpsConfigured) return
        nearbyRefreshHandler.postDelayed({
            if (!isFinishing && (!repo.gpsRecognitionEnabled || !repo.isGpsConfigured)) {
                showGpsSetupDialog(firstTime = true)
            }
        }, 700L)
'''
if old not in s:
    raise SystemExit('RC16: first-run GPS setup anchor not found')
s = s.replace(old, new, 1)

anchor = '''    private fun showConnectionCenter() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 18), 0)
        }
'''
insert = '''    private fun showConnectionCenter() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 18), 0)
        }

        val gpsReady = repo.gpsRecognitionEnabled && repo.isGpsConfigured
        val gpsSetupCard = UiKit.card(this, p, 10).apply {
            addView(UiKit.sectionLabel(this@MainActivity, p, "GPS • تعريف موقع المحل والمنطقة"))
            addView(UiKit.subtitle(this@MainActivity, p,
                if (gpsReady) "✓ موقع المحل محدد • نطاق التعرف ${repo.gpsRadiusMeters}م • اضغط لإعادة التهيئة"
                else "غير مهيأ — يجب تحديد موقع المحل ونطاق التعرف حتى يعمل اتصال GPS ويظهر بُعد هاتف الموظف"))
            addView(UiKit.button(this@MainActivity, p, if (gpsReady) "إعادة تعريف موقع المحل والنطاق" else "تهيئة GPS وتحديد موقع المحل", false).apply {
                setOnClickListener { showGpsSetupDialog(firstTime = !gpsReady) }
            })
        }
        box.addView(gpsSetupCard)
'''
if anchor not in s:
    raise SystemExit('RC16: connection center anchor not found')
s = s.replace(anchor, insert, 1)

s = s.replace('"إعداد GPS وتحديد منطقة المحل والنطاق"', '"تهيئة GPS وتحديد موقع المحل والنطاق"')

main.write_text(s, encoding='utf-8')

assert 'GPS • تعريف موقع المحل والمنطقة' in s
assert 'تهيئة GPS وتحديد موقع المحل' in s
assert '(!repo.gpsRecognitionEnabled || !repo.isGpsConfigured)' in s
print('RC16 Store GPS zone visibility fix applied')
