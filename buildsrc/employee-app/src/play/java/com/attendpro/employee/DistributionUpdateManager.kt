package com.attendpro.employee

import android.app.Activity
import android.app.AlertDialog
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

object DistributionUpdateManager {
    const val DEFAULT_SERVER = ""
    private const val REQUEST_CODE = 9202

    fun check(activity: Activity, serverUrl: String, channel: String, manual: Boolean = false) {
        val manager = AppUpdateManagerFactory.create(activity)
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                val inProgress = info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                if ((inProgress || available) && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        manager.startUpdateFlowForResult(info, AppUpdateType.IMMEDIATE, activity, REQUEST_CODE)
                    }.onFailure {
                        if (manual) message(activity, "تعذر بدء تحديث Google Play", "افتح صفحة التطبيق في Google Play وحاول التحديث من هناك.")
                    }
                } else if (manual) {
                    message(activity, "تحديثات Google Play", "أنت تستخدم أحدث إصدار متاح حاليًا عبر Google Play.")
                }
            }
            .addOnFailureListener {
                if (manual) message(activity, "تعذر فحص تحديث Google Play", "تحقق من اتصال الإنترنت ومن تثبيت النسخة عبر Google Play ثم أعد المحاولة.")
            }
    }

    fun resume(activity: Activity) {
        val manager = AppUpdateManagerFactory.create(activity)
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                runCatching {
                    @Suppress("DEPRECATION")
                    manager.startUpdateFlowForResult(info, AppUpdateType.IMMEDIATE, activity, REQUEST_CODE)
                }
            }
        }
    }

    private fun message(activity: Activity, title: String, body: String) {
        if (activity.isFinishing) return
        AlertDialog.Builder(activity).setTitle(title).setMessage(body).setPositiveButton("حسنًا", null).show()
    }
}
