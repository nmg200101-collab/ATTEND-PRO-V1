#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
store_p = ROOT / 'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
emp_p = ROOT / 'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'

# -----------------------------------------------------------------------------
# RC23 field fix:
# 1) GPS recognition must remain useful after Bluetooth is switched off.
# 2) A stale/non-delivering BLE client must never swallow a presence-proof request.
# 3) Employee must poll server challenges independently from the serverLinked flag.
# Protected pairing/BLE runtime files are intentionally untouched.
# -----------------------------------------------------------------------------

s = store_p.read_text(encoding='utf-8')

old = '''                    val secret = SecretCodec.decode(e.pairingSecret)
                    val requestToken = java.security.SecureRandom().nextInt()
                    val expiresAt = System.currentTimeMillis() + 60_000L
                    val directSent = if (secret != null) directBle.sendChallenge(e.employeeId, chosen.second, expiresAt, requestToken, requestedAction) else false
                    val lanSent = if (!directSent && secret != null && isLanConnected(e.employeeId)) LocalChallengeSender.send(e.employeeId, secret, chosen.second, System.currentTimeMillis(), requestToken, requestedAction) else false
                    val localSent = directSent || lanSent
                    // One request, one transport: prefer an already verified local channel.
                    // Only fall back to the server when neither BLE-ACK nor LAN-ACK could deliver it.
'''
new = '''                    val secret = SecretCodec.decode(e.pairingSecret)
                    val requestToken = java.security.SecureRandom().nextInt()
                    val expiresAt = System.currentTimeMillis() + 60_000L
                    val routeNowRc23 = System.currentTimeMillis()
                    // RC23: never call the BLE challenge sender merely because its client object exists.
                    // It is eligible only while the Store has a fresh authenticated GATT acknowledgement.
                    // This prevents Bluetooth-OFF / GPS-only employees from being reported as "sent locally"
                    // when nothing was actually delivered.
                    val directReadyRc23 = isDirectBleUiConnected(e.employeeId, routeNowRc23)
                    val directSent = if (secret != null && directReadyRc23) directBle.sendChallenge(e.employeeId, chosen.second, expiresAt, requestToken, requestedAction) else false
                    val lanReadyRc23 = isLanConnected(e.employeeId, routeNowRc23)
                    val lanSent = if (!directSent && secret != null && lanReadyRc23) LocalChallengeSender.send(e.employeeId, secret, chosen.second, System.currentTimeMillis(), requestToken, requestedAction) else false
                    val localSent = directSent || lanSent
                    // One request, one transport. GPS is recognition, not a message transport.
                    // If Bluetooth/LAN are not currently ACK-confirmed, the proof request goes through
                    // the central server so a GPS-recognized employee can receive it with Bluetooth OFF.
'''
if old not in s:
    raise SystemExit('Store proof routing anchor not found after RC22 chain')
s = s.replace(old, new, 1)

old = '''                        val result = if (repo.hasCentralCredentials()) {
                            CentralServerClient.createPresenceChallenge(repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this), e.employeeId, chosen.second, requestedAction)
                        } else Result.failure(IllegalStateException("لا توجد قناة محلية موثقة ولا ربط خادم مهيأ"))
'''
new = '''                        val result = if (repo.hasCentralCredentials()) {
                            CentralServerClient.createPresenceChallenge(repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this), e.employeeId, chosen.second, requestedAction)
                        } else Result.failure(IllegalStateException("لا توجد قناة Bluetooth/LAN موثقة ولا ربط خادم مهيأ لإيصال الطلب إلى موظف GPS"))
'''
if old not in s:
    raise SystemExit('Store server fallback anchor not found')
s = s.replace(old, new, 1)

# Make the employee list explicitly distinguish GPS recognition from a transport connection.
old = '''        val names = employees.map { e ->
            val seen = nearby[e.employeeId]?.let { System.currentTimeMillis() - it.seenAt < 120_000L } == true
            "${e.displayName} ${if (seen) "• قريب الآن" else "• غير ظاهر حاليًا"}"
        }
'''
new = '''        val names = employees.map { e ->
            val nowRc23 = System.currentTimeMillis()
            val gpsSeenRc23 = isGpsRecognizedFresh(e.employeeId, nowRc23)
            val seen = nearby[e.employeeId]?.let { nowRc23 - it.seenAt < 120_000L } == true
            val stateRc23 = when {
                gpsSeenRc23 -> "• داخل/قرب GPS"
                seen -> "• قريب الآن"
                else -> "• غير ظاهر حاليًا"
            }
            "${e.displayName} $stateRc23"
        }
'''
if old not in s:
    raise SystemExit('Store presence employee label anchor not found')
s = s.replace(old, new, 1)
store_p.write_text(s, encoding='utf-8')

p = emp_p.read_text(encoding='utf-8')
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
new = '''            if (identity.isConfigured) {
                val serverUsableRc23 = identity.serverUrl.isNotBlank() && identity.trustedStoreId.isNotBlank()
                if (serverUsableRc23) {
                    val now = System.currentTimeMillis()
                    // RC23: challenge delivery is independent from Bluetooth and from a stale serverLinked flag.
                    // Polling itself authenticates with storeId/employeeId/pairingSecret/installationId and can
                    // recover the link. This is required for GPS-only presence when Bluetooth is switched off.
                    if (now >= nextChallengePollAt1981) pollChallenge()
                    if (now - lastMessagePollAt1975 >= 30_000L && now >= nextMessagePollAt1981) {
                        lastMessagePollAt1975 = now
                        pollMessages1975()
                    }
                    if (!identity.serverLinked) ensureServerLinkWhenAvailable()
                }
            }
'''
if old not in p:
    raise SystemExit('Employee challenge poller anchor not found after RC22 chain')
p = p.replace(old, new, 1)

# Do not attempt an invalid registration URL while offline/unconfigured.
old = '''    private fun ensureServerLinkWhenAvailable() {
        val now = System.currentTimeMillis()
        if (serverLinkInFlight || now - lastServerLinkAttemptAt < 45_000L || !identity.isConfigured) return
'''
new = '''    private fun ensureServerLinkWhenAvailable() {
        val now = System.currentTimeMillis()
        if (serverLinkInFlight || now - lastServerLinkAttemptAt < 45_000L || !identity.isConfigured || identity.serverUrl.isBlank() || identity.trustedStoreId.isBlank()) return
'''
if old not in p:
    raise SystemExit('Employee server relink anchor not found')
p = p.replace(old, new, 1)

emp_p.write_text(p, encoding='utf-8')

ss = store_p.read_text(encoding='utf-8')
pp = emp_p.read_text(encoding='utf-8')
assert 'directReadyRc23 = isDirectBleUiConnected' in ss
assert 'lanReadyRc23 = isLanConnected' in ss
assert 'GPS is recognition' in ss
assert 'داخل/قرب GPS' in ss
assert 'serverUsableRc23' in pp
assert 'if (now >= nextChallengePollAt1981) pollChallenge()' in pp
assert 'identity.serverUrl.isBlank() || identity.trustedStoreId.isBlank()' in pp
print('RC23 GPS-independent recognition/proof routing applied')
