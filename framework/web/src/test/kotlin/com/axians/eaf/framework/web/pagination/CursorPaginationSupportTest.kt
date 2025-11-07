package com.axians.eaf.framework.web.pagination

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
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
class CursorPaginationSupportTest :
    FunSpec({

        context("encodeCursor (AC 5)") {
            test("should encode Instant as Base64 cursor string") {
                // Given
                val timestamp = Instant.parse("2025-11-07T10:30:00Z")

                // When
                val cursor = CursorPaginationSupport.encodeCursor(timestamp)

                // Then - Cursor should be non-empty Base64 string
                cursor shouldNotBe ""
                cursor shouldNotBe timestamp.toString() // Should be encoded (opaque)
            }

            test("should produce consistent cursors for same timestamp") {
                // Given
                val timestamp = Instant.parse("2025-11-07T10:30:00Z")

                // When
                val cursor1 = CursorPaginationSupport.encodeCursor(timestamp)
                val cursor2 = CursorPaginationSupport.encodeCursor(timestamp)

                // Then - Same timestamp → Same cursor (deterministic)
                cursor1 shouldBe cursor2
            }

            test("should produce different cursors for different timestamps") {
                // Given
                val timestamp1 = Instant.parse("2025-11-07T10:30:00Z")
                val timestamp2 = Instant.parse("2025-11-07T10:31:00Z")

                // When
                val cursor1 = CursorPaginationSupport.encodeCursor(timestamp1)
                val cursor2 = CursorPaginationSupport.encodeCursor(timestamp2)

                // Then - Different timestamps → Different cursors
                cursor1 shouldNotBe cursor2
            }
        }

        context("decodeCursor (AC 5)") {
            test("should decode valid cursor to original Instant") {
                // Given
                val originalTimestamp = Instant.parse("2025-11-07T10:30:00Z")
                val cursor = CursorPaginationSupport.encodeCursor(originalTimestamp)

                // When
                val decodedTimestamp = CursorPaginationSupport.decodeCursor(cursor)

                // Then - Round-trip should preserve exact timestamp
                decodedTimestamp shouldBe originalTimestamp
            }

            test("should decode cursor with nanosecond precision") {
                // Given - Instant with nanosecond precision
                val originalTimestamp = Instant.parse("2025-11-07T10:30:00.123456789Z")
                val cursor = CursorPaginationSupport.encodeCursor(originalTimestamp)

                // When
                val decodedTimestamp = CursorPaginationSupport.decodeCursor(cursor)

                // Then - Nanosecond precision preserved
                decodedTimestamp shouldBe originalTimestamp
                decodedTimestamp.nano shouldBe 123456789
            }

            test("should throw IllegalArgumentException for invalid Base64") {
                // Given - Invalid Base64 string
                val invalidCursor = "not-valid-base64!!!"

                // When/Then
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        CursorPaginationSupport.decodeCursor(invalidCursor)
                    }

                exception.message shouldContain "Invalid cursor format"
                exception.message shouldContain "malformed Base64"
            }

            test("should throw IllegalArgumentException for valid Base64 but invalid timestamp") {
                // Given - Valid Base64 but NOT a valid ISO-8601 timestamp
                val invalidTimestamp = "not-a-timestamp"
                val cursor =
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(invalidTimestamp.toByteArray())

                // When/Then
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        CursorPaginationSupport.decodeCursor(cursor)
                    }

                exception.message shouldContain "Invalid cursor format"
                exception.message shouldContain "malformed timestamp"
            }

            test("should throw IllegalArgumentException for empty cursor") {
                // Given - Empty cursor string
                val emptyCursor = ""

                // When/Then
                shouldThrow<IllegalArgumentException> {
                    CursorPaginationSupport.decodeCursor(emptyCursor)
                }
            }
        }

        context("Round-trip encoding/decoding (AC 5)") {
            test("should preserve timestamp through encode → decode cycle") {
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
                    decoded shouldBe original
                }
            }
        }

        context("Cursor format (opaqueness)") {
            test("cursor should be opaque (clients cannot parse directly)") {
                // Given
                val timestamp = Instant.parse("2025-11-07T10:30:00Z")
                val cursor = CursorPaginationSupport.encodeCursor(timestamp)

                // Then - Cursor should NOT be the raw timestamp (opaque)
                cursor shouldNotBe timestamp.toString()
                cursor shouldNotBe "2025-11-07T10:30:00Z"

                // Cursor should look like Base64 (alphanumeric + =)
                cursor.matches(Regex("[A-Za-z0-9+/=]+")) shouldBe true
            }
        }
    })
