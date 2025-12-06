# AGENTS.md

This file provides guidance for AI coding assistants (OpenAI Codex, GitHub Copilot, Cursor, etc.) when working with this repository.

## Project Overview

**EAF/DVMM** is a Gradle multi-module monorepo with two main component groups:

- **EAF** (`eaf/`) - Enterprise Application Framework (reusable, zero product dependencies)
- **DVMM** (`dvmm/`) - Dynamic Virtual Machine Manager (product modules)

## Build Commands

```bash
# Pre-push gate (runs ktlint, Detekt, Konsist, unit & integration tests)
./gradlew clean build

# Run tests only
./gradlew test

# Single module build
./gradlew :dvmm:dvmm-app:build
./gradlew :eaf:eaf-core:build

# Code coverage (70% minimum)
./gradlew koverHtmlReport          # Per-module reports
./gradlew :koverHtmlReport         # Merged report (root)
./gradlew koverVerify              # Verify 70% threshold

# Mutation testing (70% threshold)
./gradlew pitest
```

## Module Structure

### EAF Framework (`eaf/`)
| Module | Purpose |
|--------|---------|
| `eaf-core` | Domain primitives (Entity, AggregateRoot, ValueObject, DomainEvent) |
| `eaf-eventsourcing` | Event Store interfaces, projection base classes |
| `eaf-tenant` | Multi-tenancy with PostgreSQL RLS |
| `eaf-auth` | IdP-agnostic authentication interfaces |
| `eaf-auth-keycloak` | Keycloak implementation (reusable across products) |
| `eaf-testing` | Test utilities (InMemoryEventStore, TestClock) |

### DVMM Product (`dvmm/`)
| Module | Purpose | Constraints |
|--------|---------|-------------|
| `dvmm-domain` | Business logic, aggregates | NO Spring dependencies |
| `dvmm-application` | Use cases, command/query handlers | - |
| `dvmm-api` | REST controllers, DTOs | - |
| `dvmm-infrastructure` | Persistence, external integrations | - |
| `dvmm-app` | Spring Boot entry point | - |

## Critical Architecture Rules

**These rules are enforced by Konsist tests and CI will block violations:**

1. **EAF modules MUST NOT import from `de.acci.dvmm.*`**
2. **DVMM modules CAN import from `de.acci.eaf.*`**
3. **`dvmm-domain` MUST NOT import from `org.springframework.*`**

## Tech Stack

- **Kotlin 2.2** with context parameters (`-Xcontext-parameters`)
- **Spring Boot 3.5** with WebFlux/Coroutines
- **Gradle 9.2** with Version Catalog (`gradle/libs.versions.toml`)
- **PostgreSQL** with Row-Level Security
- **jOOQ 3.20** with DDLDatabase for type-safe SQL
- **JUnit 6** + MockK + Testcontainers
- **Konsist** for architecture testing
- **Pitest** for mutation testing

### MockK Unit Testing Patterns

**Use `any()` for ALL parameters when stubbing functions with default arguments.**

When stubbing Kotlin functions that have default parameters, MockK evaluates default values at stub setup time, not at call time:

```kotlin
// Given a handler with a default parameter:
public suspend fun handle(
    command: RejectVmRequestCommand,
    correlationId: CorrelationId = CorrelationId.generate()  // Default generates UUID
)

// ❌ WRONG - MockK evaluates the default at setup time
coEvery { handler.handle(any()) } returns result.success()

// ✅ CORRECT - Explicitly match ALL parameters
coEvery { handler.handle(any(), any()) } returns result.success()
```

### Coroutine Event Listener Patterns

**Launch handlers independently** when multiple handlers react to the same event:

```kotlin
// ✅ CORRECT - Handlers execute independently
@EventListener
fun onEvent(event: SomeEvent) {
    scope.launch {
        try { handlerA.handle(event) }
        catch (e: Exception) { logger.error(e) { "Handler A failed" } }
    }
    scope.launch {
        try { handlerB.handle(event) }
        catch (e: Exception) { logger.error(e) { "Handler B failed" } }
    }
}

// ❌ WRONG - Handler B blocked if Handler A fails
@EventListener
fun onEvent(event: SomeEvent) {
    scope.launch {
        handlerA.handle(event)
        handlerB.handle(event)  // Never runs if A throws!
    }
}
```

**Rationale:** In eventual consistency architectures, handlers should be independent. Failure of one shouldn't prevent others from executing.

### Event Sourcing Defensive Patterns

**Always check for empty event lists when loading events to determine `expectedVersion`.**

```kotlin
// ✅ CORRECT - Check for empty events before using size
val currentEvents = eventStore.load(aggregateId)
if (currentEvents.isEmpty()) {
    logger.error { "Cannot append: aggregate $aggregateId not found" }
    return
}
val expectedVersion = currentEvents.size.toLong()

// ❌ WRONG - Empty list silently becomes expectedVersion = 0
val currentEvents = eventStore.load(aggregateId)
val expectedVersion = currentEvents.size.toLong()  // 0 if empty!
```

