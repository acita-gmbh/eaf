package com.axians.eaf.framework.multitenancy.test

import org.axonframework.commandhandling.CommandBus
import org.axonframework.commandhandling.SimpleCommandBus
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.commandhandling.gateway.DefaultCommandGateway
import org.axonframework.config.Configurer
import org.axonframework.config.ConfigurerModule
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore
import org.axonframework.eventsourcing.eventstore.EventStore
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Test application for TenantValidationInterceptor integration tests.
 *
 * Configures a minimal Axon Framework setup with:
 * - In-memory event store (no PostgreSQL required)
 * - SimpleCommandBus (no Axon Server required)
 * - Subscribing event processors (synchronous for fast tests)
 * - TestAggregate for command validation testing
 * - TenantValidationInterceptor (auto-discovered via ComponentScan)
 *
 * Epic 4, Story 4.3: Test Infrastructure
 */
@SpringBootApplication(
    exclude = [
        DataSourceAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration::class,
        org.axonframework.springboot.autoconfig.AxonServerBusAutoConfiguration::class,
        org.axonframework.springboot.autoconfig.AxonServerActuatorAutoConfiguration::class,
    ],
)
@ComponentScan(
    basePackages = [
        "com.axians.eaf.framework.multitenancy", // Scan interceptor
        "com.axians.eaf.framework.multitenancy.test", // Scan test classes
    ],
)
open class TenantValidationTestApplication

/**
 * Axon configuration for tenant validation integration tests.
 *
 * Uses local SimpleCommandBus instead of Axon Server for fast, isolated testing.
 */
@Configuration(proxyBeanMethods = false)
open class TenantValidationTestAxonConfig {
    /**
     * In-memory event store for testing (no database required).
     */
    @Bean
    @Primary
    open fun eventStore(): EventStore =
        EmbeddedEventStore
            .builder()
            .storageEngine(InMemoryEventStorageEngine())
            .build()

    /**
     * Simple command bus for local command dispatch (no Axon Server).
     */
    @Bean
    @Primary
    open fun commandBus(): CommandBus = SimpleCommandBus.builder().build()

    /**
     * Command gateway using local SimpleCommandBus.
     */
    @Bean
    @Primary
    open fun commandGateway(commandBus: CommandBus): CommandGateway =
        DefaultCommandGateway
            .builder()
            .commandBus(commandBus)
            .build()

    /**
     * Configure event processors in subscribing mode for synchronous testing.
     */
    @Bean
    open fun eventProcessorConfigurer(): ConfigurerModule =
        ConfigurerModule { configurer: Configurer ->
            configurer
                .eventProcessing()
                .usingSubscribingEventProcessors()
        }
}
