# ATTEND-PRO V139 — Receiver Link & Offline Reports Stabilization

Base:
- Android V138 final: e9fd2ba3fd3db02089dc2689dfff7881af91f980
- Server V138 final: a3024663b5124e9059df1b24fb54313f706efea1

Scope:
1. Store receiver center: one Phones screen; permissions are edited inline per phone. Remove duplicate Permissions tab.
2. Full unlink: server binding deletion + local cleanup + deterministic UI refresh.
3. Receiver add state machine: QR -> local staging -> server register -> permissions -> verification -> final grant, with bounded failures and retry.
4. Offline report delivery independent from employee connection:
   - LAN / Wi-Fi / Hotspot discovery and authenticated TCP delivery.
   - Dedicated BLE service UUID and authenticated fragmented delivery.
   - Internet remains fallback.
   - Same transferId across transports; receiver deduplicates.
5. Transport status surfaced to users.
6. No changes to RC29 field-proven employee pairing/direct-link protocol.

Protected RC29 files (must remain byte-identical to baseline 7c9c8aeb8316da5334096820bf3fe16f45f8a630):
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

Validation:
- Delete receiver persists after reopening.
- Add receiver cannot stay indefinitely in Linking state.
- Permissions live only in Phones card/details.
- Two receiver phones remain independent.
- Offline LAN report receives and confirms without internet.
- BLE uses dedicated receiver-report UUIDs only.
- Internet delivery still works.
- transferId deduplication prevents duplicate reports.
- Protected RC29 diff gate passes.
