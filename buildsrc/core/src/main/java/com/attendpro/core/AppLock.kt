package com.attendpro.core

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.ConcurrentHashMap

enum class AppPinResult { SUCCESS, INCORRECT, TEMPORARILY_LOCKED }

data class AppLockState(
    val enabled: Boolean,
    val biometricEnabled: Boolean,
    val timeoutSeconds: Int,
    val remainingAttempts: Int,
    val lockedUntil: Long
)

/** Device-local UI protection. Its PIN and session are deliberately excluded from backups. */
class AppLockManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val sessionKey = appContext.packageName

    fun state(): AppLockState {
        val enabled = prefs.getBoolean(KEY_ENABLED, false) && pinHash().isNotBlank()
        val failed = prefs.getInt(KEY_FAILED, 0).coerceIn(0, MAX_ATTEMPTS)
        return AppLockState(
            enabled,
            enabled && prefs.getBoolean(KEY_BIOMETRIC, false),
            normalizedTimeout(prefs.getInt(KEY_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS)),
            (MAX_ATTEMPTS - failed).coerceAtLeast(0),
            prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        )
    }

    fun setPin(pin: String, biometricEnabled: Boolean = false, timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS) {
        validatePin(pin)
        prefs.edit()
            .putString(KEY_PIN_HASH, CredentialHash1980.hash(pin))
            .putBoolean(KEY_ENABLED, true)
            .putBoolean(KEY_BIOMETRIC, biometricEnabled)
            .putInt(KEY_TIMEOUT_SECONDS, normalizedTimeout(timeoutSeconds))
            .putInt(KEY_FAILED, 0)
            .putLong(KEY_LOCKED_UNTIL, 0L)
            .commit()
        markUnlocked()
    }

    fun verifyPin(pin: String, now: Long = System.currentTimeMillis()): AppPinResult {
        val current = state()
        if (!current.enabled) return AppPinResult.SUCCESS
        if (current.lockedUntil > now) return AppPinResult.TEMPORARILY_LOCKED
        val stored = pinHash()
        if (CredentialHash1980.verify(stored, pin)) {
            if (CredentialHash1980.needsUpgrade(stored)) prefs.edit().putString(KEY_PIN_HASH, CredentialHash1980.hash(pin)).commit()
            prefs.edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCKED_UNTIL, 0L).commit()
            markUnlocked(now)
            return AppPinResult.SUCCESS
        }
        val attempts = prefs.getInt(KEY_FAILED, 0) + 1
        if (attempts >= MAX_ATTEMPTS) {
            prefs.edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCKED_UNTIL, now + LOCKOUT_MILLIS).commit()
            return AppPinResult.TEMPORARILY_LOCKED
        }
        prefs.edit().putInt(KEY_FAILED, attempts).commit()
        return AppPinResult.INCORRECT
    }

    fun unlockWithBiometric(now: Long = System.currentTimeMillis()): Boolean {
        if (!state().enabled || !state().biometricEnabled) return false
        prefs.edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCKED_UNTIL, 0L).commit()
        markUnlocked(now)
        return true
    }

    fun setBiometricEnabled(enabled: Boolean) {
        require(state().enabled) { "App lock is not enabled" }
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).commit()
    }

    fun setTimeoutSeconds(seconds: Int) {
        require(state().enabled) { "App lock is not enabled" }
        prefs.edit().putInt(KEY_TIMEOUT_SECONDS, normalizedTimeout(seconds)).commit()
    }

    fun disable() {
        prefs.edit().clear().commit()
        unlockedSessions.remove(sessionKey)
    }

    fun lockNow(now: Long = System.currentTimeMillis()) {
        unlockedSessions.remove(sessionKey)
        prefs.edit().putLong(KEY_LAST_BACKGROUND_AT, now).commit()
    }

    fun markUnlocked(now: Long = System.currentTimeMillis()) {
        unlockedSessions[sessionKey] = now
    }

    fun markBackground(now: Long = System.currentTimeMillis()) {
        if (state().enabled) prefs.edit().putLong(KEY_LAST_BACKGROUND_AT, now).apply()
    }

    fun requiresUnlock(now: Long = System.currentTimeMillis()): Boolean {
        val current = state()
        if (!current.enabled) return false
        val unlockedAt = unlockedSessions[sessionKey] ?: return true
        val backgroundAt = prefs.getLong(KEY_LAST_BACKGROUND_AT, 0L)
        if (backgroundAt == 0L || backgroundAt < unlockedAt) return false
        return now - backgroundAt >= current.timeoutSeconds * 1_000L
    }

    fun lockoutRemainingSeconds(now: Long = System.currentTimeMillis()): Long =
        ((state().lockedUntil - now + 999L) / 1_000L).coerceAtLeast(0L)

    private fun pinHash(): String = prefs.getString(KEY_PIN_HASH, "").orEmpty()

    private fun validatePin(pin: String) {
        require(pin.length in 6..12 && pin.all(Char::isDigit)) { "App PIN must contain 6 to 12 digits" }
    }

    private fun normalizedTimeout(value: Int): Int = TIMEOUT_OPTIONS.firstOrNull { it == value } ?: DEFAULT_TIMEOUT_SECONDS

    companion object {
        private const val PREFS = "attend_pro_app_lock_v2"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PIN_HASH = "pinHash"
        private const val KEY_BIOMETRIC = "biometricEnabled"
        private const val KEY_TIMEOUT_SECONDS = "timeoutSeconds"
        private const val KEY_FAILED = "failedAttempts"
        private const val KEY_LOCKED_UNTIL = "lockedUntil"
        private const val KEY_LAST_BACKGROUND_AT = "lastBackgroundAt"
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_MILLIS = 60_000L
        const val DEFAULT_TIMEOUT_SECONDS = 30
        val TIMEOUT_OPTIONS = intArrayOf(0, 30, 60, 300)
        private val unlockedSessions = ConcurrentHashMap<String, Long>()
    }
}

