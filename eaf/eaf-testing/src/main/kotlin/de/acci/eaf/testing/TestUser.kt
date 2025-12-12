package de.acci.eaf.testing

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * Represents a user in the test environment.
 */
public data class TestUser(
    /** The user's unique ID */
    val id: UserId,
    /** The tenant this user belongs to */
    val tenantId: TenantId,
    /** The user's email address */
    val email: String,
    /** List of roles assigned to the user */
    val roles: List<String>
)
