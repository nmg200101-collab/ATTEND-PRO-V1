package com.attendpro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class ShiftTimeCodecTest {
    @Test fun midnightAndNoonConvertCorrectly() {
        assertEquals(ShiftTimeCodec.ClockTime(0, 0), ShiftTimeCodec.from12Hour(12, 0, false))
        assertEquals(ShiftTimeCodec.ClockTime(12, 0), ShiftTimeCodec.from12Hour(12, 0, true))
    }

    @Test fun arabicAndEnglishFormattingIsTwelveHour() {
        assertEquals("8:00 ص", ShiftTimeCodec.format(8, 0, Locale("ar")))
        assertEquals("10:00 م", ShiftTimeCodec.format(22, 0, Locale("ar")))
        assertEquals("8:00 AM", ShiftTimeCodec.format(8, 0, Locale.ENGLISH))
        assertEquals("10:00 PM", ShiftTimeCodec.format(22, 0, Locale.ENGLISH))
    }

    @Test fun invalidTwelveHourValuesAreRejected() {
        assertNull(ShiftTimeCodec.from12Hour(0, 0, false))
        assertNull(ShiftTimeCodec.from12Hour(13, 0, true))
        assertNull(ShiftTimeCodec.from12Hour(8, 60, false))
    }

    @Test fun legacyTwentyFourHourValuesRemainReadable() {
        assertEquals(ShiftTimeCodec.ClockTime(22, 15), ShiftTimeCodec.parseLegacy24("22:15"))
        assertNull(ShiftTimeCodec.parseLegacy24("24:00"))
    }

    @Test fun formatsRangeWithAmPmAndDetectsOvernight() {
        assertEquals("8:00 ص - 10:00 م", ShiftTimeCodec.formatRange(8, 0, 22, 0, java.util.Locale("ar")))
        assertEquals("8:00 AM - 10:00 PM", ShiftTimeCodec.formatRange(8, 0, 22, 0, java.util.Locale.ENGLISH))
        assertTrue(ShiftTimeCodec.isOvernight(22, 0, 6, 0))
        assertFalse(ShiftTimeCodec.isOvernight(8, 0, 22, 0))
    }
}
