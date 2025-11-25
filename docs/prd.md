# DVMM - Product Requirements Document

**Author:** Wall-E
**Date:** 2025-11-24
**Version:** 1.1 (Validated)
**Product:** Dynamic Virtual Machine Manager (DVMM)
**Framework:** Enterprise Application Framework (EAF)

---

## Executive Summary

DVMM (Dynamic Virtual Machine Manager) is a **multi-tenant self-service portal** for VMware ESXi and Windows VM provisioning with workflow-based approval automation. It replaces the legacy ZEWSSP system and serves as the pilot project for the Enterprise Application Framework (EAF).

**The Core Value Proposition:**

> *"DVMM frees IT teams from VM provisioning chaos. End users get VMs in minutes instead of days. Admins retain full control through intelligent workflows—without becoming the bottleneck."*

**Dual Perspective - Why Users Love It:**

| Stakeholder | Pain Today | DVMM Solution |
|-------------|------------|---------------|
| **End User** | 3-5 days waiting, no visibility, chasing IT | VM in minutes, real-time status, self-service |
| **IT Admin** | Email chaos, manual approvals, bottleneck | Smart workflows, policy-based auto-approval, full audit |
| **IT Manager** | Compliance gaps, no audit trail, scaling issues | ISO 27001-ready, event-sourced history, multi-tenant |

**Why DVMM Over Alternatives:**
- **vs. ServiceNow:** Enterprise features at mid-market price, true multi-tenancy
- **vs. ManageEngine:** On-premise option, German support, compliance-first
- **vs. VMware vRealize:** No Broadcom lock-in, predictable pricing

**Market Trigger:** Broadcom's VMware acquisition (150-500% price increases) is driving 74% of enterprises to seek alternatives. This creates a 3-year window—but the REASON customers choose DVMM is the value proposition above, not just escaping VMware.

**Strategic Position:** Enterprise capability at mid-market price. Made in Germany. ISO 27001-ready. Local support.

### What Makes This Special

**1. The Workflow IS the Product**
Not a feature list, but a single compelling moment: User requests → Admin approves → VM appears. Everything else enables this.

**2. Perfect Timing - VMware Disruption**
Broadcom's 150-500% price increases are driving enterprises to seek alternatives. DVMM captures this migration wave.

**3. True Multi-Tenancy**
PostgreSQL Row-Level Security (RLS) provides enterprise-grade tenant isolation—not just tenant_id fields, but database-enforced boundaries.

**4. Intelligent Approvals via Dockets**
Policy engine enables conditional auto-approval: `if vm.ram <= 16GB AND cost <= budget: auto_approve()`. Governance without bottlenecks.

**5. Compliance-First Architecture**
ISO 27001-ready from day 1. Event Sourcing provides complete audit trails. CQRS enables regulatory reporting without impacting operations.

**6. Developer Experience Revolution**
1-week onboarding target (vs. 6+ months for legacy DCA). Docusaurus portal, scaffolding CLI, comprehensive tests.

---

## Project Classification

**Technical Type:** SaaS B2B Platform
**Domain:** IT Infrastructure / VM Provisioning
**Product Complexity:** Medium (User sees simple workflow)
**Implementation Complexity:** High (Multi-tenant RLS, Event Sourcing, VMware API)

**Complexity Clarification:**
- *Product Complexity* = What the customer needs to understand → **Medium**
- *Implementation Complexity* = What the dev team builds → **High**

This distinction matters: DVMM appears simple to users while hiding significant technical sophistication.

**Project Type Characteristics (saas_b2b):**
- Multi-tenant architecture with PostgreSQL Row-Level Security
- Role-based access control (RBAC) via Keycloak
- Workflow-driven operations with Dockets policy engine
- Enterprise integrations (VMware vSphere API, Keycloak OIDC)
- Compliance requirements (ISO 27001, complete audit trails via Event Sourcing)

**Domain Characteristics:**
- IT Infrastructure Management
- Virtualization (VMware ESXi focus, Proxmox roadmap)
- Self-Service IT Portals
- Workflow Approval Systems

**Input Documents:**
- Product Brief: `docs/product-brief-dvmm-2025-11-24.md`
- Market Research: `docs/research-market-2025-11-24.md`
- Brainstorming Results: `docs/brainstorming-session-results-2025-11-24.md`

---

## Success Criteria

### What Winning Looks Like

Success for DVMM is NOT generic metrics. It's these specific moments:

**For End Users:**
> "I requested a VM at 10:00. By 10:15, I was logged in and working."

- **Target:** VM request to provisioned < 30 minutes (vs. 3-5 days today)
- **Measure:** Time from request submission to VM accessible

**For IT Admins:**
> "I approve 50 requests per day without it consuming my entire morning."

- **Target:** Average approval time < 2 minutes per request
- **Measure:** Time spent in approval workflow per request

**For IT Managers:**
> "When the auditor asks for VM provisioning history, I generate the report in 30 seconds."

- **Target:** Complete audit trail with one-click export
- **Measure:** Audit report generation time, completeness score

