package com.axians.eaf.framework.multitenancy.test

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan

/**
 * Test application for Multi-Tenancy Framework integration tests.
 *
 * Loads Multi-Tenancy module configuration for testing:
 * - TenantContext (ThreadLocal API from Story 4.1)
 * - TenantContextFilter (Layer 1 from Story 4.2)
 * - TenantTestController (Test infrastructure)
 *
 * Provides test-specific beans:
 * - SimpleMeterRegistry for metrics (TenantContextFilter dependency)
 *
 * Epic 4, Story 4.2: Integration test infrastructure
 */
@SpringBootApplication
@ComponentScan(basePackages = ["com.axians.eaf.framework.multitenancy"])
class MultiTenancyTestApplication {
    /**
     * Provide SimpleMeterRegistry for TenantContextFilter metrics.
     * Simpler than full Actuator for integration tests.
     */
    @Bean
    fun meterRegistry() = SimpleMeterRegistry()
}
