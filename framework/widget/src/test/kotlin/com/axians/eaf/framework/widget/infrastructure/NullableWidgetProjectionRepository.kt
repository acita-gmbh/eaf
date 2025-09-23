package com.axians.eaf.framework.widget.infrastructure

import com.axians.eaf.framework.persistence.entities.WidgetProjection
import com.axians.eaf.framework.persistence.repositories.WidgetProjectionRepository
import org.springframework.data.domain.Example
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.query.FluentQuery
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.function.Function

/**
 * Nullable implementation of WidgetProjectionRepository for fast unit testing.
 * Follows the Nullable Design Pattern to avoid mocking frameworks.
 *
 * This implementation provides in-memory storage with configurable test data,
 * allowing for deterministic testing without database dependencies.
 */
class NullableWidgetProjectionRepository : WidgetProjectionRepository {
    // In-memory storage for test data
    private val storage = mutableMapOf<String, WidgetProjection>()
    private val tenantIndex = mutableMapOf<String, MutableList<WidgetProjection>>()

    /**
     * Factory method following the Nullable Design Pattern convention.
     */
    companion object {
        fun createNull(): NullableWidgetProjectionRepository = NullableWidgetProjectionRepository()
    }

    /**
     * Adds test data to the repository.
     */
    fun withProjection(projection: WidgetProjection): NullableWidgetProjectionRepository {
        storage[projection.widgetId] = projection
        tenantIndex.computeIfAbsent(projection.getTenantId()) { mutableListOf() }.add(projection)
        return this
    }

    /**
     * Clears all test data.
     */
    fun clear() {
        storage.clear()
        tenantIndex.clear()
    }

    // Custom query methods implementation

    override fun findByWidgetIdAndTenantId(
        widgetId: String,
        tenantId: String,
    ): WidgetProjection? =
        storage[widgetId]?.takeIf {
            it.getTenantId() == tenantId
        }

    override fun findByTenantIdOrderByCreatedAtDesc(tenantId: String): List<WidgetProjection> =
        tenantIndex[tenantId]
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()

    override fun findByTenantIdAndCategoryOrderByCreatedAtDesc(
        tenantId: String,
        category: String,
    ): List<WidgetProjection> =
        tenantIndex[tenantId]
            ?.filter { it.category == category }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()

    override fun countByTenantId(tenantId: String): Long = tenantIndex[tenantId]?.size?.toLong() ?: 0L

    override fun existsByWidgetIdAndTenantId(
        widgetId: String,
        tenantId: String,
    ): Boolean = findByWidgetIdAndTenantId(widgetId, tenantId) != null

    override fun findByTenantIdAndValueGreaterThanOrderByValueDesc(
        tenantId: String,
        minValue: BigDecimal,
    ): List<WidgetProjection> =
        tenantIndex[tenantId]
            ?.filter { it.value > minValue }
            ?.sortedByDescending { it.value }
            ?: emptyList()

    override fun findByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(
        tenantId: String,
        afterTimestamp: Instant,
    ): List<WidgetProjection> =
        tenantIndex[tenantId]
            ?.filter { it.createdAt.isAfter(afterTimestamp) }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()

