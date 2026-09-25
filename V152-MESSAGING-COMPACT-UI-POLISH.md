# ATTEND-PRO V152 — MESSAGING FIX + COMPACT UI POLISH

Base:
- V151 Professional One-Screen UI
- V149 Receiver Final Lock remains protected

Goals:
1. Fix Store and Employee messaging screens so they open immediately from local data and refresh from the server in the background.
2. Remove the redundant Store tab strip: Home / Attendance / Connection / Administration.
3. Compact Store attendance verification options into a two-column grid.
4. Visually distinguish the Store management card with a dedicated accent frame.
5. Improve the ATTEND PRO header/logo composition without increasing vertical space.

Hard locks:
- Receiver V149 files remain byte-identical.
- RC29 field-proven pairing/direct-link files remain immutable.
- No change to BLE/GATT, QR pairing, LAN, GPS, attendance verification engines, activation, server APIs, or attendance/report data formats.
- CentralServerClient remains unchanged in V152.

Acceptance:
- Store Messages screen renders immediately before any network response.
- Employee Messages screen renders local content immediately before network refresh.
- No call to addStoreTabs1978(root) remains in active Store layouts.
- Attendance method selector uses compactGrid = true.
- Store owner/admin card has an accent border.
- Store logo is framed and enlarged modestly without increasing header height.
- Store and Employee APKs pass CI and signing.
