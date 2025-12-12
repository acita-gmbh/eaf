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
import de.acci.dvmm.application.vmrequest.GetRequestDetailError
import de.acci.dvmm.application.vmrequest.GetRequestDetailHandler
import de.acci.dvmm.application.vmrequest.GetRequestDetailQuery
import de.acci.dvmm.application.vmrequest.SyncVmStatusCommand
import de.acci.dvmm.application.vmrequest.SyncVmStatusError
import de.acci.dvmm.application.vmrequest.SyncVmStatusHandler
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.eaf.core.error.InvalidIdentifierFormatException
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.projection.PageRequest
import de.acci.eaf.tenant.TenantContext
import io.github.oshai.kotlinlogging.KotlinLogging
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
 * - `GET /api/requests/{id}` - Get detailed request with timeline
 * - `POST /api/requests/{id}/cancel` - Cancel a pending request
 *
 * ## Error Handling
 *
 * - **400 Bad Request**: Validation errors (field-level)
 * - **401 Unauthorized**: Missing or invalid JWT
 * - **403 Forbidden**: Cross-tenant access attempt or not owner (returns 404 to prevent enumeration)
 * - **404 Not Found**: Request not found
 * - **409 Conflict**: Quota exceeded, concurrency conflict, or invalid state
 */
@RestController
@RequestMapping("/api/requests")
public class VmRequestController(
    private val createVmRequestHandler: CreateVmRequestHandler,
    private val getMyRequestsHandler: GetMyRequestsHandler,
    private val getRequestDetailHandler: GetRequestDetailHandler,
    private val cancelVmRequestHandler: CancelVmRequestHandler,
    private val syncVmStatusHandler: SyncVmStatusHandler
) {
    private val logger = KotlinLogging.logger {}

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
        val requesterEmail = jwt.getClaimAsString("email") ?: ""

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
            requesterEmail = requesterEmail,
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
     * Get detailed information about a specific VM request with timeline.
     *
     * Returns the full request details including all timeline events
     * in chronological order (oldest first).
     *
     * @param id The ID of the request to retrieve
     * @param jwt The authenticated user's JWT
     * @return 200 OK with request detail, or error response
     */
    @GetMapping("/{id}")
    public suspend fun getRequestDetail(
        @PathVariable id: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val tenantId = TenantContext.current()
        val userId = UserId.fromString(jwt.subject)

        val requestId = try {
            VmRequestId.fromString(id)
        } catch (e: InvalidIdentifierFormatException) {
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

        val query = GetRequestDetailQuery(
            tenantId = tenantId,
            requestId = requestId,
            userId = userId
        )

        return when (val result = getRequestDetailHandler.handle(query)) {
            is Result.Success -> {
                ResponseEntity.ok(VmRequestDetailResponse.fromDomain(result.value))
            }
            is Result.Failure -> handleGetRequestDetailError(result.error)
        }
    }

    private fun handleGetRequestDetailError(error: GetRequestDetailError): ResponseEntity<Any> {
        return when (error) {
            is GetRequestDetailError.NotFound -> {
                logger.info { "Request not found: ${error.requestId.value}" }
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    NotFoundResponse(message = error.message)
                )
            }
            is GetRequestDetailError.Forbidden -> {
                logger.warn { "Forbidden access to request: ${error.message}" }
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    NotFoundResponse(message = "VM request not found")
                )
            }
            is GetRequestDetailError.QueryFailure -> {
                logger.error { "Failed to retrieve request details: ${error.message}" }
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    InternalErrorResponse(message = "Failed to retrieve request details")
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
        } catch (e: InvalidIdentifierFormatException) {
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
                logger.warn { "Forbidden cancel attempt: ${error.message}" }
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    NotFoundResponse(message = "VM request not found")
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

    /**
     * Sync VM status from vSphere.
     *
     * Refreshes the VM runtime details (power state, IP address, hostname, guest OS)
     * by querying vSphere and updating the projection.
     *
     * Story 3-7: Users can manually refresh VM status when viewing their provisioned VMs.
     *
     * @param id The ID of the request to sync
     * @param jwt The authenticated user's JWT
     * @return 200 OK with synced status, or appropriate error response
     */
    @PostMapping("/{id}/sync-status")
    public suspend fun syncVmStatus(
        @PathVariable id: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val tenantId = TenantContext.current()
        val userId = UserId.fromString(jwt.subject)

        val requestId = try {
            VmRequestId.fromString(id)
        } catch (e: InvalidIdentifierFormatException) {
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

        val command = SyncVmStatusCommand(
            tenantId = tenantId,
            requestId = requestId,
            userId = userId
        )

        return when (val result = syncVmStatusHandler.handle(command)) {
            is Result.Success -> {
                ResponseEntity.ok(
                    SyncVmStatusResponse(
                        requestId = result.value.requestId.value.toString(),
                        powerState = result.value.powerState,
                        ipAddress = result.value.ipAddress,
                        message = "VM status synced successfully"
                    )
                )
            }
            is Result.Failure -> handleSyncError(result.error)
        }
    }

    private fun handleSyncError(error: SyncVmStatusError): ResponseEntity<Any> {
        return when (error) {
            is SyncVmStatusError.NotFound -> {
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    NotFoundResponse(message = error.message)
                )
            }
            is SyncVmStatusError.Forbidden -> {
                // Return 404 to prevent resource enumeration (per security guidelines)
                logger.warn { "User not authorized to sync VM status: ${error.requestId.value}" }
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    NotFoundResponse(message = "VM request not found")
                )
            }
            is SyncVmStatusError.NotProvisioned -> {
                // Return 409 Conflict - VM not yet ready for status sync
                ResponseEntity.status(HttpStatus.CONFLICT).body(
                    SyncNotProvisionedResponse(
                        message = error.message,
                        requestId = error.requestId.value.toString()
                    )
                )
            }
            is SyncVmStatusError.HypervisorError -> {
                // Return 502 Bad Gateway - upstream vSphere error
                ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                    HypervisorErrorResponse(message = error.message)
                )
            }
            is SyncVmStatusError.UpdateFailure -> {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    InternalErrorResponse(message = "Failed to update VM status")
                )
            }
        }
    }
}
