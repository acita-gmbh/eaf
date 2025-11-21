package com.axians.eaf.framework.multitenancy.test

import com.axians.eaf.framework.multitenancy.TenantContext
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Test filter that ensures TenantContext cleanup after each MockMvc request.
 *
 * **Problem:** withTenantContext() RequestPostProcessor sets TenantContext in request thread,
 * but ThreadLocal persists across requests in the same thread (thread pool reuse).
 *
 * **Solution:** This filter runs LAST in filter chain and ensures TenantContext is always
 * cleared after request completes, preventing cross-contamination between test requests.
 *
 * **Execution Order:**
 * 1. MockMvc request starts
 * 2. withTenantContext() sets TenantContext (in request thread)
 * 3. Application code executes (Query Handlers see TenantContext)
 * 4. THIS FILTER cleans up TenantContext
 * 5. Request completes
 *
 * **Framework Module:** Provides reusable test utility for all product modules.
 *
 * **Story 4.6 AC7:** Enables reliable cross-tenant isolation tests.
 */
@Component
@Profile("test", "rbac-test") // Active for both test profiles
@Order(Int.MAX_VALUE) // Run LAST in filter chain
class TestTenantContextCleanupFilter : Filter {
    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        try {
            chain.doFilter(request, response)
        } finally {
            // Always clean up TenantContext after request
            TenantContext.clearCurrentTenant()
        }
    }
}
