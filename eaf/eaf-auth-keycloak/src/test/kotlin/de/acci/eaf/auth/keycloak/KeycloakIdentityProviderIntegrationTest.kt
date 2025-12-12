package de.acci.eaf.auth.keycloak

import de.acci.eaf.auth.InvalidTokenException
import de.acci.eaf.testing.keycloak.KeycloakTestFixture
import de.acci.eaf.testing.keycloak.KeycloakTestUsers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.web.reactive.function.client.WebClient

/**
 * Integration tests for [KeycloakIdentityProvider].
 *
 * Tests validate:
 * - AC 6: getUserInfo() returns correct user details from token
 * - AC 6: getTenantId() extracts tenant_id claim correctly
 * - AC 6: Invalid token throws appropriate exception
 * - AC 8: Achieves coverage for mutation testing
 *
 * Uses Keycloak Testcontainer with configured test realm including:
 * - dcm-web client (public, direct access grants enabled)
 * - Test users with tenant_id attribute and realm roles
 */
class KeycloakIdentityProviderIntegrationTest {

    companion object {
        private lateinit var fixture: KeycloakTestFixture
        private lateinit var identityProvider: KeycloakIdentityProvider

        @JvmStatic
        @BeforeAll
        fun setup() {
            fixture = KeycloakTestFixture.create()

            val jwtDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(fixture.getJwksUri()).build()
            val userInfoClient = WebClient.builder()
                .baseUrl(fixture.getUserInfoEndpoint())
                .build()

            identityProvider = KeycloakIdentityProvider(
                jwtDecoder = jwtDecoder,
                userInfoClient = userInfoClient,
                clientId = KeycloakTestFixture.WEB_CLIENT_ID,
            )
        }
    }

    @Test
    fun `validateToken should extract tenant_id claim from JWT`() = runTest {
        // Given: A valid access token from Keycloak
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

        // When: Validating the token
        val claims = identityProvider.validateToken(accessToken)

        // Then: tenant_id is correctly extracted
        assertEquals(KeycloakTestUsers.TENANT1_USER.tenantId, claims.tenantId)
    }

    @Test
    fun `validateToken should extract subject and roles from JWT`() = runTest {
        // Given: A valid access token from Keycloak admin user
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_ADMIN)

        // When: Validating the token
        val claims = identityProvider.validateToken(accessToken)

