# ATTEND-PRO V157 — BLUETOOTH FIELD-APPROVED BASELINE LOCK

Status: FIELD-APPROVED / PROTECTED BASELINE
Date approved: 2026-09-25

## Approved release
- Version: ATTEND-PRO V157 — Bluetooth Dual-Path Stability
- Branch: attend-pro-v157-bluetooth-dual-path-stability
- Field-approved commit before this documentation lock: 575f4dddc77ae025574caba3321d6990e102c216
- GitHub Actions run: 36161498185
- Run number: 164
- Artifact: ATTEND-PRO-V157-BLUETOOTH-DUAL-PATH-STABILITY-SIGNED
- Artifact ID: 10875414244
- Artifact SHA-256: 527ccb53e58339eaee00748d61d508f7aa0d3a7b1f34274d30b64713418c06e0
- Store APK SHA-256: 9d08bdebbc65aafa44f92310d8c456a333e3b234ff67bb128491c0b0405e928b
- Employee APK SHA-256: 54d056597a99c60e4aa7f69fe5758ba28f7d663605806bfecc25c37c8b20e8e7

## Field approval
The project owner tested V157 on real devices and explicitly approved this release as the new essential Bluetooth baseline.

## Locked behavior
1. Bluetooth pairing must continue to work.
2. Bluetooth presence recognition must remain active without requiring Wi-Fi/Hotspot.
3. The dual-path Bluetooth recognition introduced in V157 is a protected regression baseline.
4. A later update must not silently disable either Bluetooth direction, background restart, BLE advertising, scanning, authenticated ACK, or UI connection-state propagation.
5. Wi-Fi/Hotspot, LAN, Server, QR, GPS observation, messages, proof requests and attendance methods must continue to coexist without suppressing Bluetooth.
6. GPS remains observation/proximity only; it is not a Bluetooth substitute.
7. Receiver V149 remains separately locked.
8. RC29 protected connection protocol files remain protected.

## Regression rule
Any later release that modifies connectivity, lifecycle, background services, permissions, scanning, advertising, connection status, proof delivery or messaging transport must run the Bluetooth lock gate and must be field-tested on real devices before replacing V157 as the approved Bluetooth baseline.

## Release discipline
No later feature update may be considered approved if Bluetooth regresses. If a regression appears, development must stop at that release and restore V157 Bluetooth behavior before continuing.
