# Story 1.8: jOOQ Projection Base

Status: ready-for-dev

## Story

As a **developer**,
I want jOOQ configured for type-safe read queries,
so that I can build efficient read projections.

## Requirements Context Summary

- **Epic/AC source:** Story 1.8 in `docs/epics.md` — configure jOOQ Gradle plugin for code generation from Flyway migrations, create `BaseProjectionRepository` with common query patterns and automatic tenant filtering via RLS.
- **Architecture constraint:** CQRS pattern requires separate read model; jOOQ is the chosen technology for type-safe queries against PostgreSQL.
- **Tech Spec guidance:** `docs/sprint-artifacts/tech-spec-epic-1.md` Story 1.8 — jOOQ Gradle plugin configuration, `BaseProjectionRepository` pattern, pagination helpers, awaitProjection test utility.
- **Prerequisites:** Story 1.3 (Event Store Setup) — Flyway migrations exist, Story 1.6 (PostgreSQL RLS) — tenant filtering at database level.
- **Testability Concern TC-004:** Tests must be able to await projection updates for eventual consistency handling.

## Acceptance Criteria

1. **jOOQ code generation runs during build**
   - Given jOOQ Gradle plugin is configured
   - When I run `./gradlew build`
   - Then generated record classes are created in `build/generated-sources/jooq`.

2. **Type-safe queries with generated classes**
   - Given jOOQ code generation has run
   - When I query using generated record classes
   - Then queries are type-safe and compile-time checked.

3. **BaseProjectionRepository provides common patterns**
   - Given I extend `BaseProjectionRepository<T>`
   - When I implement a repository
   - Then common query patterns (find, list, pagination) are available.

4. **Tenant filtering via RLS (automatic)**
   - Given RLS policies are enabled (Story 1.6)
   - When I execute queries via jOOQ DSLContext
   - Then tenant_id filter is automatically applied by PostgreSQL RLS.

5. **Pagination helpers available**
   - Given I use `PageRequest` and `PagedResponse<T>`
   - When I query with pagination
   - Then I receive properly paginated results with totalElements and totalPages.

6. **TC-004: awaitProjection helper for tests**
   - Given a command has been processed and events persisted
   - When the test calls `awaitProjection(aggregateId, timeout = 5.seconds)`
   - Then the test blocks until the projection is updated
   - And returns the projected entity when available
   - And throws `TimeoutException` after 5 seconds if projection never appears.

## Test Plan

- **Unit:** `PageRequest` validation (page ≥ 0, size > 0).
- **Unit:** `PagedResponse` total pages calculation.
- **Unit:** `BaseProjectionRepository` paginate extension function.
- **Integration:** jOOQ code generation produces expected classes.
- **Integration:** Query with pagination returns correct page.
- **Integration:** RLS filtering works with DSLContext queries.
- **Integration:** `awaitProjection` helper waits for projection update.
- **Integration:** `awaitProjection` throws `TimeoutException` after timeout.

## Structure Alignment / Previous Learnings

### Learnings from Previous Story

#### From Story 1-7-keycloak-integration (Status: done)

- **Module Pattern Established:** New modules follow pattern of `eaf-{module}/build.gradle.kts` with `eaf.kotlin-conventions` plugin.
- **Library vs Application:** Library modules must disable `bootJar` and enable `jar` (see eaf-auth-keycloak/build.gradle.kts:8-15).
- **IdP-Agnostic Pattern:** Interfaces in core module, implementations in adapter modules — same pattern applies to projections (interface in eaf-eventsourcing, implementation in dvmm-infrastructure).
- **Tenant Context Chain:** JWT → TenantContextWebFilter → TenantContext → RLS — jOOQ queries benefit from automatic RLS filtering.
- **Test Fixtures:** Use `src/testFixtures` for shared test utilities like `awaitProjection`.

