# V166 Employee Management Security / Lock Audit

## Scope
Allowed source changes are limited to:
- `buildsrc/core/src/main/java/com/attendpro/core/EmployeeSecurityV166.kt`
- `buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt`
- `buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt`
- `buildsrc/store-app/build.gradle.kts`
- `buildsrc/employee-app/build.gradle.kts`

## Protected runtime identity
The effective V165 baseline and V166 working source were SHA-256 compared before packaging:

- `employee-app/.../PresenceBootReceiver.kt` — `330c8111446fbfeafb4df500f6dfb94ba146ec9c5688b67e3cf4c78fdce74ebd`
- `employee-app/.../PresenceService.kt` — `24b78cff4088671d15659ebaa347fa99ef6edaaff4d9ae73440c06c8ce6f6f94`
- `employee-app/.../EmployeeReverseBluetoothPresenceServiceV157.kt` — `dcf853efc8628b8de80e206d205c1a247bec270fe4747cf4f61e01ab64fa354b`
- `store-app/.../StoreReverseBluetoothPresenceServiceV157.kt` — `89113fe392417c6b986047cf538a14e45e8adfa01ff3a3106d41fe4ddfd1f8f1`
- `store-app/.../StoreReverseBluetoothBootReceiverV157.kt` — `dcc5a6d9884dc0777ed6d0ebbe1b32816cfbc65d7be609e7b8868e34180d1a6e`
- `store-app/.../ReceiverReportService.kt` — `a6ca2c062874da66210a610778489519cc61af6d3488d139c0956b8c7f404b8f`
- `core/.../PairingProtocol.kt` — `fb509472000295cc0c5d1b2455cd961dbeca350f3efb8d45c7191969fbf995c4`

All: `LOCK_OK`.

## Security architecture notes
- Employee records remain in the existing repository/vault path; V166 does not introduce a parallel plaintext employee database.
- Credential hashing uses existing `CredentialHash1980` PBKDF2-HMAC-SHA256 salted storage. Legacy hashes are verified through the compatibility path and upgraded on successful verification where the plaintext input is available.
- `EmployeeCredentialGuardV166` stores only hashed subject/method keys plus counters/timestamps; it does not store credentials.
- `EmployeeSecurityAuditV166` uses `SecureTokenVault`, whose payload is protected with the existing Android Keystore-backed vault.
- Test-only face/voice/password paths are explicitly separated from attendance recording.
- External fingerprint `probeTcp` verifies reader transport reachability only. V166 deliberately does not mislabel transport connectivity as biometric-template verification.

## Non-goals / locked subsystems
No changes are authorized or made to V157 Bluetooth dual-path, V149 Receiver runtime, RC29 PairingProtocol, BLE/GATT ACK/Confirm, Wi-Fi/LAN/hotspot transport, GPS engine, central-server transport, reports/messages transport, or background-presence services.
