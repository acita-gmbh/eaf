package de.acci.dvmm.vmrequest

import de.acci.dvmm.DvmmApplication
import de.acci.dvmm.TestNotificationConfiguration
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
import java.time.OffsetDateTime
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
    classes = [DvmmApplication::class, VmRequestIntegrationTest.TestConfig::class, TestNotificationConfiguration::class],
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
            // Use Flyway migrations for consistent schema setup
            // This ensures all tables including new columns (like BOOT_TIME from V012) are present
            TestContainers.ensureFlywayMigrations()
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
            when {
                token == TENANT_A_TOKEN -> {
                    Mono.just(createJwt(tenantId = tenantAId, userId = userAId, tokenValue = token))
                }
                token == TENANT_B_TOKEN -> {
                    Mono.just(createJwt(tenantId = tenantBId, userId = userBId, tokenValue = token))
                }
                token == "invalid-token" -> Mono.error(BadJwtException("Invalid token"))
                else -> Mono.error(BadJwtException("Unknown token"))
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
        // Truncate event store and projection tables between tests
        // Use quoted uppercase identifiers to match jOOQ-generated schema
        // Timeline events first (FK constraint), then projections
        TestContainers.postgres.createConnection("").use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { stmt ->
                stmt.execute("TRUNCATE TABLE eaf_events.events RESTART IDENTITY CASCADE")
                stmt.execute("""TRUNCATE TABLE public."REQUEST_TIMELINE_EVENTS" RESTART IDENTITY CASCADE""")
                stmt.execute("""TRUNCATE TABLE public."VM_REQUESTS_PROJECTION" RESTART IDENTITY CASCADE""")
            }
        }
    }

    /**
     * Insert a projection record directly into the database for testing.
     * Uses quoted uppercase identifiers to match jOOQ-generated schema.
     */
    private fun insertProjection(
        id: UUID,
        tenantId: UUID,
        requesterId: UUID,
        requesterName: String = "Test User",
        projectId: UUID = UUID.randomUUID(),
        projectName: String = "Test Project",
        vmName: String = "test-vm",
        size: String = "M",
        cpuCores: Int = 4,
        memoryGb: Int = 8,
        diskGb: Int = 100,
        justification: String = "Test justification for VM request",
        status: String = "PENDING",
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        updatedAt: OffsetDateTime = OffsetDateTime.now()
    ) {
        TestContainers.postgres.createConnection("").use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO public."VM_REQUESTS_PROJECTION"
                ("ID", "TENANT_ID", "REQUESTER_ID", "REQUESTER_NAME", "PROJECT_ID", "PROJECT_NAME",
                 "VM_NAME", "SIZE", "CPU_CORES", "MEMORY_GB", "DISK_GB", "JUSTIFICATION",
                 "STATUS", "CREATED_AT", "UPDATED_AT", "VERSION")
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, tenantId)
                stmt.setObject(3, requesterId)
                stmt.setString(4, requesterName)
                stmt.setObject(5, projectId)
                stmt.setString(6, projectName)
                stmt.setString(7, vmName)
                stmt.setString(8, size)
                stmt.setInt(9, cpuCores)
                stmt.setInt(10, memoryGb)
                stmt.setInt(11, diskGb)
                stmt.setString(12, justification)
                stmt.setString(13, status)
                stmt.setObject(14, createdAt)
                stmt.setObject(15, updatedAt)
                stmt.executeUpdate()
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

    @Nested
    @DisplayName("GET /api/requests/my")
    inner class GetMyRequests {

        @Test
        @DisplayName("should return 200 OK with empty list when no requests exist")
        fun `should return empty list when no requests exist`() {
            // When: User requests their list with no projections
            webTestClient.mutateWith(csrf()).get()
                .uri("/api/requests/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .exchange()
                // Then: 200 OK with empty list
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items").isArray
                .jsonPath("$.items.length()").isEqualTo(0)
                .jsonPath("$.totalElements").isEqualTo(0)
        }

        @Test
        @DisplayName("should return user's requests from projection")
        fun `should return users requests from projection`() {
            // Given: A projection exists for user A
            val requestId = UUID.randomUUID()
            insertProjection(
                id = requestId,
                tenantId = tenantAId,
                requesterId = userAId,
                requesterName = "User A",
                vmName = "user-a-server",
                size = "L",
                cpuCores = 8,
                memoryGb = 16,
                diskGb = 200,
                justification = "Production server for team A"
            )

            // When: User A requests their list
            webTestClient.mutateWith(csrf()).get()
                .uri("/api/requests/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .exchange()
                // Then: 200 OK with the request
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.items[0].id").isEqualTo(requestId.toString())
                .jsonPath("$.items[0].vmName").isEqualTo("user-a-server")
                .jsonPath("$.items[0].size.code").isEqualTo("L")
                .jsonPath("$.items[0].size.cpuCores").isEqualTo(8)
                .jsonPath("$.items[0].size.memoryGb").isEqualTo(16)
                .jsonPath("$.items[0].size.diskGb").isEqualTo(200)
                .jsonPath("$.items[0].status").isEqualTo("PENDING")
                .jsonPath("$.totalElements").isEqualTo(1)
        }

        @Test
        @DisplayName("should not return other users' requests (tenant isolation)")
        fun `should not return other users requests`() {
            // Given: Projections for both tenants
            insertProjection(
                id = UUID.randomUUID(),
                tenantId = tenantAId,
                requesterId = userAId,
                vmName = "tenant-a-vm"
            )
            insertProjection(
                id = UUID.randomUUID(),
                tenantId = tenantBId,
                requesterId = userBId,
                vmName = "tenant-b-vm"
            )

            // When: User A requests their list
            webTestClient.mutateWith(csrf()).get()
                .uri("/api/requests/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .exchange()
                // Then: Only tenant A's requests returned
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.items[0].vmName").isEqualTo("tenant-a-vm")
        }

        @Test
        @DisplayName("should return all pagination metadata fields correctly")
        fun `should return all pagination metadata fields correctly`() {
            // Given: Multiple projections for user A (5 total)
            repeat(5) { i ->
                insertProjection(
                    id = UUID.randomUUID(),
                    tenantId = tenantAId,
                    requesterId = userAId,
                    vmName = "vm-$i"
                )
            }

            // When: Request with pagination (page 0, size 2)
            webTestClient.mutateWith(csrf()).get()
                .uri("/api/requests/my?page=0&size=2")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .exchange()
                // Then: All pagination fields correctly populated
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(2)
                .jsonPath("$.totalElements").isEqualTo(5)
                .jsonPath("$.totalPages").isEqualTo(3) // ceil(5/2) = 3 pages
                .jsonPath("$.hasNext").isEqualTo(true) // page 0 of 3 has next
                .jsonPath("$.hasPrevious").isEqualTo(false) // page 0 has no previous
        }

        @Test
        @DisplayName("should return correct hasNext/hasPrevious for middle page")
        fun `should return correct hasNext and hasPrevious for middle page`() {
            // Given: Multiple projections for user A (6 total)
            repeat(6) { i ->
                insertProjection(
                    id = UUID.randomUUID(),
                    tenantId = tenantAId,
                    requesterId = userAId,
                    vmName = "middle-page-vm-$i"
                )
            }

            // When: Request middle page (page 1, size 2 -> pages 0,1,2)
            webTestClient.mutateWith(csrf()).get()
                .uri("/api/requests/my?page=1&size=2")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .exchange()
                // Then: Middle page has both next and previous
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.page").isEqualTo(1)
                .jsonPath("$.totalPages").isEqualTo(3) // ceil(6/2) = 3 pages
                .jsonPath("$.hasNext").isEqualTo(true) // page 1 has page 2
                .jsonPath("$.hasPrevious").isEqualTo(true) // page 1 has page 0
        }

        @Test
        @DisplayName("should return correct hasNext for last page")
        fun `should return correct hasNext for last page`() {
            // Given: Multiple projections for user A (4 total)
            repeat(4) { i ->
                insertProjection(
                    id = UUID.randomUUID(),
                    tenantId = tenantAId,
                    requesterId = userAId,
                    vmName = "last-page-vm-$i"
                )
            }

            // When: Request last page (page 1, size 2 -> pages 0,1)
            webTestClient.mutateWith(csrf()).get()
                .uri("/api/requests/my?page=1&size=2")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .exchange()
                // Then: Last page has no next
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.page").isEqualTo(1)
                .jsonPath("$.totalPages").isEqualTo(2) // ceil(4/2) = 2 pages
                .jsonPath("$.hasNext").isEqualTo(false) // last page has no next
                .jsonPath("$.hasPrevious").isEqualTo(true) // page 1 has page 0
        }

        @Test
        @DisplayName("should return 401 Unauthorized without auth token")
        fun `should return 401 without auth token`() {
            webTestClient.mutateWith(csrf()).get()
                .uri("/api/requests/my")
                .exchange()
                .expectStatus().isUnauthorized
        }
    }

    @Nested
    @DisplayName("GET /api/requests/{id}")
    inner class GetRequestDetail {

        @Test
        @DisplayName("should return 200 OK with request detail when user is owner")
        fun `should return request detail when user is owner`() {
            // Given: A request owned by user A in tenant A
            val requestId = UUID.randomUUID()
            insertProjection(
                id = requestId,
                tenantId = tenantAId,
                requesterId = userAId,
                vmName = "detail-test-vm",
                status = "PENDING"
            )

            // When: User A requests their own request
            val response = webTestClient.mutateWith(csrf()).get()
                .uri("/api/requests/$requestId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .exchange()

            // Then: 200 OK with request details
            response
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(requestId.toString())
                .jsonPath("$.vmName").isEqualTo("detail-test-vm")
                .jsonPath("$.status").isEqualTo("PENDING")
        }

        @Test
        @DisplayName("should return 403 Forbidden when user tries to view another user's request")
        fun `should return 403 when viewing another user request`() {
            // Given: A request owned by user B in tenant A
            val requestId = UUID.randomUUID()
            insertProjection(
                id = requestId,
                tenantId = tenantAId,
                requesterId = userBId, // Owned by user B
                vmName = "other-user-vm",
                status = "PENDING"
            )

            // When: User A (different user) tries to view user B's request
            val response = webTestClient.mutateWith(csrf()).get()
                .uri("/api/requests/$requestId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .exchange()

            // Then: 403 Forbidden
            response
                .expectStatus().isForbidden
                .expectBody()
                .jsonPath("$.type").isEqualTo("forbidden")
        }

        @Test
        @DisplayName("should return 404 Not Found when request does not exist")
        fun `should return 404 when request not found`() {
            // Given: A non-existent request ID
            val nonExistentId = UUID.randomUUID()

            // When: User tries to view non-existent request
            val response = webTestClient.mutateWith(csrf()).get()
                .uri("/api/requests/$nonExistentId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .exchange()

            // Then: 404 Not Found
            response
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.type").isEqualTo("not_found")
        }

        @Test
        @DisplayName("should return 400 Bad Request for invalid UUID format")
        fun `should return 400 for invalid uuid`() {
            // When: Request with invalid UUID format
            val response = webTestClient.mutateWith(csrf()).get()
                .uri("/api/requests/not-a-valid-uuid")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .exchange()

            // Then: 400 Bad Request
            response
                .expectStatus().isBadRequest
        }
    }

    @Nested
    @DisplayName("POST /api/requests/{id}/cancel")
    inner class CancelRequest {

        @Test
        @DisplayName("should return 200 OK when cancelling own PENDING request")
        fun `should cancel own pending request`() {
            // Given: Create a request first (stores VmRequestCreated event)
            val projectId = UUID.randomUUID()
            val requestBody = """
                {
                    "vmName": "cancel-test-vm",
                    "projectId": "$projectId",
                    "size": "M",
                    "justification": "Test VM that will be cancelled"
                }
            """.trimIndent()

            val createResponse = webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .returnResult()

            // Extract the request ID from response
            val responseJson = String(createResponse.responseBody!!)
            val requestId = responseJson.substringAfter("\"id\":\"").substringBefore("\"")

            // When: Cancel the request (projection already inserted by CreateVmRequestHandler)
            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests/$requestId/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue("""{"reason": "No longer needed"}""")
                .exchange()
                // Then: 200 OK with type field
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.type").isEqualTo("cancelled")
                .jsonPath("$.message").isEqualTo("Request cancelled successfully")
                .jsonPath("$.requestId").isEqualTo(requestId)
        }

        @Test
        @DisplayName("should return 200 OK when cancelling without reason")
        fun `should cancel without reason`() {
            // Given: Create a request
            val requestBody = """
                {
                    "vmName": "no-reason-cancel-vm",
                    "projectId": "${UUID.randomUUID()}",
                    "size": "S",
                    "justification": "Test VM for cancellation without reason"
                }
            """.trimIndent()

            val createResponse = webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .returnResult()

            val responseJson = String(createResponse.responseBody!!)
            val requestId = responseJson.substringAfter("\"id\":\"").substringBefore("\"")

            // When: Cancel without body (projection already inserted by CreateVmRequestHandler)
            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests/$requestId/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .exchange()
                // Then: 200 OK with type field
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.type").isEqualTo("cancelled")
                .jsonPath("$.message").isEqualTo("Request cancelled successfully")
        }

        @Test
        @DisplayName("should return 404 Not Found for non-existent request")
        fun `should return 404 for non-existent request`() {
            val nonExistentId = UUID.randomUUID()

            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests/$nonExistentId/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.type").isEqualTo("not_found")
        }

        @Test
        @DisplayName("should return 400 Bad Request for invalid ID format")
        fun `should return 400 for invalid id format`() {
            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests/invalid-uuid/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.type").isEqualTo("validation")
                .jsonPath("$.errors[0].field").isEqualTo("id")
        }

        @Test
        @DisplayName("should return 403 Forbidden when cancelling another user's request")
        fun `should return 403 when cancelling others request`() {
            // Given: Create a request as tenant A
            val requestBody = """
                {
                    "vmName": "tenant-a-vm",
                    "projectId": "${UUID.randomUUID()}",
                    "size": "M",
                    "justification": "Tenant A's VM request"
                }
            """.trimIndent()

            val createResponse = webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .returnResult()

            val responseJson = String(createResponse.responseBody!!)
            val requestId = responseJson.substringAfter("\"id\":\"").substringBefore("\"")

            // When: Tenant B tries to cancel it
            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests/$requestId/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantB()}")
                .exchange()
                // Then: 403 Forbidden - authorization check rejects cross-tenant access
                .expectStatus().isForbidden
        }

        @Test
        @DisplayName("should return 401 Unauthorized without auth token")
        fun `should return 401 without auth token`() {
            val requestId = UUID.randomUUID()

            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests/$requestId/cancel")
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        @DisplayName("should persist VmRequestCancelled event to event store")
        fun `should persist cancel event to event store`() {
            // Given: Create a request
            val requestBody = """
                {
                    "vmName": "event-check-vm",
                    "projectId": "${UUID.randomUUID()}",
                    "size": "M",
                    "justification": "VM to verify event persistence"
                }
            """.trimIndent()

            val createResponse = webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .returnResult()

            val responseJson = String(createResponse.responseBody!!)
            val requestId = responseJson.substringAfter("\"id\":\"").substringBefore("\"")

            // When: Cancel (projection already inserted by CreateVmRequestHandler)
            webTestClient.mutateWith(csrf()).post()
                .uri("/api/requests/$requestId/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.tokenForTenantA()}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue("""{"reason": "Testing event persistence"}""")
                .exchange()
                .expectStatus().isOk

            // Then: VmRequestCancelled event is persisted
            TestContainers.postgres.createConnection("").use { conn ->
                conn.prepareStatement(
                    """
                    SELECT event_type, payload::text
                    FROM eaf_events.events
                    WHERE aggregate_id = ?::uuid
                    ORDER BY version
                    """.trimIndent()
                ).use { prepStmt ->
                    prepStmt.setObject(1, java.util.UUID.fromString(requestId))
                    val rs = prepStmt.executeQuery()
                    // First event: VmRequestCreated
                    assertTrue(rs.next())
                    assertEquals("VmRequestCreated", rs.getString("event_type"))

                    // Second event: VmRequestCancelled
                    assertTrue(rs.next())
                    assertEquals("VmRequestCancelled", rs.getString("event_type"))
                    val payload = rs.getString("payload")
                    assertTrue(payload.contains("Testing event persistence"))
                }
            }
        }
    }
}
