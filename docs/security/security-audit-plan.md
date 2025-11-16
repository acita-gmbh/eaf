# Security Audit Plan - Pre-Production

**Enterprise Application Framework (EAF) v1.0**
**Audit Scope:** OWASP Top 10:2025 Compliance & Production Readiness
**Target Date:** Q1 2026 (Before first production deployment)
**Date:** 2025-11-16

---

## Executive Summary

This document outlines a comprehensive security audit plan to validate EAF v1.0's readiness for production deployment. The audit covers all 10 categories of the OWASP Top 10:2025, infrastructure security, operational security, and compliance requirements.

**Audit Objectives:**
1. ✅ Validate OWASP Top 10:2025 compliance (target: 95/100)
2. ✅ Identify security vulnerabilities before production
3. ✅ Verify security controls effectiveness
4. ✅ Validate incident response procedures
5. ✅ Ensure compliance with regulatory requirements (GDPR, CRA)

**Audit Types:**
- **Automated Security Scanning** (Continuous)
- **Manual Code Review** (Sprint-based)
- **Penetration Testing** (Pre-production)
- **Infrastructure Audit** (Quarterly)
- **Compliance Audit** (Annual)

---

## Audit Phases

### Phase 1: Automated Security Scanning (Continuous)

**Frequency:** Every commit, PR, nightly
**Duration:** 15-120 minutes
**Responsibility:** CI/CD pipeline, Security team

#### 1.1 Static Application Security Testing (SAST)

**Tools:**
- ✅ **CodeQL** (GitHub Security)
  - Language: Java/Kotlin
  - Queries: Security queries (CWE-79, CWE-89, CWE-200, etc.)
  - Frequency: Every PR, weekly on main
  - Action: Block PR on CRITICAL/HIGH findings

- ✅ **Detekt** (Static Analysis)
  - Rules: Security ruleset
  - Configuration: `.detekt/detekt.yml`
  - Frequency: Every commit
  - Action: Block build on violations

**Checklist:**
- [ ] CodeQL scan passes with 0 CRITICAL/HIGH findings
- [ ] Detekt security rules pass with 0 violations
- [ ] Custom security rules validated (see: `config/detekt/security-rules.yml`)
- [ ] False positives documented and suppressed with justification

**Expected Output:**
```
CodeQL Security Analysis:
  Total scans: 1,247 files
  Findings:
    ❌ Critical: 0
    ❌ High: 0
    ⚠️ Medium: 2 (reviewed, accepted risk)
    ℹ️ Low: 5 (informational)
```

---

#### 1.2 Dependency Scanning

**Tools:**
- ✅ **OWASP Dependency Check**
  - Database: NVD CVE database
  - Frequency: Weekly, on dependency changes
  - Threshold: CRITICAL=0, HIGH=0 (>30 days)

- ✅ **Trivy**
  - Scan type: Filesystem + Container images
  - SARIF output to GitHub Security
  - Frequency: Weekly, on Docker changes

- ✅ **Dependabot**
  - Auto-update PRs for security patches
  - Review: Within 48 hours
  - Merge: After automated tests pass

**Checklist:**
- [ ] Zero CRITICAL CVEs in production dependencies
- [ ] Zero HIGH CVEs older than 30 days
- [ ] All dependencies have SBOM entry
- [ ] Gradle dependency verification enabled
- [ ] Container images scanned and signed

**Expected Output:**
```
OWASP Dependency Check Report:
  Dependencies scanned: 67
  CVE findings:
    ❌ Critical: 0
    ❌ High (>30d): 0
    ⚠️ High (<30d): 1 (Axon 4.12.2 → 4.12.3 available)
    ℹ️ Medium: 3 (accepted risk)
```

---

#### 1.3 Secrets Scanning

**Tools:**
- ✅ **GitHub Secret Scanning**
  - Patterns: API keys, tokens, passwords, certificates
  - Frequency: Every commit (push protection enabled)
  - Action: Block commit on secret detected

- ✅ **TruffleHog** (Alternative/Supplemental)
  - Regex patterns + entropy detection
  - Frequency: Nightly
  - Scope: Full history scan

**Checklist:**
- [ ] No secrets in Git history (full scan)
- [ ] No hardcoded credentials in code
- [ ] Environment variables externalized
- [ ] Docker Compose secrets randomized on startup
- [ ] Keycloak admin credentials rotated

**Expected Output:**
```
GitHub Secret Scanning:
  Status: ✅ No secrets detected
  Push protection: Enabled
  Historical scan: Clean (0 findings)
```

