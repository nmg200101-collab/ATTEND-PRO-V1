package com.attendpro.store

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.attendpro.core.AttendanceAction
import com.attendpro.core.PairedEmployee
import com.attendpro.core.ShiftWindow
import com.attendpro.core.StoreRepository

/**
 * Keeps one inexpensive, inexact wake-up alarm for the next late-attendance check.
 * No exact-alarm permission is required. Android may shift the wake-up slightly to
 * preserve battery, but the rule remains local and does not require Internet.
 */
object LateAlertScheduler {
    const val ACTION_LATE_TICK = "com.attendpro.store.ACTION_LATE_TICK"
    private const val REQUEST_CODE = 49390
    private const val MIN_DELAY = 60_000L
    internal const val ALERT_TAIL_MILLIS = 2L * 60L * 60_000L

    fun sync(context: Context, repo: StoreRepository, now: Long = System.currentTimeMillis()) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        if (!repo.smartLateAlertsEnabled || repo.employees().none { it.active && it.lateAlertEnabled }) {
            alarm.cancel(pi)
            return
        }
        val next = nextWake(repo, now) ?: run { alarm.cancel(pi); return }
        val at = next.coerceAtLeast(now + MIN_DELAY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            @Suppress("DEPRECATION") alarm.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, LateAlertReceiver::class.java).setAction(ACTION_LATE_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    internal fun nextWake(repo: StoreRepository, now: Long): Long? = repo.employees().asSequence()
        .filter { it.active && it.lateAlertEnabled }
        .mapNotNull { employee ->
            val window = windowFor(employee, repo, now)
            val next = ShiftWindow.next(window)
            val checkedIn = repo.events().any { event ->
                event.employeeId.equals(employee.employeeId, true) &&
                    event.action == AttendanceAction.CHECK_IN &&
                    event.timestampEpochMillis in window.proofCutoff()..now
            }
            if (checkedIn) return@mapNotNull dueForWindow(employee, repo, next)

            val due = dueForWindow(employee, repo, window)
            val end = window.end + ALERT_TAIL_MILLIS
            val repeatMs = (employee.lateRepeatMinutes.takeIf { it >= 5 } ?: repo.lateAlertRepeatMinutes)
                .coerceIn(5, 180) * 60_000L
            when {
                now < due -> due
                now <= end -> (now + repeatMs).coerceAtMost(end)
                else -> dueForWindow(employee, repo, next)
            }
        }.minOrNull()

    internal fun windowFor(e: PairedEmployee, repo: StoreRepository, reference: Long): ShiftWindow.Window = ShiftWindow.resolve(
        reference,
        if (e.useCustomShift) e.shiftStartHour else repo.shiftHour,
        if (e.useCustomShift) e.shiftStartMinute else repo.shiftMinute,
        if (e.useCustomShift) e.shiftEndHour else repo.shiftEndHour,
        if (e.useCustomShift) e.shiftEndMinute else repo.shiftEndMinute,
        ALERT_TAIL_MILLIS
    )

    internal fun dueForWindow(e: PairedEmployee, repo: StoreRepository, window: ShiftWindow.Window): Long =
        window.start + ((e.lateGraceMinutes.takeIf { it >= 0 } ?: repo.graceMinutes) +
            (e.lateFirstAlertDelayMinutes.takeIf { it >= 0 } ?: repo.lateAlertDelayMinutes)) * 60_000L
}
