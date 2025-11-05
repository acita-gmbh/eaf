package com.axians.eaf.framework.persistence.eventstore

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.axonframework.eventhandling.DomainEventMessage
import org.axonframework.eventhandling.GenericDomainEventMessage
import org.axonframework.eventsourcing.eventstore.EventStorageEngine
import org.axonframework.messaging.MetaData
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.sql.DataSource
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.seconds

/**
 * Integration and performance tests for event store partitioning.
 *
 * Validates that:
 *  - Monthly partitions are created and used for inserts
 *  - Events route to the correct partition based on timestamp
 *  - Query performance for aggregate retrieval stays below 200ms with 100K events
 */
@SpringBootTest(classes = [EventStorePartitioningPerformanceTest.TestConfiguration::class])
class EventStorePartitioningPerformanceTest : FunSpec() {
    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var eventStorageEngine: EventStorageEngine

    private lateinit var jdbcTemplate: JdbcTemplate

    init {
        extension(SpringExtension())

        beforeSpec {
            jdbcTemplate = JdbcTemplate(dataSource)
        }

        test("DomainEventEntry is partitioned by month") {
            val partitions =
                jdbcTemplate.queryForList(
                    """
                    SELECT child.relname
                    FROM pg_inherits
                    JOIN pg_class parent ON parent.oid = pg_inherits.inhparent
                    JOIN pg_class child ON child.oid = pg_inherits.inhrelid
                    WHERE parent.relname = 'domainevententry'
                    ORDER BY child.relname
                    """.trimIndent(),
                    String::class.java,
                )

            partitions.isEmpty() shouldBe false
            partitions.count { it.startsWith("domainevententry_") } shouldBeGreaterThan 0
        }

        test("Events route to correct monthly partition") {
            truncateEvents()

            val currentMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
            val nextMonth = currentMonth.plusMonths(1)

            val currentAggregate = "partition-test-current"
            val nextAggregate = "partition-test-next"

            insertEventDirect(
                aggregateId = currentAggregate,
                sequence = 0L,
                timestamp = currentMonth.atStartOfDay().toInstant(ZoneOffset.UTC),
            )

            insertEventDirect(
                aggregateId = nextAggregate,
                sequence = 0L,
                timestamp = nextMonth.atStartOfDay().toInstant(ZoneOffset.UTC),
            )

            val format = DateTimeFormatter.ofPattern("yyyy_MM")

            val currentPartition =
                jdbcTemplate.queryForObject(
                    """
                    SELECT tableoid::regclass::text
                    FROM domainevententry
                    WHERE aggregateidentifier = ? AND sequencenumber = 0
                    """.trimIndent(),
                    String::class.java,
                    currentAggregate,
                )

            val nextPartition =
                jdbcTemplate.queryForObject(
                    """
                    SELECT tableoid::regclass::text
                    FROM domainevententry
                    WHERE aggregateidentifier = ? AND sequencenumber = 0
                    """.trimIndent(),
                    String::class.java,
                    nextAggregate,
                )

            val normalizedCurrent = requireNotNull(currentPartition).trim('"').lowercase()
            val normalizedNext = requireNotNull(nextPartition).trim('"').lowercase()

            normalizedCurrent shouldBe "domainevententry_${currentMonth.format(format)}"
            normalizedNext shouldBe "domainevententry_${nextMonth.format(format)}"
        }

        test("Aggregate retrieval stays below 200ms with 100K events")
            .config(timeout = 180.seconds) {
                truncateEvents()

                val totalAggregates = 100
                val eventsPerAggregate = 1_000
                val payload =
                    PerformanceEvent(
                        description = "event-store-partitioning-performance",
                        recordedAt = Instant.now(),
                    )

                repeat(totalAggregates) { aggregateIndex ->
                    val aggregateId = "perf-${aggregateIndex.toString().padStart(3, '0')}"
                    val events =
                        (0 until eventsPerAggregate).map { sequence ->
                            newEvent(
                                aggregateId = aggregateId,
                                sequence = sequence.toLong(),
                                payload = payload.copy(recordedAt = Instant.now()),
                            )
                        }

                    eventStorageEngine.appendEvents(*events.toTypedArray())
                }

                jdbcTemplate.execute("ANALYZE domainevententry")

                val targetAggregate = "perf-target"
                val targetEvents =
                    (0 until eventsPerAggregate).map { sequence ->
                        newEvent(
                            aggregateId = targetAggregate,
                            sequence = sequence.toLong(),
                            payload =
                                PerformanceEvent(
                                    description = "target-$sequence",
                                    recordedAt = Instant.now(),
                                ),
                        )
                    }

                eventStorageEngine.appendEvents(*targetEvents.toTypedArray())

                val durationMillis =
                    measureTimeMillis {
                        val retrieved =
                            eventStorageEngine
                                .readEvents(targetAggregate)
                                .asStream()
                                .toList()

                        retrieved.size shouldBe eventsPerAggregate
                    }

                println("Aggregate retrieval duration: $durationMillis ms")

                durationMillis shouldBeLessThan 200L
            }
    }

    private fun truncateEvents() {
        jdbcTemplate.execute("TRUNCATE TABLE domainevententry RESTART IDENTITY CASCADE")
    }

    private fun insertEventDirect(
        aggregateId: String,
        sequence: Long,
        timestamp: Instant,
    ) {
        val sql =
            """
            INSERT INTO domainevententry (
                aggregateidentifier,
                sequencenumber,
                type,
                eventidentifier,
                payload,
                payloadtype,
                "timestamp"
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

        jdbcTemplate.update(sql) { ps ->
            ps.setString(1, aggregateId)
            ps.setLong(2, sequence)
            ps.setString(3, "PerformanceAggregate")
            ps.setString(4, UUID.randomUUID().toString())
            ps.setBytes(5, """{"sequence":$sequence}""".toByteArray())
            ps.setString(6, PerformanceEvent::class.qualifiedName)
            ps.setString(7, timestamp.atOffset(ZoneOffset.UTC).toString())
        }
    }

    private fun newEvent(
        aggregateId: String,
        sequence: Long,
        payload: PerformanceEvent,
    ): DomainEventMessage<PerformanceEvent> =
        GenericDomainEventMessage(
            "PerformanceAggregate",
            aggregateId,
            sequence,
            payload,
            MetaData.emptyInstance(),
        )

    data class PerformanceEvent(
        val description: String,
        val recordedAt: Instant,
    )

    @Configuration
    @EnableAutoConfiguration
    @Import(PostgresEventStoreConfiguration::class)
    open class TestConfiguration

    companion object {
        private val postgresContainer =
            PostgreSQLContainer(
                DockerImageName.parse("postgres:16.10"),
            ).apply {
                withDatabaseName("eaf_test")
                withUsername("test_user")
                withPassword("test_pass")
                start()
            }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgresContainer.jdbcUrl }
            registry.add("spring.datasource.username") { postgresContainer.username }
            registry.add("spring.datasource.password") { postgresContainer.password }
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("spring.flyway.locations") { "classpath:db/migration" }
            registry.add("spring.flyway.baseline-on-migrate") { "true" }
        }
    }
}
