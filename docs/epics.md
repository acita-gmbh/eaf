# DVMM - Epic Breakdown

**Author:** Wall-E
**Date:** 2025-11-25
**Project Level:** Enterprise
**Target Scale:** Multi-tenant SaaS (50+ tenants, 1000+ VMs/tenant)
**Framework:** Enterprise Application Framework (EAF)

---

## Overview

This document provides the complete epic and story breakdown for DVMM (Dynamic Virtual Machine Manager), decomposing the requirements from the [PRD](./prd.md) into implementable stories.

**Living Document Notice:** This document incorporates context from:
- ✅ PRD (90 FRs, 95 NFRs)
- ✅ UX Design Specification (Tech Teal theme, shadcn-admin-kit, 3 user journeys)
- ✅ Architecture Document (Kotlin 2.2, Spring Boot 3.5, CQRS/ES, PostgreSQL RLS)

## Epic Summary

| Epic | Name | Stories | Key Value | Risk |
|------|------|---------|-----------|------|
| 1 | Foundation | 11 | Technical base for all features | Low |
| 2 | Core Workflow | 13 | "Request → Approve → Notify" | High |
| 3 | VM Provisioning | 10 | "VM is actually created" | **Critical** |
| 4 | Projects & Quota | 9 | "Resources organized & controlled" | Medium |
| 5 | Compliance & Oversight | 10 | "Audit-ready in 30 seconds" | Medium |

**Total: 53 Stories for MVP**

### Epic Sequence & Dependencies

```
Epic 1 (Foundation) ──────► Enables all subsequent epics
        │
        ▼
Epic 2 (Core Workflow) ───► Tracer Bullet demo (Docker mock)
        │
        ▼
Epic 3 (Provisioning) ────► Real VMware integration (highest risk)
        │
        ▼
Epic 4 (Projects & Quota) ► Resource organization & control
        │
        ▼
Epic 5 (Compliance) ──────► Audit-ready, ISO 27001 prepared
```

---

## Functional Requirements Inventory

### MVP Functional Requirements (66 FRs)

#### User Account & Authentication (8 FRs)
| ID | Requirement | Epic |
|----|-------------|------|
| FR1 | Users can authenticate via Keycloak SSO (OIDC) | Epic 2 |
| FR2 | Users can log out and terminate their session | Epic 2 |
| FR3 | Users can view their own profile information | Growth |
| FR4 | Admins can invite new users to the tenant | Growth |
| FR5 | Admins can assign roles (User, Admin) to users | Growth |
| FR6 | Admins can deactivate user accounts | Growth |
| FR7 | Users can reset their password via Keycloak | Growth |
| FR7a | System handles Keycloak token expiration with transparent refresh | Epic 2 |

#### Project Management (5 FRs)
| ID | Requirement | Epic |
|----|-------------|------|
| FR10 | Users can view list of projects they have access to | Epic 4 |
| FR11 | Admins can create new projects with name and description | Epic 4 |
| FR12 | Admins can edit project details | Epic 4 |
| FR13 | Admins can archive projects (soft delete) | Epic 4 |
| FR14 | Users can view all VMs within a project | Epic 4 |

#### VM Request Management (8 FRs)
| ID | Requirement | Epic |
|----|-------------|------|
| FR16 | Users can create a new VM request | Epic 2 |
| FR17 | Users can select a project for the VM request | Epic 2 |
| FR18 | Users can select VM size (S/M/L/XL) with visible specs | Epic 2 |
| FR19 | Users can provide VM name and justification | Epic 2 |
| FR20 | Users can view their submitted requests | Epic 2 |
| FR21 | Users can see real-time status of their requests | Epic 2 |
| FR22 | Users can cancel pending (not yet approved) requests | Epic 2 |
| FR23 | Users can view request history with full timeline | Epic 2 |

#### Approval Workflow (6 FRs)
| ID | Requirement | Epic |
|----|-------------|------|
| FR25 | Admins can view all pending requests in dashboard | Epic 2 |
| FR26 | Admins can view full request details before deciding | Epic 2 |
| FR27 | Admins can approve a request with one click | Epic 2 |
| FR28 | Admins can reject a request with mandatory reason | Epic 2 |
| FR29 | System records who approved/rejected and when | Epic 2 |
| FR30 | System automatically triggers provisioning on approval | Epic 3 |

#### VM Provisioning (7 FRs)
| ID | Requirement | Epic |
|----|-------------|------|
| FR34 | System provisions VM on VMware ESXi via vSphere API | Epic 3 |
| FR35 | System applies VM size specifications (CPU/RAM/Disk) | Epic 3 |
| FR36 | System assigns network configuration to provisioned VM | Epic 3 |
| FR37 | System tracks provisioning progress and updates status | Epic 3 |
| FR38 | System handles provisioning failures gracefully | Epic 3 |
| FR39 | System retries failed provisioning with backoff | Epic 3 |
| FR40 | Users can view provisioned VM details (IP, status) | Epic 3 |

#### Status & Notifications (5 FRs)
| ID | Requirement | Epic |
|----|-------------|------|
| FR44 | Users see real-time status updates without page refresh | Epic 2 |
| FR45 | System sends email notification on request submission | Epic 2 |
| FR46 | System sends email notification on approval/rejection | Epic 2 |
| FR47 | System sends email notification when VM is ready | Epic 3 |
| FR48 | Users see in-app notifications for status changes | Epic 2 |

#### Onboarding & Empty States (2 FRs)
| ID | Requirement | Epic |
|----|-------------|------|
| FR85 | System displays helpful empty states with clear next actions | Epic 2 |
| FR86 | System provides contextual onboarding guidance | Epic 2 |

#### Admin Dashboard (4 FRs)
| ID | Requirement | Epic |
|----|-------------|------|
| FR51 | Admins see count of pending requests on dashboard | Epic 5 |
| FR52 | Admins see list of recent requests across tenant | Epic 5 |
| FR53 | Admins can filter requests by status, project, user | Epic 5 |
| FR54 | Admins can sort requests by date, priority | Epic 5 |

#### Reporting & Audit (6 FRs)
| ID | Requirement | Epic |
|----|-------------|------|
| FR57 | Admins can export request history as CSV | Epic 5 |
| FR58 | System maintains complete audit trail of all actions | Epic 5 |
| FR59 | Admins can view audit log for any request | Epic 5 |
| FR60 | Audit trail includes who, what, when for every change | Epic 5 |
| FR90 | System provides ISO 27001 control mapping in audit reports | Epic 5 |

#### Multi-Tenancy (4 FRs)
| ID | Requirement | Epic |
|----|-------------|------|
| FR64 | Each tenant's data is completely isolated from others | Epic 5 |
| FR65 | Users can only see data within their own tenant | Epic 5 |
| FR66 | Tenant context is enforced at database level (RLS) | Epic 1 |
| FR67 | System rejects requests with missing tenant context | Epic 1 |

#### System Administration (3 FRs)
| ID | Requirement | Epic |
|----|-------------|------|
| FR71 | Admins can configure VMware connection settings | Epic 3 |
| FR72 | Admins can configure email/SMTP settings | Epic 2 |
| FR73 | Admins can view system health status | Epic 5 |

#### Error Handling & Resilience (4 FRs)
| ID | Requirement | Epic |
|----|-------------|------|
| FR77 | System handles VMware API connection failures | Epic 3 |
| FR78 | System implements retry logic for transient VMware errors | Epic 3 |
| FR79 | System handles partial failures and reports context | Epic 3 |
| FR80 | System logs all infrastructure errors with correlation IDs | Epic 1 |

#### Quota Management (3 FRs)
| ID | Requirement | Epic |
|----|-------------|------|
| FR82 | Admins can define resource quotas per tenant | Epic 4 |
| FR83 | Users can view remaining quota capacity before submitting | Epic 4 |
| FR84 | System enforces quotas synchronously | Epic 4 |

#### Capacity Visibility (1 FR)
| ID | Requirement | Epic |
|----|-------------|------|
| FR87 | Admins can view real-time resource utilization dashboard | Epic 4 |

---

## FR Coverage Map

| Epic | Functional Requirements | Count |
|------|------------------------|-------|
| **Epic 1: Foundation** | FR66, FR67, FR80 | 3 |
| **Epic 2: Core Workflow** | FR1, FR2, FR7a, FR16-FR23, FR25-FR29, FR44-FR46, FR48, FR72, FR85, FR86 | 21 |
| **Epic 3: VM Provisioning** | FR30, FR34-FR40, FR47, FR71, FR77-FR79 | 13 |
| **Epic 4: Projects & Quota** | FR10-FR14, FR82-FR84, FR87 | 9 |
| **Epic 5: Compliance & Oversight** | FR51-FR54, FR57-FR60, FR64-FR65, FR73, FR90, NFR-SEC-10 | 13 |
| **Growth (not in MVP)** | FR3-FR7, FR8-FR9, FR15, FR24, FR31-FR33, FR41-FR43, FR49-FR50, FR55-FR56, FR61-FR63, FR68-FR70, FR74-FR76, FR88-FR89 | 28 |
| **Total MVP** | | **59** |

---

## Epic 1: Foundation

**Goal:** Establish the technical foundation for all DVMM features including project structure, event sourcing infrastructure, multi-tenant context, and quality gates.

**User Value:** None directly (technical foundation exception) - but enables ALL subsequent user-facing features.

**FRs Covered:** FR66, FR67, FR80

**Stories:** 11 | **Risk:** Low

### Story 1.1: Project Scaffolding

As a **developer**,
I want a properly configured Kotlin/Spring Boot project structure,
So that I can start implementing domain logic immediately.

**Acceptance Criteria:**

**Given** I clone the repository
**When** I run `./gradlew build`
**Then** the build succeeds with zero errors

**And** the following modules exist:
- `eaf-core` (shared kernel)
- `eaf-eventsourcing` (event store abstractions)
- `eaf-tenant` (multi-tenancy)
- `eaf-auth` (authentication)
- `dvmm-domain` (aggregates, events)
- `dvmm-application` (commands, queries, handlers)
- `dvmm-api` (REST controllers)
- `dvmm-infrastructure` (adapters, projections)

**And** Kotlin 2.2+ with K2 compiler is configured
**And** Spring Boot 3.5+ with WebFlux is configured
**And** Konsist architecture tests scaffold exists
**And** code coverage reporting (Kover) is configured
**And** mutation testing (Pitest) is configured

