package de.acci.dcm.application.project

import de.acci.dcm.domain.project.ProjectId
import de.acci.dcm.domain.project.ProjectName
import de.acci.eaf.core.types.TenantId

/**
 * Query service for Project read model operations.
 *
 * This interface is implemented in the infrastructure layer using jOOQ
 * to query the PROJECTS projection table.
 *
 * Used by command handlers for cross-aggregate validations like
 * uniqueness checks that cannot be performed within a single aggregate.
 */
public interface ProjectQueryService {

    /**
     * Find a project by its case-insensitive name within a tenant.
     *
     * Used by [CreateProjectHandler] to enforce unique project names
     * before creating a new project aggregate.
     *
     * @param tenantId Tenant to search in
     * @param name Project name to search for (case-insensitive)
     * @return ProjectId if found, null otherwise
     */
    public suspend fun findByName(tenantId: TenantId, name: ProjectName): ProjectId?

    /**
     * Check if a project exists by ID.
     *
     * @param tenantId Tenant to search in
     * @param projectId Project ID to check
     * @return true if project exists, false otherwise
     */
    public suspend fun exists(tenantId: TenantId, projectId: ProjectId): Boolean
}

/**
 * Stub implementation that allows all names.
 * Used in tests or when projection is not yet available.
 */
public object NoOpProjectQueryService : ProjectQueryService {
    override suspend fun findByName(tenantId: TenantId, name: ProjectName): ProjectId? = null
    override suspend fun exists(tenantId: TenantId, projectId: ProjectId): Boolean = true
}
