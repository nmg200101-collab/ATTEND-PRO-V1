# ATTEND-PRO V2.0.0-RC2 PLAY-COMPLIANCE

Status: release candidate for internal and field verification. Not final.

## Baseline
- Source baseline: `3b479e310d06bdaec96205aae8787fb264fa02c9`
- Field pairing reference: ATTEND-PRO 1.9.70 STABLE BASE
- Official signing certificate SHA-256: `2fc214199b0c6e86f9cdd9288419fc143a119258c34f6a0ff70eed6aecb6fe59`
- Protected connection hashes remain governed by `CONNECTION_CORE_LOCK_V2_FOUNDATION.sha256`.

## Distribution separation
- Play variants remove `REQUEST_INSTALL_PACKAGES`.
- Play variants use Google Play In-App Updates only.
- Direct variants retain the existing HTTPS + package + signer + SHA-256 verified updater and require user-approved installation.
- RC2 CI rejects a Play APK if the direct APK update endpoint remains reachable in its optimized DEX.

## Employee monitoring classification
The Employee app runs an ongoing foreground presence service and can transmit proximity/location observations to the store server when the employer-enabled GPS feature is active. The Employee Play manifest therefore declares `isMonitoringTool=enterprise_management`, and the foreground notification explicitly says monitoring is active.

## Location disclosure
Before the first runtime location permission request, the employee is shown a prominent disclosure describing background location collection, the attendance/proximity purpose, possible server transmission, and the fact that location alone does not create an attendance record.

## Permission timing
Camera, microphone, Bluetooth/Nearby Devices, and location runtime permissions remain feature-triggered. RC2 does not intentionally modify QR/BLE/GATT/PairingProtocol/ACK/Confirm/Reconnect protected code.

## Remaining before production submission
- Publish active non-editable privacy-policy and data-deletion web URLs and wire them in-app.
- Complete Play Console Data Safety and Background Location declaration/video if background location remains enabled.
- Complete real Samsung and Motorola pairing/presence regression.
- Complete internal Play track In-App Update test.
