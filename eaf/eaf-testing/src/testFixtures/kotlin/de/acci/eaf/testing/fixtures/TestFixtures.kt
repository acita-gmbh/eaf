package de.acci.eaf.testing.fixtures

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.testing.TestUser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.Date
import java.util.UUID

public object TestTenantFixture {
    public fun createTenant(name: String = "Test Tenant"): TenantId {
        return TenantId.generate()
    }
}

public object TestUserFixture {
    public fun createUser(
        tenantId: TenantId,
        role: String = "USER"
    ): TestUser {
        return TestUser(
            id = UserId.generate(),
            tenantId = tenantId,
            email = "test-${UUID.randomUUID()}@example.com",
            roles = listOf(role)
        )
    }

    public fun generateJwt(user: TestUser): String {
        val now = Date()
        val expiry = Date(now.time + 3600000) // 1 hour
        
        // Fixed test key for HMAC SHA256
        val secret = "test-secret-key-must-be-at-least-256-bits-long-so-make-it-long"
        val key = Keys.hmacShaKeyFor(secret.toByteArray())

        return Jwts.builder()
            .subject(user.id.value.toString())
            .claim("tenant_id", user.tenantId.value.toString())
            .claim("roles", user.roles)
            .claim("email", user.email)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }
}
