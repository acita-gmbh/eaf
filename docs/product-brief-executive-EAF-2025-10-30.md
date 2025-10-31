# Product Brief: Enterprise Application Framework (EAF) v1.0
## Executive Summary Version

**Date:** 2025-10-30
**Author:** Wall-E
**Status:** Executive Review
**Version:** 1.0

---

## At a Glance

**What:** Modern "batteries-included" development platform replacing legacy DCA framework (2016)

**Why:** DCA framework consumes 25% developer effort, requires 6+ months onboarding, blocks revenue due to security/architecture gaps

**Outcome:** <5% framework overhead, <1 month productivity, audit-ready security (ASVS L1/L2), enable ZEWSSP/DPCM migration

**Timeline:** 11-13 weeks for Epic 1-9 completion, ZEWSSP migration follows immediately

**Team:** Michael Walloschke (Staff Engineer, 100% during development) + Claude Code AI assistance, Majlinda joins post-Epic (100%, Kotlin/Spring training required)

**Investment:** ~3 months development effort (1 FTE) + infrastructure/tooling costs

**ROI:** Break-even within 12-18 months, accelerates with ZEWSSP contracts (~€500K-750K ARR potential)

---

## The Problem

### Business Impact

The legacy DCA framework (2016) has become Axians' primary strategic bottleneck:

1. **Cost Inefficiency:** 25% of developer time consumed by framework overhead and workarounds - inflates all feature development costs
2. **Velocity Constraint:** 6+ month onboarding time severely limits team scalability and product delivery speed
3. **Revenue Blockage:** ZEWSSP cannot acquire new customers requiring security certifications (ISO 27001, NIS2, OWASP ASVS)

### Technical Root Causes

- No multi-tenancy support (unsuitable for modern SaaS)
- Critical security gaps (no JWT validation, impossible to audit)
- Zero observability (insufficient logging/metrics)
- Inflexible monolithic architecture (testing difficult, refactoring risky)
- Brittle custom persistence layer (high maintenance burden)

### Urgency

Without replacement: increasing security vulnerabilities, growing opportunity cost vs. competitors, compounding technical debt, developer turnover risk.

---

## The Solution

### EAF v1.0: Modern Enterprise Platform

**Architecture:** Production-validated stack combining Hexagonal Architecture, CQRS/Event Sourcing, Spring Modulith
- Programmatically enforced boundaries isolate business logic
- Solves DCA's "inflexible" and "untestable" nature

**Technology Foundation:**
- Kotlin/Spring Boot for type-safe, maintainable code
- Axon Framework for scalable CQRS/ES
- PostgreSQL as swappable event store adapter
- Keycloak OIDC for enterprise authentication
- Flowable BPMN for workflow orchestration

### Key Differentiators

**1. One-Command Onboarding**
Single command (`./scripts/init-dev.sh`) launches complete local stack (PostgreSQL, Keycloak, Redis, Prometheus, Grafana) with seed data

**2. Scaffolding CLI**
Code generation tool creates modules, aggregates, APIs, UI components - enforces patterns, eliminates boilerplate

**3. Constitutional Test-Driven Development**
Integration-First (40-50% integration tests), "Nullable Pattern" (60%+ faster tests), Testcontainers for real dependencies - comprehensive safety net DCA lacks

**4. Secure by Default**
- 10-layer JWT validation (format → signature → algorithm → claims → time → issuer → revocation → role → user → injection)
- 3-layer tenant isolation (request filter → service validation → PostgreSQL Row-Level Security)

**5. Enterprise Workflow Engine**
Flowable BPMN replaces legacy "Dockets" with industry-standard orchestration, compensating transactions, error handling

**6. Comprehensive Observability**
Structured JSON logging, Prometheus metrics, OpenTelemetry tracing - all configured by default with automatic tenant/trace correlation

### Strategic Technical Decision

