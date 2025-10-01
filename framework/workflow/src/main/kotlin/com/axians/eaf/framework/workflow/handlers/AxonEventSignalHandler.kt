package com.axians.eaf.framework.workflow.handlers

import com.axians.eaf.api.widget.events.WidgetCreatedEvent
import com.axians.eaf.framework.security.tenant.TenantContext
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.EventHandler
import org.flowable.common.engine.api.FlowableException
import org.flowable.engine.RuntimeService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Axon event handler that signals waiting Flowable BPMN processes when domain events occur.
 *
 * This handler implements the Axon→Flowable bridge by:
 * 1. Listening for Axon domain events (e.g., WidgetCreatedEvent)
 * 2. Correlating events to waiting BPMN process instances via business key
 * 3. Signaling processes using Flowable RuntimeService message delivery
 *
 * Security: Enforces tenant isolation with fail-closed validation
 * Resilience: Gracefully handles missing processes (logs warning, continues)
 */
@Component
@ProcessingGroup("flowable-signaling")
class AxonEventSignalHandler(
    private val runtimeService: RuntimeService,
    private val tenantContext: TenantContext
) {

    private val logger: Logger = LoggerFactory.getLogger(AxonEventSignalHandler::class.java)

    /**
     * Handles WidgetCreatedEvent by signaling waiting BPMN processes.
     *
     * Process correlation uses business key pattern:
     * - BPMN process started with businessKey = widgetId
     * - Event contains widgetId for correlation
     * - RuntimeService queries for matching process with message subscription
     *
     * @param event WidgetCreatedEvent from Axon aggregate
     */
    @EventHandler
    fun on(event: WidgetCreatedEvent) {
        // CRITICAL: Fail-closed tenant validation (Subtask 1.5)
        // SEC-001 mitigation: Prevent cross-tenant signaling
        val currentTenant = tenantContext.getCurrentTenantId() // Throws if missing
        require(event.tenantId == currentTenant) {
            "Access denied: tenant context mismatch" // CWE-209 protection
        }

        // Subtask 1.4: Query for waiting process instances using business key correlation
        val execution = runtimeService.createExecutionQuery()
            .processInstanceBusinessKey(event.widgetId)
            .messageEventSubscriptionName("WidgetCreated")
            .singleResult()

        // Subtask 1.6: Resilient error handling for missing processes
        if (execution != null) {
            try {
                // Subtask 1.4: Signal the waiting process using message delivery
                runtimeService.messageEventReceived("WidgetCreated", execution.id)
                logger.info("Signaled BPMN process for widgetId=${event.widgetId}")
            } catch (ex: FlowableException) {
                // Flowable signaling failure - log error but preserve event processing
                logger.error("Failed to signal BPMN process: ${ex.message}", ex)
                // Do not fail - log error and continue (resilient pattern)
            }
        } else {
            // No waiting process - this is OK (not all events require BPMN signaling)
            logger.warn(
                "No waiting process found for WidgetCreatedEvent: widgetId=${event.widgetId}, tenantId=${event.tenantId}"
            )
        }
    }
}