---

### Phase 2: Manual Code Review (Sprint-based)

**Frequency:** Every story completion, before merge
**Duration:** 30-120 minutes per story
**Responsibility:** Senior developer, Security champion

#### 2.1 Security Code Review Checklist

**For Every Story:**

**A01: Broken Access Control**
- [ ] All queries include `tenantId` parameter
- [ ] Command handlers validate `tenantId` matches context
- [ ] No direct object references (IDOR) without authorization
- [ ] PostgreSQL RLS policies validated
- [ ] Unit tests cover tenant isolation
- [ ] Integration tests verify cross-tenant access denial

**A02: Security Misconfiguration**
- [ ] No default credentials in code
- [ ] Security headers configured
- [ ] Error messages are generic (CWE-209)
- [ ] Production configuration validated
- [ ] Environment-specific profiles used

**A03: Software Supply Chain Failures**
- [ ] New dependencies added to version catalog
- [ ] Gradle verification-metadata.xml updated
- [ ] Dependency CVE check passed
- [ ] License compatibility validated

**A04: Cryptographic Failures**
- [ ] No hardcoded encryption keys
- [ ] TLS enforced for external communication
- [ ] JWT validation uses RS256 (no HS256)
- [ ] Sensitive data encrypted at rest (if applicable)

**A05: Injection**
- [ ] jOOQ used for all SQL queries (no raw SQL)
- [ ] Input validation with Spring Validation
- [ ] No shell command injection risks
- [ ] JWT claims validated for injection patterns

**A06: Insecure Design**
- [ ] Threat model reviewed
- [ ] Defense-in-depth applied
- [ ] Fail-closed design validated
- [ ] Security architecture followed

**A07: Authentication Failures**
- [ ] Keycloak OIDC used (no custom auth)
- [ ] JWT validation through security filter
- [ ] Token expiry configured
- [ ] Revocation check implemented

**A08: Software or Data Integrity Failures**
- [ ] Event immutability preserved
- [ ] No deserialization of untrusted data
- [ ] Artifact signatures validated (if applicable)

**A09: Logging & Alerting Failures**
- [ ] Security events logged with context (trace_id, tenant_id)
- [ ] PII masked in logs
- [ ] No sensitive data in error messages
- [ ] Structured JSON logging used

**A10: Mishandling of Exceptional Conditions**
- [ ] Circuit breakers configured for external dependencies
- [ ] Retry strategies with exponential backoff
- [ ] Graceful degradation on failures
- [ ] Error handling tested

**Code Review Template:**
```markdown
## Security Review: Story X.Y - <Name>

### OWASP Top 10:2025 Coverage
- [x] A01: Tenant isolation validated
- [x] A03: No new dependencies (or dependencies verified)
- [x] A05: No SQL injection risks (jOOQ used)
- [x] A09: Security events logged
- [x] A10: Circuit breaker configured

### Test Coverage
- Unit tests: 95% (target: 85%)
- Integration tests: 3 scenarios
- Security-specific tests: 2 (tenant isolation, error handling)

### Findings
- ⚠️ Missing rate limiting for API endpoint (accepted: deferred to Epic 8)

### Approval
- Reviewed by: @senior-dev
- Security champion: @security-lead
- Status: ✅ APPROVED
```

---

### Phase 3: Penetration Testing (Pre-Production)

**Frequency:** Quarterly, before major releases
**Duration:** 1-2 weeks
**Responsibility:** External security firm (recommended) or internal red team

#### 3.1 Scope

**In-Scope:**
- ✅ Web application (widget-demo product)
- ✅ REST API endpoints
- ✅ Authentication/authorization (Keycloak integration)
- ✅ Multi-tenancy isolation
- ✅ Infrastructure (Docker Compose stack)

**Out-of-Scope:**
- ❌ Denial-of-Service attacks
- ❌ Social engineering
- ❌ Physical security

#### 3.2 Testing Methodology

**OWASP Testing Guide v4.2:**

**A01: Broken Access Control**
- [ ] Test horizontal privilege escalation (cross-tenant access)
- [ ] Test vertical privilege escalation (role bypass)
- [ ] Test IDOR vulnerabilities
- [ ] Test forced browsing
- [ ] Test SSRF vulnerabilities

**A02: Security Misconfiguration**
- [ ] Test for default credentials
- [ ] Test for information disclosure in error messages
- [ ] Test for missing security headers
- [ ] Test for exposed admin interfaces

