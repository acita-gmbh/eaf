package com.axians.eaf.framework.web.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Secured REST controller demonstrating JWT authentication.
 * Validates Spring Security OAuth2 resource server integration.
 */
@RestController
@RequestMapping("/secure")
@Tag(name = "Security", description = "Authentication and authorization endpoints")
@SecurityRequirement(name = "BearerAuth")
class SecureController {
    /**
     * Secured hello endpoint requiring JWT authentication.
     * Returns JWT claims information for verification.
     */
    @GetMapping("/hello")
    @Operation(
        summary = "Secured hello endpoint",
        description = "Demonstrates JWT authentication with claims extraction",
    )
    fun secureHello(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Map<String, Any>> {
        // Extract JWT claims for verification
        val response =
            mapOf(
                "message" to "Hello from secured endpoint!",
                "user" to
                    mapOf(
                        "id" to jwt.getClaimAsString("sub"),
                        "tenantId" to jwt.getClaimAsString("tenant_id"),
                        "roles" to (jwt.getClaimAsStringList("realm_access.roles") ?: emptyList()),
                        "issuer" to jwt.issuer?.toString(),
                        "audience" to jwt.audience,
                        "issuedAt" to jwt.issuedAt?.epochSecond,
                        "expiresAt" to jwt.expiresAt?.epochSecond,
                    ),
                "timestamp" to System.currentTimeMillis(),
            )

        return ResponseEntity.ok(response)
    }
}
