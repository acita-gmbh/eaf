# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

# Run mutation testing (Pitest + Arcmutate) - 70% threshold
# Requires arcmutate-licence.txt at project root OR ARCMUTATE_LICENSE env var
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
- `eaf-auth-keycloak` - Keycloak implementation (future: add more IdP modules as needed)
- `eaf-testing` - Test utilities (InMemoryEventStore, TestClock, TenantTestContext)

**Auth Abstraction Pattern:** `eaf-auth` defines interfaces (e.g., `AuthContext`, `TenantResolver`), and dedicated EAF modules provide IdP-specific implementations (e.g., `eaf-auth-keycloak`). Product modules only configure which implementation to use. This enables switching IdPs without rewriting auth logic in each product.

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
- `eaf.pitest-conventions` - Mutation testing (70% threshold) with Arcmutate Kotlin/Spring

## Critical Architecture Rules (ADR-001)

**Enforced by Konsist tests in `ArchitectureTest.kt`:**
- EAF modules MUST NOT import from `de.acci.dvmm.*`
- DVMM modules CAN import from `de.acci.eaf.*`
- `dvmm-domain` MUST NOT import from `org.springframework.*`
- Detail query handlers (`Get*DetailHandler`, `Get*ByIdHandler`) MUST have `Forbidden` error type

## Tech Stack

- **Kotlin 2.2** with context parameters (`-Xcontext-parameters`)
- **Spring Boot 3.5** with WebFlux/Coroutines
- **Gradle 9.2** with Version Catalog (`gradle/libs.versions.toml`)
- **PostgreSQL** with Row-Level Security for multi-tenancy
- **jOOQ 3.20** with DDLDatabase for type-safe SQL
- **JUnit 6** + MockK + Testcontainers
- **Konsist** for architecture testing
- **Pitest + Arcmutate** for mutation testing (Kotlin/Spring support)

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

### Coroutine CancellationException Handling

**NEVER catch `CancellationException` with a broad `catch (e: Exception)` block - always rethrow it.**

Kotlin's structured concurrency uses `CancellationException` to propagate cancellation signals through the coroutine hierarchy. Catching it with a broad exception handler breaks cancellation:

```kotlin
// ❌ WRONG - Swallows coroutine cancellation, breaks structured concurrency
private suspend fun doWork() {
    try {
        eventStore.append(aggregateId, events, version)
    } catch (e: Exception) {
        logger.error(e) { "Failed to append events" }
        return  // CancellationException is caught here and not rethrown!
    }
}

// ✅ CORRECT - Explicitly rethrow CancellationException
import kotlin.coroutines.cancellation.CancellationException

private suspend fun doWork() {
    try {
        eventStore.append(aggregateId, events, version)
    } catch (e: CancellationException) {
        throw e  // Allow proper coroutine cancellation
    } catch (e: Exception) {
        logger.error(e) { "Failed to append events" }
        return
    }
}
```

**Why this matters:**
- `CancellationException` is used by `withTimeout`, `Job.cancel()`, and scope cancellation
- Catching it prevents parent coroutines from being notified of cancellation
- Resources may not be properly cleaned up (finally blocks, use {} blocks)
- Application shutdown can hang waiting for "cancelled" coroutines

**When to apply:** Any `catch (e: Exception)` in a `suspend fun` should have a preceding `catch (e: CancellationException) { throw e }` block.

### Event Sourcing Defensive Patterns

**Always check for empty event lists when loading events to determine `expectedVersion`.**

When loading events from the event store to calculate `expectedVersion` for optimistic concurrency, always validate that events were actually returned:

```kotlin
// ✅ CORRECT - Check for empty events before using size
val currentEvents = eventStore.load(aggregateId)
if (currentEvents.isEmpty()) {
    logger.error { "Cannot append: aggregate $aggregateId not found in event store" }
    return  // or throw appropriate exception
}
val expectedVersion = currentEvents.size.toLong()
eventStore.append(aggregateId, newEvents, expectedVersion)

// ❌ WRONG - Empty list silently becomes expectedVersion = 0
val currentEvents = eventStore.load(aggregateId)
val expectedVersion = currentEvents.size.toLong()  // 0 if empty!
eventStore.append(aggregateId, newEvents, expectedVersion)  // Concurrency conflict
```

**Why empty results are dangerous:**
1. **Data corruption**: Aggregate doesn't exist (was deleted or never created)
2. **Race condition**: Events were deleted between operations
3. **Wrong ID**: Incorrect aggregate ID was passed

Failing silently with `expectedVersion = 0` causes concurrency conflicts on append because the event store expects version 0 for new aggregates only.

**When adding new domain events, ALWAYS update event deserializers.**

New domain events must be registered in the corresponding `*EventDeserializer` class. Forgetting this causes silent failures when loading aggregates:

```kotlin
// In JacksonVmRequestEventDeserializer (or similar):
private fun resolveEventClass(eventType: String): Class<out DomainEvent> {
    return when (eventType) {
        "VmRequestCreated" -> VmRequestCreated::class.java
        "VmRequestApproved" -> VmRequestApproved::class.java
        "VmRequestProvisioningStarted" -> VmRequestProvisioningStarted::class.java  // ← Don't forget new events!
        else -> throw IllegalArgumentException("Unknown event type: $eventType")
    }
}
```

