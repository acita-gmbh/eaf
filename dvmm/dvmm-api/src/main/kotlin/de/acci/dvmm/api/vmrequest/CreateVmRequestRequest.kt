package de.acci.dvmm.api.vmrequest

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Request DTO for creating a new VM request.
 *
 * Uses Jakarta Bean Validation annotations for input validation.
 * Invalid requests will be rejected with HTTP 400 Bad Request.
 *
 * @property vmName VM name (3-63 chars, lowercase alphanumeric + hyphens)
 * @property projectId UUID of the project this VM belongs to
 * @property size VM size code (S, M, L, XL)
 * @property justification Business justification (minimum 10 characters)
 */
public data class CreateVmRequestRequest(
    @field:NotBlank(message = "VM name is required")
    @field:Size(min = 3, max = 63, message = "VM name must be between 3 and 63 characters")
    @field:Pattern(
        regexp = "^(?!.*--)[a-z0-9][a-z0-9-]*[a-z0-9]$|^[a-z0-9]{3}$",
        message = "VM name must contain only lowercase letters, numbers, and hyphens, must start and end with alphanumeric, no consecutive hyphens"
    )
    val vmName: String,

    @field:NotBlank(message = "Project ID is required")
    @field:Pattern(
        regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        message = "Project ID must be a valid UUID"
    )
    val projectId: String,

    @field:NotBlank(message = "Size is required")
    @field:Pattern(
        regexp = "^(S|M|L|XL)$",
        message = "Size must be one of: S, M, L, XL"
    )
    val size: String,

    @field:NotBlank(message = "Justification is required")
    @field:Size(min = 10, message = "Justification must be at least 10 characters")
    val justification: String
)