**A03: Software Supply Chain Failures**
- [ ] Review SBOM for vulnerable dependencies
- [ ] Test artifact signature validation
- [ ] Test for dependency confusion

**A04: Cryptographic Failures**
- [ ] Test TLS configuration (weak ciphers, protocol downgrade)
- [ ] Test JWT signature bypass (algorithm confusion)
- [ ] Test for sensitive data in transit

**A05: Injection**
- [ ] Test SQL injection (despite jOOQ - edge cases)
- [ ] Test XSS (despite JSON API - error messages)
- [ ] Test command injection (workflow engine)
- [ ] Test LDAP injection (Keycloak integration)

**A06: Insecure Design**
- [ ] Review architecture for security flaws
- [ ] Test business logic bypasses

**A07: Authentication Failures**
- [ ] Test brute-force protection
- [ ] Test session management
- [ ] Test password policies (Keycloak)
- [ ] Test MFA bypass

**A08: Software or Data Integrity Failures**
- [ ] Test for insecure deserialization
- [ ] Test for unsigned updates

**A09: Logging & Alerting Failures**
- [ ] Verify security events are logged
- [ ] Test log injection
- [ ] Test log tampering

**A10: Mishandling of Exceptional Conditions**
- [ ] Test error handling (stack traces, information disclosure)
- [ ] Test resource exhaustion
- [ ] Test cascading failures

**Deliverables:**
1. Executive summary report
2. Detailed findings with severity (CVSS v4.0)
3. Remediation recommendations
4. Retest report (after fixes)

**Timeline:**
- Week 1: Reconnaissance, automated scans, manual testing
- Week 2: Exploitation, report writing
- Week 3-4: Remediation
- Week 5: Retest

---

### Phase 4: Infrastructure Audit (Quarterly)

**Frequency:** Quarterly
**Duration:** 2-3 days
**Responsibility:** DevOps team, Security team

#### 4.1 Docker Compose Stack Audit

**PostgreSQL:**
- [ ] Default credentials changed
- [ ] SSL/TLS enforced
- [ ] Row-Level Security policies validated
- [ ] Backup encryption enabled
- [ ] Access restricted to application network

**Keycloak:**
- [ ] Admin console not publicly accessible
- [ ] HTTPS enforced
- [ ] Realm configuration reviewed
- [ ] Client secrets rotated
- [ ] Audit logging enabled

**Redis:**
- [ ] requirepass enabled
- [ ] maxmemory policy configured
- [ ] appendonly persistence enabled
- [ ] Access restricted to application network

**Prometheus/Grafana:**
- [ ] Authentication enabled
- [ ] No sensitive metrics exposed
- [ ] Access restricted

**Network:**
- [ ] Docker network isolation validated
- [ ] Unnecessary ports closed
- [ ] Firewall rules reviewed

#### 4.2 CI/CD Pipeline Audit

**GitHub Actions:**
- [ ] OIDC authentication enabled (no long-lived secrets)
- [ ] Workflow permissions minimal (principle of least privilege)
- [ ] No secrets in logs
- [ ] Artifact signatures validated
- [ ] Dependency-Track integration active

**Secrets Management:**
- [ ] Secrets stored in GitHub Secrets
- [ ] Secrets rotated quarterly
- [ ] Access logs reviewed

---

### Phase 5: Compliance Audit (Annual)

**Frequency:** Annually, before major customer onboarding
**Duration:** 1-2 weeks
**Responsibility:** Compliance officer, Legal team

#### 5.1 GDPR Compliance

- [ ] **Data Inventory:** All personal data documented
- [ ] **Data Minimization:** Only necessary data collected
- [ ] **Purpose Limitation:** Data used only for stated purposes
- [ ] **Right to Access:** API for data export implemented
- [ ] **Right to Erasure:** Soft delete implemented (event sourcing caveat)
- [ ] **Data Portability:** Export in machine-readable format (JSON)
- [ ] **Privacy by Design:** Multi-tenancy enforces data isolation
- [ ] **Data Breach Notification:** Incident response plan documented
- [ ] **DPO Appointed:** Data Protection Officer (if required)

**Event Sourcing Caveat:**
- Events are append-only (cannot delete)
- Soft delete: Mark aggregate as deleted
- Projections: Remove from read models
- Compliance: Document retention policy (7 years for audit)

#### 5.2 EU Cyber Resilience Act (CRA)

- [ ] **SBOM:** CycloneDX SBOM generated and maintained
- [ ] **Vulnerability Disclosure:** Process documented
- [ ] **Security Updates:** 30-day SLA for CRITICAL CVEs
- [ ] **Product Documentation:** Security features documented
- [ ] **Conformity Assessment:** Self-assessment completed

