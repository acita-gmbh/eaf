package com.axians.eaf.framework.workflow.delegates

import com.axians.eaf.framework.security.tenant.TenantContext
import org.axonframework.commandhandling.gateway.CommandGateway
import org.flowable.engine.delegate.BpmnError
import org.flowable.engine.delegate.DelegateExecution
import org.flowable.engine.delegate.JavaDelegate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

/**
 * Generic Flowable JavaDelegate that dispatches Axon commands from BPMN workflows.
 *
 * This delegate enables BPMN processes to initiate business logic in CQRS aggregates via Axon's
 * CommandGateway. It implements the critical bridge between Flowable workflow orchestration and
 * Axon domain logic for ANY domain.
 *
 * **Framework Infrastructure**: This is generic, extensible infrastructure. Domains add command
 * builders via when clauses (see buildCommand method).
 *
 * **BPMN Usage Contract**:
 * ```xml
 * <serviceTask id="dispatchCommand" name="Dispatch Command"
 *              flowable:delegateExpression="${dispatchAxonCommandTask}">
 *   <documentation>
 *     Required process variables:
 *     - commandType (String): Command class name (e.g., "CreateWidgetCommand", "CreateOrderCommand")
 *     - tenantId (String): Tenant identifier for isolation
 *     - [command-specific variables]: Depends on commandType
 *   </documentation>
 * </serviceTask>
 * ```
 *
 * **Adding New Commands** (Extensible Pattern):
 * 1. Add case to buildCommand() when expression
 * 2. Implement private buildXYZCommand() method
 * 3. Extract variables and construct command
 *
 * ## Security: Tenant Isolation (MANDATORY)
 *
 * All workflow service tasks MUST validate tenant context to prevent cross-tenant data leakage.
 * This delegate uses fail-closed tenant validation: if tenant context is missing or mismatched,
 * the task throws an exception.
 *
 * ## Error Handling
 *
 * Command dispatch failures are converted to BPMN errors, enabling error boundary events:
 * - Missing variables → MISSING_VARIABLE error
 * - Tenant mismatch → TENANT_ISOLATION_VIOLATION error
 * - Unknown command type → UNKNOWN_COMMAND_TYPE error
 * - Command failure → COMMAND_DISPATCH_FAILED error
 *
 * Story 6.2: Create Flowable-to-Axon Bridge (Command Dispatch)
 *
 * @see CommandGateway for Axon command dispatch
 * @see TenantContext for multi-tenant security
 */
@Component
class DispatchAxonCommandTask(
    private val commandGateway: CommandGateway,
    private val tenantContext: TenantContext,
) : JavaDelegate {
    override fun execute(execution: DelegateExecution) {
        try {
            validateTenantContext(execution)
            val command = buildCommand(execution)
            dispatchCommand(command)
            execution.setVariable("commandResult", "SUCCESS")
        } catch (
            @Suppress("SwallowedException")
            ex: IllegalArgumentException,
        ) {
            // CWE-209 Protection: Generic message
            throw BpmnError("TENANT_ISOLATION_VIOLATION", "Access denied")
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException")
            ex: Exception,
        ) {
            // CWE-209 Protection: Generic message
            throw BpmnError("COMMAND_DISPATCH_FAILED", "Command dispatch failed")
        }
    }

    private fun validateTenantContext(execution: DelegateExecution) {
        val currentTenant = tenantContext.getCurrentTenantId()
        val commandTenant =
            execution.getVariable("tenantId") as? String
                ?: throw BpmnError("MISSING_VARIABLE", "Required process variable missing: tenantId")

        require(currentTenant == commandTenant) {
            "Access denied: tenant context mismatch"
        }
    }

    /**
     * Builds command object based on commandType variable.
     *
     * Generic infrastructure: Domains add cases to when expression for their command types.
     * Each case delegates to domain-specific builder method.
     *
     * @param execution DelegateExecution containing process variables
     * @return Command object to dispatch via CommandGateway
     * @throws BpmnError if commandType unknown or variables missing
     */
    private fun buildCommand(execution: DelegateExecution): Any {
        val commandType =
            execution.getVariable("commandType") as? String
                ?: throw BpmnError("MISSING_VARIABLE", "Required process variable missing: commandType")

        return when (commandType) {
            "CreateWidgetCommand" -> buildCreateWidgetCommand(execution)
            // Future domains: Add command builders here
            // "CreateOrderCommand" -> buildCreateOrderCommand(execution)
            // "CreateLicenseCommand" -> buildCreateLicenseCommand(execution)
            else -> throw BpmnError("UNKNOWN_COMMAND_TYPE", "Unsupported command type: $commandType")
        }
    }

    /**
     * Builds CreateWidgetCommand from process variables.
     *
     * Widget-specific builder. Other domains add similar methods for their commands.
     */
    @Suppress("ThrowsCount") // Multiple variable validations legitimately require multiple throws
    private fun buildCreateWidgetCommand(execution: DelegateExecution): Any {
        val widgetId =
            execution.getVariable("widgetId") as? String
                ?: throw BpmnError("MISSING_VARIABLE", "Required process variable missing: widgetId")
        val tenantId =
            execution.getVariable("tenantId") as? String
                ?: throw BpmnError("MISSING_VARIABLE", "Required process variable missing: tenantId")
        val name =
            execution.getVariable("name") as? String
                ?: throw BpmnError("MISSING_VARIABLE", "Required process variable missing: name")
        val description = execution.getVariable("description") as? String
        val value =
            execution.getVariable("value") as? BigDecimal
                ?: throw BpmnError("MISSING_VARIABLE", "Required process variable missing: value")
        val category =
            execution.getVariable("category") as? String
                ?: throw BpmnError("MISSING_VARIABLE", "Required process variable missing: category")

        @Suppress("UNCHECKED_CAST")
        val metadata = (execution.getVariable("metadata") as? Map<String, Any>) ?: emptyMap()

        // Use fully qualified name to avoid import
        val commandClass = Class.forName("com.axians.eaf.api.widget.commands.CreateWidgetCommand")
        val constructor =
            commandClass.getConstructor(
                String::class.java, // widgetId
                String::class.java, // tenantId
                String::class.java, // name
                String::class.java, // description (nullable)
                BigDecimal::class.java, // value
                String::class.java, // category
                Map::class.java, // metadata
            )

        return constructor.newInstance(widgetId, tenantId, name, description, value, category, metadata)
    }

    private fun dispatchCommand(command: Any) {
        commandGateway.sendAndWait<Any>(command, 10, TimeUnit.SECONDS)
    }
}
