package com.axians.eaf.framework.security

import com.axians.eaf.framework.security.dto.SecureEndpointResponse
import com.axians.eaf.testing.auth.KeycloakTestTokenProvider
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource

/**
 * Comprehensive security integration tests for Spring Security OAuth2 OIDC integration.
 * Tests authentication flows, JWT validation, and security boundary enforcement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = ["spring.profiles.active=test"])
class SecurityIntegrationTest : BehaviorSpec() {
    @LocalServerPort
    private var port: Int = 0

    private val restTemplate = TestRestTemplate()

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") {
                "${TestContainers.keycloak.authServerUrl}/realms/eaf"
            }
        }
    }

    init {
        given("Spring Security OAuth2 OIDC integration") {

            `when`("making unauthenticated request to secured endpoint") {
                then("should return 401 Unauthorized (AC4 - part 1)") {
                    val response =
                        restTemplate.getForEntity(
                            "http://localhost:$port/api/secure/hello",
                            String::class.java,
                        )

                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("making authenticated request with valid token") {
                then("should return 200 OK with JWT claims (AC4 - part 2)") {
                    val token = KeycloakTestTokenProvider.getAdminToken()
                    val headers =
                        HttpHeaders().apply {
                            setBearerAuth(token)
                        }
                    val entity = HttpEntity<String>(headers)

                    val response =
                        restTemplate.exchange(
                            "http://localhost:$port/api/secure/hello",
                            HttpMethod.GET,
                            entity,
                            SecureEndpointResponse::class.java,
                        )

                    response.statusCode shouldBe HttpStatus.OK
                    response.body shouldNotBe null
                    val responseBody = response.body!!
                    responseBody.message shouldBe "Hello from secured endpoint!"

                    val userClaims = responseBody.user
                    userClaims.id shouldNotBe null
                    userClaims.tenantId shouldNotBe null
                }
            }

            `when`("validating JWT claims extraction") {
                then("should extract tenant context correctly (Subtask 4.4)") {
                    val token = KeycloakTestTokenProvider.getBasicUserToken()
                    val headers =
                        HttpHeaders().apply {
                            setBearerAuth(token)
                        }
                    val entity = HttpEntity<String>(headers)

                    val response =
                        restTemplate.exchange(
                            "http://localhost:$port/api/secure/hello",
                            HttpMethod.GET,
                            entity,
                            SecureEndpointResponse::class.java,
                        )

                    response.statusCode shouldBe HttpStatus.OK
                    val userClaims = response.body!!.user

                    // Verify tenant context propagation
                    userClaims.tenantId shouldNotBe null
                    userClaims.roles shouldNotBe null
                    userClaims.issuer?.toString() shouldContain "eaf"
                    userClaims.audience shouldNotBe null
                }
            }
        }
    }
}
