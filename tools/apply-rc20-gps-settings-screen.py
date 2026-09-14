#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
settings = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/StoreSettingsActivity.kt'
s = settings.read_text(encoding='utf-8')

# RC20 finalizer: GPS settings must be a real full-screen, scrollable settings page.
# Keep one canonical entry only: Store manager -> الحضور والتشغيل -> موقع المحل وGPS.
duplicate = '        root.addView(largeSection("GPS • موقع المحل ومنطقة التعرف", "حدد موقع المحل الحالي والنطاق بالمتر ثم احفظ لإرساله إلى هاتف الموظف") { gpsSettings() })\n'
s = s.replace(duplicate, '')

# Persistent UI state so permission/settings round-trips return to the GPS page instead of the dashboard.
anchor = '    private var pendingPhoneBackupEnvelope: String? = null\n'
if anchor not in s:
    raise SystemExit('member anchor not found')
if 'private var gpsSettingsOpen = false' not in s:
    s = s.replace(anchor, anchor + '    private var gpsSettingsOpen = false\n    private var pendingGpsCaptureAfterPermission = false\n', 1)

# Do not overwrite the GPS page on resume.
old_resume = '''        if (::repo.isInitialized && !repo.isCentralActivationActive()) { showCentralActivationRequired(); return }
        if (authenticated || !repo.hasStoreAdminPin) showDashboard()
'''
new_resume = '''        if (::repo.isInitialized && !repo.isCentralActivationActive()) { showCentralActivationRequired(); return }
        if (gpsSettingsOpen && (authenticated || !repo.hasStoreAdminPin)) { showGpsSettingsScreen(false); return }
        if (authenticated || !repo.hasStoreAdminPin) showDashboard()
'''
if old_resume not in s:
    raise SystemExit('onResume anchor not found')
s = s.replace(old_resume, new_resume, 1)

start = s.find('    private fun gpsSettings() {')
end = s.find('    private fun shiftSettings() {', start)
if start < 0 or end < 0:
    raise SystemExit('gpsSettings block not found')

