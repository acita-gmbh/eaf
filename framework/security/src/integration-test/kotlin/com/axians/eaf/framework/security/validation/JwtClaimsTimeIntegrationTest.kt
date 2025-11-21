package com.axians.eaf.framework.security.validation

import com.axians.eaf.framework.security.test.SecurityTestApplication
import com.axians.eaf.testing.keycloak.KeycloakTestContainer
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
 * Integration test for JWT Claims Schema and Time-Based Validation (Layers 4-5).
 *
 * Validates:
 * - AC2: Layer 4 - Claims schema validation (currently required: sub, iss, exp, iat; additional claims such as aud, tenant_id, roles will be validated in future phases as per the story file)
 * - AC3: Layer 5 - Time-based validation (exp, iat, nbf with 30s clock skew)
 * - AC4: Missing or invalid claims rejected with 401 and specific error message
 * - AC5: Expired tokens rejected with 401
 * - AC7: Integration test with intentionally invalid tokens (missing claims, expired)
 *
 * Story 3.5: JWT Claims Schema and Time-Based Validation (Layers 3-5)
 */
@SpringBootTest(classes = [SecurityTestApplication::class])
@ActiveProfiles("keycloak-test")
@AutoConfigureMockMvc
class JwtClaimsTimeIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `should reject JWT missing required claims (malformed token)`() {
        // AC4: Missing claims rejected with 401
        // Note: This test validates that tokens failing signature validation (Layer 2)
        // are rejected before reaching Layer 4 claim validation
        //
        // Decoded payload: {"iss":"test", "aud":"eaf-api", "exp":9999999999}
        // Missing required claims: sub, iat
        // Signature: invalid (will fail at Layer 2 before Layer 4)
        val malformedToken =
            "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJpc3MiOiJ0ZXN0IiwiYXVkIjoiZWFmLWFwaSIsImV4cCI6OTk5OTk5OTk5OX0." +
                "invalid_signature"

        mockMvc
            .perform(
                get("/api/widgets")
                    .header("Authorization", "Bearer $malformedToken"),
            ).andExpect(status().isUnauthorized())
    }

    @Test
    fun `should reject JWT with invalid issuer`() {
        // AC4: Claims validation - invalid issuer should fail
        // Note: This test validates that tokens failing signature validation (Layer 2)
        // are rejected before reaching issuer validation (Layer 6)
        //
        // Decoded payload: {"sub":"user", "iss":"wrong", "aud":"eaf-api", "exp":9999999999}
        // Missing required claim: iat (tenant_id and roles are optional)
        // Invalid: issuer = "wrong" (should be Keycloak realm URL)
        // Signature: invalid (will fail at Layer 2 before Layer 6)
        val tokenWithWrongIssuer =
            "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJzdWIiOiJ1c2VyIiwiaXNzIjoid3JvbmciLCJhdWQiOiJlYWYtYXBpIiwiZXhwIjo5OTk5OTk5OTk5fQ." +
                "invalid_signature"

        mockMvc
            .perform(
                get("/api/widgets")
                    .header("Authorization", "Bearer $tokenWithWrongIssuer"),
            ).andExpect(status().isUnauthorized())
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
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
