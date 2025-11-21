package com.axians.eaf.products.widget

import com.axians.eaf.framework.multitenancy.TenantContext
import com.axians.eaf.products.widget.WidgetDemoApplication
import com.axians.eaf.products.widget.domain.CreateWidgetCommand
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
import org.junit.jupiter.api.Timeout
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * Realistic mixed workload performance test.
 *
 * **Test Scenario:**
 * - 50 different Widget aggregates
 * - Each aggregate: 1 CREATE + 9 UPDATEs = 10 commands
 * - Total: 500 commands across 50 aggregates
 *
 * **Performance Characteristics:**
 * - Cold Cache (CREATE): First command on new aggregate (loads from empty cache)
 * - Warm Cache (UPDATEs): Subsequent commands on cached aggregate (O(1) lookup)
 * - Realistic Production Workload: Mix of hot and cold aggregates
 *
 * **Phase 2 Optimizations:**
 * - Aggregate Caching: WeakReferenceCache
 * - Expected: ~4ms per UPDATE (warm cache), ~20-40ms per CREATE (cold cache)
 * - Average: ~10-15ms per command (blended cold/warm)
 *
 * **Performance Targets:**
 * - 500 commands: <10 seconds
 * - Average per command: <20ms
 * - CREATE (cold): <50ms
 * - UPDATE (warm): <10ms
 *
 * Story 2.13: Performance Baseline and Monitoring - Phase 2
 */
@Testcontainers
@SpringBootTest(
    classes = [WidgetDemoApplication::class],
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.defer-datasource-initialization=true",
        "spring.modulith.events.jdbc.enabled=false",
        "axon.eventhandling.processors.__default__.mode=subscribing",
        "management.metrics.enable.all=false",
        "management.endpoint.metrics.enabled=false",
        TestAutoConfigurationOverrides.DISABLE_MODULITH_JPA,
    ],
)
@Import(AxonTestConfiguration::class)
@Sql("/schema.sql")
@ActiveProfiles("test")
class RealisticWorkloadPerformanceTest {
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
    inner class `Realistic mixed workload (50 aggregates × 10 commands)` {
        @Test
        @Timeout(60)
        fun `should handle mixed cold warm cache workload efficiently`() {
            val table = DSL.table("widget_projection")
            val aggregateCount = 50
            val updatesPerAggregate = 9
            val totalCommands = aggregateCount * (1 + updatesPerAggregate) // CREATE + UPDATEs

            // Track performance per aggregate
            val createTimes = mutableListOf<Long>()
            val updateTimes = mutableListOf<Long>()

            println(
                "\n📊 Realistic Workload Test: $aggregateCount aggregates × ${updatesPerAggregate + 1} commands = $totalCommands total",
            )

            // Measure total time for mixed workload
            val totalTime =
                measureTime {
                    repeat(aggregateCount) { aggregateIndex ->
                        val widgetId = WidgetId(UUID.randomUUID())

                        // CREATE (cold cache - new aggregate)
                        val createTime =
                            measureTime {
                                commandGateway.sendAndWait<Unit>(
                                    CreateWidgetCommand(widgetId, "Widget $aggregateIndex", TEST_TENANT_ID),
                                )
                            }
                        createTimes.add(createTime.inWholeMilliseconds)

                        // UPDATEs (warm cache - aggregate already in memory)
                        repeat(updatesPerAggregate) { updateIndex ->
                            val updateTime =
                                measureTime {
                                    commandGateway.sendAndWait<Unit>(
                                        UpdateWidgetCommand(widgetId, "Update $updateIndex", TEST_TENANT_ID),
                                    )
                                }
                            updateTimes.add(updateTime.inWholeMilliseconds)
                        }

                        // Progress update every 10 aggregates
                        if ((aggregateIndex + 1) % 10 == 0) {
                            println("   Processed ${aggregateIndex + 1}/$aggregateCount aggregates...")
                        }
                    }
                }

            println("\n⏱️  $totalCommands commands dispatched in: ${totalTime.inWholeSeconds}s")

            // Calculate statistics
            val avgCreateTime = createTimes.average()
            val avgUpdateTime = updateTimes.average()
            val avgCommandTime = (totalTime.inWholeMilliseconds.toDouble() / totalCommands)

            val minCreateTime = createTimes.minOrNull() ?: 0L
            val maxCreateTime = createTimes.maxOrNull() ?: 0L
            val minUpdateTime = updateTimes.minOrNull() ?: 0L
            val maxUpdateTime = updateTimes.maxOrNull() ?: 0L

            println("\n📈 Performance Breakdown:")
            println("   Overall:")
            println(
                "     Throughput: ${String.format(
                    "%.1f",
                    totalCommands.toDouble() / totalTime.inWholeSeconds,
                )} cmd/sec",
            )
            println("     Average: ${String.format("%.1f", avgCommandTime)}ms per command")
            println()
            println("   CREATE commands ($aggregateCount total) - Cold Cache:")
            println("     Average: ${String.format("%.1f", avgCreateTime)}ms")
            println("     Min: ${minCreateTime}ms, Max: ${maxCreateTime}ms")
            println()
            println("   UPDATE commands (${updateTimes.size} total) - Warm Cache:")
            println("     Average: ${String.format("%.1f", avgUpdateTime)}ms")
            println("     Min: ${minUpdateTime}ms, Max: ${maxUpdateTime}ms")
            println()
            println(
                "   Cache Benefit: ${String.format(
                    "%.1fx",
                    avgCreateTime / avgUpdateTime,
                )} faster for cached aggregates",
            )

            // Verify all aggregates were created and updated
            eventually(Duration.ofSeconds(10)) {
                val projectionCount =
                    dsl
                        .selectCount()
                        .from(table)
                        .fetchOne(0, Int::class.java)

                assertThat(projectionCount).isNotNull()
                assertThat(projectionCount).isEqualTo(aggregateCount)
            }

            // Performance assertions (relaxed for CI environment)
            assertThat(totalTime.inWholeSeconds).isLessThan(15L) // 500 commands in <15s (CI: slower hardware)
            assertThat(avgCommandTime.toLong()).isLessThan(30L) // Average <30ms per command (CI tolerance)
            assertThat(avgCreateTime.toLong()).isLessThan(100L) // CREATE (cold) <100ms (CI tolerance)
            assertThat(avgUpdateTime.toLong()).isLessThan(30L) // UPDATE (warm) <30ms (CI tolerance)

            println("✅ Realistic workload test passed: $aggregateCount aggregates processed")
        }
    }

    companion object {
        private const val TEST_TENANT_ID = "test-tenant-perf"

        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer(
                DockerImageName.parse("postgres:16.10-alpine"),
            ).withDatabaseName("realistic_workload_test")
                .withUsername("test")
                .withPassword("test")
                .withCommand(
                    "postgres",
                    "-c",
                    "fsync=off",
                    "-c",
                    "synchronous_commit=off",
                    "-c",
                    "full_page_writes=off",
                ).withTmpFs(mapOf("/var/lib/postgresql/data" to "rw"))
    }
}

/**
 * Eventually pattern for polling asynchronous operations.
 *
 * Retries block until success or timeout is reached.
 * Polls every 100ms until deadline.
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
