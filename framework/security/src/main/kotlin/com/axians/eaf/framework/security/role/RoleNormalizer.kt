package com.axians.eaf.framework.security.role

import com.axians.eaf.framework.security.config.KeycloakOidcConfiguration
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

/**
 * Normalizes Keycloak JWT role structures into Spring Security GrantedAuthority values.
 *
 * Responsibilities:
 * - Extract roles from realm_access.roles and resource_access.{client}.roles
 * - Flatten nested collections, ignoring null or unsupported shapes
 * - Ensure ROLE_ prefix for traditional/realm roles so @PreAuthorize("hasRole(..)") works
 * - Preserve permission-style entries (e.g., widget:create) without prefixing
 */
@Component
class RoleNormalizer(
    private val keycloakConfig: KeycloakOidcConfiguration,
) {
    companion object {
        private const val ROLE_PREFIX = "ROLE_"
    }

    /**
     * Extracts and normalizes all roles contained in the JWT.
     *
     * @param jwt JWT provided by Spring Security's resource server pipeline
     * @return Set of unique GrantedAuthority instances representing normalized roles/permissions
     */
    fun normalize(jwt: Jwt): Set<GrantedAuthority> {
        val collectedRoles = linkedSetOf<String>()

        extractRealmRoles(jwt, collectedRoles)
        extractResourceRoles(jwt, collectedRoles)

        val authorities = linkedMapOf<String, GrantedAuthority>()

        collectedRoles.forEach { rawRole ->
            toAuthority(rawRole)?.let { authority ->
                val key = authority.authority.lowercase()
                authorities.putIfAbsent(key, authority)
            }
        }

        return authorities.values.toSet()
    }

    private fun extractRealmRoles(
        jwt: Jwt,
        target: MutableCollection<String>,
    ) {
        val realmAccess = jwt.getClaim("realm_access") as? Map<*, *> ?: return
        extractClaimRoles(realmAccess["roles"], target)
    }

    private fun extractResourceRoles(
        jwt: Jwt,
        target: MutableCollection<String>,
    ) {
        val resourceAccess = jwt.getClaim("resource_access") as? Map<*, *>
        val targetClientId = keycloakConfig.audience.takeIf { it.isNotBlank() }

        if (resourceAccess != null && targetClientId != null) {
            val clientEntry = resourceAccess[targetClientId] as? Map<*, *>
            clientEntry?.let { extractClaimRoles(it["roles"], target) }
        }
    }

    private fun extractClaimRoles(
        value: Any?,
        target: MutableCollection<String>,
    ) {
        when (value) {
            null -> return
            is String -> target.add(value)
            is Collection<*> -> value.forEach { extractClaimRoles(it, target) }
            is Array<*> -> value.forEach { extractClaimRoles(it, target) }
            else -> Unit // Ignore unsupported types (defensive hardening for fuzz/property tests)
        }
    }

    private fun toAuthority(rawRole: String): GrantedAuthority? {
        val trimmed = rawRole.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        val authorityValue =
            when {
                trimmed.contains(":") -> trimmed // Permission-style (widget:create) - no prefixing
                trimmed.startsWith(ROLE_PREFIX, ignoreCase = true) ->
                    ROLE_PREFIX + trimmed.substring(ROLE_PREFIX.length)
                else -> ROLE_PREFIX + trimmed
            }

        return SimpleGrantedAuthority(authorityValue)
    }
}
