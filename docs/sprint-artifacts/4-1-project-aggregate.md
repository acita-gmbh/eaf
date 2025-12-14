# Story 4.1: Project Aggregate

Status: review

## Story

As a **developer**,
I want a Project aggregate with full event-sourced lifecycle,
So that users can organize VMs into projects and admins can manage project membership.

## Business Value

**Organization:** Projects provide logical grouping of VMs, enabling users to find and manage related resources. Without projects, all VMs appear in a flat list - unmanageable at scale.

**Access Control:** Project membership determines who can request VMs and view project resources. This is the foundation for the quota enforcement in Story 4.8.

**Audit Trail:** Event sourcing captures the complete history of project changes - who created it, when members were added/removed, when it was archived.

## Acceptance Criteria

### AC-4.1.1: Create Project Command

**Given** I dispatch `CreateProjectCommand` with valid data
**When** the command executes successfully
**Then** `ProjectCreated` event is persisted with:
- [ ] `aggregateId` (ProjectId)
- [ ] `tenantId` (from context)
- [ ] `name` (ProjectName value object)
- [ ] `description` (optional String)
- [ ] `createdBy` (UserId)
- [ ] `occurredAt` (Instant)

**And** project state is `ACTIVE`
**And** version is `1`

### AC-4.1.2: Creator Auto-Assignment

**Given** a project is created
**When** the creation completes
**Then** `UserAssignedToProject` event is emitted with:
- [ ] `userId` = `createdBy`
- [ ] `role` = `PROJECT_ADMIN`
- [ ] `assignedBy` = `createdBy`

### AC-4.1.3: Project Name Uniqueness

**Given** project "Alpha" exists in tenant "T1"
**When** I attempt to create another project named "Alpha" in tenant "T1"
**Then** `ValidationFailed` error is returned with message "Project name already exists"

**And** project names are case-insensitive unique (alpha = Alpha = ALPHA)

### AC-4.1.4: Archived Project Update Rejection

**Given** project is in `ARCHIVED` status
**When** I dispatch `UpdateProjectCommand`
**Then** `InvalidStateException` is thrown with message "Cannot update archived project"

### AC-4.1.5: User Assignment

**Given** an active project exists
**When** I dispatch `AssignUserCommand` with userId and role
**Then** `UserAssignedToProject` event is persisted with:
- [ ] `userId` (assigned user)
- [ ] `role` (`MEMBER` or `PROJECT_ADMIN`)
- [ ] `assignedBy` (admin performing the action)
- [ ] `occurredAt`

### AC-4.1.6: User Removal

**Given** user "U1" is a member of project "P1"
**When** I dispatch `RemoveUserCommand` for "U1"
**Then** `UserRemovedFromProject` event is persisted
**And** user "U1" can no longer access project "P1"

### AC-4.1.7: Creator Cannot Be Removed

**Given** user "U1" created project "P1"
**When** I dispatch `RemoveUserCommand` for "U1"
**Then** `IllegalArgumentException` is thrown with message "Cannot remove the project creator"

### AC-4.1.8: Project Archive

**Given** an active project exists
**When** I dispatch `ArchiveProjectCommand`
**Then** `ProjectArchived` event is persisted
**And** project status changes to `ARCHIVED`

### AC-4.1.9: Project Unarchive

**Given** an archived project exists
**When** I dispatch `UnarchiveProjectCommand`
**Then** `ProjectUnarchived` event is persisted
**And** project status changes to `ACTIVE`

### Satisfied Functional Requirements

- **FR10:** Users can view list of projects they have access to (partial - aggregate foundation)
- **FR11:** Admins can create new projects with name and description
- **FR12:** Admins can edit project details
- **FR13:** Admins can archive projects (soft delete)

## Technical Context

### Pattern: Event-Sourced Aggregate

The `ProjectAggregate` follows the same patterns as `VmRequestAggregate` (see `dcm-domain/src/main/kotlin/.../vmrequest/VmRequestAggregate.kt`):

```kotlin
// Pattern from VmRequestAggregate
public class ProjectAggregate private constructor(
    override val id: ProjectId
) : AggregateRoot<ProjectId>() {

    // Properties with private setters
    public var name: ProjectName = ProjectName.of("placeholder")
        private set

    // Event handler dispatch
    override fun handleEvent(event: DomainEvent) {
        when (event) {
            is ProjectCreated -> apply(event)
            is ProjectUpdated -> apply(event)
            // ...
        }
    }

    // Factory method for creation
    companion object {
        public fun create(...): ProjectAggregate { ... }
        public fun reconstitute(id, events): ProjectAggregate { ... }
    }
}
```