        // Then: Subject is present and roles include admin
        assertNotNull(claims.subject)
        assertTrue(claims.roles.contains("admin"), "Admin user should have 'admin' role")
        assertTrue(claims.roles.contains("user"), "Admin user should also have 'user' role")
    }

    @Test
    fun `validateToken should extract email from JWT`() = runTest {
        // Given: A valid access token
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

        // When: Validating the token
        val claims = identityProvider.validateToken(accessToken)

        // Then: Email is extracted
        assertEquals(KeycloakTestUsers.TENANT1_USER.email, claims.email)
    }

    @Test
    fun `validateToken should extract tenant_id from different tenant`() = runTest {
        // Given: A valid access token from tenant 2 user
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT2_USER)

        // When: Validating the token
        val claims = identityProvider.validateToken(accessToken)

        // Then: tenant_id is correctly extracted for tenant 2
        assertEquals(KeycloakTestUsers.TENANT2_USER.tenantId, claims.tenantId)
    }

    @Test
    fun `validateToken should throw InvalidTokenException for invalid token`() = runTest {
        // Given: An invalid token
        val invalidToken = "header.payload.signature"

        // When/Then: Validating throws InvalidTokenException
        val exception = assertThrows<InvalidTokenException> {
            identityProvider.validateToken(invalidToken)
        }

        // Then: Exception has appropriate reason
        assertNotNull(exception.reason)
    }

    @Test
    fun `validateToken should throw InvalidTokenException for malformed token`() = runTest {
        // Given: A completely malformed token
        val malformedToken = "not-a-jwt-at-all"

        // When/Then: Validating throws InvalidTokenException
        assertThrows<InvalidTokenException> {
            identityProvider.validateToken(malformedToken)
        }
    }

    @Test
    fun `validateToken should throw InvalidTokenException for empty token`() = runTest {
        // Given: An empty token
        val emptyToken = ""

        // When/Then: Validating throws InvalidTokenException
        assertThrows<InvalidTokenException> {
            identityProvider.validateToken(emptyToken)
        }
    }

    @Test
    fun `validateToken should include issuer in claims`() = runTest {
        // Given: A valid access token
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

        // When: Validating the token
        val claims = identityProvider.validateToken(accessToken)

        // Then: Issuer is present and matches Keycloak realm
        assertTrue(claims.issuer.contains("dcm"), "Issuer should contain realm name 'dcm'")
    }

    @Test
    fun `validateToken should include expiration timestamps`() = runTest {
        // Given: A valid access token
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

        // When: Validating the token
        val claims = identityProvider.validateToken(accessToken)

        // Then: Timestamps are present and valid
        assertNotNull(claims.issuedAt)
        assertNotNull(claims.expiresAt)
        assertTrue(
            claims.expiresAt.isAfter(claims.issuedAt),
            "Expiration should be after issuance",
        )
    }

    @Test
    fun `getUserInfo should return correct user details`() = runTest {
        // Given: A valid access token
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

        // When: Getting user info
        val userInfo = identityProvider.getUserInfo(accessToken)

        // Then: User details are correct
        assertEquals(KeycloakTestUsers.TENANT1_USER.email, userInfo.email)
        assertEquals(KeycloakTestUsers.TENANT1_USER.tenantId, userInfo.tenantId)
        assertEquals("Test", userInfo.givenName)
        assertEquals("User", userInfo.familyName)
        assertTrue(userInfo.emailVerified)
    }

    @Test
    fun `getUserInfo should return correct tenant_id for different tenants`() = runTest {
        // Given: Access tokens for different tenants
        val tenant1Token = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)
        val tenant2Token = fixture.getAccessToken(KeycloakTestUsers.TENANT2_USER)

        // When: Getting user info for both
        val tenant1Info = identityProvider.getUserInfo(tenant1Token)
        val tenant2Info = identityProvider.getUserInfo(tenant2Token)

        // Then: tenant_ids are different and correct
        assertEquals(KeycloakTestUsers.TENANT1_USER.tenantId, tenant1Info.tenantId)
        assertEquals(KeycloakTestUsers.TENANT2_USER.tenantId, tenant2Info.tenantId)
        assertTrue(
            tenant1Info.tenantId != tenant2Info.tenantId,
            "Tenant IDs should be different",
        )
    }

    @Test
    fun `getUserInfo should include roles from realm`() = runTest {
        // Given: A valid access token for admin user
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_ADMIN)

        // When: Getting user info
        val userInfo = identityProvider.getUserInfo(accessToken)

        // Then: Roles are present
        assertTrue(userInfo.roles.contains("admin"), "Admin should have 'admin' role")
    }

    @Test
    fun `getUserInfo should throw InvalidTokenException for invalid token`() = runTest {
        // Given: An invalid token
        val invalidToken = "invalid-token"

        // When/Then: Getting user info throws InvalidTokenException
        assertThrows<InvalidTokenException> {
            identityProvider.getUserInfo(invalidToken)
        }
    }

    @Test
    fun `TokenClaims hasRole should work correctly`() = runTest {
        // Given: A valid access token with admin role
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_ADMIN)
        val claims = identityProvider.validateToken(accessToken)

        // Then: hasRole returns correct values
        assertTrue(claims.hasRole("admin"))
        assertTrue(claims.hasRole("user"))
        assertTrue(!claims.hasRole("nonexistent"))
    }

    @Test
    fun `TokenClaims hasAnyRole should work correctly`() = runTest {
        // Given: A valid access token with user role only
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)
        val claims = identityProvider.validateToken(accessToken)

        // Then: hasAnyRole returns correct values
        assertTrue(claims.hasAnyRole("user", "admin"))
        assertTrue(claims.hasAnyRole("user"))
        assertTrue(!claims.hasAnyRole("admin", "superadmin"))
    }

    @Test
    fun `TokenClaims isExpired should return false for valid token`() = runTest {
        // Given: A freshly issued token
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)
        val claims = identityProvider.validateToken(accessToken)

        // Then: isExpired returns false
        assertTrue(!claims.isExpired())
    }
}
