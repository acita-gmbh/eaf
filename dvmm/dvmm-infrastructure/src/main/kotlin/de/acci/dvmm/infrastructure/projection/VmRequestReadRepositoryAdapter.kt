package de.acci.dvmm.infrastructure.projection

import de.acci.dvmm.application.vmrequest.ProjectSummary
import de.acci.dvmm.application.vmrequest.VmRequestReadRepository
import de.acci.dvmm.application.vmrequest.VmRequestSummary
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmRequestStatus
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.projection.PageRequest
import de.acci.eaf.eventsourcing.projection.PagedResponse

/**
 * Infrastructure adapter that implements the application layer's
 * VmRequestReadRepository interface using the jOOQ-based projection repository.
 *
 * This adapter translates between:
 * - Application layer types (VmRequestSummary, domain value objects)
 * - Infrastructure layer types (jOOQ-generated POJOs)
 */
public class VmRequestReadRepositoryAdapter(
    private val projectionRepository: VmRequestProjectionRepository
) : VmRequestReadRepository {

    override suspend fun findById(id: VmRequestId): VmRequestSummary? {
        val projection = projectionRepository.findById(id.value) ?: return null
        
        return VmRequestSummary(
            id = VmRequestId(projection.id),
            tenantId = TenantId(projection.tenantId),
            requesterId = UserId(projection.requesterId),
            requesterName = projection.requesterName,
            projectId = ProjectId(projection.projectId),
            projectName = projection.projectName,
            vmName = projection.vmName,
            size = VmSize.valueOf(projection.size),
            justification = projection.justification,
            status = VmRequestStatus.valueOf(projection.status),
            createdAt = projection.createdAt.toInstant(),
            updatedAt = projection.updatedAt.toInstant()
        )
    }

    override suspend fun findByRequesterId(
        requesterId: UserId,
        pageRequest: PageRequest
    ): PagedResponse<VmRequestSummary> {
        val pagedProjections = projectionRepository.findByRequesterId(
            requesterId = requesterId.value,
            pageRequest = pageRequest
        )

        return mapToSummaryResponse(pagedProjections)
    }

    override suspend fun findPendingByTenantId(
        tenantId: TenantId,
        projectId: ProjectId?,
        pageRequest: PageRequest
    ): PagedResponse<VmRequestSummary> {
        // Note: tenantId is not passed to repository - RLS handles tenant filtering
        val pagedProjections = projectionRepository.findPendingByTenantId(
            projectId = projectId?.value,
            pageRequest = pageRequest
        )

        return mapToSummaryResponse(pagedProjections)
    }

    override suspend fun findDistinctProjects(
        tenantId: TenantId
    ): List<ProjectSummary> {
        // Note: tenantId is not passed to repository - RLS handles tenant filtering
        val projects = projectionRepository.findDistinctProjects()

        return projects.map { projectInfo ->
            ProjectSummary(
                id = ProjectId(projectInfo.projectId),
                name = projectInfo.projectName
            )
        }
    }

    /**
     * Maps jOOQ projection POJOs to application layer VmRequestSummary.
     */
    private fun mapToSummaryResponse(
        pagedProjections: PagedResponse<de.acci.dvmm.infrastructure.jooq.`public`.tables.pojos.VmRequestsProjection>
    ): PagedResponse<VmRequestSummary> {
        return PagedResponse(
            items = pagedProjections.items.map { projection ->
                VmRequestSummary(
                    id = VmRequestId(projection.id),
                    tenantId = TenantId(projection.tenantId),
                    requesterId = UserId(projection.requesterId),
                    requesterName = projection.requesterName,
                    projectId = ProjectId(projection.projectId),
                    projectName = projection.projectName,
                    vmName = projection.vmName,
                    size = VmSize.valueOf(projection.size),
                    justification = projection.justification,
                    status = VmRequestStatus.valueOf(projection.status),
                    createdAt = projection.createdAt.toInstant(),
                    updatedAt = projection.updatedAt.toInstant()
                )
            },
            page = pagedProjections.page,
            size = pagedProjections.size,
            totalElements = pagedProjections.totalElements
        )
    }
}