package com.axians.eaf.framework.cqrs.interceptors

import com.axians.eaf.framework.security.tenant.TenantContext
import org.axonframework.messaging.Message
import org.axonframework.messaging.correlation.CorrelationDataProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Axon CorrelationDataProvider that enriches event messages with tenant ID metadata.
 *
 * ## Purpose
 *
 * Ensures all events published by command handlers include `tenantId` in their metadata,
 * enabling TenantEventMessageInterceptor to propagate tenant context to asynchronous
 * @EventHandler methods in tracking processors.
 *
 * ## Integration with Command Flow
 *
 * 1. **Command Boundary**: TenantContext populated from JWT (Layer 1 filter - Story 4.1)
 * 2. **Command Handler**: Executes business logic, publishes events via `apply()`
 * 3. **Event Publication**: **This provider executes**, reads TenantContext, adds `tenantId` to metadata
 * 4. **Event Tracking**: TenantEventMessageInterceptor reads metadata, restores TenantContext
 * 5. **Projection Handler**: @EventHandler can access TenantContext.getCurrentTenantId()
 *
 * ## Metadata Contract (Story 4.4 - Subtask 2.3)
 *
 * All EventMessages published by command handlers include the following metadata when
 * tenant context is available:
 *
 * **Key**: `tenantId`
 * **Type**: String (non-blank)
 * **Source**: TenantContext.current() (nullable - may be null for system events)
 * **Enforcement**: TenantEventMessageInterceptor validates presence and fails closed
 * **Behavior**: Only adds metadata if tenant context is present (graceful for system events)
 *
 * **Failure Mode if Missing**:
 * When tracking processors receive events without `tenantId` metadata, TenantEventMessageInterceptor
 * throws SecurityException with generic message: "Access denied: required context missing"
 *
 * This fail-closed design ensures tenant-scoped events always have proper context while
 * allowing system-level events (saga compensations, scheduled tasks) to publish without tenant.
 *
 * ## Fail-Safe Design
 *
 * - **Tenant context present**: Metadata added, downstream validation succeeds
 * - **Tenant context missing**: Metadata NOT added, downstream interceptor fails-closed
 * - **System events**: Can publish without tenant (e.g., scheduled tasks, saga events)
 *
 * This fail-safe design allows system-level events while maintaining strict validation
 * for tenant-scoped events (enforced by TenantEventMessageInterceptor).
 *
 * @param tenantContext ThreadLocal tenant context from Story 4.1
 *
 * @see TenantEventMessageInterceptor Consumes tenantId metadata
 * @see com.axians.eaf.framework.security.tenant.TenantContext ThreadLocal storage
 */
@Component
class TenantCorrelationDataProvider(
    private val tenantContext: TenantContext,
) : CorrelationDataProvider {
    companion object {
        private val logger = LoggerFactory.getLogger(TenantCorrelationDataProvider::class.java)
        private const val TENANT_METADATA_KEY = "tenantId"
    }

    /**
     * Enriches message metadata with current tenant ID if tenant context is available.
     *
     * **Behavior**:
     * - TenantContext present → Returns map with `{"tenantId": "tenant-abc"}`
     * - TenantContext absent → Returns empty map (system event)
     *
     * @param message Message being published (command or event)
     * @return Metadata map with tenantId (or empty if no tenant context)
     */
    override fun correlationDataFor(message: Message<*>): Map<String, *> {
        val tenantId = tenantContext.current()

        return if (tenantId != null) {
            logger.trace("Enriching message metadata with tenantId: {}", tenantId)
            mapOf(TENANT_METADATA_KEY to tenantId)
        } else {
            logger.trace("No tenant context - skipping metadata enrichment (system event)")
            emptyMap<String, Any>()
        }
    }
}
