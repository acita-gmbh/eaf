package com.axians.eaf.products.widgetdemo.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter

/**
 * JWT Authentication Configuration
 *
 * Story 9.1: Extract authorities from Keycloak realm_access.roles
 *
 * Keycloak JWT structure:
 * ```json
 * {
 *   "realm_access": {
 *     "roles": ["USER", "widget:read", "widget:create"]
 *   }
 * }
 * ```
 *
 * Spring Security authorities needed:
 * - ROLE_USER (for @PreAuthorize("hasRole('USER')"))
 * - widget:read (for @PreAuthorize("hasAuthority('widget:read')"))
 * - widget:create, etc.
 */
@Configuration
class JwtConfig {
    @Bean
    fun jwtAuthenticationConverter(): Converter<Jwt, AbstractAuthenticationToken> {
        val jwtAuthenticationConverter = JwtAuthenticationConverter()
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter())
        return jwtAuthenticationConverter
    }

    @Suppress("UNCHECKED_CAST")
    private fun jwtGrantedAuthoritiesConverter(): Converter<Jwt, Collection<GrantedAuthority>> =
        Converter { jwt ->
            // Extract roles from Keycloak's realm_access.roles claim
            val realmAccess = jwt.claims["realm_access"] as? Map<String, Any>
            val roles = (realmAccess?.get("roles") as? List<String>) ?: emptyList()

            roles.flatMap { role ->
                // Return both:
                // 1. ROLE_<name> for hasRole() checks
                // 2. Original role for hasAuthority() checks
                if (role.contains(":")) {
                    // Permission-style (widget:read) - keep as-is
                    listOf(SimpleGrantedAuthority(role))
                } else {
                    // Role-style (USER) - add ROLE_ prefix AND keep original
                    listOf(
                        SimpleGrantedAuthority("ROLE_$role"),
                        SimpleGrantedAuthority(role),
                    )
                }
            }
        }
}
