package com.axians.eaf.framework.workflow.delegates

import org.flowable.engine.delegate.DelegateExecution
import org.flowable.engine.delegate.JavaDelegate
import org.springframework.stereotype.Component

/**
 * Test-only JavaDelegate that sets commandType variable for compensation testing (Story 6.5).
 *
 * **Purpose**: Enables BPMN compensation flows to switch from creation command to
 * cancellation command without requiring scripting engines (Groovy/JavaScript) which
 * may not be available in test classpaths.
 *
 * **Usage in BPMN**:
 * ```xml
 * <serviceTask id="setCommandType"
 *              flowable:delegateExpression="${setCommandTypeDelegate}">
 *   <extensionElements>
 *     <flowable:field name="targetCommandType">
 *       <flowable:string>CancelTestEntityCommand</flowable:string>
 *     </flowable:field>
 *   </extensionElements>
 * </serviceTask>
 * ```
 *
 * Story 6.5 (Task 4.1) - Framework test infrastructure for E2E compensation testing
 */
@Component
class SetCommandTypeDelegate : JavaDelegate {
    override fun execute(execution: DelegateExecution) {
        val targetCommandType =
            execution.getVariable("targetCommandType") as? String
                ?: "CancelTestEntityCommand" // Default for compensation flows

        execution.setVariable("commandType", targetCommandType)
    }
}
