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
 * Query to retrieve detailed VM request information for admin view.
 *
 * Story 2.10: Request Detail View (Admin)
 *
 * Unlike GetRequestDetailQuery, this does not include userId because
 * admin role authorization is handled at the controller level via @PreAuthorize.
 *
 * @param tenantId The tenant context for RLS
 * @param requestId The ID of the VM request to retrieve
 */
public data class GetAdminRequestDetailQuery(
    val tenantId: TenantId,
    val requestId: VmRequestId
)

/**
 * Errors that can occur when retrieving admin request details.
 *
 * Per CLAUDE.md Architecture Rules: Detail query handlers MUST have Forbidden error type.
 * At the controller level, both NotFound and Forbidden map to 404 to prevent tenant enumeration.
 */
public sealed class GetAdminRequestDetailError {
    /**
     * Request not found (doesn't exist in tenant).
     */
    public data object NotFound : GetAdminRequestDetailError()

    /**
     * User is not authorized to view this request.
     * Note: Admin role check is at controller level, but this exists for defense-in-depth.
     */
    public data class Forbidden(
        val message: String = "Not authorized to view this request"
    ) : GetAdminRequestDetailError()

    /**
     * Unexpected failure when querying the read model.
     */
    public data class QueryFailure(
        val message: String
    ) : GetAdminRequestDetailError()
}

/**
 * Requester information for admin view.
 *
 * AC 2: Displays Name, Email, Role
 */
public data class RequesterInfo(
    val id: UserId,
    val name: String,
    val email: String,
    val role: String
)

/**
 * VM size specifications.
 */
public data class VmSizeInfo(
    val code: String,
    val cpuCores: Int,
    val memoryGb: Int,
    val diskGb: Int
)

/**
 * Summary of a requester's previous request for history display.
 *
 * AC 6: Shows VM Name, Status, Date
 */
public data class VmRequestHistorySummary(
    val id: VmRequestId,
    val vmName: String,
    val status: String,
    val createdAt: Instant
)

/**
 * Full admin request detail response.
 *
 * Story 2.10: Request Detail View (Admin)
 * Story 2.11: Approve/Reject Actions (version field for optimistic locking)
 *
 * Includes all information needed for admin decision-making:
 * - Request details (AC 3)
 * - Requester info (AC 2)
 * - Timeline events (AC 5)
 * - Requester history (AC 6)
 * - Version for optimistic locking (Story 2.11)
 */
public data class AdminRequestDetail(
    val id: VmRequestId,
    val vmName: String,
    val size: VmSizeInfo,
    val justification: String,
    val status: String,
    val projectName: String,
    val requester: RequesterInfo,
    val timeline: List<TimelineEventItem>,
    val requesterHistory: List<VmRequestHistorySummary>,
    val createdAt: Instant,
    val version: Long
)

/**
 * Projection data for admin request detail view.
 *
 * Includes requester information not present in end-user view.
 *
 * @property version Aggregate version for optimistic locking (Story 2.11)
 */
public data class AdminRequestDetailProjection(
    val id: VmRequestId,
    val vmName: String,
    val size: String,
    val cpuCores: Int,
    val memoryGb: Int,
    val diskGb: Int,
    val justification: String,
    val status: String,
    val projectName: String,
    val requesterId: UserId,
    val requesterName: String,
    val requesterEmail: String,
    val requesterRole: String,
    val createdAt: Instant,
    val version: Long
)

/**
 * Read-only repository interface for admin request detail queries.
 *
 * This is a port (in hexagonal architecture terms) that allows the
 * application layer to query admin-specific request details.
 *
 * RLS NOTE: Implementations should ensure tenant isolation is applied.
 */
public interface AdminRequestDetailRepository {
    /**
     * Finds a VM request by its ID with full admin details.
     *
     * @param requestId The ID of the VM request
     * @return The projection with admin-specific fields or null if not found
     */
    public suspend fun findById(requestId: VmRequestId): AdminRequestDetailProjection?

    /**
     * Finds recent requests by the same requester (for context).
     *
     * AC 6: Up to 5 recent requests excluding current request
     *
     * @param requesterId The ID of the requester
     * @param excludeRequestId The current request ID to exclude
     * @param limit Maximum number of results (default 5)
     * @return List of recent request summaries sorted by date descending
     */
    public suspend fun findRecentByRequesterId(
        requesterId: UserId,
        excludeRequestId: VmRequestId,
        limit: Int = 5
    ): List<VmRequestHistorySummary>
}

/**
 * Handler for GetAdminRequestDetailQuery.
 *
 * Story 2.10: Request Detail View (Admin)
 *
 * Retrieves detailed VM request information for admin decision-making.
 * Unlike GetRequestDetailHandler, this:
 * - Does NOT check requester authorization (admin can view all in tenant)
 * - Includes requester info (name, email, role)
 * - Includes requester history (up to 5 recent requests)
 *
 * Authorization is enforced at controller level via @PreAuthorize("hasRole('admin')").
 *
 * ## Usage
 *
 * ```kotlin
 * val handler = GetAdminRequestDetailHandler(requestRepository, timelineRepository)
 * val result = handler.handle(query)
 * result.fold(
 *     onSuccess = { detail ->
 *         println("Request: ${detail.vmName}")
 *         println("Requester: ${detail.requester.name} (${detail.requester.email})")
 *         println("History: ${detail.requesterHistory.size} previous requests")
 *     },
 *     onFailure = { println("Failed: $it") }
 * )
 * ```
 */
public class GetAdminRequestDetailHandler(
    private val requestRepository: AdminRequestDetailRepository,
    private val timelineRepository: TimelineEventReadRepository
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle the get admin request detail query.
     *
     * @param query The query to process
     * @return Result containing detailed admin request info or an error
     */
    public suspend fun handle(
        query: GetAdminRequestDetailQuery
    ): Result<AdminRequestDetail, GetAdminRequestDetailError> {
        return try {
            // Find the request (RLS handles tenant filtering)
            val requestDetails = requestRepository.findById(query.requestId)
                ?: return GetAdminRequestDetailError.NotFound.failure()

            // Fetch timeline events
            val timelineEvents = timelineRepository.findByRequestId(query.requestId)

            // Fetch requester's recent requests for context (AC 6)
            val requesterHistory = requestRepository.findRecentByRequesterId(
                requesterId = requestDetails.requesterId,
                excludeRequestId = query.requestId,
                limit = 5
            )

            // Combine into response
            AdminRequestDetail(
                id = requestDetails.id,
                vmName = requestDetails.vmName,
                size = VmSizeInfo(
                    code = requestDetails.size,
                    cpuCores = requestDetails.cpuCores,
                    memoryGb = requestDetails.memoryGb,
                    diskGb = requestDetails.diskGb
                ),
                justification = requestDetails.justification,
                status = requestDetails.status,
                projectName = requestDetails.projectName,
                requester = RequesterInfo(
                    id = requestDetails.requesterId,
                    name = requestDetails.requesterName,
                    email = requestDetails.requesterEmail,
                    role = requestDetails.requesterRole
                ),
                timeline = timelineEvents,
                requesterHistory = requesterHistory,
                createdAt = requestDetails.createdAt,
                version = requestDetails.version
            ).success()
        } catch (e: CancellationException) {
            // Propagate coroutine cancellation for proper structured concurrency
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to query admin request details: " +
                    "requestId=${query.requestId.value}, " +
                    "tenantId=${query.tenantId.value}"
            }
            GetAdminRequestDetailError.QueryFailure(
                message = "Failed to retrieve request details: ${e.message}"
            ).failure()
        }
    }
}
