from pathlib import Path
import shutil

ROOT = Path('buildsrc')
STORE = ROOT / 'store-app/src/main/java/com/attendpro/store'
CORE = ROOT / 'core/src/main/java/com/attendpro/core'
MANIFEST = ROOT / 'store-app/src/main/AndroidManifest.xml'
SOURCE = Path('.source_patches/1.9.71/SystemManagement1971Activity.kt')
TARGET = STORE / 'SystemManagement1971Activity.kt'
SYSTEM = STORE / 'SystemSettingsActivity.kt'
MAIN = STORE / 'MainActivity.kt'
CLIENT = CORE / 'CentralServerClient.kt'

for required in (SOURCE, SYSTEM, MAIN, CLIENT, MANIFEST):
    if not required.exists():
        raise SystemExit(f'missing required file: {required}')

shutil.copyfile(SOURCE, TARGET)

text = SYSTEM.read_text(encoding='utf-8')
agent_anchor = 'agent.addView(UiKit.button(this, p, "دخول بوابة الوكيل", false).apply { setOnClickListener { agentLogin() } })'
agent_button = '''agent.addView(UiKit.button(this, p, "بوابة الوكيل المركزية 1.9.71", false).apply {
            setOnClickListener {
                startActivity(Intent(this@SystemSettingsActivity, SystemManagement1971Activity::class.java).putExtra("mode", "AGENT"))
            }
        })'''
if 'بوابة الوكيل المركزية 1.9.71' not in text:
    if agent_anchor not in text:
        raise SystemExit('agent gateway anchor not found; refusing unsafe patch')
    text = text.replace(agent_anchor, agent_anchor + '\n        ' + agent_button, 1)

owner_anchor = 'network.addView(UiKit.button(this, p, "إدارة الوكلاء").apply { setOnClickListener { showAgents() } })'
owner_button = '''network.addView(UiKit.button(this, p, "إدارة النظام المركزية 1.9.71").apply {
            setOnClickListener {
                startActivity(Intent(this@SystemSettingsActivity, SystemManagement1971Activity::class.java).putExtra("mode", "OWNER"))
            }
        })'''
if 'إدارة النظام المركزية 1.9.71' not in text:
    if owner_anchor not in text:
        raise SystemExit('owner dashboard anchor not found; refusing unsafe patch')
    text = text.replace(owner_anchor, owner_anchor + '\n        ' + owner_button, 1)
SYSTEM.write_text(text, encoding='utf-8')

client = CLIENT.read_text(encoding='utf-8')
result_anchor = '    data class ActivationRequestResult(val requestId: String, val pollSecret: String, val status: String)\n'
result_model = '''    data class SelfRegistrationResult(
        val status: String,
        val trialDays: Int,
        val expiresAt: Long,
        val ownerApprovalRequired: Boolean,
        val ownerNotified: Boolean
    )
'''
if 'data class SelfRegistrationResult(' not in client:
    if result_anchor not in client:
        raise SystemExit('activation result model anchor not found')
    client = client.replace(result_anchor, result_anchor + result_model, 1)

function_anchor = '''    fun recoverActivation(serverUrl: String, identity: DeviceIdentity): Result<ActivationRecovery> = runCatching {\n'''
self_register_function = '''    fun selfRegisterActivation(
        serverUrl: String,
        requestId: String,
        pollSecret: String,
        storeId: String,
        identity: DeviceIdentity
    ): Result<SelfRegistrationResult> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/activation/self-register", "POST", JSONObject().apply {
            put("requestId", requestId)
            put("pollSecret", pollSecret)
        }, deviceIdentity = identity, storeId = storeId)
        SelfRegistrationResult(
            status = o.optString("status", "APPROVED"),
            trialDays = o.optInt("trialDays", 3).coerceIn(1, 30),
            expiresAt = o.optLong("expiresAt", 0L),
            ownerApprovalRequired = o.optBoolean("ownerApprovalRequired", false),
            ownerNotified = o.optBoolean("ownerNotified", true)
        )
    }

'''
if 'fun selfRegisterActivation(' not in client:
    if function_anchor not in client:
        raise SystemExit('self registration insertion anchor not found')
    client = client.replace(function_anchor, self_register_function + function_anchor, 1)
