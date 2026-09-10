from pathlib import Path

ROOT = Path('buildsrc')
CORE = ROOT / 'core/src/main/java/com/attendpro/core'
STORE = ROOT / 'store-app/src/main/java/com/attendpro/store'
EMP = ROOT / 'employee-app/src/main/java/com/attendpro/employee'


def must(path: Path):
    if not path.exists():
        raise SystemExit(f'missing required file: {path}')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'missing anchor: {label}')
    return text.replace(old, new, 1)

# 1) PBKDF2 credential helper (administrative credentials only; pairing protocol remains untouched).
cred = CORE / 'CredentialHash1980.kt'
cred.write_text(r'''package com.attendpro.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** 1.9.80: slow salted credential hashing for local administration gates. */
object CredentialHash1980 {
    private const val PREFIX = "pbkdf2-sha256"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun hash(value: String): String {
        val clean = value.trim()
        require(clean.isNotBlank()) { "credential must not be blank" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val derived = derive(clean, salt, ITERATIONS)
        return "$PREFIX:$ITERATIONS:${b64(salt)}:${b64(derived)}"
    }

    fun verify(stored: String, entered: String): Boolean {
        if (stored.isBlank() || entered.isBlank()) return false
        if (!stored.startsWith("$PREFIX:")) return PairingProtocol.matchesPin(stored, entered)
        return runCatching {
            val parts = stored.split(':')
            require(parts.size == 4 && parts[0] == PREFIX)
            val rounds = parts[1].toInt().coerceIn(50_000, 500_000)
            val salt = b64d(parts[2])
            val expected = b64d(parts[3])
            val actual = derive(entered.trim(), salt, rounds)
            MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false)
    }

    fun needsUpgrade(stored: String): Boolean = stored.isNotBlank() && !stored.startsWith("$PREFIX:")

    private fun derive(value: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(value.toCharArray(), salt, rounds, KEY_BITS)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    private fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun b64d(text: String): ByteArray = Base64.getUrlDecoder().decode(text)
}
''', encoding='utf-8')

# 2) Migrate pairing secret and employee records into Android-Keystore-backed SecureTokenVault.
local = CORE / 'LocalStores.kt'; must(local)
s = local.read_text(encoding='utf-8')
s = replace_once(s,
'''class EmployeeIdentityStore(context: Context) {
    private val prefs = context.getSharedPreferences("employee_identity", Context.MODE_PRIVATE)
''',
'''class EmployeeIdentityStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("employee_identity", Context.MODE_PRIVATE)
    private val secureVault = SecureTokenVault(appContext)
''','employee secure vault init')
old_pair = '''    var pairingSecret: String
        get() {
            val existing = prefs.getString("pairingSecret", "") ?: ""
            if (SecretCodec.isValid(existing)) return existing
            val generated = SecretCodec.encode(SecretCodec.generate())
            prefs.edit().putString("pairingSecret", generated).apply()
            return generated
        }
        set(value) = prefs.edit().putString("pairingSecret", value).apply()
'''
new_pair = '''    var pairingSecret: String
        get() {
            val secured = secureVault.get("employee_pairing_secret_v1980")
            if (SecretCodec.isValid(secured)) return secured
            val legacy = prefs.getString("pairingSecret", "") ?: ""
            if (SecretCodec.isValid(legacy)) {
                secureVault.put("employee_pairing_secret_v1980", legacy)
                if (secureVault.get("employee_pairing_secret_v1980") == legacy) prefs.edit().remove("pairingSecret").apply()
                return legacy
            }
            val generated = SecretCodec.encode(SecretCodec.generate())
            secureVault.put("employee_pairing_secret_v1980", generated)
            return generated
        }
        set(value) {
            if (SecretCodec.isValid(value)) {
                secureVault.put("employee_pairing_secret_v1980", value)
                if (secureVault.get("employee_pairing_secret_v1980") == value) prefs.edit().remove("pairingSecret").apply()
            }
        }
'''
s = replace_once(s, old_pair, new_pair, 'employee pairing secret migration')

