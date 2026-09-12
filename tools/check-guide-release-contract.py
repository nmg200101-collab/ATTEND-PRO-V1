#!/usr/bin/env python3
from pathlib import Path
import re
import sys

EXPECTED_CODE = 95
EXPECTED_NAME = "2.0.0-RC5"

apps = {
    "store": Path("buildsrc/store-app/build.gradle.kts"),
    "employee": Path("buildsrc/employee-app/build.gradle.kts"),
}
guides = {
    "store": Path("buildsrc/store-app/src/main/java/com/attendpro/store/StoreUserGuideActivity.kt"),
    "employee": Path("buildsrc/employee-app/src/main/java/com/attendpro/employee/EmployeeUserGuideActivity.kt"),
}
release_notes = Path("RELEASE_NOTES_V2.0.0_RC5.md")

errors = []
for name, path in apps.items():
    text = path.read_text(encoding="utf-8")
    code = re.search(r"versionCode\s*=\s*(\d+)", text)
    version = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not code or int(code.group(1)) != EXPECTED_CODE:
        errors.append(f"{name}: versionCode must be {EXPECTED_CODE}")
    if not version or version.group(1) != EXPECTED_NAME:
        errors.append(f"{name}: versionName must be {EXPECTED_NAME}")

notes = release_notes.read_text(encoding="utf-8") if release_notes.exists() else ""
for token in (EXPECTED_NAME, "versionCode 95", "Bluetooth", "GPS", "Presence Proof", "credential"):
    if token.lower() not in notes.lower():
        errors.append(f"release notes missing token: {token}")

for name, path in guides.items():
    text = path.read_text(encoding="utf-8") if path.exists() else ""
    required = (
        "BuildConfig.VERSION_NAME",
        "ما الجديد",
        "What's new",
    )
    for token in required:
        if token not in text:
            errors.append(f"{name} guide missing: {token}")

if errors:
    print("Guide/release contract FAILED", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    sys.exit(1)

print(f"Guide/release contract PASS: {EXPECTED_NAME} ({EXPECTED_CODE})")
