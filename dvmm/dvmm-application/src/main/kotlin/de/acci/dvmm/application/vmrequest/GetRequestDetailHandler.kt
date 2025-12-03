package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant

/**
 * Query to retrieve detailed VM request information with timeline.
 *
 * @param tenantId The tenant context for RLS
 * @param requestId The ID of the VM request to retrieve
 * @param userId The user making the request (for authorization)
 */
public data class GetRequestDetailQuery(
    val tenantId: TenantId,
    val requestId: VmRequestId,
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
        val requestId: VmRequestId,
        val message: String = "VM request not found: ${requestId.value}"
    ) : GetRequestDetailError()

    /**
     * User is not authorized to view this request.
     * Only the original requester can view their request details.
     */
    public data class Forbidden(
        val message: String = "Not authorized to view this request"
    ) : GetRequestDetailError()

    /**
     * Unexpected failure when querying the read model.
     */
    public data class QueryFailure(
        val message: String
    ) : GetRequestDetailError()
}

/**
 * A single timeline event for display.
 *
 * @param eventType Type of event (CREATED, APPROVED, REJECTED, CANCELLED, etc.)
 * @param actorName Display name of the user who performed the action (null for system events)
 * @param details Additional event details (e.g., rejection reason)
 * @param occurredAt When the event occurred
 */
public data class TimelineEventItem(
    val eventType: TimelineEventType,
    val actorName: String?,
    val details: String?,
    val occurredAt: Instant
)

/**
 * Detailed VM request information with timeline.
 *
 * @param id Unique identifier for the request
 * @param vmName Name of the requested VM
 * @param size Size tier (SMALL, MEDIUM, LARGE)
 * @param cpuCores Number of CPU cores
 * @param memoryGb RAM in gigabytes
 * @param diskGb Disk space in gigabytes
 * @param justification Business justification for the request
 * @param status Current status of the request
 * @param projectName Name of the project this VM belongs to
 * @param requesterName Name of the user who submitted the request
 * @param createdAt When the request was submitted
 * @param timeline List of timeline events in chronological order
 */
public data class VmRequestDetail(
    val id: VmRequestId,
    val vmName: String,
    val size: String,
    val cpuCores: Int,
    val memoryGb: Int,
    val diskGb: Int,
    val justification: String,
    val status: String,
    val projectName: String,
    val requesterName: String,
    val createdAt: Instant,
    val timeline: List<TimelineEventItem>
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
                timeline = timelineEvents
            ).success()
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
 * @property id Unique identifier for the request
 * @property requesterId User who created the request (used for authorization checks)
 * @property vmName Name of the requested virtual machine
 * @property size Size category (S, M, L, XL)
 * @property cpuCores Number of CPU cores
 * @property memoryGb Memory allocation in gigabytes
 * @property diskGb Disk storage in gigabytes
 * @property justification Business justification for the request
 * @property status Current status (PENDING, APPROVED, REJECTED, etc.)
 * @property projectName Name of the associated project
 * @property requesterName Display name of the requester
 * @property createdAt Timestamp when the request was created
 */
public data class VmRequestDetailProjection(
    val id: VmRequestId,
    val requesterId: UserId,
    val vmName: String,
    val size: String,
    val cpuCores: Int,
    val memoryGb: Int,
    val diskGb: Int,
    val justification: String,
    val status: String,
    val projectName: String,
    val requesterName: String,
    val createdAt: Instant
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
