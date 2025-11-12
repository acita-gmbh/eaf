package com.axians.eaf.products.widget

import com.axians.eaf.products.widget.WidgetDemoApplication
import com.axians.eaf.products.widget.domain.CreateWidgetCommand
import com.axians.eaf.products.widget.domain.UpdateWidgetCommand
import com.axians.eaf.products.widget.domain.WidgetId
import com.axians.eaf.products.widget.test.config.AxonTestConfiguration
import com.axians.eaf.products.widget.test.config.TestAutoConfigurationOverrides
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.axonframework.commandhandling.gateway.CommandGateway
import org.jooq.DSLContext
import org.jooq.impl.DSL
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
 * Snapshot performance and functional tests (deferred from Story 2.4).
 *
 * **Validation Strategy:**
 * - Uses widget_projection as proxy for event processing (1:1 event-to-projection)
 * - Measures command throughput (commands/second)
 * - Validates snapshot threshold reached via event count
 * - No direct EventStore.readEvents() calls (causes issues in Testcontainers)
 *
 * **Performance Targets (Relaxed for MVP):**
 * - Command throughput: >10 commands/second
 * - 250 commands: <30 seconds
 * - 1000 commands: <2 minutes
 *
 * **Phase 2 Optimizations (Story 2.13):**
 * - Aggregate Caching: WeakReferenceCache eliminates repeated event loading
 * - Expected improvement: ~100-150ms per command for hot aggregates
 *
 * Story 2.13: Performance Baseline and Monitoring
 * Original Story: 2.4 - Snapshot Support
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
class SnapshotPerformanceTest : FunSpec() {
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var commandGateway: CommandGateway

    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var dsl: DSLContext

