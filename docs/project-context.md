# Project Context for AI Agents

_Critical rules and patterns for implementing code in EAF/DVMM. Focus on unobvious details that agents might miss._

---

## Technology Stack & Versions

| Technology | Version | Notes |
|------------|---------|-------|
| Kotlin | 2.2 | Context parameters enabled (`-Xcontext-parameters`) |
| Spring Boot | 3.5 | WebFlux + Coroutines (NOT MVC) |
| Gradle | 9.2 | Version Catalog at `gradle/libs.versions.toml` |
| JVM | 21 | Target and toolchain |
| PostgreSQL | 16 | Row-Level Security for multi-tenancy |
| jOOQ | 3.20 | DDLDatabase code generation |
| JUnit | 6 | With MockK + Testcontainers |

### Frontend (dvmm-web)

| Technology | Version | Notes |
|------------|---------|-------|
| React | 19.2 | With React Compiler for automatic optimization |
| TypeScript | 5.9 | Strict mode enabled |
| Vite | 7.2 | @vitejs/plugin-react with Babel |
| Tailwind CSS | 4 | With shadcn/ui components |
| Vitest | 4 | Unit testing |
| Playwright | 1.57 | E2E testing |

---

## Architecture Rules (Konsist-Enforced)

**These are CI-blocking - violations prevent merge:**

- `eaf/*` modules MUST NOT import from `de.acci.dvmm.*`
- `dvmm-domain` MUST NOT import from `org.springframework.*`
- All modules use **Explicit API mode** - public members require explicit visibility

**Module Boundaries:**
```
eaf-core → eaf-eventsourcing → eaf-tenant → eaf-auth → eaf-testing
                    ↓
dvmm-domain → dvmm-application → dvmm-api → dvmm-infrastructure → dvmm-app
```

---

## Kotlin Style Rules (Zero-Tolerance)

### Imports
```kotlin
// ✅ REQUIRED - Explicit imports only
import de.acci.eaf.core.domain.AggregateRoot
import de.acci.eaf.core.domain.DomainEvent

// ❌ FORBIDDEN - Wildcard imports
import de.acci.eaf.core.domain.*
```

### Named Arguments
```kotlin
// ✅ REQUIRED - Named arguments for >2 parameters
VmRequest.create(
    tenantId = tenantId,
    requesterId = userId,
    vmName = "web-server-01"
)

// ❌ FORBIDDEN - Positional arguments for >2 parameters
VmRequest.create(tenantId, userId, "web-server-01")
```

### Error Handling
```kotlin
// ✅ REQUIRED - Domain exceptions with context
throw VmProvisioningException(
    vmRequestId = requestId,
    reason = VmProvisioningFailure.RESOURCE_EXHAUSTED,
    details = "Cluster 'prod-01' has insufficient memory"
)

// ❌ FORBIDDEN - Generic exceptions
throw RuntimeException("VM creation failed")

// ❌ FORBIDDEN - Swallowing exceptions
catch (e: Exception) { logger.error("Failed") }
```

### Security Patterns (Multi-Tenant)

**Resource access errors MUST return 404 to prevent tenant enumeration:**

```kotlin
// ✅ CORRECT - Opaque error response prevents information leakage
when (result.error) {
    is NotFound, is Forbidden -> ResponseEntity.notFound().build()
}
// Log actual error for audit
logger.warn { "Access error: ${result.error}" }

// ❌ FORBIDDEN - Returning 403 reveals resource exists in another tenant
```

**Rationale:** If `/api/requests/123` returns 403, attacker knows request 123 exists (in another tenant). Returning 404 prevents enumeration.

---

## Testing Rules (CI-Blocking)

| Metric | Threshold | Tool |
|--------|-----------|------|
| Line Coverage | ≥70% | Kover |
| Mutation Score | ≥70% | Pitest |
| Architecture | 100% pass | Konsist |

**Tests First Pattern:**
1. Write integration test (proves feature works E2E)
2. Write unit tests (proves components work)
3. Implement production code

**Constructor Injection Required:**
```kotlin
// ✅ Testable
class VmService(private val httpClient: HttpClient)

// ❌ Untestable - hard-coded dependency
class VmService {
    private val httpClient = HttpClient.newHttpClient()
}
```

**MockK Mocking Patterns (Kotlin):**
- Use `any()` for ALL parameters when stubbing functions with default arguments
- MockK evaluates defaults at setup time, not call time - `coEvery { handler.handle(any()) }` with a defaulted second param creates `eq(specific-uuid)` matcher
- Always: `coEvery { handler.handle(any(), any()) }` to match all parameters explicitly

**Coroutine Event Listener Pattern:**
- Launch handlers independently when multiple handlers react to the same event
- Each handler in separate `scope.launch{}` block with individual try-catch
- Failure of one handler shouldn't prevent others from executing (eventual consistency)

