package de.acci.eaf.core.error

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class DomainErrorTest {

    @Test
    fun `equality holds for same fields`() {
        val a = DomainError.ValidationFailed(field = "name", message = "required")
        val b = DomainError.ValidationFailed(field = "name", message = "required")
        assertEquals(a, b)
    }

    @Test
    fun `equality differs when fields differ`() {
        val a = DomainError.ValidationFailed(field = "name", message = "required")
        val b = DomainError.ValidationFailed(field = "email", message = "required")
        assertTrue(a != b)
    }

    @Test
    fun `quota exceeded stores context`() {
        val err = DomainError.QuotaExceeded(current = 5, max = 4)
        assertEquals(5, err.current)
        assertEquals(4, err.max)
    }

    @Test
    fun `all getters return provided values`() {
        val validation = DomainError.ValidationFailed(field = "name", message = "required")
        val notFound = DomainError.ResourceNotFound(type = "Order", id = "o-1")
        val invalid = DomainError.InvalidStateTransition(from = "draft", to = "published", action = "publish")
        val infra = DomainError.InfrastructureError(cause = "io-timeout")

        assertEquals("name", validation.field)
        assertEquals("required", validation.message)
        assertEquals("Order", notFound.type)
        assertEquals("o-1", notFound.id)
        assertEquals("draft", invalid.from)
        assertEquals("published", invalid.to)
        assertEquals("publish", invalid.action)
        assertEquals("io-timeout", infra.cause)
    }
}
