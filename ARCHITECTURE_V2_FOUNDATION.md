# ATTEND-PRO V2.0.0 Foundation Architecture

## Release identity

- Product: ATTEND-PRO Store + ATTEND-PRO Employee
- Target: `2.0.0-FOUNDATION` (`versionCode 90`)
- Protected source baseline: `b651ff153f82ed740109a76474b2e52708686fa0`
- Field pairing reference: ATTEND-PRO 1.9.70 STABLE BASE
- Branch: `attend-pro-v2.0.0-foundation`
- Automatic merge to `main`: forbidden

## Safety boundary

This release uses a strangler migration. The working 1.9.82 application remains the runtime
implementation while the V2 architecture is introduced beside it. No protected QR, BLE, GATT,
pairing protocol, ACK/confirm, heartbeat, reconnect, presence, or direct local-message source is
edited. The six protected files and eighteen protected functions are checked before tests, after
tests, and after both APK builds.

The Foundation module is linked as a dependency but is not allowed to replace a production path
until its adapter passes automated regression tests and real-device Store/Employee testing.

## Module boundaries

| Module | Responsibility | May depend on |
|---|---|---|
| `core` | Field-proven V1 protocols, security primitives, legacy models | Android/runtime libraries |
| `foundation` | V2 domain, connection contracts, SQLite, repositories, roles, messaging, attendance | `core`, Android database APIs |
| `store-app` | Store UI and existing runtime orchestration | `core`, `foundation` |
| `employee-app` | Employee UI and existing runtime orchestration | `core`, `foundation` |

UI classes must not become dependencies of `foundation`. Transport implementations must enter V2
through adapters; repositories and domain engines must not reference Activities, Views, BLE APIs,
or network clients.

## Foundation layers

### Connection

- Transport-neutral identities for BLE/GATT, QR, presence, GPS, LAN, and cloud.
- An explicit state machine covers discovery, authentication, ACK, connection, disconnect, and
  reconnect.
- A peer cannot move directly from discovered to connected; authenticated flows must pass the ACK
  boundary.
- Existing V1.9.70/V1.9.82 transport classes remain authoritative until adapter migration.

### Data

- SQLite with write-ahead logging, foreign keys, busy timeout, secure deletion, constraints, and
  purpose-specific indices.
- Tables: employees, attendance, settings, messages, activation, role assignments, staged legacy
  preferences, and migration state.
- Enum names are stored as text, never ordinals.
- Attendance evidence and message bodies require an injected cipher; there is no plaintext
  repository fallback.
- Activation records are accepted only as already-encrypted bytes from the existing security layer.

### Repository

- UI-independent contracts and SQLite implementations for employees, attendance, settings,
  messages, activation, and roles.
- Duplicate attendance and message IDs are rejected rather than overwritten.
- Unsynced attendance has a bounded query path and explicit acknowledgment.
- Repository writes use parameter binding and transactions where an update-or-insert sequence is
  required.

### Authorization

- Roles: system owner, central agent, store manager, and employee.
- Capabilities are explicit and deny-by-default for every role except system owner.
- Central agents cannot read attendance reports or open store administration.
- Employees can record only their own attendance and cannot obtain management capabilities.

### Messaging

- Cloud and local BLE transports are separate contracts.
- The requested channel is honored exactly; a later fallback policy cannot be introduced silently.
- Outgoing messages are persisted before transport and keep a queued/sending/sent/failed state.
- Incoming messages are idempotent by ID, preventing duplicate inbox rows and notifications.
- Replies use an explicit parent message ID.

### Attendance

- Check-in, check-out, lateness, early departure, and presence proof are domain decisions outside
  the UI.
- Employee active status, the owner-selected verification method, and proof verification are checked
  before an event is accepted.
- Shift resolution is timezone-aware and supports overnight work plus a configurable checkout tail.
- Clock calculations use calendar dates rather than adding a fixed 24 hours, avoiding daylight-saving
  errors when the product is used outside Yemen.

## Security continuity

### V2.0.0-RC1 backup and UI-lock boundary

- `foundation.backup.EncryptedBackupCodec` owns the portable password-encrypted envelope and has no UI dependency.
- `foundation.backup.StoreBackupManager` adapts the current live stores into validated snapshots during the gradual SQLite cutover.
- `CentralServerClient` transports only opaque envelopes through device-signed HTTPS requests.
- The Cloudflare V2 wrapper enforces active-store authorization, binding, replay protection, and five-version retention.
- `AppLockManager` owns local policy; `AttendProApplication` observes real background transitions; `AppLockGateActivity` owns authentication UI.
- App-lock state and activation credentials stay outside the backup boundary.
- Store and Employee launchers are thin app-specific subclasses of the common gate.

The existing Android Keystore, AES-GCM, PBKDF2, signature verification, anti-tamper checks,
HTTPS-only policy, R8/resource shrinking, and protected administration screens remain the runtime
security authority. The release workflow refuses to publish APKs unless both packages:

1. build as minified release APKs;
2. pass zip alignment and APK Signature Scheme v2/v3 verification;
3. match certificate SHA-256
   `2fc214199b0c6e86f9cdd9288419fc143a119258c34f6a0ff70eed6aecb6fe59`;
4. report the release-specific version values enforced by its CI workflow.

## Incremental migration sequence

1. Foundation contracts and tests, with no runtime replacement.
2. Read-only connection adapters around stable implementations.
3. Copy legacy preferences into lossless staging tables; retain legacy data.
4. Compare legacy and V2 reads, then switch one repository at a time.
5. Move inbox/outbox orchestration, keeping local and cloud channels isolated.
6. Move attendance decisions behind the engine and compare results in shadow mode.
7. Remove a legacy access only after rollback, migration, CI, and real-device gates pass.

## Release gates

- Clean build of both debug and minified release variants.
- Core, Foundation, migration, repository, messaging, role, connection-state, and overnight-shift
  tests pass.
- Protected hashes and function guards pass three times.
- Official certificate, package IDs, version code, and version name are verified from signed APKs.
- Source, architecture, migration report, release notes, mappings, manifest, and SHA-256 inventory are
  generated from the exact workflow commit.
- Store and Employee real-device pairing regression remains mandatory before this Foundation can
  replace 1.9.82 for general use.
