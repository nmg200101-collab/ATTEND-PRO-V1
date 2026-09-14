#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
settings = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/StoreSettingsActivity.kt'
main = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'

s = settings.read_text(encoding='utf-8')

# Put GPS setup directly on the Store manager dashboard; no hidden submenu required.
old = '''        root.addView(largeSection("الحضور والتشغيل", "طرق الحضور، الدوام، الموقع، والبصمة الخارجية") { showStoreOperations1976() })
        root.addView(largeSection("الصوت والرسائل", "التحكم الصوتي، إشعارات المحل، ورسائل الموظفين") { showStoreCommunication1976() })
'''
new = '''        root.addView(largeSection("الحضور والتشغيل", "طرق الحضور، الدوام، الموقع، والبصمة الخارجية") { showStoreOperations1976() })
        root.addView(largeSection("GPS • موقع المحل ومنطقة التعرف", "حدد موقع المحل الحالي والنطاق بالمتر ثم احفظ لإرساله إلى هاتف الموظف") { gpsSettings() })
        root.addView(largeSection("الصوت والرسائل", "التحكم الصوتي، إشعارات المحل، ورسائل الموظفين") { showStoreCommunication1976() })
'''
if old not in s:
    raise SystemExit('dashboard anchor not found')
s = s.replace(old, new, 1)

# Modernize the legacy GPS dialog while keeping the familiar fields shown in the previous working version.
old = '''        val radius = UiKit.field(this, p, "نطاق التعرف GPS بالمتر", true).apply { setText(repo.gpsRadiusMeters.toString()) }
        box.addView(UiKit.subtitle(this, p, "أدخل إحداثيات المحل مرة واحدة. GPS في 1.9.58 للمراقبة فقط: يسجل متى أصبح الهاتف داخل/قرب/خارج النطاق مع المسافة والدقة. يمكن استخدام 10م للمحل الصغير، لكن التطبيق يعرض الدقة ولا يعتبر GPS إثبات حضور."))
        listOf(lat, lon, radius).forEach { box.addView(it) }
'''
new = '''        val radius = UiKit.field(this, p, "نطاق التعرف GPS بالمتر", true).apply { setText(repo.gpsRadiusMeters.toString()) }
        val enabled = CheckBox(this).apply {
            text = "تفعيل التعرف على هاتف الموظف عبر منطقة GPS"
            setTextColor(p.text)
            isChecked = repo.gpsRecognitionEnabled
        }
        val currentState = if (repo.isGpsConfigured) "✓ موقع المحل محفوظ • النطاق الحالي ${repo.gpsRadiusMeters}م" else "لم يتم تحديد موقع المحل بعد"
        box.addView(UiKit.subtitle(this, p, "حدد نقطة المحل مرة واحدة ثم اختر نطاق التعرف بالمتر. سيظهر الهاتف داخل/قرب/خارج المنطقة مع المسافة والدقة. GPS للمراقبة ولا يسجل الحضور بمفرده."))
        box.addView(enabled)
        box.addView(UiKit.subtitle(this, p, currentState))
        listOf(lat, lon, radius).forEach { box.addView(it) }
'''
if old not in s:
    raise SystemExit('gps dialog anchor not found')
s = s.replace(old, new, 1)

old = '''                repo.storeLatitude = la; repo.storeLongitude = lo; repo.gpsRadiusMeters = r
                dialog.dismiss(); info("تم", "تم حفظ موقع المحل ونطاق التعرف GPS. تُزامن الإعدادات أيضًا عبر Bluetooth عند الاتصال، وGPS لن يسجل حضورًا بمفرده."); showDashboard()
'''
new = '''                repo.storeLatitude = la; repo.storeLongitude = lo; repo.gpsRadiusMeters = r; repo.gpsRecognitionEnabled = enabled.isChecked
                getSharedPreferences("gps_config_sync", MODE_PRIVATE).edit().putLong("revision", System.currentTimeMillis()).apply()
                dialog.dismiss(); info("تم", "✓ تم حفظ موقع المحل ونطاق GPS. سيعاد إرسال المنطقة إلى هاتف الموظف عند الرجوع للشاشة الرئيسية مع إبقاء Bluetooth مفعّلًا."); showDashboard()
'''
if old not in s:
    raise SystemExit('gps save anchor not found')
s = s.replace(old, new, 1)

settings.write_text(s, encoding='utf-8')

m = main.read_text(encoding='utf-8')
# Resend GPS config after StoreSettingsActivity changes it, without touching field-approved BLE runtime files.
old = '''    override fun onResume() {
        installConnectionRecovery1927()
        autoSyncIfReady()
'''
new = '''    override fun onResume() {
        installConnectionRecovery1927()
        val gpsSyncPrefs = getSharedPreferences("gps_config_sync", MODE_PRIVATE)
        val gpsRevision = gpsSyncPrefs.getLong("revision", 0L)
        val gpsApplied = gpsSyncPrefs.getLong("applied", 0L)
        if (gpsRevision > gpsApplied && ::directBle.isInitialized) {
            refreshGpsConfigToEmployeesRc14Fix()
            gpsSyncPrefs.edit().putLong("applied", gpsRevision).apply()
        }
        autoSyncIfReady()
'''
if old not in m:
    raise SystemExit('onResume anchor not found')
m = m.replace(old, new, 1)
main.write_text(m, encoding='utf-8')

assert 'GPS • موقع المحل ومنطقة التعرف' in settings.read_text(encoding='utf-8')
assert 'تفعيل التعرف على هاتف الموظف عبر منطقة GPS' in settings.read_text(encoding='utf-8')
assert 'gps_config_sync' in main.read_text(encoding='utf-8')
print('RC17 visible Store GPS setup applied')
