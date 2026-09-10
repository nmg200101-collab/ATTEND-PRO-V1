package com.attendpro.core

object PhoneAttendancePolicy {
    val visibleMethods: List<AttendanceMethod> = listOf(
        AttendanceMethod.SHARED_DEVICE_FACE,
        AttendanceMethod.VOICE_PHRASE,
        AttendanceMethod.PASSWORD,
        AttendanceMethod.PHONE_BLE_BIOMETRIC,
        AttendanceMethod.PHONE_PROXIMITY
    )

    fun label(method: AttendanceMethod): String = when (method) {
        AttendanceMethod.SHARED_DEVICE_FACE -> "◉  التعرف بالوجه"
        AttendanceMethod.VOICE_PHRASE -> "◖  بصمة الصوت"
        AttendanceMethod.PASSWORD -> "▣  كلمة المرور"
        AttendanceMethod.PHONE_BIOMETRIC,
        AttendanceMethod.PHONE_BLE_BIOMETRIC,
        AttendanceMethod.PHONE_FINGERPRINT -> "◉  بصمة/وجه الهاتف"
        AttendanceMethod.PHONE_PROXIMITY -> "▦  QR مباشر"
        else -> method.name
    }

    fun sanitize(methods: Set<AttendanceMethod>): Set<AttendanceMethod> =
        methods.filterTo(linkedSetOf()) { it != AttendanceMethod.PATTERN && it != AttendanceMethod.PIN }
}
