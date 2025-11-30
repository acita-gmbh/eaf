# Epic 2: Core Workflow - Technical Specification

**Version:** 1.0
**Created:** 2025-11-28
**Author:** SM Agent (Bob)
**Status:** Draft

---

## 1. Overview

### 1.1 Epic Summary

| Attribute | Value |
|-----------|-------|
| **Epic ID** | Epic 2 |
| **Title** | Core Workflow |
| **Goal** | Implement "Request → Approve → Notify" workflow (Tracer Bullet) |
| **Stories** | 12 |
| **Risk Level** | HIGH (first user-facing features) |
| **FRs Covered** | FR1, FR2, FR7a, FR16-FR23, FR25-FR29, FR44-FR46, FR48, FR72, FR85, FR86 (21 FRs) |

### 1.2 User Value Statement

> "Ich kann einen VM-Request erstellen und sehe genau, was damit passiert" - Complete transparency from request to decision.

### 1.3 Tracer Bullet Strategy

Epic 2 is the **Tracer Bullet** that validates the complete stack:
- Frontend: React + shadcn-admin-kit + Tailwind CSS
- Auth: Keycloak OIDC integration
- Backend: Spring WebFlux + CQRS/ES pattern
- Database: PostgreSQL with RLS tenant isolation
- Email: Async notifications via Spring Mail

**Important:** VM "provisioning" in Epic 2 creates a mock response only. Real VMware integration comes in Epic 3.

---

## 2. Objectives & Scope

### 2.1 In Scope

1. **Authentication Flow** (Story 2.1)
   - Keycloak OIDC login/logout
   - JWT validation and refresh
   - httpOnly cookie session management
   - Coverage restoration for `eaf-auth-keycloak` and `dvmm-api`

2. **End User Interface** (Stories 2.2-2.8)
   - Dashboard layout with request list
   - Empty states and onboarding
   - VM request form (basic fields + size selector)
   - Request submission command handling
   - Request status timeline with real-time updates

3. **Admin Interface** (Stories 2.9-2.11)
   - Approval queue with pending requests
   - Request detail view with full context
   - Approve/reject actions with optimistic locking

4. **Notifications** (Story 2.12)
   - Email on request creation (to admins)
   - Email on approval/rejection (to requester)
   - Async, fault-tolerant email sending

### 2.2 Out of Scope

- Real VMware provisioning (Epic 3)
- Project management (Epic 4)
- Audit trail/compliance features (Epic 5)
- Bulk approval operations
- Advanced filtering/search

### 2.3 Technical Debt to Address

| Item | Source | Action |
|------|--------|--------|
| `eaf-auth-keycloak` coverage | Story 1.7 | Restore ≥80% in Story 2.1 |
| `dvmm-api` coverage | Story 1.7 | Restore ≥80% in Story 2.1 |
| Pitest `eaf-auth-keycloak` | Story 1.11 | Restore ≥70% mutation score |

---

## 3. Architecture Alignment

### 3.1 Module Dependencies

```
┌─────────────────────────────────────────────────────────────┐
│                     dvmm-app (Spring Boot)                  │
│   ┌─────────────┐   ┌──────────────┐   ┌────────────────┐  │
│   │  dvmm-api   │──▶│dvmm-application│──▶│  dvmm-domain   │  │
│   │  (REST)     │   │  (Use Cases) │   │  (Aggregates)  │  │
│   └─────────────┘   └──────────────┘   └────────────────┘  │
│         │                  │                    ▲          │
│         ▼                  ▼                    │          │
│   ┌───────────────────────────────────────────────────┐    │
│   │              dvmm-infrastructure                   │    │
│   │   (jOOQ Projections, Email, Keycloak Adapter)     │    │
│   └───────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│                    EAF Framework                            │
│  ┌──────────┐  ┌─────────────────┐  ┌──────────────────┐   │
│  │ eaf-core │  │eaf-eventsourcing│  │   eaf-tenant     │   │
│  └──────────┘  └─────────────────┘  └──────────────────┘   │
│  ┌──────────────────┐  ┌─────────────────────────────────┐ │
│  │    eaf-auth      │  │      eaf-auth-keycloak          │ │
│  └──────────────────┘  └─────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 Architecture Constraints (Konsist Enforced)

| Rule | Enforcement |
|------|-------------|
| EAF modules MUST NOT import `de.acci.dvmm.*` | Konsist `ArchitectureTest` |
| `dvmm-domain` MUST NOT import `org.springframework.*` | Konsist `ArchitectureTest` |
| Commands/Queries in `dvmm-application` only | Package convention |
| REST controllers in `dvmm-api` only | Package convention |

### 3.3 CQRS Pattern Implementation

```kotlin
// Command Side (dvmm-application)
data class CreateVmRequestCommand(
    val tenantId: TenantId,
    val requesterId: UserId,
    val projectId: ProjectId,
    val vmName: VmName,
    val size: VmSize,
    val justification: String
)

