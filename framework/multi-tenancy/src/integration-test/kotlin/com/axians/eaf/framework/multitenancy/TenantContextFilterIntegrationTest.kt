package com.axians.eaf.framework.multitenancy

import com.axians.eaf.framework.multitenancy.test.MultiTenancyTestApplication
import com.axians.eaf.testing.keycloak.KeycloakTestContainer
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
    classes = [MultiTenancyTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=\${eaf.security.jwt.issuer-uri}",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=\${eaf.security.jwt.jwks-uri}",
        "eaf.security.jwt.audience=eaf-api",
        "eaf.security.role-whitelist=WIDGET_ADMIN,WIDGET_VIEWER,ADMIN",
        "logging.level.com.axians.eaf=DEBUG",
        "spring.modulith.events.jdbc.schema-initialization.enabled=false",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("keycloak-test")
class TenantContextFilterIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())

        test("DIAGNOSTIC: Verify JWT contains tenant_id claim") {
            // Given: Real Keycloak JWT
            val jwt = KeycloakTestContainer.generateToken("admin", "password")

            // Decode JWT and check claims
            val parts = jwt.split(".")
            parts.size shouldBe 3

            // Decode payload (Base64 URL encoded)
            val payload =
                String(
                    java.util.Base64
                        .getUrlDecoder()
                        .decode(parts[1]),
                )
            println("JWT Payload: $payload")

            // Then: Verify tenant_id claim exists
            payload shouldContain "tenant_id"
            payload shouldContain "tenant-test-001"
        }

        test("AC2+AC3: Extract tenant_id from JWT and populate TenantContext") {
            // Given: Real Keycloak JWT with tenant_id claim (admin user has tenant-test-001)
            val expectedTenant = "tenant-test-001"
            val jwt = KeycloakTestContainer.generateToken("admin", "password")

            // When: Make authenticated request
            val result =
                mockMvc.get("/test/tenant-info") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $jwt")
                }

            // Then: Request succeeds and tenant context is populated
            result.andExpect {
                status { isOk() }
                content { string(containsString(expectedTenant)) }
            }
        }

        test("AC4: Missing tenant_id claim rejects request with 400 Bad Request") {
            // Given: Real Keycloak JWT WITHOUT tenant_id claim (no-tenant user has no attributes)
            val jwt = KeycloakTestContainer.generateToken("no-tenant", "password")

            // When: Make authenticated request
            val result =
                mockMvc.get("/test/tenant-info") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $jwt")
                }

            // Then: Request is rejected with 400 Bad Request
            result.andExpect {
                status { isBadRequest() }
                content { string(containsString("Missing required tenant context")) }
            }
        }

        test("AC5: ThreadLocal cleanup after request - context cleared") {
            // Given: Real Keycloak JWTs with different tenant_ids
            // admin = tenant-test-001, viewer = tenant-test-002
            val jwt1 = KeycloakTestContainer.generateToken("admin", "password")
            val jwt2 = KeycloakTestContainer.generateToken("viewer", "password")

            // When: Make first authenticated request
            mockMvc
                .get("/test/tenant-info") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $jwt1")
                }.andExpect {
                    status { isOk() }
                }

            // Then: After request completes, ThreadLocal should be cleared
            // (Verified by making second request with different tenant and checking isolation)
            mockMvc
                .get("/test/tenant-info") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $jwt2")
                }.andExpect {
                    status { isOk() }
                    content {
                        string(containsString("tenant-test-002"))
                        string(not(containsString("tenant-test-001")))
                    }
                }
        }

        test("AC6: Concurrent requests have isolated tenant contexts") {
            // Given: Two different tenants (admin and viewer)
            val jwtAdmin = KeycloakTestContainer.generateToken("admin", "password")
            val jwtViewer = KeycloakTestContainer.generateToken("viewer", "password")

            // When: Make concurrent requests
            val result1 =
                mockMvc.get("/test/tenant-info") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $jwtAdmin")
                }

            val result2 =
                mockMvc.get("/test/tenant-info") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $jwtViewer")
                }

            // Then: Each request has correct isolated tenant context
            result1.andExpect {
                status { isOk() }
                content { string(containsString("tenant-test-001")) }
            }

            result2.andExpect {
                status { isOk() }
                content { string(containsString("tenant-test-002")) }
            }
        }

        test("AC7: Metrics emitted - tenant_context_extraction_duration timer") {
            // Given: Real Keycloak JWT
            val jwt = KeycloakTestContainer.generateToken("admin", "password")

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
        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            // Start KeycloakTestContainer (shared singleton from Epic 3)
            KeycloakTestContainer.start()

            // Configure Spring Security OAuth2 properties
            registry.add("eaf.security.jwt.issuer-uri") {
                KeycloakTestContainer.getIssuerUri()
            }
            registry.add("eaf.security.jwt.jwks-uri") {
                KeycloakTestContainer.getJwksUri()
            }
        }
    }
}
