package com.axians.eaf.framework.multitenancy.test

import com.axians.eaf.framework.security.config.SecurityConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import

/**
 * Test application for Multi-Tenancy Framework integration tests.
 *
 * Loads configuration for testing:
 * - Multi-Tenancy: TenantContext, TenantContextFilter, TenantTestController
 * - Security: JWT validation (required for tenant extraction from JWTs)
 *
 * **CRITICAL FIX (Story 4.2):**
 * - Removed SimpleMeterRegistry bean (conflicted with auto-configuration)
 * - Added @Import(SecurityConfiguration) for explicit security loading
 * - Spring Boot's SimpleMetricsExportAutoConfiguration now provides MeterRegistry
 *
 * **Why SimpleMeterRegistry was removed:**
 * Custom MeterRegistry bean satisfied @ConditionalOnMissingBean, disabling
 * Spring Boot's metrics auto-configuration. This had cascading effects that
 * prevented SecurityAutoConfiguration and OAuth2ResourceServerAutoConfiguration
 * from loading, resulting in no JWT validation and empty SecurityContextHolder.
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
@Import(SecurityConfiguration::class)
open class MultiTenancyTestApplication
// No custom beans needed - rely on Spring Boot auto-configuration
