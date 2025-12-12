package de.acci.dcm.api.admin

import de.acci.eaf.testing.keycloak.KeycloakTestFixture
import de.acci.eaf.testing.keycloak.KeycloakTestUsers
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Integration tests for Admin approve/reject endpoint security.
 *
 * Story 2.11: Approve/Reject Actions
 *
 * These tests validate:
 * - AC 1, 2: Approve/Reject actions require admin role
 * - AC 3: Request body validation (version, reason)
 * - Security: 401 Unauthorized for unauthenticated requests
 * - Security: 403 Forbidden for authenticated non-admin users
 *
 * Uses KeycloakTestFixture to generate real JWTs with proper role claims.
 */
@SpringBootTest(
    classes = [ApproveRejectEndpointIntegrationTest.TestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@DisplayName("Approve/Reject Endpoint Security")
class ApproveRejectEndpointIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    companion object {
        private val fixture: KeycloakTestFixture by lazy { KeycloakTestFixture.create() }
        private val testRequestId: String = UUID.randomUUID().toString()

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
        de.acci.dcm.api.security.SecurityConfig::class,
        de.acci.dcm.api.security.CsrfController::class
    )
    class TestConfig {
        @Bean
        fun jwtDecoder(): ReactiveJwtDecoder {
            val jwksUri = fixture.getJwksUri()
            return NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build()
        }

        /**
         * Test controller that mimics AdminRequestController's approve/reject endpoints.
         *
         * Uses @PreAuthorize at class level (same as AdminRequestController) to
         * validate that admin role enforcement works correctly for POST methods.
         */
        @RestController
        @RequestMapping("/api/admin")
        @PreAuthorize("hasRole('admin')")
        class AdminApproveRejectTestController {

            @PostMapping("/requests/{id}/approve")
            fun approveRequest(
                @PathVariable id: String,
                @RequestBody body: ApproveRequestBody
            ): Mono<Map<String, Any>> = Mono.just(
                mapOf(
                    "requestId" to id,
                    "status" to "APPROVED"
                )
            )

            @PostMapping("/requests/{id}/reject")
            fun rejectRequest(
                @PathVariable id: String,
                @RequestBody body: RejectRequestBody
            ): Mono<Map<String, Any>> = Mono.just(
                mapOf(
                    "requestId" to id,
                    "status" to "REJECTED"
                )
            )
        }
    }

    @Nested
    @DisplayName("POST /api/admin/requests/{id}/approve - Role-Based Access")
    inner class ApproveEndpointRoleTests {

        @Test
        @DisplayName("Admin user can access approve endpoint")
        fun `admin user can access approve endpoint`() {
            // Given - Admin user has 'admin' role in Keycloak realm
            val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_ADMIN)
            val body = mapOf("version" to 1)

            // When/Then - Should return 200 OK
            webTestClient.post()
                .uri("/api/admin/requests/$testRequestId/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.requestId").isEqualTo(testRequestId)
                .jsonPath("$.status").isEqualTo("APPROVED")
        }

        @Test
        @DisplayName("Non-admin user gets 403 Forbidden")
        fun `non-admin user gets 403 forbidden on approve`() {
            // Given - Regular user has only 'user' role, not 'admin'
            val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)
            val body = mapOf("version" to 1)

            // When/Then - Should return 403 Forbidden
            webTestClient.post()
                .uri("/api/admin/requests/$testRequestId/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        @DisplayName("Unauthenticated request gets 403 Forbidden (CSRF protection)")
        fun `unauthenticated request to approve gets 403`() {
            // When/Then - No Authorization header + no CSRF token = 403
            // CSRF filter runs before authentication, rejecting requests without token
            val body = mapOf("version" to 1)

            webTestClient.post()
                .uri("/api/admin/requests/$testRequestId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        @DisplayName("Invalid JWT gets 401 Unauthorized")
        fun `invalid jwt gets 401 unauthorized on approve`() {
            // Given - Invalid token (intentionally invalid for testing)
            // ggignore (GitGuardian) - This is a fake test token, not a real secret
            val invalidToken = "not-a-valid-jwt"
            val body = mapOf("version" to 1)

            // When/Then - Should return 401
            webTestClient.post()
                .uri("/api/admin/requests/$testRequestId/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $invalidToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isUnauthorized
        }
    }

    @Nested
    @DisplayName("POST /api/admin/requests/{id}/reject - Role-Based Access")
    inner class RejectEndpointRoleTests {

        @Test
        @DisplayName("Admin user can access reject endpoint")
        fun `admin user can access reject endpoint`() {
            // Given - Admin user has 'admin' role in Keycloak realm
            val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_ADMIN)
            val body = mapOf(
                "version" to 1,
                "reason" to "Resources not available for this allocation"
            )

            // When/Then - Should return 200 OK
            webTestClient.post()
                .uri("/api/admin/requests/$testRequestId/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.requestId").isEqualTo(testRequestId)
                .jsonPath("$.status").isEqualTo("REJECTED")
        }

        @Test
        @DisplayName("Non-admin user gets 403 Forbidden")
        fun `non-admin user gets 403 forbidden on reject`() {
            // Given - Regular user has only 'user' role, not 'admin'
            val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)
            val body = mapOf(
                "version" to 1,
                "reason" to "Resources not available"
            )

            // When/Then - Should return 403 Forbidden
            webTestClient.post()
                .uri("/api/admin/requests/$testRequestId/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        @DisplayName("Unauthenticated request gets 403 Forbidden (CSRF protection)")
        fun `unauthenticated request to reject gets 403`() {
            // When/Then - No Authorization header + no CSRF token = 403
            // CSRF filter runs before authentication, rejecting requests without token
            val body = mapOf(
                "version" to 1,
                "reason" to "Resources not available"
            )

            webTestClient.post()
                .uri("/api/admin/requests/$testRequestId/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        @DisplayName("Invalid JWT gets 401 Unauthorized")
        fun `invalid jwt gets 401 unauthorized on reject`() {
            // Given - Invalid token
            // ggignore (GitGuardian) - This is a fake test token, not a real secret
            val invalidToken = "invalid-token"
            val body = mapOf(
                "version" to 1,
                "reason" to "Resources not available"
            )

            // When/Then - Should return 401
            webTestClient.post()
                .uri("/api/admin/requests/$testRequestId/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $invalidToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isUnauthorized
        }
    }

    @Nested
    @DisplayName("Request Body Validation")
    inner class RequestBodyValidationTests {

        @Test
        @DisplayName("Approve endpoint accepts request with version")
        fun `approve endpoint accepts request with version`() {
            // Given
            val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_ADMIN)
            val body = mapOf("version" to 5)

            // When/Then
            webTestClient.post()
                .uri("/api/admin/requests/$testRequestId/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        @DisplayName("Reject endpoint accepts request with version and reason")
        fun `reject endpoint accepts request with version and reason`() {
            // Given
            val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_ADMIN)
            val body = mapOf(
                "version" to 3,
                "reason" to "This request exceeds project resource limits"
            )

            // When/Then
            webTestClient.post()
                .uri("/api/admin/requests/$testRequestId/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk
        }
    }

    // Note: Cross-tenant security is enforced via PostgreSQL RLS in production,
    // not at the endpoint level. RLS tests are in the infrastructure layer.
}
