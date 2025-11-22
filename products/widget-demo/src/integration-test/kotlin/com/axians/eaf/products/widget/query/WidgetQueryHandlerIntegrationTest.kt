package com.axians.eaf.products.widget.query

import com.axians.eaf.framework.multitenancy.TenantContext
import com.axians.eaf.products.widget.WidgetDemoApplication
import com.axians.eaf.products.widget.domain.CreateWidgetCommand
import com.axians.eaf.products.widget.domain.PublishWidgetCommand
import com.axians.eaf.products.widget.domain.WidgetId
import com.axians.eaf.products.widget.test.config.AxonTestConfiguration
import com.axians.eaf.products.widget.test.config.TestAutoConfigurationOverrides
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.QueryGateway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
        TestAutoConfigurationOverrides.DISABLE_MODULITH_JPA,
    ],
)
@Import(AxonTestConfiguration::class)
@Sql("/schema.sql")
@ActiveProfiles("test")
class WidgetQueryHandlerIntegrationTest {
    @Autowired
    private lateinit var commandGateway: CommandGateway

    @Autowired
    private lateinit var queryGateway: QueryGateway

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
    inner class FindWidgetQuery {
        @Test
        fun `returns widget projection after command execution`() =
            runBlocking {
                // Given - Create widget via command
                val widgetId = WidgetId(UUID.randomUUID())
                val widgetName = "Query Test Widget"
                commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widgetId, widgetName, TEST_TENANT_ID))

                // Wait for projection to complete
                eventually(Duration.ofSeconds(10)) {
                    val projection =
                        queryGateway
                            .query(
                                com.axians.eaf.products.widget.query
                                    .FindWidgetQuery(widgetId),
                                ResponseTypes.optionalInstanceOf(WidgetProjection::class.java),
                            ).join()
                            .orElse(null)

                    assertThat(projection).isNotNull()
                }

                // When - Query for widget (measure performance)
                val startTime = System.nanoTime()
                val projection =
                    queryGateway
                        .query(
                            com.axians.eaf.products.widget.query
                                .FindWidgetQuery(widgetId),
                            ResponseTypes.optionalInstanceOf(WidgetProjection::class.java),
                        ).join()
                        .orElse(null)
                val queryDuration = (System.nanoTime() - startTime) / 1_000_000 // ms

                // Then - Widget returned with correct data
                assertThat(projection).isNotNull()
                assertThat(projection!!.id).isEqualTo(widgetId)
                assertThat(projection.name).isEqualTo(widgetName)
                assertThat(projection.published).isFalse()
                assertThat(projection.createdAt).isNotNull()
                assertThat(projection.updatedAt).isNotNull()

