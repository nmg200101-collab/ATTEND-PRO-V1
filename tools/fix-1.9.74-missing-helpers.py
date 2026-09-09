from pathlib import Path

p = Path('buildsrc/store-app/src/main/java/com/attendpro/store/SystemManagement1971Activity.kt')
s = p.read_text(encoding='utf-8')

if 'private fun showOverflowMenu(anchor: View)' in s:
    print('1.9.74 helpers already present')
    raise SystemExit(0)

anchor = '    private fun card(root: LinearLayout, title: String, description: String = "", block: LinearLayout.() -> Unit) {'
pos = s.find(anchor)
if pos < 0:
    raise SystemExit('card anchor missing')

helpers = r'''    private fun showOverflowMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "⌂ الرئيسية")
            menu.add(0, 2, 1, "🔔 مركز الإشعارات")
            menu.add(0, 3, 2, "📘 دليل إدارة النظام")
            menu.add(0, 4, 3, "🛡 سجل العمليات")
            menu.add(0, 5, 4, "↻ تحديث الصفحة")
            menu.add(0, 6, 5, "＋ إجراء سريع")
            menu.add(0, 7, 6, "ⓘ حول إدارة النظام")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> showHome()
                    2 -> showNotifications()
                    3 -> showUserGuide()
                    4 -> showAudit()
                    5 -> showHome()
                    6 -> showQuickActions()
                    7 -> AlertDialog.Builder(this@SystemManagement1971Activity)
                        .setTitle("ATTEND-PRO • إدارة النظام 1.9.74")
                        .setMessage("واجهة الإدارة النهائية بألوان هادئة، بحث وفلاتر، عمليات جماعية، إشعارات منظمة، سجل عمليات، ووصول سريع للأدوات. نظام الارتباط الأساسي بقي دون تعديل.")
                        .setPositiveButton("حسنًا", null).show()
                }
                true
            }
            show()
        }
    }

    private fun showUserGuide() {
        val text = TextView(this).apply {
            textSize = 15f
            setTextColor(ink)
            gravity = Gravity.RIGHT
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(12), dp(18), dp(18))
            text = """دليل إدارة النظام

1) لوحة العرض
• تعرض ملخص المشتركين والوكلاء والحالات الرئيسية بسرعة.
• استخدم الأقسام للانتقال بين المشتركين والوكلاء والصلاحيات والإشعارات والسجل.

2) المشتركـون
• استخدم البحث للوصول بالاسم أو الهاتف أو المعرّف أو الوكيل.
• استخدم الفلاتر لعرض النشط أو الموقوف أو المنتهي أو المؤرشف.
• افتح الحساب للتحكم الفردي في حالته واشتراكه.

3) العمليات الجماعية
• افتح «إدارة جماعية» وحدد الحسابات المطلوبة.
• تظهر أدوات الإيقاف وإعادة التفعيل والأرشفة والتمديد والإشعار والحذف للمالك.
• الحذف النهائي يحتاج تأكيدًا واضحًا ولا يمكن التراجع عنه.

4) الوكلاء والصلاحيات
• أنشئ الوكيل وحدد مستوى الثقة والصلاحيات المسموحة له.
• صلاحيات الوكيل تُطبّق على نطاق العملاء المسموح به فقط.

5) الإشعارات
• مركز الإشعارات مرتب حسب النوع وحالة القراءة.
• يمكن تعليم الإشعارات غير المقروءة كمقروءة دفعة واحدة.

6) سجل العمليات
• ابحث بالإجراء أو المستخدم أو الحساب لمراجعة النشاط الإداري.

7) قائمة ⋮ والإجراء السريع
• للوصول السريع إلى الرئيسية والإشعارات والدليل والسجل والإجراءات المتكررة."""
        }
        AlertDialog.Builder(this)
            .setTitle("📘 دليل إدارة النظام")
            .setView(ScrollView(this).apply { addView(text) })
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun statusArabic(value: String): String = when (value.uppercase(Locale.ROOT)) {
        "ACTIVE" -> "نشط"
        "TRIAL" -> "تجريبي"
        "SUSPENDED" -> "موقوف"
        "ARCHIVED" -> "مؤرشف"
        "EXPIRED" -> "منتهي"
        "PENDING" -> "بانتظار الموافقة"
        else -> value.ifBlank { "غير محدد" }
    }

    private fun categoryArabic(value: String): String = when (value.uppercase(Locale.ROOT)) {
        "ACTIVATION" -> "التفعيل"
        "AGENT" -> "الوكلاء"
        "REGISTRATION" -> "التسجيل"
        "SUBSCRIPTION" -> "الاشتراكات"
        "DEVICE" -> "الأجهزة"
        "SECURITY" -> "الأمان"
        "SYSTEM" -> "النظام"
        else -> "عام"
    }

    private fun confirmPermanentDelete(count: Int, yes: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("🗑 حذف نهائي")
            .setMessage("سيتم حذف ${if (count == 1) "المشترك المحدد" else "$count مشتركين محددين"} من الحسابات التشغيلية على الخادم. لا يمكن التراجع عن هذه العملية.\n\nسجل التدقيق الإداري سيبقى محفوظًا.")
            .setPositiveButton("حذف نهائي") { _, _ -> yes() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

'''

s = s[:pos] + helpers + s[pos:]
p.write_text(s, encoding='utf-8')
print('restored 1.9.74 management helpers')
