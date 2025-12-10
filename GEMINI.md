# GEMINI.md

This file provides guidance to Google Gemini when working with code in this repository.

## Build Commands

```bash
# Build entire project (pre-push gate – runs ktlint, Detekt, Konsist, unit & integration tests)
./gradlew clean build

# Build specific module
./gradlew :dvmm:dvmm-app:build
./gradlew :eaf:eaf-core:build

# Run tests
./gradlew test

# Run single test class
./gradlew :dvmm:dvmm-app:test --tests "de.acci.dvmm.architecture.ArchitectureTest"

# Run single test method
./gradlew :dvmm:dvmm-app:test --tests "ArchitectureTest.eaf modules must not depend on dvmm modules"

# Check code coverage (Kover) - 70% minimum required
./gradlew koverHtmlReport          # Per-module reports
./gradlew :koverHtmlReport         # Merged report (root)
./gradlew koverVerify              # Verify 70% threshold

# Run mutation testing (Pitest) - 70% threshold
./gradlew pitest
```

## Architecture Overview

This is a **Gradle multi-module monorepo** containing two main component groups:

### EAF (Enterprise Application Framework) - `eaf/`
Reusable framework modules with **zero product dependencies**:
- `eaf-core` - Domain primitives (Entity, AggregateRoot, ValueObject, DomainEvent)
- `eaf-eventsourcing` - Event Store interfaces and projection base classes
- `eaf-tenant` - Multi-tenancy with PostgreSQL RLS support
- `eaf-auth` - IdP-agnostic authentication (interfaces only)
- `eaf-auth-keycloak` - Keycloak implementation (reusable across products)
- `eaf-testing` - Test utilities (InMemoryEventStore, TestClock, TenantTestContext)

**Auth Abstraction Pattern:** `eaf-auth` defines interfaces, dedicated EAF modules provide IdP implementations (e.g., `eaf-auth-keycloak`). Products only configure which implementation to use—no auth rewrite needed.

### DVMM (Dynamic Virtual Machine Manager) - `dvmm/`
Product modules following Hexagonal Architecture:
- `dvmm-domain` - Business logic, aggregates (NO Spring dependencies allowed)
- `dvmm-application` - Use cases, command/query handlers
- `dvmm-api` - REST controllers, DTOs
- `dvmm-infrastructure` - Persistence, external integrations (VMware, Email)
- `dvmm-app` - Spring Boot application entry point

### Build Logic - `build-logic/`
Convention plugins for consistent configuration:
- `eaf.kotlin-conventions` - Kotlin 2.2, JVM 21, Explicit API mode, context parameters
- `eaf.spring-conventions` - Spring Boot 3.5 with WebFlux
- `eaf.test-conventions` - JUnit 6, Kover (70% coverage), Testcontainers, Konsist
- `eaf.pitest-conventions` - Mutation testing (70% threshold)

## Critical Architecture Rules (ADR-001)

**IMPORTANT: These rules are enforced by Konsist tests in `ArchitectureTest.kt`. CI will block any violations.**

- EAF modules MUST NOT import from `de.acci.dvmm.*`
- DVMM modules CAN import from `de.acci.eaf.*`
- `dvmm-domain` MUST NOT import from `org.springframework.*`

## Tech Stack

- **Kotlin 2.2** with context parameters (`-Xcontext-parameters`)
- **Spring Boot 3.5** with WebFlux/Coroutines
- **Gradle 9.2** with Version Catalog (`gradle/libs.versions.toml`)
- **PostgreSQL** with Row-Level Security for multi-tenancy
- **jOOQ 3.20** with DDLDatabase for type-safe SQL
- **JUnit 6** + MockK + Testcontainers
- **Konsist** for architecture testing
- **Pitest** for mutation testing

### MockK Unit Testing Patterns

**Use `any()` for ALL parameters when stubbing functions with default arguments.**

When stubbing Kotlin functions that have default parameters, MockK evaluates default values at stub setup time, not at call time. This creates unexpected matchers:

