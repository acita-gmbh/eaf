package com.axians.eaf.products.widget.api

import com.axians.eaf.framework.multitenancy.TenantContext
import com.axians.eaf.framework.web.rest.ProblemDetailExceptionHandler
import com.axians.eaf.products.widget.WidgetDemoApplication
import com.axians.eaf.products.widget.test.config.AxonTestConfiguration
import com.axians.eaf.products.widget.test.config.TestAutoConfigurationOverrides
import com.axians.eaf.products.widget.test.config.TestSecurityConfig
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
class WidgetControllerIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun beforeEach() {
        // Story 4.6: Set tenant context (TestSecurityConfig bypasses TenantContextFilter)
        TenantContext.setCurrentTenantId(TEST_TENANT_ID)
    }

    @AfterEach
    fun afterEach() {
        // Story 4.6: Clean up tenant context
        TenantContext.clearCurrentTenant()
    }

    @Nested
    inner class `POST api v1 widgets - Create Widget` {
        @Test
        fun `should create widget and return 201 Created`() {
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
            assertThat(response.name).isEqualTo("Test Widget")
            assertThat(response.published).isFalse()
            assertThat(response.id).isNotNull()
        }

        @Test
        fun `should return 400 Bad Request for blank name`() {
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
            assertThat(result.response.status).isEqualTo(400)
        }

        @Test
        fun `should return 400 Bad Request for name exceeding 255 characters`() {
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

    @Nested
    inner class `GET api v1 widgets id - Find Widget` {
        @Test
        fun `should return widget by ID with 200 OK`() {
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
            assertThat(response.id).isEqualTo(createdWidget.id)
            assertThat(response.name).isEqualTo("Findable Widget")
        }

        @Test
        fun `should return 404 Not Found for non-existent widget`() {
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

    @Nested
    inner class `GET api v1 widgets - List Widgets` {
        @Test
        fun `should return paginated widget list with 200 OK`() =
            runBlocking {
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
                    assertThat(response.has("data")).isTrue()
                    assertThat(response.has("nextCursor")).isTrue()
                    assertThat(response.has("hasMore")).isTrue()

                    val widgets = response.get("data")
                    assertThat(widgets.isArray).isTrue()
                    // At least 3 widgets (may be more from other tests in same context)
                    assertThat(widgets.size() >= 3).isTrue()
                }
            }

        @Test
        fun `should support cursor-based pagination`() {
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
            assertThat(firstPageData.size()).isEqualTo(2)
            assertThat(firstPage.get("hasMore").asBoolean()).isTrue()

            val cursor = firstPage.get("nextCursor")?.asText()
            assertThat(cursor).isNotBlank()

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
            assertThat(secondPageData.size()).isEqualTo(2)
        }
    }

    @Nested
    inner class `PUT api v1 widgets id - Update Widget` {
        @Test
        fun `should update widget and return 200 OK`() {
            // Given - Create widget first
            val createRequest = CreateWidgetRequest(name = "Original Name")
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

            // When - PUT update widget (controller handles retry internally)
            val updateRequest = UpdateWidgetRequest(name = "Updated Name")
            val updateBody = objectMapper.writeValueAsString(updateRequest)

            val updateResult =
                mockMvc
                    .put("/api/v1/widgets/${createdWidget.id}") {
                        contentType = MediaType.APPLICATION_JSON
                        content = updateBody
                    }.andExpect {
                        status { isOk() }
                        content { contentType(MediaType.APPLICATION_JSON) }
                    }.andReturn()

            // Then - Response contains updated widget (not stale data)
            val response =
                objectMapper.readValue(
                    updateResult.response.contentAsString,
                    WidgetResponse::class.java,
                )
            assertThat(response.id).isEqualTo(createdWidget.id)
            assertThat(response.name).isEqualTo("Updated Name")
        }

        @Test
        fun `should return 404 Not Found for non-existent widget`() {
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

        @Test
        fun `should return 400 Bad Request for invalid update`() {
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

    @Nested
    inner class `Full CRUD Flow Integration Test` {
        @Test
        fun `should complete full lifecycle - POST to GET to PUT to GET`() {
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
            assertThat(createdWidget.name).isEqualTo("CRUD Flow Widget")

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
            assertThat(readWidget.id).isEqualTo(createdWidget.id)
            assertThat(readWidget.name).isEqualTo("CRUD Flow Widget")

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
            assertThat(verifiedWidget.name).isEqualTo("Updated CRUD Widget")
            assertThat(verifiedWidget.updatedAt).isNotEqualTo(verifiedWidget.createdAt)
        }
    }

    companion object {
        private const val TEST_TENANT_ID = "test-tenant-controller"

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
