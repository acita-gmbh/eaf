package com.axians.eaf.framework.multitenancy.test

import com.axians.eaf.framework.multitenancy.TenantContext
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Test filter that propagates TenantContext from @BeforeEach to MockMvc request threads.
 *
 * **Problem:** @BeforeEach sets TenantContext in TEST thread, but MockMvc runs in REQUEST thread.
 * ThreadLocal doesn't cross thread boundaries!
 *
 * **Solution:**
 * 1. Tests set TenantContext in @BeforeEach (test thread)
 * 2. This filter checks if TenantContext exists in CURRENT (request) thread
 * 3. If missing, checks TEST_TENANT_ID request attribute (set by withTenantContext())
 * 4. Sets TenantContext in request thread for Query Handlers
 *
 * **Backward Compatible:**
 * - Works with existing @BeforeEach pattern (no changes needed!)
 * - Works with new withTenantContext() pattern (explicit per-request)
 * - If both present, withTenantContext() wins (request-level override)
 *
 * **Execution Order:**
 * 1. MockMvc request starts (request thread)
 * 2. Optional: withTenantContext() sets request attribute
 * 3. THIS FILTER reads attribute → sets TenantContext in request thread
 * 4. Application code executes (Query Handlers see TenantContext)
 * 5. TestTenantContextCleanupFilter cleans up
 *
 * **Framework Module:** Provides seamless test utility for all product modules.
 *
 * **Story 4.6 AC7:** Enables cross-tenant isolation tests with zero test changes!
 */
@Component
@Profile("test", "rbac-test") // Active for both test profiles
@Order(1) // Run FIRST (before security filters)
class TestTenantContextPropagationFilter : Filter {
    companion object {
        const val REQUEST_ATTRIBUTE_TENANT_ID = "X-Test-Tenant-Id"
    }

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        // Check if TenantContext already set in THIS thread
        val existingContext = TenantContext.current()

        if (existingContext == null) {
            // Not set in request thread - try multiple sources:
            // 1. Request attribute (from withTenantContext() explicit call)
            var tenantId = (request as? HttpServletRequest)?.getAttribute(REQUEST_ATTRIBUTE_TENANT_ID) as? String

            // 2. TestTenantContextHolder (from @BeforeEach pattern)
            if (tenantId == null) {
                tenantId = TestTenantContextHolder.getTestTenantId()
            }

            if (tenantId != null) {
                // Set context in request thread
                TenantContext.setCurrentTenantId(tenantId)
            }
        }

        // Continue filter chain (TestTenantContextCleanupFilter will clean up)
        chain.doFilter(request, response)
    }
}
