import os
from pathlib import Path

if os.environ.get('GITHUB_ACTIONS') == 'true':
    root = Path.cwd()

    def patch(path, old, new, all_matches=False):
        p = root / path
        if not p.exists():
            return
        text = p.read_text(encoding='utf-8')
        if old not in text:
            return
        text = text.replace(old, new) if all_matches else text.replace(old, new, 1)
        p.write_text(text, encoding='utf-8')

    store = 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
    employee = 'buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt'

    # RC14 marked the first-run prompt as completed before the user actually configured GPS.
    patch(store,
'''        val prefs = getSharedPreferences("gps_first_setup_rc14", MODE_PRIVATE)
        if (prefs.getBoolean("prompted", false)) return
        if (repo.gpsRecognitionEnabled && repo.isGpsConfigured) {
            prefs.edit().putBoolean("prompted", true).apply()
            return
        }
        prefs.edit().putBoolean("prompted", true).apply()
        nearbyRefreshHandler.postDelayed({ if (!isFinishing) showGpsSetupDialog(firstTime = true) }, 700L)
''',
'''        if (repo.gpsRecognitionEnabled && repo.isGpsConfigured) return
        nearbyRefreshHandler.postDelayed({ if (!isFinishing && !repo.isGpsConfigured) showGpsSetupDialog(firstTime = true) }, 700L)
''')

    # Do not silently accept a very old Store position as the Store zone center.
    patch(store,
'''                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                val best = providers.mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
                    .maxByOrNull { it.time }
''',
'''                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                val nowFix = System.currentTimeMillis()
                val best = providers.mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
                    .filter { nowFix - it.time <= 15 * 60_000L }
                    .maxByOrNull { it.time }
''')

    store_helper = '''    private fun addGpsZoneCardRc14Fix(root: LinearLayout) {
        val card = UiKit.card(this, p, 14)
        card.addView(UiKit.sectionLabel(this, p, "GPS • منطقة التعرف على هاتف الموظف"))
        val stateText = when {
            repo.gpsRecognitionEnabled && repo.isGpsConfigured -> "✓ مفعّل • نطاق ${repo.gpsRadiusMeters}م • موقع المحل محفوظ"
            repo.gpsRecognitionEnabled -> "مفعّل لكن موقع المحل غير محدد"
            else -> "غير مفعّل"
        }
        card.addView(UiKit.subtitle(this, p, "$stateText\nحدد موقع المحل والنطاق هنا، ثم أعد مزامنة المنطقة مع الهاتف المرتبط."))
        card.addView(UiKit.button(this, p, "تفعيل GPS وتحديد منطقة المحل", false).apply {
            setOnClickListener { showGpsSetupDialog(firstTime = !repo.isGpsConfigured) }
        })
        if (repo.gpsRecognitionEnabled && repo.isGpsConfigured) {
            card.addView(UiKit.button(this, p, "إعادة إرسال منطقة GPS للهاتف", false).apply {
                setOnClickListener { refreshGpsConfigToEmployeesRc14Fix() }
            })
        }
        root.addView(card)
    }

    private fun refreshGpsConfigToEmployeesRc14Fix() {
        if (!::directBle.isInitialized) return
        StoreDirectLinkBridge1977.unbind(directBle)
        runCatching { directBle.stop() }
        directBle = BleDirectLinkClient(this) { employeeId, connected, message ->
            runOnUiThread { markDirectBleState(employeeId, connected, message) }
        }
        StoreDirectLinkBridge1977.bind(directBle)
        if (::status.isInitialized) status.text = "تم حفظ منطقة GPS — بانتظار الهاتف لاستلام الإعداد"
        nearbyRefreshHandler.postDelayed({ if (!isFinishing) ensurePresenceDiscoveryRunning() }, 600L)
    }

'''
    patch(store, '    private fun maybeShowGpsFirstSetup() {\n', store_helper + '    private fun maybeShowGpsFirstSetup() {\n')
    patch(store, '        addOwnerShortcutCard(root)\n', '        addGpsZoneCardRc14Fix(root)\n        addOwnerShortcutCard(root)\n', all_matches=True)
    patch(store, '"إعداد GPS وتحديد النطاق"', '"إعداد GPS وتحديد منطقة المحل والنطاق"', all_matches=True)

    # Once the Store zone is saved, recreate only the Store-side client object so the existing
    # authenticated GATT handshake sends CONFIG again to phones paired before GPS was configured.
    patch(store,
'''                    repo.storeLatitude = best.latitude
                    repo.storeLongitude = best.longitude
                    if (::status.isInitialized) status.text = "✓ GPS مفعّل • موقع المحل محفوظ • النطاق ${meters}م"
                    dialog.dismiss()
                    refreshDashboard()
                    return@setOnClickListener
''',
'''                    repo.storeLatitude = best.latitude
                    repo.storeLongitude = best.longitude
                    if (::status.isInitialized) status.text = "✓ GPS مفعّل • موقع المحل محفوظ • النطاق ${meters}م • مزامنة المنطقة مع الهاتف"
                    dialog.dismiss()
                    refreshGpsConfigToEmployeesRc14Fix()
                    refreshDashboard()
                    return@setOnClickListener
''')
    patch(store,
'''                            if (::status.isInitialized) status.text = "✓ GPS مفعّل • تم تحديد موقع المحل • النطاق ${meters}م"
                            if (dialog.isShowing) dialog.dismiss()
                            refreshDashboard()
''',
'''                            if (::status.isInitialized) status.text = "✓ GPS مفعّل • تم تحديد موقع المحل • النطاق ${meters}م • مزامنة المنطقة مع الهاتف"
                            if (dialog.isShowing) dialog.dismiss()
                            refreshGpsConfigToEmployeesRc14Fix()
                            refreshDashboard()
''')

    employee_helper = '''    private fun addEmployeeGpsCardRc14Fix(root: LinearLayout) {
        if (!identity.isConfigured) return
        val lm = getSystemService(LocationManager::class.java)
        val locationOn = lm != null && (runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ||
            runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false))
        val hasLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val card = UiKit.card(this, p)
        card.addView(UiKit.sectionLabel(this, p, "GPS • منطقة المحل"))
        val zone = if (identity.isTrustedStoreGpsConfigured) "✓ تم استلام منطقة المحل • النطاق ${identity.trustedStoreGpsRadius}م" else "لم تصل منطقة المحل بعد — احفظها من تطبيق المحل وأبق Bluetooth مفعّلًا لثوانٍ"
        val reading = if (identity.lastGpsObservedAt > 0L) "آخر قراءة: ${identity.lastGpsState} • ${identity.lastGpsDistanceMeters}م • دقة ±${identity.lastGpsAccuracyMeters}م" else "لا توجد قراءة GPS حديثة"
        card.addView(UiKit.subtitle(this, p, "$zone\nإذن الموقع: ${if (hasLocation) "مسموح" else "مطلوب"} • خدمة الموقع: ${if (locationOn) "تعمل" else "مغلقة"}\n$reading"))
        card.addView(UiKit.button(this, p, if (hasLocation) "تشغيل/تحديث GPS" else "السماح بالموقع وتشغيل GPS", false).apply {
            setOnClickListener {
                if (!hasLocation) requestLocationPermissionWithDisclosure("بعد السماح سيبدأ GPS تلقائيًا عند استلام منطقة المحل")
                else if (!locationOn) startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                else { identity.autoPresence = true; startPresence(); status.text = "GPS جاهز — انتظار قراءة حديثة" }
            }
        })
        root.addView(card)
    }

'''
    patch(employee, '    private fun buildUi() {\n', employee_helper + '    private fun buildUi() {\n')
    patch(employee, '        addEmployeeTabs1978(root)\n', '        addEmployeeTabs1978(root)\n        addEmployeeGpsCardRc14Fix(root)\n', all_matches=True)
