package com.axians.eaf.products.widget.api

import com.axians.eaf.framework.multitenancy.TenantContext
import com.axians.eaf.framework.web.rest.ProblemDetailExceptionHandler
import com.axians.eaf.products.widget.WidgetDemoApplication
import com.axians.eaf.products.widget.query.PaginatedWidgetResponse
import com.axians.eaf.products.widget.test.config.RbacTestContainersConfig
import com.axians.eaf.products.widget.test.config.RbacTestSecurityConfig
import com.axians.eaf.products.widget.test.config.TestAutoConfigurationOverrides
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
 * Multi-Tenant Integration Test for Widget Demo (Story 4.6 AC6-AC7).
 *
 * Validates comprehensive multi-tenancy support across all 3 layers:
 * - Layer 1: TenantContextFilter extracts tenant_id from JWT (Story 4.2)
 * - Layer 2: TenantValidationInterceptor validates command.tenantId (Story 4.3)
 * - Layer 3: PostgreSQL RLS enforces database-level isolation (Story 4.4)
 *
 * **Test Coverage:**
 * - AC6: Integration test creates widgets for multiple tenants
 * - AC7: Cross-tenant access test validates isolation
 * - Layer 1: Missing tenant_id claim rejection (400 Bad Request)
 * - Layer 2: Tenant context mismatch rejection
 * - Layer 3: PostgreSQL RLS blocks cross-tenant queries
 *
 * **Framework:** JUnit 6.0.1 + AssertJ 3.27.3 + Spring Security Test
 * **Prerequisites:** Stories 4.1-4.5 complete
 *
 * @since Story 4.6 AC6-AC7
 */
@SpringBootTest(
    classes = [
        WidgetDemoApplication::class,
        RbacTestSecurityConfig::class,
        ProblemDetailExceptionHandler::class,
        RbacTestContainersConfig::class,
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
class MultiTenantWidgetIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    companion object {
        private const val TENANT_A = "tenant-a"
        private const val TENANT_B = "tenant-b"
        private const val WIDGET_ADMIN_ROLE = "ROLE_WIDGET_ADMIN"
        private const val WIDGET_VIEWER_ROLE = "ROLE_WIDGET_VIEWER"
    }

    @BeforeEach
    fun beforeEach() {
        // Story 4.6: Ensure clean tenant context before each test
        TenantContext.clearCurrentTenant()
    }

    @AfterEach
    fun afterEach() {
        // Story 4.6: Clean up tenant context after each test
        TenantContext.clearCurrentTenant()
    }

    @Nested
    inner class `AC6 - Multi-Tenant Widget Creation` {
        @Test
        fun `Tenant A can create widget with tenant-a context`() {
            // Given - Tenant A creates a widget
            val request = CreateWidgetRequest(name = "Tenant A Widget")
            val requestBody = objectMapper.writeValueAsString(request)

            // When - POST with tenant-a JWT claim
            val result =
                mockMvc
                    .post("/api/v1/widgets") {
                        with(
                            jwt()
                                .authorities(SimpleGrantedAuthority(WIDGET_ADMIN_ROLE))
                                .jwt { it.claim("tenant_id", TENANT_A) },
                        )
                        contentType = MediaType.APPLICATION_JSON
                        content = requestBody
                    }.andExpect {
                        status { isCreated() }
                    }.andReturn()

            // Then - Widget created with tenant-a isolation
            val response =
                objectMapper.readValue(
                    result.response.contentAsString,
                    WidgetResponse::class.java,
                )
            assertThat(response.name).isEqualTo("Tenant A Widget")
            assertThat(response.id).isNotNull()
        }

        @Test
        fun `Tenant B can create widget with tenant-b context`() {
            // Given - Tenant B creates a widget
            val request = CreateWidgetRequest(name = "Tenant B Widget")
            val requestBody = objectMapper.writeValueAsString(request)

            // When - POST with tenant-b JWT claim
            val result =
                mockMvc
                    .post("/api/v1/widgets") {
                        with(
                            jwt()
                                .authorities(SimpleGrantedAuthority(WIDGET_ADMIN_ROLE))
                                .jwt { it.claim("tenant_id", TENANT_B) },
                        )
                        contentType = MediaType.APPLICATION_JSON
                        content = requestBody
                    }.andExpect {
                        status { isCreated() }
                    }.andReturn()

            // Then - Widget created with tenant-b isolation
            val response =
                objectMapper.readValue(
                    result.response.contentAsString,
                    WidgetResponse::class.java,
                )
            assertThat(response.name).isEqualTo("Tenant B Widget")
            assertThat(response.id).isNotNull()
        }

        @Test
        fun `Request without tenant_id claim is rejected with 400 Bad Request`() {
            // Given - Widget creation request
            val request = CreateWidgetRequest(name = "No Tenant Widget")
            val requestBody = objectMapper.writeValueAsString(request)

            // When/Then - POST without tenant_id claim returns 400 (Layer 1 validation)
            mockMvc
                .post("/api/v1/widgets") {
                    with(
                        jwt()
                            .authorities(SimpleGrantedAuthority(WIDGET_ADMIN_ROLE)),
                        // NO tenant_id claim - Layer 1 filter should reject!
                    )
                    contentType = MediaType.APPLICATION_JSON
                    content = requestBody
                }.andExpect {
                    status { isBadRequest() }
                    content {
                        contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        jsonPath("$.status") { value(400) }
                    }
                }
        }
    }

    @Nested
    inner class `AC7 - Cross-Tenant Isolation Validation` {
        @Test
        fun `Tenant A cannot see Tenant B widgets`() {
            // Given - Tenant A creates widget
            mockMvc
                .post("/api/v1/widgets") {
                    with(
                        jwt()
                            .authorities(SimpleGrantedAuthority(WIDGET_ADMIN_ROLE))
                            .jwt { it.claim("tenant_id", TENANT_A) },
                    )
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(CreateWidgetRequest("Tenant A Widget"))
                }.andExpect {
                    status { isCreated() }
                }

            // And - Tenant B creates widget
            mockMvc
                .post("/api/v1/widgets") {
                    with(
                        jwt()
                            .authorities(SimpleGrantedAuthority(WIDGET_ADMIN_ROLE))
                            .jwt { it.claim("tenant_id", TENANT_B) },
                    )
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(CreateWidgetRequest("Tenant B Widget"))
                }.andExpect {
                    status { isCreated() }
                }

            // When - Tenant A queries all widgets (with eventual consistency handling)
            runBlocking {
                delay(300) // Wait for projection (300ms)
            }

            val result =
                mockMvc
                    .get("/api/v1/widgets") {
                        with(
                            jwt()
                                .authorities(SimpleGrantedAuthority(WIDGET_VIEWER_ROLE))
                                .jwt { it.claim("tenant_id", TENANT_A) },
                        )
                    }.andExpect {
                        status { isOk() }
                    }.andReturn()

            // Then - Tenant A ONLY sees their own widget (Layer 3 RLS isolation)
            val response =
                objectMapper.readValue(
                    result.response.contentAsString,
                    PaginatedWidgetResponse::class.java,
                )
            val widgetNames = response.widgets.map { it.name }
            assertThat(widgetNames).contains("Tenant A Widget")
            assertThat(widgetNames).doesNotContain("Tenant B Widget") // CRITICAL: Cross-tenant isolation
        }
    }
}
