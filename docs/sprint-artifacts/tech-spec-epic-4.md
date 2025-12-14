# Epic 4: Projects & Quota - Technical Specification

**Version:** 1.0
**Created:** 2025-12-13
**Author:** Winston (Architect Agent)
**Status:** Draft

---

## 1. Overview

### 1.1 Epic Summary

| Attribute | Value |
|-----------|-------|
| **Epic ID** | Epic 4 |
| **Title** | Projects & Quota |
| **Goal** | Implement project organization and resource quota management |
| **Stories** | 9 |
| **Risk Level** | **MEDIUM** (Internal business rules, no external integrations) |
| **FRs Covered** | FR10-FR14, FR82-FR84, FR87 (9 FRs) |

### 1.2 User Value Statement

> "My VMs are organized and I know exactly how much budget remains" - Clear resource boundaries and organization.

### 1.3 Risk Profile Comparison

Epic 4 has a fundamentally different risk profile than Epic 3:

| Factor | Epic 3 (VMware) | Epic 4 (Projects & Quota) |
|--------|-----------------|---------------------------|
| External dependencies | vCenter API, network, credentials | None |
| Async complexity | Multi-minute provisioning | Synchronous operations |
| Failure modes | Infrastructure failures, timeouts | Business rule violations |
| Recovery patterns | Saga compensation, retry | Optimistic locking, validation |
| Testing approach | VCSIM containers | In-memory repositories |

### 1.4 Key Technical Challenges

| Challenge | Impact | Mitigation |
|-----------|--------|------------|
| **Race conditions** | Concurrent quota consumption | Optimistic locking, version checks |
| **Quota aggregation** | Performance on large datasets | Cached projections, incremental updates |
| **Multi-aggregate consistency** | Project + Quota + VmRequest | Eventual consistency, compensation |
| **Admin permission boundaries** | Authorization complexity | Role-based access at controller level |

---

## 2. Objectives & Scope

### 2.1 In Scope

1. **Project Management** (Stories 4.1-4.5)
   - Project aggregate with full lifecycle (create, edit, archive)
   - User assignment to projects
   - Project list and detail views
   - VM listing within projects

2. **Quota Configuration** (Story 4.7)
   - Tenant-level quota settings
   - Configurable limits: VMs, vCPUs, RAM, Storage
   - Default quota handling

3. **Quota Enforcement** (Story 4.8)
   - Visibility of remaining quota in VM request form
   - Synchronous quota validation on request submission
   - Quota exceeded error handling

4. **Resource Dashboard** (Story 4.9)
   - Real-time utilization metrics
   - Project and user breakdowns
   - Trend visualization (30-day)

### 2.2 Out of Scope

- Project-level quotas (tenant-level only for MVP)
- Cost allocation/chargeback
- Budget alerts/notifications
- Project hierarchy (flat structure only)
- Cross-tenant project sharing

### 2.3 Technical Debt from Epic 3 to Address

| Item | Source | Action in Epic 4 |
|------|--------|------------------|
| ProjectDirectory interface | Epic 3 Retro | Create proper ProjectQueryService |
| Project name in VmRequest | Story 3.7 | Replace UUID with Project reference |
| Quota visibility in request form | Epic 2 | Implement actual quota checks |

---

## 3. Architecture Alignment

### 3.1 Aggregate Design

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Project Aggregate                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │ ProjectAggregate                                                        ││
│  │   id: ProjectId                                                         ││
│  │   tenantId: TenantId                                                    ││
│  │   name: ProjectName                                                     ││
│  │   description: String?                                                  ││
│  │   status: ProjectStatus (ACTIVE | ARCHIVED)                             ││
│  │   members: Set<ProjectMember>                                           ││
│  │   createdBy: UserId                                                     ││
│  │   version: Long                                                         ││
│  │                                                                         ││
│  │   Commands:                                                             ││
│  │   - CreateProjectCommand → ProjectCreated                               ││
│  │   - UpdateProjectCommand → ProjectUpdated                               ││
│  │   - ArchiveProjectCommand → ProjectArchived                             ││
│  │   - UnarchiveProjectCommand → ProjectUnarchived                         ││
│  │   - AssignUserCommand → UserAssignedToProject                           ││
│  │   - RemoveUserCommand → UserRemovedFromProject                          ││
│  └─────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                           TenantQuota Aggregate                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │ TenantQuotaAggregate                                                    ││
│  │   id: TenantQuotaId                                                     ││
│  │   tenantId: TenantId                                                    ││
│  │   limits: QuotaLimits (maxVms, maxVCpus, maxRamGb, maxStorageGb)        ││
│  │   version: Long                                                         ││
│  │                                                                         ││
│  │   Commands:                                                             ││
│  │   - SetQuotaLimitsCommand → QuotaLimitsUpdated                          ││
│  │   - ClearQuotaLimitsCommand → QuotaLimitsCleared                        ││
│  └─────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Module Dependencies

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                          dcm-app (Spring Boot)                              │
│   ┌─────────────┐   ┌──────────────────┐   ┌────────────────┐              │
│   │  dcm-api   │──▶│ dcm-application │──▶│  dcm-domain   │              │
│   │  (REST)     │   │ (Handlers)       │   │  (Aggregates)  │              │
│   └─────────────┘   └──────────────────┘   └────────────────┘              │
│         │                    │                      ▲                       │
│         │                    │                      │                       │
│         ▼                    ▼                      │                       │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │                    dcm-infrastructure                               │  │
│   │   ┌───────────────────┐   ┌────────────────────────────────────┐    │  │
│   │   │ ProjectRepository │   │ QuotaProjectionRepository          │    │  │
│   │   │ (jOOQ)            │   │ (Cached usage aggregation)         │    │  │
│   │   └───────────────────┘   └────────────────────────────────────┘    │  │
│   │   ┌───────────────────┐   ┌────────────────────────────────────┐    │  │
│   │   │ TenantQuotaRepo   │   │ ResourceUtilizationProjection     │    │  │
│   │   │ (Event Sourced)   │   │ (Dashboard metrics)                │    │  │
│   │   └───────────────────┘   └────────────────────────────────────┘    │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 Quota Enforcement Flow

