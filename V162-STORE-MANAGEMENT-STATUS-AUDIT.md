# V162 Store Management Function and Status Audit

V162 is a presentation-only layer above V161.

## Route invariants
- Store information -> editStoreProfile()
- GPS -> gpsSettings()
- App lock -> showAppLockSettings()
- Store Manager PIN -> changeStorePin()
- Appearance -> UiKit.showAppearancePicker()
- Language -> AppLanguage.showPicker()
- User guide -> StoreUserGuideActivity
- Privacy -> PrivacyDataActivity
- Employee management -> MainActivity employee manager
- Attendance methods -> attendanceMethodsSettings()
- Shift -> shiftSettings()
- External fingerprint -> fingerprintSettings()
- Readiness -> healthCheck()
- Reports -> ReportsActivity
- Messages -> StoreMessages1975Activity
- Voice -> StoreVoiceControl1975Activity
- Backup/restore -> showBackupCenter()
- System administration -> SystemSettingsActivity

## Status sources
- GPS status is derived from StoreRepository.isGpsConfigured.
- Store-management protection uses StoreRepository.hasStoreAdminPin.
- Employee count and linked-phone count use the current StoreRepository employee data.
- Shift summary uses current shift values.
- Bluetooth summary is radio enabled/disabled only; it does not claim an authenticated app connection.
- Wi-Fi summary is the Android active Wi-Fi transport only; it does not alter network routing.
- Server summary is based on current central activation state.
- Backup summary uses the existing backup-state timestamp.

No transport or business logic is modified to obtain these statuses.
