# DVMM Security Architecture

**Author:** Wall-E
**Date:** 2025-11-25
**Version:** 1.0
**Classification:** Internal
**Compliance Targets:** ISO 27001, GDPR

---

## Executive Summary

This document defines the security architecture for DVMM (Dynamic Virtual Machine Manager), a multi-tenant self-service portal for VMware VM provisioning. Security is a first-class architectural concern, with defense-in-depth principles applied across all layers.

**Key Security Decisions:**
- **Multi-Tenant Isolation:** PostgreSQL Row-Level Security (RLS) with fail-closed semantics
- **Authentication:** Keycloak OIDC with JWT tokens containing tenant_id claim
- **Authorization:** RBAC with three roles (User, Admin, Manager)
- **Data Protection:** TLS 1.3 in transit, AES-256 at rest, Crypto-Shredding for GDPR
- **Audit:** Immutable Event Store with 7-year retention

---

## 1. Threat Model

### 1.1 Assets

| Asset | Classification | Description |
|-------|----------------|-------------|
| VM Request Data | Confidential | User requests, justifications, approvals |
| User PII | Personal | Names, emails, audit trail |
| Tenant Configuration | Confidential | VMware credentials, quotas |
| VMware Credentials | Secret | Service account passwords |
| JWT Tokens | Secret | Authentication tokens |
| Event Store | Confidential | Complete audit history |

### 1.2 Threat Actors

| Actor | Motivation | Capability |
|-------|------------|------------|
| External Attacker | Data theft, ransomware | High (automated tools) |
| Malicious Tenant | Access other tenant data | Medium (authenticated) |
| Insider Threat | Data exfiltration | High (legitimate access) |
| Compromised Admin | Privilege abuse | High (elevated access) |

### 1.3 STRIDE Analysis

| Threat | Component | Mitigation |
|--------|-----------|------------|
| **S**poofing | Authentication | Keycloak OIDC, JWT validation, no local passwords |
| **T**ampering | Event Store | Append-only store, no UPDATE/DELETE |
| **R**epudiation | Audit Trail | Immutable events with correlation IDs |
| **I**nformation Disclosure | Multi-Tenancy | PostgreSQL RLS, fail-closed |
| **D**enial of Service | API | Rate limiting (100 req/min/user) |
| **E**levation of Privilege | Authorization | RBAC, principle of least privilege |

### 1.4 Attack Surface

```
┌─────────────────────────────────────────────────────────────────┐
│                        ATTACK SURFACE                           │
├─────────────────────────────────────────────────────────────────┤
│  Internet                                                       │
│     │                                                           │
│     ▼                                                           │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐       │
│  │   WAF/CDN   │────▶│   Keycloak  │────▶│   DVMM API  │       │
│  │  (Future)   │     │    (OIDC)   │     │  (Spring)   │       │
│  └─────────────┘     └─────────────┘     └─────────────┘       │
│                                                │                │
│                                                ▼                │
│                           ┌─────────────────────────────────┐  │
│                           │         PostgreSQL              │  │
│                           │   (RLS + Encrypted at Rest)     │  │
│                           └─────────────────────────────────┘  │
│                                                │                │
│                                                ▼                │
│                           ┌─────────────────────────────────┐  │
│                           │         VMware vSphere          │  │
│                           │   (Isolated Network Segment)    │  │
│                           └─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Authentication Architecture

### 2.1 Identity Provider Integration

```
┌──────────────┐     OIDC     ┌──────────────┐
│   Browser    │─────────────▶│   Keycloak   │
│  (SPA/React) │◀─────────────│   (IdP)      │
└──────────────┘   JWT Token  └──────────────┘
       │                             │
       │ Bearer Token                │ User DB
       ▼                             ▼
