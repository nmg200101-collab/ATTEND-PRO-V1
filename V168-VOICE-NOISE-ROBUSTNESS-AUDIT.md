# V168 Voice Noise Robustness Security Audit

## Scope
V168 is a targeted voice-verification update. It does not authorize changes to pairing, BLE/GATT, Receiver, GPS, server transport, attendance transport, or background presence services.

## Functional changes
- Store VoiceSignatureEngine upgraded from current V3 enrollment to V4 enrollment.
- Android NoiseSuppressor is attached to the AudioRecord session when available.
- Recording source fallback order favors processed recognition/microphone paths before UNPROCESSED.
- V4 feature extraction estimates a stationary per-band noise profile from the quietest frames and subtracts that profile before speaker-feature extraction.
- Low-level spectral residuals are retained to avoid erasing speaker formants.
- Quality scoring is recalibrated after hard SNR/speech/clipping gates. Moderate usable SNR is no longer rejected because of natural silence/short phrase length alone.
- Speaker matching threshold is not reduced. Moderate-quality samples can still increase required speaker score.
- Enrollment requires five V4 samples and consistency validation.
- Phrase and random two-word challenge remain mandatory.

## Security invariants
- No bypass for low-SNR audio.
- No acceptance based on phrase text alone.
- No acceptance based on speaker score alone.
- No lowering of speaker identity threshold as a compensation for noise.
- Environmental/technical capture failure is not counted as an authentication failure.
- Repeated identity failures remain subject to the existing employee authentication lockout.

## Protected runtime
The following must remain byte-identical to the V167 effective baseline during CI:
- employee PresenceBootReceiver.kt
- employee PresenceService.kt
- employee EmployeeReverseBluetoothPresenceServiceV157.kt
- store StoreReverseBluetoothPresenceServiceV157.kt
- store StoreReverseBluetoothBootReceiverV157.kt
- store ReceiverReportService.kt
- core PairingProtocol.kt

## Field approval
V168 must not replace the V167/V166 field fallback until real-device testing confirms true-speaker acceptance and impostor/challenge rejection under store noise.
