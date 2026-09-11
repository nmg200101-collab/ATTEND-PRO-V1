# ATTEND PRO — Privacy Policy Draft for Google Play (RC2)

Status: **IMPLEMENTED IN APP / SERVER ROUTES PREPARED — PUBLIC DEPLOYMENT PENDING**

## Scope
This policy applies to the ATTEND PRO Store app and ATTEND PRO Employee app.

## Data processed
ATTEND PRO may process Store activation and device-linking information; employee identifiers and names entered by Store Management; allowed verification methods; check-in/check-out and presence-proof events; connectivity diagnostics; device/installation identifiers; Bluetooth/Wi-Fi/LAN/server connectivity state; location/proximity information when location features are enabled; and encrypted backup envelopes uploaded by Store Management.

## Background location
When Store geofencing is enabled, the Employee app may collect location data to enable phone-proximity detection and geofence alerts **even when the app is closed or not in use**. Proximity state, distance, accuracy and observation time may be transmitted to the Store Management server. Location alone does not automatically record check-in or check-out.

## Purpose
Data is used for attendance management, authorized employee verification, secure pairing, presence proof, reporting, restoration, security, anti-tamper protection and service operation. Location data is not used for advertising.

## Sharing
Operational data may be shared with the Store/employer that manages the employee and may be transmitted to the configured ATTEND PRO server when server functionality is enabled. ATTEND PRO does not sell user data to advertisers.

## Security
ATTEND PRO uses app-signature enforcement, authenticated device links, request signing and encrypted backup envelopes. Server backups are encrypted by the app before upload.

## Retention and deletion
Data is retained only as required for the service and applicable administrative/legal needs. A deletion request can be initiated through the Data Deletion page. Destructive deletion requires ownership/authority verification.

## Required before Play submission
- Deploy the reviewed server branch containing `/privacy` and `/delete-data` to the approved production domain.
- Confirm the public URLs load without authentication.
- Confirm the legal developer identity matches the Google Play listing.
- Re-review this policy against the exact production Data Safety form.
