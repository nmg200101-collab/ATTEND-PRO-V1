package com.attendpro.store

import android.app.Activity
import com.attendpro.core.AppUpdateManager

object DistributionUpdateManager {
    const val DEFAULT_SERVER = AppUpdateManager.DEFAULT_SERVER

    fun check(activity: Activity, serverUrl: String, channel: String, manual: Boolean = false) {
        AppUpdateManager.check(activity, serverUrl, channel, manual)
    }

    fun resume(activity: Activity) = Unit
}
