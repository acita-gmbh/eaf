package de.acci.eaf.auth.keycloak

import de.acci.eaf.auth.InvalidTokenException
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for [KeycloakIdentityProvider].
 *
 * Tests exception handling paths that are difficult to trigger in integration tests.
 */
class KeycloakIdentityProviderTest {

    private val jwtDecoder: ReactiveJwtDecoder = mockk()
    private val userInfoClient: WebClient = mockk()
    private val clientId = "test-client"
    private val testUserId = UUID.randomUUID().toString()
    private val testTenantId = "11111111-1111-1111-1111-111111111111"

    private val identityProvider = KeycloakIdentityProvider(
        jwtDecoder = jwtDecoder,
        userInfoClient = userInfoClient,
        clientId = clientId,
    )

    @Test
    fun `validateToken should extract claims from valid JWT`() = runTest {
        // Given: A valid JWT
        val jwt = createValidJwt()
        every { jwtDecoder.decode(any()) } returns Mono.just(jwt)

        // When: Validating the token
        val claims = identityProvider.validateToken("valid-token")

        // Then: Claims are correctly extracted
        assertEquals(TenantId.fromString(testTenantId), claims.tenantId)
        assertEquals("test@example.com", claims.email)
        assertTrue(claims.roles.contains("user"))
    }

    @Test
    fun `validateToken should throw InvalidTokenException for BadJwtException`() = runTest {
        // Given: JWT decoder throws BadJwtException
        every { jwtDecoder.decode(any()) } returns Mono.error(BadJwtException("Invalid signature"))

        // When/Then: InvalidTokenException is thrown
        val exception = assertThrows<InvalidTokenException> {
            identityProvider.validateToken("bad-jwt")
        }

        assertEquals(InvalidTokenException.Reason.INVALID_SIGNATURE, exception.reason)
    }

    @Test
    fun `validateToken should throw InvalidTokenException for expired token`() = runTest {
        // Given: JWT decoder throws JwtValidationException for expired token
        val expiredError = OAuth2Error("invalid_token", "Jwt expired at ...", null)
        every { jwtDecoder.decode(any()) } returns Mono.error(
            JwtValidationException("Token expired", listOf(expiredError))
        )

        // When/Then: InvalidTokenException is thrown with EXPIRED reason
        val exception = assertThrows<InvalidTokenException> {
            identityProvider.validateToken("expired-jwt")
        }

        assertEquals(InvalidTokenException.Reason.EXPIRED, exception.reason)
    }

    @Test
    fun `validateToken should throw InvalidTokenException for generic validation error`() = runTest {
        // Given: JWT decoder throws JwtValidationException with generic error
        val genericError = OAuth2Error("invalid_token", "Some validation error", null)
        every { jwtDecoder.decode(any()) } returns Mono.error(
            JwtValidationException("Validation failed", listOf(genericError))
        )

        // When/Then: InvalidTokenException is thrown
        val exception = assertThrows<InvalidTokenException> {
            identityProvider.validateToken("invalid-jwt")
        }

        assertNotNull(exception.reason)
    }

    @Test
    fun `validateToken should throw InvalidTokenException for unknown error code`() = runTest {
        // Given: JWT decoder throws JwtValidationException with unknown error code
        val unknownError = OAuth2Error("unknown_error", "Unknown issue", null)
        every { jwtDecoder.decode(any()) } returns Mono.error(
            JwtValidationException("Unknown", listOf(unknownError))
        )

        // When/Then: InvalidTokenException is thrown
        val exception = assertThrows<InvalidTokenException> {
            identityProvider.validateToken("unknown-error-jwt")
        }

        assertEquals(InvalidTokenException.Reason.VALIDATION_FAILED, exception.reason)
    }

    @Test
    fun `validateToken should throw InvalidTokenException for generic exception`() = runTest {
        // Given: JWT decoder throws generic exception
        every { jwtDecoder.decode(any()) } returns Mono.error(RuntimeException("Unexpected error"))

        // When/Then: InvalidTokenException is thrown
        val exception = assertThrows<InvalidTokenException> {
            identityProvider.validateToken("error-jwt")
        }

        assertEquals(InvalidTokenException.Reason.VALIDATION_FAILED, exception.reason)
    }

