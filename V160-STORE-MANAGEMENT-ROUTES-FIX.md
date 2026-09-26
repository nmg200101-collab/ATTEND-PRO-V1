# ATTEND-PRO V160 — Store Management Routes Fix

## Problem
V159 corrected one Store Management entry route, but other normal UI routes still opened the legacy "إعدادات مدير المحل — اختصارات في الرئيسية" dialog.

## V160 fix
All user-facing entry routes that mean "Store Management" or "All Store Manager settings" now open StoreSettingsActivity directly:
- bottom navigation "الإدارة"
- main Store Manager frame
- "عرض جميع إعدادات مدير المحل" shortcut
- the previously fixed "إدارة المحل" button remains routed to StoreSettingsActivity

The legacy hub function remains in source only as a compatibility fallback and is no longer reachable from the normal Store Management entries.

## Protected scope
No Bluetooth/BLE, Wi-Fi/Hotspot/LAN, Receiver, RC29 core, attendance engine, messages, presence proof transport, backup engine, or server logic is modified.

## Field gate
On-device check:
1. Tap every visible Store Management entry.
2. Each must open the six-section organized StoreSettingsActivity.
3. No "إعدادات مدير المحل — اختصارات في الرئيسية" dialog should appear from normal Store Management navigation.
4. Bluetooth/Wi-Fi recognition remains unchanged from V157.
