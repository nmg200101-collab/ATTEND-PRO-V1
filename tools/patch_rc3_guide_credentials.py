from pathlib import Path

p = Path('buildsrc/store-app/src/main/java/com/attendpro/store/StoreUserGuideActivity.kt')
s = p.read_text(encoding='utf-8')

s = s.replace(
    'تحسين إدارة بيانات دخول الوكلاء، الرد بدون إنترنت، صلاحيات هاتف الاستلام، وتحسين الإنجليزية والواجهة.',
    'تحسين إدارة بيانات دخول الوكلاء واستعادة المشترك، الرد بدون إنترنت، صلاحيات هاتف الاستلام، وتحسين الإنجليزية والواجهة.',
    1,
)
s = s.replace(
    'improved agent credential management, offline replies, receiver-phone permissions, and improved English/UI coverage.',
    'improved agent credential and subscriber-recovery management, offline replies, receiver-phone permissions, and improved English/UI coverage.',
    1,
)
s = s.replace(
    '"من الرئيسية اختر حضورًا أو انصرافًا ثم اختر الموظف وطريقة التحقق المسموحة له. نفّذ التحقق حتى تظهر نتيجة النجاح. اكتشاف الهاتف أو GPS وحده لا يسجل الحضور تلقائيًا.",',
    '"من بطاقة «الحضور والانصراف» الكبيرة في الرئيسية اختر حضورًا أو انصرافًا، ثم اختر الموظف وطريقة التحقق المسموحة له. نفّذ التحقق حتى تظهر نتيجة النجاح. اكتشاف الهاتف أو GPS وحده لا يسجل الحضور تلقائيًا.",',
    1,
)
s = s.replace(
    '"From Home choose Check in or Check out, select the employee and an allowed verification method, then complete verification. Phone discovery or GPS alone does not automatically record attendance."),',
    '"From the prominent Attendance card on Home choose Check in or Check out, select the employee and an allowed verification method, then complete verification. Phone discovery or GPS alone does not automatically record attendance."),',
    1,
)

anchor = '''                    Topic("update", "3. التحديث", "3. Update",
                        "نسخة Google Play تتحدث عبر Google Play. النسخة المباشرة تتحقق من HTTPS والحزمة والتوقيع وSHA‑256 ثم تطلب موافقة المستخدم. لا يوجد تثبيت صامت.",
                        "The Google Play build updates through Google Play. The Direct build validates HTTPS, package, signature and SHA‑256 and then asks the user to approve installation. There is no silent install.")
'''
replacement = '''                    Topic("agent_credential", "3. رمز دخول الوكيل", "3. Agent sign-in credential",
                        "من إدارة النظام افتح تفاصيل الوكيل ثم «بيانات الدخول». لا يعرض ATTEND PRO كلمة مرور قديمة ولا يفك أي hash. عند الحاجة اضغط «إظهار / إنشاء رمز دخول جديد». ينشئ الخادم رمزًا جديدًا ويلغي السابق، ويظهر الرمز كاملًا مرة واحدة فقط. انسخه واحفظه قبل إغلاق النافذة.",
                        "In System Administration open the agent details, then Sign-in credentials. ATTEND PRO never reveals an old password or reverses a hash. Choose Show / create new sign-in code when needed. The server rotates the credential, invalidates the previous one, and displays the new value only once. Copy and save it before closing the dialog."),
                    Topic("subscriber_recovery", "4. بيانات دخول واستعادة المشترك", "4. Subscriber access and recovery",
                        "من إدارة النظام ← المشتركين افتح المشترك ثم «بيانات الدخول والاستعادة». لا يتم عرض Access Token الدائم. مالك النظام فقط يستطيع إنشاء رمز استعادة جديد محدود الغرض. الرمز مؤقت، يُخزن في الخادم كـhash، يبطل الرمز السابق غير المستخدم، ويُستهلك مرة واحدة عند استعادة الجهاز. احفظ الرمز فور ظهوره لأنه لا يعرض مرة أخرى.",
                        "In System Administration → Subscribers open the subscriber, then Access and recovery. The permanent Access Token is never displayed. Only the system owner can issue a new purpose-limited recovery code. It is temporary, stored server-side only as a hash, invalidates the previous unused code, and is consumed once during device recovery. Save it when shown because it is not displayed again."),
                    Topic("update", "5. التحديث", "5. Update",
                        "نسخة Google Play تتحدث عبر Google Play. النسخة المباشرة تتحقق من HTTPS والحزمة والتوقيع وSHA‑256 ثم تطلب موافقة المستخدم. لا يوجد تثبيت صامت.",
                        "The Google Play build updates through Google Play. The Direct build validates HTTPS, package, signature and SHA‑256 and then asks the user to approve installation. There is no silent install.")
'''
if anchor not in s:
    raise SystemExit('guide update topic anchor not found')
s = s.replace(anchor, replacement, 1)
p.write_text(s, encoding='utf-8')
