package de.acci.dcm.domain.project.events

import de.acci.dcm.domain.project.ProjectId
import de.acci.dcm.domain.project.ProjectRole
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

/**
 * Domain event indicating a user has been assigned to a project.
 *
 * Emitted when:
 * - A project is created (creator auto-assigned as PROJECT_ADMIN)
 * - A project admin assigns a new member
 */
public data class UserAssignedToProject(
    /** The project this user is being assigned to */
    val aggregateId: ProjectId,
    /** The user being assigned */
    val userId: UserId,
    /** The role assigned to the user */
    val role: ProjectRole,
    /** Who performed the assignment (admin or creator) */
    val assignedBy: UserId,
    /** Event metadata (tenant, user, correlation, timestamp) */
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = ProjectCreated.AGGREGATE_TYPE
}
