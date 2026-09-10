package com.attendpro.foundation.attendance

import com.attendpro.foundation.domain.AttendanceAction
import com.attendpro.foundation.domain.AttendanceState
import com.attendpro.foundation.domain.ShiftPolicy
import com.attendpro.foundation.domain.VerificationMethod
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class AttendanceDecision(
    val action: AttendanceAction,
    val state: AttendanceState,
    val shiftStart: Long,
    val shiftEnd: Long
)

enum class AttendanceRejection {
    EMPLOYEE_DISABLED,
    METHOD_NOT_ALLOWED,
    VERIFICATION_REQUIRED
}

sealed interface AttendanceOutcome {
    data class Accepted(val decision: AttendanceDecision) : AttendanceOutcome
    data class Rejected(val reason: AttendanceRejection) : AttendanceOutcome
}

data class AttendanceRequest(
    val reference: Long,
    val action: AttendanceAction,
    val method: VerificationMethod,
    val verified: Boolean,
    val employeeEnabled: Boolean,
    val allowedMethods: Set<VerificationMethod>,
    val shift: ShiftPolicy
)

/** Deterministic domain service. It has no Activity, UI, Bluetooth, database, or network access. */
class AttendanceEngine {
    fun evaluate(request: AttendanceRequest): AttendanceOutcome {
        if (!request.employeeEnabled) return AttendanceOutcome.Rejected(AttendanceRejection.EMPLOYEE_DISABLED)
        if (request.allowedMethods.isNotEmpty() && request.method !in request.allowedMethods) {
            return AttendanceOutcome.Rejected(AttendanceRejection.METHOD_NOT_ALLOWED)
        }
        if (!request.verified) return AttendanceOutcome.Rejected(AttendanceRejection.VERIFICATION_REQUIRED)

        return AttendanceOutcome.Accepted(decide(request.reference, request.action, request.shift))
    }

    fun decide(reference: Long, action: AttendanceAction, policy: ShiftPolicy): AttendanceDecision {
        val tail = when (action) {
            AttendanceAction.CHECK_IN -> 0
            AttendanceAction.CHECK_OUT, AttendanceAction.PRESENCE_PROOF -> policy.checkoutTailMinutes
        }
        val window = resolveShift(reference, policy, tail)
        val graceMillis = policy.graceMinutes.toLong() * MINUTE_MILLIS
        val state = when (action) {
            AttendanceAction.CHECK_IN -> if (reference > window.first + graceMillis) AttendanceState.LATE else AttendanceState.ON_TIME
            AttendanceAction.CHECK_OUT -> if (reference < window.second) AttendanceState.EARLY_DEPARTURE else AttendanceState.COMPLETED
            AttendanceAction.PRESENCE_PROOF -> AttendanceState.PROOF_ACCEPTED
        }
        return AttendanceDecision(action, state, window.first, window.second)
    }

    /** Compatibility-shaped entry point for incremental adapters around the V1 screens. */
    fun decide(
        reference: Long,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        action: AttendanceAction,
        graceMinutes: Int = 0,
        checkoutTailMinutes: Int = 120,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): AttendanceDecision = decide(
        reference,
        action,
        ShiftPolicy(
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            graceMinutes = graceMinutes.coerceIn(0, 1_440),
            checkoutTailMinutes = checkoutTailMinutes.coerceIn(0, 1_440),
            zoneId = zoneId.id
        )
    )

    private fun resolveShift(reference: Long, policy: ShiftPolicy, tailMinutes: Int): Pair<Long, Long> {
        val zone = ZoneId.of(policy.zoneId)
        val observed = Instant.ofEpochMilli(reference).atZone(zone)
        val date = observed.toLocalDate()
        val startTime = LocalTime.of(policy.startHour, policy.startMinute)
        val endTime = LocalTime.of(policy.endHour, policy.endMinute)
        val overnight = !endTime.isAfter(startTime)

        val start: ZonedDateTime
        val end: ZonedDateTime
        if (!overnight) {
            start = at(date, startTime, zone)
            end = at(date, endTime, zone)
        } else {
            val endToday = at(date, endTime, zone)
            val previousShiftCutoff = endToday.plusMinutes(tailMinutes.toLong())
            if (!observed.isAfter(previousShiftCutoff)) {
                start = at(date.minusDays(1), startTime, zone)
                end = endToday
            } else {
                start = at(date, startTime, zone)
                end = at(date.plusDays(1), endTime, zone)
            }
        }
        return start.toInstant().toEpochMilli() to end.toInstant().toEpochMilli()
    }

    private fun at(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime = date.atTime(time).atZone(zone)

    private companion object { const val MINUTE_MILLIS = 60_000L }
}
