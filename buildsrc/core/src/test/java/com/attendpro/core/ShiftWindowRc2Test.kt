package com.attendpro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ShiftWindowRc2Test {
    private fun at(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 11, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test fun normalShiftResolvesSameDay() {
        val w = ShiftWindow.resolve(at(12, 0), 8, 0, 22, 0)
        assertEquals(14L * 60L * 60L * 1000L, w.end - w.start)
        assertTrue(at(12, 0) in w.start..w.end)
    }

    @Test fun overnightShiftAtLateEveningEndsNextDay() {
        val w = ShiftWindow.resolve(at(23, 0), 22, 0, 6, 0)
        assertEquals(8L * 60L * 60L * 1000L, w.end - w.start)
        assertTrue(at(23, 0) in w.start..w.end)
    }

    @Test fun overnightShiftAtEarlyMorningStartsPreviousDay() {
        val w = ShiftWindow.resolve(at(3, 0), 22, 0, 6, 0)
        assertEquals(8L * 60L * 60L * 1000L, w.end - w.start)
        assertTrue(at(3, 0) in w.start..w.end)
    }
}
