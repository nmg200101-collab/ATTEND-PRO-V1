# ATTEND-PRO Current Approved Baseline

Status: FIELD-APPROVED + OFFICIAL-SIGNING-VERIFIED BASELINE

Version: ATTEND-PRO V157 — Bluetooth Dual-Path Stability
Version code: 157
Approved date: 2026-09-25
Approved purpose: Stable Bluetooth presence recognition after pairing, with dual-path Bluetooth resilience while preserving all other connection channels.

## Field approval
- The project owner tested V157 on real devices and explicitly approved it as the new essential Bluetooth baseline.
- V157 supersedes the previous Bluetooth baseline for all future connectivity regression checks.

## Verified build provenance
- Repository: nmg200101-collab/ATTEND-PRO-V1
- Branch: attend-pro-v157-bluetooth-dual-path-stability
- Field-approved source commit: 575f4dddc77ae025574caba3321d6990e102c216
- GitHub Actions run: 36161498185
- Run number: 164
- Artifact: ATTEND-PRO-V157-BLUETOOTH-DUAL-PATH-STABILITY-SIGNED
- Artifact ID: 10875414244
- Artifact SHA-256: 527ccb53e58339eaee00748d61d508f7aa0d3a7b1f34274d30b64713418c06e0

## Official package hashes
- Store APK SHA-256: 9d08bdebbc65aafa44f92310d8c456a333e3b234ff67bb128491c0b0405e928b
- Employee APK SHA-256: 54d056597a99c60e4aa7f69fe5758ba28f7d663605806bfecc25c37c8b20e8e7

## Connection invariants for every later release
1. Preserve V157 Bluetooth dual-path presence recognition.
2. Bluetooth pairing success and Bluetooth presence recognition are separate gates; later code must preserve both.
3. Bluetooth must work without requiring Wi-Fi/Hotspot.
4. Preserve authenticated ACK/GATT semantics; discovery alone is not a valid authenticated connection.
5. Preserve Wi-Fi/Hotspot, LAN, Server and QR paths.
6. GPS remains monitoring/proximity only and must never replace Bluetooth or become attendance proof.
7. Preserve proof requests, messages, attendance and departure methods.
8. Preserve RC29 protected connection protocol files and Receiver V149 lock.
9. Any later connectivity/lifecycle/background/permission change requires the V157 Bluetooth regression gate and new real-device confirmation.

## Lock reference
See: BLUETOOTH-FINAL-LOCK-V157.md

## Release rule
A later release must not replace this baseline unless Bluetooth passes real-device field testing after all changes.
