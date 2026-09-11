package com.attendpro.core

import android.app.Activity
import android.app.AlertDialog
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * App-scoped language preference shared by Store and Employee apps.
 *
 * Android 13+ uses the platform per-app locale API so the choice is persisted by Android.
 * Android 8-12 keep the same preference in private app storage and apply it before UI creation.
 */
object AppLanguage {
    private const val PREFS = "attend_pro_language_v2"
    private const val KEY = "language"
    const val AR = "ar"
    const val EN = "en"

    fun code(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val tags = context.getSystemService(LocaleManager::class.java)?.applicationLocales?.toLanguageTags().orEmpty()
            val first = tags.substringBefore(',').substringBefore('-').lowercase(Locale.ROOT)
            if (first == AR || first == EN) return first
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, AR)
            ?.takeIf { it == AR || it == EN } ?: AR
    }

    fun isEnglish(context: Context): Boolean = code(context) == EN

    fun set(context: Context, language: String) {
        val normalized = if (language == EN) EN else AR
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, normalized).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                LocaleList.forLanguageTags(normalized)
        }
        applyToResources(context, normalized)
    }

    @Suppress("DEPRECATION")
    fun applyToResources(context: Context, forced: String? = null) {
        val language = forced ?: code(context)
        val locale = Locale(language)
        Locale.setDefault(locale)
        val res = context.resources
        val config = Configuration(res.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        res.updateConfiguration(config, res.displayMetrics)
    }

    fun text(context: Context, arabic: String, english: String): String =
        if (isEnglish(context)) english else arabic

    /**
     * Migration bridge for legacy screens that still pass Arabic literals into UiKit.
     * It affects presentation only when English is selected and does not touch protocol,
     * stored data, server payloads or Arabic mode.
     */
    fun legacyUiText(context: Context, value: String): String {
        if (!isEnglish(context) || value.isBlank()) return value
        val exact = mapOf(
            "إعدادات المحل" to "Store Settings",
            "إدارة المحل" to "Store Management",
            "لوحة إدارة المحل" to "Store Management Dashboard",
            "المحل والموظفون" to "Store and Employees",
            "معلومات المحل" to "Store Information",
            "إضافة وإدارة الموظفين" to "Add and Manage Employees",
            "الحضور والتحقق" to "Attendance and Verification",
            "الحضور والتشغيل" to "Attendance and Operations",
            "طرق الحضور" to "Attendance Methods",
            "تفعيل وتعطيل طرق الحضور" to "Enable / Disable Attendance Methods",
            "الدوام ودقائق السماح" to "Working Hours and Grace Period",
            "موقع المحل وGPS" to "Store Location and GPS",
            "إعداد GPS وموقع المحل" to "GPS and Store Location",
            "قارئ البصمة الخارجي" to "External Fingerprint Reader",
            "الصوت والرسائل" to "Voice and Messages",
            "الصوت والتنبيهات" to "Voice and Alerts",
            "التحكم الصوتي" to "Voice Controls",
            "فتح التحكم الصوتي" to "Open Voice Controls",
            "الرسائل والإشعارات" to "Messages and Notifications",
            "فتح مركز الرسائل" to "Open Message Center",
            "التقارير والحماية" to "Reports and Security",
            "التقارير والمشاركة" to "Reports and Sharing",
            "التقارير والمراقبة" to "Reports and Monitoring",
            "فتح التقارير والمشاركة" to "Open Reports and Sharing",
            "هواتف استلام التقارير" to "Report Receiver Phones",
            "هواتف استلام التقارير والصلاحيات" to "Receiver Phones and Permissions",
            "قفل التطبيق والبصمة" to "App Lock and Biometrics",
            "النسخ الاحتياطي والاستعادة" to "Backup and Restore",
            "فحص جاهزية المحل" to "Store Readiness Check",
            "المظهر والقوالب" to "Appearance and Layouts",
            "الحماية والصلاحيات" to "Security and Permissions",
            "إنشاء رمز حماية" to "Create Protection PIN",
            "تغيير رمز إدارة المحل" to "Change Store Management PIN",
            "قفل إدارة المحل الآن" to "Lock Store Management Now",
            "المظهر والصيانة" to "Appearance and Maintenance",
            "إدارة ATTEND PRO العليا" to "ATTEND PRO System Administration",
            "إغلاق إدارة المحل" to "Close Store Management",
            "القائمة" to "Menu",
            "رجوع" to "Back",
            "إغلاق" to "Close",
            "إلغاء" to "Cancel",
            "حفظ" to "Save",
            "اختيار" to "Select",
            "حسنًا" to "OK",
            "الإشعارات" to "Notifications",
            "الإعدادات" to "Settings",
            "الإعدادات المتقدمة" to "Advanced Settings",
            "إعدادات النظام" to "System Settings",
            "منطقة إدارة النظام" to "System Administration",
            "مدير النظام" to "System Administrator",
            "مالك النظام" to "System Owner",
            "دخول إدارة النظام" to "Open System Administration",
            "بوابة الوكيل" to "Agent Portal",
            "الوكيل" to "Agent",
            "دخول الوكيل المركزي" to "Open Central Agent Portal",
            "حماية الدخول" to "Login Protection",
            "رمز المالك" to "Owner Code",
            "دخول المالك" to "Owner Login",
            "دخول" to "Sign In",
            "تعذر الدخول" to "Unable to Sign In",
            "شكل لوحة المالك" to "Owner Dashboard Layout",
            "النموذج الحالي" to "Current Layout",
            "نموذج الأقسام" to "Sections Layout",
            "لوحة المالك" to "Owner Dashboard",
            "ملخص الإدارة" to "Administration Summary",
            "الصلاحيات والأمان" to "Permissions and Security",
            "صلاحية مدير النظام (المالك)" to "System Owner Authority",
            "تغيير رمز المالك" to "Change Owner Code",
            "إعادة تعيين رمز إدارة المحل" to "Reset Store Management PIN",
            "الإدارة المركزية" to "Central Administration",
            "إدارة الوكلاء المركزية" to "Central Agent Management",
            "إدارة النظام المركزية 1.9.74" to "Central System Management",
            "سجل المحلات والوكلاء (لا يفعّل التشغيل)" to "Store and Agent Registry",
            "مركز إدارة النظام والتفعيلات" to "System and Activation Center",
            "التحكم بالتطبيق" to "Application Control",
            "إعدادات التطبيق العامة" to "General App Settings",
            "فتح إعدادات المحل" to "Open Store Settings",
            "الخادم المركزي والمزامنة" to "Central Server and Sync",
            "فحص جاهزية النظام" to "System Readiness Check",
            "خروج من إدارة النظام" to "Exit System Administration",
            "أقسام لوحة المالك" to "Owner Dashboard Sections",
            "الملخص" to "Summary",
            "التطبيق" to "Application",
            "الخصوصية والبيانات" to "Privacy and Data",
            "اللغة" to "Language",
            "دليل المستخدم" to "User Guide",
            "الموظفون" to "Employees",
            "الموظفون الحاضرون" to "Employees Present",
            "الأجهزة المتصلة" to "Connected Devices",
            "الحضور والانصراف" to "Check-in and Check-out",
            "تسجيل حضور" to "Check In",
            "تسجيل انصراف" to "Check Out",
            "آخر حركة" to "Latest Activity",
            "اليوم" to "Today",
            "تحديث" to "Refresh",
            "مزامنة الآن" to "Sync Now",
            "التفعيل المركزي مطلوب" to "Central Activation Required",
            "التفعيل المركزي نشط" to "Central Activation Active",
            "فحص الجاهزية" to "Readiness Check",
            "إدارة الاتصال" to "Connection Management",
            "حالة الاتصال" to "Connection Status",
            "طرق التحقق" to "Verification Methods",
            "ربط الهاتف" to "Pair Phone"
        )
        exact[value.trim()]?.let { return it }

        return value
            .replace("إدارة المحل", "Store Management")
            .replace("إدارة النظام", "System Administration")
            .replace("الفرع", "Branch")
            .replace("الإصدار", "Version")
            .replace("موظف نشط", "active employees")
            .replace("هاتف موظف مفعّل", "linked employee phones")
            .replace("الخادم المركزي", "Central server")
            .replace("التفعيل نشط", "activation active")
            .replace("يحتاج التفعيل المركزي إلى مراجعة", "central activation needs review")
            .replace("لا توجد", "No")
    }

    fun showPicker(activity: Activity, onChanged: (() -> Unit)? = null) {
        val english = isEnglish(activity)
        val title = if (english) "Language" else "اللغة"
        val items = arrayOf("العربية", "English")
        val checked = if (english) 1 else 0
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setSingleChoiceItems(items, checked) { dialog, which ->
                val next = if (which == 1) EN else AR
                if (next != code(activity)) {
                    set(activity, next)
                    dialog.dismiss()
                    onChanged?.invoke() ?: activity.recreate()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(if (english) "Cancel" else "إلغاء", null)
            .show()
    }
}
