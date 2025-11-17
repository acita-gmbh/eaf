package com.axians.eaf.framework.multitenancy

import org.springframework.modulith.ApplicationModule

/**
 * Multi-Tenancy Module for EAF Framework.
 *
 * Provides ThreadLocal-based tenant context management with 3-layer isolation:
 * - Layer 1: TenantContextFilter (HTTP filter, Story 4.2)
 * - Layer 2: Axon command validation (Story 4.3)
 * - Layer 3: PostgreSQL RLS (Story 4.4)
 *
 * Epic 4, Story 4.1: TenantContext and ThreadLocal Management
 *
 * @since 1.0.0
 */
@ApplicationModule(
    displayName = "EAF Multi-Tenancy Module",
    allowedDependencies = ["core"],
)
class MultiTenancyModule
