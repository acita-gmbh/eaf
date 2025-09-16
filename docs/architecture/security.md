# Security

## Core Security Principles

* **Input Validation:** Spring Boot Validation (at API boundary).
* **AuthN/AuthZ:** Mandates Keycloak OIDC, **10-Layer JWT Validation**, and the **3-Layer Tenancy Model (RLS)**.
* **Secrets Management:** Mandates **HashiCorp Vault** (from prototype).
* **API Security:** Mandates ProblemDetails (RFC 7807), standard security headers.
* **Data Protection:** Mandates RLS, no PII in logs, application-layer encryption for PII.
* **Dependency Security:** Mandates **OWASP Dependency-Check** (Gradle) and **SBOM generation**.
* **Security Testing:** Mandates SAST (Detekt) and full integration testing via Keycloak Testcontainer.

## 10-Layer JWT Validation System

The EAF implements a comprehensive 10-layer JWT validation system that prevents all known JWT attack vectors and achieves ASVS 5.0 Level 2 compliance.

**Validation Layers:**

1. **Format Validation**: Verifies JWT structure (header.payload.signature), character encoding, and length constraints
2. **Signature Validation**: Cryptographic verification using RS256/ES256 with public key validation
3. **Algorithm Validation**: Prevents algorithm confusion attacks by enforcing allowed signing algorithms
4. **Claim Schema Validation**: Validates presence and format of required claims (sub, iss, aud, exp, iat, jti, tenant_id, roles)
5. **Time-based Validation**: Checks expiration (exp), issued-at (iat), and not-before (nbf) claims with clock skew tolerance
6. **Issuer/Audience Validation**: Verifies token comes from trusted issuer and intended for our audience
7. **Token Revocation Check**: Queries Redis blacklist for compromised or revoked tokens
8. **Role Validation**: Enforces role whitelist and detects privilege escalation attempts
9. **User Validation**: Verifies user existence and active status in Keycloak
10. **Injection Detection**: Scans token claims for SQL injection, XSS, and JNDI attack patterns

**Implementation Pattern:**

```kotlin
@Component
class SecureJwtValidator {
    fun validate(jwt: Jwt): ValidationResult {
        return validateFormat(jwt)
            .flatMap { validateSignature(it) }
            .flatMap { validateAlgorithm(it) }
            .flatMap { validateClaims(it) }
            .flatMap { validateExpiration(it) }
            .flatMap { validateIssuer(it) }
            .flatMap { checkRevocation(it) }
            .flatMap { validateRole(it) }
            .flatMap { validateUser(it) }
            .flatMap { checkInjection(it) }
    }
}
```

## 3-Layer Tenant Isolation Architecture

Defense-in-depth multi-tenant data isolation with three independent validation layers prevents cross-tenant data access.

**Layer 1 - Request Filter:**

```kotlin
@Component
class TenantContextFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val tenantId = extractTenantFromJWT(request)
        TenantContext.setCurrentTenant(tenantId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            TenantContext.clear()
        }
    }
}
```

**Layer 2 - Service Boundary:**

```kotlin
@Component
@Aspect
class TenantBoundaryValidator {
    @Before("@annotation(TenantBoundary)")
    fun validateTenantAccess(joinPoint: ProceedingJoinPoint) {
        val currentTenant = TenantContext.getCurrentTenant()
        val requestedTenantId = extractTenantFromMethodParams(joinPoint)

        if (currentTenant != requestedTenantId) {
            throw SecurityException("Cross-tenant access denied")
        }
    }
}
```

**Layer 3 - Database Interceptor:**

```kotlin
@Component
class TenantIsolationInterceptor : Interceptor {
    override fun onLoad(event: LoadEvent) {
        val sql = event.sql
        if (requiresTenantFilter(sql)) {
            event.sql = injectTenantFilter(sql, TenantContext.getCurrentTenant())
        }
    }
}
```

## Emergency Security Recovery Procedures

The EAF implements a 5-phase emergency security recovery process:

**Phase 1 - Immediate Response (0-4 hours):**
* Activate emergency JWT token revocation via Redis blacklist
* Enable emergency circuit breaker patterns
* Isolate affected tenant data if breach is tenant-specific
* Activate monitoring and alerting escalation

**Phase 2 - Assessment (4-12 hours):**
* Run automated security validation suite (43+ security tests)
* Perform ASVS compliance verification
* Execute cross-tenant isolation validation
* Generate security incident report

**Phase 3 - Hardening (12-48 hours):**
* Apply security patches to all 10 validation layers
* Update tenant isolation enforcement
* Refresh all cryptographic keys
* Update security monitoring rules

**Phase 4 - Validation (48-96 hours):**
* Execute full security test suite
* Validate ASVS Level 1 (95% minimum) and Level 2 (80% target) compliance
* Perform penetration testing on hardened system
* Validate zero cross-tenant access violations

**Phase 5 - Recovery (96-120 hours):**
* Gradual system restoration with enhanced monitoring
* Real-time threat detection activation
* Security incident lessons learned documentation
* Update emergency response playbook

**Recovery Success Metrics:**
* 100% critical vulnerabilities addressed
* 43+ security tests passing
* Zero cross-tenant access violations
* ASVS compliance restored within 5 days

## Network Segmentation & Hardening

  * Deployment topology isolates tiers: Traefik ingress and NGINX frontend reside in a DMZ subnet; the Spring Boot service, Redis, and Flowable workers live in a protected application subnet; Postgres and Vault run in a data subnet exposed only to application security groups.
  * All east-west traffic uses mTLS with certificates rotated by Vault. External TLS terminates at Traefik with automatic Let's Encrypt renewal.

## Identity & Access Governance

  * Service-to-service access relies on short-lived Vault-issued credentials (24 h TTL, renewable). IAM policies grant least privilege (e.g., backend role can read Postgres credentials but cannot modify Vault mounts).
  * Operator roles map to Keycloak groups; licensing flows require `role_product_manager`, tenant administration requires `role_security_admin`.

## Rate Limiting & Abuse Controls

  * Traefik enforces per-tenant throttles of 100 requests/min with bursts of 20. Suspicious patterns move clients to a degrated rate of 20 requests/min for 15 minutes.
  * JWT validation checks issuer, audience, tenant claim, and token freshness (max age 5 minutes skew). Replay detection uses Redis nonce store for high-risk operations.

## Data Lifecycle & Retention

  * Event store retains 18 months of history by default; projections retain 24 months with monthly archival to cold storage. Audit logs remain 90 days online and 12 months in WORM storage.
  * Scheduled jobs (Spring Batch) clean expired licenses and anonymise soft-deleted tenants, respecting legal hold flags stored in Vault.

## Security Monitoring & Response

  * SIEM integration: Keycloak, application audit logs, and Vault events ship to the central SOC via Fluent Bit.
  * Alert thresholds: more than five failed logins per minute per tenant triggers a medium-severity alert; circuit breaker open events escalate to on-call.
  * Incident response runbooks define containment, eradication, and recovery steps; tabletop exercises occur twice per year.

-----
