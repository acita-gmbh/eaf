package com.axians.eaf.products.widgetdemo.repositories

import com.axians.eaf.products.widgetdemo.entities.WidgetProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant

/**
 * Repository interface for WidgetProjection entities.
 *
 * This repository provides CRUD operations and tenant-aware query methods
 * for the Widget projection read model. All queries include tenant isolation
 * to ensure proper multi-tenant data segregation.
 */
@Repository
interface WidgetProjectionRepository : JpaRepository<WidgetProjection, String> {
    /**
     * Finds a widget projection by widget ID and tenant ID.
     *
     * @param widgetId the widget ID
     * @param tenantId the tenant ID for isolation
     * @return the widget projection if found, null otherwise
     */
    fun findByWidgetIdAndTenantId(
        widgetId: String,
        tenantId: String,
    ): WidgetProjection?

    /**
     * Finds all widget projections for a specific tenant.
     *
     * @param tenantId the tenant ID for isolation
     * @return list of widget projections for the tenant
     */
    fun findByTenantIdOrderByCreatedAtDesc(tenantId: String): List<WidgetProjection>

    /**
     * Finds all widget projections for a specific tenant and category.
     *
     * @param tenantId the tenant ID for isolation
     * @param category the widget category
     * @return list of widget projections for the tenant and category
     */
    fun findByTenantIdAndCategoryOrderByCreatedAtDesc(
        tenantId: String,
        category: String,
    ): List<WidgetProjection>

    /**
     * Finds all widget projections for a tenant with value greater than specified amount.
     *
     * @param tenantId the tenant ID for isolation
     * @param minValue the minimum value threshold
     * @return list of widget projections matching criteria
     */
    fun findByTenantIdAndValueGreaterThanOrderByValueDesc(
        tenantId: String,
        minValue: BigDecimal,
    ): List<WidgetProjection>

    /**
     * Finds all widget projections for a tenant created after a specific timestamp.
     *
     * @param tenantId the tenant ID for isolation
     * @param afterTimestamp the timestamp threshold
     * @return list of widget projections created after the timestamp
     */
    fun findByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(
        tenantId: String,
        afterTimestamp: Instant,
    ): List<WidgetProjection>

    /**
     * Counts the total number of widget projections for a specific tenant.
     *
     * @param tenantId the tenant ID for isolation
     * @return count of widget projections for the tenant
     */
    fun countByTenantId(tenantId: String): Long

    /**
     * Custom query to find widgets by name pattern (case-insensitive) for a specific tenant.
     *
     * @param tenantId the tenant ID for isolation
     * @param namePattern the name pattern to search for (use % wildcards)
     * @return list of widget projections matching the name pattern
     */
    @Query(
        """
        SELECT w FROM WidgetProjection w
        WHERE w.tenantId = :tenantId
        AND LOWER(w.name) LIKE LOWER(:namePattern)
        ORDER BY w.createdAt DESC
    """,
    )
    fun findByTenantIdAndNameContainingIgnoreCase(
        @Param("tenantId") tenantId: String,
        @Param("namePattern") namePattern: String,
    ): List<WidgetProjection>

    /**
     * Custom query to get summary statistics for widgets by category for a specific tenant.
     *
     * @param tenantId the tenant ID for isolation
     * @return list of arrays containing [category, count, avgValue, totalValue]
     */
    @Query(
        """
        SELECT w.category, COUNT(w), AVG(w.value), SUM(w.value)
        FROM WidgetProjection w
        WHERE w.tenantId = :tenantId
        GROUP BY w.category
        ORDER BY w.category
    """,
    )
    fun getCategorySummaryByTenantId(
        @Param("tenantId") tenantId: String,
    ): List<Array<Any>>

    /**
     * Checks if a widget projection exists for the given widget ID and tenant ID.
     *
     * @param widgetId the widget ID
     * @param tenantId the tenant ID for isolation
     * @return true if the projection exists, false otherwise
     */
    fun existsByWidgetIdAndTenantId(
        widgetId: String,
        tenantId: String,
    ): Boolean

    /**
     * Deletes a widget projection by widget ID and tenant ID.
     *
     * @param widgetId the widget ID
     * @param tenantId the tenant ID for isolation
     * @return number of deleted records (should be 0 or 1)
     */
    fun deleteByWidgetIdAndTenantId(
        widgetId: String,
        tenantId: String,
    ): Long
}