**For Developers (EAF):**
> "I joined the team Monday. By Friday, I shipped my first feature."

- **Target:** New developer productive in < 1 week (vs. 6+ months)
- **Measure:** Time to first merged PR

### Business Success Metrics

| Metric | Baseline | Target | Why It Matters |
|--------|----------|--------|----------------|
| **Developer Overhead** | 25% of time on framework | <5% | More time for business value |
| **Onboarding Time** | 6+ months | <1 week | Team can scale |
| **VM Provisioning (E2E)** | 3-5 days | <30 minutes | User satisfaction |
| **ISO 27001 Status** | Blocked | Audit-ready | Unlock enterprise customers |
| **First Paying Customer** | N/A | Mid-market company with VMware pain | Market validation |

**First Customer Profile:** German mid-market company (500-5000 employees) actively seeking VMware alternatives due to Broadcom pricing. This is our beachhead market.

### Controllable vs. Dependent Metrics

**Controllable (Our Code) - Engineering Targets:**

| Metric | Target | Measurement |
|--------|--------|-------------|
| Request Processing | < 5 seconds | API response time |
| Approval Workflow UI | < 2 minutes | Time in approval screen |
| VMware API Call | < 10 seconds | Outbound API latency |
| Audit Report Generation | < 30 seconds | Export completion time |

**Dependent (Customer Environment) - Expectations:**

| Metric | Typical Range | Dependency |
|--------|---------------|------------|
| VM Boot Time | 2-10 minutes | VMware template, hardware |
| Network Setup | 1-5 minutes | Customer network config |
| Total E2E Time | 5-30 minutes | All factors combined |

*We commit to controllable metrics. Dependent metrics are documented expectations.*

### User Experience Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| **Task Success Rate** | 95% | Users complete VM request without help |
| **Request Abandonment** | <10% | Users who start but don't finish |
| **User NPS** | >+30 | Net Promoter Score after 3 months |
| **Admin NPS** | >+40 | Admin satisfaction with workflow |

### Implementation Quality Gates

| Gate | Requirement | Enforcement |
|------|-------------|-------------|
| **Test Coverage** | ≥80% | CI blocks merge |
| **Mutation Score** | ≥70% | CI blocks merge |
| **E2E Suite Runtime** | <15 minutes | Performance budget |
| **Security Scan** | Zero critical vulnerabilities | CI blocks merge |
| **All Tests Pass** | 100% green | CI blocks merge |

*These gates are non-negotiable. "No Broken Windows" policy.*

### Early Warning Signs (from Pre-Mortem)

**Red Flags to Watch:**
- ❌ No end-to-end workflow demo-able after initial development phase
- ❌ Test coverage below 80%
- ❌ "We need to finish X before we can show anything..."
- ❌ Customers haven't seen DVMM after extended period
- ❌ Team turnover starting
- ❌ E2E tests becoming "flaky"

### Anti-Goals (What We're NOT Optimizing For)

- **NOT:** Maximum features at launch
- **NOT:** Perfect architecture before shipping
- **NOT:** Supporting every hypervisor (VMware first, others later)
- **NOT:** Enterprise pricing from day 1

---

## Product Scope

### MVP - Minimum Viable Product

**Guiding Principle:** The workflow IS the product. MVP = working end-to-end workflow.

**Tracer Bullet (First Demo):**
> User creates request → Admin approves → VM is provisioned → User notified

This must work before anything else. Even with Docker containers instead of real VMware.

**MVP Feature Set:**

| Feature | Priority | Description |
|---------|----------|-------------|
| **VM Request Form** | P0 | Simple form: name, size, project, justification |
| **Approval Workflow** | P0 | Request → Approve/Reject → Notify (basic flow) |
| **Admin Dashboard** | P0 | View pending requests, one-click approve/reject |
| **User Status View** | P0 | "My Requests" with real-time status |
| **Notification System** | P0 | Email + in-app notifications for status changes |
| **Projects** | P1 | Group VMs by project (name, description) |
| **VMware Integration** | P1 | Provision VMs on ESXi via vSphere API |
| **Keycloak Auth** | P1 | OIDC authentication, basic roles (user/admin) |
| **Tenant Isolation** | P1 | PostgreSQL RLS for multi-tenant data separation |
| **Audit Trail** | P1 | Event Sourcing provides complete history |
| **Basic Reporting** | P2 | Export request history as CSV |

**VM Size Transparency:**

Users select from predefined sizes with VISIBLE specifications:

| Size | vCPU | RAM | Disk | Use Case |
|------|------|-----|------|----------|
| **S (Small)** | 2 | 4 GB | 50 GB | Dev/Test, lightweight apps |
| **M (Medium)** | 4 | 8 GB | 100 GB | Standard workloads |
| **L (Large)** | 8 | 16 GB | 200 GB | Database, heavy processing |
| **XL (Extra Large)** | 16 | 32 GB | 500 GB | Data science, analytics |

*Sizes are tenant-configurable in Growth phase. MVP uses these defaults.*

