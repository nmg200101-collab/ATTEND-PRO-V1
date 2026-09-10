# ATTEND PRO Central Server 1.9.2

This server enables central store activation, attendance sync, remote owner dashboards, authorized receiver phones, encrypted report queues, and remote receipt confirmation.

## Security model
- Run the service behind a real HTTPS reverse proxy / load balancer. The Android client intentionally rejects non-HTTPS URLs.
- `ATTEND_PRO_OWNER_API_KEY` is a separate high-entropy server administration key. Never reuse the in-app owner code.
- `ATTEND_PRO_DATA_KEY` is a 32-byte server-only AES key used to encrypt activation access tokens at rest.
- Store and receiver secrets are stored as hashes where practical. Store access tokens are random 256-bit values and are stored as SHA-256 hashes.
- Every store API request is also signed by a non-exportable Android Keystore P-256 private key. The server verifies timestamp, nonce, body hash, and ECDSA signature, so a copied bearer token alone is not sufficient.
- Activation-status retrieval is device-signed as well; the approval token is released only to the device key that created the request.
- Used nonces are retained for a replay window to reject repeated signed requests.
- Receiver secrets are scrypt-hashed. Report payloads are already AES-GCM encrypted by the Store app for the intended receiver before upload.
- Rate limiting, request size limits, no-store headers, and constant-time credential comparisons are included.
- Store operation is centrally leased. `ATTEND_PRO_OFFLINE_LEASE_HOURS` defaults to 72 hours, so a store can keep recording during a temporary outage but must periodically revalidate with the server. Suspension takes effect on the next successful validation/API call, and always no later than lease expiry.
- Local/offline license issuance is not used to unlock Store app operation in 1.9.2; central approval is mandatory.

## Start locally
1. Create the two secrets in `.env.example` in your deployment provider's secret manager.
2. Set the environment variables.
3. Run `node server.mjs`.
4. Put HTTPS in front of port 8080. Do not expose plain HTTP to Android clients.

The included file-backed data store is intended for a single-server pilot. Before large commercial rollout, migrate persistence to PostgreSQL and run multiple server instances behind a load balancer.
