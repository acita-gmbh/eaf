package de.acci.eaf.testing

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

public data class TestUser(
    val id: UserId,
    val tenantId: TenantId,
    val email: String,
    val roles: List<String>
)
