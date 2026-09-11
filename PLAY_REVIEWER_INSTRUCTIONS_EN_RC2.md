# ATTEND PRO — Google Play Reviewer Instructions (RC2)

Status: **APP FLOW PREPARED — REVIEW CREDENTIALS / FIXED REVIEWER TEST DATA PENDING BEFORE SUBMISSION**

## Purpose
Enterprise attendance management. The Store app is authoritative. The Employee app is linked to a Store and may report presence/connectivity and location-based proximity when enabled by the enterprise.

## Reviewer flow
1. Install the Store Play build.
2. Complete the dedicated reviewer activation supplied in Play Console → App access.
3. Open Store Management → Employees and select the reviewer employee.
4. Open pairing and display the reviewer pairing QR.
5. Install/open the Employee Play build and scan the QR.
6. Keep Bluetooth and Location enabled during pairing.
7. Pairing succeeds only after the authenticated ACK/link confirmation.
8. Return to Store and verify the linked Employee phone.
9. Enable automatic Store visibility in Employee.
10. Test check-in/check-out using an allowed verification method.
11. For background-location review, enable Store geofence functionality and observe the prominent disclosure before the runtime location permission request.
12. Open Menu → Privacy and data.

## Background location
The Employee app may use location for employee-phone proximity/geofence status even when the app is closed or not in use, when that enterprise feature is enabled. Location alone never records attendance automatically.

## Monitoring disclosure
The Employee Play manifest declares `isMonitoringTool=enterprise_management`.

## Reviewer credentials
**PENDING BEFORE SUBMISSION.** Create a dedicated reviewer Store and Employee. Do not provide owner master keys, production administrator secrets, universal PINs or production employee data.

## Public policy links after approved server deployment
- `https://attend-pro-central.nmg200101.workers.dev/privacy`
- `https://attend-pro-central.nmg200101.workers.dev/delete-data`
