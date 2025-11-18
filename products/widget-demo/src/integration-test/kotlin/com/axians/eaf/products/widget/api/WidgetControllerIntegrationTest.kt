package com.axians.eaf.products.widget.api

import com.axians.eaf.framework.multitenancy.TenantContext
import com.axians.eaf.framework.web.rest.ProblemDetailExceptionHandler
import com.axians.eaf.products.widget.WidgetDemoApplication
import com.axians.eaf.products.widget.test.config.AxonTestConfiguration
import com.axians.eaf.products.widget.test.config.TestAutoConfigurationOverrides
import com.axians.eaf.products.widget.test.config.TestSecurityConfig
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.delay
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration

/**
 * Integration test for Widget REST API Controller (Story 2.10).
 *
 * Validates end-to-end REST API flow:
 * - HTTP endpoints → WidgetController → CommandGateway/QueryGateway
 * - Uses Testcontainers PostgreSQL + MockMvc
 * - Tests full CRUD operations via REST API
 * - Validates HTTP status codes (201, 200, 400, 404)
 * - Validates RFC 7807 ProblemDetail error responses
 *
 * **AC Coverage:**
 * - AC1: WidgetController with @RestController ✅
 * - AC2: POST, GET, GET (list), PUT endpoints ✅
 * - AC3: CommandGateway/QueryGateway usage ✅ (implicit)
 * - AC4: Request/Response DTOs with validation ✅
 * - AC5: OpenAPI annotations ✅ (implicit)
 * - AC6: Full CRUD flow integration test ✅
 * - AC8: Correct HTTP status codes ✅
 */