**Why empty results are dangerous:**
1. Aggregate doesn't exist (data corruption)
2. Race condition deleted events
3. Wrong aggregate ID passed

Failing silently with `expectedVersion = 0` causes concurrency conflicts.

**When adding new domain events, ALWAYS update event deserializers.**

```kotlin
// In *EventDeserializer.resolveEventClass():
"VmRequestProvisioningStarted" -> VmRequestProvisioningStarted::class.java  // Don't forget!
```

**Checklist for new domain events:**
1. Create event class in `dvmm-domain/.../events/`
2. Add case to `resolveEventClass()` in deserializer
3. Add deserialization test
4. If aggregate handles event, add `apply()` method

Without deserializer registration, aggregate loads throw `IllegalArgumentException: Unknown event type`.

### CQRS Command Handler Pattern

**Command handlers must update BOTH write-side AND read-side together.**

In CQRS/Event Sourcing, the write-side (event store) and read-side (projections) are separate. Command handlers must update both:

```kotlin
// ✅ CORRECT - Update BOTH write-side and read-side
public suspend fun handle(command: MarkProvisioningCommand): Result<Unit, Error> {
    // 1. Write-side: Persist domain event
    aggregate.markProvisioning(metadata)
    eventStore.append(aggregate.id.value, aggregate.uncommittedEvents, expectedVersion)

    // 2. Read-side: Update projection (timeline, status views)
    timelineUpdater.addTimelineEvent(NewTimelineEvent(
        eventType = TimelineEventType.PROVISIONING_STARTED,
        details = "VM provisioning has started"
    ))
    return Unit.success()
}

// ❌ WRONG - Only updates write-side, forgets read-side projection
eventStore.append(aggregate.id.value, aggregate.uncommittedEvents, expectedVersion)
// Missing: timelineUpdater.addTimelineEvent(...) ← Users won't see state change!
```

**Why this matters:** Write-side is easy to verify (event persisted), but read-side (timeline events, status views) is easy to forget. Acceptance criteria like "Timeline event added" won't be satisfied.

**Reference implementations:** `CreateVmRequestHandler`, `ApproveVmRequestHandler`, `MarkVmRequestProvisioningHandler`

### VMware VCF SDK 9.0 Patterns

The project uses **VCF SDK 9.0** for VMware vCenter integration.

**Key Patterns:**
- **PropertyCollector:** Fetch properties via `PropertySpec` + `ObjectSpec` + `FilterSpec`
- **SearchIndex:** Use inventory paths like `datacenter/host/clusterName` to find objects
- **Port 443:** `VcenterClientFactory` only supports port 443 (use `VcsimAdapter` mock for testing)

```kotlin
// SearchIndex navigation
val clusterRef = vimPort.findByInventoryPath(searchIndex, "MyDatacenter/host/MyCluster")
val datastoreRef = vimPort.findByInventoryPath(searchIndex, "MyDatacenter/datastore/MyDatastore")
```

## Frontend (dvmm-web)

The frontend is a **React 19 + TypeScript + Vite** application at `dvmm/dvmm-web/`.

### Frontend Commands

```bash
cd dvmm/dvmm-web

npm run dev          # Start dev server (port 5173)
npm run build        # Type-check and build for production
npm run test         # Run Vitest unit tests
npm run test:e2e     # Run Playwright E2E tests
npm run lint         # Run ESLint
```

### Key Constraints

- **React Compiler** handles memoization - manual `useMemo`/`useCallback`/`memo` is PROHIBITED
- Use function components with TypeScript - class components are FORBIDDEN
- **React Hook Form:** Use `useWatch` instead of `watch()` for React Compiler compatibility

```tsx
// FORBIDDEN - watch() causes React Compiler lint warnings
const { watch } = useForm()
const value = watch('fieldName')

// CORRECT - useWatch is React Compiler compatible
import { useForm, useWatch } from 'react-hook-form'
const { control } = useForm()
const value = useWatch({ control, name: 'fieldName' })
```

### Floating Promises and the `void` Operator

**All promises must be explicitly handled - either awaited or marked as fire-and-forget with `void`.**

```tsx
// FORBIDDEN - Floating promise (ESLint error)
const handleClick = () => {
  navigate('/dashboard')  // Returns Promise in React Router v6!
}

// CORRECT - Use `void` for fire-and-forget
const handleClick = () => {
  void navigate('/dashboard')
}
```

**Common patterns requiring `void`:**
```tsx
void navigate('/path')                                      // React Router v6
void queryClient.invalidateQueries({ queryKey: ['data'] })  // TanStack Query
void refetch()                                              // TanStack Query
void auth.signinRedirect({ state: { returnTo: '/' } })      // OIDC
```

