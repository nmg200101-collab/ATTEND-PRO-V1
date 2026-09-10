package com.attendpro.foundation

import com.attendpro.foundation.attendance.AttendanceEngine
import com.attendpro.foundation.attendance.AttendanceOutcome
import com.attendpro.foundation.attendance.AttendanceRejection
import com.attendpro.foundation.attendance.AttendanceRequest
import com.attendpro.foundation.domain.AttendanceAction
import com.attendpro.foundation.domain.AttendanceState
import com.attendpro.foundation.domain.ShiftPolicy
import com.attendpro.foundation.domain.VerificationMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AttendanceEngineTest {
    private val utc = ZoneId.of("UTC")
    private fun at(day: Int, hour: Int, minute: Int) = LocalDateTime.of(2026, 9, day, hour, minute)
        .atZone(utc).toInstant().toEpochMilli()

    @Test fun overnightCheckoutBelongsToPreviousShift() {
        val result = AttendanceEngine().decide(at(11, 5, 30), 22, 0, 6, 0, AttendanceAction.CHECK_OUT, zoneId = utc)
        assertEquals(AttendanceState.EARLY_DEPARTURE, result.state)
        assertEquals(at(10, 22, 0), result.shiftStart)
        assertEquals(at(11, 6, 0), result.shiftEnd)
    }

    @Test fun gracePeriodPreventsFalseLate() {
        val result = AttendanceEngine().decide(at(10, 8, 7), 8, 0, 17, 0, AttendanceAction.CHECK_IN, 10, zoneId = utc)
        assertEquals(AttendanceState.ON_TIME, result.state)
    }

    @Test fun checkInAfterGraceIsLate() {
        val result = AttendanceEngine().decide(at(10, 8, 11), 8, 0, 17, 0, AttendanceAction.CHECK_IN, 10, zoneId = utc)
        assertEquals(AttendanceState.LATE, result.state)
    }

    @Test fun checkoutTailKeepsMorningCheckoutOnPreviousNightShift() {
        val result = AttendanceEngine().decide(
            at(11, 7, 30), 22, 0, 6, 0, AttendanceAction.CHECK_OUT,
            checkoutTailMinutes = 120, zoneId = utc
        )
        assertEquals(at(10, 22, 0), result.shiftStart)
        assertEquals(AttendanceState.COMPLETED, result.state)
    }

    @Test fun presenceProofHasExplicitState() {
        val result = AttendanceEngine().decide(at(10, 12, 0), 8, 0, 17, 0, AttendanceAction.PRESENCE_PROOF, zoneId = utc)
        assertEquals(AttendanceState.PROOF_ACCEPTED, result.state)
    }

    @Test fun ownerSelectedMethodIsEnforcedBeforeRecording() {
        val outcome = AttendanceEngine().evaluate(
            AttendanceRequest(
                reference = at(10, 8, 0),
                action = AttendanceAction.CHECK_IN,
                method = VerificationMethod.PIN,
                verified = true,
                employeeEnabled = true,
                allowedMethods = setOf(VerificationMethod.DEVICE_BIOMETRIC),
                shift = ShiftPolicy(8, 0, 17, 0, zoneId = utc.id)
            )
        )
        assertEquals(
            AttendanceRejection.METHOD_NOT_ALLOWED,
            (outcome as AttendanceOutcome.Rejected).reason
        )
    }

    @Test fun unverifiedProofIsRejected() {
        val outcome = AttendanceEngine().evaluate(
            AttendanceRequest(
                reference = at(10, 8, 0),
                action = AttendanceAction.PRESENCE_PROOF,
                method = VerificationMethod.GPS_GEOFENCE,
                verified = false,
                employeeEnabled = true,
                allowedMethods = emptySet(),
                shift = ShiftPolicy(8, 0, 17, 0, zoneId = utc.id)
            )
        )
        assertTrue(outcome is AttendanceOutcome.Rejected)
        assertEquals(AttendanceRejection.VERIFICATION_REQUIRED, (outcome as AttendanceOutcome.Rejected).reason)
    }
}
