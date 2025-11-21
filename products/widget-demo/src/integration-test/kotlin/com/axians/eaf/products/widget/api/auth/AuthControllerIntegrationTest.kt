package com.axians.eaf.products.widget.api.auth

import com.axians.eaf.products.widget.test.config.TestAutoConfigurationOverrides
import com.axians.eaf.testing.keycloak.KeycloakTestContainer
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
@SpringBootTest(
    classes = [AuthControllerTestApplication::class],
    properties = [TestAutoConfigurationOverrides.DISABLE_MODULITH_JPA],
)
@AutoConfigureMockMvc
@ActiveProfiles("keycloak-test")
class AuthControllerIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `admin can revoke token and revoked token is rejected`() {
        val adminToken = KeycloakTestContainer.generateToken("admin", "password")
        val payload = objectMapper.writeValueAsString(mapOf("token" to adminToken))

        mockMvc
            .post("/auth/revoke") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = payload
            }.andExpect {
                status { isNoContent() }
            }

        mockMvc
            .post("/auth/revoke") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = payload
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `non-admin user receives 403 when invoking revoke endpoint`() {
        val viewerToken = KeycloakTestContainer.generateToken("viewer", "password")
        val payload = objectMapper.writeValueAsString(mapOf("token" to viewerToken))

        mockMvc
            .post("/auth/revoke") {
                header("Authorization", "Bearer $viewerToken")
                contentType = MediaType.APPLICATION_JSON
                content = payload
            }.andExpect {
                status { isForbidden() }
            }
    }

    companion object {
        @Container
        private val redis: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("redis:7.2-alpine")).apply {
                withExposedPorts(6379)
            }

        @DynamicPropertySource
        @JvmStatic
        fun register(registry: DynamicPropertyRegistry) {
            KeycloakTestContainer.start()
            redis.start()

            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("eaf.security.jwt.issuer-uri") { KeycloakTestContainer.getIssuerUri() }
            registry.add("eaf.security.jwt.jwks-uri") { KeycloakTestContainer.getJwksUri() }
            registry.add("eaf.security.jwt.audience") { "eaf-api" }
        }
    }
}
