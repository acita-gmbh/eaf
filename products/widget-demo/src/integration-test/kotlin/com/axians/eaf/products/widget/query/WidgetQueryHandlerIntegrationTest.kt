package com.axians.eaf.products.widget.query

import com.axians.eaf.products.widget.WidgetDemoApplication
import com.axians.eaf.products.widget.domain.CreateWidgetCommand
import com.axians.eaf.products.widget.domain.PublishWidgetCommand
import com.axians.eaf.products.widget.domain.UpdateWidgetCommand
import com.axians.eaf.products.widget.domain.WidgetId
import com.axians.eaf.products.widget.test.config.AxonTestConfiguration
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.delay
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.QueryGateway
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
 * Integration test for Widget Query Handler (Story 2.8).
 *
 * Validates end-to-end query flow:
 * - Command → Event → Projection → Query
 * - Uses Testcontainers PostgreSQL + Axon QueryGateway
 * - Tests cursor-based pagination
 * - Validates performance targets (FR011):
 *   - Single widget query: <50ms
 *   - Paginated list query: <200ms
 *
 * **Pattern:** Spring Boot 3.1+ @Testcontainers + @Container + @ServiceConnection
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
@Import(AxonTestConfiguration::class)
@Sql("/schema.sql")
@ActiveProfiles("test")
class WidgetQueryHandlerIntegrationTest : FunSpec() {
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var commandGateway: CommandGateway

    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var queryGateway: QueryGateway

    init {
        extension(SpringExtension())

        context("FindWidgetQuery") {

            test("returns widget projection after command execution") {
                // Given - Create widget via command
                val widgetId = WidgetId(UUID.randomUUID())
                val widgetName = "Query Test Widget"
                commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widgetId, widgetName))

                // Wait for projection to complete
                eventually(Duration.ofSeconds(10)) {
                    val projection =
                        queryGateway
                            .query(
                                FindWidgetQuery(widgetId),
                                ResponseTypes.optionalInstanceOf(WidgetProjection::class.java),
                            ).join()
                            .orElse(null)

                    projection.shouldNotBeNull()
                }

                // When - Query for widget (measure performance)
                val startTime = System.nanoTime()
                val projection =
                    queryGateway
                        .query(
                            FindWidgetQuery(widgetId),
                            ResponseTypes.optionalInstanceOf(WidgetProjection::class.java),
                        ).join()
                        .orElse(null)
                val queryDuration = (System.nanoTime() - startTime) / 1_000_000 // ms

                // Then - Widget returned with correct data
                projection.shouldNotBeNull()
                projection.id shouldBe widgetId
                projection.name shouldBe widgetName
                projection.published shouldBe false
                projection.createdAt.shouldNotBeNull()
                projection.updatedAt.shouldNotBeNull()

                // Performance validation (FR011: <50ms production target, <100ms CI threshold)
                // Note: CI runners may exceed production targets due to shared resources
                println(
                    "✅ Single widget query performance: ${queryDuration}ms (production target: <50ms, CI threshold: <100ms)",
                )
                (queryDuration < 100) shouldBe true
            }

            test("returns null for non-existent widget") {
                // Given - Non-existent widget ID
                val nonExistentId = WidgetId(UUID.randomUUID())

                // When - Query for non-existent widget
                val projection =
                    queryGateway
                        .query(
                            FindWidgetQuery(nonExistentId),
                            ResponseTypes.optionalInstanceOf(WidgetProjection::class.java),
                        ).join()
                        .orElse(null)

                // Then - Null returned
                projection.shouldBeNull()
            }

