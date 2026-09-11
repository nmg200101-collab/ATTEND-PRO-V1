# ATTEND-PRO RC3 Functional Review

This review distinguishes source/CI validation from real-device validation. A green build is not treated as proof of physical pairing behavior.

| Area | RC3 review | Validation |
|---|---|---|
| QR pairing | Protected reference retained | Protected diff + SHA lock; previously passed on real devices |
| Bluetooth / BLE / GATT | Protected core retained | Unit/guard/lint; previously passed real-device pairing |
| Heartbeat / ACK / Confirm | Protected | Pairing guard + protected SHA |
| Reconnect | Core preserved | Guard/lint; repeat field test after RC3 install |
| Store → Employee offline messaging | Retained | Protocol unit test + lint; field recheck required |
| Employee → Store offline reply | Separate authenticated GATT characteristic | Protocol HMAC tests + lint; field test required |
| Wi‑Fi / Hotspot | Retained | Build/lint; field test required |
| GPS/geofence | Monitoring only, not automatic attendance proof | Permission/lint/source review |
| Check-in / check-out | Central daily action enlarged in RC3 | Build/lint; field tap-through required |
| General working hours | AM/PM display; 24h persistence | ShiftTimeCodec tests |
| Employee custom hours | AM/PM picker replaces 0–23 fields | ShiftTimeCodec tests + lint |
| Overnight shifts | Supported and labeled | Unit tests |
| Presence proof | Owner-selected verification retained | Regression build; field test required |
| Password/voice/face/phone biometric | Existing verification flows retained | Lint/regression; biometric/voice/face require device tests |
| External fingerprint reader | Network reachability integration retained | Build/lint; model-specific hardware connector still device-dependent |
| Reports | Daily/week/month and sharing retained | Build/lint |
| Receiver phone reports | Independent permission | Server tests + Android lint |
| Receiver phone messaging | Independent permission | Server tests + Android lint |
| Receiver remote Store settings | Revisioned and permission-gated | Server tests + Android lint; production server deployment required for live use |
| Store backup / restore | Encrypted | AES-GCM/PBKDF2 contract + foundation tests |
| Server backup / restore | Encrypted before upload | Foundation/server contract + live server test required |
| App lock / biometrics | Retained | Lint/regression; biometric unlock requires device test |
| Activation / recovery | Retained | Server/client regression; live server test required |
| Agent credentials | Non-reversible storage; owner can rotate and view newly generated code | Source/lint |
| Subscriber credentials | Device activation/access token, not plaintext password | Source/server architecture review |
| Direct updater | HTTPS + version + package + SHA-256 + signer + user approval | Source contract; version 93 feed publication required |
| Google Play updater | Play-managed update path only | Manifest/build checks; Internal Testing required |
| Arabic / English | Expanded; release-aware guide | Lint/audit; full English device walkthrough still required |
| User guide | Scenario-based and version-aware | BuildConfig release linkage + functional audit |
| Classic/Main/Sections | Classic uses lightweight dashboard engine | Lint/build; repeated real-device switching required |

## RC3 release blockers before Final
1. Install RC3 over versionCode 92 and confirm data/activation/pairing preservation.
2. Re-test pairing/reconnect on at least Samsung and Motorola.
3. Test Employee offline reply with Internet disabled.
4. Test Main ↔ Classic ↔ Sections repeatedly.
5. Verify Arabic shift display uses ص/م everywhere and English uses AM/PM.
6. Complete English walkthrough and record remaining Arabic UI strings.
7. Publish versionCode 93 update feed and server endpoints only after explicit production-deployment approval.
8. Test Google Play Internal track before Production promotion.