### State Machine

```text
                          ┌───────────────────┐
                          │                   │
   ┌──────────────────────┤      ACTIVE       │◄──────────────────┐
   │    UpdateProject     │                   │    Unarchive      │
   │    AssignUser        └─────────┬─────────┘                   │
   │    RemoveUser                  │                             │
   │         │                      │ Archive                     │
   │         │                      ▼                             │
   │         │            ┌───────────────────┐                   │
   │         └───────────▶│                   │───────────────────┘
                          │     ARCHIVED      │
                          │                   │
                          └───────────────────┘
```

**Invariants:**
- Only ACTIVE projects can be updated
- Only ACTIVE projects can have members assigned/removed
- ARCHIVED projects reject all mutations except Unarchive
- Project creator cannot be removed

### Value Objects

```kotlin
// ProjectId - UUID wrapper
@JvmInline
value class ProjectId(val value: UUID) {
    companion object {
        fun generate(): ProjectId = ProjectId(UUID.randomUUID())
        fun fromString(s: String): ProjectId = ProjectId(UUID.fromString(s))
    }
}

// ProjectName - Validated name with constraints
@JvmInline
value class ProjectName(val value: String) {
    init {
        require(value.isNotBlank()) { "Project name cannot be blank" }
        require(value.length in 3..100) { "Project name must be 3-100 characters" }
        require(value.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9\\s\\-_]*$"))) {
            "Project name must start with alphanumeric and contain only alphanumeric, spaces, hyphens, underscores"
        }
    }

    companion object {
        fun of(name: String): ProjectName = ProjectName(name.trim())
    }
}
```

### Database Migrations

**V013__create_projects_table.sql** (from tech spec Section 4.2.1):
- `PROJECTS` table with RLS enabled
- UNIQUE constraint on `(TENANT_ID, NAME)` - case-insensitive via CITEXT or LOWER()

**V014__create_project_members_table.sql** (from tech spec Section 4.2.2):
- `PROJECT_MEMBERS` table with RLS enabled
- Foreign key to `PROJECTS(ID)` with CASCADE delete
- UNIQUE constraint on `(PROJECT_ID, USER_ID)`

## Tasks / Subtasks

- [x] **Task 1: Create Value Objects (AC: 4.1.1, 4.1.3)**
  - [x] Create `ProjectId` in `dcm-domain/src/main/kotlin/de/acci/dcm/domain/project/ProjectId.kt`
  - [x] Create `ProjectName` with validation in `dcm-domain/src/main/kotlin/de/acci/dcm/domain/project/ProjectName.kt`
  - [x] Create `ProjectStatus` enum (ACTIVE, ARCHIVED)
  - [x] Create `ProjectRole` enum (MEMBER, PROJECT_ADMIN)
  - [x] Create `ProjectMember` data class
  - [x] Write unit tests for ProjectName validation (blank, length, character constraints)

- [x] **Task 2: Create Domain Events (AC: 4.1.1, 4.1.2, 4.1.5-4.1.9)**
  - [x] Create `ProjectCreated` event
  - [x] Create `ProjectUpdated` event
  - [x] Create `ProjectArchived` event
  - [x] Create `ProjectUnarchived` event
  - [x] Create `UserAssignedToProject` event
  - [x] Create `UserRemovedFromProject` event
  - [x] Register all events in `EventTypeRegistry` (CRITICAL - see checklist below)

- [x] **Task 3: Implement ProjectAggregate (AC: all)**
  - [x] Create `ProjectAggregate.kt` following VmRequestAggregate pattern
  - [x] Implement `create()` factory method with auto-assign creator
  - [x] Implement `update()` method with archived guard
  - [x] Implement `archive()` method with state check
  - [x] Implement `unarchive()` method with state check
  - [x] Implement `assignUser()` method
  - [x] Implement `removeUser()` method with creator protection
  - [x] Implement `reconstitute()` for event replay
  - [x] Implement `handleEvent()` dispatcher

- [x] **Task 4: Create Database Migrations**
  - [x] Create `V013__create_projects_table.sql` with RLS
  - [x] Create `V014__create_project_members_table.sql` with RLS
  - [x] Add indexes for tenant_id, status
  - [x] Verify RLS policies enforce tenant isolation
  - [x] Run jOOQ code generation (`./gradlew generateJooq`)

