package com.axians.eaf.framework.workflow.delegates

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.framework.security.tenant.TenantContext
import org.axonframework.commandhandling.gateway.CommandGateway
import org.flowable.engine.delegate.BpmnError
import org.flowable.engine.delegate.DelegateExecution
import org.flowable.engine.delegate.JavaDelegate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

/**
 * Flowable JavaDelegate that dispatches Axon commands from BPMN workflows.
 *
 * This delegate enables BPMN processes to initiate business logic in CQRS aggregates via Axon's
 * CommandGateway. It implements the critical bridge between Flowable workflow orchestration and
 * Axon domain logic.
 *
 * ## Security: Tenant Isolation (MANDATORY)
 *
 * All workflow service tasks MUST validate tenant context to prevent cross-tenant data leakage.
 * This delegate uses fail-closed tenant validation: if tenant context is missing or mismatched,
 * the task throws an exception.
 *
 * ## Usage in BPMN
 *
 * ```xml
 * <serviceTask id="dispatchCommand" name="Create Widget"
 *              flowable:delegateExpression="${dispatchAxonCommandTask}">
 *   <documentation>
 *     Required process variables:
 *     - widgetId (String): UUID for widget aggregate
 *     - tenantId (String): Tenant identifier
 *     - name (String): Widget name
 *     - description (String, optional): Widget description
 *     - value (BigDecimal): Widget value
 *     - category (String): Widget category
 *   </documentation>
 * </serviceTask>
 * ```
 *
 * ## Error Handling
 *
 * Command dispatch failures are converted to BPMN errors, enabling error boundary events:
 * - Missing variables → MISSING_VARIABLE error
 * - Tenant mismatch → TENANT_ISOLATION_VIOLATION error
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
            val command = extractAndBuildCommand(execution)
            dispatchCommand(command)
            execution.setVariable("commandResult", "SUCCESS")
        } catch (
            @Suppress("SwallowedException")
            ex: IllegalArgumentException,
        ) {
            // Tenant context mismatch or validation failure
            // Exception converted to BpmnError for workflow error handling (message preserved)
            throw BpmnError(
                "TENANT_ISOLATION_VIOLATION",
                ex.message ?: "Tenant isolation violation",
            )
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException")
            ex: Exception,
        ) {
            // Flowable delegate infrastructure pattern: Catch any exception and convert to BpmnError
            // for workflow error boundary handling. Exception details preserved in BpmnError message.
            // This is similar to infrastructure interceptor pattern in coding-standards-revision-2.md
            // where generic catch is acceptable for cross-cutting infrastructure concerns.
            throw BpmnError(
                "COMMAND_DISPATCH_FAILED",
                "Command dispatch failed: ${ex.message}",
            )
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

    @Suppress("ThrowsCount") // Multiple variable validations legitimately require multiple throws
    private fun extractAndBuildCommand(execution: DelegateExecution): CreateWidgetCommand {
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

        return CreateWidgetCommand(
            widgetId = widgetId,
            tenantId = tenantId,
            name = name,
            description = description,
            value = value,
            category = category,
            metadata = metadata,
        )
    }

    private fun dispatchCommand(command: CreateWidgetCommand) {
        commandGateway.sendAndWait<Any>(command, 10, TimeUnit.SECONDS)
    }
}
