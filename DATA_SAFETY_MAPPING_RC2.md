# ATTEND PRO — Google Play Data Safety Mapping (RC2)

This is an engineering mapping. Final Play Console answers must be verified against the exact production configuration.

## Employee app
- Precise/approximate location: used when location features are enabled; background access can occur for enterprise proximity/geofence functionality. Prominent disclosure appears before permission. Location alone does not record attendance.
- Device/installation identifiers: used for secure installation identity, pairing and anti-abuse.
- Attendance/presence events: used for attendance, presence proof and reporting.
- Employee name/ID: provisioned by Store Management.
- Camera/face and microphone/voice: accessed only for enabled verification/enrollment flows. Verify immediately before submission whether any derived data leaves the device; do not claim server collection unless confirmed.

## Store app
- Store/owner information: may be supplied during activation/management.
- Employee name/ID and attendance events: core functionality and optional server sync.
- Store location: used for Store GPS/geofence configuration.
- Device identifiers: activation, request signing and secure recovery.
- Encrypted backups: optional; encrypted client-side before server upload.

## Sharing
Data is shared with the employer/Store Management as inherent to enterprise attendance management and may be transmitted to the configured ATTEND PRO server. No advertising use or sale of user data is implemented.

## Special Play declarations
- Employee background-location declaration and reviewer video.
- `isMonitoringTool=enterprise_management`.
- Prominent background-location disclosure before permission.
- Public privacy-policy URL.
- Public data-deletion URL.

Do not submit the Data Safety form from this document alone. Re-check production manifests, endpoints and enabled SDKs immediately before submission.
