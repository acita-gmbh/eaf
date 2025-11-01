package com.axians.eaf.products.widgetdemo.infrastructure

import com.axians.eaf.products.widgetdemo.entities.WidgetProjection
import com.axians.eaf.products.widgetdemo.repositories.WidgetCategorySummary
import com.axians.eaf.products.widgetdemo.repositories.WidgetPage
import com.axians.eaf.products.widgetdemo.repositories.WidgetProjectionRepository
import com.axians.eaf.products.widgetdemo.repositories.WidgetSearchCriteria
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Nullable Design Pattern implementation of [WidgetProjectionRepository] for unit tests.
 * Provides deterministic, in-memory behaviour without requiring a database connection.
 */
class NullableWidgetProjectionRepository : WidgetProjectionRepository {
    private val storage = ConcurrentHashMap<String, WidgetProjection>()

    companion object {
        fun createNull(): NullableWidgetProjectionRepository = NullableWidgetProjectionRepository()
    }

    fun withProjection(projection: WidgetProjection): NullableWidgetProjectionRepository {
        storage[projection.widgetId] = projection
        return this
    }

    fun clear() {
        storage.clear()
    }

    override fun save(projection: WidgetProjection): WidgetProjection {
        storage[projection.widgetId] = projection
        return projection
    }

    override fun findByWidgetIdAndTenantId(
        widgetId: String,
        tenantId: String,
    ): WidgetProjection? = storage[widgetId]?.takeIf { it.tenantId == tenantId }

    override fun findByTenantIdOrderByCreatedAtDesc(tenantId: String): List<WidgetProjection> =
        storage.values
            .filter { it.tenantId == tenantId }
            .sortedByDescending { it.createdAt }

    override fun findByTenantIdAndCategoryOrderByCreatedAtDesc(
        tenantId: String,
        category: String,
    ): List<WidgetProjection> =
        storage.values
            .filter { it.tenantId == tenantId && it.category == category }
            .sortedByDescending { it.createdAt }

    override fun findByTenantIdAndValueGreaterThanOrderByValueDesc(
        tenantId: String,
        minValue: BigDecimal,
    ): List<WidgetProjection> =
        storage.values
            .filter { it.tenantId == tenantId && it.value > minValue }
            .sortedByDescending { it.value }

    override fun findByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(
        tenantId: String,
        afterTimestamp: Instant,
    ): List<WidgetProjection> =
        storage.values
            .filter { it.tenantId == tenantId && it.createdAt.isAfter(afterTimestamp) }
            .sortedByDescending { it.createdAt }

    override fun countByTenantId(tenantId: String): Long = storage.values.count { it.tenantId == tenantId }.toLong()

    override fun findByTenantIdAndNameContainingIgnoreCase(
        tenantId: String,
        namePattern: String,
    ): List<WidgetProjection> {
        val needle = namePattern.lowercase()
        return storage.values
            .filter { it.tenantId == tenantId && it.name.lowercase().contains(needle) }
            .sortedByDescending { it.createdAt }
    }

    override fun getCategorySummaryByTenantId(tenantId: String): List<WidgetCategorySummary> =
        storage.values
            .filter { it.tenantId == tenantId }
            .groupBy { it.category }
            .map { (category, items) ->
                val total = items.fold(BigDecimal.ZERO) { acc, projection -> acc + projection.value }
                WidgetCategorySummary(
                    category = category,
                    count = items.size.toLong(),
                    averageValue =
                        if (items.isEmpty()) {
                            BigDecimal.ZERO
                        } else {
                            total.divide(BigDecimal.valueOf(items.size.toLong()), 2, RoundingMode.HALF_UP)
                        },
                    totalValue = total,
                )
            }.sortedBy { it.category }

    override fun existsByWidgetIdAndTenantId(
        widgetId: String,
        tenantId: String,
    ): Boolean = storage[widgetId]?.tenantId == tenantId

    override fun deleteByWidgetIdAndTenantId(
        widgetId: String,
        tenantId: String,
    ): Long {
        val removed = storage.remove(widgetId)
        return if (removed != null && removed.tenantId == tenantId) 1 else 0
    }

    override fun deleteBatch(batchSize: Int): Long {
        val candidates =
            storage.values
                .sortedBy { it.createdAt }
                .take(batchSize)
        candidates.forEach { storage.remove(it.widgetId) }
        return candidates.size.toLong()
    }

    override fun search(criteria: WidgetSearchCriteria): WidgetPage {
        val filtered =
            storage.values
                .asSequence()
                .filter { it.tenantId == criteria.tenantId }
                .let { seq ->
                    criteria.category?.let { category -> seq.filter { it.category == category } } ?: seq
                }.let { seq ->
                    criteria.search?.let { term ->
                        val needle = term.lowercase()
                        seq.filter { it.name.lowercase().contains(needle) }
                    } ?: seq
                }.toList()

        val sorted = filtered.sortedWith(sortComparator(criteria.sort))
        val total = sorted.size.toLong()
        val fromIndex = (criteria.page * criteria.size).coerceAtMost(sorted.size)
        val toIndex = (fromIndex + criteria.size).coerceAtMost(sorted.size)
        val pageItems = if (fromIndex < toIndex) sorted.subList(fromIndex, toIndex) else emptyList()

        return WidgetPage(items = pageItems, total = total)
    }

    private fun sortComparator(sort: List<String>): Comparator<WidgetProjection> {
        if (sort.isEmpty()) {
            return compareByDescending { it.createdAt }
        }

        val comparators =
            sort.mapNotNull { spec ->
                val parts = spec.split('.')
                if (parts.size != 2) return@mapNotNull null

                val field = parts[0]
                val direction = parts[1].lowercase()

                val comparator =
                    when (field) {
                        "name" -> compareBy<WidgetProjection> { it.name.lowercase() }
                        "category" -> compareBy<WidgetProjection> { it.category.lowercase() }
                        "value" -> compareBy<WidgetProjection> { it.value }
                        "createdAt" -> compareBy<WidgetProjection> { it.createdAt }
                        "updatedAt" -> compareBy<WidgetProjection> { it.updatedAt }
                        else -> null
                    }

                comparator?.let {
                    if (direction == "desc") it.reversed() else it
                }
            }

        if (comparators.isEmpty()) {
            return compareByDescending { it.createdAt }
        }

        return comparators.reduce { acc, comparator -> acc.then(comparator) }
    }
}
