# ATTEND-PRO Official Android Signing Policy

This policy applies to every future FINAL/OFFICIAL Android release after the field-approved 1.9.65 baseline.

Approved permanent certificate
SHA-256:
6F:8B:CC:08:AE:6C:8D:96:54:E7:AA:2C:9A:66:1F:B6:BD:F2:1E:31:B7:20:AB:3A:DD:88:AE:D0:CD:4F:4D:82

Mandatory conditions before any APK is called FINAL or OFFICIAL
1. Build from RELEASE_BASELINE_CURRENT.md or a later explicitly approved baseline.
2. versionCode must increase over the prior official release.
3. zipalign must pass.
4. APK must be signed with the exact approved permanent certificate above.
5. apksigner verification must pass for APK Signature Scheme v2 and v3.
6. The certificate SHA-256 must be checked after signing; a mismatch fails the release.
7. Package names must remain com.attendpro.employee and com.attendpro.store unless an intentional migration is separately approved.
8. SHA-256 checksums must be generated for final APKs and retained with the release verification report.

CI safety rule
- The Android permanent signing credentials are secrets and must never be committed to this repository, printed in logs, or included in artifacts.
- If the permanent signing credentials are unavailable, CI may produce DEBUG TEST or UNSIGNED RELEASE artifacts only.
- Missing signing credentials must never be interpreted as a successful official release.
- A future official release workflow should fail/withhold FINAL artifacts rather than silently substitute a debug certificate.

Expected GitHub Actions secret names when automatic official signing is configured
- ATTEND_PRO_KEYSTORE_B64
- ATTEND_PRO_STORE_PASSWORD
- ATTEND_PRO_KEY_ALIAS
- ATTEND_PRO_KEY_PASSWORD

Do not invent or replace these values. They must correspond to the original permanent ATTEND-PRO signing key whose certificate matches the approved SHA-256 above.
