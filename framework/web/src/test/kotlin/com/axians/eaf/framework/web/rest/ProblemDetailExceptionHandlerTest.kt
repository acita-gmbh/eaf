package com.axians.eaf.framework.web.rest

import com.axians.eaf.framework.core.exceptions.AggregateNotFoundException
import com.axians.eaf.framework.core.exceptions.EafException
import com.axians.eaf.framework.core.exceptions.TenantIsolationException
import com.axians.eaf.framework.core.exceptions.ValidationException
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import java.time.Instant

/**
 * Unit tests for ProblemDetailExceptionHandler (Story 2.9).
 *
 * Validates RFC 7807 Problem Details compliance for all framework exceptions.
 *
 * **Test Coverage:**
 * - AC 2: ProblemDetailExceptionHandler implements RFC 7807
 * - AC 3: Error responses include type, title, status, detail, instance, traceId, tenantId
 * - AC 7: All framework exceptions mapped to HTTP status codes
 *
 * **Test Strategy:**
 * - Unit tests (no Spring context) - fast, isolated
 * - MockHttpServletRequest for request URI
 * - Verify HTTP status codes (400, 403, 404, 500)
 * - Verify RFC 7807 fields (type, title, status, detail, instance)
 * - Verify custom properties (traceId, tenantId, timestamp)
 *
 * **References:**
 * - Story 2.9: REST API Foundation
 * - RFC 7807: https://tools.ietf.org/html/rfc7807
 */
class ProblemDetailExceptionHandlerTest {
    private val handler = ProblemDetailExceptionHandler()

    /**
     * Creates a mock HTTP request with specified URI.
     */
    private fun createMockRequest(uri: String = "/api/widgets"): HttpServletRequest =
        MockHttpServletRequest().apply {
            requestURI = uri
        }

