package com.axians.eaf.framework.multitenancy.test

import org.springframework.test.web.servlet.request.RequestPostProcessor

/**
 * MockMvc RequestPostProcessor that propagates TenantContext to request thread.
 *
 * **Problem:** TenantContext is ThreadLocal. When tests set context in test thread,
 * MockMvc requests run in different thread (Servlet container thread) and lose context.
 *
 * **Solution:** Sets request attribute that TestTenantContextPropagationFilter reads
 * to set TenantContext in the request thread.
 *
 * **Usage (Explicit Per-Request):**
 * ```kotlin
 * import com.axians.eaf.framework.multitenancy.test.withTenantContext
 *
 * mockMvc
 *     .get("/api/v1/widgets") {
 *         with(withTenantContext("tenant-a"))
 *     }
 *     .andExpect { status { isOk() } }
 * ```
 *
 * **Usage (Works with @BeforeEach):**
 * ```kotlin
 * @BeforeEach
 * fun beforeEach() {
 *     TenantContext.setCurrentTenantId("test-tenant")
 * }
 *
 * @Test
 * fun myTest() {
 *     // MockMvc calls automatically inherit tenant from @BeforeEach!
 *     mockMvc.get("/api/v1/widgets") {}
 *         .andExpect { status { isOk() } }
 * }
 * ```
 *
 * **Cleanup:** TestTenantContextCleanupFilter clears context after each request.
 *
 * **Framework Module:** Provides reusable test utility for all product modules.
 *
 * **Story 4.6 AC7:** Enables cross-tenant isolation tests with zero test changes!
 */
fun withTenantContext(tenantId: String): RequestPostProcessor =
    RequestPostProcessor { request ->
        // Set request attribute for TestTenantContextPropagationFilter to read
        request.setAttribute(TestTenantContextPropagationFilter.REQUEST_ATTRIBUTE_TENANT_ID, tenantId)
        request
    }
