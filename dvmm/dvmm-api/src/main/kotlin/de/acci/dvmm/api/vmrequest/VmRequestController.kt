package de.acci.dvmm.api.vmrequest

import de.acci.dvmm.application.vmrequest.CancelVmRequestCommand
import de.acci.dvmm.application.vmrequest.CancelVmRequestError
import de.acci.dvmm.application.vmrequest.CancelVmRequestHandler
import de.acci.dvmm.application.vmrequest.CreateVmRequestCommand
import de.acci.dvmm.application.vmrequest.CreateVmRequestError
import de.acci.dvmm.application.vmrequest.CreateVmRequestHandler
import de.acci.dvmm.application.vmrequest.GetMyRequestsError
import de.acci.dvmm.application.vmrequest.GetMyRequestsHandler
import de.acci.dvmm.application.vmrequest.GetMyRequestsQuery
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.projection.PageRequest
import de.acci.eaf.tenant.TenantContext
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
import java.time.Instant

/**
 * REST controller for VM request operations.
 *
 * Handles VM request creation, listing, and cancellation.
 * All endpoints require authentication and tenant context.
 *
 * ## Endpoints
 *
 * - `POST /api/requests` - Create a new VM request
 * - `GET /api/requests/my` - Get current user's requests (paginated)
 * - `POST /api/requests/{id}/cancel` - Cancel a pending request
 *
 * ## Error Handling
 *
 * - **400 Bad Request**: Validation errors (field-level)
 * - **401 Unauthorized**: Missing or invalid JWT
 * - **403 Forbidden**: Cross-tenant access attempt or not owner
 * - **404 Not Found**: Request not found
 * - **409 Conflict**: Quota exceeded, concurrency conflict, or invalid state
 */
