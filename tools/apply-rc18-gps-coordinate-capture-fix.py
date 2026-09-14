#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
settings = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/StoreSettingsActivity.kt'
s = settings.read_text(encoding='utf-8')

old = '''                requestFreshLocation(manager) { location ->
                    if (location == null) { info("الموقع", "لم يصل موقع صالح. فعّل دقة الموقع العالية واقترب من نافذة أو مكان مفتوح ثم أعد المحاولة."); return@requestFreshLocation }
                    if (isMockLocation(location)) { info("الموقع", "تم رفض موقع تجريبي/مزيف."); return@requestFreshLocation }
                    lat.setText(location.latitude.toString()); lon.setText(location.longitude.toString())
                    info("تم التقاط الموقع", "تم التقاط موقع حديث بدقة ${location.accuracy.toInt()} متر. راجع نطاق التعرف ثم اضغط حفظ.")
                }
'''
new = '''                requestFreshLocation(manager) { location ->
                    if (location == null) { info("الموقع", "لم يصل موقع صالح. تأكد من تشغيل الموقع بدقة عالية ومنح إذن الموقع الدقيق ثم أعد المحاولة."); return@requestFreshLocation }
                    if (isMockLocation(location)) { info("الموقع", "تم رفض موقع تجريبي/مزيف."); return@requestFreshLocation }
                    val la = location.latitude
                    val lo = location.longitude
                    val r = radius.text.toString().toIntOrNull()?.coerceIn(10, 5000) ?: 25
                    lat.setText(String.format(java.util.Locale.US, "%.7f", la))
                    lon.setText(String.format(java.util.Locale.US, "%.7f", lo))
                    radius.setText(r.toString())
                    repo.storeLatitude = la
                    repo.storeLongitude = lo
                    repo.gpsRadiusMeters = r
                    repo.gpsRecognitionEnabled = true
                    enabled.isChecked = true
                    getSharedPreferences("gps_config_sync", MODE_PRIVATE).edit().putLong("revision", System.currentTimeMillis()).apply()
                    info("تم تحديد موقع المحل ✓", "خط العرض: ${String.format(java.util.Locale.US, "%.7f", la)}\nخط الطول: ${String.format(java.util.Locale.US, "%.7f", lo)}\nالدقة: ±${location.accuracy.toInt()}م\nتم حفظ النقطة وتفعيل GPS تلقائيًا. اضغط حفظ لإغلاق النافذة.")
                }
'''
if old not in s:
    raise SystemExit('fresh location callback anchor not found')
s = s.replace(old, new, 1)

# Make the dialog state explicit so a blank coordinate can never look configured.
old = '''        val currentState = if (repo.isGpsConfigured) "✓ موقع المحل محفوظ • النطاق الحالي ${repo.gpsRadiusMeters}م" else "لم يتم تحديد موقع المحل بعد"
'''
new = '''        val currentState = if (repo.isGpsConfigured) "✓ موقع المحل محفوظ • ${String.format(java.util.Locale.US, "%.6f", repo.storeLatitude)}, ${String.format(java.util.Locale.US, "%.6f", repo.storeLongitude)} • النطاق ${repo.gpsRadiusMeters}م" else "⚠ لم يتم تحديد خط العرض وخط الطول بعد — اضغط «استخدام موقع هذا الجهاز الآن»"
'''
if old not in s:
    raise SystemExit('currentState anchor not found')
s = s.replace(old, new, 1)

# Saving must never leave recognition disabled when valid coordinates are present.
old = '''                repo.storeLatitude = la; repo.storeLongitude = lo; repo.gpsRadiusMeters = r; repo.gpsRecognitionEnabled = enabled.isChecked
'''
new = '''                repo.storeLatitude = la; repo.storeLongitude = lo; repo.gpsRadiusMeters = r; repo.gpsRecognitionEnabled = true; enabled.isChecked = true
'''
if old not in s:
    raise SystemExit('save recognition anchor not found')
s = s.replace(old, new, 1)

settings.write_text(s, encoding='utf-8')

assert 'تم تحديد موقع المحل ✓' in settings.read_text(encoding='utf-8')
assert 'خط العرض:' in settings.read_text(encoding='utf-8')
assert 'repo.gpsRecognitionEnabled = true' in settings.read_text(encoding='utf-8')
print('RC18 GPS coordinate capture fix applied')