```text
User submits VM Request
         │
         ▼
┌─────────────────────────────────────────┐
│ CreateVmRequestHandler                   │
│                                          │
│ 1. Validate request fields               │
│ 2. Load tenant quota limits ─────────────┼──► TenantQuotaAggregate
│ 3. Load current usage ───────────────────┼──► QuotaUsageProjection
│ 4. Calculate new usage (current + size)  │
│ 5. Check: new usage <= limits?           │
│    ├─► YES: Create VmRequest aggregate   │
│    └─► NO:  Return QuotaExceeded error   │
│ 6. Persist VmRequestCreated event        │
│ 7. Update quota usage projection ────────┼──► QuotaUsageProjection (increment)
└─────────────────────────────────────────┘
         │
         ▼
   On VmProvisioned event:
   QuotaUsageProjection confirms usage (no change, already counted)

   On VmDeleted event (future):
   QuotaUsageProjection decrements usage
```

### 3.4 Architecture Constraints (Konsist Enforced)

| Rule | Enforcement |
|------|-------------|
| `dcm-domain` MUST NOT import `org.springframework.*` | Konsist `ArchitectureTest` |
| Project uniqueness within tenant | Domain invariant + DB constraint |
| Quota checks synchronous (not eventual) | Handler implementation |
| Archived projects reject new VM requests | Aggregate state machine |

---

## 4. Detailed Design

### 4.1 Domain Model Extensions

#### 4.1.1 New Domain Events

```kotlin
// dcm-domain/src/main/kotlin/de/acci/dcm/domain/project/events/

// Project lifecycle events
data class ProjectCreated(
    override val aggregateId: ProjectId,
    override val tenantId: TenantId,
    val name: ProjectName,
    val description: String?,
    val createdBy: UserId,
    override val occurredAt: Instant
) : DomainEvent

data class ProjectUpdated(
    override val aggregateId: ProjectId,
    override val tenantId: TenantId,
    val name: ProjectName?,          // null = unchanged
    val description: String?,        // null = unchanged
    val updatedBy: UserId,
    override val occurredAt: Instant
) : DomainEvent

data class ProjectArchived(
    override val aggregateId: ProjectId,
    override val tenantId: TenantId,
    val archivedBy: UserId,
    override val occurredAt: Instant
) : DomainEvent

data class ProjectUnarchived(
    override val aggregateId: ProjectId,
    override val tenantId: TenantId,
    val unarchivedBy: UserId,
    override val occurredAt: Instant
) : DomainEvent

// Member management events
data class UserAssignedToProject(
    override val aggregateId: ProjectId,
    override val tenantId: TenantId,
    val userId: UserId,
    val role: ProjectRole,
    val assignedBy: UserId,
    override val occurredAt: Instant
) : DomainEvent

data class UserRemovedFromProject(
    override val aggregateId: ProjectId,
    override val tenantId: TenantId,
    val userId: UserId,
    val removedBy: UserId,
    override val occurredAt: Instant
) : DomainEvent
```

```kotlin
// dcm-domain/src/main/kotlin/de/acci/dcm/domain/quota/events/

data class QuotaLimitsUpdated(
    override val aggregateId: TenantQuotaId,
    override val tenantId: TenantId,
    val limits: QuotaLimits,
    val updatedBy: UserId,
    override val occurredAt: Instant
) : DomainEvent

data class QuotaLimitsCleared(
    override val aggregateId: TenantQuotaId,
    override val tenantId: TenantId,
    val clearedBy: UserId,
    override val occurredAt: Instant
) : DomainEvent
```

#### 4.1.2 New Value Objects

