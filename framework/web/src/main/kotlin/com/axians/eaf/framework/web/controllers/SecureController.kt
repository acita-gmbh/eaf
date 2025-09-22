package com.axians.eaf.framework.web.controllers

import com.axians.eaf.framework.security.dto.SecureEndpointResponse
import com.axians.eaf.framework.security.dto.UserClaimsDto
import com.axians.eaf.framework.security.jwt.JwtClaimsExtractor
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
    ): ResponseEntity<SecureEndpointResponse> {
        // Extract JWT claims for verification
        val userClaims =
            UserClaimsDto(
                id = jwt.getClaimAsString("sub"),
                tenantId = jwt.getClaimAsString("tenant_id"),
                roles = JwtClaimsExtractor.extractRolesAsList(jwt),
                issuer = jwt.issuer?.toString(),
                audience = jwt.audience,
                issuedAt = jwt.issuedAt?.epochSecond,
                expiresAt = jwt.expiresAt?.epochSecond,
            )

        val response =
            SecureEndpointResponse(
                message = "Hello from secured endpoint!",
                user = userClaims,
                timestamp = System.currentTimeMillis(),
            )

        return ResponseEntity.ok(response)
    }
}
