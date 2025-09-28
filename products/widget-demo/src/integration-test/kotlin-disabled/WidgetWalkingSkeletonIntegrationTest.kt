package com.axians.eaf.products.widgetdemo.api

import com.axians.eaf.api.widget.dto.WidgetResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.extensions.testcontainers.perSpec
import java.math.BigDecimal
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Walking Skeleton Integration Test for Epic 2 completion.
 *
 * This test validates the complete CQRS/ES flow from command to query:
 * 1. POST command → event store → projection
 * 2. Wait for projection synchronization
 * 3. GET query → read model → response
 * 4. Validate end-to-end data consistency
 *
 * Critical Epic 2 milestone validation with <500ms performance target.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [com.axians.eaf.licensing.LicensingServerApplication::class]
)
@AutoConfigureWebMvc
class WidgetWalkingSkeletonIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) : FunSpec({

    extension(SpringExtension)

    // Use shared TestContainers with Kotest lifecycle management
    listener(TestContainers.postgres.perSpec())
    listener(TestContainers.redis.perSpec())
    listener(TestContainers.keycloak.perSpec())

    test("2.4-E2E-001: Complete Walking Skeleton flow - POST command → projection → GET query") {
        // Given: Widget creation request data
        val startTime = System.currentTimeMillis()
        val testTenantId = "test-tenant-${System.currentTimeMillis()}"
        val widgetRequest = mapOf(
            "name" to "Walking Skeleton Widget",
            "description" to "Epic 2 completion test widget",
            "value" to 999.99,
            "category" to "VALIDATION",
            "metadata" to mapOf(
                "testType" to "walkingSkeleton",
                "epic" to "2",
                "story" to "2.4"
            )
        )

        // When: POST command to create widget
        val createResponse = mockMvc.perform(
            post("/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token-$testTenantId")
                .content(objectMapper.writeValueAsString(widgetRequest))
        )
            .andExpect(status().isCreated)
            .andReturn()

        val createResponseBody = objectMapper.readValue<Map<String, Any>>(
            createResponse.response.contentAsString
        )
        val widgetId = createResponseBody["id"] as String
        widgetId shouldNotBe null

        // Wait for projection to be updated (eventual consistency)
        // Allow up to 2 seconds for projection processing
        var attempts = 0
        var widgetResponse: WidgetResponse? = null
        val maxAttempts = 20 // 2 seconds with 100ms intervals

        while (attempts < maxAttempts && widgetResponse == null) {
            TimeUnit.MILLISECONDS.sleep(100)
            attempts++

            try {
                val queryResponse = mockMvc.perform(
                    get("/widgets/$widgetId")
                        .header("Authorization", "Bearer test-token-$testTenantId")
                )
                    .andReturn()

                if (queryResponse.response.status == 200) {
                    widgetResponse = objectMapper.readValue<WidgetResponse>(
                        queryResponse.response.contentAsString
                    )
                }
            } catch (e: Exception) {
                // Continue waiting for projection
            }
        }

        // Then: Validate complete Walking Skeleton flow
        widgetResponse shouldNotBe null

        // Validate data consistency between command and query
        widgetResponse!!.id shouldBe widgetId
        widgetResponse.name shouldBe "Walking Skeleton Widget"
        widgetResponse.description shouldBe "Epic 2 completion test widget"
        widgetResponse.value shouldBe BigDecimal("999.99")
        widgetResponse.category shouldBe "VALIDATION"

        // Validate metadata deserialization from PostgreSQL jsonb
        widgetResponse.metadata shouldNotBe null
        widgetResponse.metadata!!["testType"] shouldBe "walkingSkeleton"
        widgetResponse.metadata!!["epic"] shouldBe "2"
        widgetResponse.metadata!!["story"] shouldBe "2.4"

        // Validate sensitive fields are excluded from response
        val responseJson = objectMapper.writeValueAsString(widgetResponse)
        responseJson.contains("tenantId") shouldBe false
        responseJson.contains("updatedAt") shouldBe false

        // Validate performance target: <500ms end-to-end
        val endTime = System.currentTimeMillis()
        val totalDuration = Duration.ofMillis(endTime - startTime)
        totalDuration.toMillis() shouldBe { it < 500 }

        println("✅ Walking Skeleton completed in ${totalDuration.toMillis()}ms")
        println("✅ Epic 2 milestone: Complete CQRS/ES architecture validated!")
    }

    test("2.4-E2E-002: Multi-tenant Walking Skeleton isolation") {
        // Given: Two different tenants
        val tenant1 = "tenant-1-${System.currentTimeMillis()}"
        val tenant2 = "tenant-2-${System.currentTimeMillis()}"

        val widgetRequest = mapOf(
            "name" to "Tenant Isolation Test",
            "description" to "Multi-tenant validation",
            "value" to 100.00,
            "category" to "ISOLATION",
            "metadata" to mapOf("tenant" to "isolation-test")
        )

        // When: Create widget for tenant 1
        val tenant1Response = mockMvc.perform(
            post("/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token-$tenant1")
                .content(objectMapper.writeValueAsString(widgetRequest))
        )
            .andExpect(status().isCreated)
            .andReturn()

        val tenant1WidgetId = objectMapper.readValue<Map<String, Any>>(
            tenant1Response.response.contentAsString
        )["id"] as String

        // Create widget for tenant 2 with same data
        val tenant2Response = mockMvc.perform(
            post("/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token-$tenant2")
                .content(objectMapper.writeValueAsString(widgetRequest))
        )
            .andExpect(status().isCreated)
            .andReturn()

        val tenant2WidgetId = objectMapper.readValue<Map<String, Any>>(
            tenant2Response.response.contentAsString
        )["id"] as String

        // Wait for projections
        TimeUnit.SECONDS.sleep(1)

        // Then: Validate tenant isolation in queries
        // Tenant 1 can access their widget
        mockMvc.perform(
            get("/widgets/$tenant1WidgetId")
                .header("Authorization", "Bearer test-token-$tenant1")
        )
            .andExpect(status().isOk)

        // Tenant 1 cannot access tenant 2's widget
        mockMvc.perform(
            get("/widgets/$tenant2WidgetId")
                .header("Authorization", "Bearer test-token-$tenant1")
        )
            .andExpect(status().isNotFound)

        // Tenant 2 can access their widget
        mockMvc.perform(
            get("/widgets/$tenant2WidgetId")
                .header("Authorization", "Bearer test-token-$tenant2")
        )
            .andExpect(status().isOk)

        // Tenant 2 cannot access tenant 1's widget
        mockMvc.perform(
            get("/widgets/$tenant1WidgetId")
                .header("Authorization", "Bearer test-token-$tenant2")
        )
            .andExpect(status().isNotFound)

        println("✅ Multi-tenant isolation validated across complete CQRS flow")
    }

    test("2.4-INT-005: GET /widgets endpoint with pagination") {
        // Given: Multiple widgets for pagination testing
        val testTenant = "pagination-tenant-${System.currentTimeMillis()}"
        val widgetIds = mutableListOf<String>()

        repeat(5) { index ->
            val widgetRequest = mapOf(
                "name" to "Pagination Widget $index",
                "description" to "Widget for pagination test",
                "value" to (100.0 + index),
                "category" to "PAGINATION",
                "metadata" to mapOf("index" to index)
            )

            val response = mockMvc.perform(
                post("/widgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test-token-$testTenant")
                    .content(objectMapper.writeValueAsString(widgetRequest))
            )
                .andExpect(status().isCreated)
                .andReturn()

            val widgetId = objectMapper.readValue<Map<String, Any>>(
                response.response.contentAsString
            )["id"] as String
            widgetIds.add(widgetId)
        }

        // Wait for projections
        TimeUnit.SECONDS.sleep(1)

        // When: Query widgets with pagination
        val paginationResponse = mockMvc.perform(
            get("/widgets")
                .param("page", "0")
                .param("size", "3")
                .param("category", "PAGINATION")
                .header("Authorization", "Bearer test-token-$testTenant")
        )
            .andExpect(status().isOk)
            .andReturn()

        // Then: Validate pagination response structure
        val responseBody = objectMapper.readValue<Map<String, Any>>(
            paginationResponse.response.contentAsString
        )

        val content = responseBody["content"] as List<*>
        content.size shouldBe 3

        val totalElements = responseBody["totalElements"] as Int
        totalElements shouldBe 5

        val size = responseBody["size"] as Int
        size shouldBe 3

        val number = responseBody["number"] as Int
        number shouldBe 0

        println("✅ Pagination validation completed with ${content.size} items")
    }

}) {
    companion object {
        // Configure Spring properties to use shared TestContainers
        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            // Ensure containers are started
            TestContainers.startAll()

            // Configure PostgreSQL
            registry.add("spring.datasource.url") { TestContainers.postgres.jdbcUrl }
            registry.add("spring.datasource.username") { TestContainers.postgres.username }
            registry.add("spring.datasource.password") { TestContainers.postgres.password }

            // Configure Redis if needed
            registry.add("spring.redis.host") { TestContainers.redis.host }
            registry.add("spring.redis.port") { TestContainers.redis.getMappedPort(6379) }

            // Configure Keycloak if needed
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") {
                "${TestContainers.keycloak.authServerUrl}/realms/eaf"
            }
        }
    }
}