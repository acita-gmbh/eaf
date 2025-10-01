package com.axians.eaf.framework.workflow.delegates

import com.axians.eaf.framework.observability.metrics.CustomMetrics
import com.axians.eaf.framework.security.tenant.TenantContext
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.axonframework.eventsourcing.eventstore.EventStorageEngine
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * Test configuration for Axon integration tests (Story 6.2).
 *
 * Provides minimal beans required for testing Flowable-to-Axon bridge:
 * - InMemoryEventStorageEngine: Lightweight, in-memory Axon event store (no database)
 * - MeterRegistry: Required by various metrics-aware components
 * - TenantContext: Required by DispatchAxonCommandTask for tenant isolation
 *
 * This configuration works with axon.axonserver.enabled=false to get fully in-memory
 * Axon infrastructure (SimpleCommandBus, InMemoryEventStorageEngine).
 *
 * Research: 4 external AI sources unanimously recommend this pattern for Axon testing.
 */
@TestConfiguration
open class AxonIntegrationTestConfig {
    /**
     * Provides in-memory Axon event storage (real Axon class, not a mock).
     * Combined with axon.axonserver.enabled=false, this gives us lightweight Axon infrastructure.
     */
    @Bean
    @Primary
    open fun eventStorageEngine(): EventStorageEngine = InMemoryEventStorageEngine()

    /**
     * Provides SimpleMeterRegistry for testing (avoids framework.observability dependency).
     */
    @Bean
    open fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()

    /**
     * Provides TenantContext bean for testing with SimpleMeterRegistry.
     * Tests can manually set tenant ID using tenantContext.setCurrentTenantId().
     */
    @Bean
    open fun tenantContext(meterRegistry: MeterRegistry): TenantContext = TenantContext(meterRegistry)

    /**
     * Provides CustomMetrics for TenantEventMessageInterceptor (Story 6.3).
     * Real implementation with SimpleMeterRegistry (no external dependencies).
     */
    @Bean
    open fun customMetrics(meterRegistry: MeterRegistry, tenantContext: TenantContext): CustomMetrics =
        CustomMetrics(meterRegistry, tenantContext)
}
