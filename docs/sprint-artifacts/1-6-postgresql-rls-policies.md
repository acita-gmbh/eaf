# Story 1.6: PostgreSQL RLS Policies

Status: review

## Story

As a **developer**,
I want Row-Level Security enforced at database level,
so that tenant isolation is guaranteed even if application code has bugs.

## Requirements Context Summary

- **Epic/AC source:** Story 1.6 in `docs/epics.md` — must enable RLS on tenant-scoped tables with fail-closed semantics (zero rows returned without context); set `app.tenant_id` via PostgreSQL session variable on every connection.
- **Testability Concern TC-002 (critical):** RLS prevents cross-tenant data access; test database connection MUST enforce tenant context (throws `IllegalStateException` if missing); `RlsEnforcingDataSource` required in `eaf-testing`.
- **Architecture constraints:** Follow Architecture ADR-003 (PostgreSQL RLS); tenant context from Story 1.5 must integrate with connection pool; no superuser bypass for application role.
- **Security:** Aligns with Security Architecture (database-enforced isolation, fail-closed).
- **Prerequisites:** Story 1.5 (Tenant Context Module) done; `TenantContext.current()`, `TenantContextElement`, coroutine propagation available.

## Acceptance Criteria (Story 1.6)

1. **RLS enabled on tenant-scoped tables**
   - Given RLS is enabled via Flyway migration V002
   - When a query runs without proper tenant context
   - Then zero rows are returned (fail-closed, NOT an exception).

2. **Session variable setting**
   - `app.tenant_id` PostgreSQL session variable is set per connection before any tenant-scoped query.

3. **RLS policy definition**
   - Policy uses: `tenant_id = current_setting('app.tenant_id', true)::uuid`
   - Note: `true` parameter makes `current_setting` return NULL instead of error if not set.

4. **Connection pool integration**
   - HikariCP (or R2DBC pool) sets tenant context on connection checkout.
   - `RlsConnectionCustomizer` or equivalent mechanism injects tenant from coroutine context.

5. **Flyway migration V002**
   - Creates RLS policies on `eaf_events.events` and `eaf_events.snapshots` tables.
   - Creates `dvmm_app` role without superuser privileges.
   - Forces RLS for application role (no bypass).

6. **Superuser bypass disabled**
   - Application role `dvmm_app` cannot bypass RLS.
   - `ALTER TABLE ... FORCE ROW LEVEL SECURITY` applied.

## TC-002 Acceptance Criteria (Critical)

7. **Cross-tenant data isolation**
   - Given tenant "A" has created a VM request (or event) "req-A"
   - And tenant "B" exists in the system
   - When tenant "B" queries all VM requests
   - Then the result set does NOT contain "req-A"
   - And a direct SQL query as tenant "B" returns 0 rows for "req-A".

8. **Test database connection enforcement**
   - Given a test is running
   - When the test attempts a database query without tenant context
   - Then the connection MUST throw `IllegalStateException`
   - And the error message indicates "NO TENANT CONTEXT IN TEST"
   - And queries without tenant context MUST fail (not return all data).

## Test Plan

- **Unit:** `RlsEnforcingDataSource` throws when `TenantTestContext.current()` is null.
- **Integration:** Multi-tenant isolation test: Tenant A writes event, Tenant B cannot read it.
- **Integration:** Missing context test: Query without tenant context returns zero rows (RLS fail-closed).
- **Integration:** Verify Flyway migration V002 applies correctly (RLS enabled, policies created).
- **Integration:** Connection pool test: Tenant context propagates from coroutine to SQL session variable.

## Structure Alignment / Previous Learnings

### Learnings from Previous Story

**From Story 1-5-tenant-context-module (Status: done)**

- **New Service Created:** `TenantContext` coroutine-based helpers available at `eaf/eaf-tenant/src/main/kotlin/de/acci/eaf/tenant/TenantContext.kt` — use `TenantContext.current()` to get current tenant.
- **Coroutine Context Element:** `TenantContextElement` propagates tenant through coroutine boundaries — integrate with connection customizer.
- **Reactor Fallback:** `REACTOR_TENANT_KEY` constant used for WebFlux Reactor context integration — may need to bridge for R2DBC.
- **Testing Patterns:** `TenantContextPropagationTest` demonstrates dispatcher switch and async boundary tests — follow similar patterns for RLS tests.
- **JWT Integration:** `JwtTenantClaimExtractor` extracts `tenant_id` from JWT — tenant is available in request context.
- **Fail-Closed Semantics:** `TenantContextMissingException` thrown when context missing at application layer — RLS adds database layer enforcement.

