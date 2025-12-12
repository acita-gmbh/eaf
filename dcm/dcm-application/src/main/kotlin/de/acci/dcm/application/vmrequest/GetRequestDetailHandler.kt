package de.acci.dcm.application.vmrequest

import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/**
 * Query to retrieve detailed VM request information with timeline.
 */
public data class GetRequestDetailQuery(
    /** The tenant context for RLS */
    val tenantId: TenantId,
    /** The ID of the VM request to retrieve */
    val requestId: VmRequestId,
    /** The user making the request (for authorization) */
    val userId: UserId
)

/**
 * Errors that can occur when retrieving request details.
 */
public sealed class GetRequestDetailError {
    /**
     * Request not found (either doesn't exist or not visible to the user).
     */
    public data class NotFound(
        /** The ID of the missing request */
        val requestId: VmRequestId,
        /** Error message */
        val message: String = "VM request not found: ${requestId.value}"
    ) : GetRequestDetailError()

    /**
     * User is not authorized to view this request.
     * Only the original requester can view their request details.
     */
    public data class Forbidden(
        /** Error message */
        val message: String = "Not authorized to view this request"
    ) : GetRequestDetailError()

    /**
     * Unexpected failure when querying the read model.
     */
    public data class QueryFailure(
        /** Error message */
        val message: String
    ) : GetRequestDetailError()
}

/**
 * A single timeline event for display.
 */
public data class TimelineEventItem(
    /** Type of event (CREATED, APPROVED, REJECTED, CANCELLED, etc.) */
    val eventType: TimelineEventType,
    /** Display name of the user who performed the action (null for system events) */
    val actorName: String?,
    /** Additional event details (e.g., rejection reason) */
    val details: String?,
    /** When the event occurred */
    val occurredAt: Instant
)

/**
 * Detailed VM request information with timeline and VM runtime details.
 */
public data class VmRequestDetail(
    /** Unique identifier for the request */
    val id: VmRequestId,
    /** Name of the requested VM */
    val vmName: String,
    /** Size tier (SMALL, MEDIUM, LARGE) */
    val size: String,
    /** Number of CPU cores */
    val cpuCores: Int,
    /** RAM in gigabytes */
    val memoryGb: Int,
    /** Disk space in gigabytes */
    val diskGb: Int,
    /** Business justification for the request */
    val justification: String,
    /** Current status of the request */
    val status: String,
    /** Name of the project this VM belongs to */
    val projectName: String,
    /** Name of the user who submitted the request */
    val requesterName: String,
    /** When the request was submitted */
    val createdAt: Instant,
    /** List of timeline events in chronological order */
    val timeline: List<TimelineEventItem>,
    /** VMware MoRef ID (null if not yet provisioned) */
    val vmwareVmId: String? = null,
    /** Primary IP address from VMware Tools (null if not detected) */
    val ipAddress: String? = null,
    /** Guest hostname from VMware Tools (null if not detected) */
    val hostname: String? = null,
    /** VM power state: POWERED_ON, POWERED_OFF, SUSPENDED (null if not provisioned) */
    val powerState: String? = null,
    /** Detected guest OS from VMware Tools (null if not detected) */
    val guestOs: String? = null,
    /** Timestamp of last status sync from vSphere (null if never synced) */
    val lastSyncedAt: Instant? = null,
    /** Timestamp when VM was last powered on (null if not running or not detected) */
    val bootTime: Instant? = null
)

/**
 * Handler for GetRequestDetailQuery.
 *
 * Retrieves detailed VM request information including the full timeline
 * of events. Delegates to read repositories for actual data retrieval.
 *
 * ## Usage
 *
 * ```kotlin
 * val handler = GetRequestDetailHandler(requestRepository, timelineRepository)
 * val result = handler.handle(query)
 * result.fold(
 *     onSuccess = { detail ->
 *         println("Request: ${detail.vmName} - ${detail.status}")
 *         detail.timeline.forEach { event ->
 *             println("  ${event.occurredAt}: ${event.eventType}")
 *         }
 *     },
 *     onFailure = { println("Failed: $it") }
 * )
 * ```
 */
