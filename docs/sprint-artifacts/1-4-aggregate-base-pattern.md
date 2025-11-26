# Story 1.4: Aggregate Base Pattern

**Status:** review

## Story

As a **developer**,
I want a base class for Event Sourced aggregates,
So that I can implement domain logic consistently.

## Acceptance Criteria

1. **Event Application**
   - Given I extend `AggregateRoot<TId>`, when I call `aggregate.applyEvent(event)`, then the event is added to `uncommittedEvents` list.
   - Version is incremented per applied event.

2. **Event Replay / Reconstitution**
   - `reconstitute(id, events)` rebuilds aggregate state from event history.
   - Events are applied in version order without adding to uncommittedEvents.

3. **Snapshot Support**
   - Snapshot support exists with configurable threshold (default: 100 events).
   - `AggregateSnapshot` data class stores serialized state + version + aggregateId + tenantId.
   - `SnapshotStore` interface provides `save()` and `load()` operations.

4. **Version Management**
   - Version starts at 0 for new aggregates.
   - Each event application increments version by 1.
   - After reconstitution, version equals number of events replayed.

5. **Uncommitted Events Lifecycle**
   - `uncommittedEvents` returns immutable list of events applied since last commit.
   - `clearUncommittedEvents()` clears the list after persistence.

## Tasks / Subtasks

- [x] Create `AggregateRoot<TId>` abstract class in `eaf-eventsourcing` (AC: 1, 2, 4, 5)
  - [x] Define abstract `id` property of type `TId`
  - [x] Add `version: Long` property starting at 0
  - [x] Add private `_uncommittedEvents` mutable list
  - [x] Add public `uncommittedEvents` property returning immutable list
  - [x] Implement `applyEvent(event, isReplay = false)` method
  - [x] Implement protected abstract `handleEvent(event)` method
  - [x] Implement `clearUncommittedEvents()` method
  - [x] Add companion object with `DEFAULT_SNAPSHOT_THRESHOLD = 100`

- [x] Create reconstitution support (AC: 2)
  - [x] Add `reconstitute(id: TId, events: List<StoredEvent>)` static factory pattern or method
  - [x] Ensure events are applied with `isReplay = true`
  - [x] Verify version equals event count after reconstitution

- [x] Create `AggregateSnapshot` data class in `eaf-eventsourcing` (AC: 3)
  - [x] Fields: `aggregateId` (UUID), `aggregateType` (String), `version` (Long), `state` (String/JSON), `tenantId` (UUID), `createdAt` (Instant)

- [x] Define `SnapshotStore` interface in `eaf-eventsourcing` (AC: 3)
  - [x] `suspend fun save(snapshot: AggregateSnapshot)`
  - [x] `suspend fun load(aggregateId: UUID): AggregateSnapshot?`

- [x] ~~Create Flyway migration `V002__create_snapshot_store.sql`~~ (AC: 3) - **SKIPPED: Snapshots table already exists in V001**
  - [x] ~~Create `eaf_events.snapshots` table~~ - Already in V001
  - [x] ~~Add unique constraint on `(tenant_id, aggregate_id)`~~ - Already in V001
  - [x] ~~Add index on `aggregate_id`~~ - Already in V001 (via unique constraint)
  - [x] ~~Enable RLS on snapshots table~~ - Will be done in Story 1.6

- [x] Write unit tests for `AggregateRoot` (AC: 1, 2, 4, 5)
  - [x] Test event application increments version
  - [x] Test uncommittedEvents contains applied events
  - [x] Test clearUncommittedEvents empties the list
  - [x] Test reconstitution replays events without uncommitting
  - [x] Test version equals event count after reconstitution

- [x] Write integration tests for `SnapshotStore` (AC: 3)
  - [x] Test save and load snapshot round-trip
  - [x] Test snapshot overwrites previous version for same aggregate
  - [x] Test load returns null for non-existent aggregate

- [x] Create example aggregate (test fixture) demonstrating pattern (AC: 1, 2)
  - [x] Create `TestAggregate` extending `AggregateRoot<UUID>`
  - [x] Create `TestEvent` sealed class with `Created` and `Updated` variants
  - [x] Implement `handleEvent()` for state mutations

## Dev Notes

### Learnings from Previous Story

#### From Story 1-3-event-store-setup (Status: done)

- **EventStore Interface**: `EventStore` in `eaf-eventsourcing` provides `append()`, `load()`, `loadFrom()` - use `load()` for reconstitution.
- **DomainEvent Interface**: `DomainEvent` with `aggregateType` and `EventMetadata` (tenantId, userId, correlationId, timestamp) - aggregates should emit events implementing this.
- **StoredEvent**: Contains `aggregateId`, `version`, `eventType`, `payload`, `metadata` - use for reconstitution.
- **PostgresEventStore**: Implementation in `eaf-eventsourcing` using jOOQ DSLContext.
- **Multi-tenant Unique Constraint**: Events table has `UNIQUE (tenant_id, aggregate_id, version)` - version management is tenant-scoped.
- **Version Starts at 1**: Events are persisted starting with version 1, so new aggregate version = 0 means no events yet.
- **Test Fixtures Available**: `TestTenantFixture`, `TestUserFixture`, `@IsolatedEventStore` available from Story 1.9.

