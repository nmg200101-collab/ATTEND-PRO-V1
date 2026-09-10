package com.attendpro.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import java.io.File
import java.security.MessageDigest

/**
 * ATTEND-PRO 1.9.82 defensive runtime integrity checks.
 * This is defense-in-depth, not a claim of impossible reverse engineering.
 */
object AppIntegrity1982 {
    const val OFFICIAL_CERT_SHA256 = "2fc214199b0c6e86f9cdd9288419fc143a119258c34f6a0ff70eed6aecb6fe59"

    private val officialPackages = setOf("com.attendpro.store", "com.attendpro.employee")

    fun isOfficialPackageAndSignature(context: Context): Boolean {
        if (context.packageName !in officialPackages) return false
        val signatures = runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= 28) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signing = info.signingInfo ?: return@runCatching emptyArray()
                if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }
        }.getOrNull() ?: return false
        return signatures.any { signature ->
            sha256(signature.toByteArray()).equals(OFFICIAL_CERT_SHA256, ignoreCase = true)
        }
    }

    fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    fun debuggerOrTracerDetected(): Boolean {
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return true
        val tracer = runCatching {
            File("/proc/self/status").useLines { lines ->
                lines.firstOrNull { it.startsWith("TracerPid:") }
                    ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
            }
        }.getOrDefault(0)
        return tracer > 0
    }

    fun activeInstrumentationDetected(): Boolean {
        if (debuggerOrTracerDetected()) return true
        val maps = runCatching { File("/proc/self/maps").readText().lowercase() }.getOrDefault("")
        return listOf("frida", "gum-js-loop", "libsubstrate").any { it in maps }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
