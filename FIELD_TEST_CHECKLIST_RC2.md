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
9. Store -> Employee local direct message without Internet.
10. Employee -> Store reply without Internet while authenticated Bluetooth is active.
11. Presence proof request with owner-selected method.

Record PASS/FAIL, device model, Android version, distance, and failure message for every item.

## Home / Classic regression
- Open the Main layout.
- Switch to Classic and confirm the app remains responsive.
- Switch Classic -> Sections -> Main repeatedly.
- Lock/unlock the phone while Classic is displayed.
- Confirm ATTEND PRO header is first and sized compactly.
- Confirm Store Manager Settings appears immediately below the header.
- Confirm normal phone screens show the operational dashboard without excessive vertical space.
- Confirm attendance, connection status and today's summary remain functional after layout switching.

## Attendance and shifts
- 8:00 AM -> 10:00 PM normal shift.
- 10:00 PM -> 6:00 AM overnight shift.
- 12:00 AM midnight conversion.
- 12:00 PM noon conversion.
- Late arrival inside and outside grace minutes.
- Checkout during overnight shift after midnight.
- Existing saved 24-hour shift values remain unchanged after upgrade.

## Receiver / management phone
- Add receiver using QR.
- Confirm default capability is Reports only.
- Grant Message Employees only and verify messaging appears.
- Revoke Message Employees and verify server rejects messaging.
- Grant Store Manager Settings and change a normal shift.
- Test a 10:00 PM -> 6:00 AM remote shift.
- Verify the Store phone applies the new revision when it reconnects to the server.
- Revoke Store Manager Settings and verify further remote changes are rejected.
- Disable the receiver phone and confirm all remote access is rejected.
- Re-enable and verify only explicitly granted capabilities return.
- Remove the receiver and verify it can no longer authenticate.

## Arabic / English
- Switch Store app Arabic -> English and restart.
- Verify Home, Store Management, receiver phone, dialogs and practical guide use LTR English.
- Switch Employee app Arabic -> English and restart.
- Verify Home, messages, offline reply, permissions and practical guide use LTR English.
- Record any remaining hard-coded Arabic user-facing string for the final migration audit.

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
- Upgrade over the previous signed build without clearing data.
- Activation survives upgrade.
