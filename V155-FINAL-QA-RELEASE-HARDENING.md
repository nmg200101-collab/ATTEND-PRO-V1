# V155 FINAL QA & RELEASE HARDENING

## Baseline
- V154 approved HEAD: `42a6b9b4e1a6222bc02c37c54895ea2bf76b45b9`
- V155 branch: `attend-pro-v155-final-qa-release-hardening`
- No feature additions.
- No V154 visual redesign.

## Immutable areas
1. RC29 pairing/direct-link sources listed in `CONNECTION_CORE_FINAL_LOCK_RC29.md`.
2. Receiver implementation locked by `RECEIVER-FINAL-LOCK-V149.md`.
3. Effective RC29 CI BLE recovery patch in `buildsrc/build.gradle.kts`.

## Automated QA gates
The V155 workflow fails on any drift in the locks above. It also runs unit tests, Android lint, Direct release APK builds, Play release AAB builds, signature/package verification, API 36 checks, Play manifest guards, and source/archive checks.

## Real-device smoke matrix required before FINAL-RELEASE-LOCK
- Store cold start, background/resume and repeated navigation.
- Employee QR pairing and Bluetooth pairing.
- BLE GATT/ACK and reconnect after app/device restart.
- Wi-Fi/Hotspot/LAN fallback.
- Server path under good and weak connectivity.
- GPS foreground and background behavior with explicit permission flow.
- Check-in/check-out using native biometric and password.
- Store/Employee messages and presence-proof requests.
- Receiver V149 automatic report/live mirror/message paths (observe only; do not modify).
- Direct in-app APK update: discover, download, SHA/signature verify, install, preserve data/activation.
- Phone backup restore and server backup restore.
- Android 12/13/14/15/16 device coverage as available.

Only after the field matrix passes should `FINAL-RELEASE-LOCK-V155.md` be created.

## Diagnosed automated-QA exception
Run 36116195322 exposed a pre-existing `WrongConstant` lint finding in the V149-locked Receiver BLE transport. V155 does not modify that Receiver source. A path-scoped lint rule ignores only `WrongConstant` in that immutable file while preserving abort-on-error behavior everywhere else.
