# ATTEND PRO — Data Deletion Policy (RC2)

Status: **REQUEST FLOW IMPLEMENTED ON SERVER BRANCH — PRODUCTION DEPLOYMENT PENDING**

Prepared server routes:
- `GET /delete-data`
- `POST /delete-data`
- `POST /api/v1/privacy/delete-request`

A request receives an ID and enters `PENDING_VERIFICATION`. No unauthenticated request directly deletes data. Ownership/authority must be verified before destructive deletion. Inputs are validated and rate limiting is applied when available.

Before Play submission: deploy the reviewed server branch, confirm retention/legal obligations, define the internal verified-deletion procedure, and test the public deletion URL in an incognito browser.
