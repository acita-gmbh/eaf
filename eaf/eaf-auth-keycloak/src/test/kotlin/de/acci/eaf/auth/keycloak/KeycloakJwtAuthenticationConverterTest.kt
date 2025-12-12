package de.acci.eaf.auth.keycloak

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class KeycloakJwtAuthenticationConverterTest {

    private val clientId = "dcm-api"
    private val converter = KeycloakJwtAuthenticationConverter(clientId)

    private fun createMockJwt(
        realmRoles: List<String>? = null,
        clientRoles: List<String>? = null,
    ): Jwt {
        val jwt = mockk<Jwt>()

        // Basic claims
        every { jwt.subject } returns "user-123"
        every { jwt.tokenValue } returns "mock-token"
        every { jwt.issuedAt } returns Instant.now()
        every { jwt.expiresAt } returns Instant.now().plusSeconds(3600)
        every { jwt.headers } returns mapOf("alg" to "RS256")
        every { jwt.claims } returns emptyMap()

        // Realm access
        val realmAccess: Map<String, Any>? = realmRoles?.let { mapOf("roles" to it) }
        every { jwt.getClaim<Map<String, Any>>("realm_access") } returns realmAccess

        // Resource access
        val resourceAccess: Map<String, Any>? = clientRoles?.let {
            mapOf(clientId to mapOf("roles" to it))
        }
        every { jwt.getClaim<Map<String, Any>>("resource_access") } returns resourceAccess

        return jwt
    }

    @Test
    fun `extracts realm roles with ROLE_ prefix`() {
        val jwt = createMockJwt(realmRoles = listOf("USER", "ADMIN"))

        val result = converter.convert(jwt)?.block()

        val authorities = result?.authorities?.map { it.authority }
        assertEquals(setOf("ROLE_USER", "ROLE_ADMIN"), authorities?.toSet())
    }

    @Test
    fun `extracts client roles with ROLE_ prefix`() {
        val jwt = createMockJwt(clientRoles = listOf("vm-requester", "vm-approver"))

        val result = converter.convert(jwt)?.block()

        val authorities = result?.authorities?.map { it.authority }
        assertEquals(setOf("ROLE_vm-requester", "ROLE_vm-approver"), authorities?.toSet())
    }

    @Test
    fun `combines realm and client roles`() {
        val jwt = createMockJwt(
            realmRoles = listOf("USER"),
            clientRoles = listOf("vm-requester"),
        )

        val result = converter.convert(jwt)?.block()

        val authorities = result?.authorities?.map { it.authority }
        assertEquals(setOf("ROLE_USER", "ROLE_vm-requester"), authorities?.toSet())
    }

    @Test
    fun `handles missing realm_access claim`() {
        val jwt = createMockJwt(clientRoles = listOf("vm-requester"))

        val result = converter.convert(jwt)?.block()

        val authorities = result?.authorities?.map { it.authority }
        assertEquals(setOf("ROLE_vm-requester"), authorities?.toSet())
    }

    @Test
    fun `handles missing resource_access claim`() {
        val jwt = createMockJwt(realmRoles = listOf("USER"))

        val result = converter.convert(jwt)?.block()

        val authorities = result?.authorities?.map { it.authority }
        assertEquals(setOf("ROLE_USER"), authorities?.toSet())
    }

    @Test
    fun `handles empty roles`() {
        val jwt = createMockJwt(realmRoles = emptyList(), clientRoles = emptyList())

        val result = converter.convert(jwt)?.block()

        assertTrue(result?.authorities?.isEmpty() == true)
    }

    @Test
    fun `handles no roles claims at all`() {
        val jwt = createMockJwt()

        val result = converter.convert(jwt)?.block()

        assertTrue(result?.authorities?.isEmpty() == true)
    }

    @Test
    fun `uses custom role prefix`() {
        val customConverter = KeycloakJwtAuthenticationConverter(
            clientId = clientId,
            rolePrefix = "SCOPE_",
        )
        val jwt = createMockJwt(realmRoles = listOf("USER"))

        val result = customConverter.convert(jwt)?.block()

        val authorities = result?.authorities?.map { it.authority }
        assertEquals(setOf("SCOPE_USER"), authorities?.toSet())
    }

    @Test
    fun `ignores roles from other clients`() {
        val jwt = mockk<Jwt>()
        every { jwt.subject } returns "user-123"
        every { jwt.tokenValue } returns "mock-token"
        every { jwt.issuedAt } returns Instant.now()
        every { jwt.expiresAt } returns Instant.now().plusSeconds(3600)
        every { jwt.headers } returns mapOf("alg" to "RS256")
        every { jwt.claims } returns emptyMap()
        every { jwt.getClaim<Map<String, Any>>("realm_access") } returns null
        every { jwt.getClaim<Map<String, Any>>("resource_access") } returns mapOf(
            "other-client" to mapOf("roles" to listOf("other-role")),
        )

        val result = converter.convert(jwt)?.block()

        assertTrue(result?.authorities?.isEmpty() == true)
    }

    @Test
    fun `returns JwtAuthenticationToken with principal`() {
        val jwt = createMockJwt(realmRoles = listOf("USER"))

        val result = converter.convert(jwt)?.block()

        assertEquals(jwt, result?.principal)
        assertEquals("user-123", result?.name)
    }

    @Test
    fun `authorities are SimpleGrantedAuthority instances`() {
        val jwt = createMockJwt(realmRoles = listOf("USER"))

        val result = converter.convert(jwt)?.block()

        result?.authorities?.forEach { authority ->
            assertTrue(authority is SimpleGrantedAuthority)
        }
    }
}