```kotlin
// Given a handler with a default parameter:
public suspend fun handle(
    command: RejectVmRequestCommand,
    correlationId: CorrelationId = CorrelationId.generate()  // Default generates UUID
)

// ❌ WRONG - MockK evaluates the default at setup time, creating eq(specific-uuid)
coEvery { handler.handle(any()) } returns result.success()
// This creates: handler.handle(any(), eq("550e8400-e29b-41d4-a716-446655440000"))
// Calls with different correlationIds won't match!

// ✅ CORRECT - Explicitly match ALL parameters including defaulted ones
coEvery { handler.handle(any(), any()) } returns result.success()
```

**Rationale:**
- MockK stub setup evaluates all parameters immediately
- Default parameter expressions (like `UUID.randomUUID()`) execute during setup
- The generated value becomes an `eq()` matcher, not `any()`
- Result: test passes if same UUID by chance, fails randomly otherwise

### Coroutine Event Listener Patterns

**Launch handlers independently** when multiple handlers react to the same event:

```kotlin
// CORRECT - Handlers execute independently
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

// WRONG - Handler B blocked if Handler A fails
@EventListener
fun onEvent(event: SomeEvent) {
    scope.launch {
        handlerA.handle(event)
        handlerB.handle(event)  // Never runs if A throws!
    }
}
```

**Rationale:** In eventual consistency architectures, handlers should be independent. Failure of one shouldn't prevent others from executing.

### Coroutine CancellationException Handling

**NEVER catch `CancellationException` with a broad `catch (e: Exception)` - always rethrow it.**

Kotlin's structured concurrency uses `CancellationException` to propagate cancellation. Catching it breaks cancellation:

```kotlin
// WRONG - Swallows coroutine cancellation
private suspend fun doWork() {
    try {
        eventStore.append(aggregateId, events, version)
    } catch (e: Exception) {
        logger.error(e) { "Failed" }
        return  // CancellationException caught and not rethrown!
    }
}

// CORRECT - Explicitly rethrow CancellationException
import kotlin.coroutines.cancellation.CancellationException

private suspend fun doWork() {
    try {
        eventStore.append(aggregateId, events, version)
    } catch (e: CancellationException) {
        throw e  // Allow proper coroutine cancellation
    } catch (e: Exception) {
        logger.error(e) { "Failed" }
        return
    }
}
```

**Why:** CancellationException is used by `withTimeout`, `Job.cancel()`, and scope cancellation. Catching it prevents parent coroutines from being notified, and resources may not clean up properly.

### Event Sourcing Defensive Patterns

**Always check for empty event lists when loading events to determine `expectedVersion`.**

```kotlin
// CORRECT - Check for empty events before using size
val currentEvents = eventStore.load(aggregateId)
if (currentEvents.isEmpty()) {
    logger.error { "Cannot append: aggregate $aggregateId not found" }
    return
}
val expectedVersion = currentEvents.size.toLong()

// WRONG - Empty list silently becomes expectedVersion = 0
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

**CQRS Command Handlers: Update Write-Side AND Read-Side Together**

Command handlers must update both write-side (event store) AND read-side (projections):

```kotlin
// CORRECT - Update BOTH write-side and read-side
eventStore.append(aggregate.id.value, aggregate.uncommittedEvents, expectedVersion)
timelineUpdater.addTimelineEvent(NewTimelineEvent(eventType = PROVISIONING_STARTED, ...))

