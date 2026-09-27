# ATTEND-PRO V161 — Store Management Function Audit

Status: STATIC ROUTING + BUILD AUDIT. Real-device behavior still requires field confirmation.

## Store Settings
- Store information -> editStoreProfile()
- Store location and GPS -> gpsSettings()
- App lock and biometric -> showAppLockSettings()
- Store Management PIN -> changeStorePin()
- Appearance and templates -> UiKit.showAppearancePicker()
- Language -> AppLanguage.showPicker()
- User guide -> showStoreUserGuide1976()
- Privacy and data -> PrivacyDataActivity
- Advanced settings -> showAdvancedDashboard1975() through V161 internal navigation

## Employees and Shifts
- Add/manage employees -> MainActivity with EXTRA_EMPLOYEE_MANAGER + store admin session
- Attendance methods -> attendanceMethodsSettings()
- Shift and grace -> shiftSettings()
- External fingerprint reader -> fingerprintSettings()

## Connectivity and Devices
- Store readiness check -> healthCheck()
- This area remains status/check-only; no locked transport implementation is edited.

## Reports and Receiving
- Reports and sharing -> ReportsActivity with the existing store-admin session

## Messages and Communication
- Messages and notifications -> StoreMessages1975Activity
- Voice control -> StoreVoiceControl1975Activity

## Backup, Protection and System
- Backup and restore -> showBackupCenter()
- Higher ATTEND PRO administration -> SystemSettingsActivity
- Lock Store Management now -> existing store-admin session clear/login path

## Sequential-back audit
- Store Settings sections render as StoreSettingsActivity pages instead of dismiss-on-action dialogs.
- Option dialogs remain above their parent section, so Android back closes the option first.
- External child activities return to the same saved Store Management page.
- onResume renders the current V161 page instead of forcing the dashboard.
- navigation page and history are preserved through saved instance state.
- settings saves that previously forced showDashboard() now refresh the current section.

## Lock statement
V161 does not authorize changes inside Bluetooth, Wi-Fi/LAN, Receiver, RC29 core, attendance, messages, proof, backup transport or server logic.
