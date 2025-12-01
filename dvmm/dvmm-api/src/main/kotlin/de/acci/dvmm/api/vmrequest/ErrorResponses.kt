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

/**
 * Response DTO for concurrency conflict errors (HTTP 409).
 */
public data class ConcurrencyConflictResponse(
    val type: String = "concurrency_conflict",
    val message: String
)

/**
 * Response DTO for internal server errors (HTTP 500).
 */
public data class InternalErrorResponse(
    val type: String = "internal_error",
    val message: String
)

/**
 * Response DTO for not found errors (HTTP 404).
 */
public data class NotFoundResponse(
    val type: String = "not_found",
    val message: String
)

/**
 * Response DTO for forbidden errors (HTTP 403).
 */
public data class ForbiddenResponse(
    val type: String = "forbidden",
    val message: String
)

/**
 * Response DTO for invalid state errors (HTTP 409).
 */
public data class InvalidStateResponse(
    val type: String = "invalid_state",
    val message: String,
    val currentState: String
)

/**
 * Response DTO for successful VM request cancellation (HTTP 200).
 */
public data class CancelSuccessResponse(
    val type: String = "cancelled",
    val message: String,
    val requestId: String
)