**Checklist when adding a new domain event:**
1. Create the event class in `dvmm-domain/.../events/`
2. Add case to `resolveEventClass()` in the corresponding deserializer
3. Add deserialization test in `*EventDeserializerTest`
4. If aggregate handles the event, add `apply()` method and test

**Why this matters:** Without deserializer registration, any aggregate load that includes the new event will throw `IllegalArgumentException: Unknown event type`. This breaks idempotency checks, status updates, and all future operations on affected aggregates.

**CQRS Command Handlers: Update Write-Side AND Read-Side Together**

In CQRS/Event Sourcing, command handlers must update both:
1. **Write-side:** Persist domain events to the event store
2. **Read-side:** Update any associated projections (timelines, status views, etc.)

```kotlin
// ✅ CORRECT - Handler updates BOTH write-side and read-side
public suspend fun handle(command: MarkVmRequestProvisioningCommand): Result<Unit, Error> {
    // 1. Write-side: Persist domain event
    aggregate.markProvisioning(metadata)
    eventStore.append(aggregate.id.value, aggregate.uncommittedEvents, expectedVersion)

    // 2. Read-side: Update projection (timeline, status, etc.)
    timelineUpdater.addTimelineEvent(NewTimelineEvent(
        eventType = TimelineEventType.PROVISIONING_STARTED,
        details = "VM provisioning has started",
        // ...
    ))
    return Unit.success()
}

// ❌ WRONG - Forgets read-side projection update
public suspend fun handle(command: MarkVmRequestProvisioningCommand): Result<Unit, Error> {
    aggregate.markProvisioning(metadata)
    eventStore.append(aggregate.id.value, aggregate.uncommittedEvents, expectedVersion)
    // Missing: timelineUpdater.addTimelineEvent(...)
    return Unit.success()  // AC-6 "Timeline event added" NOT satisfied!
}
```

**Why this matters:**
- Write-side correctly persists the domain event, but read-side (what users see) is never updated
- Timeline views, status dashboards, and detail pages won't reflect the state change
- The Acceptance Criteria "Timeline event added" appears done (event exists) but isn't visible
- This is a common CQRS pitfall: write-side is easy to verify, read-side is easy to forget

**Pattern to follow:** Look at existing handlers (`CreateVmRequestHandler`, `ApproveVmRequestHandler`) that inject `TimelineEventProjectionUpdater` and call `addTimelineEvent()` after successful event persistence.

**CQRS Partial Failure Observability**

When CQRS operations span multiple aggregates without distributed transactions, partial failures can leave the system in an inconsistent state. Use "CRITICAL" log prefix with full context to enable alerting and manual reconciliation:

```kotlin
// ✅ CORRECT - Detailed logging for partial failures
when (requestAppendResult) {
    is Result.Failure -> {
        logger.error {
            "CRITICAL: [Step 2/3] Failed to emit VmRequestReady for request $requestId " +
                "after VM $vmId was already marked provisioned. " +
                "System may be in inconsistent state. Error: ${requestAppendResult.error}"
        }
        return
    }
}

// ❌ WRONG - Generic error message loses context
logger.error { "Failed to emit VmRequestReady: ${requestAppendResult.error}" }
```

**Why this matters:**
- Partial success (aggregate A updated, aggregate B failed) requires manual reconciliation
- "CRITICAL" prefix enables alerting rules to trigger immediately
- Including both aggregate IDs helps operators identify what succeeded and what failed
- This pattern applies whenever operations span multiple aggregates sequentially

### VMware VCF SDK 9.0 Patterns

The project uses **VCF SDK 9.0** (`com.vmware.sdk:vsphere-utils:9.0.0.0`) for VMware vCenter integration.

**PropertyCollector Pattern:**

The SDK requires explicit property fetching via `PropertySpec` + `ObjectSpec` + `FilterSpec`:

```kotlin
// Fetch specific properties from a managed object
val propSpec = PropertySpec().apply {
    type = "ClusterComputeResource"
    pathSet.add("host")  // Property to fetch
}

val objSpec = ObjectSpec().apply {
    obj = clusterRef  // ManagedObjectReference
    isSkip = false
}

val filterSpec = PropertyFilterSpec().apply {
    propSet.add(propSpec)
    objectSet.add(objSpec)
}

val result = vimPort.retrievePropertiesEx(propertyCollector, listOf(filterSpec), RetrieveOptions())
```

**SearchIndex Navigation:**

Use inventory paths to find vSphere objects (datacenter/folder/object pattern):

```kotlin
val searchIndex = serviceContent.searchIndex

// Find datacenter
val datacenterRef = vimPort.findByInventoryPath(searchIndex, "MyDatacenter")

// Find cluster (path: datacenter/host/clusterName)
val clusterRef = vimPort.findByInventoryPath(searchIndex, "MyDatacenter/host/MyCluster")

// Find datastore (path: datacenter/datastore/datastoreName)
val datastoreRef = vimPort.findByInventoryPath(searchIndex, "MyDatacenter/datastore/MyDatastore")

// Find VM/template (path: datacenter/vm/vmName)
val vmRef = vimPort.findByInventoryPath(searchIndex, "MyDatacenter/vm/MyTemplate")
```

**Port 443 Constraint:**

VCF SDK's `VcenterClientFactory` only supports HTTPS on port 443:

```kotlin
// ✅ CORRECT - Hostname only (SDK assumes port 443)
val factory = VcenterClientFactory("vcenter.example.com", trustStore)

// ❌ WRONG - Custom port not supported
val factory = VcenterClientFactory("vcenter.example.com:8443", trustStore)  // URISyntaxException
```

