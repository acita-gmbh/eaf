package de.acci.dcm.domain.project.events

import de.acci.dcm.domain.project.ProjectId
import de.acci.dcm.domain.project.ProjectName
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

/**
 * Domain event indicating a new project has been created.
 *
 * Emitted when an admin creates a new project.
 * The project enters ACTIVE status and the creator is auto-assigned as PROJECT_ADMIN.
 */
public data class ProjectCreated(
    /** Unique identifier for this project */
    val aggregateId: ProjectId,
    /** Validated project name */
    val name: ProjectName,
    /** Optional description of the project */
    val description: String?,
    /** Event metadata (tenant, user, correlation, timestamp) */
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = AGGREGATE_TYPE

    public companion object {
        public const val AGGREGATE_TYPE: String = "Project"
    }
}