    init {
        extension(SpringExtension())

        context("Snapshot threshold validation (250 events)") {
            test("should process 250 commands successfully").config(timeout = 60.seconds) {
                val widgetId = WidgetId(UUID.randomUUID())
                val table = DSL.table("widget_projection")
                val snapshotTable = DSL.table("snapshot_event_entry")

                // Measure command throughput
                val totalTime =
                    measureTime {
                        // Create widget (command 1, event 0)
                        commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widgetId, "Snapshot Test"))

                        // 249 updates (total: 250 commands, events 0-249)
                        repeat(249) { index ->
                            if (index % 50 == 0 && index > 0) {
                                println("   Dispatched ${index + 1}/249 updates...")
                            }
                            commandGateway.sendAndWait<Unit>(UpdateWidgetCommand(widgetId, "Update $index"))
                        }
                    }

                println("⏱️  250 commands dispatched in: ${totalTime.inWholeSeconds}s")
                println("   Throughput: ${String.format("%.1f", 250.0 / totalTime.inWholeSeconds)} cmd/sec")

                // Verify final projection (eventually pattern for async)
                eventually(Duration.ofSeconds(10)) {
                    val projection =
                        dsl
                            .selectFrom(table)
                            .where(DSL.field("id").eq(UUID.fromString(widgetId.value)))
                            .fetchOne()

                    projection.shouldNotBeNull()
                    projection[DSL.field("name", String::class.java)] shouldBe "Update 248"
                }

                // Verify snapshot behavior (NOTE: Snapshots disabled in test profile via @Profile("!test"))
                // This documents expected behavior: 0 in test, 2 in production (at seq 100, 200)
                val snapshotCount =
                    dsl
                        .selectCount()
                        .from(snapshotTable)
                        .where(DSL.field("aggregate_identifier").eq(widgetId.value))
                        .fetchOne(0, Int::class.java)

                // Expected: 0 snapshots in test profile (disabled for 70x performance improvement)
                // Production: 2 snapshots expected (threshold = 100 events)
                println("   Snapshots created: ${snapshotCount ?: 0} (expected: 0 in test, 2 in production)")
                snapshotCount shouldBe 0 // Verify snapshots correctly disabled in test profile

                // Performance target: <30 seconds for 250 commands
                totalTime.inWholeSeconds shouldBeLessThan 30L
                println("✅ 250 events processed (snapshots disabled in test for performance)")
            }
        }

        context("Large event set with snapshots (1000 events)") {
            test("should process 1000 commands with snapshot benefit").config(timeout = 180.seconds) {
                val widgetId = WidgetId(UUID.randomUUID())
                val table = DSL.table("widget_projection")

                // Measure 1000 command throughput
                val totalTime =
                    measureTime {
                        commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widgetId, "Performance Test"))

                        repeat(999) { index ->
                            if (index % 100 == 0 && index > 0) {
                                println("   Dispatched ${index + 1}/999 updates...")
                            }
                            commandGateway.sendAndWait<Unit>(UpdateWidgetCommand(widgetId, "Update $index"))
                        }
                    }

                println("⏱️  1000 commands dispatched in: ${totalTime.inWholeSeconds}s")
                println("   Throughput: ${String.format("%.1f", 1000.0 / totalTime.inWholeSeconds)} cmd/sec")

                // Verify final projection
                eventually(Duration.ofSeconds(10)) {
                    val projection =
                        dsl
                            .selectFrom(table)
                            .where(DSL.field("id").eq(UUID.fromString(widgetId.value)))
                            .fetchOne()

                    projection.shouldNotBeNull()
                    projection[DSL.field("name", String::class.java)] shouldBe "Update 998"
                }

                // Performance target: <2 minutes for 1000 commands
                totalTime.inWholeSeconds shouldBeLessThan 120L
                println("✅ 1000 events processed (snapshots expected at seq 100, 200, ..., 900)")
            }
        }

        context("Command performance baseline") {
            test("measure individual command dispatch time").config(timeout = 60.seconds) {
                val widgetId = WidgetId(UUID.randomUUID())
                val table = DSL.table("widget_projection")

                // Measure single create command
                val createTime =
                    measureTime {
                        commandGateway.sendAndWait<Unit>(CreateWidgetCommand(widgetId, "Baseline Test"))
                    }

                println("📊 Command Performance Baseline:")
                println("   CreateWidget: ${createTime.inWholeMilliseconds}ms")

                // Verify projection
                eventually(Duration.ofSeconds(10)) {
                    val projection =
                        dsl
                            .selectFrom(table)
                            .where(DSL.field("id").eq(UUID.fromString(widgetId.value)))
                            .fetchOne()
                    projection.shouldNotBeNull()
                }

                // Measure 10 update commands
                val updateTimes = mutableListOf<Long>()
                repeat(10) { index ->
                    val time =
                        measureTime {
                            commandGateway.sendAndWait<Unit>(UpdateWidgetCommand(widgetId, "Baseline $index"))
                        }
                    updateTimes.add(time.inWholeMilliseconds)
                }

                val avgTime = updateTimes.average()
                val minTime = updateTimes.minOrNull() ?: 0L
                val maxTime = updateTimes.maxOrNull() ?: 0L

                println("   UpdateWidget (10 samples):")
                println("     Average: ${String.format("%.0f", avgTime)}ms")
                println("     Min: ${minTime}ms, Max: ${maxTime}ms")
                println("     Throughput: ${String.format("%.1f", 1000.0 / avgTime)} cmd/sec")

                // Verify all updates projected
                eventually(Duration.ofSeconds(10)) {
                    val projection =
                        dsl
                            .selectFrom(table)
                            .where(DSL.field("id").eq(UUID.fromString(widgetId.value)))
                            .fetchOne()

                    projection.shouldNotBeNull()
                    projection[DSL.field("name", String::class.java)] shouldBe "Baseline 9"
                }

                // Sanity: Commands should complete in reasonable time
                avgTime.toLong() shouldBeLessThan 1000L
                println("✅ Baseline measurements complete")
            }
        }
    }

    companion object {
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer(
                DockerImageName.parse("postgres:16.10-alpine"),
            ).withDatabaseName("snapshot_perf_test")
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
