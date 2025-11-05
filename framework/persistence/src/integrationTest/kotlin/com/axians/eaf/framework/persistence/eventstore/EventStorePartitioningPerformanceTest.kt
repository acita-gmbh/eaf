package com.axians.eaf.framework.persistence.eventstore

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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

        test("BRIN indexes exist on DomainEventEntry") {
            val indexes =
                jdbcTemplate.queryForList(
                    """
                    SELECT indexname, indexdef
                    FROM pg_indexes
                    WHERE tablename = 'domainevententry'
                    AND indexdef LIKE '%USING brin%'
                    ORDER BY indexname
                    """.trimIndent(),
                )

            // Verify BRIN indexes exist
            indexes.size shouldBeGreaterThan 0

            val indexNames = indexes.map { (it["indexname"] as String).lowercase() }
            indexNames.any { it.contains("timestamp") && it.contains("brin") } shouldBe true
            indexNames.any { it.contains("aggregate") && it.contains("brin") } shouldBe true
        }

        test("B-tree index exists for aggregate replay") {
            val indexes =
                jdbcTemplate.queryForList(
                    """
                    SELECT indexname, indexdef
                    FROM pg_indexes
                    WHERE tablename = 'domainevententry'
                    AND indexdef LIKE '%aggregateidentifier%'
                    AND indexdef LIKE '%sequencenumber%'
                    AND indexdef NOT LIKE '%USING brin%'
                    ORDER BY indexname
                    """.trimIndent(),
                )

            // Verify B-tree index for (aggregateIdentifier, sequenceNumber) exists
            indexes.size shouldBeGreaterThan 0
        }

        test("Uniqueness constraint prevents duplicate eventIdentifier") {
            truncateEvents()

            val aggregateId = "uniqueness-test-event"
            val eventId = UUID.randomUUID().toString()
            val timestamp = Instant.now().atOffset(ZoneOffset.UTC).toString()

            // Insert first event
            insertEventDirect(
                aggregateId = aggregateId,
                sequence = 0L,
                timestamp = Instant.parse(timestamp),
                eventIdentifier = eventId,
            )

            // Attempt to insert duplicate eventIdentifier (should fail)
            val exception =
                kotlin
                    .runCatching {
                        insertEventDirect(
                            aggregateId = "different-aggregate",
                            sequence = 0L,
                            timestamp = Instant.parse(timestamp),
                            eventIdentifier = eventId, // Same eventId
                        )
                    }.exceptionOrNull()

            exception shouldNotBe null
            (exception is org.springframework.dao.DataIntegrityViolationException) shouldBe true
        }

        test("Uniqueness constraint prevents duplicate (aggregateIdentifier, sequenceNumber)") {
            truncateEvents()

            val aggregateId = "uniqueness-test-sequence"
            val timestamp = Instant.now().atOffset(ZoneOffset.UTC).toString()

            // Insert first event
            insertEventDirect(
                aggregateId = aggregateId,
                sequence = 0L,
                timestamp = Instant.parse(timestamp),
            )

            // Attempt to insert duplicate (aggregateId, sequence) (should fail)
            val exception =
                kotlin
                    .runCatching {
                        insertEventDirect(
                            aggregateId = aggregateId,
                            sequence = 0L, // Same aggregateId + sequence
                            timestamp = Instant.parse(timestamp),
                            eventIdentifier = UUID.randomUUID().toString(), // Different eventId
                        )
                    }.exceptionOrNull()

            exception shouldNotBe null
            (exception is org.springframework.dao.DataIntegrityViolationException) shouldBe true
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

        test("Partition creation script creates new monthly partition") {
            val futureMonth = LocalDate.now(ZoneOffset.UTC).plusMonths(6)
            val monthSpec = futureMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val partitionSuffix = futureMonth.format(DateTimeFormatter.ofPattern("yyyy_MM"))

            // Execute partition creation script
            // Resolve absolute path to script from project root
            val projectRoot =
                System.getProperty("user.dir").let { workingDir ->
                    // In CI: /home/runner/work/eaf/eaf/framework/persistence
                    // Locally: /Users/michael/eaf (or similar)
                    if (workingDir.endsWith("framework/persistence")) {
                        java.io
                            .File(workingDir)
                            .parentFile.parentFile.absolutePath
                    } else {
                        workingDir
                    }
                }
            val scriptPath = "$projectRoot/scripts/create-event-store-partition.sh"
            val scriptFile = java.io.File(scriptPath)

            // Skip test if script not found (e.g., in minimal CI environments)
            if (!scriptFile.exists()) {
                println("SKIP: Script not found at $scriptPath")
                return@test
            }

            // Skip test if python3 or psql not available
            val python3Process = ProcessBuilder("which", "python3").start()
            val hasPython3 =
                python3Process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) &&
                    python3Process.exitValue() == 0

            val psqlProcess = ProcessBuilder("which", "psql").start()
            val hasPsql =
                psqlProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) &&
                    psqlProcess.exitValue() == 0

            if (!hasPython3 || !hasPsql) {
                println("SKIP: python3=$hasPython3, psql=$hasPsql (required dependencies not available)")
                return@test
            }

            val process =
                ProcessBuilder(
                    "bash",
                    scriptPath,
                    "--month",
                    monthSpec,
                    "--host",
                    postgresContainer.host,
                    "--port",
                    postgresContainer.getMappedPort(5432).toString(),
                    "--user",
                    postgresContainer.username,
                    "--dbname",
                    postgresContainer.databaseName,
                ).apply {
                    environment()["PGPASSWORD"] = postgresContainer.password
                    directory(java.io.File(projectRoot))
                    redirectErrorStream(true)
                }.start()

            val completed = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                error("Script execution timed out after 30 seconds")
            }

            val output = process.inputStream.bufferedReader().readText()
            if (process.exitValue() != 0) {
                println("Script failed with exit code ${process.exitValue()}")
                println("Script output: $output")
                error("Script execution failed. See output above.")
            }

            process.exitValue() shouldBe 0

            // Verify partition was created
            val partitionName = "domainevententry_$partitionSuffix"
            val partitionExists =
                jdbcTemplate.queryForObject(
                    """
                    SELECT EXISTS(
                        SELECT 1 FROM pg_class
                        WHERE relname = ? AND relkind = 'r'
                    )
                    """.trimIndent(),
                    Boolean::class.java,
                    partitionName,
                )

            partitionExists shouldBe true

            // Verify events can be inserted into new partition
            val futureTimestamp = futureMonth.atStartOfDay().toInstant(ZoneOffset.UTC)
            insertEventDirect(
                aggregateId = "future-partition-test",
                sequence = 0L,
                timestamp = futureTimestamp,
            )

            val routedPartition =
                jdbcTemplate.queryForObject(
                    """
                    SELECT tableoid::regclass::text
                    FROM domainevententry
                    WHERE aggregateidentifier = 'future-partition-test'
                    """.trimIndent(),
                    String::class.java,
                )

            requireNotNull(routedPartition).trim('"').lowercase() shouldBe partitionName
        }
    }

    private fun truncateEvents() {
        jdbcTemplate.execute("TRUNCATE TABLE domainevententry RESTART IDENTITY CASCADE")
    }

    private fun insertEventDirect(
        aggregateId: String,
        sequence: Long,
        timestamp: Instant,
        eventIdentifier: String = UUID.randomUUID().toString(),
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
            ps.setString(4, eventIdentifier)
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
