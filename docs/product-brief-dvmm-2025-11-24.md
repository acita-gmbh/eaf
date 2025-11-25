# Product Brief: DVMM (Dynamic Virtual Machine Manager)

**Date:** 2025-11-24
**Author:** Mary (Analyst Agent) + Wall-E
**Status:** Final Draft
**Version:** 2.0

---

## Executive Summary

**DVMM** (Dynamic Virtual Machine Manager) is a **multi-tenant self-service portal** for VMware ESXi and Windows VM provisioning with workflow-based approval automation. It replaces the legacy ZEWSSP system and serves as the pilot project for the Enterprise Application Framework (EAF).

### Market Timing

The market opportunity is **exceptionally strong**:

- **74% of IT leaders** are actively exploring VMware alternatives (Gartner)
- **150-500% price increases** under Broadcom's new VMware licensing
- **SAM (DACH):** €280M - €420M for VM provisioning + self-service portals
- **3-year window** to capture VMware refugees before market stabilizes

### Core Value Proposition

> *"Request a VM. Get approval. VM is provisioned. That's it."*

**The workflow is the product.** Everything else (multi-tenancy, compliance, CQRS) is an enabler.

### Critical Success Factors (from Brainstorming)

| Priority | Insight | Action |
|----------|---------|--------|
| #1 | **Quality Gates Mandatory** | CI/CD with hard gates from Day 1 |
| #2 | **Business-Value First** | Tracer Bullet demo-able in Month 2 |
| #3 | **Avoid Perfectionism** | Start simple, iterate to complex |

---

## Problem Statement

### Customer Problem

**IT teams waste hours on manual VM provisioning:**

- Request via email/ticket → Manual approval → Manual provisioning → Manual notification
- **Result:** 3-5 days for a VM that could be done in minutes
- **No visibility:** Users don't know status, admins juggle multiple systems
- **No governance:** Approvals are informal, audit trails incomplete

### Axians Problem (Legacy ZEWSSP)

The current ZEWSSP system is **blocking business growth**:

| Problem | Impact | Root Cause |
|---------|--------|------------|
| **Single-tenant only** | Cannot serve multiple customers | 2016 architecture |
| **No compliance** | Blocks ISO 27001 certification | DCA framework limitations |
| **25% dev overhead** | 1/4 of time wasted on framework | Tech debt from 2016 |
| **6+ month onboarding** | Cannot scale team | No docs, no tests |

**Root Causes (Five Whys Analysis):**
1. **Perfectionism** - Previous EAF attempt failed by trying to be "perfect from day 1"
2. **Budget + Wrong Priorities** - 9 years of ignored tech debt

### Urgency: VMware Disruption Window

Broadcom's VMware acquisition has created a **once-in-a-decade market opportunity**:

- **98%** of VMware customers considering alternatives
- **72-core licensing minimums** pushing SMBs to seek alternatives
- Enterprises need **on-premise solutions** that aren't locked to VMware pricing

**If we don't move now:**
- Window closes as competitors capture VMware refugees
- DCA tech debt compounds further
- ISO 27001 certification remains blocked

---

## Proposed Solution

### Product: DVMM Self-Service Portal

**Multi-tenant web application** enabling:

1. **Users:** Request VMs through intuitive self-service UI
2. **Admins:** Approve/reject requests with full visibility
3. **System:** Automatically provisions VMs on VMware ESXi
4. **Audit:** Complete trail of all actions for compliance

### Core Workflow (The Product)

```
User Request → Approval Workflow → VM Provisioned → Notification
     ↓              ↓                    ↓               ↓
  Form UI     Docket Policy        VMware API       Email/Portal
```

### Differentiators

| Feature | DVMM | ZEWSSP | ServiceNow | VMware vRealize |
|---------|------|--------|------------|-----------------|
| **Multi-tenant** | Yes (RLS) | No | Yes | Limited |
| **Pricing** | Mid-market | N/A | $165-500/user | $$$$ (Broadcom) |
| **On-premise** | Yes | Yes | Cloud-only | Yes |
| **ISO 27001 ready** | Yes | No | Yes | Yes |
| **Workflow engine** | Dockets | Legacy | ServiceNow | vRO |
| **Onboarding** | 1 week target | 6+ months | Weeks | Weeks |

