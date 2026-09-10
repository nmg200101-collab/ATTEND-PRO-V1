package com.attendpro.employee

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.attendpro.core.EmployeeIdentityStore
import com.attendpro.core.ShiftWindow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local, inexact late-attendance wake-up. It does not require Internet or exact-alarm permission. */
object EmployeeLateAlertScheduler {
    const val ACTION_LATE_ALERT = "com.attendpro.employee.ACTION_LATE_ALERT"
    private const val REQUEST_CODE = 49391
    private const val MIN_DELAY = 60_000L
    internal const val ALERT_TAIL_MILLIS = 2L * 60L * 60_000L

    fun sync(context: Context, identity: EmployeeIdentityStore, now: Long = System.currentTimeMillis()) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        if (!identity.isConfigured || !identity.lateAlertEnabled) {
            alarm.cancel(pi)
            return
        }
        val next = nextWake(context, identity, now) ?: run { alarm.cancel(pi); return }
        val at = next.coerceAtLeast(now + MIN_DELAY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            @Suppress("DEPRECATION") alarm.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE,
        Intent(context, EmployeeLateAlertReceiver::class.java).setAction(ACTION_LATE_ALERT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    internal fun nextWake(context: Context, identity: EmployeeIdentityStore, now: Long): Long? {
        val window = windowFor(identity, now)
        val next = ShiftWindow.next(window)
        val proofForWindow = identity.lastPresenceProofAt in window.proofCutoff()..now
        if (proofForWindow) return dueForWindow(identity, next)

        val prefs = context.getSharedPreferences("attend_employee_late_state", Context.MODE_PRIVATE)
        val dayKey = operationalDayKey(window)
        val count = prefs.getInt("count_$dayKey", 0)
        if (count >= identity.lateAlertCount.coerceIn(1, 10)) return dueForWindow(identity, next)

        val due = dueForWindow(identity, window)
        val end = window.end + ALERT_TAIL_MILLIS
        return when {
            now < due -> due
            now <= end -> {
                val repeat = identity.lateRepeatMinutes.coerceIn(5, 180) * 60_000L
                val last = prefs.getLong("last_$dayKey", 0L)
                if (last <= 0L) now + MIN_DELAY else (last + repeat).coerceAtLeast(now + MIN_DELAY)
            }
            else -> dueForWindow(identity, next)
        }
    }

    internal fun windowFor(identity: EmployeeIdentityStore, reference: Long): ShiftWindow.Window = ShiftWindow.resolve(
        reference,
        identity.shiftStartHour, identity.shiftStartMinute,
        identity.shiftEndHour, identity.shiftEndMinute,
        ALERT_TAIL_MILLIS
    )

    internal fun dueFor(identity: EmployeeIdentityStore, reference: Long): Long = dueForWindow(identity, windowFor(identity, reference))
    internal fun endFor(identity: EmployeeIdentityStore, reference: Long): Long = windowFor(identity, reference).end

    internal fun dueForWindow(identity: EmployeeIdentityStore, window: ShiftWindow.Window): Long =
        window.start + (identity.lateGraceMinutes + identity.lateFirstAlertDelayMinutes) * 60_000L

    internal fun operationalDayKey(window: ShiftWindow.Window): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(window.start))
}
