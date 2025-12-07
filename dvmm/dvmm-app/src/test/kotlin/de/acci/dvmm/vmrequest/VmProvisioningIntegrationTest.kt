package de.acci.dvmm.vmrequest

import de.acci.dvmm.DvmmApplication
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
    classes = [DvmmApplication::class, VmProvisioningIntegrationTest.TestConfig::class],
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
            TestContainers.ensureEventStoreSchema {
                VmProvisioningIntegrationTest::class.java
                    .getResource("/db/migration/V001__create_event_store.sql")!!
                    .readText()
            }
            createConfigTable()
            createProjectionTables()
        }

        private fun createProjectionTables() {
            TestContainers.postgres.createConnection("").use { conn ->
                conn.createStatement().use { stmt ->
                    // VM Requests Projection (full schema matching jOOQ code)
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS public."VM_REQUESTS_PROJECTION" (
                            "ID" UUID PRIMARY KEY,
                            "TENANT_ID" UUID NOT NULL,
                            "REQUESTER_ID" UUID NOT NULL,
                            "REQUESTER_NAME" VARCHAR(255) NOT NULL,
                            "REQUESTER_EMAIL" VARCHAR(255),
                            "REQUESTER_ROLE" VARCHAR(100),
                            "PROJECT_ID" UUID NOT NULL,
                            "PROJECT_NAME" VARCHAR(255) NOT NULL,
                            "VM_NAME" VARCHAR(255) NOT NULL,
                            "SIZE" VARCHAR(10) NOT NULL,
                            "CPU_CORES" INT NOT NULL,
                            "MEMORY_GB" INT NOT NULL,
                            "DISK_GB" INT NOT NULL,
                            "JUSTIFICATION" TEXT NOT NULL,
                            "STATUS" VARCHAR(50) NOT NULL,
                            "APPROVED_BY" UUID,
                            "APPROVED_BY_NAME" VARCHAR(255),
                            "REJECTED_BY" UUID,
                            "REJECTED_BY_NAME" VARCHAR(255),
                            "REJECTION_REASON" TEXT,
                            "CREATED_AT" TIMESTAMPTZ NOT NULL,
                            "UPDATED_AT" TIMESTAMPTZ NOT NULL,
                            "VERSION" INT NOT NULL DEFAULT 1
                        )
                    """.trimIndent())

                    // Request Timeline Events (references VM_REQUESTS_PROJECTION)
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS public."REQUEST_TIMELINE_EVENTS" (
                            "ID" UUID PRIMARY KEY,
                            "REQUEST_ID" UUID NOT NULL REFERENCES public."VM_REQUESTS_PROJECTION"("ID") ON DELETE CASCADE,
                            "TENANT_ID" UUID NOT NULL,
                            "EVENT_TYPE" VARCHAR(50) NOT NULL,
                            "ACTOR_ID" UUID,
                            "ACTOR_NAME" VARCHAR(255),
                            "DETAILS" VARCHAR(4000),
                            "OCCURRED_AT" TIMESTAMPTZ NOT NULL
                        )
                    """.trimIndent())
                }
            }
        }

        private fun createConfigTable() {
            TestContainers.postgres.createConnection("").use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS public."VMWARE_CONFIGURATIONS" (
                            "ID" UUID PRIMARY KEY,
                            "TENANT_ID" UUID NOT NULL UNIQUE,
                            "VCENTER_URL" VARCHAR(255) NOT NULL,
                            "USERNAME" VARCHAR(255) NOT NULL,
                            "PASSWORD_ENCRYPTED" BYTEA NOT NULL,
                            "DATACENTER_NAME" VARCHAR(255) NOT NULL,
                            "CLUSTER_NAME" VARCHAR(255) NOT NULL,
                            "DATASTORE_NAME" VARCHAR(255) NOT NULL,
                            "NETWORK_NAME" VARCHAR(255) NOT NULL,
                            "TEMPLATE_NAME" VARCHAR(255) NOT NULL,
                            "FOLDER_PATH" VARCHAR(255),
                            "VERIFIED_AT" TIMESTAMPTZ,
                            "CREATED_AT" TIMESTAMPTZ NOT NULL,
                            "UPDATED_AT" TIMESTAMPTZ NOT NULL,
                            "CREATED_BY" UUID NOT NULL,
                            "UPDATED_BY" UUID NOT NULL,
                            "VERSION" BIGINT NOT NULL
                        )
                    """.trimIndent())
                }
            }
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
            return KeycloakJwtAuthenticationConverter(clientId = "dvmm-web")
        }

        private fun createJwt(tenantId: UUID, userId: UUID, tokenValue: String, roles: List<String>): Jwt {
            return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("email", "user@example.com")
                .claim("realm_access", mapOf("roles" to roles))
                .claim("resource_access", mapOf("dvmm-web" to mapOf("roles" to roles)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .issuer("http://localhost:8180/realms/dvmm")
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
                    "resource_access":{"dvmm-web":{"roles":${roles.map { "\"$it\"" }}}},
                    "iat":${java.time.Instant.now().epochSecond},
                    "exp":${java.time.Instant.now().plusSeconds(3600).epochSecond},
                    "iss":"http://localhost:8180/realms/dvmm"
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
        // Ensure tables exist
        createConfigTable()
        createProjectionTables()

        TestContainers.postgres.createConnection("").use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { stmt ->
                stmt.execute("TRUNCATE TABLE eaf_events.events RESTART IDENTITY CASCADE")
                stmt.execute("DELETE FROM public.\"REQUEST_TIMELINE_EVENTS\"")
                stmt.execute("DELETE FROM public.\"VM_REQUESTS_PROJECTION\"")
                stmt.execute("DELETE FROM public.\"VMWARE_CONFIGURATIONS\"")
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
