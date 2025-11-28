# Security Partnership Briefing: EAF/DVMM Project

**Prepared for:** Strong IT SOC Team
**Date:** 2025-11-28
**Classification:** Internal
**Version:** 1.0

---

## Executive Summary

This document provides a comprehensive overview of the **EAF (Enterprise Application Framework)** and **DVMM (Dynamic Virtual Machine Manager)** project to facilitate evaluation by our internal SOC team for security partnership opportunities.

**Key Facts:**
- **Product Type:** Multi-tenant B2B SaaS for VMware VM provisioning
- **Target Market:** German Mittelstand (500-5,000 employees), CSPs
- **Compliance Targets:** ISO 27001, GDPR
- **Tech Stack:** Kotlin 2.2, Spring Boot 3.5, PostgreSQL 16, Keycloak, VMware vSphere API
- **Architecture Pattern:** CQRS + Event Sourcing, Hexagonal Architecture

**Primary Security Concerns:**
1. Multi-tenant data isolation (PostgreSQL RLS)
2. Keycloak OIDC integration and JWT handling
3. VMware API security (credential management, network isolation)
4. GDPR-compliant audit trail with Crypto-Shredding
5. CI/CD security pipeline

---

## 1. Project Overview

### 1.1 What is DVMM?

DVMM is a **self-service portal** enabling:
- End users to request virtual machines through a web interface
- IT administrators to approve/reject requests via workflow
- Automated VM provisioning on VMware vSphere
- Complete audit trail for compliance (ISO 27001)

**Core Workflow:**
```text
User Request → Approval Workflow → VM Provisioned → Notification
     ↓              ↓                    ↓               ↓
  Form UI     Admin Dashboard       VMware API       Email/Portal
```

### 1.2 Why This Project Matters

| Driver | Impact |
|--------|--------|
| **VMware Migration Wave** | 74% of IT leaders exploring alternatives post-Broadcom acquisition |
| **Compliance Requirements** | ISO 27001 certification currently blocked by legacy system |
| **Multi-Tenant SaaS** | Serve multiple customers with strict data isolation |
| **Market Opportunity** | DACH SAM: €280M-€420M |

### 1.3 EAF Framework

DVMM is built on a reusable **Enterprise Application Framework (EAF)** containing:
- `eaf-core` - Domain primitives (pure Kotlin, zero dependencies)
- `eaf-eventsourcing` - Event Store, projections, snapshots
- `eaf-tenant` - Multi-tenancy with PostgreSQL RLS
- `eaf-auth` - IdP-agnostic authentication interfaces
- `eaf-audit` - Audit trail with crypto-shredding utilities

---

## 2. Security Architecture Overview

### 2.1 High-Level Attack Surface

```text
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

### 2.2 Asset Classification

| Asset | Classification | Description |
|-------|----------------|-------------|
| VM Request Data | Confidential | User requests, justifications, approvals |
| User PII | Personal | Names, emails, audit trail |
| Tenant Configuration | Confidential | VMware credentials, quotas |
| VMware Credentials | **Secret** | Service account passwords |
| JWT Tokens | **Secret** | Authentication tokens |
| Event Store | Confidential | Complete audit history (7 years) |

### 2.3 Threat Actors

| Actor | Motivation | Capability |
|-------|------------|------------|
| External Attacker | Data theft, ransomware | High (automated tools) |
| Malicious Tenant | Access other tenant data | Medium (authenticated) |
| Insider Threat | Data exfiltration | High (legitimate access) |
| Compromised Admin | Privilege abuse | High (elevated access) |

### 2.4 STRIDE Analysis (Implemented)

| Threat | Component | Current Mitigation |
|--------|-----------|-------------------|
| **S**poofing | Authentication | Keycloak OIDC, JWT validation, no local passwords |
| **T**ampering | Event Store | Append-only store, no UPDATE/DELETE |
| **R**epudiation | Audit Trail | Immutable events with correlation IDs |
| **I**nformation Disclosure | Multi-Tenancy | PostgreSQL RLS, fail-closed |
| **D**enial of Service | API | Rate limiting (100 req/min/user) |
| **E**levation of Privilege | Authorization | RBAC, principle of least privilege |

---

## 3. Detailed Security Controls

### 3.1 Authentication (Keycloak OIDC)

**Current Implementation:**
```text
User → Keycloak Login → JWT Token → DVMM API Validation
         ↓
   MFA (optional)
   LDAP/AD (optional)
