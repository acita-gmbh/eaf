package de.acci.dcm.domain.project.events

import de.acci.dcm.domain.project.ProjectId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

/**
 * Domain event indicating a project has been archived.
 *
 * Archived projects cannot be modified (no member changes, no updates).
 * VMs in archived projects may remain running but new requests are blocked.
 */
public data class ProjectArchived(
    /** Unique identifier for this project */
    val aggregateId: ProjectId,
    /** Event metadata (tenant, user, correlation, timestamp) */
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = ProjectCreated.AGGREGATE_TYPE
}
