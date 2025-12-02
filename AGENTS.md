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

# Code coverage (80% minimum)
./gradlew koverHtmlReport          # Per-module reports
./gradlew :koverHtmlReport         # Merged report (root)
./gradlew koverVerify              # Verify 80% threshold

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
   CREATE POLICY tenant_isolation ON my_table ...;
   -- [jooq ignore stop]
   ```
4. Run `./gradlew :dvmm:dvmm-infrastructure:generateJooq`
5. Verify: `./gradlew :dvmm:dvmm-infrastructure:compileKotlin`

**Checklist:** Flyway migration + jooq-init.sql + ignore tokens + regenerate + integration tests updated for FK constraints + tests pass.

**FK Constraints in Tests:** When adding FK constraints, test helpers must create parent records first using `ON CONFLICT DO NOTHING` for idempotency. Cleanup should use `TRUNCATE ... CASCADE`.

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
- Achieve ≥80% line coverage per module
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

## Quality Gates

| Gate | Threshold | Enforcement |
|------|-----------|-------------|
| Test Coverage | ≥80% | CI blocks merge |
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
