package com.axians.eaf.products.widgetdemo.repositories

import com.axians.eaf.products.widgetdemo.entities.WidgetProjection
import com.axians.eaf.products.widgetdemo.jooq.tables.records.WidgetProjectionRecord
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import com.axians.eaf.products.widgetdemo.jooq.tables.WidgetProjection as WidgetProjectionTable

interface WidgetProjectionRepository {
    fun save(projection: WidgetProjection): WidgetProjection

    fun findByWidgetIdAndTenantId(
        widgetId: String,
        tenantId: String,
    ): WidgetProjection?

    fun findByTenantIdOrderByCreatedAtDesc(tenantId: String): List<WidgetProjection>

    fun findByTenantIdAndCategoryOrderByCreatedAtDesc(
        tenantId: String,
        category: String,
    ): List<WidgetProjection>

    fun findByTenantIdAndValueGreaterThanOrderByValueDesc(
        tenantId: String,
        minValue: BigDecimal,
    ): List<WidgetProjection>

    fun findByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(
        tenantId: String,
        afterTimestamp: Instant,
    ): List<WidgetProjection>

    fun countByTenantId(tenantId: String): Long

    fun findByTenantIdAndNameContainingIgnoreCase(
        tenantId: String,
        namePattern: String,
    ): List<WidgetProjection>

    fun getCategorySummaryByTenantId(tenantId: String): List<WidgetCategorySummary>

    fun existsByWidgetIdAndTenantId(
        widgetId: String,
        tenantId: String,
    ): Boolean

    fun deleteByWidgetIdAndTenantId(
        widgetId: String,
        tenantId: String,
    ): Long

    fun deleteBatch(batchSize: Int): Long

    fun search(criteria: WidgetSearchCriteria): WidgetPage
}

