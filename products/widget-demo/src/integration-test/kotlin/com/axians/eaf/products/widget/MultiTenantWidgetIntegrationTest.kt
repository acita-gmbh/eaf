package com.axians.eaf.products.widget

import com.axians.eaf.framework.multitenancy.TenantContext
import com.axians.eaf.products.widget.domain.CreateWidgetCommand
import com.axians.eaf.products.widget.domain.WidgetId
import com.axians.eaf.products.widget.query.FindWidgetQuery
import com.axians.eaf.products.widget.query.ListWidgetsQuery
import com.axians.eaf.products.widget.query.PaginatedWidgetResponse
import com.axians.eaf.products.widget.query.WidgetProjection
import com.axians.eaf.products.widget.test.config.AxonTestConfiguration
import com.axians.eaf.products.widget.test.config.TestAutoConfigurationOverrides
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.QueryGateway
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.UUID

/**
 * Integration test validating multi-tenant isolation for Widget aggregate.
 *
 * **Story 4.6 - AC6, AC7:**
 * - AC6: Integration test creates widgets for multiple tenants
 * - AC7: Cross-tenant access test validates isolation (tenant A cannot see tenant B widgets)
 *
 * **Test Pattern:**
 * 1. Create widgets for tenant-a
 * 2. Create widgets for tenant-b
 * 3. Verify tenant-a can only query tenant-a widgets
 * 4. Verify tenant-b can only query tenant-b widgets
 * 5. Verify database projection has correct tenant_id
 *
 * Epic 4, Story 4.6: AC6, AC7
 */
