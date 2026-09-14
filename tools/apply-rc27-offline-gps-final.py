#!/usr/bin/env python3
from pathlib import Path

# RC27 build trigger: final offline GPS validation.
ROOT = Path('.')
service_p = ROOT / 'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'
ui_p = ROOT / 'buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt'
service = service_p.read_text(encoding='utf-8')
ui = ui_p.read_text(encoding='utf-8')

# RC27 goal: GPS recognition remains operational without Internet after the employee
# has once received/persisted the store zone (from pairing or server). Local LAN GPS
# frames continue to reach Store without Internet. Cloud telemetry is queued and retried.

field_anchor = '    private var lastGpsConfigRevisionRc25 = 0L\n'
if 'private val gpsOfflinePrefsRc27' not in service:
    if field_anchor not in service:
        raise SystemExit('RC27: field anchor missing')
    service = service.replace(field_anchor, field_anchor + '''    private val gpsOfflinePrefsRc27 by lazy { getSharedPreferences("gps_offline_rc27", MODE_PRIVATE) }\n''', 1)

tick_anchor = '            identity.presenceServiceHeartbeatAt = System.currentTimeMillis()\n            syncGpsConfigFromServerRc25()\n'
if 'restoreCachedGpsConfigRc27()' not in service:
    if tick_anchor not in service:
        raise SystemExit('RC27: tick anchor missing')
    service = service.replace(tick_anchor, '            identity.presenceServiceHeartbeatAt = System.currentTimeMillis()\n            restoreCachedGpsConfigRc27()\n            flushQueuedGpsObservationRc27()\n            syncGpsConfigFromServerRc25()\n', 1)

success_anchor = '''                    if (cfg.enabled && cfg.latitude.isFinite() && cfg.longitude.isFinite()) {\n                        identity.trustedStoreLatitude = cfg.latitude\n                        identity.trustedStoreLongitude = cfg.longitude\n                        identity.trustedStoreGpsRadius = cfg.radiusMeters\n'''
if 'cacheGpsConfigRc27(' not in service:
    if success_anchor not in service:
        raise SystemExit('RC27: config success anchor missing')
    service = service.replace(success_anchor, '''                    if (cfg.enabled && cfg.latitude.isFinite() && cfg.longitude.isFinite()) {\n                        identity.trustedStoreLatitude = cfg.latitude\n                        identity.trustedStoreLongitude = cfg.longitude\n                        identity.trustedStoreGpsRadius = cfg.radiusMeters\n                        cacheGpsConfigRc27(cfg.latitude, cfg.longitude, cfg.radiusMeters, cfg.revision)\n''', 1)

obs_anchor = '''        identity.lastGpsAccuracyMeters = accuracy\n        getSharedPreferences("gps_rc26_diag", MODE_PRIVATE).edit()\n            .putLong("observation_at", observedAt)\n            .putString("observation_state", state)\n            .putInt("observation_distance", distance)\n            .putInt("observation_accuracy", accuracy)\n            .apply()\n        if (!identity.isConfigured || identity.serverUrl.isBlank()) return\n        Thread {\n            CentralServerClient.sendEmployeeGeoObservation(\n'''
if 'queueGpsObservationRc27(' not in service:
    if obs_anchor not in service:
        raise SystemExit('RC27: observation anchor missing')
    service = service.replace(obs_anchor, '''        identity.lastGpsAccuracyMeters = accuracy\n        getSharedPreferences("gps_rc26_diag", MODE_PRIVATE).edit()\n            .putLong("observation_at", observedAt)\n            .putString("observation_state", state)\n            .putInt("observation_distance", distance)\n            .putInt("observation_accuracy", accuracy)\n            .apply()\n        queueGpsObservationRc27(state, distance, accuracy, observedAt)\n        if (!identity.isConfigured || identity.serverUrl.isBlank()) return\n        Thread {\n            CentralServerClient.sendEmployeeGeoObservation(\n''', 1)

upload_ok_anchor = '''                getSharedPreferences("gps_rc26_diag", MODE_PRIVATE).edit()\n                    .putLong("upload_ok_at", System.currentTimeMillis())\n                    .putString("upload_state", state)\n                    .putString("upload_error", "")\n                    .apply()\n'''
if 'clearQueuedGpsObservationRc27(observedAt)' not in service:
    if upload_ok_anchor not in service:
        raise SystemExit('RC27: upload success anchor missing')
    service = service.replace(upload_ok_anchor, upload_ok_anchor + '                clearQueuedGpsObservationRc27(observedAt)\n', 1)

