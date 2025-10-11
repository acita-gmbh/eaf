package com.axians.eaf.framework.security.services

import com.axians.eaf.framework.security.errors.SecurityError
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service

/**
 * Service for formatting security error responses.
 * Extracted from filters for unit testability (Story 8.6).
 *
 * Provides consistent error response formatting across all security filters.
 */
@Service
class SecurityErrorResponseFormatter(
    private val objectMapper: ObjectMapper,
) {
    /**
     * Formats JWT validation error as JSON response.
     *
     * @param error The security error to format
     * @return JSON string for HTTP response
     */
    fun formatValidationError(error: SecurityError): String {
        val errorResponse =
            mapOf(
                "error" to "unauthorized",
                "message" to error.message,
                "type" to error::class.simpleName,
                "timestamp" to System.currentTimeMillis(),
            )
        return objectMapper.writeValueAsString(errorResponse)
    }

    /**
     * Formats tenant validation error as JSON response.
     *
     * @param errorType The error type descriptor
     * @param overrideMessage Optional override for error message
     * @return JSON string for HTTP response
     */
    fun formatTenantError(
        errorType: String,
        overrideMessage: String? = null,
    ): String {
        val errorDetail = overrideMessage ?: errorType
        return """{"error":"Tenant validation failed: $errorDetail"}"""
    }

    /**
     * Formats unexpected error as JSON response.
     *
     * @return JSON string for HTTP response
     */
    fun formatUnexpectedError(): String {
        val errorResponse =
            mapOf(
                "error" to "internal_server_error",
                "message" to "Authentication processing error",
                "timestamp" to System.currentTimeMillis(),
            )
        return objectMapper.writeValueAsString(errorResponse)
    }

    /**
     * Writes error response to HTTP response object.
     *
     * @param response The HTTP response
     * @param status The HTTP status code
     * @param errorJson The JSON error string
     */
    fun writeErrorResponse(
        response: HttpServletResponse,
        status: HttpStatus,
        errorJson: String,
    ) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(errorJson)
    }
}
