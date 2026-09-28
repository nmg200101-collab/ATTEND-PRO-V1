# ATTEND PRO V166 — Employee Management Core & Security

## Scope
V166 is limited to Store-side employee management correctness, verification readiness, credential hardening and safe verification testing. It does not change the field-proven RC29 connection core, V149 Receiver runtime, V157 Bluetooth dual-path runtime, Wi-Fi/LAN/hotspot transport, GPS engine, central-server transport, activation or backup protocols.

## Functional corrections
- Quick employee entry now carries the in-progress employee data into **Advanced Settings** instead of reopening the stale pre-edit object.
- A new employee remains a new draft while Advanced Settings are open: employee ID validation and duplicate checks still run as a creation flow, and nothing is persisted until Save.
- Employee details now distinguish **allowed**, **configured** and **tested/ready** verification methods instead of treating a checked option as automatically ready.
- Voice enrollment wording is aligned with the existing five-sample enrollment flow.

## Verification Test Center
A Store-side test center was added for the selected employee. Test operations are explicitly separated from attendance recording.
- Password test.
- Voice phrase test through the existing voice-template/challenge flow.
- Shared-device face test through the existing face template, recognition and liveness flow.
- External fingerprint reader TCP connectivity test, with an explicit limitation: reader connectivity and configured employee ID do not prove biometric template matching without the vendor device driver/API.
- Employee phone link test, with an explicit limitation: authenticated phone connectivity does not claim that the phone biometric itself passed.

A successful TEST_ONLY operation is consumed before attendance commit, so it cannot create an IN/OUT event by mistake.

## Security hardening
- New employee passwords use the existing salted **PBKDF2-HMAC-SHA256** credential format in `CredentialHash1980`.
- Existing legacy password hashes remain compatible and are automatically upgraded after a successful verification.
- Normalized voice challenge hashes use the same salted credential primitive for new enrollments.
- Verification failure throttling is scoped per employee/method. Five failures inside the configured window activate a temporary delay that increases for repeated failures and is capped.
- Verification readiness timestamps and throttle counters contain no password, PIN, voice sample, face template or biometric secret.
- Permission cancellation/denial clears pending TEST_ONLY state so a future verification cannot inherit a stale test.

## Compatibility and lock guarantees
The V166 source patch is restricted to four effective files:
1. `buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt`
2. `buildsrc/store-app/src/main/java/com/attendpro/store/EmployeeVerificationSecurityV166.kt`
3. `buildsrc/store-app/build.gradle.kts`
4. `buildsrc/employee-app/build.gradle.kts`

Protected RC29/V149/V157 runtime sources are required to remain byte-identical by CI.

## Field-device acceptance gate
Before V166 becomes the field baseline, verify on a real Store device:
- Quick employee fields survive transition to Advanced Settings and save correctly.
- Existing employee edit remains correct.
- Password test succeeds/fails without creating attendance and legacy password upgrade remains transparent.
- Voice test uses the enrolled five-sample profile and never creates attendance in TEST_ONLY mode.
- Face test completes recognition/liveness and never creates attendance in TEST_ONLY mode.
- External fingerprint reader test reports only reader connectivity unless a vendor biometric API is available.
- Phone link test does not falsely report biometric success.
- Real attendance still works after the tests.
- Bluetooth V157, Receiver V149, RC29 pairing, Wi-Fi/LAN, GPS and existing reports/messages continue unchanged.
