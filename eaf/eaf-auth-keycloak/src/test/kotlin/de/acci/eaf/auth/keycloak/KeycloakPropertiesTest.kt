package de.acci.eaf.auth.keycloak

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [KeycloakProperties].
 */
class KeycloakPropertiesTest {

    @Test
    fun `should create properties with clientId and default userInfoUri`() {
        val properties = KeycloakProperties(clientId = "test-client")

        assertEquals("test-client", properties.clientId)
        assertEquals("", properties.userInfoUri)
    }

    @Test
    fun `should create properties with clientId and custom userInfoUri`() {
        val properties = KeycloakProperties(
            clientId = "test-client",
            userInfoUri = "http://keycloak/realms/test/protocol/openid-connect/userinfo",
        )

        assertEquals("test-client", properties.clientId)
        assertEquals("http://keycloak/realms/test/protocol/openid-connect/userinfo", properties.userInfoUri)
    }

    @Test
    fun `should support data class copy`() {
        val original = KeycloakProperties(clientId = "original-client")
        val copied = original.copy(clientId = "new-client")

        assertEquals("original-client", original.clientId)
        assertEquals("new-client", copied.clientId)
    }

    @Test
    fun `should support equals and hashCode`() {
        val props1 = KeycloakProperties(clientId = "test-client")
        val props2 = KeycloakProperties(clientId = "test-client")

        assertEquals(props1, props2)
        assertEquals(props1.hashCode(), props2.hashCode())
    }
}
