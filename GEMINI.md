# GEMINI.md

This file provides guidance to Google Gemini when working with code in this repository.

## Specialized Guides

Read these guides when working on specific areas:
- **[Backend Patterns](docs/claude-guides/backend-patterns.md)** - MockK, Coroutines, Event Sourcing, CQRS
- **[Frontend Patterns](docs/claude-guides/frontend-patterns.md)** - React 19, Vitest, Playwright, TanStack Query
- **[VMware Patterns](docs/claude-guides/vmware-patterns.md)** - VCF SDK 9.0 integration
- **[Docker/jOOQ Setup](docs/claude-guides/docker-jooq-setup.md)** - Docker Compose, jOOQ generation, PostgreSQL

---

## Build Commands

```bash
./gradlew clean build           # Full build (ktlint, Detekt, Konsist, tests)
./gradlew test                  # Run all tests
./gradlew koverVerify           # Verify 70% coverage threshold
./gradlew pitest                # Mutation testing (70% threshold)
```

## Architecture Overview

**Gradle multi-module monorepo** with two component groups:

### EAF (Enterprise Application Framework) - `eaf/`
Reusable framework with **zero product dependencies**:
- `eaf-core` - Domain primitives (Entity, AggregateRoot, ValueObject, DomainEvent)
- `eaf-eventsourcing` - Event Store interfaces and projections
- `eaf-tenant` - Multi-tenancy with PostgreSQL RLS
- `eaf-auth` - IdP-agnostic authentication interfaces
- `eaf-auth-keycloak` - Keycloak implementation
- `eaf-testing` - Test utilities (InMemoryEventStore, TestClock)

### DVMM (Dynamic Virtual Machine Manager) - `dvmm/`
Product modules following **Hexagonal Architecture**:
- `dvmm-domain` - Business logic (NO Spring dependencies)
- `dvmm-application` - Use cases, command/query handlers
- `dvmm-api` - REST controllers, DTOs
- `dvmm-infrastructure` - Persistence, external integrations
- `dvmm-app` - Spring Boot entry point

### Build Logic - `build-logic/`
Convention plugins: `eaf.kotlin-conventions`, `eaf.spring-conventions`, `eaf.test-conventions`, `eaf.pitest-conventions`

## Critical Architecture Rules (ADR-001)

**Enforced by Konsist tests in `ArchitectureTest.kt`:**
- EAF modules MUST NOT import from `de.acci.dvmm.*`
- `dvmm-domain` MUST NOT import from `org.springframework.*`
- Detail query handlers (`Get*Detail*Handler`) MUST have `Forbidden` error type
- Suspend functions catching `Exception` MUST handle `CancellationException`

## Tech Stack

- **Kotlin 2.2** with context parameters
- **Spring Boot 3.5** with WebFlux/Coroutines
- **PostgreSQL** with Row-Level Security
- **jOOQ 3.20** for type-safe SQL
- **JUnit 6** + MockK + Testcontainers
- **Konsist** for architecture testing
- **Pitest + Arcmutate** for mutation testing

---

## Zero-Tolerance Policies

### Code Style
```kotlin
// Explicit imports (NO wildcards)
import de.acci.eaf.core.domain.AggregateRoot

// Named arguments for >2 parameters
val request = VmRequest.create(tenantId = tenantId, requesterId = userId, vmName = "web-01")
```

### Testing Requirements
- Write tests BEFORE implementation (Tests First)
- ≥70% line coverage per module
- ≥70% mutation score (Pitest)
- Run `./gradlew clean build` before committing

### Security (Multi-Tenant)
**Return 404 for Forbidden errors to prevent tenant enumeration:**
```kotlin
when (result.error) {
    is NotFound -> ResponseEntity.notFound().build()
    is Forbidden -> ResponseEntity.notFound().build()  // NOT 403!
}
logger.warn { "Access denied: ${result.error}" }  // Log for audit
```

---

## Anti-Patterns (PROHIBITED)

1. **Deferred Architectural Decisions** - No "TODO: decide later" comments
2. **Untestable Code** - Use constructor injection, not hard-coded dependencies
3. **Missing Error Context** - Use domain-specific exceptions with full context
4. **Silent Failures** - Log errors with context, rethrow appropriately
5. **Parameter Bag Anti-Pattern** - Don't create entities with invalid state; use value objects

---

## Git Conventions

### Commit Messages (Conventional Commits)
```text
<type>: <description>

Types: feat, fix, docs, refactor, test, chore
```

### Branch Naming
- `feature/<story-id>-<description>`
- `fix/<issue>-<description>`

---

## Project Documentation

| Document | Purpose |
|----------|---------|
| [PRD](docs/prd.md) | Requirements (90 FRs + 95 NFRs) |
| [Architecture](docs/architecture.md) | System design, ADRs |
| [Epics](docs/epics.md) | 5 Epics, 51 Stories |
| [Sprint Status](docs/sprint-artifacts/sprint-status.yaml) | Story tracking |

---

## Quick Reference

| Action | Command |
|--------|---------|
| Build all | `./gradlew clean build` |
| Run tests | `./gradlew test` |
| Coverage report | `./gradlew :koverHtmlReport` |
| Mutation testing | `./gradlew pitest` |
| Architecture tests | `./gradlew :dvmm:dvmm-app:test --tests "*ArchitectureTest*"` |
