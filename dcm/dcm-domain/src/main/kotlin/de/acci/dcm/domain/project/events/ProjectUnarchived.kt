package de.acci.dcm.domain.project.events

import de.acci.dcm.domain.project.ProjectId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

/**
 * Domain event indicating a project has been unarchived.
 *
 * Restores the project to ACTIVE status, allowing modifications and new VM requests.
 */
public data class ProjectUnarchived(
    /** Unique identifier for this project */
    val aggregateId: ProjectId,
    /** Event metadata (tenant, user, correlation, timestamp) */
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = ProjectCreated.AGGREGATE_TYPE
}