/** Records real app background transitions without treating navigation between activities as leaving the app. */
class AttendProApplication : Application(), Application.ActivityLifecycleCallbacks {
    private val handler = Handler(Looper.getMainLooper())
    private var startedActivities = 0
    private var pendingBackground: Runnable? = null
    private var gateLaunchInProgress = false

    override fun onCreate() {
        super.onCreate()
        AppLanguage.applyToResources(this)
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityPreCreated(activity: Activity, state: Bundle?) {
        AppLanguage.applyToResources(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities += 1
        pendingBackground?.let(handler::removeCallbacks)
        pendingBackground = null
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (activity.isChangingConfigurations || startedActivities != 0) return
        val task = Runnable {
            if (startedActivities == 0) AppLockManager(this).markBackground()
        }
        pendingBackground = task
        handler.postDelayed(task, 500L)
    }

    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) {
        if (activity is AppLockGateActivity) {
            gateLaunchInProgress = true
            return
        }
        val manager = AppLockManager(this)
        if (!manager.requiresUnlock()) {
            gateLaunchInProgress = false
            return
        }
        if (gateLaunchInProgress) return
        val gate = packageManager.getLaunchIntentForPackage(packageName) ?: return
        gate.putExtra(AppLockGateActivity.EXTRA_GATE_ONLY, true)
        gate.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        gateLaunchInProgress = true
        activity.startActivity(gate)
    }
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        if (activity is AppLockGateActivity && !AppLockManager(this).requiresUnlock()) gateLaunchInProgress = false
    }
}

/** Shared secure gate; each app provides a strongly typed intent for its own MainActivity. */
abstract class AppLockGateActivity : Activity() {
    private lateinit var manager: AppLockManager
    private var biometricCancellation: CancellationSignal? = null
    private var biometricAttempted = false
    private var completed = false
    private lateinit var statusView: TextView

