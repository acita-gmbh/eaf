package com.axians.eaf.framework.persistence.eventstore

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.axonframework.eventhandling.DomainEventMessage
import org.axonframework.eventhandling.GenericDomainEventMessage
import org.axonframework.eventsourcing.eventstore.EventStorageEngine
import org.axonframework.messaging.MetaData
import org.flywaydb.core.Flyway
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
 * - Uses Kotest FunSpec (JUnit forbidden per EAF standards)
 * - Uses @SpringBootTest with field injection + init block pattern
 * - Uses Testcontainers PostgreSQL for real database (H2 forbidden)
 * - Follows Constitutional TDD approach
 *
 * @see PostgresEventStoreConfiguration
 * @see com.axians.eaf.framework.persistence.migration.V001__event_store_schema
 */
@SpringBootTest(classes = [EventStoreIntegrationTest.TestConfiguration::class])
class EventStoreIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var eventStorageEngine: EventStorageEngine

    @Autowired
    private lateinit var dataSource: DataSource

    init {
        extension(SpringExtension())

        test("Flyway migration should create event store tables") {
            // Verify Flyway executed successfully
            val flyway =
                Flyway
                    .configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()

            val info = flyway.info()
            val appliedMigrations = info.all().filter { it.state.isApplied }

            appliedMigrations shouldNotBe emptyList<Any>()
            appliedMigrations.any { it.version.version == "1" } shouldBe true
        }

        test("EventStorageEngine should store and retrieve domain events") {
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

            storedEvents shouldHaveSize 1
            val storedEvent = storedEvents[0]
            storedEvent.aggregateIdentifier shouldBe aggregateId
            storedEvent.sequenceNumber shouldBe 0L
            storedEvent.type shouldBe "TestAggregate"

            val storedPayload = storedEvent.payload as TestEventPayload
            storedPayload.eventId shouldBe eventId
            storedPayload.message shouldBe "Test event for event store"
        }

        test("EventStorageEngine should store multiple events for same aggregate") {
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

            storedEvents shouldHaveSize 3
            storedEvents[0].sequenceNumber shouldBe 0L
            storedEvents[1].sequenceNumber shouldBe 1L
            storedEvents[2].sequenceNumber shouldBe 2L

            (storedEvents[0].payload as TestEventPayload).message shouldBe "First event"
            (storedEvents[1].payload as TestEventPayload).message shouldBe "Second event"
            (storedEvents[2].payload as TestEventPayload).message shouldBe "Third event"
        }

        test("EventStorageEngine should handle metadata correctly") {
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

            storedEvents shouldHaveSize 1
            val storedMetadata = storedEvents[0].metaData
            storedMetadata["userId"] shouldBe "test-user"
            storedMetadata["correlationId"] shouldBe "test-correlation-123"
            storedMetadata["timestamp"] shouldNotBe null
        }
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
