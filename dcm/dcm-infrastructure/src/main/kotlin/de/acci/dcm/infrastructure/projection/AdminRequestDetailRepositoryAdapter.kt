package de.acci.dcm.infrastructure.projection

import de.acci.dcm.application.vmrequest.AdminRequestDetailProjection
import de.acci.dcm.application.vmrequest.AdminRequestDetailRepository
import de.acci.dcm.application.vmrequest.VmRequestHistorySummary
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.dcm.infrastructure.jooq.`public`.tables.VmRequestsProjection.Companion.VM_REQUESTS_PROJECTION
import de.acci.eaf.core.types.UserId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext

/**
 * Infrastructure adapter implementing AdminRequestDetailRepository.
 *
 * Story 2.10: Request Detail View (Admin)
 *
 * Provides read access to detailed VM request projections for admin view.
 * Includes requester information (name, email, role) and requester history.
 *
 * RLS ensures tenant isolation automatically - admins can only see
 * requests within their own tenant.
 *
 * @param dsl The jOOQ DSLContext for database operations
 * @param ioDispatcher Dispatcher for blocking I/O operations (injectable for testing)
 */
public class AdminRequestDetailRepositoryAdapter(
    private val dsl: DSLContext,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AdminRequestDetailRepository {

    /**
     * Finds a VM request by its ID with full admin details.
     *
     * AC 2: Requester Information displayed (Name, Email, Role)
     * AC 3: Request details displayed
     *
     * @param requestId The ID of the VM request
     * @return The projection with admin-specific fields or null if not found
     */
    override suspend fun findById(requestId: VmRequestId): AdminRequestDetailProjection? =
        withContext(ioDispatcher) {
            dsl.selectFrom(VM_REQUESTS_PROJECTION)
                .where(VM_REQUESTS_PROJECTION.ID.eq(requestId.value))
                .fetchOne()
                ?.let { record ->
                    AdminRequestDetailProjection(
                        id = VmRequestId(record.get(VM_REQUESTS_PROJECTION.ID)!!),
                        vmName = record.get(VM_REQUESTS_PROJECTION.VM_NAME)!!,
                        size = record.get(VM_REQUESTS_PROJECTION.SIZE)!!,
                        cpuCores = record.get(VM_REQUESTS_PROJECTION.CPU_CORES)!!,
                        memoryGb = record.get(VM_REQUESTS_PROJECTION.MEMORY_GB)!!,
                        diskGb = record.get(VM_REQUESTS_PROJECTION.DISK_GB)!!,
                        justification = record.get(VM_REQUESTS_PROJECTION.JUSTIFICATION)!!,
                        status = record.get(VM_REQUESTS_PROJECTION.STATUS)!!,
                        projectName = record.get(VM_REQUESTS_PROJECTION.PROJECT_NAME)!!,
                        requesterId = UserId(record.get(VM_REQUESTS_PROJECTION.REQUESTER_ID)!!),
                        requesterName = record.get(VM_REQUESTS_PROJECTION.REQUESTER_NAME)!!,
                        // Provide sensible defaults for nullable fields (backward compatibility)
                        requesterEmail = record.get(VM_REQUESTS_PROJECTION.REQUESTER_EMAIL)
                            ?: "Not available",
                        requesterRole = record.get(VM_REQUESTS_PROJECTION.REQUESTER_ROLE)
                            ?: "User",
                        createdAt = record.get(VM_REQUESTS_PROJECTION.CREATED_AT)!!.toInstant(),
                        version = record.get(VM_REQUESTS_PROJECTION.VERSION)!!.toLong()
                    )
                }
        }

    /**
     * Finds recent requests by the same requester for admin context.
     *
     * AC 6: Requester History shown (up to 5 recent requests excluding current)
     *
     * @param requesterId The ID of the requester
     * @param excludeRequestId The current request ID to exclude
     * @param limit Maximum number of results (default 5)
     * @return List of recent request summaries sorted by date descending
     */
    override suspend fun findRecentByRequesterId(
        requesterId: UserId,
        excludeRequestId: VmRequestId,
        limit: Int
    ): List<VmRequestHistorySummary> = withContext(ioDispatcher) {
        dsl.select(
            VM_REQUESTS_PROJECTION.ID,
            VM_REQUESTS_PROJECTION.VM_NAME,
            VM_REQUESTS_PROJECTION.STATUS,
            VM_REQUESTS_PROJECTION.CREATED_AT
        )
            .from(VM_REQUESTS_PROJECTION)
            .where(VM_REQUESTS_PROJECTION.REQUESTER_ID.eq(requesterId.value))
            .and(VM_REQUESTS_PROJECTION.ID.ne(excludeRequestId.value))
            .orderBy(VM_REQUESTS_PROJECTION.CREATED_AT.desc())
            .limit(limit)
            .fetch { record ->
                VmRequestHistorySummary(
                    id = VmRequestId(record.get(VM_REQUESTS_PROJECTION.ID)!!),
                    vmName = record.get(VM_REQUESTS_PROJECTION.VM_NAME)!!,
                    status = record.get(VM_REQUESTS_PROJECTION.STATUS)!!,
                    createdAt = record.get(VM_REQUESTS_PROJECTION.CREATED_AT)!!.toInstant()
                )
            }
    }
}