    @Nested
    inner class ValidationExceptionHandling {
        @Test
        fun `should return 400 Bad Request for ValidationException`() {
            // Given
            val exception = ValidationException("Widget name must not be blank")
            val request = createMockRequest("/api/widgets")

            // When
            val response = handler.handleValidation(exception, request)

            // Then
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @Test
        fun `should include RFC 7807 fields for ValidationException (AC 2, AC 3)`() {
            // Given
            val exception = ValidationException("Widget name must not be blank")
            val request = createMockRequest("/api/widgets/123")

            // When
            val response = handler.handleValidation(exception, request)
            val problem = response.body!!

            // Then - RFC 7807 standard fields
            assertThat(problem.type.toString()).isEqualTo("https://eaf.axians.com/errors/validation-error")
            assertThat(problem.title).isEqualTo("Validation Error")
            assertThat(problem.status).isEqualTo(400)
            assertThat(problem.detail).contains("Widget name must not be blank")

            // Then - RFC 7807 instance field (AC 3)
            assertThat(problem.instance.toString()).isEqualTo("/api/widgets/123")

            // Then - Custom properties (AC 3)
            assertThat(problem.properties).isNotNull
            assertThat(problem.properties!!.keys).contains("traceId")
            assertThat(problem.properties!!.keys).contains("tenantId")
            assertThat(problem.properties!!.keys).contains("timestamp")

            // Timestamp should be recent (within last 5 seconds for CI/CD stability)
            val timestamp = problem.properties!!["timestamp"]
            assertThat(timestamp).isInstanceOf(Instant::class.java)
            val now = Instant.now()
            val diff = java.time.Duration.between(timestamp as Instant, now)
            assertThat(diff.abs().seconds).isLessThan(5)
        }
    }

    @Nested
    inner class AggregateNotFoundExceptionHandling {
        @Test
        fun `should return 404 Not Found for AggregateNotFoundException`() {
            // Given
            val exception =
                AggregateNotFoundException(
                    aggregateId = "widget-123",
                    aggregateType = "Widget",
                )
            val request = createMockRequest("/api/widgets/widget-123")

            // When
            val response = handler.handleNotFound(exception, request)

            // Then
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @Test
        fun `should include RFC 7807 fields for AggregateNotFoundException (AC 2, AC 3)`() {
            // Given
            val exception =
                AggregateNotFoundException(
                    aggregateId = "widget-123",
                    aggregateType = "Widget",
                )
            val request = createMockRequest("/api/widgets/widget-123")

            // When
            val response = handler.handleNotFound(exception, request)
            val problem = response.body!!

            // Then - RFC 7807 standard fields
            assertThat(problem.type.toString()).isEqualTo("https://eaf.axians.com/errors/not-found")
            assertThat(problem.title).isEqualTo("Not Found")
            assertThat(problem.status).isEqualTo(404)
            assertThat(problem.detail).contains("Widget with ID widget-123 not found")

            // Then - RFC 7807 instance field (AC 3)
            assertThat(problem.instance.toString()).isEqualTo("/api/widgets/widget-123")

            // Then - Custom properties (AC 3)
            assertThat(problem.properties).isNotNull
            assertThat(problem.properties!!.keys).contains("traceId")
            assertThat(problem.properties!!.keys).contains("tenantId")
            assertThat(problem.properties!!.keys).contains("timestamp")
        }
    }

    @Nested
    inner class TenantIsolationExceptionHandling {
        @Test
        fun `should return 403 Forbidden for TenantIsolationException`() {
            // Given
            val exception = TenantIsolationException("Tenant mismatch: expected tenant-a, got tenant-b")
            val request = createMockRequest("/api/widgets/widget-123")

            // When
            val response = handler.handleTenantIsolation(exception, request)

            // Then
            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `should use generic error message for TenantIsolationException (CWE-209 protection)`() {
            // Given - Specific exception message with tenant details
            val exception = TenantIsolationException("Tenant mismatch: expected tenant-a, got tenant-b")
            val request = createMockRequest("/api/widgets/widget-123")

            // When
            val response = handler.handleTenantIsolation(exception, request)
            val problem = response.body!!

            // Then - Generic message (does NOT leak tenant details)
            assertThat(problem.detail).isEqualTo("Access denied: tenant context mismatch")
            assertThat(problem.detail).isNotEqualTo(exception.message) // NOT the specific exception message
        }

        @Test
        fun `should include RFC 7807 fields for TenantIsolationException (AC 2, AC 3)`() {
            // Given
            val exception = TenantIsolationException("Tenant isolation violation")
            val request = createMockRequest("/api/widgets/widget-123")

            // When
            val response = handler.handleTenantIsolation(exception, request)
            val problem = response.body!!

            // Then - RFC 7807 standard fields
            assertThat(problem.type.toString()).isEqualTo("https://eaf.axians.com/errors/tenant-isolation")
            assertThat(problem.title).isEqualTo("Forbidden")
            assertThat(problem.status).isEqualTo(403)

            // Then - RFC 7807 instance field (AC 3)
            assertThat(problem.instance.toString()).isEqualTo("/api/widgets/widget-123")

            // Then - Custom properties (AC 3)
            assertThat(problem.properties).isNotNull
            assertThat(problem.properties!!.keys).contains("traceId")
            assertThat(problem.properties!!.keys).contains("tenantId")
            assertThat(problem.properties!!.keys).contains("timestamp")
        }
    }

    @Nested
    inner class GenericEafExceptionHandling {
        @Test
        fun `should return 500 Internal Server Error for EafException`() {
            // Given - Custom EafException subclass
            class CustomEafException(
                message: String,
            ) : EafException(message)
            val exception = CustomEafException("Internal framework error")
            val request = createMockRequest("/api/widgets")

            // When
            val response = handler.handleEafException(exception, request)

            // Then
            assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        }

        @Test
        fun `should use generic error message for EafException (CWE-209 protection)`() {
            // Given - Specific exception message
            class CustomEafException(
                message: String,
            ) : EafException(message)
            val exception = CustomEafException("Database connection failed: host=db-prod-1")
            val request = createMockRequest("/api/widgets")

            // When
            val response = handler.handleEafException(exception, request)
            val problem = response.body!!

            // Then - Generic message (does NOT leak internal details)
            assertThat(problem.detail).isEqualTo("An internal error occurred")
            assertThat(problem.detail).isNotEqualTo(exception.message)
        }

        @Test
        fun `should include RFC 7807 fields for EafException (AC 2, AC 3)`() {
            // Given
            class CustomEafException(
                message: String,
            ) : EafException(message)
            val exception = CustomEafException("Internal error")
            val request = createMockRequest("/api/widgets")

            // When
            val response = handler.handleEafException(exception, request)
            val problem = response.body!!

            // Then - RFC 7807 standard fields
            assertThat(problem.type.toString()).isEqualTo("https://eaf.axians.com/errors/internal-error")
            assertThat(problem.title).isEqualTo("Internal Server Error")
            assertThat(problem.status).isEqualTo(500)

            // Then - RFC 7807 instance field (AC 3)
            assertThat(problem.instance.toString()).isEqualTo("/api/widgets")

            // Then - Custom properties (AC 3)
            assertThat(problem.properties).isNotNull
            assertThat(problem.properties!!.keys).contains("traceId")
            assertThat(problem.properties!!.keys).contains("tenantId")
            assertThat(problem.properties!!.keys).contains("timestamp")
        }
    }

    @Nested
    inner class GenericExceptionHandling {
        @Test
        fun `should return 500 Internal Server Error for unexpected Exception`() {
            // Given - Non-EAF exception
            val exception = RuntimeException("Unexpected error: null pointer")
            val request = createMockRequest("/api/widgets")

            // When
            val response = handler.handleGenericException(exception, request)

            // Then
            assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        }

        @Test
        fun `should use generic error message for Exception (CWE-209 protection)`() {
            // Given - Exception with internal details
            val exception = RuntimeException("NullPointerException at DatabaseService.kt:42")
            val request = createMockRequest("/api/widgets")

            // When
            val response = handler.handleGenericException(exception, request)
            val problem = response.body!!

            // Then - Generic message (does NOT leak stack trace details)
            assertThat(problem.detail).isEqualTo("An unexpected error occurred")
            assertThat(problem.detail).isNotEqualTo(exception.message)
        }

        @Test
        fun `should include RFC 7807 fields for generic Exception (AC 2, AC 3)`() {
            // Given
            val exception = RuntimeException("Unexpected error")
            val request = createMockRequest("/api/widgets/123")

            // When
            val response = handler.handleGenericException(exception, request)
            val problem = response.body!!

            // Then - RFC 7807 standard fields
            assertThat(problem.type.toString()).isEqualTo("https://eaf.axians.com/errors/internal-error")
            assertThat(problem.title).isEqualTo("Internal Server Error")
            assertThat(problem.status).isEqualTo(500)

            // Then - RFC 7807 instance field (AC 3)
            assertThat(problem.instance.toString()).isEqualTo("/api/widgets/123")

            // Then - Custom properties (AC 3)
            assertThat(problem.properties).isNotNull
            assertThat(problem.properties!!.keys).contains("traceId")
            assertThat(problem.properties!!.keys).contains("tenantId")
            assertThat(problem.properties!!.keys).contains("timestamp")
        }
    }

    @Nested
    inner class DefensiveUriHandling {
        @Test
        fun `should handle malformed request URI gracefully`() {
            // Given - Mock request with malformed URI (contains spaces, invalid for URI.create)
            val exception = ValidationException("Test error")
            val request =
                MockHttpServletRequest().apply {
                    // Simulate malformed URI that would cause URI.create to throw
                    // Note: HttpServletRequest.getRequestURI() typically returns valid URIs,
                    // but this tests defensive handling for edge cases
                    requestURI = "/api/widgets/malformed uri with spaces"
                }

            // When - Should NOT throw exception during error handling
            val response = handler.handleValidation(exception, request)
            val problem = response.body!!

            // Then - Should fallback to /unknown instead of throwing
            assertThat(problem.instance.toString()).isEqualTo("/unknown")

            // Original error response should still be valid
            assertThat(problem.status).isEqualTo(400)
            assertThat(problem.type.toString()).isEqualTo("https://eaf.axians.com/errors/validation-error")
        }

        @Test
        fun `should use valid URI when request URI is properly formatted`() {
            // Given - Normal request with valid URI
            val exception = ValidationException("Test error")
            val request = createMockRequest("/api/widgets/123")

            // When
            val response = handler.handleValidation(exception, request)
            val problem = response.body!!

            // Then - Should use actual URI (not fallback)
            assertThat(problem.instance.toString()).isEqualTo("/api/widgets/123")
        }
    }
}
