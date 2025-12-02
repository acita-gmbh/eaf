package de.acci.dvmm.application.vmrequest

import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.eventsourcing.projection.PagedResponse
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Errors that can occur when retrieving pending requests for admin.
 */
public sealed class GetPendingRequestsError {
    /**
     * User lacks admin privileges to view pending requests.
     * Note: Role check is primarily done at controller level with @PreAuthorize,
     * but this error type exists for defense-in-depth.
     */
    public data object Forbidden : GetPendingRequestsError()

    /**
     * Unexpected failure when querying the read model.
     */
    public data class QueryFailure(
        val message: String
    ) : GetPendingRequestsError()
}

/**
 * Handler for GetPendingRequestsQuery.
 *
 * Retrieves a paginated list of pending VM requests for admin review.
 * Delegates to the read repository for actual data retrieval.
 *
 * Story 2.9: Admin Approval Queue (AC 1, 2, 3, 5, 6)
 *
 * ## Usage
 *
 * ```kotlin
 * val handler = GetPendingRequestsHandler(readRepository)
 * val result = handler.handle(query)
 * result.fold(
 *     onSuccess = { response ->
 *         response.items.forEach { println("Request: ${it.vmName} by ${it.requesterName}") }
 *     },
 *     onFailure = { println("Failed: $it") }
 * )
 * ```
 */
public class GetPendingRequestsHandler(
    private val readRepository: VmRequestReadRepository
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle the get pending requests query.
     *
     * @param query The query to process
     * @return Result containing paginated pending VM request summaries or an error
     */
    public suspend fun handle(
        query: GetPendingRequestsQuery
    ): Result<PagedResponse<VmRequestSummary>, GetPendingRequestsError> {
        return try {
            val response = readRepository.findPendingByTenantId(
                tenantId = query.tenantId,
                projectId = query.projectId,
                pageRequest = query.pageRequest
            )

            logger.debug {
                "Retrieved ${response.items.size} pending requests: " +
                    "tenantId=${query.tenantId.value}, " +
                    "projectId=${query.projectId?.value}, " +
                    "page=${query.pageRequest.page}, " +
                    "size=${query.pageRequest.size}, " +
                    "total=${response.totalElements}"
            }

            response.success()
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to query pending requests: " +
                    "tenantId=${query.tenantId.value}, " +
                    "projectId=${query.projectId?.value}, " +
                    "page=${query.pageRequest.page}, " +
                    "size=${query.pageRequest.size}"
            }
            GetPendingRequestsError.QueryFailure(
                message = "Failed to retrieve pending requests: ${e.message}"
            ).failure()
        }
    }
}