@RestController
@RequestMapping("/api/requests")
public class VmRequestController(
    private val createVmRequestHandler: CreateVmRequestHandler,
    private val getMyRequestsHandler: GetMyRequestsHandler,
    private val cancelVmRequestHandler: CancelVmRequestHandler
) {

    /**
     * Create a new VM request.
     *
     * @param request The VM request data
     * @param jwt The authenticated user's JWT
     * @return 201 Created with the created request, or error response
     */
    @PostMapping
    public suspend fun createVmRequest(
        @Valid @RequestBody request: CreateVmRequestRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        // Extract user context from JWT
        val tenantId = TenantContext.current()
        val userId = UserId.fromString(jwt.subject)

        // Convert request to domain types
        val vmName = VmName.create(request.vmName).getOrElse {
            return ResponseEntity.badRequest().body(
                ValidationErrorResponse(
                    errors = listOf(
                        ValidationErrorResponse.FieldError(
                            field = "vmName",
                            message = it.message ?: "Invalid VM name"
                        )
                    )
                )
            )
        }

        val size = VmSize.fromCode(request.size).getOrElse {
            return ResponseEntity.badRequest().body(
                ValidationErrorResponse(
                    errors = listOf(
                        ValidationErrorResponse.FieldError(
                            field = "size",
                            message = it.message ?: "Invalid size"
                        )
                    )
                )
            )
        }

        val projectId = ProjectId.fromString(request.projectId)

        // Create command and execute
        val command = CreateVmRequestCommand(
            tenantId = tenantId,
            requesterId = userId,
            projectId = projectId,
            vmName = vmName,
            size = size,
            justification = request.justification
        )

        return when (val result = createVmRequestHandler.handle(command)) {
            is Result.Success -> {
                val response = VmRequestResponse.created(
                    requestId = result.value.requestId,
                    vmName = request.vmName,
                    projectId = request.projectId,
                    size = size,
                    createdAt = Instant.now()
                )
                ResponseEntity
                    .created(URI.create("/api/requests/${result.value.requestId.value}"))
                    .body(response)
            }
            is Result.Failure -> handleCreateError(result.error)
        }
    }

    private fun handleCreateError(error: CreateVmRequestError): ResponseEntity<Any> {
        return when (error) {
            is CreateVmRequestError.QuotaExceeded -> {
                ResponseEntity.status(HttpStatus.CONFLICT).body(
                    QuotaExceededResponse(
                        message = error.message,
                        available = error.available,
                        requested = error.requested
                    )
                )
            }
            is CreateVmRequestError.ConcurrencyConflict -> {
                ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ConcurrencyConflictResponse(message = error.message)
                )
            }
            is CreateVmRequestError.PersistenceFailure -> {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    InternalErrorResponse(message = "Failed to persist request")
                )
            }
        }
    }

    /**
     * Get the current user's VM requests.
     *
     * Returns a paginated list of requests submitted by the authenticated user,
     * ordered by creation date (newest first).
     *
     * @param page Page number (zero-based, default 0)
     * @param size Page size (default 20, max 100)
     * @param jwt The authenticated user's JWT
     * @return 200 OK with paginated requests, or error response
     */
    @GetMapping("/my")
    public suspend fun getMyRequests(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val tenantId = TenantContext.current()
        val userId = UserId.fromString(jwt.subject)

        // Validate pagination parameters
        val validatedSize = size.coerceIn(1, 100)
        val validatedPage = page.coerceAtLeast(0)

        val query = GetMyRequestsQuery(
            tenantId = tenantId,
            userId = userId,
            pageRequest = PageRequest(page = validatedPage, size = validatedSize)
        )

        return when (val result = getMyRequestsHandler.handle(query)) {
            is Result.Success -> {
                ResponseEntity.ok(PagedVmRequestsResponse.fromDomain(result.value))
            }
            is Result.Failure -> handleGetMyRequestsError(result.error)
        }
    }

    private fun handleGetMyRequestsError(error: GetMyRequestsError): ResponseEntity<Any> {
        return when (error) {
            is GetMyRequestsError.QueryFailure -> {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    InternalErrorResponse(message = "Failed to retrieve requests")
                )
            }
        }
    }

    /**
     * Cancel a pending VM request.
     *
     * Only the original requester can cancel their own request,
     * and only while it is still in PENDING status.
     *
     * @param id The ID of the request to cancel
     * @param request Optional cancellation reason
     * @param jwt The authenticated user's JWT
     * @return 200 OK on success, or appropriate error response
     */
    @PostMapping("/{id}/cancel")
    public suspend fun cancelRequest(
        @PathVariable id: String,
        @Valid @RequestBody(required = false) request: CancelVmRequestRequest?,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val tenantId = TenantContext.current()
        val userId = UserId.fromString(jwt.subject)

        val requestId = try {
            VmRequestId.fromString(id)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(
                ValidationErrorResponse(
                    errors = listOf(
                        ValidationErrorResponse.FieldError(
                            field = "id",
                            message = "Invalid request ID format"
                        )
                    )
                )
            )
        }

        val command = CancelVmRequestCommand(
            tenantId = tenantId,
            requestId = requestId,
            userId = userId,
            reason = request?.reason
        )

        return when (val result = cancelVmRequestHandler.handle(command)) {
            is Result.Success -> {
                ResponseEntity.ok().body(
                    CancelSuccessResponse(
                        message = "Request cancelled successfully",
                        requestId = result.value.requestId.value.toString()
                    )
                )
            }
            is Result.Failure -> handleCancelError(result.error)
        }
    }

    private fun handleCancelError(error: CancelVmRequestError): ResponseEntity<Any> {
        return when (error) {
            is CancelVmRequestError.NotFound -> {
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    NotFoundResponse(message = error.message)
                )
            }
            is CancelVmRequestError.Forbidden -> {
                ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    ForbiddenResponse(message = error.message)
                )
            }
            is CancelVmRequestError.InvalidState -> {
                ResponseEntity.status(HttpStatus.CONFLICT).body(
                    InvalidStateResponse(
                        message = error.message,
                        currentState = error.currentState
                    )
                )
            }
            is CancelVmRequestError.ConcurrencyConflict -> {
                ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ConcurrencyConflictResponse(message = error.message)
                )
            }
            is CancelVmRequestError.PersistenceFailure -> {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    InternalErrorResponse(message = "Failed to cancel request")
                )
            }
        }
    }
}
