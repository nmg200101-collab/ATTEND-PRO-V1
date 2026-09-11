package com.attendpro.core

import java.util.Locale

object ShiftTimeCodec {
    data class ClockTime(val hour24: Int, val minute: Int) {
        init {
            require(hour24 in 0..23) { "hour24 must be 0..23" }
            require(minute in 0..59) { "minute must be 0..59" }
        }
    }

    fun from12Hour(hour12: Int, minute: Int, isPm: Boolean): ClockTime? {
        if (hour12 !in 1..12 || minute !in 0..59) return null
        val base = hour12 % 12
        return ClockTime(base + if (isPm) 12 else 0, minute)
    }

    fun format(hour24: Int, minute: Int, locale: Locale = Locale.getDefault()): String {
        val time = runCatching { ClockTime(hour24, minute) }.getOrNull() ?: return ""
        val isPm = time.hour24 >= 12
        val hour12 = when (val h = time.hour24 % 12) { 0 -> 12; else -> h }
        val marker = if (locale.language.equals("ar", ignoreCase = true)) {
            if (isPm) "م" else "ص"
        } else {
            if (isPm) "PM" else "AM"
        }
        return String.format(Locale.US, "%d:%02d %s", hour12, time.minute, marker)
    }

    fun parseLegacy24(value: String): ClockTime? {
        val parts = value.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        return runCatching { ClockTime(hour, minute) }.getOrNull()
    }

    fun same(a: ClockTime, b: ClockTime): Boolean =
        a.hour24 == b.hour24 && a.minute == b.minute
}
