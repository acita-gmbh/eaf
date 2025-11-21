package com.axians.eaf.framework.web.pagination

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit tests for CursorPaginationSupport (Story 2.9, AC 5).
 *
 * Validates cursor encoding/decoding for stable pagination.
 *
 * **Test Coverage:**
 * - AC 5: CursorPaginationSupport.kt utility for cursor-based pagination
 * - Cursor encoding: Instant → Base64 string
 * - Cursor decoding: Base64 string → Instant
 * - Error handling: Invalid cursors, malformed Base64, invalid timestamps
 *
 * **Test Strategy:**
 * - Unit tests (no dependencies) - fast, isolated
 * - Test round-trip encoding/decoding
 * - Test error cases (invalid format, malformed data)
 *
 * **References:**
 * - Story 2.9: REST API Foundation
 * - Architecture: Section 15 (API Contracts - Pagination)
 */
class CursorPaginationSupportTest {
    @Nested
    inner class EncodeCursor {
        @Test
        fun `should encode Instant as Base64 cursor string`() {
            // Given
            val timestamp = Instant.parse("2025-11-07T10:30:00Z")

            // When
            val cursor = CursorPaginationSupport.encodeCursor(timestamp)

            // Then - Cursor should be non-empty Base64 string
            assertThat(cursor).isNotEmpty()
            assertThat(cursor).isNotEqualTo(timestamp.toString()) // Should be encoded (opaque)
        }

        @Test
        fun `should produce consistent cursors for same timestamp`() {
            // Given
            val timestamp = Instant.parse("2025-11-07T10:30:00Z")

            // When
            val cursor1 = CursorPaginationSupport.encodeCursor(timestamp)
            val cursor2 = CursorPaginationSupport.encodeCursor(timestamp)

            // Then - Same timestamp → Same cursor (deterministic)
            assertThat(cursor1).isEqualTo(cursor2)
        }

        @Test
        fun `should produce different cursors for different timestamps`() {
            // Given
            val timestamp1 = Instant.parse("2025-11-07T10:30:00Z")
            val timestamp2 = Instant.parse("2025-11-07T10:31:00Z")

            // When
            val cursor1 = CursorPaginationSupport.encodeCursor(timestamp1)
            val cursor2 = CursorPaginationSupport.encodeCursor(timestamp2)

            // Then - Different timestamps → Different cursors
            assertThat(cursor1).isNotEqualTo(cursor2)
        }
    }

    @Nested
    inner class DecodeCursor {
        @Test
        fun `should decode valid cursor to original Instant`() {
            // Given
            val originalTimestamp = Instant.parse("2025-11-07T10:30:00Z")
            val cursor = CursorPaginationSupport.encodeCursor(originalTimestamp)

            // When
            val decodedTimestamp = CursorPaginationSupport.decodeCursor(cursor)

            // Then - Round-trip should preserve exact timestamp
            assertThat(decodedTimestamp).isEqualTo(originalTimestamp)
        }

        @Test
        fun `should decode cursor with nanosecond precision`() {
            // Given - Instant with nanosecond precision
            val originalTimestamp = Instant.parse("2025-11-07T10:30:00.123456789Z")
            val cursor = CursorPaginationSupport.encodeCursor(originalTimestamp)

            // When
            val decodedTimestamp = CursorPaginationSupport.decodeCursor(cursor)

            // Then - Nanosecond precision preserved
            assertThat(decodedTimestamp).isEqualTo(originalTimestamp)
            assertThat(decodedTimestamp.nano).isEqualTo(123456789)
        }

        @Test
        fun `should throw IllegalArgumentException for invalid Base64`() {
            // Given - Invalid Base64 string
            val invalidCursor = "not-valid-base64!!!"

            // When/Then
            assertThatThrownBy {
                CursorPaginationSupport.decodeCursor(invalidCursor)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid cursor format")
                .hasMessageContaining("malformed Base64")
        }

        @Test
        fun `should throw IllegalArgumentException for valid Base64 but invalid timestamp`() {
            // Given - Valid Base64 but NOT a valid ISO-8601 timestamp
            val invalidTimestamp = "not-a-timestamp"
            val cursor =
                java.util.Base64
                    .getEncoder()
                    .encodeToString(invalidTimestamp.toByteArray())

            // When/Then
            assertThatThrownBy {
                CursorPaginationSupport.decodeCursor(cursor)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid cursor format")
                .hasMessageContaining("malformed timestamp")
        }

        @Test
        fun `should throw IllegalArgumentException for empty cursor`() {
            // Given - Empty cursor string
            val emptyCursor = ""

            // When/Then
            assertThatThrownBy {
                CursorPaginationSupport.decodeCursor(emptyCursor)
            }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class RoundTripEncodingDecoding {
        @Test
        fun `should preserve timestamp through encode decode cycle`() {
            // Given - Various timestamps
            val timestamps =
                listOf(
                    Instant.parse("2025-11-07T10:30:00Z"),
                    Instant.parse("2025-01-01T00:00:00Z"),
                    Instant.parse("2025-12-31T23:59:59.999999999Z"),
                    Instant.now(),
                )

            timestamps.forEach { original ->
                // When
                val cursor = CursorPaginationSupport.encodeCursor(original)
                val decoded = CursorPaginationSupport.decodeCursor(cursor)

                // Then - Round-trip preserves exact value
                assertThat(decoded).isEqualTo(original)
            }
        }
    }

    @Nested
    inner class CursorFormat {
        @Test
        fun `cursor should be opaque (clients cannot parse directly)`() {
            // Given
            val timestamp = Instant.parse("2025-11-07T10:30:00Z")
            val cursor = CursorPaginationSupport.encodeCursor(timestamp)

            // Then - Cursor should NOT be the raw timestamp (opaque)
            assertThat(cursor).isNotEqualTo(timestamp.toString())
            assertThat(cursor).isNotEqualTo("2025-11-07T10:30:00Z")

            // Cursor should look like Base64 (alphanumeric + =)
            assertThat(cursor.matches(Regex("[A-Za-z0-9+/=]+"))).isTrue()
        }
    }
}
