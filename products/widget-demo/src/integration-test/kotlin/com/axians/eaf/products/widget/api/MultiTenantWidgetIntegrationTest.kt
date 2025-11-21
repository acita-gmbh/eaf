package com.axians.eaf.products.widget.api

import com.axians.eaf.framework.multitenancy.TenantContext
import com.axians.eaf.framework.web.rest.ProblemDetailExceptionHandler
import com.axians.eaf.products.widget.WidgetDemoApplication
import com.axians.eaf.products.widget.query.PaginatedWidgetResponse
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * Multi-Tenant Integration Test for Widget Demo (Story 4.6 AC6-AC7).
 *
 * Validates comprehensive multi-tenancy support using TenantContext:
 * - Multiple tenants can create and query their own widgets
 * - Cross-tenant isolation: Tenant A cannot see Tenant B widgets
 * - TenantContext managed via @BeforeEach (TestSecurityConfig bypasses JWT)
 *
 * **Test Coverage (AC6-AC7):**
 * - AC6: Integration test creates widgets for multiple tenants
 * - AC7: Cross-tenant isolation validated (Tenant A cannot see Tenant B)
 *
 * **Test Strategy:**
 * - Uses "test" profile with TestSecurityConfig (Security bypassed)
 * - TenantContext manually set in @BeforeEach per test
 * - Tests create widgets for different tenants and verify isolation
 * - Eventual consistency handling with polling for projections
 *
 * **Framework:** JUnit Jupiter 6.0.1 + AssertJ 3.27.3
 * **Prerequisites:** Stories 4.1-4.5 complete
 *
 * @since Story 4.6 AC6-AC7
 */
@Testcontainers
@SpringBootTest(
    classes = [
        WidgetDemoApplication::class,
        com.axians.eaf.products.widget.test.config.TestSecurityConfig::class,
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
@Import(com.axians.eaf.products.widget.test.config.AxonTestConfiguration::class)
@Sql("/schema.sql")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MultiTenantWidgetIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    companion object {
        private const val TENANT_A = "tenant-a"
        private const val TENANT_B = "tenant-b"

        @Container
        @ServiceConnection
        @JvmStatic
        val postgresContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer(
                DockerImageName.parse("postgres:16.10-alpine"),
            ).apply {
                withDatabaseName("multi_tenant_test")
                withUsername("test")
                withPassword("test")
            }
    }

    @Nested
    inner class `AC6 - Multi-Tenant Widget Creation` {
        @Test
        fun `Tenant A can create widget with tenant-a context`() {
            // Given - Set Tenant A context
            TenantContext.setCurrentTenantId(TENANT_A)

            val request = CreateWidgetRequest(name = "Tenant A Widget")
            val requestBody = objectMapper.writeValueAsString(request)

            try {
                // When - POST widget creation
                val result =
                    mockMvc
                        .post("/api/v1/widgets") {
                            contentType = MediaType.APPLICATION_JSON
                            content = requestBody
                        }.andExpect {
                            status { isCreated() }
                        }.andReturn()

                // Then - Widget created successfully
                val response =
                    objectMapper.readValue(
                        result.response.contentAsString,
                        WidgetResponse::class.java,
                    )
                assertThat(response.name).isEqualTo("Tenant A Widget")
            } finally {
                TenantContext.clearCurrentTenant()
            }
        }

        @Test
        fun `Tenant B can create widget with tenant-b context`() {
            // Given - Set Tenant B context
            TenantContext.setCurrentTenantId(TENANT_B)

            val request = CreateWidgetRequest(name = "Tenant B Widget")
            val requestBody = objectMapper.writeValueAsString(request)

            try {
                // When - POST widget creation
                val result =
                    mockMvc
                        .post("/api/v1/widgets") {
                            contentType = MediaType.APPLICATION_JSON
                            content = requestBody
                        }.andExpect {
                            status { isCreated() }
                        }.andReturn()

                // Then - Widget created successfully
                val response =
                    objectMapper.readValue(
                        result.response.contentAsString,
                        WidgetResponse::class.java,
                    )
                assertThat(response.name).isEqualTo("Tenant B Widget")
            } finally {
                TenantContext.clearCurrentTenant()
            }
        }
    }

    @Nested
    inner class `AC7 - Cross-Tenant Isolation Validation` {
        @Test
        fun `Tenant A cannot see Tenant B widgets`() {
            // Given - Create widget for Tenant A
            TenantContext.setCurrentTenantId(TENANT_A)
            val tenantARequest = CreateWidgetRequest(name = "Tenant A Secret")
            mockMvc
                .post("/api/v1/widgets") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(tenantARequest)
                }.andExpect {
                    status { isCreated() }
                }
            TenantContext.clearCurrentTenant()

            // And - Create widget for Tenant B
            TenantContext.setCurrentTenantId(TENANT_B)
            val tenantBRequest = CreateWidgetRequest(name = "Tenant B Widget")
            mockMvc
                .post("/api/v1/widgets") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(tenantBRequest)
                }.andExpect {
                    status {
                        isCreated()
                    }
                }
            TenantContext.clearCurrentTenant()

            // When - Tenant A queries widgets (eventual consistency with retry)
            TenantContext.setCurrentTenantId(TENANT_A)
            var widgetNames = emptyList<String>()

            try {
                runBlocking {
                    repeat(10) { attempt ->
                        val result =
                            mockMvc
                                .get("/api/v1/widgets") {
                                }.andReturn()

                        if (result.response.status == 200 && result.response.contentLength > 0) {
                            try {
                                val response =
                                    objectMapper.readValue(
                                        result.response.contentAsString,
                                        PaginatedWidgetResponse::class.java,
                                    )
                                widgetNames = response.widgets.map { it.name }

                                // Stop if widget appeared
                                if (widgetNames.isNotEmpty()) {
                                    return@runBlocking
                                }
                            } catch (e: Exception) {
                                // Parsing error, retry
                            }
                        }

                        if (attempt < 9) {
                            delay(100)
                        }
                    }
                }

                // Then - Tenant A ONLY sees their widget (not Tenant B's)
                assertThat(widgetNames).isNotEmpty()
                assertThat(widgetNames).contains("Tenant A Secret")
                assertThat(widgetNames).doesNotContain("Tenant B Widget") // Cross-tenant isolation!
            } finally {
                TenantContext.clearCurrentTenant()
            }
        }
    }
}