**Prerequisites:** None

**Technical Notes:**
- See Architecture ADR-001 for module structure
- Use Gradle version catalogs for dependency management
- Configure Kotlin coroutines with reactor-kotlin-extensions

---

### Story 1.2: EAF Core Module

As a **developer**,
I want foundational types for error handling and tracing,
So that all modules use consistent patterns.

**Acceptance Criteria:**

**Given** I import eaf-core
**When** I use `Result<T, E>` for operations
**Then** I can handle Success and Failure cases exhaustively

**And** `DomainError` sealed class exists with:
- `ValidationFailed(field: String, message: String)`
- `ResourceNotFound(type: String, id: String)`
- `InvalidStateTransition(from: String, action: String)`
- `QuotaExceeded(current: Int, max: Int)`
- `InfrastructureError(cause: String)`

**And** `CorrelationId` value class exists
**And** `TenantId` value class exists
**And** `UserId` value class exists
**And** extension functions exist for Result mapping and error handling

**Prerequisites:** Story 1.1

**Technical Notes:**
- See Architecture Section: Error Handling Approach
- Use `@JvmInline value class` for IDs
- Result type should be a sealed class, not Arrow-kt dependency

**Observability Foundation:**
- kotlin-logging (io.github.oshai) wraps SLF4J with Kotlin-idiomatic API
- CorrelationId propagation via MDC (Mapped Diagnostic Context) for request tracing
- **Growth Phase (NFR-OBS-9):** Add `kotlinx-coroutines-slf4j` for automatic MDC propagation across coroutine boundaries. Critical for distributed tracing integrity when using reactive/coroutine-based operations.

---

### Story 1.3: Event Store Setup

As a **developer**,
I want a PostgreSQL-based event store,
So that I can persist domain events durably.

**Acceptance Criteria:**

**Given** an aggregate emits events
**When** I call `eventStore.save(aggregateId, events, expectedVersion)`
**Then** events are persisted to `domain_events` table

**And** `domain_events` table has columns:
- `id` (UUID, PK)
- `aggregate_id` (UUID, indexed)
- `aggregate_type` (VARCHAR)
- `event_type` (VARCHAR)
- `event_data` (JSONB)
- `metadata` (JSONB) with tenant_id, user_id, correlation_id, timestamp
- `version` (BIGINT)
- `created_at` (TIMESTAMPTZ)

**And** optimistic locking prevents concurrent writes to same aggregate
**And** events are immutable (no UPDATE, no DELETE allowed)
**And** Flyway migration V001 creates the schema

**Prerequisites:** Story 1.1, Story 1.2

**Technical Notes:**
- See Architecture Section: Event Store Setup
- Use Jackson for JSONB serialization
- Index on (aggregate_id, version) for efficient loading

---

### Story 1.4: Aggregate Base Pattern

As a **developer**,
I want a base class for Event Sourced aggregates,
So that I can implement domain logic consistently.

**Acceptance Criteria:**

**Given** I extend `AggregateRoot<TId, TEvent>`
**When** I call `aggregate.applyEvent(event)`
**Then** the event is added to `uncommittedEvents` list

**And** `reconstitute(id, events)` rebuilds aggregate state from event history
**And** `version` is incremented per applied event
**And** snapshot support exists with configurable threshold (default: 100 events)
**And** `AggregateSnapshot` data class stores serialized state + version

**Prerequisites:** Story 1.2, Story 1.3

**Technical Notes:**
- See Architecture Section: Aggregate Pattern
- Use sealed class for events per aggregate type
- Snapshot stored in separate `aggregate_snapshots` table

---

### Story 1.5: Tenant Context Module

As a **developer**,
I want tenant context automatically propagated through coroutines,
So that all operations are tenant-scoped.

**Acceptance Criteria:**

**Given** a request with valid JWT containing `tenant_id` claim
**When** the request is processed
**Then** `TenantContext.current()` returns the tenant ID

**And** `TenantContextElement` implements `CoroutineContext.Element`
**And** `TenantContextWebFilter` extracts tenant from JWT on every request
**And** missing tenant context throws `TenantContextMissingException`
**And** fail-closed semantics: no tenant = request rejected with HTTP 403

**Prerequisites:** Story 1.2

**Technical Notes:**
- See Architecture Section: Tenant Context Pattern
- NOT ThreadLocal - we use Kotlin coroutines
- Use `withContext(TenantContextElement(tenantId))` for propagation

**Testability Concern TC-001 - Additional ACs (CRITICAL):**

**Scenario: Tenant context survives dispatcher switch**
**Given** a coroutine running with tenant "A"
**When** the coroutine switches to Dispatchers.IO
**Then** the tenant context is still "A"

**Scenario: Tenant context survives async boundary**
**Given** a coroutine running with tenant "A"
**When** an `async { }` block is launched
**Then** the child coroutine has tenant context "A"

**Scenario: Tenant context is isolated under concurrent load**
**Given** 100 parallel coroutines (50 for Tenant A, 50 for Tenant B)
**When** all coroutines execute concurrently with dispatcher switches
**Then** coroutine A always sees tenant "A"
**And** coroutine B always sees tenant "B"
**And** NO cross-contamination occurs (stress test in CI)

---

### Story 1.6: PostgreSQL RLS Policies

As a **developer**,
I want Row-Level Security enforced at database level,
So that tenant isolation is guaranteed even if application code has bugs.

**Acceptance Criteria:**

**Given** RLS is enabled on tenant-scoped tables
**When** a query runs without proper tenant context
**Then** zero rows are returned (fail-closed)

**And** `app.tenant_id` session variable is set per connection
**And** RLS policy: `tenant_id = current_setting('app.tenant_id')::uuid`
**And** connection pool (HikariCP) sets tenant context on checkout
**And** Flyway migration V002 creates RLS policies
**And** superuser bypass is disabled for application role

**Prerequisites:** Story 1.5

**Technical Notes:**
- See Architecture ADR-002
- Test with multi-tenant data to verify complete isolation
- FR66, FR67 satisfied by this story

**Testability Concern TC-002 - Additional ACs (CRITICAL):**

**Scenario: RLS prevents cross-tenant data access**
**Given** tenant "A" has created a VM request "req-A"
**And** tenant "B" exists in the system
**When** tenant "B" queries all VM requests
**Then** the result set does NOT contain "req-A"
**And** a direct SQL query as tenant "B" returns 0 rows for "req-A"

**Scenario: Test database connection enforces tenant context**
**Given** a test is running
**When** the test attempts a database query without tenant context
**Then** the connection MUST throw `IllegalStateException`
**And** the error message indicates "NO TENANT CONTEXT IN TEST"
**And** queries without tenant context MUST fail (not return all data)

**Technical Note - eaf-testing Module:**
```kotlin
// RlsEnforcingDataSource - MUST be implemented
class RlsEnforcingDataSource(delegate: DataSource) : DataSource {
    override fun getConnection(): Connection {
        val tenant = TenantTestContext.current()
            ?: throw IllegalStateException("NO TENANT CONTEXT IN TEST!")
        return delegate.connection.also { conn ->
            // NOTE: tenant.id is UUID from test context - SET doesn't support parameterized queries
            conn.createStatement().execute(
                "SET app.tenant_id = '${tenant.id}'"
            )
        }
    }
}
```

---

### Story 1.7: Keycloak Integration

As a **developer**,
I want Keycloak OIDC authentication configured,
So that users can authenticate securely.

**Acceptance Criteria:**

**Given** a valid Keycloak JWT token
**When** a request includes `Authorization: Bearer <token>`
**Then** the request is authenticated and user context is available

**And** invalid/expired tokens return HTTP 401
**And** JWT claims are extracted: `sub`, `tenant_id`, `roles`, `email`
**And** `SecurityConfig` uses Spring Security OAuth2 Resource Server
**And** token refresh is handled by frontend (architecture decision)
**And** CORS is configured for frontend origin

**Prerequisites:** Story 1.5

**Technical Notes:**
- Keycloak realm: `dvmm`
- Client: `dvmm-api` (confidential) and `dvmm-web` (public)
- Custom claim mapper for tenant_id in Keycloak
- FR7a (token refresh) handled client-side

---

### Story 1.8: jOOQ Projection Base

As a **developer**,
I want jOOQ configured for type-safe read queries,
So that I can build efficient read projections.

**Acceptance Criteria:**

**Given** jOOQ code generation runs during build
**When** I query using generated record classes
**Then** queries are type-safe and compile-time checked

**And** jOOQ Gradle plugin generates from Flyway migrations
**And** generated code goes to `build/generated-sources/jooq`
**And** `BaseProjectionRepository` provides common query patterns
**And** tenant_id filter is automatically applied to all queries
**And** pagination helpers exist: `PageRequest`, `PagedResponse<T>`

**Prerequisites:** Story 1.3

**Technical Notes:**
- jOOQ 3.20+ with Kotlin extensions
- See Architecture Section: Query Pattern
- Use DSLContext with R2DBC for reactive queries

**Testability Concern TC-004 - Additional AC:**

**Scenario: Tests can await projection updates (Eventual Consistency)**
**Given** a command has been processed and events persisted
**When** the test calls `awaitProjection(aggregateId, timeout = 5.seconds)`
**Then** the test blocks until the projection is updated
**And** returns the projected entity when available
**And** throws `TimeoutException` after 5 seconds if projection never appears

**Technical Note - eaf-testing Module:**
```kotlin
// awaitProjection helper - MUST be implemented
suspend fun <T> awaitProjection(
    aggregateId: UUID,
    repository: ProjectionRepository<T>,
    timeout: Duration = 5.seconds
): T = withTimeout(timeout) {
    while (true) {
        repository.findById(aggregateId)?.let { return@withTimeout it }
        delay(50.milliseconds)
    }
}
```

---

### Story 1.9: Testcontainers Setup

As a **developer**,
I want Testcontainers for integration tests,
So that tests run against real infrastructure.

**Acceptance Criteria:**

**Given** I run integration tests
**When** PostgreSQL container is needed
**Then** Testcontainers starts PostgreSQL 16 automatically

**And** Keycloak container is available for auth tests
**And** containers are reused across test classes (singleton pattern)
**And** test fixtures module (`src/testFixtures`) provides:
  - `TestTenantFixture` - creates test tenants
  - `TestUserFixture` - creates users with valid JWTs
  - `TestDataFixture` - seeds common test data

