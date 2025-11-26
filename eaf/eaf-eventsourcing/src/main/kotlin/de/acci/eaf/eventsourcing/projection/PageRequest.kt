package de.acci.eaf.eventsourcing.projection

/**
 * Request for paginated data retrieval.
 *
 * @param page Zero-based page index (must be >= 0)
 * @param size Number of items per page (must be > 0)
 * @throws IllegalArgumentException if page < 0 or size <= 0
 */
public data class PageRequest(
    val page: Int = 0,
    val size: Int = 20
) {
    init {
        require(page >= 0) { "Page index must not be negative, was: $page" }
        require(size > 0) { "Page size must be positive, was: $size" }
    }

    /**
     * Returns the offset for database queries.
     */
    public val offset: Long
        get() = page.toLong() * size
}