┌──────────────┐              ┌──────────────┐
│   DVMM API   │              │   LDAP/AD    │
│  (Validate)  │              │  (Optional)  │
└──────────────┘              └──────────────┘
```

### 2.2 JWT Token Structure

```json
{
  "sub": "user-uuid",
  "tenant_id": "tenant-uuid",
  "email": "user@example.com",
  "roles": ["user", "admin"],
  "iat": 1700000000,
  "exp": 1700003600
}
```

### 2.3 Token Handling

| Aspect | Implementation | NFR Reference |
|--------|----------------|---------------|
| Storage | httpOnly cookie (Secure, SameSite=Lax) | NFR-SEC-1 |
| Refresh | Silent refresh via Keycloak | FR7a |
| Expiration | 1 hour access, 8 hour refresh | NFR-SEC-2 |
| Validation | Every request, signature + expiry | NFR-SEC-1 |
| CSRF Protection | X-CSRF-Token header | NFR-SEC-10 |

### 2.4 Authentication Flows

**Login Flow:**
1. User navigates to DVMM
2. Redirect to Keycloak login
3. User authenticates (password, MFA if enabled)
4. Keycloak issues JWT with tenant_id claim
5. DVMM validates token, extracts tenant context
6. Session established

**Token Refresh:**
1. Access token expires (1 hour)
2. Frontend silently requests new token
3. If refresh fails → redirect to login

---

## 3. Authorization Architecture

### 3.1 Role-Based Access Control (RBAC)

| Role | Permissions | Scope |
|------|-------------|-------|
| **User** | Create requests, view own requests, view projects | Own data |
| **Admin** | Approve/reject, manage projects, view all tenant data | Tenant |
| **Manager** | Reports, audit logs, compliance dashboards | Tenant |

### 3.2 Permission Matrix

| Resource | User | Admin | Manager |
|----------|------|-------|---------|
| VM Requests (own) | CRUD | CRUD | R |
| VM Requests (all) | - | RU | R |
| Projects | R | CRUD | R |
| Approvals | - | CRU | R |
| Audit Logs | - | R | R |
| VMware Config | - | RU | - |
| Reports | - | R | R |
| User Management | - | RU | - |

### 3.3 Authorization Enforcement

```kotlin
// Controller Level
@PreAuthorize("hasRole('ADMIN')")
fun approveRequest(...)

// Service Level
fun getRequests(): List<Request> {
    val tenantId = TenantContext.current() // Enforced
    return repository.findByTenant(tenantId)
}

// Database Level (RLS)
CREATE POLICY tenant_isolation ON vm_requests
    USING (tenant_id = current_setting('app.tenant_id')::uuid);
```

---

## 4. Multi-Tenant Isolation

### 4.1 Isolation Strategy

**Defense in Depth - 3 Layers:**

| Layer | Mechanism | Failure Mode |
|-------|-----------|--------------|
| **Application** | TenantContext (Coroutine) | Exception |
| **Service** | Tenant filter on all queries | Empty result |
| **Database** | PostgreSQL RLS | Zero rows (fail-closed) |

### 4.2 Tenant Context Propagation

```kotlin
// CoroutineContext Element
class TenantContextElement(val tenantId: TenantId) :
    CoroutineContext.Element

// WebFilter extracts from JWT
class TenantContextWebFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val tenantId = extractTenantFromJwt(exchange)
        return chain.filter(exchange)
            .contextWrite { it.put(TenantContext.KEY, tenantId) }
    }
}
```

### 4.3 Row-Level Security (RLS)

```sql
-- Enable RLS
ALTER TABLE vm_requests ENABLE ROW LEVEL SECURITY;