@Repository
class JooqWidgetProjectionRepository(
    private val dsl: DSLContext,
) : WidgetProjectionRepository {
    override fun save(projection: WidgetProjection): WidgetProjection {
        val record = projection.toRecord()

        val saved =
            dsl
                .insertInto(WidgetProjectionTable.WIDGET_PROJECTION)
                .set(record)
                .onConflict(WidgetProjectionTable.WIDGET_PROJECTION.WIDGET_ID)
                .doUpdate()
                .set(WidgetProjectionTable.WIDGET_PROJECTION.TENANT_ID, record.tenantId)
                .set(WidgetProjectionTable.WIDGET_PROJECTION.NAME, record.name)
                .set(WidgetProjectionTable.WIDGET_PROJECTION.DESCRIPTION, record.description)
                .set(WidgetProjectionTable.WIDGET_PROJECTION.`VALUE`, record.value)
                .set(WidgetProjectionTable.WIDGET_PROJECTION.CATEGORY, record.category)
                .set(WidgetProjectionTable.WIDGET_PROJECTION.METADATA, record.metadata)
                .set(WidgetProjectionTable.WIDGET_PROJECTION.CREATED_AT, record.createdAt)
                .set(WidgetProjectionTable.WIDGET_PROJECTION.UPDATED_AT, record.updatedAt)
                .where(WidgetProjectionTable.WIDGET_PROJECTION.TENANT_ID.eq(record.tenantId))
                .returning()
                .fetchOne() ?: error("Failed to persist widget projection ${projection.widgetId}")

        return saved.toDomain()
    }

    override fun findByWidgetIdAndTenantId(
        widgetId: String,
        tenantId: String,
    ): WidgetProjection? =
        dsl
            .selectFrom(WidgetProjectionTable.WIDGET_PROJECTION)
            .where(
                WidgetProjectionTable.WIDGET_PROJECTION.WIDGET_ID
                    .eq(widgetId.toUuid())
                    .and(WidgetProjectionTable.WIDGET_PROJECTION.TENANT_ID.eq(tenantId.toUuid())),
            ).fetchOne()
            ?.toDomain()

    override fun findByTenantIdOrderByCreatedAtDesc(tenantId: String): List<WidgetProjection> =
        dsl
            .selectFrom(WidgetProjectionTable.WIDGET_PROJECTION)
            .where(WidgetProjectionTable.WIDGET_PROJECTION.TENANT_ID.eq(tenantId.toUuid()))
            .orderBy(WidgetProjectionTable.WIDGET_PROJECTION.CREATED_AT.desc())
            .fetch()
            .map { it.toDomain() }

    override fun findByTenantIdAndCategoryOrderByCreatedAtDesc(
        tenantId: String,
        category: String,
    ): List<WidgetProjection> =
        dsl
            .selectFrom(WidgetProjectionTable.WIDGET_PROJECTION)
            .where(
                WidgetProjectionTable.WIDGET_PROJECTION.TENANT_ID
                    .eq(tenantId.toUuid())
                    .and(WidgetProjectionTable.WIDGET_PROJECTION.CATEGORY.eq(category)),
            ).orderBy(WidgetProjectionTable.WIDGET_PROJECTION.CREATED_AT.desc())
            .fetch()
            .map { it.toDomain() }

    override fun findByTenantIdAndValueGreaterThanOrderByValueDesc(
        tenantId: String,
        minValue: BigDecimal,
    ): List<WidgetProjection> =
        dsl
            .selectFrom(WidgetProjectionTable.WIDGET_PROJECTION)
            .where(
                WidgetProjectionTable.WIDGET_PROJECTION.TENANT_ID
                    .eq(tenantId.toUuid())
                    .and(WidgetProjectionTable.WIDGET_PROJECTION.`VALUE`.gt(minValue)),
            ).orderBy(WidgetProjectionTable.WIDGET_PROJECTION.`VALUE`.desc())
            .fetch()
            .map { it.toDomain() }

    override fun findByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(
        tenantId: String,
        afterTimestamp: Instant,
    ): List<WidgetProjection> =
        dsl
            .selectFrom(WidgetProjectionTable.WIDGET_PROJECTION)
            .where(
                WidgetProjectionTable.WIDGET_PROJECTION.TENANT_ID
                    .eq(tenantId.toUuid())
                    .and(WidgetProjectionTable.WIDGET_PROJECTION.CREATED_AT.gt(afterTimestamp.toOffsetDateTime())),
            ).orderBy(WidgetProjectionTable.WIDGET_PROJECTION.CREATED_AT.desc())
            .fetch()
            .map { it.toDomain() }

    override fun countByTenantId(tenantId: String): Long =
        dsl
            .fetchCount(
                WidgetProjectionTable.WIDGET_PROJECTION,
                WidgetProjectionTable.WIDGET_PROJECTION.TENANT_ID.eq(tenantId.toUuid()),
            ).toLong()

    override fun findByTenantIdAndNameContainingIgnoreCase(
        tenantId: String,
        namePattern: String,
    ): List<WidgetProjection> =
        dsl
            .selectFrom(WidgetProjectionTable.WIDGET_PROJECTION)
            .where(
                WidgetProjectionTable.WIDGET_PROJECTION.TENANT_ID
                    .eq(tenantId.toUuid())
                    .and(WidgetProjectionTable.WIDGET_PROJECTION.NAME.containsIgnoreCase(namePattern.trim())),
            ).orderBy(WidgetProjectionTable.WIDGET_PROJECTION.CREATED_AT.desc())
            .fetch()
            .map { it.toDomain() }

    override fun getCategorySummaryByTenantId(tenantId: String): List<WidgetCategorySummary> =
        dsl
            .select(
                WidgetProjectionTable.WIDGET_PROJECTION.CATEGORY,
                COUNT_FIELD,
                AVG_FIELD,
                SUM_FIELD,
            ).from(WidgetProjectionTable.WIDGET_PROJECTION)
            .where(WidgetProjectionTable.WIDGET_PROJECTION.TENANT_ID.eq(tenantId.toUuid()))
            .groupBy(WidgetProjectionTable.WIDGET_PROJECTION.CATEGORY)
            .orderBy(WidgetProjectionTable.WIDGET_PROJECTION.CATEGORY.asc())
            .fetch { record ->
                WidgetCategorySummary(
                    category = record.get(WidgetProjectionTable.WIDGET_PROJECTION.CATEGORY)!!,
                    count = record.get(COUNT_FIELD) ?: 0L,
                    averageValue = record.get(AVG_FIELD) ?: BigDecimal.ZERO,
                    totalValue = record.get(SUM_FIELD) ?: BigDecimal.ZERO,
                )
            }

    override fun existsByWidgetIdAndTenantId(
        widgetId: String,
        tenantId: String,
    ): Boolean =
        dsl.fetchExists(
            WidgetProjectionTable.WIDGET_PROJECTION,
            WidgetProjectionTable.WIDGET_PROJECTION.WIDGET_ID
                .eq(widgetId.toUuid())
                .and(WidgetProjectionTable.WIDGET_PROJECTION.TENANT_ID.eq(tenantId.toUuid())),
        )

    override fun deleteByWidgetIdAndTenantId(
        widgetId: String,
        tenantId: String,
    ): Long =
        dsl
            .deleteFrom(WidgetProjectionTable.WIDGET_PROJECTION)
            .where(
                WidgetProjectionTable.WIDGET_PROJECTION.WIDGET_ID
                    .eq(widgetId.toUuid())
                    .and(WidgetProjectionTable.WIDGET_PROJECTION.TENANT_ID.eq(tenantId.toUuid())),
            ).execute()
            .toLong()

    override fun deleteBatch(batchSize: Int): Long {
        val ids =
            dsl
                .select(WidgetProjectionTable.WIDGET_PROJECTION.WIDGET_ID)
                .from(WidgetProjectionTable.WIDGET_PROJECTION)
                .orderBy(WidgetProjectionTable.WIDGET_PROJECTION.CREATED_AT.asc())
                .limit(batchSize)
                .fetch(WidgetProjectionTable.WIDGET_PROJECTION.WIDGET_ID)

        if (ids.isEmpty()) {
            return 0
        }

        return dsl
            .deleteFrom(WidgetProjectionTable.WIDGET_PROJECTION)
            .where(WidgetProjectionTable.WIDGET_PROJECTION.WIDGET_ID.`in`(ids))
            .execute()
            .toLong()
    }

    override fun search(criteria: WidgetSearchCriteria): WidgetPage {
        val baseCondition = tenantCondition(criteria.tenantId)
        val filters = mutableListOf<Condition>(baseCondition)

        criteria.category?.takeIf { it.isNotBlank() }?.let { filters += WidgetProjectionTable.WIDGET_PROJECTION.CATEGORY.eq(it) }
        criteria.search?.takeIf { it.isNotBlank() }?.let {
            val trimmed = it.trim()
            filters += WidgetProjectionTable.WIDGET_PROJECTION.NAME.containsIgnoreCase(trimmed)
        }

        val whereCondition = filters.reduce { acc, condition -> acc.and(condition) }
        val orderBy = resolveSort(criteria.sort)
        val offset = criteria.page * criteria.size

        val items =
            dsl
                .selectFrom(WidgetProjectionTable.WIDGET_PROJECTION)
                .where(whereCondition)
                .orderBy(orderBy)
                .limit(criteria.size)
                .offset(offset)
                .fetch()
                .map { it.toDomain() }

        val total = dsl.fetchCount(WidgetProjectionTable.WIDGET_PROJECTION, whereCondition).toLong()
        return WidgetPage(items = items, total = total)
    }

    private fun tenantCondition(tenantId: String): Condition = WidgetProjectionTable.WIDGET_PROJECTION.TENANT_ID.eq(tenantId.toUuid())

    private fun resolveSort(sort: List<String>): List<SortField<*>> {
        if (sort.isEmpty()) {
            return listOf(WidgetProjectionTable.WIDGET_PROJECTION.CREATED_AT.desc())
        }

        return sort
            .mapNotNull { spec ->
                val parts = spec.split('.')
                if (parts.size != 2) return@mapNotNull null

                val field = parts[0]
                val direction = parts[1].lowercase()

                val column =
                    when (field) {
                        "name" -> WidgetProjectionTable.WIDGET_PROJECTION.NAME
                        "category" -> WidgetProjectionTable.WIDGET_PROJECTION.CATEGORY
                        "value" -> WidgetProjectionTable.WIDGET_PROJECTION.`VALUE`
                        "createdAt" -> WidgetProjectionTable.WIDGET_PROJECTION.CREATED_AT
                        "updatedAt" -> WidgetProjectionTable.WIDGET_PROJECTION.UPDATED_AT
                        else -> return@mapNotNull null
                    }

                when (direction) {
                    "asc" -> column.asc()
                    "desc" -> column.desc()
                    else -> null
                }
            }.ifEmpty { listOf(WidgetProjectionTable.WIDGET_PROJECTION.CREATED_AT.desc()) }
    }

    private fun WidgetProjectionRecord.toDomain(): WidgetProjection = WidgetProjection.fromRecord(this)
}

data class WidgetCategorySummary(
    val category: String,
    val count: Long,
    val averageValue: BigDecimal,
    val totalValue: BigDecimal,
)

data class WidgetSearchCriteria(
    val tenantId: String,
    val category: String?,
    val search: String?,
    val page: Int,
    val size: Int,
    val sort: List<String>,
)

data class WidgetPage(
    val items: List<WidgetProjection>,
    val total: Long,
)

private fun String.toUuid(): UUID = UUID.fromString(this)

private fun Instant.toOffsetDateTime(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

private val COUNT_FIELD = DSL.count().cast(Long::class.javaObjectType).`as`("projection_count")
private val AVG_FIELD = DSL.avg(WidgetProjectionTable.WIDGET_PROJECTION.`VALUE`).`as`("projection_avg")
private val SUM_FIELD = DSL.sum(WidgetProjectionTable.WIDGET_PROJECTION.`VALUE`).`as`("projection_sum")
