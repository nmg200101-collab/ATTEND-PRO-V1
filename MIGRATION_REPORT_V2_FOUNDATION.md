# ATTEND-PRO V2.0.0 Foundation Migration Report

## Current status

Foundation schema and migration machinery are implemented, but production data reads and writes are
intentionally still served by the 1.9.82 stores. This release does not delete, rename, reinterpret,
or overwrite existing user data. Runtime migration will be enabled incrementally only after shadow
read comparisons and device testing.

## Strategy

- Copy first: every legacy value is copied to a staging table before domain conversion.
- Rollback safe: legacy SharedPreferences remain untouched.
- Idempotent: a completed source is recorded once and subsequent runs perform no duplicate writes.
- Transactional: copied values and the completion marker commit together or roll back together.
- Lossless: string, integer, long, float raw bits, boolean, null, and string-set types are stored
  explicitly. Values are never reconstructed by guessing from text.
- Auditable: every source records database schema version, completion time, item count, and a
  deterministic SHA-256 checksum.
- Collision safe: keys are scoped by legacy preference source.

## Legacy source inventory

| Priority | Sources | Treatment |
|---|---|---|
| Critical identity/business | `employee_identity`, `store_repository` | Stage, validate, shadow-read, then map to employees/settings/roles |
| Encrypted security | `attend_pro_secure_tokens`, activation data | Preserve ciphertext; decrypt only through existing Keystore/AES-GCM authority |
| Reports/messages | `report_receiver_store`, `attend_employee_local_messages_1977` | Stage before repository conversion; preserve IDs for deduplication |
| Attendance support | late-alert, GPS state, QR replay/deduplication stores | Migrate after identity and connection regression gates |
| UI preferences | appearance, home template, owner/store UI stores | Migrate last; never block attendance or pairing |
| Ephemeral state | active scans, transient pairing sessions, temporary network state | Do not promote blindly; recreate safely after restart |

## Foundation database

The database is `attend_pro_v2.db`, schema version 1. It contains constrained tables and indices for:

- employees and store/branch ownership;
- attendance, verification method, shift window, encrypted evidence, and sync state;
- typed settings;
- encrypted messages, delivery state, inbox ordering, and replies;
- encrypted activation record;
- role assignments;
- staged legacy preferences and per-source migration audit.

Database downgrades and unknown upgrades fail closed. A future schema change must ship an explicit,
tested migration instead of destructive recreation.

## Automated evidence

The migration integration test creates the real Android SQLite schema, copies all supported
SharedPreferences types (including Arabic and delimiter-containing strings), verifies exact
round-trip values, verifies the legacy source remains unchanged, checks idempotency, and validates
the audit count and checksum. Repository integration tests also verify that message bodies and
attendance evidence are not stored as plaintext.

## Activation and signing continuity

- Existing application IDs remain `com.attendpro.store` and `com.attendpro.employee`.
- Version code advances from 89 to 90.
- The workflow requires the same official signing certificate as 1.9.82.
- Encrypted activation bytes are never converted into plaintext by the Foundation data layer.
- No activation or user record is cleared during installation or Foundation initialization.

## Rollback plan

Until a repository is explicitly switched, 1.9.82 remains its source of truth. During later phases,
legacy writes will continue for a bounded compatibility window. If validation fails, the adapter is
disabled and the untouched legacy store resumes immediately. Removing legacy data is outside this
release and requires a separate approved retention plan and verified backup.
