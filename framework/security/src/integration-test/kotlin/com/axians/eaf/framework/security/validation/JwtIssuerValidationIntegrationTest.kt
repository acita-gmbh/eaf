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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Integration test verifying Layer 6 issuer validation rejects mismatched issuers with 401.
 */
@SpringBootTest(classes = [SecurityTestApplication::class])
@ActiveProfiles("keycloak-test")
@AutoConfigureMockMvc
@TestPropertySource(properties = ["eaf.security.jwt.issuer-uri=http://evil-issuer/realms/unknown"])
class JwtIssuerValidationIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())

        beforeSpec {
            KeycloakTestContainer.start()
        }

        test("should reject JWT when issuer claim does not match trusted realm") {
            val validJwt = KeycloakTestContainer.generateToken("admin", "password")

            mockMvc
                .perform(
                    get("/api/widgets")
                        .header("Authorization", "Bearer $validJwt"),
                ).andExpect(status().isUnauthorized())
        }
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            KeycloakTestContainer.start()

            registry.add("eaf.security.jwt.jwks-uri") {
                KeycloakTestContainer.getJwksUri()
            }
            registry.add("eaf.security.jwt.audience") { "eaf-api" }
        }
    }
}
