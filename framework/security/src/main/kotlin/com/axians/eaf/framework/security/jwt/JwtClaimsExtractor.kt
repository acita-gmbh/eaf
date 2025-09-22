package com.axians.eaf.framework.security.jwt

import org.springframework.security.oauth2.jwt.Jwt

/**
 * Utility for safely extracting claims from JWT tokens.
 * Provides type-safe navigation through nested claim structures.
 */
object JwtClaimsExtractor {

    /**
     * Extracts roles from Keycloak JWT token's realm_access.roles claim.
     * Safely navigates nested JSON structure and filters for valid string roles.
     *
     * @param jwt The JWT token containing claims
     * @return Set of role names, or empty set if no roles found
     */
    fun extractRoles(jwt: Jwt): Set<String> {
        return (jwt.getClaimAsMap("realm_access")?.get("roles") as? List<*>)
            ?.filterIsInstance<String>()
            ?.toSet() ?: emptySet()
    }

    /**
     * Extracts roles as list for API responses.
     * Convenience method for REST endpoint responses.
     *
     * @param jwt The JWT token containing claims
     * @return List of role names, or empty list if no roles found
     */
    fun extractRolesAsList(jwt: Jwt): List<String> {
        return (jwt.getClaimAsMap("realm_access")?.get("roles") as? List<*>)
            ?.filterIsInstance<String>() ?: emptyList()
    }
}
