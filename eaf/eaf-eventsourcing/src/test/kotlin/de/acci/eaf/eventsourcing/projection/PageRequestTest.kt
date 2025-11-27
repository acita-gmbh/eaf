package de.acci.eaf.eventsourcing.projection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PageRequestTest {

    @Test
    fun `should create PageRequest with default values`() {
        val request = PageRequest()

        assertEquals(0, request.page)
        assertEquals(20, request.size)
        assertEquals(0L, request.offset)
    }

    @Test
    fun `should create PageRequest with custom values`() {
        val request = PageRequest(page = 2, size = 50)

        assertEquals(2, request.page)
        assertEquals(50, request.size)
        assertEquals(100L, request.offset)
    }

    @Test
    fun `should calculate correct offset for first page`() {
        val request = PageRequest(page = 0, size = 10)

        assertEquals(0L, request.offset)
    }

    @Test
    fun `should calculate correct offset for subsequent pages`() {
        val request = PageRequest(page = 5, size = 25)

        assertEquals(125L, request.offset)
    }

    @Test
    fun `should throw exception for negative page`() {
        val exception = assertThrows<IllegalArgumentException> {
            PageRequest(page = -1, size = 20)
        }

        assertEquals("Page index must not be negative, was: -1", exception.message)
    }

    @Test
    fun `should throw exception for zero size`() {
        val exception = assertThrows<IllegalArgumentException> {
            PageRequest(page = 0, size = 0)
        }

        assertEquals("Page size must be positive, was: 0", exception.message)
    }

    @Test
    fun `should throw exception for negative size`() {
        val exception = assertThrows<IllegalArgumentException> {
            PageRequest(page = 0, size = -5)
        }

        assertEquals("Page size must be positive, was: -5", exception.message)
    }

    @Test
    fun `should allow page zero with valid size`() {
        val request = PageRequest(page = 0, size = 1)

        assertEquals(0, request.page)
        assertEquals(1, request.size)
    }

    @Test
    fun `should handle large page numbers`() {
        val request = PageRequest(page = 1000, size = 100)

        assertEquals(100_000L, request.offset)
    }
}