This is correct for production vCenter servers (always use port 443). For testing with VCSIM (which uses dynamic ports), use the `VcsimAdapter` mock instead.

**Timeout Layering (Critical for Nested Async Operations):**

When you have nested async operations, the outer timeout MUST be longer than all inner timeouts combined. Failure to do this causes the outer timeout to kill the operation before inner operations complete.

```kotlin
// ✅ CORRECT - Outer timeout (5 min) > inner timeouts (clone ~60s + IP detection 120s)
private val vmwareToolsTimeoutMs: Long = 120_000  // Wait for IP detection
private val createVmTimeoutMs: Long = 300_000    // 5 minutes total

suspend fun createVm(spec: VmSpec) = executeResilient(
    name = "createVm",
    operationTimeoutMs = createVmTimeoutMs  // 5 min covers clone + IP wait
) {
    cloneVm(spec)  // ~60s
    waitForIpAddress(vmwareToolsTimeoutMs)  // 120s
}

// ❌ WRONG - Outer timeout (60s) < inner timeout (120s IP detection)
suspend fun createVm(spec: VmSpec) = executeResilient("createVm") {  // Default 60s
    cloneVm(spec)
    waitForIpAddress(vmwareToolsTimeoutMs)  // 120s - will be killed at 60s!
}
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

### Frontend Commands

```bash
cd dvmm/dvmm-web

npm run dev          # Start dev server (port 5173)
npm run build        # Type-check and build for production
npm run test         # Run Vitest unit tests
npm run test:e2e     # Run Playwright E2E tests
npm run lint         # Run ESLint
```

### Frontend Test File Convention

**Tests MUST be colocated with their source files.** Do NOT use `__tests__` directories.

```tsx
// ✅ CORRECT - Test file next to source file
src/components/Button.tsx
src/components/Button.test.tsx

