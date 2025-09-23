package com.axians.eaf.framework.widget.api

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.axonframework.commandhandling.gateway.CommandGateway
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.extensions.spring.SpringExtension
import io.kotest.extensions.testcontainers.perSpec
import java.math.BigDecimal
import java.sql.DriverManager
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
class WidgetApiIntegrationTest(
    private val mockMvc: MockMvc,
    private val commandGateway: CommandGateway,
    private val objectMapper: ObjectMapper,
) : FunSpec({

    extension(SpringExtension)

    // Use shared TestContainers with Kotest lifecycle management
    listener(TestContainers.postgres.perSpec())
    listener(TestContainers.redis.perSpec())

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            // Ensure containers are started
            TestContainers.startAll()

            registry.add("spring.datasource.url") { TestContainers.postgres.jdbcUrl }
            registry.add("spring.datasource.username") { TestContainers.postgres.username }
            registry.add("spring.datasource.password") { TestContainers.postgres.password }
        }
    }

    context("Widget API Integration Tests") {
        test("should create widget successfully via REST API") {
            val request = mapOf(
                "name" to "Integration Test Widget",
                "description" to "Created via REST API integration test",
                "value" to 150.00,
                "category" to "INTEGRATION_TEST",
                "metadata" to mapOf("test" to "api", "version" to 1)
            )

            val result = mockMvc.perform(
                post("/widgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test-token")
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("created"))
                .andReturn()

            val responseBody = result.response.contentAsString
            val response = objectMapper.readValue(responseBody, Map::class.java)
            val widgetId = response["id"] as String

            widgetId shouldNotBe null
            UUID.fromString(widgetId) shouldNotBe null // Validate UUID format
        }

        test("should handle validation errors with RFC 7807 Problem Details") {
            val invalidRequest = mapOf(
                "name" to "",  // Invalid empty name
                "description" to null,
                "value" to -100.00,  // Invalid negative value
                "category" to "invalid-category",  // Invalid category format
                "metadata" to emptyMap<String, Any>()
            )

            mockMvc.perform(
                post("/widgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test-token")
                    .content(objectMapper.writeValueAsString(invalidRequest))
            )
                .andExpect(status().isBadRequest)
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Widget Creation Failed"))
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.detail").exists())
        }

        test("should handle missing authorization header") {
            val request = mapOf(
                "name" to "Test Widget",
                "description" to "Missing auth test",
                "value" to 100.00,
                "category" to "TEST_CATEGORY"
            )

            mockMvc.perform(
                post("/widgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        test("should extract tenant context from JWT header") {
            val request = mapOf(
                "name" to "Tenant Test Widget",
                "description" to "Testing tenant extraction",
                "value" to 75.50,
                "category" to "TENANT_TEST"
            )

            mockMvc.perform(
                post("/widgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer tenant-specific-token")
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").exists())
        }
    }

    context("Command Gateway Integration") {
        test("should dispatch CreateWidgetCommand through CommandGateway") {
            val widgetId = UUID.randomUUID().toString()
            val command = CreateWidgetCommand(
                widgetId = widgetId,
                tenantId = "gateway-test",
                name = "Gateway Test Widget",
                description = "Testing direct CommandGateway",
                value = BigDecimal("200.00"),
                category = "GATEWAY_TEST",
                metadata = mapOf("source" to "direct")
            )

            val result = commandGateway.sendAndWait<String>(command, 5, java.util.concurrent.TimeUnit.SECONDS)
            result shouldBe widgetId
        }

        test("should handle command validation errors through gateway") {
            val command = CreateWidgetCommand(
                widgetId = UUID.randomUUID().toString(),
                tenantId = "validation-test",
                name = "",  // Invalid empty name
                description = null,
                value = BigDecimal("-50"),  // Invalid negative value
                category = "invalid",  // Invalid category
                metadata = emptyMap()
            )

            try {
                commandGateway.sendAndWait<String>(command, 5, java.util.concurrent.TimeUnit.SECONDS)
            } catch (e: Exception) {
                e.message shouldContain "Validation failed"
            }
        }
    }
})