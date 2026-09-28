# ATTEND-PRO V165 — Store Branding Modes & Built-in Designer

V165 is a presentation-only update built on the field-approved V164 behavior and the locked V157/V149/RC29 runtime lines.

## Scope
- Add persistent Store header display modes: `SMALL` and `COVER`.
- Add Store header visual presets: `DEFAULT`, `BUSINESS`, `GLASS`, and `NIGHT`.
- Keep the Store header compact; COVER uses `CENTER_CROP` with a dark readability overlay.
- Keep the ATTEND PRO app mark/title visible at the top of the branded header.
- Add an in-app quick logo designer with title, optional subtitle, store symbol, and visual preset.
- Save generated/imported branding inside app-private storage and reuse it after restart.
- Improve Arabic/English direction and branch label handling in the Store identity header.
- Set Store and Employee release metadata to versionCode 165 / `2.0.0-RC29-V165-STORE-BRANDING-MODES-DESIGNER`.

## Locked runtime
No V165 source patch change is permitted in Bluetooth/BLE, pairing protocols, Wi-Fi/LAN transport, Receiver, GPS, attendance engine, message transport, central server, activation, backup engine, V157 runtime services, V149 Receiver lock, or RC29 protected connection sources.

## Release gate
Automated CI must pass unit tests, Android Lint, Direct APK builds, Play AAB builds, signature/package/version checks, and lock verification. V164 remains the field baseline until V165 is installed on a real device and SMALL, COVER, designer output, compact header height, persistence, Bluetooth, and previous functions are confirmed by the user.
