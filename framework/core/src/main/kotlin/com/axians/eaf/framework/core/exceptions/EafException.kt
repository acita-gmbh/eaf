package com.axians.eaf.framework.core.exceptions

/**
 * Base exception for all EAF framework exceptions.
 *
 * All domain and framework exceptions should extend this base class
 * to provide consistent exception handling across the application.
 *
 * This is a RuntimeException (unchecked) to avoid polluting method
 * signatures with checked exception declarations while still allowing
 * specific exception types for precise error handling.
 *
 * Example:
 * ```kotlin
 * throw EafException("Configuration error", cause = originalException)
 * ```
 *
 * @param message The exception message
 * @param cause The underlying cause (optional)
 */
open class EafException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