    protected abstract fun mainActivityIntent(): Intent
    protected open fun appLabelForLock(): String = applicationInfo.loadLabel(packageManager).toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        manager = AppLockManager(this)
        if (!manager.requiresUnlock()) {
            completeGate()
            return
        }
        showLockedUi()
    }

    override fun onResume() {
        super.onResume()
        if (::manager.isInitialized && !completed && manager.requiresUnlock() && manager.state().biometricEnabled && !biometricAttempted) {
            biometricAttempted = true
            requestBiometricUnlock()
        }
    }

    override fun onDestroy() {
        biometricCancellation?.cancel()
        super.onDestroy()
    }

    @Deprecated("Back closes protected content")
    override fun onBackPressed() { finishAffinity() }

    private fun showLockedUi() {
        val palette = UiKit.palette(this)
        window.statusBarColor = palette.bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@AppLockGateActivity, 20), UiKit.dp(this@AppLockGateActivity, 30), UiKit.dp(this@AppLockGateActivity, 20), UiKit.dp(this@AppLockGateActivity, 30))
            setBackgroundColor(palette.bg)
        }
        val card = UiKit.card(this, palette, 18)
        card.addView(UiKit.sectionLabel(this, palette, "حماية ATTEND PRO"))
        card.addView(UiKit.title(this, palette, "التطبيق مقفل", 25f).apply { gravity = Gravity.CENTER })
        card.addView(UiKit.subtitle(this, palette, "أدخل رمز قفل ${appLabelForLock()} للوصول إلى البيانات.").apply { gravity = Gravity.CENTER })
        val pin = UiKit.field(this, palette, "رمز القفل", true).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        card.addView(pin)
        statusView = UiKit.subtitle(this, palette, lockStatusText()).apply { gravity = Gravity.CENTER }
        card.addView(statusView)
        card.addView(UiKit.button(this, palette, "فتح التطبيق").apply {
            setOnClickListener {
                when (manager.verifyPin(pin.text.toString())) {
                    AppPinResult.SUCCESS -> completeGate()
                    AppPinResult.INCORRECT -> {
                        pin.text?.clear()
                        pin.error = "الرمز غير صحيح"
                        statusView.text = lockStatusText()
                    }
                    AppPinResult.TEMPORARILY_LOCKED -> {
                        pin.text?.clear()
                        statusView.text = lockStatusText()
                    }
                }
            }
        })
        if (manager.state().biometricEnabled && biometricAvailable(this)) {
            card.addView(UiKit.button(this, palette, "فتح بالبصمة", false).apply {
                setOnClickListener { biometricAttempted = true; requestBiometricUnlock() }
            })
        }
        root.addView(card)
        setContentView(ScrollView(this).apply { setBackgroundColor(palette.bg); addView(root) })
    }

    private fun lockStatusText(): String {
        val seconds = manager.lockoutRemainingSeconds()
        return if (seconds > 0) "تم إيقاف المحاولات مؤقتًا. حاول بعد $seconds ثانية."
        else "المحاولات المتبقية: ${manager.state().remainingAttempts}"
    }

    private fun requestBiometricUnlock() {
        if (!biometricAvailable(this)) {
            if (::statusView.isInitialized) statusView.text = "البصمة غير جاهزة على هذا الهاتف؛ استخدم رمز القفل."
            return
        }
        biometricCancellation?.cancel()
        biometricCancellation = CancellationSignal()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val prompt = BiometricPrompt.Builder(this)
                .setTitle("فتح ${appLabelForLock()}")
                .setSubtitle("تحقق ببصمة الهاتف")
                .setNegativeButton("استخدام الرمز", mainExecutor) { _, _ -> Unit }
                .build()
            prompt.authenticate(biometricCancellation!!, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    if (manager.unlockWithBiometric()) completeGate()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    if (::statusView.isInitialized) statusView.text = "استخدم رمز القفل أو أعد محاولة البصمة."
                }
            })
        } else {
            @Suppress("DEPRECATION")
            val fingerprint = getSystemService(FingerprintManager::class.java)
            @Suppress("DEPRECATION")
            fingerprint.authenticate(null, biometricCancellation, 0, object : FingerprintManager.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult?) {
                    if (manager.unlockWithBiometric()) completeGate()
                }
                override fun onAuthenticationFailed() {
                    if (::statusView.isInitialized) statusView.text = "لم تتطابق البصمة؛ حاول مجددًا أو استخدم الرمز."
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    if (::statusView.isInitialized) statusView.text = "استخدم رمز القفل."
                }
            }, null)
        }
    }

    private fun completeGate() {
        if (completed) return
        completed = true
        biometricCancellation?.cancel()
        if (intent.getBooleanExtra(EXTRA_GATE_ONLY, false)) finish()
        else {
            startActivity(mainActivityIntent().addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
    }

    companion object {
        const val EXTRA_GATE_ONLY = "com.attendpro.extra.APP_LOCK_GATE_ONLY"

        fun biometricAvailable(context: Context): Boolean = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val manager = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
                manager?.canAuthenticate() == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                val manager = context.getSystemService(FingerprintManager::class.java)
                @Suppress("DEPRECATION")
                manager?.isHardwareDetected == true && manager.hasEnrolledFingerprints()
            }
        }.getOrDefault(false)
    }
}

