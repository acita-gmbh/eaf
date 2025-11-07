package com.axians.eaf.framework.web.rest

import com.axians.eaf.framework.core.exceptions.AggregateNotFoundException
import com.axians.eaf.framework.core.exceptions.EafException
import com.axians.eaf.framework.core.exceptions.TenantIsolationException
import com.axians.eaf.framework.core.exceptions.ValidationException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI
import java.time.Instant

/**
 * Global REST exception handler implementing RFC 7807 Problem Details (Story 2.9).
 *
 * Provides consistent, machine-readable error responses across all REST endpoints.
 * All error responses follow RFC 7807 format with:
 * - type: URI identifying the error type
 * - title: Human-readable error category
 * - status: HTTP status code
 * - detail: Specific error message
 * - instance: Request URI where error occurred
 * - traceId: Correlation ID from MDC (if available)
 * - tenantId: Tenant context (will be added in Story 4.1)
 * - timestamp: When error occurred (ISO-8601)
 *
 * **Security Note (CWE-209):**
 * Generic error messages prevent information leakage. Details are logged but not exposed to clients.
 *
 * **References:**
 * - [RFC 7807](https://tools.ietf.org/html/rfc7807)
 * - Architecture: Section 15 (API Contracts - Error Format)
 * - Tech Spec: Section 5.3 (Error Response Format)
 *
 * @see ProblemDetail
 * @see ValidationException
 * @see AggregateNotFoundException
 * @see TenantIsolationException
 */
@RestControllerAdvice
class ProblemDetailExceptionHandler {
    private val logger = LoggerFactory.getLogger(ProblemDetailExceptionHandler::class.java)

    /**
     * Handles ValidationException → 400 Bad Request.
     *
     * Triggered when business rule validation fails (invalid input, constraint violations).
     *
     * **Example Response:**
     * ```json
     * {
     *   "type": "https://eaf.axians.com/errors/validation-error",
     *   "title": "Validation Error",
     *   "status": 400,
     *   "detail": "Widget name must not be blank",
     *   "instance": "/api/widgets",
     *   "traceId": "abc123",
     *   "timestamp": "2025-11-07T10:30:00Z"
     * }
     * ```
     */
    @ExceptionHandler(ValidationException::class)
    fun handleValidation(
        ex: ValidationException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        logger.warn("Validation error: ${ex.message}", ex)

        val problem =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.message ?: "Validation failed",
            )

        problem.type = URI.create("https://eaf.axians.com/errors/validation-error")
        problem.title = "Validation Error"
        enrichProblemDetail(problem, request)

