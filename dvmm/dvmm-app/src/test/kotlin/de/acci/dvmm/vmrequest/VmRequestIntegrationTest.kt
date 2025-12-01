package de.acci.dvmm.vmrequest

import de.acci.dvmm.DvmmApplication
import de.acci.eaf.auth.keycloak.KeycloakJwtAuthenticationConverter
import de.acci.eaf.testing.TestContainers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for VM Request creation flow.
 *
 * Tests the complete CQRS/ES flow:
 * HTTP POST -> VmRequestController -> CreateVmRequestHandler -> VmRequestAggregate -> EventStore -> Response
 *
 * Uses Testcontainers for PostgreSQL database.
 */
@SpringBootTest(
    classes = [DvmmApplication::class, VmRequestIntegrationTest.TestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.flyway.enabled=false",
    ]
)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@DisplayName("VM Request Integration Tests")
class VmRequestIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    companion object {
        private val tenantAId = UUID.randomUUID()
        private val tenantBId = UUID.randomUUID()
        private val userAId = UUID.randomUUID()
        private val userBId = UUID.randomUUID()

        @JvmStatic
        @BeforeAll
        fun setupSchema() {
            // Initialize event store schema
            TestContainers.ensureEventStoreSchema {
                VmRequestIntegrationTest::class.java
                    .getResource("/db/migration/V001__create_event_store.sql")!!
                    .readText()
            }
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            // Configure database connection from Testcontainers
            registry.add("spring.datasource.url") { TestContainers.postgres.jdbcUrl }
            registry.add("spring.datasource.username") { TestContainers.postgres.username }
            registry.add("spring.datasource.password") { TestContainers.postgres.password }
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
        }
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun testJwtDecoder(): ReactiveJwtDecoder = ReactiveJwtDecoder { token ->
            println(">>> JWT Decoder received token: ${token.take(50)}...")
            when {
                token == TENANT_A_TOKEN -> {
                    println(">>> Matched tenant A token")
                    Mono.just(createJwt(tenantId = tenantAId, userId = userAId, tokenValue = token))
                }
                token == TENANT_B_TOKEN -> {
                    println(">>> Matched tenant B token")
                    Mono.just(createJwt(tenantId = tenantBId, userId = userBId, tokenValue = token))
                }
                token == "invalid-token" -> Mono.error(BadJwtException("Invalid token"))
                else -> {
                    println(">>> Unknown token - expected: ${TENANT_A_TOKEN.take(50)}...")
                    Mono.error(BadJwtException("Unknown token"))
                }
            }
        }

        @Bean
        @Primary
        fun testKeycloakJwtAuthenticationConverter(): KeycloakJwtAuthenticationConverter {
            return KeycloakJwtAuthenticationConverter(clientId = "dvmm-web")
        }

        private fun createJwt(tenantId: UUID, userId: UUID, tokenValue: String): Jwt = Jwt.withTokenValue(tokenValue)
            .header("alg", "RS256")
            .subject(userId.toString())
            .claim("tenant_id", tenantId.toString())
            .claim("email", "user@example.com")
            .claim("realm_access", mapOf("roles" to listOf("USER")))
            .claim("resource_access", mapOf("dvmm-web" to mapOf("roles" to listOf("vm-requester"))))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .issuer("http://localhost:8180/realms/dvmm")
            .build()

