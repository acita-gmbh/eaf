package de.acci.dcm.api.security

import de.acci.eaf.testing.keycloak.KeycloakTestFixture
import de.acci.eaf.testing.keycloak.KeycloakTestUsers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Integration tests for Keycloak JWT role mapping.
 *
 * Story 2.9 - Task 2.2: CRITICAL integration test verifying Keycloak JWT structure.
 *
 * These tests validate that:
 * 1. Actual Keycloak JWT tokens have the expected `realm_access.roles` structure
 * 2. Roles are correctly extracted from the JWT claims
 * 3. Spring Security authorities are properly mapped with ROLE_ prefix
 * 4. The role mapping works with real Keycloak-issued tokens (not mocked JWTs)
 *
 * This ensures production Keycloak configuration matches our test assumptions
 * and prevents role mapping failures in production.
 */
@SpringBootTest(
    classes = [KeycloakRoleMappingIntegrationTest.TestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class KeycloakRoleMappingIntegrationTest {

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
            val jwksUri = KeycloakRoleMappingIntegrationTest.fixture.getJwksUri()
            return NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build()
        }

        /**
         * Test controller that returns authentication details for verification.
         */
        @RestController
        class AuthInfoController {
            @GetMapping("/api/auth/roles")
            @PreAuthorize("isAuthenticated()")
            fun getRoles(): Mono<AuthInfo> {
                return ReactiveSecurityContextHolder.getContext()
                    .map { securityContext ->
                        val authentication = securityContext.authentication as? JwtAuthenticationToken
                            ?: error(
                                "Expected JwtAuthenticationToken but got " +
                                    "${securityContext.authentication::class.simpleName}"
                            )
                        val authorities = authentication.authorities.map { it.authority }
                        val jwt = authentication.token

                        AuthInfo(
                            authorities = authorities,
                            realmRoles = extractRealmRoles(jwt),
                            subject = jwt.subject,
                            tenantId = jwt.getClaimAsString("tenant_id")
                        )
                    }
            }

            /**
             * Admin-only endpoint for testing hasRole() authorization.
             */
            @GetMapping("/api/auth/admin-only")
            @PreAuthorize("hasRole('admin')")
            fun adminOnly(): Mono<Map<String, String>> {
                return Mono.just(mapOf("message" to "Admin access granted"))
            }

            private fun extractRealmRoles(jwt: org.springframework.security.oauth2.jwt.Jwt): List<String> {
                val realmAccess = jwt.getClaim<Map<String, Any>>("realm_access") ?: return emptyList()
                val roles = realmAccess["roles"] as? List<*> ?: return emptyList()
                return roles.filterIsInstance<String>()
            }

            data class AuthInfo(
                val authorities: List<String>,
                val realmRoles: List<String>,
                val subject: String,
                val tenantId: String?
            )
        }
    }

    /**
     * Verifies that Keycloak JWT contains realm_access.roles claim.
     * This is the critical claim structure our role mapping depends on.
     */
    @Test
    fun `Keycloak JWT contains realm_access roles claim`() {
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_ADMIN)

        val response = webTestClient.get()
            .uri("/api/auth/roles")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody(TestConfig.AuthInfoController.AuthInfo::class.java)
            .returnResult()
            .responseBody!!

        // Verify realm_access.roles exists and contains expected roles
        assertTrue(
            response.realmRoles.isNotEmpty(),
            "realm_access.roles should not be empty for authenticated user"
        )
        assertTrue(
            response.realmRoles.contains("admin"),
            "Admin user should have 'admin' role in realm_access.roles"
        )
        assertTrue(
            response.realmRoles.contains("user"),
            "Admin user should have 'user' role in realm_access.roles"
        )
    }

    /**
     * Verifies that realm roles are mapped to Spring Security authorities with ROLE_ prefix.
     * This tests the KeycloakJwtAuthenticationConverter integration.
     */
    @Test
    fun `realm roles are mapped to Spring authorities with ROLE_ prefix`() {
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_ADMIN)

        val response = webTestClient.get()
            .uri("/api/auth/roles")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody(TestConfig.AuthInfoController.AuthInfo::class.java)
            .returnResult()
            .responseBody!!

        // Verify authorities have ROLE_ prefix
        assertTrue(
            response.authorities.contains("ROLE_admin"),
            "Admin role should be mapped to ROLE_admin authority"
        )
        assertTrue(
            response.authorities.contains("ROLE_user"),
            "User role should be mapped to ROLE_user authority"
        )
    }

    /**
     * Verifies role mapping for non-admin user.
     * Ensures that users without admin role don't receive admin authority.
     */
    @Test
    fun `non-admin user has user role but not admin role`() {
        val accessToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

        val response = webTestClient.get()
            .uri("/api/auth/roles")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody(TestConfig.AuthInfoController.AuthInfo::class.java)
            .returnResult()
            .responseBody!!

        // Verify user role is present
        assertTrue(
            response.authorities.contains("ROLE_user"),
            "Regular user should have ROLE_user authority"
        )
        
        // Verify admin role is NOT present
        assertFalse(
            response.authorities.contains("ROLE_admin"),
            "Regular user should NOT have ROLE_admin authority"
        )

        // Verify realm_access.roles structure
        // Note: assertEquals(expected, actual, message) - Java method, named args not supported
        assertEquals(
            /* expected = */ listOf("user"),
            /* actual = */ response.realmRoles,
            /* message = */ "Regular user should only have 'user' in realm_access.roles"
        )
    }

    /**
     * Verifies that hasRole() check works correctly with the ROLE_ prefix.
     * Spring Security's hasRole('admin') should allow admin users and deny regular users.
     */
    @Test
    fun `PreAuthorize hasRole works with Keycloak JWT roles`() {
        val adminToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_ADMIN)
        val userToken = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)

        // Admin can access the admin-only endpoint
        webTestClient.get()
            .uri("/api/auth/admin-only")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
            .exchange()
            .expectStatus().isOk

        // Regular user is forbidden from accessing admin-only endpoint
        webTestClient.get()
            .uri("/api/auth/admin-only")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $userToken")
            .exchange()
            .expectStatus().isForbidden
    }

    /**
     * Verifies tenant isolation between users from different tenants.
     *
     * AC-4 requires: "different subject IDs in their JWTs" for tenant isolation.
     * This test validates:
     * 1. Different users have different subject IDs (REQUIRED - satisfies AC-4)
     * 2. Different tenants have different tenant_id claims (OPTIONAL - enhanced isolation)
     *
     * Subject ID isolation is the primary mechanism. The tenant_id claim provides
     * additional isolation when configured in Keycloak realm mappers.
     */
    @Test
    fun `different tenant users have different subject IDs for isolation`() {
        val tenant1Token = fixture.getAccessToken(KeycloakTestUsers.TENANT1_USER)
        val tenant2Token = fixture.getAccessToken(KeycloakTestUsers.TENANT2_USER)

        val tenant1Info = webTestClient.get()
            .uri("/api/auth/roles")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $tenant1Token")
            .exchange()
            .expectStatus().isOk
            .expectBody(TestConfig.AuthInfoController.AuthInfo::class.java)
            .returnResult()
            .responseBody!!

        val tenant2Info = webTestClient.get()
            .uri("/api/auth/roles")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $tenant2Token")
            .exchange()
            .expectStatus().isOk
            .expectBody(TestConfig.AuthInfoController.AuthInfo::class.java)
            .returnResult()
            .responseBody!!

        // Verify subjects are different (user isolation)
        assertTrue(
            tenant1Info.subject != tenant2Info.subject,
            "Different users should have different subject IDs"
        )

        // Optional: Verify tenant_id claims if configured in Keycloak
        // This provides additional isolation beyond subject IDs
        if (tenant1Info.tenantId != null && tenant2Info.tenantId != null) {
            assertTrue(
                tenant1Info.tenantId != tenant2Info.tenantId,
                "Users from different tenants should have different tenant_id claims. " +
                    "Got: tenant1=${tenant1Info.tenantId}, tenant2=${tenant2Info.tenantId}"
            )
        }
        // Note: tenant_id claim requires Keycloak realm mapper configuration.
        // Subject ID isolation (verified above) satisfies AC-4 requirements.
    }
}

