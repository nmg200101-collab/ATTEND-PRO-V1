from pathlib import Path

p = Path('buildsrc/store-app/src/main/java/com/attendpro/store/ReportReceiverActivity.kt')
s = p.read_text(encoding='utf-8')

s = s.replace(
'identity.addView(UiKit.button(this, p, t("عرض QR منح الصلاحية", "Show permission QR")).apply { setOnClickListener { showInviteQr() } })\n        identity.addView(UiKit.button(this, p, t("مسح QR ربط الخادم من جهاز المحل", "Scan server-link QR from Store device"), false).apply { setOnClickListener { scanRemoteGrant() } })',
'identity.addView(UiKit.subtitle(this, p, t("الخطوة 1: اعرض QR تعريف هذا الهاتف، ثم امسحه من جهاز المحل عبر: إدارة المحل ← التقارير والحماية ← هواتف استلام التقارير ← إضافة هاتف استلام عبر QR. بعد قبول الهاتف سيعرض جهاز المحل QR ربط خاصًا بهذا الهاتف.", "Step 1: Show this phone identity QR, then scan it on the Store device from Store Management → Reports & Security → Receiver phones → Add receiver phone by QR. After authorization, the Store device will show a server-link QR specifically for this phone.")))\n        identity.addView(UiKit.button(this, p, t("1 • عرض QR تعريف هذا الهاتف", "1 • Show this phone identity QR")).apply { setOnClickListener { showInviteQr() } })\n        identity.addView(UiKit.button(this, p, t("2 • مسح QR الربط الظاهر بعد قبول الهاتف في جهاز المحل", "2 • Scan the link QR shown after Store accepts this phone"), false).apply { setOnClickListener { scanRemoteGrant() } })'
)

s = s.replace(
'val grant = ReportProtocol.decodeRemoteGrant(raw, receiver.receiverId)\n        if (grant == null) {\n            info(t("QR غير صالح", "Invalid QR"), t("الرمز غير موجه لهذا الهاتف أو انتهت صلاحيته.", "This QR is not for this phone or has expired."))\n            return\n        }',
'if (ReportProtocol.decodeInvite(raw) != null) {\n            info(t("هذا QR التعريف وليس QR الربط", "This is the identity QR, not the link QR"), t("لا تمسح QR الذي يعرضه هاتف الاستلام هنا. أولًا امسحه من جهاز المحل عبر «إضافة هاتف استلام عبر QR». بعد قبول الهاتف سيظهر على جهاز المحل QR جديد؛ امسح ذلك الرمز الجديد هنا.", "Do not scan the Receiver phone identity QR here. First scan it on the Store device using Add receiver phone by QR. After the phone is accepted, a new QR appears on the Store device; scan that new QR here."))\n            return\n        }\n        val grant = ReportProtocol.decodeRemoteGrant(raw, receiver.receiverId)\n        if (grant == null) {\n            info(t("QR الربط غير صالح", "Invalid link QR"), t("استخدم QR الجديد الذي يظهر على جهاز المحل مباشرة بعد قبول هذا الهاتف. إذا مضت أكثر من 10 دقائق، أعد الخطوة 1 لإنشاء ربط جديد.", "Use the new QR shown on the Store device immediately after this phone is accepted. If more than 10 minutes passed, repeat step 1 to create a fresh link."))\n            return\n        }'
)

s = s.replace(
'"من هاتف المحل: إدارة المحل ← هواتف الاستلام والصلاحيات ← إضافة هاتف ← امسح هذا الرمز. الرمز صالح 10 دقائق.",\n            "On the Store phone: Store Management → Receiver phones and permissions → Add phone → scan this QR. The QR is valid for 10 minutes."',
'"هذا QR تعريف هاتف الاستلام (الخطوة 1)، وليس QR ربط الخادم. من جهاز المحل: إدارة المحل ← التقارير والحماية ← هواتف استلام التقارير ← إضافة هاتف استلام عبر QR، ثم امسح هذا الرمز. بعد القبول سيظهر QR ثانٍ على جهاز المحل. الرمز صالح 10 دقائق.",\n            "This is the Receiver phone identity QR (step 1), not the server-link QR. On the Store device: Store Management → Reports & Security → Receiver phones → Add receiver phone by QR, then scan this code. After acceptance, a second QR will appear on the Store device. Valid for 10 minutes."'
)

s = s.replace(
'AlertDialog.Builder(this).setTitle(t("منح صلاحية لهذا الهاتف", "Authorize this phone"))',
'AlertDialog.Builder(this).setTitle(t("1 • QR تعريف هاتف الاستلام", "1 • Receiver phone identity QR"))'
)

if '1 • عرض QR تعريف هذا الهاتف' not in s or 'هذا QR التعريف وليس QR الربط' not in s:
    raise SystemExit('receiver flow patch did not apply')
p.write_text(s, encoding='utf-8')
print('Report receiver two-step QR flow patched successfully')
