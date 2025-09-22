package com.axians.eaf.testing.containers

import com.axians.eaf.testing.auth.KeycloakTestTokenProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Integration tests for Keycloak Testcontainer configuration.
 *
 * Validates container setup, realm configuration, and authentication
 * token generation for Epic 3 authentication testing foundation.
 */
class KeycloakTestContainerTest :
    FunSpec({

        test("3.1-INT-001: Keycloak container starts with realm import") {
            // Given: Keycloak container configuration with EAF test realm
            val container = TestContainers.keycloak

            // When: Container is accessed (triggers startup)
            val authServerUrl = container.authServerUrl

            // Then: Container should be running and accessible
            container.isRunning shouldBe true
            authServerUrl shouldNotBe null
            authServerUrl shouldStartWith "http://"
        }

        test("3.1-INT-002: OIDC discovery endpoint accessibility") {
            // Given: Running Keycloak container with EAF test realm
            val container = TestContainers.keycloak
            val discoveryUrl = "${container.authServerUrl}/realms/eaf-test/.well-known/openid_configuration"

            // When: OIDC discovery endpoint is accessed
            val httpClient = HttpClient.newHttpClient()
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(discoveryUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            // Then: Discovery endpoint should return valid OIDC configuration
            response.statusCode() shouldBe 200
            response.body() shouldContain "token_endpoint"
            response.body() shouldContain "authorization_endpoint"
            response.body() shouldContain "RS256"
        }

        test("3.1-INT-003: Container integration with existing infrastructure") {
            // Given: Both PostgreSQL and Keycloak containers
            val postgresContainer = TestContainers.postgres
            val keycloakContainer = TestContainers.keycloak

            // When: Both containers are accessed
            val postgresRunning = postgresContainer.isRunning
            val keycloakRunning = keycloakContainer.isRunning

            // Then: Both containers should be running without conflicts
            postgresRunning shouldBe true
            keycloakRunning shouldBe true

            // Verify no port conflicts
            val postgresPort = postgresContainer.getMappedPort(5432)
            val keycloakPort = keycloakContainer.getMappedPort(8080)
            postgresPort shouldNotBe keycloakPort
        }

        test("3.1-UNIT-004: KeycloakTestTokenProvider admin token generation") {
            // Given: KeycloakTestTokenProvider utility
            val tokenProvider = KeycloakTestTokenProvider

            // When: Admin token is requested
            val adminToken = tokenProvider.getAdminToken()

            // Then: Valid JWT token should be returned
            adminToken shouldNotBe null
            adminToken.split(".").size shouldBe 3 // JWT has 3 parts
            adminToken shouldStartWith "eyJ" // JWT header start
        }

        test("3.1-INT-005: Token generation for different user types") {
            // Given: KeycloakTestTokenProvider with different user types
            val tokenProvider = KeycloakTestTokenProvider

            // When: Tokens are requested for different users
            val adminToken = tokenProvider.getAdminToken()
            val userToken = tokenProvider.getBasicUserToken()

            // Then: Different tokens should be generated
            adminToken shouldNotBe null
            userToken shouldNotBe null
            adminToken shouldNotBe userToken // Different users get different tokens

            // Validate token structure
            adminToken.split(".").size shouldBe 3
            userToken.split(".").size shouldBe 3
        }

        test("3.1-INT-006: Token caching and expiration handling") {
            // Given: KeycloakTestTokenProvider with caching
            val tokenProvider = KeycloakTestTokenProvider

            // Clear cache to start fresh
            tokenProvider.clearCache()

            // When: Same token is requested multiple times
            val token1 = tokenProvider.getAdminToken()
            val token2 = tokenProvider.getAdminToken()

            // Then: Same token should be returned from cache
            token1 shouldBe token2 // Cache should return same token

            // Verify token is valid JWT
            token1 shouldNotBe null
            token1.split(".").size shouldBe 3
        }

        test("3.1-E2E-001: Complete authentication testing workflow") {
            // Given: Complete Keycloak authentication infrastructure
            val container = TestContainers.keycloak
            val tokenProvider = KeycloakTestTokenProvider

            // When: Complete authentication workflow is executed
            // 1. Validate container accessibility
            val setupValid = tokenProvider.validateKeycloakSetup()

            // 2. Generate tokens for different users
            val adminToken = tokenProvider.getAdminToken()
            val userToken = tokenProvider.getBasicUserToken()

            // 3. Validate OIDC discovery
            val discoveryUrl = tokenProvider.getOidcDiscoveryUrl()

            // Then: Complete workflow should work successfully
            setupValid shouldBe true
            adminToken shouldNotBe null
            userToken shouldNotBe null
            discoveryUrl shouldContain "eaf-test"
            discoveryUrl shouldContain ".well-known/openid_configuration"

            // Validate different tokens for different users
            adminToken shouldNotBe userToken
        }

        test("3.1-UNIT-001: EAF test realm JSON structure validation") {
            // Given: EAF test realm configuration file
            val realmConfigPath = this::class.java.getResource("/keycloak/eaf-test-realm.json")

            // When: Configuration file is loaded
            realmConfigPath shouldNotBe null

            // Then: Configuration should be accessible and valid
            val configContent = realmConfigPath!!.readText()
            configContent shouldContain "eaf-test"
            configContent shouldContain "eaf-client"
            configContent shouldContain "admin-user"
            configContent shouldContain "basic-user"
            configContent shouldContain "RS256"
        }
    }) {
    companion object {
        init {
            // Ensure containers are started before tests
            TestContainers.startAll()
        }
    }
}
