# ATTEND-PRO V161 — Store Management Compact UX & Sequential Back

## Scope
V161 changes Store Management presentation and navigation only.

### Visual changes
- Replaces large spaced option cards with compact settings-style rows.
- Groups related options under clear section headers.
- Shows short live summaries where existing state is already available.
- Reduces outer page padding and vertical spacing.
- Keeps the six approved top-level Store Management sections.

### Sequential back navigation
- Dashboard -> section -> option/detail.
- Back from an option/dialog returns to the same section.
- Back from a section returns to Store Management dashboard.
- Back from Store Management dashboard returns to the Store main screen.
- Advanced settings opened from Store Settings returns to Store Settings first.
- Returning from employee manager, reports, messages, privacy, guide or system administration restores the section the user came from.

### Protected behavior
No Bluetooth/BLE, Wi-Fi/Hotspot/LAN, Receiver V149, RC29 pairing/GATT, attendance engine, message transport, proof transport, backup engine or server logic is changed.

## Field gate
V161 is not a replacement for V160 until real-device checks confirm the compact layout, sequential back behavior and the existing option actions.