```kotlin
// Project identifiers
@JvmInline
value class ProjectId(val value: UUID)

@JvmInline
value class ProjectName(val value: String) {
    init {
        require(value.isNotBlank()) { "Project name cannot be blank" }
        require(value.length in 3..100) { "Project name must be 3-100 characters" }
        require(value.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9\\s\\-_]*$"))) {
            "Project name must start with alphanumeric and contain only alphanumeric, spaces, hyphens, underscores"
        }
    }
}

enum class ProjectStatus { ACTIVE, ARCHIVED }

enum class ProjectRole { MEMBER, PROJECT_ADMIN }

data class ProjectMember(
    val userId: UserId,
    val role: ProjectRole,
    val assignedAt: Instant
)

// Quota value objects
@JvmInline
value class TenantQuotaId(val value: UUID)

data class QuotaLimits(
    val maxVms: Int?,           // null = unlimited
    val maxVCpus: Int?,
    val maxRamGb: Int?,
    val maxStorageGb: Int?
) {
    init {
        maxVms?.let { require(it >= 0) { "maxVms cannot be negative" } }
        maxVCpus?.let { require(it >= 0) { "maxVCpus cannot be negative" } }
        maxRamGb?.let { require(it >= 0) { "maxRamGb cannot be negative" } }
        maxStorageGb?.let { require(it >= 0) { "maxStorageGb cannot be negative" } }
    }

    companion object {
        val UNLIMITED = QuotaLimits(null, null, null, null)
    }
}

data class QuotaUsage(
    val currentVms: Int,
    val currentVCpus: Int,
    val currentRamGb: Int,
    val currentStorageGb: Int
) {
    fun isWithinLimits(limits: QuotaLimits): Boolean =
        (limits.maxVms == null || currentVms <= limits.maxVms) &&
        (limits.maxVCpus == null || currentVCpus <= limits.maxVCpus) &&
        (limits.maxRamGb == null || currentRamGb <= limits.maxRamGb) &&
        (limits.maxStorageGb == null || currentStorageGb <= limits.maxStorageGb)

    fun wouldExceedWith(size: VmSize, limits: QuotaLimits): QuotaViolation? {
        val newVms = currentVms + 1
        val newVCpus = currentVCpus + size.cpuCores
        val newRamGb = currentRamGb + size.ramGb
        val newStorageGb = currentStorageGb + size.diskGb

        return when {
            limits.maxVms != null && newVms > limits.maxVms ->
                QuotaViolation.VM_COUNT_EXCEEDED
            limits.maxVCpus != null && newVCpus > limits.maxVCpus ->
                QuotaViolation.VCPU_EXCEEDED
            limits.maxRamGb != null && newRamGb > limits.maxRamGb ->
                QuotaViolation.RAM_EXCEEDED
            limits.maxStorageGb != null && newStorageGb > limits.maxStorageGb ->
                QuotaViolation.STORAGE_EXCEEDED
            else -> null
        }
    }
}

enum class QuotaViolation(val userMessage: String) {
    VM_COUNT_EXCEEDED("Maximum VM count reached"),
    VCPU_EXCEEDED("Maximum vCPU allocation reached"),
    RAM_EXCEEDED("Maximum RAM allocation reached"),
    STORAGE_EXCEEDED("Maximum storage allocation reached")
}
```

#### 4.1.3 ProjectAggregate

