package de.acci.dvmm.infrastructure.projection

import de.acci.dvmm.application.vmrequest.VmRequestDetailProjection
import de.acci.dvmm.application.vmrequest.VmRequestDetailRepository
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.infrastructure.jooq.`public`.tables.VmRequestsProjection.Companion.VM_REQUESTS_PROJECTION
import de.acci.eaf.core.types.UserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext

/**
 * Infrastructure adapter implementing VmRequestDetailRepository.
 *
 * Provides read access to detailed VM request projections.
 * RLS ensures tenant isolation automatically.
 */
public class VmRequestDetailRepositoryAdapter(
    private val dsl: DSLContext
) : VmRequestDetailRepository {

    override suspend fun findById(requestId: VmRequestId): VmRequestDetailProjection? =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(VM_REQUESTS_PROJECTION)
                .where(VM_REQUESTS_PROJECTION.ID.eq(requestId.value))
                .fetchOne()
                ?.let { record ->
                    VmRequestDetailProjection(
                        id = VmRequestId(record.get(VM_REQUESTS_PROJECTION.ID)!!),
                        requesterId = UserId(record.get(VM_REQUESTS_PROJECTION.REQUESTER_ID)!!),
                        vmName = record.get(VM_REQUESTS_PROJECTION.VM_NAME)!!,
                        size = record.get(VM_REQUESTS_PROJECTION.SIZE)!!,
                        cpuCores = record.get(VM_REQUESTS_PROJECTION.CPU_CORES)!!,
                        memoryGb = record.get(VM_REQUESTS_PROJECTION.MEMORY_GB)!!,
                        diskGb = record.get(VM_REQUESTS_PROJECTION.DISK_GB)!!,
                        justification = record.get(VM_REQUESTS_PROJECTION.JUSTIFICATION)!!,
                        status = record.get(VM_REQUESTS_PROJECTION.STATUS)!!,
                        projectName = record.get(VM_REQUESTS_PROJECTION.PROJECT_NAME)!!,
                        requesterName = record.get(VM_REQUESTS_PROJECTION.REQUESTER_NAME)!!,
                        createdAt = record.get(VM_REQUESTS_PROJECTION.CREATED_AT)!!.toInstant()
                    )
                }
        }
}