class CreateVmRequestHandler(
    private val eventStore: EventStore,
    private val clock: Clock
) {
    suspend fun handle(command: CreateVmRequestCommand): VmRequestId {
        val aggregate = VmRequestAggregate.create(
            id = VmRequestId.generate(),
            tenantId = command.tenantId,
            requesterId = command.requesterId,
            projectId = command.projectId,
            vmName = command.vmName,
            size = command.size,
            justification = command.justification,
            clock = clock
        )
        eventStore.save(aggregate)
        return aggregate.id
    }
}

// Query Side (dvmm-infrastructure)
class VmRequestQueryService(
    private val dsl: DSLContext
) {
    suspend fun findMyRequests(
        tenantId: TenantId,
        userId: UserId,
        pagination: Pagination
    ): Page<VmRequestSummary> {
        // jOOQ query with RLS automatic tenant filtering
    }
}
```

---

## 4. Detailed Design

### 4.1 Domain Model

#### 4.1.1 VmRequestAggregate

```kotlin
// dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/vmrequest/VmRequestAggregate.kt

class VmRequestAggregate private constructor(
    override val id: VmRequestId,
    override val tenantId: TenantId,
    private var status: VmRequestStatus,
    private val requesterId: UserId,
    private val projectId: ProjectId,
    private val vmName: VmName,
    private val size: VmSize,
    private val justification: String,
    private var approvedBy: UserId? = null,
    private var rejectedBy: UserId? = null,
    private var rejectionReason: String? = null,
    private var cancelledAt: Instant? = null
) : AggregateRoot<VmRequestId>() {

    companion object {
        fun create(
            id: VmRequestId,
            tenantId: TenantId,
            requesterId: UserId,
            projectId: ProjectId,
            vmName: VmName,
            size: VmSize,
            justification: String,
            clock: Clock
        ): VmRequestAggregate {
            val aggregate = VmRequestAggregate(
                id = id,
                tenantId = tenantId,
                status = VmRequestStatus.PENDING,
                requesterId = requesterId,
                projectId = projectId,
                vmName = vmName,
                size = size,
                justification = justification
            )
            aggregate.recordEvent(
                VmRequestCreated(
                    aggregateId = id,
                    tenantId = tenantId,
                    requesterId = requesterId,
                    projectId = projectId,
                    vmName = vmName,
                    size = size,
                    justification = justification,
                    occurredAt = clock.instant()
                )
            )
            return aggregate
        }

        fun reconstitute(events: List<DomainEvent>): VmRequestAggregate {
            // Event sourcing reconstitution
        }
    }

    fun approve(adminId: UserId, clock: Clock) {
        require(status == VmRequestStatus.PENDING) {
            "Can only approve pending requests"
        }
        status = VmRequestStatus.APPROVED
        approvedBy = adminId
        recordEvent(
            VmRequestApproved(
                aggregateId = id,
                tenantId = tenantId,
                approvedBy = adminId,
                occurredAt = clock.instant()
            )
        )
    }

    fun reject(adminId: UserId, reason: String, clock: Clock) {
        require(status == VmRequestStatus.PENDING) {
            "Can only reject pending requests"
        }
        require(reason.length >= 10) {
            "Rejection reason must be at least 10 characters"
        }
        status = VmRequestStatus.REJECTED
        rejectedBy = adminId
        rejectionReason = reason
        recordEvent(
            VmRequestRejected(
                aggregateId = id,
                tenantId = tenantId,
                rejectedBy = adminId,
                reason = reason,
                occurredAt = clock.instant()
            )
        )
    }

    fun cancel(clock: Clock) {
        require(status == VmRequestStatus.PENDING) {
            "Can only cancel pending requests"
        }
        status = VmRequestStatus.CANCELLED
        cancelledAt = clock.instant()
        recordEvent(
            VmRequestCancelled(
                aggregateId = id,
                tenantId = tenantId,
                occurredAt = clock.instant()
            )
        )
    }
}
```

#### 4.1.2 Domain Events

```kotlin
// VmRequestCreated
data class VmRequestCreated(
    override val aggregateId: VmRequestId,
    override val tenantId: TenantId,
    val requesterId: UserId,
    val projectId: ProjectId,
    val vmName: VmName,
    val size: VmSize,
    val justification: String,
    override val occurredAt: Instant
) : DomainEvent

// VmRequestApproved
data class VmRequestApproved(
    override val aggregateId: VmRequestId,
    override val tenantId: TenantId,
    val approvedBy: UserId,
    override val occurredAt: Instant
) : DomainEvent

