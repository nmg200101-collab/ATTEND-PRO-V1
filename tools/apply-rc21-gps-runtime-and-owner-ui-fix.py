#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
settings = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/StoreSettingsActivity.kt'
store_main = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
presence = ROOT / 'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'
geo = ROOT / 'buildsrc/employee-app/src/main/java/com/attendpro/employee/OfflineGeoMonitor.kt'

# -----------------------------------------------------------------------------
# Store settings: keep operations visible and prevent submenu from covering GPS.
# -----------------------------------------------------------------------------
s = settings.read_text(encoding='utf-8')

member_anchor = '    private var pendingPhoneBackupEnvelope: String? = null\n'
if member_anchor not in s:
    raise SystemExit('StoreSettings member anchor not found')
if 'pendingOpenOperationsRc21' not in s:
    s = s.replace(member_anchor, member_anchor + '    private var pendingOpenOperationsRc21 = false\n', 1)

repo_anchor = '        repo = StoreRepository(this)\n'
if repo_anchor not in s:
    raise SystemExit('StoreSettings repo init anchor not found')
if 'open_store_operations_rc21' not in s[s.find(repo_anchor):s.find('showGateOrDashboard()', s.find(repo_anchor))+100]:
    s = s.replace(repo_anchor, repo_anchor + '        pendingOpenOperationsRc21 = intent?.getBooleanExtra("open_store_operations_rc21", false) == true\n', 1)

old_layered = '''    private fun showLayeredMenu1977(title: String, items: List<Pair<String, () -> Unit>>) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = if (AppLanguage.isEnglish(this@StoreSettingsActivity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@StoreSettingsActivity, 18), UiKit.dp(this@StoreSettingsActivity, 8), UiKit.dp(this@StoreSettingsActivity, 18), UiKit.dp(this@StoreSettingsActivity, 8))
        }
        items.forEach { (label, action) -> box.addView(UiKit.button(this, p, label, false).apply { setOnClickListener { action() } }) }
        AlertDialog.Builder(this).setTitle(AppLanguage.legacyUiText(this, title)).setView(box).setNegativeButton(t("رجوع", "Back"), null).show()
    }
'''
new_layered = '''    private fun showLayeredMenu1977(title: String, items: List<Pair<String, () -> Unit>>) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = if (AppLanguage.isEnglish(this@StoreSettingsActivity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@StoreSettingsActivity, 18), UiKit.dp(this@StoreSettingsActivity, 8), UiKit.dp(this@StoreSettingsActivity, 18), UiKit.dp(this@StoreSettingsActivity, 8))
        }
        lateinit var menuDialog: AlertDialog
        items.forEach { (label, action) ->
            box.addView(UiKit.button(this, p, label, false).apply {
                setOnClickListener {
                    menuDialog.dismiss()
                    action()
                }
            })
        }
        menuDialog = AlertDialog.Builder(this)
            .setTitle(AppLanguage.legacyUiText(this, title))
            .setView(box)
            .setNegativeButton(t("رجوع", "Back"), null)
            .create()
        menuDialog.show()
    }
'''
if old_layered not in s:
    raise SystemExit('showLayeredMenu1977 anchor not found')
s = s.replace(old_layered, new_layered, 1)

# After dashboard is actually visible, honor direct entry from Owner Hub exactly once.
view_anchor = '        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })\n    }\n\n    private fun showLayeredMenu1977'
if view_anchor not in s:
    raise SystemExit('dashboard end anchor not found')
replacement = '''        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
        if (pendingOpenOperationsRc21) {
            pendingOpenOperationsRc21 = false
            root.post { if (!isFinishing) showStoreOperations1976() }
        }
    }

    private fun showLayeredMenu1977'''
s = s.replace(view_anchor, replacement, 1)

