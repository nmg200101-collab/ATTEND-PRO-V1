# ATTEND-PRO V2.0.0-RC3 — versionCode 93

RC3 is the update candidate intended to be discoverable by installed versionCode 92 Direct builds once the update feed is published.

## User-requested fixes
- Working hours now display in locale-aware 12-hour format with Arabic ص/م or English AM/PM.
- Employee-specific shifts use the same AM/PM time picker instead of separate 0–23 hour fields.
- Overnight shifts are labeled and remain stored internally as 24-hour values.
- The main attendance/fingerprint action area is wider, taller and visually clearer while preserving the compact dashboard.
- System-owner agent credential management now clearly exposes credential status and lets the owner generate a replacement sign-in code that is shown once and can be copied.
- Subscriber authentication is documented accurately as device activation / protected access token rather than a recoverable plaintext password. The system owner can generate a one-time 30-minute recovery credential, view it in full and copy it when a known recovery code is needed.
- Store and Employee user guides include a release-aware “What’s new” section using BuildConfig.VERSION_NAME, so guide content ships with every app update.

## Existing RC2 capabilities retained
- Field-confirmed QR/Bluetooth pairing core remains protected and byte-identical.
- Offline Store → Employee messaging and authenticated Employee → Store offline reply.
- Receiver-phone permissions for reports, employee messaging and selected Store settings.
- Backup/restore, app lock, activation, reports, English/Arabic support and Play-compliance separation.

## Security rule
Existing agent/subscriber secrets are not converted to plaintext storage. One-way hashes and protected tokens remain non-recoverable. Where the owner needs a known credential, ATTEND PRO rotates/generates a new credential and shows it at creation time.

## Validation
RC3 must pass unit tests, Direct/Play lint, protected-pairing hash checks, functional source audit, Direct/Play builds, official signing and real-device checks before Final promotion.
