package com.axians.eaf.framework.multitenancy.test

import com.axians.eaf.framework.multitenancy.TenantContext
import org.springframework.test.web.servlet.request.RequestPostProcessor

/**
 * MockMvc RequestPostProcessor that sets TenantContext in the request thread.
 *
 * **Problem:** TenantContext is ThreadLocal. Tests run in one thread, MockMvc requests
 * run in a different thread (Servlet container thread pool). ThreadLocal doesn't cross boundaries!
 *
 * **Solution:** This RequestPostProcessor executes IN THE REQUEST THREAD, allowing it to
 * set TenantContext where Query Handlers actually need it.
 *
 * **Usage:**
 * ```kotlin
 * import com.axians.eaf.framework.multitenancy.test.withTenantContext
 *
 * mockMvc
 *     .get("/api/v1/widgets") {
 *         with(withTenantContext("tenant-a"))
 *     }
 *     .andExpect { status { isOk() } }
 *
 * // Can combine with other RequestPostProcessors:
 * mockMvc
 *     .post("/api/v1/widgets") {
 *         with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN")))
 *         with(withTenantContext("tenant-a"))
 *         contentType = MediaType.APPLICATION_JSON
 *         content = requestBody
 *     }
 * ```
 *
 * **Cleanup:** TestTenantContextCleanupFilter automatically clears context after each request.
 *
 * **Framework Module:** Reusable test utility for all product modules.
 *
 * **Story 4.6 AC7:** Enables cross-tenant isolation testing with MockMvc.
 */
fun withTenantContext(tenantId: String): RequestPostProcessor =
    RequestPostProcessor { request ->
        // Executes in REQUEST thread - set TenantContext where it's needed
        TenantContext.setCurrentTenantId(tenantId)
        request
    }
