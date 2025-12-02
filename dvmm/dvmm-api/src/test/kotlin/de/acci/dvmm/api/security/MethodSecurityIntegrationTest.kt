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
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Integration tests for Spring Security Method Security.
 *
 * Validates that @EnableMethodSecurity is properly configured and
 * @PreAuthorize annotations are enforced at the method level.
 *
 * Story 2.9: These tests ensure role-based access control works for admin endpoints.
 */
@SpringBootTest(
    classes = [MethodSecurityIntegrationTest.TestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class MethodSecurityIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    companion object {
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
    @Import(SecurityConfig::class, CsrfController::class)
    class TestConfig {
        @Bean
        fun jwtDecoder(): ReactiveJwtDecoder {
            val jwksUri = MethodSecurityIntegrationTest.fixture.getJwksUri()
            return NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build()
        }

        /**
         * Test controller with role-protected endpoint.
         * Uses @PreAuthorize to require admin role.
         *
         * Note: Methods return Mono for compatibility with reactive method security.
         */
        @RestController
        class AdminTestController {
            @GetMapping("/api/admin/test")
            @PreAuthorize("hasRole('admin')")
            fun adminOnly(): Mono<String> = Mono.just("ADMIN OK")

            @GetMapping("/api/user/test")
            fun anyAuthenticated(): Mono<String> = Mono.just("USER OK")
        }
    }

    @Test
    fun `admin user can access admin-only endpoint`() {
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_ADMIN)

        webTestClient.get()
            .uri("/api/admin/test")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).isEqualTo("ADMIN OK")
    }

    @Test
    fun `non-admin user gets 403 Forbidden on admin-only endpoint`() {
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

        webTestClient.get()
            .uri("/api/admin/test")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `any authenticated user can access user endpoint`() {
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

        webTestClient.get()
            .uri("/api/user/test")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).isEqualTo("USER OK")
    }

    @Test
    fun `unauthenticated request to admin endpoint returns 401`() {
        webTestClient.get()
            .uri("/api/admin/test")
            .exchange()
            .expectStatus().isUnauthorized
    }
}