**Rationale:** React Router v6's `navigate()` returns a Promise (unlike v5). ESLint rule `@typescript-eslint/no-floating-promises` catches these at compile time.

### E2E Testing with Playwright

Use **@seontechnologies/playwright-utils** fixtures for consistent patterns:

```tsx
// Use playwright-utils fixtures for API requests
import { test } from '@seontechnologies/playwright-utils/fixtures'

test('creates VM request', async ({ apiRequest }) => {
  const { status } = await apiRequest({
    method: 'POST',
    path: '/api/vm-requests',
    data: { vmName: 'web-01', cpuCores: 4 }
  })
  expect(status).toBe(201)
})
```

```tsx
// Use recurse for polling async conditions
import { recurse } from '@seontechnologies/playwright-utils/recurse'

const result = await recurse(
  () => page.locator('[data-testid="status"]').textContent(),
  (text) => text === 'Provisioned',
  { timeout: 30000 }
)
```

**Key features:** `apiRequest` fixture, `recurse` polling, `log` integration, network interception, auth session persistence.

**Security: Avoid dynamic RegExp in E2E tests (CWE-1333 ReDoS)**

```tsx
// FORBIDDEN - Dynamic RegExp can cause ReDoS
await expect(page).toHaveURL(new RegExp(`/admin/requests/${requestId}`))

// CORRECT - String literal with interpolation
await expect(page).toHaveURL(`/admin/requests/${requestId}`)
```

**ESLint enforces this:** The `security/detect-non-literal-regexp` rule blocks dynamic RegExp construction.

### Vitest Unit Testing Patterns

**Module mocking:** Use `vi.hoisted()` to ensure mocks exist before ES module imports:

```tsx
const mockUseAuth = vi.hoisted(() => vi.fn(() => ({ user: { access_token: 'token' } })))
vi.mock('react-oidc-context', () => ({ useAuth: mockUseAuth }))
```

**Sequential responses:** Use `mockResolvedValueOnce()` for deterministic refetch/retry tests:

```tsx
mockGetData.mockResolvedValueOnce({ status: 'PENDING' }).mockResolvedValueOnce({ status: 'APPROVED' })
```

### TanStack Query Polling

For real-time data (admin queues, dashboards), use both `staleTime` AND `refetchInterval`:

```tsx
useQuery({
  staleTime: 10000,  // Stale after 10s (triggers refetch on access)
  refetchInterval: 30000 + Math.floor(Math.random() * 5000), // Jitter prevents thundering herd
  refetchIntervalInBackground: false,
})
```

## jOOQ Code Generation

jOOQ uses **DDLDatabase** to generate code from SQL DDL files without a running database.

```bash
# Regenerate jOOQ code
./gradlew :dvmm:dvmm-infrastructure:generateJooq
```

### Adding New Tables

**IMPORTANT:** Two SQL files must be kept in sync - Flyway migrations and jooq-init.sql.

1. Add migration to `db/migration/`
2. Update `dvmm/dvmm-infrastructure/src/main/resources/db/jooq-init.sql` with H2-compatible DDL:
   - Use quoted uppercase identifiers (jOOQ DDLDatabase uses H2)
   - Example: `CREATE TABLE "DOMAIN_EVENTS"` not `CREATE TABLE domain_events`
3. Wrap PostgreSQL-specific statements with ignore tokens:
   ```sql
   -- [jooq ignore start]
   ALTER TABLE my_table ENABLE ROW LEVEL SECURITY;
   -- RLS policies MUST include both USING (reads) AND WITH CHECK (writes)
   CREATE POLICY tenant_isolation ON my_table
       FOR ALL
       USING (tenant_id = ...)
       WITH CHECK (tenant_id = ...);
   -- [jooq ignore stop]
   ```
4. Run `./gradlew :dvmm:dvmm-infrastructure:generateJooq`
5. Verify: `./gradlew :dvmm:dvmm-infrastructure:compileKotlin`

**Checklist:** Flyway migration + jooq-init.sql + ignore tokens + RLS WITH CHECK + regenerate + integration tests for FK + tests pass.

**FK Constraints in Tests:** When adding FK constraints, test helpers must create parent records first using `ON CONFLICT DO NOTHING` for idempotency. Cleanup should use `TRUNCATE ... CASCADE`.

### Projection Column Symmetry (CRITICAL)

**CQRS projection repositories must handle all columns symmetrically in both read and write operations.**

When adding a new column to a projection table:
1. Add the column to the Flyway migration + jooq-init.sql
2. Add the column to `mapRecord()` (read path)
3. Add the column to `insert()` (write path)
4. **Compile fails if any step is missed** - use sealed class pattern

