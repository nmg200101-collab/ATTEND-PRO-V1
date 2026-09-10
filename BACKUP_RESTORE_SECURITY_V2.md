# ATTEND-PRO V2 Backup and Restore Security

Status: `2.0.0-RC1` implementation contract. This is not a declaration of final production acceptance.

## Scope

- Phone backup: encrypted business data plus available reference images under `filesDir/face_profiles`.
- Server backup: encrypted business data and biometric templates without reference image files, capped at 1.5 MB.

Both contain employee records, attendance records, merchant settings, report-receiver data, appearance, and dashboard layout. The current live V1-compatible stores remain the source during the gradual V2 migration.

## Cryptography

- Format `ATTEND-PRO-ENCRYPTED-BACKUP`, version 1.
- PBKDF2-HMAC-SHA256, 310,000 iterations, random 128-bit salt.
- AES-256-GCM with a random 96-bit IV.
- GZIP before encryption with bounded decompression.
- Authenticated metadata includes format, version, backup ID, role, time, source-store hash, compression, KDF, iteration count, and cipher.
- The password has at least eight characters, is processed only on Android, and is never uploaded.

The server stores an opaque authenticated envelope and cannot read its contents.

## Deliberately excluded

- Android Keystore keys and the device signing private key.
- Central access token, activation poll secret, and system-owner API key.
- Central license/lease state, target server URL, and device/store identity.
- App-lock PIN, biometric setting, and in-memory unlock session.
- Admin/owner sessions, failed-attempt counters, replay nonces, and transient sync cursors.

Employee pairing material remains inside the encrypted employee payload so paired phones continue to work after approved same-store recovery. It is never sent as plaintext.

## Restore rules

1. Envelope, metadata, role, sizes, types, employee IDs, file names, and SHA-256 values are validated before writes.
2. AES-GCM authentication must succeed before parsing.
3. The encrypted source-store hash and plaintext source store ID must match.
4. The active target store ID must equal the source store ID; a different store is rejected.
5. A new phone must first complete the existing approved activation transfer.
6. Device-bound activation values are preserved.
7. Preference changes, employee payload, and face files keep rollback snapshots; failures restore prior values.
8. The app restarts after success so repositories and screens reload data.

## Server authorization and retention

- Active store bearer token.
- P-256 device signature over method, path, time, nonce, and body SHA-256.
- Five-minute request window and one-time nonce.
- Store binding in both device proof and backup envelope.
- API rate limiting, audit records, size limits, and five retained versions per store.

Server changes are isolated on `attend-pro-v2-backup-foundation`; CI and explicit production approval are required. There is no automatic merge to `main`.

## Required acceptance before V2 final

- Real-device restore using production-like data.
- Same-device restore, approved replacement device, wrong-store, wrong-password, and corrupt-file cases.
- Server retention, replay, authorization, outage, and size-limit tests against non-production D1.
- Post-restore QR/BLE/GATT/ACK/heartbeat/reconnect regression.
- Documented recovery drill, RPO/RTO, and owner-held password procedure.
