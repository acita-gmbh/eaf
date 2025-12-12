# ADR-003: Core Technology Decisions

**Status:** Accepted
**Date:** 2025-11-25
**Author:** DCM Team
**Deciders:** Architecture Team
**Origin:** Team Party Mode Discussion

---

## Overview

| # | Area | Decision |
|---|------|----------|
| 1 | Reactive Model | WebFlux + Kotlin Coroutines |
| 2 | Event Store | Single Table + Outbox Pattern |
| 3 | Projection Strategy | Async + Read-Your-Own-Writes |
| 4 | Multi-Tenant Data | PostgreSQL RLS |
| 5 | API Versioning | URL Path (`/api/v1/`) |
| 6 | Error Handling | Custom Result Type in eaf-core |
| 7 | Read Projections | jOOQ for Type-Safe Queries |

---

## Decision 1: WebFlux + Kotlin Coroutines

**Context:** Choice between blocking (Web MVC) and reactive (WebFlux) stack.

**Decision:** WebFlux with Kotlin Coroutines instead of Reactor Mono/Flux.

```kotlin
// Suspend functions instead of Mono/Flux
suspend fun getVmRequest(id: RequestId): VmRequest? {
    return repository.findById(id)
}

// Controller
@GetMapping("/requests/{id}")
suspend fun getRequest(@PathVariable id: UUID): ResponseEntity<VmRequestDto> {
    val request = vmRequestService.getVmRequest(RequestId(id))
        ?: return ResponseEntity.notFound().build()
    return ResponseEntity.ok(request.toDto())
}
```

**Rationale:**
- Better performance for I/O-bound operations (VMware API, DB)
- Kotlin Coroutines more idiomatic than Reactor
- Virtual Threads (Spring Boot 3.5) as fallback for blocking libraries

---

## Decision 2: Single Table + Outbox Pattern

**Context:** Event Store Schema Design for Event Sourcing.

**Decision:** Single `events` table with separate `outbox` table for reliable Event Publishing.

```sql
-- Event Store
CREATE TABLE events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    metadata JSONB NOT NULL,
    tenant_id UUID NOT NULL,
    version INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(aggregate_id, version)
);

-- Outbox for reliable Publishing
CREATE TABLE outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id),
    published BOOLEAN DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for Outbox Polling
CREATE INDEX idx_outbox_unpublished ON outbox(created_at) WHERE NOT published;
```

**Rationale:**
- Single table easier to manage than table-per-aggregate
- Outbox guarantees at-least-once delivery
- RLS on `tenant_id` for multi-tenancy

---

## Decision 3: Async Projections + Read-Your-Own-Writes

**Context:** Eventual Consistency between Event Store and Read Models.

**Decision:** Asynchronous Projections with Read-Your-Own-Writes Pattern for UX.

```kotlin
// After Command: Read directly from Events (not from Projection)
class CreateVmRequestHandler(
    private val eventStore: EventStore,
    private val projectionUpdater: ProjectionUpdater
) {
    suspend fun handle(command: CreateVmRequestCommand): VmRequestCreatedResponse {
        // 1. Create and persist events
        val events = VmRequestAggregate.create(command)
        eventStore.append(command.aggregateId, events)

        // 2. Trigger Async Projection Update (fire-and-forget)
        projectionUpdater.scheduleUpdate(command.aggregateId)

        // 3. Build response directly from Events (Read-Your-Own-Writes)
        return VmRequestCreatedResponse.fromEvents(events)
    }
}
```

**Rationale:**
- User sees own changes immediately
- Projections can catch up asynchronously
- No latency from synchronous projection updates

---

## Decision 4: PostgreSQL Row-Level Security (RLS)

**Context:** Multi-Tenant Data Isolation.

**Decision:** RLS Policies on all tenant-related tables.

```sql
-- Enable RLS
ALTER TABLE events ENABLE ROW LEVEL SECURITY;
ALTER TABLE vm_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE vms ENABLE ROW LEVEL SECURITY;

-- Policy: Only own tenant data visible
CREATE POLICY tenant_isolation_events ON events
    USING (tenant_id = current_setting('app.tenant_id')::UUID);

CREATE POLICY tenant_isolation_vm_requests ON vm_requests
    USING (tenant_id = current_setting('app.tenant_id')::UUID);

-- Set Tenant-Context (in eaf-tenant Filter)
SET LOCAL app.tenant_id = 'uuid-here';
```

```kotlin
// eaf-tenant: Automatic Tenant-Context setting
class TenantContextFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val tenantId = exchange.extractTenantId()
        return chain.filter(exchange)
            .contextWrite { it.put(TenantContext.KEY, tenantId) }
    }
}
```

**Rationale:**
- Database-Level Enforcement (fail-closed)
- No possibility of forgetting WHERE clauses
- Audit-friendly (Compliance)

---

## Decision 5: URL Path API Versioning

**Context:** API Versioning Strategy.

**Decision:** Version in URL path: `/api/v1/`, `/api/v2/`

```kotlin
@RestController
@RequestMapping("/api/v1/vm-requests")
class VmRequestControllerV1 { ... }

@RestController
@RequestMapping("/api/v2/vm-requests")
class VmRequestControllerV2 { ... }
```

```yaml
# OpenAPI Specs per Version
openapi/
├── v1/
│   └── openapi.yaml
└── v2/
    └── openapi.yaml
```

