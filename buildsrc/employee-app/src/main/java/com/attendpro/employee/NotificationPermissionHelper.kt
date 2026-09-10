package com.attendpro.employee

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build

object NotificationPermissionHelper {
    private const val REQUEST_NOTIFICATIONS = 1933

    fun ensure(activity: Activity) {
        AttendanceRequestNotifier.ensureChannel(activity)
        if (Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }
}