// VmRequestRejected
data class VmRequestRejected(
    override val aggregateId: VmRequestId,
    override val tenantId: TenantId,
    val rejectedBy: UserId,
    val reason: String,
    override val occurredAt: Instant
) : DomainEvent

// VmRequestCancelled
data class VmRequestCancelled(
    override val aggregateId: VmRequestId,
    override val tenantId: TenantId,
    override val occurredAt: Instant
) : DomainEvent
```

#### 4.1.3 Value Objects

```kotlin
// VmRequestId
@JvmInline
value class VmRequestId(val value: UUID) {
    companion object {
        fun generate(): VmRequestId = VmRequestId(UUID.randomUUID())
        fun fromString(s: String): VmRequestId = VmRequestId(UUID.fromString(s))
    }
}

// VmName
@JvmInline
value class VmName(val value: String) {
    init {
        require(value.matches(Regex("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$"))) {
            "VM name must be 3-63 lowercase alphanumeric characters or hyphens"
        }
    }
}

// VmSize
enum class VmSize(val cpuCores: Int, val memoryGb: Int, val diskGb: Int) {
    S(cpuCores = 2, memoryGb = 4, diskGb = 50),
    M(cpuCores = 4, memoryGb = 8, diskGb = 100),
    L(cpuCores = 8, memoryGb = 16, diskGb = 200),
    XL(cpuCores = 16, memoryGb = 32, diskGb = 500)
}

// VmRequestStatus
enum class VmRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    PROVISIONING,
    READY,
    FAILED
}
```

### 4.2 API Design

#### 4.2.1 REST Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/requests` | Create VM request | User |
| GET | `/api/requests/mine` | List my requests | User |
| GET | `/api/requests/{id}` | Get request details | User (owner) |
| GET | `/api/requests/{id}/timeline` | Get request timeline | User (owner) |
| DELETE | `/api/requests/{id}` | Cancel pending request | User (owner) |
| GET | `/api/admin/requests/pending` | List pending requests | Admin |
| GET | `/api/admin/requests/{id}` | Get request details | Admin |
| POST | `/api/admin/requests/{id}/approve` | Approve request | Admin |
| POST | `/api/admin/requests/{id}/reject` | Reject request | Admin |

#### 4.2.2 Request/Response DTOs

```kotlin
// CreateVmRequestRequest
data class CreateVmRequestRequest(
    val vmName: String,
    val projectId: String,
    val size: String,
    val justification: String
)

// VmRequestResponse
data class VmRequestResponse(
    val id: String,
    val vmName: String,
    val projectId: String,
    val projectName: String,
    val size: VmSizeResponse,
    val status: String,
    val justification: String,
    val requesterName: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

// VmSizeResponse
data class VmSizeResponse(
    val code: String,
    val cpuCores: Int,
    val memoryGb: Int,
    val diskGb: Int,
    val monthlyEstimate: String
)

// TimelineEventResponse
data class TimelineEventResponse(
    val type: String,
    val occurredAt: Instant,
    val actorName: String?,
    val details: Map<String, Any>?
)

// RejectRequestRequest
data class RejectRequestRequest(
    val reason: String
)
```

### 4.3 Database Schema

#### 4.3.1 Read Model Tables (Projections)

```sql
-- VM Request Projection (for query side)
CREATE TABLE "VM_REQUESTS" (
    "ID" UUID PRIMARY KEY,
    "TENANT_ID" UUID NOT NULL,
    "REQUESTER_ID" UUID NOT NULL,
    "REQUESTER_NAME" VARCHAR(255) NOT NULL,
    "PROJECT_ID" UUID NOT NULL,
    "PROJECT_NAME" VARCHAR(255) NOT NULL,
    "VM_NAME" VARCHAR(63) NOT NULL,
    "SIZE" VARCHAR(10) NOT NULL,
    "STATUS" VARCHAR(20) NOT NULL,
    "JUSTIFICATION" TEXT NOT NULL,
    "APPROVED_BY" UUID,
    "APPROVED_BY_NAME" VARCHAR(255),
    "REJECTED_BY" UUID,
    "REJECTED_BY_NAME" VARCHAR(255),
    "REJECTION_REASON" TEXT,
    "CREATED_AT" TIMESTAMP WITH TIME ZONE NOT NULL,
    "UPDATED_AT" TIMESTAMP WITH TIME ZONE NOT NULL,
    "VERSION" BIGINT NOT NULL DEFAULT 0
);

-- [jooq ignore start]
ALTER TABLE "VM_REQUESTS" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "VM_REQUESTS" FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON "VM_REQUESTS"
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
-- [jooq ignore stop]

CREATE INDEX idx_vm_requests_tenant_requester ON "VM_REQUESTS"("TENANT_ID", "REQUESTER_ID");
CREATE INDEX idx_vm_requests_tenant_status ON "VM_REQUESTS"("TENANT_ID", "STATUS");

-- Request Timeline Projection
CREATE TABLE "REQUEST_TIMELINE_EVENTS" (
    "ID" UUID PRIMARY KEY,
    "REQUEST_ID" UUID NOT NULL REFERENCES "VM_REQUESTS"("ID"),
    "TENANT_ID" UUID NOT NULL,
    "EVENT_TYPE" VARCHAR(50) NOT NULL,
    "ACTOR_ID" UUID,
    "ACTOR_NAME" VARCHAR(255),
    -- [jooq ignore start]
    "DETAILS" JSONB,
    -- [jooq ignore stop]
    "OCCURRED_AT" TIMESTAMP WITH TIME ZONE NOT NULL
);

-- [jooq ignore start]
ALTER TABLE "REQUEST_TIMELINE_EVENTS" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "REQUEST_TIMELINE_EVENTS" FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON "REQUEST_TIMELINE_EVENTS"
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
-- [jooq ignore stop]

CREATE INDEX idx_timeline_request ON "REQUEST_TIMELINE_EVENTS"("REQUEST_ID", "OCCURRED_AT");
```

