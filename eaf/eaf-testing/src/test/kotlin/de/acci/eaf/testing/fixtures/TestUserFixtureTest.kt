package de.acci.eaf.testing.fixtures

import de.acci.eaf.core.types.TenantId
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class TestUserFixtureTest {

    @Test
    fun `generateJwt creates valid token with correct claims`() {
        // Given
        val tenantId = TenantId.generate()
        val user = TestUserFixture.createUser(tenantId, "ADMIN")
        val key = Keys.hmacShaKeyFor(TestUserFixture.TEST_JWT_SECRET.toByteArray())

        // When
        val token = TestUserFixture.generateJwt(user)

        // Then
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload

        assertEquals(user.id.value.toString(), claims.subject)
        assertEquals(user.tenantId.value.toString(), claims["tenant_id"])
        assertEquals(user.email, claims["email"])
        
        val roles = claims["roles"] as List<*>
        assertEquals(1, roles.size)
        assertEquals("ADMIN", roles[0])
        
        assertNotNull(claims.issuedAt)
        assertNotNull(claims.expiration)
    }
}
