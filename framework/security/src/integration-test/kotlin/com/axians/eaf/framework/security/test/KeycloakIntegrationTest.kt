package com.axians.eaf.framework.security.test

import com.axians.eaf.testing.keycloak.KeycloakTestContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Integration test using Testcontainers Keycloak with real JWT tokens.
 *
 * Validates:
 * - AC2: KeycloakTestContainer creates container with realm import and generateToken()
 * - AC7: Container reuse enabled for performance
 * - AC8: Container-generated JWTs for authentication tests
 * - AC9: Security integration tests pass using Testcontainers Keycloak
 *
 * Story 3.3: Testcontainers Keycloak for Integration Tests
 */
@SpringBootTest(classes = [SecurityTestApplication::class])
@ActiveProfiles("keycloak-test")
@AutoConfigureMockMvc
class KeycloakIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `should start Keycloak container successfully`() {
        // AC7: Container reuse enabled for performance
        val issuerUri = KeycloakTestContainer.getIssuerUri()
        assertThat(issuerUri).contains("/realms/eaf")
    }

    @Test
    fun `should provide JWKS endpoint URI`() {
        val jwksUri = KeycloakTestContainer.getJwksUri()
        assertThat(jwksUri).contains("/protocol/openid-connect/certs")
    }

    @Test
    fun `should generate valid JWT token for admin user`() {
        // AC8: Container-generated JWTs for authentication tests
        val jwt = KeycloakTestContainer.generateToken("admin", "password")

        assertThat(jwt).contains(".") // JWT has 3 parts separated by dots
        assertThat(jwt.split(".")).hasSize(3)
    }

    @Test
    fun `should generate valid JWT token for viewer user`() {
        val jwt = KeycloakTestContainer.generateToken("viewer", "password")

        assertThat(jwt).contains(".")
        assertThat(jwt.split(".")).hasSize(3)
    }

    @Test
    fun `should allow authenticated requests with valid JWT`() {
        // AC9: All security integration tests pass using Testcontainers Keycloak
        val jwt = KeycloakTestContainer.generateToken("admin", "password")

        mockMvc
            .perform(
                get("/api/widgets")
                    .header("Authorization", "Bearer $jwt"),
            ).andExpect(status().isOk())
    }

    @Test
    fun `should reject unauthenticated requests`() {
        // Verify security is still enforced with Testcontainers config
        mockMvc
            .perform(get("/api/widgets"))
            .andExpect(status().isUnauthorized())
    }

    @Test
    fun `should reject requests with invalid JWT`() {
        val invalidJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid.signature"

        mockMvc
            .perform(
                get("/api/widgets")
                    .header("Authorization", "Bearer $invalidJwt"),
            ).andExpect(status().isUnauthorized())
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            // AC7: Container reuse - start once per test class
            KeycloakTestContainer.start()

            registry.add("eaf.security.jwt.issuer-uri") {
                KeycloakTestContainer.getIssuerUri()
            }
            registry.add("eaf.security.jwt.jwks-uri") {
                KeycloakTestContainer.getJwksUri()
            }
            registry.add("eaf.security.jwt.audience") { "eaf-api" }
        }
    }
}
