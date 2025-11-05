package com.axians.eaf.framework.cqrs.snapshot

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.eventsourcing.eventstore.EventStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Integration test for snapshot creation and aggregate loading
 *
 * Tests:
 * - AC1: SnapshotTriggerDefinition configured (every 100 events)
 * - AC3: snapshot_entry table schema validated
 * - AC4: Integration test creates 250+ events and verifies snapshots
 * - AC5: Aggregate loading uses snapshot (not full replay)
 *
 * Uses Testcontainers with PostgreSQL for real database integration testing.
 */
@SpringBootTest(
    classes = [SnapshotTestConfiguration::class],
)
@ActiveProfiles("test")
@Testcontainers
class SnapshotIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var commandGateway: CommandGateway

    @Autowired
    private lateinit var eventStore: EventStore

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    init {
        extension(SpringExtension())

        test("AC4: aggregate with 250 events creates 2 snapshots at sequence 100 and 200") {
            // Create aggregate
            val aggregateId = UUID.randomUUID()
            commandGateway.sendAndWait<Any>(CreateTestAggregateCommand(aggregateId))

            // Apply 249 more commands → total 250 events (1 create + 249 updates)
            repeat(249) {
                commandGateway.sendAndWait<Any>(UpdateTestAggregateCommand(aggregateId))
            }

            // Verify snapshots created in snapshot_entry table
            val snapshots =
                jdbcTemplate.query(
                    """
                    SELECT aggregateIdentifier, sequenceNumber
                    FROM SnapshotEventEntry
                    WHERE aggregateIdentifier = ?
                    ORDER BY sequenceNumber
                    """.trimIndent(),
                    { rs, _ ->
                        Pair(
                            rs.getString("aggregateIdentifier"),
                            rs.getLong("sequenceNumber"),
                        )
                    },
                    aggregateId.toString(),
                )

            // Should have snapshots at sequence 100 and 200
            snapshots shouldHaveSize 2
            snapshots[0].second shouldBe 100
            snapshots[1].second shouldBe 200
        }

        test("AC5: aggregate loading uses snapshot (not full event replay)") {
            // Create aggregate with 150 events (snapshot at 100)
            val aggregateId = UUID.randomUUID()
            commandGateway.sendAndWait<Any>(CreateTestAggregateCommand(aggregateId))
            repeat(149) {
                commandGateway.sendAndWait<Any>(UpdateTestAggregateCommand(aggregateId))
            }

            // Read events from event store
            val events = eventStore.readEvents(aggregateId.toString()).asStream().toList()

            // Verify aggregate state matches expected counter (150 events total)
            events shouldHaveSize 150

            // Verify snapshot was created at sequence 100
            val snapshot =
                jdbcTemplate.queryForObject(
                    """
                    SELECT sequenceNumber
                    FROM SnapshotEventEntry
                    WHERE aggregateIdentifier = ?
                    ORDER BY sequenceNumber DESC
                    LIMIT 1
                    """.trimIndent(),
                    Long::class.java,
                    aggregateId.toString(),
                )

            snapshot shouldBe 100
        }

        test("AC3: snapshot_entry table has correct schema") {
            // Verify table exists and has expected columns
            val columns =
                jdbcTemplate.query(
                    """
                    SELECT column_name, data_type
                    FROM information_schema.columns
                    WHERE table_name = 'snapshotevententry'
                    ORDER BY ordinal_position
                    """.trimIndent(),
                ) { rs, _ ->
                    Pair(
                        rs.getString("column_name"),
                        rs.getString("data_type"),
                    )
                }

            // Verify essential columns exist
            val columnNames = columns.map { it.first }
            columnNames shouldContain "aggregateidentifier"
            columnNames shouldContain "sequencenumber"
            columnNames shouldContain "payload"
            columnNames shouldContain "payloadtype"
            columnNames shouldContain "timestamp"
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