**Tracer Bullet Approach:**
1. **First:** Docker containers simulate VM provisioning (proves workflow)
2. **Then:** Real VMware vSphere API integration (P1)

This de-risks the demo and allows parallel development.

**Explicitly NOT in MVP:**
- ❌ Conditional auto-approval (Dockets complexity)
- ❌ Statistical VM suggestions
- ❌ Proxmox/Hyper-V support
- ❌ Advanced RBAC beyond user/admin
- ❌ White-label/CSP features
- ❌ Mobile app

### Growth Features (Post-MVP)

**Phase 2: Intelligent Workflows**

| Feature | Value | Complexity |
|---------|-------|------------|
| **Docket-based Auto-Approval** | Policy engine: `if vm.ram <= 16GB: auto_approve()` | Medium |
| **Statistical VM Suggestions** | "Data Scientists typically choose 32GB/8CPU" | Low |
| **Granular Self-Service Policies** | Admin configures what needs approval | Medium |
| **Advanced RBAC** | Custom roles, fine-grained permissions | Medium |
| **Chargeback/Showback** | Cost attribution per team/project | Medium |

**Phase 3: Enterprise & Scale**

| Feature | Value | Complexity |
|---------|-------|------------|
| **CSP/White-Label** | Multi-tenant SaaS for partners | High |
| **Advanced Reporting** | Dashboards, trends, forecasting | Medium |
| **ServiceNow Integration** | ITSM ticket sync | Medium |
| **Proxmox Support** | Alternative hypervisor | Medium |
| **API for Automation** | External system integration | Low |

### Vision (Future)

**Long-Term Product Evolution:**

1. **Multi-Hypervisor Platform**
   - VMware ESXi (MVP)
   - Proxmox VE (Growth)
   - Microsoft Hyper-V (Vision)
   - Nutanix AHV (Vision)

2. **Beyond VMs**
   - Kubernetes namespace provisioning
   - Container workload management
   - Cloud resource provisioning (AWS/Azure/GCP)

3. **AI-Enhanced Operations**
   - Predictive capacity planning
   - Anomaly detection in usage patterns
   - Natural language VM requests ("I need a medium dev server for 2 weeks")

4. **Full Dockets Engine**
   - Visual workflow builder
   - Complex approval chains
   - Dynamic UI generation
   - Integration marketplace

**Platform Vision:**
> "From VM provisioning tool to unified infrastructure self-service platform"

---

## SaaS B2B Specific Requirements

### Multi-Tenancy Architecture

**Tenant Model:**
- Each customer organization = one tenant
- Complete data isolation via PostgreSQL Row-Level Security (RLS)
- Tenant context propagated through all layers (API → Service → Database)
- Fail-closed: Missing tenant context = request rejected

**Tenant Hierarchy:**
```
Tenant (Organization)
├── Users (with roles)
├── Projects (grouping for VMs)
├── VMs (provisioned resources)
├── Requests (workflow items)
└── Audit Events (complete history)
```

### Authentication & Authorization

**Authentication (Keycloak OIDC):**
- Single Sign-On via Keycloak
- JWT tokens with tenant_id claim
- Session management across devices
- Password reset, MFA support (Keycloak-managed)

**Authorization (RBAC):**

| Role | Permissions |
|------|-------------|
| **User** | Create requests, view own VMs, view own history |
| **Approver** | User + approve/reject requests in their scope |
| **Admin** | Approver + manage users, configure policies |
| **Super Admin** | Admin + cross-tenant operations (CSP only) |

**MVP Roles:** User, Admin (simplified)
**Growth Roles:** Full RBAC matrix above

### Subscription Model (Future)

| Tier | Target | Included |
|------|--------|----------|
| **Starter** | SMB (<100 VMs) | Basic workflow, 5 users |
| **Professional** | Enterprise (100-500 VMs) | Full features, 50 users |
| **Enterprise** | Large (500+ VMs) | Unlimited, custom SLA |
| **CSP** | Partners | Multi-tenant, white-label |

*Pricing defined in Go-to-Market, not PRD.*

---

## User Experience Principles

### Visual Personality

**The Vibe:** Professional, clean, trustworthy—but not cold.

- **Primary feeling:** "I'm in control, I know what's happening"
- **Secondary feeling:** "This is modern, this company knows what they're doing"
- **Avoid:** Cluttered dashboards, confusing navigation, "enterprise gray"

**Design Language:**
- Clean whitespace, clear hierarchy
- Status colors: Green (approved), Yellow (pending), Red (rejected), Blue (in progress)
- Minimal but informative—every element earns its place

### Key Interaction Patterns

**1. Request Flow (User)**
```
[Select Project] → [Choose Size] → [Add Details] → [Submit] → [Confirmation]
     ↓                  ↓               ↓              ↓            ↓
  Dropdown         Size cards      Name + Why      One click    Status view
                  with specs
```
- **Goal:** Submit a valid request in < 60 seconds
- **Key moment:** Size selection shows exactly what you get (CPU/RAM/Disk)

