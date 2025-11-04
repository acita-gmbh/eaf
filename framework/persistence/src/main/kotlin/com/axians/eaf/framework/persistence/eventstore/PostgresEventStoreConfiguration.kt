package com.axians.eaf.framework.persistence.eventstore

import com.fasterxml.jackson.databind.ObjectMapper
import org.axonframework.common.transaction.NoTransactionManager
import org.axonframework.eventsourcing.eventstore.EventStorageEngine
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine
import org.axonframework.serialization.Serializer
import org.axonframework.serialization.json.JacksonSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

/**
 * PostgreSQL Event Store Configuration for Axon Framework.
 *
 * Configures JdbcEventStorageEngine to persist domain events in PostgreSQL using the
 * standard Axon event store schema. This configuration provides durable event storage
 * with full ACID guarantees.
 *
 * **Architecture Notes:**
 * - Uses JdbcEventStorageEngine for direct JDBC access to event store tables
 * - NoTransactionManager delegates transaction management to Spring's @Transactional
 * - Event store schema created and managed via Flyway migrations
 *
 * @see org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine
 * @see com.axians.eaf.framework.persistence.migration.V001__event_store_schema
 */
@Configuration
open class PostgresEventStoreConfiguration {
    /**
     * Creates a Jackson-based Serializer for Axon Framework event serialization.
     *
     * Uses JacksonSerializer for JSON-based event serialization instead of the default
     * XStreamSerializer. This provides better security, performance, and Kotlin compatibility.
     *
     * @return Serializer for event and message serialization
     */
    @Bean
    @Primary
    open fun serializer(): Serializer {
        val objectMapper = ObjectMapper().findAndRegisterModules()
        return JacksonSerializer
            .builder()
            .objectMapper(objectMapper)
            .build()
    }

    /**
     * Creates the Axon EventStorageEngine using PostgreSQL JDBC.
     *
     * The JdbcEventStorageEngine provides low-level access to the event store tables:
     * - domain_event_entry: All domain events
     * - snapshot_entry: Aggregate snapshots (configured in Story 2.4)
     *
     * Transaction management is delegated to Spring via NoTransactionManager.
     * Event serialization uses the configured JacksonSerializer for JSON storage.
     *
     * @param dataSource PostgreSQL DataSource configured by Spring Boot
     * @param serializer Jackson-based serializer for event payload and metadata
     * @return EventStorageEngine for event persistence
     */
    @Bean
    open fun eventStorageEngine(
        dataSource: DataSource,
        serializer: Serializer,
    ): EventStorageEngine =
        JdbcEventStorageEngine
            .builder()
            .connectionProvider(dataSource::getConnection)
            .transactionManager(NoTransactionManager.INSTANCE)
            .snapshotSerializer(serializer)
            .eventSerializer(serializer)
            .build()

    // Note: TokenStore configuration will be added in a future story
    // when event processors are implemented.
}