**Rationale:**
- Visible in DevTools/Logs
- Clear OpenAPI-Spec per version
- Simple routing

---

## Decision 6: Result Type for Error Handling

**Context:** Error handling in Domain/Application Layer.

**Decision:** Custom `Result<T, E>` type in `eaf-core` (no external library).

```kotlin
// eaf-core/src/main/kotlin/com/eaf/core/Result.kt
package com.eaf.core

sealed class Result<out T, out E> {
    data class Success<T>(val value: T) : Result<T, Nothing>()
    data class Failure<E>(val error: E) : Result<Nothing, E>()

    inline fun <R> map(transform: (T) -> R): Result<R, E> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    inline fun <R> flatMap(transform: (T) -> Result<R, @UnsafeVariance E>): Result<R, E> =
        when (this) {
            is Success -> transform(value)
            is Failure -> this
        }

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw ResultException(error.toString())
    }
}

// Extension functions
fun <T> T.success(): Result<T, Nothing> = Result.Success(this)
fun <E> E.failure(): Result<Nothing, E> = Result.Failure(this)
```

```kotlin
// Usage in Domain
sealed class QuotaError {
    data class Exceeded(val current: Int, val max: Int) : QuotaError()
    data class NotConfigured(val tenantId: TenantId) : QuotaError()
}

fun validateQuota(request: VmRequest, quota: Quota): Result<Unit, QuotaError> {
    if (request.cpuCores > quota.remainingCores) {
        return QuotaError.Exceeded(quota.usedCores, quota.maxCores).failure()
    }
    return Unit.success()
}
```

**Rationale:**
- No external dependency (Arrow is overkill)
- Explicit error handling in the type system
- Kotlin-idiomatic

---

## Decision 7: jOOQ for Read Projections

**Context:** Query technology for CQRS Read Side.

**Decision:** jOOQ for type-safe Projection Queries, R2DBC for Write Side.

```
Write Side (Commands):  R2DBC + Coroutines → Event Store
Read Side (Queries):    jOOQ + Virtual Threads → Projections
```

```kotlin
// Projection Repository with jOOQ
class VmRequestProjectionRepository(private val dsl: DSLContext) {

    fun findByTenantWithStats(tenantId: TenantId): List<VmRequestSummary> {
        return dsl
            .select(
                VM_REQUEST.ID,
                VM_REQUEST.NAME,
                VM_REQUEST.STATUS,
                count(VM.ID).`as`("vm_count"),
                sum(VM.CPU_CORES).`as`("total_cores")
            )
            .from(VM_REQUEST)
            .leftJoin(VM).on(VM.REQUEST_ID.eq(VM_REQUEST.ID))
            .where(VM_REQUEST.TENANT_ID.eq(tenantId.value))
            .groupBy(VM_REQUEST.ID)
            .fetchInto(VmRequestSummary::class.java)
    }

    fun findPendingApprovals(managerId: UserId): List<ApprovalQueueItem> {
        return dsl
            .select(
                VM_REQUEST.asterisk(),
                USER.DISPLAY_NAME.`as`("requester_name"),
                PROJECT.NAME.`as`("project_name")
            )
            .from(VM_REQUEST)
            .join(USER).on(VM_REQUEST.REQUESTER_ID.eq(USER.ID))
            .join(PROJECT).on(VM_REQUEST.PROJECT_ID.eq(PROJECT.ID))
            .where(VM_REQUEST.STATUS.eq(RequestStatus.PENDING_APPROVAL))
            .and(PROJECT.MANAGER_ID.eq(managerId.value))
            .orderBy(VM_REQUEST.CREATED_AT.asc())
            .fetchInto(ApprovalQueueItem::class.java)
    }
}
```

**Rationale:**
- Type-safe SQL with compile-time checks
- Ideal for complex JOINs and aggregations in Projections
- Code generation from DB schema
- Virtual Threads (Spring Boot 3.5) for blocking jOOQ calls

**Code Generation (DDLDatabase):**

jOOQ code is generated from DDL files using `DDLDatabase` - no running database required:

```bash
./gradlew :dcm:dcm-infrastructure:generateJooq
```

Key files:
- `dcm/dcm-infrastructure/src/main/resources/db/jooq-init.sql` - Combined DDL script
- `dcm/dcm-infrastructure/build.gradle.kts` - jOOQ configuration

**PostgreSQL-Specific Syntax:**

DDLDatabase uses H2 internally and cannot parse PostgreSQL-specific statements. Use jOOQ ignore tokens:

```sql
-- [jooq ignore start]
ALTER TABLE events ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON events ...;
GRANT SELECT ON events TO eaf_app;
CREATE TRIGGER trg_prevent_update ...;
-- [jooq ignore stop]
```

Statements to wrap in ignore tokens:
- RLS policies (`ENABLE/FORCE ROW LEVEL SECURITY`, `CREATE POLICY`)
- Permission grants (`GRANT`, `REVOKE`)
- Roles (`CREATE ROLE`, `DO $$ ... $$` blocks)
- Triggers and functions (`CREATE TRIGGER`, `CREATE FUNCTION`)
- Comments (`COMMENT ON`)

These are runtime concerns that don't affect generated jOOQ code.

## References

- [ADR-001: EAF Framework-First Architecture](ADR-001-eaf-framework-first-architecture.md)
- [ADR-002: IdP-Agnostic Authentication](ADR-002-idp-agnostic-authentication.md)