s = replace_once(s,
'        val ok = PairingProtocol.matchesPin(storeAdminPinHash, entered)\n',
'''        val currentHash = storeAdminPinHash
        val ok = CredentialHash1980.verify(currentHash, entered)
        if (ok && CredentialHash1980.needsUpgrade(currentHash)) storeAdminPinHash = CredentialHash1980.hash(entered)
''','store admin verify')
s = replace_once(s,
'        storeAdminPinHash = PairingProtocol.pinHash(pin)\n',
'        storeAdminPinHash = CredentialHash1980.hash(pin)\n','store admin set')
s = replace_once(s,
'        val ok = PairingProtocol.matchesPin(systemPasswordHash, entered)\n',
'''        val currentHash = systemPasswordHash
        val ok = CredentialHash1980.verify(currentHash, entered)
        if (ok && CredentialHash1980.needsUpgrade(currentHash)) systemPasswordHash = CredentialHash1980.hash(entered)
''','system password verify')
s = replace_once(s,
'    fun setSystemPassword(password: String) { systemPasswordHash = PairingProtocol.pinHash(password); failedSystemAttempts = 0; systemLockUntil = 0L }\n',
'    fun setSystemPassword(password: String) { systemPasswordHash = CredentialHash1980.hash(password); failedSystemAttempts = 0; systemLockUntil = 0L }\n','system password set')
s = replace_once(s,
'        val ok = PairingProtocol.matchesPin(ownerCodeHash, entered)\n',
'''        val currentHash = ownerCodeHash
        val ok = CredentialHash1980.verify(currentHash, entered)
        if (ok && CredentialHash1980.needsUpgrade(currentHash)) ownerCodeHash = CredentialHash1980.hash(entered)
''','owner verify')
s = replace_once(s,
'        ownerCodeHash = PairingProtocol.pinHash(newCode)\n',
'        ownerCodeHash = CredentialHash1980.hash(newCode)\n','owner set')

old_employees = '''    fun employees(): List<PairedEmployee> {
        val array = JSONArray(prefs.getString("employees", "[]") ?: "[]")
'''
new_employees = '''    private fun employeesJson1980(): String {
        val secured = secureVault.get("employees_json_v1980")
        if (secured.isNotBlank() && runCatching { JSONArray(secured) }.isSuccess) return secured
        val legacy = prefs.getString("employees", "[]") ?: "[]"
        if (runCatching { JSONArray(legacy) }.isSuccess) {
            secureVault.put("employees_json_v1980", legacy)
            if (secureVault.get("employees_json_v1980") == legacy) prefs.edit().remove("employees").apply()
            return legacy
        }
        return "[]"
    }

    fun employees(): List<PairedEmployee> {
        val array = JSONArray(employeesJson1980())
'''
s = replace_once(s, old_employees, new_employees, 'employee json secure read')
s = replace_once(s,
'        prefs.edit().putString("employees", array.toString()).apply()\n',
'''        val payload = array.toString()
        secureVault.put("employees_json_v1980", payload)
        if (secureVault.get("employees_json_v1980") == payload) prefs.edit().remove("employees").apply()
''','employee json secure save')
local.write_text(s, encoding='utf-8')

# 3) Central agent is the only visible agent entry; legacy local agent code remains unreachable for data migration only.
sysf = STORE / 'SystemSettingsActivity.kt'; must(sysf)
s = sysf.read_text(encoding='utf-8')
s = replace_once(s,
'        agentGate.addView(UiKit.button(this, p, "دخول الوكيل", false).apply { setOnClickListener { agentLogin() } })\n',
'''        agentGate.addView(UiKit.button(this, p, "دخول الوكيل المركزي", false).apply {
            setOnClickListener { startActivity(Intent(this@SystemSettingsActivity, SystemManagement1971Activity::class.java).putExtra("mode", "AGENT")) }
        })
''','owner-only central agent entry')
s = replace_once(s,
'''        agent.addView(UiKit.button(this, p, "دخول بوابة الوكيل", false).apply { setOnClickListener { agentLogin() } })
        agent.addView(UiKit.button(this, p, "بوابة الوكيل المركزية 1.9.71", false).apply {
            setOnClickListener {
                startActivity(Intent(this@SystemSettingsActivity, SystemManagement1971Activity::class.java).putExtra("mode", "AGENT"))
            }
        })
''',
'''        agent.addView(UiKit.button(this, p, "دخول الوكيل المركزي", false).apply {
            setOnClickListener { startActivity(Intent(this@SystemSettingsActivity, SystemManagement1971Activity::class.java).putExtra("mode", "AGENT")) }
        })
''','gateway central agent only')
s = s.replace('network.addView(UiKit.button(this, p, "إدارة الوكلاء").apply { setOnClickListener { showAgents() } })',
'''network.addView(UiKit.button(this, p, "إدارة الوكلاء المركزية").apply {
            setOnClickListener { startActivity(Intent(this@SystemSettingsActivity, SystemManagement1971Activity::class.java).putExtra("mode", "OWNER")) }
        })''')
