# Licensing System Architecture

**Protecting our IP while enabling flexible commercial models.**

As DVMM moves from an internal tool to a commercial product (SaaS and On-Premise), we need a robust way to enforce usage limits. We need to prevent piracy without annoying legitimate customers.

---

## The License Key

A license key is not just a random string. It is a **Signed JWT (JSON Web Token)** containing the entitlement details.

### Structure
```json
{
  "sub": "customer-uuid",
  "iss": "dvmm-licensing-server",
  "exp": 1767225600, // Expiration Date
  "entitlements": {
    "tier": "ENTERPRISE",
    "max_vms": 500,
    "max_users": 50,
    "features": ["SSO", "AUDIT_EXPORT", "MULTI_HYPERVISOR"]
  },
  "signature": "..." // Cryptographically signed by our Private Key
}
```

## Validation Process

### 1. Offline Validation (The Fast Check)
The application has our **Public Key** embedded in the binary. On startup (and periodically), it verifies the signature of the installed license key.
*   **Pros:** Works without internet access (air-gapped datacenters). Fast.
*   **Cons:** Cannot detect revoked keys immediately.

### 2. Online Validation (The Heartbeat)
For connected instances, the system sends a daily "heartbeat" to our Licensing Server (`license.dvmm.io`).
*   **Data Sent:** `license_id`, `usage_metrics` (anonymized VM counts), `instance_id`.
*   **Response:** `OK`, `REVOKED`, or `RENEWED` (auto-update key).

## Enforcement Strategy: "Soft Limits"

We avoid "hard stops" that break production systems.

*   **Hard Limit:** Expiration Date. After this date, the system goes into Read-Only mode.
*   **Soft Limit:** VM Count. If a customer pays for 500 VMs and tries to create #501:
    *   **Allow it** (for a grace period/buffer, e.g., +10%).
    *   **Warn Admins** via email/dashboard banner ("License exceeded").
    *   **Block** only after significant overage (e.g., +20%).

## Grace Periods

If the Online Validation fails (e.g., firewall issue), we don't shut down immediately. We enter a **Grace Period** (e.g., 14 days).
*   **Day 1-14:** "Warning: Cannot contact license server."
*   **Day 15:** "Error: License validation failed. Functionality restricted."

## Security

*   **Anti-Tamper:** The license logic is obfuscated in the JAR.
*   **Node Locking:** The license is bound to a specific `instance_id` (generated on first run) to prevent copying the key to 100 servers.

## Summary

*   **Format:** Signed JWT.
*   **Policy:** Trust but verify (Offline check + Online heartbeat).
*   **UX:** Warn before blocking. Never kill running VMs.
