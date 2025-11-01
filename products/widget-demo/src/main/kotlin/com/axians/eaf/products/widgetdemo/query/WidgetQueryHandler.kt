package com.axians.eaf.products.widgetdemo.query

import com.axians.eaf.api.widget.dto.PagedResponse
import com.axians.eaf.api.widget.dto.WidgetResponse
import com.axians.eaf.api.widget.queries.FindWidgetByIdQuery
import com.axians.eaf.api.widget.queries.FindWidgetsQuery
import com.axians.eaf.products.widgetdemo.entities.WidgetProjection
import com.axians.eaf.products.widgetdemo.repositories.WidgetProjectionRepository
import com.axians.eaf.products.widgetdemo.repositories.WidgetSearchCriteria
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.axonframework.queryhandling.QueryHandler
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import kotlin.math.ceil

/**
 * Query handler for Widget-related queries.
 * Implements QueryHandler methods for retrieving widget data from projections.
 *
 * **Story 9.2 Fix - Use Concrete Data Class**:
 * Returns `PagedResponse<WidgetResponse>` (concrete data class) instead of Spring Data's
 * `Page<WidgetResponse>` (interface) to avoid Axon's type matching issues with generic
 * interfaces. Concrete classes work better with Axon's reflection-based handler discovery.
 */
@Component
open class WidgetQueryHandler(
    private val repository: WidgetProjectionRepository,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val METADATA_TYPE_REF: TypeReference<Map<String, Any>> =
            object : TypeReference<Map<String, Any>>() {}

        // Valid sort fields for WidgetProjection
        private val VALID_SORT_FIELDS =
            setOf(
                "name",
                "category",
                "value",
                "createdAt",
                "updatedAt",
            )

        private const val MAX_PAGE_SIZE = 1000
    }

    /**
     * Handles FindWidgetByIdQuery to retrieve a specific widget.
     * Enforces tenant isolation and converts projection to response DTO.
     *
     * @param query the query containing widget ID and tenant ID
     * @return WidgetResponse if found, null otherwise
     */
    @QueryHandler
    @Transactional(readOnly = true)
    fun handle(query: FindWidgetByIdQuery): WidgetResponse? {
        val projection = repository.findByWidgetIdAndTenantId(query.widgetId, query.tenantId)
        return projection?.let { convertToResponse(it) }
    }

    /**
     * Handles FindWidgetsQuery to retrieve widgets with pagination and filtering.
     * Supports category filtering, name search, and sorting with tenant isolation.
     *
     * @param query the query containing pagination and filter parameters
     * @return PagedResponse (concrete data class) with pagination metadata
     */
    @QueryHandler
    @Transactional(readOnly = true)
    fun handle(query: FindWidgetsQuery): PagedResponse<WidgetResponse> {
        // Validate pagination parameters
        require(query.page >= 0) { "Page number must be >= 0, got ${query.page}" }
        require(query.size > 0 && query.size <= MAX_PAGE_SIZE) {
            "Page size must be between 1 and $MAX_PAGE_SIZE, got ${query.size}"
        }

        val sort = createSort(query.sort)

        val criteria =
            WidgetSearchCriteria(
                tenantId = query.tenantId,
                category = query.category?.takeIf { it.isNotBlank() },
                search = query.search?.takeIf { it.isNotBlank() },
                page = query.page,
                size = query.size,
                sort = query.sort,
            )

        val result = repository.search(criteria)
        val responses = result.items.map { convertToResponse(it) }

        val totalPages = ceil(result.total.toDouble() / query.size).toInt()

        return PagedResponse(
            content = responses,
            totalElements = result.total,
            page = query.page,
            size = query.size,
            totalPages = totalPages,
        )
    }

    /**
     * Converts a WidgetProjection to WidgetResponse DTO.
     * Excludes sensitive fields (tenantId, updatedAt) and deserializes JSON metadata.
     *
     * @param projection the widget projection entity
     * @return WidgetResponse DTO for API response
     */
    private fun convertToResponse(projection: WidgetProjection): WidgetResponse {
        val metadata =
            projection.metadata?.let { jsonString ->
                try {
                    objectMapper.readValue(jsonString, METADATA_TYPE_REF)
                } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
                    // Log error and return null if JSON parsing fails
                    // In production, this should use proper logging: logger.warn("Failed to parse metadata JSON", e)
                    println("Warning: Failed to parse widget metadata JSON: ${e.message}")
                    null
                }
            }

        return WidgetResponse(
            id = projection.widgetId,
            name = projection.name,
            description = projection.description,
            value = projection.value,
            category = projection.category,
            metadata = metadata,
            createdAt = projection.createdAt,
        )
    }

    /**
     * Creates a Sort object from string array.
     * Expected format: ["field.direction"] e.g., ["name.asc", "createdAt.desc"]
     *
     * @param sortSpecs list of sort specifications
     * @return Sort object for query ordering
     */
    private fun createSort(sortSpecs: List<String>): Sort {
        if (sortSpecs.isEmpty()) {
            return Sort.by(Sort.Direction.DESC, "createdAt")
        }

        val orders =
            sortSpecs.mapNotNull { spec ->
                val parts = spec.split(".")
                if (parts.size != 2) {
                    // Invalid format - skip this sort spec
                    return@mapNotNull null
                }

                val field = parts[0]
                val directionStr = parts[1].lowercase()

                // Validate field against known properties
                if (!VALID_SORT_FIELDS.contains(field)) {
                    // Unknown field - skip this sort spec
                    return@mapNotNull null
                }

                val direction =
                    when (directionStr) {
                        "asc" -> Sort.Direction.ASC
                        "desc" -> Sort.Direction.DESC
                        else -> return@mapNotNull null // Invalid direction - skip
                    }

                Sort.Order(direction, field)
            }

        return if (orders.isNotEmpty()) {
            Sort.by(orders)
        } else {
            // Fallback to default if all sort specs were invalid
            Sort.by(Sort.Direction.DESC, "createdAt")
        }
    }
}
