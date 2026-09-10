from pathlib import Path

ROOT = Path('buildsrc')
CORE = ROOT / 'core/src/main/java/com/attendpro/core'
STORE = ROOT / 'store-app'
EMP = ROOT / 'employee-app'


def must(path: Path):
    if not path.exists():
        raise SystemExit(f'missing required file: {path}')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'missing anchor: {label}')
    return text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# 1) Runtime integrity verification. Pairing protocol itself is NOT modified.
# ---------------------------------------------------------------------------
integrity = CORE / 'AppIntegrity1982.kt'
integrity.write_text(r'''package com.attendpro.core

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
''', encoding='utf-8')

# ---------------------------------------------------------------------------
# 2) Release R8 / resource shrinking / BuildConfig gate.
# ---------------------------------------------------------------------------
for app in (STORE, EMP):
    gradle = app / 'build.gradle.kts'; must(gradle)
    s = gradle.read_text(encoding='utf-8')
    anchor = '    kotlinOptions { jvmTarget = "17" }\n'
    block = '''    kotlinOptions { jvmTarget = "17" }\n\n    buildFeatures {\n        buildConfig = true\n    }\n\n    buildTypes {\n        getByName("debug") {\n            buildConfigField("boolean", "ENFORCE_OFFICIAL_SIGNATURE", "false")\n        }\n        getByName("release") {\n            isMinifyEnabled = true\n            isShrinkResources = true\n            buildConfigField("boolean", "ENFORCE_OFFICIAL_SIGNATURE", "true")\n            proguardFiles(\n                getDefaultProguardFile("proguard-android-optimize.txt"),\n                "proguard-rules.pro"\n            )\n        }\n    }\n'''
    s = replace_once(s, anchor, block, f'{app.name} release hardening')
    gradle.write_text(s, encoding='utf-8')

    rules = app / 'proguard-rules.pro'
    rules.write_text(r'''# ATTEND-PRO 1.9.82 release obfuscation rules.
# R8 obfuscation remains enabled; only runtime metadata required by Android/Kotlin is kept.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Android component entry points are discovered from the manifest by AGP/R8.
# Keep FileProvider from AndroidX intact.
-keep class androidx.core.content.FileProvider { *; }

# JourneyApps / ZXing use camera resources and reflection internally in some versions.
-dontwarn com.google.zxing.**
-dontwarn com.journeyapps.barcodescanner.**
''', encoding='utf-8')

# ---------------------------------------------------------------------------
# 3) Explicit HTTPS-only app policy.
# ---------------------------------------------------------------------------
for app in (STORE, EMP):
    manifest = app / 'src/main/AndroidManifest.xml'; must(manifest)
    s = manifest.read_text(encoding='utf-8')
    s = replace_once(
        s,
        '<application android:hardwareAccelerated="true" android:allowBackup="false"',
        '<application android:hardwareAccelerated="true" android:allowBackup="false" android:usesCleartextTraffic="false" android:networkSecurityConfig="@xml/network_security_config"',
        f'{app.name} https policy'
    )
    manifest.write_text(s, encoding='utf-8')
    xml = app / 'src/main/res/xml/network_security_config.xml'
    xml.parent.mkdir(parents=True, exist_ok=True)
    xml.write_text('''<?xml version="1.0" encoding="utf-8"?>\n<network-security-config>\n    <base-config cleartextTrafficPermitted="false" />\n</network-security-config>\n''', encoding='utf-8')