```kotlin
// ✅ CORRECT - Handlers execute independently
@EventListener
fun onEvent(event: SomeEvent) {
    scope.launch { try { handlerA.handle(event) } catch (e: Exception) { logger.error(e) { "Handler A failed" } } }
    scope.launch { try { handlerB.handle(event) } catch (e: Exception) { logger.error(e) { "Handler B failed" } } }
}
```

**Event Sourcing Defensive Pattern:**
- Always check for empty event lists when loading events to determine `expectedVersion`
- Empty results indicate: (1) aggregate doesn't exist, (2) race condition deleted events, or (3) wrong ID passed
- Failing silently with `expectedVersion = 0` causes concurrency conflicts on append

```kotlin
// ✅ CORRECT - Check for empty events before using size
val currentEvents = eventStore.load(aggregateId)
if (currentEvents.isEmpty()) {
    logger.error { "Cannot append: aggregate $aggregateId not found" }
    return
}
val expectedVersion = currentEvents.size.toLong()
```

**New Domain Event Checklist:**
- Create event class in `dvmm-domain/.../events/`
- Add case to `resolveEventClass()` in corresponding `*EventDeserializer`
- Add deserialization test in `*EventDeserializerTest`
- If aggregate handles event, add `apply()` method and test
- Without deserializer registration → `IllegalArgumentException: Unknown event type` on aggregate load

**Vitest Mocking Patterns (TypeScript):**
- Use `vi.hoisted()` for module mocks (ensures mock exists before ES module imports)
- Use `mockResolvedValueOnce()` for sequential responses in refetch/retry tests

---

## Frontend Patterns

**TanStack Query Polling (for admin queues, dashboards):**
- Use both `staleTime` (cache freshness) AND `refetchInterval` (active background polling)
- Add jitter to `refetchInterval` to prevent thundering herd: `30000 + Math.floor(Math.random() * 5000)`
- Set `refetchIntervalInBackground: false` to stop polling when tab inactive

**Floating Promises (ESLint-enforced):**

All promises must be handled - either awaited or marked as fire-and-forget with `void`:

```tsx
// ❌ FORBIDDEN - Floating promise
navigate('/dashboard')  // Returns Promise in React Router v6!

// ✅ CORRECT - Use `void` for fire-and-forget
void navigate('/dashboard')
void queryClient.invalidateQueries({ queryKey: ['data'] })
void refetch()
void auth.signinRedirect({ state: { returnTo: '/' } })
```

**Why:** React Router v6's `navigate()` returns a Promise (unlike v5). ESLint rule `@typescript-eslint/no-floating-promises` catches these at compile time.

---

## VMware VCF SDK 9.0 Patterns

The project uses **VCF SDK 9.0** (`com.vmware.sdk:vsphere-utils:9.0.0.0`) for vCenter integration.

**PropertyCollector Pattern:**
```kotlin
// Fetch properties via PropertySpec + ObjectSpec + FilterSpec
val propSpec = PropertySpec().apply { type = "ClusterComputeResource"; pathSet.add("host") }
val objSpec = ObjectSpec().apply { obj = clusterRef; isSkip = false }
val filterSpec = PropertyFilterSpec().apply { propSet.add(propSpec); objectSet.add(objSpec) }
val result = vimPort.retrievePropertiesEx(propertyCollector, listOf(filterSpec), RetrieveOptions())
```

**SearchIndex Navigation:**
```kotlin
// Inventory paths: datacenter/folder/objectName
val clusterRef = vimPort.findByInventoryPath(searchIndex, "MyDatacenter/host/MyCluster")
val datastoreRef = vimPort.findByInventoryPath(searchIndex, "MyDatacenter/datastore/MyDatastore")
val vmRef = vimPort.findByInventoryPath(searchIndex, "MyDatacenter/vm/MyTemplate")
```

**Port 443 Constraint:** `VcenterClientFactory` only supports HTTPS/443. For VCSIM testing (dynamic ports), use `VcsimAdapter` mock instead.

**Timeout Layering (Critical for Nested Async Operations):**

When you have nested async operations, the outer timeout MUST be longer than all inner timeouts combined:

```kotlin
// ✅ CORRECT - Outer timeout (5 min) > inner timeouts (clone ~60s + IP detection 120s)
private val createVmTimeoutMs: Long = 300_000  // 5 minutes total
suspend fun createVm(spec: VmSpec) = executeResilient("createVm", createVmTimeoutMs) {
    cloneVm(spec)  // ~60s
    waitForIpAddress(120_000)  // 120s
}

// ❌ WRONG - Outer timeout (60s) < inner timeout (120s) - killed before completion!
```

**Rule:** Calculate worst-case inner duration sum, then add buffer for outer timeout.

---

## jOOQ Code Generation (Critical Gotchas)

**Two SQL files must stay in sync:**
- Flyway migrations (production): `*/resources/db/migration/`
- jOOQ DDL (codegen): `dvmm-infrastructure/src/main/resources/db/jooq-init.sql`