[Source: docs/sprint-artifacts/1-3-event-store-setup.md#Dev-Agent-Record]

### Architecture & Constraints

- **Module**: `eaf-eventsourcing` (same module as EventStore)
- **No Spring Dependencies**: `AggregateRoot` and `SnapshotStore` interfaces must be framework-agnostic
- **Sealed Events**: Each aggregate should define its events as a sealed class (not enforced by base, but documented pattern)
- **Immutable Events**: Events are value objects - use data classes with val properties

### Technical Implementation Details

- **Generic Type `TId`**: Use `TId : Any` bound, typically `UUID` for aggregate IDs
- **Event Handling**: `handleEvent()` is protected abstract - concrete aggregates implement state mutation logic
- **Reconstitution Pattern**: Either static factory method `reconstitute(id, events)` or builder pattern
- **Snapshot Serialization**: Use Jackson ObjectMapper (same as EventStore) for state JSON
- **Snapshot Table RLS**: Must have `tenant_id` column and RLS policy matching events table

### Project Structure Notes

- AggregateRoot: `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/aggregate/AggregateRoot.kt`
- AggregateSnapshot: `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/snapshot/AggregateSnapshot.kt`
- SnapshotStore: `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/snapshot/SnapshotStore.kt`
- PostgresSnapshotStore: `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/snapshot/PostgresSnapshotStore.kt`
- Tests: `eaf/eaf-eventsourcing/src/test/kotlin/de/acci/eaf/eventsourcing/aggregate/AggregateRootTest.kt`
- SnapshotStore Tests: `eaf/eaf-eventsourcing/src/test/kotlin/de/acci/eaf/eventsourcing/snapshot/PostgresSnapshotStoreIntegrationTest.kt`

### References

- [Source: docs/sprint-artifacts/tech-spec-epic-1.md#Story-1.4-Aggregate-Base-Pattern]
- [Source: docs/epics.md#Story-1.4-Aggregate-Base-Pattern]
- [Source: docs/architecture.md#Aggregate-Pattern]
- [Source: docs/test-design-system.md#TC-003-Event-Store-Isolation]
- [Source: docs/sprint-artifacts/1-3-event-store-setup.md#File-List]

## Dev Agent Record

### Context Reference

- docs/sprint-artifacts/1-4-aggregate-base-pattern.context.xml

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

1. V002 migration task skipped: Discovery during implementation revealed snapshots table already exists in V001__create_event_store.sql
2. PostgresSnapshotStore SQL fix: Required explicit type casts (?::uuid, ?::timestamptz) for jOOQ raw SQL execution

### Completion Notes List

- ✅ Implemented AggregateRoot<TId> abstract class with event application, version tracking, and uncommitted events management
- ✅ Created reconstitution support via companion object factory method in TestAggregate (pattern demonstrated)
- ✅ Created AggregateSnapshot data class with all required fields
- ✅ Defined SnapshotStore interface with save() and load() operations
- ✅ Implemented PostgresSnapshotStore with upsert behavior (ON CONFLICT DO UPDATE)
- ✅ 16 unit tests for AggregateRoot covering all ACs (Event Application, Reconstitution, Version Management, Uncommitted Events)
- ✅ 5 integration tests for PostgresSnapshotStore (round-trip, upsert, null handling, complex JSON)
- ✅ TestAggregate + TestAggregateEvent sealed class as reference implementation

### File List

**New Files:**
- `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/aggregate/AggregateRoot.kt`
- `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/snapshot/AggregateSnapshot.kt`
- `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/snapshot/SnapshotStore.kt`
- `eaf/eaf-eventsourcing/src/main/kotlin/de/acci/eaf/eventsourcing/snapshot/PostgresSnapshotStore.kt`
- `eaf/eaf-eventsourcing/src/test/kotlin/de/acci/eaf/eventsourcing/aggregate/AggregateRootTest.kt`
- `eaf/eaf-eventsourcing/src/test/kotlin/de/acci/eaf/eventsourcing/aggregate/TestAggregate.kt`
- `eaf/eaf-eventsourcing/src/test/kotlin/de/acci/eaf/eventsourcing/snapshot/PostgresSnapshotStoreIntegrationTest.kt`

**Modified Files:**
- `docs/sprint-artifacts/sprint-status.yaml` (status: ready-for-dev → review)
- `docs/sprint-artifacts/1-4-aggregate-base-pattern.md` (this file)

## Change Log

- 2025-11-26: Draft created from epics/tech-spec with SM agent (#create-story).
- 2025-11-26: Added test-design-system.md citation after validation (#validate-create-story).
- 2025-11-26: Story context generated, status changed to ready-for-dev (#create-story-context).
- 2025-11-26: Implementation complete - all tasks done, all tests passing, status changed to review (#dev-story).
