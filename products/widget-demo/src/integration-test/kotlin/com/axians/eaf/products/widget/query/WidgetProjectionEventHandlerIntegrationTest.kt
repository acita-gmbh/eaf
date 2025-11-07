package com.axians.eaf.products.widget.query

import com.axians.eaf.products.widget.WidgetDemoApplication
import com.axians.eaf.products.widget.domain.CreateWidgetCommand
import com.axians.eaf.products.widget.domain.PublishWidgetCommand
import com.axians.eaf.products.widget.domain.UpdateWidgetCommand
import com.axians.eaf.products.widget.domain.WidgetId
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.axonframework.commandhandling.gateway.CommandGateway
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
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
    ],
)
@Sql("/schema.sql")
@ActiveProfiles("test")
class WidgetProjectionEventHandlerIntegrationTest : FunSpec() {
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var commandGateway: CommandGateway

    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var dsl: DSLContext

    init {
        extension(SpringExtension())

        context("WidgetCreatedEvent projection") {
            test("CreateWidgetCommand → widget_projection INSERT") {
                // Given
                val widgetId = WidgetId(UUID.randomUUID())
                val widgetName = "Test Widget"

                // When - Dispatch command
                val startTime = System.currentTimeMillis()
                commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widgetId, widgetName))

                // Then - Verify projection updated (eventually pattern for async)
                eventually(Duration.ofSeconds(10)) {
                    val table = DSL.table("widget_projection")
                    val projection =
                        dsl
                            .selectFrom(table)
                            .where(DSL.field("id").eq(UUID.fromString(widgetId.value)))
                            .fetchOne()

                    projection.shouldNotBeNull()
                    projection[DSL.field("name", String::class.java)] shouldBe widgetName
                    projection[DSL.field("published", Boolean::class.java)] shouldBe false
                }

                // Measure projection lag (target <10s per FR011)
                val projectionLag = System.currentTimeMillis() - startTime
                println("✅ Projection lag: ${projectionLag}ms (target: <10000ms)")
                (projectionLag < 10000) shouldBe true
            }
        }

        context("WidgetUpdatedEvent projection") {
            test("UpdateWidgetCommand → widget_projection UPDATE") {
                // Given - Widget exists
                val widgetId = WidgetId(UUID.randomUUID())
                commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widgetId, "Original Name"))

                eventually(Duration.ofSeconds(10)) {
                    val table = DSL.table("widget_projection")
                    val projection =
                        dsl
                            .selectFrom(table)
                            .where(DSL.field("id").eq(UUID.fromString(widgetId.value)))
                            .fetchOne()
                    projection.shouldNotBeNull()
                }

                // When - Update widget
                val updatedName = "Updated Name"
                commandGateway.sendAndWait<Unit>(UpdateWidgetCommand(widgetId, updatedName))

                // Then - Verify projection updated
                eventually(Duration.ofSeconds(10)) {
                    val table = DSL.table("widget_projection")
                    val projection =
                        dsl
                            .selectFrom(table)
                            .where(DSL.field("id").eq(UUID.fromString(widgetId.value)))
                            .fetchOne()

                    projection.shouldNotBeNull()
                    projection[DSL.field("name", String::class.java)] shouldBe updatedName
                    projection[DSL.field("published", Boolean::class.java)] shouldBe false
                }
            }
        }

        context("WidgetPublishedEvent projection") {
            test("PublishWidgetCommand → widget_projection published=true") {
                // Given - Widget exists
                val widgetId = WidgetId(UUID.randomUUID())
                commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widgetId, "Test Widget"))

                eventually(Duration.ofSeconds(10)) {
                    val table = DSL.table("widget_projection")
                    val projection =
                        dsl
                            .selectFrom(table)
                            .where(DSL.field("id").eq(UUID.fromString(widgetId.value)))
                            .fetchOne()
                    projection.shouldNotBeNull()
                }

                // When - Publish widget
                commandGateway.sendAndWait<Unit>(PublishWidgetCommand(widgetId))

                // Then - Verify published flag set
                eventually(Duration.ofSeconds(10)) {
                    val table = DSL.table("widget_projection")
                    val projection =
                        dsl
                            .selectFrom(table)
                            .where(DSL.field("id").eq(UUID.fromString(widgetId.value)))
                            .fetchOne()

                    projection.shouldNotBeNull()
                    projection[DSL.field("published", Boolean::class.java)] shouldBe true
                }
            }
        }
    }

    companion object {
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
            kotlinx.coroutines.delay(100) // Poll every 100ms
        }
    }

    throw AssertionError("Eventually block did not succeed within $timeout", lastException)
}
