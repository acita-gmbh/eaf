package com.axians.eaf.framework.security.test

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Test controller for Security Framework integration tests.
 *
 * Provides endpoints for testing security configuration:
 * - /api/widgets: Protected endpoint (requires authentication)
 *
 * Story 3.1: Spring Security OAuth2 Resource Server Foundation
 */
@RestController
@RequestMapping("/api/widgets")
class TestController {
    /**
     * Provide a simple status map used as a protected test endpoint.
     *
     * @return A map with a single entry: "status" mapped to "ok".
     */
    @GetMapping
    fun getWidgets(): Map<String, String> = mapOf("status" to "ok")
}