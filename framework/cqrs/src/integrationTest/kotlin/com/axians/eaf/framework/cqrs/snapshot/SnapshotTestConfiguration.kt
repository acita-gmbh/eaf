package com.axians.eaf.framework.cqrs.snapshot

import com.axians.eaf.framework.cqrs.config.AxonConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Test configuration for snapshot integration tests
 *
 * Enables:
 * - Axon Framework auto-configuration (EventStore, CommandBus, etc.)
 * - Spring Boot auto-configuration (DataSource, Flyway, etc.)
 * - AxonConfiguration (CommandGateway, QueryGateway, Snapshot beans)
 * - TestAggregate scanning
 */
@Configuration
@EnableAutoConfiguration
@Import(AxonConfiguration::class)
@ComponentScan(basePackageClasses = [TestAggregate::class])
class SnapshotTestConfiguration