CLIENT.write_text(client, encoding='utf-8')

main = MAIN.read_text(encoding='utf-8')
button_anchor = '            activation.addView(UiKit.button(this, p, "إرسال طلب التفعيل لإدارة النظام").apply { setOnClickListener { requestCentralActivation() } })'
direct_button = '''            activation.addView(UiKit.button(this, p, "تسجيل محل جديد مباشرة — تجربة 3 أيام").apply {
                setOnClickListener { requestCentralActivation(selfRegister = true) }
            })'''
if 'تسجيل محل جديد مباشرة — تجربة 3 أيام' not in main:
    if button_anchor not in main:
        raise SystemExit('activation lock button anchor not found')
    main = main.replace(button_anchor, direct_button + '\n' + button_anchor, 1)

old_request_sig = '    private fun requestCentralActivation() {'
new_request_sig = '    private fun requestCentralActivation(selfRegister: Boolean = false) {'
if new_request_sig not in main:
    if old_request_sig not in main:
        raise SystemExit('requestCentralActivation signature anchor not found')
    main = main.replace(old_request_sig, new_request_sig, 1)
main = main.replace('repo.serverUrl = url; d.dismiss(); showActivationRequestForm()', 'repo.serverUrl = url; d.dismiss(); showActivationRequestForm(selfRegister)', 1)
main = main.replace('        showActivationRequestForm()\n    }\n\n    private fun showActivationRequestForm() {', '        showActivationRequestForm(selfRegister)\n    }\n\n    private fun showActivationRequestForm(selfRegister: Boolean = false) {', 1)
main = main.replace('box.addView(UiKit.subtitle(this, p, "أدخل البيانات الأساسية للطلب. تجربة 3 أيام لا تبدأ إلا بعد موافقة إدارة النظام."))', 'box.addView(UiKit.subtitle(this, p, if (selfRegister) "أنشئ حساب المحل مباشرة وابدأ تجربة 3 أيام دون انتظار موافقة. سيصل إشعار لإدارة النظام." else "أدخل البيانات الأساسية للطلب. يبدأ الاشتراك بعد اعتماد إدارة النظام."))', 1)
main = main.replace('val dialog = AlertDialog.Builder(this).setTitle("طلب تفعيل ATTEND PRO").setView(scroll)\n            .setPositiveButton("إرسال الطلب", null).setNegativeButton("إلغاء", null).create()', 'val dialog = AlertDialog.Builder(this).setTitle(if (selfRegister) "تسجيل محل جديد" else "طلب تفعيل ATTEND PRO").setView(scroll)\n            .setPositiveButton(if (selfRegister) "تسجيل وبدء التجربة" else "إرسال الطلب", null).setNegativeButton("إلغاء", null).create()', 1)
old_submit = '''                submitCentralActivationRequest(CentralServerClient.ActivationApplicant(ownerName, storeName, type,
                    repo.storePhone, repo.storeAddress, repo.storeCommercialId, repo.storeNotes))'''
new_submit = '''                val applicant = CentralServerClient.ActivationApplicant(ownerName, storeName, if (selfRegister) "TRIAL_3_DAYS" else type,
                    repo.storePhone, repo.storeAddress, repo.storeCommercialId, repo.storeNotes)
                if (selfRegister) submitSelfRegistration(applicant) else submitCentralActivationRequest(applicant)'''
if 'if (selfRegister) submitSelfRegistration(applicant)' not in main:
    if old_submit not in main:
        raise SystemExit('activation form submit anchor not found')
    main = main.replace(old_submit, new_submit, 1)

