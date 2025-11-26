package de.acci.eaf.auth

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * User information retrieved from identity provider.
 *
 * This represents the user profile information that can be fetched
 * via OIDC UserInfo endpoint or extracted from ID tokens.
 *
 * @property id The unique user identifier.
 * @property tenantId The tenant the user belongs to.
 * @property email The user's email address.
 * @property name The user's display name.
 * @property givenName The user's first/given name.
 * @property familyName The user's last/family name.
 * @property emailVerified Whether the email has been verified.
 * @property roles Set of roles assigned to the user.
 */
public data class UserInfo(
    val id: UserId,
    val tenantId: TenantId,
    val email: String,
    val name: String?,
    val givenName: String?,
    val familyName: String?,
    val emailVerified: Boolean,
    val roles: Set<String>,
)
