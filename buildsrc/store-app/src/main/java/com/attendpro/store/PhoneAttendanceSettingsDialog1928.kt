package com.attendpro.store

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.NumberPicker
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import com.attendpro.core.StoreRepository
import com.attendpro.core.UiKit

object PhoneAttendanceSettingsDialog1928 {
    fun show(activity: Activity, repo: StoreRepository) {
        val p = UiKit.palette(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(activity, 14), UiKit.dp(activity, 10), UiKit.dp(activity, 14), UiKit.dp(activity, 8))
        }
        root.addView(UiKit.title(activity, p, "طرق الحضور المعتمدة", 20f).apply { gravity = Gravity.CENTER })
        root.addView(UiKit.subtitle(activity, p, "طرق الحضور منفصلة عن التعرف على الهاتف. Bluetooth وWi‑Fi للاتصال المباشر، وGPS يسجل القرب والوقت فقط ولا يثبت الحضور.").apply {
            gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(activity, 5), 0, UiKit.dp(activity, 12))
        })

        fun option(icon: String, title: String, subtitle: String, checked: Boolean): CheckBox {
            val box = CheckBox(activity).apply {
                text = "$icon  $title\n$subtitle"
                textSize = 14.6f
                isChecked = checked
                gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setTextColor(p.text)
                buttonTintList = android.content.res.ColorStateList.valueOf(p.primary)
                background = UiKit.round(p.surface2, p.buttonRadius, activity, p.divider)
                setPadding(UiKit.dp(activity, 12), UiKit.dp(activity, 10), UiKit.dp(activity, 12), UiKit.dp(activity, 10))
            }
            box.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = UiKit.dp(activity, 8)
            }
            root.addView(box)
            return box
        }

        val face = option("◉", "التعرف بالوجه", "كاميرا جهاز المحل + جودة + فحص حيوية", repo.allowFaceEnrollment)
        val voice = option("◖", "بصمة الصوت", "عدة عينات + النبرة + العبارة + تحدٍ متغير", repo.allowVoiceVerification)
        val password = option("▣", "كلمة المرور", "بديل احتياطي آمن بكلمة مرور الموظف", repo.allowPasswordFallback)
        val phone = option("◎", "بصمة/وجه هاتف الموظف", "التحقق البيومتري الأصلي في Android على الهاتف المرتبط", repo.allowEmployeeCompanion && repo.requirePhoneBiometric)
        val qr = option("▦", "QR مباشر", "رمز مؤقت وموقّع يفتح طلب الموافقة في هاتف الموظف", repo.allowQrAttendance)

        val voiceAnnounce = option("◖", "الناطق الصوتي", "يذكر اسم الموظف عند إثبات الحضور أو الانصراف", repo.attendanceVoiceAnnouncementEnabled)
        val employeeVoice = option("◖", "طلب صوتي في هاتف الموظف", "ينادي اسم الموظف ويطلب منه إثبات الحضور عند وصول طلب BLE/Wi‑Fi", repo.employeeVoicePromptsEnabled)
        val geoArrival = option("⌖", "التعرّف على الهاتف عبر GPS", "يسجل داخل/قريب/خارج النطاق مع المسافة والدقة والوقت، ولا يسجل حضورًا", repo.gpsRecognitionEnabled && repo.employeeGeoArrivalAlertsEnabled)
        val gpsOwnerNotify = option("⌖", "تنبيه صاحب المحل عند التعرف عبر GPS", "يظهر تنبيه عند انتقال الهاتف إلى داخل/قرب نطاق المحل إذا وصلت القراءة للخادم أو للمحل", repo.gpsNotifyOwnerEnabled)
        val smartLate = option("◷", "التنبيه الذكي للتأخر", "بعد وقت الدوام + السماح + المهلة المحددة يصدر تنبيهًا باسم الموظف", repo.smartLateAlertsEnabled)
        val serverLate = option("⇄", "تنبيه الموظف عبر الخادم عند التأخر", "إذا لم يكن الهاتف متصلًا محليًا يرسل الخادم طلب إثبات حضور يظهر كإشعار في هاتف الموظف", repo.lateNotifyEmployeeViaServer)
        val autoCall = option("☎", "اتصال تلقائي عند التأخر", "اختياري؛ يبدأ مكالمة مرة واحدة في اليوم بعد التأخر إذا كان رقم الموظف محفوظًا", repo.lateAutoCallEnabled)

        fun pickerCard(title: String, value: Int, min: Int, max: Int): Pair<LinearLayout, NumberPicker> {
            val card = UiKit.card(activity, p, 9)
            card.addView(UiKit.subtitle(activity, p, title))
            val picker = NumberPicker(activity).apply { minValue = min; maxValue = max; this.value = value.coerceIn(min, max); wrapSelectorWheel = true }
            card.addView(picker)
            root.addView(card)
            return card to picker
        }
        val delayPicker = pickerCard("مهلة إضافية بعد فترة السماح قبل اعتبار الموظف متأخرًا — بالدقائق", repo.lateAlertDelayMinutes, 0, 60).second
        val repeatPicker = pickerCard("تكرار تنبيه التأخر — بالدقائق", repo.lateAlertRepeatMinutes, 5, 120).second

        val protection = UiKit.card(activity, p, 11)
        protection.addView(UiKit.sectionLabel(activity, p, "طبقات الحماية الخلفية"))
        protection.addView(UiKit.subtitle(activity, p, "Bluetooth/Wi‑Fi للاتصال المباشر الموثق • GPS للمراقبة فقط • الخادم للتنبيهات والاحتياط عند عدم وجود قناة محلية."))
        root.addView(protection)

        AlertDialog.Builder(activity)
            .setTitle("الحضور والتحقق")
            .setView(ScrollView(activity).apply { addView(root) })
            .setPositiveButton("حفظ") { _, _ ->
                repo.allowFaceEnrollment = face.isChecked
                repo.allowVoiceVerification = voice.isChecked
                repo.allowPasswordFallback = password.isChecked
                repo.allowPatternFallback = false
                repo.allowPinFallback = false
                repo.allowQrAttendance = qr.isChecked
                repo.allowEmployeeCompanion = phone.isChecked || qr.isChecked
                repo.requirePhoneBiometric = phone.isChecked
                repo.attendanceVoiceAnnouncementEnabled = voiceAnnounce.isChecked
                repo.employeeVoicePromptsEnabled = employeeVoice.isChecked
                repo.employeeGeoArrivalAlertsEnabled = geoArrival.isChecked
                repo.gpsRecognitionEnabled = geoArrival.isChecked
                repo.gpsNotifyOwnerEnabled = gpsOwnerNotify.isChecked
                repo.smartLateAlertsEnabled = smartLate.isChecked
                repo.lateNotifyEmployeeViaServer = serverLate.isChecked
                repo.lateAutoCallEnabled = autoCall.isChecked
                repo.lateAlertDelayMinutes = delayPicker.value
                repo.lateAlertRepeatMinutes = repeatPicker.value
                LateAlertScheduler.sync(activity, repo)
                val permissions = mutableListOf<String>()
                if (autoCall.isChecked && activity.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) permissions += Manifest.permission.CALL_PHONE
                if (Build.VERSION.SDK_INT >= 33 && smartLate.isChecked && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissions += Manifest.permission.POST_NOTIFICATIONS
                if (permissions.isNotEmpty()) activity.requestPermissions(permissions.toTypedArray(), 6311)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
