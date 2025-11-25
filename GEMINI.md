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
./gradlew :dvmm:dvmm-app:test --tests "com.acita.dvmm.architecture.ArchitectureTest"

# Run single test method
./gradlew :dvmm:dvmm-app:test --tests "ArchitectureTest.eaf modules must not depend on dvmm modules"

# Check code coverage (JaCoCo) - 80% minimum required
./gradlew jacocoTestReport

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
- `eaf.test-conventions` - JUnit 6, JaCoCo (80% coverage), Testcontainers, Konsist
- `eaf.pitest-conventions` - Mutation testing (70% threshold)

## Critical Architecture Rules (ADR-001)

**IMPORTANT: These rules are enforced by Konsist tests in `ArchitectureTest.kt`. CI will block any violations.**

- EAF modules MUST NOT import from `com.acita.dvmm.*`
- DVMM modules CAN import from `com.acita.eaf.*`
- `dvmm-domain` MUST NOT import from `org.springframework.*`

## Tech Stack

- **Kotlin 2.2** with context parameters (`-Xcontext-parameters`)
- **Spring Boot 3.5** with WebFlux/Coroutines
- **Gradle 9.2** with Version Catalog (`gradle/libs.versions.toml`)
- **PostgreSQL** with Row-Level Security for multi-tenancy
- **JUnit 6** + MockK + Testcontainers
- **Konsist** for architecture testing
- **Pitest** for mutation testing

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
import com.acita.eaf.core.domain.AggregateRoot
import com.acita.eaf.core.domain.DomainEvent

// FORBIDDEN - Wildcard imports
import com.acita.eaf.core.domain.*
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
import com.acita.dvmm.domain.VmRequest  // BLOCKED BY KONSIST

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
| Check coverage | `./gradlew jacocoTestReport` |
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
