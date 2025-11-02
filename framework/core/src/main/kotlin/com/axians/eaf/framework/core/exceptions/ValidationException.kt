package com.axians.eaf.framework.core.exceptions

/**
 * Exception thrown when domain validation fails.
 *
 * Use this exception for business rule violations, constraint violations,
 * and invalid domain state transitions.
 *
 * Example:
 * ```kotlin
 * if (widgetName.isBlank()) {
 *     throw ValidationException("Widget name cannot be blank")
 * }
 * ```
 *
 * @param message Description of the validation failure
 * @param cause The underlying cause (optional)
 */
class ValidationException(
    message: String,
    cause: Throwable? = null,
) : EafException(message, cause)
