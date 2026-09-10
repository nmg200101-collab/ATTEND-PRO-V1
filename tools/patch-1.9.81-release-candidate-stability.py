from pathlib import Path

ROOT = Path('buildsrc')

def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing anchor: {label}')
    return text.replace(old, new, 1)

# PresenceService: prevent overlapping server polls, add bounded backoff, and modern network callback.
p = ROOT / 'employee-app/src/main/java/com/attendpro/employee/PresenceService.kt'
s = p.read_text(encoding='utf-8')
if 'import android.net.ConnectivityManager' not in s:
    s = s.replace('import android.content.pm.PackageManager\n', 'import android.content.pm.PackageManager\nimport android.net.ConnectivityManager\nimport android.net.Network\n', 1)

s = must_replace(s,
'''    @Volatile private var serverLinkInFlight = false
    private var lastServerLinkAttemptAt = 0L
    private var lastMessagePollAt1975 = 0L
''',
'''    @Volatile private var serverLinkInFlight = false
    @Volatile private var challengePollInFlight1981 = false
    @Volatile private var messagePollInFlight1981 = false
    private var lastServerLinkAttemptAt = 0L
    private var lastMessagePollAt1975 = 0L
    private var nextChallengePollAt1981 = 0L
    private var nextMessagePollAt1981 = 0L
    private var challengeFailures1981 = 0
    private var messageFailures1981 = 0
    private var networkCallback1981: ConnectivityManager.NetworkCallback? = null
''','presence fields')

s = must_replace(s,
'''                if (identity.serverLinked) {
                    pollChallenge()
                    val now = System.currentTimeMillis()
                    if (now - lastMessagePollAt1975 >= 30_000L) { lastMessagePollAt1975 = now; pollMessages1975() }
                } else ensureServerLinkWhenAvailable()
''',
'''                if (identity.serverLinked) {
                    val now = System.currentTimeMillis()
                    if (now >= nextChallengePollAt1981) pollChallenge()
                    if (now - lastMessagePollAt1975 >= 30_000L && now >= nextMessagePollAt1981) {
                        lastMessagePollAt1975 = now
                        pollMessages1975()
                    }
                } else ensureServerLinkWhenAvailable()
''','poller scheduling')

s = must_replace(s,
'''        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"اكتشاف جهاز المحل",NotificationManager.IMPORTANCE_LOW).apply {
            description = "يحافظ على التعرف الآمن على هاتف الموظف عبر الشبكة المحلية"
        })
''',
'''        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"اكتشاف جهاز المحل",NotificationManager.IMPORTANCE_LOW).apply {
            description = "يحافظ على التعرف الآمن على هاتف الموظف عبر الشبكة المحلية"
        })
        registerNetworkCallback1981()
''','network callback register')

anchor = '    private fun canAdvertiseAndServeGatt(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || (\n'
helper = r'''    private fun registerNetworkCallback1981() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || networkCallback1981 != null) return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handler.post {
                    nextChallengePollAt1981 = 0L
                    nextMessagePollAt1981 = 0L
                    lastServerLinkAttemptAt = 0L
                    if (identity.isConfigured && !identity.serverLinked) ensureServerLinkWhenAvailable()
                }
            }
        }
        if (runCatching { cm.registerDefaultNetworkCallback(callback) }.isSuccess) networkCallback1981 = callback
    }

    private fun unregisterNetworkCallback1981() {
        val callback = networkCallback1981 ?: return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        runCatching { cm.unregisterNetworkCallback(callback) }
        networkCallback1981 = null
    }

    private fun serverBackoff1981(failures: Int, baseMillis: Long, maxMillis: Long): Long {
        val shift = (failures - 1).coerceIn(0, 4)
        return (baseMillis * (1L shl shift)).coerceAtMost(maxMillis)
    }

'''
if helper.strip() not in s:
    if anchor not in s: raise SystemExit('missing network helper anchor')
    s = s.replace(anchor, helper + anchor, 1)

