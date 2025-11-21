package com.axians.eaf.framework.persistence.eventstore

import org.assertj.core.api.Assertions.assertThat
import org.axonframework.eventhandling.DomainEventMessage
import org.axonframework.eventhandling.GenericDomainEventMessage
import org.axonframework.eventsourcing.eventstore.EventStorageEngine
import org.axonframework.messaging.MetaData
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/**
 * Integration test for PostgreSQL Event Store configuration.
 *
 * Verifies that:
 * - Flyway migrations create Axon event store tables correctly
 * - JdbcEventStorageEngine can persist and retrieve domain events
 * - PostgreSQL Testcontainer provides real database for integration testing
 *
 * **Test Strategy:**
 * - Uses JUnit 6 with AssertJ
 * - Uses @SpringBootTest with field injection
 * - Uses Testcontainers PostgreSQL for real database (H2 forbidden)
 * - Follows Constitutional TDD approach
 *
 * @see PostgresEventStoreConfiguration
 * @see com.axians.eaf.framework.persistence.migration.V001__event_store_schema
 */
@SpringBootTest(classes = [EventStoreIntegrationTest.TestConfiguration::class])
class EventStoreIntegrationTest {
    @Autowired
    private lateinit var eventStorageEngine: EventStorageEngine

    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    fun `Flyway migration should create event store tables`() {
        // Verify Flyway executed successfully
        val flyway =
            Flyway
                .configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()

        val info = flyway.info()
        val appliedMigrations = info.all().filter { it.state.isApplied }

        assertThat(appliedMigrations).isNotEmpty
        assertThat(appliedMigrations.any { it.version == MigrationVersion.fromVersion("1") }).isTrue()
    }

    @Test
    fun `EventStorageEngine should store and retrieve domain events`() {
        // Given: A domain event
        val aggregateId = UUID.randomUUID().toString()
        val eventId = UUID.randomUUID()
        val eventPayload =
            TestEventPayload(
                eventId = eventId,
                message = "Test event for event store",
            )

        val domainEvent: DomainEventMessage<TestEventPayload> =
            GenericDomainEventMessage(
                "TestAggregate", // type
                aggregateId, // aggregateIdentifier
                0L, // sequenceNumber
                eventPayload, // payload
                MetaData.with("userId", "test-user"),
            )

        // When: Storing the event
        eventStorageEngine.appendEvents(domainEvent)

        // Then: Event can be retrieved
        val storedEvents =
            eventStorageEngine
                .readEvents(aggregateId)
                .asStream()
                .toList()

        assertThat(storedEvents).hasSize(1)
        val storedEvent = storedEvents[0]
        assertThat(storedEvent.aggregateIdentifier).isEqualTo(aggregateId)
        assertThat(storedEvent.sequenceNumber).isEqualTo(0L)
        assertThat(storedEvent.type).isEqualTo("TestAggregate")

        val storedPayload = storedEvent.payload as TestEventPayload
        assertThat(storedPayload.eventId).isEqualTo(eventId)
        assertThat(storedPayload.message).isEqualTo("Test event for event store")
    }

    @Test
    fun `EventStorageEngine should store multiple events for same aggregate`() {
        // Given: Multiple events for the same aggregate
        val aggregateId = UUID.randomUUID().toString()

        val event1 =
            GenericDomainEventMessage<TestEventPayload>(
                "TestAggregate",
                aggregateId,
                0L,
                TestEventPayload(UUID.randomUUID(), "First event"),
            )

        val event2 =
            GenericDomainEventMessage<TestEventPayload>(
                "TestAggregate",
                aggregateId,
                1L,
                TestEventPayload(UUID.randomUUID(), "Second event"),
            )

        val event3 =
            GenericDomainEventMessage<TestEventPayload>(
                "TestAggregate",
                aggregateId,
                2L,
                TestEventPayload(UUID.randomUUID(), "Third event"),
            )

        // When: Storing multiple events
        eventStorageEngine.appendEvents(event1, event2, event3)

        // Then: All events can be retrieved in correct order
        val storedEvents =
            eventStorageEngine
                .readEvents(aggregateId)
                .asStream()
                .toList()

        assertThat(storedEvents).hasSize(3)
        assertThat(storedEvents[0].sequenceNumber).isEqualTo(0L)
        assertThat(storedEvents[1].sequenceNumber).isEqualTo(1L)
        assertThat(storedEvents[2].sequenceNumber).isEqualTo(2L)

        assertThat((storedEvents[0].payload as TestEventPayload).message).isEqualTo("First event")
        assertThat((storedEvents[1].payload as TestEventPayload).message).isEqualTo("Second event")
        assertThat((storedEvents[2].payload as TestEventPayload).message).isEqualTo("Third event")
    }

    @Test
    fun `EventStorageEngine should handle metadata correctly`() {
        // Given: An event with metadata
        val aggregateId = UUID.randomUUID().toString()
        val metadata =
            MetaData
                .with("userId", "test-user")
                .and("correlationId", "test-correlation-123")
                .and("timestamp", Instant.now().toString())

        val domainEvent =
            GenericDomainEventMessage(
                "TestAggregate",
                aggregateId,
                0L,
                TestEventPayload(UUID.randomUUID(), "Event with metadata"),
                metadata,
            )

        // When: Storing the event
        eventStorageEngine.appendEvents(domainEvent)

        // Then: Metadata is preserved
        val storedEvents =
            eventStorageEngine
                .readEvents(aggregateId)
                .asStream()
                .toList()

        assertThat(storedEvents).hasSize(1)
        val storedMetadata = storedEvents[0].metaData
        assertThat(storedMetadata["userId"]).isEqualTo("test-user")
        assertThat(storedMetadata["correlationId"]).isEqualTo("test-correlation-123")
        assertThat(storedMetadata["timestamp"]).isNotNull()
    }

    /**
     * Test event payload for integration testing.
     */
    data class TestEventPayload(
        val eventId: UUID,
        val message: String,
    )

    /**
     * Minimal Spring Boot test configuration.
     * Imports PostgresEventStoreConfiguration to test event store setup.
     */
    @Configuration
    @EnableAutoConfiguration
    @Import(PostgresEventStoreConfiguration::class)
    open class TestConfiguration

    companion object {
        /**
         * PostgreSQL Testcontainer for integration testing.
         * Singleton pattern - started once and reused across all tests.
         */
        private val postgresContainer =
            PostgreSQLContainer(
                DockerImageName.parse("postgres:16.10"),
            ).apply {
                withDatabaseName("eaf_test")
                withUsername("test_user")
                withPassword("test_pass")
                start()
            }

        /**
         * Configures Spring Boot DataSource properties from Testcontainer.
         * Flyway will automatically run migrations on this test database.
         */
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgresContainer.jdbcUrl }
            registry.add("spring.datasource.username") { postgresContainer.username }
            registry.add("spring.datasource.password") { postgresContainer.password }
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }

            // Enable Flyway for test database
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("spring.flyway.locations") { "classpath:db/migration" }
            registry.add("spring.flyway.baseline-on-migrate") { "true" }
        }
    }
}
