package com.axians.eaf.framework.security.tenant

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Utility for extracting tenant context from Spring Security authentication.
 * Provides safe access to tenant information from JWT claims.
 */
object TenantContext {
    /**
     * Extracts the current tenant ID from the Spring Security context.
     * Safely navigates JWT claims to find tenant_id.
     *
     * @return The tenant ID from the authenticated JWT token
     * @throws IllegalStateException if no authenticated context or tenant_id claim found
     */
    fun getCurrentTenantId(): String {
        val authentication =
            SecurityContextHolder.getContext().authentication
                ?: error("No authentication context found")

        check(
            authentication is org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken,
        ) {
            "Authentication is not JWT-based"
        }

        val jwt = authentication.token
        return extractTenantId(jwt)
    }

    /**
     * Extracts tenant ID from JWT token claims.
     * Supports both direct tenant_id claim and nested structures.
     *
     * @param jwt The JWT token to extract tenant ID from
     * @return The tenant ID claim value
     * @throws IllegalStateException if tenant_id claim is missing or invalid
     */
    private fun extractTenantId(jwt: Jwt): String {
        // Try direct tenant_id claim first
        val tenantId = jwt.getClaimAsString("tenant_id")

        if (tenantId?.isNotBlank() == true) {
            return tenantId
        }

        // If not found, this violates our security requirements
        error("Missing or invalid tenant_id claim in JWT token")
    }
}
