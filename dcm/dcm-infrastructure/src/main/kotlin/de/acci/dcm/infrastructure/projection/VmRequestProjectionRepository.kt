package de.acci.dcm.infrastructure.projection

import de.acci.dcm.infrastructure.jooq.`public`.tables.VmRequestsProjection.Companion.VM_REQUESTS_PROJECTION
import de.acci.dcm.infrastructure.jooq.`public`.tables.pojos.VmRequestsProjection
import de.acci.eaf.eventsourcing.projection.PageRequest
import de.acci.eaf.eventsourcing.projection.PagedResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.InsertSetMoreStep
import org.jooq.Record
import org.jooq.SortField
import org.jooq.Table
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
 *
 * ## Column Symmetry Pattern
 *
 * This repository uses a sealed column mapping pattern to ensure read/write symmetry.
 * Both [mapRecord] (read) and [insert] (write) use the same [ProjectionColumns] sealed
 * interface to guarantee that all columns are handled consistently.
 *
 * **Adding new columns:**
 * 1. Add new sealed class to [ProjectionColumns]
 * 2. Add case to [mapColumn] in [mapRecord]
 * 3. Add case to [setColumn] in insert
 * 4. Compile will fail if any step is missed
 *
 * @see ProjectionColumns
 * @param dsl The jOOQ DSLContext for database operations
 * @param ioDispatcher Dispatcher for blocking I/O operations (injectable for testing)
 */
