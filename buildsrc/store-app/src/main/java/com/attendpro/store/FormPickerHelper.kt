package com.attendpro.store

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.EditText
import android.widget.NumberPicker
import com.attendpro.core.AppLanguage
import com.attendpro.core.ShiftTimeCodec
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object FormPickerHelper {
    fun pickDate(activity: Activity, target: EditText) {
        val c = Calendar.getInstance()
        val existing = target.text?.toString()?.trim().orEmpty()
        if (existing.isNotBlank()) {
            runCatching {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(existing)
                if (date != null) c.time = date
            }
        }
        DatePickerDialog(activity, { _, y, m, d ->
            target.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    fun selectedTime(target: EditText, defaultHour: Int = 8, defaultMinute: Int = 0): ShiftTimeCodec.ClockTime {
        val tagged = target.tag as? ShiftTimeCodec.ClockTime
        if (tagged != null) return tagged
        return ShiftTimeCodec.parseLegacy24(target.text?.toString().orEmpty())
            ?: ShiftTimeCodec.ClockTime(defaultHour.coerceIn(0, 23), defaultMinute.coerceIn(0, 59))
    }

    fun setTime(target: EditText, hour24: Int, minute: Int) {
        val value = ShiftTimeCodec.ClockTime(hour24, minute)
        target.tag = value
        target.setText(ShiftTimeCodec.format(value.hour24, value.minute, Locale.getDefault()))
    }

    fun pickTime(activity: Activity, target: EditText, defaultHour: Int = 8, defaultMinute: Int = 0) {
        val current = selectedTime(target, defaultHour, defaultMinute)
        TimePickerDialog(activity, { _, h, m -> setTime(target, h, m) },
            current.hour24, current.minute, false).show()
    }

    fun pickNumber(activity: Activity, target: EditText, min: Int, max: Int, title: String) {
        val picker = NumberPicker(activity).apply {
            minValue = min
            maxValue = max
            wrapSelectorWheel = true
            value = target.text.toString().toIntOrNull()?.coerceIn(min, max) ?: min
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(picker)
            .setPositiveButton(AppLanguage.text(activity, "اختيار", "Select")) { _, _ -> target.setText(picker.value.toString()) }
            .setNegativeButton(AppLanguage.text(activity, "إلغاء", "Cancel"), null)
            .show()
    }
}
