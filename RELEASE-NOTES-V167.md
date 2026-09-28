# ATTEND-PRO V167 — Voice Verification Hardening

Version: `2.0.0-RC29-V167-VOICE-VERIFICATION-HARDENING`

## Scope
V167 is intentionally limited to Store voice verification correctness, resilience and security. V166 employee-management behavior remains the functional baseline. V157 Bluetooth dual-path, V149 Receiver, RC29 pairing/GATT/direct-link, Presence services and central transport remain locked and unchanged.

## Voice V3
- New `vs3/vm3` speaker template format. Existing V1/V2 voice templates are not accepted for attendance; the employee must re-enroll five V167 samples.
- Six-second maximum adaptive capture with early stop after sufficient speech and trailing silence.
- Rejects clipped, too-short, highly noisy and weak-SNR samples instead of lowering the biometric threshold.
- Denser vocal spectrum + cepstral shape + pitch/ZCR/spectral-centroid statistics.
- Explicit speaker-similarity score and per-employee threshold derived from enrollment consistency with a fixed security floor.
- Five enrollment samples must meet both minimum quality and minimum consistency before the template is saved.

## Phrase + liveness challenge
- Replaces the single-word challenge with two random words selected to avoid words already present in the employee phrase.
- Arabic normalization covers hamza/taa marbuta/alif maqsura, punctuation, definite article on challenge words and Arabic digits.
- Phrase matching is ordered and stricter than the V166 bag-of-words comparison.
- Low recognizer confidence alone does not override a strong exact text match; confidence is treated as a diagnostic rather than a biometric factor.
- One retry is allowed only for a technical/ambiguous speech-recognition result and is not counted as an authentication failure. A real mismatch remains rate-limited.

## Same-audio recognition path
- On Android 13/API 33+ V167 supplies the captured PCM to the recognizer using `RecognizerIntent.EXTRA_AUDIO_SOURCE` where the active recognizer supports it.
- On Android 12/API 31–32 V167 attempts the platform `EXTRA_AUDIO_INJECT_SOURCE` URI path; recognizer support is implementation-dependent.
- If a recognizer does not support injected audio, Android may open the microphone itself. The biometric voiceprint still has to pass before text/challenge verification.

## Diagnostics
Failures are separated into quality/SNR, voiceprint mismatch, phrase mismatch and challenge mismatch. Test mode continues to produce no attendance event.

## Required field test
1. Re-enroll one employee with five V167 voice samples.
2. Test in a quiet room, normal shop noise and moderate distance changes.
3. Verify correct employee passes repeatedly without lowering thresholds.
4. Verify a different speaker fails the voiceprint stage.
5. Verify wrong phrase, missing challenge word and reversed challenge order fail.
6. Verify a technical recognizer failure can retry once without recording an attendance event.
7. Confirm real attendance succeeds after the test path.
8. Regression-check V157 Bluetooth, V149 Receiver and RC29 pairing.

V166 remains the field rollback baseline until this checklist passes on the real Store device.