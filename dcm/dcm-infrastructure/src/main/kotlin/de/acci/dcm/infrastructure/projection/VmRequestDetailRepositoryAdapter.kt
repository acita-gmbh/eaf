package de.acci.dcm.infrastructure.projection

import de.acci.dcm.application.vmrequest.VmRequestDetailProjection
import de.acci.dcm.application.vmrequest.VmRequestDetailRepository
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.dcm.infrastructure.jooq.`public`.tables.VmRequestsProjection.Companion.VM_REQUESTS_PROJECTION
import de.acci.eaf.core.types.UserId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext

/**
 * Infrastructure adapter implementing VmRequestDetailRepository.
 *
 * Provides read access to detailed VM request projections.
 * RLS ensures tenant isolation automatically.
 *
 * @param dsl The jOOQ DSLContext for database operations
 * @param ioDispatcher Dispatcher for blocking I/O operations (injectable for testing)
 */
public class VmRequestDetailRepositoryAdapter(
    private val dsl: DSLContext,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : VmRequestDetailRepository {

    override suspend fun findById(requestId: VmRequestId): VmRequestDetailProjection? =
        withContext(ioDispatcher) {
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
                        createdAt = record.get(VM_REQUESTS_PROJECTION.CREATED_AT)!!.toInstant(),
                        vmwareVmId = record.get(VM_REQUESTS_PROJECTION.VMWARE_VM_ID),
                        ipAddress = record.get(VM_REQUESTS_PROJECTION.IP_ADDRESS),
                        hostname = record.get(VM_REQUESTS_PROJECTION.HOSTNAME),
                        powerState = record.get(VM_REQUESTS_PROJECTION.POWER_STATE),
                        guestOs = record.get(VM_REQUESTS_PROJECTION.GUEST_OS),
                        lastSyncedAt = record.get(VM_REQUESTS_PROJECTION.LAST_SYNCED_AT)?.toInstant(),
                        bootTime = record.get(VM_REQUESTS_PROJECTION.BOOT_TIME)?.toInstant()
                    )
                }
        }
}
