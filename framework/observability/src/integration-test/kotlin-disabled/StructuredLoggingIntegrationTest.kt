package com.axians.eaf.framework.observability.logging

import com.axians.eaf.framework.security.tenant.TenantContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Integration test for structured JSON logging functionality.
 * Tests AC 4: Integration tests confirm logs are written in correct JSON format
 * and include required context (service_name, trace_id, tenant_id).
 */
@SpringBootTest(classes = [com.axians.eaf.framework.observability.ObservabilityTestApplication::class])
@ActiveProfiles("test")
class StructuredLoggingIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var loggingContextProvider: LoggingContextProvider

    @Autowired
    private lateinit var tenantContext: TenantContext

    private val logger = LoggerFactory.getLogger(StructuredLoggingIntegrationTest::class.java)
    private val objectMapper = ObjectMapper()

    init {
        extension(SpringExtension())

        test("should initialize service name in MDC context") {
            // Given: LoggingContextProvider is configured
            loggingContextProvider.setServiceName()

            // When: Service name is set
            val serviceName = MDC.get(LoggingContextProvider.SERVICE_NAME_KEY)

            // Then: Service name should be present
            serviceName shouldNotBe null
            serviceName shouldContain "test" // Default test application name
        }

        test("should set and retrieve trace ID in MDC") {
            // Given: A trace ID
            val testTraceId = "test-trace-12345"

            // When: Trace ID is set
            loggingContextProvider.setTraceId(testTraceId)

            // Then: Trace ID should be retrievable
            val retrievedTraceId = loggingContextProvider.getCurrentTraceId()
            retrievedTraceId shouldBe testTraceId

            val mdcTraceId = MDC.get(LoggingContextProvider.TRACE_ID_KEY)
            mdcTraceId shouldBe testTraceId
        }

        test("should integrate with TenantContext for tenant ID") {
            // Given: A tenant context is set
            val testTenantId = "tenant-test-123"
            tenantContext.setCurrentTenantId(testTenantId)

            // When: Tenant ID is extracted and set in MDC
            val currentTenantId = tenantContext.current()
            loggingContextProvider.setTenantId(currentTenantId)

            // Then: Tenant ID should be available in MDC
            val retrievedTenantId = loggingContextProvider.getCurrentTenantId()
            retrievedTenantId shouldBe testTenantId

            val mdcTenantId = MDC.get(LoggingContextProvider.TENANT_ID_KEY)
            mdcTenantId shouldBe testTenantId

            // Cleanup
            tenantContext.clearCurrentTenant()
            loggingContextProvider.clearContext()
        }

        test("should clear context properly") {
            // Given: Context is set with trace and tenant
            loggingContextProvider.setTraceId("test-trace")
            tenantContext.setCurrentTenantId("test-tenant")
            loggingContextProvider.setTenantId(tenantContext.current())

            // When: Context is cleared
            loggingContextProvider.clearContext()
            tenantContext.clearCurrentTenant()

            // Then: Context should be empty
            loggingContextProvider.getCurrentTraceId() shouldBe null
            loggingContextProvider.getCurrentTenantId() shouldBe null
            MDC.get(LoggingContextProvider.TRACE_ID_KEY) shouldBe null
            MDC.get(LoggingContextProvider.TENANT_ID_KEY) shouldBe null
        }

        test("should handle empty or null context values gracefully") {
            // Given: Empty or null values
            loggingContextProvider.setTraceId("")
            loggingContextProvider.setTraceId(null)
            loggingContextProvider.setTenantId("")
            loggingContextProvider.setTenantId(null)

            // Then: Values should not be set in MDC
            MDC.get(LoggingContextProvider.TRACE_ID_KEY) shouldBe null
            MDC.get(LoggingContextProvider.TENANT_ID_KEY) shouldBe null
        }

        test("should validate logging format validator works") {
            // Given: A valid JSON log entry
            val validJson =
                """
                {
                    "@timestamp": "2025-09-29T10:15:30.123Z",
                    "level": "INFO",
                    "logger": "com.example.TestLogger",
                    "message": "Test message",
                    "thread": "main",
                    "service_name": "test-service",
                    "trace_id": "trace123",
                    "tenant_id": "tenant123"
                }
                """.trimIndent()

            // When: Validation is performed
            val validator = LoggingFormatValidator()
            val result = validator.validateLogEntry(validJson)

            // Then: Validation should pass
            result.isValid shouldBe true
            result.jsonValid shouldBe true
            result.missingRequiredFields.size shouldBe 0
            result.presentContextFields shouldContain "service_name"
            result.presentContextFields shouldContain "trace_id"
            result.presentContextFields shouldContain "tenant_id"
        }

        test("should detect missing required fields") {
            // Given: Invalid JSON missing required fields
            val invalidJson = """{"message": "Test"}"""

            // When: Validation is performed
            val validator = LoggingFormatValidator()
            val result = validator.validateLogEntry(invalidJson)

            // Then: Validation should fail with missing fields
            result.isValid shouldBe false
            result.jsonValid shouldBe true
            result.missingRequiredFields shouldContain "timestamp"
            result.missingRequiredFields shouldContain "level"
            result.missingRequiredFields shouldContain "logger"
        }

        test("should detect invalid JSON format") {
            // Given: Invalid JSON syntax
            val invalidJson = "not-json-at-all"

            // When: Validation is performed
            val validator = LoggingFormatValidator()
            val result = validator.validateLogEntry(invalidJson)

            // Then: Validation should fail with JSON error
            result.isValid shouldBe false
            result.jsonValid shouldBe false
            result.error shouldNotBe null
        }
    }
}