s = s.replace('network.addView(UiKit.button(this, p, "إدارة الوكلاء", false).apply { setOnClickListener { showAgents() } })',
'''network.addView(UiKit.button(this, p, "إدارة الوكلاء المركزية", false).apply {
                    setOnClickListener { startActivity(Intent(this@SystemSettingsActivity, SystemManagement1971Activity::class.java).putExtra("mode", "OWNER")) }
                })''')
s = replace_once(s,
'''        when (navigationScreen1977) {
            "CENTRAL" -> showOwnerDashboard()
            "OWNER", "AGENT" -> showGateway()
            else -> super.onBackPressed()
        }
''',
'''        val ownerOnly = intent?.getBooleanExtra("OWNER_ONLY_1978", false) == true
        when (navigationScreen1977) {
            "CENTRAL" -> showOwnerDashboard()
            "OWNER", "AGENT" -> if (ownerOnly) showOwnerOnlyEntry1978() else showGateway()
            "OWNER_GATE" -> finish()
            else -> super.onBackPressed()
        }
''','system settings back chain')
sysf.write_text(s, encoding='utf-8')

# 4) Guard externally-shared pairing text with explicit confirmation; pairing functions themselves are untouched.
emain = EMP / 'MainActivity.kt'; must(emain)
s = emain.read_text(encoding='utf-8')
s = replace_once(s,
'        if(shared.isNotBlank()) handlePairingInput(shared.trim())\n',
'        if(shared.isNotBlank()) handleExternalPairing1980(shared.trim())\n','external share entry')
anchor = '    private fun buildUi() {\n'
helper = r'''    private fun handleExternalPairing1980(raw: String) {
        val value = raw.trim()
        val envelope = PairingProtocol.decodeQrPairingEnvelope(value)
        val provision = envelope?.let { PairingProtocol.decodeEmployeeProvision(it.provisionText) }
            ?: PairingProtocol.decodeEmployeeProvision(value)
        val shortCode = value.removePrefix("APPAIR:").replace(Regex("[^A-Za-z0-9]"), "").uppercase()
        if (provision == null && !(shortCode.length == 8 && (value.startsWith("APPAIR:") || value.matches(Regex("[A-Za-z0-9]{8}"))))) {
            AlertDialog.Builder(this).setTitle("محتوى غير مدعوم").setMessage("النص المُرسل إلى ATTEND PRO ليس رمز ربط صالحًا.").setPositiveButton("حسنًا", null).show()
            return
        }
        val replacing = provision != null && identity.isConfigured &&
            (identity.trustedStoreId != provision.storeId || identity.employeeId != provision.employeeId)
        val same = provision != null && identity.isConfigured &&
            identity.trustedStoreId == provision.storeId && identity.employeeId == provision.employeeId
        val details = if (provision != null) buildString {
            append("المحل: ${provision.storeName}\n")
            append("الموظف: ${provision.displayName} (${provision.employeeId})\n")
            append("الفرع: ${provision.branchId}")
            if (replacing) append("\n\nالارتباط الحالي: ${identity.trustedStoreName.ifBlank { identity.trustedStoreId }} / ${identity.displayName.ifBlank { identity.employeeId }}")
        } else "تم استلام رمز اقتران قصير من تطبيق آخر. تأكد أنه صادر من جهاز المحل أمامك."
        val title = when {
            replacing -> "استبدال ارتباط الهاتف؟"
            same -> "تحديث بيانات الارتباط؟"
            else -> "تأكيد ربط الهاتف"
        }
        val warning = if (replacing) "\n\nسيتم استبدال المحل/الموظف المرتبط حاليًا بهذا الهاتف. لا توافق إلا إذا كنت تنفذ إعادة ربط مقصودة." else "\n\nلا توافق إلا إذا كان الرمز صادرًا من جهاز المحل الذي تريد ربط الهاتف به."
        AlertDialog.Builder(this).setTitle(title).setMessage(details + warning)
            .setPositiveButton(if (replacing) "استبدال الارتباط" else "متابعة الربط") { _, _ -> handlePairingInput(value) }
            .setNegativeButton("إلغاء", null).show()
    }

'''
if helper.strip() not in s:
    s = s.replace(anchor, helper + anchor, 1)
