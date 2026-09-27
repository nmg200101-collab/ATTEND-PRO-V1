# V163 Background Readiness Audit

This audit validates background readiness without changing locked runtime engines.

## Store app
- RECEIVE_BOOT_COMPLETED permission present.
- FOREGROUND_SERVICE and FOREGROUND_SERVICE_CONNECTED_DEVICE present.
- StoreReverseBluetoothPresenceServiceV157 remains a connectedDevice foreground service.
- StoreReverseBluetoothBootReceiverV157 remains wired to BOOT_COMPLETED, MY_PACKAGE_REPLACED and Bluetooth state change.
- ReceiverReportService remains a foreground service with START_STICKY.
- ReceiverReportBootReceiver remains wired to BOOT_COMPLETED and MY_PACKAGE_REPLACED.
- StoreMessagePoll1975 and PresenceProofReceiver boot/update hooks remain present.

## Employee app
- RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE and FOREGROUND_SERVICE_LOCATION remain present.
- PresenceService remains connectedDevice|location and START_STICKY.
- EmployeeReverseBluetoothPresenceServiceV157 remains connectedDevice and START_STICKY.
- PresenceBootReceiver remains wired to BOOT_COMPLETED, MY_PACKAGE_REPLACED, Bluetooth state changes and connectivity changes.

## V163 immutability gate
Before applying the V163 profile patch, CI hashes the existing background-service and boot-receiver files. After applying V163, the same hashes must match exactly. Therefore V163 cannot silently alter those background engines.

## Field limitation
Android/OEM battery optimization can only be confirmed on physical devices. CI verifies code/manifest wiring, not vendor-specific process killing.
