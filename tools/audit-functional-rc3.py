#!/usr/bin/env python3
from pathlib import Path
import sys

checks = {
    "AM/PM shift formatter": ("buildsrc/core/src/main/java/com/attendpro/core/ShiftTimeCodec.kt", "formatRange("),
    "Overnight shift detection": ("buildsrc/core/src/main/java/com/attendpro/core/ShiftTimeCodec.kt", "isOvernight("),
    "Store custom-shift AM/PM picker": ("buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt", "FormPickerHelper.pickTime(this@MainActivity"),
    "Large attendance action area": ("buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt", "if (classic) 132 else 122"),
    "Offline Employee reply channel": ("buildsrc/core/src/main/java/com/attendpro/core/BleLocalReplyChannel1977.kt", "REPLY_UUID"),
    "Store receives offline replies": ("buildsrc/store-app/src/main/java/com/attendpro/store/StoreLocalReplyStore1977.kt", "StoreLocalReplyReceiver1977"),
    "Receiver report permission": ("buildsrc/core/src/main/java/com/attendpro/core/LocalStores.kt", "canReceiveReports"),
    "Receiver messaging permission": ("buildsrc/core/src/main/java/com/attendpro/core/LocalStores.kt", "canMessageEmployees"),
    "Receiver Store-management permission": ("buildsrc/core/src/main/java/com/attendpro/core/LocalStores.kt", "canManageStore"),
    "Encrypted phone/server backups": ("buildsrc/foundation/src/main/java/com/attendpro/foundation/backup/EncryptedBackupCodec.kt", "AES/GCM/NoPadding"),
    "App lock": ("buildsrc/core/src/main/java/com/attendpro/core/AppLockGateActivity.kt", "class AppLockGateActivity"),
    "Direct update verification": ("buildsrc/core/src/main/java/com/attendpro/core/AppUpdateManager.kt", "release.sha256"),
    "Store guide tracks release": ("buildsrc/store-app/src/main/java/com/attendpro/store/StoreUserGuideActivity.kt", "BuildConfig.VERSION_NAME"),
    "Employee guide tracks release": ("buildsrc/employee-app/src/main/java/com/attendpro/employee/EmployeeUserGuideActivity.kt", "BuildConfig.VERSION_NAME"),
    "Agent credential reset UI": ("buildsrc/store-app/src/main/java/com/attendpro/store/SystemSettingsActivity.kt", "showAgentCredentialInfo"),
    "Central agent one-time credential UI": ("buildsrc/store-app/src/main/java/com/attendpro/store/SystemManagement1971Activity.kt", "بيانات الدخول — إنشاء وعرض رمز جديد"),
    "Subscriber one-time recovery credential UI": ("buildsrc/store-app/src/main/java/com/attendpro/store/SystemManagement1971Activity.kt", "showSubscriberRecoveryCredential"),
    "Store versionCode 93": ("buildsrc/store-app/build.gradle.kts", "versionCode = 93"),
    "Employee versionCode 93": ("buildsrc/employee-app/build.gradle.kts", "versionCode = 93"),
}

failed = []
lines = ["ATTEND PRO RC3 functional source audit", ""]
for name, (path, token) in checks.items():
    p = Path(path)
    ok = p.exists() and token in p.read_text(encoding="utf-8")
    lines.append(f"{'PASS' if ok else 'FAIL'}  {name}  [{path}]")
    if not ok:
        failed.append(name)

out = Path("functional-audit-rc3.txt")
out.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(out.read_text(encoding="utf-8"))
if failed:
    print("Functional audit failed:", ", ".join(failed), file=sys.stderr)
    sys.exit(1)