```

**JWT Token Structure:**
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

**Token Handling:**
| Aspect | Implementation |
|--------|----------------|
| Storage | httpOnly cookie (Secure, SameSite=Lax) |
| Access Token Expiration | 30 minutes |
| Refresh Token Expiration | 8 hours |
| Validation | Every request (signature + expiry) |
| CSRF Protection | X-CSRF-Token header |

**Review Needed:**
- [ ] Keycloak hardening configuration
- [ ] Token rotation strategy
- [ ] Session management across devices
- [ ] MFA enforcement policies

### 3.2 Multi-Tenant Isolation (PostgreSQL RLS)

**Defense in Depth - 3 Layers:**

| Layer | Mechanism | Failure Mode |
|-------|-----------|--------------|
| Application | TenantContext (Kotlin Coroutine) | Exception thrown |
| Service | Tenant filter on all queries | Empty result set |
| Database | PostgreSQL RLS Policy | Zero rows (fail-closed) |

**RLS Implementation:**
```sql
-- Enable RLS on all tenant tables
ALTER TABLE vm_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE vm_requests FORCE ROW LEVEL SECURITY;

-- Create fail-closed policy
CREATE POLICY tenant_isolation ON vm_requests
    FOR ALL
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- Connection context (set per-request, NOT in connection pool init)
SET LOCAL app.tenant_id = '${tenantId}';
```

**Critical Design Decision:**
> Tenant context is set via `SET LOCAL` per-request in the WebFilter, NOT via connection pool initialization. This prevents cross-tenant data leakage on pooled connections.

**Review Needed:**
- [ ] RLS policy completeness audit
- [ ] Cross-tenant query testing
- [ ] Tenant context propagation in async operations
- [ ] Connection pool security review

### 3.3 Authorization (RBAC)

**Role Matrix:**

| Role | Permissions | Scope |
|------|-------------|-------|
| **User** | Create requests, view own requests | Own data only |
| **Admin** | Approve/reject, manage projects, manage users | Tenant-wide |
| **Manager** | Reports, audit logs, compliance dashboards | Tenant-wide (read) |

**Permission Enforcement Layers:**
1. **Controller:** `@PreAuthorize("hasRole('ADMIN')")`
2. **Service:** `TenantContext.current()` enforced
3. **Database:** RLS policy active

**Review Needed:**
- [ ] Role assignment workflow security
- [ ] Privilege escalation testing
- [ ] Permission boundary enforcement

### 3.4 Data Protection

**Encryption Standards:**

| Data State | Method | Key Management |
|------------|--------|----------------|
| In Transit | TLS 1.3 | Let's Encrypt / Internal CA |
| At Rest (DB) | AES-256 | OS-level (dm-crypt/LUKS) |
| Secrets | AES-256-GCM | Environment/Vault |
| PII in Events | AES-256 | Per-user keys (Crypto-Shredding) |

**Crypto-Shredding Pattern (GDPR Compliance):**

```text
┌─────────────────┐     ┌─────────────────┐
│   Event Store   │     │   Key Store     │
├─────────────────┤     ├─────────────────┤
│ aggregate_id    │     │ user_id (PK)    │
│ event_type      │     │ encryption_key  │
│ encrypted_pii   │────▶│ created_at      │
│ metadata        │     │ destroyed_at    │
└─────────────────┘     └─────────────────┘
                              │
                        Key Destroyed (GDPR Art. 17)
                              │
                    encrypted_pii = unreadable garbage
