package com.attendpro.store

import android.app.Activity
import com.attendpro.core.AttendanceMethod
import com.attendpro.core.AttendanceAction

/** Dispatches the same attendance request over local transports. QR remains an explicit fallback UI. */
object ChallengeDispatch1928 {
    fun send(activity: Activity, employeeId: String, secret: ByteArray, method: AttendanceMethod, expiresAt: Long = System.currentTimeMillis() + 60_000L, requestToken: Int = java.security.SecureRandom().nextInt(), action: AttendanceAction = AttendanceAction.CHECK_IN): Boolean {
        val issuedAt = System.currentTimeMillis()
        val bleSent = BleChallengeBroadcaster.broadcast(activity, employeeId, secret, method, expiresAt, requestToken, action)
        val lanSent = LocalChallengeSender.send(employeeId, secret, method, issuedAt, requestToken, action)
        return bleSent || lanSent
    }
}
