# ATTEND-PRO Connection Baseline Lock — 1.9.58

The field-approved 1.9.58 connection engine is frozen as the compatibility baseline for subsequent UI/application updates.

Locked areas:
- BLE pairing protocol and direct GATT protocol
- Employee foreground presence BLE advertising / GATT server
- Employee pairing discovery and LAN broadcaster
- Store BLE scanner / direct GATT client / LAN listener / pairing beacon

`CONNECTION_CORE_LOCK_1.9.58.sha256` contains the approved hashes.
Every 1.9.59+ CI build must run `sha256sum -c CONNECTION_CORE_LOCK_1.9.58.sha256` before compiling.
A UI/settings/report update must not change any locked file. If the connection engine ever needs an intentional change, it must be a dedicated connection release with a new field test and a new lock baseline; the lock must never be silently refreshed inside an unrelated update.

MainActivity is intentionally not hash-locked because it also contains the Store UI. Connection-specific classes are the protected boundary.
