# ATTEND-PRO V163 — Store Profile Branding

## Scope
Store profile information and store identity branding on top of approved V162.

## Changes
- Store profile now includes a dedicated Store Identity section.
- Owner can choose a store image from the device without permanent storage permission.
- Selected image is decoded safely, downscaled, copied to app-private storage and reused after restart.
- Owner can choose a store symbol instead of an image.
- Owner can restore the default store logo.
- The store logo/symbol and store name appear inside the blue main header below ATTEND PRO across the active Store home layouts.
- Existing store information fields and save behavior remain in place.
- V161 sequential back navigation and V162 visual status presentation are preserved.

## Protected behavior
No Bluetooth/BLE transport, Wi-Fi/Hotspot/LAN transport, Receiver V149, RC29 pairing/direct-link core, attendance engine, message transport, presence proof transport, backup engine or server behavior is modified.

## Background-readiness policy
V163 must prove that its patch leaves the existing background-service files byte-identical to the V162 effective build, and CI also verifies foreground-service/boot-receiver wiring and START_STICKY markers. Real-device background testing remains required before making any claim about OEM battery-management behavior.
