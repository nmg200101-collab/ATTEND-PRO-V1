# ATTEND-PRO V155 — FINAL QA & RELEASE HARDENING

Base: ATTEND-PRO V154 — FINAL VISUAL ERGONOMICS  
Base commit: `42a6b9b4e1a6222bc02c37c54895ea2bf76b45b9`

## Scope
V155 is a release-hardening build. It does not add product features and does not redesign the V154 user interface.

## Preserved locks
- Receiver remains locked to the V149 receiver baseline.
- The RC29 field-proven pairing/direct-link source set remains unchanged.
- The RC29 CI-time BLE recovery behavior in `buildsrc/build.gradle.kts` remains unchanged.

## Automated release checks
- Android compile/target SDK 36.
- Core/Foundation and app unit tests.
- Android lint for Direct and Play release variants.
- Direct Store/Employee release APK builds.
- Play Store/Employee release AAB builds.
- Official signing verification for Direct APKs and signed Play bundles.
- Package/version verification on Direct APKs.
- Play manifests remove `REQUEST_INSTALL_PACKAGES`.
- Foreground-service, Bluetooth, location and background-location declaration guards.
- In-app update integrity/signature guards remain present.
- Encrypted phone/server backup implementation remains present.
- Source backup ZIP and SHA-256 manifest are included in the CI artifact.

## Field release gate
GitHub Actions cannot certify real-device Bluetooth pairing, QR pairing, BLE GATT/ACK, Wi-Fi/Hotspot/LAN, GPS, Android biometric prompts, OEM background behavior, APK installer flow, or end-to-end backup restoration. V155 is not to receive the final field-release lock until the real-device smoke matrix passes.

## Lint compatibility note
Current Android lint flags a numeric `0` comparison in the byte-locked V149 `ReceiverReportBleTransport.kt` even though Android's success status is zero. Because Receiver is explicitly immutable for V155, the Store lint configuration suppresses only `WrongConstant` for that locked file. The V149 byte-diff guard still prevents any source change in the ignored file; all other lint errors remain release-blocking.
