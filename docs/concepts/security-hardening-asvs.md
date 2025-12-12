# Security Hardening (OWASP ASVS)

**Moving from "Secure" to "Certified Secure".**

ISO 27001 is about *process*. **OWASP ASVS (Application Security Verification Standard)** is about *technical proof*. It provides a rigorous checklist of controls (Level 1, 2, 3) to verify that the application is technically secure.

DCM targets **ASVS Level 2** (Applications handling sensitive data).

---

## Key Control Mapping

We map ASVS requirements directly to our architecture.

### 1. Authentication (ASVS V2)
*   **Requirement:** Verify that passwords are not stored in plain text.
*   **DCM Solution:** We **delegate** this entirely to Keycloak. We never see or store passwords. We verify Keycloak's configuration (PBKDF2/Argon2 hashing).

### 2. Session Management (ASVS V3)
*   **Requirement:** Verify session tokens have timeouts and are invalidated on logout.
*   **DCM Solution:**
    *   **Stateless JWTs:** Short lifespan (e.g., 5-15 mins).
    *   **Refresh Tokens:** Securely stored, revoked on logout.
    *   **BFF Pattern:** Use a "Backend for Frontend" proxy to keep tokens out of browser local storage (using HttpOnly cookies instead).

### 3. Access Control (ASVS V4)
*   **Requirement:** Verify that users cannot access data of other users/tenants (IDOR).
*   **DCM Solution:**
    *   **RLS (Row-Level Security):** Database-level enforcement of tenant boundaries.
    *   **Architecture Tests:** Automated tests that explicitly try to break isolation.

### 4. Input Validation (ASVS V5)
*   **Requirement:** Verify that all input is validated before use.
*   **DCM Solution:**
    *   **Strong Typing:** Kotlin Value Objects (`Email`, `VmName`). You can't pass a raw String where an `Email` is required.
    *   **Bean Validation:** `@Valid`, `@NotNull` annotations on all DTOs.

### 5. Cryptography (ASVS V6)
*   **Requirement:** Verify proper use of encryption.
*   **DCM Solution:**
    *   **Crypto-Shredding:** Per-user AES-256-GCM keys for PII.
    *   **TLS 1.3:** Enforced for all connections.

## The "Shift Left" Strategy

We don't wait for a pentest to find ASVS violations.

1.  **IDE:** SonarLint checks for common flaws while coding.
2.  **CI/CD:** SAST (Static Analysis) scans every PR.
3.  **Dependency Check:** Automated scans for vulnerable libraries (Log4Shell-style issues).

## Why This Matters

Passing an ASVS verification gives enterprise customers (banks, government) the confidence that DCM is hardened against sophisticated attacks, not just script kiddies.