new_block = r'''    private fun gpsSettings() {
        gpsSettingsOpen = true
        showGpsSettingsScreen(false)
    }

    private fun showGpsSettingsScreen(autoCapture: Boolean) {
        gpsSettingsOpen = true
        window.statusBarColor = p.bg
        val root = baseRoot()

        val header = UiKit.heroCard(this, p, 12)
        header.addView(UiKit.title(this, p, "موقع المحل وGPS", 24f).apply {
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
        })
        header.addView(UiKit.subtitle(this, p, "حدد نقطة المحل الحقيقية وخط العرض وخط الطول ونطاق التعرف. هذه الإعدادات تستخدم للتعرف والمراقبة ولا تسجل حضورًا تلقائيًا.").apply {
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.argb(225,255,255,255))
        })
        root.addView(header)

        val card = UiKit.card(this, p, 12)
        card.addView(UiKit.sectionLabel(this, p, "إحداثيات موقع المحل"))

        card.addView(UiKit.title(this, p, "خط العرض Latitude", 16f))
        val lat = UiKit.field(this, p, "مثال: 12.785000").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(if (repo.storeLatitude.isFinite()) String.format(java.util.Locale.US, "%.7f", repo.storeLatitude) else "")
        }
        card.addView(lat)

        card.addView(UiKit.title(this, p, "خط الطول Longitude", 16f))
        val lon = UiKit.field(this, p, "مثال: 45.018000").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(if (repo.storeLongitude.isFinite()) String.format(java.util.Locale.US, "%.7f", repo.storeLongitude) else "")
        }
        card.addView(lon)

        card.addView(UiKit.title(this, p, "نطاق التعرف بالمتر", 16f))
        val radius = UiKit.field(this, p, "من 10 إلى 5000 متر", true).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(repo.gpsRadiusMeters.coerceIn(10, 5000).toString())
        }
        card.addView(radius)

        val enabled = CheckBox(this).apply {
            text = "تفعيل التعرف على هاتف الموظف عبر منطقة GPS"
            isChecked = repo.gpsRecognitionEnabled
            textSize = 15f
            setPadding(0, UiKit.dp(this@StoreSettingsActivity, 8), 0, UiKit.dp(this@StoreSettingsActivity, 8))
        }
        card.addView(enabled)

        val status = UiKit.subtitle(this, p,
            if (repo.isGpsConfigured) {
                "✓ الموقع محفوظ\nخط العرض: ${String.format(java.util.Locale.US, "%.7f", repo.storeLatitude)}\nخط الطول: ${String.format(java.util.Locale.US, "%.7f", repo.storeLongitude)}\nالنطاق: ${repo.gpsRadiusMeters} متر"
            } else {
                "⚠ لم يتم تحديد إحداثيات المحل بعد. اضغط الزر أدناه وسيتم تعبئة خط العرض وخط الطول تلقائيًا."
            }
        )
        card.addView(status)

        lateinit var captureButton: android.widget.Button
        captureButton = UiKit.button(this, p, "استخدام موقع هذا الجهاز الآن").apply {
            setOnClickListener {
                val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!fine) {
                    pendingGpsCaptureAfterPermission = true
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_GPS_LOCATION_RC20)
                    status.text = "بانتظار منح إذن الموقع الدقيق… بعد الموافقة سيكمل التطبيق تلقائيًا."
                    return@setOnClickListener
                }

                val manager = getSystemService(LocationManager::class.java)
                if (manager == null) {
                    status.text = "تعذر الوصول إلى خدمة الموقع في الجهاز."
                    return@setOnClickListener
                }
                val gpsOn = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
                val netOn = runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
                if (!gpsOn && !netOn) {
                    status.text = "⚠ خدمة الموقع GPS متوقفة. افتح إعدادات الموقع وشغّلها ثم ارجع إلى هذه الصفحة."
                    return@setOnClickListener
                }

                status.text = "جاري التقاط موقع المحل الفعلي… انتظر حتى تظهر الإحداثيات هنا."
                isEnabled = false
                requestFreshLocation(manager) { location ->
                    isEnabled = true
                    if (location == null) {
                        status.text = "تعذر الحصول على موقع حديث خلال المهلة. تأكد من تشغيل «الموقع الدقيق» وGPS ثم اضغط الزر مرة أخرى."
                        return@requestFreshLocation
                    }
                    if (isMockLocation(location)) {
                        status.text = "تم رفض موقع تجريبي/مزيف. أوقف تطبيقات الموقع الوهمي وحاول من جديد."
                        return@requestFreshLocation
                    }
                    val la = location.latitude
                    val lo = location.longitude
                    if (!la.isFinite() || la !in -90.0..90.0 || !lo.isFinite() || lo !in -180.0..180.0) {
                        status.text = "وصلت إحداثيات غير صالحة من الجهاز. لم يتم حفظها."
                        return@requestFreshLocation
                    }
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
                    status.text = "✓ تم التقاط وحفظ موقع المحل\nخط العرض: ${String.format(java.util.Locale.US, "%.7f", la)}\nخط الطول: ${String.format(java.util.Locale.US, "%.7f", lo)}\nالدقة: ±${location.accuracy.toInt()} متر\nالنطاق: $r متر"
                }
            }
        }
        card.addView(captureButton)
        card.addView(UiKit.button(this, p, "فتح إعدادات الموقع GPS", false).apply {
            setOnClickListener {
                runCatching { startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                    .onFailure { info("إعدادات الموقع", "تعذر فتح إعدادات الموقع على هذا الجهاز.") }
            }
        })
        root.addView(card)

        val actions = UiKit.card(this, p, 10)
        actions.addView(UiKit.button(this, p, "حفظ إعدادات GPS").apply {
            setOnClickListener {
                val la = lat.text.toString().trim().toDoubleOrNull()
                val lo = lon.text.toString().trim().toDoubleOrNull()
                val r = radius.text.toString().trim().toIntOrNull()
                if (la == null || !la.isFinite() || la !in -90.0..90.0) { lat.error = "أدخل خط عرض صحيحًا بين -90 و90"; return@setOnClickListener }
                if (lo == null || !lo.isFinite() || lo !in -180.0..180.0) { lon.error = "أدخل خط طول صحيحًا بين -180 و180"; return@setOnClickListener }
                if (r == null || r !in 10..5000) { radius.error = "استخدم نطاقًا من 10 إلى 5000 متر"; return@setOnClickListener }
                repo.storeLatitude = la
                repo.storeLongitude = lo
                repo.gpsRadiusMeters = r
                repo.gpsRecognitionEnabled = enabled.isChecked
                getSharedPreferences("gps_config_sync", MODE_PRIVATE).edit().putLong("revision", System.currentTimeMillis()).apply()
                status.text = "✓ تم حفظ إعدادات GPS\nخط العرض: ${String.format(java.util.Locale.US, "%.7f", la)}\nخط الطول: ${String.format(java.util.Locale.US, "%.7f", lo)}\nالنطاق: $r متر"
                info("تم حفظ GPS ✓", "تم حفظ موقع المحل وإحداثياته ونطاق التعرف، وسيتم إرسال الإعدادات لهاتف الموظف عبر قنوات الاتصال المتاحة.")
            }
        })
        actions.addView(UiKit.button(this, p, "رجوع إلى إدارة المحل", false).apply {
            setOnClickListener {
                gpsSettingsOpen = false
                pendingGpsCaptureAfterPermission = false
                showDashboard()
            }
        })
        root.addView(actions)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(p.bg)
            isFillViewport = true
            addView(root)
        })

        if (autoCapture) captureButton.post { captureButton.performClick() }
    }

'''
s = s[:start] + new_block + s[end:]