**Prerequisites:** Story 1.1

**Technical Notes:**
- Use `@Testcontainers` and `@Container` annotations
- Singleton containers via companion object
- See Architecture: Test Design System

**Testability Concern TC-003 - Additional AC:**

**Scenario: Event store isolation is configurable**
**Given** a test class annotated with `@IsolatedEventStore`
**When** the test runs
**Then** events from previous tests are NOT visible
**And** the isolation strategy can be configured:
  - `TRUNCATE` (default, fast ~5ms, for unit/integration)
  - `SCHEMA_PER_TEST` (slower ~50ms, for E2E with parallelization)

**And** the total test suite duration stays under 15 minutes (NFR-MAINT-11)

**Technical Note - eaf-testing Module:**
```kotlin
// @IsolatedEventStore annotation - MUST be implemented
@Target(AnnotationTarget.CLASS)
@ExtendWith(EventStoreIsolationExtension::class)
annotation class IsolatedEventStore(
    val strategy: IsolationStrategy = IsolationStrategy.TRUNCATE
)

enum class IsolationStrategy { TRUNCATE, SCHEMA_PER_TEST }
```

---

### Story 1.10: VCSIM Integration

As a **developer**,
I want VMware vCenter Simulator for integration tests,
So that I can test VMware operations without real infrastructure.

**Acceptance Criteria:**

**Given** I run VMware integration tests
**When** VCSIM container starts
**Then** it provides vSphere API compatible endpoints

**And** `VcsimTestFixture` provides helpers for:
  - Creating test VMs
  - Creating test networks
  - Creating test datastores
  - Simulating provisioning operations

**And** connection parameters are injected via test properties
**And** VCSIM state resets between test classes

**Prerequisites:** Story 1.9

**Technical Notes:**
- Docker image: `vmware/vcsim:latest`
- See Architecture: Test Design TC-001
- VCSIM provides /sdk endpoint compatible with govmomi/pyvmomi

**Spring Boot Integration Pattern:**
When Epic 3 introduces Spring Boot tests that require vSphere connection properties, add a `@DynamicPropertySource` example test to `eaf-testing`:
```kotlin
@SpringBootTest
@VcsimTest
class VcsimSpringIntegrationTest {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun vcsimProperties(registry: DynamicPropertyRegistry) {
            registry.add("vsphere.url") { TestContainers.vcsim.getSdkUrl() }
            registry.add("vsphere.username") { TestContainers.vcsim.getUsername() }
            registry.add("vsphere.password") { TestContainers.vcsim.getPassword() }
            registry.add("vsphere.insecure") { "true" }
        }
    }
}
```
This pattern enables automatic property injection for Spring Boot integration tests.

---

### Story 1.11: CI/CD Quality Gates

As a **developer**,
I want CI/CD pipeline with enforced quality gates,
So that code quality standards are maintained automatically.

**Acceptance Criteria:**

**Given** I push code or open a PR
**When** GitHub Actions workflow runs
**Then** the pipeline executes in order:
  1. Build (Gradle)
  2. Unit tests
  3. Integration tests (Testcontainers)
  4. Code coverage check (koverVerify ≥70%)
  5. Mutation testing (Pitest ≥70%)
  6. Architecture tests (Konsist)

**And** pipeline FAILS if ANY gate fails (no exceptions)
**And** coverage report is published as workflow artifact
**And** PR merge requires passing pipeline ("No Broken Windows")
**And** main branch is protected

**Prerequisites:** Story 1.9

**Technical Notes:**
- Workflow file: `.github/workflows/ci.yml`
- See PRD: Implementation Quality Gates
- FR80 (correlation ID logging) verified in tests

---

## Epic 2: Core Workflow

**Goal:** Implement the complete "Request → Approve → Notify" workflow that demonstrates DVMM's core value proposition. This is the **Tracer Bullet** - users can request VMs, admins can approve/reject, and everyone gets notified.

**User Value:** "I can create a VM request and see exactly what happens with it" - Complete transparency from request to decision.

**FRs Covered:** FR1, FR2, FR7a, FR16-FR23, FR25-FR29, FR44-FR46, FR48, FR72, FR85, FR86

**Stories:** 13 | **Risk:** High (first user-facing features)

**Tracer Bullet Note:** VM "provisioning" in Epic 2 creates a Docker container as mock. Real VMware integration comes in Epic 3.

### Story 2.1: Keycloak Login Flow

As an **end user**,
I want to log in via Keycloak SSO,
So that I can access DVMM securely with my company credentials.

**Acceptance Criteria:**

**Given** I navigate to DVMM root URL (`/`)
**When** I am not authenticated
**Then** I am redirected to Keycloak login page

**And** after successful Keycloak login, I am redirected back to DVMM dashboard
**And** my session is maintained via an httpOnly cookie (Secure, SameSite=Lax)
**And** my name and tenant are displayed in the header
**And** logout button is visible and functional
**And** clicking logout redirects to Keycloak logout and clears session

**Given** my JWT token expires during a session
**When** I perform any action
**Then** the frontend transparently refreshes the token
**And** if refresh fails, I am redirected to login

**Prerequisites:** Story 1.7 (Keycloak Integration)

**FRs Satisfied:** FR1, FR2, FR7a

**Technical Notes:**
- Frontend: React with `react-oidc-context` or similar
- Backend validates JWT on every request (Story 1.7)
- See UX Design: Login screen follows Tech Teal theme

**⚠️ Coverage Restoration Required (2 modules):**

**Module 1: `eaf-auth-keycloak`**
- Has temporarily disabled coverage verification (see `eaf/eaf-auth-keycloak/build.gradle.kts`)
- **Action Required:**
  1. Add Keycloak Testcontainer integration tests for `KeycloakIdentityProvider`
  2. Achieve ≥70% test coverage for `eaf-auth-keycloak` module
  3. Remove the `tasks.named("koverVerify") { enabled = false }` block in `eaf/eaf-auth-keycloak/build.gradle.kts`
- Reason: Story 1.7 created the implementation, but testing requires Keycloak Testcontainer setup

