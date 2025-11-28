package de.acci.dvmm.api.security

import de.acci.eaf.testing.keycloak.KeycloakTestFixture
import de.acci.eaf.testing.keycloak.KeycloakTestUsers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Integration tests for SecurityConfig.
 *
 * Validates:
 * - AC 7: Unauthenticated requests to /api endpoints return 401
 * - AC 7: Unauthenticated /actuator/health requests are permitted
 * - AC 7: Authenticated requests with valid JWT succeed
 * - AC 7: CORS headers are correctly applied
 * - AC 5: CSRF token validation on mutations
 */
@SpringBootTest(
    classes = [SecurityConfigIntegrationTest.TestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class SecurityConfigIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    companion object {
        // Single shared fixture instance for all tests and configuration
        private val fixture: KeycloakTestFixture by lazy { KeycloakTestFixture.create() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") { fixture.getJwksUri() }
            registry.add("eaf.auth.keycloak.client-id") { KeycloakTestFixture.WEB_CLIENT_ID }
            registry.add("eaf.cors.allowed-origins") { "http://localhost:3000" }
        }
    }

    @Configuration
    @EnableAutoConfiguration
    @Import(SecurityConfig::class, CsrfController::class, CsrfValidationFilter::class)
    class TestConfig {
        @Bean
        fun jwtDecoder(): ReactiveJwtDecoder {
            // Access companion property via class - shares the same container
            val jwksUri = SecurityConfigIntegrationTest.fixture.getJwksUri()
            return NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build()
        }

        /**
         * Test controller to provide endpoints for security testing.
         * Nested inside TestConfig to prevent accidental use outside tests.
         */
        @RestController
        class TestEndpointController {
            @GetMapping("/api/test")
            fun test(): String = "OK"

            @PostMapping("/api/test")
            fun postTest(): String = "POST OK"
        }
    }

    @Test
    fun `unauthenticated request to api endpoint returns 401`() {
        webTestClient.get()
            .uri("/api/test")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `unauthenticated request to actuator health is permitted`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `authenticated request with valid JWT succeeds`() {
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

        webTestClient.get()
            .uri("/api/test")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `authenticated request with invalid JWT returns 401`() {
        webTestClient.get()
            .uri("/api/test")
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `cors preflight request returns allowed headers`() {
        webTestClient.options()
            .uri("/api/test")
            .header(HttpHeaders.ORIGIN, "http://localhost:3000")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000")
            .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)
    }

    @Test
    fun `cors preflight from disallowed origin is rejected`() {
        webTestClient.options()
            .uri("/api/test")
            .header(HttpHeaders.ORIGIN, "http://malicious-site.com")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            .exchange()
            .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
    }

    @Test
    fun `request from allowed origin includes cors headers`() {
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

        webTestClient.get()
            .uri("/api/test")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .header(HttpHeaders.ORIGIN, "http://localhost:3000")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000")
    }

    // CSRF Tests (AC 5)

    @Test
    fun `csrf token endpoint returns token when authenticated`() {
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

        webTestClient.get()
            .uri("/api/csrf")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.headerName").isEqualTo("X-XSRF-TOKEN")
            .jsonPath("$.token").isNotEmpty
    }

    @Test
    fun `post request without csrf header is forbidden`() {
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

        // POST without X-XSRF-TOKEN header should fail
        webTestClient.post()
            .uri("/api/test")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isForbidden
            .expectHeader().valueEquals("X-CSRF-Error", "Missing CSRF header")
    }

    @Test
    fun `post request with csrf header succeeds`() {
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

        // First, get the CSRF token - this sets the XSRF-TOKEN cookie
        val csrfResponse = webTestClient.get()
            .uri("/api/csrf")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .returnResult(CsrfTokenResponse::class.java)

        val cookies = csrfResponse.responseCookies
        val csrfCookie = cookies["XSRF-TOKEN"]?.firstOrNull()?.value
            ?: throw AssertionError("XSRF-TOKEN cookie not found in response")

        // POST with X-XSRF-TOKEN header AND cookie should succeed (double-submit pattern)
        webTestClient.post()
            .uri("/api/test")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .header("X-XSRF-TOKEN", csrfCookie)
            .cookie("XSRF-TOKEN", csrfCookie)
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `post request with wrong csrf header is forbidden`() {
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

        // First, get a valid CSRF token - this sets the XSRF-TOKEN cookie
        val csrfResponse = webTestClient.get()
            .uri("/api/csrf")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .returnResult(CsrfTokenResponse::class.java)

        val cookies = csrfResponse.responseCookies
        val csrfCookie = cookies["XSRF-TOKEN"]?.firstOrNull()?.value
            ?: throw AssertionError("XSRF-TOKEN cookie not found in response")

        // POST with mismatched header value should fail (double-submit validation)
        webTestClient.post()
            .uri("/api/test")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .header("X-XSRF-TOKEN", "wrong-token-value")
            .cookie("XSRF-TOKEN", csrfCookie)
            .exchange()
            .expectStatus().isForbidden
            .expectHeader().valueEquals("X-CSRF-Error", "CSRF token mismatch")
    }

    @Test
    fun `post request with missing csrf cookie is forbidden`() {
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

        // POST with header but no cookie should fail
        webTestClient.post()
            .uri("/api/test")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .header("X-XSRF-TOKEN", "some-token-value")
            .exchange()
            .expectStatus().isForbidden
            .expectHeader().valueEquals("X-CSRF-Error", "Missing CSRF cookie")
    }
}
