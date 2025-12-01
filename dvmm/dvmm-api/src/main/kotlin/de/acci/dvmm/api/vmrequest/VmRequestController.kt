package de.acci.dvmm.api.vmrequest

import de.acci.dvmm.application.vmrequest.CreateVmRequestCommand
import de.acci.dvmm.application.vmrequest.CreateVmRequestError
import de.acci.dvmm.application.vmrequest.CreateVmRequestHandler
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.tenant.TenantContext
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.validation.FieldError
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.support.WebExchangeBindException
import java.net.URI
import java.time.Instant

/**
 * REST controller for VM request operations.
 *
 * Handles VM request creation and returns appropriate HTTP responses.
 * All endpoints require authentication and tenant context.
 *
 * ## Endpoints
 *
 * - `POST /api/requests` - Create a new VM request
 *
 * ## Error Handling
 *
 * - **400 Bad Request**: Validation errors (field-level)
 * - **401 Unauthorized**: Missing or invalid JWT
 * - **403 Forbidden**: Cross-tenant access attempt
 * - **409 Conflict**: Quota exceeded or concurrency conflict
 */
@RestController
@RequestMapping("/api/requests")
public class VmRequestController(
    private val createVmRequestHandler: CreateVmRequestHandler
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
            is Result.Failure -> handleError(result.error)
        }
    }

    private fun handleError(error: CreateVmRequestError): ResponseEntity<Any> {
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
}
