package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmRequestStatus
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import java.time.Instant

/**
 * Summary view of a VM request for display in lists.
 *
 * This is an application-layer DTO used for queries, not tied to
 * any specific persistence implementation (jOOQ, JPA, etc.).
 *
 * Resource specifications (cpuCores, memoryGb, diskGb) are derived
 * from the [size] property to maintain a single source of truth.
 */
public data class VmRequestSummary(
    val id: VmRequestId,
    val tenantId: TenantId,
    val requesterId: UserId,
    val requesterName: String,
    val projectId: ProjectId,
    val projectName: String,
    val vmName: String,
    val size: VmSize,
    val justification: String,
    val status: VmRequestStatus,
    val createdAt: Instant,
    val updatedAt: Instant
)