**Module 2: `dvmm-api`**
- Has temporarily disabled coverage verification (see `dvmm/dvmm-api/build.gradle.kts`)
- **Action Required:**
  1. Add SecurityConfig integration tests verifying:
     - Unauthenticated /api/** requests return 401
     - Unauthenticated /actuator/health requests are allowed
     - Authenticated requests with valid JWT succeed
  2. Achieve ≥70% test coverage for `dvmm-api` module
  3. Remove the `tasks.named("koverVerify") { enabled = false }` block in `dvmm/dvmm-api/build.gradle.kts`
- Reason: SecurityConfig.securityWebFilterChain() requires Spring Security WebFlux integration testing with Keycloak

**Frontend Tracer Bullet Note:**
Story 2.1 is the **Frontend Tracer Bullet** - the first user-facing code that validates the complete UI stack (React + shadcn + Keycloak integration).

**Pair Programming Recommended:**
This story should be implemented with **Pair Programming** (Backend + Frontend Developer) because:
- Backend: Auth-Flow, Token-Validation, Tenant-Context-API
- Frontend: Keycloak-JS, Token-Storage (httpOnly cookie), Protected Routes
- CSRF: Backend validates X-CSRF-Token header on state-changing requests

**Token Storage Decision (RESOLVED):** httpOnly cookie with `Secure` and `SameSite=Lax` flags, plus explicit CSRF token validation. SameSite=Lax (not Strict) is required to support OIDC redirect flows from the IdP. The CSRF token is issued by the API and rotated per session.

---

### Story 2.2: End User Dashboard Layout

As an **end user**,
I want a clean dashboard showing my VM requests,
So that I have a single place to see all my activity.

**Acceptance Criteria:**

**Given** I am logged in as a regular user
**When** I view the dashboard
**Then** I see:
  - Header with logo, my name, tenant name, logout button
  - Primary CTA button: "Request New VM" (Tech Teal accent)
  - "My Requests" section with request list
  - Quick stats: Pending / Approved / Provisioned counts

**And** the layout follows shadcn-admin-kit patterns
**And** the design matches UX Design Specification (Tech Teal #0D9488)
**And** the dashboard is responsive (mobile-friendly)

**Prerequisites:** Story 2.1

**FRs Satisfied:** FR85 (empty states)

**Technical Notes:**
- Use shadcn/ui components with Tailwind
- See UX Design: Dashboard Layout section
- Stats come from Query endpoints (Story 2.7)

---

### Story 2.3: Empty States & Onboarding

As a **new user**,
I want helpful guidance when I have no data,
So that I understand how to get started.

**Acceptance Criteria:**

**Given** I am a new user with zero VM requests
**When** I view the dashboard
**Then** I see an empty state illustration with:
  - Friendly message: "Noch keine VMs angefordert"
  - Clear CTA: "Erste VM anfordern" button
  - Brief explanation of the workflow

**And** first-time tooltip hints appear on key UI elements
**And** tooltips can be dismissed permanently
**And** empty states exist for: My Requests, Admin Queue

**Prerequisites:** Story 2.2

**FRs Satisfied:** FR85, FR86

**Technical Notes:**
- Use localStorage to track "has seen onboarding"
- See UX Design: Empty States section
- Illustrations from shadcn-admin-kit or simple icons

---

### Story 2.4: VM Request Form - Basic Fields

As an **end user**,
I want to enter basic information for my VM request,
So that I can specify what I need.

**Acceptance Criteria:**

**Given** I click "Neue VM anfordern"
**When** the request form opens
**Then** I see form fields for:
  - VM Name (required, alphanumeric + hyphens, 3-63 chars)
  - Project dropdown (populated from my accessible projects)
  - Justification/Description (required, min 10 chars)

**And** validation errors appear inline as I type
**And** form state persists if I navigate away accidentally (warn on leave)
**And** project dropdown shows "No Project?" link to request access

**Given** I select a project
**When** the project is loaded
**Then** I see the project's remaining quota displayed

**Prerequisites:** Story 2.2

**FRs Satisfied:** FR17, FR19

**Technical Notes:**
- Use React Hook Form with Zod validation
- Project list from ProjectQueryService
- See UX Design: VM Request Form mockup

---

### Story 2.5: VM Request Form - Size Selector

As an **end user**,
I want to select a VM size with clear specifications,
So that I can choose the right resources for my needs.

**Acceptance Criteria:**

**Given** I am on the VM request form
**When** I view the size selector
**Then** I see visual cards for each size:

| Size | vCPU | RAM | Disk | Monthly Estimate |
|------|------|-----|------|------------------|
| S | 2 | 4 GB | 50 GB | ~€25 |
| M | 4 | 8 GB | 100 GB | ~€50 |
| L | 8 | 16 GB | 200 GB | ~€100 |
| XL | 16 | 32 GB | 500 GB | ~€200 |

**And** selecting a size highlights the card
**And** selected size shows against remaining quota
**And** if quota would be exceeded, the size is disabled with explanation
**And** default selection is "M" (most common)

**Prerequisites:** Story 2.4

**FRs Satisfied:** FR18, FR83 (quota visibility)

**Technical Notes:**
- Sizes defined in configuration (not hardcoded)
- See UX Design: Size Selector component
- Quota check is display-only here, enforced on submit

---

### Story 2.6: VM Request Form - Submit Command

As an **end user**,
I want to submit my VM request,
So that it enters the approval workflow.

**Acceptance Criteria:**

**Given** I have filled all required fields
**When** I click "Request absenden"
**Then** a `CreateVmRequestCommand` is dispatched
**And** the command handler creates `VmRequestAggregate`
**And** `VmRequestCreated` event is persisted
**And** I see success toast: "Request eingereicht!"
**And** I am redirected to request detail view

**Given** the request violates quota
**When** I submit
**Then** the command fails with `QuotaExceeded` error
**And** I see inline error explaining the quota issue
**And** no event is persisted (transactional)

**Given** validation fails
**When** I submit
**Then** I see specific validation errors per field
**And** form stays open for correction

**Prerequisites:** Story 2.4, Story 2.5, Story 1.4 (Aggregate Base)

**FRs Satisfied:** FR16, FR45 (triggers email)

**Technical Notes:**
- Command: `CreateVmRequestCommand(tenantId, userId, projectId, name, size, justification)`
- See Architecture: Command Pattern implementation
- Event triggers email notification (Story 2.12)

---

### Story 2.7: My Requests List & Cancel

As an **end user**,
I want to see all my VM requests and cancel pending ones,
So that I can track and manage my requests.

**Acceptance Criteria:**

**Given** I am on the dashboard
**When** I view "My Requests"
**Then** I see a table/list with columns:
  - VM Name
  - Project
  - Size
  - Status (badge: Pending/Approved/Rejected/Provisioning/Ready)
  - Created date
  - Actions

**And** requests are sorted by created date (newest first)
**And** pagination is available (10/25/50 per page)
**And** I can click a row to see details

**Given** a request is in "Pending" status
**When** I click "Cancel" action
**Then** confirmation dialog appears
**And** on confirm, `CancelVmRequestCommand` is dispatched
**And** `VmRequestCancelled` event is persisted
**And** status changes to "Cancelled"

**Given** a request is not Pending (Approved, Rejected, etc.)
**When** I view the actions
**Then** "Cancel" is not available

**Prerequisites:** Story 1.8 (jOOQ Projections), Story 2.6

**FRs Satisfied:** FR20, FR22, FR23

**Technical Notes:**
- Query endpoint: `GET /api/requests/my`
- Uses jOOQ projection with tenant filter
- Cancel command idempotent (no-op if already cancelled)

---

### Story 2.8: Request Status Timeline

As an **end user**,
I want to see the complete history of my request,
So that I know exactly what happened and when.

**Acceptance Criteria:**

**Given** I view a request detail page
**When** I look at the timeline section
**Then** I see chronological events:
  - "Request created" - timestamp, my name
  - "Approved" / "Rejected" - timestamp, admin name, (rejection reason if rejected)
  - "Provisioning started" - timestamp
  - "VM ready" - timestamp, VM details

**And** the timeline updates in real-time (SSE or polling)
**And** each event shows relative time ("5 minutes ago")
**And** clicking an event shows full details

**Given** my request status changes
**When** I am viewing the request
**Then** the timeline updates without page refresh

**Prerequisites:** Story 2.7

**FRs Satisfied:** FR21, FR44 (real-time updates)

**Technical Notes:**
- Query: `GET /api/requests/{id}/timeline`
- Consider SSE endpoint for real-time: `GET /api/requests/{id}/events`
- Events reconstructed from event store or dedicated projection

**Graceful Degradation AC (VMware Unavailable):**

**Scenario: VMware temporarily unavailable during provisioning**
**Given** a VM request has been approved
**And** VMware vCenter is temporarily unavailable
**When** the system attempts provisioning
**Then** the request status shows "Provisioning Queued"
**And** a timeline entry shows "Waiting for VMware - you will be notified"
**And** user receives email when VM is successfully provisioned (after VMware recovers)

**Technical Note:**
This scenario is primarily handled in Epic 3 (Story 3.6), but the UI must display the queued state properly here.

---

### Story 2.9: Admin Approval Queue

As an **admin**,
I want to see all pending requests in my tenant,
So that I can efficiently process approvals.

**Acceptance Criteria:**

**Given** I am logged in as an admin
**When** I view the admin dashboard
**Then** I see "Offene Requests" section with:
  - Count badge showing number of pending requests
  - List of pending requests with: Requester, VM Name, Project, Size, Age, Actions
  - Sorting by age (oldest first by default)
  - Filtering by project

**And** clicking a request opens detail view
**And** bulk actions are NOT in MVP (single approval only)
**And** requests from all users in my tenant are visible
**And** requests older than 48h are highlighted as "Waiting long"

**Prerequisites:** Story 2.7, Story 1.7 (role-based access)

**FRs Satisfied:** FR25

**Technical Notes:**
- Admin role check in SecurityConfig
- Query: `GET /api/admin/requests/pending`
- See UX Design: Admin Dashboard mockup

---

### Story 2.9a: E2E Auth Setup & Keycloak Role Mapping Tests

As a **developer**,
I want E2E authentication setup and Keycloak role mapping integration tests,
So that I can verify authentication works correctly in E2E and backend tests.

**Acceptance Criteria:**

**Given** a Playwright E2E test suite
**When** the setup project runs
**Then** it authenticates users via Keycloak and saves session to `storageState`

**And** integration tests verify `realm_access.roles` maps to `ROLE_*` authorities
**And** `@PreAuthorize("hasRole('admin')")` is tested (admin=200, user=403)
**And** different tenant users have different subject IDs

**Prerequisites:** Story 2.1, Story 2.9

**FRs Satisfied:** Supports FR1 (Keycloak SSO), FR25 (Admin role verification)

**Technical Notes:**
- E2E: Playwright `storageState` for session persistence
- Backend: `KeycloakRoleMappingIntegrationTest` with real Keycloak Testcontainer
- See: GitHub Issue #58, PR #59

---

### Story 2.10: Request Detail View (Admin)

As an **admin**,
I want to see complete request details before deciding,
So that I can make informed approval decisions.

**Acceptance Criteria:**

**Given** I click on a pending request
**When** the detail view opens
**Then** I see:
  - Requester: Name, Email, Role
  - Request: VM Name, Size (with specs), Project, Justification
  - Project context: Current usage, Quota remaining
  - Timeline: When requested, any previous actions
  - Approve / Reject buttons (prominent)

**And** the justification is fully visible (not truncated)
**And** I can see requester's other recent requests (context)
**And** back button returns to queue

**Prerequisites:** Story 2.9

**FRs Satisfied:** FR26

**Technical Notes:**
- Query: `GET /api/admin/requests/{id}`
- Include aggregated requester history
- See UX Design: Request Detail screen

---

### Story 2.11: Approve/Reject Actions

As an **admin**,
I want to approve or reject requests with one click,
So that I can process requests efficiently.

**Acceptance Criteria:**

**Given** I am viewing a pending request
**When** I click "Approve"
**Then** confirmation dialog appears: "Approve request?"
**And** on confirm, `ApproveVmRequestCommand` is dispatched
**And** `VmRequestApproved` event is persisted with my userId and timestamp
**And** success toast: "Request approved!"
**And** I return to queue (request removed from pending)

**Given** I am viewing a pending request
**When** I click "Reject"
**Then** rejection dialog appears with mandatory reason field
**And** reason must be at least 10 characters
**And** on submit, `RejectVmRequestCommand` is dispatched
**And** `VmRequestRejected` event is persisted with reason, userId, timestamp
**And** success toast: "Request rejected"
**And** I return to queue

**Given** the request is already approved/rejected (concurrent admin)
**When** I try to approve/reject
**Then** I see error: "Request already processed"
**And** detail view refreshes showing current state

**Prerequisites:** Story 2.10, Story 1.4 (Aggregate)

**FRs Satisfied:** FR27, FR28, FR29

**Technical Notes:**
- Commands dispatch domain events
- Optimistic locking prevents double-processing
- Events trigger email notifications (Story 2.12)
- Approval triggers provisioning in Epic 3 (mock in Epic 2)

---

### Story 2.12: Email Notifications

As a **user** or **admin**,
I want email notifications for request status changes,
So that I stay informed without checking the portal.

**Acceptance Criteria:**

**Given** a user submits a VM request
**When** `VmRequestCreated` event is persisted
**Then** email is sent to tenant admins:
  - Subject: "[DVMM] New VM Request: {vmName}"
  - Body: Requester, VM details, link to approve

**Given** an admin approves a request
**When** `VmRequestApproved` event is persisted
**Then** email is sent to requester:
  - Subject: "[DVMM] Request Approved: {vmName}"
  - Body: Approval confirmation, next steps

**Given** an admin rejects a request
**When** `VmRequestRejected` event is persisted
**Then** email is sent to requester:
  - Subject: "[DVMM] Request Rejected: {vmName}"
  - Body: Rejection reason, contact info

**And** SMTP settings are configurable per tenant
**And** email sending is async (doesn't block request flow)
**And** failed emails are logged but don't fail the request
**And** emails use HTML templates with tenant branding

**Prerequisites:** Story 2.6, Story 2.11

**FRs Satisfied:** FR45, FR46, FR72

**Technical Notes:**
- Event handler listens to relevant events
- Use Spring Mail with async execution
- See FR72: Admin SMTP configuration (basic for MVP)
- Templates in `resources/templates/email/`

---

## Epic 3: VM Provisioning

**Goal:** Implement actual VM provisioning on VMware ESXi via vSphere API. This transforms DVMM from a workflow tool into a real infrastructure automation system.

**User Value:** "My VM is actually created - not just a ticket" - From approved request to running VM without manual intervention.

**FRs Covered:** FR30, FR34-FR40, FR47, FR71, FR77-FR79

**Stories:** 10 | **Risk:** **Critical** (VMware API complexity, infrastructure dependency)

**Risk Mitigation:**
- VCSIM (vCenter Simulator) for all integration tests (Story 1.10)
- Idempotent provisioning with correlation IDs
- Circuit breaker pattern for vSphere API calls
- Comprehensive retry logic with exponential backoff

### Story 3.1: VMware Connection Configuration

As a **tenant admin**,
I want to configure VMware vCenter connection settings,
So that DVMM can provision VMs in my infrastructure.

**Acceptance Criteria:**

**Given** I am a tenant admin
**When** I navigate to Settings → VMware Configuration
**Then** I see form fields for:
  - vCenter URL (https://vcenter.example.com/sdk)
  - Username (service account)
  - Password (masked, encrypted at rest)
  - Datacenter name
  - Cluster name
  - Datastore name
  - Network name (for default network)

**And** "Test Connection" button validates connectivity
**And** success shows: "✓ Connected to vCenter 8.0, Cluster: {name}"
**And** failure shows specific error: "Connection refused" / "Authentication failed" / "Datacenter not found"
**And** configuration is stored encrypted (AES-256)
**And** configuration is tenant-isolated (RLS)

**Given** no VMware configuration exists
**When** a user tries to submit a VM request
**Then** they see message: "VMware not configured - contact Admin"

**Prerequisites:** Story 1.6 (RLS), Story 2.1 (Auth)

**FRs Satisfied:** FR71

**Technical Notes:**
- Store credentials encrypted using Spring Security Crypto
- Use official vSphere Automation SDK (yavijava deprecated - see Story 3.1.1)
- Connection pooling for vSphere sessions
- See Architecture: Infrastructure Adapter pattern

**SDK Migration Note (Story 3.1.1):**
Story 3.1 was originally implemented using yavijava. Story 3.1.1 migrates to the official VMware SDK. See Story 3.1.1 for details.

**VMware Tracer Bullet Note:**
Story 3.1 is the **VMware Tracer Bullet** - validates the complete VMware integration stack.

**CRITICAL - Adapter Abstraction (Two-Phase Development):**
The `VspherePort` interface MUST be implemented so that VCSIM and real vCenter are swappable via Spring Profile (`vsphere.mode=vcsim|real`). NO conditional logic in Service Layer.

```kotlin
// CORRECT: Clean abstraction
interface VspherePort {
    suspend fun createVm(spec: VmSpec): Result<VmId, ProvisioningError>
    suspend fun getVm(vmId: VmId): Result<VmInfo, NotFoundError>
}

class VcsimAdapter : VspherePort { /* VCSIM implementation */ }
class VcenterAdapter : VspherePort { /* Real vCenter implementation */ }

