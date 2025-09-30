package com.axians.eaf.framework.cqrs.config

import com.axians.eaf.framework.cqrs.interceptors.CommandMetricsInterceptor
import com.axians.eaf.framework.cqrs.interceptors.TenantCorrelationDataProvider
import com.axians.eaf.framework.cqrs.interceptors.TenantEventMessageInterceptor
import com.axians.eaf.framework.cqrs.interceptors.TracingCommandInterceptor
import com.axians.eaf.framework.cqrs.interceptors.TracingEventInterceptor
import org.axonframework.config.Configurer
import org.axonframework.config.EventProcessingConfigurer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/**
 * Spring Boot auto-configuration for EAF tenant context propagation in Axon event processing.
 *
 * ## Auto-Configuration Behavior
 *
 * This configuration is automatically applied when:
 * - The `framework-cqrs` module is on the classpath
 * - Axon Framework's `Configurer` class is available
 * - The property `eaf.cqrs.tenant-propagation.enabled` is not set to `false`
 *
 * **Discovery**: Registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
 *
 * **Disable**: Set `eaf.cqrs.tenant-propagation.enabled=false` in application.yml
 *
 * ## What This Configures
 *
 * Registers framework-level components for multi-tenant async event processing:
 * - TenantEventMessageInterceptor: Propagates tenant context to @EventHandler methods
 * - TenantCorrelationDataProvider: Enriches events with tenantId metadata
 *
 * ## Tenant Context Propagation (Story 4.4)
 *
 * Ensures tenant context flows from synchronous command handlers to asynchronous
 * tracking event processors, enabling RLS (Row-Level Security) enforcement for
 * projection writes to PostgreSQL.
 *
 * **Event Flow**:
 * 1. Command handler publishes event with TenantContext active
 * 2. TenantCorrelationDataProvider enriches event metadata with tenantId
 * 3. Tracking processor receives event asynchronously
 * 4. TenantEventMessageInterceptor restores TenantContext from metadata
 * 5. Projection handler writes to database with RLS session variable set
 *
 * ## Architecture Pattern
 *
 * This follows the Spring Boot Starter pattern for framework libraries:
 * - Framework module provides auto-configuration
 * - Product applications auto-discover via classpath scanning
 * - No manual configuration required in products
 * - Consistent behavior across all products
 *
 * ## Micrometer Context Propagation (Optional)
 *
 * For saga timeouts or scheduled tasks requiring tenant context, integrate with
 * Micrometer Context Propagation by enabling in application.yml:
 *
 * ```yaml
 * management:
 *   metrics:
 *     context-propagation:
 *       enabled: true
 * ```
 *
 * @see TenantEventMessageInterceptor Thread-safe tenant context propagation
 * @see TenantCorrelationDataProvider Event metadata enrichment
 */
@AutoConfiguration
@ConditionalOnClass(Configurer::class)
@ConditionalOnProperty(
    prefix = "eaf.cqrs.tenant-propagation",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class AxonConfiguration {
    /**
     * Registers TracingCommandInterceptor for command dispatch trace context injection.
     *
     * **Execution Order**: Registered BEFORE tenant validation to ensure trace context
     * is available during tenant isolation checks.
     *
     * Story 5.3: Trace context propagation for Axon commands
     *
     * @param configurer Axon framework configurer
     * @param tracingCommandInterceptor Tracing interceptor bean (conditionally created)
     */
    @Autowired(required = false)
    fun configureCommandTracing(
        configurer: Configurer,
        tracingCommandInterceptor: TracingCommandInterceptor?,
    ) {
        tracingCommandInterceptor?.let { interceptor ->
            configurer.onInitialize { config ->
                config.commandBus().registerDispatchInterceptor(interceptor)
            }
        }
    }

    /**
     * Registers TracingEventInterceptor for event handler trace context restoration.
     *
     * **Execution Order**: Registered BEFORE tenant propagation to ensure trace spans
     * are active when tenant validation occurs.
     *
     * **CRITICAL**: This interceptor mitigates TECH-001 risk (async trace context loss).
     *
     * Story 5.3: Async trace propagation for event handlers
     *
     * @param eventProcessingConfigurer Axon event processing configuration
     * @param tracingEventInterceptor Tracing interceptor bean (conditionally created)
     */
    @Autowired(required = false)
    fun configureEventTracing(
        eventProcessingConfigurer: EventProcessingConfigurer,
        tracingEventInterceptor: TracingEventInterceptor?,
    ) {
        tracingEventInterceptor?.let { interceptor ->
            eventProcessingConfigurer.registerDefaultHandlerInterceptor { config, processorName ->
                interceptor
            }
        }
    }

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

    @Autowired
    fun configureCommandMetrics(
        configurer: Configurer,
        commandMetricsInterceptor: CommandMetricsInterceptor,
    ) {
        configurer.onInitialize { config ->
            config.commandBus().registerHandlerInterceptor(commandMetricsInterceptor)
        }
    }
}
