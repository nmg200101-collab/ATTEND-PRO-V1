package com.attendpro.core

import java.util.Calendar

/**
 * Resolves the operational shift that is current/relevant for [reference].
 * Overnight shifts are anchored to the date on which the shift starts, not midnight.
 */
object ShiftWindow {
    const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L
    const val EARLY_PROOF_ALLOWANCE_MILLIS: Long = 2L * 60L * 60L * 1000L

    data class Window(val start: Long, val end: Long) {
        val overnight: Boolean get() = end - start >= DAY_MILLIS / 2
        fun proofCutoff(): Long = start - EARLY_PROOF_ALLOWANCE_MILLIS
    }

    fun resolve(
        reference: Long,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        tailMillis: Long = 0L
    ): Window {
        val sh = startHour.coerceIn(0, 23)
        val sm = startMinute.coerceIn(0, 59)
        val eh = endHour.coerceIn(0, 23)
        val em = endMinute.coerceIn(0, 59)
        val startMinutes = sh * 60 + sm
        val endMinutes = eh * 60 + em

        var start = clockOnReferenceDate(reference, sh, sm)
        var end = clockOnReferenceDate(reference, eh, em)
        if (endMinutes <= startMinutes) {
            // Early morning (plus the permitted alert tail) belongs to yesterday's shift.
            if (reference <= end + tailMillis) {
                start -= DAY_MILLIS
            } else {
                end += DAY_MILLIS
            }
        }
        return Window(start, end)
    }

    fun next(window: Window): Window = Window(window.start + DAY_MILLIS, window.end + DAY_MILLIS)

    private fun clockOnReferenceDate(reference: Long, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        timeInMillis = reference
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
