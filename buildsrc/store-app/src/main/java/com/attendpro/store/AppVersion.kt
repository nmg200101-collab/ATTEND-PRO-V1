package com.attendpro.store
import android.content.Context
internal fun Context.attendProVersionName(): String = try {
    packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
} catch (_: Exception) { "?" }
