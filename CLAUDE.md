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

**Enforced by Konsist tests in `ArchitectureTest.kt`:**
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

### Frontend Commands

```bash
cd dvmm/dvmm-web

npm run dev          # Start dev server (port 5173)
npm run build        # Type-check and build for production
npm run test         # Run Vitest unit tests
npm run test:e2e     # Run Playwright E2E tests
npm run lint         # Run ESLint
```

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

## jOOQ Code Generation

jOOQ generates type-safe Kotlin code from SQL DDL files using **DDLDatabase** (no running database required).

### Key Files

| File | Purpose |
|------|---------|
| `dvmm/dvmm-infrastructure/src/main/resources/db/jooq-init.sql` | Combined DDL for jOOQ generation |
| `dvmm/dvmm-infrastructure/build.gradle.kts` | jOOQ Gradle configuration |

### Regenerate jOOQ Code

```bash
./gradlew :dvmm:dvmm-infrastructure:generateJooq
```

### Adding New Tables

**IMPORTANT:** Two SQL files must be kept in sync - Flyway migrations (production) and jooq-init.sql (code generation).

1. Add migration to `eaf/eaf-eventsourcing/src/main/resources/db/migration/` or `dvmm/dvmm-infrastructure/src/main/resources/db/migration/`
2. Update `dvmm/dvmm-infrastructure/src/main/resources/db/jooq-init.sql` with H2-compatible DDL:
   - Use quoted uppercase identifiers for table/column names (jOOQ DDLDatabase uses H2 which generates uppercase)
   - Example: `CREATE TABLE "DOMAIN_EVENTS"` not `CREATE TABLE domain_events`
3. Update `dvmm/dvmm-infrastructure/src/test/resources/db/jooq-init.sql` (test-specific version) if it exists
4. Wrap PostgreSQL-specific statements with jOOQ ignore tokens:
   ```sql
   -- [jooq ignore start]
   ALTER TABLE my_table ENABLE ROW LEVEL SECURITY;
   CREATE POLICY tenant_isolation ON my_table ...;
   GRANT SELECT ON my_table TO eaf_app;
   -- [jooq ignore stop]
   ```
5. Run `./gradlew :dvmm:dvmm-infrastructure:generateJooq`
6. Verify generated code compiles: `./gradlew :dvmm:dvmm-infrastructure:compileKotlin`

**Checklist before committing:**
- [ ] Flyway migration created (V00X__*.sql)
- [ ] jooq-init.sql updated with H2-compatible DDL
- [ ] PostgreSQL-specific statements wrapped with ignore tokens
- [ ] jOOQ code regenerated
- [ ] Tests pass with new schema

### What Gets Ignored

DDLDatabase uses H2 internally, so PostgreSQL-specific statements must be wrapped:
- RLS: `ENABLE/FORCE ROW LEVEL SECURITY`, `CREATE POLICY`
- Permissions: `GRANT`, `REVOKE`, `CREATE ROLE`
- Triggers/Functions: `CREATE TRIGGER`, `CREATE FUNCTION`, `DO $$ ... $$`
- Comments: `COMMENT ON TABLE/COLUMN`

These are runtime concerns that don't affect generated jOOQ code.

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
| [DevOps Strategy](docs/devops-strategy.md) | CI/CD, quality gates, monitoring | GitHub Actions, 80%/70% gates |
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

- **Test Coverage:** ≥80% (CI blocks merge)
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
- **YOU MUST** achieve ≥80% line coverage per module
- **YOU MUST** achieve ≥70% mutation score (Pitest)
- **YOU MUST** run `./gradlew clean build` before committing

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
