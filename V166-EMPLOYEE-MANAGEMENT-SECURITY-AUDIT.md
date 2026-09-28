# V166 Employee Management & Security Audit

## Baseline
Source baseline: ATTEND-PRO V165 — Store Branding Modes Designer.

## Allowed source-change scope
Exactly four runtime/build files are changed by the V166 source patch:
- `buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt`
- `buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt`
- `buildsrc/store-app/build.gradle.kts`
- `buildsrc/employee-app/build.gradle.kts`

## Locked runtime verification
The following files were byte-compared with V165 and are unchanged:
- Employee `PresenceBootReceiver.kt` — `330c8111446fbfeafb4df500f6dfb94ba146ec9c5688b67e3cf4c78fdce74ebd`
- Employee `PresenceService.kt` — `24b78cff4088671d15659ebaa347fa99ef6edaaff4d9ae73440c06c8ce6f6f94`
- Employee `EmployeeReverseBluetoothPresenceServiceV157.kt` — `dcf853efc8628b8de80e206d205c1a247bec270fe4747cf4f61e01ab64fa354b`
- Store `StoreReverseBluetoothPresenceServiceV157.kt` — `89113fe392417c6b986047cf538a14e45e8adfa01ff3a3106d41fe4ddfd1f8f1`
- Store `StoreReverseBluetoothBootReceiverV157.kt` — `dcc5a6d9884dc0777ed6d0ebbe1b32816cfbc65d7be609e7b8868e34180d1a6e`
- Store `ReceiverReportService.kt` — `a6ca2c062874da66210a610778489519cc61af6d3488d139c0956b8c7f404b8f`
- Core `PairingProtocol.kt` — `fb509472000295cc0c5d1b2455cd961dbeca350f3efb8d45c7191969fbf995c4`

## Security design checks
- PairingProtocol is not modified.
- New employee-password creation uses `CredentialHash1980` rather than fast unsalted hashing.
- Backward-compatible verification avoids invalidating deployed employee credentials.
- Authentication throttling is applied to the employee authentication/test surface.
- Authentication guard keys use SHA-256-derived identifiers so Arabic/non-ASCII employee IDs cannot collide through lossy normalization.
- Voice/face verification-center operations are test-only and do not invoke attendance recording on a successful test.
- Allowed and Ready are distinct states; enabling a method does not by itself assert that the method is usable.
- External fingerprint TCP reachability is not misrepresented as a biometric match.

## Patch integrity
- Raw V166 source patch SHA-256: `ec4495b3bd713d206e2153db4b6cbb11b00e51726cfc894d3450711adbfc9557`
- V166 source patch base64/XZ SHA-256: `a680128c736766944559db1a1b9687838899670a635a40630ffeefbb2ca8720a`

## Release gate
Automated CI must verify patch allowlist, patch hashes, protected-runtime snapshots, unit tests, Android Lint, Direct APKs, Play AABs, official signing certificate, package names and version 166 before artifacts are accepted. Real-device field approval remains a separate final gate.
