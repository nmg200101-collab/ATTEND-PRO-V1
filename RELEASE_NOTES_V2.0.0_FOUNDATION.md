# ATTEND-PRO V2.0.0-FOUNDATION Release Notes

## Purpose

This is the first production-engineering foundation for ATTEND-PRO V2. It reorganizes the source
and establishes tested boundaries without adding end-user features or redesigning the current UI.

## Added

- Dedicated Foundation module linked to both Store and Employee builds.
- Connection contracts and authenticated ACK/reconnect state regression rules.
- Constrained SQLite data layer with WAL, typed settings, encrypted payload boundaries, indices,
  activation storage, roles, and migration audit.
- Lossless, transactional, idempotent SharedPreferences staging migration.
- UI-independent repositories with duplicate protection and sync/delivery state.
- Deny-by-default role capabilities for owner, central agent, store manager, and employee.
- Separate cloud/local BLE messaging contracts, persistent delivery status, inbox deduplication,
  replies, and notification boundary.
- Timezone-aware attendance engine for check-in, check-out, lateness, early departure, presence
  proof, allowed methods, and overnight shifts.
- Unit and integration coverage for connection state, data repositories, migration, messaging,
  permissions, and attendance decisions.
- CI gates for protected connection hashes/functions, security configuration, R8 mappings, package
  identity, version identity, zip alignment, official signature, source backup, and SHA-256 manifests.

## Explicitly unchanged

- QR pairing and QR payload behavior.
- BLE discovery/advertising and GATT transfer.
- PairingProtocol, encrypted ACK/confirm, heartbeat, reconnect, presence, GPS, and direct local
  messages.
- Existing Keystore, AES-GCM, PBKDF2, anti-tamper, official signature enforcement, HTTPS-only
  policy, and protected administration screens.
- Current Store and Employee interface behavior.
- Existing user records, activation records, and SharedPreferences.

## Release identity

- Store package: `com.attendpro.store`
- Employee package: `com.attendpro.employee`
- Version code: 90
- Version name: `2.0.0-FOUNDATION`
- Official certificate SHA-256:
  `2fc214199b0c6e86f9cdd9288419fc143a119258c34f6a0ff70eed6aecb6fe59`

## Deployment status

This build is an officially signed Foundation candidate. It must pass Store/Employee installation,
QR, BLE/GATT, ACK/confirm, reconnect, offline messages, and activation-retention tests on real
devices before replacing 1.9.82 as the general-use release. It is not merged to `main` automatically.