- [x] **Task 5: Create Command Handlers**
  - [x] Create `CreateProjectCommand` and `CreateProjectHandler`
  - [x] Create `UpdateProjectCommand` and `UpdateProjectHandler`
  - [x] Create `ArchiveProjectCommand` and `ArchiveProjectHandler`
  - [x] Create `UnarchiveProjectCommand` and `UnarchiveProjectHandler`
  - [x] Create `AssignUserToProjectCommand` and handler
  - [x] Create `RemoveUserFromProjectCommand` and handler
  - [x] Add uniqueness check in `CreateProjectHandler` (query before create)

- [x] **Task 6: Create Project Projection (Read Model)**
  - [x] Create `ProjectProjection` table handler
  - [x] Listen for `ProjectCreated`, `ProjectUpdated`, `ProjectArchived`, `ProjectUnarchived`
  - [x] Create `ProjectMemberProjection` for member list
  - [x] Listen for `UserAssignedToProject`, `UserRemovedFromProject`
  - [x] Create `ProjectQueryService` interface in `dcm-application`
  - [x] Implement `JooqProjectQueryService` in `dcm-infrastructure`

- [x] **Task 7: Write Tests (Tests First Pattern)**
  - [x] `ProjectAggregateTest.kt` - Creation, state machine, invariants
  - [x] `ProjectAggregateArchiveTest.kt` - Archive/unarchive behavior
  - [x] `ProjectAggregateMemberTest.kt` - Assignment/removal, creator protection
  - [x] `ProjectNameTest.kt` - Validation edge cases
  - [x] `CreateProjectHandlerTest.kt` - Uniqueness, happy path
  - [x] Handler tests for UpdateProjectHandler, ArchiveProjectHandler

## Dev Notes

### Event Serialization Checklist (CRITICAL)

Follow the 5-step checklist from `project-context.md` for EVERY new event:

```
1. Define event class: dcm-domain/src/main/kotlin/de/acci/dcm/domain/project/events/
2. Register in EventTypeRegistry: Add mapping "ProjectCreated" -> ProjectCreated::class
3. Add Jackson @JsonTypeName: @JsonTypeName("ProjectCreated")
4. Create deserializer test: Verify JSON -> Event
5. Create serialization test: Verify Event -> JSON -> Event roundtrip
```

**Common Bug:** Forgetting to register event types causes `UnknownEventTypeException` at runtime during reconstitute.

### Uniqueness Check Pattern

Project name uniqueness must be checked BEFORE creating the aggregate (not inside the aggregate):

```kotlin
// In CreateProjectHandler
suspend fun handle(command: CreateProjectCommand): Result<ProjectId, CreateProjectError> {
    // 1. Check uniqueness via projection (read model)
    val existing = projectQueryService.findByName(command.tenantId, command.name)
    if (existing != null) {
        return Result.failure(ProjectNameAlreadyExists(command.name))
    }

    // 2. Create aggregate (command side)
    val aggregate = ProjectAggregate.create(...)

    // 3. Persist events
    eventStore.append(aggregate.id.value, aggregate.uncommittedEvents, aggregate.version)

    return Result.success(aggregate.id)
}
```

**Why not in aggregate?** Aggregates don't query the database - they only handle their own state. Uniqueness is a cross-aggregate constraint enforced at the handler level.

### Projection Update Strategy

Use synchronous projection updates in the same transaction:

```kotlin
// In EventStoreImpl after persisting events
events.forEach { event ->
    when (event) {
        is ProjectCreated -> projectProjection.handle(event)
        is UserAssignedToProject -> memberProjection.handle(event)
        // ...
    }
}
```

**Important for Story 4.8:** The quota usage projection will follow this same pattern.

### Case-Insensitive Uniqueness

Option 1: PostgreSQL CITEXT extension
```sql
CREATE EXTENSION IF NOT EXISTS citext;
ALTER TABLE "PROJECTS" ALTER COLUMN "NAME" TYPE citext;
```

Option 2: LOWER() in constraint (no extension needed)
```sql
CREATE UNIQUE INDEX idx_project_name_tenant_lower
    ON "PROJECTS" ("TENANT_ID", LOWER("NAME"));
```

Recommend Option 2 (no PostgreSQL extension dependency).

### Test Data Fixtures