**2. Approval Flow (Admin)**
```
[Dashboard] → [Request Detail] → [Approve/Reject] → [Done]
     ↓              ↓                   ↓              ↓
 Pending list   Full context      One-click action   Back to list
```
- **Goal:** Process a request in < 30 seconds
- **Key moment:** All info needed for decision visible without scrolling

**3. Status Tracking (User)**
```
[My Requests] → [Request Card] → [Timeline View]
      ↓               ↓                ↓
  Filter/Sort    Quick status     Full history
```
- **Goal:** Answer "where is my VM?" in < 5 seconds
- **Key moment:** Real-time status updates (no page refresh needed)

### Critical User Journeys

**Journey 1: First-Time User Requests a VM**
1. User logs in via Keycloak SSO
2. Sees clean dashboard with "New Request" button prominent
3. Guided form: Project → Size → Details
4. Clear confirmation: "Request #1234 submitted, pending approval"
5. Email notification when status changes

**Journey 2: Admin Morning Approval Routine**
1. Admin logs in, sees badge: "5 pending requests"
2. Dashboard shows requests sorted by oldest first
3. Click request → sees: requester, project, size, justification
4. One-click approve (or reject with reason)
5. Notification sent automatically

**Journey 3: User Checks VM Status**
1. User clicks "My Requests" in nav
2. Sees all requests with color-coded status
3. Clicks request for timeline: Submitted → Approved → Provisioning → Ready
4. "Ready" status includes VM details (IP, credentials link)

### UX Principles

| Principle | Application |
|-----------|-------------|
| **Clarity over cleverness** | No hidden features, obvious actions |
| **Status always visible** | User never wonders "what's happening?" |
| **Mobile-friendly but desktop-first** | Responsive, but optimized for admin workflows |
| **Accessible by default** | WCAG 2.1 AA compliance, keyboard navigation |
| **Error prevention > error handling** | Disable invalid actions, guide correct input |

### Information Architecture

```
DVMM Portal
├── Dashboard (home)
│   ├── [User] Quick stats, recent requests, "New Request" CTA
│   └── [Admin] Pending count, recent activity, quick approve
├── Requests
│   ├── My Requests (user view)
│   ├── All Requests (admin view)
│   └── Request Detail (shared)
├── Projects
│   ├── Project List
│   └── Project Detail (VMs in project)
├── VMs
│   ├── My VMs (user)
│   └── All VMs (admin)
├── Reports (admin)
│   └── Export, basic analytics
└── Settings (admin)
    ├── User Management
    └── Tenant Configuration
```

---

## Functional Requirements

> **Purpose:** FRs define WHAT capabilities the product must have. This is the complete inventory of user-facing and system capabilities. UX designers will design for these. Architects will support these. Developers will implement these.

### User Account & Authentication

| ID | Requirement | Phase |
|----|-------------|-------|
| FR1 | Users can authenticate via Keycloak SSO (OIDC) | MVP |
| FR2 | Users can log out and terminate their session | MVP |
| FR3 | Users can view their own profile information | MVP |
| FR4 | Admins can invite new users to the tenant | MVP |
| FR5 | Admins can assign roles (User, Admin) to users | MVP |
| FR6 | Admins can deactivate user accounts | MVP |
| FR7 | Users can reset their password via Keycloak | MVP |
| FR7a | System handles Keycloak token expiration with transparent refresh | MVP |
| FR8 | Admins can assign Approver role to users | Growth |
| FR9 | Super Admins can manage users across tenants (CSP) | Growth |

### Project Management

| ID | Requirement | Phase |
|----|-------------|-------|
| FR10 | Users can view list of projects they have access to | MVP |
| FR11 | Admins can create new projects with name and description | MVP |
| FR12 | Admins can edit project details | MVP |
| FR13 | Admins can archive projects (soft delete) | MVP |
| FR14 | Users can view all VMs within a project | MVP |
| FR15 | Admins can assign users to projects | Growth |

### VM Request Management

| ID | Requirement | Phase |
|----|-------------|-------|
| FR16 | Users can create a new VM request | MVP |
| FR17 | Users can select a project for the VM request | MVP |
| FR18 | Users can select VM size (S/M/L/XL) with visible specs | MVP |
| FR19 | Users can provide VM name and justification | MVP |
| FR20 | Users can view their submitted requests | MVP |
| FR21 | Users can see real-time status of their requests | MVP |
| FR22 | Users can cancel pending (not yet approved) requests | MVP |
| FR23 | Users can view request history with full timeline | MVP |
| FR24 | Users receive suggestions based on historical patterns | Growth |

### Approval Workflow

| ID | Requirement | Phase |
|----|-------------|-------|
| FR25 | Admins can view all pending requests in dashboard | MVP |
| FR26 | Admins can view full request details before deciding | MVP |
| FR27 | Admins can approve a request with one click | MVP |
| FR28 | Admins can reject a request with mandatory reason | MVP |
| FR29 | System records who approved/rejected and when | MVP |
| FR30 | System automatically triggers provisioning on approval | MVP |
| FR31 | Admins can configure auto-approval policies (Dockets) | Growth |
| FR32 | System auto-approves requests matching policy rules | Growth |
| FR33 | Admins can define approval chains (multi-level) | Growth |