-- Create policy (fail-closed)
CREATE POLICY tenant_isolation ON vm_requests
    FOR ALL
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- Connection setup (HikariCP)
SET app.tenant_id = '${tenantId}';
```

### 4.4 Tenant Isolation Verification

**Automated Tests (Story 5.7):**
- Cross-tenant query prevention
- Cross-tenant write prevention
- Missing context = zero rows (not all rows!)
- Stress test: 100 parallel coroutines, no cross-contamination

---

## 5. Data Protection

### 5.1 Encryption Standards

| Data State | Method | Key Management |
|------------|--------|----------------|
| In Transit | TLS 1.3 | Let's Encrypt / Internal CA |
| At Rest (DB) | AES-256 | PostgreSQL native |
| At Rest (Files) | AES-256 | File system encryption |
| Secrets | AES-256-GCM | Vault / Environment |
| PII in Events | AES-256 | Per-user keys (Crypto-Shredding) |

### 5.2 Crypto-Shredding Pattern (GDPR)

```
┌─────────────────┐     ┌─────────────────┐
│   Event Store   │     │   Key Store     │
├─────────────────┤     ├─────────────────┤
│ aggregate_id    │     │ user_id (PK)    │
│ event_type      │     │ encryption_key  │
│ encrypted_pii   │────▶│ created_at      │
│ metadata        │     │ destroyed_at    │
└─────────────────┘     └─────────────────┘
                              │
                              ▼
                        Key Destroyed
                              │
                              ▼
                    encrypted_pii = garbage
```

**GDPR Deletion Process:**
1. User requests deletion (Art. 17)
2. System destroys user's encryption key
3. PII in events becomes unreadable
4. Audit structure preserved (compliance)
5. Reports show "[GDPR DELETED]"

### 5.3 Secrets Management

| Secret Type | Storage | Rotation |
|-------------|---------|----------|
| VMware credentials | Encrypted DB | Manual (admin) |
| Database password | Environment/Vault | Quarterly |
| JWT signing key | Keycloak | Annual |
| API keys | Environment/Vault | On compromise |

---

## 6. API Security

### 6.1 Security Controls

| Control | Implementation | NFR Reference |
|---------|----------------|---------------|
| Authentication | Bearer JWT | NFR-SEC-1 |
| Rate Limiting | 100 req/min/user | NFR-SEC-9 |
| Input Validation | Bean Validation + Custom | NFR-SEC-6 |
| Output Encoding | Jackson defaults | NFR-SEC-7 |
| CORS | Whitelist origins | NFR-SEC-8 |
| CSRF | Token-based | NFR-SEC-10 |

### 6.2 Input Validation

```kotlin
data class CreateVmRequestCommand(
    @field:NotBlank
    @field:Size(min = 3, max = 63)
    @field:Pattern(regexp = "^[a-z0-9-]+$")
    val vmName: String,

    @field:NotNull
    val projectId: UUID,

    @field:NotBlank
    @field:Size(min = 10, max = 1000)
    val justification: String,

    @field:NotNull
    val size: VmSize
)
```

### 6.3 Error Handling (No Information Leakage)

```kotlin
// Sanitized error response
{
    "error": "VALIDATION_FAILED",
    "message": "Invalid input",
    "correlationId": "abc-123",
    // NO stack traces, NO internal details
}
```

---

## 7. Audit & Compliance

### 7.1 Audit Trail Requirements

| Requirement | Implementation | NFR Reference |
|-------------|----------------|---------------|
| Complete trail | Event Sourcing | NFR-COMP-2 |
| Immutability | Append-only store | NFR-COMP-3 |
| Retention | 7 years | NFR-COMP-4 |
| Who/What/When | Event metadata | FR60 |
| Correlation | CorrelationId | NFR-OBS-2 |

### 7.2 Audit Event Structure

```kotlin
data class AuditEvent(
    val id: UUID,
    val tenantId: UUID,
    val correlationId: String,
    val aggregateType: String,
    val aggregateId: UUID,
    val eventType: String,
    val actorId: UUID,
    val actorEmail: String,
    val actorRole: String,
    val timestamp: Instant,
    val changes: JsonNode,  // What changed
    val metadata: JsonNode  // IP, User-Agent
)
```

### 7.3 ISO 27001 Control Mapping

| ISO Control | Description | DVMM Implementation |
|-------------|-------------|---------------------|
| A.9.2.3 | Privileged access management | RBAC, approval workflow |
| A.9.4.1 | Information access restriction | RLS, tenant isolation |
| A.12.4.1 | Event logging | Event Sourcing audit trail |
| A.12.4.3 | Admin activity logs | All admin actions logged |
| A.18.1.3 | Records protection | Immutable event store |

---

## 8. Infrastructure Security

### 8.1 Network Segmentation

```
┌─────────────────────────────────────────────────────────────┐
│                      DMZ / Public                           │
│  ┌─────────────┐                                           │
│  │   Keycloak  │  (OIDC Endpoint)                          │
│  └─────────────┘                                           │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Application Tier                         │
│  ┌─────────────┐     ┌─────────────┐                       │
│  │   DVMM API  │     │  Frontend   │                       │
│  └─────────────┘     │   (Static)  │                       │
│                      └─────────────┘                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Data Tier                              │
│  ┌─────────────┐     ┌─────────────┐                       │
│  │ PostgreSQL  │     │    Redis    │                       │
│  │   (RLS)     │     │  (Cache)    │                       │
│  └─────────────┘     └─────────────┘                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Infrastructure Tier                       │
│  ┌─────────────┐                                           │
│  │   VMware    │  (Isolated VLAN)                          │
│  │   vSphere   │                                           │
│  └─────────────┘                                           │
└─────────────────────────────────────────────────────────────┘
```

### 8.2 Data Residency

| Data Type | Location | NFR Reference |
|-----------|----------|---------------|
| Application | Germany (DE) | NFR-COMP-9 |
| Database | Germany (DE) | NFR-COMP-6 |
| Backups | Germany (DE) | NFR-COMP-10 |
| Logs | EU | NFR-COMP-11 |

---

## 9. Security Testing

### 9.1 Testing Strategy

| Test Type | Frequency | Tools |
|-----------|-----------|-------|
| SAST | Every commit | SonarQube, Detekt |
| Dependency Scan | Every build | OWASP Dependency-Check |
| Container Scan | Every build | Trivy |
| DAST | Weekly (Growth) | OWASP ZAP |
| Penetration Test | Annual | External vendor |

### 9.2 Security Quality Gates

```yaml
# CI Pipeline Security Gates
security_gates:
  sast:
    critical_vulnerabilities: 0
    high_vulnerabilities: 0
  dependencies:
    critical_cve: 0
    high_cve: 0  # Block on high
  container:
    critical: 0
