package com.axians.eaf.framework.security.revocation

import com.axians.eaf.framework.security.test.SecurityTestApplication
import com.axians.eaf.testing.keycloak.KeycloakTestContainer
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
@SpringBootTest(classes = [SecurityTestApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("keycloak-test")
class JwtRevocationIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var revocationStore: RedisRevocationStore

    @Autowired
    private lateinit var jwtDecoder: JwtDecoder

    init {
        extension(SpringExtension())

        test("revoked token is rejected with 401") {
            val token = KeycloakTestContainer.generateToken("admin", "password")

            mockMvc
                .get("/api/widgets") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                }

            val decoded = jwtDecoder.decode(token)
            revocationStore.revoke(decoded.id, decoded.expiresAt)

            mockMvc
                .get("/api/widgets") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isUnauthorized() }
                }
        }
    }

    companion object {
        @Container
        private val redis: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("redis:7.2-alpine"))
                .withExposedPorts(6379)

        @DynamicPropertySource
        @JvmStatic
        fun configure(registry: DynamicPropertyRegistry) {
            KeycloakTestContainer.start()

            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("eaf.security.jwt.issuer-uri") { KeycloakTestContainer.getIssuerUri() }
            registry.add("eaf.security.jwt.jwks-uri") { KeycloakTestContainer.getJwksUri() }
            registry.add("eaf.security.jwt.audience") { "eaf-api" }
        }
    }
}