### Innovation from Brainstorming

Three innovations identified through Assumption Reversal:

1. **Docket-based Conditional Auto-Approval**
   - Policy engine: `if vm.ram <= 16GB AND cost <= budget: auto_approve()`
   - Reduces admin overhead while maintaining governance

2. **Statistical VM Suggestions**
   - "Data Scientists typically choose 32GB/8CPU (80% of cases)"
   - Smart defaults based on role, project type, history

3. **Granular Self-Service Policies**
   - Admin configures which actions need approval vs. self-service
   - Balance governance + user autonomy

---

## Target Users

### Primary: IT Administrators (Approvers)

**Profile:**
- 5-30 person IT teams in German Mittelstand
- Responsible for VM infrastructure
- Currently drowning in manual provisioning requests

**Pain Points:**
- Email/ticket chaos for VM requests
- No visibility into request status
- Manual approval = bottleneck
- Audit trail gaps

**Goals:**
- Single dashboard for all requests
- One-click approval workflow
- Automatic compliance documentation

### Secondary: End Users (Requesters)

**Profile:**
- Developers, data scientists, project managers
- Need VMs for projects, testing, demos

**Pain Points:**
- Days waiting for VM
- No visibility into status
- Have to chase IT team

**Goals:**
- Self-service VM creation (within policies)
- Real-time status updates
- Minutes, not days

### Tertiary: Cloud Service Providers (CSPs)

**Profile:**
- Regional MSPs, hosting providers
- Serve multiple customers

**Pain Points:**
- Need multi-tenant solution
- VMware licensing costs squeezing margins
- Customer isolation requirements

**Goals:**
- White-label capability
- Per-tenant isolation
- Volume licensing model

---

## Goals and Success Metrics

### Business Objectives

| Objective | Target | Measure |
|-----------|--------|---------|
| **Developer Overhead** | <5% (from 25%) | Time tracking |
| **Developer Onboarding** | <1 month (from 6+) | Time to first PR |
| **ISO 27001** | Audit-ready | Security team review |
| **First Customer** | Within 6 months | Signed contract |

### Key Performance Indicators (KPIs)

| KPI | Baseline | Target | Timeline |
|-----|----------|--------|----------|
| **Test Coverage** | 0% | 80%+ | Continuous |
| **E2E Tests Pass** | N/A | 100% | Continuous |
| **VM Request → Provisioned** | 3-5 days | <30 minutes | MVP+3mo |
| **Onboarding Time** | 6 months | 1 week | MVP |
| **dNPS** | N/A | +50 | MVP+6mo |

### Early Warning Signs (Pre-Mortem)

**Month 3 Red Flags:**
- No end-to-end workflow demo-able
- Test coverage below 80%
- "We need to finish X before we can show anything..."

**Month 6 Red Flags:**
- Customers haven't seen DVMM
- Team turnover starting
- E2E tests becoming "flaky"

---

## MVP Scope

### Tracer Bullet (Month 2)

**Absolute minimum for first demo:**

1. User creates VM request (simple form)
2. Admin sees request, clicks "Approve"
3. System creates VM (Docker container OK, not real VMware)
4. User gets notification

**NOT in Tracer Bullet:**
- JWT authentication (simple login OK)
- Multi-tenancy (single tenant OK)
- Complex RBAC (admin/user OK)
- VMware integration (Docker OK)

### MVP Features (Must Have)

| Feature | Priority | Description |
|---------|----------|-------------|
| **VM Request Form** | P0 | Simple form: name, size, project |
| **Approval Workflow** | P0 | Request → Approve/Reject → Notify |
| **Admin Dashboard** | P0 | View pending requests, approve/reject |
| **User Status View** | P0 | See my requests and status |
| **VMware Integration** | P1 | Actually provision VMs on ESXi |
| **Basic Auth** | P1 | Keycloak OIDC integration |
| **Tenant Isolation** | P1 | Row-Level Security for data |
| **Audit Trail** | P1 | Event Sourcing provides complete history |

### Out of Scope (MVP)

- Full Dockets complexity (conditional approvals)
- Statistical VM suggestions
- Hyper-V/Proxmox support
- Self-service policies configuration
- Advanced reporting/dashboards
- Mobile app

