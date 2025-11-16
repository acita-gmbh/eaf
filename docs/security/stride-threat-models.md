# STRIDE Threat Models

**Enterprise Application Framework (EAF) v1.0**
**Last Updated:** 2025-11-16
**Methodology:** Microsoft STRIDE (Spoofing, Tampering, Repudiation, Information Disclosure, Denial of Service, Elevation of Privilege)

---

## Executive Summary

This document provides comprehensive threat models for EAF's critical architectural components using the STRIDE methodology. Two primary models are documented:

1. **CQRS/Event Sourcing Architecture** - Axon Framework-based event-driven system
2. **Multi-Tenant Isolation** - 3-layer tenant boundary enforcement

**Key Findings:**
- **High-Risk Threats:** 6 identified (all mitigated)
- **Medium-Risk Threats:** 12 identified (10 mitigated, 2 require additional controls)
- **Low-Risk Threats:** 8 identified (6 mitigated, 2 accepted)

---

## Table of Contents

1. [STRIDE Methodology Overview](#stride-methodology-overview)
2. [Threat Model 1: CQRS/Event Sourcing](#threat-model-1-cqrsevent-sourcing)
3. [Threat Model 2: Multi-Tenant Isolation](#threat-model-2-multi-tenant-isolation)
4. [Cross-Cutting Threats](#cross-cutting-threats)
5. [Residual Risks & Recommendations](#residual-risks--recommendations)

---

## STRIDE Methodology Overview

### Threat Categories

| Category | Description | Example Threats |
|----------|-------------|-----------------|
| **S**poofing | Impersonating users, services, or data sources | Fake JWT, session hijacking, man-in-the-middle |
| **T**ampering | Unauthorized modification of data or code | Event store corruption, command injection, message manipulation |
| **R**epudiation | Denying actions without ability to prove otherwise | Missing audit logs, unsigned events, log tampering |
| **I**nformation Disclosure | Exposing sensitive data to unauthorized parties | SQL injection, log leakage, tenant data crossover |
| **D**enial of Service | Degrading or denying service availability | Resource exhaustion, event processor stalling, infinite loops |
| **E**levation of Privilege | Gaining unauthorized access to resources | Broken access control, privilege escalation, tenant crossover |

### Risk Scoring

| Severity | Impact | Likelihood | Examples |
|----------|--------|------------|----------|
| **HIGH** | Critical business impact | Likely without controls | Unauthenticated event publishing, tenant data leakage |
| **MEDIUM** | Moderate impact | Possible with effort | Event replay attacks, resource exhaustion |
| **LOW** | Minor impact or unlikely | Rare or theoretical | Timing attacks, log flooding |

---

## Threat Model 1: CQRS/Event Sourcing

**Architecture:** Axon Framework 4.12.1 with PostgreSQL event store
**Components:** Command handlers, Event store, Event processors, Query models (jOOQ)

### Data Flow Diagram

```
[Client] → [REST API] → [Command Gateway] → [Aggregate (Command Handler)]
                                                     ↓
                                              [Event Store] ← [Axon Server / Embedded]
                                                     ↓
                                          [Event Processors (Async)]
                                                     ↓
                                              [Query Models (jOOQ)]
                                                     ↓
                                          [REST API (Query Endpoints)]
                                                     ↓
                                                [Client]
```

### Trust Boundaries

1. **External → API Gateway:** Client to REST API (HTTPS)
2. **API → Command Gateway:** REST to Axon (Spring Security)
3. **Command Handler → Event Store:** Aggregate to PostgreSQL (internal trust)
4. **Event Store → Event Processors:** Async messaging (internal trust)
5. **Event Processors → Query Models:** Projection updates (internal trust)

---

### S - Spoofing Threats

| Threat ID | Description | Severity | Attack Vector | Mitigation | Status |
|-----------|-------------|----------|---------------|------------|--------|
| **CQRS-S01** | Unauthenticated command submission | 🔴 HIGH | Bypass JWT validation, submit commands without token | 10-layer JWT validation (Layer 1-10), Spring Security | ✅ MITIGATED |
| **CQRS-S02** | Fake event injection into event store | 🔴 HIGH | Direct PostgreSQL access, inject malicious events | Database access controls, application-level writes only | ✅ MITIGATED |
| **CQRS-S03** | Impersonate another tenant in commands | 🔴 HIGH | Forge `tenant_id` claim in command payload | Layer 2 tenant validation (command handler), JWT claim validation | ✅ MITIGATED |
| **CQRS-S04** | Event replay from compromised backup | 🟡 MEDIUM | Restore old event store backup, replay stale events | Event sequence numbers, snapshot validation | ✅ MITIGATED |

**Controls:**
- ✅ **JWT Validation:** 10-layer validation (Stories 4.3, OWASP A02)
- ✅ **Tenant Context:** TenantContext validation in command handlers (Story 4.2)
- ✅ **Database ACLs:** PostgreSQL role-based access (Story 3.1)
- ✅ **Sequence Validation:** Axon Framework automatic sequence checking

---

### T - Tampering Threats

| Threat ID | Description | Severity | Attack Vector | Mitigation | Status |
|-----------|-------------|----------|---------------|------------|--------|
| **CQRS-T01** | Modify events after persistence | 🔴 HIGH | Direct database UPDATE on `domain_event_entry` table | Event store immutability (INSERT-only), PostgreSQL RLS | ✅ MITIGATED |
| **CQRS-T02** | Alter command payload in transit | 🟡 MEDIUM | MITM attack, modify JSON payload | HTTPS/TLS 1.3, HSTS preload (OWASP A02) | ✅ MITIGATED |
| **CQRS-T03** | Corrupt event metadata (tenant_id, timestamp) | 🟡 MEDIUM | Database manipulation, change event metadata | Application-controlled metadata, DB constraints | ✅ MITIGATED |
| **CQRS-T04** | Tamper with projection tables (query models) | 🟡 MEDIUM | Direct UPDATE on jOOQ query tables | PostgreSQL RLS on projections, application-only writes | ✅ MITIGATED |
| **CQRS-T05** | Modify snapshots to alter aggregate state | 🟡 MEDIUM | Corrupt snapshot table, force invalid state | Snapshot validation on load, fallback to events | ✅ MITIGATED |

**Controls:**
- ✅ **Immutable Event Store:** Axon Framework INSERT-only design
- ✅ **TLS Enforcement:** HTTPS mandatory (production), HSTS headers
- ✅ **PostgreSQL RLS:** Row-level security on all tables (Story 4.4)
- ✅ **Snapshot Validation:** Axon validates snapshots against event stream

**Residual Risk:**
- 🟡 **Database Administrator Tampering:** DBA with direct PostgreSQL access can modify events
  - **Mitigation Plan:** Database audit logging (pgAudit), immutable log storage (Epic 11, Story 11.10)

---

### R - Repudiation Threats

| Threat ID | Description | Severity | Attack Vector | Mitigation | Status |
|-----------|-------------|----------|---------------|------------|--------|
| **CQRS-R01** | User denies issuing command | 🟡 MEDIUM | No proof of command origin | JWT `sub` claim in command metadata, audit logs | ✅ MITIGATED |
| **CQRS-R02** | Attacker deletes audit logs | 🟡 MEDIUM | Access to log files, tamper with evidence | Centralized logging, immutable log storage | 🟡 PARTIAL |
| **CQRS-R03** | Event processor failure not logged | 🟢 LOW | Silent failure, no dead letter queue entry | Dead Letter Queue (OWASP A10), structured logging | ✅ MITIGATED |
| **CQRS-R04** | Aggregate state change without evidence | 🟢 LOW | Event store corruption loses events | Event Sourcing guarantees all changes are events | ✅ MITIGATED |

**Controls:**
- ✅ **Audit Logging:** trace_id, tenant_id, user_id auto-injected (Story 5.2)
- ✅ **Event Sourcing:** Complete audit trail by design
- ✅ **Dead Letter Queue:** Failures recorded in DLQ (OWASP A10)
- 🟡 **Log Immutability:** Partial (file-based, needs WORM storage)

**Gap:**
- 🟡 **GAP-11 (ASVS V16.1.2):** Log tamper-proofing required for L2 compliance
  - **Recommendation:** Epic 11, Story 11.10 - Forward logs to AWS CloudWatch or Splunk with WORM

---

### I - Information Disclosure Threats

| Threat ID | Description | Severity | Attack Vector | Mitigation | Status |
|-----------|-------------|----------|---------------|------------|--------|
| **CQRS-I01** | Read another tenant's events | 🔴 HIGH | Query event store without tenant filter | PostgreSQL RLS (Story 4.4), tenant_id in all queries | ✅ MITIGATED |
| **CQRS-I02** | Expose PII in event payloads | 🟡 MEDIUM | Log events with sensitive data (email, SSN) | PII masking in logs (Story 5.3), encryption at rest | 🟡 PARTIAL |
| **CQRS-I03** | Information leakage in error messages | 🟡 MEDIUM | Stack traces, SQL errors expose schema | Generic error messages (CWE-209), ProblemDetail abstraction | ✅ MITIGATED |
| **CQRS-I04** | Query model leaks cross-tenant data | 🔴 HIGH | jOOQ projection missing tenant filter | PostgreSQL RLS on query tables, TenantContext validation | ✅ MITIGATED |
| **CQRS-I05** | Event metadata exposes user identity | 🟢 LOW | Event headers contain `sub` claim (user ID) | User ID is non-sensitive (opaque UUID), GDPR-compliant | ✅ ACCEPTED |

**Controls:**
- ✅ **PostgreSQL RLS:** 3-layer tenant isolation (Story 4.4)
- ✅ **PII Masking:** Logs redact email, phone, SSN, credit cards (Story 5.3)
- ✅ **Generic Errors:** No schema/stack trace exposure (Decision #4)
- 🟡 **Encryption at Rest:** GAP-06 (Epic 11, Story 11.5)

**Gap:**
- 🟡 **GAP-06 (ASVS V11.1.2):** Database encryption at rest not documented
  - **Recommendation:** Epic 11, Story 11.5 - PostgreSQL TDE or filesystem encryption

---

### D - Denial of Service Threats

| Threat ID | Description | Severity | Attack Vector | Mitigation | Status |
|-----------|-------------|----------|---------------|------------|--------|
| **CQRS-D01** | Event store disk exhaustion | 🟡 MEDIUM | Publish high-volume events, fill disk | Event archival, snapshot strategy (100 events), monitoring | 🟡 PARTIAL |
| **CQRS-D02** | Command flooding (rate limiting bypass) | 🟡 MEDIUM | Submit 1000s of commands per second | Resilience4j RateLimiter (planned), Bulkhead | 🔴 GAP |
| **CQRS-D03** | Event processor stalls (poison message) | 🟡 MEDIUM | Send malformed event, block processor | Dead Letter Queue (OWASP A10), circuit breaker | ✅ MITIGATED |
| **CQRS-D04** | Snapshot generation overload | 🟢 LOW | Trigger snapshot creation repeatedly | Snapshot threshold (100 events), async generation | ✅ MITIGATED |
| **CQRS-D05** | Long-running query locks projections | 🟡 MEDIUM | Execute slow jOOQ query, block updates | Connection timeout, query timeout, readonly replica | 🟡 PARTIAL |

**Controls:**
- ✅ **Dead Letter Queue:** Poison message isolation (OWASP A10)
- ✅ **Circuit Breaker:** ResilientOperationExecutor (OWASP A10)
- ✅ **Snapshot Strategy:** Every 100 events (configurable)
- 🟡 **Event Archival:** Partial (no automated retention, GAP-01)
- 🔴 **Rate Limiting:** GAP-05 (Epic 11, Story 11.4)

**Gaps:**
- 🔴 **GAP-05 (ASVS V2.2.2):** API rate limiting not implemented
  - **Recommendation:** Epic 11, Story 11.4 - Resilience4j RateLimiter per tenant
- 🟡 **GAP-01 (GDPR Art. 5):** Data retention policy not documented
  - **Recommendation:** Epic 11, Story 11.1 - Event archival + snapshot deletion

---

### E - Elevation of Privilege Threats

| Threat ID | Description | Severity | Attack Vector | Mitigation | Status |
|-----------|-------------|----------|---------------|------------|--------|
| **CQRS-E01** | Bypass tenant isolation in commands | 🔴 HIGH | Craft command with different `tenant_id` than JWT | Layer 2 validation (command handler), require match | ✅ MITIGATED |
| **CQRS-E02** | Escalate from USER to ADMIN role | 🟡 MEDIUM | Forge JWT roles claim, bypass authorization | JWT signature validation (RS256), role whitelist | ✅ MITIGATED |
| **CQRS-E03** | Replay command with elevated privileges | 🟡 MEDIUM | Capture admin command, replay as different user | Idempotency checks, aggregate version validation | ✅ MITIGATED |
| **CQRS-E04** | Modify event processor to corrupt projections | 🟡 MEDIUM | Code injection into event handler, alter query models | Code review, static analysis (Detekt, Konsist), immutable deployments | ✅ MITIGATED |

**Controls:**
- ✅ **Tenant Validation:** Layer 2 enforcement (Story 4.2)
- ✅ **JWT Signature:** RS256 validation, algorithm enforcement (Layer 2, 3)
- ✅ **Role Validation:** Layer 8 JWT validation (role whitelist)
- ✅ **Idempotency:** Axon Framework automatic duplicate detection
- ✅ **Code Quality:** Constitutional TDD, Konsist architecture verification

---

## Threat Model 2: Multi-Tenant Isolation

**Architecture:** 3-layer defense (Filter → Service → Database)
**Threat:** Tenant A accessing Tenant B's data

### Data Flow Diagram

```
[Client (Tenant A)] → [TenantContextFilter] → [REST Controller]
                            ↓ (JWT tenant_id)
                     [TenantContext (ThreadLocal)]
                            ↓
                     [Command Handler (Layer 2 Validation)]
                            ↓
                     [PostgreSQL RLS (Layer 3 Enforcement)]
                            ↓
                     [Query Results (Tenant A only)]
```

### Trust Boundaries

1. **External → Filter:** Client to TenantContextFilter (HTTPS + JWT)
2. **Filter → Service:** TenantContext propagation (ThreadLocal)
3. **Service → Database:** PostgreSQL RLS enforcement (SQL)

---

### S - Spoofing Threats

| Threat ID | Description | Severity | Attack Vector | Mitigation | Status |
|-----------|-------------|----------|---------------|------------|--------|
| **MT-S01** | Forge `tenant_id` claim in JWT | 🔴 HIGH | Modify JWT payload, sign with fake key | JWT signature validation (RS256), Keycloak-issued only | ✅ MITIGATED |
| **MT-S02** | Impersonate tenant in command payload | 🔴 HIGH | Send command with `tenant_id=tenant-b` while authenticated as tenant-a | Layer 2 validation (require match), generic error | ✅ MITIGATED |
| **MT-S03** | Bypass TenantContext via direct database access | 🔴 HIGH | SQL injection, bypass filter | jOOQ type-safe queries (Story 3.1), PostgreSQL RLS | ✅ MITIGATED |

**Controls:**
- ✅ **JWT Validation:** 10-layer validation (Layer 4: claim schema)
- ✅ **Command Validation:** `require(command.tenantId == TenantContext().getCurrentTenantId())`
- ✅ **PostgreSQL RLS:** Database-level enforcement (Story 4.4)

---

### T - Tampering Threats

| Threat ID | Description | Severity | Attack Vector | Mitigation | Status |
|-----------|-------------|----------|---------------|------------|--------|
| **MT-T01** | Modify TenantContext after validation | 🔴 HIGH | ThreadLocal manipulation, change tenant mid-request | Stack-based design, automatic cleanup (finally block) | ✅ MITIGATED |
| **MT-T02** | ALTER PostgreSQL RLS policies | 🔴 HIGH | Database admin disables RLS, access all tenants | Database ACLs, policy enforcement verification | 🟡 PARTIAL |
| **MT-T03** | Inject tenant_id into SQL query | 🟡 MEDIUM | SQL injection, bypass parameterized queries | jOOQ type-safe queries, no raw SQL | ✅ MITIGATED |

**Controls:**
- ✅ **ThreadLocal Cleanup:** TenantContextFilter ensures cleanup
- ✅ **jOOQ Type Safety:** No SQL injection vectors (Story 3.1)
- 🟡 **RLS Policy Integrity:** Requires database audit logging

**Residual Risk:**
- 🟡 **Database Administrator Privilege:** DBA can disable RLS policies
  - **Mitigation Plan:** pgAudit logging, policy verification tests (Story 4.4 test suite)

---

### R - Repudiation Threats

| Threat ID | Description | Severity | Attack Vector | Mitigation | Status |
|-----------|-------------|----------|---------------|------------|--------|
| **MT-R01** | Tenant denies accessing another tenant's data | 🟡 MEDIUM | No proof of tenant crossover attempt | Audit logs (trace_id, tenant_id, requested_tenant_id) | ✅ MITIGATED |
| **MT-R02** | Attacker deletes tenant isolation violation logs | 🟡 MEDIUM | Access to log files, remove evidence | Centralized logging, immutable storage | 🟡 PARTIAL |

**Controls:**
- ✅ **Audit Logging:** Auto-context injection (Story 5.2)
- 🟡 **Log Immutability:** GAP-11 (Epic 11, Story 11.10)

---

### I - Information Disclosure Threats

| Threat ID | Description | Severity | Attack Vector | Mitigation | Status |
|-----------|-------------|----------|---------------|------------|--------|
| **MT-I01** | Read tenant B's data via query model | 🔴 HIGH | jOOQ query missing `WHERE tenant_id = ?` clause | PostgreSQL RLS (Layer 3), TenantContext validation | ✅ MITIGATED |
| **MT-I02** | Error message reveals tenant existence | 🟡 MEDIUM | "Tenant 'tenant-b' not found" vs "Access denied" | Generic error messages (CWE-209): "Access denied" | ✅ MITIGATED |
| **MT-I03** | Log tenant B's data in tenant A's context | 🟡 MEDIUM | Incorrect TenantContext during async processing | Axon tenant propagation interceptor (Story 4.5) | ✅ MITIGATED |
| **MT-I04** | Timing attack reveals tenant existence | 🟢 LOW | Measure response time, infer tenant data existence | Constant-time tenant validation | 🟢 ACCEPTED |

**Controls:**
- ✅ **PostgreSQL RLS:** 3-layer enforcement (Story 4.4)
- ✅ **Generic Errors:** CWE-209 compliance (architecture Decision #4)
- ✅ **Async Context:** TenantValidationInterceptor (Story 4.5)
- 🟢 **Timing Attacks:** Low risk, accepted (no high-value secrets)

---

### D - Denial of Service Threats

| Threat ID | Description | Severity | Attack Vector | Mitigation | Status |
|-----------|-------------|----------|---------------|------------|--------|
| **MT-D01** | Exhaust resources of one tenant affects others | 🟡 MEDIUM | Tenant A creates 1M events, degrades tenant B queries | Per-tenant quotas (planned), bulkhead pattern | 🔴 GAP |
| **MT-D02** | ThreadLocal memory leak (TenantContext not cleared) | 🟡 MEDIUM | Filter exception, TenantContext remains on thread | finally block cleanup in TenantContextFilter | ✅ MITIGATED |
| **MT-D03** | PostgreSQL RLS performance degradation | 🟡 MEDIUM | Complex RLS policies slow down queries | BRIN indexes, partitioning (Story 3.2), query optimization | ✅ MITIGATED |

**Controls:**
- ✅ **ThreadLocal Cleanup:** Guaranteed in finally block
- ✅ **Database Optimization:** BRIN indexes, partitioning (Story 3.2)
- 🔴 **Tenant Quotas:** GAP (Epic 11, Story 4.9 - Per-Tenant Quotas)

**Gap:**
- 🔴 **Per-Tenant Resource Quotas:** Not implemented (Story 4.9 exists but deferred)
  - **Recommendation:** Epic 11, Story 11.12 - Implement per-tenant rate limits and storage quotas

---

### E - Elevation of Privilege Threats

| Threat ID | Description | Severity | Attack Vector | Mitigation | Status |
|-----------|-------------|----------|---------------|------------|--------|
| **MT-E01** | Escalate from tenant-a to tenant-b access | 🔴 HIGH | Bypass Layer 2 validation, access cross-tenant data | 3-layer defense (filter, service, database RLS) | ✅ MITIGATED |
| **MT-E02** | Exploit async event processor (missing context) | 🔴 HIGH | Event processor runs without TenantContext, accesses all | Axon TenantValidationInterceptor (Story 4.5) | ✅ MITIGATED |
| **MT-E03** | Privilege escalation via ROLE_ADMIN in one tenant | 🟡 MEDIUM | ADMIN in tenant-a grants ADMIN in tenant-b | Tenant-scoped roles (Keycloak realm configuration) | ✅ MITIGATED |

**Controls:**
- ✅ **3-Layer Isolation:** Filter (Layer 1), Service (Layer 2), Database (Layer 3)
- ✅ **Async Context Propagation:** TenantValidationInterceptor (Story 4.5)
- ✅ **Tenant-Scoped Roles:** Keycloak tenant_id in JWT, role validation

---

## Cross-Cutting Threats

### Infrastructure Threats

| Threat ID | Description | Severity | Attack Vector | Mitigation | Status |
|-----------|-------------|----------|---------------|------------|--------|
| **INFRA-01** | Compromise Keycloak admin console | 🔴 HIGH | Weak admin password, access identity provider | Strong password policy, MFA for admins, network isolation | 🟡 EXTERNAL |
| **INFRA-02** | PostgreSQL database server compromise | 🔴 HIGH | Unpatched database, remote code execution | Regular patching, network segmentation, firewall rules | 🟡 EXTERNAL |
| **INFRA-03** | Redis revocation store unavailability | 🟡 MEDIUM | Redis crash, token revocation fails | Fail-closed configuration (reject requests), Redis HA | 🟡 PARTIAL |
| **INFRA-04** | Docker Compose secret exposure | 🟡 MEDIUM | Secrets in docker-compose.yml, committed to Git | Environment variables, Docker secrets, .gitignore | ✅ MITIGATED |

**Controls:**
- 🟡 **Infrastructure Hardening:** CIS Benchmarks (PostgreSQL, Docker) recommended
- ✅ **Secret Management:** Environment variables, not hardcoded
- 🟡 **Redis HA:** Fail-closed mode (Story 4.7), but no clustering (dev mode)

### Supply Chain Threats

| Threat ID | Description | Severity | Attack Vector | Mitigation | Status |
|-----------|-------------|----------|---------------|------------|--------|
| **SUPPLY-01** | Vulnerable dependency (Log4Shell-style) | 🔴 HIGH | Transitive dependency with RCE vulnerability | OWASP Dependency-Check, Dependabot, SBOM | ✅ MITIGATED |
| **SUPPLY-02** | Malicious dependency injection | 🟡 MEDIUM | Compromised Maven repository, trojan package | Version Catalog lock, checksum verification | 🟡 PARTIAL |
| **SUPPLY-03** | Compromised build pipeline (CI/CD) | 🔴 HIGH | Attacker modifies GitHub Actions, injects backdoor | Branch protection, signed commits, SLSA compliance | 🟡 PARTIAL |

**Controls:**
- ✅ **Dependency Scanning:** OWASP Dependency-Check, NVD integration (OWASP A06)
- ✅ **SBOM Generation:** CycloneDX format (OWASP A06)
- ✅ **Dependabot:** Automated dependency updates
- 🟡 **SLSA Compliance:** Partial (Epic 11 for full SLSA Level 2)

---

## Residual Risks & Recommendations

### High-Priority Residual Risks

| Risk ID | Description | Current Control | Residual Risk | Recommendation |
|---------|-------------|-----------------|---------------|----------------|
| **RR-01** | Database administrator tampering | PostgreSQL ACLs | 🟡 MEDIUM | Epic 11, Story 11.10 - pgAudit + WORM logging |
| **RR-02** | Command/API rate limiting missing | None | 🔴 HIGH | Epic 11, Story 11.4 - Resilience4j RateLimiter |
| **RR-03** | Frontend XSS vulnerabilities | Not implemented | 🔴 HIGH | Epic 9 - React security hardening |
| **RR-04** | GDPR right to erasure non-compliance | None | 🔴 HIGH | Epic 11, Story 11.2 - Crypto-shredding |
| **RR-05** | Database encryption at rest | Unknown | 🟡 MEDIUM | Epic 11, Story 11.5 - PostgreSQL TDE |
| **RR-06** | Per-tenant resource quotas | None | 🟡 MEDIUM | Epic 11, Story 11.12 - Tenant quotas |

### Medium-Priority Residual Risks

| Risk ID | Description | Current Control | Residual Risk | Recommendation |
|---------|-------------|-----------------|---------------|----------------|
| **RR-07** | Event store disk exhaustion | Snapshot strategy | 🟡 MEDIUM | Epic 11, Story 11.1 - Event archival |
| **RR-08** | User-level token revocation | Single JWT revocation | 🟡 LOW | Epic 11, Story 11.6 - User-level revocation |
| **RR-09** | Secrets management (no vault) | Environment variables | 🟡 MEDIUM | Epic 11, Story 11.8 - HashiCorp Vault |
| **RR-10** | Cryptographic key rotation | Keycloak default | 🟡 LOW | Epic 11, Story 11.9 - Key rotation policy |

### Accepted Risks (Low Priority)

| Risk ID | Description | Justification |
|---------|-------------|---------------|
| **AR-01** | Timing attacks on tenant validation | Constant-time validation not critical for tenant IDs (UUIDs, non-sensitive) |
| **AR-02** | User ID exposure in event metadata | User IDs are opaque UUIDs, non-sensitive, required for audit |
| **AR-03** | Certificate pinning not implemented | Mobile apps not in scope (web-only MVP) |
| **AR-04** | Mutual TLS (mTLS) not implemented | L2 requirement, specialized use case (deferred to Epic 12) |

---

## Threat Modeling Process

### Regular Review Cadence

1. **Epic Completion:** Review threat model for new features
2. **Quarterly:** Comprehensive STRIDE review of all components
3. **Incident-Driven:** Update after security incidents or CVEs
4. **Architecture Changes:** Review whenever trust boundaries change

### Threat Model Ownership

| Component | Owner | Review Frequency |
|-----------|-------|------------------|
| CQRS/Event Sourcing | Tech Lead | Per Epic |
| Multi-Tenant Isolation | Security Engineer | Monthly |
| API Gateway | DevOps Lead | Quarterly |
| Supply Chain | DevSecOps | Continuous (Dependabot) |

---

## References

- **Microsoft STRIDE:** https://learn.microsoft.com/en-us/azure/security/develop/threat-modeling-tool-threats
- **OWASP Threat Modeling:** https://owasp.org/www-community/Threat_Modeling
- **EAF Architecture:** docs/architecture.md (Decision #2: Multi-Tenancy, Decision #3: JWT Validation)
- **ASVS 5.0 Compliance:** docs/security/asvs-5.0-compliance-mapping.md
- **OWASP Top 10:2025:** docs/security/owasp-top-10-2025-compliance.md

---

**Last Updated:** 2025-11-16
**Next Review:** After Epic 11 completion (GDPR + ASVS L2 compliance)
**Threat Model Version:** 1.0
