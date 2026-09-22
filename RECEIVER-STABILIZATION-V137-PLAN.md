# ATTEND-PRO V137 — Receiver & Reports Stabilization Plan

Baseline:
- Android V136: 953df7986887e0b46b5073fe491d23ae7127bc01
- Field-proven pairing/BLE baseline remains RC29.
- Do not change protected pairing/BLE/GATT files.

## Goal
Rebuild the report-receiver area as one stable, simple workflow without changing successful attendance, pairing, BLE, GPS, activation, or employee-app behavior.

## Architecture rules
1. One receiver domain with a single source of truth for:
   - receiver registration/link state
   - three permissions: reports, employee messaging, employee management
   - server reachability and last refresh
2. Receiver phone never gets Store/System settings.
3. Employee-management commands use server queue -> Store device apply -> ACK.
4. No receiver operation may mutate pairing/employee_links directly.
5. No full Activity rebuild from background callbacks while a modal/dialog is open.
6. Network I/O never runs on the UI thread.
7. Only one refresh/sync operation per domain at a time; cancel/ignore stale results.
8. Prefer IPv4 for central-server fallback on networks with broken IPv6.
9. All user-facing network errors are Arabic/English friendly; raw socket/IP errors are diagnostic only.

## UI model
Store side:
Reports -> Receiver phones
- Overview
- Phones
- Add phone
- Permissions
Each phone card:
- linked/disabled state
- last server sync
- three explicit permission toggles
- save + verified server result
- disable/remove

Receiver phone:
- Status
- Reports
- Messages
- Employees
Only tabs/functions allowed by granted permissions are visible.
Employee management is a full stable screen, not nested AlertDialog chains.

## Employee-management state
Command states:
PENDING -> DISPATCHED -> APPLIED / FAILED
Receiver shows:
- Pending
- Applied
- Failed with retry guidance
Store app:
- publishes employee snapshot
- pulls commands serially
- applies idempotently
- ACKs each command
- republishes snapshot after changes

## Stability work
- Replace repeated buildUi()/setContentView calls from async callbacks with explicit render/update methods.
- Preserve selected tab and screen state across refreshes.
- Add in-flight guards and generation tokens for capabilities, reports, messages, and employees.
- Move periodic receiver employee sync away from the 5-second dashboard refresh loop.
- Use a slower bounded cadence plus immediate sync on network return / app resume / command action.
- Prevent overlapping Store sync, activation validation, and receiver command sync from rebuilding the same UI.
- Add lifecycle guards: isFinishing/isDestroyed before dialogs or rendering.

## Regression locks
Must pass before release:
- RC29 protected files unchanged.
- Store<->Employee pairing unaffected.
- BLE direct link unaffected.
- GPS recognition unaffected.
- Store activation/recovery still works.
- Reports can be received.
- Messaging works both directions where supported.
- Permission changes are reflected on receiver.
- Employee add/edit/enable/disable completes through queue and ACK.
- Offline/poor network shows stable error state without leaving the current screen.
- Rotation/resume does not reset selected tab unexpectedly.
- No receiver action opens Store/System settings.

## Release gate
V137 is not field-approved until:
1. CI/build/signing passes.
2. Store device smoke test passes.
3. Receiver phone smoke test passes.
4. Pairing/BLE/GPS regression passes on real devices.
