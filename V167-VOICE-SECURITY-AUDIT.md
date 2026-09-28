# V167 Voice Verification Security Audit

## Allowed source scope
- `buildsrc/store-app/src/main/java/com/attendpro/store/VoiceSignatureEngine.kt`
- `buildsrc/store-app/src/main/java/com/attendpro/store/VoiceChallengeVerifierV167.kt`
- `buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt`
- `buildsrc/store-app/src/main/AndroidManifest.xml`
- `buildsrc/store-app/src/main/res/xml/file_paths.xml`
- `buildsrc/store-app/src/test/java/com/attendpro/store/VoiceChallengeVerifierV167Test.kt`
- Store/Employee Gradle version metadata (plus Store JUnit test dependency)

## Protected runtime SHA-256 — V166 vs V167
All are byte-identical:
- `PresenceBootReceiver.kt` — `330c8111446fbfeafb4df500f6dfb94ba146ec9c5688b67e3cf4c78fdce74ebd`
- `PresenceService.kt` — `24b78cff4088671d15659ebaa347fa99ef6edaaff4d9ae73440c06c8ce6f6f94`
- `EmployeeReverseBluetoothPresenceServiceV157.kt` — `dcf853efc8628b8de80e206d205c1a247bec270fe4747cf4f61e01ab64fa354b`
- `StoreReverseBluetoothPresenceServiceV157.kt` — `89113fe392417c6b986047cf538a14e45e8adfa01ff3a3106d41fe4ddfd1f8f1`
- `StoreReverseBluetoothBootReceiverV157.kt` — `dcc5a6d9884dc0777ed6d0ebbe1b32816cfbc65d7be609e7b8868e34180d1a6e`
- `ReceiverReportService.kt` — `a6ca2c062874da66210a610778489519cc61af6d3488d139c0956b8c7f404b8f`
- `PairingProtocol.kt` — `fb509472000295cc0c5d1b2455cd961dbeca350f3efb8d45c7191969fbf995c4`

## Security invariants
- Poor audio is rejected; no quality-based threshold reduction.
- Legacy V1/V2 voice templates cannot authorize V167 attendance.
- Enrollment requires five accepted V3 samples plus consistency gate.
- Voiceprint is evaluated before phrase/challenge acceptance.
- Challenge uses two ordered random words.
- Test-only path never calls attendance recording.
- Technical recognition failures do not consume the authentication failure budget.
- Identity/phrase/challenge mismatches do consume the failure budget.
- Existing V166 rate limiting remains active.

## Patch integrity
- V167 source patch SHA-256: `7c7f485142928b298c335f8d57d3461c8ebbb2891d813551558ca1921b3dbec9`
- V167 patch XZ SHA-256: `40e9a47e92fdacbc3835fa3fee7eebdd32011bc42fd25865379973d14630e836`
- V167 patch base64 SHA-256: `3715444240ecbae85f0ce389a1328f7b46dc07d650248ad6641e9c330832f9c3`

## Important limitation
V167 materially hardens the local phrase-dependent speaker check and challenge flow, but it is not claimed to be equivalent to a dedicated certified anti-spoof speaker-verification service. High-risk deployments should combine voice with device/biometric/presence factors rather than treating voice as the sole identity proof.