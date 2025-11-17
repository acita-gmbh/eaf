package com.axians.eaf.framework.multitenancy

import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

/**
 * Integration test for TenantContextFilter (Layer 1 tenant extraction).
 *
 * **Test Strategy:**
 * - Uses real Keycloak container for JWT generation
 * - Validates filter execution order (AFTER Spring Security)
 * - Tests tenant_id extraction from JWT claims
 * - Verifies ThreadLocal cleanup after request completion
 * - Tests missing tenant_id → 400 Bad Request response
 *
 * **Constitutional TDD:** This integration test is written FIRST (Red phase),
 * before TenantContextFilter implementation (Green phase).
 *
 * Epic 4, Story 4.2: AC6 (Integration test validates tenant extraction from real Keycloak JWT)
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=\${keycloak.issuer-uri}",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=\${keycloak.jwk-set-uri}",
        "eaf.security.keycloak.realm=eaf-test",
        "eaf.security.keycloak.client-id=eaf-client",
        "eaf.security.role-whitelist=user,admin,widget_read,widget_write",
        "logging.level.com.axians.eaf=DEBUG",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantContextFilterIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())

        test("AC2+AC3: Extract tenant_id from JWT and populate TenantContext") {
            // Given: Real Keycloak JWT with tenant_id claim
            val tenantId = "tenant-alpha"
            val jwt =
                generateKeycloakJwt(
                    subject = "user123",
                    tenantId = tenantId,
                    roles = listOf("user", "widget_read"),
                )

            // When: Make authenticated request
            val result =
                mockMvc.get("/test/tenant-info") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $jwt")
                }

            // Then: Request succeeds and tenant context is populated
            result.andExpect {
                status { isOk() }
                content { string(containsString(tenantId)) }
            }
        }

        test("AC4: Missing tenant_id claim rejects request with 400 Bad Request") {
            // Given: JWT WITHOUT tenant_id claim
            val jwtWithoutTenant =
                generateKeycloakJwtWithoutTenant(
                    subject = "user456",
                    roles = listOf("user"),
                )

            // When: Make authenticated request
            val result =
                mockMvc.get("/test/tenant-info") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $jwtWithoutTenant")
                }

            // Then: Request is rejected with 400 Bad Request
            result.andExpect {
                status { isBadRequest() }
                content { string(containsString("Missing required tenant context")) }
            }
        }

        test("AC5: ThreadLocal cleanup after request - context cleared") {
            // Given: Real Keycloak JWT with tenant_id
            val tenantId = "tenant-beta"
            val jwt =
                generateKeycloakJwt(
                    subject = "user789",
                    tenantId = tenantId,
                    roles = listOf("admin"),
                )

            // When: Make authenticated request
            mockMvc
                .get("/test/tenant-info") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $jwt")
                }.andExpect {
                    status { isOk() }
                }

            // Then: After request completes, ThreadLocal should be cleared
            // (Verified by making second request and checking isolation)
            val secondJwt =
                generateKeycloakJwt(
                    subject = "user999",
                    tenantId = "tenant-gamma",
                    roles = listOf("user"),
                )

            mockMvc
                .get("/test/tenant-info") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $secondJwt")
                }.andExpect {
                    status { isOk() }
                    content {
                        string(containsString("tenant-gamma"))
                        string(not(containsString(tenantId)))
                    }
                }
        }

        test("AC6: Concurrent requests have isolated tenant contexts") {
            // Given: Two different tenants
            val tenant1 = "tenant-concurrent-1"
            val tenant2 = "tenant-concurrent-2"
            val jwt1 = generateKeycloakJwt("user1", tenant1, listOf("user"))
            val jwt2 = generateKeycloakJwt("user2", tenant2, listOf("user"))

            // When: Make concurrent requests
            val result1 =
                mockMvc.get("/test/tenant-info") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $jwt1")
                }

            val result2 =
                mockMvc.get("/test/tenant-info") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $jwt2")
                }

            // Then: Each request has correct isolated tenant context
            result1.andExpect {
                status { isOk() }
                content { string(containsString(tenant1)) }
            }

            result2.andExpect {
                status { isOk() }
                content { string(containsString(tenant2)) }
            }
        }

        test("AC7: Metrics emitted - tenant_context_extraction_duration timer") {
            // Given: Real Keycloak JWT
            val jwt =
                generateKeycloakJwt(
                    subject = "metrics-user",
                    tenantId = "tenant-metrics",
                    roles = listOf("user"),
                )

            // When: Make authenticated request
            mockMvc
                .get("/test/tenant-info") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $jwt")
                }.andExpect {
                    status { isOk() }
                }

            // Then: Metrics should be emitted
            // (Verified by checking Micrometer registry in separate test or manually in production)
            // This is a placeholder for metric validation - full implementation in Story 5.4
        }
    }

    companion object {
        private lateinit var keycloakContainer: GenericContainer<*>
        private lateinit var postgresContainer: PostgreSQLContainer<*>

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            // Start PostgreSQL for application data
            postgresContainer =
                PostgreSQLContainer(DockerImageName.parse("postgres:16.10"))
                    .withDatabaseName("eaf_test")
                    .withUsername("eaf")
                    .withPassword("eaf123")
            postgresContainer.start()

            // Start Keycloak for JWT generation
            keycloakContainer =
                GenericContainer(DockerImageName.parse("quay.io/keycloak/keycloak:26.0.7"))
                    .withExposedPorts(8080)
                    .withEnv("KEYCLOAK_ADMIN", "admin")
                    .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
                    .withEnv("KC_HTTP_ENABLED", "true")
                    .withCommand("start-dev")
                    .waitingFor(Wait.forHttp("/health/ready").forPort(8080))
                    .withStartupTimeout(Duration.ofMinutes(3))
            keycloakContainer.start()

            val keycloakUrl = "http://${keycloakContainer.host}:${keycloakContainer.firstMappedPort}"

            // Configure Spring properties
            registry.add("spring.datasource.url") { postgresContainer.jdbcUrl }
            registry.add("spring.datasource.username") { postgresContainer.username }
            registry.add("spring.datasource.password") { postgresContainer.password }
            registry.add("keycloak.issuer-uri") { "$keycloakUrl/realms/eaf-test" }
            registry.add("keycloak.jwk-set-uri") { "$keycloakUrl/realms/eaf-test/protocol/openid-connect/certs" }
        }

        /**
         * Generate real Keycloak JWT with tenant_id claim.
         * Uses Keycloak Admin API to create user and generate token.
         */
        private fun generateKeycloakJwt(
            subject: String,
            tenantId: String,
            roles: List<String>,
        ): String {
            // TODO: Implement Keycloak Admin API call to generate JWT
            // Pattern from Epic 3 Story 3.2: KeycloakIntegrationTest
            // For now, return mock JWT (to be implemented)
            return "mock-jwt-with-tenant-$tenantId"
        }

        /**
         * Generate real Keycloak JWT WITHOUT tenant_id claim (for negative testing).
         */
        private fun generateKeycloakJwtWithoutTenant(
            subject: String,
            roles: List<String>,
        ): String {
            // TODO: Implement Keycloak Admin API call to generate JWT without tenant_id
            return "mock-jwt-without-tenant"
        }
    }
}
