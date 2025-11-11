package com.axians.eaf.framework.security.revocation

import com.axians.eaf.framework.security.test.SecurityTestApplication
import com.axians.eaf.testing.keycloak.KeycloakTestContainer
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(classes = [SecurityTestApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("keycloak-test", "redis-failure")
class JwtRevocationFailOpenIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())

        test("requests continue when Redis unavailable and failClosed disabled") {
            val token = KeycloakTestContainer.generateToken("admin", "password")

            mockMvc
                .get("/api/widgets") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                }
        }
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configure(registry: DynamicPropertyRegistry) {
            KeycloakTestContainer.start()
            registry.add("eaf.security.jwt.issuer-uri") { KeycloakTestContainer.getIssuerUri() }
            registry.add("eaf.security.jwt.jwks-uri") { KeycloakTestContainer.getJwksUri() }
            registry.add("eaf.security.jwt.audience") { "eaf-api" }
        }
    }
}