```kotlin
// dcm-domain/src/main/kotlin/de/acci/dcm/domain/project/ProjectAggregate.kt

class ProjectAggregate private constructor(
    id: ProjectId,
    tenantId: TenantId
) : AggregateRoot<ProjectId, ProjectEvent>(id, tenantId) {

    private lateinit var name: ProjectName
    private var description: String? = null
    private var status: ProjectStatus = ProjectStatus.ACTIVE
    private val members: MutableSet<ProjectMember> = mutableSetOf()
    private lateinit var createdBy: UserId

    val isActive: Boolean get() = status == ProjectStatus.ACTIVE
    val isArchived: Boolean get() = status == ProjectStatus.ARCHIVED

    fun getMemberRole(userId: UserId): ProjectRole? =
        members.find { it.userId == userId }?.role

    companion object {
        fun create(
            id: ProjectId,
            tenantId: TenantId,
            name: ProjectName,
            description: String?,
            createdBy: UserId,
            clock: Clock
        ): ProjectAggregate {
            val aggregate = ProjectAggregate(id, tenantId)
            aggregate.recordEvent(
                ProjectCreated(
                    aggregateId = id,
                    tenantId = tenantId,
                    name = name,
                    description = description,
                    createdBy = createdBy,
                    occurredAt = clock.instant()
                )
            )
            // Creator is automatically assigned as PROJECT_ADMIN
            aggregate.recordEvent(
                UserAssignedToProject(
                    aggregateId = id,
                    tenantId = tenantId,
                    userId = createdBy,
                    role = ProjectRole.PROJECT_ADMIN,
                    assignedBy = createdBy,
                    occurredAt = clock.instant()
                )
            )
            return aggregate
        }
    }

    fun update(
        newName: ProjectName?,
        newDescription: String?,
        updatedBy: UserId,
        clock: Clock
    ) {
        require(isActive) { "Cannot update archived project" }
        if (newName == null && newDescription == null) return // No-op

        recordEvent(
            ProjectUpdated(
                aggregateId = id,
                tenantId = tenantId,
                name = newName,
                description = newDescription,
                updatedBy = updatedBy,
                occurredAt = clock.instant()
            )
        )
    }

    fun archive(archivedBy: UserId, clock: Clock) {
        require(isActive) { "Project is already archived" }
        recordEvent(
            ProjectArchived(
                aggregateId = id,
                tenantId = tenantId,
                archivedBy = archivedBy,
                occurredAt = clock.instant()
            )
        )
    }

    fun unarchive(unarchivedBy: UserId, clock: Clock) {
        require(isArchived) { "Project is not archived" }
        recordEvent(
            ProjectUnarchived(
                aggregateId = id,
                tenantId = tenantId,
                unarchivedBy = unarchivedBy,
                occurredAt = clock.instant()
            )
        )
    }

    fun assignUser(userId: UserId, role: ProjectRole, assignedBy: UserId, clock: Clock) {
        require(isActive) { "Cannot assign users to archived project" }
        require(members.none { it.userId == userId }) { "User is already a member" }

        recordEvent(
            UserAssignedToProject(
                aggregateId = id,
                tenantId = tenantId,
                userId = userId,
                role = role,
                assignedBy = assignedBy,
                occurredAt = clock.instant()
            )
        )
    }

    fun removeUser(userId: UserId, removedBy: UserId, clock: Clock) {
        require(isActive) { "Cannot remove users from archived project" }
        require(members.any { it.userId == userId }) { "User is not a member" }
        require(userId != createdBy) { "Cannot remove the project creator" }

        recordEvent(
            UserRemovedFromProject(
                aggregateId = id,
                tenantId = tenantId,
                userId = userId,
                removedBy = removedBy,
                occurredAt = clock.instant()
            )
        )
    }

    override fun apply(event: ProjectEvent) {
        when (event) {
            is ProjectCreated -> {
                name = event.name
                description = event.description
                createdBy = event.createdBy
                status = ProjectStatus.ACTIVE
            }
            is ProjectUpdated -> {
                event.name?.let { name = it }
                event.description?.let { description = it }
            }
            is ProjectArchived -> {
                status = ProjectStatus.ARCHIVED
            }
            is ProjectUnarchived -> {
                status = ProjectStatus.ACTIVE
            }
            is UserAssignedToProject -> {
                members.add(ProjectMember(
                    userId = event.userId,
                    role = event.role,
                    assignedAt = event.occurredAt
                ))
            }
            is UserRemovedFromProject -> {
                members.removeIf { it.userId == event.userId }
            }
        }
    }
}
```

#### 4.1.4 TenantQuotaAggregate

```kotlin
// dcm-domain/src/main/kotlin/de/acci/dcm/domain/quota/TenantQuotaAggregate.kt

class TenantQuotaAggregate private constructor(
    id: TenantQuotaId,
    tenantId: TenantId
) : AggregateRoot<TenantQuotaId, QuotaEvent>(id, tenantId) {

    private var limits: QuotaLimits = QuotaLimits.UNLIMITED

    val currentLimits: QuotaLimits get() = limits
    val hasLimits: Boolean get() = limits != QuotaLimits.UNLIMITED

    companion object {
        fun create(
            id: TenantQuotaId,
            tenantId: TenantId,
            limits: QuotaLimits,
            createdBy: UserId,
            clock: Clock
        ): TenantQuotaAggregate {
            val aggregate = TenantQuotaAggregate(id, tenantId)
            aggregate.recordEvent(
                QuotaLimitsUpdated(
                    aggregateId = id,
                    tenantId = tenantId,
                    limits = limits,
                    updatedBy = createdBy,
                    occurredAt = clock.instant()
                )
            )
            return aggregate
        }
    }

    fun updateLimits(newLimits: QuotaLimits, updatedBy: UserId, clock: Clock) {
        if (newLimits == limits) return // No-op

        recordEvent(
            QuotaLimitsUpdated(
                aggregateId = id,
                tenantId = tenantId,
                limits = newLimits,
                updatedBy = updatedBy,
                occurredAt = clock.instant()
            )
        )
    }

    fun clearLimits(clearedBy: UserId, clock: Clock) {
        if (!hasLimits) return // Already unlimited

        recordEvent(
            QuotaLimitsCleared(
                aggregateId = id,
                tenantId = tenantId,
                clearedBy = clearedBy,
                occurredAt = clock.instant()
            )
        )
    }

    override fun apply(event: QuotaEvent) {
        when (event) {
            is QuotaLimitsUpdated -> limits = event.limits
            is QuotaLimitsCleared -> limits = QuotaLimits.UNLIMITED
        }
    }
}
```

### 4.2 Database Schema

#### 4.2.1 Projects Table

```sql
-- V013__create_projects_table.sql

CREATE TABLE "PROJECTS" (
    "ID" UUID PRIMARY KEY,
    "TENANT_ID" UUID NOT NULL,
    "NAME" VARCHAR(100) NOT NULL,
    "DESCRIPTION" TEXT,
    "STATUS" VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    "CREATED_BY" UUID NOT NULL,
    "CREATED_AT" TIMESTAMP WITH TIME ZONE NOT NULL,
    "UPDATED_AT" TIMESTAMP WITH TIME ZONE NOT NULL,
    "VERSION" BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_project_name_tenant UNIQUE ("TENANT_ID", "NAME")
);

-- [jooq ignore start]
ALTER TABLE "PROJECTS" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "PROJECTS" FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON "PROJECTS"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE, DELETE ON "PROJECTS" TO eaf_app;
-- [jooq ignore stop]

CREATE INDEX idx_projects_tenant ON "PROJECTS"("TENANT_ID");
CREATE INDEX idx_projects_status ON "PROJECTS"("STATUS");
```