```

**GDPR Deletion Process:**
1. User requests deletion (Art. 17)
2. System destroys user's encryption key
3. PII in events becomes unreadable
4. Audit structure preserved (compliance)
5. Reports show "[GDPR DELETED]"

**Review Needed:**
- [ ] Key management implementation review
- [ ] Key rotation procedures
- [ ] Crypto-shredding implementation audit
- [ ] TLS configuration hardening

### 3.5 VMware Integration Security

**Credential Management:**
| Secret Type | Current Storage | Rotation |
|-------------|-----------------|----------|
| VMware service account | Encrypted DB column | Manual (admin) |
| VMware API certificates | Environment/Vault | On compromise |

**Network Isolation:**
- VMware vSphere in isolated VLAN
- No direct internet access
- DVMM API is only gateway

**Review Needed:**
- [ ] VMware credential storage security
- [ ] API call authentication review
- [ ] Network segmentation validation
- [ ] Privileged Access Management for VMware accounts

### 3.6 API Security

| Control | Implementation |
|---------|----------------|
| Authentication | Bearer JWT (required) |
| Rate Limiting | 100 req/min/user (WebFilter) |
| Input Validation | Bean Validation + Custom validators |
| Output Encoding | Jackson defaults |
| CORS | Whitelist origins only |
| CSRF | Token-based protection |

**Error Handling (No Information Leakage):**
```json
{
    "error": "VALIDATION_FAILED",
    "message": "Invalid input",
    "correlationId": "abc-123"
    // NO stack traces, NO internal details
}
```

**Review Needed:**
- [ ] API endpoint security audit
- [ ] Input validation completeness
- [ ] Rate limiting effectiveness
- [ ] CORS policy review

---

## 4. Compliance Requirements

### 4.1 ISO 27001 Control Mapping

| ISO Control | Description | DVMM Implementation |
|-------------|-------------|---------------------|
| A.9.2.3 | Privileged access management | RBAC, approval workflow |
| A.9.4.1 | Information access restriction | RLS, tenant isolation |
| A.12.4.1 | Event logging | Event Sourcing audit trail |
| A.12.4.3 | Admin activity logs | All admin actions logged |
| A.18.1.3 | Records protection | Immutable event store |

### 4.2 GDPR Requirements

| Requirement | Implementation |
|-------------|----------------|
| Data Subject Access (Art. 15) | Export capability planned |
| Right to Erasure (Art. 17) | Crypto-Shredding pattern |
| Data Residency | Germany (DE) only |
| Audit Trail | 7-year retention |

### 4.3 Data Residency

| Data Type | Location |
|-----------|----------|
| Application hosting | Germany (DE) |
| PostgreSQL database | Germany (DE) |
| Backups | Germany (DE) |
| Log aggregation | EU (max) |

---

## 5. CI/CD Security Pipeline

### 5.1 Pipeline Security Gates

```yaml
quality_gates:
  coverage:
    threshold: 80%
    tool: kover

  mutation_testing:
    threshold: 70%
    tool: pitest

  architecture_tests:
    tool: konsist
    rules:
      - "eaf modules have no dvmm dependencies"
      - "domain has no infrastructure dependencies"

  security:
    sast:
      tool: detekt, sonarqube
      critical_vulnerabilities: 0
      high_vulnerabilities: 0

    dependencies:
      tool: owasp-dependency-check
      critical_cve: 0
      high_cve: 0

    container:
      tool: trivy
      severity: CRITICAL,HIGH
      exit_code: 1
```

### 5.2 Security Testing Strategy

| Test Type | Frequency | Tools |
|-----------|-----------|-------|
| SAST | Every commit | SonarQube, Detekt |
| Dependency Scan | Every build | OWASP Dependency-Check |
| Container Scan | Every build | Trivy |
| DAST | Weekly (Growth phase) | OWASP ZAP |
| Penetration Test | Annual | **External vendor (Strong IT?)** |

### 5.3 Branch Protection

- `main` branch protected
- Require PR with CI pass
- Require 1 code review approval
- No direct pushes allowed
- Auto-delete merged branches

---

## 6. Infrastructure & Operations

### 6.1 Monitoring Stack

```text
Grafana (Dashboards) ← Prometheus (Metrics) ← DVMM API (/actuator)
        ↑
       Loki (Logs) ← Promtail (Log Shipping)
        ↑
   AlertManager → PagerDuty / Slack
