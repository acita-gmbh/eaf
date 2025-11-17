package com.axians.eaf.framework.multitenancy.test

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan

/**
 * Test application for Multi-Tenancy Framework integration tests.
 *
 * Loads configuration for testing:
 * - Multi-Tenancy: TenantContext, TenantContextFilter, TenantTestController
 * - Security: JWT validation (required for tenant extraction from JWTs)
 *
 * Provides test-specific beans:
 * - SimpleMeterRegistry for metrics (TenantContextFilter dependency)
 *
 * Note: Security module is required because TenantContextFilter extracts
 * tenant_id from JWTs validated by Spring Security (Epic 3).
 *
 * Epic 4, Story 4.2: Integration test infrastructure
 */
@SpringBootApplication
@ComponentScan(
    basePackages = [
        "com.axians.eaf.framework.multitenancy",
        "com.axians.eaf.framework.security",
    ],
)
open class MultiTenancyTestApplication {
    /**
     * Provide SimpleMeterRegistry for TenantContextFilter metrics.
     * Simpler than full Actuator for integration tests.
     *
     * Note: open modifier required for Spring CGLIB proxies.
     */
    @Bean
    open fun meterRegistry() = SimpleMeterRegistry()
}
