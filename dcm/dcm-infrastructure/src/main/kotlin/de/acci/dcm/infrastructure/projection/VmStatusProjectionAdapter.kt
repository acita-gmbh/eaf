package de.acci.dcm.infrastructure.projection

import de.acci.dcm.application.vmrequest.VmStatusProjectionPort
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.dcm.infrastructure.jooq.`public`.tables.VmRequestsProjection.Companion.VM_REQUESTS_PROJECTION
import de.acci.eaf.core.types.UserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Infrastructure adapter implementing VmStatusProjectionPort.
 *
 * Story 3-7: Provides access to VM status operations on the projection.
 * RLS ensures tenant isolation automatically.
 */
public class VmStatusProjectionAdapter(
    private val dsl: DSLContext
) : VmStatusProjectionPort {

    override suspend fun updateVmDetails(
        requestId: VmRequestId,
        vmwareVmId: String?,
        ipAddress: String?,
        hostname: String?,
        powerState: String?,
        guestOs: String?,
        lastSyncedAt: Instant,
        bootTime: Instant?
    ): Int = withContext(Dispatchers.IO) {
        dsl.update(VM_REQUESTS_PROJECTION)
            .set(VM_REQUESTS_PROJECTION.VMWARE_VM_ID, vmwareVmId)
            .set(VM_REQUESTS_PROJECTION.IP_ADDRESS, ipAddress)
            .set(VM_REQUESTS_PROJECTION.HOSTNAME, hostname)
            .set(VM_REQUESTS_PROJECTION.POWER_STATE, powerState)
            .set(VM_REQUESTS_PROJECTION.GUEST_OS, guestOs)
            .set(VM_REQUESTS_PROJECTION.LAST_SYNCED_AT, OffsetDateTime.ofInstant(lastSyncedAt, ZoneOffset.UTC))
            .set(VM_REQUESTS_PROJECTION.BOOT_TIME, bootTime?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) })
            .set(VM_REQUESTS_PROJECTION.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(VM_REQUESTS_PROJECTION.ID.eq(requestId.value))
            .execute()
    }

    override suspend fun getVmwareVmId(requestId: VmRequestId): String? = withContext(Dispatchers.IO) {
        dsl.select(VM_REQUESTS_PROJECTION.VMWARE_VM_ID)
            .from(VM_REQUESTS_PROJECTION)
            .where(VM_REQUESTS_PROJECTION.ID.eq(requestId.value))
            .fetchOne(VM_REQUESTS_PROJECTION.VMWARE_VM_ID)
    }

    override suspend fun getRequesterId(requestId: VmRequestId): UserId? = withContext(Dispatchers.IO) {
        dsl.select(VM_REQUESTS_PROJECTION.REQUESTER_ID)
            .from(VM_REQUESTS_PROJECTION)
            .where(VM_REQUESTS_PROJECTION.ID.eq(requestId.value))
            .fetchOne(VM_REQUESTS_PROJECTION.REQUESTER_ID)
            ?.let { UserId(it) }
    }
}