### VM Provisioning

| ID | Requirement | Phase |
|----|-------------|-------|
| FR34 | System provisions VM on VMware ESXi via vSphere API | MVP |
| FR35 | System applies VM size specifications (CPU/RAM/Disk) | MVP |
| FR36 | System assigns network configuration to provisioned VM | MVP |
| FR37 | System tracks provisioning progress and updates status | MVP |
| FR38 | System handles provisioning failures gracefully | MVP |
| FR39 | System retries failed provisioning with backoff | MVP |
| FR40 | Users can view provisioned VM details (IP, status) | MVP |
| FR41 | Users can request VM actions (reboot, shutdown) | Growth |
| FR42 | Users can request VM decommissioning | Growth |
| FR43 | System provisions on Proxmox VE | Vision |

### Status & Notifications

| ID | Requirement | Phase |
|----|-------------|-------|
| FR44 | Users see real-time status updates without page refresh | MVP |
| FR45 | System sends email notification on request submission | MVP |
| FR46 | System sends email notification on approval/rejection | MVP |
| FR47 | System sends email notification when VM is ready | MVP |
| FR48 | Users see in-app notifications for status changes | MVP |
| FR49 | Users can configure notification preferences | Growth |
| FR50 | System sends digest emails for pending approvals | Growth |

### Onboarding & Empty States

| ID | Requirement | Phase |
|----|-------------|-------|
| FR85 | System displays helpful empty states with clear next actions for new users | MVP |
| FR86 | System provides contextual onboarding guidance for first-time workflows | MVP |

### Admin Dashboard

| ID | Requirement | Phase |
|----|-------------|-------|
| FR51 | Admins see count of pending requests on dashboard | MVP |
| FR52 | Admins see list of recent requests across tenant | MVP |
| FR53 | Admins can filter requests by status, project, user | MVP |
| FR54 | Admins can sort requests by date, priority | MVP |
| FR55 | Admins see tenant-wide VM statistics | Growth |
| FR56 | Admins see usage trends and forecasts | Growth |

### Reporting & Audit

| ID | Requirement | Phase |
|----|-------------|-------|
| FR57 | Admins can export request history as CSV | MVP |
| FR58 | System maintains complete audit trail of all actions | MVP |
| FR59 | Admins can view audit log for any request | MVP |
| FR60 | Audit trail includes who, what, when for every change | MVP |
| FR90 | System provides ISO 27001 control mapping in audit reports | MVP |
| FR61 | Admins can generate compliance reports | Growth |
| FR62 | Admins can export audit trail for external review | Growth |
| FR63 | System provides chargeback/showback reports | Growth |

### Multi-Tenancy

| ID | Requirement | Phase |
|----|-------------|-------|
| FR64 | Each tenant's data is completely isolated from others | MVP |
| FR65 | Users can only see data within their own tenant | MVP |
| FR66 | Tenant context is enforced at database level (RLS) | MVP |
| FR67 | System rejects requests with missing tenant context | MVP |
| FR68 | Super Admins can create new tenants (CSP) | Growth |
| FR69 | Super Admins can configure tenant-specific settings | Growth |
| FR70 | Tenants can have custom VM size definitions | Growth |

### System Administration

| ID | Requirement | Phase |
|----|-------------|-------|
| FR71 | Admins can configure VMware connection settings | MVP |
| FR72 | Admins can configure email/SMTP settings | MVP |
| FR73 | Admins can view system health status | MVP |
| FR74 | System provides API for external integrations | Growth |
| FR75 | Admins can configure Docket approval policies | Growth |
| FR76 | System integrates with ServiceNow for ticket sync | Growth |

### Error Handling & Resilience

| ID | Requirement | Phase |
|----|-------------|-------|
| FR77 | System handles VMware API connection failures with appropriate user feedback | MVP |
| FR78 | System implements configurable retry logic for transient VMware errors | MVP |
| FR79 | System handles partial failures in operations and reports specific error context | MVP |
| FR80 | System logs all infrastructure errors with correlation IDs for troubleshooting | MVP |

### Quota Management

| ID | Requirement | Phase |
|----|-------------|-------|
| FR82 | Admins can define resource quotas per tenant (vCPU, RAM, Disk, VM count) | MVP |
| FR83 | Users can view remaining quota capacity before submitting requests | MVP |
| FR84 | System enforces quotas synchronously and prevents over-provisioning | MVP |

**Quota Enforcement Pattern (FR84):** In CQRS architecture, synchronous quota enforcement requires careful design. The Command Handler must validate quota availability BEFORE accepting the command, using a dedicated Quota Projection that is updated synchronously. Architecture Document must specify: (1) Quota Projection update strategy, (2) Optimistic locking for concurrent requests, (3) Reservation pattern for in-flight requests.

### Capacity & Cost Visibility

| ID | Requirement | Phase |
|----|-------------|-------|
| FR87 | Admins can view real-time resource utilization dashboard across tenant | MVP |
| FR88 | Users can see estimated cost per VM size before requesting | Growth |
| FR89 | Admins can configure cost models per VM size | Growth |