// WRONG: Do NOT do this!
class VmProvisioningService {
    fun provision(request: VmRequest) {
        if (isVcsim) { ... } else { ... }  // ❌ NO!
    }
}
```

**Phase 1 (VCSIM):** Stories 3.1-3.6 - Development with VCSIM
**Phase 2 (Real vCenter):** Stories 3.7-3.9 - Validation with real vCenter (when available)

---

### Story 3.1.1: Migrate to Official vSphere SDK

As a **platform maintainer**,
I want to replace yavijava with the official VMware vSphere SDK,
So that DVMM uses a supported, maintained SDK for VMware integration with vSphere 7.x/8.x compatibility.

**Acceptance Criteria:**

**Given** the existing `VcenterAdapter` uses yavijava
**When** I complete this story
**Then** `VcenterAdapter` uses the official **VCF SDK (Maven Central)**
**And** yavijava dependencies are completely removed
**And** Unified Authentication pattern is implemented with **session caching/pooling**
**And** VMware integration tests use `VcsimAdapter` mock (VCF SDK port 443 limitation);
    `VcenterAdapter` validation deferred to Story 3.9 (real vCenter contract tests)

**Prerequisites:** Story 3.1 (VMware Connection Configuration)

**FRs Satisfied:** Technical debt remediation (no new FRs)

**Technical Notes:**
- **VCF SDK Java 9.0.0.0** (Maven Central) - SELECTED
- vSphere Automation SDK 8.0.3.0 (Requires local install - AVOIDED)
- All SDK calls wrapped with `withContext(Dispatchers.IO)`
- New `VcenterAdapterIntegrationTest` required (using `VcsimTestFixture`)

---

### Story 3.2: vSphere API Client

As a **developer**,
I want a robust vSphere API client,
So that I can interact with VMware infrastructure reliably.

**Acceptance Criteria:**

**Given** a VMware configuration exists
**When** I use `VsphereClient.connect(config)`
**Then** a session is established to vCenter

**And** `VsphereClient` provides operations:
  - `listDatacenters(): List<Datacenter>`
  - `listClusters(datacenter): List<Cluster>`
  - `listDatastores(cluster): List<Datastore>`
  - `listNetworks(datacenter): List<Network>`
  - `listResourcePools(cluster): List<ResourcePool>`
  - `createVm(spec: VmSpec): Result<VmId, ProvisioningError>`
  - `getVm(vmId): Result<VmInfo, NotFoundError>`
  - `deleteVm(vmId): Result<Unit, DeletionError>`

**And** all operations have configurable timeout (default: 60s)
**And** connection reuse with session keepalive
**And** all API calls logged with correlation ID
**And** circuit breaker trips after 5 consecutive failures

**Prerequisites:** Story 3.1, Story 1.2 (EAF Core)

**FRs Satisfied:** FR34 (partial)

**Technical Notes:**
- Use official vSphere Automation SDK (after Story 3.1.1 migration)
- Wrap in Kotlin coroutines for non-blocking (`withContext(Dispatchers.IO)`)
- See Architecture: Adapter Pattern for Infrastructure
- Integration tests use VCSIM (Story 1.10)

**VCSIM Enhancement Required:**
Story 1.10's `VcsimTestFixture` currently simulates moRefs locally (counter-based). When implementing Story 3.2, extend `VcsimTestFixture` to make actual VCSIM SOAP API calls via the `/sdk` endpoint for realistic testing of:
- `createVm()` → actual CloneVM_Task SOAP call
- `getVm()` → actual RetrieveProperties SOAP call
- `deleteVm()` → actual Destroy_Task SOAP call

This ensures VCSIM tests validate the same code paths as real vCenter integration.

---

### Story 3.3: Provisioning Trigger on Approval

As a **system**,
I want to automatically start VM provisioning when a request is approved,
So that no manual intervention is needed after approval.

**Acceptance Criteria:**

**Given** an admin approves a VM request
**When** `VmRequestApproved` event is persisted
**Then** a `ProvisionVmCommand` is automatically dispatched

**And** the command contains:
  - requestId (correlation)
  - tenantId
  - vmSpec (name, size, project, network)

**And** provisioning starts within 5 seconds of approval
**And** request status changes to "Provisioning"
**And** timeline event added: "Provisioning started"
**And** if VMware config missing, error event added: "VMware not configured"

**Prerequisites:** Story 2.11 (Approve Action), Story 3.2 (vSphere Client)

**FRs Satisfied:** FR30

**Technical Notes:**
- Event handler triggers Saga/Process Manager
- Use Spring ApplicationEventPublisher or direct command dispatch
- Idempotent: duplicate events don't create duplicate VMs

---

### Story 3.4: VM Provisioning Execution

As a **system**,
I want to create VMs on VMware with correct specifications,
So that users get exactly what they requested.

**Acceptance Criteria:**

**Given** a `ProvisionVmCommand` is received
**When** the provisioning process executes
**Then** a VM is created on VMware with:
  - Name: `{projectPrefix}-{requestedName}` (tenant-safe)
  - CPU: as per size specification
  - RAM: as per size specification
  - Disk: as per size specification
  - Network: tenant default or specified
  - Folder: organized by tenant/project

**And** the VM is created from template (not from scratch)
**And** template selection based on OS choice (Linux default for MVP)
**And** Guest customization applies hostname
**And** VM is powered on after creation
**And** provisioning waits for VMware Tools ready (timeout: 5 min)

**Given** provisioning succeeds
**When** VM is ready
**Then** `VmProvisioned` event is persisted with:
  - vmId (VMware MoRef)
  - ipAddress
  - hostname
  - provisionedAt timestamp

**Prerequisites:** Story 3.3, Story 3.2

**FRs Satisfied:** FR34, FR35, FR36, FR37

**Technical Notes:**
- Use CloneVM_Task for template-based provisioning
- See VMware vSphere API: VirtualMachine.Clone
- Correlation ID in VM annotation for traceability
- Timeout handling with partial cleanup

---

### Story 3.5: Provisioning Progress Tracking

As an **end user**,
I want to see provisioning progress,
So that I know my VM is being created.

**Acceptance Criteria:**

**Given** my request is in "Provisioning" status
**When** I view the request detail
**Then** I see progress stages:
  - ○ Created (queued)
  - ● Cloning from template... (in progress)
  - ○ Apply configuration
  - ○ Start VM
  - ○ Wait for network
  - ○ Ready

**And** progress updates without page refresh (SSE/polling)
**And** each stage shows timestamp when completed
**And** estimated remaining time shown (based on average)
**And** current stage animated (spinner)

**Given** provisioning takes longer than expected
**When** 10 minutes pass
**Then** status shows: "Provisioning is taking longer than usual..."

**Prerequisites:** Story 3.4, Story 2.8 (Timeline)

**FRs Satisfied:** FR37

**Technical Notes:**
- Store progress in dedicated projection table
- Update progress on VMware task callbacks
- Stages map to VMware clone task states
- See UX Design: Progress component

---

### Story 3.6: Provisioning Error Handling

As a **system**,
I want to handle provisioning failures gracefully,
So that users understand what went wrong.

**Acceptance Criteria:**

**Given** VMware API returns an error during provisioning
**When** the error is transient (network timeout, temporary unavailability)
**Then** the system retries with exponential backoff:
  - Attempt 1: immediate
  - Attempt 2: after 10s
  - Attempt 3: after 30s
  - Attempt 4: after 60s
  - Attempt 5: after 120s
**And** retry attempts are logged with correlation ID

**Given** all retry attempts fail
**When** max retries exceeded
**Then** `VmProvisioningFailed` event is persisted with:
  - errorCode
  - errorMessage
  - retryCount
  - lastAttemptAt
**And** request status changes to "Failed"
**And** user notification sent with error summary
**And** admin notification sent with full error details

**Given** VMware API returns a permanent error (invalid config, no capacity)
**When** error is non-retryable
**Then** provisioning fails immediately without retries
**And** error message is user-friendly:
  - "InsufficientResourcesFault" → "Insufficient resources in cluster"
  - "InvalidDatastorePath" → "Datastore not available"
  - "VmConfigFault" → "VM configuration invalid"

**Given** provisioning partially completes (VM created but network failed)
**When** cleanup is triggered
**Then** partial VM is deleted to prevent orphans
**And** cleanup logged with correlation ID

**Prerequisites:** Story 3.4

**FRs Satisfied:** FR38, FR39, FR77, FR78, FR79

**Technical Notes:**
- Use resilience4j for circuit breaker and retry
- Map VMware fault types to user-friendly messages
- Saga compensation pattern for partial failures
- Idempotency keys prevent duplicate VMs on retry

---

### Story 3.7: Provisioned VM Details

As an **end user**,
I want to see my provisioned VM details,
So that I can connect to and use my VM.

**Acceptance Criteria:**

**Given** my VM is successfully provisioned
**When** I view the request detail
**Then** I see VM information:
  - Status: "Bereit" (green badge)
  - IP Address: `192.168.1.x` (clickable to copy)
  - Hostname: `projectprefix-myvm`
  - CPU: 4 vCPU
  - RAM: 8 GB
  - Disk: 100 GB
  - Created: timestamp
  - Power State: Running

**And** I see connection instructions:
  - SSH: `ssh user@192.168.1.x`
  - RDP: (if Windows) `mstsc /v:192.168.1.x`
  - Console link: Opens vSphere console (if available)

**And** I can refresh VM status (get latest from VMware)
**And** I see uptime since last boot

**Prerequisites:** Story 3.4, Story 2.7 (Request List)

**FRs Satisfied:** FR40

**Technical Notes:**
- Query VMware for live status on detail view
- Cache VM info in projection (refresh on demand)
- IP detection via VMware Tools guest info
- See UX Design: VM Details card

---

### Story 3.8: VM Ready Notification

As an **end user**,
I want to be notified when my VM is ready,
So that I can start using it immediately.

**Acceptance Criteria:**

**Given** my VM provisioning completes successfully
**When** `VmProvisioned` event is persisted
**Then** email is sent to me:
  - Subject: "[DVMM] VM ready: {vmName}"
  - Body:
    - VM Name, Project
    - IP Address, Hostname
    - Connection instructions (SSH/RDP)
    - Link to VM details in portal
    - "VM created in X minutes"

**And** in-app notification appears (if I'm logged in)
**And** dashboard counter updates ("1 new VM ready")

**Given** VM provisioning fails
**When** `VmProvisioningFailed` event is persisted
**Then** email is sent to me:
  - Subject: "[DVMM] VM could not be created: {vmName}"
  - Body:
    - Error summary (user-friendly)
    - Suggested actions
    - Contact admin option

**Prerequisites:** Story 3.6, Story 2.12 (Email Notifications)

**FRs Satisfied:** FR47

**Technical Notes:**
- Extend email notification handler from Story 2.12
- Include provisioning duration in success email
- Separate templates: `vm-ready.html`, `vm-failed.html`
- See UX Design: Email templates

---

### Story 3.9: vCenter Contract Test Suite

> **📋 Status Note:** This story is deferred until vCenter test infrastructure is available. Development proceeds with VCSIM (Stories 3.1-3.8). See "Unblock Condition" below.

As a **developer**,
I want contract tests against a real vCenter instance,
So that I can validate VCSIM assumptions match real API behavior.

**Acceptance Criteria:**

**Given** access to a dedicated vCenter test instance
**When** the contract test suite runs
**Then** it validates:

**Phase 1 - Smoke Tests (Go-Live Gate):**
- [ ] Basic connectivity to vCenter
- [ ] Authentication with service account
- [ ] List datacenters/clusters/datastores
- [ ] Create a test VM from template
- [ ] Power on/off the test VM
- [ ] Delete the test VM

**Phase 2 - Contract Tests (Post Go-Live):**
- [ ] All `VspherePort` operations match VCSIM behavior
- [ ] Error responses match expected formats
- [ ] Timeout behavior validated
- [ ] Rate limiting behavior documented

**Phase 3 - Stress Tests (Post MVP):**
- [ ] Concurrent VM provisioning (10 parallel)
- [ ] API response times under load
- [ ] Session management under load

**Go-Live Gate Definition:**
- Phase 1 Smoke Tests MUST pass before production launch
- At least 1 successful VM provisioning on real vCenter

**Prerequisites:** Story 3.1-3.8 (VCSIM development complete), vCenter access

**FRs Satisfied:** FR34, FR77, FR78 (validation)

**Technical Notes:**
- Uses same `VspherePort` interface as VCSIM tests
- Spring Profile: `vsphere.mode=real` for these tests
- Runs in separate CI job (not on every PR)
- Keycloak for smoke tests uses local container stack (no external dependency)

**Unblock Condition:**
This story is unblocked when dedicated vCenter test machine is available. PM (John) tracks this with IT-Infrastructure.

**Staged Rollout Strategy:**
When vCenter becomes available but before full contract tests:
1. Run Smoke Tests
2. Enable VMware integration for Pilot Tenant (Feature Flag)
3. Monitor intensively for 2 weeks
4. Enable for all tenants after successful pilot

---

## Epic 4: Projects & Quota

**Goal:** Implement project organization and resource quota management to enable structured VM allocation and prevent resource overconsumption.

**User Value:** "My VMs are organized and I know exactly how much budget remains" - Clear resource boundaries and organization.

**FRs Covered:** FR10-FR14, FR82-FR84, FR87

**Stories:** 9 | **Risk:** Medium

### Story 4.1: Project Aggregate

As a **developer**,
I want a Project aggregate for domain logic,
So that project management is consistent with CQRS/ES patterns.

**Acceptance Criteria:**

**Given** I create a new project
**When** I dispatch `CreateProjectCommand`
**Then** `ProjectCreated` event is persisted with:
  - projectId (UUID)
  - tenantId
  - name
  - description
  - createdBy (userId)
  - createdAt

**And** `ProjectAggregate` supports commands:
  - `CreateProjectCommand` → `ProjectCreated`
  - `UpdateProjectCommand` → `ProjectUpdated`
  - `ArchiveProjectCommand` → `ProjectArchived`
  - `AssignUserCommand` → `UserAssignedToProject`
  - `RemoveUserCommand` → `UserRemovedFromProject`

**And** project names are unique within tenant (validated)
**And** archived projects cannot receive new VM requests

**Prerequisites:** Story 1.4 (Aggregate Base)

**FRs Satisfied:** Foundation for FR10-FR14

**Technical Notes:**
- Project is a full aggregate (not entity inside another)
- User assignments stored as value objects in aggregate
- See Architecture: Aggregate Pattern

---

### Story 4.2: Project List View

As an **end user**,
I want to see all projects I have access to,
So that I can organize my work.

**Acceptance Criteria:**

**Given** I am logged in
**When** I navigate to Projects page
**Then** I see a list of projects with:
  - Project name
  - Description (truncated)
  - VM count in project
  - My role (Member / Admin)
  - Status (Active / Archived)

**And** projects are sorted alphabetically by default
**And** I can search projects by name
**And** clicking a project shows its details
**And** only projects I'm assigned to are visible

**Given** I have no project assignments
**When** I view Projects page
**Then** I see empty state: "No project assigned"
**And** guidance: "Please contact Admin for project access"

**Prerequisites:** Story 4.1, Story 1.8 (jOOQ Projections)

**FRs Satisfied:** FR10

**Technical Notes:**
- Query: `GET /api/projects`
- jOOQ projection filters by user assignments
- See UX Design: Project List component

---

### Story 4.3: Create Project (Admin)

As a **tenant admin**,
I want to create new projects,
So that I can organize VMs by team or purpose.

**Acceptance Criteria:**

**Given** I am a tenant admin
**When** I click "Create New Project"
**Then** I see a form with:
  - Name (required, unique within tenant)
  - Description (optional)
  - Initial members (multi-select users)
  - Quota limits (optional, inherits tenant default)

**And** on submit, `CreateProjectCommand` is dispatched
**And** `ProjectCreated` event includes all initial members
**And** success toast: "Project '{name}' created"
**And** I am redirected to project detail

**Given** project name already exists
**When** I submit
**Then** validation error: "Project name already taken"

**Prerequisites:** Story 4.1, Story 2.1 (Auth)

**FRs Satisfied:** FR11

**Technical Notes:**
- Admin role check in controller
- Uniqueness check via query before command
- Initial members trigger `UserAssignedToProject` events

---

### Story 4.4: Edit Project (Admin)

As a **tenant admin**,
I want to edit project details,
So that I can update information as needs change.

**Acceptance Criteria:**

**Given** I view a project as admin
**When** I click "Edit"
**Then** I see edit form with current values pre-filled

**And** I can update: Name, Description, Quota limits
**And** on save, `UpdateProjectCommand` is dispatched
**And** `ProjectUpdated` event includes changed fields
**And** success toast: "Project updated"

**Given** I change the project name to an existing name
**When** I save
**Then** validation error: "Project name already taken"

**Prerequisites:** Story 4.3

**FRs Satisfied:** FR12

**Technical Notes:**
- Only changed fields in event (delta tracking optional)
- Audit trail shows what changed

---

### Story 4.5: Archive Project (Admin)

As a **tenant admin**,
I want to archive inactive projects,
So that they don't clutter active project lists.

**Acceptance Criteria:**

**Given** I view a project as admin
**When** I click "Archive"
**Then** confirmation dialog: "Archive project? VMs remain intact, but no new requests possible."
**And** on confirm, `ArchiveProjectCommand` is dispatched
**And** `ProjectArchived` event is persisted
**And** project status changes to "Archived"
**And** success toast: "Project archived"

**Given** a project is archived
**When** users view the project
**Then** they see read-only view with "Archived" badge
**And** "New VM" button is disabled
**And** existing VMs are still visible

**Given** I want to restore an archived project
**When** I click "Restore"
**Then** `UnarchiveProjectCommand` is dispatched
**And** project becomes active again

**Prerequisites:** Story 4.4

**FRs Satisfied:** FR13

**Technical Notes:**
- Soft delete pattern (status field, not physical delete)
- Archived projects excluded from default list view
- Admin can toggle "Show archived" filter

---

### Story 4.6: Project VM List

As an **end user**,
I want to see all VMs in a project,
So that I can understand resource usage.

**Acceptance Criteria:**

**Given** I view a project detail page
**When** I look at the VMs section
**Then** I see all VMs belonging to this project:
  - VM Name
  - Status (Pending/Provisioning/Ready/Failed)
  - Size
  - Requester
  - Created date
  - IP Address (if provisioned)

**And** VMs are sorted by created date (newest first)
**And** I can filter by status
**And** I can click a VM to see full details
**And** summary shows: "12 VMs (8 Running, 2 Pending, 2 Failed)"

**Prerequisites:** Story 4.2, Story 3.7 (VM Details)

**FRs Satisfied:** FR14

**Technical Notes:**
- Query: `GET /api/projects/{id}/vms`
- Aggregates VM status from projections
- See UX Design: Project Detail page

---

### Story 4.7: Tenant Quota Configuration

As a **tenant admin**,
I want to define resource quotas for my tenant,
So that I can control overall resource consumption.

**Acceptance Criteria:**

**Given** I am a tenant admin
**When** I navigate to Settings → Quotas
**Then** I see configurable quota limits:
  - Max VMs (total count)
  - Max vCPUs (aggregate)
  - Max RAM GB (aggregate)
  - Max Storage GB (aggregate)

**And** current usage is displayed next to each limit
**And** I can set limits per quota type
**And** on save, `UpdateTenantQuotaCommand` is dispatched
**And** `TenantQuotaUpdated` event is persisted

**Given** no quota is configured
**When** users create VMs
**Then** default unlimited quotas apply
**And** warning banner: "No quotas configured"

**Prerequisites:** Story 2.1 (Auth), Story 1.6 (RLS)

**FRs Satisfied:** FR82

**Technical Notes:**
- Quota stored in tenant_quotas table
- Default quotas configurable per environment
- Consider project-level quotas as extension

---

### Story 4.8: Quota Visibility & Enforcement

As an **end user**,
I want to see remaining quota before requesting a VM,
So that I don't waste time on requests that will fail.

**Acceptance Criteria:**

**Given** I am on the VM request form
**When** I select a project and size
**Then** I see quota status:
  - "Available: 3 of 10 VMs"
  - "RAM: 24 GB of 64 GB available"
  - Progress bars showing usage

**And** if selected size would exceed quota:
  - Size card shows "Quota exceeded"
  - Submit button is disabled
  - Message: "Not enough quota for this VM size"

**Given** I try to submit a request exceeding quota
**When** command handler validates
**Then** command fails with `QuotaExceeded` error
**And** event is NOT persisted
**And** error message specifies which quota exceeded

**Given** quota is nearly full (>90%)
**When** I view dashboard
**Then** warning banner: "Quota almost exhausted (92%)"

**Prerequisites:** Story 4.7, Story 2.6 (Submit Command)

**FRs Satisfied:** FR83, FR84

**Technical Notes:**
- Synchronous quota check in command handler
- Query quota usage from projection
- Optimistic locking prevents race conditions
- See Architecture: Invariant Enforcement

---

### Story 4.9: Resource Utilization Dashboard

As a **tenant admin**,
I want to see real-time resource utilization,
So that I can plan capacity and manage costs.

**Acceptance Criteria:**

**Given** I am a tenant admin
**When** I view the Admin Dashboard
**Then** I see resource utilization section:
  - Total VMs: X of Y (pie chart)
  - vCPU Usage: X of Y cores (progress bar)
  - RAM Usage: X GB of Y GB (progress bar)
  - Storage: X GB of Y GB (progress bar)

**And** charts show trend over last 30 days
**And** breakdown by project is available
**And** data refreshes every 5 minutes

**Given** I want more detail
**When** I click "Details"
**Then** I see detailed breakdown:
  - Usage by project (table)
  - Usage by user (table)
  - VM size distribution (chart)
  - Growth trend (line chart)

**Prerequisites:** Story 4.8, Story 3.7 (VM inventory)

**FRs Satisfied:** FR87

**Technical Notes:**
- Aggregate data from VM projections
- Consider caching aggregated stats
- Charts using shadcn/ui chart components
- See UX Design: Admin Dashboard metrics section

---

## Epic 5: Compliance & Oversight

**Goal:** Implement comprehensive audit logging, reporting, and oversight capabilities to achieve ISO 27001 audit-readiness and complete tenant isolation verification.

**User Value:** "I can trace every request in 30 seconds" - Complete accountability and compliance confidence.

**FRs Covered:** FR51-FR54, FR57-FR60, FR64-FR65, FR73, FR90, NFR-SEC-10 (GDPR)

**Stories:** 10 | **Risk:** Medium

### Story 5.1: Admin Request Overview

As a **tenant admin**,
I want to see all requests across my tenant,
So that I have complete visibility into request activity.

**Acceptance Criteria:**

**Given** I am logged in as a tenant admin
**When** I view the Admin Dashboard
**Then** I see:
  - Pending requests count (prominent badge)
  - Recent requests list (last 7 days)
  - Quick stats: Total / Approved / Rejected / Pending this week

**And** clicking pending count goes to approval queue
**And** clicking a request opens detail view
**And** dashboard updates without page refresh (polling)

**Given** there are pending requests
**When** count exceeds 5
**Then** badge shows warning color (amber)
**When** count exceeds 10
**Then** badge shows alert color (red)

**Prerequisites:** Story 2.9 (Admin Approval Queue)

**FRs Satisfied:** FR51, FR52

**Technical Notes:**
- Aggregated stats from request projections
- See UX Design: Admin Dashboard layout
- Consider WebSocket for real-time count updates

---

### Story 5.2: Request Filtering & Sorting

As a **tenant admin**,
I want to filter and sort requests,
So that I can find specific requests quickly.

**Acceptance Criteria:**

**Given** I am viewing the all-requests list
**When** I use filters
**Then** I can filter by:
  - Status (multi-select: Pending, Approved, Rejected, Provisioning, Ready, Failed)
  - Project (dropdown)
  - Requester (user search)
  - Date range (from/to)
  - Size (S/M/L/XL)

**And** filters are combinable (AND logic)
**And** active filters show as chips (dismissible)
**And** "Clear all filters" resets to default
**And** filter state persists in URL (shareable)

**Given** I am viewing filtered results
**When** I click column headers
**Then** I can sort by:
  - Created date (default: desc)
  - VM Name (asc/desc)
  - Status
  - Requester name
  - Project name

**And** sort direction toggles on click
**And** current sort shown with arrow indicator

**Prerequisites:** Story 5.1

**FRs Satisfied:** FR53, FR54

**Technical Notes:**
- Query params: `?status=pending,approved&project=123&sort=created_at:desc`
- jOOQ dynamic query building
- See UX Design: Filter component

---

### Story 5.3: Request History Export

As a **tenant admin**,
I want to export request history as CSV,
So that I can analyze data in spreadsheets or for audits.

**Acceptance Criteria:**

**Given** I am viewing requests (with optional filters applied)
**When** I click "Export CSV"
**Then** a CSV file downloads with columns:
  - Request ID
  - VM Name
  - Project Name
  - Requester Email
  - Size
  - Status
  - Created At
  - Approved/Rejected At
  - Approver Email
  - Rejection Reason (if rejected)
  - Provisioned At (if provisioned)
  - IP Address (if provisioned)

**And** export includes all filtered results (not just current page)
**And** filename: `dvmm-requests-{tenant}-{date}.csv`
**And** large exports (>10000 rows) run async with download link

**Given** I want audit-formatted export
**When** I select "Audit Export" format
**Then** additional columns included:
  - Correlation ID
  - All event timestamps
  - User agent (if tracked)

**Prerequisites:** Story 5.2

**FRs Satisfied:** FR57

**Technical Notes:**
- Stream large exports to avoid memory issues
- Consider background job for very large exports
- UTF-8 BOM for Excel compatibility

---

### Story 5.4: Audit Trail Projection

As a **developer**,
I want a dedicated audit trail projection,
So that audit queries are fast and comprehensive.

**Acceptance Criteria:**

**Given** any domain event is persisted
**When** the event processor runs
**Then** an audit entry is created in `audit_log` table with:
  - id (UUID)
  - tenant_id
  - correlation_id
  - aggregate_type (VmRequest, Project, etc.)
  - aggregate_id
  - event_type
  - actor_id (who performed action)
  - actor_email
  - actor_role
  - timestamp
  - changes (JSONB diff of what changed)
  - metadata (JSONB: IP, user agent, etc.)

**And** audit log is immutable (append-only)
**And** audit log has separate RLS policy
**And** retention policy: 7 years (configurable)

**Given** system-generated events (e.g., provisioning)
**When** no human actor
**Then** actor_id is "SYSTEM" with appropriate label

**Prerequisites:** Story 1.3 (Event Store)

**FRs Satisfied:** FR58, FR60

**Technical Notes:**
- Event handler subscribes to all domain events
- Changes extracted from event payload
- Consider separate audit database for scale (future)

**Observability Dependency (NFR-OBS-9):**
- Audit entries require correlation_id for request tracing across services
- In coroutine-based systems, MDC context (containing correlation_id, tenant_id) must propagate correctly
- **Growth Phase:** `kotlinx-coroutines-slf4j` integration ensures audit trail integrity across async boundaries. Without this, correlation_ids may be lost during dispatcher switches or async operations.

---

### Story 5.5: Request Audit Log View

As a **tenant admin**,
I want to view the complete audit log for any request,
So that I can trace exactly what happened.

**Acceptance Criteria:**

**Given** I view a request detail page
**When** I click "Show Audit Log"
**Then** I see chronological list of all events:

| Timestamp | Action | Actor | Details |
|-----------|--------|-------|---------|
| 2025-01-15 10:23 | Request created | max@example.com | VM: web-server, Size: M |
| 2025-01-15 14:45 | Request approved | admin@example.com | |
| 2025-01-15 14:45 | Provisioning started | SYSTEM | |
| 2025-01-15 14:52 | VM ready | SYSTEM | IP: 192.168.1.100 |

**And** each row expandable to show full event details
**And** correlation ID visible for support tickets
**And** export to PDF available for audit documentation

**Given** I want to see technical details
**When** I toggle "Technical View"
**Then** I see raw event JSON and metadata

**Prerequisites:** Story 5.4, Story 2.8 (Timeline)

**FRs Satisfied:** FR59

**Technical Notes:**
- Query: `GET /api/requests/{id}/audit-log`
- Uses audit_log projection
- PDF export via wkhtmltopdf or similar

---

### Story 5.6: ISO 27001 Control Mapping

As a **compliance officer**,
I want audit reports mapped to ISO 27001 controls,
So that I can demonstrate compliance during audits.

**Acceptance Criteria:**

**Given** I am a tenant admin
**When** I navigate to Reports → Compliance
**Then** I see ISO 27001 control mapping section with controls:

| Control | Description | Status | Evidence |
|---------|-------------|--------|----------|
| A.9.2.3 | Privileged access management | ✓ Active | Role-based access, approval workflow |
| A.12.4.1 | Event logging | ✓ Active | Complete audit trail |
| A.12.4.3 | Admin activity logs | ✓ Active | All admin actions logged |
| A.18.1.3 | Records protection | ✓ Active | Immutable event store |

**And** clicking a control shows:
  - Control details
  - How DVMM implements it
  - Link to relevant audit logs
  - Export evidence as PDF

**Given** I need to generate audit report
**When** I click "Generate ISO 27001 Report"
**Then** PDF report generated with:
  - Control mapping
  - Sample audit log entries
  - System architecture summary
  - Data flow diagrams

**Prerequisites:** Story 5.5

**FRs Satisfied:** FR90

**Technical Notes:**
- Control mapping maintained in configuration
- Evidence auto-generated from audit logs
- Report template: `templates/compliance/iso27001-report.html`

---

### Story 5.7: Tenant Isolation Verification

As a **system operator**,
I want to verify tenant data isolation,
So that I can confirm security guarantees.

**Acceptance Criteria:**

**Given** RLS is configured (Story 1.6)
**When** I run tenant isolation tests
**Then** the following scenarios pass:

**Scenario 1: Cross-tenant query prevention**
- Tenant A user queries requests
- Zero results from Tenant B returned

**Scenario 2: Cross-tenant write prevention**
- Tenant A user attempts to modify Tenant B data
- Operation rejected with 403

**Scenario 3: Missing tenant context**
- Query without tenant context
- Zero rows returned (fail-closed)

**And** automated tests run in CI pipeline
**And** test results included in compliance reports

**Given** I want runtime verification
**When** I run "Isolation Health Check" from admin
**Then** system confirms:
  - RLS policies active
  - No bypass routes detected
  - Recent isolation test results

**Prerequisites:** Story 1.6 (RLS Policies)

**FRs Satisfied:** FR64, FR65

**Technical Notes:**
- Integration tests with multiple test tenants
- See Architecture: Test Design TC-002
- Consider periodic production verification (careful!)

**🔒 Security Controls Reference:**

This story validates the **Tenant-Isolation Security Controls** implemented in Stories 1.5 and 1.6:

| Control ID | Control Name | Implementing Story | Validation |
|------------|--------------|-------------------|------------|
| TC-001 | Coroutine Tenant Context Propagation | Story 1.5 | Stress Test (100 parallel coroutines) |
| TC-002 | PostgreSQL RLS Fail-Closed | Story 1.6 | RlsEnforcingDataSource + Missing Context Test |

**CI/CD Integration Requirements:**
- Full **Isolation-Test-Suite** MUST run at least **once per release**
- Stress Tests (TC-001 Scenario 3) can be marked as "nightly"
- **Every PR**: At minimum Basic Isolation Tests (Scenarios 1-3 above)
- **Before Go-Live**: Complete suite including Stress Tests

**Compliance Mapping:**
- ISO 27001: A.9.4.1 (Information Access Restriction)
- GDPR: Art. 32 (Security of Processing)

---

### Story 5.8: System Health Dashboard

As a **tenant admin**,
I want to see system health status,
So that I know if the system is operating normally.

**Acceptance Criteria:**

**Given** I am a tenant admin
**When** I navigate to Settings → System Health
**Then** I see:

| Component | Status | Details |
|-----------|--------|---------|
| API Server | ✓ Healthy | Response time: 45ms |
| Database | ✓ Healthy | Connections: 12/100 |
| Event Store | ✓ Healthy | Events today: 1,234 |
| VMware | ✓ Connected | vCenter 8.0 |
| Email (SMTP) | ✓ Configured | Last sent: 5 min ago |
| Keycloak | ✓ Connected | Realm: dvmm |

**And** status refreshes every 30 seconds
**And** warning/error states shown prominently
**And** clicking a component shows detailed metrics

**Given** a component is unhealthy
**When** I view the dashboard
**Then** I see:
  - Red status indicator
  - Error message summary
  - "Last healthy" timestamp
  - Suggested actions

**Prerequisites:** Story 3.1 (VMware Config), Story 2.12 (Email)

**FRs Satisfied:** FR73

**Technical Notes:**
- Spring Boot Actuator health endpoints
- Custom health indicators per component
- See UX Design: Health Dashboard component

---

### Story 5.9: Admin Activity Log

As a **tenant admin**,
I want to see all admin actions in my tenant,
So that I can monitor privileged operations.

**Acceptance Criteria:**

**Given** I am a tenant admin
**When** I navigate to Reports → Admin Activity
**Then** I see log of all admin actions:
  - Timestamp
  - Admin user
  - Action type
  - Target (project, user, configuration)
  - Details

**And** actions include:
  - Request approvals/rejections
  - Project create/edit/archive
  - User assignments
  - Quota changes
  - VMware configuration changes
  - SMTP configuration changes

**And** log is filterable by:
  - Date range
  - Admin user
  - Action type

**And** export as CSV available

**Given** sensitive configuration changed (VMware password, etc.)
**When** viewing the log
**Then** sensitive values are masked (show "***changed***")

**Prerequisites:** Story 5.4 (Audit Projection)

**FRs Satisfied:** FR58, FR60 (admin-specific)

**Technical Notes:**
- Filtered view of audit_log for admin actions
- Query: `GET /api/admin/activity-log`
- Role filter: admin actions only

---

### Story 5.10: GDPR Crypto-Shredding

As a **compliance officer**,
I want personal data in events to be cryptographically shredded on user deletion,
So that GDPR right-to-erasure is enforced while preserving audit integrity.

**Acceptance Criteria:**

**Given** a user requests account deletion (GDPR Art. 17)
**When** the deletion process executes
**Then** all personal data encryption keys for that user are destroyed

**And** event store entries remain intact (audit integrity)
**And** personal data fields in events become unreadable garbage
**And** non-personal data (aggregateId, eventType, timestamp) remains readable
**And** audit reports show "[GDPR DELETED]" for shredded user data

**Given** an admin queries audit history for a deleted user
**When** the query executes
**Then** the system returns:
  - Event structure intact
  - Personal fields: "[GDPR DELETED - 2025-01-15]"
  - Deletion timestamp and reason logged

**Given** the crypto-shredding key store
**When** I inspect the implementation
**Then** I see:
  - Per-user encryption keys for personal data
  - Keys stored separately from events (different table/service)
  - Key destruction is atomic and logged
  - No recovery possible after destruction

**Technical Design:**

```
┌─────────────────┐     ┌─────────────────┐
│   Event Store   │     │   Key Store     │
├─────────────────┤     ├─────────────────┤
│ aggregate_id    │     │ user_id (PK)    │
│ event_type      │     │ encryption_key  │
│ encrypted_pii   │────►│ created_at      │
│ metadata        │     │ destroyed_at    │
└─────────────────┘     └─────────────────┘
                              │
                              ▼
                        Key Destroyed
                              │
                              ▼
                    encrypted_pii = garbage
