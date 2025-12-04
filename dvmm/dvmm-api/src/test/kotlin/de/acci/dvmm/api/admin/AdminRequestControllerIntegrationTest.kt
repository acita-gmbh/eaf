package de.acci.dvmm.api.admin

import de.acci.eaf.testing.keycloak.KeycloakTestFixture
import de.acci.eaf.testing.keycloak.KeycloakTestUsers
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Integration tests for Admin endpoint security.
 *
 * Story 2.9: Admin Approval Queue
 *
 * These tests validate:
 * - AC 4: Only admins can access admin endpoints (role-based access control)
 * - CRITICAL: Keycloak 'admin' role maps correctly to Spring Security ROLE_ADMIN
 * - 401 Unauthorized for unauthenticated requests
 * - 403 Forbidden for authenticated non-admin users
 *
 * Uses KeycloakTestFixture to generate real JWTs with proper role claims.
 * Follows the same pattern as MethodSecurityIntegrationTest with a simple test controller.
 */
@SpringBootTest(
    classes = [AdminRequestControllerIntegrationTest.TestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureWebTestClient(timeout = "60s")
@DisplayName("Admin Endpoint Security")
class AdminRequestControllerIntegrationTest {

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
    @Import(
        de.acci.dvmm.api.security.SecurityConfig::class,
        de.acci.dvmm.api.security.CsrfController::class
    )
    class TestConfig {
        @Bean
        fun jwtDecoder(): ReactiveJwtDecoder {
            val jwksUri = fixture.getJwksUri()
            return NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build()
        }

        /**
         * Test controller that mimics AdminRequestController's security requirements.
         *
         * Uses @PreAuthorize at class level (same as AdminRequestController) to
         * validate that admin role enforcement works correctly.
         */
        @RestController
        @RequestMapping("/api/admin")
        @PreAuthorize("hasRole('admin')")
        class AdminTestController {

            @GetMapping("/requests/pending")
            fun getPendingRequests(
                @RequestParam(required = false) projectId: String?,
                @RequestParam(defaultValue = "0") page: Int,
                @RequestParam(defaultValue = "25") size: Int
            ): Mono<Map<String, Any>> = Mono.just(
                mapOf(
                    "items" to emptyList<Any>(),
                    "page" to page,
                    "size" to size,
                    "totalElements" to 0L,
                    "totalPages" to 0
                )
            )

            @GetMapping("/projects")
            fun getProjects(): Mono<List<Map<String, String>>> = Mono.just(
                listOf(
                    mapOf("id" to "test-id", "name" to "Test Project")
                )
            )
        }
    }

    @Nested
    @DisplayName("GET /api/admin/requests/pending - Role-Based Access")
    inner class PendingRequestsRoleTests {

        @Test
        @DisplayName("CRITICAL: Admin user with Keycloak 'admin' role can access endpoint")
        fun `admin user can access pending requests endpoint`() {
            // Given - Admin user has 'admin' role in Keycloak realm
            val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_ADMIN)

            // When/Then - Should return 200 OK
            webTestClient.get()
                .uri("/api/admin/requests/pending")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items").isArray
                .jsonPath("$.page").isEqualTo(0)
        }

        @Test
        @DisplayName("Non-admin user gets 403 Forbidden")
        fun `non-admin user gets 403 forbidden`() {
            // Given - Regular user has only 'user' role, not 'admin'
            val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

            // When/Then - Should return 403 Forbidden
            webTestClient.get()
                .uri("/api/admin/requests/pending")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        @DisplayName("Unauthenticated request gets 401 Unauthorized")
        fun `unauthenticated request gets 401 unauthorized`() {
            // When/Then - No Authorization header = 401
            webTestClient.get()
                .uri("/api/admin/requests/pending")
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        @DisplayName("Invalid JWT gets 401 Unauthorized")
        fun `invalid jwt gets 401 unauthorized`() {
            // Given - Malformed/expired token (intentionally invalid for testing)
            // ggignore (GitGuardian) - This is a fake test token, not a real secret
            val invalidToken = "not-a-valid-jwt-token"

            // When/Then - Should return 401
            webTestClient.get()
                .uri("/api/admin/requests/pending")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $invalidToken")
                .exchange()
                .expectStatus().isUnauthorized
        }
    }

    @Nested
    @DisplayName("GET /api/admin/projects - Role-Based Access")
    inner class ProjectsRoleTests {

        @Test
        @DisplayName("Admin user can access projects endpoint")
        fun `admin user can access projects endpoint`() {
            // Given
            val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_ADMIN)

            // When/Then
            webTestClient.get()
                .uri("/api/admin/projects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$").isArray
        }

        @Test
        @DisplayName("Non-admin user gets 403 Forbidden")
        fun `non-admin user gets 403 forbidden on projects endpoint`() {
            // Given
            val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

            // When/Then
            webTestClient.get()
                .uri("/api/admin/projects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        @DisplayName("Unauthenticated request gets 401 Unauthorized")
        fun `unauthenticated request to projects gets 401`() {
            // When/Then
            webTestClient.get()
                .uri("/api/admin/projects")
                .exchange()
                .expectStatus().isUnauthorized
        }
    }

    @Nested
    @DisplayName("Pagination Parameters")
    inner class PaginationTests {

        @Test
        @DisplayName("Default pagination parameters are applied")
        fun `default pagination parameters are applied`() {
            // Given
            val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_ADMIN)

            // When/Then - No page/size params, should use defaults
            webTestClient.get()
                .uri("/api/admin/requests/pending")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(25)
        }

        @Test
        @DisplayName("Custom pagination parameters are accepted")
        fun `custom pagination parameters are accepted`() {
            // Given
            val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_ADMIN)

            // When/Then
            webTestClient.get()
                .uri("/api/admin/requests/pending?page=2&size=10")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.page").isEqualTo(2)
                .jsonPath("$.size").isEqualTo(10)
        }
    }
}