PostgreSQL as event store via Axon's native integration prioritizes mature, proven technology over custom solutions. Implemented as **swappable Hexagonal adapter** preserving low-effort migration path to streaming solutions (NATS, Axon Server) when performance triggers met.

---

## Target Users & Success Metrics

### Primary: Axians Developers (Node.js/React background)
**Pain:** 6+ month onboarding, 25% time on framework overhead, no tests/documentation, fear of changes
**Goal:** <1 month productivity, focus on business logic, comprehensive safety net
**Success:** Build/deploy domain aggregate in <3 days using only docs and CLI

### Secondary: Product Managers, Security Team, End Customers
**PM Goal:** Predictable timelines, rapid prototyping, <2 week MVP creation
**Security Goal:** Audit-ready framework (ISO 27001, NIS2, ASVS L1/L2), automated compliance artifacts
**Customer Goal:** Stable products, modern UX, regular features, enterprise security

### Key Performance Indicators

1. **Developer Time-to-Value:** >6 months → <3 months (first production deployment)
2. **Legacy Retirement:** 100% DPCM + ZEWSSP migration from DCA to EAF
3. **New Market Enablement:** ≥1 new enterprise customer requiring ISO 27001/NIS2
4. **Developer Satisfaction (dNPS):** +50 score indicating strong advocacy
5. **Test Suite Performance:** <15min comprehensive, <3min fast feedback
6. **Framework Overhead:** <5% developer time on framework vs. business logic

---

## MVP Scope & Timeline

### Core Features (11-13 weeks)

**Epic 1-2 (1-2 weeks):** Foundation + Walking Skeleton
One-command setup, CI/CD pipeline, constitutional quality gates, first CQRS/ES vertical slice

**Epic 3-4 (3 weeks):** Authentication + Multi-Tenancy
10-layer JWT validation, Keycloak integration, 3-layer tenant isolation with async context propagation

**Epic 5-6 (3 weeks):** Observability + Flowable
Structured logging, Prometheus metrics, OpenTelemetry tracing, BPMN engine integration, Axon-Flowable bridges

**Epic 7 (1 week):** Scaffolding CLI v1
Code generation for modules, aggregates, APIs, React-Admin components

**Epic 8 (1 week):** Code Quality & Architectural Alignment
Systematic resolution of architectural deviations and technical debt

**Epic 9 (2 weeks):** Reference Application
Multi-tenant Widget Management System demonstrating all framework capabilities

### Success Criteria

1. **Production Viability:** Reference app (Epic 9) built, deployed, meets performance KPIs
2. **Onboarding Validation:** Majlinda builds domain aggregate in <3 days using only docs/CLI
3. **Security Validation:** Formal security review confirms ASVS 100% L1 / 50% L2 compliance

### Explicitly Out of Scope

- Full Dockets UI/advanced features (Phase 2)
- Observability dashboards (metrics collection only)
- AI-powered developer assistant
- Automated DCA migration tooling
- React-Admin advanced features

---

## Financial Impact & Strategic Alignment

### ROI Analysis

**Cost Reduction (~€165K-292K annual):**
- Developer efficiency: 25% → <5% overhead = 0.4 FTE (~€40K, scales to ~€200K at 10-person team)
- Onboarding: 6mo → 1mo saves ~15-20 person-months (~€125K-167K) for Majlinda + 2-3 future hires

**Revenue Enablement (~€500K-750K ARR):**
- ZEWSSP migration unlocks 2-3 enterprise contracts annually requiring security certifications
- DPCM enhancement reduces churn risk (~€200K annual)
- Faster MVP cycles (2 weeks vs. 3-6 months) enable 3-4 new market explorations annually

**Risk Mitigation (~€4.45M potential):**
- Security breach prevention (industry average €4.45M)
- Compliance penalty avoidance (NIS2/GDPR fines up to €10M or 2% revenue)
- Technical debt prevention (exponential cost growth)