```kotlin
sealed interface ProjectionColumns {
    data object Id : ProjectionColumns
    data object NewColumn : ProjectionColumns  // Add new columns here
    companion object { val all = listOf(Id, NewColumn) }
}

// Exhaustive when expressions force handling all columns
private fun mapColumn(record: Record, column: ProjectionColumns) = when (column) {
    ProjectionColumns.Id -> record.get(TABLE.ID)
    ProjectionColumns.NewColumn -> record.get(TABLE.NEW_COLUMN)  // Compiler forces this
}
```

**See:** `VmRequestProjectionRepository.kt` for the reference implementation.

## Code Style Requirements

### Imports
```kotlin
// CORRECT - Explicit imports
import de.acci.eaf.core.domain.AggregateRoot
import de.acci.eaf.core.domain.DomainEvent

// FORBIDDEN - Wildcard imports
import de.acci.eaf.core.domain.*
```

### Named Arguments
```kotlin
// CORRECT - Named arguments for >2 parameters
val request = VmRequest.create(
    tenantId = tenantId,
    requesterId = userId,
    vmName = "web-server-01",
    cpuCores = 4,
    memoryGb = 16
)

// FORBIDDEN - Positional arguments for >2 parameters
val request = VmRequest.create(tenantId, userId, "web-server-01", 4, 16)
```

### Error Handling
```kotlin
// CORRECT - Domain-specific exceptions with context
throw VmProvisioningException(
    vmRequestId = requestId,
    reason = VmProvisioningFailure.RESOURCE_EXHAUSTED,
    details = "vCenter cluster 'prod-01' has insufficient memory"
)

// FORBIDDEN - Generic exceptions
throw RuntimeException("VM creation failed")
```

### Security Patterns (Multi-Tenant)

**Resource access errors MUST return 404 to prevent tenant enumeration:**

```kotlin
// CORRECT - Return 404 for both NotFound and Forbidden
when (result.error) {
    is NotFound, is Forbidden -> ResponseEntity.notFound().build()
}
// Log actual error for audit
logger.warn { "Access error: ${result.error}" }

// FORBIDDEN - Returning 403 reveals resource exists in another tenant
```

### Dependency Injection
```kotlin
// CORRECT - Constructor injection (testable)
class VmService(
    private val httpClient: HttpClient
)

// FORBIDDEN - Hard-coded dependencies (untestable)
class VmService {
    private val httpClient = HttpClient.newHttpClient()
}
```

## Testing Requirements

- Write tests BEFORE implementation (Tests First)
- Achieve ≥70% line coverage per module
- Achieve ≥70% mutation score (Pitest)
- Run `./gradlew clean build` before committing

### Test Order for New Features
1. Integration test (proves feature works end-to-end)
2. Unit tests (prove individual components)
3. Implementation

## Git Conventions

### Commit Messages (Conventional Commits)
```
<type>: <description>

[optional body]
```

**Types:** `feat`, `fix`, `docs`, `refactor`, `test`, `chore`

**Rules:**
- Lowercase type and description
- No period at end
- Under 72 characters
- Reference Jira: `[DVMM-123] feat: ...`

### Branch Naming
| Pattern | Example |
|---------|---------|
| `feature/<story-id>-<description>` | `feature/story-1.2-eaf-core-module` |
| `fix/<issue>-<description>` | `fix/tenant-leak-in-projections` |
| `docs/<description>` | `docs/claude-md-setup` |

## Anti-Patterns to Avoid

1. **Deferred architectural decisions** - Decide NOW or raise blocking issue
2. **Untestable code** - Always use constructor injection
3. **Silent failures** - Log with context and re-throw appropriately
4. **Over-engineering** - Keep it simple until complexity is needed
5. **Copy-paste without understanding** - Adapt code to specific context
6. **Parameter bag (entities with invalid state)** - Never create entities with placeholder/invalid values just to pass parameters. Create a dedicated value object instead:

```kotlin
// FORBIDDEN - Entity with invalid state
val config = VmwareConfiguration(passwordEncrypted = ByteArray(0), ...)

// REQUIRED - Value object with only needed fields
val params = VcenterConnectionParams(vcenterUrl = url, username = user, ...)
```

**Why:** Entities should NEVER exist in invalid states. Value objects preserve invariants and make APIs type-safe.

## Quality Gates

| Gate | Threshold | Enforcement |
|------|-----------|-------------|
| Test Coverage | ≥70% | CI blocks merge |
| Mutation Score | ≥70% | CI blocks merge |
| Architecture Tests | All pass | CI blocks merge |
| Security Scan | Zero critical | CI blocks merge |

## Key Documentation

| Document | Path |
|----------|------|
| Architecture | `docs/architecture.md` |
| PRD | `docs/prd.md` |
| Sprint Status | `docs/sprint-artifacts/sprint-status.yaml` |
| Test Design | `docs/test-design-system.md` |