old_poll_challenge = '''    private fun pollChallenge() {
        Thread {
            val result = CentralServerClient.pollEmployeePresenceChallenge(identity.serverUrl,identity.trustedStoreId,identity.employeeId,identity.pairingSecret,identity.installationId)
            if (result.isFailure) { identity.serverLinked = false; return@Thread }
            identity.serverLinked = true
            val challenge = result.getOrNull() ?: return@Thread
            if(challenge.challengeId==identity.pendingChallengeId || challenge.expiresAt<=System.currentTimeMillis()) return@Thread
            if (!identity.acceptChallengeOnce(challenge.challengeId, challenge.expiresAt)) return@Thread
            val action = challenge.action ?: AttendanceAction.CHECK_IN
            identity.pendingChallengeId=challenge.challengeId;identity.pendingChallengeMethod=challenge.requiredMethod.name;identity.pendingChallengeExpiresAt=challenge.expiresAt;identity.pendingChallengeAction=action.name
            AttendanceRequestNotifier.notifyChallenge(this, challenge.challengeId, challenge.requiredMethod, challenge.expiresAt, action)
            if (identity.employeeVoicePromptsEnabled) {
                val configured = identity.employeeRequestVoiceText.replace("{name}", identity.displayName).trim()
                voicePrompter.speak(configured.ifBlank { "${identity.displayName}، يرجى إثبات ${if (action == AttendanceAction.CHECK_IN) "حضورك" else "انصرافك"}" })
            }
        }.start()
    }
'''
new_poll_challenge = '''    private fun pollChallenge() {
        val now = System.currentTimeMillis()
        if (challengePollInFlight1981 || now < nextChallengePollAt1981) return
        challengePollInFlight1981 = true
        Thread {
            try {
                val result = CentralServerClient.pollEmployeePresenceChallenge(identity.serverUrl,identity.trustedStoreId,identity.employeeId,identity.pairingSecret,identity.installationId)
                if (result.isFailure) {
                    challengeFailures1981++
                    nextChallengePollAt1981 = System.currentTimeMillis() + serverBackoff1981(challengeFailures1981, 10_000L, 60_000L)
                    if (challengeFailures1981 >= 3) identity.serverLinked = false
                    return@Thread
                }
                challengeFailures1981 = 0
                nextChallengePollAt1981 = 0L
                identity.serverLinked = true
                val challenge = result.getOrNull() ?: return@Thread
                if(challenge.challengeId==identity.pendingChallengeId || challenge.expiresAt<=System.currentTimeMillis()) return@Thread
                if (!identity.acceptChallengeOnce(challenge.challengeId, challenge.expiresAt)) return@Thread
                val action = challenge.action ?: AttendanceAction.CHECK_IN
                identity.pendingChallengeId=challenge.challengeId;identity.pendingChallengeMethod=challenge.requiredMethod.name;identity.pendingChallengeExpiresAt=challenge.expiresAt;identity.pendingChallengeAction=action.name
                AttendanceRequestNotifier.notifyChallenge(this, challenge.challengeId, challenge.requiredMethod, challenge.expiresAt, action)
                if (identity.employeeVoicePromptsEnabled) {
                    val configured = identity.employeeRequestVoiceText.replace("{name}", identity.displayName).trim()
                    voicePrompter.speak(configured.ifBlank { "${identity.displayName}، يرجى إثبات ${if (action == AttendanceAction.CHECK_IN) "حضورك" else "انصرافك"}" })
                }
            } finally {
                challengePollInFlight1981 = false
            }
        }.apply { isDaemon = true }.start()
    }
'''
s = must_replace(s, old_poll_challenge, new_poll_challenge, 'challenge poll')

old_poll_messages = '''    private fun pollMessages1975() {
        if (!identity.employeeMessageNotificationsEnabled) return
        Thread {
            val result = CentralServerClient.employeeMessages(identity.serverUrl, identity.trustedStoreId, identity.employeeId, identity.pairingSecret, identity.installationId, false, 20)
            result.getOrNull().orEmpty().forEach { message -> EmployeeMessageNotifier1975.notify(this, message) }
        }.start()
    }
'''
new_poll_messages = '''    private fun pollMessages1975() {
        if (!identity.employeeMessageNotificationsEnabled) return
        val now = System.currentTimeMillis()
        if (messagePollInFlight1981 || now < nextMessagePollAt1981) return
        messagePollInFlight1981 = true
        Thread {
            try {
                val result = CentralServerClient.employeeMessages(identity.serverUrl, identity.trustedStoreId, identity.employeeId, identity.pairingSecret, identity.installationId, false, 20)
                if (result.isFailure) {
                    messageFailures1981++
                    nextMessagePollAt1981 = System.currentTimeMillis() + serverBackoff1981(messageFailures1981, 30_000L, 120_000L)
                    return@Thread
                }
                messageFailures1981 = 0
                nextMessagePollAt1981 = 0L
                result.getOrNull().orEmpty().forEach { message -> EmployeeMessageNotifier1975.notify(this, message) }
            } finally {
                messagePollInFlight1981 = false
            }
        }.apply { isDaemon = true }.start()
    }
'''
s = must_replace(s, old_poll_messages, new_poll_messages, 'message poll')

