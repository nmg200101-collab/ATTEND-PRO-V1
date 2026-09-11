# ATTEND-PRO V2.0.0-RC2 PLAY-COMPLIANCE

Release candidate for internal and field testing only. Not a final production release.

## Changes in the current RC2 candidate
- Version code 92 / target SDK 36.
- Google Play variants use Google Play In-App Updates; Direct variants retain the verified HTTPS updater with package, signer and SHA-256 validation plus explicit user approval.
- Employee Play variant keeps the enterprise employee-monitoring classification, explicit ongoing monitoring notification and prominent background-location disclosure.
- Store shift selection uses Arabic/English 12-hour display while preserving 24-hour persisted values and overnight shifts.

## Field-pairing preservation
The pairing build was confirmed working on real devices before this update. The following protected connection files remain byte-for-byte locked against the approved baseline:
- PairingDiscovery
- PairingBeacon
- BleDirectProtocol
- BleProtocol
- PairingAckProtocol
- PairingProtocol

The new offline Employee -> Store reply channel is implemented on a separate authenticated GATT characteristic. It does not replace or modify the protected pairing/Heartbeat/ACK command channel.

## Home and Classic UI
- Fixed the Classic-home freeze by removing the heavy legacy route from the Classic selector.
- Main and Classic layouts now use the lightweight dashboard engine.
- Compact ATTEND PRO logo/header is first.
- Store Manager Settings is directly below the header.
- Attendance, connection status and today's summary are arranged to fit a normal phone screen with scrolling retained only as a small-screen accessibility fallback.

## Offline messaging
- Store -> Employee local Bluetooth messaging remains available without Internet.
- Employee -> Store replies can now be sent without Internet while the authenticated BLE/GATT session is active.
- Local Employee replies are authenticated, reassembled and stored in the Store message inbox.
- Server fallback remains available when Bluetooth is unavailable and Internet exists.

## Receiver / management phone
- Receiver phones now have independent Store-controlled capabilities:
  - Receive reports
  - Message employees
  - Manage selected Store settings remotely
- A newly authorized receiver defaults to Reports only.
- Store Management can grant/revoke each capability independently, disable the phone, or remove it.
- The server enforces capabilities; hiding a UI button is not the security boundary.
- A permitted receiver can message employees through the server and Employee replies are routed back to that receiver.
- A permitted receiver can edit selected operational settings: working hours, grace period, selected voice settings, geofence alerts and automatic sync.
- Remote Store changes use a revision number. The authorized Store device pulls, validates, applies and acknowledges the revision.
- Owner/system secrets and master administration authority are not delegated to receiver phones.

## User guide and language
- Store and Employee guides were rebuilt around practical use cases: choose what you want to do -> choose a step -> open full instructions.
- Added detailed guidance for pairing, daily attendance, offline messaging, receiver permissions, backups and troubleshooting.
- Expanded English/LTR handling across the primary dashboard, shared UI components, Store Management and System Administration surfaces.
- A migration bridge translates remaining legacy shared labels while older secondary diagnostic/admin text continues to be audited; do not claim 100% English coverage in the Play listing until the final legacy audit is cleared.

## Server
Receiver permissions, messaging and remote Store-control APIs are implemented and tested on the server branch. CI and Wrangler dry-run pass. Production deployment is intentionally not performed from this RC task without explicit authorization.

## Validation completed by CI
- Core/foundation unit tests
- Direct + Play lint for Store and Employee
- pairing-lock guard and protected SHA checks
- Direct and Play builds
- official signing
- Play manifest/update-policy checks
- source/release packaging
- server receiver-permission, messaging, reply-routing and remote-setting tests

## Not final
Promotion to Final still requires real-device regression of Classic switching, offline Employee reply, receiver permissions/remote control, Samsung/Motorola connection regression and internal Play update testing.