#### 4.2.2 Project Members Table

```sql
-- V014__create_project_members_table.sql

CREATE TABLE "PROJECT_MEMBERS" (
    "ID" UUID PRIMARY KEY,
    "PROJECT_ID" UUID NOT NULL REFERENCES "PROJECTS"("ID") ON DELETE CASCADE,
    "TENANT_ID" UUID NOT NULL,
    "USER_ID" UUID NOT NULL,
    "ROLE" VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    "ASSIGNED_AT" TIMESTAMP WITH TIME ZONE NOT NULL,
    "ASSIGNED_BY" UUID NOT NULL,

    CONSTRAINT uq_project_member UNIQUE ("PROJECT_ID", "USER_ID")
);

-- [jooq ignore start]
ALTER TABLE "PROJECT_MEMBERS" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "PROJECT_MEMBERS" FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON "PROJECT_MEMBERS"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE, DELETE ON "PROJECT_MEMBERS" TO eaf_app;
-- [jooq ignore stop]

CREATE INDEX idx_project_members_project ON "PROJECT_MEMBERS"("PROJECT_ID");
CREATE INDEX idx_project_members_user ON "PROJECT_MEMBERS"("USER_ID");
```

#### 4.2.3 Tenant Quotas Table

```sql
-- V015__create_tenant_quotas_table.sql

CREATE TABLE "TENANT_QUOTAS" (
    "ID" UUID PRIMARY KEY,
    "TENANT_ID" UUID NOT NULL UNIQUE,
    "MAX_VMS" INT,              -- NULL = unlimited
    "MAX_VCPUS" INT,
    "MAX_RAM_GB" INT,
    "MAX_STORAGE_GB" INT,
    "CREATED_AT" TIMESTAMP WITH TIME ZONE NOT NULL,
    "UPDATED_AT" TIMESTAMP WITH TIME ZONE NOT NULL,
    "VERSION" BIGINT NOT NULL DEFAULT 0
);

-- [jooq ignore start]
ALTER TABLE "TENANT_QUOTAS" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "TENANT_QUOTAS" FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON "TENANT_QUOTAS"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE, DELETE ON "TENANT_QUOTAS" TO eaf_app;
-- [jooq ignore stop]
```

#### 4.2.4 Quota Usage Projection Table

```sql
-- V016__create_quota_usage_projection_table.sql

-- Materialized view of current resource usage per tenant
-- Updated by event handlers when VMs are created/deleted
CREATE TABLE "QUOTA_USAGE" (
    "TENANT_ID" UUID PRIMARY KEY,
    "CURRENT_VMS" INT NOT NULL DEFAULT 0,
    "CURRENT_VCPUS" INT NOT NULL DEFAULT 0,
    "CURRENT_RAM_GB" INT NOT NULL DEFAULT 0,
    "CURRENT_STORAGE_GB" INT NOT NULL DEFAULT 0,
    "LAST_UPDATED_AT" TIMESTAMP WITH TIME ZONE NOT NULL
);

-- [jooq ignore start]
ALTER TABLE "QUOTA_USAGE" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "QUOTA_USAGE" FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON "QUOTA_USAGE"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE, DELETE ON "QUOTA_USAGE" TO eaf_app;
-- [jooq ignore stop]
```

### 4.3 API Extensions

#### 4.3.1 New REST Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/projects` | List user's accessible projects | User |
| GET | `/api/projects/{id}` | Get project details | User (member) |
| POST | `/api/admin/projects` | Create new project | Admin |
| PUT | `/api/admin/projects/{id}` | Update project | Admin |
| POST | `/api/admin/projects/{id}/archive` | Archive project | Admin |
| POST | `/api/admin/projects/{id}/unarchive` | Unarchive project | Admin |
| GET | `/api/projects/{id}/vms` | List VMs in project | User (member) |
| GET | `/api/admin/projects/{id}/members` | List project members | Admin |
| POST | `/api/admin/projects/{id}/members` | Assign user to project | Admin |
| DELETE | `/api/admin/projects/{id}/members/{userId}` | Remove user from project | Admin |
| GET | `/api/admin/quotas` | Get tenant quota configuration | Admin |
| PUT | `/api/admin/quotas` | Update tenant quota configuration | Admin |
| GET | `/api/quotas/usage` | Get current quota usage | User |
| GET | `/api/admin/dashboard/utilization` | Get resource utilization metrics | Admin |

#### 4.3.2 New DTOs

