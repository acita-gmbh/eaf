package com.axians.eaf.framework.cqrs.config

import com.axians.eaf.framework.cqrs.interceptors.TenantCorrelationDataProvider
import com.axians.eaf.framework.cqrs.interceptors.TenantEventMessageInterceptor
import org.axonframework.config.Configurer
import org.axonframework.config.EventProcessingConfigurer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration

/**
 * Axon Framework configuration for CQRS/ES infrastructure.
 *
 * Centralizes:
 * - Event processing configuration (tracking processors, interceptors)
 * - Command gateway configuration
 * - Message handler interceptor registration
 *
 * ## Tenant Context Propagation (Story 4.4)
 *
 * Registers TenantEventMessageInterceptor for all tracking event processors to ensure
 * tenant context is propagated to asynchronous @EventHandler methods before execution.
 *
 * This enables downstream RLS (Row-Level Security) enforcement by populating TenantContext
 * ThreadLocal before projection handlers write to PostgreSQL with RLS policies.
 *
 * ## Micrometer Context Propagation Bridge (Subtask 2.2)
 *
 * **Configuration for Async Task Scheduling**:
 *
 * For scenarios where Axon scheduled tasks need tenant context (e.g., saga timeouts,
 * deadline handlers), integrate with Micrometer Context Propagation:
 *
 * ```yaml
 * # application.yml
 * management:
 *   metrics:
 *     context-propagation:
 *       enabled: true
 * ```
 *
 * ```kotlin
 * @Configuration
 * class MicrometerConfiguration {
 *     @Bean
 *     fun tenantContextPropagator(tenantContext: TenantContext): ContextSnapshot {
 *         return ContextSnapshot.of("tenant", tenantContext.current())
 *     }
 * }
 * ```
 *
 * **Note**: Current implementation focuses on tracking event processor propagation (AC 1-4).
 * Micrometer bridge for scheduled tasks is optional enhancement for future stories.
 *
 * @see TenantEventMessageInterceptor Thread-safe tenant context propagation
 */
@Configuration
class AxonConfiguration {
    /**
     * Registers TenantEventMessageInterceptor for all tracking event processors.
     *
     * **Configuration Pattern**: Uses EventProcessingConfigurer to register interceptor
     * globally for all processing groups (starting with "widget-projection").
     *
     * **Execution Order**:
     * 1. Event published by command handler with tenantId metadata
     * 2. Tracking processor receives event
     * 3. **TenantEventMessageInterceptor executes** (sets TenantContext ThreadLocal)
     * 4. @EventHandler method executes (can access TenantContext.getCurrentTenantId())
     * 5. Database interceptor reads TenantContext, sets PostgreSQL session variable
     * 6. Repository write succeeds (passes RLS check)
     * 7. **TenantEventMessageInterceptor cleanup** (clears ThreadLocal in finally block)
     *
     * @param eventProcessingConfigurer Axon's event processing configuration hook
     * @param tenantEventInterceptor Interceptor bean from Spring context
     */
    @Autowired
    fun configureTenantPropagation(
        eventProcessingConfigurer: EventProcessingConfigurer,
        tenantEventInterceptor: TenantEventMessageInterceptor,
    ) {
        eventProcessingConfigurer.registerDefaultHandlerInterceptor { config, processorName ->
            tenantEventInterceptor
        }
    }

    /**
     * Registers TenantCorrelationDataProvider to enrich event metadata with tenantId.
     *
     * **Metadata Flow**:
     * 1. Command handler publishes event via `apply(event)`
     * 2. **This provider executes**: Reads TenantContext, adds `tenantId` to event metadata
     * 3. Event persisted to event store with metadata
     * 4. Tracking processor receives event with metadata
     * 5. TenantEventMessageInterceptor reads metadata, restores TenantContext
     *
     * @param configurer Axon framework configurer for global registration
     * @param correlationDataProvider Provider bean from Spring context
     */
    @Autowired
    fun configureMetadataEnrichment(
        configurer: Configurer,
        correlationDataProvider: TenantCorrelationDataProvider,
    ) {
        configurer.onInitialize { config ->
            config.correlationDataProviders().add(correlationDataProvider)
        }
    }
}