// WRONG - Only updates write-side, forgets read-side projection
eventStore.append(aggregate.id.value, aggregate.uncommittedEvents, expectedVersion)
// Missing: timelineUpdater.addTimelineEvent(...) ← AC "Timeline event added" NOT satisfied!
```

**Why this matters:** Write-side is easy to verify (event persisted), but read-side (timeline, status views) is easy to forget. Users won't see the state change. Follow patterns in `CreateVmRequestHandler`, `ApproveVmRequestHandler`.

**CQRS Partial Failure Observability:**

When operations span multiple aggregates, use "CRITICAL" prefix + full context for alerting:

```kotlin
// CORRECT - Detailed logging for partial failures
logger.error {
    "CRITICAL: [Step 2/3] Failed to emit VmRequestReady for request $requestId " +
        "after VM $vmId was already marked provisioned. " +
        "System may be in inconsistent state. Error: ${error}"
}
```

**Why:** Partial success (aggregate A updated, aggregate B failed) is silent without proper logging. "CRITICAL" enables alerting; both IDs help operators reconcile.

### VMware VCF SDK 9.0 Patterns

The project uses **VCF SDK 9.0** (`com.vmware.sdk:vsphere-utils:9.0.0.0`) for VMware vCenter integration.

**PropertyCollector Pattern:** Fetch properties via `PropertySpec` + `ObjectSpec` + `FilterSpec`:

```kotlin
val propSpec = PropertySpec().apply {
    type = "ClusterComputeResource"
    pathSet.add("host")
}
val objSpec = ObjectSpec().apply {
    obj = clusterRef
    isSkip = false
}
val filterSpec = PropertyFilterSpec().apply {
    propSet.add(propSpec)
    objectSet.add(objSpec)
}
val result = vimPort.retrievePropertiesEx(propertyCollector, listOf(filterSpec), RetrieveOptions())
```

**SearchIndex Navigation:** Use inventory paths (datacenter/folder/object):

```kotlin
// Find cluster: datacenter/host/clusterName
val clusterRef = vimPort.findByInventoryPath(searchIndex, "MyDatacenter/host/MyCluster")

// Find datastore: datacenter/datastore/datastoreName
val datastoreRef = vimPort.findByInventoryPath(searchIndex, "MyDatacenter/datastore/MyDatastore")
```

**Port 443 Constraint:** `VcenterClientFactory` only supports port 443. For VCSIM testing (dynamic ports), use `VcsimAdapter` mock.

**Timeout Layering (Critical for Nested Async Operations):**

When you have nested async operations, the outer timeout MUST be longer than all inner timeouts combined:

```kotlin
// CORRECT - Outer timeout (5 min) > inner timeouts (clone ~60s + IP detection 120s)
private val vmwareToolsTimeoutMs: Long = 120_000  // Wait for IP detection
private val createVmTimeoutMs: Long = 300_000    // 5 minutes total

suspend fun createVm(spec: VmSpec) = executeResilient(
    name = "createVm",
    operationTimeoutMs = createVmTimeoutMs  // 5 min covers clone + IP wait
) {
    cloneVm(spec)  // ~60s
    waitForIpAddress(vmwareToolsTimeoutMs)  // 120s
}

// WRONG - Outer timeout (60s) < inner timeout (120s IP detection)
// Operation will be killed at 60s before IP detection completes!
```

**Rule:** Calculate total worst-case inner duration, then add buffer for outer timeout.

## Frontend (dvmm-web)

The frontend is a **React 19 + TypeScript + Vite** application located at `dvmm/dvmm-web/`.

### Frontend Tech Stack

- **React 19.2** with React Compiler (automatic optimization)
- **Vite 7.2** with @vitejs/plugin-react (Babel-based)
- **TypeScript 5.9**
- **Tailwind CSS 4** with shadcn/ui components
- **Vitest** for unit tests, **Playwright** for E2E tests
- **@seontechnologies/playwright-utils** for E2E test fixtures

### Frontend Commands

```bash
cd dvmm/dvmm-web

npm run dev          # Start dev server (port 5173)
npm run build        # Type-check and build for production
npm run test         # Run Vitest unit tests
npm run test:e2e     # Run Playwright E2E tests
npm run test:e2e:ui  # Run with Playwright UI mode
npm run lint         # Run ESLint
```

### Frontend Test File Convention

**Tests MUST be colocated with their source files.** Do NOT use `__tests__` directories.

```tsx
// CORRECT - Test file next to source file
src/components/Button.tsx
src/components/Button.test.tsx

