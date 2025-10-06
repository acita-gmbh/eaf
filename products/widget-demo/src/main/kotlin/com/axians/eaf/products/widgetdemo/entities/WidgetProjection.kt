package com.axians.eaf.products.widgetdemo.entities

import com.axians.eaf.framework.core.tenant.TenantAware
import com.axians.eaf.products.widgetdemo.jooq.tables.records.WidgetProjectionRecord
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.jvm.JvmName

/**
 * Immutable representation of the widget read model persisted by jOOQ.
 *
 * The projection remains a pure Kotlin data class (no persistence annotations) so
 * it can be materialised both from jOOQ records in production and from Nullable
 * test fixtures.
 */
data class WidgetProjection(
    val widgetId: String,
    @get:JvmName("tenantIdValue")
    val tenantId: String,
    val name: String,
    val description: String?,
    val value: BigDecimal,
    val category: String,
    /** JSON payload stored as text (PostgreSQL jsonb). */
    val metadata: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) : TenantAware {
    override fun getTenantId(): String = tenantId

    fun update(
        name: String = this.name,
        description: String? = this.description,
        value: BigDecimal = this.value,
        category: String = this.category,
        metadata: String? = this.metadata,
        updatedAt: Instant = Instant.now(),
    ): WidgetProjection =
        copy(
            name = name,
            description = description,
            value = value,
            category = category,
            metadata = metadata,
            updatedAt = updatedAt,
        )

    fun toRecord(): WidgetProjectionRecord =
        WidgetProjectionRecord().apply {
            widgetId = UUID.fromString(this@WidgetProjection.widgetId)
            tenantId = UUID.fromString(this@WidgetProjection.tenantId)
            name = this@WidgetProjection.name
            description = this@WidgetProjection.description
            value = this@WidgetProjection.value
            category = this@WidgetProjection.category
            metadata = this@WidgetProjection.metadata?.let { org.jooq.JSON.valueOf(it) }
            createdAt = this@WidgetProjection.createdAt.atOffset(ZoneOffset.UTC)
            updatedAt = this@WidgetProjection.updatedAt.atOffset(ZoneOffset.UTC)
        }

    companion object {
        fun fromRecord(record: WidgetProjectionRecord): WidgetProjection =
            WidgetProjection(
                widgetId = record.widgetId?.toString() ?: error("widget_id must not be null"),
                tenantId = record.tenantId?.toString() ?: error("tenant_id must not be null"),
                name = record.name ?: error("name must not be null"),
                description = record.description,
                value = record.value ?: BigDecimal.ZERO,
                category = record.category ?: error("category must not be null"),
                metadata = record.metadata?.data(),
                createdAt = record.createdAt?.toInstant() ?: error("created_at must not be null"),
                updatedAt = record.updatedAt?.toInstant() ?: error("updated_at must not be null"),
            )
    }
}
