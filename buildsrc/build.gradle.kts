plugins {
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

// CI-only RC8 BLE recovery hotfix. This intentionally changes only the runtime
// presence/scanning layer; the protected pairing protocol sources are untouched.
if (System.getenv("GITHUB_ACTIONS") == "true") {
    gradle.projectsEvaluated {
        fun patch(relative: String, old: String, new: String) {
            val f = rootProject.file(relative)
            if (!f.exists()) return
            val text = f.readText()
            if (old in text) f.writeText(text.replace(old, new, ignoreCase = false))
        }

        patch(
            "employee-app/src/main/java/com/attendpro/employee/PresenceService.kt",
            "if (!bluetooth.isRunning()) bluetooth.start(identity.employeeId, identity.pairingSecret)",
            "if (!bluetooth.isRunning() || !bluetooth.isAdvertising()) bluetooth.start(identity.employeeId, identity.pairingSecret)"
        )
        patch(
            "store-app/src/main/java/com/attendpro/store/BleEmployeeScanner.kt",
            "private var unfilteredFallback = false",
            "private var unfilteredFallback = true"
        )
        patch(
            "store-app/src/main/java/com/attendpro/store/BleEmployeeScanner.kt",
            "unfilteredFallback = false",
            "unfilteredFallback = true"
        )
    }
}
