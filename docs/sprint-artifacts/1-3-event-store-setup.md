# Story 1.3: Event Store Setup

**Status:** done

## Story

As a **developer**,
I want a PostgreSQL-based event store,
So that I can persist domain events durably.

## Acceptance Criteria

1. **Event Persistence**
   - Events are persisted to `eaf_events.events` table when `eventStore.append()` is called.
   - Table columns: `id` (UUID, PK), `aggregate_id` (UUID, indexed), `aggregate_type` (VARCHAR), `event_type` (VARCHAR), `payload` (JSONB), `metadata` (JSONB with tenant_id, user_id, correlation_id, timestamp), `version` (INT), `created_at` (TIMESTAMPTZ).

2. **Optimistic Locking**
   - Concurrent writes to the same aggregate are prevented via unique constraint on `(aggregate_id, version)`.
   - `ConcurrencyConflict` error is returned when version mismatch occurs.

3. **Event Immutability**
   - Events are immutable (no UPDATE, no DELETE allowed at database level).
   - Only INSERT and SELECT operations are permitted on the events table.

4. **Flyway Migration**
   - Migration `V001__create_event_store.sql` creates the `eaf_events` schema and `events` table.
   - Migration includes all required indexes and constraints.

5. **Event Loading**
   - Events can be loaded by aggregate ID in version order.
   - Events can be loaded from a specific version for replay after snapshot.

## Tasks / Subtasks

- [x] Create Flyway migration `V001__create_event_store.sql` in `eaf-eventsourcing/src/main/resources/db/migration/` (AC: 1, 3, 4)
  - [x] Create `eaf_events` schema
  - [x] Create `events` table with all columns
  - [x] Add unique constraint on `(tenant_id, aggregate_id, version)` - includes tenant_id for multi-tenancy
  - [x] Add indexes on `aggregate_id` (via unique constraint) and `tenant_id`
  - [x] Revoke UPDATE/DELETE permissions on events table
- [x] Define `DomainEvent` interface in `eaf-eventsourcing` (AC: 1)
  - [x] Include `aggregateType`, `metadata` properties
  - [x] Define `EventMetadata` data class with `tenantId`, `userId`, `correlationId`, `timestamp`
- [x] Define `EventStore` interface in `eaf-eventsourcing` (AC: 1, 2, 5)
  - [x] `append(aggregateId, events, expectedVersion): Result<Long, EventStoreError>`
  - [x] `load(aggregateId): List<StoredEvent>`
  - [x] `loadFrom(aggregateId, fromVersion): List<StoredEvent>`
- [x] Define `EventStoreError` sealed class (AC: 2)
  - [x] `ConcurrencyConflict(aggregateId, expectedVersion, actualVersion)`
- [x] Implement `PostgresEventStore` in `eaf-eventsourcing` using jOOQ DSLContext (AC: 1, 2, 5)
  - [x] Implement `append()` with version increment and conflict detection
  - [x] Implement `load()` returning events ordered by version ASC
  - [x] Implement `loadFrom()` for partial replay
- [x] Configure Jackson `ObjectMapper` for JSONB serialization in Spring context (AC: 1)
  - [x] Register Kotlin module
  - [x] Configure ISO-8601 timestamp format
- [x] Write unit tests for `PostgresEventStore` (AC: 1, 2, 5)
  - [x] Test successful event append
  - [x] Test optimistic locking conflict detection
  - [x] Test event loading by aggregate ID
  - [x] Test partial loading from version
- [x] Write integration test verifying Flyway migration runs successfully (AC: 4)
- [x] Write integration test verifying RLS applies to events table via `RlsEnforcingDataSource` (AC: 1)
- [x] Write integration test verifying UPDATE/DELETE operations are rejected on events table (AC: 3)
  - [x] Attempt UPDATE on existing event row, verify permission denied error
  - [x] Attempt DELETE on existing event row, verify permission denied error

## Dev Notes

### Learnings from Previous Story

**From Story 1-9-testcontainers-setup (Status: done)**

- **TestContainers Setup**: PostgreSQL 16 singleton available via `TestContainers.postgres` - use for all integration tests.
- **RLS Enforcement**: `RlsEnforcingDataSource` uses parameterized queries with `set_config()` - events table must respect this.
- **Event Store Isolation**: `@IsolatedEventStore` annotation available with TRUNCATE strategy - use for tests that need clean state.
- **Test Fixtures**: `TestTenantFixture.createTenant()` and `TestUserFixture.createUser()` available for test data.
- **Flyway Integration**: Flyway migration execution deferred to this story (Story 1.3) - this is where it gets integrated.

