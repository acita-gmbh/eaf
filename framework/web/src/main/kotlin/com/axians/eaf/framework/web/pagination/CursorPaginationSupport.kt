package com.axians.eaf.framework.web.pagination

import java.time.Instant
import java.util.Base64

/**
 * Cursor-based pagination support utilities (Story 2.8).
 *
 * Provides reusable cursor encoding/decoding for stable pagination across API endpoints.
 * Cursors are Base64-encoded timestamps (Instant) following Relay Cursor Connection spec.
 *
 * **Use Cases:**
 * - List endpoints with cursor-based pagination
 * - Time-series data queries
 * - Any ordered dataset requiring stable page boundaries
 *
 * **Benefits:**
 * - Stable pagination (no skipped/duplicate items when data changes)
 * - Opaque cursors (clients don't parse, just pass through)
 * - Efficient queries (no OFFSET scans)
 *
 * **References:**
 * - [Relay Cursor Spec](https://relay.dev/graphql/connections.htm)
 * - Architecture: Section 15 (API Contracts - Pagination)
 *
 * @see encodeCursor
 * @see decodeCursor
 */
object CursorPaginationSupport {
    /**
     * Encodes a timestamp into a Base64 cursor string.
     *
     * Creates an opaque cursor that clients can use to request the next page.
     * The cursor encodes the pagination boundary (timestamp of last item on current page).
     *
     * **Example:**
     * ```kotlin
     * val lastItem = widgets.last()
     * val nextCursor = CursorPaginationSupport.encodeCursor(lastItem.createdAt)
     * return PaginatedResponse(items, nextCursor, hasMore = true)
     * ```
     *
     * @param timestamp Instant representing the pagination boundary
     * @return Base64-encoded cursor string (opaque to clients)
     */
    fun encodeCursor(timestamp: Instant): String =
        Base64.getEncoder().encodeToString(timestamp.toString().toByteArray())

    /**
     * Decodes a Base64 cursor string into an Instant timestamp.
     *
     * Extracts the pagination boundary from an opaque cursor.
     * Throws IllegalArgumentException for invalid cursor formats.
     *
     * **Example:**
     * ```kotlin
     * val cursor = query.cursor?.let { CursorPaginationSupport.decodeCursor(it) }
     * val whereCondition = cursor?.let { CREATED_AT.lt(it) } ?: noCondition()
     * ```
     *
     * @param cursor Base64-encoded cursor string
     * @return Decoded Instant representing pagination boundary
     * @throws IllegalArgumentException if cursor format is invalid (malformed Base64 or timestamp)
     */
    fun decodeCursor(cursor: String): Instant =
        try {
            val timestamp = String(Base64.getDecoder().decode(cursor))
            Instant.parse(timestamp)
        } catch (e: IllegalArgumentException) {
            // Invalid Base64 format
            throw IllegalArgumentException("Invalid cursor format: malformed Base64", e)
        } catch (e: java.time.format.DateTimeParseException) {
            // Valid Base64 but invalid timestamp format
            throw IllegalArgumentException("Invalid cursor format: malformed timestamp", e)
        }
}
