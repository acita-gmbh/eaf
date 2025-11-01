package com.axians.eaf.framework.workflow.delegates

import com.axians.eaf.framework.security.tenant.TenantContext
import com.axians.eaf.framework.workflow.observability.FlowableMetrics
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
 * **BPMN Usage Contract** (ARCH-001 Pure Reflection Pattern):
 * ```xml
 * <serviceTask id="dispatchCommand" name="Dispatch Command"
 *              flowable:delegateExpression="${dispatchAxonCommandTask}">
 *   <documentation>
 *     Required process variables:
 *     - commandClassName (String): Fully qualified class name
 *       Example: "com.axians.eaf.api.widget.commands.CreateWidgetCommand"
 *     - constructorParameters (List&lt;String&gt;): Ordered constructor parameter names
 *       Example: ["widgetId", "tenantId", "name", "description", "value", "category"]
 *     - tenantId (String): Tenant identifier for isolation
 *     - [constructor parameters]: One process variable per parameter name in constructorParameters list
 *   </documentation>
 * </serviceTask>
 * ```
 *
 * **Adding New Commands** (Security-Hardened Pattern):
 * 1. Add command FQN to ALLOWED_COMMAND_CLASSES whitelist in companion object
 * 2. If compensation command, add to COMPENSATION_COMMAND_CLASSES for metrics
 * 3. In BPMN: Set commandClassName + constructorParameters + parameter values
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
    private val flowableMetrics: FlowableMetrics?,
) : JavaDelegate {
    companion object {
        /**
         * SECURITY: Explicit whitelist of allowed command classes (CWE-94 protection).
         *
         * **Fail-Closed Design**: Only explicitly listed classes can be instantiated via reflection.
         * Package prefix validation alone is INSUFFICIENT - an attacker with BPMN deployment
         * access could load malicious classes within the com.axians.eaf.* namespace.
         *
         * **Adding New Commands**: Products must update this whitelist when adding command types.
         *
         * Story 6.5 Security Hardening: Addresses code injection vulnerability identified in
         * security review (Quinn + /security-review analysis).
         */
        private val ALLOWED_COMMAND_CLASSES =
            setOf(
                // Framework test infrastructure
                "com.axians.eaf.framework.workflow.test.CreateTestEntityCommand",
                "com.axians.eaf.framework.workflow.test.CancelTestEntityCommand",
                // Widget domain (products/widget-demo)
                "com.axians.eaf.api.widget.commands.CreateWidgetCommand",
                "com.axians.eaf.api.widget.commands.UpdateWidgetCommand",
                "com.axians.eaf.api.widget.commands.CancelWidgetCreationCommand",
                // Add new command classes here as products expand
            )

        /**
         * OBSERVABILITY: Explicit list of compensation command classes for metrics tracking.
         *
         * **Purpose**: Identifies which commands represent compensation/rollback actions
         * for observability telemetry (FlowableMetrics.recordCompensationCommand).
         *
         * **Maintenance**: Update when adding new compensation commands.
         */
        private val COMPENSATION_COMMAND_CLASSES =
            setOf(
                "com.axians.eaf.framework.workflow.test.CancelTestEntityCommand",
                "com.axians.eaf.api.widget.commands.CancelWidgetCreationCommand",
            )
    }

    override fun execute(execution: DelegateExecution) {
        val processKey = execution.processDefinitionId.split(":").firstOrNull() ?: "unknown"

        try {
            validateTenantContext(execution)
            val command = buildCommand(execution)
            dispatchCommand(command)
            execution.setVariable("commandResult", "SUCCESS")

            // Story 6.5 (Task 3.3): Record compensation telemetry for compensation commands
            val commandClassName = execution.getVariable("commandClassName") as? String
            if (commandClassName in COMPENSATION_COMMAND_CLASSES) {
                flowableMetrics?.recordCompensationCommand(
                    commandType = commandClassName?.substringAfterLast(".") ?: "unknown",
                    processKey = processKey,
                    success = true,
                )
            }
        } catch (
            @Suppress("SwallowedException")
            ex: BpmnError,
        ) {
            // Story 6.5: Record failed compensation telemetry before re-throwing
            val commandClassName = execution.getVariable("commandClassName") as? String
            if (commandClassName in COMPENSATION_COMMAND_CLASSES) {
                flowableMetrics?.recordCompensationCommand(
                    commandType = commandClassName?.substringAfterLast(".") ?: "unknown",
                    processKey = processKey,
                    success = false,
                )
            }
            // Re-throw BpmnErrors as-is (TENANT_ISOLATION_VIOLATION, MISSING_VARIABLE, etc.)
            throw ex
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException")
            ex: Exception,
        ) {
            // Story 6.5: Record failed compensation telemetry before throwing generic error
            val commandClassName = execution.getVariable("commandClassName") as? String
            if (commandClassName in COMPENSATION_COMMAND_CLASSES) {
                flowableMetrics?.recordCompensationCommand(
                    commandType = commandClassName?.substringAfterLast(".") ?: "unknown",
                    processKey = processKey,
                    success = false,
                )
            }
            // CWE-209 Protection: Generic message for unexpected errors
            throw BpmnError("COMMAND_DISPATCH_FAILED", "Command dispatch failed")
        }
    }

    private fun validateTenantContext(execution: DelegateExecution) {
        val currentTenant = tenantContext.getCurrentTenantId()
        val commandTenant =
            execution.getVariable("tenantId") as? String
                ?: throw BpmnError("MISSING_VARIABLE", "Required process variable missing: tenantId")

        // SECURITY: Explicit BpmnError for tenant isolation violations (clearer semantics)
        if (currentTenant != commandTenant) {
            throw BpmnError("TENANT_ISOLATION_VIOLATION", "Access denied")
        }
    }

    /**
     * Builds command object using pure reflection.
     *
     * ## ARCHITECTURAL REMEDIATION (Story 6.5 ARCH-001)
     *
     * Framework modules MUST be product-agnostic. This method uses pure reflection
     * to build commands without compile-time dependencies on product types.
     *
     * ## SECURITY CRITICAL: Reflection Code Injection Prevention
     *
     * **SECURITY REQUIREMENTS**:
     * 1. ✅ **Class Name Whitelist**: commandClassName must match allowed package prefixes
     * 2. ✅ **Package Prefix Validation**: Only allow com.axians.eaf.* classes
     * 3. ✅ **No Arbitrary Classes**: Reject classes outside trusted packages
     * 4. ⚠️ **BPMN Trust Boundary**: Only deploy BPMN processes from trusted sources
     *
     * **SAFE PATTERN** (ARCH-001 Remediation):
     * ```kotlin
     * // Process provides fully qualified class name
     * val commandClassName = execution.getVariable("commandClassName") as String
     * // Example: "com.axians.eaf.api.widget.commands.CancelWidgetCreationCommand"
     *
     * // Validate package prefix (whitelist approach)
     * require(commandClassName.startsWith("com.axians.eaf.")) { "Untrusted package" }
     *
     * val commandClass = Class.forName(commandClassName)
     * // Build using reflection from process variables
     * ```
     *
     * **Framework Purity**: Framework has ZERO compile-time knowledge of product domains.
     * Products provide commandClassName + constructor parameter names via process variables.
     *
     * @param execution DelegateExecution containing process variables
     * @return Command object to dispatch via CommandGateway
     * @throws BpmnError if commandClassName invalid or variables missing
     */
    @Suppress("ThrowsCount", "SpreadOperator")
    private fun buildCommand(execution: DelegateExecution): Any {
        val commandClassName =
            execution.getVariable("commandClassName") as? String
                ?: throw BpmnError("MISSING_VARIABLE", "Required process variable missing: commandClassName")

        // SECURITY: Explicit whitelist (fail-closed) - prevents arbitrary class loading (CWE-94)
        // Package prefix validation alone is INSUFFICIENT - attacker with BPMN deployment access
        // could load malicious classes within com.axians.eaf.* namespace
        // CWE-209 Protection: Use BpmnError with generic message (not require() which leaks class name)
        if (commandClassName !in ALLOWED_COMMAND_CLASSES) {
            throw BpmnError("UNAUTHORIZED_COMMAND", "Command class not authorized")
        }

        // Load command class via reflection (framework-agnostic)
        val commandClass =
            try {
                Class.forName(commandClassName)
            } catch (
                @Suppress("SwallowedException")
                ex: ClassNotFoundException,
            ) {
                throw BpmnError("UNKNOWN_COMMAND_CLASS", "Command class not found: $commandClassName")
            }

        // Get constructor parameter names from process variable (products provide this)
        @Suppress("UNCHECKED_CAST")
        val parameterNames =
            execution.getVariable("constructorParameters") as? List<String>
                ?: throw BpmnError(
                    "MISSING_VARIABLE",
                    "Required process variable missing: constructorParameters",
                )

        // Build parameter values array from process variables (handles nulls)
        val parameterValues =
            parameterNames
                .map { paramName ->
                    execution.getVariable(paramName) // May be null for nullable parameters
                }.toTypedArray()

        // Find constructor matching parameter count (handles Kotlin default parameters)
        // Kotlin generates synthetic constructors for default parameters - select by arity match
        val constructor =
            commandClass.constructors.firstOrNull { it.parameterCount == parameterValues.size }
                ?: throw BpmnError(
                    "CONSTRUCTOR_NOT_FOUND",
                    "No constructor found matching ${parameterValues.size} parameters",
                )

        // Instantiate command via reflection
        return try {
            constructor.newInstance(*parameterValues)
        } catch (
            @Suppress("SwallowedException")
            ex: IllegalArgumentException,
        ) {
            throw BpmnError(
                "CONSTRUCTOR_INVOCATION_FAILED",
                "Failed to invoke constructor - parameter type mismatch",
            )
        }
    }

    private fun dispatchCommand(command: Any) {
        commandGateway.sendAndWait<Any>(command, 10, TimeUnit.SECONDS)
    }
}
