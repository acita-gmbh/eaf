package de.acci.dcm.domain.project.events

import de.acci.dcm.domain.project.ProjectId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

/**
 * Domain event indicating a user has been removed from a project.
 *
 * The project creator cannot be removed (enforced by aggregate invariant).
 */
public data class UserRemovedFromProject(
    /** The project this user is being removed from */
    val aggregateId: ProjectId,
    /** The user being removed */
    val userId: UserId,
    /** Who performed the removal */
    val removedBy: UserId,
    /** Event metadata (tenant, user, correlation, timestamp) */
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = ProjectCreated.AGGREGATE_TYPE
}
