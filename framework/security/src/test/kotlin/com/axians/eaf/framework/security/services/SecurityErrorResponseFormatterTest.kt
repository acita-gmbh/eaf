package com.axians.eaf.framework.security.services

import com.axians.eaf.framework.security.errors.SecurityError
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse

/**
 * Unit tests for SecurityErrorResponseFormatter.
 * Story 8.6: Comprehensive coverage for extracted error formatting logic.
 */
class SecurityErrorResponseFormatterTest :
    FunSpec({
        val objectMapper: ObjectMapper = ObjectMapper()
        val formatter = SecurityErrorResponseFormatter(objectMapper)

        context("8.6-UNIT-SVC-200: formatValidationError") {
            test("8.6-UNIT-SVC-201: should format SecurityError into valid JSON") {
                val error = SecurityError.MissingTenantClaim

                val jsonResponse = formatter.formatValidationError(error)

                jsonResponse.shouldContain("\"error\":\"unauthorized\"")
                jsonResponse.shouldContain("\"message\":\"Missing or invalid tenant_id claim\"")
                jsonResponse.shouldContain("\"type\":\"MissingTenantClaim\"")
                jsonResponse.shouldContain("\"timestamp\":")
            }

            test("8.6-UNIT-SVC-202: should format different error types correctly") {
                val error = SecurityError.TokenExpired(java.time.Instant.now(), java.time.Instant.now())

                val jsonResponse = formatter.formatValidationError(error)

                jsonResponse.shouldContain("\"error\":\"unauthorized\"")
                jsonResponse.shouldContain("\"type\":\"TokenExpired\"")
            }
        }

        context("8.6-UNIT-SVC-210: formatTenantError") {
            test("8.6-UNIT-SVC-211: should format tenant error with default message") {
                val errorType = "invalid_scope"

                val jsonResponse = formatter.formatTenantError(errorType)

                jsonResponse shouldBe """{"error":"Tenant validation failed: invalid_scope"}"""
            }

            test("8.6-UNIT-SVC-212: should format tenant error with override message") {
                val errorType = "invalid_scope"
                val overrideMessage = "Custom error message"

                val jsonResponse = formatter.formatTenantError(errorType, overrideMessage)

                jsonResponse shouldBe """{"error":"Tenant validation failed: Custom error message"}"""
            }

            test("8.6-UNIT-SVC-213: should use override message when provided (null check)") {
                val jsonWithNull = formatter.formatTenantError("type1", null)
                val jsonWithValue = formatter.formatTenantError("type1", "override")

                jsonWithNull shouldBe """{"error":"Tenant validation failed: type1"}"""
                jsonWithValue shouldBe """{"error":"Tenant validation failed: override"}"""
            }
        }

        context("8.6-UNIT-SVC-220: formatUnexpectedError") {
            test("8.6-UNIT-SVC-221: should format unexpected error into valid JSON") {
                val jsonResponse = formatter.formatUnexpectedError()

                jsonResponse.shouldContain("\"error\":\"internal_server_error\"")
                jsonResponse.shouldContain("\"message\":\"Authentication processing error\"")
                jsonResponse.shouldContain("\"timestamp\":")
            }
        }

        context("8.6-UNIT-SVC-230: writeErrorResponse") {
            test("8.6-UNIT-SVC-231: should set status, content type, and write JSON") {
                val response = MockHttpServletResponse()
                val status = HttpStatus.UNAUTHORIZED
                val errorJson = """{"error":"test"}"""

                formatter.writeErrorResponse(response, status, errorJson)

                response.status shouldBe status.value()
                response.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
                response.contentAsString shouldBe errorJson
            }

            test("8.6-UNIT-SVC-232: should handle different status codes") {
                val response1 = MockHttpServletResponse()
                val response2 = MockHttpServletResponse()

                formatter.writeErrorResponse(response1, HttpStatus.UNAUTHORIZED, "{}")
                formatter.writeErrorResponse(response2, HttpStatus.FORBIDDEN, "{}")

                response1.status shouldBe 401
                response2.status shouldBe 403
            }
        }
    })
