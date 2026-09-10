package com.attendpro.store

import android.app.Activity
import android.app.AlertDialog
import android.view.ViewGroup
import android.widget.ImageView
import com.attendpro.core.AttendanceMethod
import com.attendpro.core.AttendanceAction
import com.attendpro.core.AttendanceQrProtocol
import com.attendpro.core.QrCodeTools

object AttendanceQrPresenter {
    fun show(activity: Activity, employeeId: String, secret: ByteArray, method: AttendanceMethod, expiresAt: Long, action: AttendanceAction = AttendanceAction.CHECK_IN) {
        if (method != AttendanceMethod.PHONE_PROXIMITY) return
        val value = AttendanceQrProtocol.encode(employeeId, secret, method, expiresAt, action)
        val image = ImageView(activity).apply {
            setImageBitmap(QrCodeTools.bitmap(value, 720))
            adjustViewBounds = true
            setPadding(32, 32, 32, 32)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        AlertDialog.Builder(activity)
            .setTitle(if (action == AttendanceAction.CHECK_IN) "▦  QR إثبات الحضور" else "▦  QR إثبات الانصراف")
            .setMessage("يمسح الموظف هذا الرمز من تطبيقه ثم يوافق على ${if (action == AttendanceAction.CHECK_IN) "الحضور" else "الانصراف"}. الرمز مؤقت وموقّع ولا يحتاج إنترنت.")
            .setView(image)
            .setNegativeButton("إغلاق", null)
            .show()
    }
}
