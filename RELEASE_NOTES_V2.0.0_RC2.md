# ATTEND-PRO V2.0.0-RC2 PLAY-COMPLIANCE

Release candidate for internal and field testing only. Not a final production release.

## Changes
- Version code raised from 91 to 92.
- Google Play variants now route update checks through Google Play In-App Updates.
- Direct variants retain the verified HTTPS APK updater with package, signer and SHA-256 validation plus explicit user approval.
- Employee Play variant declares enterprise employee-monitoring classification and uses an explicit ongoing monitoring notification.
- Employee location permission flow now shows a prominent data-use disclosure before the Android runtime location prompt.
- Store shift-time selection displays 12-hour Arabic/English clock notation while preserving existing 24-hour persisted values.
- Overnight shifts such as 10:00 PM to 6:00 AM remain supported; equal start/end values are rejected.
- Added regression coverage for AM/PM conversion, midnight/noon, normal shifts and overnight shifts.

## Protected areas
RC2 is required by CI to be byte-for-byte unchanged from RC1 for PairingDiscovery, PairingBeacon, BleDirectProtocol, BleProtocol, PairingAckProtocol and PairingProtocol. Existing backup, app-lock, activation, anti-tamper and signature verification are retained.

## Not yet final
Real-device Samsung/Motorola pairing regression, internal Play update testing, active privacy/deletion URLs, full bilingual resource migration and the expanded offline guide must pass before final promotion.
