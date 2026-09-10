package com.attendpro.core

import android.util.Base64

object AttendanceQrProtocol {
    private const val PREFIX = "APATT1:"

    fun encode(employeeId: String, secret: ByteArray, method: AttendanceMethod, expiresAt: Long, action: AttendanceAction = AttendanceAction.CHECK_IN): String =
        PREFIX + Base64.encodeToString(
            BleChallengeProtocol.encode(employeeId, secret, method, expiresAt, action = action),
            Base64.NO_WRAP or Base64.URL_SAFE
        )

    fun decode(value: String, employeeId: String, secret: ByteArray): BleChallengeProtocol.Decoded? {
        if (!value.startsWith(PREFIX)) return null
        return runCatching {
            val bytes = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP or Base64.URL_SAFE)
            BleChallengeProtocol.decode(bytes, employeeId, secret)
        }.getOrNull()
    }

    fun isAttendanceQr(value: String): Boolean = value.startsWith(PREFIX)
}
