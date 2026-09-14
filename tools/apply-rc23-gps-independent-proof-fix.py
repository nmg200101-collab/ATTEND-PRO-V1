#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
store_p = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
emp_p = ROOT / 'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'

store = store_p.read_text(encoding='utf-8')
emp = emp_p.read_text(encoding='utf-8')

# RC23 is applied after RC22, which already chains the GPS-independent proof route.
# Here we only harden GPS-as-live-presence and immediate server recovery without
# touching protected pairing/Bluetooth runtime sources.

old = '''        if (recognized && System.currentTimeMillis() - seenAt <= GPS_RECOGNITION_FRESH_MILLIS) {
            val channels = previous?.channelTimes.orEmpty().toMutableMap().apply { put("GPS • $source", seenAt) }
            nearby[employeeId] = NearbyPhone(
                maxOf(previous?.seenAt ?: 0L, seenAt), previous?.rssi ?: -127, channels,
                previous?.deviceName.orEmpty(), seenAt
            )
            repo.markCompanionLinked(employeeId, "GPS • $source", seenAt)
        }
'''
new = '''        if (recognized && System.currentTimeMillis() - seenAt <= GPS_RECOGNITION_FRESH_MILLIS) {
            val channels = previous?.channelTimes.orEmpty().toMutableMap().apply {
                put("GPS • $source", seenAt)
                if (source == "Server") put("Server • GPS heartbeat", seenAt)
            }
            nearby[employeeId] = NearbyPhone(
                maxOf(previous?.seenAt ?: 0L, seenAt), previous?.rssi ?: -127, channels,
                previous?.deviceName.orEmpty(), seenAt
            )
            // A fresh signed GPS observation is a live authenticated presence signal.
            // Keep the employee visible/connected even when Bluetooth is switched off.
            serverPresenceAt[employeeId] = maxOf(serverPresenceAt[employeeId] ?: 0L, seenAt)
            repo.markCompanionLinked(employeeId, if (source == "Server") "GPS • عبر الخادم" else "GPS • $source", seenAt)
        }
'''
if old not in store:
    raise SystemExit('Store GPS applyGpsObservation block not found after RC22 chain')
store = store.replace(old, new, 1)

# RC22 already changed challenge polling through apply-rc23-gps-independent-proof-route.py.
# Add immediate recovery on network return when the exact callback anchor exists.
old_net = '''                    nextChallengePollAt1981 = 0L
                    nextMessagePollAt1981 = 0L
                    lastServerLinkAttemptAt = 0L
                    if (identity.isConfigured && !identity.serverLinked) ensureServerLinkWhenAvailable()
'''
new_net = '''                    nextChallengePollAt1981 = 0L
                    nextMessagePollAt1981 = 0L
                    lastServerLinkAttemptAt = 0L
                    if (identity.isConfigured && identity.serverUrl.isNotBlank()) {
                        pollChallenge()
                        if (!identity.serverLinked) ensureServerLinkWhenAvailable()
                    }
'''
if old_net in emp:
    emp = emp.replace(old_net, new_net, 1)

# A successful GPS upload proves current server reachability and reopens challenge polling.
old_upload = '''            ).onSuccess { identity.serverLinked = true }.onFailure {
'''
new_upload = '''            ).onSuccess {
                identity.serverLinked = true
                nextChallengePollAt1981 = 0L
            }.onFailure {
'''
if old_upload in emp:
    emp = emp.replace(old_upload, new_upload, 1)

store_p.write_text(store, encoding='utf-8')
emp_p.write_text(emp, encoding='utf-8')

s = store_p.read_text(encoding='utf-8')
e = emp_p.read_text(encoding='utf-8')
assert 'GPS • عبر الخادم' in s
assert 'Server • GPS heartbeat' in s
assert 'fresh signed GPS observation is a live authenticated presence signal' in s
assert 'challenge delivery is independent from Bluetooth' in e
assert 'if (now >= nextChallengePollAt1981) pollChallenge()' in e
print('RC23 GPS-independent connection/proof hardening applied')
