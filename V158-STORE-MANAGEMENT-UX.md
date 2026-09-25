# ATTEND-PRO V158 — Store Management UX

## Scope
UI-only reorganization of Store Management. No attendance, Bluetooth, Wi-Fi/Hotspot/LAN, Receiver, server, messaging transport, backup engine, pairing protocol, or attendance logic is redesigned.

## Main Store Management sections
1. Store settings
2. Employees and shifts
3. Connectivity and devices
4. Reports and receiving
5. Messages and communication
6. Backup, protection and system

## UX rules
- Group options by the manager's task, not by implementation location.
- Each main section opens an organized list with a short description for every existing option.
- Existing functions are called directly; their internal behavior is unchanged.
- The legacy Advanced Settings view remains available as a compatibility fallback.
- Connectivity section is status/check-only; it does not modify locked Bluetooth/Wi-Fi/LAN logic.

## Protected baselines
- V157 Bluetooth dual-path behavior remains protected.
- V149 Receiver remains protected.
- RC29 connection core remains protected.
- V154 visual behavior outside Store Management remains protected.

## Field gate
CI validates build, lint, signatures and lock markers. Real-device regression remains required before replacing V157 as the functional baseline for any locked behavior.