    override fun findByTenantIdAndNameContainingIgnoreCase(
        tenantId: String,
        namePattern: String,
    ): List<WidgetProjection> {
        val pattern = namePattern.lowercase()
        return tenantIndex[tenantId]
            ?.filter { it.name.lowercase().contains(pattern) }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    override fun getCategorySummaryByTenantId(tenantId: String): List<Array<Any>> =
        tenantIndex[tenantId]
            ?.groupBy { it.category }
            ?.map { (category, widgets) ->
                arrayOf<Any>(
                    category,
                    widgets.size.toLong(),
                    widgets.map { it.value.toDouble() }.average(),
                    widgets.sumOf { it.value },
                )
            }?.sortedBy { it[0] as String }
            ?: emptyList()

    override fun deleteByWidgetIdAndTenantId(
        widgetId: String,
        tenantId: String,
    ): Long {
        val removed =
            storage.remove(widgetId)?.let { projection ->
                if (projection.getTenantId() == tenantId) {
                    tenantIndex[projection.getTenantId()]?.remove(projection)
                    1L
                } else {
                    storage[widgetId] = projection
                    0L
                }
            } ?: 0L
        return removed
    }

    // JpaRepository methods - minimal implementation for testing

    override fun <S : WidgetProjection> save(entity: S): S {
        withProjection(entity)
        return entity
    }

    override fun <S : WidgetProjection> saveAll(entities: MutableIterable<S>): MutableList<S> {
        entities.forEach { withProjection(it) }
        return entities.toMutableList()
    }

    override fun findById(id: String): Optional<WidgetProjection> = Optional.ofNullable(storage[id])

    override fun existsById(id: String): Boolean = storage.containsKey(id)

    override fun findAll(): MutableList<WidgetProjection> = storage.values.toMutableList()

    override fun findAll(sort: Sort): MutableList<WidgetProjection> {
        // Simplified - ignoring sort for nullable implementation
        return findAll()
    }

    override fun findAll(pageable: Pageable): Page<WidgetProjection> {
        val allProjections = findAll()
        val start = pageable.pageNumber * pageable.pageSize
        val end = minOf(start + pageable.pageSize, allProjections.size)
        val content =
            if (start < allProjections.size) {
                allProjections.subList(start, end)
            } else {
                emptyList()
            }
        return PageImpl(content, pageable, allProjections.size.toLong())
    }

    @Suppress("MaxLineLength")
    override fun findAllById(ids: MutableIterable<String>): MutableList<WidgetProjection> = ids.mapNotNull { storage[it] }.toMutableList()

    override fun count(): Long = storage.size.toLong()

    override fun deleteById(id: String) {
        storage.remove(id)?.let { projection ->
            tenantIndex[projection.getTenantId()]?.remove(projection)
        }
    }

    override fun delete(entity: WidgetProjection) {
        deleteById(entity.widgetId)
    }

    override fun deleteAll(entities: MutableIterable<WidgetProjection>) {
        entities.forEach { delete(it) }
    }

    override fun deleteAll() {
        clear()
    }

    override fun deleteAllById(ids: MutableIterable<String>) {
        ids.forEach { deleteById(it) }
    }

    override fun flush() {
        // No-op for in-memory implementation
    }

    override fun <S : WidgetProjection> saveAndFlush(entity: S): S = save(entity)

    @Suppress("MaxLineLength")
    override fun <S : WidgetProjection> saveAllAndFlush(entities: MutableIterable<S>): MutableList<S> = saveAll(entities)

    override fun deleteAllInBatch(entities: MutableIterable<WidgetProjection>) {
        deleteAll(entities)
    }

    override fun deleteAllByIdInBatch(ids: MutableIterable<String>) {
        deleteAllById(ids)
    }

    override fun deleteAllInBatch() {
        deleteAll()
    }

    @Deprecated("Use getReferenceById instead")
    @Suppress("MaxLineLength")
    override fun getOne(id: String): WidgetProjection = storage[id] ?: throw NoSuchElementException("Widget with id $id not found")

    @Deprecated("Use getReferenceById instead")
    override fun getById(id: String): WidgetProjection {
        @Suppress("DEPRECATION")
        return getOne(id)
    }

    override fun getReferenceById(id: String): WidgetProjection =
        storage[id] ?: throw NoSuchElementException("Widget with id $id not found")

    override fun <S : WidgetProjection> findOne(example: Example<S>): Optional<S> {
        // Simplified - not implementing example matching
        return Optional.empty()
    }

    override fun <S : WidgetProjection> findAll(example: Example<S>): MutableList<S> {
        // Simplified - not implementing example matching
        return mutableListOf()
    }

    override fun <S : WidgetProjection> findAll(
        example: Example<S>,
        sort: Sort,
    ): MutableList<S> {
        // Simplified - not implementing example matching
        return mutableListOf()
    }

    override fun <S : WidgetProjection> findAll(
        example: Example<S>,
        pageable: Pageable,
    ): Page<S> {
        // Simplified - not implementing example matching
        return PageImpl(emptyList(), pageable, 0)
    }

    override fun <S : WidgetProjection> count(example: Example<S>): Long {
        // Simplified - not implementing example matching
        return 0L
    }

    override fun <S : WidgetProjection> exists(example: Example<S>): Boolean {
        // Simplified - not implementing example matching
        return false
    }

    override fun <S : WidgetProjection, R : Any> findBy(
        example: Example<S>,
        queryFunction: Function<FluentQuery.FetchableFluentQuery<S>, R>,
    ): R {
        // Simplified - not implementing fluent query
        throw UnsupportedOperationException("Fluent query not supported in nullable implementation")
    }
}
