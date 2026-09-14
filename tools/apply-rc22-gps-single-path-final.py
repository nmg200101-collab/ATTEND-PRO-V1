#!/usr/bin/env python3
from pathlib import Path
import runpy

ROOT = Path('.')
settings_p = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/StoreSettingsActivity.kt'
main_p = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
presence_p = ROOT / 'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'

m = main_p.read_text(encoding='utf-8')
start = m.find('    private fun addGpsZoneCardRc14Fix(root: LinearLayout) {')
end = m.find('    private fun refreshGpsConfigToEmployeesRc14Fix()', start)
if start < 0 or end < 0:
    raise SystemExit('GPS home helper anchors not found after RC21 chain')
new_helper = r'''    private fun addGpsZoneCardRc14Fix(root: LinearLayout) {
        val card = UiKit.card(this, p, 14)
        card.addView(UiKit.sectionLabel(this, p, "GPS • إعداد موقع المحل"))
        val gpsReady = repo.gpsRecognitionEnabled && repo.isGpsConfigured
        val stateText = if (gpsReady) {
            "✓ مفعّل • خط العرض ${String.format(java.util.Locale.US, "%.6f", repo.storeLatitude)} • خط الطول ${String.format(java.util.Locale.US, "%.6f", repo.storeLongitude)} • النطاق ${repo.gpsRadiusMeters}م"
        } else {
            "غير مكتمل — اضغط هنا لتحديد موقع المحل وخط العرض وخط الطول والنطاق وتفعيل التعرف"
        }
        card.addView(UiKit.subtitle(this, p, stateText))
        card.addView(UiKit.button(this, p, if (gpsReady) "فتح إعداد GPS الكامل" else "تهيئة GPS الآن").apply {
            setOnClickListener {
                startActivity(Intent(this@MainActivity, StoreSettingsActivity::class.java)
                    .putExtra("open_gps_settings_rc22", true))
            }
        })
        root.addView(card)
    }

'''
m = m[:start] + new_helper + m[end:]

first_start = m.find('    private fun maybeShowGpsFirstSetup() {')
first_end = m.find('    private fun showGpsSetupDialog(', first_start)
if first_start >= 0 and first_end > first_start:
    m = m[:first_start] + '''    private fun maybeShowGpsFirstSetup() {
        // RC22: legacy first-run popup intentionally disabled. Use the single home GPS card.
    }

''' + m[first_end:]

lines = []
for line in m.splitlines(True):
    if 'إعداد GPS وتحديد منطقة المحل والنطاق' in line and 'showGpsSetupDialog' in line:
        continue
    if 'إعداد GPS وتحديد النطاق' in line and 'showGpsSetupDialog' in line:
        continue
    lines.append(line)
m = ''.join(lines)
m = m.replace('nearbyRefreshHandler.postDelayed({ if (!isFinishing) showGpsSetupDialog(firstTime = false) }, 250L)',
              'nearbyRefreshHandler.postDelayed({ if (!isFinishing) startActivity(Intent(this@MainActivity, StoreSettingsActivity::class.java).putExtra("open_gps_settings_rc22", true)) }, 250L)')
main_p.write_text(m, encoding='utf-8')

s = settings_p.read_text(encoding='utf-8')
member_anchor = '    private var pendingGpsCaptureAfterPermission = false\n'
if member_anchor not in s:
    raise SystemExit('RC20 GPS state anchor not found')
if 'pendingOpenGpsRc22' not in s:
    s = s.replace(member_anchor, member_anchor + '    private var pendingOpenGpsRc22 = false\n', 1)
repo_anchor = '        repo = StoreRepository(this)\n'
if repo_anchor not in s:
    raise SystemExit('Store settings repo anchor missing')
if 'open_gps_settings_rc22' not in s[s.find(repo_anchor):s.find('showGateOrDashboard()', s.find(repo_anchor)) + 100]:
    s = s.replace(repo_anchor, repo_anchor + '        pendingOpenGpsRc22 = intent?.getBooleanExtra("open_gps_settings_rc22", false) == true\n', 1)