    @Test
    fun `validateToken should throw InvalidTokenException for missing subject`() = runTest {
        // Given: JWT without subject
        val jwt = createJwtWithoutSubject()
        every { jwtDecoder.decode(any()) } returns Mono.just(jwt)

        // When/Then: InvalidTokenException is thrown
        val exception = assertThrows<InvalidTokenException> {
            identityProvider.validateToken("no-subject-jwt")
        }

        assertEquals(InvalidTokenException.Reason.MISSING_CLAIM, exception.reason)
    }

    @Test
    fun `validateToken should throw InvalidTokenException for missing tenant_id`() = runTest {
        // Given: JWT without tenant_id
        val jwt = createJwtWithoutTenantId()
        every { jwtDecoder.decode(any()) } returns Mono.just(jwt)

        // When/Then: InvalidTokenException is thrown
        val exception = assertThrows<InvalidTokenException> {
            identityProvider.validateToken("no-tenant-jwt")
        }

        assertEquals(InvalidTokenException.Reason.MISSING_CLAIM, exception.reason)
    }

    @Test
    fun `validateToken should extract client roles from resource_access`() = runTest {
        // Given: JWT with client roles
        val jwt = createJwtWithClientRoles()
        every { jwtDecoder.decode(any()) } returns Mono.just(jwt)

        // When: Validating the token
        val claims = identityProvider.validateToken("client-roles-jwt")

        // Then: Client roles are included
        assertTrue(claims.roles.contains("client-admin"))
    }

    @Test
    fun `validateToken should handle JWT without realm_access`() = runTest {
        // Given: JWT without realm_access
        val jwt = createJwtWithoutRealmAccess()
        every { jwtDecoder.decode(any()) } returns Mono.just(jwt)

        // When: Validating the token
        val claims = identityProvider.validateToken("no-realm-access-jwt")

        // Then: Roles are empty (only from non-existent realm_access)
        assertTrue(claims.roles.isEmpty())
    }

    @Test
    fun `validateToken should handle JWT without resource_access`() = runTest {
        // Given: JWT without resource_access
        val jwt = createJwtWithoutResourceAccess()
        every { jwtDecoder.decode(any()) } returns Mono.just(jwt)

        // When: Validating the token
        val claims = identityProvider.validateToken("no-resource-access-jwt")

        // Then: Only realm roles are present
        assertTrue(claims.roles.contains("user"))
    }

    @Test
    fun `validateToken should use default expiration when not present`() = runTest {
        // Given: JWT without expiration
        val jwt = createJwtWithoutExpiration()
        every { jwtDecoder.decode(any()) } returns Mono.just(jwt)

        // When: Validating the token
        val claims = identityProvider.validateToken("no-exp-jwt")

        // Then: Default expiration is used
        assertNotNull(claims.expiresAt)
    }

    @Test
    fun `validateToken should use default issuedAt when not present`() = runTest {
        // Given: JWT without issuedAt
        val jwt = createJwtWithoutIssuedAt()
        every { jwtDecoder.decode(any()) } returns Mono.just(jwt)

        // When: Validating the token
        val claims = identityProvider.validateToken("no-iat-jwt")

        // Then: Default issuedAt is used
        assertNotNull(claims.issuedAt)
    }

    @Test
    fun `validateToken should use empty string when issuer not present`() = runTest {
        // Given: JWT without issuer
        val jwt = createJwtWithoutIssuer()
        every { jwtDecoder.decode(any()) } returns Mono.just(jwt)

        // When: Validating the token
        val claims = identityProvider.validateToken("no-iss-jwt")

        // Then: Empty issuer is used
        assertEquals("", claims.issuer)
    }

    @Test
    fun `getUserInfo should throw InvalidTokenException for WebClient errors`() = runTest {
        // Given: WebClient throws error
        val requestSpec = mockk<RequestHeadersUriSpec<*>>()
        val headersSpec = mockk<RequestHeadersSpec<*>>()
        val responseSpec = mockk<ResponseSpec>()

        every { userInfoClient.get() } returns requestSpec
        every { requestSpec.header(any(), any()) } returns headersSpec
        every { headersSpec.retrieve() } returns responseSpec
        every { responseSpec.bodyToMono(KeycloakUserInfoResponse::class.java) } returns
            Mono.error(RuntimeException("Network error"))

        // When/Then: InvalidTokenException is thrown
        assertThrows<InvalidTokenException> {
            identityProvider.getUserInfo("error-token")
        }
    }