// ❌ FORBIDDEN - __tests__ directory (vitest will ignore these)
src/components/__tests__/Button.test.tsx
```

**Rationale:**
- Easier to find tests when they're next to the source
- IDE navigation works better with colocated files
- Enforced via `vitest.config.ts` which excludes `**/__tests__/**`

### React Coding Standards (Zero-Tolerance)

**React Compiler handles memoization automatically. Manual optimization is PROHIBITED.**

```tsx
// ❌ FORBIDDEN - Manual memoization (ESLint will error)
import { useMemo, useCallback, memo } from 'react'
const memoizedValue = useMemo(() => computeExpensive(a, b), [a, b])
const memoizedFn = useCallback(() => doSomething(a), [a])
const MemoizedComponent = memo(MyComponent)

// ✅ CORRECT - Let React Compiler optimize automatically
const value = computeExpensive(a, b)
const handleClick = () => doSomething(a)
function MyComponent() { ... }
```

**Rationale:**
- React Compiler analyzes code at build time and adds memoization where beneficial
- Manual memoization is often applied incorrectly (wrong deps, unnecessary overhead)
- Compiler optimization is more consistent and maintainable
- See: https://react.dev/learn/react-compiler

### React Hook Form: useWatch over watch

**Use `useWatch` instead of `watch()` for React Compiler compatibility.**

```tsx
// ❌ FORBIDDEN - watch() causes React Compiler lint warnings
const { watch } = useForm()
const value = watch('fieldName')  // ESLint: react-hooks/incompatible-library

// ✅ CORRECT - useWatch is React Compiler compatible
import { useForm, useWatch } from 'react-hook-form'

const { control } = useForm()
const value = useWatch({ control, name: 'fieldName' })
```

**Rationale:**
- `watch()` returns a subscription that React Compiler cannot safely memoize
- `useWatch` is a separate hook designed to work with React's rules of hooks
- Both provide the same functionality, but `useWatch` integrates properly with the compiler

### Component Patterns

```tsx
// ✅ REQUIRED - Function components with TypeScript
interface Props {
  title: string
  onAction: () => void
}

export function MyComponent({ title, onAction }: Props) {
  return <button onClick={onAction}>{title}</button>
}

// ❌ FORBIDDEN - Class components
class MyComponent extends React.Component { ... }
```

### Read-only Props (SonarQube S6759)

**React props must be marked as read-only using TypeScript's `Readonly<T>` utility type.**

```tsx
// ✅ REQUIRED - Wrap props with Readonly<>
interface ButtonProps {
  label: string
  onClick: () => void
  disabled?: boolean
}

export function Button({ label, onClick, disabled }: Readonly<ButtonProps>) {
  return <button onClick={onClick} disabled={disabled}>{label}</button>
}

// ❌ FORBIDDEN - Mutable props (ESLint will error)
export function Button({ label, onClick, disabled }: ButtonProps) {
  return <button onClick={onClick} disabled={disabled}>{label}</button>
}
```

**Rationale:**
- Props are read-only snapshots in time - every render receives a new version
- Prevents accidental prop mutation which causes unpredictable behavior
- Enables React Compiler optimizations by guaranteeing immutability
- Enforced by ESLint rule `@eslint-react/prefer-read-only-props`

### Floating Promises and the `void` Operator

**All promises must be explicitly handled - either awaited or marked as intentional fire-and-forget with `void`.**

```tsx
// ❌ FORBIDDEN - Floating promise (ESLint error: @typescript-eslint/no-floating-promises)
const handleClick = () => {
  navigate('/dashboard')  // Returns a promise in React Router v6!
}

// ✅ CORRECT - Use `void` for intentional fire-and-forget
const handleClick = () => {
  void navigate('/dashboard')  // Explicitly marks as fire-and-forget
}

// ✅ CORRECT - Or use async/await if you need to wait
const handleClick = async () => {
  await navigate('/dashboard')
  console.log('Navigation complete')
}
```

**Common fire-and-forget patterns requiring `void`:**

```tsx
// React Router navigation (returns Promise in v6)
void navigate('/path')

// TanStack Query cache invalidation (background refetch)
void queryClient.invalidateQueries({ queryKey: ['my-requests'] })

// TanStack Query refetch (when you don't need the result)
void refetch()

// OIDC auth redirects (page will navigate away)
void auth.signinRedirect({ state: { returnTo: location.pathname } })
```

**Rationale:**
- Unhandled promises hide errors silently - `void` makes intent explicit
- React Router v6's `navigate()` returns a Promise (unlike v5)
- ESLint rule `@typescript-eslint/no-floating-promises` catches these bugs at compile time
- The `void` operator evaluates the expression and returns `undefined`, satisfying the linter while documenting intent

### E2E Testing with Playwright

The project uses **Playwright** with **@seontechnologies/playwright-utils** for enhanced E2E testing.

```bash
npm run test:e2e     # Run Playwright E2E tests
npm run test:e2e:ui  # Run with Playwright UI mode
```

**Use playwright-utils fixtures** for consistent testing patterns:

```tsx
// ✅ CORRECT - Use playwright-utils fixtures for API requests
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
// ✅ CORRECT - Use recurse for polling async conditions
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
// ❌ FORBIDDEN - Dynamic RegExp can cause ReDoS with malicious input
const requestId = await getRequestId() // User-controlled value
await expect(page).toHaveURL(new RegExp(`/admin/requests/${requestId}`))

// ✅ CORRECT - String literal with interpolation
await expect(page).toHaveURL(`/admin/requests/${requestId}`)

// ✅ CORRECT - If regex truly needed, use static pattern
await expect(page).toHaveURL(/\/admin\/requests\/[\w-]+/)
```

**ESLint enforces this:** The `security/detect-non-literal-regexp` rule blocks dynamic RegExp construction.

### Vitest Unit Testing Patterns

**Use `vi.hoisted()` for module mocks that need to be available before imports.**

When mocking modules like `react-oidc-context` or other external dependencies, the mock must be created before ES modules are imported. Use `vi.hoisted()` to ensure proper hoisting:

```tsx
// ✅ CORRECT - vi.hoisted() ensures mock exists before import
const mockUseAuth = vi.hoisted(() =>
  vi.fn(() => ({
    user: { access_token: 'test-token' },
    isAuthenticated: true,
  }))
)

vi.mock('react-oidc-context', () => ({
  useAuth: mockUseAuth,
}))

// In tests, you can now override the mock:
beforeEach(() => {
  mockUseAuth.mockReturnValue({
    user: { access_token: 'test-token' },
    isAuthenticated: true,
  })
})

it('handles unauthenticated state', () => {
  mockUseAuth.mockReturnValue({ user: null, isAuthenticated: false })
  // ... test code
})
```

```tsx
// ❌ WRONG - Dynamic import and re-mock doesn't work reliably
it('test', async () => {
  const { useAuth } = await import('react-oidc-context')
  vi.mocked(useAuth).mockReturnValue({ ... })  // May not apply!
})
```

**Use `mockResolvedValueOnce()` for sequential responses in async tests.**

When testing refetch behavior or sequential API calls, use `mockResolvedValueOnce()` to return different values for each call:

```tsx
// ✅ CORRECT - Deterministic sequential responses
mockGetData
  .mockResolvedValueOnce({ status: 'PENDING' })   // First call
  .mockResolvedValueOnce({ status: 'APPROVED' })  // After refetch

const { result } = renderHook(() => useMyHook())
await waitFor(() => expect(result.current.data?.status).toBe('PENDING'))

await result.current.refetch()
await waitFor(() => expect(mockGetData).toHaveBeenCalledTimes(2))
expect(result.current.data?.status).toBe('APPROVED')
```

```tsx
// ❌ WRONG - Changing mock between calls is unreliable
mockGetData.mockResolvedValue({ status: 'PENDING' })
// ... initial fetch
mockGetData.mockResolvedValue({ status: 'APPROVED' })  // May still return PENDING!
await result.current.refetch()
```

### TanStack Query Polling Patterns

**Understand when to use `staleTime` vs `refetchInterval` for real-time data.**

- `staleTime`: Controls when cached data is considered stale. Stale data triggers a refetch on the *next access* (component mount, window focus, etc.)
- `refetchInterval`: Actively polls in the background, regardless of user interaction

For admin queues and dashboards where new data should appear automatically:

```tsx
// ✅ CORRECT - Real-time data needs both staleTime AND refetchInterval
useQuery({
  queryKey: ['admin', 'pending-requests'],
  queryFn: fetchPendingRequests,
  staleTime: 10000,  // Data stale after 10s (triggers refetch on access)
  // Jitter prevents "thundering herd" when many clients poll simultaneously
  refetchInterval: 30000 + Math.floor(Math.random() * 5000),
  refetchIntervalInBackground: false,  // Don't poll when tab inactive
  refetchOnWindowFocus: true,  // Immediate refresh when user returns
})

// ❌ WRONG - Only staleTime means no automatic background polling
useQuery({
  staleTime: 30000,  // Admin won't see new requests until they interact!
})
```

**Jitter Pattern Rationale:**
- Without jitter: 100 admins polling at exactly 30s intervals → 100 simultaneous requests
- With jitter (`+ Math.random() * 5000`): Requests spread over 5s window, reducing server load spikes

## Docker Compose (E2E Environment)

The project uses **Docker Compose** for local development and E2E testing. Infrastructure is layered:

```
docker/
├── eaf/                    # EAF infrastructure (reusable by future products)
│   └── docker-compose.yml  # PostgreSQL 16 + Keycloak 24.0.1
└── dvmm/                   # DVMM product services
    ├── docker-compose.yml  # Includes EAF, adds backend + frontend
    └── Dockerfile.backend  # Runtime-only (expects pre-built JAR)
```

### Quick Start

```bash
# 1. Build backend JAR first (jOOQ needs Docker access on host)
./gradlew :dvmm:dvmm-app:bootJar -x test

# 2. Start everything (postgres, keycloak, backend, frontend)
docker compose -f docker/dvmm/docker-compose.yml up -d

# 3. Wait for services (or use --wait flag)
docker compose -f docker/dvmm/docker-compose.yml ps

# 4. Run E2E tests
cd dvmm/dvmm-web && npm run test:e2e

# 5. Stop and clean up
docker compose -f docker/dvmm/docker-compose.yml down -v
```

### Development Mode (Backend on Host)

For debugging with hot-reload, run only infrastructure containers:

```bash
# Start only postgres + keycloak
docker compose -f docker/dvmm/docker-compose.yml up postgres keycloak -d

# Run backend on host with debugger
./gradlew :dvmm:dvmm-app:bootRun

# Run frontend on host
cd dvmm/dvmm-web && npm run dev
```

### Service Ports

| Service | Port | URL |
|---------|------|-----|
| PostgreSQL | 5432 | `jdbc:postgresql://localhost:5432/eaf_test` |
| Keycloak | 8180 | http://localhost:8180 |
| Backend | 8080 | http://localhost:8080 |
| Frontend | 5173 | http://localhost:5173 |

### Credentials

- **PostgreSQL:** `eaf` / `eaf` (database: `eaf_test`)
- **Keycloak Admin:** `admin` / `admin`
- **Test Users:** See `eaf/eaf-testing/src/main/resources/test-realm.json`

## jOOQ Code Generation

jOOQ generates type-safe Kotlin code using **Testcontainers + Flyway** to spin up a real PostgreSQL database, run migrations, and generate code from the actual production-identical schema.

### How It Works

1. A custom Gradle task `generateJooqWithTestcontainers` starts a PostgreSQL Testcontainer
2. Flyway runs all migrations from both `eaf/eaf-eventsourcing/.../db/migration/` and `dvmm/dvmm-infrastructure/.../db/migration/`
3. jOOQ generates code from the real PostgreSQL schema
4. The container is stopped after generation

**Benefits:**
- Single source of truth: Flyway migrations ARE the schema definition
- No jooq-init.sql to maintain - eliminates synchronization issues
- Full PostgreSQL compatibility - JSONB, RLS, triggers, functions all work
- Catches migration errors at build time

**Requirements:**
- Docker must be running (locally and on CI)
- Build time increases ~10-15s for container startup

### Key Files

| File | Purpose |
|------|---------|
| `dvmm/dvmm-infrastructure/build.gradle.kts` | Custom jOOQ generation task with Testcontainers |
| `eaf/eaf-eventsourcing/src/main/resources/db/migration/` | EAF framework migrations |
| `dvmm/dvmm-infrastructure/src/main/resources/db/migration/` | DVMM product migrations |

### Regenerate jOOQ Code

```bash
./gradlew :dvmm:dvmm-infrastructure:generateJooqWithTestcontainers
```

Or simply build the project - jOOQ generation runs automatically before `compileKotlin`:

```bash
./gradlew :dvmm:dvmm-infrastructure:compileKotlin
```

### Adding New Tables

**Just add the Flyway migration - no additional files needed!**

1. Add migration to `eaf/eaf-eventsourcing/src/main/resources/db/migration/` or `dvmm/dvmm-infrastructure/src/main/resources/db/migration/`
2. Use PostgreSQL-native types (JSONB, TIMESTAMPTZ, UUID, etc.) - they all work
3. Use quoted uppercase identifiers for table/column names for consistency:
   - Example: `CREATE TABLE "DOMAIN_EVENTS"` not `CREATE TABLE domain_events`
4. Include RLS policies with both `USING` AND `WITH CHECK` clauses:
   ```sql
   ALTER TABLE "MY_TABLE" ENABLE ROW LEVEL SECURITY;
   CREATE POLICY tenant_isolation ON "MY_TABLE"
       FOR ALL
       USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
       WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
   ALTER TABLE "MY_TABLE" FORCE ROW LEVEL SECURITY;
   ```
   **CRITICAL: Always include `WITH CHECK` in RLS policies.** Without it, RLS only filters reads but allows writes to any tenant, enabling cross-tenant data injection.
5. Build to regenerate jOOQ code: `./gradlew :dvmm:dvmm-infrastructure:compileKotlin`

**Checklist before committing:**
- [ ] Flyway migration created (V00X__*.sql)
- [ ] RLS policies include both `USING` AND `WITH CHECK` clauses
- [ ] FK constraints added for related tables (e.g., `REFERENCES parent_table(id) ON DELETE CASCADE`)
- [ ] jOOQ code regenerated (happens automatically on build)
- [ ] Integration tests updated for FK constraints (see below)
- [ ] Tests pass with new schema

### FK Constraints and Integration Tests

When adding FK constraints, integration tests that directly insert child records will fail. Update test helpers:

```kotlin
// ✅ CORRECT - Test helper creates parent record first
private fun insertTestTimelineEvent(requestId: UUID, tenantId: TenantId, ...) {
    // Ensure parent exists (FK constraint)
    insertParentRequest(id = requestId, tenantId = tenantId)
    // Then insert child record
    // ...
}

// ✅ CORRECT - Parent insert is idempotent
private fun insertParentRequest(id: UUID, tenantId: TenantId) {
    // Use ON CONFLICT to avoid duplicates when same parent used multiple times
    """INSERT INTO ... VALUES (...) ON CONFLICT ("ID") DO NOTHING"""
}

// ✅ CORRECT - Cleanup uses CASCADE for FK constraints
@AfterEach
fun cleanup() {
    superuserDsl.execute("""TRUNCATE TABLE "PARENT_TABLE" CASCADE""")
}
```

### PostgreSQL Types in Generated Code

Since we now generate from real PostgreSQL, jOOQ correctly types PostgreSQL-specific columns:

| PostgreSQL Type | jOOQ Type | Usage |
|----------------|-----------|-------|
| `JSONB` | `org.jooq.JSONB` | Use `JSONB.jsonb(jsonString)` to create, `.data()` to read |
| `UUID` | `java.util.UUID` | Direct mapping |
| `TIMESTAMPTZ` | `java.time.OffsetDateTime` | Direct mapping |
| `BYTEA` | `ByteArray` | Direct mapping |

Example for JSONB columns:

```kotlin
// Writing JSONB
val jsonb = JSONB.jsonb("""{"key": "value"}""")
dsl.insertInto(TABLE).set(TABLE.JSON_COLUMN, jsonb).execute()

// Reading JSONB
val record = dsl.selectFrom(TABLE).fetchOne()
val json: String = record.jsonColumn?.data() ?: "{}"
```

### Projection Column Symmetry (CRITICAL)

**CQRS projection repositories must handle all columns symmetrically in both read and write operations.**

When adding a new column to a projection table:
1. Add the column to the Flyway migration
2. Regenerate jOOQ code (automatic on build)
3. Add the column to `mapRecord()` (read path)
4. Add the column to `insert()` (write path)
5. **Compile fails if any step is missed** - use sealed class pattern

```kotlin
// ✅ CORRECT - Sealed class pattern ensures read/write symmetry
sealed interface ProjectionColumns {
    data object Id : ProjectionColumns
    data object TenantId : ProjectionColumns
    data object NewColumn : ProjectionColumns  // Adding new column? Add here!

    companion object {
        val all = listOf(Id, TenantId, NewColumn)  // Must include all columns
    }
}

private fun mapColumn(record: Record, column: ProjectionColumns): Any? = when (column) {
    ProjectionColumns.Id -> record.get(TABLE.ID)
    ProjectionColumns.TenantId -> record.get(TABLE.TENANT_ID)
    ProjectionColumns.NewColumn -> record.get(TABLE.NEW_COLUMN)  // Compiler forces this
}

private fun setColumn(step: InsertSetMoreStep<*>, column: ProjectionColumns, data: Projection): InsertSetMoreStep<*> = when (column) {
    ProjectionColumns.Id -> step.set(TABLE.ID, data.id)
    ProjectionColumns.TenantId -> step.set(TABLE.TENANT_ID, data.tenantId)
    ProjectionColumns.NewColumn -> step.set(TABLE.NEW_COLUMN, data.newColumn)  // Compiler forces this
}
```

**Why this matters:** Without this pattern, jOOQ silently allows reading columns that aren't written during insert, causing data loss. The sealed class makes this a compile-time error instead of a runtime data corruption.

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

## Project Documentation

### Planning & Requirements

| Document | Purpose | Status |
|----------|---------|--------|
| [Product Brief](docs/product-brief-dvmm-2025-11-24.md) | Vision, market timing, core value proposition | Final v2.0 |
| [PRD](docs/prd.md) | 90 FRs + 95 NFRs, success criteria, scope | Validated v1.1 |
| [Market Research](docs/research-market-2025-11-24.md) | TAM/SAM/SOM DACH, competitive analysis | Complete |
| [Epics](docs/epics.md) | 5 Epics, 51 Stories for MVP | Complete |

### Architecture & Design

| Document | Purpose | Key Decisions |
|----------|---------|---------------|
| [Architecture](docs/architecture.md) | System design, ADRs, module structure | CQRS/ES, PostgreSQL RLS, Hexagonal |
| [Security Architecture](docs/security-architecture.md) | Threat model, STRIDE, compliance | ISO 27001, GDPR Crypto-Shredding |
| [DevOps Strategy](docs/devops-strategy.md) | CI/CD, quality gates, monitoring | GitHub Actions, 70%/70% gates |
| [Test Design](docs/test-design-system.md) | Testability concerns TC-001–TC-004 | k6, Playwright, VCSIM |
| [UX Design](docs/ux-design-specification.md) | Design system, user journeys | shadcn-admin-kit, Tech Teal |

### Implementation Tracking

| Document | Purpose |
|----------|---------|
| [Sprint Status](docs/sprint-artifacts/sprint-status.yaml) | Story lifecycle tracking (backlog→done) |
| [BMM Workflow Status](docs/bmm-workflow-status.yaml) | Phase 0-4 workflow completion |
| [Implementation Readiness](docs/implementation-readiness-report-2025-11-25.md) | Phase 3→4 validation (PASSED) |

---

## BMAD Method Workflow

This project uses **BMAD Method v6** (`.bmad/`) for structured AI-driven development.

### Current Phase: Implementation (Phase 4)

Track: **Enterprise Method** (PRD + Architecture + Security/DevOps/Test)

### Story Lifecycle

Stories move through: `backlog` → `drafted` → `ready-for-dev` → `in-progress` → `review` → `done`

**Key Principle:** One story at a time through the entire lifecycle.

### Agent Responsibilities

| Agent | Role | Key Workflows |
|-------|------|---------------|
| **SM** (Scrum Master) | Implementation orchestrator | `create-story`, `story-context`, `epic-tech-context` |
| **DEV** (Developer) | Implementation + quality | `dev-story`, `code-review`, `story-done` |
| **Architect** | Technical design | `architecture`, `implementation-readiness` |
| **PM** | Requirements | `prd`, `create-epics-and-stories` |

### Implementation Workflow (per Story)

1. **SM:** `create-story` - Create story from epic backlog
2. **SM:** `story-context` - Assemble dynamic context XML
3. **DEV:** `dev-story` - Implement with tests (Tests First pattern)
4. **DEV:** `code-review` - Senior dev review
5. **DEV:** `story-done` - Mark complete, advance queue

### Quality Gates (Non-Negotiable)

- **Test Coverage:** ≥70% (CI blocks merge)
- **Mutation Score:** ≥70% (CI blocks merge)
- **Architecture Tests:** All Konsist rules pass
- **Security Scan:** Zero critical vulnerabilities

### Slash Commands

```bash
# Check workflow status
/bmad:bmm:workflows:workflow-status

# Create next story
/bmad:bmm:workflows:create-story

# Generate story context
/bmad:bmm:workflows:story-context

# Implement story
/bmad:bmm:workflows:dev-story

# Code review
/bmad:bmm:workflows:code-review

# Mark story done
/bmad:bmm:workflows:story-done
```

### Important Rules

1. **Always check `sprint-status.yaml`** before starting work to see current story state
2. **Use fresh chats** for each workflow to avoid context limitations
3. **Tests First pattern:** Write tests before implementation
4. **Never skip code review** - required before marking done
5. **Always update sprint-status.yaml** when story state changes

---

## Zero-Tolerance Policies

**IMPORTANT: These rules are absolute and must never be violated.**

### Code Style (Enforced)

```kotlin
// ✅ CORRECT - Explicit imports
import de.acci.eaf.core.domain.AggregateRoot
import de.acci.eaf.core.domain.DomainEvent

// ❌ FORBIDDEN - Wildcard imports
import de.acci.eaf.core.domain.*
```

```kotlin
// ✅ CORRECT - Named arguments for clarity
val request = VmRequest.create(
    tenantId = tenantId,
    requesterId = userId,
    vmName = "web-server-01",
    cpuCores = 4,
    memoryGb = 16
)

// ❌ FORBIDDEN - Positional arguments for >2 parameters
val request = VmRequest.create(tenantId, userId, "web-server-01", 4, 16)
```

### Architecture Violations (CI Blocks Merge)

```kotlin
// ❌ FORBIDDEN - EAF depending on DVMM
// File: eaf/eaf-core/src/main/kotlin/...
import de.acci.dvmm.domain.VmRequest  // BLOCKED BY KONSIST

// ❌ FORBIDDEN - Spring in domain layer
// File: dvmm/dvmm-domain/src/main/kotlin/...
import org.springframework.stereotype.Service  // BLOCKED BY KONSIST
```

### Testing Requirements

- **YOU MUST** write tests BEFORE implementation (Tests First)
- **YOU MUST** achieve ≥70% line coverage per module
- **YOU MUST** achieve ≥70% mutation score (Pitest)
- **YOU MUST** run `./gradlew clean build` before committing

### Security Patterns (Multi-Tenant)

**Resource access errors MUST return 404 to prevent tenant enumeration attacks.**

```kotlin
// ✅ CORRECT - Opaque error response prevents information leakage
when (result.error) {
    is NotFound -> ResponseEntity.notFound().build()
    is Forbidden -> ResponseEntity.notFound().build()  // Return 404, not 403!
}
// Log actual error type for audit trail
logger.warn { "Access denied: ${result.error}" }

// ❌ FORBIDDEN - Reveals resource exists in another tenant
when (result.error) {
    is NotFound -> ResponseEntity.notFound().build()
    is Forbidden -> ResponseEntity.status(403).build()  // Exposes tenant boundary!
}
```

**Rationale:**
- If `/api/admin/requests/123` returns 403, attacker knows request 123 exists (in another tenant)
- If it returns 404, attacker cannot distinguish "doesn't exist" from "exists but no access"
- Internal logging preserves audit trail for security investigations
- Applies to all resource access endpoints (GET by ID, detail views, etc.)

---

## Anti-Patterns (PROHIBITED)

### 1. Deferred Architectural Decisions

```kotlin
// ❌ PROHIBITED - "TODO: decide later" comments
class EventStore {
    // TODO: Decide if we need snapshots later
    fun loadAggregate(id: UUID): Aggregate { ... }
}

// ✅ REQUIRED - Explicit decision NOW or raise blocking issue
class EventStore {
    // ADR-003: Snapshots after 100 events, configured per aggregate type
    fun loadAggregate(id: UUID, snapshotThreshold: Int = 100): Aggregate { ... }
}
```

### 2. Untestable Code

```kotlin
// ❌ PROHIBITED - Hard-coded dependencies
class VmService {
    private val httpClient = HttpClient.newHttpClient()  // Untestable!
}

// ✅ REQUIRED - Constructor injection
class VmService(
    private val httpClient: HttpClient  // Testable via mock
)
```

### 3. Missing Error Context

```kotlin
// ❌ PROHIBITED - Generic exceptions
throw RuntimeException("VM creation failed")

// ✅ REQUIRED - Domain-specific exceptions with context
throw VmProvisioningException(
    vmRequestId = requestId,
    reason = VmProvisioningFailure.RESOURCE_EXHAUSTED,
    details = "vCenter cluster 'prod-01' has insufficient memory"
)
```

### 4. Silent Failures

```kotlin
// ❌ PROHIBITED - Swallowing exceptions
try {
    vmwareClient.createVm(spec)
} catch (e: Exception) {
    logger.error("Failed")  // No context, no re-throw!
}

// ✅ REQUIRED - Proper error handling
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
// ❌ PROHIBITED - Creating entity with invalid data just to pass parameters
// Entity invariant violated: VmwareConfiguration requires valid encrypted password
val config = VmwareConfiguration(
    id = VmwareConfigurationId.generate(),
    tenantId = command.tenantId,
    vcenterUrl = command.vcenterUrl,
    username = command.username,
    passwordEncrypted = ByteArray(0),  // INVALID! Violates domain invariant
    // ... other fields
)
vspherePort.testConnection(config)

// ✅ REQUIRED - Create a dedicated value object for the operation
// VcenterConnectionParams only contains what's needed - no entity lifecycle concerns
val connectionParams = VcenterConnectionParams(
    vcenterUrl = command.vcenterUrl,
    username = command.username,
    datacenterName = command.datacenterName,
    clusterName = command.clusterName,
    datastoreName = command.datastoreName,
    networkName = command.networkName,
    templateName = command.templateName,
    folderPath = null
)
vspherePort.testConnection(params = connectionParams, password = resolvedPassword)
```

**Benefits:**
- Entity invariants remain intact (VmwareConfiguration always has valid encrypted password)
- API is type-safe and self-documenting (method signature shows exactly what's needed)
- Value object can have its own focused validation (e.g., URL format, non-blank fields)
- Clear separation: entities have identity and lifecycle; value objects are just data

**When to apply:** If you're tempted to pass `null`, empty string, or `ByteArray(0)` to satisfy an entity constructor, create a value object instead.

---

## Lessons Learned (from previous project)

### Story Size Limits

- **Documentation:** Max 4KB per story file
- **Implementation:** Max 2 weeks work (AI-adjusted: 2-3 days)
- **If larger:** Split into multiple stories BEFORE starting

### Integration Tests First

```kotlin
// ✅ CORRECT order for new features:
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

### MVP Validation

- **Every merge to main** must pass smoke test
- **No feature flags** for hiding incomplete work
- **If it merges, it works** - no "will finish later"

### Dependency Rules

- Story dependencies: **Max 1 story back**
- If story B needs story A: A must be `done` before B starts
- Circular dependencies: **PROHIBITED** - restructure stories

---

## Memory Management

**IMPORTANT: Use `/clear` between major tasks to prevent context pollution.**

### When to Clear Context

- After completing a story (`story-done`)
- Before starting a new workflow
- When Claude seems confused or repetitive
- After large file operations (>10 files read)

### Context-Efficient Practices

- Read only files you need (avoid exploratory reads)
- Use `Task` agent for complex searches instead of multiple Grep calls
- Reference files by path instead of pasting content when possible

---

## Common Failure Modes (Avoid These)

### 1. Over-Engineering

```kotlin
// ❌ DON'T add abstractions for single use cases
interface VmNameStrategy { fun generate(): String }
class DefaultVmNameStrategy : VmNameStrategy { ... }
class CustomVmNameStrategy : VmNameStrategy { ... }

// ✅ DO keep it simple until needed
fun generateVmName(prefix: String, index: Int): String = "$prefix-$index"
```

### 2. Premature Optimization

```kotlin
// ❌ DON'T optimize without measurement
val cache = ConcurrentHashMap<UUID, Aggregate>()  // "Might be slow"

// ✅ DO measure first, optimize if needed
// TC-003 performance tests will identify actual bottlenecks
```

### 3. Copy-Paste Without Understanding

- **YOU MUST** understand code before copying from other modules
- **YOU MUST** adapt to the specific context (different tenant, different aggregate)
- **Never copy tests** - write tests specific to the new functionality

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
| Start workflow | `/bmad:bmm:workflows:workflow-status` |
