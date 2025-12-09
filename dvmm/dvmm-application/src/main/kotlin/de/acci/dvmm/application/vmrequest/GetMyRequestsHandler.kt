package de.acci.dvmm.application.vmrequest

import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.eventsourcing.projection.PagedResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.coroutines.cancellation.CancellationException

/**
 * Errors that can occur when retrieving user's requests.
 */
public sealed class GetMyRequestsError {
    /**
     * Unexpected failure when querying the read model.
     */
    public data class QueryFailure(
        val message: String
    ) : GetMyRequestsError()
}

/**
 * Handler for GetMyRequestsQuery.
 *
 * Retrieves a paginated list of VM requests submitted by the current user.
 * Delegates to the read repository for actual data retrieval.
 *
 * ## Usage
 *
 * ```kotlin
 * val handler = GetMyRequestsHandler(readRepository)
 * val result = handler.handle(query)
 * result.fold(
 *     onSuccess = { response ->
 *         response.items.forEach { println("Request: ${it.vmName} - ${it.status}") }
 *     },
 *     onFailure = { println("Failed: $it") }
 * )
 * ```
 */
public class GetMyRequestsHandler(
    private val readRepository: VmRequestReadRepository
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle the get my requests query.
     *
     * @param query The query to process
     * @return Result containing paginated VM request summaries or an error
     */
    public suspend fun handle(
        query: GetMyRequestsQuery
    ): Result<PagedResponse<VmRequestSummary>, GetMyRequestsError> {
        return try {
            val response = readRepository.findByRequesterId(
                requesterId = query.userId,
                pageRequest = query.pageRequest
            )
            response.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to query user requests: " +
                    "userId=${query.userId.value}, " +
                    "tenantId=${query.tenantId.value}, " +
                    "page=${query.pageRequest.page}, " +
                    "size=${query.pageRequest.size}"
            }
            GetMyRequestsError.QueryFailure(
                message = "Failed to retrieve requests: ${e.message}"
            ).failure()
        }
    }
}