```kotlin
// ProjectTestFixtures.kt
object ProjectTestFixtures {
    val DEFAULT_TENANT = TenantId.fromString("00000000-0000-0000-0000-000000000001")
    val DEFAULT_USER = UserId.fromString("00000000-0000-0000-0000-000000000002")

    fun createTestProject(
        name: String = "Test Project",
        tenantId: TenantId = DEFAULT_TENANT,
        createdBy: UserId = DEFAULT_USER
    ): ProjectAggregate = ProjectAggregate.create(
        name = ProjectName.of(name),
        description = "Test project description",
        tenantId = tenantId,
        createdBy = createdBy,
        metadata = EventMetadata.create(tenantId, createdBy)
    )
}
```

### Source Tree Locations

**Domain Layer (`dcm-domain`):**
- `src/main/kotlin/de/acci/dcm/domain/project/`
  - `ProjectId.kt`
  - `ProjectName.kt`
  - `ProjectStatus.kt`
  - `ProjectRole.kt`
  - `ProjectMember.kt`
  - `ProjectAggregate.kt`
  - `events/` (all project events)

**Application Layer (`dcm-application`):**
- `src/main/kotlin/de/acci/dcm/application/project/`
  - `commands/` (CreateProjectCommand, etc.)
  - `handlers/` (CreateProjectHandler, etc.)
  - `queries/` (ProjectQueryService interface)

**Infrastructure Layer (`dcm-infrastructure`):**
- `src/main/kotlin/de/acci/dcm/infrastructure/project/`
  - `JooqProjectQueryService.kt`
  - `ProjectProjectionHandler.kt`
  - `ProjectMemberProjectionHandler.kt`

**Database:**
- `src/main/resources/db/migration/`
  - `V013__create_projects_table.sql`
  - `V014__create_project_members_table.sql`

### Previous Story Patterns to Apply

**From Story 1.4 (Aggregate Base Pattern):**
- Use `AggregateRoot<ProjectId>` as base class
- Implement `handleEvent()` for all event types
- Use `applyEvent(event)` to record and apply events
- Use `applyEvent(event, isReplay = true)` in `reconstitute()`

**From Story 2.6 (VmRequest Aggregate):**
- Factory method pattern: `ProjectAggregate.create(...)`
- State transition guards: `require(isActive) { "Cannot ..." }`
- Idempotent operations where appropriate
- Comprehensive KDoc on public methods

**From Epic 3 Retrospective:**
- Always run `./gradlew clean build` before committing
- Register events in `EventTypeRegistry` immediately after creating them
- Write deserializer tests for all events

### What's Already Implemented (Do NOT Duplicate)

| Feature | Status | Location |
|---------|--------|----------|
| `AggregateRoot<T>` base | Done (1.4) | `eaf-eventsourcing/aggregate/` |
| `EventStore` interface | Done (1.3) | `eaf-eventsourcing/` |
| `EventMetadata` | Done (1.3) | `eaf-eventsourcing/` |
| `TenantId`, `UserId` | Done (1.2) | `eaf-core/types/` |
| RLS infrastructure | Done (1.6) | Flyway migrations |
| jOOQ code generation | Done (1.8) | Gradle plugin |

## Project Context Reference

- **Tech Spec:** docs/sprint-artifacts/tech-spec-epic-4.md (Section 4: Detailed Design)
- **Architecture:** docs/architecture.md (ADR-001: Module Structure)
- **Reference Aggregate:** dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/VmRequestAggregate.kt
- **Event Checklist:** docs/project-context.md (5-step checklist)

## Definition of Done

- [x] All 9 acceptance criteria implemented and tested
- [x] Unit tests ≥70% coverage (koverVerify threshold met)
- [x] Mutation score ≥70%
- [x] Integration test with PostgreSQL (RLS via migrations)
- [x] All events registered in `EventTypeRegistry`
- [x] Deserializer tests for all events
- [x] `./gradlew clean build` passes
- [ ] Code review approved

## Dev Agent Record

### Context Reference
- Epic 4 tech spec fully analyzed (comprehensive aggregate design)
- VmRequestAggregate analyzed as implementation pattern reference
- epics.md reviewed for story definition
- project-context.md patterns applied (event serialization checklist)

### Agent Model Used
Claude Opus 4.5 (via Claude Code)

### Notes
- This is the foundation story for Epic 4 - all other stories depend on it
- Project uniqueness check happens at handler level, not aggregate level
- Creator auto-assignment happens as second event in same transaction
- Case-insensitive uniqueness recommended via LOWER() index (no CITEXT extension)
