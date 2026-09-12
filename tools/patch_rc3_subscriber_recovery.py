from pathlib import Path

p = Path('buildsrc/store-app/src/main/java/com/attendpro/store/SystemManagement1971Activity.kt')
s = p.read_text(encoding='utf-8')

old = '''        card(root, "بيانات الدخول", "حالة الاعتماد والحماية") {
            addView(TextView(this@SystemManagement1971Activity).apply {
                text = "هذا المشترك لا يملك كلمة مرور نصية محفوظة في النظام. الدخول والتشغيل يعتمدان على تفعيل الجهاز وAccess Token محمي. عند نقل الجهاز استخدم الاستعادة أو إعادة التفعيل المعتمدة من مالك النظام."
                textSize = 13f; setTextColor(muted); gravity = Gravity.RIGHT
            })
        }
'''
new = '''        card(root, "بيانات الدخول والاستعادة", "حالة الاعتماد والحماية") {
            addView(TextView(this@SystemManagement1971Activity).apply {
                text = "حالة الاعتماد: ${statusArabic(store.optString(\"status\"))}\\nلا توجد كلمة مرور نصية قابلة للاسترجاع. التفعيل وAccess Token الدائم لا يتم عرضهما من إدارة النظام."
                textSize = 13f; setTextColor(muted); gravity = Gravity.RIGHT
            })
            if (owner) addView(action("إنشاء رمز وصول/استعادة جديد") { issueSubscriberRecovery(id) })
        }
'''
if old not in s:
    raise SystemExit('subscriber credential card anchor not found')
s = s.replace(old, new, 1)

anchor = '''    private fun storeAction(storeId: String, action: String, extra: JSONObject = JSONObject()) {
'''
method = '''    private fun issueSubscriberRecovery(storeId: String) {
        request("POST", "/api/v1/admin/stores/recovery-grant", JSONObject().put("storeId", storeId)) { result ->
            result.onSuccess { data ->
                val code = data.optString("code")
                val expiresAt = data.optLong("expiresAt")
                if (code.isBlank()) {
                    toast("لم يُرجع الخادم رمز الاستعادة")
                    return@onSuccess
                }
                AlertDialog.Builder(this)
                    .setTitle("رمز وصول/استعادة جديد")
                    .setMessage("احفظ الرمز الآن، لن يظهر مرة أخرى بعد إغلاق النافذة.\\n\\n$code\\n\\nصالح حتى: ${time(expiresAt)}\\nالرمز السابق غير المستخدم أصبح غير صالح، ويُستخدم هذا الرمز مرة واحدة فقط للاستعادة.")
                    .setPositiveButton("نسخ الرمز") { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ATTEND-PRO recovery code", code))
                        toast("تم نسخ رمز الاستعادة")
                    }
                    .setNegativeButton("إغلاق", null)
                    .show()
            }.onFailure {
                AlertDialog.Builder(this)
                    .setTitle("تعذر إصدار رمز الاستعادة")
                    .setMessage(it.message ?: "خطأ غير معروف")
                    .setPositiveButton("إغلاق", null)
                    .show()
            }
        }
    }

'''
if anchor not in s:
    raise SystemExit('storeAction anchor not found')
s = s.replace(anchor, method + anchor, 1)
p.write_text(s, encoding='utf-8')
