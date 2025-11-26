package de.acci.eaf.eventsourcing.projection

/**
 * Response containing a page of items with pagination metadata.
 *
 * @param T The type of items in the page
 * @param items The items in the current page
 * @param page Current page index (zero-based)
 * @param size Number of items requested per page
 * @param totalElements Total number of elements across all pages
 */
public data class PagedResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long
) {
    /**
     * Total number of pages available.
     * Returns 0 if totalElements is 0, otherwise calculates ceiling division.
     */
    val totalPages: Int
        get() = if (totalElements == 0L) 0 else ((totalElements + size - 1) / size).toInt()

    /**
     * Whether there is a next page available.
     */
    val hasNext: Boolean
        get() = page < totalPages - 1

    /**
     * Whether there is a previous page available.
     */
    val hasPrevious: Boolean
        get() = page > 0

    /**
     * Whether this is the first page.
     */
    val isFirst: Boolean
        get() = page == 0

    /**
     * Whether this is the last page.
     */
    val isLast: Boolean
        get() = !hasNext

    /**
     * Number of elements in the current page.
     */
    val numberOfElements: Int
        get() = items.size

    public companion object {
        /**
         * Creates an empty paged response.
         */
        public fun <T> empty(pageRequest: PageRequest = PageRequest()): PagedResponse<T> =
            PagedResponse(
                items = emptyList(),
                page = pageRequest.page,
                size = pageRequest.size,
                totalElements = 0L
            )
    }
}