    @Test
    fun `getUserInfo should throw InvalidTokenException for missing email`() = runTest {
        // Given: UserInfo response without email
        setupUserInfoMock(createUserInfoResponseWithoutEmail())

        // When/Then: InvalidTokenException is thrown
        val exception = assertThrows<InvalidTokenException> {
            identityProvider.getUserInfo("no-email-token")
        }

        assertEquals(InvalidTokenException.Reason.MISSING_CLAIM, exception.reason)
    }

    @Test
    fun `getUserInfo should throw InvalidTokenException for missing tenant_id`() = runTest {
        // Given: UserInfo response without tenant_id
        setupUserInfoMock(createUserInfoResponseWithoutTenantId())

        // When/Then: InvalidTokenException is thrown
        val exception = assertThrows<InvalidTokenException> {
            identityProvider.getUserInfo("no-tenant-token")
        }

        assertEquals(InvalidTokenException.Reason.MISSING_CLAIM, exception.reason)
    }

    @Test
    fun `getUserInfo should extract roles from realmAccess`() = runTest {
        // Given: UserInfo with realm roles
        setupUserInfoMock(createUserInfoResponseWithRoles())

        // When: Getting user info
        val userInfo = identityProvider.getUserInfo("roles-token")

        // Then: Roles are extracted
        assertTrue(userInfo.roles.contains("user"))
    }

    @Test
    fun `getUserInfo should extract roles from resourceAccess for matching client`() = runTest {
        // Given: UserInfo with client roles
        setupUserInfoMock(createUserInfoResponseWithClientRoles())

        // When: Getting user info
        val userInfo = identityProvider.getUserInfo("client-roles-token")

        // Then: Client roles are extracted
        assertTrue(userInfo.roles.contains("client-role"))
    }

    @Test
    fun `getUserInfo should handle null realmAccess`() = runTest {
        // Given: UserInfo without realmAccess
        setupUserInfoMock(createUserInfoResponseWithoutRealmAccess())

        // When: Getting user info
        val userInfo = identityProvider.getUserInfo("no-realm-access-token")

        // Then: Roles are empty
        assertTrue(userInfo.roles.isEmpty())
    }

    @Test
    fun `getUserInfo should handle null resourceAccess`() = runTest {
        // Given: UserInfo without resourceAccess
        setupUserInfoMock(createUserInfoResponseWithoutResourceAccess())

        // When: Getting user info
        val userInfo = identityProvider.getUserInfo("no-resource-access-token")

        // Then: Only realm roles are present
        assertTrue(userInfo.roles.contains("user"))
    }

    @Test
    fun `getUserInfo should handle emailVerified null as false`() = runTest {
        // Given: UserInfo with null emailVerified
        setupUserInfoMock(createUserInfoResponseWithNullEmailVerified())

        // When: Getting user info
        val userInfo = identityProvider.getUserInfo("null-verified-token")

        // Then: emailVerified is false
        assertEquals(false, userInfo.emailVerified)
    }

    private fun setupUserInfoMock(response: KeycloakUserInfoResponse) {
        val requestSpec = mockk<RequestHeadersUriSpec<*>>()
        val headersSpec = mockk<RequestHeadersSpec<*>>()
        val responseSpec = mockk<ResponseSpec>()

        every { userInfoClient.get() } returns requestSpec
        every { requestSpec.header(any(), any()) } returns headersSpec
        every { headersSpec.retrieve() } returns responseSpec
        every { responseSpec.bodyToMono(KeycloakUserInfoResponse::class.java) } returns Mono.just(response)
    }

    private fun createValidJwt(): Jwt = Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject(testUserId)
        .claim("tenant_id", testTenantId)
        .claim("email", "test@example.com")
        .claim("realm_access", mapOf("roles" to listOf("user")))
        .issuer("http://keycloak/realms/test")
        .expiresAt(Instant.now().plusSeconds(3600))
        .issuedAt(Instant.now())
        .build()

    private fun createJwtWithoutSubject(): Jwt = Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .claim("tenant_id", testTenantId)
        .claim("email", "test@example.com")
        .expiresAt(Instant.now().plusSeconds(3600))
        .issuedAt(Instant.now())
        .build()

