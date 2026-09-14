#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
store_p = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
emp_p = ROOT / 'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'

store = store_p.read_text(encoding='utf-8')
emp = emp_p.read_text(encoding='utf-8')

# 1) GPS-recognized employee must count as an authenticated live connection even when BLE is OFF.
old = '''        if (recognized && System.currentTimeMillis() - link.gpsSeenAt <= GPS_RECOGNITION_FRESH_MILLIS) {
            val channels = previous?.channelTimes.orEmpty().toMutableMap().apply { put("GPS • تعرّف", link.gpsSeenAt) }
            nearby[employeeId] = NearbyPhone(
                maxOf(previous?.seenAt ?: 0L, link.gpsSeenAt),
                previous?.rssi ?: -127, channels, previous?.deviceName.orEmpty(), link.gpsSeenAt
            )
        }
'''
new = '''        if (recognized && System.currentTimeMillis() - link.gpsSeenAt <= GPS_RECOGNITION_FRESH_MILLIS) {
            val channels = previous?.channelTimes.orEmpty().toMutableMap().apply {
                put("GPS • داخل/قرب النطاق", link.gpsSeenAt)
                put("Server • GPS heartbeat", link.gpsSeenAt)
            }
            nearby[employeeId] = NearbyPhone(
                maxOf(previous?.seenAt ?: 0L, link.gpsSeenAt),
                previous?.rssi ?: -127, channels, previous?.deviceName.orEmpty(), link.gpsSeenAt
            )
            // A fresh authenticated GPS observation received through the central server is itself
            // proof that the employee app is online. Keep the Store UI connected even with BLE OFF.
            serverPresenceAt[employeeId] = maxOf(serverPresenceAt[employeeId] ?: 0L, link.gpsSeenAt)
            repo.markCompanionLinked(employeeId, "GPS • عبر الخادم", link.gpsSeenAt)
        }
'''
if old not in store:
    raise SystemExit('Store GPS recognized block not found')
store = store.replace(old, new, 1)

# 2) Presence challenge polling must not depend on Bluetooth or a stale serverLinked flag.
old = '''            if (identity.isConfigured) {
                if (identity.serverLinked) {
                    val now = System.currentTimeMillis()
                    if (now >= nextChallengePollAt1981) pollChallenge()
                    if (now - lastMessagePollAt1975 >= 30_000L && now >= nextMessagePollAt1981) {
                        lastMessagePollAt1975 = now
                        pollMessages1975()
                    }
                } else ensureServerLinkWhenAvailable()
            }
'''
new = '''            if (identity.isConfigured && identity.serverUrl.isNotBlank()) {
                val now = System.currentTimeMillis()
                // RC23: server challenge delivery is independent from Bluetooth. Poll even when
                // serverLinked is stale/false; a successful poll re-establishes the server link.
                if (now >= nextChallengePollAt1981) pollChallenge()
                if (now - lastMessagePollAt1975 >= 30_000L && now >= nextMessagePollAt1981) {
                    lastMessagePollAt1975 = now
                    pollMessages1975()
                }
                if (!identity.serverLinked) ensureServerLinkWhenAvailable()
            }
'''
if old not in emp:
    raise SystemExit('Employee challenge poller block not found')
emp = emp.replace(old, new, 1)

# 3) As soon as internet returns, force an immediate challenge poll instead of waiting for BLE state.
old = '''                    nextChallengePollAt1981 = 0L
                    nextMessagePollAt1981 = 0L
                    lastServerLinkAttemptAt = 0L
                    if (identity.isConfigured && !identity.serverLinked) ensureServerLinkWhenAvailable()
'''
new = '''                    nextChallengePollAt1981 = 0L
                    nextMessagePollAt1981 = 0L
                    lastServerLinkAttemptAt = 0L
                    if (identity.isConfigured && identity.serverUrl.isNotBlank()) {
                        pollChallenge()
                        if (!identity.serverLinked) ensureServerLinkWhenAvailable()
                    }
'''
if old not in emp:
    raise SystemExit('Employee network callback block not found')
emp = emp.replace(old, new, 1)

# 4) A successful GPS upload is an authenticated server contact; immediately reopen challenge polling.
old = '''            ).onSuccess { identity.serverLinked = true }.onFailure {
'''
new = '''            ).onSuccess {
                identity.serverLinked = true
                nextChallengePollAt1981 = 0L
            }.onFailure {
'''
if old not in emp:
    raise SystemExit('GPS upload success block not found')
emp = emp.replace(old, new, 1)

store_p.write_text(store, encoding='utf-8')
emp_p.write_text(emp, encoding='utf-8')

s = store_p.read_text(encoding='utf-8')
e = emp_p.read_text(encoding='utf-8')
assert 'GPS • عبر الخادم' in s
assert 'Server • GPS heartbeat' in s
assert 'server challenge delivery is independent from Bluetooth' in e
assert 'pollChallenge()\n                        if (!identity.serverLinked)' in e
assert 'nextChallengePollAt1981 = 0L' in e
print('RC23 GPS-independent connection and proof delivery fix applied')
