# ATTEND-PRO V153 — STABILITY + FINAL UI POLISH

Base:
- V152 Messaging Fix + Compact UI Polish
- V149 Receiver Final Lock remains protected

Goals:
1. Give Store Management its own framed inner panel.
2. Replace large attendance option cards with elegant medium horizontal strips.
3. Make Recent Activity open immediately and load history off the UI thread.
4. Reduce non-urgent periodic UI maintenance load without slowing Bluetooth/LAN discovery or receiver live sync.
5. Reduce repeated dashboard event scans by grouping today's events per employee.

Hard locks:
- Receiver V149 files remain byte-identical.
- RC29 field-proven pairing/direct-link files remain immutable.
- No protocol, BLE/GATT, QR, LAN, GPS, attendance engine, activation, server API, or data-format changes.
- V152 local-first messaging behavior remains intact.

Acceptance:
- Store management quick actions are inside a dedicated accent-framed inner panel.
- Attendance verification choices render as medium strips, not a two-column card grid.
- Tapping Recent Activity immediately opens a lightweight dialog and loads the log on a worker thread.
- Non-urgent maintenance is throttled to 15 seconds; discovery/live sync cadence is unchanged.
- Dashboard calculations avoid repeated whole-event scans per employee.
- Store and Employee signed APKs pass CI.
