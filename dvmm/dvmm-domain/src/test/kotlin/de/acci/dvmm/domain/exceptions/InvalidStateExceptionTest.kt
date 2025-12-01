package de.acci.dvmm.domain.exceptions

import de.acci.dvmm.domain.vmrequest.VmRequestStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InvalidStateExceptionTest {

    @Test
    fun `should be a RuntimeException`() {
        val exception = InvalidStateException(
            currentState = VmRequestStatus.APPROVED.name,
            expectedState = VmRequestStatus.PENDING.name,
            operation = "cancel"
        )
        assertTrue(exception is RuntimeException)
    }

    @Test
    fun `should contain current state, expected state, and operation`() {
        val exception = InvalidStateException(
            currentState = "APPROVED",
            expectedState = "PENDING",
            operation = "cancel"
        )

        assertEquals("APPROVED", exception.currentState)
        assertEquals("PENDING", exception.expectedState)
        assertEquals("cancel", exception.operation)
    }

    @Test
    fun `should have descriptive message`() {
        val exception = InvalidStateException(
            currentState = "APPROVED",
            expectedState = "PENDING",
            operation = "cancel"
        )

        assertTrue(exception.message!!.contains("cancel"))
        assertTrue(exception.message!!.contains("APPROVED"))
        assertTrue(exception.message!!.contains("PENDING"))
    }

    @Test
    fun `should support nullable expected state for terminal state errors`() {
        val exception = InvalidStateException(
            currentState = "CANCELLED",
            expectedState = null,
            operation = "cancel"
        )

        assertEquals("CANCELLED", exception.currentState)
        assertEquals(null, exception.expectedState)
    }
}
