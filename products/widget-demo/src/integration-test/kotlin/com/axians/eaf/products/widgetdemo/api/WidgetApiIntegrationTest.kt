@file:Suppress("DEPRECATION")

package com.axians.eaf.products.widgetdemo.api

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.products.widgetdemo.WidgetDemoApplication
import com.axians.eaf.testing.auth.KeycloakTestTokenProvider
import com.axians.eaf.testing.containers.TestContainers
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.springboot.autoconfig.AxonAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest(classes = [WidgetApiIntegrationTest.MinimalTestConfig::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "otel.java.global-autoconfigure.enabled=false",
        "otel.sdk.disabled=true",
        "otel.traces.exporter=none",
        "otel.metrics.exporter=none",
        "otel.logs.exporter=none",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "otel.instrumentation.spring-boot-starter.enabled=false",
        "otel.instrumentation.common.enabled=false",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
class WidgetApiIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var commandGateway: CommandGateway

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @TestConfiguration
    @EnableAutoConfiguration(
        exclude = [
            org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration::class,
            org.springframework.boot.actuate.autoconfigure.tracing.BraveAutoConfiguration::class,
            org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryAutoConfiguration::class,
        ],
    )
    @Import(
        org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration::class,
        org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration::class,
        org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration::class,
        org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration::class,
        org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration::class,
        org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration::class,
        org.axonframework.springboot.autoconfig.AxonAutoConfiguration::class,
        com.axians.eaf.products.widgetdemo.domain.Widget::class, // Import the aggregate
    )
    class MinimalTestConfig

    init {
        extension(SpringExtension())

        context("Widget API Integration Tests") {
            test("8.4-INT-006: should create widget successfully via REST API with JWT authentication") {
                val validToken = KeycloakTestTokenProvider.getAdminToken()
                val request =
                    mapOf(
                        "name" to "Integration Test Widget",
                        "description" to "Created via REST API integration test",
                        "value" to 150.00,
                        "category" to "INTEGRATION_TEST",
                        "metadata" to mapOf("test" to "api", "version" to 1),
                    )

                val result =
                    mockMvc
                        .perform(
                            post("/widgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", "Bearer $validToken")
                                .content(objectMapper.writeValueAsString(request)),
                        ).andExpect(status().isCreated)
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

            test("8.4-INT-007: should handle validation errors with RFC 7807 Problem Details") {
                val validToken = KeycloakTestTokenProvider.getAdminToken()
                val invalidRequest =
                    mapOf(
                        "name" to "", // Invalid empty name
                        "description" to null,
                        "value" to -100.00, // Invalid negative value
                        "category" to "invalid-category", // Invalid category format
                        "metadata" to emptyMap<String, Any>(),
                    )

                mockMvc
                    .perform(
                        post("/widgets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer $validToken")
                            .content(objectMapper.writeValueAsString(invalidRequest)),
                    ).andExpect(status().isBadRequest)
                    .andExpect(content().contentType("application/problem+json"))
                    .andExpect(jsonPath("$.title").value("Widget Creation Failed"))
                    .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                    .andExpect(jsonPath("$.detail").exists())
            }

            test("8.4-INT-008: should return 401 for missing authorization header") {
                val request =
                    mapOf(
                        "name" to "Test Widget",
                        "description" to "Missing auth test",
                        "value" to 100.00,
                        "category" to "TEST_CATEGORY",
                    )

                mockMvc
                    .perform(
                        post("/widgets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)),
                    ).andExpect(status().isUnauthorized)
            }

            test("8.4-INT-009: should extract tenant context from JWT header") {
                val validToken = KeycloakTestTokenProvider.getAdminToken()
                val request =
                    mapOf(
                        "name" to "Tenant Test Widget",
                        "description" to "Testing tenant extraction",
                        "value" to 75.50,
                        "category" to "TENANT_TEST",
                    )

                mockMvc
                    .perform(
                        post("/widgets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer $validToken")
                            .content(objectMapper.writeValueAsString(request)),
                    ).andExpect(status().isCreated)
                    .andExpect(jsonPath("$.id").exists())
            }

            test("8.4-INT-010: should return 401 for invalid JWT token") {
                val request =
                    mapOf(
                        "name" to "Test Widget",
                        "description" to "Invalid JWT test",
                        "value" to 100.00,
                        "category" to "TEST_CATEGORY",
                    )

                mockMvc
                    .perform(
                        post("/widgets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer invalid-jwt-token")
                            .content(objectMapper.writeValueAsString(request)),
                    ).andExpect(status().isUnauthorized)
            }

            test("8.4-INT-011: should retrieve widget with valid JWT authentication") {
                val validToken = KeycloakTestTokenProvider.getAdminToken()

                // First create a widget
                val createRequest =
                    mapOf(
                        "name" to "Retrieval Test Widget",
                        "description" to "Widget for GET endpoint test",
                        "value" to 123.45,
                        "category" to "RETRIEVAL_TEST",
                    )

                val createResult =
                    mockMvc
                        .perform(
                            post("/widgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", "Bearer $validToken")
                                .content(objectMapper.writeValueAsString(createRequest)),
                        ).andExpect(status().isCreated)
                        .andReturn()

                val createResponse = objectMapper.readValue(createResult.response.contentAsString, Map::class.java)
                val widgetId = createResponse["id"] as String

                // Then retrieve the widget
                mockMvc
                    .perform(
                        get("/widgets/$widgetId")
                            .header("Authorization", "Bearer $validToken"),
                    ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.id").value(widgetId))
                    .andExpect(jsonPath("$.name").value("Retrieval Test Widget"))
            }

            test("8.4-INT-012: should return 401 when retrieving widget without authentication") {
                val widgetId = UUID.randomUUID().toString()

                mockMvc
                    .perform(
                        get("/widgets/$widgetId"),
                    ).andExpect(status().isUnauthorized)
            }
        }

        context("Command Gateway Integration") {
            test("8.4-INT-013: should dispatch CreateWidgetCommand through CommandGateway") {
                val widgetId = UUID.randomUUID().toString()
                val command =
                    CreateWidgetCommand(
                        widgetId = widgetId,
                        tenantId = "550e8400-e29b-41d4-a716-446655440005",
                        name = "Gateway Test Widget",
                        description = "Testing direct CommandGateway",
                        value = BigDecimal("200.00"),
                        category = "GATEWAY_TEST",
                        metadata = mapOf("source" to "direct"),
                    )

                val result = commandGateway.sendAndWait<String>(command, 5, java.util.concurrent.TimeUnit.SECONDS)
                result shouldBe widgetId
            }

            test("8.4-INT-014: should handle command validation errors through gateway") {
                val command =
                    CreateWidgetCommand(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = "550e8400-e29b-41d4-a716-446655440006",
                        name = "", // Invalid empty name
                        description = null,
                        value = BigDecimal("-50"), // Invalid negative value
                        category = "invalid", // Invalid category
                        metadata = emptyMap(),
                    )

                try {
                    commandGateway.sendAndWait<String>(command, 5, java.util.concurrent.TimeUnit.SECONDS)
                } catch (e: Exception) {
                    e.message shouldContain "Validation failed"
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            TestContainers.postgres.start()
            TestContainers.redis.start()
            TestContainers.keycloak.start()

            registry.add("spring.datasource.url", TestContainers.postgres::getJdbcUrl)
            registry.add("spring.datasource.username", TestContainers.postgres::getUsername)
            registry.add("spring.datasource.password", TestContainers.postgres::getPassword)
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") {
                "${TestContainers.keycloak.authServerUrl}/realms/eaf"
            }
        }
    }
}