s = s.replace('            "موقع المحل وGPS" to { gpsSettings() },\n', '')
s = s.replace('        attendance.addView(UiKit.button(this, p, "إعداد GPS وموقع المحل", false).apply { setOnClickListener { gpsSettings() } })\n', '')
anchor = '''        if (!repo.hasStoreAdminPin && !repo.validateStoreAdminSession(sessionToken)) sessionToken = repo.issueStoreAdminSession()
        StoreMessagePoll1975.schedule(this)
'''
replacement = '''        if (!repo.hasStoreAdminPin && !repo.validateStoreAdminSession(sessionToken)) sessionToken = repo.issueStoreAdminSession()
        if (pendingOpenGpsRc22) {
            pendingOpenGpsRc22 = false
            gpsSettings()
            return
        }
        StoreMessagePoll1975.schedule(this)
'''
if anchor not in s:
    raise SystemExit('Store dashboard authentication anchor missing')
s = s.replace(anchor, replacement, 1)
needle = '        card.addView(UiKit.sectionLabel(this, p, "إحداثيات موقع المحل"))\n'
if needle not in s:
    raise SystemExit('GPS full-screen card anchor missing')
s = s.replace(needle, needle + '''        card.addView(UiKit.subtitle(this, p, "هذا هو مسار GPS الوحيد في تطبيق المحل. اضبط الإحداثيات والنطاق هنا ثم احفظ؛ سيعاد إرسال المنطقة تلقائيًا إلى هاتف الموظف المرتبط."))
''', 1)
status_anchor = '        card.addView(status)\n\n        lateinit var captureButton: android.widget.Button\n'
if status_anchor not in s:
    raise SystemExit('GPS status anchor missing')
status_new = '''        card.addView(status)
        val runtimeState = UiKit.subtitle(this, p, if (repo.gpsRecognitionEnabled && repo.isGpsConfigured) {
            "حالة GPS: جاهز للإرسال • سيتم التعرف على هاتف الموظف عندما يستلم منطقة المحل وتتوفر لديه صلاحية الموقع وقراءة GPS حديثة."
        } else {
            "حالة GPS: غير جاهز — أكمل الإحداثيات وفعّل التعرف ثم احفظ."
        })
        card.addView(runtimeState)

        lateinit var captureButton: android.widget.Button
'''
s = s.replace(status_anchor, status_new, 1)
s = s.replace('                repo.gpsRecognitionEnabled = enabled.isChecked\n                getSharedPreferences("gps_config_sync", MODE_PRIVATE).edit().putLong("revision", System.currentTimeMillis()).apply()\n',
              '                repo.gpsRecognitionEnabled = enabled.isChecked\n                getSharedPreferences("gps_config_sync", MODE_PRIVATE).edit().putLong("revision", System.currentTimeMillis()).remove("applied").apply()\n', 1)
settings_p.write_text(s, encoding='utf-8')

p = presence_p.read_text(encoding='utf-8')
old = '''    private fun refreshGpsRuntimeRc21() {
        if (!identity.isConfigured || !identity.autoPresence) {
            if (geoMonitor.isRunning()) geoMonitor.stop()
            return
        }
        val hasLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
'''
new = '''    private fun refreshGpsRuntimeRc21() {
        if (!identity.isConfigured) {
            if (geoMonitor.isRunning()) geoMonitor.stop()
            return
        }
        if (identity.isTrustedStoreGpsConfigured && !identity.autoPresence) {
            identity.autoPresence = true
        }
        val hasLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
'''
if old not in p:
    raise SystemExit('RC21 GPS runtime anchor missing')
p = p.replace(old, new, 1)
presence_p.write_text(p, encoding='utf-8')

mm = main_p.read_text(encoding='utf-8')
ss = settings_p.read_text(encoding='utf-8')
pp = presence_p.read_text(encoding='utf-8')
assert 'GPS • إعداد موقع المحل' in mm
assert 'open_gps_settings_rc22' in mm and 'open_gps_settings_rc22' in ss
assert 'legacy first-run popup intentionally disabled' in mm
assert '"موقع المحل وGPS" to { gpsSettings() }' not in ss
assert '"إعداد GPS وموقع المحل"' not in ss
assert 'هذا هو مسار GPS الوحيد في تطبيق المحل' in ss
assert 'remove("applied")' in ss
assert 'identity.isTrustedStoreGpsConfigured && !identity.autoPresence' in pp
assert mm.count('GPS • إعداد موقع المحل') == 1
print('RC22 single-path GPS finalizer applied')

# RC23 hotfix is deliberately chained from the existing signed RC22 workflow.
runpy.run_path('tools/apply-rc23-gps-independent-proof-route.py', run_name='__main__')
