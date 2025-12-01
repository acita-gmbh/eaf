package de.acci.eaf.testing.fixtures

import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.EventMetadata
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
    /**
     * Shared test secret for JWT signing/verification.
     * Must be at least 256 bits (32 bytes) for HMAC-SHA256.
     */
    public const val TEST_JWT_SECRET: String =
        "test-secret-key-must-be-at-least-256-bits-long-so-make-it-long"

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

        val key = Keys.hmacShaKeyFor(TEST_JWT_SECRET.toByteArray())

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

/**
 * Factory for creating EventMetadata in tests.
 *
 * Reduces boilerplate by providing sensible defaults for all metadata fields.
 * Use the parameterized version when specific tenant/user context is needed.
 */
public object TestMetadataFactory {
    /**
     * Creates EventMetadata with randomly generated IDs.
     *
     * Useful for simple unit tests where specific tenant/user context isn't important.
     */
    public fun create(): EventMetadata = EventMetadata.create(
        tenantId = TenantId.generate(),
        userId = UserId.generate(),
        correlationId = CorrelationId.generate()
    )

    /**
     * Creates EventMetadata with configurable tenant and user IDs.
     *
     * Use this when tests need to verify tenant isolation or user-specific behavior.
     *
     * @param tenantId The tenant ID to use (defaults to randomly generated)
     * @param userId The user ID to use (defaults to randomly generated)
     * @param correlationId The correlation ID to use (defaults to randomly generated)
     */
    public fun create(
        tenantId: TenantId = TenantId.generate(),
        userId: UserId = UserId.generate(),
        correlationId: CorrelationId = CorrelationId.generate()
    ): EventMetadata = EventMetadata.create(
        tenantId = tenantId,
        userId = userId,
        correlationId = correlationId
    )
}