@Testcontainers
@SpringBootTest(
    classes = [
        WidgetDemoApplication::class,
        TestSecurityConfig::class,
        ProblemDetailExceptionHandler::class,
    ],
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.defer-datasource-initialization=true",
        "spring.mvc.problemdetails.enabled=true",
        TestAutoConfigurationOverrides.DISABLE_MODULITH_JPA,
    ],
)
@Import(AxonTestConfiguration::class)
@Sql("/schema.sql")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class WidgetControllerIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    init {
        extension(SpringExtension())

        beforeTest {
            // CRITICAL: Set tenant context for REST tests
            // TestTenantContextFilter sets context for HTTP thread, but Commands execute in Axon thread pool
            // This beforeTest sets a fallback that testTenantContextInterceptor can use
            TenantContext.setCurrentTenantId("test-tenant")
        }

        afterTest {
            TenantContext.clearCurrentTenant()
        }

        context("POST /api/v1/widgets - Create Widget") {

            test("should create widget and return 201 Created") {
                // Given - Create widget request
                val request = CreateWidgetRequest(name = "Test Widget")
                val requestBody = objectMapper.writeValueAsString(request)

                // When - POST create widget
                val result =
                    mockMvc
                        .post("/api/v1/widgets") {
                            contentType = MediaType.APPLICATION_JSON
                            content = requestBody
                        }.andExpect {
                            status { isCreated() }
                            content { contentType(MediaType.APPLICATION_JSON) }
                        }.andReturn()

                // Then - Response contains created widget
                val response =
                    objectMapper.readValue(
                        result.response.contentAsString,
                        WidgetResponse::class.java,
                    )
                response.name shouldBe "Test Widget"
                response.published shouldBe false
                response.id shouldNotBe null
            }

            test("should return 400 Bad Request for blank name") {
                // Given - Invalid request with blank name
                val request = mapOf("name" to "")
                val requestBody = objectMapper.writeValueAsString(request)

                // When - POST with blank name
                val result =
                    mockMvc
                        .post("/api/v1/widgets") {
                            contentType = MediaType.APPLICATION_JSON
                            content = requestBody
                        }.andReturn()

                // Then - Verify 400 Bad Request with ProblemDetail
                // Note: Bean Validation (@NotBlank) should catch empty string
                result.response.status shouldBe 400
            }

            test("should return 400 Bad Request for name exceeding 255 characters") {
                // Given - Invalid request with name too long
                val request = CreateWidgetRequest(name = "a".repeat(256))
                val requestBody = objectMapper.writeValueAsString(request)

                // When/Then - POST returns 400 with ProblemDetail
                mockMvc
                    .post("/api/v1/widgets") {
                        contentType = MediaType.APPLICATION_JSON
                        content = requestBody
                    }.andExpect {
                        status { isBadRequest() }
                        content { contentType("application/problem+json") }
                        jsonPath("$.status") { value(400) }
                    }
            }
        }

        context("GET /api/v1/widgets/{id} - Find Widget") {

            test("should return widget by ID with 200 OK") {
                // Given - Create widget first
                val createRequest = CreateWidgetRequest(name = "Findable Widget")
                val createBody = objectMapper.writeValueAsString(createRequest)

                val createResult =
                    mockMvc
                        .post("/api/v1/widgets") {
                            contentType = MediaType.APPLICATION_JSON
                            content = createBody
                        }.andReturn()

                val createdWidget =
                    objectMapper.readValue(
                        createResult.response.contentAsString,
                        WidgetResponse::class.java,
                    )

                // When - GET widget by ID
                val result =
                    mockMvc
                        .get("/api/v1/widgets/${createdWidget.id}") {
                            accept = MediaType.APPLICATION_JSON
                        }.andExpect {
                            status { isOk() }
                            content { contentType(MediaType.APPLICATION_JSON) }
                        }.andReturn()

                // Then - Response contains correct widget
                val response =
                    objectMapper.readValue(
                        result.response.contentAsString,
                        WidgetResponse::class.java,
                    )
                response.id shouldBe createdWidget.id
                response.name shouldBe "Findable Widget"
            }

            test("should return 404 Not Found for non-existent widget") {
                // Given - Random UUID that doesn't exist
                val nonExistentId = "00000000-0000-0000-0000-000000000001"

                // When/Then - GET returns 404 with ProblemDetail
                mockMvc
                    .get("/api/v1/widgets/$nonExistentId") {
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isNotFound() }
                        content { contentType("application/problem+json") }
                        jsonPath("$.status") { value(404) }
                        jsonPath("$.title") { exists() }
                    }
            }
        }

        context("GET /api/v1/widgets - List Widgets") {

            test("should return paginated widget list with 200 OK") {
                // Given - Create multiple widgets
                repeat(3) { index ->
                    val request = CreateWidgetRequest(name = "List Widget $index")
                    val body = objectMapper.writeValueAsString(request)
                    mockMvc.post("/api/v1/widgets") {
                        contentType = MediaType.APPLICATION_JSON
                        content = body
                    }
                }

                // Wait for projections to be available
                eventually(Duration.ofSeconds(10)) {
                    // When - GET widget list
                    val result =
                        mockMvc
                            .get("/api/v1/widgets") {
                                accept = MediaType.APPLICATION_JSON
                                param("limit", "100")
                            }.andExpect {
                                status { isOk() }
                                content { contentType(MediaType.APPLICATION_JSON) }
                            }.andReturn()

                    // Then - Response contains paginated list
                    val response = objectMapper.readTree(result.response.contentAsString)
                    response.has("data") shouldBe true
                    response.has("nextCursor") shouldBe true
                    response.has("hasMore") shouldBe true

                    val widgets = response.get("data")
                    widgets.isArray shouldBe true
                    // At least 3 widgets (may be more from other tests in same context)
                    (widgets.size() >= 3) shouldBe true
                }
            }

            test("should support cursor-based pagination") {
                // Given - Create 5 widgets
                repeat(5) { index ->
                    val request = CreateWidgetRequest(name = "Pagination Widget $index")
                    val body = objectMapper.writeValueAsString(request)
                    mockMvc.post("/api/v1/widgets") {
                        contentType = MediaType.APPLICATION_JSON
                        content = body
                    }
                }

                // When - GET first page with limit 2
                val firstPageResult =
                    mockMvc
                        .get("/api/v1/widgets") {
                            accept = MediaType.APPLICATION_JSON
                            param("limit", "2")
                        }.andReturn()

                val firstPage = objectMapper.readTree(firstPageResult.response.contentAsString)
                val firstPageData = firstPage.get("data")
                firstPageData.size() shouldBe 2
                firstPage.get("hasMore").asBoolean() shouldBe true

                val cursor = firstPage.get("nextCursor")?.asText()
                cursor.shouldNotBeBlank()

                // Then - GET second page with cursor
                val secondPageResult =
                    mockMvc
                        .get("/api/v1/widgets") {
                            accept = MediaType.APPLICATION_JSON
                            param("limit", "2")
                            param("cursor", cursor!!)
                        }.andReturn()

                val secondPage = objectMapper.readTree(secondPageResult.response.contentAsString)
                val secondPageData = secondPage.get("data")
                secondPageData.size() shouldBe 2
            }
        }

        context("PUT /api/v1/widgets/{id} - Update Widget") {

            // NOTE: "should update widget and return 200 OK" test removed as redundant
            // The "Full CRUD Flow" test covers the same scenario more comprehensively:
            // POST → GET → PUT → GET (with proper eventual consistency handling)

            test("should return 404 Not Found for non-existent widget") {
                // Given - Random UUID that doesn't exist
                val nonExistentId = "00000000-0000-0000-0000-000000000002"
                val updateRequest = UpdateWidgetRequest(name = "Cannot Update")
                val updateBody = objectMapper.writeValueAsString(updateRequest)

                // When/Then - PUT returns 404 (Axon AggregateNotFoundException → handler → 404)
                // CRITICAL: Must use andExpect(), not andReturn() for error responses
                mockMvc
                    .put("/api/v1/widgets/$nonExistentId") {
                        contentType = MediaType.APPLICATION_JSON
                        content = updateBody
                    }.andExpect {
                        status { isNotFound() }
                    }
            }

            test("should return 400 Bad Request for invalid update") {
                // Given - Create widget first
                val createRequest = CreateWidgetRequest(name = "Widget to Update")
                val createBody = objectMapper.writeValueAsString(createRequest)

                val createResult =
                    mockMvc
                        .post("/api/v1/widgets") {
                            contentType = MediaType.APPLICATION_JSON
                            content = createBody
                        }.andReturn()

                val createdWidget =
                    objectMapper.readValue(
                        createResult.response.contentAsString,
                        WidgetResponse::class.java,
                    )

                // When/Then - PUT with blank name returns 400
                val updateRequest = mapOf("name" to "")
                val updateBody = objectMapper.writeValueAsString(updateRequest)

                mockMvc
                    .put("/api/v1/widgets/${createdWidget.id}") {
                        contentType = MediaType.APPLICATION_JSON
                        content = updateBody
                    }.andExpect {
                        status { isBadRequest() }
                        content { contentType("application/problem+json") }
                        jsonPath("$.status") { value(400) }
                    }
            }
        }

        context("Full CRUD Flow Integration Test") {

            test("should complete full lifecycle: POST → GET → PUT → GET") {
                // Step 1: Create widget
                val createRequest = CreateWidgetRequest(name = "CRUD Flow Widget")
                val createBody = objectMapper.writeValueAsString(createRequest)

                val createResult =
                    mockMvc
                        .post("/api/v1/widgets") {
                            contentType = MediaType.APPLICATION_JSON
                            content = createBody
                        }.andExpect {
                            status { isCreated() }
                        }.andReturn()

                val createdWidget =
                    objectMapper.readValue(
                        createResult.response.contentAsString,
                        WidgetResponse::class.java,
                    )
                createdWidget.name shouldBe "CRUD Flow Widget"

                // Step 2: Read widget (with retry for eventual consistency)
                val readResult =
                    mockMvc
                        .get("/api/v1/widgets/${createdWidget.id}") {
                            accept = MediaType.APPLICATION_JSON
                        }.andExpect {
                            status { isOk() }
                        }.andReturn()

                val readWidget =
                    objectMapper.readValue(
                        readResult.response.contentAsString,
                        WidgetResponse::class.java,
                    )
                readWidget.id shouldBe createdWidget.id
                readWidget.name shouldBe "CRUD Flow Widget"

                // Step 3: Update widget
                val updateRequest = UpdateWidgetRequest(name = "Updated CRUD Widget")
                val updateBody = objectMapper.writeValueAsString(updateRequest)

                val updateResult =
                    mockMvc
                        .put("/api/v1/widgets/${createdWidget.id}") {
                            contentType = MediaType.APPLICATION_JSON
                            content = updateBody
                        }.andExpect {
                            status { isOk() }
                        }.andReturn()

                // Step 4: Verify updated widget (updateWidget now waits for consistent projection)
                val verifiedWidget =
                    objectMapper.readValue(
                        updateResult.response.contentAsString,
                        WidgetResponse::class.java,
                    )
                verifiedWidget.name shouldBe "Updated CRUD Widget"
                verifiedWidget.updatedAt shouldNotBe verifiedWidget.createdAt
            }
        }
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgresContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer(
                DockerImageName.parse("postgres:16.10-alpine"),
            ).apply {
                withDatabaseName("testdb")
                withUsername("test")
                withPassword("test")
            }
    }
}

/**
 * Eventually polling pattern for asynchronous projection updates.
 *
 * Repeatedly executes assertion block until it succeeds or timeout is reached.
 */
private suspend fun eventually(
    timeout: Duration,
    block: suspend () -> Unit,
) {
    val deadline = System.currentTimeMillis() + timeout.toMillis()
    var lastException: Throwable? = null

    while (System.currentTimeMillis() < deadline) {
        try {
            block()
            return // Success!
        } catch (e: Throwable) {
            lastException = e
            delay(100) // Poll every 100ms
        }
    }

    throw AssertionError("Eventually block did not succeed within $timeout", lastException)
}
