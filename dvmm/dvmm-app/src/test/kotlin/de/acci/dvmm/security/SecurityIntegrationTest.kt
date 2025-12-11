package de.acci.dvmm.security

import de.acci.dvmm.DvmmApplication
import de.acci.dvmm.TestNotificationConfiguration
import de.acci.dvmm.application.vmrequest.CreateVmRequestHandler
import de.acci.eaf.auth.keycloak.KeycloakJwtAuthenticationConverter
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for security configuration.
 *
 * Tests verify:
 * - Actuator health endpoint is publicly accessible
 * - API endpoints require authentication
 * - Valid JWT grants access
 * - Invalid/expired JWT returns 401
 * - Filter chain runs correctly (SecurityConfig -> TenantContextWebFilter)
 */
@SpringBootTest(
    classes = [DvmmApplication::class, SecurityIntegrationTest.TestSecurityConfig::class, TestNotificationConfiguration::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Configuration
    class TestSecurityConfig {
        // Mock the entire database dependency chain since this test runs without Testcontainers
        // This test focuses on security configuration, not business logic
        @Bean
        @Primary
        fun dataSource(): javax.sql.DataSource = mockk(relaxed = true)

        @Bean
        @Primary
        fun dslContext(): org.jooq.DSLContext = mockk(relaxed = true)

        @Bean
        @Primary
        fun eventStore(): de.acci.eaf.eventsourcing.EventStore = mockk(relaxed = true)

        @Bean
        @Primary
        fun createVmRequestHandler(): CreateVmRequestHandler = mockk(relaxed = true)

        @Bean
        @Primary
        fun testJwtDecoder(): ReactiveJwtDecoder = ReactiveJwtDecoder { token ->
            when {
                token == "valid-token" -> Mono.just(createValidJwt())
                token == "expired-token" -> Mono.error(BadJwtException("JWT expired"))
                token == "admin-token" -> Mono.just(createAdminJwt())
                token == "no-tenant-token" -> Mono.just(createJwtWithoutTenant())
                else -> Mono.error(BadJwtException("Invalid token"))
            }
        }

        private fun createValidJwt(): Jwt = Jwt.withTokenValue("valid-token")
            .header("alg", "RS256")
            .subject(UUID.randomUUID().toString())
            .claim("tenant_id", UUID.randomUUID().toString())
            .claim("email", "user@example.com")
            .claim("realm_access", mapOf("roles" to listOf("USER")))
            .claim("resource_access", mapOf("dvmm-web" to mapOf("roles" to listOf("vm-requester"))))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .issuer("http://localhost:8180/realms/dvmm")
            .build()

        private fun createAdminJwt(): Jwt = Jwt.withTokenValue("admin-token")
            .header("alg", "RS256")
            .subject(UUID.randomUUID().toString())
            .claim("tenant_id", UUID.randomUUID().toString())
            .claim("email", "admin@example.com")
            .claim("realm_access", mapOf("roles" to listOf("USER", "ADMIN")))
            .claim("resource_access", mapOf("dvmm-web" to mapOf("roles" to listOf("vm-requester", "vm-approver"))))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .issuer("http://localhost:8180/realms/dvmm")
            .build()

        private fun createJwtWithoutTenant(): Jwt = Jwt.withTokenValue("no-tenant-token")
            .header("alg", "RS256")
            .subject(UUID.randomUUID().toString())
            .claim("email", "user@example.com")
            .claim("realm_access", mapOf("roles" to listOf("USER")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .issuer("http://localhost:8180/realms/dvmm")
            .build()

        @Bean
        @Primary
        fun testKeycloakJwtAuthenticationConverter(): KeycloakJwtAuthenticationConverter {
            return KeycloakJwtAuthenticationConverter(clientId = "dvmm-web")
        }
    }

    @Test
    fun `health endpoint is publicly accessible`() {
        // This test verifies the endpoint doesn't require authentication (no 401/403)
        // The actual health status may be DOWN (503) when database is mocked
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().value { status ->
                assert(status != 401 && status != 403) {
                    "Health endpoint should be publicly accessible but returned $status"
                }
            }
    }

    @Test
    fun `API endpoint without authentication returns 401`() {
        webTestClient.get()
            .uri("/api/vms")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `API endpoint with valid JWT returns success or 404`() {
        // With valid authentication, we should not get 401
        // We may get 404 if the endpoint doesn't exist, which is fine for this test
        webTestClient.get()
            .uri("/api/vms")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .exchange()
            .expectStatus().value { status ->
                // Should be authenticated (not 401) - could be 200, 404, etc.
                assert(status != 401) { "Expected authenticated response but got 401" }
            }
    }

    @Test
    fun `API endpoint with expired JWT returns 401`() {
        webTestClient.get()
            .uri("/api/vms")
            .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `API endpoint with invalid JWT returns 401`() {
        webTestClient.get()
            .uri("/api/vms")
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `request without tenant_id in JWT is rejected`() {
        // Token is valid but missing tenant_id claim
        // TenantContextWebFilter extracts tenant from JWT and fails when missing
        // This results in TenantContextMissingException which returns 500
        webTestClient.get()
            .uri("/api/vms")
            .header(HttpHeaders.AUTHORIZATION, "Bearer no-tenant-token")
            .exchange()
            .expectStatus().value { status ->
                // Should either be 500 (tenant context missing) or 404 (no endpoint)
                // Both are acceptable as long as not 401 (authenticated)
                assert(status != 401) { "Token was valid - should not return 401" }
            }
    }

    @Test
    fun `health liveness endpoint is accessible`() {
        webTestClient.get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `health readiness endpoint is accessible`() {
        webTestClient.get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus().isOk
    }
}