```

**Prerequisites:** Story 5.4 (Audit Trail Projection), Story 1.3 (Event Store)

**FRs Satisfied:** NFR-SEC-10 (Crypto-Shredding), GDPR Art. 17

**Technical Notes:**
- Use AES-256 with per-user keys
- Key store in separate `user_encryption_keys` table
- Shred operation: DELETE key, log destruction event
- See Architecture: eaf-audit/crypto module
- Integration test: create user → create events → shred → verify unreadable

---

## Final Summary

### Story Count by Epic

| Epic | Stories | User Value |
|------|---------|------------|
| Epic 1: Foundation | 11 | Technical enabler |
| Epic 2: Core Workflow | 13 | Request → Approve → Notify |
| Epic 3: VM Provisioning | 10 | VM is created |
| Epic 4: Projects & Quota | 9 | Organization & Control |
| Epic 5: Compliance & Oversight | 10 | Audit-ready + GDPR |
| **Total** | **53** | |

### FR Coverage Summary

**MVP FRs Covered:** 59 of 66 (89%)
**NFRs Addressed:** NFR-SEC-10 (Crypto-Shredding) now covered
**Remaining FRs:** Growth phase or implicit coverage

### Dependency Chain

```
Epic 1 ──► Epic 2 ──► Epic 3 ──► Epic 4 ──► Epic 5
  │           │          │          │          │
  │           │          │          │          └─ Audit & Compliance
  │           │          │          └─ Projects & Quotas
  │           │          └─ VMware Integration (Critical Risk)
  │           └─ Core Workflow (Tracer Bullet)
  └─ Foundation (Enables Everything)
```

### Tracer Bullet Milestone

**Target: End of Epic 2**
- User can log in via Keycloak ✓
- User can create VM request ✓
- Admin can approve request ✓
- Email notifications sent ✓
- (VM provisioning mocked with Docker container)

### Key Risks

1. **Epic 3 (VMware)**: Critical risk - real infrastructure integration
   - Mitigation: VCSIM testing, circuit breaker, retry logic

2. **Epic 2 (First UI)**: High risk - first user-facing code
   - Mitigation: shadcn-admin-kit foundation, UX spec

3. **Multi-tenancy bugs**: Medium risk across all epics
   - Mitigation: RLS at database level, comprehensive tests

---

*This epic breakdown was created following the BMad Method, integrating context from PRD (90 FRs), UX Design Specification (Tech Teal theme), and Architecture Document (CQRS/ES, PostgreSQL RLS, Kotlin/Spring Boot).*

*Next Step: → Implementation Readiness Check → Sprint Planning*
