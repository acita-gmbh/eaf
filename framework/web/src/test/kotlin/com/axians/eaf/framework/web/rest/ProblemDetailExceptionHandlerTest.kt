package com.axians.eaf.framework.web.rest

import com.axians.eaf.framework.core.exceptions.AggregateNotFoundException
import com.axians.eaf.framework.core.exceptions.EafException
import com.axians.eaf.framework.core.exceptions.TenantIsolationException
import com.axians.eaf.framework.core.exceptions.ValidationException
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import jakarta.servlet.http.HttpServletRequest
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
class ProblemDetailExceptionHandlerTest :
    FunSpec({

        val handler = ProblemDetailExceptionHandler()

        /**
         * Creates a mock HTTP request with specified URI.
         */
        fun createMockRequest(uri: String = "/api/widgets"): HttpServletRequest =
            MockHttpServletRequest().apply {
                requestURI = uri
            }

        context("ValidationException handling (AC 7: 400 Bad Request)") {
            test("should return 400 Bad Request for ValidationException") {
                // Given
                val exception = ValidationException("Widget name must not be blank")
                val request = createMockRequest("/api/widgets")

                // When
                val response = handler.handleValidation(exception, request)

                // Then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }

            test("should include RFC 7807 fields for ValidationException (AC 2, AC 3)") {
                // Given
                val exception = ValidationException("Widget name must not be blank")
                val request = createMockRequest("/api/widgets/123")

                // When
                val response = handler.handleValidation(exception, request)
                val problem = response.body!!

                // Then - RFC 7807 standard fields
                problem.type.toString() shouldBe "https://eaf.axians.com/errors/validation-error"
                problem.title shouldBe "Validation Error"
                problem.status shouldBe 400
                problem.detail shouldContain "Widget name must not be blank"

                // Then - RFC 7807 instance field (AC 3)
                problem.instance.toString() shouldBe "/api/widgets/123"

                // Then - Custom properties (AC 3)
                problem.properties shouldNotBe null
                problem.properties!!.keys shouldContain "traceId"
                problem.properties!!.keys shouldContain "tenantId"
                problem.properties!!.keys shouldContain "timestamp"

                // Timestamp should be recent (within last 5 seconds for CI/CD stability)
                val timestamp = problem.properties!!["timestamp"]
                timestamp.shouldBeInstanceOf<Instant>()
                val now = Instant.now()
                val diff = java.time.Duration.between(timestamp as Instant, now)
                diff.abs().seconds shouldBeLessThan 5
            }
        }

        context("AggregateNotFoundException handling (AC 7: 404 Not Found)") {
            test("should return 404 Not Found for AggregateNotFoundException") {
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
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            test("should include RFC 7807 fields for AggregateNotFoundException (AC 2, AC 3)") {
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
                problem.type.toString() shouldBe "https://eaf.axians.com/errors/not-found"
                problem.title shouldBe "Not Found"
                problem.status shouldBe 404
                problem.detail shouldContain "Widget with ID widget-123 not found"

                // Then - RFC 7807 instance field (AC 3)
                problem.instance.toString() shouldBe "/api/widgets/widget-123"

                // Then - Custom properties (AC 3)
                problem.properties shouldNotBe null
                problem.properties!!.keys shouldContain "traceId"
                problem.properties!!.keys shouldContain "tenantId"
                problem.properties!!.keys shouldContain "timestamp"
            }
        }

        context("TenantIsolationException handling (AC 7: 403 Forbidden)") {
            test("should return 403 Forbidden for TenantIsolationException") {
                // Given
                val exception = TenantIsolationException("Tenant mismatch: expected tenant-a, got tenant-b")
                val request = createMockRequest("/api/widgets/widget-123")

                // When
                val response = handler.handleTenantIsolation(exception, request)

                // Then
                response.statusCode shouldBe HttpStatus.FORBIDDEN
            }

            test("should use generic error message for TenantIsolationException (CWE-209 protection)") {
                // Given - Specific exception message with tenant details
                val exception = TenantIsolationException("Tenant mismatch: expected tenant-a, got tenant-b")
                val request = createMockRequest("/api/widgets/widget-123")

                // When
                val response = handler.handleTenantIsolation(exception, request)
                val problem = response.body!!

                // Then - Generic message (does NOT leak tenant details)
                problem.detail shouldBe "Access denied: tenant context mismatch"
                problem.detail shouldNotBe exception.message // NOT the specific exception message
            }

            test("should include RFC 7807 fields for TenantIsolationException (AC 2, AC 3)") {
                // Given
                val exception = TenantIsolationException("Tenant isolation violation")
                val request = createMockRequest("/api/widgets/widget-123")

                // When
                val response = handler.handleTenantIsolation(exception, request)
                val problem = response.body!!

                // Then - RFC 7807 standard fields
                problem.type.toString() shouldBe "https://eaf.axians.com/errors/tenant-isolation"
                problem.title shouldBe "Forbidden"
                problem.status shouldBe 403

                // Then - RFC 7807 instance field (AC 3)
                problem.instance.toString() shouldBe "/api/widgets/widget-123"

                // Then - Custom properties (AC 3)
                problem.properties shouldNotBe null
                problem.properties!!.keys shouldContain "traceId"
                problem.properties!!.keys shouldContain "tenantId"
                problem.properties!!.keys shouldContain "timestamp"
            }
        }

        context("Generic EafException handling (AC 7: 500 Internal Server Error)") {
            test("should return 500 Internal Server Error for EafException") {
                // Given - Custom EafException subclass
                class CustomEafException(
                    message: String,
                ) : EafException(message)
                val exception = CustomEafException("Internal framework error")
                val request = createMockRequest("/api/widgets")

                // When
                val response = handler.handleEafException(exception, request)

                // Then
                response.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR
            }

            test("should use generic error message for EafException (CWE-209 protection)") {
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
                problem.detail shouldBe "An internal error occurred"
                problem.detail shouldNotBe exception.message
            }

            test("should include RFC 7807 fields for EafException (AC 2, AC 3)") {
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
                problem.type.toString() shouldBe "https://eaf.axians.com/errors/internal-error"
                problem.title shouldBe "Internal Server Error"
                problem.status shouldBe 500

                // Then - RFC 7807 instance field (AC 3)
                problem.instance.toString() shouldBe "/api/widgets"

                // Then - Custom properties (AC 3)
                problem.properties shouldNotBe null
                problem.properties!!.keys shouldContain "traceId"
                problem.properties!!.keys shouldContain "tenantId"
                problem.properties!!.keys shouldContain "timestamp"
            }
        }

        context("Generic Exception handling (AC 7: 500 Internal Server Error)") {
            test("should return 500 Internal Server Error for unexpected Exception") {
                // Given - Non-EAF exception
                val exception = RuntimeException("Unexpected error: null pointer")
                val request = createMockRequest("/api/widgets")

                // When
                val response = handler.handleGenericException(exception, request)

                // Then
                response.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR
            }

            test("should use generic error message for Exception (CWE-209 protection)") {
                // Given - Exception with internal details
                val exception = RuntimeException("NullPointerException at DatabaseService.kt:42")
                val request = createMockRequest("/api/widgets")

                // When
                val response = handler.handleGenericException(exception, request)
                val problem = response.body!!

                // Then - Generic message (does NOT leak stack trace details)
                problem.detail shouldBe "An unexpected error occurred"
                problem.detail shouldNotBe exception.message
            }

            test("should include RFC 7807 fields for generic Exception (AC 2, AC 3)") {
                // Given
                val exception = RuntimeException("Unexpected error")
                val request = createMockRequest("/api/widgets/123")

                // When
                val response = handler.handleGenericException(exception, request)
                val problem = response.body!!

                // Then - RFC 7807 standard fields
                problem.type.toString() shouldBe "https://eaf.axians.com/errors/internal-error"
                problem.title shouldBe "Internal Server Error"
                problem.status shouldBe 500

                // Then - RFC 7807 instance field (AC 3)
                problem.instance.toString() shouldBe "/api/widgets/123"

                // Then - Custom properties (AC 3)
                problem.properties shouldNotBe null
                problem.properties!!.keys shouldContain "traceId"
                problem.properties!!.keys shouldContain "tenantId"
                problem.properties!!.keys shouldContain "timestamp"
            }
        }
    })
