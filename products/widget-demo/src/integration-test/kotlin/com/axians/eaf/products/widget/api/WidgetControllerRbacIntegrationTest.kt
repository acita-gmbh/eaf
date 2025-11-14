package com.axians.eaf.products.widget.api

import com.axians.eaf.framework.web.rest.ProblemDetailExceptionHandler
import com.axians.eaf.products.widget.WidgetDemoApplication
import com.axians.eaf.products.widget.test.config.RbacTestContainersConfig
import com.axians.eaf.products.widget.test.config.RbacTestSecurityConfig
import com.axians.eaf.products.widget.test.config.TestAutoConfigurationOverrides
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/**
 * RBAC Integration Test for WidgetController (Story 3.10).
 *
 * Tests @PreAuthorize role-based access control with Spring Security method security.
 *
 * **SOLUTION (from 5 AI agents + Zen MCP consensus):**
 * - Container managed as Spring @Bean in RbacTestContainersConfig (NOT static field!)
 * - @ServiceConnection on bean → Spring Boot auto-configures DataSource
 * - NO manual DataSource bean for "rbac-test" (eliminates @ServiceConnection conflict)
 * - TestDslConfiguration only for "test" profile (zero regression)
 *
 * **Why This Works:**
 * 1. RbacTestContainersConfig.postgresContainer() registered as bean
 * 2. @ServiceConnection starts container → creates JdbcConnectionDetails
 * 3. DataSourceAutoConfiguration consumes ConnectionDetails → correct URL
 * 4. @EnableMethodSecurity early bean request → DataSource already available! ✅
 * 5. No race condition, no localhost:5432 fallback
 *
 * **AC Coverage:**
 * - AC1: @PreAuthorize annotations on WidgetController ✅
 * - AC2: WIDGET_ADMIN can create/update widgets ✅
 * - AC3: WIDGET_VIEWER can read widgets ✅
 * - AC4: WIDGET_VIEWER cannot create/update (403) ✅
 * - AC5: Unauthenticated requests return 401 ✅
 * - AC6: 403 responses follow RFC 7807 ProblemDetail format ✅
 */
@SpringBootTest(
    classes = [
        WidgetDemoApplication::class,
        RbacTestSecurityConfig::class,
        ProblemDetailExceptionHandler::class,
        RbacTestContainersConfig::class, // Container + DataSource auto-configuration!
        // Exception handler for @PreAuthorize (MVC-Stack, not Filter-Stack!)
        com.axians.eaf.products.widget.test.config.RbacTestAccessDeniedAdvice::class,
    ],
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.defer-datasource-initialization=true",
        "spring.mvc.problemdetails.enabled=true",
        TestAutoConfigurationOverrides.DISABLE_MODULITH_JPA,
    ],
)
@Sql("/schema.sql")
@ActiveProfiles("rbac-test")
@AutoConfigureMockMvc
class WidgetControllerRbacIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    init {
        extension(SpringExtension())