# Save GPS -> immediately return to Store main screen so MainActivity.onResume performs
# the existing RC17 config refresh instead of leaving the new config waiting behind settings.
save_tail = '''                status.text = "✓ تم حفظ إعدادات GPS\\nخط العرض: ${String.format(java.util.Locale.US, "%.7f", la)}\\nخط الطول: ${String.format(java.util.Locale.US, "%.7f", lo)}\\nالنطاق: $r متر"
                info("تم حفظ GPS ✓", "تم حفظ موقع المحل وإحداثياته ونطاق التعرف، وسيتم إرسال الإعدادات لهاتف الموظف عبر قنوات الاتصال المتاحة.")
'''
new_save_tail = '''                status.text = "✓ تم حفظ وتفعيل GPS\\nخط العرض: ${String.format(java.util.Locale.US, "%.7f", la)}\\nخط الطول: ${String.format(java.util.Locale.US, "%.7f", lo)}\\nالنطاق: $r متر\\nجارٍ الرجوع لتطبيق المحل لإرسال المنطقة إلى هواتف الموظفين…"
                gpsSettingsOpen = false
                pendingGpsCaptureAfterPermission = false
                android.widget.Toast.makeText(this, "تم حفظ GPS — جارٍ تفعيل التعرف وإرسال المنطقة للموظفين", android.widget.Toast.LENGTH_LONG).show()
                setResult(RESULT_OK)
                finish()
'''
if save_tail not in s:
    raise SystemExit('RC20 GPS save tail not found')
s = s.replace(save_tail, new_save_tail, 1)

settings.write_text(s, encoding='utf-8')

# -----------------------------------------------------------------------------
# Store Owner Hub: make الحضور والتشغيل visible directly, not hidden behind ⋮.
# -----------------------------------------------------------------------------
m = store_main.read_text(encoding='utf-8')
owner_anchor = '''        val scroll = ScrollView(this).apply { addView(content) }
        var hubDialog: AlertDialog? = null

        ownerShortcutCatalog().filterNot { it.id == "all_settings" }.forEach { shortcut ->
'''
owner_new = '''        val scroll = ScrollView(this).apply { addView(content) }
        var hubDialog: AlertDialog? = null

        content.addView(UiKit.button(this, p, "الحضور والتشغيل", false).apply {
            setOnClickListener {
                hubDialog?.dismiss()
                startActivity(Intent(this@MainActivity, StoreSettingsActivity::class.java)
                    .putExtra("open_store_operations_rc21", true))
            }
        })

        ownerShortcutCatalog().filterNot { it.id == "all_settings" }.forEach { shortcut ->
'''
if owner_anchor not in m:
    raise SystemExit('Owner Hub anchor not found')
m = m.replace(owner_anchor, owner_new, 1)
store_main.write_text(m, encoding='utf-8')

# -----------------------------------------------------------------------------
# Employee runtime: the real GPS bug.
# GPS config can arrive AFTER PresenceService already started. Previously the
# locationMonitoringAllowedForRun flag stayed false for the lifetime of that service,
# so coordinates were stored but OfflineGeoMonitor never started. Re-evaluate at runtime.
# Also replay the latest GPS observation over authenticated BLE so one timing miss does
# not make the Store think GPS is disconnected.
# -----------------------------------------------------------------------------
p = presence.read_text(encoding='utf-8')
field_anchor = '    private var locationMonitoringAllowedForRun: Boolean = false\n'
if field_anchor not in p:
    raise SystemExit('PresenceService location flag anchor not found')
if 'lastGpsBleReplayAtRc21' not in p:
    p = p.replace(field_anchor, field_anchor + '    private var lastGpsBleReplayAtRc21: Long = 0L\n', 1)

poll_anchor = '''            ensurePresenceChannels()
            if (identity.isConfigured) {
'''
if poll_anchor not in p:
    raise SystemExit('PresenceService poll anchor not found')
p = p.replace(poll_anchor, '''            ensurePresenceChannels()
            refreshGpsRuntimeRc21()
            if (identity.isConfigured) {
''', 1)