method_anchor = '    private fun ensurePresenceChannels() {\n'
if 'private fun restoreCachedGpsConfigRc27()' not in service:
    helpers = '''    private fun cacheGpsConfigRc27(latitude: Double, longitude: Double, radius: Int, revision: Long) {\n        gpsOfflinePrefsRc27.edit()\n            .putBoolean("configured", true)\n            .putLong("lat_bits", java.lang.Double.doubleToRawLongBits(latitude))\n            .putLong("lon_bits", java.lang.Double.doubleToRawLongBits(longitude))\n            .putInt("radius", radius.coerceIn(10, 5000))\n            .putLong("revision", revision)\n            .putLong("cached_at", System.currentTimeMillis())\n            .apply()\n    }\n\n    private fun restoreCachedGpsConfigRc27() {\n        if (identity.trustedStoreLatitude.isFinite() && identity.trustedStoreLongitude.isFinite()) return\n        if (!gpsOfflinePrefsRc27.getBoolean("configured", false)) return\n        val lat = java.lang.Double.longBitsToDouble(gpsOfflinePrefsRc27.getLong("lat_bits", java.lang.Double.doubleToRawLongBits(Double.NaN)))\n        val lon = java.lang.Double.longBitsToDouble(gpsOfflinePrefsRc27.getLong("lon_bits", java.lang.Double.doubleToRawLongBits(Double.NaN)))\n        if (!lat.isFinite() || !lon.isFinite()) return\n        identity.trustedStoreLatitude = lat\n        identity.trustedStoreLongitude = lon\n        identity.trustedStoreGpsRadius = gpsOfflinePrefsRc27.getInt("radius", 25).coerceIn(10, 5000)\n        lastGpsConfigRevisionRc25 = maxOf(lastGpsConfigRevisionRc25, gpsOfflinePrefsRc27.getLong("revision", 0L))\n        refreshGpsRuntimeRc21()\n    }\n\n    private fun queueGpsObservationRc27(state: String, distance: Int, accuracy: Int, observedAt: Long) {\n        gpsOfflinePrefsRc27.edit()\n            .putBoolean("pending", true)\n            .putString("pending_state", state)\n            .putInt("pending_distance", distance)\n            .putInt("pending_accuracy", accuracy)\n            .putLong("pending_observed_at", observedAt)\n            .apply()\n    }\n\n    private fun clearQueuedGpsObservationRc27(observedAt: Long) {\n        if (gpsOfflinePrefsRc27.getLong("pending_observed_at", -1L) == observedAt) {\n            gpsOfflinePrefsRc27.edit().putBoolean("pending", false).apply()\n        }\n    }\n\n    private fun flushQueuedGpsObservationRc27() {\n        if (!gpsOfflinePrefsRc27.getBoolean("pending", false)) return\n        if (!identity.isConfigured || identity.serverUrl.isBlank()) return\n        val observedAt = gpsOfflinePrefsRc27.getLong("pending_observed_at", 0L)\n        if (observedAt <= 0L) return\n        val state = gpsOfflinePrefsRc27.getString("pending_state", "UNKNOWN") ?: "UNKNOWN"\n        val distance = gpsOfflinePrefsRc27.getInt("pending_distance", -1)\n        val accuracy = gpsOfflinePrefsRc27.getInt("pending_accuracy", -1)\n        Thread {\n            CentralServerClient.sendEmployeeGeoObservation(\n                identity.serverUrl, identity.trustedStoreId, identity.employeeId, identity.pairingSecret,\n                identity.installationId, state, distance, accuracy, observedAt\n            ).onSuccess { clearQueuedGpsObservationRc27(observedAt) }\n        }.apply { isDaemon = true }.start()\n    }\n\n'''
    if method_anchor not in service:
        raise SystemExit('RC27: method anchor missing')
    service = service.replace(method_anchor, helpers + method_anchor, 1)

ui_anchor = '            appendLine("GPS Upload: ${if (uploadOkAt > 0L && uploadOkAt >= uploadFailAt) "وصل للخادم ✓ • منذ ${(now-uploadOkAt).coerceAtLeast(0L)/1000}ث" else if (uploadFailAt > 0L) "فشل • ${uploadError.ifBlank { "خطأ غير محدد" }}" else "لم تُرفع قراءة بعد"}")\n'
if 'GPS Offline:' not in ui:
    if ui_anchor not in ui:
        raise SystemExit('RC27: UI diag anchor missing')
    ui = ui.replace(ui_anchor, ui_anchor + '''            val offlineGps = getSharedPreferences("gps_offline_rc27", MODE_PRIVATE)\n            appendLine("GPS Offline: ${if (offlineGps.getBoolean("configured", false)) "إعداد الموقع محفوظ محليًا ✓" else "لا يوجد إعداد محلي محفوظ"} • ${if (offlineGps.getBoolean("pending", false)) "توجد قراءة بانتظار المزامنة" else "لا توجد قراءة معلقة"}")\n''', 1)

service_p.write_text(service, encoding='utf-8')
ui_p.write_text(ui, encoding='utf-8')

s = service_p.read_text(encoding='utf-8')
u = ui_p.read_text(encoding='utf-8')
assert 'restoreCachedGpsConfigRc27()' in s
assert 'queueGpsObservationRc27(state, distance, accuracy, observedAt)' in s
assert 'flushQueuedGpsObservationRc27()' in s
assert 'GPS Offline:' in u
print('RC27 applied: cached GPS zone + offline local recognition + queued cloud telemetry')
