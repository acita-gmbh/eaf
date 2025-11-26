package de.acci.eaf.auth

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import java.time.Instant

/**
 * Extracted claims from a validated identity token.
 *
 * This data class provides a vendor-agnostic representation of JWT claims
 * that are relevant to the application. Identity providers (Keycloak, Auth0, etc.)
 * extract their specific token structure into this common format.
 *
 * @property subject The unique user identifier (JWT 'sub' claim).
 * @property tenantId The tenant identifier for multi-tenancy support.
 * @property roles Set of roles assigned to the user (realm + client roles).
 * @property email The user's email address.
 * @property expiresAt Token expiration timestamp.
 * @property issuedAt Token issuance timestamp.
 * @property issuer The token issuer (e.g., Keycloak realm URL).
 */
public data class TokenClaims(
    val subject: UserId,
    val tenantId: TenantId,
    val roles: Set<String>,
    val email: String?,
    val expiresAt: Instant,
    val issuedAt: Instant,
    val issuer: String,
) {
    /**
     * Checks if the token is expired at the given instant.
     * @param at The instant to check against (defaults to now).
     * @return true if token is expired, false otherwise.
     */
    public fun isExpired(at: Instant = Instant.now()): Boolean = expiresAt.isBefore(at)

    /**
     * Checks if the user has a specific role.
     * @param role The role to check (case-sensitive).
     * @return true if user has the role, false otherwise.
     */
    public fun hasRole(role: String): Boolean = roles.contains(role)

    /**
     * Checks if the user has any of the specified roles.
     * @param requiredRoles The roles to check.
     * @return true if user has at least one of the roles.
     */
    public fun hasAnyRole(vararg requiredRoles: String): Boolean =
        requiredRoles.any { roles.contains(it) }

    /**
     * Checks if the user has all of the specified roles.
     * @param requiredRoles The roles to check.
     * @return true if user has all specified roles.
     */
    public fun hasAllRoles(vararg requiredRoles: String): Boolean =
        requiredRoles.all { roles.contains(it) }
}