emain.write_text(s, encoding='utf-8')

# 5) Store notification permission + direct reply to employee messages.
msg = STORE / 'StoreMessages1975Activity.kt'; must(msg)
s = msg.read_text(encoding='utf-8')
if 'import android.Manifest' not in s:
    s = s.replace('package com.attendpro.store\n\n', 'package com.attendpro.store\n\nimport android.Manifest\n', 1)
if 'import android.content.pm.PackageManager' not in s:
    s = s.replace('import android.app.AlertDialog\n', 'import android.app.AlertDialog\nimport android.content.pm.PackageManager\n', 1)
if 'import android.os.Build' not in s:
    s = s.replace('import android.os.Bundle\n', 'import android.os.Build\nimport android.os.Bundle\n', 1)
s = replace_once(s,
'''        identity = DeviceIdentity(this)
        showInbox()
        StoreMessagePoll1975.schedule(this)
''',
'''        identity = DeviceIdentity(this)
        showInbox()
        ensureNotificationPermission1980()
        StoreMessagePoll1975.schedule(this)
''','store messages notification request')
anchor = '    private fun showInbox() {\n'
notif = r'''    private fun ensureNotificationPermission1980() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1980)
        }
    }

'''
if notif.strip() not in s:
    s = s.replace(anchor, notif + anchor, 1)
old = '''            card.addView(UiKit.button(this, p, if (unread) "تعليم كمقروء" else "مقروء ✓", false).apply {
                isEnabled = unread
                setOnClickListener { markRead(message.messageId) }
            })
'''
new = '''            if (message.senderType == "EMPLOYEE" && message.employeeId.isNotBlank()) {
                card.addView(UiKit.button(this, p, "↩ رد على الموظف", false).apply {
                    setOnClickListener {
                        val employeeName = repo.employees().firstOrNull { it.employeeId.equals(message.employeeId, true) }?.displayName ?: message.employeeId
                        composeFor(message.employeeId, employeeName)
                    }
                })
            }
            card.addView(UiKit.button(this, p, if (unread) "تعليم كمقروء" else "مقروء ✓", false).apply {
                isEnabled = unread
                setOnClickListener { markRead(message.messageId) }
            })
'''
s = replace_once(s, old, new, 'store direct reply')
msg.write_text(s, encoding='utf-8')

# 6) Correct overnight shift calculations in dashboard and reports using ShiftWindow.
main = STORE / 'MainActivity.kt'; must(main)
s = main.read_text(encoding='utf-8')
if 'import com.attendpro.core.ShiftWindow' not in s:
    s = s.replace('import com.attendpro.core.SecretCodec\n', 'import com.attendpro.core.SecretCodec\nimport com.attendpro.core.ShiftWindow\n', 1)
