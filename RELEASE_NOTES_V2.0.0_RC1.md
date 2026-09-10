# ATTEND-PRO V2.0.0-RC1 Release Notes

Version code: 91  
Status: release candidate for controlled testing; not the final public V2 release.

## Added

- Password-encrypted store backups on the phone with available face reference files.
- End-to-end encrypted server backup upload and latest-backup restore.
- Restore preview, store binding, protected-field preservation, bounded parsing, and rollback.
- Device-local app lock for both apps with 6–12 digit PIN, optional biometric, background timeout, lock-now action, and temporary lockout.
- Separate direct APK and Google Play AAB variants.

## Platform and compliance

- Compile/target SDK 36 (Android 16).
- Android Gradle Plugin 8.10.1 and Gradle 8.11.1.
- Play variants remove `REQUEST_INSTALL_PACKAGES` and hand updates to Google Play.
- Direct variants retain the signed SHA-256-verified updater.
- Official signature verification, R8, HTTPS-only networking, protected admin screens, Keystore, and anti-tamper remain enabled.

## Protected compatibility

QR pairing, BLE discovery, GATT, PairingProtocol, ACK/confirm, heartbeat, reconnect, and local messaging files remain byte-identical to the approved lock. CI verifies hashes and protected functions before and after build.

## Server

Cloudflare branch `attend-pro-v2-backup-foundation` adds device-signed backup endpoints, opaque D1 storage, five-version retention, rate limits, replay protection, store binding, and audits. It is not merged or deployed automatically.

## Before final release

- Complete CI tests, lint, signed APK/AAB build, and certificate checks.
- Complete physical-device and overnight-shift regression.
- Complete staging recovery and restore drills.
- Review Play declarations for background location, foreground services, data safety, and reviewer access.
- Promote only after explicit approval; never merge automatically to `main`.
