# ATTEND-PRO V166 — Employee Management & Security Hardening

## Scope
V166 is a focused Employee Management release built on the V165 source baseline. It does not alter the locked RC29 connection core, V149 Receiver, V157 Bluetooth dual-path runtime, GPS engine, attendance transport, message transport, activation, backup, or central server transport.

## Employee data continuity
- Fixes the quick-entry -> Advanced Settings handoff so the values entered in the compact employee form are carried into the same employee draft instead of reopening the previous/empty object.
- The draft is not persisted merely by opening Advanced Settings; cancelling does not create an incomplete employee.
- New-draft validation, duplicate employee-ID validation, shifts, methods and existing employee fields remain in the same save path.

## Authentication and readiness
- Separates **Allowed** from **Ready** for employee verification methods.
- Readiness is derived from the actual prerequisites for password, voice, shared-device face, paired-phone biometric/proximity, and external fingerprint reader mapping.
- Adds an employee verification center with explicit test-only operations.
- Voice and face tests do not create attendance/checkout events.
- Voice enrollment/help text is normalized to the actual five-sample implementation.

## Password hardening
- New employee passwords use the existing `CredentialHash1980` PBKDF2-HMAC-SHA256 salted format.
- Legacy password hashes remain verifiable and are upgraded after a successful verification.
- Employee App local credentials support both the new and legacy formats and upgrade legacy hashes after successful verification.
- Repeated authentication failures are throttled; five failures inside a short window cause a temporary lock.

## Verification diagnostics
- Adds readable status for each verification method: disabled/not allowed, allowed but not ready, or ready.
- External fingerprint testing is honest about scope: TCP reachability and employee mapping are checked; a live fingerprint match still requires the configured reader-model driver and is not inferred from a TCP connection.

## Version
- Store App: versionCode 166
- Employee App: versionCode 166
- versionName: `2.0.0-RC29-V166-EMPLOYEE-MANAGEMENT-SECURITY`

## Field acceptance gate
V166 must not be declared field-approved until a real-device test confirms:
1. quick employee data survives opening Advanced Settings;
2. cancelling Advanced Settings does not persist an incomplete draft;
3. save/restart preserves employee data;
4. legacy and new password credentials both verify, with legacy upgrade;
5. lockout activates after repeated failed attempts and clears after the lock period/success;
6. voice five-sample enrollment and test-only verification work without an attendance event;
7. face/liveness test-only verification works without an attendance event;
8. paired-phone test works through the existing locked connection layer;
9. external-reader reachability/mapping diagnostics are accurate;
10. V157 Bluetooth, V149 Receiver, RC29 pairing and previous attendance flows remain regression-free.