### 4.4 Frontend Architecture

#### 4.4.1 Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| React | 18.x | UI Framework |
| TypeScript | 5.x | Type Safety |
| Vite | 5.x | Build Tool |
| shadcn/ui | Latest | Component Library |
| Tailwind CSS | 4.x | Styling |
| Vitest | 3.x | Unit Testing |
| @testing-library/react | 16.x | Component Testing |
| React Query | 5.x | Server State |
| React Hook Form | 7.x | Form Management |
| Zod | 3.x | Validation |
| react-oidc-context | 3.x | Keycloak OIDC |

#### 4.4.2 Component Structure

```
dvmm/dvmm-web/src/
├── components/
│   ├── ui/                    # shadcn/ui components
│   ├── layout/
│   │   ├── Header.tsx
│   │   ├── Sidebar.tsx
│   │   ├── MobileNav.tsx
│   │   └── DashboardLayout.tsx
│   ├── dashboard/
│   │   ├── StatsCard.tsx
│   │   └── RequestsPlaceholder.tsx
│   ├── requests/
│   │   ├── VmRequestCard.tsx
│   │   ├── VmRequestForm.tsx
│   │   ├── SizeSelector.tsx
│   │   ├── RequestTimeline.tsx
│   │   └── RequestList.tsx
│   └── admin/
│       ├── ApprovalQueue.tsx
│       ├── RequestDetailAdmin.tsx
│       └── ApprovalActions.tsx
├── pages/
│   ├── Dashboard.tsx
│   ├── NewRequest.tsx
│   ├── RequestDetail.tsx
│   └── admin/
│       ├── AdminDashboard.tsx
│       └── RequestDetailAdmin.tsx
├── hooks/
│   ├── useAuth.ts
│   ├── useRequests.ts
│   └── useAdmin.ts
├── api/
│   └── api-client.ts
├── auth/
│   └── auth-config.ts
├── test/
│   ├── setup.ts
│   └── test-utils.tsx
└── types/
    └── index.ts
```

#### 4.4.3 Design System (Tech Teal Theme)

```css
/* Primary Colors (oklch format in index.css) */
--color-primary: #0D9488;      /* Tech Teal 600 - Primary actions */
--color-primary-hover: #0f766e; /* Tech Teal 700 - Hover state (darker) */
--color-primary-light: #14b8a6; /* Tech Teal 500 - Accents */

/* Status Colors (HSL format as CSS variables) */
--status-pending: 43 96% 56%;    /* Amber 500 - #f59e0b */
--status-approved: 160 84% 39%;  /* Emerald 500 - #10b981 */
--status-rejected: 347 77% 50%;  /* Rose 500 - #f43f5e */
--status-info: 199 89% 48%;      /* Sky 500 - #0ea5e9 */

/* Background */
--color-bg-primary: #ffffff;
--color-bg-secondary: #f9fafb;
--color-bg-tertiary: #f3f4f6;
```

**Note:** Tailwind 4 uses CSS variables in `src/index.css`. There is NO `tailwind.config.js` file.
Usage pattern: `text-[hsl(var(--status-pending))] bg-[hsl(var(--status-pending)/0.1)]`

### 4.5 Email Templates

#### 4.5.1 Request Created (to Admins)

```html
<!-- resources/templates/email/request-created.html -->
Subject: [DVMM] Neuer VM-Request: {{vmName}}

Hallo Admin,

Ein neuer VM-Request wurde eingereicht:

Antragsteller: {{requesterName}}
VM-Name: {{vmName}}
Größe: {{size}} ({{cpuCores}} vCPU, {{memoryGb}} GB RAM)
Projekt: {{projectName}}
Begründung: {{justification}}

Request prüfen: {{approvalLink}}

Mit freundlichen Grüßen,
DVMM System
```

