package de.acci.eaf.auth.keycloak

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import reactor.core.publisher.Mono

/**
 * Custom JWT authentication converter for Keycloak.
 *
 * Extracts roles from Keycloak's JWT structure:
 * - realm_access.roles: Realm-level roles
 * - resource_access.{clientId}.roles: Client-specific roles
 *
 * Roles are mapped to Spring Security authorities with ROLE_ prefix.
 *
 * @property clientId The OAuth2 client ID for extracting client-specific roles.
 * @property rolePrefix Prefix for Spring Security authorities (default: "ROLE_").
 */
public class KeycloakJwtAuthenticationConverter(
    private val clientId: String,
    private val rolePrefix: String = "ROLE_",
) : Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    override fun convert(jwt: Jwt): Mono<AbstractAuthenticationToken> {
        val authorities = extractAuthorities(jwt)
        return Mono.just(JwtAuthenticationToken(jwt, authorities))
    }

    /**
     * Extracts authorities from JWT claims.
     * Combines realm roles and client roles, applying the configured prefix.
     */
    private fun extractAuthorities(jwt: Jwt): Collection<GrantedAuthority> {
        val authorities = mutableListOf<GrantedAuthority>()

        // Extract realm roles
        extractRealmRoles(jwt).forEach { role ->
            authorities.add(SimpleGrantedAuthority("$rolePrefix$role"))
        }

        // Extract client roles
        extractClientRoles(jwt).forEach { role ->
            authorities.add(SimpleGrantedAuthority("$rolePrefix$role"))
        }

        return authorities
    }

    /**
     * Extracts roles from realm_access.roles claim.
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractRealmRoles(jwt: Jwt): List<String> {
        val realmAccess = jwt.getClaim<Map<String, Any>>("realm_access") ?: return emptyList()
        return (realmAccess["roles"] as? List<String>) ?: emptyList()
    }

    /**
     * Extracts roles from resource_access.{clientId}.roles claim.
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractClientRoles(jwt: Jwt): List<String> {
        val resourceAccess = jwt.getClaim<Map<String, Any>>("resource_access") ?: return emptyList()
        val clientAccess = resourceAccess[clientId] as? Map<String, Any> ?: return emptyList()
        return (clientAccess["roles"] as? List<String>) ?: emptyList()
    }
}
