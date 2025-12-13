package de.acci.dcm.domain.project.events

import de.acci.dcm.domain.project.ProjectId
import de.acci.dcm.domain.project.ProjectName
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

/**
 * Domain event indicating a project's details have been updated.
 *
 * Emitted when a project admin updates the project name or description.
 * Only ACTIVE projects can be updated.
 */
public data class ProjectUpdated(
    /** Unique identifier for this project */
    val aggregateId: ProjectId,
    /** Updated project name */
    val name: ProjectName,
    /** Updated description */
    val description: String?,
    /** Event metadata (tenant, user, correlation, timestamp) */
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = ProjectCreated.AGGREGATE_TYPE
}
