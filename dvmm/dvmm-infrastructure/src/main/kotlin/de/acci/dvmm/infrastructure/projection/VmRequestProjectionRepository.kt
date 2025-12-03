package de.acci.dvmm.infrastructure.projection

import de.acci.dvmm.infrastructure.jooq.`public`.tables.VmRequestsProjection.Companion.VM_REQUESTS_PROJECTION
import de.acci.dvmm.infrastructure.jooq.`public`.tables.pojos.VmRequestsProjection
import de.acci.eaf.eventsourcing.projection.PageRequest
import de.acci.eaf.eventsourcing.projection.PagedResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SortField
import org.jooq.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Simple data class representing a project for filter dropdowns.
 *
 * Story 2.9: Admin Approval Queue (AC 5)
 */
public data class ProjectInfo(
    val projectId: UUID,
    val projectName: String
)

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
        // All NOT NULL fields use !! assertion, nullable fields use null-safe access
        return VmRequestsProjection(
            id = record.get(VM_REQUESTS_PROJECTION.ID)!!,
            tenantId = record.get(VM_REQUESTS_PROJECTION.TENANT_ID)!!,
            requesterId = record.get(VM_REQUESTS_PROJECTION.REQUESTER_ID)!!,
            requesterName = record.get(VM_REQUESTS_PROJECTION.REQUESTER_NAME)!!,
            requesterEmail = record.get(VM_REQUESTS_PROJECTION.REQUESTER_EMAIL),
            requesterRole = record.get(VM_REQUESTS_PROJECTION.REQUESTER_ROLE),
            projectId = record.get(VM_REQUESTS_PROJECTION.PROJECT_ID)!!,
            projectName = record.get(VM_REQUESTS_PROJECTION.PROJECT_NAME)!!,
            vmName = record.get(VM_REQUESTS_PROJECTION.VM_NAME)!!,
            size = record.get(VM_REQUESTS_PROJECTION.SIZE)!!,
            cpuCores = record.get(VM_REQUESTS_PROJECTION.CPU_CORES)!!,
            memoryGb = record.get(VM_REQUESTS_PROJECTION.MEMORY_GB)!!,
            diskGb = record.get(VM_REQUESTS_PROJECTION.DISK_GB)!!,
            justification = record.get(VM_REQUESTS_PROJECTION.JUSTIFICATION)!!,
            status = record.get(VM_REQUESTS_PROJECTION.STATUS)!!,
            approvedBy = record.get(VM_REQUESTS_PROJECTION.APPROVED_BY),
            approvedByName = record.get(VM_REQUESTS_PROJECTION.APPROVED_BY_NAME),
            rejectedBy = record.get(VM_REQUESTS_PROJECTION.REJECTED_BY),
            rejectedByName = record.get(VM_REQUESTS_PROJECTION.REJECTED_BY_NAME),
            rejectionReason = record.get(VM_REQUESTS_PROJECTION.REJECTION_REASON),
            createdAt = record.get(VM_REQUESTS_PROJECTION.CREATED_AT)!!,
            updatedAt = record.get(VM_REQUESTS_PROJECTION.UPDATED_AT)!!,
            version = record.get(VM_REQUESTS_PROJECTION.VERSION)
        )
    }

    override fun table(): Table<*> = VM_REQUESTS_PROJECTION

    /**
     * Returns the default ordering for deterministic pagination.
     * Orders by creation date descending (newest first).
     */
    override fun defaultOrderBy(): List<SortField<*>> = listOf(
        VM_REQUESTS_PROJECTION.CREATED_AT.desc()
    )

    /**
     * Inserts a new VM request projection.
     *
     * Used when handling VmRequestCreated events from the event store.
     *
     * @param projection The projection data to insert
     */
    public suspend fun insert(projection: VmRequestsProjection): Unit = withContext(Dispatchers.IO) {
        dsl.insertInto(VM_REQUESTS_PROJECTION)
            .set(VM_REQUESTS_PROJECTION.ID, projection.id)
            .set(VM_REQUESTS_PROJECTION.TENANT_ID, projection.tenantId)
            .set(VM_REQUESTS_PROJECTION.REQUESTER_ID, projection.requesterId)
            .set(VM_REQUESTS_PROJECTION.REQUESTER_NAME, projection.requesterName)
            .set(VM_REQUESTS_PROJECTION.PROJECT_ID, projection.projectId)
            .set(VM_REQUESTS_PROJECTION.PROJECT_NAME, projection.projectName)
            .set(VM_REQUESTS_PROJECTION.VM_NAME, projection.vmName)
            .set(VM_REQUESTS_PROJECTION.SIZE, projection.size)
            .set(VM_REQUESTS_PROJECTION.CPU_CORES, projection.cpuCores)
            .set(VM_REQUESTS_PROJECTION.MEMORY_GB, projection.memoryGb)
            .set(VM_REQUESTS_PROJECTION.DISK_GB, projection.diskGb)
            .set(VM_REQUESTS_PROJECTION.JUSTIFICATION, projection.justification)
            .set(VM_REQUESTS_PROJECTION.STATUS, projection.status)
            .set(VM_REQUESTS_PROJECTION.CREATED_AT, projection.createdAt)
            .set(VM_REQUESTS_PROJECTION.UPDATED_AT, projection.updatedAt)
            .set(VM_REQUESTS_PROJECTION.VERSION, projection.version)
            .execute()
    }

    /**
     * Updates an existing VM request projection.
     *
     * Used when handling state change events (approval, rejection, etc.).
     *
     * @param id The ID of the projection to update
     * @param status The new status
     * @param approvedBy Optional approver ID (for APPROVED status)
     * @param approvedByName Optional approver name
     * @param rejectedBy Optional rejector ID (for REJECTED status)
     * @param rejectedByName Optional rejector name
     * @param rejectionReason Optional rejection reason
     * @param version The new version (for optimistic locking)
     */
    public suspend fun updateStatus(
        id: UUID,
        status: String,
        approvedBy: UUID? = null,
        approvedByName: String? = null,
        rejectedBy: UUID? = null,
        rejectedByName: String? = null,
        rejectionReason: String? = null,
        version: Int?
    ): Int = withContext(Dispatchers.IO) {
        dsl.update(VM_REQUESTS_PROJECTION)
            .set(VM_REQUESTS_PROJECTION.STATUS, status)
            .set(VM_REQUESTS_PROJECTION.APPROVED_BY, approvedBy)
            .set(VM_REQUESTS_PROJECTION.APPROVED_BY_NAME, approvedByName)
            .set(VM_REQUESTS_PROJECTION.REJECTED_BY, rejectedBy)
            .set(VM_REQUESTS_PROJECTION.REJECTED_BY_NAME, rejectedByName)
            .set(VM_REQUESTS_PROJECTION.REJECTION_REASON, rejectionReason)
            .set(VM_REQUESTS_PROJECTION.UPDATED_AT, OffsetDateTime.now())
            .set(VM_REQUESTS_PROJECTION.VERSION, version)
            .where(VM_REQUESTS_PROJECTION.ID.eq(id))
            .execute()
    }

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
    ): PagedResponse<VmRequestsProjection> =
        findByCondition(
            condition = VM_REQUESTS_PROJECTION.STATUS.eq(status),
            pageRequest = pageRequest
        )

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
    ): PagedResponse<VmRequestsProjection> =
        findByCondition(
            condition = VM_REQUESTS_PROJECTION.REQUESTER_ID.eq(requesterId),
            pageRequest = pageRequest
        )

    /**
     * Finds all pending VM request projections for admin queue.
     *
     * Story 2.9: Admin Approval Queue (AC 1, 2, 3, 5, 6)
     *
     * Returns PENDING status requests, sorted by creation date ascending
     * (oldest first) per AC 3: "sorted oldest→newest by submission time"
     *
     * RLS NOTE: Tenant filtering is automatic via PostgreSQL RLS.
     *
     * @param projectId Optional project filter (AC 5)
     * @param pageRequest Pagination parameters
     * @return A paginated response of pending projections
     */
    public suspend fun findPendingByTenantId(
        projectId: UUID? = null,
        pageRequest: PageRequest = PageRequest()
    ): PagedResponse<VmRequestsProjection> = withContext(Dispatchers.IO) {
        // Build condition: always filter by PENDING status
        var condition: Condition = VM_REQUESTS_PROJECTION.STATUS.eq("PENDING")

        // Add optional project filter (AC 5)
        if (projectId != null) {
            condition = condition.and(VM_REQUESTS_PROJECTION.PROJECT_ID.eq(projectId))
        }

        // Count total elements matching the condition
        val totalElements = dsl.selectCount()
            .from(VM_REQUESTS_PROJECTION)
            .where(condition)
            .fetchOne(0, Long::class.java) ?: 0L

        // Query with oldest first ordering (AC 3)
        val items = dsl.selectFrom(VM_REQUESTS_PROJECTION)
            .where(condition)
            .orderBy(VM_REQUESTS_PROJECTION.CREATED_AT.asc())
            .limit(pageRequest.size)
            .offset(pageRequest.offset)
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
     * Finds distinct projects that have VM requests.
     *
     * Story 2.9: Admin Approval Queue (AC 5)
     *
     * Used to populate the project filter dropdown. Returns projects
     * from all VM request statuses (pending, approved, rejected).
     *
     * RLS NOTE: Tenant filtering is automatic via PostgreSQL RLS.
     *
     * @return List of distinct projects, sorted alphabetically by name
     */
    public suspend fun findDistinctProjects(): List<ProjectInfo> = withContext(Dispatchers.IO) {
        dsl.selectDistinct(
            VM_REQUESTS_PROJECTION.PROJECT_ID,
            VM_REQUESTS_PROJECTION.PROJECT_NAME
        )
            .from(VM_REQUESTS_PROJECTION)
            .orderBy(VM_REQUESTS_PROJECTION.PROJECT_NAME.asc())
            .fetch { record ->
                ProjectInfo(
                    projectId = record.get(VM_REQUESTS_PROJECTION.PROJECT_ID)!!,
                    projectName = record.get(VM_REQUESTS_PROJECTION.PROJECT_NAME)!!
                )
            }
    }

    /**
     * Shared pagination logic for filtered queries.
     *
     * @param condition The WHERE condition for filtering
     * @param pageRequest Pagination parameters
     * @return A paginated response of matching projections
     */
    private suspend fun findByCondition(
        condition: Condition,
        pageRequest: PageRequest
    ): PagedResponse<VmRequestsProjection> = withContext(Dispatchers.IO) {
        val totalElements = dsl.selectCount()
            .from(VM_REQUESTS_PROJECTION)
            .where(condition)
            .fetchOne(0, Long::class.java) ?: 0L

        val items = dsl.selectFrom(VM_REQUESTS_PROJECTION)
            .where(condition)
            .orderBy(defaultOrderBy())
            .limit(pageRequest.size)
            .offset(pageRequest.offset)
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
