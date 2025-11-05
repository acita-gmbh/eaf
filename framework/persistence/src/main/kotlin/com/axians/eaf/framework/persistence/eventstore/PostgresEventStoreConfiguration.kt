package com.axians.eaf.framework.persistence.eventstore

import com.fasterxml.jackson.databind.ObjectMapper
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper
import org.axonframework.eventsourcing.eventstore.EventStorageEngine
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine
import org.axonframework.serialization.Serializer
import org.axonframework.serialization.json.JacksonSerializer
import org.axonframework.spring.jdbc.SpringDataSourceConnectionProvider
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.transaction.PlatformTransactionManager
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
     * Creates a Spring-aware ConnectionProvider for Axon Framework.
     *
     * Uses SpringDataSourceConnectionProvider to ensure connections are retrieved via
     * Spring's DataSourceUtils.getConnection(), which reuses the transaction-bound
     * connection instead of obtaining a new one from the pool. This is critical for
     * transactional consistency.
     *
     * Wrapped with UnitOfWorkAwareConnectionProviderWrapper per Axon best practices
     * to ensure connection reuse within a single Unit of Work.
     *
     * @param dataSource PostgreSQL DataSource configured by Spring Boot
     * @return ConnectionProvider that participates in Spring transactions
     */
    @Bean
    open fun connectionProvider(dataSource: DataSource): ConnectionProvider =
        UnitOfWorkAwareConnectionProviderWrapper(
            SpringDataSourceConnectionProvider(dataSource),
        )

    /**
     * Creates the Axon EventStorageEngine using PostgreSQL JDBC.
     *
     * The JdbcEventStorageEngine provides low-level access to the event store tables:
     * - DomainEventEntry: All domain events
     * - SnapshotEventEntry: Aggregate snapshots (configured in Story 2.4)
     *
     * Transaction management explicitly integrates with Spring's PlatformTransactionManager
     * via SpringTransactionManager. This ensures event persistence participates in the
     * surrounding @Transactional context, providing atomicity for command handling operations.
     *
     * Connection management uses SpringDataSourceConnectionProvider to reuse the
     * transaction-bound connection instead of obtaining independent connections from
     * the pool. This prevents events from being committed on separate connections,
     * ensuring rollback consistency.
     *
     * If command handler logic fails after event application, the Spring transaction rollback
     * will also rollback the event persistence, preventing inconsistent aggregate state.
     *
     * @param connectionProvider Spring-aware connection provider for transaction participation
     * @param serializer Jackson-based serializer for event payload and metadata
     * @param platformTransactionManager Spring's transaction manager for ACID guarantees
     * @return EventStorageEngine for event persistence
     */
    @Bean
    open fun eventStorageEngine(
        connectionProvider: ConnectionProvider,
        serializer: Serializer,
        platformTransactionManager: PlatformTransactionManager,
    ): EventStorageEngine =
        JdbcEventStorageEngine
            .builder()
            .connectionProvider(connectionProvider)
            .transactionManager(SpringTransactionManager(platformTransactionManager))
            .snapshotSerializer(serializer)
            .eventSerializer(serializer)
            .build()

    // Note: TokenStore configuration will be added in a future story
    // when event processors are implemented.
}
