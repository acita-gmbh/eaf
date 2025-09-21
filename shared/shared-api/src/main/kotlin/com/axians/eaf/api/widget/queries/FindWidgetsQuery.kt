package com.axians.eaf.api.widget.queries

/**
 * Query to find widgets with pagination and filtering support.
 * Includes tenant context propagation and comprehensive search parameters.
 */
data class FindWidgetsQuery(
    val tenantId: String,
    val page: Int = 0,
    val size: Int = 20,
    val sort: List<String> = emptyList(),
    val category: String? = null,
    val search: String? = null,
) {
    init {
        require(page >= 0) { "Page number must be non-negative" }
        require(size > 0 && size <= 100) { "Page size must be between 1 and 100" }
        require(tenantId.isNotBlank()) { "Tenant ID must not be blank" }
    }
}
