# ATTEND-PRO — ALL APPROVED SECTIONS FINAL LOCK (V157)

Status: FIELD-APPROVED / COMPREHENSIVE FUNCTIONAL LOCK
Lock date: 2026-09-25
Functional anchor commit: 575f4dddc77ae025574caba3321d6990e102c216
Current protected baseline: ATTEND-PRO V157 — Bluetooth Dual-Path Stability

## Owner directive
All functionality approved up to V157 is frozen by default. No future update may alter a protected functional area unless the project owner directly requests that exact change and is shown a warning before the protected code is modified.

## Protected areas

### 1. Receiver / report phone — V149 lock
- Receiver pairing and unlinking
- Multi-store selection
- Automatic report reception
- Live Mirror
- Present employees
- Connected devices
- Daily report
- Last movement
- Presence proof requests
- Receiver-to-employee messaging
- Server / BLE / LAN receiver transports
Reference: RECEIVER-FINAL-LOCK-V149.md

### 2. Bluetooth / BLE — V157 lock
- Pairing
- BLE advertising
- BLE scanning
- authenticated ACK/GATT
- direct link
- dual-path Bluetooth recognition
- background restart / boot recovery
- propagation of the actual Bluetooth connection state to the UI
Reference: BLUETOOTH-FINAL-LOCK-V157.md

### 3. Wi-Fi / Hotspot / LAN
- network presence discovery
- Wi-Fi/Hotspot fallback
- LAN ACK / confirm
- local message transport
- nearby report delivery
- automatic fallback ordering
Bluetooth must never be disabled merely because Wi-Fi/Hotspot is available.

### 4. Server connectivity
- central activation and recovery
- server heartbeat/presence
- secure resilient HTTP/DNS transport
- attendance synchronization
- receiver live mirror
- message history and delivery
- backup upload/download

### 5. GPS
- GPS proximity/observation behavior
- geofence state
- GPS remains observation only and is not an identity proof or Bluetooth replacement.

### 6. Attendance and proof
- check-in / check-out
- allowed verification methods
- device biometric/password flows
- presence proof request and response
- immediate attendance synchronization

### 7. Messaging — V156 accepted behavior
- Store/Employee conversation history
- old and new messages
- local-first delivery
- Bluetooth/GATT direct messaging where available
- LAN/Hotspot local messaging
- server fallback/history
- receiver messaging
- fast polling and local persistence

### 8. Store UI / Employee UI approved behavior
- V154 visual ergonomics
- V153 stability/performance work
- navigation behavior
- Arabic/English behavior already approved
- dark mode and layout behavior already approved

### 9. Backup / restore / app lock / update
- encrypted phone backup
- encrypted server backup
- rollback-safe restore
- app lock
- Direct updater verification: HTTPS, SHA-256, package, signer and version code
- Play update path

### 10. Security and protected connection core
- RC29 protected connection files
- signing/integrity policy
- activation security
- pairing secrets and authenticated protocols
Reference: CONNECTION_CORE_FINAL_LOCK_RC29.md

## Locked repository scope
The comprehensive CI lock treats the following as protected functional code:
- buildsrc/**
- .source_patches/**
- tools/**

A change to any of those paths after the V157 functional anchor is rejected unless the modifying commit carries BOTH exact trailers:

USER-DIRECT-APPROVAL: YES
LOCK-WARNING-SHOWN: YES

The commit must also include a LOCK-SCOPE trailer describing the specifically authorized section.

## Mandatory warning before an override
Before any protected change, the owner must be told:
1. which locked section will be modified;
2. why the change is necessary;
3. which already-working functions could regress;
4. that V157 / V149 / RC29 behavior remains the rollback reference;
5. that CI success alone does not certify Bluetooth/GPS/biometric/nearby behavior and a real-device regression test is required.

## Authorization rule
A general request such as "continue", "improve the app", "update everything", or "do what is suitable" is NOT authorization to alter a locked section.
Authorization must name or clearly describe the protected behavior to be changed.
If the requested work can be done without touching a protected area, the protected area must remain unchanged.

## Release rule
No later release supersedes this baseline for a protected section until:
- the owner explicitly authorized the change;
- the warning was shown;
- CI passed;
- the affected real-device field test passed;
- the owner explicitly approved the new behavior.

If a regression occurs, stop feature work and restore the last field-approved locked behavior before continuing.