#### 4.5.2 Request Approved (to Requester)

```html
<!-- resources/templates/email/request-approved.html -->
Subject: [DVMM] Request genehmigt: {{vmName}}

Hallo {{requesterName}},

Ihr VM-Request wurde genehmigt!

VM-Name: {{vmName}}
Genehmigt von: {{approverName}}
Genehmigt am: {{approvedAt}}

Die VM wird nun bereitgestellt. Sie erhalten eine weitere E-Mail,
sobald die VM verfügbar ist.

Details ansehen: {{requestLink}}

Mit freundlichen Grüßen,
DVMM System
```

---

## 5. Story Breakdown

### 5.1 Story Dependency Graph

```
Story 2.1 (Keycloak Login) ──┬──▶ Story 2.2 (Dashboard)
                             │         │
                             │         ▼
                             │    Story 2.3 (Empty States)
                             │         │
                             │         ▼
                             │    Story 2.4 (Form Basic) ──▶ Story 2.5 (Size Selector)
                             │                                      │
                             │                                      ▼
                             │                               Story 2.6 (Submit)
                             │                                      │
                             ├──────────────────────────────────────┘
                             │                                      │
                             ▼                                      ▼
                       Story 2.9 (Admin Queue) ◀──────────── Story 2.7 (My Requests)
                             │                                      │
                             ▼                                      ▼
                       Story 2.10 (Admin Detail)             Story 2.8 (Timeline)
                             │
                             ▼
                       Story 2.11 (Approve/Reject)
                             │
                             ▼
                       Story 2.12 (Email Notifications)
```

### 5.2 Story Summary

| ID | Title | Prerequisites | FRs | Risk |
|----|-------|---------------|-----|------|
| 2.1 | Keycloak Login Flow | 1.7 | FR1, FR2, FR7a | HIGH |
| 2.2 | End User Dashboard Layout | 2.1 | FR85 | LOW |
| 2.3 | Empty States & Onboarding | 2.2 | FR85, FR86 | LOW |
| 2.4 | VM Request Form - Basic | 2.2 | FR17, FR19 | MEDIUM |
| 2.5 | VM Request Form - Size | 2.4 | FR18, FR83 | MEDIUM |
| 2.6 | VM Request Form - Submit | 2.4, 2.5, 1.4 | FR16, FR45 | HIGH |
| 2.7 | My Requests List & Cancel | 1.8, 2.6 | FR20, FR22, FR23 | MEDIUM |
| 2.8 | Request Status Timeline | 2.7 | FR21, FR44 | MEDIUM |
| 2.9 | Admin Approval Queue | 2.7, 1.7 | FR25 | MEDIUM |
| 2.10 | Request Detail View (Admin) | 2.9 | FR26 | LOW |
| 2.11 | Approve/Reject Actions | 2.10, 1.4 | FR27, FR28, FR29 | HIGH |
| 2.12 | Email Notifications | 2.6, 2.11 | FR45, FR46, FR72 | MEDIUM |

### 5.3 Key Acceptance Criteria (High-Risk Stories)

#### Story 2.1: Keycloak Login Flow (HIGH)

| AC | Given | When | Then |
|----|-------|------|------|
| AC-2.1.1 | Unauthenticated user | Navigates to `/dashboard` | Redirected to Keycloak login page |
| AC-2.1.2 | User on Keycloak login | Enters valid credentials | Redirected back to `/dashboard` with session |
| AC-2.1.3 | Authenticated user | Session JWT expires | Automatic silent refresh via refresh token |
| AC-2.1.4 | Authenticated user | Clicks logout | Session invalidated, redirected to login |
| AC-2.1.5 | User with invalid token | Makes API request | Receives 401 Unauthorized |
| AC-2.1.6 | Tenant A user | Accesses Tenant B data | Receives 403 Forbidden (RLS enforced) |

**Coverage Restoration:**
- `eaf-auth-keycloak` module ≥80% line coverage (restore from exclusion)
- `dvmm-api` module ≥80% line coverage (restore from exclusion)
- Pitest mutation score ≥70% for both modules

#### Story 2.6: VM Request Form - Submit (HIGH)

| AC | Given | When | Then |
|----|-------|------|------|
| AC-2.6.1 | Valid form data | Clicks submit | `VmRequestCreated` event stored, request ID returned |
| AC-2.6.2 | Valid form data | Clicks submit | User sees success toast with request ID |
| AC-2.6.3 | Invalid VM name (spaces) | Clicks submit | Validation error shown, no event stored |
| AC-2.6.4 | Empty justification | Clicks submit | Validation error: "Justification required" |
| AC-2.6.5 | Request created | - | Projection updated, visible in "My Requests" |
| AC-2.6.6 | Request created | - | Email notification queued for admins |