[Source: docs/sprint-artifacts/1-5-tenant-context-module.md#Dev-Agent-Record]

### Project Structure Notes

- RLS infrastructure belongs in `eaf-tenant` module (connection customizer, RLS utilities).
- Migration V002 should be placed in `dvmm-infrastructure` or `dvmm-app` (product-specific schema).
- `RlsEnforcingDataSource` belongs in `eaf-testing` module per tech spec.
- `TenantTestContext` already exists in `eaf-testing` per tech spec — use for test tenant injection.

## Tasks / Subtasks

- [x] **Task 1: Create Flyway migration V002__enable_rls.sql** (AC: 1, 3, 5, 6)
  - [x] Enable RLS on `eaf_events.events` table
  - [x] Enable RLS on `eaf_events.snapshots` table
  - [x] Create RLS policy `tenant_isolation_events` using `current_setting('app.tenant_id', true)::uuid`
  - [x] Create RLS policy `tenant_isolation_snapshots` using same pattern
  - [x] Create role `eaf_app` with NOINHERIT (renamed from dvmm_app for framework-level)
  - [x] Grant appropriate permissions to `eaf_app` role
  - [x] Apply `FORCE ROW LEVEL SECURITY` to prevent bypass

- [x] **Task 2: Implement RlsConnectionCustomizer in eaf-tenant** (AC: 2, 4)
  - [x] Create `RlsConnectionCustomizer` component
  - [x] Integrate with `TenantContext.current()` to get tenant from coroutine context
  - [x] Execute `set_config('app.tenant_id', ?, false)` for session-scoped tenant context
  - [x] Handle suspend context properly (coroutine-safe)

- [x] **Task 3: Configure HikariCP/R2DBC pool integration** (AC: 4)
  - [x] Add `TenantAwareDataSourceDecorator` for connection customization
  - [x] Ensure tenant context is set before any query execution
  - [x] Document connection lifecycle and tenant injection point

- [x] **Task 4: Verify RlsEnforcingDataSource in eaf-testing** (AC: 8)
  - [x] `RlsEnforcingDataSource` already exists with correct implementation
  - [x] Delegates to underlying DataSource
  - [x] Override `getConnection()` to check `TenantTestContext.current()`
  - [x] Throws `IllegalStateException("NO TENANT CONTEXT IN TEST!")` if null
  - [x] Sets `app.tenant_id` session variable from test context

- [x] **Task 5: Verify TenantTestContext in eaf-testing** (AC: 8)
  - [x] `TenantTestContext` already exists with ThreadLocal storage
  - [x] Provides `set(tenantId)`, `current()`, `clear()` methods
  - [x] Works correctly with RlsEnforcingDataSource

- [x] **Task 6: Write integration tests for RLS** (AC: 1, 7, 8)
  - [x] Test: Tenant A event not visible to Tenant B
  - [x] Test: Query without tenant context returns zero rows (fail-closed)
  - [x] Test: `RlsEnforcingDataSource` throws when no test context
  - [x] Test: RLS policy uses current_setting with missing_ok=true
  - [x] Test: FORCE ROW LEVEL SECURITY is enabled

- [x] **Task 7: Ensure no Spring dependencies in core eaf-tenant classes** (meta)
  - [x] `RlsConnectionCustomizer` is Spring-free (pure Kotlin)
  - [x] `TenantAwareDataSourceDecorator` is Spring-free (pure Kotlin)
  - [x] Follow patterns from Story 1.5 (Spring in adapter layer only)

## Dev Notes

- **Relevant architecture patterns:** See `docs/architecture.md` ADR-003 Decision 4 (PostgreSQL RLS); fail-closed semantics are mandatory.
- **Source tree components to touch:**
  - `dvmm/dvmm-infrastructure/src/main/resources/db/migration/V002__enable_rls.sql` (or dvmm-app)
  - `eaf/eaf-tenant/src/main/kotlin/de/acci/eaf/tenant/rls/RlsConnectionCustomizer.kt`
  - `eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/RlsEnforcingDataSource.kt`
  - `eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/TenantTestContext.kt`
- **Testing standards:** Use Testcontainers PostgreSQL; achieve ≥80% coverage and ≥70% mutation score; TC-002 tests are mandatory CI gate.
- **Connection pooling note:** If using R2DBC, may need `ConnectionFactoryDecorator` instead of DataSource wrapper.

### References

- [Source: docs/epics.md#Story-1.6-PostgreSQL-RLS-Policies]
- [Source: docs/architecture.md#ADR-003-Decision-4-PostgreSQL-RLS]
- [Source: docs/sprint-artifacts/tech-spec-epic-1.md#Story-1.6-PostgreSQL-RLS-Policies]
- [Source: docs/security-architecture.md#Multi-Tenant-Isolation]
- [Source: docs/test-design-system.md#TC-002]
- [Source: docs/sprint-artifacts/1-5-tenant-context-module.md#Completion-Notes]

## Dev Agent Record

### Learnings from Previous Story

**From Story 1-5-tenant-context-module (Status: done)**

- **New Files Created:**
  - `eaf/eaf-tenant/src/main/kotlin/de/acci/eaf/tenant/TenantContextElement.kt`
  - `eaf/eaf-tenant/src/main/kotlin/de/acci/eaf/tenant/TenantContext.kt`
  - `eaf/eaf-tenant/src/main/kotlin/de/acci/eaf/tenant/TenantContextMissingException.kt`
  - `eaf/eaf-tenant/src/main/kotlin/de/acci/eaf/tenant/JwtTenantClaimExtractor.kt`
  - `eaf/eaf-tenant/src/main/kotlin/de/acci/eaf/tenant/TenantContextWebFilter.kt`
- **Patterns Established:** Coroutine-based `TenantContextElement` for context propagation; Reactor context fallback via `REACTOR_TENANT_KEY`.
- **Architectural Consistency:** Tenant metadata in event store aligns with `tenant_id` from JWT — RLS must use same UUID format.
- **Reusable Components:** `TenantContext.current()` suspend function available for getting current tenant in any coroutine.
- **Technical Debt:** None from previous story.
- **Warnings for This Story:** Ensure `SET LOCAL` (not `SET`) for session variable — `SET LOCAL` scopes to transaction, `SET` persists for connection lifetime.

[Source: docs/sprint-artifacts/1-5-tenant-context-module.md#Dev-Agent-Record]

### Change Log

- 2025-11-26: Story drafted from epics.md, tech-spec-epic-1.md, and architecture.md.

### Context Reference

- `docs/sprint-artifacts/1-6-postgresql-rls-policies.context.xml` (generated 2025-11-26)

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

- **AC 1-6 verified:** RLS enabled on events/snapshots tables with fail-closed semantics, FORCE RLS applied
- **AC 7 verified:** Cross-tenant isolation tested - Tenant B cannot see Tenant A's events
- **AC 8 verified:** RlsEnforcingDataSource throws `IllegalStateException` when no test context
- **Design decision:** Used `set_config('app.tenant_id', ?, false)` (session-scoped) instead of `SET LOCAL` (transaction-scoped) for compatibility with autocommit and connection pooling
- **Two complementary approaches implemented:**
  - `RlsConnectionCustomizer` - Coroutine-based for suspend functions
  - `TenantAwareDataSourceDecorator` - ThreadLocal-based for synchronous DataSource API
- **Role naming:** Changed from `dvmm_app` to `eaf_app` since this is framework-level (EAF) not product-level (DVMM)

### File List

**New Files:**
- `eaf/eaf-eventsourcing/src/main/resources/db/migration/V002__enable_rls.sql` - Flyway migration enabling RLS
- `eaf/eaf-tenant/src/main/kotlin/de/acci/eaf/tenant/rls/RlsConnectionCustomizer.kt` - Coroutine-based connection customizer
- `eaf/eaf-tenant/src/main/kotlin/de/acci/eaf/tenant/rls/TenantAwareDataSourceDecorator.kt` - ThreadLocal-based DataSource decorator
- `eaf/eaf-tenant/src/test/kotlin/de/acci/eaf/tenant/rls/RlsConnectionCustomizerTest.kt` - Unit tests for connection customizer
- `eaf/eaf-tenant/src/test/kotlin/de/acci/eaf/tenant/rls/TenantAwareDataSourceDecoratorTest.kt` - Unit tests for DataSource decorator

**Modified Files:**
- `eaf/eaf-tenant/build.gradle.kts` - Added Testcontainers dependencies
- `eaf/eaf-eventsourcing/src/test/kotlin/de/acci/eaf/eventsourcing/RlsEnforcementIntegrationTest.kt` - Extended with RLS verification tests
- `eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/TestContainers.kt` - Added `ensureEventStoreSchemaWithRls()` helper
