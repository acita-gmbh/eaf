package de.acci.dcm.infrastructure.project

import de.acci.dcm.application.project.ProjectQueryService
import de.acci.dcm.domain.project.ProjectId
import de.acci.dcm.domain.project.ProjectName
import de.acci.eaf.core.types.TenantId

/**
 * jOOQ implementation of [ProjectQueryService].
 *
 * Provides query operations for the Project read model using
 * the underlying projection repository.
 *
 * RLS NOTE: Tenant filtering is handled automatically by PostgreSQL
 * Row-Level Security at the database level.
 */
public class JooqProjectQueryService(
    private val projectRepository: ProjectProjectionRepository
) : ProjectQueryService {

    /**
     * Finds a project by its case-insensitive name within a tenant.
     *
     * Used by command handlers for uniqueness validation before
     * creating or updating projects.
     *
     * @param tenantId Tenant to search in (RLS enforced)
     * @param name Project name to search for
     * @return ProjectId if found, null otherwise
     */
    override suspend fun findByName(tenantId: TenantId, name: ProjectName): ProjectId? {
        // Note: TenantId is enforced by RLS at database level
        val uuid = projectRepository.findIdByName(name.value)
        return uuid?.let { ProjectId.fromString(it.toString()) }
    }

    /**
     * Checks if a project exists by ID.
     *
     * @param tenantId Tenant to search in (RLS enforced)
     * @param projectId Project ID to check
     * @return true if project exists, false otherwise
     */
    override suspend fun exists(tenantId: TenantId, projectId: ProjectId): Boolean {
        // Note: TenantId is enforced by RLS at database level
        return projectRepository.findById(projectId.value) != null
    }
}
