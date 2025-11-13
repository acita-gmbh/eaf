package com.axians.eaf.framework.security.validation

import com.axians.eaf.framework.security.test.SecurityTestApplication
import com.axians.eaf.framework.security.user.UserDirectory
import com.axians.eaf.framework.security.user.UserRecord
import com.axians.eaf.testing.keycloak.KeycloakTestContainer
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.atomic.AtomicReference

/**
 * Integration test for JWT Layer 9 user validation.
 *
 * Validates:
 * - AC1: Layer 9 user validation checks if user exists and is active
 * - AC3: Invalid users are rejected with 401
 * - AC7: User validation can be enabled via configuration
 *
 * Story 3.8: User Validation and Injection Detection (Layers 9-10)
 */
@SpringBootTest(classes = [SecurityTestApplication::class, JwtUserValidationIntegrationTest.StubUserDirectoryConfiguration::class])
@ActiveProfiles("keycloak-test")
@AutoConfigureMockMvc
class JwtUserValidationIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())

        beforeSpec {
            KeycloakTestContainer.start()
        }

        beforeTest {
            StubUserDirectory.setState(UserValidationState.ACTIVE)
        }

        test("allows request when directory reports active user") {
            val adminToken = KeycloakTestContainer.generateToken("admin", "password")

            mockMvc
                .perform(
                    get("/api/widgets")
                        .header("Authorization", "Bearer $adminToken"),
                ).andExpect(status().isOk())
        }

        test("rejects request when user directory reports missing user") {
            StubUserDirectory.setState(UserValidationState.UNKNOWN)
            val adminToken = KeycloakTestContainer.generateToken("admin", "password")

            mockMvc
                .perform(
                    get("/api/widgets")
                        .header("Authorization", "Bearer $adminToken"),
                ).andExpect(status().isUnauthorized())
        }

        test("rejects request when user directory reports disabled user") {
            StubUserDirectory.setState(UserValidationState.DISABLED)
            val adminToken = KeycloakTestContainer.generateToken("admin", "password")

            mockMvc
                .perform(
                    get("/api/widgets")
                        .header("Authorization", "Bearer $adminToken"),
                ).andExpect(status().isUnauthorized())
        }
    }

    @TestConfiguration
    open class StubUserDirectoryConfiguration {
        @Bean
        @Primary
        open fun stubUserDirectory(): UserDirectory = StubUserDirectory()
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
            registry.add("eaf.keycloak.user-validation-enabled") { "true" }
        }
    }
}

private enum class UserValidationState {
    ACTIVE,
    DISABLED,
    UNKNOWN,
}

private class StubUserDirectory : UserDirectory {
    override fun findById(userId: String): UserRecord? =
        when (state.get()) {
            UserValidationState.ACTIVE -> UserRecord(userId, active = true)
            UserValidationState.DISABLED -> UserRecord(userId, active = false)
            UserValidationState.UNKNOWN -> null
        }

    companion object {
        private val state = AtomicReference(UserValidationState.ACTIVE)

        fun setState(newState: UserValidationState) {
            state.set(newState)
        }
    }
}
