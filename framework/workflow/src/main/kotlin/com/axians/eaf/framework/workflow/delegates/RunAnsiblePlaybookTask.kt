package com.axians.eaf.framework.workflow.delegates

import com.axians.eaf.framework.security.tenant.TenantContext
import com.axians.eaf.framework.workflow.ansible.AnsibleExecutionException
import com.axians.eaf.framework.workflow.ansible.AnsibleExecutor
import com.axians.eaf.framework.workflow.observability.FlowableMetrics
import org.flowable.engine.delegate.BpmnError
import org.flowable.engine.delegate.DelegateExecution
import org.flowable.engine.delegate.JavaDelegate
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * RunAnsiblePlaybookTask - Flowable JavaDelegate for Ansible Playbook Execution
 *
 * Executes Ansible playbooks from BPMN Service Tasks, replicating the core
 * functionality of the legacy Dockets system. This delegate bridges BPMN workflows
 * with infrastructure automation via Ansible.
 *
 * **BPMN Configuration Example:**
 * ```xml
 * <serviceTask id="ansibleTask" flowable:delegateExpression="${runAnsiblePlaybookTask}">
 *   <extensionElements>
 *     <!-- Process variables required: -->
 *     <!-- - tenantId: String (MANDATORY for isolation) -->
 *     <!-- - playbookPath: String (MANDATORY - path to .yml playbook) -->
 *     <!-- - inventory: String (optional - Ansible inventory file) -->
 *     <!-- - extraVars: Map<String, Any> (optional - playbook variables) -->
 *   </extensionElements>
 * </serviceTask>
 * ```
 *
 * **Required Process Variables:**
 * - `tenantId` (String): Tenant identifier for multi-tenant isolation (MANDATORY)
 * - `playbookPath` (String): Path to Ansible playbook on remote host (MANDATORY)
 * - `inventory` (String, optional): Ansible inventory file path
 * - `extraVars` (Map<String, Any>, optional): Variables passed to playbook via --extra-vars
 *
 * **Output Variables** (set in process context after execution):
 * - `ansibleExitCode` (Int): Playbook exit code
 * - `ansibleStdout` (String): Standard output from playbook
 * - `ansibleStderr` (String): Standard error from playbook
 *
 * **Error Handling:**
 * Ansible failures throw BpmnError for error boundary event routing (Story 6.5).
 * Error codes:
 * - `MISSING_VARIABLE`: Required process variable not found
 * - `TENANT_MISMATCH`: Tenant context doesn't match process variable
 * - `ANSIBLE_FAILED`: Ansible playbook execution failed (non-zero exit code)
 *
 * **Observability:**
 * Records Prometheus metrics via FlowableMetrics:
 * - Process duration (flowable.process.duration)
 * - BPMN error rates (flowable.process.errors)
 * - Structured logging with playbook name, duration, tenant ID (Story 5.1 patterns)
 *
 * Story 6.4 (Task 3, Task 5) - Ansible Service Task Adapter
 *
 * @param ansibleExecutor SSH-based Ansible execution utility
 * @param tenantContext Tenant context for multi-tenant isolation validation
 * @param flowableMetrics Prometheus metrics for workflow monitoring
 */