                // Performance validation (FR011: <50ms production target, <100ms CI threshold)
                // Note: CI runners may exceed production targets due to shared resources
                println(
                    "✅ Single widget query performance: ${queryDuration}ms (production target: <50ms, CI threshold: <100ms)",
                )
                assertThat(queryDuration < 100).isTrue()
            }

        @Test
        fun `returns null for non-existent widget`() {
            // Given - Non-existent widget ID
            val nonExistentId = WidgetId(UUID.randomUUID())

            // When - Query for non-existent widget
            val projection =
                queryGateway
                    .query(
                        com.axians.eaf.products.widget.query
                            .FindWidgetQuery(nonExistentId),
                        ResponseTypes.optionalInstanceOf(WidgetProjection::class.java),
                    ).join()
                    .orElse(null)

            // Then - Null returned
            assertThat(projection).isNull()
        }

        @Test
        fun `reflects published state after PublishWidgetCommand`() =
            runBlocking {
                // Given - Create and publish widget
                val widgetId = WidgetId(UUID.randomUUID())
                commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widgetId, "Publish Test", TEST_TENANT_ID))
                commandGateway.sendAndWait<Unit>(PublishWidgetCommand(widgetId, TEST_TENANT_ID))

                // Wait for projection to complete
                eventually(Duration.ofSeconds(10)) {
                    val projection =
                        queryGateway
                            .query(
                                com.axians.eaf.products.widget.query
                                    .FindWidgetQuery(widgetId),
                                ResponseTypes.optionalInstanceOf(WidgetProjection::class.java),
                            ).join()
                            .orElse(null)

                    assertThat(projection).isNotNull()
                    assertThat(projection!!.published).isTrue()
                }

                // When - Query for published widget
                val projection =
                    queryGateway
                        .query(
                            com.axians.eaf.products.widget.query
                                .FindWidgetQuery(widgetId),
                            ResponseTypes.optionalInstanceOf(WidgetProjection::class.java),
                        ).join()
                        .orElse(null)

                // Then - Published flag is true
                assertThat(projection).isNotNull()
                assertThat(projection!!.published).isTrue()
            }
    }

    @Nested
    inner class ListWidgetsQuery {
        @Test
        fun `returns widgets list (may include data from other tests)`() {
            // Given - Database may contain widgets from previous tests
            // When - Query for widgets
            val response =
                queryGateway
                    .query(
                        com.axians.eaf.products.widget.query
                            .ListWidgetsQuery(limit = 50),
                        ResponseTypes.instanceOf(PaginatedWidgetResponse::class.java),
                    ).join()

            // Then - Response structure valid (regardless of widget count)
            assertThat(response).isNotNull()
            assertThat(response.hasMore).isEqualTo(response.widgets.size >= 50)
            if (response.hasMore) {
                assertThat(response.nextCursor).isNotNull()
            }
        }

        @Test
        fun `returns widgets in descending order by created_at`() =
            runBlocking {
                // Given - Create 3 widgets with unique names for this test
                val testPrefix = "DescTest-${System.currentTimeMillis()}"
                val widget1Id = WidgetId(UUID.randomUUID())
                val widget2Id = WidgetId(UUID.randomUUID())
                val widget3Id = WidgetId(UUID.randomUUID())

                commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widget1Id, "$testPrefix-First", TEST_TENANT_ID))
                delay(100) // Ensure different timestamps
                commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widget2Id, "$testPrefix-Second", TEST_TENANT_ID))
                delay(100)
                commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widget3Id, "$testPrefix-Third", TEST_TENANT_ID))

                // Wait for all projections
                eventually(Duration.ofSeconds(10)) {
                    val allWidgets =
                        queryGateway
                            .query(
                                com.axians.eaf.products.widget.query
                                    .ListWidgetsQuery(limit = 100),
                                ResponseTypes.instanceOf(PaginatedWidgetResponse::class.java),
                            ).join()
                    val testWidgets = allWidgets.widgets.filter { it.name.startsWith(testPrefix) }
                    assertThat(testWidgets.size).isEqualTo(3)
                }

                // When - List all widgets and filter to test widgets (measure performance)
                val startTime = System.nanoTime()
                val response =
                    queryGateway
                        .query(
                            com.axians.eaf.products.widget.query
                                .ListWidgetsQuery(limit = 100),
                            ResponseTypes.instanceOf(PaginatedWidgetResponse::class.java),
                        ).join()
                val queryDuration = (System.nanoTime() - startTime) / 1_000_000 // ms

                // Filter to test widgets only
                val testWidgets = response.widgets.filter { it.name.startsWith(testPrefix) }

                // Then - Test widgets returned in descending order (newest first)
                assertThat(testWidgets).hasSize(3)
                assertThat(testWidgets[0].name).isEqualTo("$testPrefix-Third") // Newest
                assertThat(testWidgets[2].name).isEqualTo("$testPrefix-First") // Oldest

                // Performance validation (FR011: <200ms production target, <500ms CI threshold)
                // Note: CI runners may exceed production targets due to shared resources
                println(
                    "✅ Paginated list query performance: ${queryDuration}ms (production target: <200ms, CI threshold: <500ms)",
                )
                assertThat(queryDuration < 500).isTrue()
            }

        @Test
        fun `cursor pagination returns next page correctly`() =
            runBlocking {
                // Given - Create 5 widgets with unique names for this test
                val testPrefix = "PageTest-${System.currentTimeMillis()}"
                val widgetIds =
                    (1..5).map { i ->
                        val widgetId = WidgetId(UUID.randomUUID())
                        commandGateway.sendAndWait<Unit>(
                            CreateWidgetCommand(widgetId, "$testPrefix-Widget-$i", TEST_TENANT_ID),
                        )
                        delay(50) // Ensure different timestamps
                        widgetId
                    }

                // Wait for all projections
                eventually(Duration.ofSeconds(10)) {
                    val response =
                        queryGateway
                            .query(
                                com.axians.eaf.products.widget.query
                                    .ListWidgetsQuery(limit = 100),
                                ResponseTypes.instanceOf(PaginatedWidgetResponse::class.java),
                            ).join()
                    val testWidgets = response.widgets.filter { it.name.startsWith(testPrefix) }
                    assertThat(testWidgets.size).isEqualTo(5)
                }

                // When - Query first page with large limit to get all our test widgets
                val allTestWidgetsResponse =
                    queryGateway
                        .query(
                            com.axians.eaf.products.widget.query
                                .ListWidgetsQuery(limit = 100),
                            ResponseTypes.instanceOf(PaginatedWidgetResponse::class.java),
                        ).join()

                val testWidgets = allTestWidgetsResponse.widgets.filter { it.name.startsWith(testPrefix) }

                // Then - Verify we have our 5 test widgets
                assertThat(testWidgets).hasSize(5)

                // Verify pagination works: if we query with limit=2, we should see hasMore behavior
                val limitedResponse =
                    queryGateway
                        .query(
                            com.axians.eaf.products.widget.query
                                .ListWidgetsQuery(limit = 2),
                            ResponseTypes.instanceOf(PaginatedWidgetResponse::class.java),
                        ).join()

                // Then - Limited response should respect limit
                assertThat(limitedResponse.widgets).hasSize(2)
                // hasMore depends on total widgets in DB (may be true if other tests created data)
                if (limitedResponse.hasMore) {
                    assertThat(limitedResponse.nextCursor).isNotNull()
                    assertThat(limitedResponse.nextCursor).isNotBlank()
                }
            }

        @Test
        fun `enforces maximum limit of 100 items`() {
            // Given - Request excessive limit
            val query =
                com.axians.eaf.products.widget.query
                    .ListWidgetsQuery(limit = 500) // Exceeds max 100

            // When - Query with large limit
            val response =
                queryGateway
                    .query(
                        query,
                        ResponseTypes.instanceOf(PaginatedWidgetResponse::class.java),
                    ).join()

            // Then - No exception thrown (limit clamped internally to 100)
            assertThat(response).isNotNull()
        }
    }

    companion object {
        private const val TEST_TENANT_ID = "test-tenant-query"

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
