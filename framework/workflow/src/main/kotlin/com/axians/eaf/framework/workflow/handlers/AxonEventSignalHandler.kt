package com.axians.eaf.framework.workflow.handlers

import com.axians.eaf.framework.security.tenant.TenantContext
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.EventHandler
import org.axonframework.messaging.MetaData
import org.flowable.common.engine.api.FlowableException
import org.flowable.engine.RuntimeService
import org.flowable.engine.runtime.Execution
import org.flowable.engine.runtime.ProcessInstance
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Generic Axon event handler that signals waiting Flowable BPMN processes when domain events occur.
 *
 * This handler implements the Axon→Flowable bridge for ANY domain event by:
 * 1. Listening for all Axon events (generic event parameter)
 * 2. Using event metadata for correlation (correlationKey, messageName)
 * 3. Signaling processes using Flowable RuntimeService message delivery
 *
 * **Framework Infrastructure**: This is a generic, reusable component. Product-specific
 * event handlers are NOT needed - domains provide correlation via event metadata.
 *
 * **Event Metadata Contract**:
 * - `correlationKey` (String): Business key for process correlation (e.g., orderId, entityId)
 * - `messageName` (String): Flowable message name for subscription matching (e.g., "OrderCreated")
 * - `tenantId` (String): Tenant identifier for isolation validation
 *
 * **Usage Example** (from any domain):
 * ```kotlin
 * val event = OrderCreatedEvent(orderId = "123", customerId = "456")
 * val eventMessage = GenericEventMessage.asEventMessage(event)
 *     .andMetaData(mapOf(
 *         "correlationKey" to "123",      // Order ID for correlation
 *         "messageName" to "OrderCreated", // Flowable message subscription name
 *         "tenantId" to "tenant-a"        // Tenant isolation
 *     ))
 * eventBus.publish(eventMessage)
 * ```
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

        // Event metadata keys (contract with event publishers)
        private const val METADATA_CORRELATION_KEY = "correlationKey"
        private const val METADATA_MESSAGE_NAME = "messageName"
        private const val METADATA_TENANT_ID = "tenantId"
    }

    /**
     * Handles ANY domain event by signaling waiting BPMN processes.
     *
     * Process correlation uses metadata-driven pattern:
     * - Event metadata contains correlationKey (business key for process lookup)
     * - Event metadata contains messageName (Flowable message subscription name)
     * - BPMN process started with businessKey = correlationKey
     * - RuntimeService signals process using messageName
     *
     * @param event Any domain event from Axon aggregates
     * @param metadata Event metadata containing correlation and message information
     */
    @EventHandler
    fun on(
        event: Any,
        metadata: MetaData,
    ) {
        // CRITICAL: Fail-closed tenant validation
        validateEventTenant(metadata)

        // Extract correlation metadata (fail-safe if missing)
        val correlationKey = metadata[METADATA_CORRELATION_KEY] as? String
        val messageName = metadata[METADATA_MESSAGE_NAME] as? String

        if (correlationKey == null || messageName == null) {
            // Not all events require BPMN signaling (missing metadata is acceptable)
            if (logger.isDebugEnabled) {
                logger.debug(
                    "Event skipped - missing correlation metadata [eventType={}, correlationKey={}, messageName={}]",
                    event.javaClass.simpleName,
                    correlationKey,
                    messageName,
                )
            }
            return
        }

        // Two-step query for waiting process (Flowable business key limitation)
        val processInstance = findProcessInstanceByBusinessKey(correlationKey) ?: return

        // SECURITY: Validate process belongs to current tenant
        validateProcessTenant(processInstance, metadata)

        // Find and signal waiting execution
        val execution = findWaitingExecution(processInstance.id, messageName)
        if (execution != null) {
            signalProcess(execution, messageName, correlationKey)
        } else {
            logNoWaitingProcess(correlationKey, messageName)
        }
    }

    private fun validateEventTenant(metadata: MetaData) {
        // SEC-001 mitigation: Prevent cross-tenant signaling
        val eventTenantId = metadata[METADATA_TENANT_ID] as? String
        if (eventTenantId == null) {
            logger.warn("Event metadata missing tenantId")
            throw SecurityException(GENERIC_ERROR_MESSAGE)
        }

        val currentTenant = tenantContext.getCurrentTenantId() // Throws if missing
        require(eventTenantId == currentTenant) {
            "Access denied: tenant context mismatch" // CWE-209 protection
        }
    }

    private fun findProcessInstanceByBusinessKey(correlationKey: String): ProcessInstance? {
        val processInstance =
            runtimeService
                .createProcessInstanceQuery()
                .processInstanceBusinessKey(correlationKey)
                .singleResult()

        if (processInstance == null) {
            logger.warn("No process instance found for business key correlation")
            if (logger.isDebugEnabled) {
                logger.debug("Process correlation failed [correlationKey={}]", correlationKey)
            }
        }
        return processInstance
    }

    private fun validateProcessTenant(
        processInstance: ProcessInstance,
        metadata: MetaData,
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
        val eventTenantId = metadata[METADATA_TENANT_ID] as? String

        // Tenant isolation validation (fail-closed)
        if (processTenantId != null && eventTenantId != null && processTenantId != eventTenantId) {
            logger.warn("Tenant isolation violation detected during process correlation")

            if (logger.isDebugEnabled) {
                logger.debug(
                    "Tenant mismatch [processTenant={}, eventTenant={}]",
                    processTenantId,
                    eventTenantId,
                )
            }

            // TODO Story 6.4: Record metric 'workflow.tenant.isolation.violation' via CustomMetrics.recordEvent()
            // TODO Story 6.4: Alert on tenant isolation violations for security monitoring
            // Fail-closed: Throw SecurityException (consistent with TenantEventMessageInterceptor)
            throw SecurityException(GENERIC_ERROR_MESSAGE)
        }
    }

    private fun findWaitingExecution(
        processInstanceId: String,
        messageName: String,
    ): Execution? =
        runtimeService
            .createExecutionQuery()
            .processInstanceId(processInstanceId)
            .messageEventSubscriptionName(messageName)
            .singleResult()

    private fun signalProcess(
        execution: Execution,
        messageName: String,
        correlationKey: String,
    ) {
        try {
            runtimeService.messageEventReceived(messageName, execution.id)
            logger.info("BPMN process signaled successfully")

            if (logger.isDebugEnabled) {
                logger.debug("Process signaled [correlationKey={}, executionId={}]", correlationKey, execution.id)
            }
        } catch (ex: FlowableException) {
            // Flowable signaling failure - log error but preserve event processing
            logger.error("Failed to signal BPMN process: ${ex.message}", ex)
        }
    }

    private fun logNoWaitingProcess(
        correlationKey: String,
        messageName: String,
    ) {
        // CWE-209 protection: Generic message
        logger.warn("No waiting process found for event correlation")

        if (logger.isDebugEnabled) {
            logger.debug("No process subscription [correlationKey={}, messageName={}]", correlationKey, messageName)
        }
    }
}
