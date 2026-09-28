# ATTEND-PRO V164 — Store Logo Import + Compact Header

## Fixes
- Fix store-logo import failures reported on real devices.
- Image selection now uses a temporary read grant and copies the selected content into app-private temporary storage before decoding.
- The imported image is validated locally, downscaled safely and then saved as store_logo.png.
- The temporary import file is always removed after processing.
- The blue Store dashboard header no longer grows vertically because of store branding.
- Store logo + store name/branch are shown in one compact inline row instead of separate vertical logo/name rows.
- ATTEND PRO and the existing header content are moved back upward close to the V162 layout.

## Preserved behavior
No Bluetooth/BLE, Wi-Fi/Hotspot/LAN, Receiver, attendance, messages, proof transport, backup or server logic is changed.
