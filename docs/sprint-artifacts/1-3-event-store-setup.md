# Story 1.3: Event Store Setup

**Status:** ready-for-dev

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

- [ ] Create Flyway migration `V001__create_event_store.sql` in `eaf-eventsourcing/src/main/resources/db/migration/` (AC: 1, 3, 4)
  - [ ] Create `eaf_events` schema
  - [ ] Create `events` table with all columns
  - [ ] Add unique constraint on `(aggregate_id, version)`
  - [ ] Add indexes on `aggregate_id` and `tenant_id`
  - [ ] Revoke UPDATE/DELETE permissions on events table
- [ ] Define `DomainEvent` interface in `eaf-eventsourcing` (AC: 1)
  - [ ] Include `aggregateType`, `metadata` properties
  - [ ] Define `EventMetadata` data class with `tenantId`, `userId`, `correlationId`, `timestamp`
- [ ] Define `EventStore` interface in `eaf-eventsourcing` (AC: 1, 2, 5)
  - [ ] `append(aggregateId, events, expectedVersion): Result<Long, EventStoreError>`
  - [ ] `load(aggregateId): List<StoredEvent>`
  - [ ] `loadFrom(aggregateId, fromVersion): List<StoredEvent>`
- [ ] Define `EventStoreError` sealed class (AC: 2)
  - [ ] `ConcurrencyConflict(aggregateId, expectedVersion, actualVersion)`
- [ ] Implement `PostgresEventStore` in `eaf-eventsourcing` using jOOQ DSLContext (AC: 1, 2, 5)
  - [ ] Implement `append()` with version increment and conflict detection
  - [ ] Implement `load()` returning events ordered by version ASC
  - [ ] Implement `loadFrom()` for partial replay
- [ ] Configure Jackson `ObjectMapper` for JSONB serialization in Spring context (AC: 1)
  - [ ] Register Kotlin module
  - [ ] Configure ISO-8601 timestamp format
- [ ] Write unit tests for `PostgresEventStore` (AC: 1, 2, 5)
  - [ ] Test successful event append
  - [ ] Test optimistic locking conflict detection
  - [ ] Test event loading by aggregate ID
  - [ ] Test partial loading from version
- [ ] Write integration test verifying Flyway migration runs successfully (AC: 4)
- [ ] Write integration test verifying RLS applies to events table via `RlsEnforcingDataSource` (AC: 1)
- [ ] Write integration test verifying UPDATE/DELETE operations are rejected on events table (AC: 3)
  - [ ] Attempt UPDATE on existing event row, verify permission denied error
  - [ ] Attempt DELETE on existing event row, verify permission denied error

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

### Debug Log References

### Completion Notes List

### File List

## Change Log

- 2025-11-26: Draft created from epics/tech-spec with SM agent (#create-story).
- 2025-11-26: Auto-improved after validation - added AC 3 test task, added test-design-system.md citations (#validate-create-story).
- 2025-11-26: Story context generated, status changed to ready-for-dev (#create-story-context).