```

### 6.2 Incident Response Classification

| Severity | Definition | Response Time |
|----------|------------|---------------|
| P1 Critical | Active breach, data loss | Immediate |
| P2 High | Vulnerability exploited | < 4 hours |
| P3 Medium | Vulnerability discovered | < 24 hours |
| P4 Low | Security improvement | Next sprint |

### 6.3 Disaster Recovery

| Metric | Target |
|--------|--------|
| RTO (Recovery Time Objective) | < 4 hours |
| RPO (Recovery Point Objective) | < 1 hour |
| Backup Frequency | Daily + WAL (continuous) |
| Backup Retention | 30 days |

---

## 7. Identified Areas for Strong IT Assistance

Based on Strong IT's service portfolio and our project requirements, we identify the following collaboration opportunities:

### 7.1 Attack Services (Offensive Security)

| Service | Our Need | Priority |
|---------|----------|----------|
| **Web Application Pentest** | Full security assessment of DVMM portal | **HIGH** |
| **API Security Testing** | REST API vulnerability assessment | **HIGH** |
| **Lateral Movement Analysis** | Multi-tenant isolation verification | **HIGH** |
| **MITRE ATT&CK Assessment** | Threat model validation | MEDIUM |
| **Phishing Simulation** | Admin account protection testing | LOW (Growth) |

### 7.2 Defense Services (Hardening)

| Service | Our Need | Priority |
|---------|----------|----------|
| **Active Directory Hardening** | Keycloak/LDAP integration security | MEDIUM |
| **Infrastructure Security** | PostgreSQL, Kubernetes hardening | **HIGH** |
| **Privileged Access Management** | VMware credential management (Delinea?) | **HIGH** |
| **Endpoint Protection** | API server hardening (CrowdStrike?) | MEDIUM |

### 7.3 Hunting Services (Detection & Response)

| Service | Our Need | Priority |
|---------|----------|----------|
| **24/7 MDR** | Production monitoring (Growth phase) | LOW |
| **SIEM Implementation** | Log analysis, anomaly detection | MEDIUM |
| **Tabletop Exercises** | Incident response preparedness | MEDIUM |

### 7.4 Consulting & Architecture

| Service | Our Need | Priority |
|---------|----------|----------|
| **Security Architecture Review** | Validate our design decisions | **HIGH** |
| **ISO 27001 Readiness Assessment** | Pre-audit preparation | **HIGH** |
| **Threat Modeling Workshop** | Enhance STRIDE analysis | **HIGH** |
| **Secure SDLC Integration** | CI/CD security gate optimization | MEDIUM |

---

## 8. Specific Questions for Strong IT

### 8.1 Architecture Review
1. Is PostgreSQL RLS sufficient for multi-tenant isolation, or should we add application-level encryption?
2. What's the recommended approach for VMware credential management in a multi-tenant environment?
3. How should we handle JWT token revocation for immediate session termination?

### 8.2 Compliance
4. What gaps do you see in our ISO 27001 control mapping?
5. Is our Crypto-Shredding approach sufficient for GDPR Article 17 compliance?
6. Should we consider TISAX certification for automotive customers?

### 8.3 Testing
7. What's the recommended penetration testing scope for a multi-tenant SaaS application?
8. How should we test cross-tenant isolation vulnerabilities specifically?
9. What DAST tools/configurations would you recommend for our API?

### 8.4 Operations
10. What MDR service level would you recommend for a B2B SaaS handling confidential data?
11. Should we implement a WAF? If so, which rules/configurations?
12. What's the recommended secrets management approach (Vault vs. cloud KMS)?

---

## 9. Proposed Engagement Model

### 9.1 Phase 1: Assessment (Immediate)
- Security architecture review
- Threat model validation
- ISO 27001 gap analysis
- Pentest scope definition

### 9.2 Phase 2: Testing (Pre-MVP)
- Web application penetration test
- API security assessment
- Multi-tenant isolation verification
- Vulnerability remediation support

### 9.3 Phase 3: Hardening (MVP Launch)
- Infrastructure hardening recommendations
- PAM implementation for VMware credentials
- CI/CD security gate optimization
- Incident response playbook development

### 9.4 Phase 4: Operations (Post-Launch)
- MDR service evaluation
- SIEM integration
- Ongoing vulnerability management
- Annual penetration testing

---

## 10. Reference Documents

| Document | Description | Location |
|----------|-------------|----------|
| Security Architecture | Detailed security design | `docs/security-architecture.md` |
| System Architecture | Technical architecture, ADRs | `docs/architecture.md` |
| Product Requirements | FRs/NFRs including security | `docs/prd.md` |
| DevOps Strategy | CI/CD, monitoring, DR | `docs/devops-strategy.md` |
| Product Brief | Business context | `docs/product-brief-dvmm-2025-11-24.md` |

---

## Appendix A: Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Kotlin | 2.2 |
| Framework | Spring Boot | 3.5 |
| Database | PostgreSQL | 16 |
| Identity Provider | Keycloak | 26+ |
| Container Runtime | Docker / Kubernetes | Latest |
| CI/CD | GitHub Actions | - |
| Monitoring | Grafana + Prometheus + Loki | - |
| SAST | SonarQube, Detekt | - |
| Dependency Scanning | OWASP Dependency-Check | - |
| Container Scanning | Trivy | - |

## Appendix B: NFR Security Requirements Summary

| NFR ID | Requirement | Target |
|--------|-------------|--------|
| NFR-SEC-1 | TLS encryption | 100% traffic |
| NFR-SEC-2 | OIDC authentication | Required |
| NFR-SEC-3 | Tenant isolation (RLS) | Database-enforced |
| NFR-SEC-4 | Session timeout | 30 min inactive |
| NFR-SEC-5 | Password policy | Keycloak-managed |
| NFR-SEC-6 | API rate limiting | 100 req/min/user |
| NFR-SEC-7 | Input validation | All endpoints |
| NFR-SEC-8 | SQL injection prevention | Parameterized queries |
| NFR-SEC-9 | XSS prevention | CSP headers |
| NFR-SEC-10 | CSRF protection | Token-based |
| NFR-SEC-11 | Secrets management | Vault/env injection |
| NFR-SEC-12 | Dependency scanning | Zero critical CVEs |
| NFR-SEC-13 | Penetration testing | Annual |

---

*Document prepared for Strong IT SOC team security partnership evaluation.*
*Contact: [Project Lead Contact Information]*