# ---------------------------------------------------------------------------
# 4) Block repackaged release builds at app entry. Debug builds remain usable internally.
# ---------------------------------------------------------------------------
for rel in (
    'store-app/src/main/java/com/attendpro/store/MainActivity.kt',
    'employee-app/src/main/java/com/attendpro/employee/MainActivity.kt',
):
    path = ROOT / rel; must(path)
    s = path.read_text(encoding='utf-8')
    package = 'store' if '/store/' in rel else 'employee'
    if 'import com.attendpro.core.AppIntegrity1982' not in s:
        # Add next to first core import if possible, otherwise after package.
        if 'import com.attendpro.core.' in s:
            first = s.index('import com.attendpro.core.')
            s = s[:first] + 'import com.attendpro.core.AppIntegrity1982\n' + s[first:]
        else:
            s = s.replace(f'package com.attendpro.{package}\n', f'package com.attendpro.{package}\n\nimport com.attendpro.core.AppIntegrity1982\n', 1)
    old = '    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n'
    new = '''    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        if (!enforceOfficialBuild1982()) return\n'''
    s = replace_once(s, old, new, f'{package} main integrity onCreate')
    # insert helper before onCreate
    marker = '    override fun onCreate(savedInstanceState: Bundle?) {\n'
    helper = r'''    private fun enforceOfficialBuild1982(): Boolean {
        if (!BuildConfig.ENFORCE_OFFICIAL_SIGNATURE) return true
        if (AppIntegrity1982.isOfficialPackageAndSignature(this) && !AppIntegrity1982.isDebuggable(this)) return true
        AlertDialog.Builder(this)
            .setTitle("نسخة غير رسمية")
            .setMessage("تعذر التحقق من توقيع ATTEND PRO الرسمي. لحماية بيانات الحضور لا يمكن تشغيل نسخة معاد توقيعها أو معدلة.")
            .setCancelable(false)
            .setPositiveButton("إغلاق") { _, _ -> finishAffinity() }
            .show()
        return false
    }

'''
    s = s.replace(marker, helper + marker, 1)
    path.write_text(s, encoding='utf-8')

# ---------------------------------------------------------------------------
# 5) Sensitive owner/system screens: FLAG_SECURE + active instrumentation block.
# ---------------------------------------------------------------------------
for filename in ('SystemSettingsActivity.kt', 'SystemManagement1971Activity.kt'):
    path = STORE / f'src/main/java/com/attendpro/store/{filename}'; must(path)
    s = path.read_text(encoding='utf-8')
    if 'import android.view.WindowManager' not in s:
        # place near other android.view imports
        if 'import android.view.' in s:
            idx = s.index('import android.view.')
            s = s[:idx] + 'import android.view.WindowManager\n' + s[idx:]
        else:
            s = s.replace('import android.os.Bundle\n', 'import android.os.Bundle\nimport android.view.WindowManager\n', 1)
    if 'import com.attendpro.core.AppIntegrity1982' not in s:
        if 'import com.attendpro.core.' in s:
            idx = s.index('import com.attendpro.core.')
            s = s[:idx] + 'import com.attendpro.core.AppIntegrity1982\n' + s[idx:]
        else:
            s = s.replace('package com.attendpro.store\n', 'package com.attendpro.store\n\nimport com.attendpro.core.AppIntegrity1982\n', 1)
    old = '    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n'
    new = '''    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)\n        if (BuildConfig.ENFORCE_OFFICIAL_SIGNATURE && AppIntegrity1982.activeInstrumentationDetected()) {\n            AlertDialog.Builder(this)\n                .setTitle("تم إيقاف الإدارة الحساسة")\n                .setMessage("تم اكتشاف جلسة فحص/تعديل نشطة. أُغلقت منطقة إدارة النظام لحماية الحسابات والصلاحيات.")\n                .setCancelable(false)\n                .setPositiveButton("إغلاق") { _, _ -> finish() }\n                .show()\n            return\n        }\n'''
    s = replace_once(s, old, new, f'{filename} secure owner screen')
    path.write_text(s, encoding='utf-8')

# Also protect Store management/settings from screenshots without blocking normal attendance.
path = STORE / 'src/main/java/com/attendpro/store/StoreSettingsActivity.kt'; must(path)
s = path.read_text(encoding='utf-8')
if 'import android.view.WindowManager' not in s:
    if 'import android.view.' in s:
        idx = s.index('import android.view.')
        s = s[:idx] + 'import android.view.WindowManager\n' + s[idx:]
    else:
        s = s.replace('import android.os.Bundle\n', 'import android.os.Bundle\nimport android.view.WindowManager\n', 1)
s = replace_once(s, '    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n', '    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)\n', 'store settings secure flag')
path.write_text(s, encoding='utf-8')

print('ATTEND-PRO 1.9.82 anti-tamper/IP hardening patch applied')
