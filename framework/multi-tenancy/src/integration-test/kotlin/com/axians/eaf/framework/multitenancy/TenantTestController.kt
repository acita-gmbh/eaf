package com.axians.eaf.framework.multitenancy

import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Test controller for validating tenant context extraction.
 * Used by TenantContextFilterIntegrationTest.
 *
 * **SECURITY NOTE:** @Profile("test") prevents production deployment.
 *
 * Epic 4, Story 4.2: Integration test infrastructure
 */
@RestController
@RequestMapping("/test")
@Profile("test")
class TenantTestController {
    /**
     * Test endpoint that returns current tenant context.
     * Used to verify TenantContextFilter properly populates ThreadLocal.
     */
    @GetMapping("/tenant-info")
    fun getTenantInfo(): ResponseEntity<Map<String, String>> {
        val tenantId = TenantContext.current()

        return ResponseEntity.ok(
            mapOf(
                "tenantId" to (tenantId ?: "MISSING"),
                "message" to "Tenant context: $tenantId",
            ),
        )
    }
}