@Component("runAnsiblePlaybookTask") // Bean name for delegateExpression
@ConditionalOnProperty(
    prefix = "eaf.workflow",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class RunAnsiblePlaybookTask(
    private val ansibleExecutor: AnsibleExecutor,
    private val tenantContext: TenantContext,
    private val flowableMetrics: FlowableMetrics,
) : JavaDelegate {
    private val logger: Logger = LoggerFactory.getLogger(RunAnsiblePlaybookTask::class.java)

    /**
     * Execute Ansible playbook from BPMN Service Task.
     *
     * **Execution Flow** (Tasks 3 & 5):
     * 1. Validate tenant isolation (Task 3, Subtask 3.4)
     * 2. Extract BPMN variables with type-safe validation (Task 3, Subtasks 3.3, 3.5)
     * 3. Record start time for metrics (Task 5, Subtask 5.1)
     * 4. Execute Ansible playbook via SSH
     * 5. Record process duration metric (Task 5, Subtask 5.2)
     * 6. Store results in process variables
     * 7. Handle errors with BpmnError (Task 3, Subtask 3.6; Task 5, Subtask 5.3)
     *
     * @param execution Flowable DelegateExecution providing process context and variables
     * @throws BpmnError if required variables missing, tenant mismatch, or Ansible fails
     */
    override fun execute(execution: DelegateExecution) {
        // Task 5, Subtask 5.1: Record start time for duration metrics
        val startTime = System.currentTimeMillis()

        try {
            // Task 3, Subtask 3.4: CRITICAL - Fail-closed tenant validation
            validateTenantIsolation(execution)

            // Task 3, Subtasks 3.3 & 3.5: Extract and validate BPMN variables
            val playbookPath = extractRequiredVariable<String>(execution, "playbookPath")
            val inventory = execution.getVariable("inventory") as? String
            val extraVars =
                @Suppress("UNCHECKED_CAST")
                (execution.getVariable("extraVars") as? Map<String, Any>) ?: emptyMap()
            val tenantId = tenantContext.getCurrentTenantId()

            // Task 5, Subtask 5.4: Structured logging (Story 5.1 patterns)
            logger.info(
                "Executing Ansible playbook [playbook={}, inventory={}, tenant={}, processInstanceId={}]",
                playbookPath,
                inventory ?: "default",
                tenantId,
                execution.processInstanceId,
            )

            // Execute Ansible playbook via SSH
            val result =
                ansibleExecutor.executePlaybook(
                    playbookPath = playbookPath,
                    inventory = inventory,
                    extraVars = extraVars,
                    tenantId = tenantId,
                )

            // Task 5, Subtask 5.2: Record successful process duration
            val duration = System.currentTimeMillis() - startTime
            flowableMetrics.recordProcessDuration(
                processInstanceId = execution.processInstanceId,
                durationMs = duration,
                processKey = execution.processDefinitionId,
            )

            // Store Ansible results in process variables for downstream tasks
            execution.setVariable("ansibleExitCode", result.exitCode)
            execution.setVariable("ansibleStdout", result.stdout)
            execution.setVariable("ansibleStderr", result.stderr)

            // Task 5, Subtask 5.4: Success logging with metrics
            logger.info(
                "Ansible playbook succeeded [playbook={}, duration={}ms, tenant={}, exitCode={}]",
                playbookPath,
                duration,
                tenantId,
                result.exitCode,
            )

            // TODO Task 5, Subtask 5.5: OpenTelemetry span correlation (requires Story 5.3 integration)
        } catch (
            @Suppress("SwallowedException")
            ex: AnsibleExecutionException,
        ) {
            // Swallowed exception justification:
            // BpmnError (Flowable API) doesn't support cause chaining. Original exception
            // is fully logged above (line 156-163) with all context preserved. This conversion
            // is necessary for BPMN error boundary event routing (Story 6.5 prerequisite).

            // Task 5, Subtask 5.3: Record BPMN error metric for rollback monitoring
            flowableMetrics.recordBpmnError(ex.errorCode, execution.processDefinitionId)

            // Task 5, Subtask 5.4: Error logging with structured context
            logger.error(
                "Ansible execution failed [playbook={}, errorCode={}, exitCode={}, tenant={}]",
                execution.getVariable("playbookPath"),
                ex.errorCode,
                ex.exitCode,
                tenantContext.current(),
                ex,
            )

            // Task 3, Subtask 3.6: Throw BpmnError for error boundary event routing (Story 6.5)
            throw BpmnError(
                ex.errorCode,
                "Ansible execution failed: ${ex.message}",
            )
        }
        // Note: BpmnError from validation failures propagates automatically (no catch needed)
    }

    /**
     * Validates tenant isolation between process context and TenantContext.
     *
     * **Security Requirement** (Task 3, Subtask 3.4):
     * Fail-closed validation ensures tenant context matches process tenant variable.
     * Prevents cross-tenant workflow execution.
     *
     * @throws BpmnError if tenant validation fails (MISSING_VARIABLE or TENANT_MISMATCH)
     */
    private fun validateTenantIsolation(execution: DelegateExecution) {
        val currentTenant = tenantContext.getCurrentTenantId() // Throws if missing
        val processTenant =
            execution.getVariable("tenantId") as? String
                ?: throw BpmnError(
                    "MISSING_VARIABLE",
                    "Required variable 'tenantId' not found in process context",
                )

        // Fail-closed: Throw BpmnError for tenant mismatch (CWE-209 protection - generic message)
        if (currentTenant != processTenant) {
            throw BpmnError(
                "TENANT_MISMATCH",
                "Access denied: tenant context mismatch",
            )
        }
    }

    /**
     * Extracts a required BPMN variable with type-safe validation.
     *
     * **Type-Safe Validation** (Task 3, Subtask 3.5):
     * Ensures variable exists and has expected type, failing with BpmnError if not.
     *
     * @param T Expected variable type
     * @param execution Flowable DelegateExecution
     * @param variableName Name of the required variable
     * @return Variable value cast to type T
     * @throws BpmnError if variable missing or wrong type
     */
    private inline fun <reified T> extractRequiredVariable(
        execution: DelegateExecution,
        variableName: String,
    ): T {
        val value =
            execution.getVariable(variableName)
                ?: throw BpmnError(
                    "MISSING_VARIABLE",
                    "Required variable '$variableName' not found",
                )

        if (value !is T) {
            throw BpmnError(
                "INVALID_VARIABLE_TYPE",
                "Variable '$variableName' has wrong type (expected ${T::class.simpleName})",
            )
        }

        return value
    }
}
