# Story 1.9: Testcontainers Setup

**Status:** ready-for-dev

## Story

As a **developer**,
I want Testcontainers for integration tests,
So that tests run against real infrastructure.

## Acceptance Criteria

1. **PostgreSQL Container**
   - Integration tests start a PostgreSQL 16 container automatically.
   - Container is reused across test classes (singleton pattern).
   - Database schema is initialized via Flyway.
2. **Keycloak Container**
   - Keycloak container is available for auth tests.
   - Test realm is imported on startup.
3. **Test Fixtures Module**
   - `src/testFixtures` provides reusable helpers:
     - `TestTenantFixture`: Creates random `TenantId`.
     - `TestUserFixture`: Creates `TestUser` with valid JWT.
     - `TestDataFixture`: Seeds common reference data.
4. **Event Store Isolation (TC-003)**
   - `@IsolatedEventStore` annotation exists in `eaf-testing`.
   - Supports `TRUNCATE` (fast) and `SCHEMA_PER_TEST` (robust) strategies.
   - Tests annotated with this start with a clean event store state.
5. **Configuration**
   - `TestContainersConfig` (or equivalent) provides `DataSource` bean pointing to container.
   - `RlsEnforcingDataSource` wrapper is applied to test data source (TC-002 groundwork).

## Tasks / Subtasks

- [x] Configure `eaf-testing` build with Testcontainers dependencies (PostgreSQL, Keycloak). (AC: 1, 2)
- [x] Implement singleton `TestContainers` object in `eaf-testing` to manage container lifecycle. (AC: 1, 2)
- [x] Create `RlsEnforcingDataSource` in `eaf-testing` to enforce tenant context in tests. (AC: 5)
- [x] Implement `@IsolatedEventStore` annotation and `EventStoreIsolationExtension` (JUnit 5). (AC: 4)
- [x] Implement `TestTenantFixture`, `TestUserFixture` in `testFixtures` source set. (AC: 3)
- [x] Unit test `TestUserFixture` to verify valid JWT generation structure and claims. (AC: 3)
- [x] Verify PostgreSQL container startup and reuse in a sample integration test. (AC: 1)
- [x] Verify Keycloak container startup and realm import. (AC: 2)
- [x] Verify Event Store isolation strategy (TRUNCATE) works between tests. (AC: 4)

## Dev Notes

### Learnings from Previous Story (1-2-eaf-core-module)
**From Story 1-2-eaf-core-module (Status: done)**
- **New Files**: `Result.kt`, `DomainError.kt`, `Identifiers.kt` in `eaf-core`.
- **Standards**: Build conventions and version catalog are authoritative.
- **Architecture**: EAF modules must not import from DVMM. `eaf-testing` should depend on `eaf-core` and `eaf-eventsourcing` but NOT `dvmm-*`.

### Architecture & Constraints
- **Module**: `eaf-testing`
- **Dependencies**: `testcontainers`, `junit-jupiter`, `postgresql`, `flyway-core`.
- **Isolation**: Tests must not leak data. RLS must be enforced even in tests (TC-002).
- **Performance**: Container startup is slow; use Singleton pattern (`static val`) to start once per test suite run.

### Technical Implementation Details
- **Testcontainers**: Use `org.testcontainers:postgresql` and `com.github.dasniko:testcontainers-keycloak`.
- **Fixtures**: Kotlin `testFixtures` plugin allows sharing test code across modules.
- **TC-003**: `EventStoreIsolationExtension` should implement `BeforeEachCallback`. For `TRUNCATE`, use `TRUNCATE TABLE eaf_events.events, eaf_events.snapshots RESTART IDENTITY CASCADE`.

### References
- Tech Spec Epic 1: `docs/sprint-artifacts/tech-spec-epic-1.md` (Section 3.9)
- Epics: `docs/epics.md`
- Architecture: `docs/architecture.md` (Test Strategy)
- Test Design: `docs/test-design-system.md`

## Dev Agent Record

### Context Reference
- Story context XML: docs/sprint-artifacts/1-9-testcontainers-setup.context.xml

### Agent Model Used
- Gemini 2.0 Flash Experimental

### Debug Log References
- Verified container startup with `TestContainersIntegrationTest`
- Verified isolation with `IsolatedEventStore` test case
- Verified JWT generation with `TestUserFixtureTest`
- Verified RLS data source with `RlsEnforcingDataSourceTest`

### Completion Notes List
- Configured `eaf-testing` with `java-test-fixtures` and Testcontainers dependencies
- Implemented `TestContainers` singleton managing PostgreSQL 16 and Keycloak 26
- Implemented `RlsEnforcingDataSource` using session-scoped variables (`SET app.tenant_id`) to ensure robust RLS enforcement in tests
- Implemented `TestTenantFixture` and `TestUserFixture` (generating valid JWTs) in `src/testFixtures`
- Implemented `@IsolatedEventStore` extension supporting TRUNCATE strategy
- Added comprehensive unit and integration tests verifying all acceptance criteria

### File List
- eaf/eaf-testing/build.gradle.kts
- gradle/libs.versions.toml
- eaf/eaf-testing/src/main/resources/test-realm.json
- eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/TestContainers.kt
- eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/TenantTestContext.kt
- eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/RlsEnforcingDataSource.kt
- eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/IsolatedEventStore.kt
- eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/TestUser.kt
- eaf/eaf-testing/src/testFixtures/kotlin/de/acci/eaf/testing/fixtures/TestFixtures.kt
- eaf/eaf-testing/src/test/kotlin/de/acci/eaf/testing/fixtures/TestUserFixtureTest.kt
- eaf/eaf-testing/src/test/kotlin/de/acci/eaf/testing/TestContainersIntegrationTest.kt
- eaf/eaf-testing/src/test/kotlin/de/acci/eaf/testing/RlsEnforcingDataSourceTest.kt

## Change Log

- 2025-11-26: Draft created from epics/tech-spec with SM agent (#create-story).
- 2025-11-26: Implemented all tasks and tests. Status: ready-for-dev -> review.