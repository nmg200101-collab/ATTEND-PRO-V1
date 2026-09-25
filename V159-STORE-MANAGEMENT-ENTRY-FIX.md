# ATTEND-PRO V159 — Store Management Entry Fix

## Problem fixed
The V158 Store Management organization was built correctly, but the main-screen button labeled "إدارة المحل" still opened the legacy owner shortcut hub instead of the new StoreSettingsActivity layout.

## Fix
- The main-screen "إدارة المحل" button now opens StoreSettingsActivity directly after the existing owner check.
- V158's six-section Store Management layout is therefore visible from the user's normal entry path.
- The legacy owner shortcut hub remains in source for compatibility but is no longer the primary Store Management entry from that button.

## Protected scope
No Bluetooth/BLE, Wi-Fi/Hotspot/LAN, Receiver, RC29 connection core, attendance engine, messaging transport, proof transport, server logic, backup engine, or employee data logic is changed.

## Field gate
Verify on-device that:
1. Main screen -> إدارة المحل opens the new six-section layout.
2. Each section opens its existing functions.
3. Bluetooth and Wi-Fi recognition remain unchanged from V157.