        return ResponseEntity.badRequest().body(problem)
    }

    /**
     * Handles AggregateNotFoundException → 404 Not Found.
     *
     * Triggered when requested aggregate/entity doesn't exist.
     *
     * **Example Response:**
     * ```json
     * {
     *   "type": "https://eaf.axians.com/errors/not-found",
     *   "title": "Not Found",
     *   "status": 404,
     *   "detail": "Widget with ID 123 not found",
     *   "instance": "/api/widgets/123",
     *   "traceId": "abc123",
     *   "timestamp": "2025-11-07T10:30:00Z"
     * }
     * ```
     */
    @ExceptionHandler(AggregateNotFoundException::class)
    fun handleNotFound(
        ex: AggregateNotFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        logger.info("Resource not found: ${ex.message}")

        val problem =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.message ?: "Resource not found",
            )

        problem.type = URI.create("https://eaf.axians.com/errors/not-found")
        problem.title = "Not Found"
        enrichProblemDetail(problem, request)

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem)
    }

    /**
     * Handles TenantIsolationException → 403 Forbidden.
     *
     * Triggered when tenant isolation boundary is violated (Stories 4.1-4.8).
     * Generic error message prevents tenant enumeration (CWE-209 protection).
     *
     * **Example Response:**
     * ```json
     * {
     *   "type": "https://eaf.axians.com/errors/tenant-isolation",
     *   "title": "Forbidden",
     *   "status": 403,
     *   "detail": "Access denied: tenant context mismatch",
     *   "instance": "/api/widgets/123",
     *   "traceId": "abc123",
     *   "timestamp": "2025-11-07T10:30:00Z"
     * }
     * ```
     */
    @ExceptionHandler(TenantIsolationException::class)
    fun handleTenantIsolation(
        ex: TenantIsolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        // High-severity security event - log with full context
        logger.error("Tenant isolation violation: ${ex.message}", ex)

        // Generic message to prevent information leakage (CWE-209)
        val problem =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "Access denied: tenant context mismatch",
            )

        problem.type = URI.create("https://eaf.axians.com/errors/tenant-isolation")
        problem.title = "Forbidden"
        enrichProblemDetail(problem, request)

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem)
    }

    /**
     * Handles all other EafException subclasses → 500 Internal Server Error.
     *
     * Catch-all for framework exceptions not explicitly handled above.
     * Logs full stack trace but returns generic error to prevent information leakage.
     *
     * **Example Response:**
     * ```json
     * {
     *   "type": "https://eaf.axians.com/errors/internal-error",
     *   "title": "Internal Server Error",
     *   "status": 500,
     *   "detail": "An internal error occurred",
     *   "instance": "/api/widgets",
     *   "traceId": "abc123",
     *   "timestamp": "2025-11-07T10:30:00Z"
     * }
     * ```
     */
    @ExceptionHandler(EafException::class)
    fun handleEafException(
        ex: EafException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        logger.error("EAF exception: ${ex.message}", ex)

        val problem =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An internal error occurred",
            )

        problem.type = URI.create("https://eaf.axians.com/errors/internal-error")
        problem.title = "Internal Server Error"
        enrichProblemDetail(problem, request)

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem)
    }

    /**
     * Handles IllegalArgumentException → 400 Bad Request.
     *
     * Triggered by domain validation (require() statements in aggregates).
     * Maps domain precondition failures to client errors.
     *
     * **Story 2.10:** Added to handle Widget aggregate validation exceptions.
     *
     * **Example Response:**
     * ```json
     * {
     *   "type": "https://eaf.axians.com/errors/validation-error",
     *   "title": "Validation Error",
     *   "status": 400,
     *   "detail": "Widget name cannot be blank",
     *   "instance": "/api/widgets",
     *   "traceId": "abc123",
     *   "timestamp": "2025-11-07T10:30:00Z"
     * }
     * ```
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        logger.warn("Illegal argument: ${ex.message}", ex)

        val problem =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.message ?: "Invalid argument",
            )

        problem.type = URI.create("https://eaf.axians.com/errors/validation-error")
        problem.title = "Validation Error"
        enrichProblemDetail(problem, request)

        return ResponseEntity.badRequest().body(problem)
    }

    /**
     * Handles all other exceptions → 500 Internal Server Error.
     *
     * Final catch-all for unexpected errors. Logs full exception but returns
     * generic error message to prevent information leakage (CWE-209).
     *
     * **Example Response:**
     * ```json
     * {
     *   "type": "https://eaf.axians.com/errors/internal-error",
     *   "title": "Internal Server Error",
     *   "status": 500,
     *   "detail": "An unexpected error occurred",
     *   "instance": "/api/widgets",
     *   "traceId": "abc123",
     *   "timestamp": "2025-11-07T10:30:00Z"
     * }
     * ```
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        logger.error("Unexpected exception: ${ex.message}", ex)

        val problem =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
            )

        problem.type = URI.create("https://eaf.axians.com/errors/internal-error")
        problem.title = "Internal Server Error"
        enrichProblemDetail(problem, request)

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem)
    }

    /**
     * Enriches ProblemDetail with standard and custom fields (Story 2.9).
     *
     * Sets:
     * - instance: Request URI (RFC 7807 standard field)
     * - traceId: Correlation ID from MDC (custom property)
     * - tenantId: Tenant context (custom property, placeholder for Story 4.1)
     * - timestamp: When error occurred (custom property, ISO-8601)
     *
     * **Defensive URI Handling:**
     * If request.requestURI contains malformed URI syntax, falls back to "/unknown"
     * to prevent IllegalArgumentException from masking the original exception.
     *
     * @param problem ProblemDetail to enrich
     * @param request HttpServletRequest for request URI
     */
    private fun enrichProblemDetail(
        problem: ProblemDetail,
        request: HttpServletRequest,
    ) {
        // RFC 7807 standard field - use direct assignment (not setProperty)
        // Defensive handling: malformed URIs fallback to /unknown
        problem.instance =
            runCatching { URI.create(request.requestURI) }
                .getOrElse {
                    logger.debug("Malformed request URI, using fallback: ${request.requestURI}", it)
                    URI.create("/unknown")
                }

        // Custom properties - use setProperty
        problem.setProperty("traceId", getTraceId())
        problem.setProperty("tenantId", getTenantId())
        problem.setProperty("timestamp", Instant.now())
    }

    /**
     * Retrieves traceId from MDC (Mapped Diagnostic Context).
     *
     * MDC is populated by observability framework (Story 5.2: Context Injection).
     * Returns null if traceId not yet configured.
     *
     * @return traceId string or null if not available
     */
    private fun getTraceId(): String? = MDC.get("trace_id")

    /**
     * Retrieves tenantId from TenantContext (placeholder for Story 4.1).
     *
     * TenantContext will be implemented in Story 4.1 (Tenant Context ThreadLocal).
     * Returns null until multi-tenancy stories (4.1-4.8) are complete.
     *
     * @return tenantId string or null if TenantContext not yet implemented
     */
    @Suppress("FunctionOnlyReturningConstant") // Placeholder for Story 4.1 - will be replaced
    private fun getTenantId(): String? {
        // TODO Story 4.1: Replace with TenantContext.current()
        return null
    }
}
