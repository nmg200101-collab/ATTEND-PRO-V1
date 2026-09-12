# ATTEND-PRO V2.0.0-RC4 — versionCode 94

RC3 is the update candidate intended to be discoverable by installed versionCode 92 Direct builds once the update feed is published.

## User-requested fixes
- Working hours now display in locale-aware 12-hour format with Arabic ص/م or English AM/PM.
- Employee-specific shifts use the same AM/PM time picker instead of separate 0–23 hour fields.
- Overnight shifts are labeled and remain stored internally as 24-hour values.
- The main attendance/fingerprint action area is wider, taller and visually clearer while preserving the compact dashboard.
- System-owner agent credential management clearly exposes credential status and lets the owner generate a replacement sign-in code that is shown once and can be copied; no stored password hash is reversed.
- Subscriber management now contains “Access and recovery / بيانات الدخول والاستعادة”. The permanent central access token is not displayed; the owner can issue a purpose-limited recovery code that is short-lived, server-hashed, invalidates the previous unused grant and is consumed once.
- Store and Employee user guides include a release-aware “What’s new” section using BuildConfig.VERSION_NAME, so guide content ships with every app update. The RC3 guide covers AM/PM and overnight shifts, the larger attendance card, offline replies, receiver-phone permissions, agent credentials, subscriber recovery and in-app update behavior.

## Existing RC2 capabilities retained
- Field-confirmed QR/Bluetooth pairing core remains protected and byte-identical.
- Offline Store → Employee messaging and authenticated Employee → Store offline reply.
- Receiver-phone permissions for reports, employee messaging and selected Store settings.
- Backup/restore, app lock, activation, reports, English/Arabic support and Play-compliance separation.

## Security rule
Existing agent/subscriber secrets are not converted to plaintext storage. One-way hashes and protected tokens remain non-recoverable. Where the owner needs a known credential, ATTEND PRO rotates/generates a new credential and shows it at creation time.

## Validation
RC3 must pass unit/integration/regression tests, Direct/Play lint, protected-pairing hash checks, functional source audit, Direct/Play builds, official signing and real-device checks before Final promotion. The server recovery-credential contract additionally verifies owner-only issuance, hash-only persistence, rotation invalidation, one-time consumption and audit logging without Production deployment.


## RC4 final correction batch
- Bluetooth Direct/GATT authenticated connection now appears in connected-device status even without Wi-Fi/Hotspot broadcasting. Discovery alone never counts as connected.
- Home layouts: Main, Sections, Classic (original full dashboard), and Focus. Classic no longer duplicates Main.
- Store Manager settings can be opened all together or one option at a time.
- Check for app updates is available directly in the top three-dot menu.
- Direct keeps the verified internal updater; Play keeps Play In-App Updates.
- Protected pairing protocol files remain unchanged.