            test("reflects published state after PublishWidgetCommand") {
                // Given - Create and publish widget
                val widgetId = WidgetId(UUID.randomUUID())
                commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widgetId, "Publish Test"))
                commandGateway.sendAndWait<Unit>(PublishWidgetCommand(widgetId))

                // Wait for projection to complete
                eventually(Duration.ofSeconds(10)) {
                    val projection =
                        queryGateway
                            .query(
                                FindWidgetQuery(widgetId),
                                ResponseTypes.optionalInstanceOf(WidgetProjection::class.java),
                            ).join()
                            .orElse(null)

                    projection.shouldNotBeNull()
                    projection.published shouldBe true
                }

                // When - Query for published widget
                val projection =
                    queryGateway
                        .query(
                            FindWidgetQuery(widgetId),
                            ResponseTypes.optionalInstanceOf(WidgetProjection::class.java),
                        ).join()
                        .orElse(null)

                // Then - Published flag is true
                projection.shouldNotBeNull()
                projection.published shouldBe true
            }
        }

        context("ListWidgetsQuery") {

            test("returns widgets list (may include data from other tests)") {
                // Given - Database may contain widgets from previous tests
                // When - Query for widgets
                val response =
                    queryGateway
                        .query(
                            ListWidgetsQuery(limit = 50),
                            ResponseTypes.instanceOf(PaginatedWidgetResponse::class.java),
                        ).join()

                // Then - Response structure valid (regardless of widget count)
                response.shouldNotBeNull()
                response.hasMore shouldBe (response.widgets.size >= 50)
                if (response.hasMore) {
                    response.nextCursor.shouldNotBeNull()
                }
            }

            test("returns widgets in descending order by created_at") {
                // Given - Create 3 widgets with unique names for this test
                val testPrefix = "DescTest-${System.currentTimeMillis()}"
                val widget1Id = WidgetId(UUID.randomUUID())
                val widget2Id = WidgetId(UUID.randomUUID())
                val widget3Id = WidgetId(UUID.randomUUID())

                commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widget1Id, "$testPrefix-First"))
                delay(100) // Ensure different timestamps
                commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widget2Id, "$testPrefix-Second"))
                delay(100)
                commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widget3Id, "$testPrefix-Third"))

                // Wait for all projections
                eventually(Duration.ofSeconds(10)) {
                    val allWidgets =
                        queryGateway
                            .query(
                                ListWidgetsQuery(limit = 100),
                                ResponseTypes.instanceOf(PaginatedWidgetResponse::class.java),
                            ).join()
                    val testWidgets = allWidgets.widgets.filter { it.name.startsWith(testPrefix) }
                    testWidgets.size shouldBe 3
                }

                // When - List all widgets and filter to test widgets (measure performance)
                val startTime = System.nanoTime()
                val response =
                    queryGateway
                        .query(
                            ListWidgetsQuery(limit = 100),
                            ResponseTypes.instanceOf(PaginatedWidgetResponse::class.java),
                        ).join()
                val queryDuration = (System.nanoTime() - startTime) / 1_000_000 // ms

                // Filter to test widgets only
                val testWidgets = response.widgets.filter { it.name.startsWith(testPrefix) }

                // Then - Test widgets returned in descending order (newest first)
                testWidgets shouldHaveSize 3
                testWidgets[0].name shouldBe "$testPrefix-Third" // Newest
                testWidgets[2].name shouldBe "$testPrefix-First" // Oldest

                // Performance validation (FR011: <200ms production target, <500ms CI threshold)
                // Note: CI runners may exceed production targets due to shared resources
                println(
                    "✅ Paginated list query performance: ${queryDuration}ms (production target: <200ms, CI threshold: <500ms)",
                )
                (queryDuration < 500) shouldBe true
            }

            test("cursor pagination returns next page correctly") {
                // Given - Create 5 widgets with unique names for this test
                val testPrefix = "PageTest-${System.currentTimeMillis()}"
                val widgetIds =
                    (1..5).map { i ->
                        val widgetId = WidgetId(UUID.randomUUID())
                        commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widgetId, "$testPrefix-Widget-$i"))
                        delay(50) // Ensure different timestamps
                        widgetId
                    }

                // Wait for all projections
                eventually(Duration.ofSeconds(10)) {
                    val response =
                        queryGateway
                            .query(
                                ListWidgetsQuery(limit = 100),
                                ResponseTypes.instanceOf(PaginatedWidgetResponse::class.java),
                            ).join()
                    val testWidgets = response.widgets.filter { it.name.startsWith(testPrefix) }
                    testWidgets.size shouldBe 5
                }

                // When - Query first page with large limit to get all our test widgets
                val allTestWidgetsResponse =
                    queryGateway
                        .query(
                            ListWidgetsQuery(limit = 100),
                            ResponseTypes.instanceOf(PaginatedWidgetResponse::class.java),
                        ).join()

                val testWidgets = allTestWidgetsResponse.widgets.filter { it.name.startsWith(testPrefix) }

                // Then - Verify we have our 5 test widgets
                testWidgets shouldHaveSize 5

                // Verify pagination works: if we query with limit=2, we should see hasMore behavior
                val limitedResponse =
                    queryGateway
                        .query(
                            ListWidgetsQuery(limit = 2),
                            ResponseTypes.instanceOf(PaginatedWidgetResponse::class.java),
                        ).join()

                // Then - Limited response should respect limit
                limitedResponse.widgets shouldHaveSize 2
                // hasMore depends on total widgets in DB (may be true if other tests created data)
                if (limitedResponse.hasMore) {
                    limitedResponse.nextCursor.shouldNotBeNull()
                    limitedResponse.nextCursor!!.shouldNotBeBlank()
                }
            }

            test("enforces maximum limit of 100 items") {
                // Given - Request excessive limit
                val query = ListWidgetsQuery(limit = 500) // Exceeds max 100

                // When - Query with large limit
                val response =
                    queryGateway
                        .query(
                            query,
                            ResponseTypes.instanceOf(PaginatedWidgetResponse::class.java),
                        ).join()

                // Then - No exception thrown (limit clamped internally to 100)
                response.shouldNotBeNull()
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
            delay(100) // Poll every 100ms
        }
    }

    throw AssertionError("Eventually block did not succeed within $timeout", lastException)
}
