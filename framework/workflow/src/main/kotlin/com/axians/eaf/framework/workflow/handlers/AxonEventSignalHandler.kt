package com.axians.eaf.framework.workflow.handlers

import com.axians.eaf.api.widget.events.WidgetCreatedEvent
import com.axians.eaf.framework.security.tenant.TenantContext
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.EventHandler
import org.flowable.common.engine.api.FlowableException
import org.flowable.engine.RuntimeService
import org.flowable.engine.runtime.Execution
import org.flowable.engine.runtime.ProcessInstance
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
        validateEventTenant(event)

        // Two-step query for waiting process (Flowable business key limitation)
        val processInstance = findProcessInstanceByBusinessKey(event.widgetId) ?: return

        // SECURITY: Validate process belongs to current tenant
        validateProcessTenant(processInstance, event)

        // Find and signal waiting execution
        val execution = findWaitingExecution(processInstance.id)
        if (execution != null) {
            signalProcess(execution, event.widgetId)
        } else {
            logNoWaitingProcess(event)
        }
    }

    private fun validateEventTenant(event: WidgetCreatedEvent) {
        // SEC-001 mitigation: Prevent cross-tenant signaling
        val currentTenant = tenantContext.getCurrentTenantId() // Throws if missing
        require(event.tenantId == currentTenant) {
            "Access denied: tenant context mismatch" // CWE-209 protection
        }
    }

    private fun findProcessInstanceByBusinessKey(widgetId: String): ProcessInstance? {
        val processInstance =
            runtimeService
                .createProcessInstanceQuery()
                .processInstanceBusinessKey(widgetId)
                .singleResult()

        if (processInstance == null) {
            logger.warn("No process instance found for business key correlation")
            if (logger.isDebugEnabled) {
                logger.debug("Process correlation failed [widgetId={}]", widgetId)
            }
        }
        return processInstance
    }

    private fun validateProcessTenant(
        processInstance: ProcessInstance,
        event: WidgetCreatedEvent,
    ) {
        // Query process variables separately (ProcessInstance might not include them)
        val processTenantVar = runtimeService.getVariable(processInstance.id, "tenantId")

        // Type validation: Ensure tenantId is String (fail-closed on type mismatch)
        if (processTenantVar != null && processTenantVar !is String) {
            logger.warn("Process variable 'tenantId' has invalid type")
            if (logger.isDebugEnabled) {
                logger.debug("Invalid tenantId type [expected=String, actual=${processTenantVar.javaClass.name}]")
            }
            throw SecurityException(GENERIC_ERROR_MESSAGE)
        }

        val processTenantId = processTenantVar as? String

        // Tenant isolation validation (fail-closed)
        if (processTenantId != null && processTenantId != event.tenantId) {
            logger.warn("Tenant isolation violation detected during process correlation")

            if (logger.isDebugEnabled) {
                logger.debug(
                    "Tenant mismatch [processTenant={}, eventTenant={}, widgetId={}]",
                    processTenantId,
                    event.tenantId,
                    event.widgetId,
                )
            }

            // TODO Story 6.4: Add security metrics for tenant isolation violations
            // Fail-closed: Throw SecurityException (consistent with TenantEventMessageInterceptor)
            throw SecurityException(GENERIC_ERROR_MESSAGE)
        }
    }

    private fun findWaitingExecution(processInstanceId: String): Execution? =
        runtimeService
            .createExecutionQuery()
            .processInstanceId(processInstanceId)
            .messageEventSubscriptionName("WidgetCreated")
            .singleResult()

    private fun signalProcess(
        execution: Execution,
        widgetId: String,
    ) {
        try {
            runtimeService.messageEventReceived("WidgetCreated", execution.id)
            logger.info("BPMN process signaled successfully")

            if (logger.isDebugEnabled) {
                logger.debug("Process signaled [widgetId={}, executionId={}]", widgetId, execution.id)
            }
        } catch (ex: FlowableException) {
            // Flowable signaling failure - log error but preserve event processing
            logger.error("Failed to signal BPMN process: ${ex.message}", ex)
        }
    }

    private fun logNoWaitingProcess(event: WidgetCreatedEvent) {
        // CWE-209 protection: Generic message
        logger.warn("No waiting process found for event correlation")

        if (logger.isDebugEnabled) {
            logger.debug("No process subscription [widgetId={}, tenantId={}]", event.widgetId, event.tenantId)
        }
    }
}
