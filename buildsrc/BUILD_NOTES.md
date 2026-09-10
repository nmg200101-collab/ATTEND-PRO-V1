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

# ATTEND PRO 1.9.20 — حضور هاتف الموظف والقوائم المباشرة

- مزامنة عكسية موقعة تسحب حضور الموظف من الخادم إلى جهاز المحل دون تكرار.
- قائمة مستقلة للهواتف المتصلة وقنوات Bluetooth وWi-Fi/Hotspot ووقت آخر التقاط.
- قائمة حضور يومية مستقلة تعرض الموظف والوقت ونوع العملية وطريقة التحقق.
- QR مبسط يحمل الرمز القصير فقط لرفع موثوقية المسح بين شاشات الهواتف.
- الإصدار: versionCode 40 / versionName 1.9.20.
# 1.9.21 — Store dashboard redesign

- Unified the store landing page into one calm operational dashboard.
- Added a live connection card showing the number and names of connected employee devices.
- Added a detailed connection center for server, Bluetooth, Wi-Fi/Hotspot, channel, signal, and last-seen information.
- Kept the central attendance fingerprint as the primary action and grouped all allowed verification methods behind it.
- Reorganized today's summary, nearby devices, recent attendance, reports, activation, updates, and owner settings.
- Preserved server attendance push/pull and the existing verification flows.
# 1.9.22 — Elegant store and employee dashboards

- Rebuilt both landing screens around compact top tiles, a smaller central attendance fingerprint, and a two-column section grid.
- Restored explicit Store Owner and System Owner entries on the store dashboard.
- Moved connection details and recent attendance into compact clickable top windows.
- Organized employee pairing, attendance, verification, connection, readiness, credentials, update, and unlink actions into focused windows.
- Preserved all existing attendance, server synchronization, pairing, BLE, Wi-Fi/Hotspot, GPS, and biometric flows.
# 1.9.23 — Reliable employee-phone discovery and offline presence proof

- Store discovery is now self-healing: BLE and LAN listeners restart automatically while linked employees exist.
- The compact Store dashboard refreshes connected-device state every five seconds.
- Employee BLE/LAN advertising now has one foreground-service owner, avoiding duplicate advertiser failures on Motorola/Xiaomi/Oppo and other OEM devices.
- The employee foreground service retries presence channels after Bluetooth or network changes.
- Signed local proof-of-presence requests work on a shared Wi-Fi/hotspot without Internet.
- Presence challenge proofs are tagged separately and no longer create an accidental attendance record.


## 1.9.38 pre-build
- Final phone attendance cleanup: QR replay protection and local signed proof; no passive-proximity attendance.
- Active UI methods limited to face, voice, password, Android phone biometric, direct QR.
- UI themes retained and isolated from attendance data.

## 1.9.45 REAL CONNECTION FIX
- Store no longer counts BLE/LAN discovery as a connected employee.
- BLE connected state remains gated by signed GATT Heartbeat/ACK and expires automatically.
- LAN now uses a two-way authenticated handshake: Employee presence -> Store ACK -> Employee confirmation. Store only promotes LAN to connected after the confirmation matches the pending proof token.
- Attendance reminder logic now uses only verified BLE ACK or verified LAN ACK.
- Presence requests avoid duplicate BLE/LAN dispatch: direct GATT is preferred; LAN is used only when a recent LAN ACK is confirmed.
- CentralServerClient records actual HTTP success/error status and time through ServerDiagnostics; credentials alone are no longer presented as a live server connection.
- Store and Employee connection diagnostics distinguish discovered/nearby from ACK-verified connection and show BLE/LAN/Server/GPS states.
- GPS remains confirmation/context only; it does not establish a connection or attendance by itself.


## 1.9.46 REAL CONNECTION HARDENING
- BLE is now three-step authenticated on Employee: signed PING -> signed ACK -> signed ACK-confirm. Employee does not report connected on PING alone.
- Store reports connected only after verifying signed ACK; then sends ACK-confirm to Employee.
- Employee readiness no longer calls plain Wi-Fi association a store connection; it requires fresh LAN ACK.
- Server diagnostics retain the most recent error details for display after recovery while freshness still depends on the latest successful HTTP state.
- No UI redesign; only connection truth/diagnostics hardening.
- Employee GATT now keeps PING as a pending session only; CONFIG/CHALLENGE are accepted only after ACK-confirm, preventing pre-confirm application traffic.
- Added source-level JUnit coverage for BLE ACK-confirm and ServerDiagnostics recovery/history semantics.


## 1.9.48 ATOMIC PAIRING FINALIZATION
- Fixed the field failure where a pairing GATT/LAN session completed and then disconnected while the Store's persistent scanner remained paused until the dialog was manually closed.
- Pairing completion now immediately stops the temporary pairing beacon and resumes persistent BLE/LAN discovery.
- Store marks companion-linked only from verified pairing ACK, persistent BLE ACK, persistent LAN ACK, or server link ACK.
- Added AP5Q QR envelope binding the QR to the active 8-character Store pairing session. QR now performs a return ACK through Bluetooth/LAN instead of only copying the provision to the employee phone.
- Added Store polling for actual server employee-link status while the pairing ticket is open.
- Legacy AP3P/AP4P remains supported.


## 1.9.50 — connection startup + server heartbeat recovery
- Employee PresenceService no longer reports a ready link until its foreground-service heartbeat is alive.
- BLE advertising is connectable again so Store GATT can open the authenticated PING/ACK channel.
- Employee starts the GATT service before exposing connectable advertising.
- BLE advertiser no longer remains in a false `running` state when Android permissions are missing; it retries after permission/service recovery.
- Store Bluetooth permission result now uses the scanner's real request code (`8111`).
- Store polls authenticated employee server `lastSeenAt` and treats a fresh server heartbeat as a real third transport, alongside BLE and LAN.
- Server presence is considered live only while the employee heartbeat is fresh; stale links are removed automatically.


## 1.9.61 — Store Manager Hub + Home Shortcuts
- Replaced the broken manager AlertDialog message/list combination with a custom scrollable management hub, so all manager actions are visible and clickable.
- Added manager-selected home shortcuts for every management function in the hub.
- Shortcuts are persisted locally and can be added/removed from a multi-choice picker.
- Home shortcuts retain store-owner PIN protection.
- 1.9.58 connection core lock files are unchanged.
