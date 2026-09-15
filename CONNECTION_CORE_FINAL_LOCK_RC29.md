# ATTEND-PRO Connection Core — FINAL LOCK RC29

Status: FIELD VERIFIED / FINAL / PROTECTED
Baseline: RC28 connection runtime, validated on real devices on 2026-09-15.

## Field evidence accepted
- Store ↔ Employee pairing works on real devices.
- GPS presence remains visible through the server with Bluetooth disabled.
- Server reachability alone is not counted as an employee device connection.
- GPS is presence/zone recognition, not automatic attendance and not exact phone-to-phone ranging.

## Frozen connection scope
The following runtime/protocol areas are frozen and MUST NOT be modified by normal feature/UI/release updates:
- Pairing / QR / pairing ACK
- BLE advertising, scanning, GATT direct link and presence
- LAN presence / ACK path
- GPS zone config, local classification, offline cache/queue and cloud telemetry
- Store device-presence semantics
- CentralServerClient GPS configuration/telemetry contracts

## Protected files
- buildsrc/employee-app/src/main/java/com/attendpro/employee/PairingDiscovery.kt
- buildsrc/store-app/src/main/java/com/attendpro/store/PairingBeacon.kt
- buildsrc/core/src/main/java/com/attendpro/core/BleDirectProtocol.kt
- buildsrc/core/src/main/java/com/attendpro/core/BleProtocol.kt
- buildsrc/core/src/main/java/com/attendpro/core/PairingAckProtocol.kt
- buildsrc/core/src/main/java/com/attendpro/core/PairingProtocol.kt
- buildsrc/employee-app/src/main/java/com/attendpro/employee/BlePresenceAdvertiser.kt
- buildsrc/employee-app/src/main/java/com/attendpro/employee/BleDirectLinkServer.kt
- buildsrc/store-app/src/main/java/com/attendpro/store/BleEmployeeScanner.kt
- buildsrc/store-app/src/main/java/com/attendpro/store/BleDirectLinkClient.kt
- buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt
- buildsrc/core/src/main/java/com/attendpro/core/CentralServerClient.kt

Store MainActivity contains both UI and connection-state presentation. Future UI work may edit it only if the RC29 connection invariants and final-lock checks continue to pass; connection logic itself is frozen.

## Mandatory semantics
A linked employee is considered currently present/connected only by fresh BLE, LAN ACK, or fresh recognized GPS telemetry. A server HTTP heartbeat by itself is diagnostics only and MUST NOT increment the connected-device count.

## Change policy
Future attendance, reports, management, language, Play compliance, backup, or visual updates MUST NOT alter this connection core. Opening the lock requires an explicit connection-core revision, a documented reason, automated regression checks, and real-device revalidation before replacing this baseline.
