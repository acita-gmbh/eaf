package com.axians.eaf.framework.security.config

import com.axians.eaf.framework.security.test.SecurityTestApplication
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Integration test for SecurityConfiguration.
 *
 * Validates:
 * - AC7: Unauthenticated requests return 401 Unauthorized
 * - AC6: /actuator/health is public (no authentication required)
 *
 * Story 3.1: Spring Security OAuth2 Resource Server Foundation
 */
@SpringBootTest(classes = [SecurityTestApplication::class])
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "eaf.security.jwt.issuer-uri=http://localhost:8080/realms/eaf",
        "eaf.security.jwt.jwks-uri=http://localhost:8080/realms/eaf/protocol/openid-connect/certs",
        "eaf.security.jwt.audience=eaf-api",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=\${eaf.security.jwt.issuer-uri}",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=\${eaf.security.jwt.jwks-uri}",
    ],
)
class SecurityConfigurationIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `should return 401 Unauthorized for unauthenticated API requests`() {
        // AC7: Integration test validates unauthenticated requests return 401 Unauthorized
        mockMvc
            .perform(get("/api/widgets"))
            .andExpect(status().isUnauthorized())
    }

    @Test
    fun `should return 401 Unauthorized for unauthenticated root path`() {
        // AC6: All API endpoints require authentication by default
        mockMvc
            .perform(get("/"))
            .andExpect(status().isUnauthorized())
    }

    @Test
    fun `should return 401 Unauthorized for any unauthenticated endpoint`() {
        // AC6: All API endpoints require authentication by default (except /actuator/health)
        // Note: /actuator/health is tested separately when Actuator is configured
        mockMvc
            .perform(get("/api/test"))
            .andExpect(status().isUnauthorized())
    }
}
