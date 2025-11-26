package de.acci.eaf.auth

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UserInfoTest {

    private val userId = UserId.generate()
    private val tenantId = TenantId.generate()

    @Test
    fun `UserInfo contains all fields`() {
        val userInfo = UserInfo(
            id = userId,
            tenantId = tenantId,
            email = "user@example.com",
            name = "Max Mustermann",
            givenName = "Max",
            familyName = "Mustermann",
            emailVerified = true,
            roles = setOf("USER", "ADMIN"),
        )

        assertEquals(userId, userInfo.id)
        assertEquals(tenantId, userInfo.tenantId)
        assertEquals("user@example.com", userInfo.email)
        assertEquals("Max Mustermann", userInfo.name)
        assertEquals("Max", userInfo.givenName)
        assertEquals("Mustermann", userInfo.familyName)
        assertTrue(userInfo.emailVerified)
        assertEquals(setOf("USER", "ADMIN"), userInfo.roles)
    }

    @Test
    fun `UserInfo with null optional fields`() {
        val userInfo = UserInfo(
            id = userId,
            tenantId = tenantId,
            email = "user@example.com",
            name = null,
            givenName = null,
            familyName = null,
            emailVerified = false,
            roles = emptySet(),
        )

        assertEquals(userId, userInfo.id)
        assertEquals(tenantId, userInfo.tenantId)
        assertEquals("user@example.com", userInfo.email)
        assertNull(userInfo.name)
        assertNull(userInfo.givenName)
        assertNull(userInfo.familyName)
        assertFalse(userInfo.emailVerified)
        assertTrue(userInfo.roles.isEmpty())
    }

    @Test
    fun `UserInfo equality works correctly`() {
        val userInfo1 = UserInfo(
            id = userId,
            tenantId = tenantId,
            email = "user@example.com",
            name = "Max",
            givenName = "Max",
            familyName = "Mustermann",
            emailVerified = true,
            roles = setOf("USER"),
        )

        val userInfo2 = UserInfo(
            id = userId,
            tenantId = tenantId,
            email = "user@example.com",
            name = "Max",
            givenName = "Max",
            familyName = "Mustermann",
            emailVerified = true,
            roles = setOf("USER"),
        )

        assertEquals(userInfo1, userInfo2)
        assertEquals(userInfo1.hashCode(), userInfo2.hashCode())
    }

    @Test
    fun `UserInfo copy works correctly`() {
        val original = UserInfo(
            id = userId,
            tenantId = tenantId,
            email = "original@example.com",
            name = "Original Name",
            givenName = "Original",
            familyName = "Name",
            emailVerified = false,
            roles = setOf("USER"),
        )

        val modified = original.copy(
            email = "modified@example.com",
            emailVerified = true,
            roles = setOf("USER", "ADMIN"),
        )

        assertEquals("modified@example.com", modified.email)
        assertTrue(modified.emailVerified)
        assertEquals(setOf("USER", "ADMIN"), modified.roles)
        assertEquals(original.id, modified.id)
        assertEquals(original.tenantId, modified.tenantId)
        assertEquals(original.name, modified.name)
    }
}