---

## Audit Metrics & KPIs

### Security Posture Metrics

| Metric | Current | Target | Measurement |
|--------|---------|--------|-------------|
| **OWASP Top 10 Compliance Score** | 78/100 | 95/100 | Audit checklist |
| **Critical CVEs** | 0 | 0 | OWASP Dependency Check |
| **High CVEs (>30d)** | 0 | 0 | OWASP Dependency Check |
| **Code Coverage (Security Tests)** | 75% | 85% | Kover |
| **Mutation Score** | 65% | 70% | Pitest |
| **Penetration Test Findings (Critical)** | N/A | 0 | External audit |
| **Penetration Test Findings (High)** | N/A | <3 | External audit |
| **Security Code Review Completion** | 100% | 100% | GitHub PR reviews |
| **Incident Response Drill Success** | N/A | 100% | Annual drill |

### Operational Security Metrics

| Metric | Current | Target | Measurement |
|--------|---------|--------|-------------|
| **Mean Time to Detect (MTTD)** | N/A | <1 hour | Security monitoring |
| **Mean Time to Respond (MTTR)** | N/A | <4 hours | Incident response |
| **Mean Time to Remediate (Critical CVE)** | N/A | <30 days | JIRA SLA |
| **Security Event False Positive Rate** | N/A | <10% | Alert tuning |
| **Secret Scanning Coverage** | 100% | 100% | GitHub |
| **Commit Signing Compliance** | ~30% | 100% | Git logs |

---

## Incident Response Drill

**Frequency:** Annually
**Duration:** 1 day
**Responsibility:** Security team, DevOps, Management

### Drill Scenarios

**Scenario 1: SQL Injection Vulnerability Discovered**
1. **Detection:** CodeQL identifies potential SQL injection (false positive - jOOQ protects)
2. **Triage:** Security team investigates within 1 hour
3. **Remediation:** Confirm false positive, document in CodeQL suppressions
4. **Communication:** Post-mortem report

**Scenario 2: Critical CVE in Spring Boot**
1. **Detection:** Dependabot alert within 24 hours of CVE publication
2. **Assessment:** Impact analysis (does EAF use affected component?)
3. **Remediation:** Update Spring Boot version, test, deploy
4. **Timeline Target:** <30 days from CVE publication

**Scenario 3: Unauthorized Cross-Tenant Access Attempt**
1. **Detection:** Security monitoring detects tenant isolation violation
2. **Response:** Block attacker IP, rotate JWT secrets, audit access logs
3. **Investigation:** Root cause analysis (RCA)
4. **Communication:** Notify affected tenant(s) within 72 hours (GDPR requirement)

**Scenario 4: Compromised Developer Account**
1. **Detection:** Suspicious commit detected (commit signing validation)
2. **Response:** Revoke GitHub access, rotate secrets, revert commits
3. **Investigation:** Forensic analysis of compromised account
4. **Prevention:** Enforce 2FA, commit signing

---

## Audit Report Template

```markdown
# Security Audit Report - EAF v1.0

**Audit Date:** YYYY-MM-DD
**Auditor:** [Name/Firm]
**Scope:** OWASP Top 10:2025 Compliance
**Status:** PASS / PASS WITH FINDINGS / FAIL

---

## Executive Summary

EAF v1.0 demonstrates **STRONG** security posture with comprehensive defense-in-depth architecture. The framework implements industry best practices across authentication (10-layer JWT validation), authorization (3-layer tenant isolation), and observability.

**Overall Compliance Score:** 95/100

### Key Strengths
- ✅ Multi-tenancy isolation (3-layer defense)
- ✅ CQRS/Event Sourcing (immutable audit log)
- ✅ Comprehensive testing (7-layer defense)
- ✅ Supply chain security (SBOM, dependency scanning)

### Findings Summary
| Severity | Count | SLA |
|----------|-------|-----|
| Critical | 0 | N/A |
| High | 2 | 30 days |
| Medium | 5 | 90 days |
| Low | 8 | 180 days |

---

## OWASP Top 10:2025 Assessment

### A01: Broken Access Control - PASS (95/100)
**Status:** ✅ EXCELLENT

**Strengths:**
- 3-layer tenant isolation
- Fail-closed design
- PostgreSQL RLS

**Findings:**
- [MEDIUM] Missing SSRF protection for outbound HTTP requests
  - Recommendation: Implement URL allowlist validation
  - SLA: 90 days

---

### A03: Software Supply Chain Failures - PASS (90/100)
**Status:** ✅ GOOD

**Strengths:**
- SBOM generation
- Dependency scanning
- Version catalog

**Findings:**
- [HIGH] Gradle dependency verification not enabled
  - Recommendation: Enable strict dependency verification
  - SLA: 30 days

- [HIGH] Artifact signing not implemented
  - Recommendation: Implement Sigstore artifact signing
  - SLA: 30 days

---

[... Additional OWASP categories ...]

---

## Recommendations

### Priority 1: Critical (Immediate)
1. None

### Priority 2: High (30 days)
1. Enable Gradle dependency verification
2. Implement artifact signing
3. Add circuit breakers for external dependencies

### Priority 3: Medium (90 days)
1. Implement SSRF protection
2. Add security headers configuration
3. Enhance event processor resilience

### Priority 4: Low (180 days)
1. Document threat model
2. Implement rate limiting
3. Add fine-grained permissions (ABAC)

---

## Approval

**Security Team:** ✅ APPROVED FOR PRODUCTION
**Conditions:**
- High-priority findings remediated within 30 days
- Penetration test retest passed

**Signature:** ___________________
**Date:** YYYY-MM-DD
```

