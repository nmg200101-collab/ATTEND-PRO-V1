# ATTEND-PRO 2.0.0-RC6

versionCode 96

## Critical fixes
- Bluetooth presence advertising restored to a legacy-31-byte-safe authenticated frame for Samsung/Motorola compatibility.
- Store BLE scanner adds an OEM-compatible unfiltered fallback when filtered scan yields no results.
- GPS recognition no longer depends on the optional arrival-alert toggle or a stale local serverLinked flag.
- GPS location permission is requested whenever the paired store has GPS coordinates.
- Presence-proof requests can be sent locally over BLE/LAN and through the central server. Scheduled requests also use local channels.
- Store Management now exposes the complete owner hub in a full-height scrollable view. Presence-proof controls are visible in that hub.
- Protected pairing protocol files remain unchanged.

## Safety
GPS remains recognition/telemetry only and never records attendance by itself. Employee verification is still required for attendance proof.
