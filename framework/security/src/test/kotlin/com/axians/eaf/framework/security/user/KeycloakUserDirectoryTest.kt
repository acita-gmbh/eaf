package com.axians.eaf.framework.security.user

import com.axians.eaf.framework.security.config.KeycloakAdminProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Unit tests for KeycloakUserDirectory - Keycloak Admin API user lookup with caching.
 *
 * Validates user existence and active status lookups via Keycloak Admin API with client
 * credentials flow, implementing two-tier caching (positive and negative results) to
 * minimize Keycloak Admin API calls while ensuring deleted/disabled users are detected.
 *
 * **Test Coverage:**
 * - Active user lookup and caching (positive cache, 60s TTL default)
 * - Missing user handling and negative caching (negative cache, 30s TTL default)
 * - Admin token reuse across lookups (token cached until expiry)
 * - MockRestServiceServer for deterministic HTTP testing
 * - Client credentials flow (Keycloak Admin API authentication)
 * - Fixed Clock for deterministic time-based tests
 *
 * **Security Patterns:**
 * - Two-tier caching (positive cache longer than negative for performance)
 * - Token expiry handling (admin token cached with expiry tracking)
 * - Client credentials flow (secure service-to-service authentication)
 * - User active status validation (enabled field check)
 * - Fail-closed behavior (Keycloak unavailable = validation fails)
 *
 * **Caching Strategy:**
 * - Positive cache: 60s TTL (configurable) - reduces Keycloak load
 * - Negative cache: 30s TTL (configurable) - faster invalidation for deleted users
 * - Admin token cache: Until expiry (tokenExpirySkew buffer)
 * - Cache invalidation: TTL-based, no manual invalidation required
 *
 * **Testing Strategy:**
 * - MockRestServiceServer: Spring HTTP client mocking
 * - Fixed Clock: Deterministic time-based behavior
 * - Expectation verification: Ensures correct API calls
 * - Cache hit validation: Verify second call doesn't hit API
 *
 * **Acceptance Criteria:**
 * - Layer 9: User validation via Keycloak Admin API
 * - Positive and negative caching (configurable TTLs)
 * - Admin token reuse (until expiry)
 *
 * @see KeycloakUserDirectory Primary class under test
 * @see UserDirectory Interface
 * @since JUnit 6 Migration (2025-11-20)
 * @author EAF Testing Framework
 */
class KeycloakUserDirectoryTest {

    private lateinit var properties: KeycloakAdminProperties
    private lateinit var directory: KeycloakUserDirectory
    private lateinit var server: MockRestServiceServer

    @BeforeEach
    fun beforeEach() {
        properties = KeycloakAdminProperties(
            baseUrl = "http://localhost:8080",
            realm = "eaf",
            adminClientId = "eaf-admin",
            adminClientSecret = "secret",
            userCacheTtl = Duration.ofSeconds(60),
            negativeUserCacheTtl = Duration.ofSeconds(30),
            tokenExpirySkew = Duration.ofSeconds(0),
        )
        directory = KeycloakUserDirectory(
            properties,
            RestTemplateBuilder(),
            Clock.fixed(Instant.parse("2025-11-12T00:00:00Z"), ZoneOffset.UTC),
        )
        server = MockRestServiceServer.createServer(directory.restTemplate)
    }

    @AfterEach
    fun afterEach() {
        server.verify()
    }

    private fun expectTokenRequest(
        token: String = "admin-token",
        expiresIn: Long = 120L,
    ) {
        server.expect(
            ExpectedCount.once(),
            requestTo("http://localhost:8080/realms/eaf/protocol/openid-connect/token"),
        ).andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"access_token":"$token","expires_in":$expiresIn}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
    }

    private fun expectUserRequest(
        userId: String,
        enabled: Boolean? = true,
    ) {
        val responder = if (enabled == null) {
            withStatus(HttpStatus.NOT_FOUND)
        } else {
            withSuccess(
                """{"id":"$userId","enabled":$enabled}""",
                MediaType.APPLICATION_JSON,
            )
        }

        server.expect(
            ExpectedCount.once(),
            requestTo("http://localhost:8080/admin/realms/eaf/users/$userId"),
        ).andExpect(method(HttpMethod.GET))
            .andRespond(responder)
    }

    @Test
    fun `returns active user and caches result`() {
        expectTokenRequest()
        expectUserRequest("user-123", enabled = true)

        val first = directory.findById("user-123")
        assertThat(first).isNotNull
        assertThat(first!!.active).isTrue()

        val second = directory.findById("user-123")
        assertThat(second).isNotNull
        assertThat(second!!.active).isTrue()
    }

    @Test
    fun `returns null for missing user and caches negative result`() {
        expectTokenRequest()
        expectUserRequest("ghost", enabled = null)

        assertThat(directory.findById("ghost")).isNull()
        assertThat(directory.findById("ghost")).isNull()
    }

    @Test
    fun `reuses admin token across lookups until expiry`() {
        // Setup ALL expectations BEFORE making any requests
        // Token request (should happen only once)
        server.expect(once(), requestTo("http://localhost:8080/realms/eaf/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"access_token":"token123","expires_in":300}""", MediaType.APPLICATION_JSON))

        // First user lookup
        server.expect(once(), requestTo("http://localhost:8080/admin/realms/eaf/users/user123"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess("""{"id":"user123","username":"user123","enabled":true}""", MediaType.APPLICATION_JSON),
            )

        // Second user lookup (token should be reused, so no new token request)
        server.expect(once(), requestTo("http://localhost:8080/admin/realms/eaf/users/user456"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess("""{"id":"user456","username":"user456","enabled":true}""", MediaType.APPLICATION_JSON),
            )

        // Now execute the lookups
        val result1 = directory.findById("user123")
        assertThat(result1?.id).isEqualTo("user123")
        assertThat(result1?.active).isEqualTo(true)

        val result2 = directory.findById("user456")
        assertThat(result2?.id).isEqualTo("user456")
        assertThat(result2?.active).isEqualTo(true)
    }
}
