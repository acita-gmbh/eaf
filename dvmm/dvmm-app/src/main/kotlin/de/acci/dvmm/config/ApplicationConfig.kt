package de.acci.dvmm.config

import com.fasterxml.jackson.databind.ObjectMapper
import de.acci.dvmm.application.vmrequest.CreateVmRequestHandler
import de.acci.eaf.eventsourcing.EventStore
import de.acci.eaf.eventsourcing.EventStoreObjectMapper
import de.acci.eaf.eventsourcing.PostgresEventStore
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * Spring configuration for DVMM application beans.
 *
 * Wires up the application layer handlers with their infrastructure dependencies.
 * Following hexagonal architecture, this is the composition root where
 * ports (interfaces) are connected to adapters (implementations).
 */
@Configuration
public class ApplicationConfig {

    /**
     * jOOQ DSLContext for type-safe SQL operations.
     *
     * Uses PostgreSQL dialect and the Spring-managed DataSource.
     *
     * @param dataSource Spring-managed DataSource (auto-configured by Spring Boot)
     */
    @Bean
    public fun dslContext(dataSource: DataSource): DSLContext =
        DSL.using(dataSource, SQLDialect.POSTGRES)

    /**
     * ObjectMapper configured for event store serialization.
     *
     * Handles Kotlin value classes and EAF core types (TenantId, UserId, etc.).
     */
    @Bean
    public fun eventStoreObjectMapper(): ObjectMapper = EventStoreObjectMapper.create()

    /**
     * PostgreSQL-based event store implementation.
     *
     * @param dsl jOOQ DSLContext for database operations
     * @param objectMapper Jackson ObjectMapper for JSON serialization
     */
    @Bean
    public fun eventStore(dsl: DSLContext, objectMapper: ObjectMapper): EventStore =
        PostgresEventStore(dsl, objectMapper)

    /**
     * Handler for creating VM requests.
     *
     * @param eventStore Event store for persisting domain events
     */
    @Bean
    public fun createVmRequestHandler(eventStore: EventStore): CreateVmRequestHandler =
        CreateVmRequestHandler(eventStore)
}
