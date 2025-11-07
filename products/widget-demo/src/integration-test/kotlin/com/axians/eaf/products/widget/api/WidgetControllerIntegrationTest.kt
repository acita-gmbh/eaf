package com.axians.eaf.products.widget.api

import com.axians.eaf.products.widget.WidgetDemoApplication
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
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
    classes = [WidgetDemoApplication::class],
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.defer-datasource-initialization=true",
        "spring.mvc.problemdetails.enabled=true",
    ],
)
@Sql("/schema.sql")
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class WidgetControllerIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    init {
        extension(SpringExtension())

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

                // When/Then - POST returns 400 with ProblemDetail
                mockMvc
                    .post("/api/v1/widgets") {
                        contentType = MediaType.APPLICATION_JSON
                        content = requestBody
                    }.andExpect {
                        status { isBadRequest() }
                        content { contentType("application/problem+json") }
                        jsonPath("$.type") { exists() }
                        jsonPath("$.title") { exists() }
                        jsonPath("$.status") { value(400) }
                    }
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

                // When - GET widget list
                val result =
                    mockMvc
                        .get("/api/v1/widgets") {
                            accept = MediaType.APPLICATION_JSON
                            param("limit", "10")
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
                widgets.size() shouldBe 3
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

            test("should update widget and return 200 OK") {
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

                // When - PUT update widget
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

                // Then - Response contains updated widget
                val response =
                    objectMapper.readValue(
                        updateResult.response.contentAsString,
                        WidgetResponse::class.java,
                    )
                response.id shouldBe createdWidget.id
                response.name shouldBe "Updated Name"
            }

            test("should return 404 Not Found for non-existent widget") {
                // Given - Random UUID that doesn't exist
                val nonExistentId = "00000000-0000-0000-0000-000000000002"
                val updateRequest = UpdateWidgetRequest(name = "Cannot Update")
                val updateBody = objectMapper.writeValueAsString(updateRequest)

                // When/Then - PUT returns 404 with ProblemDetail
                mockMvc
                    .put("/api/v1/widgets/$nonExistentId") {
                        contentType = MediaType.APPLICATION_JSON
                        content = updateBody
                    }.andExpect {
                        status { isNotFound() }
                        content { contentType("application/problem+json") }
                        jsonPath("$.status") { value(404) }
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

                // Step 2: Read widget
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

                val updatedWidget =
                    objectMapper.readValue(
                        updateResult.response.contentAsString,
                        WidgetResponse::class.java,
                    )
                updatedWidget.name shouldBe "Updated CRUD Widget"

                // Step 4: Read updated widget
                val verifyResult =
                    mockMvc
                        .get("/api/v1/widgets/${createdWidget.id}") {
                            accept = MediaType.APPLICATION_JSON
                        }.andExpect {
                            status { isOk() }
                        }.andReturn()

                val verifiedWidget =
                    objectMapper.readValue(
                        verifyResult.response.contentAsString,
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