// FORBIDDEN - __tests__ directory (vitest will ignore these)
src/components/__tests__/Button.test.tsx
```

Enforced via `vitest.config.ts` which excludes `**/__tests__/**`.

### React Coding Standards

**React Compiler handles memoization automatically. Manual optimization is PROHIBITED.**

```tsx
// FORBIDDEN - Manual memoization (ESLint will error)
import { useMemo, useCallback, memo } from 'react'
const memoizedValue = useMemo(() => computeExpensive(a, b), [a, b])

// CORRECT - Let React Compiler optimize automatically
const value = computeExpensive(a, b)
function MyComponent() { ... }
```

### React Hook Form: useWatch over watch

**Use `useWatch` instead of `watch()` for React Compiler compatibility.**

```tsx
// FORBIDDEN - watch() causes React Compiler lint warnings
const { watch } = useForm()
const value = watch('fieldName')  // ESLint: react-hooks/incompatible-library

// CORRECT - useWatch is React Compiler compatible
import { useForm, useWatch } from 'react-hook-form'

const { control } = useForm()
const value = useWatch({ control, name: 'fieldName' })
```

**Rationale:**
- `watch()` returns a subscription that React Compiler cannot safely memoize
- `useWatch` is a separate hook designed to work with React's rules of hooks

### Floating Promises and the `void` Operator

**All promises must be explicitly handled - either awaited or marked as intentional fire-and-forget with `void`.**

```tsx
// FORBIDDEN - Floating promise (ESLint error: @typescript-eslint/no-floating-promises)
const handleClick = () => {
  navigate('/dashboard')  // Returns a promise in React Router v6!
}

// CORRECT - Use `void` for intentional fire-and-forget
const handleClick = () => {
  void navigate('/dashboard')
}

// CORRECT - Or use async/await if you need to wait
const handleClick = async () => {
  await navigate('/dashboard')
}
```

**Common fire-and-forget patterns requiring `void`:**

```tsx
void navigate('/path')                                      // React Router v6
void queryClient.invalidateQueries({ queryKey: ['data'] })  // TanStack Query
void refetch()                                              // TanStack Query
void auth.signinRedirect({ state: { returnTo: '/' } })      // OIDC
```

**Rationale:**
- Unhandled promises hide errors silently - `void` makes intent explicit
- React Router v6's `navigate()` returns a Promise (unlike v5)
- ESLint rule `@typescript-eslint/no-floating-promises` catches these bugs at compile time

### E2E Testing with Playwright

**Use playwright-utils fixtures** for consistent testing patterns:

```tsx
// Use playwright-utils fixtures for API requests
import { test } from '@seontechnologies/playwright-utils/fixtures'

test('creates VM request', async ({ apiRequest }) => {
  const { status, body } = await apiRequest({
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

**Key playwright-utils features:**
- `apiRequest` fixture - Typed HTTP client for backend API testing
- `recurse` - Polling utility for async conditions
- `log` - Integrated logging with Playwright reports
- Network interception and mocking utilities
- Auth session persistence between test runs

**Security: Avoid dynamic RegExp in E2E tests (CWE-1333 ReDoS)**

```tsx
// FORBIDDEN - Dynamic RegExp can cause ReDoS with malicious input
const requestId = await getRequestId() // User-controlled value
await expect(page).toHaveURL(new RegExp(`/admin/requests/${requestId}`))

// CORRECT - String literal with interpolation
await expect(page).toHaveURL(`/admin/requests/${requestId}`)

// CORRECT - If regex truly needed, use static pattern
await expect(page).toHaveURL(/\/admin\/requests\/[\w-]+/)
```

**ESLint enforces this:** The `security/detect-non-literal-regexp` rule blocks dynamic RegExp construction.

### Vitest Unit Testing Patterns

**Use `vi.hoisted()` for module mocks** - ensures mock is created before ES modules are imported:

```tsx
// CORRECT - vi.hoisted() ensures mock exists before import
const mockUseAuth = vi.hoisted(() =>
  vi.fn(() => ({ user: { access_token: 'test-token' }, isAuthenticated: true }))
)

vi.mock('react-oidc-context', () => ({ useAuth: mockUseAuth }))

// Override in tests:
mockUseAuth.mockReturnValue({ user: null, isAuthenticated: false })
```

**Use `mockResolvedValueOnce()` for sequential responses** - deterministic ordering for refetch/retry tests:

```tsx
// CORRECT - Different response per call
mockGetData
  .mockResolvedValueOnce({ status: 'PENDING' })   // First call
  .mockResolvedValueOnce({ status: 'APPROVED' })  // After refetch
```

### TanStack Query Polling Patterns

**Real-time data needs both `staleTime` AND `refetchInterval`:**
- `staleTime` controls when cached data is stale (triggers refetch on next access)
- `refetchInterval` actively polls in background (for admin queues, dashboards)

```tsx
// CORRECT - Active polling with jitter to prevent thundering herd
useQuery({
  queryKey: ['admin', 'pending-requests'],
  staleTime: 10000,  // Stale after 10s
  refetchInterval: 30000 + Math.floor(Math.random() * 5000), // 30-35s with jitter
  refetchIntervalInBackground: false,  // Don't poll when tab inactive
})

// WRONG - Only staleTime = no automatic polling (users must interact)
useQuery({ staleTime: 30000 })  // Admin won't see new requests!
```

## jOOQ Code Generation

jOOQ uses **DDLDatabase** to generate code from SQL DDL files without a running database.

```bash
# Regenerate jOOQ code
./gradlew :dvmm:dvmm-infrastructure:generateJooq
```

### Adding New Tables

**IMPORTANT:** Two SQL files must be kept in sync - Flyway migrations (production) and jooq-init.sql (code generation).

1. Add migration to `db/migration/`
2. Update `dvmm/dvmm-infrastructure/src/main/resources/db/jooq-init.sql` with H2-compatible DDL:
   - Use quoted uppercase identifiers (jOOQ DDLDatabase uses H2 which generates uppercase)
   - Example: `CREATE TABLE "DOMAIN_EVENTS"` not `CREATE TABLE domain_events`
3. Wrap PostgreSQL-specific statements with ignore tokens:
   ```sql
   -- [jooq ignore start]
   ALTER TABLE my_table ENABLE ROW LEVEL SECURITY;
   -- CRITICAL: RLS policies MUST include both USING (reads) AND WITH CHECK (writes)
   CREATE POLICY tenant_isolation ON my_table
       FOR ALL
       USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
       WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
   -- [jooq ignore stop]
   ```
4. Run `./gradlew :dvmm:dvmm-infrastructure:generateJooq`
5. Verify generated code compiles: `./gradlew :dvmm:dvmm-infrastructure:compileKotlin`

**Checklist:** Flyway migration created, jooq-init.sql updated, ignore tokens added, RLS policies include both USING AND WITH CHECK, jOOQ regenerated, integration tests updated for FK constraints, tests pass.

**FK Constraints in Tests:** When adding FK constraints, test helpers must create parent records first using `ON CONFLICT DO NOTHING` for idempotency. Cleanup should use `TRUNCATE ... CASCADE`.

### Projection Column Symmetry (CRITICAL)

**CQRS projection repositories must handle all columns symmetrically in both read and write operations.**

When adding a new column to a projection table:
1. Add the column to the Flyway migration
2. Add the column to jooq-init.sql
3. Add the column to `mapRecord()` (read path)
4. Add the column to `insert()` (write path)
5. **Compile fails if any step is missed** - use sealed class pattern

```kotlin
// CORRECT - Sealed class pattern ensures read/write symmetry
sealed interface ProjectionColumns {
    data object Id : ProjectionColumns
    data object NewColumn : ProjectionColumns  // Adding new column? Add here!

    companion object { val all = listOf(Id, NewColumn) }
}

private fun mapColumn(record: Record, column: ProjectionColumns): Any? = when (column) {
    ProjectionColumns.Id -> record.get(TABLE.ID)
    ProjectionColumns.NewColumn -> record.get(TABLE.NEW_COLUMN)  // Compiler forces this
}

private fun setColumn(step: InsertSetMoreStep<*>, column: ProjectionColumns, data: Projection): InsertSetMoreStep<*> = when (column) {
    ProjectionColumns.Id -> step.set(TABLE.ID, data.id)
    ProjectionColumns.NewColumn -> step.set(TABLE.NEW_COLUMN, data.newColumn)  // Compiler forces this
}
```

**Why this matters:** Without this pattern, jOOQ silently allows reading columns that aren't written during insert, causing data loss. The sealed class makes this a compile-time error.

**See:** `VmRequestProjectionRepository.kt` for the reference implementation.

## Git Conventions

### Commit Messages (Conventional Commits)

```
<type>: <description>

[optional body]

[optional footer]
```

**Types:**
| Type | Purpose | Example |
|------|---------|---------|
| `feat` | New feature | `feat: Implement VM request form validation` |
| `fix` | Bug fix | `fix: Correct tenant isolation in event store` |
| `docs` | Documentation | `docs: Add API endpoint documentation` |
| `refactor` | Code restructuring | `refactor: Extract tenant context to separate module` |
| `test` | Adding/fixing tests | `test: Add integration tests for approval workflow` |
| `chore` | Maintenance | `chore: Update Spring Boot to 3.5.1` |

**Rules:**
- Use lowercase for type and description
- No period at the end of the subject line
- Keep subject line under 72 characters
- Reference Jira issues when applicable: `[DVMM-123] feat: ...`

### Branch Naming

| Pattern | Purpose | Example |
|---------|---------|---------|
| `main` | Production-ready code | - |
| `feature/<story-id>-<description>` | New features | `feature/story-1.2-eaf-core-module` |
| `fix/<issue>-<description>` | Bug fixes | `fix/tenant-leak-in-projections` |
| `docs/<description>` | Documentation only | `docs/claude-md-setup` |

---

## Code Style Rules

### IMPORTANT: Explicit Imports Only

```kotlin
// CORRECT - Explicit imports
import de.acci.eaf.core.domain.AggregateRoot
import de.acci.eaf.core.domain.DomainEvent

// FORBIDDEN - Wildcard imports
import de.acci.eaf.core.domain.*
```

### IMPORTANT: Named Arguments for Clarity

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

### Architecture Violations (CI Blocks Merge)

```kotlin
// FORBIDDEN - EAF depending on DVMM
// File: eaf/eaf-core/src/main/kotlin/...
import de.acci.dvmm.domain.VmRequest  // BLOCKED BY KONSIST

// FORBIDDEN - Spring in domain layer
// File: dvmm/dvmm-domain/src/main/kotlin/...
import org.springframework.stereotype.Service  // BLOCKED BY KONSIST
```

---

## Testing Requirements

**YOU MUST:**
- Write tests BEFORE implementation (Tests First)
- Achieve ≥70% line coverage per module
- Achieve ≥70% mutation score (Pitest)
- Run `./gradlew clean build` before committing

### Test Order for New Features

```kotlin
// CORRECT order:
// 1. Integration test (proves the feature works end-to-end)
@Test
fun `VM request approval triggers provisioning`() {
    // Given: approved VM request
    // When: approval event processed
    // Then: VM provisioning started
}

// 2. Unit tests (prove individual components)
// 3. Implementation
```

---

## Security Patterns (Multi-Tenant)

**Resource access errors MUST return 404 to prevent tenant enumeration attacks.**

```kotlin
// CORRECT - Opaque error response prevents information leakage
when (result.error) {
    is NotFound -> ResponseEntity.notFound().build()
    is Forbidden -> ResponseEntity.notFound().build()  // Return 404, not 403!
}
// Log actual error type for audit trail
logger.warn { "Access denied: ${result.error}" }

// FORBIDDEN - Reveals resource exists in another tenant
when (result.error) {
    is NotFound -> ResponseEntity.notFound().build()
    is Forbidden -> ResponseEntity.status(403).build()  // Exposes tenant boundary!
}
```

**Rationale:**
- If `/api/admin/requests/123` returns 403, attacker knows request 123 exists (in another tenant)
- Returning 404 prevents enumeration; attacker cannot distinguish "doesn't exist" from "no access"
- Internal logging preserves audit trail for security investigations

---

## Anti-Patterns (PROHIBITED)

### 1. Deferred Architectural Decisions

```kotlin
// PROHIBITED - "TODO: decide later" comments
class EventStore {
    // TODO: Decide if we need snapshots later
    fun loadAggregate(id: UUID): Aggregate { ... }
}

// REQUIRED - Explicit decision NOW or raise blocking issue
class EventStore {
    // ADR-003: Snapshots after 100 events, configured per aggregate type
    fun loadAggregate(id: UUID, snapshotThreshold: Int = 100): Aggregate { ... }
}
```

### 2. Untestable Code

```kotlin
// PROHIBITED - Hard-coded dependencies
class VmService {
    private val httpClient = HttpClient.newHttpClient()  // Untestable!
}

// REQUIRED - Constructor injection
class VmService(
    private val httpClient: HttpClient  // Testable via mock
)
```

### 3. Missing Error Context

```kotlin
// PROHIBITED - Generic exceptions
throw RuntimeException("VM creation failed")

// REQUIRED - Domain-specific exceptions with context
throw VmProvisioningException(
    vmRequestId = requestId,
    reason = VmProvisioningFailure.RESOURCE_EXHAUSTED,
    details = "vCenter cluster 'prod-01' has insufficient memory"
)
```

### 4. Silent Failures

```kotlin
// PROHIBITED - Swallowing exceptions
try {
    vmwareClient.createVm(spec)
} catch (e: Exception) {
    logger.error("Failed")  // No context, no re-throw!
}

// REQUIRED - Proper error handling
try {
    vmwareClient.createVm(spec)
} catch (e: VmwareApiException) {
    logger.error(e) { "VM creation failed for request $requestId: ${e.message}" }
    throw VmProvisioningException(requestId, e)
}
```

### 5. Parameter Bag Anti-Pattern (Entities with Invalid State)

**Entities should NEVER exist in invalid states.** When you need a subset of entity data for an operation, create a dedicated value object instead of instantiating the entity with placeholder/invalid values.

```kotlin
// PROHIBITED - Creating entity with invalid data to pass parameters
val config = VmwareConfiguration(
    id = VmwareConfigurationId.generate(),
    passwordEncrypted = ByteArray(0),  // INVALID! Violates domain invariant
    // ...
)
vspherePort.testConnection(config)

// REQUIRED - Dedicated value object for the operation
val connectionParams = VcenterConnectionParams(
    vcenterUrl = command.vcenterUrl,
    username = command.username,
    datacenterName = command.datacenterName,
    // Only fields needed for connection testing
)
vspherePort.testConnection(params = connectionParams, password = resolvedPassword)
```

**Benefits:** Entity invariants remain intact, API is type-safe and self-documenting, value objects can have focused validation.

**When to apply:** If you're tempted to pass `null`, empty string, or `ByteArray(0)` to satisfy an entity constructor, create a value object instead.

---

## Common Failure Modes (Avoid These)

### 1. Over-Engineering

```kotlin
// DON'T add abstractions for single use cases
interface VmNameStrategy { fun generate(): String }
class DefaultVmNameStrategy : VmNameStrategy { ... }
class CustomVmNameStrategy : VmNameStrategy { ... }

// DO keep it simple until needed
fun generateVmName(prefix: String, index: Int): String = "$prefix-$index"
```

### 2. Premature Optimization

```kotlin
// DON'T optimize without measurement
val cache = ConcurrentHashMap<UUID, Aggregate>()  // "Might be slow"

// DO measure first, optimize if needed
// TC-003 performance tests will identify actual bottlenecks
```

### 3. Copy-Paste Without Understanding

- Understand code before copying from other modules
- Adapt to the specific context (different tenant, different aggregate)
- Never copy tests - write tests specific to the new functionality

---

## Quality Gates

| Gate | Threshold | Enforcement |
|------|-----------|-------------|
| Test Coverage | ≥70% | CI blocks merge |
| Mutation Score | ≥70% | CI blocks merge |
| Architecture Tests | All pass | CI blocks merge |
| Security Scan | Zero critical | CI blocks merge |

---

## Quick Reference

| Action | Command |
|--------|---------|
| Build all | `./gradlew clean build` |
| Run tests | `./gradlew test` |
| Check coverage (per-module) | `./gradlew koverHtmlReport` |
| Check coverage (merged) | `./gradlew :koverHtmlReport` |
| Verify coverage threshold | `./gradlew koverVerify` |
| Mutation testing | `./gradlew pitest` |
| Architecture tests | `./gradlew :dvmm:dvmm-app:test --tests "*ArchitectureTest*"` |
| Sprint status | Check `docs/sprint-artifacts/sprint-status.yaml` |

## Key Documentation

| Document | Path |
|----------|------|
| Architecture | `docs/architecture.md` |
| PRD | `docs/prd.md` |
| Epics | `docs/epics.md` |
| Sprint Status | `docs/sprint-artifacts/sprint-status.yaml` |
| Security | `docs/security-architecture.md` |
| Test Design | `docs/test-design-system.md` |