**Validation Rules:**
- VM Name: `^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$` (3-63 chars, lowercase alphanumeric/hyphen)
- Justification: min 10 characters
- Size: must be valid enum (S, M, L, XL)

#### Story 2.11: Approve/Reject Actions (HIGH)

| AC | Given | When | Then |
|----|-------|------|------|
| AC-2.11.1 | Pending request | Admin clicks Approve | `VmRequestApproved` event stored |
| AC-2.11.2 | Pending request | Admin clicks Approve | Status changes to APPROVED, timestamp recorded |
| AC-2.11.3 | Pending request | Admin clicks Reject | Rejection reason dialog appears |
| AC-2.11.4 | Rejection reason < 10 chars | Clicks confirm | Validation error: "Reason must be at least 10 characters" |
| AC-2.11.5 | Valid rejection reason | Clicks confirm | `VmRequestRejected` event stored with reason |
| AC-2.11.6 | Already approved request | Another admin tries approve | Optimistic lock error: "Request already processed" |
| AC-2.11.7 | Request approved/rejected | - | Email notification sent to requester |

**Optimistic Locking:**
- Use aggregate version for concurrent modification detection
- Return 409 Conflict if version mismatch

---

## 6. Non-Functional Requirements

### 6.1 Performance (TC-003)

| Metric | Target | Measurement |
|--------|--------|-------------|
| Page Load (Dashboard) | < 2s | Lighthouse |
| API Response (List) | < 500ms p95 | k6 |
| API Response (Create) | < 1s p95 | k6 |
| Concurrent Users | 50 per tenant | k6 |

### 6.2 Security (TC-002)

| Requirement | Implementation |
|-------------|----------------|
| Authentication | Keycloak OIDC, JWT validation |
| Session | httpOnly cookie, Secure, SameSite=Lax |
| CSRF | X-CSRF-Token header on mutations |
| Tenant Isolation | PostgreSQL RLS (automatic) |
| Input Validation | Zod (frontend), Bean Validation (backend) |

### 6.3 Accessibility

| Requirement | Standard |
|-------------|----------|
| Keyboard Navigation | Full support |
| Screen Reader | ARIA labels |
| Color Contrast | WCAG AA |
| Focus Indicators | Visible |

### 6.4 Testability (TC-001, TC-004)

| Test Type | Coverage Target | Tools |
|-----------|-----------------|-------|
| Unit Tests | ≥80% | JUnit 6, MockK |
| Integration Tests | Critical paths | Testcontainers |
| Mutation Tests | ≥70% | Pitest |
| E2E Tests | Happy paths | Playwright |

### 6.5 Observability

| Aspect | Implementation | Details |
|--------|----------------|---------|
| Structured Logging | SLF4J + Logback JSON | Every log includes `tenantId`, `requestId`, `userId` |
| Log Levels | ERROR, WARN, INFO, DEBUG | Production: INFO, Dev: DEBUG |
| Metrics | Micrometer + Prometheus | Counters, Gauges, Histograms |
| Health Checks | Spring Actuator | `/actuator/health`, `/actuator/info` |
| Distributed Tracing | OpenTelemetry (future) | Correlation IDs via `X-Request-ID` header |

**Key Metrics:**

| Metric Name | Type | Labels | Purpose |
|-------------|------|--------|---------|
| `dvmm_vm_requests_total` | Counter | `tenant_id`, `status` | Total requests created |
| `dvmm_vm_requests_approved_total` | Counter | `tenant_id` | Approved requests |
| `dvmm_vm_requests_rejected_total` | Counter | `tenant_id` | Rejected requests |
| `dvmm_api_request_duration_seconds` | Histogram | `endpoint`, `method` | API latency |
| `dvmm_email_sent_total` | Counter | `template`, `status` | Email delivery tracking |

**Logging Standards:**

```kotlin
// Structured logging with kotlin-logging (mu.KotlinLogging)
logger.info { "VM request created: requestId=${requestId.value}, tenantId=${tenantId.value}, vmName=${vmName.value}, size=${size.name}" }

// Alternative: SLF4J with Logback structured arguments
import net.logstash.logback.argument.StructuredArguments.kv
logger.info("VM request created",
    kv("requestId", requestId.value),
    kv("tenantId", tenantId.value),
    kv("vmName", vmName.value),
    kv("size", size.name)
)
```

### 6.6 Reliability

| Aspect | Implementation | Details |
|--------|----------------|---------|
| Email Retry | Exponential Backoff | 3 retries: 1s, 5s, 30s |
| Email Dead-Letter | Database Table | Failed emails logged for manual review |
| Idempotency | Event ID deduplication | Projections skip duplicate events |
| Graceful Degradation | Email failures non-blocking | Request workflow continues if email fails |