    private fun createJwtWithoutTenantId(): Jwt = Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject(testUserId)
        .claim("email", "test@example.com")
        .expiresAt(Instant.now().plusSeconds(3600))
        .issuedAt(Instant.now())
        .build()

    private fun createJwtWithClientRoles(): Jwt = Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject(testUserId)
        .claim("tenant_id", testTenantId)
        .claim("resource_access", mapOf(
            clientId to mapOf("roles" to listOf("client-admin"))
        ))
        .expiresAt(Instant.now().plusSeconds(3600))
        .issuedAt(Instant.now())
        .build()

    private fun createJwtWithoutRealmAccess(): Jwt = Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject(testUserId)
        .claim("tenant_id", testTenantId)
        .expiresAt(Instant.now().plusSeconds(3600))
        .issuedAt(Instant.now())
        .build()

    private fun createJwtWithoutResourceAccess(): Jwt = Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject(testUserId)
        .claim("tenant_id", testTenantId)
        .claim("realm_access", mapOf("roles" to listOf("user")))
        .expiresAt(Instant.now().plusSeconds(3600))
        .issuedAt(Instant.now())
        .build()

    private fun createJwtWithoutExpiration(): Jwt = Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject(testUserId)
        .claim("tenant_id", testTenantId)
        .issuedAt(Instant.now())
        .build()

    private fun createJwtWithoutIssuedAt(): Jwt = Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject(testUserId)
        .claim("tenant_id", testTenantId)
        .expiresAt(Instant.now().plusSeconds(3600))
        .build()

    private fun createJwtWithoutIssuer(): Jwt = Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject(testUserId)
        .claim("tenant_id", testTenantId)
        .expiresAt(Instant.now().plusSeconds(3600))
        .issuedAt(Instant.now())
        .build()

    private fun createUserInfoResponseWithoutEmail() = KeycloakUserInfoResponse(
        sub = testUserId,
        email = null,
        name = "Test User",
        givenName = "Test",
        familyName = "User",
        emailVerified = true,
        tenantId = testTenantId,
        realmAccess = null,
        resourceAccess = null,
    )

    private fun createUserInfoResponseWithoutTenantId() = KeycloakUserInfoResponse(
        sub = testUserId,
        email = "test@example.com",
        name = "Test User",
        givenName = "Test",
        familyName = "User",
        emailVerified = true,
        tenantId = null,
        realmAccess = null,
        resourceAccess = null,
    )

    private fun createUserInfoResponseWithRoles() = KeycloakUserInfoResponse(
        sub = testUserId,
        email = "test@example.com",
        name = "Test User",
        givenName = "Test",
        familyName = "User",
        emailVerified = true,
        tenantId = testTenantId,
        realmAccess = KeycloakUserInfoResponse.RealmAccess(listOf("user")),
        resourceAccess = null,
    )

    private fun createUserInfoResponseWithClientRoles() = KeycloakUserInfoResponse(
        sub = testUserId,
        email = "test@example.com",
        name = "Test User",
        givenName = "Test",
        familyName = "User",
        emailVerified = true,
        tenantId = testTenantId,
        realmAccess = null,
        resourceAccess = mapOf(
            clientId to KeycloakUserInfoResponse.ClientAccess(listOf("client-role"))
        ),
    )

    private fun createUserInfoResponseWithoutRealmAccess() = KeycloakUserInfoResponse(
        sub = testUserId,
        email = "test@example.com",
        name = "Test User",
        givenName = "Test",
        familyName = "User",
        emailVerified = true,
        tenantId = testTenantId,
        realmAccess = null,
        resourceAccess = null,
    )

    private fun createUserInfoResponseWithoutResourceAccess() = KeycloakUserInfoResponse(
        sub = testUserId,
        email = "test@example.com",
        name = "Test User",
        givenName = "Test",
        familyName = "User",
        emailVerified = true,
        tenantId = testTenantId,
        realmAccess = KeycloakUserInfoResponse.RealmAccess(listOf("user")),
        resourceAccess = null,
    )

    private fun createUserInfoResponseWithNullEmailVerified() = KeycloakUserInfoResponse(
        sub = testUserId,
        email = "test@example.com",
        name = "Test User",
        givenName = "Test",
        familyName = "User",
        emailVerified = null,
        tenantId = testTenantId,
        realmAccess = null,
        resourceAccess = null,
    )
}
