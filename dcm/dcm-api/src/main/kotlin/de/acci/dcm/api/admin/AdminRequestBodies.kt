package de.acci.dcm.api.admin

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Request body for approving a VM request.
 *
 * @property version Expected aggregate version for optimistic locking.
 *                   Prevents lost updates when multiple admins act on the same request.
 */
public data class ApproveRequestBody(
    val version: Long
)

/**
 * Request body for rejecting a VM request.
 *
 * @property version Expected aggregate version for optimistic locking.
 * @property reason Mandatory rejection reason explaining why the request was denied.
 *                  Must be 10-500 characters.
 */
public data class RejectRequestBody(
    val version: Long,
    @field:NotBlank(message = "Reason is required")
    @field:Size(min = 10, max = 500, message = "Reason must be 10-500 characters")
    val reason: String
)