**Timeline:**
- **Baseline:** ROI from Epic 1 start
- **Break-even:** 12-18 months through efficiency gains
- **Acceleration:** 4-6 months post-Epic-9 with first ZEWSSP contract
- **Compounding:** Growth as products migrate and team scales

### Strategic Initiatives Enabled

- **Product Modernization:** Foundation for DPCM, ZEWSSP, future products
- **Security Compliance:** Audit-ready status enables enterprise/government contracts
- **International Expansion:** Multi-tenancy + compliance supports global markets
- **Talent Retention:** Modern stack improves recruitment and reduces turnover
- **Innovation Velocity:** 2-week MVPs enable rapid market experimentation

---

## Migration Strategy

### Post-Epic-9: ZEWSSP Migration (Confirmed First Target)

**Preparation Activities:**
1. Gap analysis of ZEWSSP-specific features
2. Detailed migration plan with phasing and rollback procedures
3. Customer communication strategy development
4. Pilot customer selection for initial validation
5. Comprehensive rollback planning

**Timeline:** Commences immediately after Epic 9 completion (~4-6 months to first customer contract)

**Team:** Majlinda (100%, post-training) + Michael (50%, mentorship) + IBM Power/VMware specialists on-demand

---

## Key Risks & Mitigation

**1. PostgreSQL Scalability (Accepted Risk)**
Mitigation: Proactive optimization (snapshotting, partitioning, BRIN indexes), performance KPIs as migration triggers, swappable adapter design

**2. Developer Learning Curve (Validated)**
Mitigation: Scaffolding CLI, comprehensive documentation, structured training, pair programming, realistic 2-3 month ramp-up expectation

**3. Multi-Tenancy Context Propagation (Critical)**
Mitigation: ThreadLocal stack with WeakReference, comprehensive async scenario testing, security team review, metrics/alerting

**4. Flowable/Dockets Migration Complexity**
Mitigation: Comprehensive analysis complete, de-scoped full complexity from MVP, BPMN template for common patterns, manual migration support

---

## Critical Constraints

**Team:** 1-person core team (Michael Walloschke 100%) during Epic development, expanding to 2-person (Michael 50% + Majlinda 100%) post-Epic

**Licensing:** Exclusive FOSS - no commercial licenses approved (PostgreSQL, Keycloak, Flowable, Axon all open source editions)

**Architecture:** ppc64le support mandatory, customer-hosted single-server deployment, Active-Passive HA with <2min failover, RTO 4h/RPO 15min

**Scope:** 100% DCA feature parity required (excluding deferred Dockets complexity) before legacy retirement

---

## Appendix: Technology Stack Summary

**Confirmed (Non-Negotiable):**
Kotlin, JVM 21, Spring Boot 3.x, Axon Framework, PostgreSQL 16.10, Keycloak OIDC, Docker Compose, ktlint, Detekt, Testcontainers, Multi-Arch (amd64/arm64/ppc64le)

**Preferred (Under Evaluation):**
Kotest, Spring Modulith, Flowable BPMN, Arrow, React-Admin, jOOQ, Prometheus/Grafana/Jaeger

**Strategic Principles:**
FOSS-only, swappable adapters, cloud-ready, enterprise-grade, security by default

---

## Decision & Next Steps

**Recommendation:** Proceed with EAF v1.0 development based on this brief.

**Immediate Actions:**
1. Finalize Epic 1-9 planning with Product Manager
2. Confirm Majlinda training timeline and curriculum
3. Initiate Epic 1 (Foundation & Onboarding)

**Post-Epic-9:**
1. ZEWSSP migration preparation and execution
2. Security team formal compliance review
3. DPCM migration planning

---

**Full Documentation:** Complete Product Brief with detailed requirements, risks, and technical specifications available at `/Users/michael/eaf/docs/product-brief-EAF-2025-10-30.md`

_This executive summary provides strategic overview for decision-makers. Refer to complete brief for comprehensive details._
