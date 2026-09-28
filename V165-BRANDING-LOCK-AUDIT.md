# V165 Branding Lock Audit

## Allowed V165 patch paths
1. `buildsrc/core/src/main/java/com/attendpro/core/LocalStores.kt`
2. `buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt`
3. `buildsrc/store-app/src/main/java/com/attendpro/store/StoreSettingsActivity.kt`
4. `buildsrc/store-app/build.gradle.kts`
5. `buildsrc/employee-app/build.gradle.kts` — release metadata only.

CI rejects the V165 source patch if any additional path appears.

## Protected background files checked byte-identical before/after V165
- `buildsrc/store-app/src/main/java/com/attendpro/store/StoreReverseBluetoothPresenceServiceV157.kt`
- `buildsrc/store-app/src/main/java/com/attendpro/store/StoreReverseBluetoothBootReceiverV157.kt`
- `buildsrc/store-app/src/main/java/com/attendpro/store/ReceiverReportService.kt` (includes `ReceiverReportBootReceiver`)
- `buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt`
- `buildsrc/employee-app/src/main/java/com/attendpro/employee/EmployeeReverseBluetoothPresenceServiceV157.kt`
- `buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceBootReceiver.kt`

The existing V164 workflow lock checks for V149 Receiver sources, RC29 pairing/connection sources, V157 wiring, Android manifests, central transport, and background services remain enabled in the V165 workflow.

## Local pre-CI audit
The uploaded V165 source was reconstructed against the effective V164 source and the final patch changes exactly the five allowed paths above. The protected runtime SHA-256 values remained unchanged. The first draft was also corrected so the internal designer keeps its key artwork in the center-safe crop area for wide COVER headers, and COVER selection is rejected unless a valid image exists.
