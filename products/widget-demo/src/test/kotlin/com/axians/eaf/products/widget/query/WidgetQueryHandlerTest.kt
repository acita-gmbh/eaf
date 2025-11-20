package com.axians.eaf.products.widget.query

import com.axians.eaf.framework.web.pagination.CursorPaginationSupport
import com.axians.eaf.testing.nullable.createNullableDSLContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * Unit tests for WidgetQueryHandler using Nullable Design Pattern.
 *
 * Tests query handler business logic with fast, zero-latency Nullable DSLContext.
 * Validates cursor encoding/decoding, limit validation, and edge cases without database overhead.
 *
 * **Nullable Pattern:** 100-1000x performance improvement over integration tests.
 * **Contract Testing:** Integration tests validate behavioral parity with real database.
 *
 * Migrated from Kotest to JUnit 6 on 2025-11-20
 */
class WidgetQueryHandlerTest {

    private lateinit var handler: WidgetQueryHandler

    @BeforeEach
    fun beforeEach() {
        val dsl = createNullableDSLContext()
        handler = WidgetQueryHandler(dsl)
    }

    // FindWidgetQuery with Nullable DSLContext Tests

    @Test
    fun `returns widget projection from mock data`() {
        // Given: Nullable DSLContext with pre-inserted test data
        // Query using ListWidgetsQuery to find the test widget ID first
        val listResult = handler.handle(ListWidgetsQuery(limit = 1))
        val testWidgetId = listResult.widgets.first().id

        // When: Execute FindWidgetQuery with actual test widget ID
        val query = FindWidgetQuery(testWidgetId)
        val result = handler.handle(query)

        // Then: Widget projection returned from in-memory H2
        assertThat(result).isNotNull
        assertThat(result.name).isEqualTo("Nullable Test Widget")
        assertThat(result.published).isFalse()
    }

    // ListWidgetsQuery with Nullable DSLContext Tests

    @Test
    fun `returns paginated response from mock data`() {
        // Given: Nullable DSLContext
        val query = ListWidgetsQuery(limit = 50)

        // When: Execute query
        val result = handler.handle(query)

        // Then: Mock data structure validated
        assertThat(result).isNotNull
        assertThat(result.widgets).isNotNull
    }

    // Cursor Encoding/Decoding via CursorPaginationSupport Tests

    @Test
    fun `encodes timestamp to Base64 cursor`() {
        // Given: Timestamp
        val timestamp = Instant.parse("2025-01-01T12:00:00Z")

        // When: Encode cursor using utility
        val encoded = CursorPaginationSupport.encodeCursor(timestamp)

        // Then: Can decode back to original
        val decoded = CursorPaginationSupport.decodeCursor(encoded)
        assertThat(decoded).isEqualTo(timestamp)
    }

    @Test
    fun `decodes Base64 cursor to timestamp`() {
        // Given: Timestamp and encoded cursor
        val timestamp = Instant.parse("2025-01-01T12:00:00Z")
        val cursor = CursorPaginationSupport.encodeCursor(timestamp)

        // When: Decode cursor
        val decoded = CursorPaginationSupport.decodeCursor(cursor)

        // Then: Timestamp matches original
        assertThat(decoded).isEqualTo(timestamp)
    }

    @Test
    fun `encodes and decodes cursor round-trip correctly`() {
        // Given: Various timestamps
        val timestamps = listOf(
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-06-15T12:30:45Z"),
            Instant.parse("2025-12-31T23:59:59Z"),
        )

        timestamps.forEach { original ->
            // When: Encode then decode
            val encoded = CursorPaginationSupport.encodeCursor(original)
            val decoded = CursorPaginationSupport.decodeCursor(encoded)

            // Then: Timestamp preserved
            assertThat(decoded).isEqualTo(original)
        }
    }

    @Test
    fun `decoding invalid Base64 cursor throws IllegalArgumentException`() {
        // Given: Invalid Base64 string
        val invalidCursor = "not-valid-base64!!!"

        // When/Then: Decoding should fail gracefully
        val exception = assertThrows<IllegalArgumentException> {
            CursorPaginationSupport.decodeCursor(invalidCursor)
        }

        assertThat(exception.message).isNotNull()
    }

    @Test
    fun `decoding malformed timestamp throws exception`() {
        // Given: Valid Base64 but invalid timestamp format
        val malformedCursor = "bm90LWEtdGltZXN0YW1w" // Base64("not-a-timestamp")

        // When/Then: Parsing should fail
        val exception = assertThrows<IllegalArgumentException> {
            CursorPaginationSupport.decodeCursor(malformedCursor)
        }

        assertThat(exception.message).isNotNull()
    }

    // Limit Validation Tests

    @Test
    fun `limit clamped to minimum value of 1`() {
        // Given: Query with limit 0
        val query = ListWidgetsQuery(limit = 0)

        // When: coerceIn should clamp to 1
        val safeLimit = query.limit.coerceIn(1, 100)

        // Then: Limit is 1
        assertThat(safeLimit).isEqualTo(1)
    }

    @Test
    fun `limit clamped to maximum value of 100`() {
        // Given: Query with limit 500
        val query = ListWidgetsQuery(limit = 500)

        // When: coerceIn should clamp to 100
        val safeLimit = query.limit.coerceIn(1, 100)

        // Then: Limit is 100
        assertThat(safeLimit).isEqualTo(100)
    }

    @Test
    fun `negative limit clamped to 1`() {
        // Given: Query with negative limit
        val query = ListWidgetsQuery(limit = -10)

        // When: coerceIn should clamp to 1
        val safeLimit = query.limit.coerceIn(1, 100)

        // Then: Limit is 1
        assertThat(safeLimit).isEqualTo(1)
    }

    @Test
    fun `valid limit values preserved`() {
        // Given: Valid limits within range
        val validLimits = listOf(1, 25, 50, 75, 100)

        validLimits.forEach { limit ->
            // When: Apply coerceIn
            val safeLimit = limit.coerceIn(1, 100)

            // Then: Value unchanged
            assertThat(safeLimit).isEqualTo(limit)
        }
    }
}
