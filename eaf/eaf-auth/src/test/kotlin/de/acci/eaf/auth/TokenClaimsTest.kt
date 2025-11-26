package de.acci.eaf.auth

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class TokenClaimsTest {

    private val now = Instant.now()
    private val subject = UserId.generate()
    private val tenantId = TenantId.generate()

    private fun createClaims(
        roles: Set<String> = setOf("USER"),
        expiresAt: Instant = now.plusSeconds(3600),
    ) = TokenClaims(
        subject = subject,
        tenantId = tenantId,
        roles = roles,
        email = "test@example.com",
        expiresAt = expiresAt,
        issuedAt = now,
        issuer = "http://localhost:8180/realms/test",
    )

    @Test
    fun `isExpired returns false for valid token`() {
        val claims = createClaims(expiresAt = Instant.now().plusSeconds(3600))
        assertFalse(claims.isExpired())
    }

    @Test
    fun `isExpired returns true for expired token`() {
        val claims = createClaims(expiresAt = Instant.now().minusSeconds(1))
        assertTrue(claims.isExpired())
    }

    @Test
    fun `isExpired checks against provided instant`() {
        val expiresAt = Instant.parse("2025-01-01T12:00:00Z")
        val claims = createClaims(expiresAt = expiresAt)

        val beforeExpiry = Instant.parse("2025-01-01T11:59:59Z")
        val afterExpiry = Instant.parse("2025-01-01T12:00:01Z")

        assertFalse(claims.isExpired(beforeExpiry))
        assertTrue(claims.isExpired(afterExpiry))
    }

    @Test
    fun `hasRole returns true when role is present`() {
        val claims = createClaims(roles = setOf("USER", "ADMIN"))
        assertTrue(claims.hasRole("USER"))
        assertTrue(claims.hasRole("ADMIN"))
    }

    @Test
    fun `hasRole returns false when role is absent`() {
        val claims = createClaims(roles = setOf("USER"))
        assertFalse(claims.hasRole("ADMIN"))
    }

    @Test
    fun `hasRole is case sensitive`() {
        val claims = createClaims(roles = setOf("User"))
        assertFalse(claims.hasRole("USER"))
        assertFalse(claims.hasRole("user"))
        assertTrue(claims.hasRole("User"))
    }

    @Test
    fun `hasAnyRole returns true when at least one role matches`() {
        val claims = createClaims(roles = setOf("USER"))
        assertTrue(claims.hasAnyRole("ADMIN", "USER", "MANAGER"))
    }

    @Test
    fun `hasAnyRole returns false when no roles match`() {
        val claims = createClaims(roles = setOf("USER"))
        assertFalse(claims.hasAnyRole("ADMIN", "MANAGER"))
    }

    @Test
    fun `hasAnyRole returns false for empty required roles`() {
        val claims = createClaims(roles = setOf("USER"))
        assertFalse(claims.hasAnyRole())
    }

    @Test
    fun `hasAllRoles returns true when all roles match`() {
        val claims = createClaims(roles = setOf("USER", "ADMIN", "MANAGER"))
        assertTrue(claims.hasAllRoles("USER", "ADMIN"))
    }

    @Test
    fun `hasAllRoles returns false when not all roles match`() {
        val claims = createClaims(roles = setOf("USER"))
        assertFalse(claims.hasAllRoles("USER", "ADMIN"))
    }

    @Test
    fun `hasAllRoles returns true for empty required roles`() {
        val claims = createClaims(roles = setOf("USER"))
        assertTrue(claims.hasAllRoles())
    }

    @Test
    fun `claims contain correct values`() {
        val email = "user@example.com"
        val issuer = "http://localhost:8180/realms/dvmm"
        val roles = setOf("USER", "APPROVER")
        val issuedAt = Instant.now()
        val expiresAt = issuedAt.plusSeconds(3600)

        val claims = TokenClaims(
            subject = subject,
            tenantId = tenantId,
            roles = roles,
            email = email,
            expiresAt = expiresAt,
            issuedAt = issuedAt,
            issuer = issuer,
        )

        assertEquals(subject, claims.subject)
        assertEquals(tenantId, claims.tenantId)
        assertEquals(roles, claims.roles)
        assertEquals(email, claims.email)
        assertEquals(expiresAt, claims.expiresAt)
        assertEquals(issuedAt, claims.issuedAt)
        assertEquals(issuer, claims.issuer)
    }

    @Test
    fun `claims with null email`() {
        val claims = TokenClaims(
            subject = subject,
            tenantId = tenantId,
            roles = setOf("USER"),
            email = null,
            expiresAt = now.plusSeconds(3600),
            issuedAt = now,
            issuer = "http://localhost:8180/realms/test",
        )
        assertNull(claims.email)
    }
}