@SpringBootTest(
    classes = [WidgetDemoApplication::class],
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.defer-datasource-initialization=true",
        TestAutoConfigurationOverrides.DISABLE_MODULITH_JPA,
    ],
)
@Import(AxonTestConfiguration::class)
@Sql("/schema.sql")
@ActiveProfiles("test")
class MultiTenantWidgetIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var commandGateway: CommandGateway

    @Autowired
    private lateinit var queryGateway: QueryGateway

    @Autowired
    private lateinit var dsl: DSLContext

    init {
        extension(SpringExtension())

        afterEach {
            // Clean up tenant context after each test
            @Suppress("SwallowedException")
            try {
                TenantContext.clearCurrentTenant()
            } catch (e: Exception) {
                // Already cleared
            }
        }

        context("AC6: Multi-tenant widget creation") {
            test("Create widgets for multiple tenants and verify isolation") {
                // Given - Two tenants
                val tenantA = "tenant-a"
                val tenantB = "tenant-b"

                // When - Create widgets for tenant A
                TenantContext.setCurrentTenantId(tenantA)
                val widgetA1 = WidgetId(UUID.randomUUID())
                val widgetA2 = WidgetId(UUID.randomUUID())

                commandGateway.sendAndWait<Unit>(
                    CreateWidgetCommand(widgetA1, "Widget A1", tenantA),
                )
                commandGateway.sendAndWait<Unit>(
                    CreateWidgetCommand(widgetA2, "Widget A2", tenantA),
                )

                // Switch to tenant B and create widgets
                TenantContext.clearCurrentTenant()
                TenantContext.setCurrentTenantId(tenantB)
                val widgetB1 = WidgetId(UUID.randomUUID())
                val widgetB2 = WidgetId(UUID.randomUUID())

                commandGateway.sendAndWait<Unit>(
                    CreateWidgetCommand(widgetB1, "Widget B1", tenantB),
                )
                commandGateway.sendAndWait<Unit>(
                    CreateWidgetCommand(widgetB2, "Widget B2", tenantB),
                )

                // Wait for projections to be updated
                eventually(Duration.ofSeconds(5)) {
                    val count =
                        dsl
                            .selectCount()
                            .from(DSL.table("widget_projection"))
                            .fetchOne(0, Int::class.java)
                    count shouldBe 4
                }

                // Then - Verify database has correct tenant_id for all widgets
                val table = DSL.table("widget_projection")
                val widgetsA =
                    dsl
                        .selectFrom(table)
                        .where(DSL.field("tenant_id").eq(tenantA))
                        .fetch()

                widgetsA shouldHaveSize 2
                widgetsA.map { it[DSL.field("id", UUID::class.java)] }.toSet() shouldBe
                    setOf(UUID.fromString(widgetA1.value), UUID.fromString(widgetA2.value))

                val widgetsB =
                    dsl
                        .selectFrom(table)
                        .where(DSL.field("tenant_id").eq(tenantB))
                        .fetch()

                widgetsB shouldHaveSize 2
                widgetsB.map { it[DSL.field("id", UUID::class.java)] }.toSet() shouldBe
                    setOf(UUID.fromString(widgetB1.value), UUID.fromString(widgetB2.value))
            }
        }

        context("AC7: Cross-tenant access isolation") {
            test("Tenant A cannot access tenant B widgets via queries") {
                // Given - Widgets exist for both tenants
                val tenantA = "tenant-isolation-a"
                val tenantB = "tenant-isolation-b"

                // Create widget for tenant A
                TenantContext.setCurrentTenantId(tenantA)
                val widgetA = WidgetId(UUID.randomUUID())
                commandGateway.sendAndWait<Unit>(
                    CreateWidgetCommand(widgetA, "Tenant A Widget", tenantA),
                )

                // Create widget for tenant B
                TenantContext.clearCurrentTenant()
                TenantContext.setCurrentTenantId(tenantB)
                val widgetB = WidgetId(UUID.randomUUID())
                commandGateway.sendAndWait<Unit>(
                    CreateWidgetCommand(widgetB, "Tenant B Widget", tenantB),
                )

                // Wait for projections
                eventually(Duration.ofSeconds(5)) {
                    val count =
                        dsl
                            .selectCount()
                            .from(DSL.table("widget_projection"))
                            .where(
                                DSL
                                    .field("tenant_id")
                                    .`in`(tenantA, tenantB),
                            ).fetchOne(0, Int::class.java)
                    count shouldBe 2
                }

                // When/Then - Tenant A queries should NOT return tenant B widgets
                TenantContext.clearCurrentTenant()
                TenantContext.setCurrentTenantId(tenantA)

                // Query by ID - tenant A should see their widget
                val resultA =
                    queryGateway
                        .query(FindWidgetQuery(widgetA), WidgetProjection::class.java)
                        .get()
                resultA.shouldNotBeNull()
                resultA.id shouldBe widgetA

                // Query tenant B's widget while in tenant A context - should return null
                val resultB =
                    queryGateway
                        .query(FindWidgetQuery(widgetB), WidgetProjection::class.java)
                        .get()
                resultB.shouldBeNull() // Cross-tenant access blocked

                // List all widgets - should only see tenant A widgets
                val allWidgets =
                    queryGateway
                        .query(ListWidgetsQuery(limit = 100), PaginatedWidgetResponse::class.java)
                        .get()

                allWidgets.widgets shouldHaveSize 1
                allWidgets.widgets[0].id shouldBe widgetA

                // When/Then - Switch to tenant B context
                TenantContext.clearCurrentTenant()
                TenantContext.setCurrentTenantId(tenantB)

                // Tenant B should see their widget
                val resultB2 =
                    queryGateway
                        .query(FindWidgetQuery(widgetB), WidgetProjection::class.java)
                        .get()
                resultB2.shouldNotBeNull()
                resultB2.id shouldBe widgetB

                // Tenant B should NOT see tenant A's widget
                val resultA2 =
                    queryGateway
                        .query(FindWidgetQuery(widgetA), WidgetProjection::class.java)
                        .get()
                resultA2.shouldBeNull() // Cross-tenant access blocked

                // List all widgets in tenant B context - should only see tenant B widgets
                val allWidgetsB =
                    queryGateway
                        .query(ListWidgetsQuery(limit = 100), PaginatedWidgetResponse::class.java)
                        .get()

                allWidgetsB.widgets shouldHaveSize 1
                allWidgetsB.widgets[0].id shouldBe widgetB
            }

            test("Database projection enforces tenant_id for all widgets") {
                // Given
                val tenant = "tenant-db-check"
                TenantContext.setCurrentTenantId(tenant)

                val widgetId = WidgetId(UUID.randomUUID())
                commandGateway.sendAndWait<Unit>(
                    CreateWidgetCommand(widgetId, "DB Tenant Test", tenant),
                )

                // Then - Verify database has tenant_id
                eventually(Duration.ofSeconds(5)) {
                    val table = DSL.table("widget_projection")
                    val projection =
                        dsl
                            .selectFrom(table)
                            .where(DSL.field("id").eq(UUID.fromString(widgetId.value)))
                            .fetchOne()

                    projection.shouldNotBeNull()
                    projection[DSL.field("tenant_id", String::class.java)] shouldBe tenant
                }
            }
        }

        context("AC8: Verify existing Widget tests still pass") {
            test("Widget creation, update, publish flow works with tenant context") {
                // Given - Tenant context set
                val tenant = "tenant-workflow-test"
                TenantContext.setCurrentTenantId(tenant)

                // When - Execute full widget lifecycle
                val widgetId = WidgetId(UUID.randomUUID())

                // Create
                commandGateway.sendAndWait<Unit>(
                    CreateWidgetCommand(widgetId, "Lifecycle Test", tenant),
                )

                // Update (requires non-published widget)
                commandGateway.sendAndWait<Unit>(
                    com.axians.eaf.products.widget.domain.UpdateWidgetCommand(
                        widgetId,
                        "Updated Name",
                        tenant,
                    ),
                )

                // Publish
                commandGateway.sendAndWait<Unit>(
                    com.axians.eaf.products.widget.domain
                        .PublishWidgetCommand(widgetId, tenant),
                )

                // Then - Verify final state in projection
                eventually(Duration.ofSeconds(5)) {
                    val result =
                        queryGateway
                            .query(FindWidgetQuery(widgetId), WidgetProjection::class.java)
                            .get()

                    result.shouldNotBeNull()
                    result.name shouldBe "Updated Name"
                    result.published shouldBe true
                }
            }
        }
    }

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:16.10-alpine"))
                .withDatabaseName("eaf_test_multi_tenant")
                .withUsername("test")
                .withPassword("test")
                .also { it.start() } // Manual start for Kotest compatibility

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
        }
    }
}

/**
 * Eventually polling pattern for asynchronous projection updates.
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
