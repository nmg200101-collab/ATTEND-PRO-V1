# ATTEND-PRO 1.9.66 — GPS / Attendance / Voice

- Added missing central-server employee phone link endpoints.
- Added GPS observation upload/status bridge with INSIDE/NEAR/OUTSIDE, distance, accuracy and timestamp.
- GPS remains presence evidence, not identity proof.
- Added server presence challenge create/poll/complete flow.
- Added employee-phone attendance submit + store attendance pull.
- GPS local sampling: 10s; server telemetry throttle: 20s.
- Store dashboard labels GPS as a recognition channel when fresh.
- Voice verification uses 5 enrollment samples already present in 1.9.65 and now applies an adaptive voice-print threshold based on capture/enrollment quality; spoken phrase and challenge remain required.
- Existing owner per-employee allowed methods, voice enrollment, attendance actions and voice announcement controls are preserved.
- Existing BLE/LAN/QR connection core was not rewritten.

## Final hardening pass
- GPS wording now reflects actual phone-presence recognition rather than “monitor only”.
- GPS remains presence evidence and cannot independently prove employee identity or create attendance.
- Employee attendance API now enforces the store owner's allowed methods for each employee.
- Server rejects GPS-only attendance and stale/future attendance timestamps outside the accepted window.
- Existing BLE / direct Bluetooth / LAN / QR pairing paths were left intact.
