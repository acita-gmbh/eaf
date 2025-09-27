package com.axians.eaf.products.widgetdemo.query

import com.axians.eaf.api.widget.dto.WidgetResponse
import com.axians.eaf.api.widget.queries.FindWidgetByIdQuery
import com.axians.eaf.api.widget.queries.FindWidgetsQuery
import com.axians.eaf.framework.persistence.entities.WidgetProjection
import com.axians.eaf.framework.persistence.repositories.WidgetProjectionRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.axonframework.queryhandling.QueryHandler
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

/**
 * Query handler for Widget-related queries.
 * Implements QueryHandler methods for retrieving widget data from projections.
 * Ensures read model isolation by interacting only with WidgetProjectionRepository.
 */
@Component
class WidgetQueryHandler(
    private val repository: WidgetProjectionRepository,
    private val objectMapper: ObjectMapper,
) {
    /**
     * Handles FindWidgetByIdQuery to retrieve a specific widget.
     * Enforces tenant isolation and converts projection to response DTO.
     *
     * @param query the query containing widget ID and tenant ID
     * @return WidgetResponse if found, null otherwise
     */
    @QueryHandler
    fun handle(query: FindWidgetByIdQuery): WidgetResponse? {
        val projection = repository.findByWidgetIdAndTenantId(query.widgetId, query.tenantId)
        return projection?.let { convertToResponse(it) }
    }

    /**
     * Handles FindWidgetsQuery to retrieve widgets with pagination and filtering.
     * Supports category filtering, name search, and sorting with tenant isolation.
     *
     * @param query the query containing pagination and filter parameters
     * @return Page of WidgetResponse with pagination metadata
     */
    @QueryHandler
    fun handle(query: FindWidgetsQuery): Page<WidgetResponse> {
        val sort = createSort(query.sort)
        val pageable = PageRequest.of(query.page, query.size, sort)

        // Get filtered projections based on query parameters
        val projections =
            when {
                // Category filter only
                !query.category.isNullOrBlank() && query.search.isNullOrBlank() -> {
                    repository.findByTenantIdAndCategoryOrderByCreatedAtDesc(query.tenantId, query.category!!)
                }
                // Name search only
                !query.search.isNullOrBlank() && query.category.isNullOrBlank() -> {
                    repository.findByTenantIdAndNameContainingIgnoreCase(query.tenantId, "%${query.search}%")
                }
                // Both category and search
                !query.category.isNullOrBlank() && !query.search.isNullOrBlank() -> {
                    repository
                        .findByTenantIdAndCategoryOrderByCreatedAtDesc(query.tenantId, query.category!!)
                        .filter { it.name.contains(query.search!!, ignoreCase = true) }
                }
                // No filters
                else -> {
                    repository.findByTenantIdOrderByCreatedAtDesc(query.tenantId)
                }
            }

        // Apply pagination manually since we may have complex filtering
        val totalElements = projections.size.toLong()
        val start = (pageable.pageNumber * pageable.pageSize).coerceAtMost(projections.size)
        val end = ((pageable.pageNumber + 1) * pageable.pageSize).coerceAtMost(projections.size)

        val pagedProjections =
            if (start < end) {
                projections.subList(start, end)
            } else {
                emptyList()
            }

        val responses = pagedProjections.map { convertToResponse(it) }

        return PageImpl(responses, pageable, totalElements)
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
                    objectMapper.readValue(jsonString, object : TypeReference<Map<String, Any>>() {})
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
                if (parts.size == 2) {
                    val field = parts[0]
                    val direction =
                        when (parts[1].lowercase()) {
                            "asc" -> Sort.Direction.ASC
                            "desc" -> Sort.Direction.DESC
                            else -> null
                        }
                    direction?.let { Sort.Order(it, field) }
                } else {
                    null
                }
            }

        return if (orders.isNotEmpty()) {
            Sort.by(orders)
        } else {
            Sort.by(Sort.Direction.DESC, "createdAt")
        }
    }
}