        companion object {
            // Pre-generated JWT-structured tokens for tests.
            // These tokens have the format: base64(header).base64(payload).signature
            // TenantContextWebFilter extracts tenant_id from the payload.
            val TENANT_A_TOKEN: String = createToken(tenantAId, userAId)
            val TENANT_B_TOKEN: String = createToken(tenantBId, userBId)

            private fun createToken(tenantId: UUID, userId: UUID): String {
                val header = """{"alg":"none","typ":"JWT"}"""
                val payload = """{"sub":"$userId","tenant_id":"$tenantId"}"""
                val encoder = java.util.Base64.getUrlEncoder().withoutPadding()
                return "${encoder.encodeToString(header.toByteArray())}.${encoder.encodeToString(payload.toByteArray())}."
            }

            fun tokenForTenantA(): String = TENANT_A_TOKEN
            fun tokenForTenantB(): String = TENANT_B_TOKEN
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        // Truncate event store between tests
        TestContainers.postgres.createConnection("").use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { stmt ->
                stmt.execute("TRUNCATE TABLE eaf_events.events RESTART IDENTITY CASCADE")
            }
        }
    }

    @Nested
    @DisplayName("POST /api/requests")
    inner class CreateVmRequest {

        @Test
        @DisplayName("should return 201 Created with Location header on successful submission")
        fun `should return 201 Created on successful submission`() {
            // Given: Valid request body
            val requestBody = """
                {
                    "vmName": "web-server-01",
                    "projectId": "${UUID.randomUUID()}",
                    "size": "M",
                    "justification": "Required for production web application hosting"
                }
            """.trimIndent()

            // When: POST request with valid auth
            val response = webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .exchange()

            // Then: 201 Created with Location header
            response
                .expectStatus().isCreated
                .expectHeader().exists(HttpHeaders.LOCATION)
                .expectBody()
                .jsonPath("$.id").isNotEmpty
                .jsonPath("$.vmName").isEqualTo("web-server-01")
                .jsonPath("$.size.code").isEqualTo("M")
                .jsonPath("$.size.cpuCores").isEqualTo(4)
                .jsonPath("$.size.memoryGb").isEqualTo(8)
                .jsonPath("$.size.diskGb").isEqualTo(100)
                .jsonPath("$.status").isEqualTo("PENDING")
        }

        @Test
        @DisplayName("should persist VmRequestCreated event to event store")
        fun `should persist VmRequestCreated event to event store`() {
            // Given: Valid request body
            val projectId = UUID.randomUUID()
            val requestBody = """
                {
                    "vmName": "db-server-01",
                    "projectId": "$projectId",
                    "size": "XL",
                    "justification": "Database server for production workload"
                }
            """.trimIndent()

            // When: POST request with valid auth
            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated

            // Then: Event is persisted in database
            TestContainers.postgres.createConnection("").use { conn ->
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery(
                        """
                        SELECT event_type, payload::text, tenant_id
                        FROM eaf_events.events
                        WHERE event_type = 'VmRequestCreated'
                        """.trimIndent()
                    )
                    assertTrue(rs.next(), "VmRequestCreated event should be persisted")
                    assertEquals("VmRequestCreated", rs.getString("event_type"))

                    val payload = rs.getString("payload")
                    assertTrue(payload.contains("db-server-01"), "Event payload should contain VM name")
                    assertTrue(payload.contains("XL"), "Event payload should contain size")

                    assertEquals(tenantAId, UUID.fromString(rs.getString("tenant_id")))
                }
            }
        }

        @Test
        @DisplayName("should return 400 Bad Request for invalid VM name")
        fun `should return 400 for invalid VM name`() {
            // Given: Invalid VM name (uppercase, spaces)
            val requestBody = """
                {
                    "vmName": "Invalid VM Name!",
                    "projectId": "${UUID.randomUUID()}",
                    "size": "M",
                    "justification": "Testing invalid VM name handling"
                }
            """.trimIndent()

            // When: POST request
            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .exchange()
                // Then: 400 Bad Request with validation error
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.type").isEqualTo("validation")
                .jsonPath("$.errors[0].field").isEqualTo("vmName")
        }

        @Test
        @DisplayName("should return 400 Bad Request for invalid size")
        fun `should return 400 for invalid size`() {
            // Given: Invalid size value
            val requestBody = """
                {
                    "vmName": "test-server-01",
                    "projectId": "${UUID.randomUUID()}",
                    "size": "XXL",
                    "justification": "Testing invalid size handling"
                }
            """.trimIndent()

            // When: POST request
            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .exchange()
                // Then: 400 Bad Request with validation error
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.type").isEqualTo("validation")
                .jsonPath("$.errors[0].field").isEqualTo("size")
        }

        @Test
        @DisplayName("should return 400 Bad Request for short justification")
        fun `should return 400 for short justification`() {
            // Given: Justification too short (less than 10 chars)
            val requestBody = """
                {
                    "vmName": "test-server-01",
                    "projectId": "${UUID.randomUUID()}",
                    "size": "M",
                    "justification": "Short"
                }
            """.trimIndent()

            // When: POST request
            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .exchange()
                // Then: 400 Bad Request
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("should return 401 Unauthorized without auth token")
        fun `should return 401 without auth token`() {
            // Given: Valid request body but no auth
            val requestBody = """
                {
                    "vmName": "web-server-01",
                    "projectId": "${UUID.randomUUID()}",
                    "size": "M",
                    "justification": "Testing unauthorized access"
                }
            """.trimIndent()

            // When: POST request without Authorization header
            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .exchange()
                // Then: 401 Unauthorized
                .expectStatus().isUnauthorized
        }

        @Test
        @DisplayName("should return 401 Unauthorized with invalid token")
        fun `should return 401 with invalid token`() {
            // Given: Valid request body with invalid token
            val requestBody = """
                {
                    "vmName": "web-server-01",
                    "projectId": "${UUID.randomUUID()}",
                    "size": "M",
                    "justification": "Testing invalid token handling"
                }
            """.trimIndent()

            // When: POST request with invalid token
            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .exchange()
                // Then: 401 Unauthorized
                .expectStatus().isUnauthorized
        }
    }

    @Nested
    @DisplayName("Multi-Tenant Isolation")
    inner class TenantIsolation {

        @Test
        @DisplayName("should isolate events by tenant (RLS)")
        fun `should isolate events by tenant`() {
            // Given: Request from tenant A
            val requestBodyA = """
                {
                    "vmName": "tenant-a-server",
                    "projectId": "${UUID.randomUUID()}",
                    "size": "S",
                    "justification": "Server for tenant A operations"
                }
            """.trimIndent()

            // And: Request from tenant B
            val requestBodyB = """
                {
                    "vmName": "tenant-b-server",
                    "projectId": "${UUID.randomUUID()}",
                    "size": "L",
                    "justification": "Server for tenant B operations"
                }
            """.trimIndent()

            // When: Both tenants create requests
            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBodyA)
                .exchange()
                .expectStatus().isCreated

            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantB()}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBodyB)
                .exchange()
                .expectStatus().isCreated

            // Then: Events are properly tagged with their tenant
            TestContainers.postgres.createConnection("").use { conn ->
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery(
                        """
                        SELECT tenant_id, payload::text
                        FROM eaf_events.events
                        WHERE event_type = 'VmRequestCreated'
                        ORDER BY created_at
                        """.trimIndent()
                    )

                    // First event should be tenant A
                    assertTrue(rs.next())
                    assertEquals(tenantAId, UUID.fromString(rs.getString("tenant_id")))
                    assertTrue(rs.getString("payload").contains("tenant-a-server"))

                    // Second event should be tenant B
                    assertTrue(rs.next())
                    assertEquals(tenantBId, UUID.fromString(rs.getString("tenant_id")))
                    assertTrue(rs.getString("payload").contains("tenant-b-server"))
                }
            }
        }
    }

    @Nested
    @DisplayName("VM Size Specifications")
    inner class VmSizeSpecs {

        @Test
        @DisplayName("should return correct specs for size S")
        fun `should return correct specs for size S`() {
            val requestBody = """
                {
                    "vmName": "small-server",
                    "projectId": "${UUID.randomUUID()}",
                    "size": "S",
                    "justification": "Small server for development"
                }
            """.trimIndent()

            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.size.code").isEqualTo("S")
                .jsonPath("$.size.cpuCores").isEqualTo(2)
                .jsonPath("$.size.memoryGb").isEqualTo(4)
                .jsonPath("$.size.diskGb").isEqualTo(50)
        }

        @Test
        @DisplayName("should return correct specs for size L")
        fun `should return correct specs for size L`() {
            val requestBody = """
                {
                    "vmName": "large-server",
                    "projectId": "${UUID.randomUUID()}",
                    "size": "L",
                    "justification": "Large server for compute workloads"
                }
            """.trimIndent()

            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.size.code").isEqualTo("L")
                .jsonPath("$.size.cpuCores").isEqualTo(8)
                .jsonPath("$.size.memoryGb").isEqualTo(16)
                .jsonPath("$.size.diskGb").isEqualTo(200)
        }

        @Test
        @DisplayName("should return correct specs for size XL")
        fun `should return correct specs for size XL`() {
            val requestBody = """
                {
                    "vmName": "xl-server",
                    "projectId": "${UUID.randomUUID()}",
                    "size": "XL",
                    "justification": "Extra large server for heavy workloads"
                }
            """.trimIndent()

            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.size.code").isEqualTo("XL")
                .jsonPath("$.size.cpuCores").isEqualTo(16)
                .jsonPath("$.size.memoryGb").isEqualTo(32)
                .jsonPath("$.size.diskGb").isEqualTo(500)
        }
    }
}