old = '''    private fun shiftCutoffMillis(employee: PairedEmployee, baseTime: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = baseTime
        set(Calendar.HOUR_OF_DAY, if (employee.useCustomShift) employee.shiftStartHour else repo.shiftHour)
        set(Calendar.MINUTE, if (employee.useCustomShift) employee.shiftStartMinute else repo.shiftMinute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.MINUTE, repo.graceMinutes)
    }.timeInMillis
    private fun shiftEndMillis(employee: PairedEmployee, baseTime: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = baseTime
        set(Calendar.HOUR_OF_DAY, if (employee.useCustomShift) employee.shiftEndHour else repo.shiftEndHour)
        set(Calendar.MINUTE, if (employee.useCustomShift) employee.shiftEndMinute else repo.shiftEndMinute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
'''
new = '''    private fun shiftWindow1980(employee: PairedEmployee, baseTime: Long = System.currentTimeMillis()): ShiftWindow.Window = ShiftWindow.resolve(
        baseTime,
        if (employee.useCustomShift) employee.shiftStartHour else repo.shiftHour,
        if (employee.useCustomShift) employee.shiftStartMinute else repo.shiftMinute,
        if (employee.useCustomShift) employee.shiftEndHour else repo.shiftEndHour,
        if (employee.useCustomShift) employee.shiftEndMinute else repo.shiftEndMinute
    )
    private fun shiftCutoffMillis(employee: PairedEmployee, baseTime: Long = System.currentTimeMillis()): Long =
        shiftWindow1980(employee, baseTime).start + repo.graceMinutes * 60_000L
    private fun shiftEndMillis(employee: PairedEmployee, baseTime: Long = System.currentTimeMillis()): Long =
        shiftWindow1980(employee, baseTime).end
'''
s = replace_once(s, old, new, 'main overnight shift')
main.write_text(s, encoding='utf-8')

reports = STORE / 'ReportsActivity.kt'; must(reports)
s = reports.read_text(encoding='utf-8')
if 'import com.attendpro.core.ShiftWindow' not in s:
    s = s.replace('import com.attendpro.core.ReportProtocol\n', 'import com.attendpro.core.ReportProtocol\nimport com.attendpro.core.ShiftWindow\n', 1)
old = '''    private fun cutoffFor(time: Long, employee: com.attendpro.core.PairedEmployee): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, if (employee.useCustomShift) employee.shiftStartHour else repo.shiftHour)
        set(Calendar.MINUTE, if (employee.useCustomShift) employee.shiftStartMinute else repo.shiftMinute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.MINUTE, repo.graceMinutes)
    }.timeInMillis

    private fun shiftEndFor(time: Long, employee: com.attendpro.core.PairedEmployee): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, if (employee.useCustomShift) employee.shiftEndHour else repo.shiftEndHour)
        set(Calendar.MINUTE, if (employee.useCustomShift) employee.shiftEndMinute else repo.shiftEndMinute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
'''
new = '''    private fun shiftWindow1980(time: Long, employee: com.attendpro.core.PairedEmployee): ShiftWindow.Window = ShiftWindow.resolve(
        time,
        if (employee.useCustomShift) employee.shiftStartHour else repo.shiftHour,
        if (employee.useCustomShift) employee.shiftStartMinute else repo.shiftMinute,
        if (employee.useCustomShift) employee.shiftEndHour else repo.shiftEndHour,
        if (employee.useCustomShift) employee.shiftEndMinute else repo.shiftEndMinute
    )

    private fun cutoffFor(time: Long, employee: com.attendpro.core.PairedEmployee): Long =
        shiftWindow1980(time, employee).start + repo.graceMinutes * 60_000L

    private fun shiftEndFor(time: Long, employee: com.attendpro.core.PairedEmployee): Long =
        shiftWindow1980(time, employee).end
'''
s = replace_once(s, old, new, 'reports overnight shift')
reports.write_text(s, encoding='utf-8')

# 7) Remove unused direct-call permission. ACTION_DIAL requires no CALL_PHONE permission.
manifest = ROOT / 'store-app/src/main/AndroidManifest.xml'; must(manifest)
s = manifest.read_text(encoding='utf-8')
s = s.replace('    <uses-permission android:name="android.permission.CALL_PHONE" />\n', '')
manifest.write_text(s, encoding='utf-8')

print('ATTEND-PRO 1.9.80 security hardening patch applied')