```

---

## 10. Incident Response

### 10.1 Security Incident Classification

| Severity | Definition | Response Time |
|----------|------------|---------------|
| P1 Critical | Active breach, data loss | Immediate |
| P2 High | Vulnerability exploited | < 4 hours |
| P3 Medium | Vulnerability discovered | < 24 hours |
| P4 Low | Security improvement | Next sprint |

### 10.2 Incident Response Process

1. **Detection:** Alerts from monitoring, user reports
2. **Triage:** Classify severity, assign responder
3. **Containment:** Isolate affected systems
4. **Eradication:** Remove threat, patch vulnerability
5. **Recovery:** Restore services, verify integrity
6. **Lessons Learned:** Post-incident review, update controls

---

## Appendix A: Security Requirements Traceability

| NFR ID | Requirement | Section |
|--------|-------------|---------|
| NFR-SEC-1 | OIDC authentication | 2.1 |
| NFR-SEC-2 | JWT validation | 2.3 |
| NFR-SEC-3 | RBAC authorization | 3.1 |
| NFR-SEC-4 | Tenant isolation (RLS) | 4.3 |
| NFR-SEC-5 | TLS 1.3 | 5.1 |
| NFR-SEC-6 | Input validation | 6.2 |
| NFR-SEC-7 | Output encoding | 6.1 |
| NFR-SEC-8 | CORS whitelist | 6.1 |
| NFR-SEC-9 | Rate limiting | 6.1 |
| NFR-SEC-10 | CSRF protection | 2.3 |
| NFR-SEC-11 | Secrets management | 5.3 |
| NFR-SEC-12 | Dependency scanning | 9.1 |
| NFR-COMP-1 | ISO 27001 | 7.3 |
| NFR-COMP-2 | Audit trail | 7.1 |
| NFR-COMP-4a | Crypto-Shredding | 5.2 |

---

*This Security Architecture document is part of the DVMM Enterprise Method documentation.*
