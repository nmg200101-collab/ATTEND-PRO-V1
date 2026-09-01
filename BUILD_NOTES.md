ATTEND PRO 1.9.2 — BUILD NOTES
================================
Android:
- store-app: versionCode 37 / versionName 1.9.17
- employee-app: versionCode 37 / versionName 1.9.17

## 1.9.17

- تذكرة اقتران خادمية أحادية الاستخدام من 8 خانات تعمل على جميع الأجهزة.
- السماح باستبدال الهاتف السابق فقط بتذكرة جديدة موقعة من جهاز المحل.
- QR أصغر وأسهل قراءة لأنه يحمل رمز التذكرة بدل ملف الموظف الطويل.
- إدخال الرمز القصير أو APPAIR أو ملف AP3P الكامل في تطبيق الموظف.
- بث الاقتران المباشر عبر Bluetooth LE والشبكة المحلية/نقطة الاتصال.
- جميع وسائل النقل تنتهي إلى تحقق واحد من الخادم قبل تثبيت الارتباط.

## 1.9.16

- إدخال رمز ربط الموظف يدويًا داخل تطبيق الموظف وداخل شاشة QR كبديل متوافق مع Motorola وغيرها.
- استقبال رمز الربط عبر مشاركة النص مباشرة إلى تطبيق الموظف.
- طلب إثبات وجود حقيقي من جهاز المحل، إشعار مرتفع الأهمية في هاتف الموظف، ومتابعة النتيجة من الخادم.
- فرض طريقة التحقق المطلوبة على الخادم: بصمة الإصبع فقط، تحقق Android الحيوي، PIN/كلمة المرور، أو GPS.
- استمرار استقبال طلبات إثبات الوجود عند إيقاف بث التعرف التلقائي عبر Bluetooth/Wi-Fi.
- إضافة شاشة حالة اتصال الموظف بالمحل وتحسين توافق فحص GPS مع إصدارات Android المختلفة.

## 1.9.9

- فحص تلقائي للتحديث عند فتح تطبيق المحل أو الموظف.
- زر فحص يدوي للتحديث داخل التطبيقين.
- تنزيل APK عبر HTTPS والتحقق من SHA-256 واسم الحزمة وشهادة التوقيع قبل فتح التثبيت.
- التحديث فوق النسخة الحالية للحفاظ على التفعيل والموظفين وسجلات الحضور.
- قناة تحديث مستقلة لكل من Store وEmployee عبر الخادم المركزي.
- compileSdk/targetSdk: 35
- minSdk: 26
- JDK: 17

Central server:
- Node.js >= 22
- No npm dependencies
- Requires ATTEND_PRO_OWNER_API_KEY (>=32 chars) and ATTEND_PRO_DATA_KEY environment secrets
- Must be published behind HTTPS
- ATTEND_PRO_OFFLINE_LEASE_HOURS defaults to 72 and is clamped to 1..168

Secure Activation Lock:
- Store operational authorization is central-only.
- Trial/local legacy license validation no longer unlocks attendance, employees, Store Settings or Store Reports.
- Embedded local owner signing-key bootstrap was removed from the Android APK source.
- Central credentials are bound to the Store device P-256 Android Keystore identity.
- Central lease must be valid; clock rollback detection is applied.
- Central owner can approve, suspend, reactivate and renew subscription/max-employees.
- Report Receiver role remains independent of Store activation by design.

Security:
- Android Keystore AES-GCM protects central store access token, activation polling secret, central owner API key and receiver-secret migration.
- Central access tokens are random 256-bit values; server stores only SHA-256 token hashes.
- Store requests use ECDSA P-256 signatures, timestamps, body hashes and replay nonces.
- Activation polling/receiver secrets use scrypt hashes server-side.
- Pending activation tokens are AES-GCM encrypted at rest using the server-only data key.
- Report packages remain receiver-specific AES-GCM encrypted before upload.

Validation performed before packaging:
- Node server syntax check passed.
- Central activation integration passed: request -> owner approval -> signed activation -> ACTIVE validation -> suspension -> SUSPENDED validation -> renewal/reactivation.
- Security regression passed: wrong owner key blocked; stolen bearer token used with another EC device key blocked; replayed signed request blocked.
- Remote workflow regression passed: signed attendance sync -> receiver registration -> encrypted inbox -> remote dashboard/report -> receipt confirmation -> receiver revocation blocks access.
- All Android XML resources parsed successfully.
- Kotlin source parser/preflight found no syntax/parser errors in introduced code. A full Android Gradle compile is intentionally left for the repository GitHub Actions workflow, which is the authoritative APK build environment.


1.9.2 compile fix
- Restored UI compatibility accessors for lastSyncAt / lastSyncMessage.
- isActivationActive now aliases central activation only.
- effectiveEmployeeLimit now comes only from active central license.
- No local/offline license path restored.
# ATTEND PRO 1.9.18 — اقتران متعدد الأجهزة قابل للاستئناف

- إصلاح فقدان رمز الاقتران عند انقطاع الاستجابة بعد تسجيل المطالبة في الخادم.
- يمكن للهاتف نفسه استئناف المطالبة الآمنة بالرمز خلال نافذة الاقتران.
- ثلاث محاولات تلقائية لاستلام الملف وأربع محاولات لتثبيت الهاتف بالخادم.
- لا تُستبدل هوية موظف موجودة محليًا حتى يؤكد الخادم الهاتف الجديد.
- بث Wi‑Fi/Hotspot إلى عناوين البث الفعلية لكل واجهة شبكة، وليس العنوان العام فقط.
- إعلان BLE مزدوج عبر Manufacturer Data وService Data لمعالجة اختلاف أجهزة Android.
- الإصدار: versionCode 38 / versionName 1.9.18.
# ATTEND PRO 1.9.19 — إصلاح صلاحية الاقتران على اختلاف ساعات الهواتف

- تعتمد صلاحية الرمز القصير وQR على تحقق الخادم ووقت الخادم فقط.
- أزيل الفحص الثاني الخاطئ بساعة هاتف الموظف بعد قبول الخادم للرمز.
- بقي فحص الصلاحية المحلي لملف AP3P الكامل غير المتصل بالخادم.
- الإصدار: versionCode 39 / versionName 1.9.19.
