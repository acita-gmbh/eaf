package de.acci.dcm.vmrequest

import de.acci.dcm.DvmmApplication
import de.acci.dcm.TestNotificationConfiguration
import de.acci.eaf.testing.TestContainers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID
import de.acci.eaf.auth.keycloak.KeycloakJwtAuthenticationConverter
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import java.util.concurrent.TimeUnit
import org.awaitility.Awaitility.await

@SpringBootTest(
    classes = [DvmmApplication::class, VmProvisioningIntegrationTest.TestConfig::class, TestNotificationConfiguration::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.flyway.enabled=false",
        // Enable async handling in tests
    ]
)
@AutoConfigureWebTestClient(timeout = "30s")
@ActiveProfiles("test", "vcsim")
@DisplayName("VM Provisioning Integration Tests")
class VmProvisioningIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient



    companion object {
        private val tenantId = UUID.randomUUID()
        private val adminId = UUID.randomUUID()
        private val requesterId = UUID.randomUUID()

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
            // Decode the payload portion to extract the subject (user ID)
            val parts = token.split(".")
            if (parts.size < 2) {
                return@ReactiveJwtDecoder Mono.error(BadJwtException("Invalid token format"))
            }

            val payloadJson = try {
                String(java.util.Base64.getUrlDecoder().decode(parts[1]))
            } catch (e: Exception) {
                return@ReactiveJwtDecoder Mono.error(BadJwtException("Cannot decode payload", e))
            }

            // Check which user ID is in the payload
            val isAdminToken = payloadJson.contains(adminId.toString())
            val isRequesterToken = payloadJson.contains(requesterId.toString())

            when {
                isAdminToken -> Mono.just(createJwt(tenantId, adminId, token, listOf("admin")))
                isRequesterToken -> Mono.just(createJwt(tenantId, requesterId, token, listOf("user")))
                else -> Mono.error(BadJwtException("Invalid token - no matching user ID found in payload"))
            }
        }

        @Bean
        @Primary
        fun testKeycloakJwtAuthenticationConverter(): KeycloakJwtAuthenticationConverter {
            return KeycloakJwtAuthenticationConverter(clientId = "dcm-web")
        }

        private fun createJwt(tenantId: UUID, userId: UUID, tokenValue: String, roles: List<String>): Jwt {
            return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("email", "user@example.com")
                .claim("realm_access", mapOf("roles" to roles))
                .claim("resource_access", mapOf("dcm-web" to mapOf("roles" to roles)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .issuer("http://localhost:8180/realms/dcm")
                .build()
        }

        companion object {
            /**
             * Creates a valid JWT format token string.
             * Must be called AFTER @BeforeAll sets up tenantId, adminId, requesterId.
             */
            fun createTestTokenString(tenantId: UUID, userId: UUID, roles: List<String>): String {
                // JWT Header: {"alg":"RS256","typ":"JWT"}
                val header = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray())

                // JWT Payload with tenant_id claim (this is what TenantContextWebFilter reads)
                val payloadJson = """{
                    "sub":"$userId",
                    "tenant_id":"$tenantId",
                    "email":"user@example.com",
                    "realm_access":{"roles":${roles.map { "\"$it\"" }}},
                    "resource_access":{"dcm-web":{"roles":${roles.map { "\"$it\"" }}}},
                    "iat":${java.time.Instant.now().epochSecond},
                    "exp":${java.time.Instant.now().plusSeconds(3600).epochSecond},
                    "iss":"http://localhost:8180/realms/dcm"
                }""".trimIndent()

                val payload = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadJson.toByteArray())

                // Fake signature (not validated in tests since we use mock decoder)
                val signature = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("test-signature".toByteArray())

                return "$header.$payload.$signature"
            }

            // These are now functions that create tokens on-demand with current tenantId/userId
            // Note: Keycloak uses lowercase role names (admin, user), not uppercase
            fun adminToken() = createTestTokenString(
                tenantId = VmProvisioningIntegrationTest.tenantId,
                userId = VmProvisioningIntegrationTest.adminId,
                roles = listOf("admin")
            )

            fun requesterToken() = createTestTokenString(
                tenantId = VmProvisioningIntegrationTest.tenantId,
                userId = VmProvisioningIntegrationTest.requesterId,
                roles = listOf("user")
            )
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        // Tables are created by Flyway migrations in @BeforeAll
        // Just truncate data between tests
        TestContainers.postgres.createConnection("").use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { stmt ->
                stmt.execute("TRUNCATE TABLE eaf_events.events RESTART IDENTITY CASCADE")
                stmt.execute("DELETE FROM public.\"REQUEST_TIMELINE_EVENTS\"")
                stmt.execute("DELETE FROM public.\"VM_REQUESTS_PROJECTION\"")
                stmt.execute("DELETE FROM public.\"VMWARE_CONFIGURATIONS\"")
                stmt.execute("DELETE FROM public.\"PROVISIONING_PROGRESS\"")
            }
        }
    }

    @Test
    fun `should start provisioning and complete successfully when request is approved`() {
        // 1. Create VMware Config
        val configBody = """
            {
                "vcenterUrl": "https://vcsim:8989/sdk",
                "username": "admin",
                "password": "password",
                "datacenterName": "Datacenter",
                "clusterName": "Cluster",
                "datastoreName": "Datastore",
                "networkName": "VM Network",
                "templateName": "Ubuntu-Template"
            }
        """.trimIndent()

        // Diagnostic: Check what error body is returned
        val result = webTestClient.mutateWith(csrf()).put()
            .uri("/api/admin/vmware-config")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.adminToken()}")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(configBody)
            .exchange()
            .returnResult(String::class.java)

        val body = result.responseBody.blockFirst()

        assertEquals(201, result.status.value(), "Expected 201 CREATED but got ${result.status}. Body: $body")

        // 2. Create VM Request
        val requestBody = """
            {
                "vmName": "prov-test-vm",
                "projectId": "${UUID.randomUUID()}",
                "size": "S",
                "justification": "Testing provisioning flow"
            }
        """.trimIndent()

        val createResponse = webTestClient.mutateWith(csrf()).post()
            .uri("/api/requests")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.requesterToken()}")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .returnResult()

        val responseJson = String(createResponse.responseBody!!)
        // Extract ID from JSON: {"id":"uuid-here","...}
        val requestId = responseJson.substringAfter("\"id\":\"").substringBefore("\"")

        // 3. Approve VM Request (version 1 = after VmRequestCreated event)
        val approveBody = "{ \"version\": 1 }"
        webTestClient.mutateWith(csrf()).post()
            .uri("/api/admin/requests/$requestId/approve")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.adminToken()}")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(approveBody)
            .exchange()
            .expectStatus().isOk

        // 4. Verify complete event flow including successful provisioning
        await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            TestContainers.postgres.createConnection("").use { conn ->
                conn.prepareStatement(
                    "SELECT event_type FROM eaf_events.events ORDER BY version"
                ).use { stmt ->
                    val rs = stmt.executeQuery()
                    val events = mutableListOf<String>()
                    while (rs.next()) {
                        events.add(rs.getString("event_type"))
                    }

                    assertTrue(events.contains("VmRequestApproved"), "Should have VmRequestApproved. Found: $events")
                    assertTrue(events.contains("VmProvisioningStarted"), "Should have VmProvisioningStarted. Found: $events")
                    assertTrue(events.contains("VmRequestProvisioningStarted"), "Should have VmRequestProvisioningStarted. Found: $events")
                    assertTrue(events.contains("VmProvisioned"), "Should have VmProvisioned (Success). Found: $events")
                    assertTrue(events.contains("VmRequestReady"), "Should have VmRequestReady (Success). Found: $events")
                }
            }
        }
    }

    @Test
    fun `should track provisioning progress (AC-3-5)`() {
        // 1. Create VMware Config
        val configBody = """
            {
                "vcenterUrl": "https://vcsim:8989/sdk",
                "username": "admin",
                "password": "password",
                "datacenterName": "Datacenter",
                "clusterName": "Cluster",
                "datastoreName": "Datastore",
                "networkName": "VM Network",
                "templateName": "Ubuntu-Template"
            }
        """.trimIndent()

        webTestClient.mutateWith(csrf()).put()
            .uri("/api/admin/vmware-config")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.adminToken()}")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(configBody)
            .exchange()
            .expectStatus().isCreated

        // 2. Create VM Request
        val requestBody = """
            {
                "vmName": "progress-test-vm",
                "projectId": "${UUID.randomUUID()}",
                "size": "S",
                "justification": "Testing progress tracking"
            }
        """.trimIndent()

        val createResponse = webTestClient.mutateWith(csrf()).post()
            .uri("/api/requests")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.requesterToken()}")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .returnResult()

        val responseJson = String(createResponse.responseBody!!)
        val requestId = responseJson.substringAfter("\"id\":\"").substringBefore("\"")

        // 3. Approve VM Request
        val approveBody = "{ \"version\": 1 }"
        webTestClient.mutateWith(csrf()).post()
            .uri("/api/admin/requests/$requestId/approve")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.adminToken()}")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(approveBody)
            .exchange()
            .expectStatus().isOk

        // 4. Verify VmProvisioningProgressUpdated events are persisted in event store
        // Note: The PROVISIONING_PROGRESS table only stores the LATEST stage (PK = VM_REQUEST_ID),
        // so we query the event store to verify ALL intermediate events were persisted.
        await().atMost(15, TimeUnit.SECONDS).untilAsserted {
            TestContainers.postgres.createConnection("").use { conn ->
                conn.prepareStatement(
                    """
                    SELECT event_type, payload FROM eaf_events.events
                    WHERE event_type = 'VmProvisioningProgressUpdated'
                    ORDER BY version
                    """.trimIndent()
                ).use { stmt ->
                    val rs = stmt.executeQuery()
                    val progressEvents = mutableListOf<String>()
                    while (rs.next()) {
                        val payload = rs.getString("payload")
                        // Extract stage from JSON payload (e.g., {"currentStage": "CLONING",...})
                        val stageMatch = """"currentStage":\s*"(\w+)"""".toRegex().find(payload)
                        stageMatch?.groupValues?.get(1)?.let { progressEvents.add(it) }
                    }

                    // Verify intermediate progress events were persisted
                    // Expected stages: CLONING -> CONFIGURING -> POWERING_ON -> WAITING_FOR_NETWORK -> READY
                    assertTrue(
                        progressEvents.contains("CLONING"),
                        "Should have CLONING progress event in event store. Found: $progressEvents"
                    )
                    assertTrue(
                        progressEvents.contains("CONFIGURING"),
                        "Should have CONFIGURING progress event in event store. Found: $progressEvents"
                    )
                    assertTrue(
                        progressEvents.contains("POWERING_ON"),
                        "Should have POWERING_ON progress event in event store. Found: $progressEvents"
                    )
                    assertTrue(
                        progressEvents.contains("WAITING_FOR_NETWORK"),
                        "Should have WAITING_FOR_NETWORK progress event in event store. Found: $progressEvents"
                    )
                    assertTrue(
                        progressEvents.contains("READY"),
                        "Should have READY progress event in event store. Found: $progressEvents"
                    )
                }
            }
        }

        // 5. Verify progress projection is cleaned up after successful provisioning
        // The projection table is ephemeral - rows are deleted when provisioning completes
        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            TestContainers.postgres.createConnection("").use { conn ->
                conn.prepareStatement(
                    "SELECT COUNT(*) FROM public.\"PROVISIONING_PROGRESS\" WHERE \"VM_REQUEST_ID\" = ?"
                ).use { stmt ->
                    stmt.setObject(1, UUID.fromString(requestId))
                    val rs = stmt.executeQuery()
                    rs.next()
                    assertEquals(0, rs.getInt(1), "Provisioning progress should be cleaned up after completion")
                }
            }
        }

        // 6. Verify VmProvisioned event was also emitted (confirms full flow)
        TestContainers.postgres.createConnection("").use { conn ->
            conn.prepareStatement(
                "SELECT COUNT(*) FROM eaf_events.events WHERE event_type = 'VmProvisioned'"
            ).use { stmt ->
                val rs = stmt.executeQuery()
                rs.next()
                assertTrue(rs.getInt(1) > 0, "VmProvisioned event should exist after successful provisioning")
            }
        }
    }

    @Test
    fun `should emit VmProvisioningFailed when VMware config is missing (AC-7)`() {
        // AC-7: If VMware config missing, error event added: "VMware not configured"
        // Note: We intentionally do NOT create VMware config before approving

        // 1. Create VM Request (without VMware config)
        val requestBody = """
            {
                "vmName": "fail-test-vm",
                "projectId": "${UUID.randomUUID()}",
                "size": "M",
                "justification": "Testing provisioning failure when VMware not configured"
            }
        """.trimIndent()

        val createResponse = webTestClient.mutateWith(csrf()).post()
            .uri("/api/requests")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.requesterToken()}")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .returnResult()

        val responseJson = String(createResponse.responseBody!!)
        val requestId = responseJson.substringAfter("\"id\":\"").substringBefore("\"")

        // 2. Approve VM Request (version 1 = after VmRequestCreated event)
        val approveBody = "{ \"version\": 1 }"
        webTestClient.mutateWith(csrf()).post()
            .uri("/api/admin/requests/$requestId/approve")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestConfig.adminToken()}")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(approveBody)
            .exchange()
            .expectStatus().isOk

        // 3. Verify VmProvisioningFailed event is emitted (because VMware config is missing)
        await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            TestContainers.postgres.createConnection("").use { conn ->
                conn.prepareStatement(
                    "SELECT event_type, payload FROM eaf_events.events ORDER BY version"
                ).use { stmt ->
                    val rs = stmt.executeQuery()
                    val events = mutableListOf<String>()
                    var failedPayload: String? = null
                    while (rs.next()) {
                        val eventType = rs.getString("event_type")
                        events.add(eventType)
                        if (eventType == "VmProvisioningFailed") {
                            failedPayload = rs.getString("payload")
                        }
                    }

                    assertTrue(events.contains("VmRequestApproved"), "Should have VmRequestApproved. Found: $events")
                    assertTrue(events.contains("VmProvisioningStarted"), "Should have VmProvisioningStarted. Found: $events")
                    assertTrue(events.contains("VmProvisioningFailed"), "Should have VmProvisioningFailed (AC-7). Found: $events")
                    assertTrue(
                        failedPayload?.contains("VMware configuration missing") == true,
                        "VmProvisioningFailed should mention VMware config missing. Payload: $failedPayload"
                    )
                }
            }
        }
    }
}