**H2 Compatibility (DDLDatabase uses H2 internally):**
```sql
-- Use quoted UPPERCASE for table/column names
CREATE TABLE "DOMAIN_EVENTS" (...)  -- ✅ Correct
CREATE TABLE domain_events (...)    -- ❌ Wrong

-- Wrap PostgreSQL-specific statements
-- [jooq ignore start]
ALTER TABLE my_table ENABLE ROW LEVEL SECURITY;
-- RLS policies MUST have both USING (reads) AND WITH CHECK (writes)
CREATE POLICY tenant_isolation ON my_table
    FOR ALL
    USING (tenant_id = ...)
    WITH CHECK (tenant_id = ...);
-- [jooq ignore stop]
```

**After schema changes:**
```bash
./gradlew :dvmm:dvmm-infrastructure:generateJooq
./gradlew :dvmm:dvmm-infrastructure:compileKotlin
```

**FK Constraints in Tests:** When adding FK constraints, test helpers must create parent records first (use `ON CONFLICT DO NOTHING` for idempotency), and cleanup must use `TRUNCATE ... CASCADE`.

**Projection Column Symmetry (CRITICAL):**

CQRS projection repositories must handle all columns symmetrically in read/write operations. Use sealed class pattern:

```kotlin
sealed interface ProjectionColumns {
    data object Id : ProjectionColumns
    data object NewColumn : ProjectionColumns
    companion object { val all = listOf(Id, NewColumn) }
}

// Exhaustive when expressions force handling all columns in both mapRecord() and insert()
private fun mapColumn(record: Record, column: ProjectionColumns) = when (column) { ... }
private fun setColumn(step: InsertSetMoreStep<*>, column: ProjectionColumns, data: Projection) = when (column) { ... }
```

**Why:** jOOQ silently allows reading columns that aren't written during insert, causing data loss. Sealed class makes this a compile-time error.

**See:** `VmRequestProjectionRepository.kt` and `TimelineEventRepository.kt` for reference implementations.

**CQRS Command Handler Pattern (CRITICAL):**

Command handlers must update BOTH write-side (event store) AND read-side (projections) together:

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
// Missing: timelineUpdater.addTimelineEvent(...) ← AC "Timeline event added" NOT satisfied!
```

**Why:** Write-side is easy to verify (event persisted), but read-side (timeline events, status views) is easy to forget. Users won't see the state change in the UI.

**See:** `CreateVmRequestHandler`, `ApproveVmRequestHandler`, `MarkVmRequestProvisioningHandler` for reference implementations.

---

## Anti-Patterns (Prohibited)

| Anti-Pattern | Why It's Bad | Required Instead |
|--------------|--------------|------------------|
| `// TODO: decide later` | Deferred decisions accumulate | Explicit ADR or blocking issue |
| Feature flags for incomplete work | Hidden tech debt | If it merges, it works |
| Abstractions for single use | Over-engineering | Simple function until reuse proven |
| `ConcurrentHashMap` "for performance" | Premature optimization | Measure first (TC-003 tests) |
| Copy-paste from other modules | Context mismatch | Understand + adapt |
| `useMemo`/`useCallback`/`memo` | Manual memoization | React Compiler handles this automatically |
| Class components in React | Legacy pattern | Function components with hooks |
| `new RegExp(userInput)` | CWE-1333 ReDoS vulnerability | Use string literals or static patterns |
| Floating promises | Silent error swallowing | Use `void` for fire-and-forget or `await` |
| Entity with invalid state | Violates domain invariants | Create dedicated value object instead |

### Domain Integrity: Value Objects for Operation Parameters

**Entities should NEVER exist in invalid states.** When needing a subset of entity data for an operation, create a dedicated value object instead of instantiating the entity with placeholder values.

```kotlin
// ❌ FORBIDDEN - Entity with invalid data just to pass parameters
val config = VmwareConfiguration(passwordEncrypted = ByteArray(0), ...)
vspherePort.testConnection(config)

// ✅ REQUIRED - Value object with only what's needed
val params = VcenterConnectionParams(vcenterUrl, username, datacenterName, ...)
vspherePort.testConnection(params = params, password = resolvedPassword)
```

**When to apply:** If you're tempted to pass `null`, empty string, or `ByteArray(0)` to satisfy an entity constructor, create a value object instead. This preserves entity invariants and makes APIs type-safe.

---

## Git Conventions

**Commit Format:**
```
<type>: <description>

[DVMM-123] feat: Implement VM request validation
```

**Types:** `feat`, `fix`, `docs`, `refactor`, `test`, `chore`

**Branch Naming:**
- `feature/<story-id>-<description>`
- `fix/<issue>-<description>`

---

## Critical Reminders

- Run `./gradlew clean build` before every commit
- Check `docs/sprint-artifacts/sprint-status.yaml` before starting work
- Never skip code review - required before story completion
- Use `/clear` between major tasks to prevent context pollution

---

_Last Updated: 2025-12-06_
_Distilled from CLAUDE.md for LLM context efficiency_
_Added: Coroutine event listener pattern (independent handler execution)_
