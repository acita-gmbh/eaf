package com.axians.eaf.framework.workflow.handlers

import com.axians.eaf.api.widget.events.WidgetCreatedEvent
import com.axians.eaf.framework.security.tenant.TenantContext
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.EventHandler
import org.flowable.common.engine.api.FlowableException
import org.flowable.engine.RuntimeService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
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
    private val tenantContext: TenantContext,
) {
    private val logger: Logger = LoggerFactory.getLogger(AxonEventSignalHandler::class.java)

    companion object {
        private const val GENERIC_ERROR_MESSAGE = "Access denied: required context missing"
    }

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

        // Subtask 1.4: Two-step query for waiting process instances
        // Research finding: Business key only on root process instance, not child executions
        // Must first find process instance, then query execution by processInstanceId

        // Step 1: Find process instance by business key (widgetId)
        val processInstance =
            runtimeService
                .createProcessInstanceQuery()
                .processInstanceBusinessKey(event.widgetId)
                .singleResult()

        if (processInstance == null) {
            // CWE-209 protection: Generic message, no tenant/widget ID disclosure
            logger.warn("No process instance found for business key correlation")

            // Debug logging (disabled in production)
            if (logger.isDebugEnabled) {
                logger.debug("Process correlation failed [widgetId={}, tenantId={}]", event.widgetId, event.tenantId)
            }
            return
        }

        // SECURITY: Validate process belongs to current tenant (fail-closed enforcement)
        // Query process variables separately (ProcessInstance might not include them)
        val processTenantId = runtimeService.getVariable(processInstance.id, "tenantId") as? String
        if (processTenantId != null && processTenantId != event.tenantId) {
            // CWE-209 protection: Generic error message
            logger.warn("Tenant isolation violation detected during process correlation")

            // Debug logging (disabled in production)
            if (logger.isDebugEnabled) {
                logger.debug(
                    "Tenant mismatch [processTenant={}, eventTenant={}, widgetId={}]",
                    processTenantId,
                    event.tenantId,
                    event.widgetId,
                )
            }

            // TODO Story 6.4: Add security metrics for tenant isolation violations
            // customMetrics?.recordEvent("TenantIsolationViolation", duration, false)

            // Fail-closed: Throw SecurityException (consistent with TenantEventMessageInterceptor)
            throw SecurityException(GENERIC_ERROR_MESSAGE)
        }

        // Step 2: Query execution by processInstanceId (works for child executions)
        val execution =
            runtimeService
                .createExecutionQuery()
                .processInstanceId(processInstance.id)
                .messageEventSubscriptionName("WidgetCreated")
                .singleResult()

        // Subtask 1.6: Resilient error handling for missing processes
        if (execution != null) {
            try {
                // Subtask 1.4: Signal the waiting process using message delivery
                runtimeService.messageEventReceived("WidgetCreated", execution.id)

                // CWE-209 protection: Generic success message
                logger.info("BPMN process signaled successfully")

                // Debug logging (disabled in production)
                if (logger.isDebugEnabled) {
                    logger.debug("Process signaled [widgetId={}, executionId={}]", event.widgetId, execution.id)
                }
            } catch (ex: FlowableException) {
                // Flowable signaling failure - log error but preserve event processing
                logger.error("Failed to signal BPMN process: ${ex.message}", ex)
                // Do not fail - log error and continue (resilient pattern)
            }
        } else {
            // No waiting process - this is OK (not all events require BPMN signaling)
            // CWE-209 protection: Generic message, no tenant/widget ID disclosure
            logger.warn("No waiting process found for event correlation")

            // Debug logging (disabled in production)
            if (logger.isDebugEnabled) {
                logger.debug("No process subscription [widgetId={}, tenantId={}]", event.widgetId, event.tenantId)
            }
        }
    }
}