s = must_replace(s,
'''        handler.removeCallbacks(challengePoller);network.stop();bluetooth.stop();localChallenges.stop();directBle.stop();geoMonitor.stop();BleChallengeInbox.stop();voicePrompter.shutdown()
        super.onDestroy()
''',
'''        handler.removeCallbacks(challengePoller);network.stop();bluetooth.stop();localChallenges.stop();directBle.stop();geoMonitor.stop();BleChallengeInbox.stop();voicePrompter.shutdown()
        unregisterNetworkCallback1981()
        super.onDestroy()
''','network callback unregister')
p.write_text(s, encoding='utf-8')

# Store messages: avoid duplicate refresh calls.
p = ROOT / 'store-app/src/main/java/com/attendpro/store/StoreMessages1975Activity.kt'
s = p.read_text(encoding='utf-8')
s = must_replace(s,
'''    private lateinit var identity: DeviceIdentity
    private val p by lazy { UiKit.palette(this) }
''',
'''    private lateinit var identity: DeviceIdentity
    private val p by lazy { UiKit.palette(this) }
    @Volatile private var inboxLoadInFlight1981 = false
''','store inbox field')
old_load = '''    private fun loadMessages(root: LinearLayout) {
        Thread {
            val result = CentralServerClient.storeMessagesInbox(repo.serverUrl, repo.centralAccessToken, repo.storeId, identity, 100)
            runOnUiThread {
                result.onSuccess { messages -> renderMessages(root, messages) }
                    .onFailure { toast("تعذر تحميل الرسائل: ${it.message}") }
            }
        }.start()
    }
'''
new_load = '''    private fun loadMessages(root: LinearLayout) {
        if (inboxLoadInFlight1981) return
        inboxLoadInFlight1981 = true
        Thread {
            try {
                val result = CentralServerClient.storeMessagesInbox(repo.serverUrl, repo.centralAccessToken, repo.storeId, identity, 100)
                runOnUiThread {
                    result.onSuccess { messages -> renderMessages(root, messages) }
                        .onFailure { toast("تعذر تحميل الرسائل: ${it.message}") }
                }
            } finally {
                inboxLoadInFlight1981 = false
            }
        }.apply { isDaemon = true }.start()
    }
'''
s = must_replace(s, old_load, new_load, 'store inbox load')
p.write_text(s, encoding='utf-8')

# Employee messages: avoid duplicate refresh calls.
p = ROOT / 'employee-app/src/main/java/com/attendpro/employee/EmployeeMessages1975Activity.kt'
s = p.read_text(encoding='utf-8')
s = must_replace(s,
'''    private lateinit var localStore: EmployeeLocalMessageStore1977
''',
'''    private lateinit var localStore: EmployeeLocalMessageStore1977
    @Volatile private var loadInFlight1981 = false
''','employee message field')
s = must_replace(s,
'''    private fun load() {
        val root = LinearLayout(this).apply {
''',
'''    private fun load() {
        if (loadInFlight1981) return
        loadInFlight1981 = true
        val root = LinearLayout(this).apply {
''','employee load start')
s = must_replace(s,
'''            runOnUiThread {
                root.removeView(loading)
''',
'''            runOnUiThread {
                loadInFlight1981 = false
                root.removeView(loading)
''','employee load completion')
p.write_text(s, encoding='utf-8')

# JVM regression coverage for credential migration introduced in 1.9.80.
test = ROOT / 'core/src/test/java/com/attendpro/core/CredentialHash1980Test.kt'
test.write_text(r'''package com.attendpro.core

import org.junit.Assert.*
import org.junit.Test

class CredentialHash1980Test {
    @Test fun saltedHashAcceptsCorrectAndRejectsWrongCredential() {
        val first = CredentialHash1980.hash("735921")
        val second = CredentialHash1980.hash("735921")
        assertTrue(first.startsWith("pbkdf2-sha256:"))
        assertNotEquals(first, second)
        assertTrue(CredentialHash1980.verify(first, "735921"))
        assertFalse(CredentialHash1980.verify(first, "735922"))
        assertFalse(CredentialHash1980.needsUpgrade(first))
    }

    @Test fun legacySha256CredentialRemainsReadableForMigration() {
        val legacy = PairingProtocol.pinHash("246810")
        assertTrue(CredentialHash1980.needsUpgrade(legacy))
        assertTrue(CredentialHash1980.verify(legacy, "246810"))
        assertFalse(CredentialHash1980.verify(legacy, "246811"))
    }
}
''', encoding='utf-8')

print('ATTEND-PRO 1.9.81 release-candidate stability patch applied')
