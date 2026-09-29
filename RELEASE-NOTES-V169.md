# ATTEND-PRO V169 — Face Verification Hardening

## Scope
V169 is restricted to shared-device face enrollment, local face matching, interactive liveness checks, device-bound face-template/reference protection, and release metadata.

## Functional changes
- Keeps five face enrollment captures but validates pose diversity instead of accepting any five images.
- Requires the first enrollment/verification image to be sufficiently frontal.
- Adds pose-aware analysis using Android FaceDetector Euler X/Y/Z values.
- Introduces Face Signature V2 (`fh2` / `fm2`) combining normalized HOG with a coarse regional texture descriptor.
- Uses quality-adaptive identity thresholds that only become stricter as capture quality decreases; V169 never lowers the identity threshold to force an acceptance.
- Replaces image-difference-only liveness with a randomized pose challenge: side turn, chin movement, or head tilt.
- Second liveness capture must prove the same employee identity and the requested pose movement.
- Preserves TEST_ONLY face verification so employee tests never create attendance events.

## Protection changes
- New face templates are encrypted at rest with AES-GCM using a device-bound Android Keystore key.
- New face reference crops are also stored encrypted rather than as directly readable JPEG files.
- Employee ID is bound as authenticated data to the protected template/reference payload.
- Legacy plaintext face templates remain readable for compatibility and can be migrated after successful verification; new enrollment always writes V169-protected data.
- A restored face template on a different Android device may require face re-enrollment because the Keystore key is intentionally non-exportable.

## Compatibility and locks
The following are outside V169 scope and must remain byte-identical to V168:
- V168 Voice verification / Voice Signature V4
- V157 Bluetooth dual-path runtime
- V149 Receiver runtime
- RC29 PairingProtocol and protected pairing/direct-link transport
- PresenceService / PresenceBootReceiver
- GPS engine, message transport, central server transport and activation flows

## Field gate
Before promoting V169 to the field baseline, verify on a real Store device:
1. five-pose enrollment, including rejection of duplicate-side poses;
2. frontal first recognition capture;
3. all three liveness challenge categories;
4. same employee succeeds while wrong employee fails;
5. TEST_ONLY does not create attendance;
6. encrypted reference image can be viewed on the same device;
7. deletion and re-enrollment work;
8. V168 voice remains unchanged and functional;
9. Bluetooth/Receiver/RC29 regression checks remain successful.
