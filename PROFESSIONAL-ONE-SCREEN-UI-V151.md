# ATTEND-PRO V151 — PROFESSIONAL ONE-SCREEN UI

Base:
- V149 RECEIVER FINAL LOCK
- Android base HEAD: bf20500742cbfa71ad10958bb536a94ee9f41956

Goals:
- Preserve the information density and quick access of V149.
- Keep the most important daily options visible on one Store screen.
- Upgrade internal dialogs from plain text lists to professional visual cards.
- Make the app easier for beginners without hiding advanced functionality.

V151 UI scope:
- Store home: compact professional header and quick Store-management access.
- Store attendance method dialog.
- Store presence-proof employee/method selection.
- Store connection center.
- Employee attendance / check-out dialogs.
- Employee pairing center.
- Employee verification center.
- Employee connection status and readiness screens.

Hard locks:
- Receiver section remains byte-identical to V149.
- RC29 field-proven pairing/direct-link files remain immutable.
- No protocol, BLE/GATT, QR, LAN, GPS, attendance engine, activation, server API, or data-format changes.
- No Server deployment is required for V151.

Acceptance:
- Main Store UI remains based on V149.
- Important Store shortcuts are visible without deep navigation.
- Attendance verification options use icon/title/description/status cards.
- Connection diagnostics show beginner-friendly states first; technical details remain available separately.
- Arabic RTL and English LTR remain supported.
- Store and Employee signed APKs must pass CI.