object AppLockSettingsDialog {
    fun show(activity: Activity, onLockNow: () -> Unit = {}, onChanged: () -> Unit = {}) {
        val manager = AppLockManager(activity)
        if (!manager.state().enabled) {
            showSetup(activity, manager, onChanged)
            return
        }
        val state = manager.state()
        val timeout = timeoutLabel(state.timeoutSeconds)
        val items = arrayOf(
            "تغيير رمز القفل",
            "البصمة: ${if (state.biometricEnabled) "مفعّلة" else "متوقفة"}",
            "القفل بعد مغادرة التطبيق: $timeout",
            "قفل التطبيق الآن",
            "إلغاء قفل التطبيق"
        )
        AlertDialog.Builder(activity).setTitle("قفل التطبيق").setItems(items) { _, which ->
            when (which) {
                0 -> requireCurrentPin(activity, manager) { showSetup(activity, manager, onChanged) }
                1 -> requireCurrentPin(activity, manager) {
                    val enabling = !manager.state().biometricEnabled
                    if (enabling && !AppLockGateActivity.biometricAvailable(activity)) {
                        message(activity, "البصمة غير جاهزة", "أضف بصمة في إعدادات الهاتف أولًا، أو استمر باستخدام رمز القفل.")
                    } else {
                        manager.setBiometricEnabled(enabling)
                        onChanged()
                        message(activity, "تم الحفظ", if (enabling) "تم تفعيل فتح التطبيق بالبصمة مع بقاء الرمز كخيار احتياطي." else "تم إيقاف فتح التطبيق بالبصمة.")
                    }
                }
                2 -> requireCurrentPin(activity, manager) { showTimeoutPicker(activity, manager, onChanged) }
                3 -> { manager.lockNow(); onLockNow() }
                4 -> requireCurrentPin(activity, manager) {
                    manager.disable(); onChanged(); message(activity, "تم", "تم إلغاء قفل التطبيق من هذا الهاتف.")
                }
            }
        }.setNegativeButton("إغلاق", null).show()
    }

    private fun showSetup(activity: Activity, manager: AppLockManager, onChanged: () -> Unit) {
        val p = UiKit.palette(activity)
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 8, 28, 4) }
        box.addView(UiKit.subtitle(activity, p, "أنشئ رمزًا محليًا من 6 إلى 12 رقمًا. لا يُرفع الرمز إلى الخادم ولا يدخل ضمن النسخ الاحتياطية."))
        val pin = UiKit.field(activity, p, "رمز القفل الجديد", true).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val confirm = UiKit.field(activity, p, "تأكيد رمز القفل", true).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        box.addView(pin); box.addView(confirm)
        val dialog = AlertDialog.Builder(activity).setTitle(if (manager.state().enabled) "تغيير رمز قفل التطبيق" else "تفعيل قفل التطبيق")
            .setView(box).setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val first = pin.text.toString()
                if (first.length !in 6..12 || first.any { !it.isDigit() }) { pin.error = "استخدم 6 إلى 12 رقمًا"; return@setOnClickListener }
                if (first != confirm.text.toString()) { confirm.error = "الرمزان غير متطابقين"; return@setOnClickListener }
                val previous = manager.state()
                manager.setPin(first, previous.biometricEnabled, previous.timeoutSeconds)
                pin.text?.clear(); confirm.text?.clear(); dialog.dismiss(); onChanged()
                message(activity, "تم تفعيل الحماية", "سيطلب التطبيق الرمز بعد إغلاقه أو مغادرته حسب مدة القفل المحددة.")
            }
        }
        dialog.show()
    }

    private fun requireCurrentPin(activity: Activity, manager: AppLockManager, action: () -> Unit) {
        val p = UiKit.palette(activity)
        val pin = UiKit.field(activity, p, "رمز القفل الحالي", true).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val dialog = AlertDialog.Builder(activity).setTitle("تأكيد الهوية").setView(pin)
            .setPositiveButton("متابعة", null).setNegativeButton("إلغاء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                when (manager.verifyPin(pin.text.toString())) {
                    AppPinResult.SUCCESS -> { pin.text?.clear(); dialog.dismiss(); action() }
                    AppPinResult.INCORRECT -> { pin.text?.clear(); pin.error = "الرمز غير صحيح • المتبقي ${manager.state().remainingAttempts}" }
                    AppPinResult.TEMPORARILY_LOCKED -> pin.error = "محاولات كثيرة؛ حاول بعد ${manager.lockoutRemainingSeconds()} ثانية"
                }
            }
        }
        dialog.show()
    }

    private fun showTimeoutPicker(activity: Activity, manager: AppLockManager, onChanged: () -> Unit) {
        val values = AppLockManager.TIMEOUT_OPTIONS
        val labels = values.map(::timeoutLabel).toTypedArray()
        val checked = values.indexOf(manager.state().timeoutSeconds).coerceAtLeast(0)
        AlertDialog.Builder(activity).setTitle("متى يقفل التطبيق؟")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                manager.setTimeoutSeconds(values[which]); dialog.dismiss(); onChanged()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun timeoutLabel(seconds: Int): String = when (seconds) {
        0 -> "فورًا"
        30 -> "بعد 30 ثانية"
        60 -> "بعد دقيقة"
        300 -> "بعد 5 دقائق"
        else -> "بعد 30 ثانية"
    }

    private fun message(activity: Activity, title: String, body: String) {
        AlertDialog.Builder(activity).setTitle(title).setMessage(body).setPositiveButton("حسنًا", null).show()
    }
}
