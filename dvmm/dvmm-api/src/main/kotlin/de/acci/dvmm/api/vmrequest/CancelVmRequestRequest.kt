package de.acci.dvmm.api.vmrequest

import jakarta.validation.constraints.Size

/**
 * Request DTO for cancelling a VM request.
 *
 * @property reason Optional reason for cancellation (max 500 characters)
 */
public data class CancelVmRequestRequest(
    @field:Size(max = 500, message = "Reason must not exceed 500 characters")
    val reason: String? = null
)
