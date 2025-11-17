package com.axians.eaf.framework.multitenancy

import io.micrometer.core.instrument.MeterRegistry
import org.axonframework.eventhandling.EventMessage
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.springframework.stereotype.Component

/**
 * Axon event interceptor for async tenant context propagation (Layer 2.5).
 *
 * **Context Propagation Challenge:**
 * - TrackingEventProcessor uses dedicated threads for async event processing
 * - ThreadLocal context (tenant_id) is lost when events are handled asynchronously
 * - Event handlers and projections need tenant context for Layer 2 validation and RLS
 *
 * **Solution: Event Metadata Enrichment Pattern**
 * - tenant_id is enriched in event metadata during command processing (CorrelationDataProvider)
 * - This interceptor extracts tenant_id from event metadata and restores ThreadLocal
 * - Context is cleared after handler completion to prevent leaks
 *
 * **Execution Flow:**
 * 1. Extract tenant_id from event.metaData["tenant_id"]
 * 2. Set TenantContext.setCurrentTenantId(tenantId) before handler
 * 3. Proceed to event handler execution
 * 4. Clear TenantContext.clearCurrentTenant() in finally block
 *
 * **Security Properties:**
 * - **Nullable handling:** Events without tenant_id (system events) are allowed
 * - **Fail-safe:** Missing metadata does NOT fail event processing
 * - **Cleanup guaranteed:** try-finally ensures ThreadLocal cleanup
 * - **Metrics:** Emit propagation failures for monitoring
 *
 * **Example Event Processing:**
 * ```kotlin
 * // Event dispatched with metadata: {"tenant_id": "tenant-a"}
 * // Interceptor restores context before handler
 *
 * @EventHandler
 * fun on(event: WidgetCreatedEvent) {
 *     val tenantId = TenantContext.getCurrentTenantId() // Returns "tenant-a"
 *     // Update projection with tenant context...
 * }
 * ```
 *
 * Epic 4, Story 4.5: AC1, AC2, AC3, AC4
 *
 * @param meterRegistry Micrometer registry for propagation metrics
 * @since 1.0.0
 */
@Component
class TenantContextEventInterceptor(
    private val meterRegistry: MeterRegistry,
) : MessageHandlerInterceptor<EventMessage<*>> {
    /**
     * Intercept event processing to restore tenant context from metadata.
     *
     * **AC1:** Implements EventMessageHandlerInterceptor (via MessageHandlerInterceptor<EventMessage<*>>)
     * **AC2:** Extract tenant_id from event.metaData["tenant_id"]
     * **AC3:** Set TenantContext before handler execution
     * **AC4:** Clear TenantContext after handler completion
     *
     * @param unitOfWork Axon unit of work containing the event
     * @param chain Interceptor chain for proceeding to event handler
     * @return Event handler result
     */
    override fun handle(
        unitOfWork: UnitOfWork<out EventMessage<*>>,
        chain: InterceptorChain,
    ): Any? {
        val event = unitOfWork.message
        // AC2: Extract tenant_id from event metadata
        val tenantId = event.metaData["tenant_id"] as? String

        return if (tenantId != null) {
            // Tenant-scoped event: Restore context
            try {
                // AC3: Set TenantContext before handler execution
                TenantContext.setCurrentTenantId(tenantId)

                // Emit metric for successful context propagation
                meterRegistry.counter("tenant.context.propagation.success").increment()

                chain.proceed()
            } finally {
                // AC4: Clear TenantContext after handler completion (CRITICAL)
                TenantContext.clearCurrentTenant()
            }
        } else {
            // System event without tenant context: Allow processing without context
            // Examples: System events, admin events, cross-tenant notifications
            meterRegistry.counter("tenant.context.propagation.skipped").increment()
            chain.proceed()
        }
    }
}
