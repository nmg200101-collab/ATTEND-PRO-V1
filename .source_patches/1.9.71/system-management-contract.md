# ATTEND-PRO 1.9.71 — System Management Contract

Baseline: 1.9.70 pairing-stable build.

## Protected pairing boundary
The following behavior is frozen for this update and must not be changed by management features:
- AP5Q QR direct provision acceptance
- BLE scan -> GATT stabilization
- MTU 247 request behavior
- Store service UUID advertising
- pairing/presence authentication flow

## Roles
- SYSTEM_OWNER: unrestricted system authority.
- AGENT: authority limited to assigned customers and granted permissions.
- STORE_MANAGER: authority limited to own store, devices and employees.
- EMPLOYEE: attendance/presence functions only.

## Agent trust modes
- AUTO_ACTIVATE_NOTIFY: agent activates assigned/new customers without owner approval; owner receives audit notification.
- REQUIRE_APPROVAL: activation requires owner approval.
- ACTIVATION_DISABLED: no activation authority.

## Granular agent permissions
- CUSTOMER_CREATE
- CUSTOMER_VIEW
- CUSTOMER_EDIT
- CUSTOMER_SUSPEND
- CUSTOMER_REACTIVATE
- CUSTOMER_ARCHIVE
- SUBSCRIPTION_ACTIVATE
- SUBSCRIPTION_EXTEND
- DEVICE_MANAGE
- NOTIFICATION_SEND
- REPORT_VIEW

Permanent destructive delete remains SYSTEM_OWNER-only by default.

## Customer lifecycle
ACTIVE -> SUSPENDED -> ACTIVE
ACTIVE/SUSPENDED -> ARCHIVED
ARCHIVED -> ACTIVE only by authorized restore operation.
Permanent delete requires SYSTEM_OWNER authority and explicit confirmation.

## Self registration
Store Manager can create a new store account without owner intervention.
Attribution:
- referral/agent code present -> assigned to that agent
- no agent code -> assigned to SYSTEM_OWNER
Registration, attribution, trial/subscription creation and device identity must be audit logged.

## Owner subscriber management
Each subscriber record exposes:
- name / phone / store
- assigned agent
- subscription type/status/expiry
- account state
- device count and last server activity
Actions:
- edit
- activate/extend
- suspend/reactivate
- archive/restore
- send notification
- transfer agent
- view audit history

## Bulk actions
Multi-select supports notification, suspend, reactivate, archive, subscription extension and agent transfer. Every bulk action records one batch id plus per-target audit result.

## Management notification categories
- ACTIVATION
- AGENT
- REGISTRATION
- SUBSCRIPTION
- DEVICE
- SECURITY
- SYSTEM

## Audit invariant
Every privileged action records actor id, actor role, target id, action, timestamp, old/new state when applicable, result and correlation/batch id.

## Security invariants
- Server enforces permissions; UI visibility is not authorization.
- Agent scope must be checked server-side for every customer operation.
- Owner receives notification for trusted-agent automatic activation.
- No management operation may modify pairing secrets or pairing transport state.