---

### Functional Requirements Summary

| Category | MVP | Growth | Total |
|----------|-----|--------|-------|
| User Account & Auth | 8 | 2 | 10 |
| Project Management | 5 | 1 | 6 |
| VM Request Management | 8 | 1 | 9 |
| Approval Workflow | 6 | 3 | 9 |
| VM Provisioning | 7 | 3 | 10 |
| Status & Notifications | 5 | 2 | 7 |
| Onboarding & Empty States | 2 | 0 | 2 |
| Admin Dashboard | 4 | 2 | 6 |
| Reporting & Audit | 5 | 3 | 8 |
| Multi-Tenancy | 4 | 3 | 7 |
| System Administration | 3 | 3 | 6 |
| Error Handling & Resilience | 4 | 0 | 4 |
| Quota Management | 3 | 0 | 3 |
| Capacity & Cost Visibility | 1 | 2 | 3 |
| **TOTAL** | **65** | **25** | **90** |

**MVP:** 65 Functional Requirements
**Growth:** 25 additional Functional Requirements

---

## Non-Functional Requirements

> **Purpose:** NFRs define HOW the system must behave—quality attributes that apply across all features. These are constraints and standards the architecture must satisfy.

### Performance

| ID | Requirement | Target | Phase |
|----|-------------|--------|-------|
| NFR-PERF-1 | API response time (95th percentile) | < 500ms | MVP |
| NFR-PERF-2 | API response time (99th percentile) | < 2s | MVP |
| NFR-PERF-3 | Dashboard page load time | < 2s | MVP |
| NFR-PERF-4 | Request submission to confirmation | < 3s | MVP |
| NFR-PERF-5 | Approval action processing | < 1s | MVP |
| NFR-PERF-6 | Audit log query (30 days) | < 5s | MVP |
| NFR-PERF-7 | CSV export (1000 records) | < 10s | MVP |
| NFR-PERF-8 | Real-time status update latency | < 500ms | MVP |
| NFR-PERF-9 | Dashboard initial load (cold cache) | < 3s | MVP |
| NFR-PERF-10 | Incremental projection rebuild (per event batch) | < 100ms/event | MVP |
| NFR-PERF-11 | Aggregate snapshot threshold | Every 100 events | MVP |
| NFR-PERF-12 | System performance under load (100 concurrent) | No degradation >20% | MVP |

**Measurement:** Application Performance Monitoring (APM) with percentile tracking.

### Scalability

| ID | Requirement | Target | Phase |
|----|-------------|--------|-------|
| NFR-SCALE-1 | Concurrent users per tenant | 100+ | MVP |
| NFR-SCALE-2 | Total tenants supported | 50+ | MVP |
| NFR-SCALE-3 | VMs per tenant | 1,000+ | MVP |
| NFR-SCALE-4 | Requests per day (system-wide) | 10,000+ | MVP |
| NFR-SCALE-5 | Event store growth | 1M+ events/year | MVP |
| NFR-SCALE-6 | Horizontal scaling | Stateless services | MVP |
| NFR-SCALE-7 | Database connection pooling | Per-tenant limits | MVP |

**Architecture:** Stateless application tier enables horizontal scaling. PostgreSQL handles vertical scaling with read replicas for Growth phase.

### Security

| ID | Requirement | Target | Phase |
|----|-------------|--------|-------|
| NFR-SEC-1 | All traffic encrypted (TLS 1.3) | 100% | MVP |
| NFR-SEC-2 | Authentication via OIDC (Keycloak) | Required | MVP |
| NFR-SEC-3 | Tenant isolation (PostgreSQL RLS) | Database-enforced | MVP |
| NFR-SEC-4 | Session timeout | 30 min inactive | MVP |
| NFR-SEC-5 | Password policy | Keycloak-managed | MVP |
| NFR-SEC-6 | API rate limiting | 100 req/min/user | MVP |
| NFR-SEC-7 | Input validation | All endpoints | MVP |
| NFR-SEC-8 | SQL injection prevention | Parameterized queries | MVP |
| NFR-SEC-9 | XSS prevention | Content Security Policy | MVP |
| NFR-SEC-10 | CSRF protection | Token-based | MVP |
| NFR-SEC-11 | Secrets management | Vault/env injection | MVP |
| NFR-SEC-12 | Dependency vulnerability scanning | Zero critical | MVP |
| NFR-SEC-13 | Penetration testing | Annual | Growth |
| NFR-SEC-14 | MFA support | Keycloak-provided | Growth |

**Principle:** Defense in depth. Fail-closed on missing tenant context.

### Availability & Reliability