submit_anchor = '    private fun submitCentralActivationRequest(applicant: CentralServerClient.ActivationApplicant) {\n'
self_submit_function = '''    private fun submitSelfRegistration(applicant: CentralServerClient.ActivationApplicant) {
        status.text = "جاري تسجيل المحل وبدء التجربة..."
        Thread {
            val identity = DeviceIdentity(this)
            val request = CentralServerClient.requestActivation(repo.serverUrl, repo.storeId, repo.branchId, applicant, identity)
            if (request.isFailure) {
                runOnUiThread {
                    status.text = "تعذر إنشاء طلب التسجيل"
                    info("تعذر التسجيل", request.exceptionOrNull()?.message ?: "خطأ في الاتصال بالخادم")
                }
                return@Thread
            }
            val pending = request.getOrThrow()
            repo.centralActivationRequestId = pending.requestId
            repo.centralActivationPollSecret = pending.pollSecret
            val registration = CentralServerClient.selfRegisterActivation(
                repo.serverUrl, pending.requestId, pending.pollSecret, repo.storeId, identity
            )
            if (registration.isFailure) {
                runOnUiThread {
                    status.text = "لم يكتمل التسجيل المباشر"
                    info("تعذر التسجيل المباشر", (registration.exceptionOrNull()?.message ?: "خطأ") + "\n\nتم الاحتفاظ بطلب التفعيل ويمكن لإدارة النظام اعتماده يدويًا.")
                    buildActivationLockUi()
                }
                return@Thread
            }
            val activation = CentralServerClient.activationStatus(
                repo.serverUrl, pending.requestId, pending.pollSecret, repo.storeId, identity
            )
            runOnUiThread {
                if (activation.isFailure) {
                    status.text = "تم التسجيل • يلزم فحص حالة التفعيل"
                    info("تم التسجيل", "تم إنشاء حساب المحل وبدء التجربة على الخادم. اضغط «فحص موافقة إدارة النظام» لاستلام اعتماد التشغيل على هذا الجهاز.")
                    buildActivationLockUi()
                    return@runOnUiThread
                }
                val x = activation.getOrThrow()
                if (x.accessToken.isBlank() || x.licenseId.isBlank() || x.leaseUntil <= System.currentTimeMillis() || x.serverTime <= 0L) {
                    status.text = "تم التسجيل • استجابة التفعيل غير مكتملة"
                    info("تم التسجيل", "تم تسجيل المحل على الخادم، لكن اعتماد التشغيل لم يصل كاملًا. استخدم فحص حالة التفعيل.")
                    buildActivationLockUi()
                    return@runOnUiThread
                }
                repo.saveCentralActivation(x.licenseId, x.accessToken, x.expiresAt, x.maxEmployees, x.leaseUntil, x.serverTime)
                repo.centralActivationRequestId = ""
                repo.centralActivationPollSecret = ""
                status.text = "✓ تم تسجيل المحل وبدء التجربة"
                buildElegantUi()
                refreshDashboard()
                val trial = registration.getOrThrow()
                info("تم التسجيل وبدء التجربة ✓", "تم إنشاء حساب المحل مباشرة وبدأت تجربة ${trial.trialDays} أيام. تم إشعار إدارة النظام تلقائيًا دون الحاجة إلى انتظار الموافقة.")
            }
        }.apply { isDaemon = true }.start()
    }

'''
if 'private fun submitSelfRegistration(' not in main:
    if submit_anchor not in main:
        raise SystemExit('self registration submit insertion anchor not found')
    main = main.replace(submit_anchor, self_submit_function + submit_anchor, 1)
MAIN.write_text(main, encoding='utf-8')

manifest = MANIFEST.read_text(encoding='utf-8')
manifest_anchor = '<activity android:name=".SystemSettingsActivity" android:screenOrientation="portrait" android:exported="false" />'
manifest_entry = '<activity android:name=".SystemManagement1971Activity" android:screenOrientation="portrait" android:exported="false" />'
if manifest_entry not in manifest:
    if manifest_anchor not in manifest:
        raise SystemExit('manifest activity anchor not found; refusing unsafe patch')
    manifest = manifest.replace(manifest_anchor, manifest_anchor + '\n        ' + manifest_entry, 1)
MANIFEST.write_text(manifest, encoding='utf-8')

if not TARGET.exists() or 'class SystemManagement1971Activity' not in TARGET.read_text(encoding='utf-8'):
    raise SystemExit('management activity copy failed')

print('ATTEND-PRO 1.9.71 Android management + self-registration patch applied')
print(f'created: {TARGET}')
print(f'patched: {SYSTEM}')
print(f'patched: {MAIN}')
print(f'patched: {CLIENT}')
print(f'patched: {MANIFEST}')
