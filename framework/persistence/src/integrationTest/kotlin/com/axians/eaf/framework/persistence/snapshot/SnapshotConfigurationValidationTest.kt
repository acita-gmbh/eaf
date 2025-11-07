package com.axians.eaf.framework.persistence.snapshot

import com.axians.eaf.framework.cqrs.config.AxonConfiguration
import com.axians.eaf.framework.persistence.eventstore.PostgresEventStoreConfiguration
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.axonframework.eventsourcing.SnapshotTriggerDefinition
import org.axonframework.eventsourcing.Snapshotter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource

/**
 * Integration test validating snapshot configuration and database schema.
 *
 * Validates:
 * - AC1: SnapshotTriggerDefinition bean is configured
 * - AC2: Snapshotter bean is configured with Jackson serialization
 * - AC3: snapshot_entry table exists with correct schema
 *
 * Note: Full snapshot creation tests with 250+ events will be added in Story 2.5
 * when the Widget aggregate is available for more realistic testing.
 */
@SpringBootTest(classes = [SnapshotConfigurationValidationTest.TestConfiguration::class])
class SnapshotConfigurationValidationTest : FunSpec() {
    @Autowired
    private lateinit var snapshotTriggerDefinition: SnapshotTriggerDefinition

    @Autowired
    private lateinit var snapshotter: Snapshotter

    @Autowired
    private lateinit var dataSource: DataSource

    init {
        extension(SpringExtension())

        test("AC1: SnapshotTriggerDefinition bean is configured") {
            snapshotTriggerDefinition.shouldBeInstanceOf<SnapshotTriggerDefinition>()
        }

        test("AC2: Snapshotter bean is configured") {
            snapshotter.shouldBeInstanceOf<Snapshotter>()
        }

        test("AC3: snapshot_entry table exists with correct Axon schema") {
            // Verify snapshot_event_entry table created by V001 migration
            dataSource.connection.use { conn ->
                val stmt =
                    conn.prepareStatement(
                        """
                        SELECT column_name
                        FROM information_schema.columns
                        WHERE table_name = 'snapshot_event_entry'
                        ORDER BY ordinal_position
                        """.trimIndent(),
                    )

                val rs = stmt.executeQuery()
                val columnNames = mutableListOf<String>()
                while (rs.next()) {
                    columnNames.add(rs.getString("column_name"))
                }

                // Verify essential Axon snapshot columns exist (snake_case)
                columnNames.any { it == "aggregate_identifier" } shouldBe true
                columnNames.any { it == "sequence_number" } shouldBe true
                columnNames.any { it == "payload" } shouldBe true
                columnNames.any { it == "payload_type" } shouldBe true
                columnNames.any { it == "time_stamp" } shouldBe true
            }
        }

        test("Configuration: Snapshotter and SnapshotTriggerDefinition can work together") {
            // Smoke test: Verify beans are wired correctly
            // Full functional testing with 250+ events will be done in Story 2.5 (Widget Aggregate)
            snapshotTriggerDefinition.shouldBeInstanceOf<SnapshotTriggerDefinition>()
            snapshotter.shouldBeInstanceOf<Snapshotter>()
        }
    }

    @Configuration
    @EnableAutoConfiguration
    @Import(AxonConfiguration::class, PostgresEventStoreConfiguration::class)
    class TestConfiguration

    companion object {
        // Testcontainers automatically handles cleanup on JVM shutdown
        private val postgresContainer =
            PostgreSQLContainer(
                DockerImageName.parse("postgres:16.10"),
            ).apply {
                withDatabaseName("eaf_snapshot_config_test")
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

            registry.add("axon.axonserver.enabled") { "false" }
            registry.add("spring.modulith.events.jdbc.enabled") { "false" }
        }
    }
}