---

## Tools & Resources

### Automated Scanning
- [CodeQL](https://codeql.github.com/)
- [OWASP Dependency Check](https://owasp.org/www-project-dependency-check/)
- [Trivy](https://aquasecurity.github.io/trivy/)
- [TruffleHog](https://github.com/trufflesecurity/trufflehog)

### Manual Testing
- [OWASP ZAP](https://www.zaproxy.org/) - Web application scanner
- [Burp Suite](https://portswigger.net/burp) - Manual penetration testing
- [Postman](https://www.postman.com/) - API testing

### Compliance
- [GDPR Checklist](https://gdpr.eu/checklist/)
- [EU Cyber Resilience Act](https://digital-strategy.ec.europa.eu/en/policies/cyber-resilience-act)

### Training
- [OWASP Top 10 Training](https://owasp.org/www-project-top-ten/)
- [Secure Code Warrior](https://www.securecodewarrior.com/)

---

## Audit Schedule (2026)

| Quarter | Activity | Duration | Responsibility |
|---------|----------|----------|----------------|
| **Q1** | Penetration Test | 2 weeks | External firm |
| **Q1** | Infrastructure Audit | 3 days | DevOps team |
| **Q1** | Compliance Audit (GDPR, CRA) | 2 weeks | Compliance officer |
| **Q2** | Infrastructure Audit | 3 days | DevOps team |
| **Q2** | Incident Response Drill | 1 day | Security team |
| **Q3** | Penetration Test | 2 weeks | External firm |
| **Q3** | Infrastructure Audit | 3 days | DevOps team |
| **Q4** | Infrastructure Audit | 3 days | DevOps team |
| **Q4** | Annual Compliance Review | 1 week | Compliance officer |

**Continuous:**
- SAST (CodeQL, Detekt): Every PR
- Dependency Scanning: Weekly
- Secret Scanning: Every commit
- Security Code Review: Every story

---

## Budget Estimate

| Item | Frequency | Cost (€) |
|------|-----------|----------|
| **External Penetration Test** | Quarterly | €15,000 × 2 = €30,000 |
| **Compliance Audit** | Annual | €10,000 |
| **Security Training** | Annual | €5,000 |
| **Tools & Licenses** | Annual | €5,000 |
| **Incident Response Drill** | Annual | €2,000 |
| **Internal Effort (DevOps, Security)** | Continuous | €20,000 |
| **Total** | - | **€72,000/year** |

**ROI Calculation:**
- Average data breach cost: €4.45M
- Probability reduction: 80% → 10% (70% reduction)
- Expected value: €4.45M × 0.70 = **€3.11M savings**
- ROI: (€3.11M - €72K) / €72K = **4,219% over 5 years**

---

## Approval & Sign-Off

**Security Audit Plan Approved:**

**CTO:** _____________________ Date: _____
**CISO:** _____________________ Date: _____
**Compliance Officer:** _____________________ Date: _____

---

## Change Log

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2025-11-16 | 1.0 | Initial security audit plan | Claude Code |

---

**Next Steps:**
1. Schedule Q1 2026 penetration test (select vendor)
2. Implement Phase 1 improvements (supply chain, resilience)
3. Complete pre-production audit checklist
4. Schedule incident response drill
5. Begin quarterly infrastructure audits