### MVP Success Criteria

1. **Working Workflow**: Request → Approve → VM Created → Notification
2. **Multi-Tenant**: 2+ tenants with data isolation
3. **Audit-Ready**: Security team sign-off on architecture
4. **1-Week Onboarding**: New dev productive in 5 days

---

## Technical Approach

### "Good Enough to Learn" Strategy (Anti-Perfectionism)

| Feature | v1 (MVP) | v2 (Post-MVP) |
|---------|----------|---------------|
| **Authentication** | Keycloak basic | 10-layer JWT validation |
| **Multi-Tenancy** | Simple tenant_id | PostgreSQL RLS |
| **Workflow** | Simple approve/reject | Full Dockets engine |
| **CQRS** | Command/Query separation | Full Event Sourcing |
| **VMware** | Basic vSphere API | Advanced provisioning |

**Principle:** Start simple, iterate to complex. Plan for refactoring.

### Technology Stack

**Core:**
- Kotlin 2.x + Spring Boot 3.x
- Axon Framework (CQRS/Event Sourcing)
- PostgreSQL 16 (Event Store + Projections)
- Keycloak (Authentication)
- React + React-Admin (UI)

**Quality:**
- Testcontainers (real dependencies)
- 80% coverage minimum
- Mutation testing (Pitest)
- CI/CD quality gates

**Observability:**
- Structured JSON logging
- OpenTelemetry tracing
- Prometheus metrics

### CI/CD Quality Gates (Non-Negotiable)

```yaml
gates:
  test_coverage: ">= 80%"    # Pipeline FAILS below
  all_tests_pass: true       # Pipeline FAILS on 1 failure
  security_scan: "no_critical"
  mutation_score: ">= 70%"   # Optional but recommended
```

**"No Broken Windows" Policy:** Every test must pass, always.

---

## Go-to-Market Strategy

### Market Positioning

> *"DVMM: Enterprise VM provisioning with self-service workflows.
> Multi-tenant. Compliance-ready. Made in Germany."*

**Position:** Enterprise capability at mid-market price

### Target Segments (Priority Order)

1. **VMware Refugees** (Q1-Q2)
   - Enterprises fleeing Broadcom pricing
   - "VMware Cost Calculator" campaign
   - Migration assessment service

2. **CSP Partners** (Q2-Q3)
   - 10x revenue multiplier
   - White-label capability
   - Targets: PlusServer, noris network

3. **Regulated Industries** (Q3+)
   - Banking, healthcare, public sector
   - ISO 27001 certification
   - On-premise deployment option

### Pricing Model (Initial)

| Tier | Target | Price |
|------|--------|-------|
| **Starter** | SMB (<100 VMs) | €500/mo |
| **Professional** | Enterprise (100-500 VMs) | €2,000/mo |
| **Enterprise** | Large (500+ VMs) | Custom |
| **CSP** | Multi-tenant | Per-tenant licensing |

### Revenue Projections

| Year | Customers | ARR |
|------|-----------|-----|
| Y1 | 5-10 | €100K-€250K |
| Y2 | 20-40 | €500K-€1M |
| Y3 | 50-100 | €2M-€5M |

---

## Risk Assessment

### Technical Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| **Perfectionism trap** | Medium | High | Weekly scope-check meetings |
| **PostgreSQL scale** | Medium | Medium | Swappable adapter design |
| **CQRS complexity** | Medium | High | Scaffolding CLI, golden paths |
| **Multi-tenancy leaks** | Low | Critical | RLS + integration tests |

### Business Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| **No customer value** | Medium | High | Tracer Bullet in Month 2 |
| **Team burnout** | Medium | High | 1-week onboarding target |
| **VMware recovers** | Low | Medium | Multi-hypervisor roadmap |

### Lessons from Old EAF Failure

**What NOT to do:**
- Don't implement JWT + Multi-tenancy + RBAC before workflow works
- Don't fear refactoring - plan for it
- Don't wait 6 months for first demo
- Don't optimize before measuring

---

## Timeline Overview

> **Current Status:** See [sprint-status.yaml](sprint-artifacts/sprint-status.yaml) for live sprint progress and AI-adjusted timelines.

