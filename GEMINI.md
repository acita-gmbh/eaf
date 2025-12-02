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

# Check code coverage (Kover) - 80% minimum required
./gradlew koverHtmlReport          # Per-module reports
./gradlew :koverHtmlReport         # Merged report (root)
./gradlew koverVerify              # Verify 80% threshold

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
- `eaf-testing` - Test utilities (InMemoryEventStore, TestClock, TenantTestContext)

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
- `eaf.test-conventions` - JUnit 6, Kover (80% coverage), Testcontainers, Konsist
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
- Achieve ≥80% line coverage per module
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
| Test Coverage | ≥80% | CI blocks merge |
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
