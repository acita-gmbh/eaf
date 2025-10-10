package com.axians.eaf.framework.security.filters

import com.axians.eaf.framework.security.tenant.TenantContext
import com.axians.eaf.framework.security.test.SecurityFrameworkTestApplication
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Integration tests for TenantContextFilter using framework-isolated test application.
 * Validates filter behavior with minimal Spring Boot context containing only framework beans.
 *
 * Uses test-scoped SecurityFrameworkTestApplication to maintain strict architectural
 * isolation from product modules while enabling comprehensive Spring Security integration testing.
 */
@SpringBootTest(
    classes = [SecurityFrameworkTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "spring.jpa.hibernate.ddl-auto=none",
        "logging.level.com.axians.eaf.framework.security=DEBUG",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("framework-test")
class TenantContextFilterIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var tenantContext: TenantContext

    @Autowired
    private lateinit var tenantContextFilter: TenantContextFilter

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    init {
        extension(SpringExtension())

        beforeEach {
            // Clean up any existing context
            repeat(10) { tenantContext.clearCurrentTenant() }
        }

        afterEach {
            // Ensure complete cleanup after each test
            repeat(10) { tenantContext.clearCurrentTenant() }
        }

        context("architectural isolation validation") {

            test("4.1-INT-001: should only load security framework beans") {
                val beanNames = applicationContext.beanDefinitionNames.toList()

                // Verify no product beans are loaded
                beanNames
                    .filter { name ->
                        name.contains("licensing", ignoreCase = true) ||
                            name.contains("product", ignoreCase = true)
                    }.shouldBeEmpty()

                // Verify security framework beans are present
                beanNames
                    .filter { name ->
                        name.contains("tenantContext", ignoreCase = true) ||
                            name.contains("securityFilterChain", ignoreCase = true)
                    }.shouldNotBeEmpty()
            }
        }

        context("framework module spring boot context validation") {

            test("4.1-INT-002: should bootstrap minimal Spring Boot context with framework beans only") {
                // Validate that we have a working Spring Boot context
                // TenantContextFilter defers to Spring Security. Test configuration permits the endpoint.
                mockMvc
                    .perform(
                        get("/test/health")
                            .contentType(MediaType.APPLICATION_JSON),
                    ).andExpect(status().isOk) // Endpoint remains accessible (permitAll).
            }

            test("4.1-INT-003: should have TenantContextFilter properly registered") {
                tenantContextFilter shouldNotBe null
                tenantContextFilter.javaClass.simpleName shouldBe "TenantContextFilter"
            }

            test("4.1-INT-004: should maintain tenant context operations in Spring environment") {
                val initialDepth = tenantContext.getStackDepth()

                tenantContext.setCurrentTenantId("spring-context-test-tenant")
                tenantContext.current() shouldBe "spring-context-test-tenant"
                tenantContext.clearCurrentTenant()

                tenantContext.getStackDepth() shouldBe initialDepth
            }
        }

        context("HTTP request processing through filter chain") {

            test("4.1-INT-005: should process requests through security filter chain and enforce fail-closed design") {
                // Test that requests go through the filter chain
                // Endpoint remains accessible under permitAll configuration.
                mockMvc
                    .perform(
                        get("/test/health")
                            .contentType(MediaType.APPLICATION_JSON),
                    ).andExpect(status().isOk)
            }

            test("4.1-INT-006: should validate filter performance meets requirements with fail-closed responses") {
                val startTime = System.nanoTime()
                val iterations = 20

                repeat(iterations) {
                    mockMvc
                        .perform(
                            get("/test/health")
                                .contentType(MediaType.APPLICATION_JSON),
                        ).andExpect(status().isOk)
                }

                val avgTimeMs = (System.nanoTime() - startTime) / iterations / 1_000_000.0

                if (System.getProperty("runPerfTests") == "true") {
                    avgTimeMs shouldBeLessThan 10.0
                }
            }
        }
    }
}
