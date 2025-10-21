package com.axians.eaf.api.widget.dto

/**
 * Generic paged response wrapper for query results.
 * Replaces Spring Data's Page interface for Axon Framework compatibility.
 *
 * **Why not use Spring Data Page?**
 * Axon Framework's query handler registration struggles with parameterized
 * interface types like Page<T> due to type erasure. This concrete data class
 * provides the same pagination information in a format that Axon can properly
 * register and dispatch.
 *
 * **React-Admin Compatibility**:
 * This format is compatible with React-Admin's expectations for paginated lists:
 * - `content`: List of items (React-Admin uses this as the data array)
 * - `totalElements`: Total count for pagination UI
 * - `page`: Current page number (0-based)
 * - `size`: Page size
 *
 * @param T the type of content elements
 * @property content the list of items in this page
 * @property totalElements total number of elements across all pages
 * @property page current page number (zero-based)
 * @property size number of items per page
 * @property totalPages total number of pages
 */
data class PagedResponse<T>(
    val content: List<T>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int,
)
