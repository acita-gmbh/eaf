package de.acci.eaf.eventsourcing.projection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PagedResponseTest {

    @Test
    fun `should calculate totalPages correctly for exact division`() {
        val response = PagedResponse(
            items = listOf("a", "b", "c"),
            page = 0,
            size = 10,
            totalElements = 100
        )

        assertEquals(10, response.totalPages)
    }

    @Test
    fun `should calculate totalPages correctly with remainder`() {
        val response = PagedResponse(
            items = listOf("a"),
            page = 0,
            size = 10,
            totalElements = 25
        )

        assertEquals(3, response.totalPages)
    }

    @Test
    fun `should return zero totalPages for empty result`() {
        val response = PagedResponse<String>(
            items = emptyList(),
            page = 0,
            size = 10,
            totalElements = 0
        )

        assertEquals(0, response.totalPages)
    }

    @Test
    fun `should return one totalPage for single item`() {
        val response = PagedResponse(
            items = listOf("a"),
            page = 0,
            size = 10,
            totalElements = 1
        )

        assertEquals(1, response.totalPages)
    }

    @Test
    fun `should return one totalPage when totalElements equals size`() {
        val response = PagedResponse(
            items = (1..10).map { "item$it" },
            page = 0,
            size = 10,
            totalElements = 10
        )

        assertEquals(1, response.totalPages)
    }

    @Test
    fun `should indicate hasNext for first of multiple pages`() {
        val response = PagedResponse(
            items = listOf("a", "b"),
            page = 0,
            size = 10,
            totalElements = 25
        )

        assertTrue(response.hasNext)
    }

    @Test
    fun `should indicate no hasNext for last page`() {
        val response = PagedResponse(
            items = listOf("a"),
            page = 2,
            size = 10,
            totalElements = 25
        )

        assertFalse(response.hasNext)
    }

    @Test
    fun `should indicate hasPrevious for non-first page`() {
        val response = PagedResponse(
            items = listOf("a", "b"),
            page = 1,
            size = 10,
            totalElements = 25
        )

        assertTrue(response.hasPrevious)
    }

    @Test
    fun `should indicate no hasPrevious for first page`() {
        val response = PagedResponse(
            items = listOf("a", "b"),
            page = 0,
            size = 10,
            totalElements = 25
        )

        assertFalse(response.hasPrevious)
    }

    @Test
    fun `should indicate isFirst for page zero`() {
        val response = PagedResponse(
            items = listOf("a"),
            page = 0,
            size = 10,
            totalElements = 25
        )

        assertTrue(response.isFirst)
        assertFalse(response.isLast)
    }

    @Test
    fun `should indicate isLast for last page`() {
        val response = PagedResponse(
            items = listOf("a"),
            page = 2,
            size = 10,
            totalElements = 25
        )

        assertTrue(response.isLast)
        assertFalse(response.isFirst)
    }

    @Test
    fun `should return correct numberOfElements`() {
        val response = PagedResponse(
            items = listOf("a", "b", "c"),
            page = 0,
            size = 10,
            totalElements = 25
        )

        assertEquals(3, response.numberOfElements)
    }

    @Test
    fun `should create empty response with defaults`() {
        val response = PagedResponse.empty<String>()

        assertEquals(emptyList<String>(), response.items)
        assertEquals(0, response.page)
        assertEquals(20, response.size)
        assertEquals(0L, response.totalElements)
        assertEquals(0, response.totalPages)
    }

    @Test
    fun `should create empty response with custom page request`() {
        val request = PageRequest(page = 5, size = 50)
        val response = PagedResponse.empty<String>(request)

        assertEquals(emptyList<String>(), response.items)
        assertEquals(5, response.page)
        assertEquals(50, response.size)
        assertEquals(0L, response.totalElements)
    }

    @Test
    fun `should handle single page scenario correctly`() {
        val response = PagedResponse(
            items = listOf("a", "b", "c"),
            page = 0,
            size = 10,
            totalElements = 3
        )

        assertTrue(response.isFirst)
        assertTrue(response.isLast)
        assertFalse(response.hasNext)
        assertFalse(response.hasPrevious)
        assertEquals(1, response.totalPages)
    }

    @Test
    fun `should handle large totalElements`() {
        val response = PagedResponse(
            items = listOf("a"),
            page = 0,
            size = 100,
            totalElements = 1_000_000
        )

        assertEquals(10_000, response.totalPages)
    }
}
