package com.axians.eaf.products.widgetdemo.api

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.testing.auth.KeycloakTestTokenProvider
import com.axians.eaf.testing.containers.TestContainers
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.extensions.testcontainers.perSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.axonframework.commandhandling.gateway.CommandGateway
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
class WidgetApiIntegrationTest : FunSpec() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var commandGateway: CommandGateway

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    init {
        extension(SpringExtension())

        // TestContainers managed via companion object @DynamicPropertySource

        context("Widget API Integration Tests") {
            test("should create widget successfully via REST API with JWT authentication") {
                val validToken = KeycloakTestTokenProvider.getAdminToken()
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
                        .header("Authorization", "Bearer $validToken")
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
                UUID.fromString(widgetId) shouldNotBe null
            }

            test("should handle validation errors with RFC 7807 Problem Details") {
                val validToken = KeycloakTestTokenProvider.getAdminToken()
                val invalidRequest = mapOf(
                    "name" to "",
                    "description" to null,
                    "value" to -100.00,
                    "category" to "invalid-category",
                    "metadata" to emptyMap<String, Any>()
                )

                mockMvc.perform(
                    post("/widgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer $validToken")
                        .content(objectMapper.writeValueAsString(invalidRequest))
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(content().contentType("application/problem+json"))
                    .andExpect(jsonPath("$.title").value("Widget Creation Failed"))
                    .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                    .andExpect(jsonPath("$.detail").exists())
            }

            test("should return 401 for missing authorization header") {
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
                    .andExpect(status().isUnauthorized)
            }

            test("should retrieve widget with valid JWT authentication") {
                val validToken = KeycloakTestTokenProvider.getAdminToken()

                val createRequest = mapOf(
                    "name" to "Retrieval Test Widget",
                    "description" to "Widget for GET endpoint test",
                    "value" to 123.45,
                    "category" to "RETRIEVAL_TEST"
                )

                val createResult = mockMvc.perform(
                    post("/widgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer $validToken")
                        .content(objectMapper.writeValueAsString(createRequest))
                )
                    .andExpect(status().isCreated)
                    .andReturn()

                val createResponse = objectMapper.readValue(createResult.response.contentAsString, Map::class.java)
                val widgetId = createResponse["id"] as String

                mockMvc.perform(
                    get("/widgets/$widgetId")
                        .header("Authorization", "Bearer $validToken")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.id").value(widgetId))
                    .andExpect(jsonPath("$.name").value("Retrieval Test Widget"))
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
        }
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            TestContainers.startAll()

            registry.add("spring.datasource.url") { TestContainers.postgres.jdbcUrl }
            registry.add("spring.datasource.username") { TestContainers.postgres.username }
            registry.add("spring.datasource.password") { TestContainers.postgres.password }
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") {
                "${TestContainers.keycloak.authServerUrl}/realms/eaf-test"
            }
        }
    }
}