| ID | Requirement | Target | Phase |
|----|-------------|--------|-------|
| NFR-AVAIL-1 | Uptime SLA | 99.5% | MVP |
| NFR-AVAIL-2 | Planned maintenance window | < 4h/month | MVP |
| NFR-AVAIL-3 | Recovery Time Objective (RTO) | < 4 hours | MVP |
| NFR-AVAIL-4 | Recovery Point Objective (RPO) | < 1 hour | MVP |
| NFR-AVAIL-5 | Database backup frequency | Daily + WAL | MVP |
| NFR-AVAIL-6 | Backup retention | 30 days | MVP |
| NFR-AVAIL-7 | Health check endpoints | /health, /ready | MVP |
| NFR-AVAIL-8 | Graceful degradation | VMware offline handling | MVP |
| NFR-AVAIL-9 | Uptime SLA (Enterprise) | 99.9% | Growth |
| NFR-AVAIL-10 | Multi-region deployment | Active-passive | Vision |
| NFR-AVAIL-11 | VMware offline: Request queuing | Requests queued, processed on reconnect | MVP |
| NFR-AVAIL-12 | VMware offline: Read operations | Continue working (cached data) | MVP |
| NFR-AVAIL-13 | VMware offline: User notification | Clear status banner displayed | MVP |

**Note:** 99.5% = ~43 minutes downtime/month. Appropriate for MVP. Enterprise tier gets 99.9%.

**Graceful Degradation Detail:** When VMware is unreachable, users can still browse, create requests (queued), and view cached VM data. A clear banner indicates degraded state. Queued requests process automatically on reconnection.

### Compliance & Audit

| ID | Requirement | Target | Phase |
|----|-------------|--------|-------|
| NFR-COMP-1 | ISO 27001 audit readiness | All controls documented | MVP |
| NFR-COMP-2 | Complete audit trail | Event Sourced | MVP |
| NFR-COMP-3 | Audit log immutability | Append-only store | MVP |
| NFR-COMP-4 | Audit log retention | 7 years | MVP |
| NFR-COMP-4a | GDPR-compliant deletion in Event Store | Crypto-Shredding pattern | MVP |
| NFR-COMP-5 | GDPR data subject requests | Export/delete capability | MVP |
| NFR-COMP-6 | Data residency | EU (Germany) | MVP |
| NFR-COMP-7 | SOC 2 Type II certification | Prepared | Growth |
| NFR-COMP-8 | TISAX readiness | Automotive customers | Growth |
| NFR-COMP-9 | Data residency: Application hosting | Germany (DE) | MVP |
| NFR-COMP-10 | Data residency: Backup storage | Germany (DE) | MVP |
| NFR-COMP-11 | Data residency: Log aggregation | EU | MVP |

**Architecture:** Event Sourcing provides immutable, complete audit trail by design.

**Crypto-Shredding Pattern (NFR-COMP-4a):** To reconcile 7-year audit retention with GDPR deletion rights, personal data in events is encrypted with per-user keys. Deletion = key destruction, rendering personal data unrecoverable while preserving audit structure. Architecture Document must specify key management approach.

**Data Residency Clarity:** All primary data (PostgreSQL, Event Store) hosted in German data centers. Backups stored in Germany. Log aggregation may use EU locations but never outside EU.

### Maintainability & Operability

| ID | Requirement | Target | Phase |
|----|-------------|--------|-------|
| NFR-MAINT-1 | Test coverage | ≥ 80% | MVP |
| NFR-MAINT-2 | Mutation test score | ≥ 70% | MVP |
| NFR-MAINT-3 | Code review required | All changes | MVP |
| NFR-MAINT-4 | CI/CD pipeline | Automated | MVP |
| NFR-MAINT-5 | Deployment frequency | On-demand | MVP |
| NFR-MAINT-6 | Rollback capability | < 15 minutes | MVP |
| NFR-MAINT-7 | Feature flags | Supported | MVP |
| NFR-MAINT-8 | Database migrations | Zero-downtime | MVP |
| NFR-MAINT-9 | Documentation | Docusaurus portal | MVP |
| NFR-MAINT-10 | Developer onboarding | < 1 week | MVP |
| NFR-MAINT-11 | E2E test suite runtime | < 15 minutes | MVP |
| NFR-MAINT-12 | Contract tests for external APIs (VMware, Keycloak) | Required | MVP |
| NFR-MAINT-13 | Load/stress test suite | Quarterly execution | MVP |

**Principle:** "No Broken Windows" - quality gates enforced in CI.

**Testing Strategy:** Contract tests (Pact) ensure VMware/Keycloak API compatibility. E2E tests run on every PR. Load tests run quarterly and before major releases.

### Observability

| ID | Requirement | Target | Phase |
|----|-------------|--------|-------|
| NFR-OBS-1 | Structured logging (JSON) | All services | MVP |
| NFR-OBS-2 | Correlation IDs | Request tracing | MVP |
| NFR-OBS-3 | Metrics export (Prometheus) | Standard metrics | MVP |
| NFR-OBS-4 | Error alerting | PagerDuty/Slack | MVP |
| NFR-OBS-5 | Log aggregation | ELK/Loki | MVP |
| NFR-OBS-6 | Dashboard (Grafana) | Ops visibility | MVP |
| NFR-OBS-7 | Distributed tracing | OpenTelemetry | Growth |
| NFR-OBS-8 | SLO monitoring | Error budgets | Growth |