[Source: docs/sprint-artifacts/1-9-testcontainers-setup.md#Dev-Agent-Record]

### Architecture & Constraints

- **Module**: `eaf-eventsourcing` (interface + implementation together for MVP, can split later)
- **Schema**: `eaf_events` (separate from projection schemas)
- **RLS**: Events table MUST have `tenant_id` column for RLS policy (added in Story 1.6)
- **No Spring in Interface**: `EventStore` interface must be framework-agnostic (no Spring annotations)

### Technical Implementation Details

- **jOOQ**: Use `DSLContext` for type-safe SQL. jOOQ code generation not yet configured (Story 1.8), so use raw SQL via `dsl.execute()` or manual record mapping.
- **JSONB Serialization**: Jackson `ObjectMapper` with `JavaTimeModule` and `KotlinModule`. Store payload and metadata as JSONB strings.
- **Optimistic Locking**: Catch `DataIntegrityViolationException` (or PostgreSQL unique constraint violation `23505`) and return `ConcurrencyConflict`.
- **Version**: Starts at 1 (not 0). Each event increments version by 1.

### Project Structure Notes

- Migration location: `eaf/eaf-eventsourcing/src/main/resources/db/migration/V001__create_event_store.sql`
- EventStore interface: `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/EventStore.kt`
- PostgresEventStore: `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/PostgresEventStore.kt`
- Domain event types: `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/DomainEvent.kt`

### References

- [Source: docs/sprint-artifacts/tech-spec-epic-1.md#Story-1.3-Event-Store-Setup]
- [Source: docs/epics.md#Story-1.3-Event-Store-Setup]
- [Source: docs/architecture.md#ADR-003-Event-Sourcing-PostgreSQL]
- [Source: docs/test-design-system.md#TC-002-RLS-Enforcement]
- [Source: docs/test-design-system.md#TC-003-Event-Store-Isolation]

## Dev Agent Record

### Context Reference

- docs/sprint-artifacts/1-3-event-store-setup.context.xml

### Agent Model Used

- claude-opus-4-5-20251101

### Debug Log References

### Completion Notes List

- All 5 ACs implemented and tested
- 27 integration tests pass (PostgresEventStore: 13, Flyway: 7, RLS: 3, Immutability: 4)
- Test coverage: 87% (exceeds 80% requirement)
- Architecture tests pass
- PR #10 merged with CodeRabbit and Copilot review fixes

### File List

- `eaf/eaf-eventsourcing/src/main/resources/db/migration/V001__create_event_store.sql`
- `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/EventStore.kt`
- `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/DomainEvent.kt`
- `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/StoredEvent.kt`
- `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/PostgresEventStore.kt`
- `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/EventStoreObjectMapper.kt`
- `eaf/eaf-eventsourcing/src/test/kotlin/de/acci/eaf/eventsourcing/PostgresEventStoreIntegrationTest.kt`
- `eaf/eaf-eventsourcing/src/test/kotlin/de/acci/eaf/eventsourcing/FlywayMigrationIntegrationTest.kt`
- `eaf/eaf-eventsourcing/src/test/kotlin/de/acci/eaf/eventsourcing/RlsEnforcementIntegrationTest.kt`
- `eaf/eaf-eventsourcing/src/test/kotlin/de/acci/eaf/eventsourcing/EventImmutabilityIntegrationTest.kt`
- `eaf/eaf-eventsourcing/src/test/kotlin/de/acci/eaf/eventsourcing/TestEvents.kt`

## Code Review

### Review Date: 2025-11-26

### Reviewer: Senior Dev Agent (claude-opus-4-5-20251101)

### Review Outcome: APPROVED

### Acceptance Criteria Validation

| AC | Status | Evidence |
|----|--------|----------|
| AC1: Event Persistence | PASS | `PostgresEventStore.kt:97-110` - INSERT, all columns present in `V001__create_event_store.sql:9-25` |
| AC2: Optimistic Locking | PASS | `V001__create_event_store.sql:22` - UNIQUE (tenant_id, aggregate_id, version), `PostgresEventStore.kt:51-65` - conflict detection |
| AC3: Event Immutability | PASS | `V001__create_event_store.sql:77-85` - triggers, `V001__create_event_store.sql:67` - REVOKE |
| AC4: Flyway Migration | PASS | `V001__create_event_store.sql` exists with schema, tables, indexes, constraints |
| AC5: Event Loading | PASS | `PostgresEventStore.kt:68-86` - load() and loadFrom() with ORDER BY version ASC |

### Code Quality Assessment

| Aspect | Rating | Notes |
|--------|--------|-------|
| Test Coverage | EXCELLENT | 87% instruction coverage (exceeds 80% threshold) |
| Architecture Compliance | PASS | No Spring dependencies in interfaces, EAF module independent |
| Security | PASS | PreparedStatement usage, RLS support, immutability triggers |
| Documentation | GOOD | KDoc on all public APIs |
| Error Handling | GOOD | ConcurrencyConflict returns Result.Failure, race condition documented |

### Issues Found and Resolved

1. **HIGH - Multi-tenant unique constraint** (Copilot review): Changed from `UNIQUE (aggregate_id, version)` to `UNIQUE (tenant_id, aggregate_id, version)` - FIXED
2. **MEDIUM - SQL injection in tests**: Converted string interpolation to PreparedStatement - FIXED
3. **MEDIUM - Type mismatch INT vs Long**: Fixed `loadCurrentVersion()` to use `Int::class.java` with `.toLong()` - FIXED
4. **LOW - Redundant index**: Removed `idx_events_aggregate` (duplicated by unique constraint) - FIXED
5. **LOW - Race condition documentation**: Added comment explaining actualVersion timing - FIXED

### Recommendations for Future Stories

1. Consider adding snapshot support in a future story (table created but not used)
2. Event replay/projection support will be needed for CQRS implementation
3. Consider adding event type registry for type-safe deserialization

## Change Log

- 2025-11-26: Draft created from epics/tech-spec with SM agent (#create-story).
- 2025-11-26: Auto-improved after validation - added AC 3 test task, added test-design-system.md citations (#validate-create-story).
- 2025-11-26: Story context generated, status changed to ready-for-dev (#create-story-context).
- 2025-11-26: Implementation complete, PR #10 created (#dev-story).
- 2025-11-26: Copilot review comments addressed - multi-tenant constraint, SQL injection, type mismatch fixes.
- 2025-11-26: Code review APPROVED, status changed to done (#code-review).
