package com.axians.eaf.framework.security.validation

import com.axians.eaf.framework.security.test.KeycloakTestContainer
import com.axians.eaf.framework.security.test.SecurityTestApplication
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
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
 * Integration test for JWT Format and Signature Validation (Layers 1-2).
 *
 * Validates:
 * - AC2: Token extraction from Authorization Bearer header
 * - AC3: RS256 algorithm enforcement (reject HS256)
 * - AC4: Invalid format tokens rejected with 401
 * - AC5: Invalid signature tokens rejected with 401
 * - AC7: Integration test with real Keycloak tokens
 *
 * Story 3.4: JWT Format and Signature Validation (Layers 1-2)
 */
@SpringBootTest(classes = [SecurityTestApplication::class])
@ActiveProfiles("keycloak-test")
@AutoConfigureMockMvc
class JwtFormatSignatureIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())

        beforeSpec {
            KeycloakTestContainer.start()
        }

        test("should accept valid JWT with correct format and RS256 signature") {
            // AC7: Integration test with real Keycloak tokens
            val validJwt = KeycloakTestContainer.generateToken("admin", "password")

            mockMvc
                .perform(
                    get("/api/widgets")
                        .header("Authorization", "Bearer $validJwt"),
                ).andExpect(status().isOk())
        }

        test("should return 403 when user lacks required admin role") {
            val viewerJwt = KeycloakTestContainer.generateToken("viewer", "password")

            mockMvc
                .perform(
                    get("/api/widgets")
                        .header("Authorization", "Bearer $viewerJwt"),
                ).andExpect(status().isForbidden())
        }

        test("should reject JWT with invalid format (only 2 parts)") {
            // AC4: Invalid format tokens rejected with 401
            val invalidFormatJwt = "header.payload" // Missing signature

            mockMvc
                .perform(
                    get("/api/widgets")
                        .header("Authorization", "Bearer $invalidFormatJwt"),
                ).andExpect(status().isUnauthorized())
        }

        test("should reject JWT with invalid format (4 parts)") {
            // AC4: Invalid format tokens rejected with 401
            val invalidFormatJwt = "header.payload.signature.extra"

            mockMvc
                .perform(
                    get("/api/widgets")
                        .header("Authorization", "Bearer $invalidFormatJwt"),
                ).andExpect(status().isUnauthorized())
        }

        test("should reject JWT with invalid signature") {
            // AC5: Invalid signature tokens rejected with 401
            val validJwt = KeycloakTestContainer.generateToken("admin", "password")
            val tamperedJwt = validJwt.replaceAfterLast(".", "invalid_signature")

            mockMvc
                .perform(
                    get("/api/widgets")
                        .header("Authorization", "Bearer $tamperedJwt"),
                ).andExpect(status().isUnauthorized())
        }

        test("should reject JWT with HS256 algorithm") {
            // AC3: RS256 algorithm enforcement (reject HS256)
            // HS256 token created with HMAC (algorithm confusion attack simulation)
            val hs256Token =
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                    "eyJzdWIiOiJ0ZXN0IiwiaXNzIjoiZXZpbCIsImV4cCI6OTk5OTk5OTk5OX0." +
                    "signature"

            mockMvc
                .perform(
                    get("/api/widgets")
                        .header("Authorization", "Bearer $hs256Token"),
                ).andExpect(status().isUnauthorized())
        }

        test("should reject request without Authorization header") {
            // AC2: Token extraction from Authorization Bearer header
            mockMvc
                .perform(get("/api/widgets"))
                .andExpect(status().isUnauthorized())
        }

        test("should reject request with malformed Authorization header") {
            // AC2: Token extraction validation
            mockMvc
                .perform(
                    get("/api/widgets")
                        .header("Authorization", "NotBearer token"),
                ).andExpect(status().isUnauthorized())
        }
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