**Stack:** OpenTelemetry-ready. Grafana + Prometheus + Loki for MVP.

### Compatibility & Integration

| ID | Requirement | Target | Phase |
|----|-------------|--------|-------|
| NFR-COMPAT-1 | Browser support | Chrome, Firefox, Edge (latest 2 versions) | MVP |
| NFR-COMPAT-2 | Mobile responsive | Tablet + Phone viewports | MVP |
| NFR-COMPAT-3 | VMware vSphere API | 7.0+ | MVP |
| NFR-COMPAT-4 | Keycloak version | 22+ | MVP |
| NFR-COMPAT-5 | PostgreSQL version | 15+ | MVP |
| NFR-COMPAT-6 | API versioning | URL-based (v1, v2) | MVP |
| NFR-COMPAT-7 | OpenAPI spec | 3.0+ | MVP |
| NFR-COMPAT-8 | Proxmox VE API | 8.0+ | Growth |

### Localization

| ID | Requirement | Target | Phase |
|----|-------------|--------|-------|
| NFR-L10N-1 | Primary language | German (de-DE) | MVP |
| NFR-L10N-2 | Secondary language | English (en-US) | MVP |
| NFR-L10N-3 | Date/time format | Locale-aware | MVP |
| NFR-L10N-4 | Number format | Locale-aware | MVP |
| NFR-L10N-5 | i18n framework | React-intl | MVP |

### Support SLA

| ID | Requirement | Target | Phase |
|----|-------------|--------|-------|
| NFR-SUPPORT-1 | Critical incident response (P1 - system down) | < 1 hour | Growth |
| NFR-SUPPORT-2 | High priority response (P2 - major feature broken) | < 4 hours | Growth |
| NFR-SUPPORT-3 | Normal priority response (P3 - minor issues) | < 1 business day | Growth |

**Note:** Support SLAs apply to Enterprise tier customers. MVP uses best-effort support.

---

### Non-Functional Requirements Summary

| Category | MVP | Growth | Total |
|----------|-----|--------|-------|
| Performance | 12 | 0 | 12 |
| Scalability | 7 | 0 | 7 |
| Security | 12 | 2 | 14 |
| Availability & Reliability | 11 | 2 | 13 |
| Compliance & Audit | 10 | 2 | 12 |
| Maintainability & Operability | 13 | 0 | 13 |
| Observability | 6 | 2 | 8 |
| Compatibility & Integration | 7 | 1 | 8 |
| Localization | 5 | 0 | 5 |
| Support SLA | 0 | 3 | 3 |
| **TOTAL** | **83** | **12** | **95** |

**MVP:** 83 Non-Functional Requirements
**Growth:** 12 additional Non-Functional Requirements

---

## PRD Summary

### Document Statistics

| Metric | Count |
|--------|-------|
| **Functional Requirements** | 90 (65 MVP, 25 Growth) |
| **Non-Functional Requirements** | 95 (83 MVP, 12 Growth) |
| **Total Requirements** | 185 |
| **MVP Requirements** | 148 |
| **Growth Requirements** | 37 |

### Validation Status

| Check | Status | Date |
|-------|--------|------|
| Completeness | ✅ 92% | 2025-11-24 |
| Input Document Alignment | ✅ Pass | 2025-11-24 |
| Technical Feasibility | ✅ Pass (with notes) | 2025-11-24 |
| Consistency | ✅ Pass | 2025-11-24 |
| Critical Issues Fixed | ✅ 3/3 | 2025-11-24 |

### Key Architectural Decisions Implied

1. **Multi-Tenancy:** PostgreSQL Row-Level Security (RLS) - database-enforced isolation
2. **Audit Trail:** Event Sourcing with EAF custom event store - immutable, complete history
3. **Authentication:** Keycloak OIDC - SSO, token refresh, MFA-ready
4. **API Design:** CQRS pattern - separate read/write models for scalability
5. **Observability:** OpenTelemetry-ready with Prometheus + Grafana + Loki
6. **Resilience:** Graceful degradation with request queuing when VMware offline

### Next Steps

1. **Architecture Document** - Technical design based on these requirements
2. **UX Design** - Wireframes and flows for critical user journeys
3. **Epic Breakdown** - Split MVP into deliverable epics
4. **Story Creation** - Developer-ready user stories with acceptance criteria

### Risk Areas Identified

| Risk | Mitigation |
|------|------------|
| VMware API instability | Contract tests (Pact), graceful degradation |
| Event Store growth | Snapshot strategy (every 100 events), incremental projections |
| Multi-tenant complexity | RLS testing, tenant context validation |
| Compliance requirements | ISO 27001 control mapping from day 1 |
| GDPR vs Audit Trail | Crypto-Shredding pattern for personal data in events |
| Quota Race Conditions | Synchronous Command Handler with optimistic locking |

---

_This PRD captures the essence of DVMM - transforming VM provisioning from a 3-5 day email chaos into a streamlined self-service experience where users get VMs in minutes while admins maintain full control through intelligent workflows._

_Created through collaborative discovery with BMad Method Party Mode reviews._
