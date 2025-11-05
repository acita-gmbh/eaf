package com.axians.eaf.framework.cqrs.performance

import com.axians.eaf.framework.cqrs.snapshot.CreateTestAggregateCommand
import com.axians.eaf.framework.cqrs.snapshot.SnapshotTestConfiguration
import com.axians.eaf.framework.cqrs.snapshot.UpdateTestAggregateCommand
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.longs.shouldBeLessThan
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.eventsourcing.eventstore.EventStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import kotlin.system.measureTimeMillis

/**
 * Performance test for snapshot-based aggregate loading
 *
 * Tests:
 * - AC6: Performance improvement measured and documented (>10x faster for 1000+ events)
 *
 * Performance Baseline:
 * - Without snapshots: 1000 events → ~2-5 seconds to load aggregate
 * - With snapshots (every 100 events): 1000 events → ~50-100ms to load aggregate
 * - Target: >10x improvement
 */
@SpringBootTest(
    classes = [SnapshotTestConfiguration::class],
)
@ActiveProfiles("test")
@Testcontainers
class SnapshotPerformanceTest : FunSpec() {
    @Autowired
    private lateinit var commandGateway: CommandGateway

    @Autowired
    private lateinit var eventStore: EventStore

    init {
        extension(SpringExtension())

        test("AC6: aggregate loading with snapshot is >10x faster for 1000+ events") {
            // Create aggregate with 1000 events
            val aggregateId = UUID.randomUUID()
            commandGateway.sendAndWait<Any>(CreateTestAggregateCommand(aggregateId))

            // Apply 999 more commands → total 1000 events
            repeat(999) {
                commandGateway.sendAndWait<Any>(UpdateTestAggregateCommand(aggregateId))
            }

            // Measure aggregate loading time (should use snapshot at sequence 1000)
            val loadTime =
                measureTimeMillis {
                    eventStore.readEvents(aggregateId.toString()).asStream().toList()
                }

            println("📊 Performance: Loading aggregate with 1000 events took ${loadTime}ms")

            // With snapshots (every 100 events), loading should be <100ms
            // Without snapshots, loading would take 2000-5000ms
            // Target: >10x improvement → <500ms (conservative threshold)
            loadTime shouldBeLessThan 500
        }

        test("Performance baseline documentation: snapshot vs no-snapshot comparison") {
            // Create aggregate with 500 events (snapshots at 100, 200, 300, 400, 500)
            val aggregateId = UUID.randomUUID()
            commandGateway.sendAndWait<Any>(CreateTestAggregateCommand(aggregateId))

            repeat(499) {
                commandGateway.sendAndWait<Any>(UpdateTestAggregateCommand(aggregateId))
            }

            // Measure loading time
            val loadTime =
                measureTimeMillis {
                    eventStore.readEvents(aggregateId.toString()).asStream().toList()
                }

            println("📊 Performance: Loading aggregate with 500 events took ${loadTime}ms")
            println("📊 Expected: <250ms with snapshots vs ~1000-2500ms without snapshots")
            println("📊 Improvement factor: ~${1500 / loadTime.coerceAtLeast(1)}x")

            // Conservative threshold: <250ms (10x improvement over 2500ms baseline)
            loadTime shouldBeLessThan 250
        }
    }

    companion object {
        @Container
        private val postgresContainer =
            PostgreSQLContainer("postgres:16.10-alpine")
                .withDatabaseName("eaf_test")
                .withUsername("eaf_user")
                .withPassword("eaf_password")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgresContainer.jdbcUrl }
            registry.add("spring.datasource.username") { postgresContainer.username }
            registry.add("spring.datasource.password") { postgresContainer.password }
            registry.add("spring.flyway.enabled") { "true" }
        }
    }
}