```kotlin
// Project DTOs
data class ProjectResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val status: String,
    val vmCount: Int,
    val myRole: String?,  // null if admin viewing all projects
    val createdAt: Instant
)

data class ProjectDetailResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val status: String,
    val vmCount: Int,
    val memberCount: Int,
    val myRole: String?,
    val createdBy: UserSummaryResponse,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class CreateProjectRequest(
    @field:NotBlank
    @field:Size(min = 3, max = 100)
    val name: String,

    @field:Size(max = 500)
    val description: String?,

    val initialMemberIds: List<UUID>?
)

data class UpdateProjectRequest(
    @field:Size(min = 3, max = 100)
    val name: String?,

    @field:Size(max = 500)
    val description: String?
)

data class ProjectMemberResponse(
    val userId: UUID,
    val email: String,
    val displayName: String,
    val role: String,
    val assignedAt: Instant
)

data class AssignMemberRequest(
    @field:NotNull
    val userId: UUID,

    @field:NotNull
    val role: String  // "MEMBER" or "PROJECT_ADMIN"
)

// Quota DTOs
data class QuotaConfigurationResponse(
    val maxVms: Int?,
    val maxVCpus: Int?,
    val maxRamGb: Int?,
    val maxStorageGb: Int?,
    val hasLimits: Boolean,
    val updatedAt: Instant?
)

data class UpdateQuotaRequest(
    val maxVms: Int?,
    val maxVCpus: Int?,
    val maxRamGb: Int?,
    val maxStorageGb: Int?
)

data class QuotaUsageResponse(
    val limits: QuotaLimitsResponse,
    val usage: QuotaCurrentUsageResponse,
    val percentages: QuotaPercentagesResponse
)

data class QuotaLimitsResponse(
    val maxVms: Int?,
    val maxVCpus: Int?,
    val maxRamGb: Int?,
    val maxStorageGb: Int?
)

data class QuotaCurrentUsageResponse(
    val currentVms: Int,
    val currentVCpus: Int,
    val currentRamGb: Int,
    val currentStorageGb: Int
)

data class QuotaPercentagesResponse(
    val vmsPercent: Int?,      // null if unlimited
    val vCpusPercent: Int?,
    val ramPercent: Int?,
    val storagePercent: Int?
)

// Utilization Dashboard DTOs
data class UtilizationDashboardResponse(
    val summary: UtilizationSummaryResponse,
    val byProject: List<ProjectUtilizationResponse>,
    val byUser: List<UserUtilizationResponse>,
    val trend: List<UtilizationTrendPointResponse>
)

data class UtilizationSummaryResponse(
    val totalVms: Int,
    val totalVCpus: Int,
    val totalRamGb: Int,
    val totalStorageGb: Int,
    val activeProjects: Int,
    val activeUsers: Int
)

data class ProjectUtilizationResponse(
    val projectId: UUID,
    val projectName: String,
    val vmCount: Int,
    val vCpus: Int,
    val ramGb: Int,
    val storageGb: Int
)

data class UserUtilizationResponse(
    val userId: UUID,
    val displayName: String,
    val vmCount: Int,
    val vCpus: Int,
    val ramGb: Int,
    val storageGb: Int
)

data class UtilizationTrendPointResponse(
    val date: LocalDate,
    val vmCount: Int,
    val vCpus: Int,
    val ramGb: Int,
    val storageGb: Int
)
```

### 4.4 Quota Enforcement in CreateVmRequestHandler

```kotlin
// Modification to existing CreateVmRequestHandler

class CreateVmRequestHandler(
    private val eventStore: EventStore,
    private val projectQueryService: ProjectQueryService,
    private val quotaQueryService: QuotaQueryService,  // NEW
    private val clock: Clock
) {
    suspend fun handle(command: CreateVmRequestCommand): Result<VmRequestId, CreateVmRequestError> {
        // 1. Validate project exists and user has access
        val project = projectQueryService.findById(command.projectId)
            ?: return Result.failure(ProjectNotFound(command.projectId))

        if (project.isArchived) {
            return Result.failure(ProjectArchived(command.projectId))
        }

        if (!project.hasMember(command.userId)) {
            return Result.failure(NotProjectMember(command.projectId, command.userId))
        }

        // 2. Check quota (NEW)
        val quotaCheck = quotaQueryService.checkQuota(
            tenantId = command.tenantId,
            requestedSize = command.size
        )

        if (quotaCheck is QuotaCheckResult.Exceeded) {
            return Result.failure(QuotaExceeded(
                violation = quotaCheck.violation,
                currentUsage = quotaCheck.currentUsage,
                limits = quotaCheck.limits
            ))
        }

        // 3. Create aggregate and persist
        val aggregate = VmRequestAggregate.create(
            id = VmRequestId(UUID.randomUUID()),
            tenantId = command.tenantId,
            projectId = command.projectId,
            requesterId = command.userId,
            vmName = command.vmName,
            size = command.size,
            justification = command.justification,
            clock = clock
        )

        eventStore.save(aggregate)

        // 4. Increment quota usage projection
        quotaQueryService.incrementUsage(command.tenantId, command.size)

        return Result.success(aggregate.id)
    }
}

sealed interface CreateVmRequestError {
    data class ProjectNotFound(val projectId: ProjectId) : CreateVmRequestError
    data class ProjectArchived(val projectId: ProjectId) : CreateVmRequestError
    data class NotProjectMember(val projectId: ProjectId, val userId: UserId) : CreateVmRequestError
    data class QuotaExceeded(
        val violation: QuotaViolation,
        val currentUsage: QuotaUsage,
        val limits: QuotaLimits
    ) : CreateVmRequestError
}
```

