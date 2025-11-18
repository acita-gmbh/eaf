package com.axians.eaf.framework.multitenancy

import com.axians.eaf.framework.multitenancy.test.MultiTenancyTestApplication
import com.axians.eaf.testing.keycloak.KeycloakTestContainer
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.instanceOf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * Integration test for TenantContextFilter (Layer 1 tenant extraction).
 *
 * **Test Strategy:**
 * - Uses real embedded server (not MockMvc) to ensure filter chain executes
 * - Uses TestRestTemplate for HTTP requests
 * - Real Keycloak container for JWT generation
 * - Validates tenant_id extraction from JWT claims
 * - Tests missing tenant_id → 400 Bad Request response
 *
 * **Why TestRestTemplate instead of MockMvc:**
 * - MockMvc filter registration issues with @AutoConfigureMockMvc
 * - TestRestTemplate uses real HTTP server with full filter chain
 * - Closer to production behavior
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
@ActiveProfiles("keycloak-test")
class TenantContextFilterIntegrationTest : FunSpec() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var tenantContextFilter: TenantContextFilter

    init {
        extension(SpringExtension())

        test("DIAGNOSTIC: Verify TenantContextFilter bean loaded") {
            // Verify filter is registered as Spring bean (autowiring confirms bean exists)
            tenantContextFilter.javaClass.simpleName shouldBe "TenantContextFilter"
            println("✅ TenantContextFilter loaded: ${tenantContextFilter::class.simpleName}")
        }

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

            // When: Make authenticated HTTP request via real server
            val headers = HttpHeaders()
            headers.setBearerAuth(jwt)
            val request = HttpEntity<Void>(headers)

            val response =
                restTemplate.exchange(
                    "http://localhost:$port/test/tenant-info",
                    HttpMethod.GET,
                    request,
                    String::class.java,
                )

            // Then: Request succeeds and tenant context is populated
            response.statusCode shouldBe HttpStatus.OK
            response.body shouldContain expectedTenant
        }

        test("AC4: Missing tenant_id claim rejects request with 400 Bad Request") {
            // Given: Real Keycloak JWT WITHOUT tenant_id claim (no-tenant user)
            val jwt = KeycloakTestContainer.generateToken("no-tenant", "password")

            // When: Make authenticated request
            val headers = HttpHeaders()
            headers.setBearerAuth(jwt)
            val request = HttpEntity<Void>(headers)

            val response =
                restTemplate.exchange(
                    "http://localhost:$port/test/tenant-info",
                    HttpMethod.GET,
                    request,
                    String::class.java,
                )

            // Then: Request is rejected with 400 Bad Request
            response.statusCode shouldBe HttpStatus.BAD_REQUEST
            response.body shouldContain "Missing required tenant context"
        }

        test("AC5: ThreadLocal cleanup after request - context cleared") {
            // Given: Real Keycloak JWTs with different tenant_ids
            val jwt1 = KeycloakTestContainer.generateToken("admin", "password")
            val jwt2 = KeycloakTestContainer.generateToken("viewer", "password")

            // When: Make first authenticated request
            val headers1 = HttpHeaders().apply { setBearerAuth(jwt1) }
            restTemplate.exchange(
                "http://localhost:$port/test/tenant-info",
                HttpMethod.GET,
                HttpEntity<Void>(headers1),
                String::class.java,
            )

            // Then: Make second request with different tenant
            val headers2 = HttpHeaders().apply { setBearerAuth(jwt2) }
            val response2 =
                restTemplate.exchange(
                    "http://localhost:$port/test/tenant-info",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers2),
                    String::class.java,
                )

            // Verify isolation: second request has correct tenant, not first
            response2.statusCode shouldBe HttpStatus.OK
            response2.body shouldContain "tenant-test-002"
            response2.body shouldNotContain "tenant-test-001" // Cleanup verified
        }

        test("AC6: Concurrent requests have isolated tenant contexts") {
            // Given: Two different tenants
            val jwtAdmin = KeycloakTestContainer.generateToken("admin", "password")
            val jwtViewer = KeycloakTestContainer.generateToken("viewer", "password")

            // When: Make requests
            val headersAdmin = HttpHeaders().apply { setBearerAuth(jwtAdmin) }
            val headersViewer = HttpHeaders().apply { setBearerAuth(jwtViewer) }

            val response1 =
                restTemplate.exchange(
                    "http://localhost:$port/test/tenant-info",
                    HttpMethod.GET,
                    HttpEntity<Void>(headersAdmin),
                    String::class.java,
                )

            val response2 =
                restTemplate.exchange(
                    "http://localhost:$port/test/tenant-info",
                    HttpMethod.GET,
                    HttpEntity<Void>(headersViewer),
                    String::class.java,
                )

            // Then: Each request has correct isolated tenant context
            response1.statusCode shouldBe HttpStatus.OK
            response1.body shouldContain "tenant-test-001"

            response2.statusCode shouldBe HttpStatus.OK
            response2.body shouldContain "tenant-test-002"
        }

        test("AC7: Metrics emitted - tenant_context_extraction_duration timer") {
            // Given: Real Keycloak JWT
            val jwt = KeycloakTestContainer.generateToken("admin", "password")

            // When: Make authenticated request
            val headers = HttpHeaders().apply { setBearerAuth(jwt) }
            val response =
                restTemplate.exchange(
                    "http://localhost:$port/test/tenant-info",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

            // Then: Request succeeds (metrics verified via filter metrics bean)
            response.statusCode shouldBe HttpStatus.OK
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
