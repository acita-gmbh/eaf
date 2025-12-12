package de.acci.dcm.domain.exceptions

import de.acci.dcm.domain.vmrequest.VmRequestStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InvalidStateExceptionTest {

    @Test
    fun `should be a RuntimeException`() {
        val exception = InvalidStateException(
            currentState = VmRequestStatus.APPROVED,
            expectedState = VmRequestStatus.PENDING,
            operation = "cancel"
        )
        assertTrue(exception is RuntimeException)
    }

    @Test
    fun `should contain current state, expected state, and operation`() {
        val exception = InvalidStateException(
            currentState = VmRequestStatus.APPROVED,
            expectedState = VmRequestStatus.PENDING,
            operation = "cancel"
        )

        assertEquals(VmRequestStatus.APPROVED, exception.currentState)
        assertEquals(VmRequestStatus.PENDING, exception.expectedState)
        assertEquals("cancel", exception.operation)
    }

    @Test
    fun `should have descriptive message`() {
        val exception = InvalidStateException(
            currentState = VmRequestStatus.APPROVED,
            expectedState = VmRequestStatus.PENDING,
            operation = "cancel"
        )

        assertTrue(exception.message!!.contains("cancel"))
        assertTrue(exception.message!!.contains("APPROVED"))
        assertTrue(exception.message!!.contains("PENDING"))
    }

    @Test
    fun `should support nullable expected state for terminal state errors`() {
        val exception = InvalidStateException(
            currentState = VmRequestStatus.CANCELLED,
            expectedState = null,
            operation = "cancel"
        )

        assertEquals(VmRequestStatus.CANCELLED, exception.currentState)
        assertEquals(null, exception.expectedState)
    }
}