---

## 5. Story Breakdown

### 5.1 Story Dependency Graph

```text
                                    ┌──────────────────────────────┐
                                    │ Story 4.1: Project Aggregate │
                                    │ (Foundation for all stories) │
                                    └───────────┬──────────────────┘
                                                │
                          ┌─────────────────────┼─────────────────────┐
                          │                     │                     │
              ┌───────────▼───────────┐ ┌───────▼───────┐ ┌───────────▼───────────┐
              │ Story 4.2: Project    │ │ Story 4.3:    │ │ Story 4.4: Edit      │
              │ List View             │ │ Create Project│ │ Project (Admin)       │
              │ (User view)           │ │ (Admin)       │ │                       │
              └───────────┬───────────┘ └───────┬───────┘ └───────────┬───────────┘
                          │                     │                     │
                          └─────────────────────┼─────────────────────┘
                                                │
                          ┌─────────────────────┼─────────────────────┐
                          │                                           │
              ┌───────────▼───────────┐               ┌───────────────▼───────────┐
              │ Story 4.5: Archive    │               │ Story 4.6: Project VM    │
              │ Project               │               │ List                      │
              └───────────────────────┘               └───────────────────────────┘

                                    ┌──────────────────────────────┐
                                    │ Story 4.7: Tenant Quota      │
                                    │ Configuration                 │
                                    └───────────┬──────────────────┘
                                                │
                                    ┌───────────▼──────────────────┐
                                    │ Story 4.8: Quota Visibility  │
                                    │ & Enforcement                 │
                                    └───────────┬──────────────────┘
                                                │
                                    ┌───────────▼──────────────────┐
                                    │ Story 4.9: Resource          │
                                    │ Utilization Dashboard        │
                                    └──────────────────────────────┘
```

### 5.2 Story Summary

| ID | Title | Prerequisites | FRs | Risk |
|----|-------|---------------|-----|------|
| 4.1 | Project Aggregate | 1.4 | Foundation | MEDIUM |
| 4.2 | Project List View | 4.1, 1.8 | FR10 | LOW |
| 4.3 | Create Project (Admin) | 4.1, 2.1 | FR11 | LOW |
| 4.4 | Edit Project (Admin) | 4.3 | FR12 | LOW |
| 4.5 | Archive Project (Admin) | 4.4 | FR13 | LOW |
| 4.6 | Project VM List | 4.2, 3.7 | FR14 | LOW |
| 4.7 | Tenant Quota Configuration | 2.1, 1.6 | FR82 | MEDIUM |
| 4.8 | Quota Visibility & Enforcement | 4.7, 2.6 | FR83, FR84 | **HIGH** |
| 4.9 | Resource Utilization Dashboard | 4.8, 3.7 | FR87 | MEDIUM |

### 5.3 Key Acceptance Criteria (Critical Stories)

#### Story 4.1: Project Aggregate (MEDIUM)

| AC | Given | When | Then |
|----|-------|------|------|
| AC-4.1.1 | I dispatch CreateProjectCommand | Command executes | ProjectCreated event persisted with projectId, tenantId, name, description, createdBy |
| AC-4.1.2 | Project created | - | Creator automatically assigned as PROJECT_ADMIN |
| AC-4.1.3 | Project name exists in tenant | Create with same name | ValidationFailed: "Project name already taken" |
| AC-4.1.4 | Project is archived | Update command dispatched | Error: "Cannot update archived project" |
| AC-4.1.5 | User assignment | AssignUserCommand dispatched | UserAssignedToProject event with userId, role |

#### Story 4.7: Tenant Quota Configuration (MEDIUM)

| AC | Given | When | Then |
|----|-------|------|------|
| AC-4.7.1 | I am tenant admin | Navigate to Settings → Quotas | See configurable limits (maxVms, maxVCpus, maxRamGb, maxStorageGb) |
| AC-4.7.2 | I set quota limits | Save configuration | QuotaLimitsUpdated event persisted |
| AC-4.7.3 | No quota configured | View quotas | Default: unlimited (null values) |
| AC-4.7.4 | Quota configured | View dashboard | Current usage displayed next to each limit |

#### Story 4.8: Quota Visibility & Enforcement (HIGH)

| AC | Given | When | Then |
|----|-------|------|------|
| AC-4.8.1 | I am on VM request form | Select size | See "Available: X of Y VMs", progress bars for all resources |
| AC-4.8.2 | Selected size exceeds quota | View size card | Size disabled with "Quota exceeded" message |
| AC-4.8.3 | Submit request exceeding quota | Command handler validates | QuotaExceeded error returned, no event persisted |
| AC-4.8.4 | Quota >90% used | View dashboard | Warning banner: "Quota almost exhausted (X%)" |
| AC-4.8.5 | Concurrent requests | Two users submit simultaneously | Only one succeeds if quota insufficient (optimistic locking) |

