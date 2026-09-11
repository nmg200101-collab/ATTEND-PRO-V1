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
            "ربط الهاتف" to "Pair Phone",
            "خطأ" to "Error",
            "تم" to "Done",
            "متابعة" to "Continue",
            "حذف" to "Delete",
            "حذف نهائي" to "Delete Permanently",
            "إرسال" to "Send",
            "تأكيد" to "Confirm",
            "تنبيه" to "Alert",
            "لاحقًا" to "Later",
            "مطلوب" to "Required",
            "متوقف" to "Stopped",
            "موقوف" to "Suspended",
            "انتهت" to "Expired",
            "غير صالح" to "Invalid",
            "غير مدعوم" to "Not Supported",
            "غير مهيأ" to "Not Configured",
            "غير متصل الآن" to "Not Connected Now",
            "يجب استخدام HTTPS" to "HTTPS is required",
            "الخادم يجب أن يستخدم HTTPS" to "The server must use HTTPS",
            "استخدم HTTPS" to "Use HTTPS",
            "الخادم المركزي" to "Central Server",
            "التفعيل المركزي" to "Central Activation",
            "التفعيل" to "Activation",
            "التسجيل" to "Registration",
            "الاشتراكات" to "Subscriptions",
            "الأجهزة" to "Devices",
            "الأمان" to "Security",
            "النظام" to "System",
            "الوكلاء" to "Agents",
            "المشتركون" to "Subscribers",
            "طلبات التفعيل" to "Activation Requests",
            "سجل العمليات" to "Activity Log",
            "مركز الإشعارات" to "Notification Center",
            "إدارة جماعية" to "Bulk Management",
            "بحث عن مشترك" to "Search Subscriber",
            "الكل" to "All",
            "نشط" to "Active",
            "مؤرشف" to "Archived",
            "منتهي" to "Expired",
            "معلق" to "Suspended",
            "جاهز" to "Ready",
            "عرض التفاصيل" to "View Details",
            "رفض الطلب" to "Reject Request",
            "أرشفة" to "Archive",
            "فصل الجهاز" to "Disconnect Device",
            "تم الحذف" to "Deleted",
            "إعادة تفعيل المحل" to "Reactivate Store",
            "تعليق سجل المحل" to "Suspend Store Record",
            "حذف سجل المحل" to "Delete Store Record",
            "اسم المحل" to "Store Name",
            "اسم المحل مطلوب" to "Store name is required",
            "رمز الفرع مطلوب" to "Branch code is required",
            "رقم الموظف" to "Employee ID",
            "هاتف الموظف" to "Employee Phone",
            "إضافة موظف" to "Add Employee",
            "حذف الموظف" to "Delete Employee",
            "إدارة الموظفين" to "Employee Management",
            "＋ إضافة موظف جديد" to "＋ Add New Employee",
            "عرض الموظفين وتعديلهم" to "View and Edit Employees",
            "شرح طرق التعرف" to "Verification Methods Guide",
            "جاهز لإدارة الموظفين" to "Ready to Manage Employees",
            "رجوع إلى إعدادات المحل" to "Back to Store Settings",
            "رقم الموظف مستخدم بالفعل" to "Employee ID is already in use",
            "الموظف غير موجود أو موقوف" to "Employee not found or disabled",
            "لا يوجد موظفون نشطون" to "No active employees",
            "الموظفون المرتبطون" to "Linked Employees",
            "لا يوجد موظف مرتبط بتطبيق الموظف حاليًا." to "No employee is currently linked to the Employee app.",
            "لم يُؤكد الربط بعد" to "Pairing has not been confirmed yet",
            "ربط هاتف الموظف" to "Pair Employee Phone",
            "ربط جهاز موظف" to "Pair Employee Device",
            "إلغاء الربط" to "Unpair",
            "إدخال أو لصق رمز الربط" to "Enter or Paste Pairing Code",
            "تم فصل الهاتف" to "Phone Disconnected",
            "إدارة الاتصال" to "Connection Management",
            "مركز الاتصال والأجهزة" to "Connection and Devices Center",
            "حالة الاتصال والأجهزة" to "Connection and Device Status",
            "فتح مركز الاتصال" to "Open Connection Center",
            "الأجهزة الموجودة الآن" to "Devices Present Now",
            "الأجهزة والاتصال" to "Devices and Connectivity",
            "الاتصال" to "Connection",
            "● مباشر" to "● Direct",
            "جاري فحص الأجهزة المتصلة…" to "Checking connected devices…",
            "جاري فحص الاتصال…" to "Checking connections…",
            "GPS • مراقبة" to "GPS • Monitoring",
            "GPS غير مؤكد" to "GPS Unconfirmed",
            "GPS: لا توجد قراءة" to "GPS: No reading",
            "داخل نطاق المحل" to "Inside Store Geofence",
            "قريب من نطاق المحل" to "Near Store Geofence",
            "خارج نطاق المحل" to "Outside Store Geofence",
            "المسافة غير متاحة" to "Distance unavailable",
            "GPS رصد فقط" to "GPS Monitoring Only",
            "التعرف على الموظفين عبر GPS" to "Employee Detection via GPS",
            "الموقع" to "Location",
            "موقع المحل وGPS" to "Store Location and GPS",
            "طرق الحضور والتحقق" to "Attendance and Verification Methods",
            "تسجيل الحضور والانصراف" to "Check-in and Check-out",
            "الحضور" to "Attendance",
            "حضور" to "Check In",
            "انصراف" to "Check Out",
            "الحضور الآن" to "Attendance Now",
            "تسجيل الحضور الآن" to "Check In Now",
            "تسجيل الانصراف الآن" to "Check Out Now",
            "إثبات الوجود" to "Presence Proof",
            "إثبات وجود" to "Presence Proof",
            "كل طرق التحقق" to "All Verification Methods",
            "▣ كلمة المرور" to "▣ Password",
            "كلمة المرور" to "Password",
            "كلمة مرور هاتف الموظف" to "Employee Phone Password",
            "◖ بصمة الصوت" to "◖ Voiceprint",
            "العبارة الصوتية" to "Voice Phrase",
            "اكتب العبارة كما هي" to "Enter the phrase exactly as shown",
            "بصمة الوجه" to "Face Verification",
            "◎ بصمة/وجه الهاتف" to "◎ Phone Biometric / Face",
            "◎ بصمة/وجه هاتف الموظف" to "◎ Employee Phone Biometric / Face",
            "اختبار بصمة/وجه الهاتف" to "Test Phone Biometric / Face",
            "▦ QR مباشر" to "▦ Direct QR",
            "QR مباشر" to "Direct QR",
            "أيام الدوام" to "Working Days",
            "طريقة قديمة محفوظة للسجل" to "Legacy method retained for history",
            "الدوام ودقائق السماح" to "Working Hours and Grace Period",
            "0 إلى 23" to "0 to 23",
            "0 إلى 59" to "0 to 59",
            "ملخص اليوم" to "Today's Summary",
            "الرئيسية" to "Home",
            "الأقسام" to "Sections",
            "الإدارة" to "Management",
            "الخدمات والإدارة" to "Services and Management",
            "آخر نشاط للنظام" to "Latest System Activity",
            "آخر عمليات الحضور اليوم" to "Latest Attendance Today",
            "عرض سجل الحضور والاتصال" to "View Attendance and Connection Log",
            "لا توجد عملية اليوم" to "No Activity Today",
            "فحص التحديث" to "Check for Updates",
            "فحص تحديث التطبيق" to "Check App Update",
            "إعدادات التطبيق" to "App Settings",
            "المظهر وطريقة العرض" to "Appearance and Layout",
            "اختصارات إدارة المحل" to "Store Management Shortcuts",
            "اختصارات الشاشة الرئيسية" to "Home Screen Shortcuts",
            "تعديل اختصارات الرئيسية" to "Edit Home Shortcuts",
            "تخصيص اختصارات الشاشة الرئيسية" to "Customize Home Screen Shortcuts",
            "إزالة الكل" to "Remove All",
            "صاحب العمل فقط" to "Employer Only",
            "الموظف فقط" to "Employee Only",
            "صاحب العمل والموظف" to "Employer and Employee",
            "رمز إدارة المحل" to "Store Management PIN",
            "رمز صاحب العمل" to "Employer PIN",
            "تم القفل لمدة دقيقة" to "Locked for one minute",
            "الرمز غير صحيح" to "Incorrect PIN",
            "تم إيقاف الإدارة الحساسة" to "Sensitive Administration Disabled",
            "نسخة غير رسمية" to "Unofficial Build",
            "النظام جاهز" to "System Ready",
            "إكمال إعداد المحل" to "Complete Store Setup",
            "المحل غير مفعّل" to "Store Not Activated",
            "يتطلب موافقة إدارة النظام" to "System Administration Approval Required",
            "بانتظار التفعيل المركزي" to "Waiting for Central Activation",
            "إرسال طلب تفعيل مركزي" to "Send Central Activation Request",
            "إرسال طلب التفعيل لإدارة النظام" to "Send Activation Request to System Administration",
            "فحص موافقة إدارة النظام" to "Check System Administration Approval",
            "التحقق من صلاحية المحل الآن" to "Validate Store Authorization Now",
            "عرض بيانات التفعيل المركزي" to "View Central Activation Details",
            "بيانات طلب التفعيل" to "Activation Request Details",
            "بيانات التفعيل المركزي" to "Central Activation Details",
            "إعداد بيانات المحل والخادم" to "Configure Store and Server",
            "إعداد التفعيل المركزي" to "Central Activation Setup",
            "عنوان الخادم المركزي" to "Central Server URL",
            "استعادة تفعيل هذا الجهاز" to "Restore Activation on This Device",
            "استعادة بتصريح صاحب النظام" to "Restore with System Owner Authorization",
            "استعادة التفعيل" to "Restore Activation",
            "استعادة" to "Restore",
            "رمز تصريح الاستعادة" to "Recovery Authorization Code",
            "تعذر استعادة التفعيل" to "Unable to Restore Activation",
            "التصريح غير صالح أو منتهي" to "Authorization is invalid or expired",
            "تمت استعادة التفعيل ✓" to "Activation Restored ✓",
            "تمت استعادة التفعيل تلقائيًا ✓" to "Activation Restored Automatically ✓",
            "تجربة 3 أيام" to "3-Day Trial",
            "اشتراك شهري" to "Monthly Subscription",
            "اشتراك سنوي" to "Annual Subscription",
            "نوع الاشتراك" to "Subscription Type",
            "تسجيل محل جديد" to "Register New Store",
            "تسجيل محل جديد مباشرة — تجربة 3 أيام" to "Register New Store — 3-Day Trial",
            "طلب تفعيل ATTEND PRO" to "ATTEND PRO Activation Request",
            "تسجيل وبدء التجربة" to "Register and Start Trial",
            "إرسال الطلب" to "Send Request",
            "تم التسجيل" to "Registered",
            "تم التسجيل وبدء التجربة ✓" to "Registered and Trial Started ✓",
            "✓ تم تسجيل المحل وبدء التجربة" to "✓ Store Registered and Trial Started",
            "تم إرسال الطلب" to "Request Sent",
            "✓ تم إرسال طلب التفعيل المركزي" to "✓ Central Activation Request Sent",
            "تعذر إرسال طلب التفعيل" to "Unable to Send Activation Request",
            "تعذر إنشاء طلب التسجيل" to "Unable to Create Registration Request",
            "تعذر التسجيل" to "Unable to Register",
            "تعذر التسجيل المباشر" to "Direct Registration Failed",
            "لم يكتمل التسجيل المباشر" to "Direct Registration Did Not Complete",
            "حالة التفعيل" to "Activation Status",
            "بانتظار الاعتماد" to "Waiting for Approval",
            "طلب التفعيل غير نشط" to "Activation Request Is Not Active",
            "طلب التفعيل بانتظار اعتماد إدارة النظام" to "Activation Request Is Waiting for System Administration Approval",
            "لا يوجد طلب مركزي محفوظ على هذا الجهاز." to "No central activation request is stored on this device.",
            "لا يوجد تفعيل مركزي محفوظ على هذا الجهاز." to "No central activation is stored on this device.",
            "جاري التحقق من التفعيل..." to "Checking activation…",
            "جاري التحقق الآمن من الخادم..." to "Securely validating with the server…",
            "تعذر فحص التفعيل" to "Unable to Check Activation",
            "استجابة تفعيل غير مكتملة" to "Incomplete Activation Response",
            "تحديث الخادم مطلوب" to "Server Update Required",
            "✓ تم تفعيل المحل مركزيًا" to "✓ Store Centrally Activated",
            "تم التفعيل المركزي ✓" to "Central Activation Complete ✓",
            "تم التحقق من صلاحية المحل بنجاح." to "Store authorization validated successfully.",
            "تم إيقاف التشغيل" to "Operation Disabled",
            "تعذر الاتصال بالخادم" to "Unable to Connect to Server",
            "خطأ في الاتصال بالخادم" to "Server Connection Error",
            "تعذر التحديث" to "Unable to Update",
            "تعذر التحميل" to "Unable to Load",
            "تعذر الحذف" to "Unable to Delete",
            "تعذر الحفظ" to "Unable to Save",
            "تعذر إرسال الطلب" to "Unable to Send Request",
            "تعذر رفع العمليات المحلية" to "Unable to Upload Local Events",
            "تمت المزامنة" to "Synchronized",
            "فحص جاهزية النظام" to "System Readiness Check",
            "فحص جاهزية المحل" to "Store Readiness Check",
            "هاتف المراقبة" to "Monitoring Phone",
            "● الخادم المركزي متصل والتفعيل نشط" to "● Central server connected and activation active",
            "● يحتاج التفعيل المركزي إلى مراجعة" to "● Central activation needs review",
            "📘 دليل إدارة النظام" to "📘 System Administration Guide",
            "＋ إجراء سريع" to "＋ Quick Action",
            "🔔 مركز الإشعارات" to "🔔 Notification Center",
            "🛡 الصلاحيات والأمان" to "🛡 Permissions and Security",
            "☑ الإدارة الجماعية" to "☑ Bulk Management",
            "☷ سجل العمليات" to "☷ Activity Log",
            "إضافة وكيل" to "Add Agent",
            "تم إنشاء الوكيل" to "Agent Created",
            "يحتاج موافقة" to "Approval Required",
            "لا توجد نتائج" to "No Results",
            "رجوع إلى قسم المشتركين" to "Back to Subscribers",
            "🔔 إرسال إشعار" to "🔔 Send Notification",
            "خطأ غير معروف" to "Unknown Error",
            "السياسة العامة للاستعادة" to "Global Recovery Policy",
            "أرشفة التفعيل القديم" to "Archive Old Activation",
            "استعادة تلقائية للجهاز الموثوق" to "Automatic Recovery for Trusted Device",
            "إدارة الاشتراك والاستعادة" to "Subscription and Recovery Management",
            "إعادة الطلب إلى السجل" to "Restore Request to Registry",
            "أرشفة الطلب من السجل" to "Archive Request from Registry",
            "المزامنة" to "Synchronization",
            "تم إلغاء المسح" to "Scan Cancelled",
            "حفظ واختبار" to "Save and Test",
            "التفعيل مطلوب" to "Activation Required",
            "الخادم" to "Server",
            "يعمل" to "Running"
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
