package com.axians.eaf.api.widget.dto

import java.math.BigDecimal
import java.time.Instant

/**
 * Response DTO for Widget queries.
 * Excludes sensitive internal fields (tenantId, updatedAt) for API security.
 */
data class WidgetResponse(
    val id: String,
    val name: String,
    val description: String?,
    val value: BigDecimal,
    val category: String,
    val metadata: Map<String, Any>?,
    val createdAt: Instant,
)
