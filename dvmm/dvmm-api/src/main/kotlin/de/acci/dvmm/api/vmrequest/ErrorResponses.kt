package de.acci.dvmm.api.vmrequest

/**
 * Response DTO for validation errors (HTTP 400).
 */
public data class ValidationErrorResponse(
    val type: String = "validation",
    val errors: List<FieldError>
) {
    public data class FieldError(
        val field: String,
        val message: String
    )
}

/**
 * Response DTO for quota exceeded errors (HTTP 409).
 */
public data class QuotaExceededResponse(
    val type: String = "quota_exceeded",
    val message: String,
    val available: Int,
    val requested: Int
)
