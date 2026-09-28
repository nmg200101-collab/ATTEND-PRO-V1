# V166 Employee Management Security Audit

## Baseline
- Effective baseline: ATTEND PRO V165 Store Branding Modes Designer.
- V166 branch: `attend-pro-v166-employee-management-security`.
- Version code: `166`.
- Version name: `2.0.0-RC29-V166-EMPLOYEE-MANAGEMENT-SECURITY`.

## Patch integrity
- Effective patch SHA-256: `22fa3bfd7b57d9eb50ff58b62f9cb801bc8104b806205e40ecefccf15a079571`
- XZ patch SHA-256: `b3a841ebfcec0b752b2e0b2bb9325bf08fa907167e41bcb6cce77791b6299096`
- Base64-XZ patch SHA-256: `59c1af5916760f346d61e37dce06fcd566aa977990cf962a838011ba46d3dbb9`

## Allowed effective source paths
- `buildsrc/employee-app/build.gradle.kts`
- `buildsrc/store-app/build.gradle.kts`
- `buildsrc/store-app/src/main/java/com/attendpro/store/EmployeeVerificationSecurityV166.kt`
- `buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt`

CI rejects any V166 patch path outside this exact allowlist.

## Protected runtime byte lock — V165 → V166
The local pre-CI comparison reported byte-identical SHA-256 values:
- `employee/PresenceBootReceiver.kt`: `330c8111446fbfeafb4df500f6dfb94ba146ec9c5688b67e3cf4c78fdce74ebd`
- `employee/PresenceService.kt`: `24b78cff4088671d15659ebaa347fa99ef6edaaff4d9ae73440c06c8ce6f6f94`
- `employee/EmployeeReverseBluetoothPresenceServiceV157.kt`: `dcf853efc8628b8de80e206d205c1a247bec270fe4747cf4f61e01ab64fa354b`
- `store/StoreReverseBluetoothPresenceServiceV157.kt`: `89113fe392417c6b986047cf538a14e45e8adfa01ff3a3106d41fe4ddfd1f8f1`
- `store/StoreReverseBluetoothBootReceiverV157.kt`: `dcc5a6d9884dc0777ed6d0ebbe1b32816cfbc65d7be609e7b8868e34180d1a6e`
- `store/ReceiverReportService.kt`: `a6ca2c062874da66210a610778489519cc61af6d3488d139c0956b8c7f404b8f`
- `core/PairingProtocol.kt`: `fb509472000295cc0c5d1b2455cd961dbeca350f3efb8d45c7191969fbf995c4`

CI independently snapshots protected sources immediately after V165 reconstruction, applies V166, then verifies the snapshot byte-for-byte.

## Security design observations
- V166 does not claim that any biometric method is impossible to bypass.
- Password storage uses the project PBKDF2 credential implementation with per-value salt and legacy migration.
- Face and voice tests reuse the existing verification engines but use TEST_ONLY state that is consumed before event commit.
- External fingerprint testing is deliberately limited to reader connectivity/configuration unless the reader vendor exposes a template-match API.
- Employee phone link success is not treated as proof that Android device biometrics passed.
- Throttle state/test timestamps are non-secret sidecar state and do not contain biometric material.

## Automated gates required
- V165 baseline reconstruction and existing RC29/V149/V157 locks pass first.
- V166 patch SHA and exact path allowlist pass.
- V166 version/marker checks pass.
- Protected V166 runtime snapshot remains byte-identical.
- Unit tests, Android Lint, Direct APK builds and Play AAB builds pass.
- Official signing certificate, package names, version code and version name verify successfully.
