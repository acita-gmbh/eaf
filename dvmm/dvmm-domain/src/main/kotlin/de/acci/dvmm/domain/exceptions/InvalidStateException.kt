package de.acci.dvmm.domain.exceptions

import de.acci.dvmm.domain.vmrequest.VmRequestStatus

/**
 * Exception thrown when an operation is attempted on an aggregate in an invalid state.
 *
 * Used for domain rule violations where an operation requires a specific state,
 * but the aggregate is in a different (incompatible) state.
 *
 * Example: Attempting to cancel a VM request that is already APPROVED.
 */
public class InvalidStateException(
    /** The current state of the aggregate */
    public val currentState: VmRequestStatus,
    /** The expected state(s) for this operation (null if terminal state) */
    public val expectedState: VmRequestStatus?,
    /** The name of the operation that was attempted */
    public val operation: String
) : RuntimeException(
    buildMessage(
        currentState = currentState,
        expectedState = expectedState,
        operation = operation
    )
) {
    private companion object {
        private fun buildMessage(
            currentState: VmRequestStatus,
            expectedState: VmRequestStatus?,
            operation: String
        ): String = if (expectedState != null) {
            "Cannot perform '$operation': expected state '${expectedState.name}', but current state is '${currentState.name}'"
        } else {
            "Cannot perform '$operation': current state '${currentState.name}' does not allow this operation"
        }
    }
}
