package com.attendpro.store

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.EditText
import android.widget.NumberPicker
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

    fun pickTime(activity: Activity, target: EditText, defaultHour: Int = 8, defaultMinute: Int = 0) {
        val parts = target.text?.toString()?.split(":").orEmpty()
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: defaultHour.coerceIn(0, 23)
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: defaultMinute.coerceIn(0, 59)
        TimePickerDialog(activity, { _, h, m -> target.setText(String.format(Locale.US, "%02d:%02d", h, m)) }, hour, minute, true).show()
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
            .setPositiveButton("اختيار") { _, _ -> target.setText(picker.value.toString()) }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