### Phase 1: Foundation (Month 1-2)

- Week 1-2: CI/CD pipeline, quality gates, dev environment
- Week 3-4: Walking skeleton (API + UI shell)
- Week 5-6: Basic auth (Keycloak)
- Week 7-8: **Tracer Bullet demo** (Request → Approve → VM)

### Phase 2: MVP (Month 3-4)

- Month 3: VMware integration, tenant isolation
- Month 4: Audit trail, observability, security review

### Phase 3: Customer Pilot (Month 5-6)

- Month 5: First customer pilot (internal or friendly external)
- Month 6: ISO 27001 audit preparation, CSP partner discussions

### Key Milestones

| Milestone | Target | Success Criteria |
|-----------|--------|------------------|
| **Quality Gates Live** | Week 1 | CI/CD blocks on failures |
| **Tracer Bullet** | Week 8 | End-to-end workflow works |
| **MVP Complete** | Month 4 | Multi-tenant, VMware, audit-ready |
| **First Customer** | Month 6 | Signed contract |
| **ISO 27001 Ready** | Month 8 | Security team sign-off |

---

## Action Items

### Immediate (Week 1)

1. [ ] Set up CI/CD with quality gates
2. [ ] Create `docs/tracer-bullet-milestone.md`
3. [ ] Create `docs/evolution-strategy.md` (v1 → v2 paths)
4. [ ] Define MVP feature freeze
5. [ ] Schedule weekly scope-check meetings

### Short-term (Month 1)

1. [ ] Walking skeleton (API + UI)
2. [ ] Keycloak integration
3. [ ] First E2E test (Request → Approve → Provision)
4. [ ] Developer Portal (Docusaurus) setup

### Dependencies

- Axians Keycloak instance or provisioned Testcontainer
- VMware ESXi access for integration (can defer to Month 3)
- Security team availability for reviews

---

## Appendix A: Market Research Summary

### TAM/SAM/SOM (DACH Region)

| Metric | Value |
|--------|-------|
| **TAM** | €2.8B - €3.2B |
| **SAM** | €280M - €420M |
| **SOM (3yr)** | €2.8M - €4.2M |
| **SOM (5yr)** | €5.6M - €8.4M |

### Key Market Data

- Global Cloud Spending 2025: $723B (Gartner)
- VMware Virtualization Market: $94.8B
- 74% IT leaders exploring VMware alternatives
- 149,000 unfilled IT positions in Germany

### Sources

See `docs/research-market-2025-11-24.md` for complete market research with 30 citations.

---

## Appendix B: Brainstorming Insights

### Root Causes (Five Whys)

1. **EAF Failure:** Perfectionism → over-engineering → fear of refactoring
2. **DCA Problems:** Budget + wrong priorities → 9 years tech debt

### Pre-Mortem Scenarios

1. Customers see no value → **Prevention:** Business-value first
2. Team leaves/overwhelmed → **Prevention:** Developer experience first
3. Bugs destroy trust → **Prevention:** Quality gates mandatory

### Innovations (Assumption Reversal)

1. Docket-based conditional auto-approval
2. Statistical VM suggestions
3. Granular self-service policies

See `docs/brainstorming-session-results-2025-11-24.md` for complete session.

---

## Appendix C: EAF Framework Reference

DVMM is built on the Enterprise Application Framework (EAF). For complete framework specifications, see:

- `old_eaf/docs/product-brief-EAF-2025-10-30.md` - Framework Product Brief
- `old_eaf/docs/architecture.md` - Technical Architecture
- `old_eaf/docs/prd.md` - Framework PRD

**Key EAF Components Used by DVMM:**

- Hexagonal Architecture (Spring Modulith)
- CQRS/Event Sourcing (Axon Framework)
- Multi-Tenancy (PostgreSQL RLS)
- Authentication (Keycloak OIDC)
- Workflow Engine (Flowable BPMN → "Dockets")

---

_This Product Brief integrates Market Research, Brainstorming Insights, and lessons from legacy systems (old_eaf, DCA, ZEWSSP) to define a focused, achievable product vision._

_Next Steps: Architecture → Epics & Stories → Implementation_
