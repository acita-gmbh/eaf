package de.acci.eaf.core.error

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DomainErrorTest {

    @Test
    fun `equality holds for same fields`() {
        val a = DomainError.ValidationFailed(field = "name", message = "required")
        val b = DomainError.ValidationFailed(field = "name", message = "required")
        assertEquals(a, b)
    }

    @Test
    fun `quota exceeded stores context`() {
        val err = DomainError.QuotaExceeded(current = 5, max = 4)
        assertEquals(5, err.current)
        assertEquals(4, err.max)
    }
}
