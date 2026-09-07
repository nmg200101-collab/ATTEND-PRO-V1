# ATTEND-PRO Current Approved Baseline

Status: FIELD-APPROVED BASELINE

Version: 1.9.65
Version code: 72
Approved purpose: Bluetooth authenticated connection after QR while preserving all existing connection paths.

Field approval
- The project owner confirmed on 2026-09-08 that Bluetooth improved and is working.
- This makes 1.9.65 the required functional base for subsequent application updates unless a later release is explicitly field-approved.

Verified build provenance
- Repository: nmg200101-collab/ATTEND-PRO-V1
- GitHub Actions run: 34141355070
- Build commit: 9e6cd1957bf4743c466f866916f5c6e87ed09ea1
- Verified source artifact: ATTEND-PRO-V1-source-1.9.65-BLUETOOTH-ACK-AFTER-QR.zip
- Verified source SHA-256: b8ba7ed0e6cf3c5a7fcfe8010c8b761b961d8b8838785a8875800719e6b7c180

Connection invariants for every later release
1. Preserve the approved 1.9.65 Bluetooth orchestration behavior after QR.
2. Bluetooth may be displayed as connected only after authenticated ACK; discovery alone is not a connection.
3. Preserve Wi-Fi/Hotspot, Server and QR connection paths.
4. GPS remains monitoring/proximity only and must never become attendance proof or a transport channel.
5. Preserve all attendance and departure methods.
6. Preserve CONNECTION_CORE_LOCK_1.9.58.sha256. Dedicated locked connection-core files may not be silently changed by unrelated UI/admin/report updates.
7. Any later Bluetooth/core connection modification requires a dedicated regression build, connection-lock review, and new field approval before replacing this baseline.

Release rule
- All subsequent work must reconstruct from this verified 1.9.65 source baseline or an exact verified descendant.
- A future build must not be labeled FINAL or OFFICIAL unless it passes the official signing policy in OFFICIAL_SIGNING_POLICY.md.
