# RC6 field test

1. Install Store + Employee RC6 over the current signed versions.
2. Keep Wi-Fi off on both phones, turn Bluetooth on, grant Nearby devices. Confirm employee appears as Bluetooth BLE authenticated.
3. Test Samsung and Motorola if available. Keep screen locked for at least 30 seconds and confirm rediscovery.
4. Configure Store GPS, ensure Employee link contains Store GPS, grant location. Confirm INSIDE/NEAR appears in Store without recording attendance.
5. Open Store > Management. Confirm all owner options are visible by scrolling, including Send proof now and Presence proof control.
6. Send proof now with internet off while phones are near/on same LAN; confirm Employee notification arrives locally.
7. Enable scheduled proof; confirm the scheduled request is dispatched locally and through server when internet exists.
8. Re-test QR/Bluetooth pairing; protected pairing files must be unchanged.
