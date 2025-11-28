package de.acci.eaf.testing.keycloak

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * Integration test verifying Keycloak Testcontainer setup.
 *
 * Validates:
 * - Container starts successfully
 * - Realm is imported correctly
 * - Token acquisition works for test users
 */
class KeycloakTestFixtureIntegrationTest {

    @Test
    fun `should start keycloak container and acquire access token`() {
        val fixture = KeycloakTestFixture.create()

        // Verify container is running and URLs are available
        val issuerUri = fixture.getIssuerUri()
        assertNotNull(issuerUri)
        assertTrue(issuerUri.contains("realms/dvmm"))

        // Verify JWKS URI is available
        val jwksUri = fixture.getJwksUri()
        assertNotNull(jwksUri)
        assertTrue(jwksUri.contains("certs"))

        // Acquire access token for test user
        val accessToken = assertDoesNotThrow {
            fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)
        }

        assertNotNull(accessToken)
        assertTrue(accessToken.isNotBlank(), "Access token should not be blank")
        // JWT tokens have 3 parts separated by dots
        assertTrue(accessToken.count { it == '.' } == 2, "Access token should be a valid JWT")
    }

    @Test
    fun `should acquire access token for admin user`() {
        val fixture = KeycloakTestFixture.create()

        val accessToken = assertDoesNotThrow {
            fixture.getAccessToken(KeycloakTestUsers.TENANT1_ADMIN)
        }

        assertNotNull(accessToken)
        assertTrue(accessToken.isNotBlank())
    }

    @Test
    fun `should acquire access token for tenant2 user`() {
        val fixture = KeycloakTestFixture.create()

        val accessToken = assertDoesNotThrow {
            fixture.getAccessToken(KeycloakTestUsers.TENANT2_USER)
        }

        assertNotNull(accessToken)
        assertTrue(accessToken.isNotBlank())
    }
}