ensure_old = '''        if (locationMonitoringAllowedForRun && !geoMonitor.isRunning()) geoMonitor.start()
        if (!locationMonitoringAllowedForRun && geoMonitor.isRunning()) geoMonitor.stop()
    }



    private fun receiveLocalChallenge'''
ensure_new = '''        refreshGpsRuntimeRc21()
    }

    private fun refreshGpsRuntimeRc21() {
        if (!identity.isConfigured || !identity.autoPresence) {
            if (geoMonitor.isRunning()) geoMonitor.stop()
            return
        }
        val hasLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val shouldMonitor = identity.isTrustedStoreGpsConfigured && hasLocation

        if (shouldMonitor && !locationMonitoringAllowedForRun) {
            // GPS configuration often arrives after the service was already started by BLE.
            // Promote the existing foreground service to include LOCATION immediately.
            val promoted = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification())
                }
            }.isSuccess
            if (promoted) locationMonitoringAllowedForRun = true
        }

        if (shouldMonitor && locationMonitoringAllowedForRun) {
            if (!geoMonitor.isRunning()) geoMonitor.start()
        } else if (geoMonitor.isRunning()) {
            geoMonitor.stop()
        }

        // Re-send the most recent GPS observation while the authenticated Store BLE session exists.
        // This turns GPS recognition into a stable state rather than a one-shot packet race.
        val now = System.currentTimeMillis()
        val seenAt = identity.lastGpsObservedAt
        if (seenAt > 0L && now - seenAt <= 120_000L && now - lastGpsBleReplayAtRc21 >= 8_000L &&
            EmployeeDirectReplyBridge1977.isAvailable()) {
            if (EmployeeDirectReplyBridge1977.queueReply(
                    "GPS",
                    "__APGPS1|${identity.lastGpsState}|${identity.lastGpsDistanceMeters}|${identity.lastGpsAccuracyMeters}|$seenAt"
                )) {
                lastGpsBleReplayAtRc21 = now
            }
        }
    }



    private fun receiveLocalChallenge'''
if ensure_old not in p:
    raise SystemExit('PresenceService ensure GPS anchor not found')
p = p.replace(ensure_old, ensure_new, 1)
presence.write_text(p, encoding='utf-8')

# -----------------------------------------------------------------------------
# GPS tolerance: keep INSIDE strict, but let NEAR reflect actual reported accuracy.
# GPS is monitoring-only, so using an uncertainty band avoids false disconnects indoors.
# -----------------------------------------------------------------------------
g = geo.read_text(encoding='utf-8')
old_allowance = '            val nearAllowance = accuracy.coerceIn(12f, 40f)\n'
new_allowance = '            val nearAllowance = (accuracy * 0.75f).coerceIn(15f, 90f)\n'
if old_allowance not in g:
    raise SystemExit('OfflineGeoMonitor near allowance anchor not found')
g = g.replace(old_allowance, new_allowance, 1)
geo.write_text(g, encoding='utf-8')

# Assertions
ss = settings.read_text(encoding='utf-8')
mm = store_main.read_text(encoding='utf-8')
pp = presence.read_text(encoding='utf-8')
gg = geo.read_text(encoding='utf-8')
assert 'menuDialog.dismiss()' in ss
assert 'pendingOpenOperationsRc21' in ss
assert 'open_store_operations_rc21' in ss
assert 'تم حفظ GPS — جارٍ تفعيل التعرف وإرسال المنطقة للموظفين' in ss
assert '"الحضور والتشغيل"' in mm and 'open_store_operations_rc21' in mm
assert 'refreshGpsRuntimeRc21()' in pp
assert 'lastGpsBleReplayAtRc21' in pp
assert '__APGPS1|' in pp
assert '(accuracy * 0.75f).coerceIn(15f, 90f)' in gg
print('RC21 GPS runtime + owner UI fix applied')
