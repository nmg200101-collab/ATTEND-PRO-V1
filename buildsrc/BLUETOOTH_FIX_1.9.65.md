# ATTEND-PRO 1.9.65 — Bluetooth ACK after QR pairing

Field issue: after QR pairing the employee profile appears, but the Store may remain without a Bluetooth-connected state while Wi-Fi/Hotspot works.

Fix:
- A valid BLE sighting for an already linked employee now always seeds/refreshes the direct GATT client.
- BLE discovery alone never marks the employee connected.
- The Store still requires the signed PING -> ACK -> ACK-confirm handshake before showing Bluetooth as connected.
- Scanning is paused briefly only while establishing GATT, reducing OEM status-133 contention.
- Wi-Fi/Hotspot, Server, GPS monitoring and all attendance methods remain independent and unchanged.

Connection-core lock 1.9.58 remains enforced. No locked connection-core file is modified by this patch; the fix is in Store orchestration only.
