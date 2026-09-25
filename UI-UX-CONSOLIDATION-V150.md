# ATTEND-PRO V150 — FINAL UI/UX CONSOLIDATION

Base:
- V149 RECEIVER FINAL LOCK
- Android base HEAD: bf20500742cbfa71ad10958bb536a94ee9f41956

Goal:
Improve clarity, visual hierarchy, ease of use and responsive layout without changing working attendance/connectivity engines.

Allowed V150 scope:
- Store main screen visual hierarchy and spacing.
- Employee main screen visual hierarchy and daily-use simplification.
- Store Management dashboard organization.
- Reports screen filtering and presentation.
- Text/labels that improve clarity.
- Responsive layout refinements.

Hard locks:
- Receiver section remains exactly as V149.
- RC29 field-proven pairing/direct-link files remain immutable.
- No changes to Bluetooth pairing, QR pairing, BLE/GATT direct link, PresenceService, LAN protocol, GPS engine, attendance verification engine, server protocol, or receiver server APIs.
- No feature additions outside UI/UX.
- No change to stored employee, activation, report, or attendance data formats.

Acceptance:
- Store daily screen presents live state first, attendance second, day summary third, management last.
- Employee screen presents profile/connection first, attendance as primary action, daily-use shortcuts only.
- Arabic RTL and English LTR remain supported.
- Compact/organized layouts remain responsive.
- V149 receiver files are byte-identical to the V149 baseline.
- Signed Store and Employee APKs must pass CI before field testing.