---

## 6. Non-Functional Requirements

### 6.1 Performance

| Metric | Target | Measurement |
|--------|--------|-------------|
| Project list load | < 200ms | API response time |
| Quota check | < 50ms | In-handler timing |
| Dashboard metrics | < 500ms | Aggregation query |
| Concurrent quota checks | 100 req/s | k6 load test |

### 6.2 Reliability

| Aspect | Implementation |
|--------|----------------|
| Quota consistency | Optimistic locking on quota usage projection |
| Project uniqueness | Database UNIQUE constraint + domain validation |
| Member access | Cached projection with event-driven updates |
| Usage tracking | Event handlers maintain accurate counts |

### 6.3 Security

| Requirement | Implementation |
|-------------|----------------|
| Project access | Users only see projects they're members of |
| Admin actions | Role check at controller level |
| Quota manipulation | Admin-only endpoints, audit logged |
| RLS isolation | All tables tenant-scoped |

### 6.4 Testability

| Test Type | Coverage | Notes |
|-----------|----------|-------|
| Unit Tests | ≥80% | Aggregate logic, quota calculations |
| Integration Tests | jOOQ + PostgreSQL | Project/quota persistence |
| E2E Tests | Playwright | Full user flows |

---

## 7. Dependencies

### 7.1 Epic 1/2/3 Dependencies

| Dependency | Story | Status |
|------------|-------|--------|
| Event Store | 1.3 | DONE |
| Aggregate Base | 1.4 | DONE |
| PostgreSQL RLS | 1.6 | DONE |
| Keycloak Auth | 1.7 | DONE |
| jOOQ Projections | 1.8 | DONE |
| VM Request Aggregate | 2.6 | DONE |
| VM Details | 3.7 | DONE |

### 7.2 New Libraries

| Library | Version | Module | Purpose |
|---------|---------|--------|---------|
| None new | - | - | All dependencies already present |

### 7.3 External Dependencies

| Dependency | Story | Status |
|------------|-------|--------|
| None | - | No external integrations in Epic 4 |

---

## 8. Lessons from Epic 3

### 8.1 Patterns to Apply

| Pattern | Source | Application in Epic 4 |
|---------|--------|----------------------|
| Result<T,E> error handling | All handlers | Quota enforcement errors |
| Event sourcing with reconstitute | Aggregates | Project and TenantQuota aggregates |
| Projection caching | Timeline | Quota usage projection |
| Sealed class hierarchies | VsphereError | QuotaViolation, CreateVmRequestError |
| Fire-and-forget pattern | Email notifications | Not applicable (all sync in Epic 4) |

### 8.2 Technical Debt to Avoid

| Issue | Prevention |
|-------|------------|
| Race conditions in quota | Use optimistic locking, not just read-check-write |
| Stale usage projection | Event handlers must update projection synchronously |
| Project name as UUID | Already fixing - proper ProjectQueryService |
| Missing event deserializer | Follow 5-step checklist from project-context.md |

### 8.3 Action Items from Epic 3 Retro

| Action | Owner | Target Story |
|--------|-------|--------------|
| Create ProjectQueryService | Dev | 4.1 |
| Replace project UUID with name | Dev | 4.6 |
| Add quota to VM request form | Dev | 4.8 |

---

## 9. Definition of Done

### 9.1 Story Level

- [ ] All acceptance criteria implemented
- [ ] Unit tests ≥80% coverage
- [ ] Mutation score ≥70%
- [ ] Integration tests with PostgreSQL
- [ ] Code review approved
- [ ] CI pipeline passes
- [ ] API documentation updated (Swagger/OpenAPI)

### 9.2 Epic Level

- [ ] All 9 stories completed
- [ ] Projects aggregate fully functional (CRUD + archive + members)
- [ ] Tenant quota configuration working
- [ ] Quota enforcement blocking oversized requests
- [ ] Resource utilization dashboard displaying accurate metrics
- [ ] E2E tests covering:
  - [ ] Create project → assign member → create VM request
  - [ ] Configure quota → exceed quota → see error
  - [ ] Archive project → attempt VM request → see error
- [ ] Performance targets met (k6)
- [ ] Security review passed

### 9.3 Quality Gates

- [ ] Optimistic locking tested for concurrent quota consumption
- [ ] Project uniqueness constraint tested
- [ ] Archived project rejection tested
- [ ] Admin-only endpoints secured
- [ ] RLS tenant isolation verified

---

## 10. References

- [Epic 4 Definition](../epics.md#epic-4-projects--quota)
- [Architecture](../architecture.md)
- [PRD](../prd.md)
- [Epic 3 Tech Spec](tech-spec-epic-3.md)
- [Epic 3 Retrospective](epic-3-retro-2025-12-12.md)
- [Project Context](../project-context.md)

---

*Generated by Winston (Architect Agent) via BMAD Method tech-spec workflow*
*Model: claude-opus-4-5-20251101*
