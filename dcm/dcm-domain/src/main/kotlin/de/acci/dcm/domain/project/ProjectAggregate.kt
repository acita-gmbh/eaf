package de.acci.dcm.domain.project

import de.acci.dcm.domain.project.events.ProjectArchived
import de.acci.dcm.domain.project.events.ProjectCreated
import de.acci.dcm.domain.project.events.ProjectUnarchived
import de.acci.dcm.domain.project.events.ProjectUpdated
import de.acci.dcm.domain.project.events.UserAssignedToProject
import de.acci.dcm.domain.project.events.UserRemovedFromProject
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata
import de.acci.eaf.eventsourcing.aggregate.AggregateRoot

/**
 * Aggregate root for Projects.
 *
 * Projects provide logical grouping of VMs and control access through membership.
 * The project creator is automatically assigned as PROJECT_ADMIN on creation.
 *
 * ## State Machine
 * ```
 * ACTIVE <─────> ARCHIVED
 * ```
 *
 * **Invariants:**
 * - Only ACTIVE projects can be updated
 * - Only ACTIVE projects can have members assigned/removed
 * - Project creator cannot be removed
 *
 * ## Usage
 *
 * Create a new project:
 * ```kotlin
 * val aggregate = ProjectAggregate.create(
 *     name = ProjectName.of("Alpha Project"),
 *     description = "Development team resources",
 *     metadata = metadata
 * )
 * eventStore.append(aggregate.id.value, aggregate.uncommittedEvents, aggregate.version)
 * ```
 *
 * Reconstitute from events:
 * ```kotlin
 * val events = eventStore.loadEvents(projectId)
 * val aggregate = ProjectAggregate.reconstitute(ProjectId(projectId), events)
 * ```
 */