**Email Reliability Flow:**

```
[Event] → [EmailNotificationHandler]
              │
              ├─► Send Email
              │      │
              │      ├─► Success → Log metric (sent)
              │      │
              │      └─► Failure → Retry (3x)
              │             │
              │             ├─► Success → Log metric (sent_after_retry)
              │             │
              │             └─► Permanent Failure → Dead-Letter Table
              │                    │
              │                    └─► Log ERROR + metric (failed)
              │
              └─► Request workflow continues (non-blocking)
```

**Dead-Letter Table Schema:**

```sql
CREATE TABLE "EMAIL_DEAD_LETTER" (
    "ID" UUID PRIMARY KEY,
    "TENANT_ID" UUID NOT NULL,
    "TEMPLATE" VARCHAR(50) NOT NULL,
    "RECIPIENT" VARCHAR(255) NOT NULL,
    -- [jooq ignore start]
    "PAYLOAD" JSONB NOT NULL,
    -- [jooq ignore stop]
    "ERROR_MESSAGE" TEXT NOT NULL,
    "RETRY_COUNT" INT NOT NULL,
    "CREATED_AT" TIMESTAMP WITH TIME ZONE NOT NULL,
    "LAST_RETRY_AT" TIMESTAMP WITH TIME ZONE
);

-- [jooq ignore start]
ALTER TABLE "EMAIL_DEAD_LETTER" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "EMAIL_DEAD_LETTER" FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON "EMAIL_DEAD_LETTER"
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
-- [jooq ignore stop]

CREATE INDEX idx_email_dead_letter_tenant ON "EMAIL_DEAD_LETTER"("TENANT_ID", "CREATED_AT");
```

---

## 7. Dependencies

### 7.1 Epic 1 Dependencies

| Dependency | Story | Status |
|------------|-------|--------|
| Event Store | 1.3 | DONE |
| Aggregate Base | 1.4 | DONE |
| Tenant Context | 1.5 | DONE |
| PostgreSQL RLS | 1.6 | DONE |
| Keycloak Integration | 1.7 | DONE |
| jOOQ Projections | 1.8 | DONE |
| Testcontainers | 1.9 | DONE |
| CI/CD Pipeline | 1.11 | DONE |

### 7.2 External Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Keycloak | 26.x | Identity Provider |
| PostgreSQL | 16.x | Database |
| SMTP Server | - | Email delivery |
| Node.js | 20.x | Frontend build |

### 7.3 New Libraries

| Library | Version | Module | Purpose |
|---------|---------|--------|---------|
| Spring Mail | 3.5.x | dvmm-infrastructure | Email sending |
| Thymeleaf | 3.1.x | dvmm-infrastructure | Email templates |
| kotlinx-html | 0.11.x | dvmm-infrastructure | HTML generation |

---

## 8. Acceptance Criteria Traceability

### 8.1 FR Mapping

| FR | Description | Story |
|----|-------------|-------|
| FR1 | SSO Authentication | 2.1 |
| FR2 | Session Management | 2.1 |
| FR7a | Token Refresh | 2.1 |
| FR16 | Create VM Request | 2.6 |
| FR17 | VM Name Validation | 2.4 |
| FR18 | VM Size Selection | 2.5 |
| FR19 | Justification Field | 2.4 |
| FR20 | View My Requests | 2.7 |
| FR21 | Request Timeline | 2.8 |
| FR22 | Cancel Request | 2.7 |
| FR23 | Request Status Display | 2.7 |
| FR25 | Admin Approval Queue | 2.9 |
| FR26 | Request Detail (Admin) | 2.10 |
| FR27 | Approve Request | 2.11 |
| FR28 | Reject Request | 2.11 |
| FR29 | Rejection Reason | 2.11 |
| FR44 | Real-time Updates | 2.8 |
| FR45 | Email on Request | 2.12 |
| FR46 | Email on Decision | 2.12 |
| FR48 | Status Notification | 2.12 |
| FR72 | SMTP Configuration | 2.12 |
| FR83 | Quota Visibility | 2.5 |
| FR85 | Empty States | 2.2, 2.3 |
| FR86 | Onboarding | 2.3 |

---

## 9. Risk Assessment

### 9.1 High Risk Items

| Risk | Mitigation | Owner |
|------|------------|-------|
| Keycloak OIDC complexity | Use proven `react-oidc-context` | DEV |
| Frontend learning curve | Follow shadcn-admin-kit patterns | DEV |
| Coverage restoration blockers | Prioritize in Story 2.1 | DEV |
| Real-time updates complexity | Start with polling, add SSE later | DEV |

### 9.2 Technical Uncertainties

| Uncertainty | Resolution Plan |
|-------------|-----------------|
| Token storage security | httpOnly cookie + CSRF validated |
| Email delivery reliability | Async + retry + dead-letter logging |
| Timeline real-time updates | Polling initially, SSE if needed |

