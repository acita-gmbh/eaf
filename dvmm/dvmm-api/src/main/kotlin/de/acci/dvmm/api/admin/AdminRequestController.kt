package de.acci.dvmm.api.admin

import de.acci.dvmm.application.vmrequest.ApproveVmRequestCommand
import de.acci.dvmm.application.vmrequest.ApproveVmRequestError
import de.acci.dvmm.application.vmrequest.ApproveVmRequestHandler
import de.acci.dvmm.application.vmrequest.GetAdminRequestDetailError
import de.acci.dvmm.application.vmrequest.GetAdminRequestDetailHandler
import de.acci.dvmm.application.vmrequest.GetAdminRequestDetailQuery
import de.acci.dvmm.application.vmrequest.GetPendingRequestsError
import de.acci.dvmm.application.vmrequest.GetPendingRequestsHandler
import de.acci.dvmm.application.vmrequest.GetPendingRequestsQuery
import de.acci.dvmm.application.vmrequest.RejectVmRequestCommand
import de.acci.dvmm.application.vmrequest.RejectVmRequestError
import de.acci.dvmm.application.vmrequest.RejectVmRequestHandler
import de.acci.dvmm.application.vmrequest.VmRequestReadRepository
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.projection.PageRequest
import de.acci.eaf.tenant.TenantContext
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * REST controller for admin VM request operations.
 *
 * Story 2.9: Admin Approval Queue
 * Story 2.10: Request Detail View (Admin)
 * Story 2.11: Approve/Reject Actions
 *
 * All endpoints require ADMIN role via @PreAuthorize.
 * Tenant isolation is handled by PostgreSQL RLS.
 *
 * ## Endpoints
 *
 * - `GET /api/admin/requests/pending` - Get pending requests for approval
 * - `GET /api/admin/requests/{id}` - Get detailed view of a specific request
 * - `GET /api/admin/projects` - Get distinct projects for filter dropdown
 * - `POST /api/admin/requests/{id}/approve` - Approve a pending request
 * - `POST /api/admin/requests/{id}/reject` - Reject a pending request with reason
 *
 * ## Error Handling
 *
 * - **401 Unauthorized**: Missing or invalid JWT
 * - **403 Forbidden**: User does not have admin role
 * - **404 Not Found**: Request not found (also returned for Forbidden to prevent enumeration)
 * - **409 Conflict**: Concurrent modification (optimistic locking)
 * - **422 Unprocessable Entity**: Invalid state or invalid rejection reason
 * - **500 Internal Server Error**: Database query failure
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('admin')")
public class AdminRequestController(
    private val getPendingRequestsHandler: GetPendingRequestsHandler,
    private val getAdminRequestDetailHandler: GetAdminRequestDetailHandler,
    private val approveVmRequestHandler: ApproveVmRequestHandler,
    private val rejectVmRequestHandler: RejectVmRequestHandler,
    private val readRepository: VmRequestReadRepository
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get pending VM requests for admin approval.
     *
     * Returns a paginated list of PENDING requests from all users in the tenant,
     * sorted by creation date (oldest first - FIFO order for fair processing).
     *
     * AC 1: Admin sees "Open Requests" section
     * AC 2: List displays required columns
     * AC 3: Sorted oldest first
     * AC 5: Filter by project
     * AC 6: Tenant isolation via RLS
     *
     * @param projectId Optional project filter
     * @param page Page number (zero-based, default 0)
     * @param size Page size (default 25, max 100)
     * @param jwt The authenticated admin's JWT
     * @return 200 OK with paginated pending requests, or error response
     */
    @GetMapping("/requests/pending")
    public suspend fun getPendingRequests(
        @RequestParam(required = false) projectId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val tenantId = TenantContext.current()
        val adminId = jwt.subject

        // Validate pagination parameters
        val validatedSize = size.coerceIn(1, GetPendingRequestsQuery.MAX_PAGE_SIZE)
        val validatedPage = page.coerceAtLeast(0)

        // Parse optional project filter
        val parsedProjectId = projectId?.let {
            try {
                ProjectId(UUID.fromString(it))
            } catch (e: IllegalArgumentException) {
                logger.warn { "Invalid project ID format: $projectId" }
                return ResponseEntity.badRequest().body(
                    mapOf(
                        "error" to "INVALID_PROJECT_ID",
                        "message" to "Invalid project ID format"
                    )
                )
            }
        }

        // Audit log
        logger.info {
            "Admin fetching pending requests: " +
                "adminId=$adminId, " +
                "tenantId=${tenantId.value}, " +
                "projectId=${parsedProjectId?.value}, " +
                "page=$validatedPage, " +
                "size=$validatedSize"
        }

        val query = GetPendingRequestsQuery(
            tenantId = tenantId,
            projectId = parsedProjectId,
            pageRequest = PageRequest(page = validatedPage, size = validatedSize)
        )

        return try {
            when (val result = getPendingRequestsHandler.handle(query)) {
                is Result.Success -> {
                    ResponseEntity.ok(PendingRequestsPageResponse.fromPagedResponse(result.value))
                }
                is Result.Failure -> handleGetPendingRequestsError(result.error)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error fetching pending requests" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf("error" to "INTERNAL_ERROR", "message" to "Unexpected error")
            )
        }
    }

    private fun handleGetPendingRequestsError(error: GetPendingRequestsError): ResponseEntity<Any> {
        return when (error) {
            is GetPendingRequestsError.Forbidden -> {
                val tenantId = try { TenantContext.currentOrNull()?.value } catch (e: Exception) { "unknown" }
                logger.warn { "Forbidden access to pending requests: tenantId=$tenantId" }
                ResponseEntity.notFound().build()
            }
            is GetPendingRequestsError.QueryFailure -> {
                logger.error { "Failed to retrieve pending requests: ${error.message}" }
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    mapOf(
                        "error" to "QUERY_FAILURE",
                        "message" to "Failed to retrieve pending requests"
                    )
                )
            }
        }
    }

    /**
     * Get distinct projects for the filter dropdown.
     *
     * Returns a list of all projects that have VM requests in the tenant,
     * sorted alphabetically by name.
     *
     * AC 5: Project filter dropdown
     *
     * @param jwt The authenticated admin's JWT
     * @return 200 OK with list of projects, or error response on failure
     */
    @GetMapping("/projects")
    public suspend fun getProjects(
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val tenantId = TenantContext.current()
        val adminId = jwt.subject

        logger.debug { "Admin fetching projects: adminId=$adminId, tenantId=${tenantId.value}" }

        return try {
            val projects = readRepository.findDistinctProjects(tenantId)
            ResponseEntity.ok(projects.map { ProjectResponse.fromSummary(it) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve projects for tenant ${tenantId.value}: ${e.message}" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf(
                    "error" to "QUERY_FAILURE",
                    "message" to "Failed to retrieve projects"
                )
            )
        }
    }

    /**
     * Get detailed view of a specific VM request.
     *
     * Story 2.10: Request Detail View (Admin)
     *
     * Returns full request details including:
     * - Requester info (name, email, role) [AC 2]
     * - Request details (VM specs, justification) [AC 3]
     * - Timeline events [AC 5]
     * - Requester history (up to 5 recent requests) [AC 6]
     *
     * Security: Both NotFound and Forbidden errors return 404 to prevent
     * tenant enumeration attacks (per CLAUDE.md security pattern).
     *
     * @param id The UUID of the request to retrieve
     * @param jwt The authenticated admin's JWT
     * @return 200 OK with request details, or 404 if not found/forbidden
     */
    @GetMapping("/requests/{id}")
    public suspend fun getRequestDetail(
        @PathVariable id: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val tenantId = TenantContext.current()
        val adminId = jwt.subject

        // Parse and validate request ID
        val requestId = try {
            VmRequestId(UUID.fromString(id))
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid request ID format: $id" }
            return ResponseEntity.notFound().build()
        }

        logger.info {
            "Admin fetching request detail: " +
                "adminId=$adminId, " +
                "tenantId=${tenantId.value}, " +
                "requestId=${requestId.value}"
        }

        val query = GetAdminRequestDetailQuery(
            tenantId = tenantId,
            requestId = requestId
        )

        return when (val result = getAdminRequestDetailHandler.handle(query)) {
            is Result.Success -> {
                ResponseEntity.ok(AdminRequestDetailResponse.fromDomain(result.value))
            }
            is Result.Failure -> handleGetAdminRequestDetailError(result.error, requestId)
        }
    }

    private fun handleGetAdminRequestDetailError(
        error: GetAdminRequestDetailError,
        requestId: VmRequestId
    ): ResponseEntity<Any> {
        return when (error) {
            is GetAdminRequestDetailError.NotFound -> {
                logger.debug { "Request not found: ${requestId.value}" }
                // Return 404 for not found
                ResponseEntity.notFound().build()
            }
            is GetAdminRequestDetailError.Forbidden -> {
                // SECURITY: Return 404 instead of 403 to prevent tenant enumeration
                // Log the actual error for audit trail
                logger.warn { "Forbidden access to request ${requestId.value}" }
                ResponseEntity.notFound().build()
            }
            is GetAdminRequestDetailError.QueryFailure -> {
                logger.error { "Failed to retrieve request details: ${error.message}" }
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    mapOf(
                        "error" to "QUERY_FAILURE",
                        "message" to "Failed to retrieve request details"
                    )
                )
            }
        }
    }

    /**
     * Approve a pending VM request.
     *
     * Story 2.11: Approve/Reject Actions
     *
     * Dispatches ApproveVmRequestCommand to the handler which:
     * - Validates admin is not the requester (separation of duties)
     * - Validates request is in PENDING status
     * - Persists VmRequestApproved event with optimistic locking
     * - Updates projections for read model consistency
     *
     * @param id The UUID of the request to approve
     * @param body Request body containing expected version for optimistic locking
     * @param jwt The authenticated admin's JWT
     * @return 200 OK on success, or appropriate error response
     */
    @PostMapping("/requests/{id}/approve")
    public suspend fun approveRequest(
        @PathVariable id: String,
        @Valid @RequestBody body: ApproveRequestBody,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val tenantId = TenantContext.current()
        val adminId = UserId.fromString(jwt.subject)

        // Parse and validate request ID
        val requestId = try {
            VmRequestId(UUID.fromString(id))
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid request ID format: $id" }
            return ResponseEntity.notFound().build()
        }

        logger.info {
            "Admin action: tenant=${tenantId.value}, " +
                "admin=${adminId.value}, " +
                "action=APPROVE, " +
                "requestId=${requestId.value}"
        }

        val command = ApproveVmRequestCommand(
            tenantId = tenantId,
            requestId = requestId,
            adminId = adminId,
            version = body.version
        )

        return when (val result = approveVmRequestHandler.handle(command)) {
            is Result.Success -> {
                ResponseEntity.ok(
                    mapOf(
                        "requestId" to result.value.requestId.value.toString(),
                        "status" to "APPROVED"
                    )
                )
            }
            is Result.Failure -> handleApproveError(result.error, requestId)
        }
    }

    private fun handleApproveError(
        error: ApproveVmRequestError,
        requestId: VmRequestId
    ): ResponseEntity<Any> {
        return when (error) {
            is ApproveVmRequestError.NotFound,
            is ApproveVmRequestError.Forbidden -> {
                // SECURITY: Return 404 for both NotFound and Forbidden to prevent enumeration
                if (error is ApproveVmRequestError.Forbidden) {
                    logger.warn { "Forbidden: Admin tried to approve own request ${requestId.value}" }
                } else {
                    logger.debug { "Request not found: ${requestId.value}" }
                }
                ResponseEntity.notFound().build()
            }
            is ApproveVmRequestError.InvalidState -> {
                logger.debug { "Invalid state for approval: ${error.currentState}" }
                ResponseEntity.unprocessableEntity().body(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        error.message
                    ).apply {
                        type = URI("/errors/invalid-state")
                    }
                )
            }
            is ApproveVmRequestError.ConcurrencyConflict -> {
                logger.info { "Concurrency conflict for request ${requestId.value}: ${error.message}" }
                ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT,
                        "Request was modified by another admin"
                    ).apply {
                        type = URI("/errors/conflict")
                    }
                )
            }
            is ApproveVmRequestError.PersistenceFailure -> {
                logger.error { "Persistence failure during approval: ${error.message}" }
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to process approval request"
                    ).apply {
                        type = URI("/errors/internal-error")
                    }
                )
            }
        }
    }

    /**
     * Reject a pending VM request with a mandatory reason.
     *
     * Story 2.11: Approve/Reject Actions
     *
     * Dispatches RejectVmRequestCommand to the handler which:
     * - Validates admin is not the requester (separation of duties)
     * - Validates request is in PENDING status
     * - Validates rejection reason (10-500 characters)
     * - Persists VmRequestRejected event with optimistic locking
     * - Updates projections for read model consistency
     *
     * @param id The UUID of the request to reject
     * @param body Request body containing expected version and rejection reason
     * @param jwt The authenticated admin's JWT
     * @return 200 OK on success, or appropriate error response
     */
    @PostMapping("/requests/{id}/reject")
    public suspend fun rejectRequest(
        @PathVariable id: String,
        @Valid @RequestBody body: RejectRequestBody,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val tenantId = TenantContext.current()
        val adminId = UserId.fromString(jwt.subject)

        // Parse and validate request ID
        val requestId = try {
            VmRequestId(UUID.fromString(id))
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid request ID format: $id" }
            return ResponseEntity.notFound().build()
        }

        logger.info {
            "Admin action: tenant=${tenantId.value}, " +
                "admin=${adminId.value}, " +
                "action=REJECT, " +
                "requestId=${requestId.value}"
        }

        val command = RejectVmRequestCommand(
            tenantId = tenantId,
            requestId = requestId,
            adminId = adminId,
            reason = body.reason,
            version = body.version
        )

        return when (val result = rejectVmRequestHandler.handle(command)) {
            is Result.Success -> {
                ResponseEntity.ok(
                    mapOf(
                        "requestId" to result.value.requestId.value.toString(),
                        "status" to "REJECTED"
                    )
                )
            }
            is Result.Failure -> handleRejectError(result.error, requestId)
        }
    }

    private fun handleRejectError(
        error: RejectVmRequestError,
        requestId: VmRequestId
    ): ResponseEntity<Any> {
        return when (error) {
            is RejectVmRequestError.NotFound,
            is RejectVmRequestError.Forbidden -> {
                // SECURITY: Return 404 for both NotFound and Forbidden to prevent enumeration
                if (error is RejectVmRequestError.Forbidden) {
                    logger.warn { "Forbidden: Admin tried to reject own request ${requestId.value}" }
                } else {
                    logger.debug { "Request not found: ${requestId.value}" }
                }
                ResponseEntity.notFound().build()
            }
            is RejectVmRequestError.InvalidState -> {
                logger.debug { "Invalid state for rejection: ${error.currentState}" }
                ResponseEntity.unprocessableEntity().body(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        error.message
                    ).apply {
                        type = URI("/errors/invalid-state")
                    }
                )
            }
            is RejectVmRequestError.InvalidReason -> {
                logger.debug { "Invalid rejection reason: ${error.message}" }
                ResponseEntity.unprocessableEntity().body(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        error.message
                    ).apply {
                        type = URI("/errors/invalid-reason")
                    }
                )
            }
            is RejectVmRequestError.ConcurrencyConflict -> {
                logger.info { "Concurrency conflict for request ${requestId.value}: ${error.message}" }
                ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT,
                        "Request was modified by another admin"
                    ).apply {
                        type = URI("/errors/conflict")
                    }
                )
            }
            is RejectVmRequestError.PersistenceFailure -> {
                logger.error { "Persistence failure during rejection: ${error.message}" }
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to process rejection request"
                    ).apply {
                        type = URI("/errors/internal-error")
                    }
                )
            }
        }
    }
}