[Source: docs/sprint-artifacts/1-7-keycloak-integration.md#Dev-Agent-Record]

### Project Structure Notes

- `eaf-eventsourcing` module contains projection interfaces (`ProjectionRepository`).
- `eaf-testing` module contains test utilities (`awaitProjection`, `TenantTestContext`).
- `dvmm-infrastructure` module contains jOOQ-based repository implementations.
- jOOQ plugin configured in `dvmm-infrastructure/build.gradle.kts`.
- Generated jOOQ code goes to `dvmm-infrastructure/build/generated-sources/jooq`.
- Package: `de.acci.dvmm.infrastructure.jooq`.

## Tasks / Subtasks

- [ ] **Task 1: Configure jOOQ Gradle plugin** (AC: 1)
  - [ ] Add `nu.studer.jooq` plugin to `dvmm-infrastructure/build.gradle.kts`
  - [ ] Configure generator to use `KotlinGenerator`
  - [ ] Set input schema to read from Flyway-migrated database
  - [ ] Set output directory to `build/generated-sources/jooq`
  - [ ] Set package name to `de.acci.dvmm.infrastructure.jooq`
  - [ ] Configure to use Testcontainers PostgreSQL for generation

- [ ] **Task 2: Add jOOQ dependency to version catalog** (AC: 1, 2)
  - [ ] Add jOOQ version to `gradle/libs.versions.toml` (3.20+)
  - [ ] Add jOOQ libraries: `jooq`, `jooq-kotlin`, `jooq-codegen`
  - [ ] Add jOOQ Gradle plugin to plugins section
  - [ ] Add PostgreSQL JDBC driver dependency

- [ ] **Task 3: Create pagination helpers in eaf-eventsourcing** (AC: 5)
  - [ ] Create `PageRequest` data class with validation
  - [ ] Create `PagedResponse<T>` data class with totalPages calculation
  - [ ] Add unit tests for pagination helpers

- [ ] **Task 4: Create BaseProjectionRepository** (AC: 3, 4)
  - [ ] Create `BaseProjectionRepository<T>` abstract class in dvmm-infrastructure
  - [ ] Inject `DSLContext` via constructor
  - [ ] Add `paginate()` extension function for jOOQ queries
  - [ ] Document that RLS handles tenant filtering automatically
  - [ ] Add unit tests for repository base class

- [ ] **Task 5: Create awaitProjection helper in eaf-testing** (AC: 6)
  - [ ] Create `awaitProjection` suspend function
  - [ ] Implement polling with configurable interval (default: 50ms)
  - [ ] Implement timeout handling with `withTimeout`
  - [ ] Add integration test verifying awaitProjection works

- [ ] **Task 6: Create sample projection for validation** (AC: 2, 4)
  - [ ] Create simple projection table migration (e.g., `V004__create_vm_requests_projection.sql`)
  - [ ] Create `VmRequestProjectionRepository` extending `BaseProjectionRepository`
  - [ ] Verify generated jOOQ classes work with repository
  - [ ] Verify RLS filtering works end-to-end

- [ ] **Task 7: Write integration tests** (AC: 1, 2, 3, 4, 5, 6)
  - [ ] Test: jOOQ generates expected classes during build
  - [ ] Test: Queries with generated classes are type-safe
  - [ ] Test: Pagination returns correct results
  - [ ] Test: RLS filters by tenant_id automatically
  - [ ] Test: awaitProjection helper waits and returns entity
  - [ ] Test: awaitProjection throws TimeoutException

## Dev Notes

- **Relevant architecture patterns:** CQRS read model with jOOQ for type-safe queries; RLS for automatic tenant filtering.
- **Source tree components to touch:**
  - `gradle/libs.versions.toml` (add jOOQ dependencies)
  - `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/projection/PageRequest.kt` (new)
  - `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/projection/PagedResponse.kt` (new)
  - `eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/ProjectionTestUtils.kt` (new)
  - `dvmm/dvmm-infrastructure/build.gradle.kts` (add jOOQ plugin)
  - `dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/projection/BaseProjectionRepository.kt` (new)
  - `dvmm/dvmm-infrastructure/src/main/resources/db/migration/V004__create_vm_requests_projection.sql` (new)
- **Testing standards:** Use Testcontainers PostgreSQL; achieve ≥80% coverage and ≥70% mutation score.
- **RLS Note:** Unlike typical JDBC patterns, we do NOT add explicit `WHERE tenant_id = ?` clauses. PostgreSQL RLS policies (Story 1.6) handle this automatically when `SET LOCAL app.tenant_id` is executed on the connection.

### jOOQ Configuration Reference

```kotlin
// dvmm-infrastructure/build.gradle.kts
plugins {
    id("nu.studer.jooq") version "9.0"
}

jooq {
    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(true)
            jooqConfiguration.apply {
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        inputSchema = "public"
                        includes = ".*"
                        excludes = "flyway_schema_history"
                    }
                    target.apply {
                        packageName = "de.acci.dvmm.infrastructure.jooq"
                        directory = "build/generated-sources/jooq"
                    }
                }
            }
        }
    }
}
```

### References

- [Source: docs/epics.md#Story-1.8-jOOQ-Projection-Base]
- [Source: docs/sprint-artifacts/tech-spec-epic-1.md#Story-1.8-jOOQ-Projection-Base]
- [Source: docs/architecture.md#Query-Pattern]
- [Source: docs/sprint-artifacts/1-7-keycloak-integration.md#Dev-Agent-Record]
- [Source: docs/test-design-system.md#TC-004-Projection-Awaiting]

## Dev Agent Record

### Context Reference

- `docs/sprint-artifacts/1-8-jooq-projection-base.context.xml`

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List

### Change Log

- 2025-11-26: Story drafted from epics.md, tech-spec-epic-1.md, and architecture.md
- 2025-11-26: Story context generated, status changed to ready-for-dev