# Permission result must automatically continue capture; never require a second press.
activity_marker = '    @Deprecated("Deprecated in Java")\n    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {'
if activity_marker not in s:
    raise SystemExit('onActivityResult marker not found')
perm_block = r'''    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_GPS_LOCATION_RC20) return
        val granted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val shouldContinue = pendingGpsCaptureAfterPermission
        pendingGpsCaptureAfterPermission = false
        if (granted && shouldContinue) {
            gpsSettingsOpen = true
            showGpsSettingsScreen(true)
        } else if (gpsSettingsOpen) {
            showGpsSettingsScreen(false)
            info("صلاحية الموقع مطلوبة", "لا يمكن التقاط خط العرض وخط الطول تلقائيًا بدون صلاحية الموقع. يمكنك منحها من إعدادات التطبيق ثم العودة.")
        }
    }

'''
if 'override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>' not in s:
    s = s.replace(activity_marker, perm_block + activity_marker, 1)

# Add request code to companion object (or create constant near class members if no easy companion anchor).
member_anchor = '    private var pendingGpsCaptureAfterPermission = false\n'
if 'REQUEST_GPS_LOCATION_RC20' not in s[s.find(member_anchor)+len(member_anchor):s.find('override fun onCreate')]:
    s = s.replace(member_anchor, member_anchor + '    private val REQUEST_GPS_LOCATION_RC20 = 9720\n', 1)

settings.write_text(s, encoding='utf-8')

result = settings.read_text(encoding='utf-8')
assert 'private fun showGpsSettingsScreen(autoCapture: Boolean)' in result
assert 'خط العرض Latitude' in result
assert 'خط الطول Longitude' in result
assert 'فتح إعدادات الموقع GPS' in result
assert 'ACTION_LOCATION_SOURCE_SETTINGS' in result
assert 'pendingGpsCaptureAfterPermission' in result
assert 'override fun onRequestPermissionsResult' in result
assert 'isFillViewport = true' in result
assert duplicate.strip() not in result
assert result.count('"موقع المحل وGPS" to { gpsSettings() }') == 1
print('RC20 definitive GPS settings screen applied')
