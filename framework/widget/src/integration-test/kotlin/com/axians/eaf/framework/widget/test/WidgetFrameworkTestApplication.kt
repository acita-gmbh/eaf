package com.axians.eaf.framework.widget.test

import com.axians.eaf.framework.security.tenant.TenantContext
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.axonframework.commandhandling.CommandBus
import org.axonframework.commandhandling.SimpleCommandBus
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.commandhandling.gateway.DefaultCommandGateway
import org.axonframework.config.EventProcessingConfiguration
import org.axonframework.eventhandling.EventBus
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore
import org.axonframework.eventsourcing.eventstore.EventStore
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * Test-only Spring Boot application for framework integration tests.
 *
 * Provides minimal Axon infrastructure (in-memory event store, command gateway)
 * without requiring full product-level setup (DataSource, JPA, OAuth2).
 *
 * Used by: Story 4.4 E2E tests validating tenant context propagation
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.axians.eaf.framework.widget.domain",
        "com.axians.eaf.framework.widget.projections",
        "com.axians.eaf.framework.security.tenant",
        "com.axians.eaf.framework.cqrs.config",
        "com.axians.eaf.framework.cqrs.interceptors",
        "com.axians.eaf.framework.core",
        "org.axonframework.springboot.autoconfig",
    ],
    exclude = [
        // DataSourceAutoConfiguration - INCLUDED for E2E tests with PostgreSQL Testcontainers
        // HibernateJpaAutoConfiguration - INCLUDED for JPA repositories
        org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration::class,
        org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration::class,
        org.axonframework.springboot.autoconfig.AxonServerBusAutoConfiguration::class,
    ],
)
open class WidgetFrameworkTestApplication {
    @Bean
    @Primary
    open fun testMeterRegistry(): MeterRegistry = SimpleMeterRegistry()

    @Bean
    @Primary
    open fun testTenantContext(meterRegistry: MeterRegistry): TenantContext = TenantContext(meterRegistry)

    @Bean
    @Primary
    open fun testCommandBus(): CommandBus = SimpleCommandBus.builder().build()

    @Bean
    @Primary
    open fun testCommandGateway(commandBus: CommandBus): CommandGateway =
        DefaultCommandGateway.builder().commandBus(commandBus).build()

    @Bean
    @Primary
    open fun testEventStore(): EventStore =
        EmbeddedEventStore.builder()
            .storageEngine(InMemoryEventStorageEngine())
            .build()
}
