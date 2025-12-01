package de.acci.dvmm.api

import de.acci.dvmm.api.vmrequest.ValidationErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException

/**
 * Global exception handler for converting framework exceptions to consistent API responses.
 *
 * Handles Bean Validation errors (WebExchangeBindException) and converts them to
 * [ValidationErrorResponse] for consistent API error format across all endpoints.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle Bean Validation errors from @Valid annotations.
     *
     * Converts Spring's WebExchangeBindException to our custom ValidationErrorResponse
     * format for consistent error handling.
     */
    @ExceptionHandler(WebExchangeBindException::class)
    public fun handleValidationException(
        ex: WebExchangeBindException
    ): ResponseEntity<ValidationErrorResponse> {
        val fieldErrors = ex.bindingResult.fieldErrors.map { fieldError ->
            ValidationErrorResponse.FieldError(
                field = fieldError.field,
                message = fieldError.defaultMessage ?: "Invalid value"
            )
        }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ValidationErrorResponse(errors = fieldErrors))
    }
}
