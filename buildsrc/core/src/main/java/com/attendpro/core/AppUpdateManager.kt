package com.attendpro.core

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object AppUpdateManager {
    const val DEFAULT_SERVER = "https://attend-pro-central.nmg200101.workers.dev"

    data class Release(
        val versionCode: Long,
        val versionName: String,
        val notes: String,
        val mandatory: Boolean,
        val downloadUrl: String,
        val sha256: String,
        val sizeBytes: Long
    )

    fun check(activity: Activity, serverUrl: String, channel: String, manual: Boolean = false) {
        if (!supportsDirectInstall(activity)) {
            if (manual) showPlayManagedUpdate(activity)
            return
        }
        Thread {
            val result = runCatching { fetchRelease(serverUrl, channel) }
            activity.runOnUiThread {
                result.onSuccess { release ->
                    val current = currentVersionCode(activity)
                    if (release == null || release.versionCode <= current) {
                        if (manual) message(activity, "التحديثات", "أنت تستخدم أحدث إصدار متاح حاليًا.")
                    } else showUpdate(activity, channel, release)
                }.onFailure {
                    if (manual) message(activity, "تعذر فحص التحديث", it.message ?: "تحقق من اتصال الإنترنت ثم أعد المحاولة.")
                }
            }
        }.start()
    }

    private fun fetchRelease(serverUrl: String, channel: String): Release? {
        require(serverUrl.startsWith("https://")) { "عنوان قناة التحديث يجب أن يستخدم HTTPS" }
        require(channel == "store" || channel == "employee") { "قناة التطبيق غير صالحة" }
        val connection = URL(serverUrl.trimEnd('/') + "/api/v1/app/update?app=" + channel).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept", "application/json")
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code == 204) return null
        if (code !in 200..299) throw IllegalStateException(JSONObject(text.ifBlank { "{}" }).optString("error", "الخادم لم يُرجع معلومات التحديث"))
        val o = JSONObject(text)
        if (!o.optBoolean("available", false)) return null
        val url = o.getString("downloadUrl")
        val hash = o.getString("sha256").lowercase()
        require(url.startsWith("https://")) { "رابط تنزيل التحديث غير آمن" }
        require(hash.matches(Regex("[0-9a-f]{64}"))) { "بصمة ملف التحديث غير صالحة" }
        return Release(o.getLong("versionCode"), o.getString("versionName"), o.optString("notes", "تحسينات وإصلاحات جديدة"),
            o.optBoolean("mandatory", false), url, hash, o.optLong("sizeBytes", 0L))
    }

    private fun showUpdate(activity: Activity, channel: String, release: Release) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("يتوفر تحديث ${release.versionName}")
            .setMessage(release.notes + "\n\nسيُحفظ التفعيل والموظفون والسجلات. لا تحذف النسخة الحالية.")
            .setPositiveButton("تنزيل وتثبيت") { _, _ -> download(activity, channel, release) }
        if (!release.mandatory) dialog.setNegativeButton("لاحقًا", null)
        dialog.setCancelable(!release.mandatory).show()
    }

    private fun download(activity: Activity, channel: String, release: Release) {
        val progress = AlertDialog.Builder(activity).setTitle("تنزيل التحديث")
            .setMessage("جارٍ تنزيل الإصدار ${release.versionName} والتحقق من سلامته…")
            .setCancelable(false).create().also { it.show() }
        Thread {
            val result = runCatching {
                val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val target = File(dir, "attend-pro-$channel-${release.versionName}.apk")
                val connection = URL(release.downloadUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 20_000
                connection.readTimeout = 45_000
                connection.setRequestProperty("Accept", "application/vnd.android.package-archive")
                if (connection.responseCode !in 200..299) throw IllegalStateException("تعذر تنزيل ملف التحديث (${connection.responseCode})")
                connection.inputStream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                connection.disconnect()
                if (release.sizeBytes > 0 && target.length() != release.sizeBytes) throw IllegalStateException("لم يكتمل تنزيل ملف التحديث")
                val actual = target.inputStream().use { input ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(64 * 1024)
                    while (true) { val n = input.read(buffer); if (n <= 0) break; digest.update(buffer, 0, n) }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }
                if (!actual.equals(release.sha256, true)) throw SecurityException("فشل التحقق من بصمة ملف التحديث")
                verifyPackageAndSigner(activity, target)
                target
            }
            activity.runOnUiThread {
                progress.dismiss()
                result.onSuccess { install(activity, it) }
                    .onFailure { message(activity, "لم يكتمل التحديث", it.message ?: "تعذر تنزيل التحديث أو التحقق منه.") }
            }
        }.start()
    }

    private fun verifyPackageAndSigner(activity: Activity, apk: File) {
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = activity.packageManager.getPackageArchiveInfo(apk.absolutePath, flags) ?: throw SecurityException("ملف APK غير صالح")
        if (archive.packageName != activity.packageName) throw SecurityException("ملف التحديث لا يخص هذا التطبيق")
        fun signerBytes(info: android.content.pm.PackageInfo): List<ByteArray> = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            val signatures = info.signatures
            signatures?.map { it.toByteArray() }.orEmpty()
        }
        val installed = activity.packageManager.getPackageInfo(activity.packageName, flags)
        val trusted = signerBytes(installed).map { sha256(it) }.toSet()
        val incoming = signerBytes(archive).map { sha256(it) }.toSet()
        if (trusted.isEmpty() || incoming.isEmpty() || trusted.intersect(incoming).isEmpty()) throw SecurityException("توقيع التحديث لا يطابق النسخة المثبتة")
    }

    private fun install(activity: Activity, apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            message(activity, "السماح بالتثبيت", "اسمح لتطبيق ATTEND PRO بتثبيت التحديث، ثم ارجع واضغط فحص التحديث مرة أخرى.")
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
    }

    private fun supportsDirectInstall(activity: Activity): Boolean {
        val info = activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_PERMISSIONS)
        return info.requestedPermissions?.contains(android.Manifest.permission.REQUEST_INSTALL_PACKAGES) == true
    }

    private fun showPlayManagedUpdate(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("تحديثات Google Play")
            .setMessage("هذه نسخة Google Play الرسمية؛ تتم تحديثاتها من المتجر ولا تطلب صلاحية تثبيت تطبيقات خارجية.")
            .setPositiveButton("فتح Google Play") { _, _ ->
                val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${activity.packageName}"))
                runCatching { activity.startActivity(market) }.onFailure {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${activity.packageName}")))
                }
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun currentVersionCode(activity: Activity): Long {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            val code = info.versionCode
            code.toLong()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun message(activity: Activity, title: String, text: String) = AlertDialog.Builder(activity).setTitle(title).setMessage(text).setPositiveButton("حسنًا", null).show()
}
