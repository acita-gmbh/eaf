package com.axians.eaf.framework.workflow.delegates

import org.flowable.engine.delegate.DelegateExecution
import org.flowable.engine.delegate.JavaDelegate
import org.springframework.stereotype.Component

/**
 * Test-only JavaDelegate that sets command class name + parameters for compensation (Story 6.5).
 *
 * **ARCH-001 Remediation**: Sets commandClassName (FQN) instead of commandType to support
 * pure reflection pattern without compile-time dependencies on product types.
 *
 * **Purpose**: Enables BPMN compensation flows to switch from creation to cancellation
 * command without requiring scripting engines (Groovy/JavaScript).
 *
 * **Usage in BPMN**:
 * ```xml
 * <serviceTask id="setCompensationCommand"
 *              flowable:delegateExpression="${setCommandTypeDelegate}">
 *   <extensionElements>
 *     <flowable:field name="targetCommandClassName">
 *       <flowable:string>com.axians.eaf.framework.workflow.test.CancelTestEntityCommand</flowable:string>
 *     </flowable:field>
 *     <flowable:field name="targetConstructorParameters">
 *       <flowable:string>entityId,tenantId,cancellationReason,operator</flowable:string>
 *     </flowable:field>
 *   </extensionElements>
 * </serviceTask>
 * ```
 *
 * Story 6.5 (Task 4.1) - Framework test infrastructure, ARCH-001 remediation
 */
@Component
class SetCommandTypeDelegate : JavaDelegate {
    override fun execute(execution: DelegateExecution) {
        val targetCommandClassName =
            execution.getVariable("targetCommandClassName") as? String
                ?: "com.axians.eaf.framework.workflow.test.CancelTestEntityCommand"

        val targetConstructorParameters =
            execution.getVariable("targetConstructorParameters") as? String
                ?: "entityId,tenantId,cancellationReason,operator"

        execution.setVariable("commandClassName", targetCommandClassName)
        execution.setVariable("constructorParameters", targetConstructorParameters.split(",").map { it.trim() })
    }
}