---

## 10. Implementation Notes

### 10.1 Story 2.1 Priority Actions

1. **First:** Add Keycloak Testcontainer tests for `eaf-auth-keycloak`
2. **Second:** Add SecurityConfig integration tests for `dvmm-api`
3. **Third:** Remove coverage exclusions from both `build.gradle.kts` files
4. **Fourth:** Implement frontend OIDC flow

### 10.2 Frontend Setup (Story 2.2)

```bash
# Create React frontend (if not exists)
cd dvmm
npm create vite@latest dvmm-web -- --template react-ts
cd dvmm-web

# Install shadcn/ui and dependencies
npx shadcn@latest init

# Install testing infrastructure (REQUIRED before writing tests)
npm install -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom

# Add shadcn components as needed
npx shadcn@latest add card badge avatar separator sheet
```

**Vitest Configuration (`vitest.config.ts`):**

```typescript
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
})
```

### 10.3 Email Configuration

```yaml
# application.yml
spring:
  mail:
    host: ${SMTP_HOST:localhost}
    port: ${SMTP_PORT:1025}
    username: ${SMTP_USER:}
    password: ${SMTP_PASS:}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

dvmm:
  email:
    from: noreply@dvmm.example.com
    enabled: ${EMAIL_ENABLED:true}
```

---

## 11. Definition of Done

### 11.1 Story Level

- [ ] All acceptance criteria implemented
- [ ] Unit tests ≥80% coverage
- [ ] Mutation score ≥70%
- [ ] Integration tests for critical paths
- [ ] API documentation updated
- [ ] Code review approved
- [ ] CI pipeline passes

### 11.2 Epic Level

- [ ] All 12 stories completed
- [ ] Coverage exclusions removed (Story 2.1)
- [ ] E2E test suite passes (Playwright)
- [ ] Performance targets met (k6)
- [ ] Security review passed
- [ ] User acceptance testing

---

## 12. Lessons Learned from Epic 1

### 12.1 Key Patterns Established (Apply in Epic 2)

| Pattern | Source | Application |
|---------|--------|-------------|
| **TenantContextElement** | Story 1.5 | Use `CoroutineContext.Element` for tenant propagation in all coroutines |
| **RLS NULLIF Pattern** | Story 1.6 | `NULLIF(current_setting('app.tenant_id', true), '')::uuid` for fail-closed isolation |
| **DDLDatabase jOOQ** | Story 1.8 | Use quoted uppercase identifiers in `jooq-init.sql` |
| **@IsolatedEventStore** | Story 1.9 | TRUNCATE strategy (~5ms) for test isolation |
| **Snapshot Threshold** | Story 1.4 | Default 100 events per aggregate |

### 12.2 Action Items from Retrospective

| # | Action | Status | Target Story |
|---|--------|--------|--------------|
| 1 | Restore `eaf-auth-keycloak` coverage ≥80% | **REQUIRED** | 2.1 |
| 2 | Restore `dvmm-api` coverage ≥80% | **REQUIRED** | 2.1 |
| 3 | Restore `eaf-auth-keycloak` Pitest ≥70% | **REQUIRED** | 2.1 |
| 4 | jooq-init.sql sync checklist | DONE | - |
| 5 | "Learnings from Previous Story" mandatory | IN EFFECT | All stories |

### 12.3 Recurring Review Findings to Avoid

| Finding | Prevention |
|---------|------------|
| bootJar/jar Config | Check `tasks.named("bootJar") { enabled = true/false }` in `build.gradle.kts` |
| Security Headers | Ensure CORS, CSRF configuration in SecurityConfig |
| Improve logging | Use structured logging with context (tenantId, requestId) |
| Test Naming | Follow `should X when Y` or backtick convention |

### 12.4 Critical Risk: Frontend Tracer Bullet

Story 2.1 is the **first user-facing code** after 11 purely technical foundation stories. The team must mentally switch from backend infrastructure to user-visible features.

**Recommended Approach:**
- Start with minimal viable UI (login → redirect → "Hello, {name}")
- Validate complete auth flow before building dashboard
- Use Playwright smoke test to verify end-to-end

---

## 13. References

- [Epic 2 Definition](../epics.md#epic-2-core-workflow)
- [Architecture](../architecture.md)
- [PRD](../prd.md)
- [UX Design Specification](../ux-design-specification.md)
- [Test Design System](../test-design-system.md)
- [Epic 1 Tech Spec](tech-spec-epic-1.md)
- [Epic 1 Retrospective](retrospectives/epic-1-foundation-retro.md)

---

*Generated by SM Agent (Bob) via BMAD epic-tech-context workflow*
*Model: claude-opus-4-5-20251101*
