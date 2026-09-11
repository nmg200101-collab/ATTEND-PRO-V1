# ATTEND-PRO RC2 Field Test Checklist

Do not promote RC2 to final until every mandatory field item passes.

## Devices
Test at minimum one Samsung and one Motorola device, including one Android 12/13 device and one current Android version if available.

## Protected connection regression
1. Store -> Employee QR pairing.
2. Direct Bluetooth pairing.
3. BLE advertisement discovery.
4. GATT direct channel.
5. Pairing ACK and confirmation.
6. Reconnect after Bluetooth off/on.
7. Reconnect after phone lock/unlock and app process restart.
8. Wi-Fi/Hotspot fallback.
9. Local direct messages without Internet.
10. Presence proof request with owner-selected method.

Record PASS/FAIL, device model, Android version, distance, and failure message for every item.

## Attendance and shifts
- 8:00 AM -> 10:00 PM normal shift.
- 10:00 PM -> 6:00 AM overnight shift.
- 12:00 AM midnight conversion.
- 12:00 PM noon conversion.
- Late arrival inside and outside grace minutes.
- Checkout during overnight shift after midnight.
- Existing saved 24-hour shift values remain unchanged after upgrade.

## Play build
- Install through internal Play track.
- Confirm no external APK download/install UI exists.
- Confirm In-App Update flow is offered only by Google Play when a newer internal-track version exists.
- Confirm Employee persistent monitoring notification is visible.
- Confirm prominent location disclosure appears before Android location permission.
- Confirm denying location leaves Bluetooth/Wi-Fi paths usable.

## Backup / lock / upgrade
- Phone backup create + restore.
- Server backup + restore.
- App lock + biometric unlock.
- Upgrade over RC1 without clearing data.
- Activation survives upgrade.
