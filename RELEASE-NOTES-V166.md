# ATTEND-PRO V166 — Employee Management Core & Security Hardening

V166 is a focused Employee Management release based on the effective V165 source. It does not redesign or modify the locked Bluetooth, Receiver, pairing, GPS, or transport baselines.

## Employee data continuity
- Fixes Quick Add → Advanced Settings so the values typed in the quick form continue into the same employee draft instead of reopening the old/null record.
- A new employee draft is not persisted merely by opening Advanced Settings; canceling does not create a partial employee.
- Advanced save preserves existing face, voice, link secret, status, and other stored employee data unless explicitly changed.

## Readiness and verification center
- Adds a per-employee readiness center that separates **Allowed** from **Ready**.
- Shows readiness for password, voice, face, employee-phone biometric link, phone proximity/QR, and external fingerprint integration.
- Adds test-only password, voice, and face/liveness checks that do not write attendance or checkout events.
- Adds employee-phone link diagnostics.
- External fingerprint testing reports network reader reachability accurately and does not claim a biometric match without a model-specific driver/connector.

## Security hardening
- New/changed employee passwords use the existing PBKDF2-HMAC-SHA256 salted credential format.
- Existing legacy password/voice hashes remain compatible and upgrade after a successful verification where possible.
- Persistent per-employee attempt throttling: 5 failed attempts trigger a 60-second lockout for protected local verification paths.
- Adds a bounded, Android-Keystore-encrypted local employee-security audit trail for sensitive employee-management and verification actions.
- Employee phone local password verification uses the same PBKDF2 compatibility/upgrade path and attempt throttle.

## Face and voice
- Face remains five-pose enrollment with the existing local similarity engine and interactive liveness challenge; V166 adds safe test mode and audit/throttling around the test path.
- Voice remains five-sample enrollment plus voice-print comparison, stored phrase verification, and a random spoken challenge; V166 adds safe test mode, attempt throttling, audit, and hash migration.
- No claim is made that the local face engine is a certified commercial anti-spoofing system.

## Protected baselines
The following are byte-identity locked relative to the effective V165 source and are not modified by the V166 patch:
- V157 Store reverse Bluetooth presence service and boot receiver
- V157 Employee reverse Bluetooth presence service
- Employee PresenceService / PresenceBootReceiver
- V149 ReceiverReportService
- RC29 PairingProtocol

## Field gate
Before V166 becomes the field baseline, test on real devices:
1. Quick employee entry → Advanced Settings retains all typed fields.
2. Save/reopen employee and verify data persistence.
3. Password test success/failure/lockout and legacy migration.
4. Five-sample voice enrollment and test-only voice challenge.
5. Five-pose face enrollment and test-only liveness flow.
6. Employee phone pairing and Android biometric test on the employee phone.
7. External fingerprint reader connectivity with a configured supported reader; biometric-template matching remains vendor-driver dependent.
8. Shift validation, Arabic/English, small screens, restart persistence.
9. Regression: Bluetooth, Receiver, server sync, reports, messages, GPS, and existing attendance functions.
