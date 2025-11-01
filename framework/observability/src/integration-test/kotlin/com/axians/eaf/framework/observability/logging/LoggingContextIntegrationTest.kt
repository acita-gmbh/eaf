package com.axians.eaf.framework.observability.logging

import com.axians.eaf.framework.observability.ObservabilityTestApplication
import com.axians.eaf.framework.security.tenant.TenantContext
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Integration test for logging context functionality with Spring Boot context.
 * Tests MDC propagation and tenant ID in logs with proper Spring integration.
 *
 * Validates infrastructure patterns from Story 4.6/4.7 approach.
 */
@SpringBootTest(
    classes = [ObservabilityTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.modulith.events.jpa.JpaEventPublicationAutoConfiguration",
        "otel.java.global-autoconfigure.enabled=false",
        "otel.sdk.disabled=true",
        "otel.traces.exporter=none",
        "otel.metrics.exporter=none",
        "otel.logs.exporter=none",
    ],
)
@ActiveProfiles("observability-test")
class LoggingContextIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var tenantContext: TenantContext

    private val logger = LoggerFactory.getLogger(LoggingContextIntegrationTest::class.java)

    init {
        extension(SpringExtension())

        beforeEach {
            // Clear MDC before each test
            MDC.clear()
        }

        afterEach {
            // Ensure MDC is clean after each test
            MDC.clear()
        }

        test("MDC context management should work correctly") {
            // Given: Clean MDC context
            MDC.getCopyOfContextMap() shouldBe null

            // When: Context values are set
            MDC.put("service_name", "test-service")
            MDC.put("trace_id", "test-trace-123")
            MDC.put("tenant_id", "tenant-abc")

            // Then: Values should be retrievable
            MDC.get("service_name") shouldBe "test-service"
            MDC.get("trace_id") shouldBe "test-trace-123"
            MDC.get("tenant_id") shouldBe "tenant-abc"

            // And: Context map should not be empty
            MDC.getCopyOfContextMap() shouldNotBe null
            MDC.getCopyOfContextMap()?.size shouldBe 3
        }

        test("logging at different levels should work") {
            // Given: Context is set
            MDC.put("service_name", "test-service")
            MDC.put("trace_id", "trace-456")

            // When: Logging at different levels
            logger.debug("Debug message for testing")
            logger.info("Info message for testing")
            logger.warn("Warning message for testing")
            logger.error("Error message for testing")

            // Then: No exceptions should occur (basic smoke test)
            // The actual JSON validation will be done in end-to-end tests
            MDC.get("service_name") shouldBe "test-service"
            MDC.get("trace_id") shouldBe "trace-456"
        }

        test("TenantContext integration should work with manual setup") {
            // Given: Manual TenantContext (without Spring)
            val tenantContext = TenantContext()

            // When: Tenant is set
            tenantContext.setCurrentTenantId("manual-tenant-123")
            val currentTenant = tenantContext.current()

            // Then: Tenant should be available
            currentTenant shouldBe "manual-tenant-123"

            // When: Set in MDC
            MDC.put("tenant_id", currentTenant)

            // Then: Should be available in logging context
            MDC.get("tenant_id") shouldBe "manual-tenant-123"

            // Cleanup
            tenantContext.clearCurrentTenant()
        }

        test("context field handling edge cases") {
            // When: Empty values are set
            MDC.put("service_name", "")
            MDC.put("trace_id", "   ")

            // Then: Should handle empty values gracefully
            MDC.get("service_name") shouldBe ""
            MDC.get("trace_id") shouldBe "   "

            // When: Null handling (remove)
            MDC.remove("service_name")
            MDC.remove("trace_id")

            // Then: Should be null after removal
            MDC.get("service_name") shouldBe null
            MDC.get("trace_id") shouldBe null
        }

        test("JSON structure validation should work") {
            // Given: A LoggingFormatValidator
            val validator = LoggingFormatValidator()

            // When: Valid JSON is tested
            val validJson =
                """
                {
                    "@timestamp": "2025-09-29T10:15:30.123Z",
                    "level": "INFO",
                    "logger_name": "com.example.TestLogger",
                    "message": "Test message",
                    "thread_name": "main",
                    "service_name": "test-service",
                    "trace_id": "trace123",
                    "tenant_id": "tenant123"
                }
                """.trimIndent()

            val result = validator.validateLogEntry(validJson)

            // Then: Validation should pass
            result.isValid shouldBe true
            result.jsonValid shouldBe true
            result.missingRequiredFields.size shouldBe 0
        }
    }
}