public class GetRequestDetailHandler(
    private val requestRepository: VmRequestDetailRepository,
    private val timelineRepository: TimelineEventReadRepository
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle the get request detail query.
     *
     * @param query The query to process
     * @return Result containing detailed request info with timeline or an error
     */
    public suspend fun handle(
        query: GetRequestDetailQuery
    ): Result<VmRequestDetail, GetRequestDetailError> {
        return try {
            // First, find the request
            val requestDetails = requestRepository.findById(query.requestId)
                ?: return GetRequestDetailError.NotFound(query.requestId).failure()

            // Authorization check: only the original requester can view details
            if (requestDetails.requesterId != query.userId) {
                logger.warn {
                    "Unauthorized access attempt: " +
                        "requestId=${query.requestId.value}, " +
                        "requesterId=${requestDetails.requesterId.value}, " +
                        "userId=${query.userId.value}"
                }
                return GetRequestDetailError.Forbidden().failure()
            }

            // Then, fetch the timeline events
            val timelineEvents = timelineRepository.findByRequestId(query.requestId)

            // Combine into the response
            VmRequestDetail(
                id = requestDetails.id,
                vmName = requestDetails.vmName,
                size = requestDetails.size,
                cpuCores = requestDetails.cpuCores,
                memoryGb = requestDetails.memoryGb,
                diskGb = requestDetails.diskGb,
                justification = requestDetails.justification,
                status = requestDetails.status,
                projectName = requestDetails.projectName,
                requesterName = requestDetails.requesterName,
                createdAt = requestDetails.createdAt,
                timeline = timelineEvents,
                vmwareVmId = requestDetails.vmwareVmId,
                ipAddress = requestDetails.ipAddress,
                hostname = requestDetails.hostname,
                powerState = requestDetails.powerState,
                guestOs = requestDetails.guestOs,
                lastSyncedAt = requestDetails.lastSyncedAt,
                bootTime = requestDetails.bootTime
            ).success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to query request details: " +
                    "requestId=${query.requestId.value}, " +
                    "tenantId=${query.tenantId.value}, " +
                    "userId=${query.userId.value}"
            }
            GetRequestDetailError.QueryFailure(
                message = "Failed to retrieve request details: ${e.message}"
            ).failure()
        }
    }
}

/**
 * Read-only repository interface for detailed VM request queries.
 *
 * This is a port (in hexagonal architecture terms) that allows the
 * application layer to query VM request details without depending on
 * specific infrastructure implementations.
 */
public interface VmRequestDetailRepository {
    /**
     * Finds a VM request by its ID.
     *
     * @param requestId The ID of the VM request
     * @return The request details or null if not found
     */
    public suspend fun findById(requestId: VmRequestId): VmRequestDetailProjection?
}

/**
 * Projection data for detailed VM request view.
 *
 * Story 3-7: Includes VM runtime details for provisioned VMs.
 */
public data class VmRequestDetailProjection(
    /** Unique identifier for the request */
    val id: VmRequestId,
    /** User who created the request (used for authorization checks) */
    val requesterId: UserId,
    /** Name of the requested virtual machine */
    val vmName: String,
    /** Size category (S, M, L, XL) */
    val size: String,
    /** Number of CPU cores */
    val cpuCores: Int,
    /** Memory allocation in gigabytes */
    val memoryGb: Int,
    /** Disk storage in gigabytes */
    val diskGb: Int,
    /** Business justification for the request */
    val justification: String,
    /** Current status (PENDING, APPROVED, REJECTED, etc.) */
    val status: String,
    /** Name of the associated project */
    val projectName: String,
    /** Display name of the requester */
    val requesterName: String,
    /** Timestamp when the request was created */
    val createdAt: Instant,
    /** VMware MoRef ID (null if not yet provisioned) */
    val vmwareVmId: String? = null,
    /** Primary IP address from VMware Tools (null if not detected) */
    val ipAddress: String? = null,
    /** Guest hostname from VMware Tools (null if not detected) */
    val hostname: String? = null,
    /** VM power state: POWERED_ON, POWERED_OFF, SUSPENDED (null if not provisioned) */
    val powerState: String? = null,
    /** Detected guest OS from VMware Tools (null if not detected) */
    val guestOs: String? = null,
    /** Timestamp of last status sync from vSphere (null if never synced) */
    val lastSyncedAt: Instant? = null,
    /** Timestamp when VM was last powered on (null if not running or not detected) */
    val bootTime: Instant? = null
)

/**
 * Read-only repository interface for timeline event queries.
 *
 * This is a port (in hexagonal architecture terms) that allows the
 * application layer to query timeline events without depending on
 * specific infrastructure implementations.
 */
public interface TimelineEventReadRepository {
    /**
     * Finds all timeline events for a specific VM request.
     *
     * @param requestId The ID of the VM request
     * @return List of timeline events sorted chronologically (oldest first)
     */
    public suspend fun findByRequestId(requestId: VmRequestId): List<TimelineEventItem>
}

/**
 * No-op implementation of TimelineEventReadRepository.
 * Returns empty lists. Used for testing handlers in isolation.
 */
public object NoOpTimelineEventReadRepository : TimelineEventReadRepository {
    override suspend fun findByRequestId(requestId: VmRequestId): List<TimelineEventItem> {
        return emptyList()
    }
}
