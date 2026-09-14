#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
settings = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/StoreSettingsActivity.kt'
s = settings.read_text(encoding='utf-8')

start = s.find('    private fun gpsSettings() {')
if start < 0:
    raise SystemExit('gpsSettings() not found')
end = s.find('\n    private fun ', start + 10)
if end < 0:
    raise SystemExit('gpsSettings() end not found')

new_func = r'''    private fun gpsSettings() {
        val palette = UiKit.palette(this)
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(28, 8, 28, 0)
        }

        val lat = UiKit.field(this, palette, "خط العرض Latitude").apply {
            setText(if (repo.storeLatitude.isFinite()) String.format(java.util.Locale.US, "%.7f", repo.storeLatitude) else "")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val lon = UiKit.field(this, palette, "خط الطول Longitude").apply {
            setText(if (repo.storeLongitude.isFinite()) String.format(java.util.Locale.US, "%.7f", repo.storeLongitude) else "")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val radius = UiKit.field(this, palette, "نطاق التعرف بالمتر", numeric = true).apply {
            setText(repo.gpsRadiusMeters.toString())
        }
        val enabled = android.widget.CheckBox(this).apply {
            text = "تفعيل التعرف على هاتف الموظف عبر منطقة GPS"
            isChecked = repo.gpsRecognitionEnabled
            textSize = 16f
        }
        val status = android.widget.TextView(this).apply {
            textSize = 15f
            setPadding(0, 14, 0, 8)
            text = if (repo.isGpsConfigured) {
                "✓ موقع المحل محفوظ\nخط العرض: ${String.format(java.util.Locale.US, "%.7f", repo.storeLatitude)}\nخط الطول: ${String.format(java.util.Locale.US, "%.7f", repo.storeLongitude)}\nالنطاق: ${repo.gpsRadiusMeters}م"
            } else {
                "⚠ لم يتم تحديد موقع المحل بعد"
            }
        }
        val useCurrent = android.widget.Button(this).apply {
            text = "استخدام موقع هذا الجهاز الآن"
            setOnClickListener {
                val manager = getSystemService(android.location.LocationManager::class.java)
                if (androidx.core.content.ContextCompat.checkSelfPermission(this@StoreSettingsActivity, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    androidx.core.app.ActivityCompat.requestPermissions(this@StoreSettingsActivity, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 9701)
                    info("إذن الموقع", "تم طلب إذن الموقع الدقيق. بعد الموافقة اضغط الزر مرة أخرى.")
                    return@setOnClickListener
                }
                requestFreshLocation(manager) { location ->
                    if (location == null) {
                        info("الموقع", "لم يصل موقع صالح. تأكد من تشغيل الموقع بدقة عالية ومنح إذن الموقع الدقيق ثم أعد المحاولة.")
                        return@requestFreshLocation
                    }
                    if (isMockLocation(location)) {
                        info("الموقع", "تم رفض موقع تجريبي/مزيف.")
                        return@requestFreshLocation
                    }
                    val la = location.latitude
                    val lo = location.longitude
                    val r = radius.text.toString().toIntOrNull()?.coerceIn(10, 5000) ?: 25
                    lat.setText(String.format(java.util.Locale.US, "%.7f", la))
                    lon.setText(String.format(java.util.Locale.US, "%.7f", lo))
                    radius.setText(r.toString())
                    enabled.isChecked = true
                    repo.storeLatitude = la
                    repo.storeLongitude = lo
                    repo.gpsRadiusMeters = r
                    repo.gpsRecognitionEnabled = true
                    getSharedPreferences("gps_config_sync", MODE_PRIVATE).edit().putLong("revision", System.currentTimeMillis()).apply()
                    status.text = "✓ موقع المحل محفوظ\nخط العرض: ${String.format(java.util.Locale.US, "%.7f", la)}\nخط الطول: ${String.format(java.util.Locale.US, "%.7f", lo)}\nالدقة: ±${location.accuracy.toInt()}م\nالنطاق: ${r}م"
                }
            }
        }

        box.addView(lat)
        box.addView(lon)
        box.addView(radius)
        box.addView(useCurrent)
        box.addView(enabled)
        box.addView(status)

        android.app.AlertDialog.Builder(this)
            .setTitle("موقع المحل وGPS")
            .setMessage("حدد موقع المحل الحالي ونطاق التعرف. GPS هنا للتعرف على قرب هاتف الموظف فقط ولا يسجل حضورًا تلقائيًا.")
            .setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                val la = lat.text.toString().trim().toDoubleOrNull()
                val lo = lon.text.toString().trim().toDoubleOrNull()
                val r = radius.text.toString().trim().toIntOrNull()?.coerceIn(10, 5000) ?: 25
                if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
                    info("الموقع", "أدخل خط عرض وخط طول صالحين أو استخدم زر «استخدام موقع هذا الجهاز الآن».")
                    return@setPositiveButton
                }
                repo.storeLatitude = la
                repo.storeLongitude = lo
                repo.gpsRadiusMeters = r
                repo.gpsRecognitionEnabled = enabled.isChecked
                getSharedPreferences("gps_config_sync", MODE_PRIVATE).edit().putLong("revision", System.currentTimeMillis()).apply()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
'''

s = s[:start] + new_func + s[end:]
settings.write_text(s, encoding='utf-8')

result = settings.read_text(encoding='utf-8')
for needle in [
    'موقع المحل وGPS',
    'خط العرض Latitude',
    'خط الطول Longitude',
    'استخدام موقع هذا الجهاز الآن',
    'نطاق التعرف بالمتر',
    'UiKit.field(this, palette,',
]:
    assert needle in result, needle
print('RC19 full GPS location dialog applied')
