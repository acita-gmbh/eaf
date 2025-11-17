package com.axians.eaf.framework.multitenancy.test

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ComponentScan

/**
 * Test application for Multi-Tenancy Framework integration tests.
 *
 * Loads Multi-Tenancy module configuration for testing:
 * - TenantContext (ThreadLocal API from Story 4.1)
 * - TenantContextFilter (Layer 1 from Story 4.2)
 * - TenantTestController (Test infrastructure)
 *
 * Epic 4, Story 4.2: Integration test infrastructure
 */
@SpringBootApplication
@ComponentScan(basePackages = ["com.axians.eaf.framework.multitenancy"])
class MultiTenancyTestApplication
