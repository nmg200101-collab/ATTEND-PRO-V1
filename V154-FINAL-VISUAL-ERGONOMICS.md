# ATTEND-PRO V154 — FINAL VISUAL ERGONOMICS

Base:
- V153 Stability + Final UI Polish
- V149 Receiver Final Lock remains protected

Goals:
1. Compact and raise the visual weight of the ATTEND PRO header so more daily controls fit on screen.
2. Make Store Management unmistakably distinct with a dedicated 2dp accent frame and management badge.
3. Rebuild the main attendance card so the title/description are clear and the Check-in / Check-out buttons are full-width and never clipped.
4. Keep optional Store shortcuts but reduce their visual height.
5. Preserve all V153 stability improvements, including non-blocking Recent Activity and reduced maintenance load.

Hard locks:
- Receiver V149 remains byte-identical.
- RC29 pairing/direct-link files remain immutable.
- No BLE/GATT, QR, LAN, GPS, attendance engine, activation, server API or data-format changes.
- V152 local-first messaging and V153 stability behavior remain intact.

Acceptance:
- Header minimum height is reduced and logo is 40dp with tighter tool row.
- Store Management inner panel uses a 2dp accent stroke and a visible management badge.
- Attendance card uses a compact top information row and a full-width action row.
- Both "تسجيل حضور" and "تسجيل انصراف" remain visible on one line.
- Optional management shortcuts use compact sizing.
- Signed Store and Employee APKs pass CI.
