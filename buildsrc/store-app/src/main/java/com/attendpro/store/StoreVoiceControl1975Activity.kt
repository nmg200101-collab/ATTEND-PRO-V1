package com.attendpro.store

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.attendpro.core.AppLanguage
import com.attendpro.core.StoreRepository
import com.attendpro.core.UiKit

class StoreVoiceControl1975Activity : Activity() {
    private lateinit var repo: StoreRepository
    private val p by lazy { UiKit.palette(this) }
    private fun t(ar: String, en: String) = AppLanguage.text(this, ar, en)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = StoreRepository(this)
        showPage()
    }

    private fun showPage() {
        window.statusBarColor = p.bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (AppLanguage.isEnglish(this@StoreVoiceControl1975Activity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(UiKit.dp(this@StoreVoiceControl1975Activity, 16), UiKit.dp(this@StoreVoiceControl1975Activity, 18), UiKit.dp(this@StoreVoiceControl1975Activity, 16), UiKit.dp(this@StoreVoiceControl1975Activity, 30))
            setBackgroundColor(p.bg)
        }
        val hero = UiKit.heroCard(this, p)
        hero.addView(UiKit.title(this, p, t("الصوت والتنبيهات", "Voice and alerts"), 24f).apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.WHITE) })
        hero.addView(UiKit.subtitle(this, p, t("تحكم عام + إعداد مستقل لكل موظف. هذه الإعدادات لا تغيّر التحقق ببصمة الصوت ولا الاقتران.", "Global controls plus per-employee settings. These settings do not change voice verification or pairing.")).apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.argb(225,255,255,255)) })
        root.addView(hero)

        val general = UiKit.card(this, p)
        general.addView(UiKit.sectionLabel(this, p, t("التحكم العام في جهاز المحل", "Store device global controls")))
        val attendance = check(t("نطق تسجيل الحضور والانصراف", "Announce check-in and check-out"), repo.storeVoiceAttendanceEnabled)
        val request = check(t("نطق إرسال طلب إثبات الوجود", "Announce presence-proof requests"), repo.storeVoiceRequestEnabled)
        val late = check(t("نطق تنبيه التأخير", "Announce late alerts"), repo.storeVoiceLateEnabled)
        val missing = check(t("نطق عدم إثبات الوجود", "Announce missing presence proof"), repo.storeVoiceMissingProofEnabled)
        val enabled = check(t("السماح بالنطق في جهاز المحل", "Enable speech on the Store device"), repo.attendanceVoiceAnnouncementEnabled)
        listOf(enabled, attendance, request, late, missing).forEach { general.addView(it) }
        root.addView(general)

        val employeeGeneral = UiKit.card(this, p)
        employeeGeneral.addView(UiKit.sectionLabel(this, p, "التحكم العام في هاتف الموظف"))
        val employeePrompts = check("السماح بالتنبيهات الصوتية المعتادة على هاتف الموظف", repo.employeeVoicePromptsEnabled)
        val messageVoice = check("اجعل الرسائل الجديدة صوتية افتراضيًا", repo.employeeMessageVoiceDefaultEnabled)
        employeeGeneral.addView(employeePrompts)
        employeeGeneral.addView(messageVoice)
        employeeGeneral.addView(UiKit.subtitle(this, p, "يمكن تجاوز الإعداد العام لكل موظف من القسم التالي: يرث الإعداد العام، صوت + إشعار، إشعار فقط، أو صامت."))
        root.addView(employeeGeneral)

        val tuning = UiKit.card(this, p)
        tuning.addView(UiKit.sectionLabel(this, p, "الصوت والعبارات"))
        val rate = numberField("سرعة النطق % (50–150)", repo.voiceRatePercent)
        val volume = numberField("مستوى الصوت % (0–100)", repo.voiceVolumePercent)
        val sentText = UiKit.field(this, p, "عبارة إرسال طلب الإثبات").apply { setText(repo.voiceRequestSentText) }
        val lateText = UiKit.field(this, p, "عبارة التأخير").apply { setText(repo.voiceLateText) }
        val missingText = UiKit.field(this, p, "عبارة عدم الإثبات").apply { setText(repo.voiceMissingProofText) }
        listOf(rate, volume, sentText, lateText, missingText).forEach { tuning.addView(it) }
        tuning.addView(UiKit.button(this, p, "تجربة النطق", false).apply {
            setOnClickListener {
                repo.attendanceVoiceAnnouncementEnabled = true
                StoreVoiceAnnouncer(this@StoreVoiceControl1975Activity, repo).apply { speak("اختبار الصوت في إدارة المحل"); android.os.Handler(mainLooper).postDelayed({ shutdown() }, 3500) }
            }
        })
        root.addView(tuning)

        val perEmployee = UiKit.card(this, p)
        perEmployee.addView(UiKit.sectionLabel(this, p, "إعداد صوتي خاص لكل موظف"))
        val active = repo.employees().filter { it.active }
        if (active.isEmpty()) perEmployee.addView(UiKit.subtitle(this, p, "لا يوجد موظفون نشطون حاليًا."))
        active.forEach { employee ->
            val label = TextView(this).apply {
                text = "${employee.displayName} • ${voiceModeArabic(repo.employeeMessageVoiceMode(employee.employeeId))}"
                textSize = 15f; gravity = Gravity.RIGHT; setTextColor(p.text)
                setPadding(0, UiKit.dp(this@StoreVoiceControl1975Activity, 6), 0, UiKit.dp(this@StoreVoiceControl1975Activity, 3))
            }
            perEmployee.addView(label)
            perEmployee.addView(UiKit.button(this, p, "تخصيص ${employee.displayName}", false).apply {
                setOnClickListener { chooseEmployeeMode(employee.employeeId, employee.displayName) }
            })
        }
        root.addView(perEmployee)

        val save = UiKit.card(this, p)
        save.addView(UiKit.button(this, p, "حفظ إعدادات الصوت").apply {
            setOnClickListener {
                repo.attendanceVoiceAnnouncementEnabled = enabled.isChecked
                repo.storeVoiceAttendanceEnabled = attendance.isChecked
                repo.storeVoiceRequestEnabled = request.isChecked
                repo.storeVoiceLateEnabled = late.isChecked
                repo.storeVoiceMissingProofEnabled = missing.isChecked
                repo.employeeVoicePromptsEnabled = employeePrompts.isChecked
                repo.employeeMessageVoiceDefaultEnabled = messageVoice.isChecked
                repo.voiceRatePercent = rate.text.toString().toIntOrNull() ?: repo.voiceRatePercent
                repo.voiceVolumePercent = volume.text.toString().toIntOrNull() ?: repo.voiceVolumePercent
                repo.voiceRequestSentText = sentText.text.toString().trim()
                repo.voiceLateText = lateText.text.toString().trim()
                repo.voiceMissingProofText = missingText.text.toString().trim()
                AlertDialog.Builder(this@StoreVoiceControl1975Activity).setTitle("تم الحفظ").setMessage("تم تحديث إعدادات النطق والتنبيهات الصوتية دون تغيير إعدادات التحقق الصوتي أو الارتباط.").setPositiveButton("حسنًا", null).show()
            }
        })
        save.addView(UiKit.button(this, p, "رجوع", false).apply { setOnClickListener { finish() } })
        root.addView(save)
        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
    }

    private fun check(text: String, value: Boolean) = CheckBox(this).apply {
        this.text = text; isChecked = value; textSize = 15f; gravity = Gravity.RIGHT
        layoutDirection = View.LAYOUT_DIRECTION_RTL; setTextColor(p.text)
    }

    private fun numberField(hint: String, value: Int) = UiKit.field(this, p, hint, true).apply {
        inputType = InputType.TYPE_CLASS_NUMBER; setText(value.toString())
    }

    private fun chooseEmployeeMode(employeeId: String, name: String) {
        val values = arrayOf("INHERIT", "VOICE_NOTIFICATION", "NOTIFICATION_ONLY", "SILENT")
        val labels = arrayOf("يتبع الإعداد العام", "صوت + إشعار", "إشعار فقط", "صامت")
        val current = values.indexOf(repo.employeeMessageVoiceMode(employeeId)).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("تنبيهات $name").setSingleChoiceItems(labels, current) { dialog, which ->
            repo.setEmployeeMessageVoiceMode(employeeId, values[which]); dialog.dismiss(); showPage()
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun voiceModeArabic(value: String) = when (value) {
        "VOICE_NOTIFICATION" -> "صوت + إشعار"
        "NOTIFICATION_ONLY" -> "إشعار فقط"
        "SILENT" -> "صامت"
        else -> "يتبع العام"
    }
}