public class ProjectAggregate private constructor(
    override val id: ProjectId
) : AggregateRoot<ProjectId>() {

    /** Project name */
    public var name: ProjectName = ProjectName.of("placeholder")
        private set

    /** Optional project description */
    public var description: String? = null
        private set

    /** Current project status */
    public var status: ProjectStatus = ProjectStatus.ACTIVE
        private set

    /** User who created the project (cannot be removed) */
    public var createdBy: UserId = UserId.fromString("00000000-0000-0000-0000-000000000000")
        private set

    /** Project members with their roles */
    private val _members: MutableMap<UserId, ProjectMember> = mutableMapOf()

    override fun handleEvent(event: DomainEvent) {
        when (event) {
            is ProjectCreated -> apply(event)
            is ProjectUpdated -> apply(event)
            is ProjectArchived -> apply(event)
            is ProjectUnarchived -> apply(event)
            is UserAssignedToProject -> apply(event)
            is UserRemovedFromProject -> apply(event)
            else -> throw IllegalArgumentException(
                "Unknown event type for ProjectAggregate: ${event::class.simpleName}"
            )
        }
    }

    private fun apply(event: ProjectCreated) {
        name = event.name
        description = event.description
        status = ProjectStatus.ACTIVE
        createdBy = event.metadata.userId
    }

    private fun apply(event: ProjectUpdated) {
        name = event.name
        description = event.description
    }

    private fun apply(@Suppress("UNUSED_PARAMETER") event: ProjectArchived) {
        status = ProjectStatus.ARCHIVED
    }

    private fun apply(@Suppress("UNUSED_PARAMETER") event: ProjectUnarchived) {
        status = ProjectStatus.ACTIVE
    }

    private fun apply(event: UserAssignedToProject) {
        _members[event.userId] = ProjectMember(
            userId = event.userId,
            role = event.role,
            assignedBy = event.assignedBy,
            assignedAt = event.metadata.timestamp
        )
    }

    private fun apply(event: UserRemovedFromProject) {
        _members.remove(event.userId)
    }

    /**
     * Updates the project name and description.
     *
     * @param name New project name
     * @param description New description (can be null)
     * @param metadata Event metadata
     * @throws IllegalStateException if project is archived
     */
    public fun update(name: ProjectName, description: String?, metadata: EventMetadata) {
        requireActive("update")

        val event = ProjectUpdated(
            aggregateId = id,
            name = name,
            description = description,
            metadata = metadata
        )
        applyEvent(event)
    }

    /**
     * Archives the project (soft delete).
     *
     * Archived projects cannot be modified.
     * This operation is idempotent - archiving an already archived project is a no-op.
     *
     * @param metadata Event metadata
     */
    public fun archive(metadata: EventMetadata) {
        // Idempotent: already archived, no-op
        if (status == ProjectStatus.ARCHIVED) {
            return
        }

        val event = ProjectArchived(
            aggregateId = id,
            metadata = metadata
        )
        applyEvent(event)
    }

    /**
     * Restores an archived project to active status.
     *
     * This operation is idempotent - unarchiving an already active project is a no-op.
     *
     * @param metadata Event metadata
     */
    public fun unarchive(metadata: EventMetadata) {
        // Idempotent: already active, no-op
        if (status == ProjectStatus.ACTIVE) {
            return
        }

        val event = ProjectUnarchived(
            aggregateId = id,
            metadata = metadata
        )
        applyEvent(event)
    }

    /**
     * Assigns a user to this project with the specified role.
     *
     * If the user is already a member with the same role, this is a no-op (idempotent).
     * If the user exists with a different role, the role is updated.
     *
     * @param userId User to assign
     * @param role Role to assign (MEMBER or PROJECT_ADMIN)
     * @param metadata Event metadata (userId = the admin performing the action)
     * @throws IllegalStateException if project is archived
     */
    public fun assignUser(userId: UserId, role: ProjectRole, metadata: EventMetadata) {
        requireActive("assign user to")

        // Idempotent: same user with same role
        val existingMember = _members[userId]
        if (existingMember != null && existingMember.role == role) {
            return
        }

        val event = UserAssignedToProject(
            aggregateId = id,
            userId = userId,
            role = role,
            assignedBy = metadata.userId,
            metadata = metadata
        )
        applyEvent(event)
    }

    /**
     * Removes a user from this project.
     *
     * The project creator cannot be removed (invariant).
     * Removing a non-member is a no-op (idempotent).
     *
     * @param userId User to remove
     * @param metadata Event metadata (userId = the admin performing the action)
     * @throws IllegalStateException if project is archived
     * @throws IllegalArgumentException if attempting to remove the project creator
     */
    public fun removeUser(userId: UserId, metadata: EventMetadata) {
        requireActive("remove user from")

        // Invariant: cannot remove creator
        if (userId == createdBy) {
            throw IllegalArgumentException("Cannot remove the project creator")
        }

        // Idempotent: user is not a member
        if (!_members.containsKey(userId)) {
            return
        }

        val event = UserRemovedFromProject(
            aggregateId = id,
            userId = userId,
            removedBy = metadata.userId,
            metadata = metadata
        )
        applyEvent(event)
    }

    /**
     * Returns true if the user is a member of this project.
     */
    public fun hasMember(userId: UserId): Boolean = _members.containsKey(userId)

    /**
     * Returns true if the user is a PROJECT_ADMIN in this project.
     */
    public fun isAdmin(userId: UserId): Boolean =
        _members[userId]?.role == ProjectRole.PROJECT_ADMIN

    /**
     * Returns the member details for a user, or null if not a member.
     */
    public fun getMember(userId: UserId): ProjectMember? = _members[userId]

    /**
     * Returns all members of this project.
     */
    public fun getMembers(): List<ProjectMember> = _members.values.toList()

    private fun requireActive(operation: String) {
        if (status != ProjectStatus.ACTIVE) {
            throw IllegalStateException("Cannot $operation archived project")
        }
    }

    public companion object {
        /**
         * Creates a new project.
         *
         * The creator is automatically assigned as PROJECT_ADMIN (AC-4.1.2).
         *
         * @param name Validated project name
         * @param description Optional project description
         * @param metadata Event metadata (userId = creator)
         * @return New ProjectAggregate with ProjectCreated and UserAssignedToProject events
         */
        public fun create(
            name: ProjectName,
            description: String?,
            metadata: EventMetadata
        ): ProjectAggregate {
            val id = ProjectId.generate()
            val aggregate = ProjectAggregate(id)

            // First event: ProjectCreated
            val createdEvent = ProjectCreated(
                aggregateId = id,
                name = name,
                description = description,
                metadata = metadata
            )
            aggregate.applyEvent(createdEvent)

            // Second event: Auto-assign creator as PROJECT_ADMIN (AC-4.1.2)
            val assignEvent = UserAssignedToProject(
                aggregateId = id,
                userId = metadata.userId,
                role = ProjectRole.PROJECT_ADMIN,
                assignedBy = metadata.userId,
                metadata = metadata
            )
            aggregate.applyEvent(assignEvent)

            return aggregate
        }

        /**
         * Reconstitutes aggregate state from a sequence of events.
         *
         * @param id The aggregate identifier
         * @param events Historical events to replay
         * @return Reconstituted aggregate
         */
        public fun reconstitute(
            id: ProjectId,
            events: List<DomainEvent>
        ): ProjectAggregate {
            val aggregate = ProjectAggregate(id)
            events.forEach { event ->
                aggregate.applyEvent(event, isReplay = true)
            }
            return aggregate
        }
    }
}
