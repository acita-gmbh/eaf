package com.axians.eaf.framework.multitenancy

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Test controller for validating tenant context extraction.
 * Used by TenantContextFilterIntegrationTest.
 *
 * **SECURITY NOTE:** Located in integration-test source set, never packaged in production.
 * Follows Epic 3 TestController pattern (no @Profile annotation).
 *
 * Epic 4, Story 4.2: Integration test infrastructure
 */
@RestController
@RequestMapping("/test")
open class TenantTestController {
    /**
     * Test endpoint that returns current tenant context.
     * Used to verify TenantContextFilter properly populates ThreadLocal.
     */
    @GetMapping("/tenant-info")
    open fun getTenantInfo(): ResponseEntity<Map<String, String>> {
        val tenantId = TenantContext.current()

        return ResponseEntity.ok(
            mapOf(
                "tenantId" to (tenantId ?: "MISSING"),
                "message" to "Tenant context: $tenantId",
            ),
        )
    }
}
