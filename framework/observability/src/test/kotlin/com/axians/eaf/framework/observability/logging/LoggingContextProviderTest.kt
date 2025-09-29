package com.axians.eaf.framework.observability.logging

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.slf4j.MDC

/**
 * Unit test for LoggingContextProvider functionality.
 * Tests core MDC integration without Spring Boot context.
 */
class LoggingContextProviderTest :
    FunSpec({

        val loggingContextProvider = LoggingContextProvider("test-service")

        beforeEach {
            MDC.clear()
        }

        afterEach {
            MDC.clear()
        }

        test("should set service name in MDC") {
            // When: Service name is set
            loggingContextProvider.setServiceName()

            // Then: Service name should be in MDC
            MDC.get(LoggingContextProvider.SERVICE_NAME_KEY) shouldBe "test-service"
        }

        test("should set and clear trace ID") {
            // When: Trace ID is set
            loggingContextProvider.setTraceId("trace-123")

            // Then: Should be retrievable
            loggingContextProvider.getCurrentTraceId() shouldBe "trace-123"
            MDC.get(LoggingContextProvider.TRACE_ID_KEY) shouldBe "trace-123"

            // When: Trace ID is cleared
            loggingContextProvider.setTraceId(null)

            // Then: Should be removed
            loggingContextProvider.getCurrentTraceId() shouldBe null
            MDC.get(LoggingContextProvider.TRACE_ID_KEY) shouldBe null
        }

        test("should set and clear tenant ID") {
            // When: Tenant ID is set
            loggingContextProvider.setTenantId("tenant-abc")

            // Then: Should be retrievable
            loggingContextProvider.getCurrentTenantId() shouldBe "tenant-abc"
            MDC.get(LoggingContextProvider.TENANT_ID_KEY) shouldBe "tenant-abc"

            // When: Context is cleared
            loggingContextProvider.clearContext()

            // Then: Tenant ID should be removed
            loggingContextProvider.getCurrentTenantId() shouldBe null
            MDC.get(LoggingContextProvider.TENANT_ID_KEY) shouldBe null
        }

        test("should handle empty values gracefully") {
            // When: Empty values are set
            loggingContextProvider.setTraceId("")
            loggingContextProvider.setTenantId("   ")

            // Then: Should not set in MDC
            MDC.get(LoggingContextProvider.TRACE_ID_KEY) shouldBe null
            MDC.get(LoggingContextProvider.TENANT_ID_KEY) shouldBe null
        }

        test("LoggingFormatValidator should work correctly") {
            // Given: A validator
            val validator = LoggingFormatValidator()

            // When: Valid JSON is validated
            val validJson =
                """
                {
                    "@timestamp": "2025-09-29T10:15:30.123Z",
                    "level": "INFO",
                    "logger": "test.Logger",
                    "message": "Test message",
                    "thread": "main",
                    "service_name": "test-service",
                    "trace_id": "trace123",
                    "tenant_id": "tenant123"
                }
                """.trimIndent()

            val result = validator.validateLogEntry(validJson)

            // Then: Should pass validation
            result.isValid shouldBe true
            result.jsonValid shouldBe true
            result.presentContextFields.size shouldBe 3
        }

        test("constants should be defined correctly") {
            LoggingContextProvider.SERVICE_NAME_KEY shouldBe "service_name"
            LoggingContextProvider.TRACE_ID_KEY shouldBe "trace_id"
            LoggingContextProvider.TENANT_ID_KEY shouldBe "tenant_id"
        }
    })
