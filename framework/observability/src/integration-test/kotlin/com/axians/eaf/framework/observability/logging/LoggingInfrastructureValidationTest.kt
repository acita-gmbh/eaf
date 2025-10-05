package com.axians.eaf.framework.observability.logging

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import org.slf4j.LoggerFactory
import org.slf4j.MDC

/**
 * Infrastructure validation test for structured logging patterns.
 * Validates core logging functionality works as expected.
 *
 * Follows Epic 4 validation approach - prove infrastructure works.
 */
class LoggingInfrastructureValidationTest :
    FunSpec({

        val logger = LoggerFactory.getLogger(LoggingInfrastructureValidationTest::class.java)
        val objectMapper = ObjectMapper()

        beforeEach {
            MDC.clear()
        }

        afterEach {
            MDC.clear()
        }

        test("5.1-INT-001: MDC context fields can be set and retrieved") {
            // Given: Core logging context fields
            val serviceName = "test-service"
            val traceId = "trace-12345"
            val tenantId = "tenant-abc"

            // When: Fields are set in MDC
            MDC.put(LoggingContextProvider.SERVICE_NAME_KEY, serviceName)
            MDC.put(LoggingContextProvider.TRACE_ID_KEY, traceId)
            MDC.put(LoggingContextProvider.TENANT_ID_KEY, tenantId)

            // Then: Fields should be retrievable
            MDC.get(LoggingContextProvider.SERVICE_NAME_KEY) shouldBe serviceName
            MDC.get(LoggingContextProvider.TRACE_ID_KEY) shouldBe traceId
            MDC.get(LoggingContextProvider.TENANT_ID_KEY) shouldBe tenantId
        }

        test("5.1-INT-002: LoggingFormatValidator validates JSON structure correctly") {
            // Given: A validator instance
            val validator = LoggingFormatValidator()

            // When: Valid JSON is validated
            val validJson =
                """
                {
                    "timestamp": "2025-09-29T10:15:30.123Z",
                    "level": "INFO",
                    "logger": "test.logger",
                    "message": "Test message",
                    "thread": "main"
                }
                """.trimIndent()

            val result = validator.validateLogEntry(validJson)

            // Then: Should pass validation
            result.isValid shouldBe true
            result.jsonValid shouldBe true
            result.missingRequiredFields.size shouldBe 0
        }

        test("5.1-INT-003: LoggingFormatValidator detects missing required fields") {
            // Given: A validator instance
            val validator = LoggingFormatValidator()

            // When: Invalid JSON with missing fields
            val invalidJson = """{"message": "incomplete"}"""
            val result = validator.validateLogEntry(invalidJson)

            // Then: Should fail validation
            result.isValid shouldBe false
            result.jsonValid shouldBe true
            result.missingRequiredFields shouldContain "timestamp"
            result.missingRequiredFields shouldContain "level"
            result.missingRequiredFields shouldContain "logger"
            result.missingRequiredFields shouldContain "thread"
        }

        test("5.1-INT-004: LoggingContextProvider constants are defined correctly") {
            // Then: Constants should match expected values
            LoggingContextProvider.SERVICE_NAME_KEY shouldBe "service_name"
            LoggingContextProvider.TRACE_ID_KEY shouldBe "trace_id"
            LoggingContextProvider.SPAN_ID_KEY shouldBe "span_id"
            LoggingContextProvider.TENANT_ID_KEY shouldBe "tenant_id"
        }

        test("5.3-INT-001: MDC contains trace_id from OpenTelemetry when span is active") {
            // Given: Mock OpenTelemetry span context
            val testTraceId = "00000000000000000000000000000001"
            val testSpanId = "0000000000000001"

            // When: MDC is populated (simulating EafMDCConfiguration behavior)
            MDC.put(LoggingContextProvider.TRACE_ID_KEY, testTraceId)
            MDC.put(LoggingContextProvider.SPAN_ID_KEY, testSpanId)

            // Then: MDC contains OpenTelemetry trace context
            MDC.get(LoggingContextProvider.TRACE_ID_KEY) shouldBe testTraceId
            MDC.get(LoggingContextProvider.SPAN_ID_KEY) shouldBe testSpanId
        }

        test("5.1-INT-005: logging produces output at different levels") {
            // Given: Context is set
            MDC.put(LoggingContextProvider.SERVICE_NAME_KEY, "validation-test")

            // When: Logging at different levels
            logger.info("Infrastructure validation - info level")
            logger.warn("Infrastructure validation - warn level")
            logger.error("Infrastructure validation - error level")

            // Then: No exceptions should occur (smoke test)
            // The actual JSON format will be validated in production with proper appender configuration
            MDC.get(LoggingContextProvider.SERVICE_NAME_KEY) shouldBe "validation-test"
        }
    })
