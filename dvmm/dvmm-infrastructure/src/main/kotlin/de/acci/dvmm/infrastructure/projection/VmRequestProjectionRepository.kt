package de.acci.dvmm.infrastructure.projection

import de.acci.dvmm.infrastructure.jooq.`public`.tables.VmRequestsProjection.Companion.VM_REQUESTS_PROJECTION
import de.acci.dvmm.infrastructure.jooq.`public`.tables.pojos.VmRequestsProjection
import de.acci.eaf.eventsourcing.projection.PageRequest
import de.acci.eaf.eventsourcing.projection.PagedResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Table
import java.util.UUID

/**
 * Repository for querying VM request projections.
 *
 * RLS NOTE: Tenant filtering is handled automatically by PostgreSQL Row-Level Security.
 * All queries through this repository are automatically filtered to the current tenant
 * based on the `app.tenant_id` session variable set by the connection customizer.
 */
public class VmRequestProjectionRepository(
    dsl: DSLContext
) : BaseProjectionRepository<VmRequestsProjection>(dsl) {

    override fun mapRecord(record: Record): VmRequestsProjection {
        // All fields are NOT NULL in the database schema, so we can safely use !!
        return VmRequestsProjection(
            id = record.get(VM_REQUESTS_PROJECTION.ID)!!,
            tenantId = record.get(VM_REQUESTS_PROJECTION.TENANT_ID)!!,
            requesterId = record.get(VM_REQUESTS_PROJECTION.REQUESTER_ID)!!,
            vmName = record.get(VM_REQUESTS_PROJECTION.VM_NAME)!!,
            cpuCores = record.get(VM_REQUESTS_PROJECTION.CPU_CORES)!!,
            memoryGb = record.get(VM_REQUESTS_PROJECTION.MEMORY_GB)!!,
            status = record.get(VM_REQUESTS_PROJECTION.STATUS)!!,
            createdAt = record.get(VM_REQUESTS_PROJECTION.CREATED_AT)!!,
            updatedAt = record.get(VM_REQUESTS_PROJECTION.UPDATED_AT)!!,
            version = record.get(VM_REQUESTS_PROJECTION.VERSION)
        )
    }

    override fun table(): Table<*> = VM_REQUESTS_PROJECTION

    /**
     * Finds a VM request projection by its ID.
     *
     * @param id The unique identifier of the VM request
     * @return The projection if found, null otherwise
     */
    public suspend fun findById(id: UUID): VmRequestsProjection? = withContext(Dispatchers.IO) {
        dsl.selectFrom(VM_REQUESTS_PROJECTION)
            .where(VM_REQUESTS_PROJECTION.ID.eq(id))
            .fetchOne()
            ?.let { mapRecord(it) }
    }

    /**
     * Finds all VM request projections with a specific status.
     *
     * @param status The status to filter by (e.g., "PENDING", "APPROVED", "REJECTED")
     * @param pageRequest Pagination parameters
     * @return A paginated response of matching projections
     */
    public suspend fun findByStatus(
        status: String,
        pageRequest: PageRequest = PageRequest()
    ): PagedResponse<VmRequestsProjection> = withContext(Dispatchers.IO) {
        val totalElements = dsl.selectCount()
            .from(VM_REQUESTS_PROJECTION)
            .where(VM_REQUESTS_PROJECTION.STATUS.eq(status))
            .fetchOne(0, Long::class.java) ?: 0L

        val items = dsl.selectFrom(VM_REQUESTS_PROJECTION)
            .where(VM_REQUESTS_PROJECTION.STATUS.eq(status))
            .orderBy(VM_REQUESTS_PROJECTION.CREATED_AT.desc())
            .limit(pageRequest.size)
            .offset(pageRequest.offset.toInt())
            .fetch()
            .map { mapRecord(it) }

        PagedResponse(
            items = items,
            page = pageRequest.page,
            size = pageRequest.size,
            totalElements = totalElements
        )
    }

    /**
     * Finds all VM request projections for a specific requester.
     *
     * @param requesterId The ID of the requester
     * @param pageRequest Pagination parameters
     * @return A paginated response of matching projections
     */
    public suspend fun findByRequesterId(
        requesterId: UUID,
        pageRequest: PageRequest = PageRequest()
    ): PagedResponse<VmRequestsProjection> = withContext(Dispatchers.IO) {
        val totalElements = dsl.selectCount()
            .from(VM_REQUESTS_PROJECTION)
            .where(VM_REQUESTS_PROJECTION.REQUESTER_ID.eq(requesterId))
            .fetchOne(0, Long::class.java) ?: 0L

        val items = dsl.selectFrom(VM_REQUESTS_PROJECTION)
            .where(VM_REQUESTS_PROJECTION.REQUESTER_ID.eq(requesterId))
            .orderBy(VM_REQUESTS_PROJECTION.CREATED_AT.desc())
            .limit(pageRequest.size)
            .offset(pageRequest.offset.toInt())
            .fetch()
            .map { mapRecord(it) }

        PagedResponse(
            items = items,
            page = pageRequest.page,
            size = pageRequest.size,
            totalElements = totalElements
        )
    }
}
