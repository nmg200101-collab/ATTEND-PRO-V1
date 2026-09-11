# ATTEND-PRO RC3 Field Test Checklist

## Mandatory regression
- QR pairing still succeeds.
- Direct Bluetooth/BLE/GATT pairing still succeeds.
- Reconnect after Bluetooth off/on and phone lock.
- Store → Employee offline message.
- Employee → Store offline reply.

## Working hours
- General shift shows 8:00 ص → 10:00 م in Arabic.
- General shift shows 8:00 AM → 10:00 PM in English.
- Employee custom shift uses AM/PM picker.
- 10:00 م → 6:00 ص is marked as overnight.
- Noon and midnight are rendered correctly.

## Main dashboard
- ATTEND PRO logo remains compact at top.
- Store Manager Settings remains directly below.
- Fingerprint/attendance action area is visibly larger and easy to tap.
- Check-in and Check-out buttons remain visible and responsive.
- Main/Classic/Sections switching does not freeze.

## System administration
- Agent record shows credential-management option.
- Generating a new agent code invalidates the old code.
- New code is visible/copyable at creation time only.
- Subscriber screen explains device-token authentication rather than a plaintext password.
- System owner can generate a one-time subscriber recovery credential, view/copy it, and the old protected device token remains non-readable.
- Owner recovery/reactivation workflow still works.

## Guide/language
- “What’s new” displays the installed app version.
- New shift and credential instructions appear after update installation.
- Arabic and English walkthroughs are coherent.

## Update
- Install versionCode 92.
- Check update feed after RC3 publication.
- versionCode 93 must be detected.
- Download verifies HTTPS, SHA-256, package and signer.
- Install over version 92 without clearing activation, pairing, employees or attendance data.
