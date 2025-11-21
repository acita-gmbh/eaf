package com.axians.eaf.framework.multitenancy

import com.axians.eaf.framework.core.exceptions.TenantIsolationException
import com.axians.eaf.framework.multitenancy.test.TenantValidationTestApplication
import com.axians.eaf.framework.multitenancy.test.TestAggregate
import com.axians.eaf.framework.multitenancy.test.TestCommand
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.config.EventProcessingConfigurer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * Integration test for TenantValidationInterceptor (Layer 2 tenant isolation).
 *
 * Tests the fail-closed validation logic that prevents cross-tenant command execution:
 * - AC2: TenantContext.getCurrentTenantId() must match command.tenantId
 * - AC4: Tenant mismatch → TenantIsolationException
 * - AC5: Missing TenantContext → TenantIsolationException
 * - AC6: Tenant A cannot modify Tenant B aggregates
 * - AC7: Metrics emitted on validation failures
 *
 * Epic 4, Story 4.3: AC2, AC4, AC5, AC6, AC7
 */
@SpringBootTest(classes = [TenantValidationTestApplication::class])
@ActiveProfiles("test")
class TenantValidationInterceptorIntegrationTest {
    @Autowired
    private lateinit var commandGateway: CommandGateway

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @BeforeEach
    fun beforeEach() {
        // Clear tenant context before each test
        TenantContext.clearCurrentTenant()

        // Reset metrics
        if (meterRegistry is SimpleMeterRegistry) {
            meterRegistry.clear()
        }
    }

    @AfterEach
    fun afterEach() {
        // Cleanup tenant context
        TenantContext.clearCurrentTenant()
    }

    @Nested
    inner class `AC2 & AC4 - Tenant context validation` {
        @Test
        fun `should allow command when tenant context matches command tenantId`() {
            // Given: Tenant A context is set
            TenantContext.setCurrentTenantId("tenant-a")
            val aggregateId = UUID.randomUUID().toString()
            val command =
                TestCommand(
                    aggregateId = aggregateId,
                    name = "Test Widget",
                    tenantId = "tenant-a",
                )

            // When: Command is sent with matching tenant
            val result = commandGateway.sendAndWait<String>(command)

            // Then: Command succeeds
            assertThat(result).isEqualTo(aggregateId)
        }

        @Test
        fun `should reject command when tenant context MISMATCHES command tenantId`() {
            // Given: Tenant A context is set
            TenantContext.setCurrentTenantId("tenant-a")
            val command =
                TestCommand(
                    aggregateId = UUID.randomUUID().toString(),
                    name = "Test Widget",
                    tenantId = "tenant-b", // Mismatch!
                )

            // When/Then: Command is rejected with TenantIsolationException
            val exception =
                assertThrows<TenantIsolationException> {
                    commandGateway.sendAndWait<String>(command)
                }

            // AC4: Generic error message (CWE-209 protection)
            assertThat(exception.message).contains("Access denied: tenant context mismatch")
        }
    }

    @Nested
    inner class `AC5 - Fail-closed validation for missing context` {
        @Test
        fun `should reject command when TenantContext is NOT set`() {
            // Given: NO tenant context is set
            // TenantContext.clearCurrentTenant() already called in beforeEach

            val command =
                TestCommand(
                    aggregateId = UUID.randomUUID().toString(),
                    name = "Test Widget",
                    tenantId = "tenant-a",
                )

            // When/Then: Command is rejected
            val exception =
                assertThrows<TenantIsolationException> {
                    commandGateway.sendAndWait<String>(command)
                }

            // AC5: Fail-closed with generic error
            assertThat(exception.message).contains("Tenant context not set")
        }
    }

    @Nested
    inner class `AC6 - Cross-tenant command isolation` {
        @Test
        fun `tenant A should successfully modify tenant A aggregates`() {
            // Given: Tenant A context
            TenantContext.setCurrentTenantId("tenant-a")
            val aggregateId = UUID.randomUUID().toString()

            // When: Tenant A creates an aggregate
            val command =
                TestCommand(
                    aggregateId = aggregateId,
                    name = "Tenant A Widget",
                    tenantId = "tenant-a",
                )
            val result = commandGateway.sendAndWait<String>(command)

            // Then: Command succeeds
            assertThat(result).isEqualTo(aggregateId)
        }

        @Test
        fun `tenant A should FAIL to modify tenant B aggregates`() {
            // Given: Tenant B context is set
            TenantContext.setCurrentTenantId("tenant-b")
            val aggregateId = UUID.randomUUID().toString()

            // When: Attempt to execute command for Tenant A
            val command =
                TestCommand(
                    aggregateId = aggregateId,
                    name = "Cross-tenant Attack",
                    tenantId = "tenant-a", // Different tenant!
                )

            // Then: Command is rejected
            assertThrows<TenantIsolationException> {
                commandGateway.sendAndWait<String>(command)
            }
        }
    }

    @Nested
    inner class `AC7 - Validation metrics` {
        @Test
        fun `should increment tenant_validation_failures counter on mismatch`() {
            // Given: Tenant A context
            TenantContext.setCurrentTenantId("tenant-a")
            val command =
                TestCommand(
                    aggregateId = UUID.randomUUID().toString(),
                    name = "Test",
                    tenantId = "tenant-b",
                )

            // When: Command with mismatch is attempted
            assertThrows<TenantIsolationException> {
                commandGateway.sendAndWait<String>(command)
            }

            // Then: Metric is incremented
            val counter = meterRegistry.counter("tenant.validation.failures")
            assertThat(counter.count()).isEqualTo(1.0)
        }

        @Test
        fun `should increment tenant_mismatch_attempts counter on cross-tenant attempt`() {
            // Given: Tenant B context
            TenantContext.setCurrentTenantId("tenant-b")
            val command =
                TestCommand(
                    aggregateId = UUID.randomUUID().toString(),
                    name = "Test",
                    tenantId = "tenant-a",
                )

            // When: Cross-tenant command is attempted
            assertThrows<TenantIsolationException> {
                commandGateway.sendAndWait<String>(command)
            }

            // Then: Metric is incremented
            val counter = meterRegistry.counter("tenant.mismatch.attempts")
            assertThat(counter.count()).isEqualTo(1.0)
        }
    }

    @Nested
    inner class `Commands without TenantAwareCommand interface` {
        @Test
        fun `should bypass validation for non-tenant-aware commands`() {
            // This test verifies that commands NOT implementing TenantAwareCommand
            // are not validated (safe bypass for system commands)
            //
            // This scenario will be tested once we have system commands in Epic 5+
            // For now, we document the expected behavior
        }
    }
}

/**
 * Test configuration for TenantValidationInterceptorIntegrationTest.
 */
@Configuration
open class TenantValidationTestConfig {
    /**
     * Provide SimpleMeterRegistry for metrics validation in tests.
     */
    @Bean
    open fun testMeterRegistry(): MeterRegistry = SimpleMeterRegistry()
}
