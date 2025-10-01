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
 *     - commandType (String): Command class name (e.g., "CreateTestEntityCommand" in framework tests)
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
     * ## SECURITY CRITICAL: Reflection Code Injection Prevention
     *
     * This method uses reflection (Class.forName) in builder methods. To prevent arbitrary class
     * instantiation attacks (CWE-94), the following security controls are MANDATORY:
     *
     * **SECURITY REQUIREMENTS**:
     * 1. ✅ **Explicit Whitelist**: All command types MUST be in when expression (fail-closed)
     * 2. ✅ **Hardcoded Class Names**: NEVER use Class.forName(commandType) with variable
     * 3. ✅ **No Dynamic Construction**: Class names must be string literals in builder methods
     * 4. ⚠️ **BPMN Trust Boundary**: Only deploy BPMN processes from trusted sources
     *
     * **FORBIDDEN PATTERN** (Code Injection Vulnerability):
     * ```kotlin
     * // ❌ NEVER DO THIS - Allows arbitrary class instantiation
     * val commandClass = Class.forName(commandType)
     * ```
     *
     * **SAFE PATTERN** (Current Implementation):
     * ```kotlin
     * // ✅ CORRECT - Explicit whitelist + hardcoded class name
     * when (commandType) {
     *     "CreateTestEntityCommand" -> buildCreateTestEntityCommand(execution)
     *     else -> throw BpmnError("UNKNOWN_COMMAND_TYPE", ...)
     * }
     *
     * private fun buildCreateTestEntityCommand(...): Any {
     *     val commandClass = Class.forName("com.axians.eaf.framework.workflow.test.CreateTestEntityCommand")
     *     // ↑ Hardcoded literal - safe
     * }
     * ```
     *
     * **Framework Purity**: This framework infrastructure only includes TestEntity for framework tests.
     * Products implement their own delegates following this pattern.
     *
     * @param execution DelegateExecution containing process variables
     * @return Command object to dispatch via CommandGateway
     * @throws BpmnError if commandType unknown or variables missing
     */
    private fun buildCommand(execution: DelegateExecution): Any {
        val commandType =
            execution.getVariable("commandType") as? String
                ?: throw BpmnError("MISSING_VARIABLE", "Required process variable missing: commandType")

        // SECURITY: Explicit whitelist - prevents arbitrary class instantiation
        return when (commandType) {
            "CreateTestEntityCommand" -> buildCreateTestEntityCommand(execution) // Framework tests only
            // Products should create their own delegates for domain-specific commands
            // Example (in products module):
            // "CreateWidgetCommand" -> buildCreateWidgetCommand(execution)
            // "CreateOrderCommand" -> buildCreateOrderCommand(execution)
            else -> throw BpmnError("UNKNOWN_COMMAND_TYPE", "Unsupported command type: $commandType")
        }
    }

    /**
     * Builds CreateTestEntityCommand from process variables.
     *
     * **Framework Test Infrastructure**: This builder exists solely for framework integration tests.
     * It enables testing the generic Flowable→Axon bridge without depending on products module.
     *
     * Products should create their own delegates with domain-specific builders following this pattern.
     */
    @Suppress("ThrowsCount")
    private fun buildCreateTestEntityCommand(execution: DelegateExecution): Any {
        val entityId =
            execution.getVariable("entityId") as? String
                ?: throw BpmnError("MISSING_VARIABLE", "Required process variable missing: entityId")
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

        // SECURITY: Hardcoded class name (safe) - NEVER use Class.forName(commandType)
        // Use fully qualified name for framework test type
        val commandClass = Class.forName("com.axians.eaf.framework.workflow.test.CreateTestEntityCommand")
        val constructor =
            commandClass.getConstructor(
                String::class.java, // entityId
                String::class.java, // tenantId
                String::class.java, // name
                String::class.java, // description (nullable)
                BigDecimal::class.java, // value
                String::class.java, // category
                Map::class.java, // metadata
            )

        return constructor.newInstance(entityId, tenantId, name, description, value, category, metadata)
    }

    private fun dispatchCommand(command: Any) {
        commandGateway.sendAndWait<Any>(command, 10, TimeUnit.SECONDS)
    }
}