public class VmRequestProjectionRepository(
    dsl: DSLContext,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BaseProjectionRepository<VmRequestsProjection>(dsl, ioDispatcher) {

    /**
     * Sealed interface defining all columns in VM_REQUESTS_PROJECTION.
     *
     * This pattern ensures compile-time safety: if a new column is added to this
     * sealed hierarchy, both [mapRecord] and [insert] must handle it or the
     * exhaustive `when` expressions will fail to compile.
     *
     * @see mapRecord
     * @see insert
     */
    public sealed interface ProjectionColumns {
        public data object Id : ProjectionColumns
        public data object TenantId : ProjectionColumns
        public data object RequesterId : ProjectionColumns
        public data object RequesterName : ProjectionColumns
        public data object RequesterEmail : ProjectionColumns
        public data object RequesterRole : ProjectionColumns
        public data object ProjectId : ProjectionColumns
        public data object ProjectName : ProjectionColumns
        public data object VmName : ProjectionColumns
        public data object Size : ProjectionColumns
        public data object CpuCores : ProjectionColumns
        public data object MemoryGb : ProjectionColumns
        public data object DiskGb : ProjectionColumns
        public data object Justification : ProjectionColumns
        public data object Status : ProjectionColumns
        public data object ApprovedBy : ProjectionColumns
        public data object ApprovedByName : ProjectionColumns
        public data object RejectedBy : ProjectionColumns
        public data object RejectedByName : ProjectionColumns
        public data object RejectionReason : ProjectionColumns
        public data object CreatedAt : ProjectionColumns
        public data object UpdatedAt : ProjectionColumns
        public data object Version : ProjectionColumns
        public data object VmwareVmId : ProjectionColumns
        public data object IpAddress : ProjectionColumns
        public data object Hostname : ProjectionColumns
        public data object PowerState : ProjectionColumns
        public data object GuestOs : ProjectionColumns
        public data object LastSyncedAt : ProjectionColumns
        public data object BootTime : ProjectionColumns

        public companion object {
            /**
             * All columns that must be handled by read and write operations.
             * Iterating over this list ensures exhaustive handling.
             */
            public val all: List<ProjectionColumns> = listOf(
                Id, TenantId, RequesterId, RequesterName, RequesterEmail, RequesterRole,
                ProjectId, ProjectName, VmName, Size, CpuCores, MemoryGb, DiskGb,
                Justification, Status, ApprovedBy, ApprovedByName, RejectedBy,
                RejectedByName, RejectionReason, CreatedAt, UpdatedAt, Version,
                VmwareVmId, IpAddress, Hostname, PowerState, GuestOs, LastSyncedAt,
                BootTime
            )
        }
    }

    /**
     * Maps a column to its value from a jOOQ Record.
     * Exhaustive when expression ensures all columns are handled.
     */
    private fun mapColumn(record: Record, column: ProjectionColumns): Any? = when (column) {
        ProjectionColumns.Id -> record.get(VM_REQUESTS_PROJECTION.ID)!!
        ProjectionColumns.TenantId -> record.get(VM_REQUESTS_PROJECTION.TENANT_ID)!!
        ProjectionColumns.RequesterId -> record.get(VM_REQUESTS_PROJECTION.REQUESTER_ID)!!
        ProjectionColumns.RequesterName -> record.get(VM_REQUESTS_PROJECTION.REQUESTER_NAME)!!
        ProjectionColumns.RequesterEmail -> record.get(VM_REQUESTS_PROJECTION.REQUESTER_EMAIL)
        ProjectionColumns.RequesterRole -> record.get(VM_REQUESTS_PROJECTION.REQUESTER_ROLE)
        ProjectionColumns.ProjectId -> record.get(VM_REQUESTS_PROJECTION.PROJECT_ID)!!
        ProjectionColumns.ProjectName -> record.get(VM_REQUESTS_PROJECTION.PROJECT_NAME)!!
        ProjectionColumns.VmName -> record.get(VM_REQUESTS_PROJECTION.VM_NAME)!!
        ProjectionColumns.Size -> record.get(VM_REQUESTS_PROJECTION.SIZE)!!
        ProjectionColumns.CpuCores -> record.get(VM_REQUESTS_PROJECTION.CPU_CORES)!!
        ProjectionColumns.MemoryGb -> record.get(VM_REQUESTS_PROJECTION.MEMORY_GB)!!
        ProjectionColumns.DiskGb -> record.get(VM_REQUESTS_PROJECTION.DISK_GB)!!
        ProjectionColumns.Justification -> record.get(VM_REQUESTS_PROJECTION.JUSTIFICATION)!!
        ProjectionColumns.Status -> record.get(VM_REQUESTS_PROJECTION.STATUS)!!
        ProjectionColumns.ApprovedBy -> record.get(VM_REQUESTS_PROJECTION.APPROVED_BY)
        ProjectionColumns.ApprovedByName -> record.get(VM_REQUESTS_PROJECTION.APPROVED_BY_NAME)
        ProjectionColumns.RejectedBy -> record.get(VM_REQUESTS_PROJECTION.REJECTED_BY)
        ProjectionColumns.RejectedByName -> record.get(VM_REQUESTS_PROJECTION.REJECTED_BY_NAME)
        ProjectionColumns.RejectionReason -> record.get(VM_REQUESTS_PROJECTION.REJECTION_REASON)
        ProjectionColumns.CreatedAt -> record.get(VM_REQUESTS_PROJECTION.CREATED_AT)!!
        ProjectionColumns.UpdatedAt -> record.get(VM_REQUESTS_PROJECTION.UPDATED_AT)!!
        ProjectionColumns.Version -> record.get(VM_REQUESTS_PROJECTION.VERSION)
        ProjectionColumns.VmwareVmId -> record.get(VM_REQUESTS_PROJECTION.VMWARE_VM_ID)
        ProjectionColumns.IpAddress -> record.get(VM_REQUESTS_PROJECTION.IP_ADDRESS)
        ProjectionColumns.Hostname -> record.get(VM_REQUESTS_PROJECTION.HOSTNAME)
        ProjectionColumns.PowerState -> record.get(VM_REQUESTS_PROJECTION.POWER_STATE)
        ProjectionColumns.GuestOs -> record.get(VM_REQUESTS_PROJECTION.GUEST_OS)
        ProjectionColumns.LastSyncedAt -> record.get(VM_REQUESTS_PROJECTION.LAST_SYNCED_AT)
        ProjectionColumns.BootTime -> record.get(VM_REQUESTS_PROJECTION.BOOT_TIME)
    }

    /**
     * Sets a column value in an INSERT statement.
     * Exhaustive when expression ensures all columns are handled symmetrically with [mapColumn].
     */
    @Suppress("UNCHECKED_CAST")
    private fun setColumn(
        step: InsertSetMoreStep<*>,
        column: ProjectionColumns,
        projection: VmRequestsProjection
    ): InsertSetMoreStep<*> = when (column) {
        ProjectionColumns.Id -> step.set(VM_REQUESTS_PROJECTION.ID, projection.id)
        ProjectionColumns.TenantId -> step.set(VM_REQUESTS_PROJECTION.TENANT_ID, projection.tenantId)
        ProjectionColumns.RequesterId -> step.set(VM_REQUESTS_PROJECTION.REQUESTER_ID, projection.requesterId)
        ProjectionColumns.RequesterName -> step.set(VM_REQUESTS_PROJECTION.REQUESTER_NAME, projection.requesterName)
        ProjectionColumns.RequesterEmail -> step.set(VM_REQUESTS_PROJECTION.REQUESTER_EMAIL, projection.requesterEmail)
        ProjectionColumns.RequesterRole -> step.set(VM_REQUESTS_PROJECTION.REQUESTER_ROLE, projection.requesterRole)
        ProjectionColumns.ProjectId -> step.set(VM_REQUESTS_PROJECTION.PROJECT_ID, projection.projectId)
        ProjectionColumns.ProjectName -> step.set(VM_REQUESTS_PROJECTION.PROJECT_NAME, projection.projectName)
        ProjectionColumns.VmName -> step.set(VM_REQUESTS_PROJECTION.VM_NAME, projection.vmName)
        ProjectionColumns.Size -> step.set(VM_REQUESTS_PROJECTION.SIZE, projection.size)
        ProjectionColumns.CpuCores -> step.set(VM_REQUESTS_PROJECTION.CPU_CORES, projection.cpuCores)
        ProjectionColumns.MemoryGb -> step.set(VM_REQUESTS_PROJECTION.MEMORY_GB, projection.memoryGb)
        ProjectionColumns.DiskGb -> step.set(VM_REQUESTS_PROJECTION.DISK_GB, projection.diskGb)
        ProjectionColumns.Justification -> step.set(VM_REQUESTS_PROJECTION.JUSTIFICATION, projection.justification)
        ProjectionColumns.Status -> step.set(VM_REQUESTS_PROJECTION.STATUS, projection.status)
        ProjectionColumns.ApprovedBy -> step.set(VM_REQUESTS_PROJECTION.APPROVED_BY, projection.approvedBy)
        ProjectionColumns.ApprovedByName -> step.set(VM_REQUESTS_PROJECTION.APPROVED_BY_NAME, projection.approvedByName)
        ProjectionColumns.RejectedBy -> step.set(VM_REQUESTS_PROJECTION.REJECTED_BY, projection.rejectedBy)
        ProjectionColumns.RejectedByName -> step.set(VM_REQUESTS_PROJECTION.REJECTED_BY_NAME, projection.rejectedByName)
        ProjectionColumns.RejectionReason -> step.set(VM_REQUESTS_PROJECTION.REJECTION_REASON, projection.rejectionReason)
        ProjectionColumns.CreatedAt -> step.set(VM_REQUESTS_PROJECTION.CREATED_AT, projection.createdAt)
        ProjectionColumns.UpdatedAt -> step.set(VM_REQUESTS_PROJECTION.UPDATED_AT, projection.updatedAt)
        ProjectionColumns.Version -> step.set(VM_REQUESTS_PROJECTION.VERSION, projection.version)
        ProjectionColumns.VmwareVmId -> step.set(VM_REQUESTS_PROJECTION.VMWARE_VM_ID, projection.vmwareVmId)
        ProjectionColumns.IpAddress -> step.set(VM_REQUESTS_PROJECTION.IP_ADDRESS, projection.ipAddress)
        ProjectionColumns.Hostname -> step.set(VM_REQUESTS_PROJECTION.HOSTNAME, projection.hostname)
        ProjectionColumns.PowerState -> step.set(VM_REQUESTS_PROJECTION.POWER_STATE, projection.powerState)
        ProjectionColumns.GuestOs -> step.set(VM_REQUESTS_PROJECTION.GUEST_OS, projection.guestOs)
        ProjectionColumns.LastSyncedAt -> step.set(VM_REQUESTS_PROJECTION.LAST_SYNCED_AT, projection.lastSyncedAt)
        ProjectionColumns.BootTime -> step.set(VM_REQUESTS_PROJECTION.BOOT_TIME, projection.bootTime)
    }

    override fun mapRecord(record: Record): VmRequestsProjection {
        // Uses mapColumn for each field to ensure symmetry with insert()
        return VmRequestsProjection(
            id = mapColumn(record, ProjectionColumns.Id) as UUID,
            tenantId = mapColumn(record, ProjectionColumns.TenantId) as UUID,
            requesterId = mapColumn(record, ProjectionColumns.RequesterId) as UUID,
            requesterName = mapColumn(record, ProjectionColumns.RequesterName) as String,
            requesterEmail = mapColumn(record, ProjectionColumns.RequesterEmail) as String?,
            requesterRole = mapColumn(record, ProjectionColumns.RequesterRole) as String?,
            projectId = mapColumn(record, ProjectionColumns.ProjectId) as UUID,
            projectName = mapColumn(record, ProjectionColumns.ProjectName) as String,
            vmName = mapColumn(record, ProjectionColumns.VmName) as String,
            size = mapColumn(record, ProjectionColumns.Size) as String,
            cpuCores = mapColumn(record, ProjectionColumns.CpuCores) as Int,
            memoryGb = mapColumn(record, ProjectionColumns.MemoryGb) as Int,
            diskGb = mapColumn(record, ProjectionColumns.DiskGb) as Int,
            justification = mapColumn(record, ProjectionColumns.Justification) as String,
            status = mapColumn(record, ProjectionColumns.Status) as String,
            approvedBy = mapColumn(record, ProjectionColumns.ApprovedBy) as UUID?,
            approvedByName = mapColumn(record, ProjectionColumns.ApprovedByName) as String?,
            rejectedBy = mapColumn(record, ProjectionColumns.RejectedBy) as UUID?,
            rejectedByName = mapColumn(record, ProjectionColumns.RejectedByName) as String?,
            rejectionReason = mapColumn(record, ProjectionColumns.RejectionReason) as String?,
            createdAt = mapColumn(record, ProjectionColumns.CreatedAt) as OffsetDateTime,
            updatedAt = mapColumn(record, ProjectionColumns.UpdatedAt) as OffsetDateTime,
            version = mapColumn(record, ProjectionColumns.Version) as Int?,
            vmwareVmId = mapColumn(record, ProjectionColumns.VmwareVmId) as String?,
            ipAddress = mapColumn(record, ProjectionColumns.IpAddress) as String?,
            hostname = mapColumn(record, ProjectionColumns.Hostname) as String?,
            powerState = mapColumn(record, ProjectionColumns.PowerState) as String?,
            guestOs = mapColumn(record, ProjectionColumns.GuestOs) as String?,
            lastSyncedAt = mapColumn(record, ProjectionColumns.LastSyncedAt) as OffsetDateTime?,
            bootTime = mapColumn(record, ProjectionColumns.BootTime) as OffsetDateTime?
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
     * Uses [setColumn] for each field to ensure symmetry with [mapRecord].
     * The [ProjectionColumns.all] list guarantees all columns are set.
     *
     * @param projection The projection data to insert
     */
    public suspend fun insert(projection: VmRequestsProjection): Unit = withContext(ioDispatcher) {
        // Start with ID column to get InsertSetMoreStep type
        val initialStep: InsertSetMoreStep<*> = dsl.insertInto(VM_REQUESTS_PROJECTION)
            .set(VM_REQUESTS_PROJECTION.ID, projection.id)

        // Set remaining columns, explicitly filtering out Id to avoid order dependency on all list
        var step = initialStep
        ProjectionColumns.all
            .filterNot { it is ProjectionColumns.Id }
            .forEach { column -> step = setColumn(step, column, projection) }

        step.execute()
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
    ): Int = withContext(ioDispatcher) {
        dsl.update(VM_REQUESTS_PROJECTION)
            .set(VM_REQUESTS_PROJECTION.STATUS, status)
            .set(VM_REQUESTS_PROJECTION.APPROVED_BY, approvedBy)
            .set(VM_REQUESTS_PROJECTION.APPROVED_BY_NAME, approvedByName)
            .set(VM_REQUESTS_PROJECTION.REJECTED_BY, rejectedBy)
            .set(VM_REQUESTS_PROJECTION.REJECTED_BY_NAME, rejectedByName)
            .set(VM_REQUESTS_PROJECTION.REJECTION_REASON, rejectionReason)
            .set(VM_REQUESTS_PROJECTION.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .set(VM_REQUESTS_PROJECTION.VERSION, version)
            .where(VM_REQUESTS_PROJECTION.ID.eq(id))
            .execute()
    }

    /**
     * Updates VM details on an existing VM request projection.
     *
     * Story 3-7: Called when:
     * 1. Provisioning completes (from VmProvisioned event handler)
     * 2. User triggers "Sync Status" from vSphere (any authenticated user with access)
     *
     * @param id The ID of the projection to update
     * @param vmwareVmId VMware MoRef ID (e.g., vm-123)
     * @param ipAddress Primary IP address from VMware Tools
     * @param hostname Guest hostname from VMware Tools
     * @param powerState VM power state: POWERED_ON, POWERED_OFF, SUSPENDED
     * @param guestOs Detected guest OS from VMware Tools
     * @param lastSyncedAt Timestamp of the sync operation
     */
    public suspend fun updateVmDetails(
        id: UUID,
        vmwareVmId: String?,
        ipAddress: String?,
        hostname: String?,
        powerState: String?,
        guestOs: String?,
        lastSyncedAt: OffsetDateTime
    ): Int = withContext(ioDispatcher) {
        dsl.update(VM_REQUESTS_PROJECTION)
            .set(VM_REQUESTS_PROJECTION.VMWARE_VM_ID, vmwareVmId)
            .set(VM_REQUESTS_PROJECTION.IP_ADDRESS, ipAddress)
            .set(VM_REQUESTS_PROJECTION.HOSTNAME, hostname)
            .set(VM_REQUESTS_PROJECTION.POWER_STATE, powerState)
            .set(VM_REQUESTS_PROJECTION.GUEST_OS, guestOs)
            .set(VM_REQUESTS_PROJECTION.LAST_SYNCED_AT, lastSyncedAt)
            .set(VM_REQUESTS_PROJECTION.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(VM_REQUESTS_PROJECTION.ID.eq(id))
            .execute()
    }

    /**
     * Finds a VM request projection by its ID.
     *
     * @param id The unique identifier of the VM request
     * @return The projection if found, null otherwise
     */
    public suspend fun findById(id: UUID): VmRequestsProjection? = withContext(ioDispatcher) {
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
    ): PagedResponse<VmRequestsProjection> = withContext(ioDispatcher) {
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
    public suspend fun findDistinctProjects(): List<ProjectInfo> = withContext(ioDispatcher) {
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
    ): PagedResponse<VmRequestsProjection> = withContext(ioDispatcher) {
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