        context("POST /api/v1/widgets - Create Widget with @PreAuthorize('hasRole(WIDGET_ADMIN)')") {

            test("WIDGET_ADMIN can create widget - returns 201 Created") {
                // Given - Create widget request with ADMIN role
                val request = CreateWidgetRequest(name = "Admin Widget")
                val requestBody = objectMapper.writeValueAsString(request)

                // When - POST create widget with ADMIN JWT
                val result =
                    mockMvc
                        .post("/api/v1/widgets") {
                            with(jwt().authorities(SimpleGrantedAuthority("ROLE_WIDGET_ADMIN")))
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
                response.name shouldBe "Admin Widget"
                response.published shouldBe false
                response.id shouldNotBe null
            }

            test("WIDGET_VIEWER cannot create widget - returns 403 Forbidden with ProblemDetail") {
                // Given - Create widget request with VIEWER role (insufficient permissions!)
                val request = CreateWidgetRequest(name = "Viewer Widget")
                val requestBody = objectMapper.writeValueAsString(request)

                // When/Then - POST with VIEWER role returns 403
                mockMvc
                    .post("/api/v1/widgets") {
                        with(jwt().authorities(SimpleGrantedAuthority("ROLE_WIDGET_VIEWER")))
                        contentType = MediaType.APPLICATION_JSON
                        content = requestBody
                    }.andExpect {
                        status { isForbidden() } // CRITICAL: 403, NOT 500!
                        content {
                            contentType(MediaType.APPLICATION_PROBLEM_JSON)
                            jsonPath("$.status") { value(403) }
                            jsonPath("$.type") { value("https://eaf.axians.com/errors/access-denied") }
                            jsonPath("$.detail") { value("Access Denied") }
                        }
                    }
            }

            test("Unauthenticated request - returns 401 Unauthorized") {
                // Given - Create widget request WITHOUT authentication
                val request = CreateWidgetRequest(name = "Anon Widget")
                val requestBody = objectMapper.writeValueAsString(request)

                // When/Then - POST without JWT returns 401
                mockMvc
                    .post("/api/v1/widgets") {
                        // NO .with(jwt()) - unauthenticated!
                        contentType = MediaType.APPLICATION_JSON
                        content = requestBody
                    }.andExpect {
                        status { isUnauthorized() }
                    }
            }
        }

        context("GET /api/v1/widgets/{id} - Find Widget (requires WIDGET_ADMIN or WIDGET_VIEWER role)") {

            test("WIDGET_VIEWER can read widget by ID - returns 200 OK") {
                // Given - Create widget first (with ADMIN)
                val createRequest = CreateWidgetRequest(name = "Readable Widget")
                val createBody = objectMapper.writeValueAsString(createRequest)

                val createResult =
                    mockMvc
                        .post("/api/v1/widgets") {
                            with(jwt().authorities(SimpleGrantedAuthority("ROLE_WIDGET_ADMIN")))
                            contentType = MediaType.APPLICATION_JSON
                            content = createBody
                        }.andReturn()

                val createdWidget =
                    objectMapper.readValue(
                        createResult.response.contentAsString,
                        WidgetResponse::class.java,
                    )

                // When - GET widget by ID with VIEWER role
                val result =
                    mockMvc
                        .get("/api/v1/widgets/${createdWidget.id}") {
                            with(jwt().authorities(SimpleGrantedAuthority("ROLE_WIDGET_VIEWER")))
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
                response.name shouldBe "Readable Widget"
            }

            test("Unauthenticated request to GET widget - returns 401") {
                // Given - Random UUID
                val nonExistentId = "00000000-0000-0000-0000-000000000001"

                // When/Then - GET without auth returns 401
                mockMvc
                    .get("/api/v1/widgets/$nonExistentId") {
                        // NO .with(jwt()) - unauthenticated!
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isUnauthorized() }
                    }
            }
        }

        context("PUT /api/v1/widgets/{id} - Update Widget with @PreAuthorize('hasRole(WIDGET_ADMIN)')") {

            test("WIDGET_ADMIN can update widget - returns 200 OK") {
                // Given - Create widget first
                val createRequest = CreateWidgetRequest(name = "Original Name")
                val createBody = objectMapper.writeValueAsString(createRequest)

                val createResult =
                    mockMvc
                        .post("/api/v1/widgets") {
                            with(jwt().authorities(SimpleGrantedAuthority("ROLE_WIDGET_ADMIN")))
                            contentType = MediaType.APPLICATION_JSON
                            content = createBody
                        }.andReturn()

                val createdWidget =
                    objectMapper.readValue(
                        createResult.response.contentAsString,
                        WidgetResponse::class.java,
                    )

                // When - PUT update widget with ADMIN role
                val updateRequest = UpdateWidgetRequest(name = "Updated Name")
                val updateBody = objectMapper.writeValueAsString(updateRequest)

                val updateResult =
                    mockMvc
                        .put("/api/v1/widgets/${createdWidget.id}") {
                            with(jwt().authorities(SimpleGrantedAuthority("ROLE_WIDGET_ADMIN")))
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
                response.updatedAt shouldNotBe response.createdAt
            }

            test("WIDGET_VIEWER cannot update widget - returns 403 Forbidden") {
                // Given - Create widget with ADMIN first
                val createRequest = CreateWidgetRequest(name = "Protected Widget")
                val createBody = objectMapper.writeValueAsString(createRequest)

                val createResult =
                    mockMvc
                        .post("/api/v1/widgets") {
                            with(jwt().authorities(SimpleGrantedAuthority("ROLE_WIDGET_ADMIN")))
                            contentType = MediaType.APPLICATION_JSON
                            content = createBody
                        }.andReturn()

                val createdWidget =
                    objectMapper.readValue(
                        createResult.response.contentAsString,
                        WidgetResponse::class.java,
                    )

                // When/Then - PUT with VIEWER role returns 403
                val updateRequest = UpdateWidgetRequest(name = "Hacker Attempt")
                val updateBody = objectMapper.writeValueAsString(updateRequest)

                mockMvc
                    .put("/api/v1/widgets/${createdWidget.id}") {
                        with(jwt().authorities(SimpleGrantedAuthority("ROLE_WIDGET_VIEWER")))
                        contentType = MediaType.APPLICATION_JSON
                        content = updateBody
                    }.andExpect {
                        status { isForbidden() } // CRITICAL: 403, NOT 500!
                        content {
                            contentType(MediaType.APPLICATION_PROBLEM_JSON)
                            jsonPath("$.status") { value(403) }
                            jsonPath("$.type") { value("https://eaf.axians.com/errors/access-denied") }
                        }
                    }
            }

            test("Unauthenticated request to UPDATE widget - returns 401") {
                // Given - Random UUID
                val nonExistentId = "00000000-0000-0000-0000-000000000002"
                val updateRequest = UpdateWidgetRequest(name = "Cannot Update")
                val updateBody = objectMapper.writeValueAsString(updateRequest)

                // When/Then - PUT without auth returns 401
                mockMvc
                    .put("/api/v1/widgets/$nonExistentId") {
                        // NO .with(jwt()) - unauthenticated!
                        contentType = MediaType.APPLICATION_JSON
                        content = updateBody
                    }.andExpect {
                        status { isUnauthorized() }
                    }
            }
        }
    }

    // NO companion object - Container managed by RbacTestContainersConfig!
}
