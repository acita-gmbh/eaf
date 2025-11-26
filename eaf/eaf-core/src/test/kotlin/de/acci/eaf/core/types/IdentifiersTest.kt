package de.acci.eaf.core.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import de.acci.eaf.core.error.InvalidIdentifierFormatException

class IdentifiersTest {

    @Test
    fun `generate produces unique ids`() {
        val a = TenantId.generate()
        val b = TenantId.generate()
        assertNotEquals(a, b)
    }

    @Test
    fun `fromString round-trips`() {
        val id = UUID.randomUUID().toString()
        val tenant = TenantId.fromString(id)
        assertEquals(id, tenant.value.toString())
    }

    @Test
    fun `invalid uuid throws`() {
        val ex = assertThrows(InvalidIdentifierFormatException::class.java) {
            UserId.fromString("not-a-uuid")
        }
        assertEquals("UserId", ex.identifierType)
        assertEquals("not-a-uuid", ex.raw)
    }

    @Test
    fun `correlation id generate is non-empty`() {
        val cid = CorrelationId.generate()
        assertTrue(cid.value.toString().isNotBlank())
    }

    @Test
    fun `correlation id round-trip`() {
        val original = CorrelationId.generate()
        val parsed = CorrelationId.fromString(original.value.toString())
        assertEquals(original, parsed)
    }
}
