package com.axians.eaf.products.widget.query

import com.axians.eaf.framework.multitenancy.TenantContext
import com.axians.eaf.products.widget.WidgetDemoApplication
import com.axians.eaf.products.widget.domain.CreateWidgetCommand
import com.axians.eaf.products.widget.domain.PublishWidgetCommand
import com.axians.eaf.products.widget.domain.UpdateWidgetCommand
import com.axians.eaf.products.widget.domain.WidgetId
import com.axians.eaf.products.widget.test.config.AxonTestConfiguration
import com.axians.eaf.products.widget.test.config.TestAutoConfigurationOverrides
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.gateway.CommandGateway
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.UUID

/**
 * Integration test for Widget Projection Event Handler.
 *
 * Validates end-to-end projection flow:
 * - Command dispatched → Event published → Projection updated
 * - Uses Testcontainers PostgreSQL for real database
 * - Tests TrackingEventProcessor with projection lag <10s target (FR011)
 *
 * **Pattern:** Spring Boot 3.1+ @Testcontainers + @Container + @DynamicPropertySource
 */
@Testcontainers
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
class WidgetProjectionEventHandlerIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var commandGateway: CommandGateway

    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var dsl: DSLContext

    @BeforeEach
    fun beforeEach() {
        // Story 4.6: Set tenant context for command validation
        TenantContext.setCurrentTenantId(TEST_TENANT_ID)
    }

    @AfterEach
    fun afterEach() {
        // Story 4.6: Clean up tenant context
        TenantContext.clearCurrentTenant()
    }

    @Nested
    inner class `WidgetCreatedEvent projection` {
        @Test
        fun `CreateWidgetCommand widget_projection INSERT`() {
            // Given
            val widgetId = WidgetId(UUID.randomUUID())
            val widgetName = "Test Widget"

            // When - Dispatch command
            val startTime = System.currentTimeMillis()
            commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widgetId, widgetName, TEST_TENANT_ID))

            // Then - Verify projection updated (eventually pattern for async)
            eventually(Duration.ofSeconds(10)) {
                val table = DSL.table("widget_projection")
                val projection =
                    dsl
                        .selectFrom(table)
                        .where(DSL.field("id").eq(UUID.fromString(widgetId.value)))
                        .fetchOne()

                assertThat(projection).isNotNull()
                assertThat(projection!![DSL.field("name", String::class.java)]).isEqualTo(widgetName)
                assertThat(projection[DSL.field("published", Boolean::class.java)]).isEqualTo(false)
            }

            // Measure projection lag (target <10s per FR011)
            val projectionLag = System.currentTimeMillis() - startTime
            println("✅ Projection lag: ${projectionLag}ms (target: <10000ms)")
            assertThat(projectionLag).isLessThan(10000)
        }
    }

    @Nested
    inner class `WidgetUpdatedEvent projection` {
        @Test
        fun `UpdateWidgetCommand widget_projection UPDATE`() {
            // Given - Widget exists
            val widgetId = WidgetId(UUID.randomUUID())
            commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widgetId, "Original Name", TEST_TENANT_ID))

            eventually(Duration.ofSeconds(10)) {
                val table = DSL.table("widget_projection")
                val projection =
                    dsl
                        .selectFrom(table)
                        .where(DSL.field("id").eq(UUID.fromString(widgetId.value)))
                        .fetchOne()
                assertThat(projection).isNotNull()
            }

            // When - Update widget
            val updatedName = "Updated Name"
            commandGateway.sendAndWait<Unit>(UpdateWidgetCommand(widgetId, updatedName, TEST_TENANT_ID))

            // Then - Verify projection updated
            eventually(Duration.ofSeconds(10)) {
                val table = DSL.table("widget_projection")
                val projection =
                    dsl
                        .selectFrom(table)
                        .where(DSL.field("id").eq(UUID.fromString(widgetId.value)))
                        .fetchOne()

                assertThat(projection).isNotNull()
                assertThat(projection!![DSL.field("name", String::class.java)]).isEqualTo(updatedName)
                assertThat(projection[DSL.field("published", Boolean::class.java)]).isEqualTo(false)
            }
        }
    }

    @Nested
    inner class `WidgetPublishedEvent projection` {
        @Test
        fun `PublishWidgetCommand widget_projection published=true`() {
            // Given - Widget exists
            val widgetId = WidgetId(UUID.randomUUID())
            commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widgetId, "Test Widget", TEST_TENANT_ID))

            eventually(Duration.ofSeconds(10)) {
                val table = DSL.table("widget_projection")
                val projection =
                    dsl
                        .selectFrom(table)
                        .where(DSL.field("id").eq(UUID.fromString(widgetId.value)))
                        .fetchOne()
                assertThat(projection).isNotNull()
            }

            // When - Publish widget
            commandGateway.sendAndWait<Unit>(PublishWidgetCommand(widgetId, TEST_TENANT_ID))

            // Then - Verify published flag set
            eventually(Duration.ofSeconds(10)) {
                val table = DSL.table("widget_projection")
                val projection =
                    dsl
                        .selectFrom(table)
                        .where(DSL.field("id").eq(UUID.fromString(widgetId.value)))
                        .fetchOne()

                assertThat(projection).isNotNull()
                assertThat(projection!![DSL.field("published", Boolean::class.java)]).isEqualTo(true)
            }
        }
    }

    companion object {
        private const val TEST_TENANT_ID = "test-tenant-projection"

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:16.10-alpine"))
                .withDatabaseName("eaf_test")
                .withUsername("test")
                .withPassword("test")
    }
}

/**
 * Eventually polling pattern for asynchronous projection updates.
 *
 * Repeatedly executes assertion block until it succeeds or timeout is reached.
 */
private fun eventually(
    timeout: Duration,
    block: () -> Unit,
) {
    val deadline = System.currentTimeMillis() + timeout.toMillis()
    var lastException: Throwable? = null

    while (System.currentTimeMillis() < deadline) {
        try {
            block()
            return // Success!
        } catch (e: Throwable) {
            lastException = e
            Thread.sleep(100) // Poll every 100ms
        }
    }

    throw AssertionError("Eventually block did not succeed within $timeout", lastException)
}
