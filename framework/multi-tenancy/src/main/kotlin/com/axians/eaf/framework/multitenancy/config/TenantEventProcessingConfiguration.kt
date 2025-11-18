package com.axians.eaf.framework.multitenancy.config

import com.axians.eaf.framework.multitenancy.TenantContext
import com.axians.eaf.framework.multitenancy.TenantContextEventInterceptor
import org.axonframework.config.EventProcessingConfigurer
import org.axonframework.messaging.Message
import org.axonframework.messaging.correlation.CorrelationDataProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for tenant context propagation to async Axon event processors.
 *
 * **Context Propagation Pattern:**
 * 1. **Enrichment (CorrelationDataProvider):** Automatically add tenant_id to event metadata
 * 2. **Restoration (TenantContextEventInterceptor):** Extract tenant_id and restore ThreadLocal
 * 3. **Cleanup:** Clear ThreadLocal after event handler execution
 *
 * **Why This is Necessary:**
 * - TrackingEventProcessor uses dedicated threads for async event processing
 * - ThreadLocal context is NOT propagated across thread boundaries
 * - Event metadata carries context from command thread to event processor thread
 *
 * **Flow:**
 * ```
 * Command Thread:
 *   1. TenantContextFilter sets TenantContext from JWT
 *   2. Command handler executes with tenant context
 *   3. AggregateLifecycle.apply(event) → CorrelationDataProvider enriches metadata
 *   4. Event persisted with metadata: {"tenant_id": "tenant-a"}
 *
 * Event Processor Thread (async):
 *   5. TenantContextEventInterceptor extracts tenant_id from metadata
 *   6. Sets TenantContext before handler execution
 *   7. Event handler has tenant context available
 *   8. Context cleared after handler completion
 * ```
 *
 * Epic 4, Story 4.5: AC5, AC7
 *
 * @since 1.0.0
 */
@Configuration
open class TenantEventProcessingConfiguration {
    /**
     * CorrelationDataProvider for automatic tenant_id metadata enrichment.
     *
     * **AC5:** Event metadata enriched with tenant_id during command processing
     *
     * **How it works:**
     * - Axon automatically calls this provider when events are applied
     * - Provider extracts current tenant from TenantContext
     * - tenant_id is added to event metadata
     * - No manual MetaData.with() calls needed in command handlers
     *
     * **Nullable Handling:**
     * - Returns null if TenantContext is not set (e.g., system events)
     * - Null values are NOT added to metadata (graceful degradation)
     *
     * **Example:**
     * ```kotlin
     * @CommandHandler
     * fun handle(command: CreateWidgetCommand) {
     *     // TenantContext already set by TenantContextFilter
     *     AggregateLifecycle.apply(WidgetCreatedEvent(...))
     *     // CorrelationDataProvider automatically adds metadata: {"tenant_id": "tenant-a"}
     * }
     * ```
     *
     * @return CorrelationDataProvider for tenant_id enrichment
     */
    @Bean
    open fun tenantCorrelationDataProvider(): CorrelationDataProvider {
        // AC5: Custom CorrelationDataProvider for automatic tenant_id metadata enrichment
        return object : CorrelationDataProvider {
            override fun correlationDataFor(message: Message<*>): Map<String, *> {
                val tenantId = TenantContext.current()
                return if (tenantId != null) {
                    mapOf("tenant_id" to tenantId)
                } else {
                    emptyMap<String, Any>()
                }
            }
        }
    }

    /**
     * Register TenantContextEventInterceptor for all event processors.
     *
     * **AC7:** Async event processors (TrackingEventProcessor) receive correct context
     *
     * **Registration Strategy:**
     * - registerDefaultHandlerInterceptor() applies to ALL event processors
     * - Includes both SubscribingEventProcessor (sync) and TrackingEventProcessor (async)
     * - Interceptor is invoked BEFORE event handler execution
     *
     * **Alternative (per-processor registration):**
     * ```kotlin
     * configurer.registerHandlerInterceptor("widget-projection") { _, _ -> tenantInterceptor }
     * ```
     *
     * @param configurer Axon EventProcessingConfigurer for interceptor registration
     * @param tenantInterceptor TenantContextEventInterceptor bean
     */
    @Autowired
    open fun configureEventProcessing(
        configurer: EventProcessingConfigurer,
        tenantInterceptor: TenantContextEventInterceptor,
    ) {
        // AC7: Register interceptor for ALL event processors
        // This ensures async TrackingEventProcessor receives tenant context
        configurer.registerDefaultHandlerInterceptor { _, _ -> tenantInterceptor }
    }
}
