package com.axians.eaf.products.widgetdemo.entities

import com.axians.eaf.framework.core.tenant.TenantAware
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant

/**
 * JPA entity representing a Widget projection for read operations.
 *
 * This entity is part of the CQRS read-side and is populated by the
 * WidgetProjectionHandler when WidgetCreatedEvent is processed.
 *
 * The entity implements TenantAware for multi-tenant isolation and includes
 * audit fields for tracking creation and updates.
 */
@Entity
@Table(
    name = "widget_projection",
    indexes = [
        Index(name = "idx_widget_projection_tenant_id", columnList = "tenant_id"),
        Index(name = "idx_widget_projection_category", columnList = "category"),
        Index(name = "idx_widget_projection_created_at", columnList = "created_at"),
    ],
)
data class WidgetProjection(
    @Id
    @Column(name = "widget_id", nullable = false, length = 36)
    val widgetId: String,
    @Column(name = "tenant_id", nullable = false, length = 36)
    private val tenantId: String,
    @Column(name = "name", nullable = false, length = 255)
    val name: String,
    @Column(name = "description", nullable = true, length = 1000)
    val description: String?,
    @Column(name = "value", nullable = false, precision = 19, scale = 2)
    val value: BigDecimal,
    @Column(name = "category", nullable = false, length = 100)
    val category: String,
    // Story 6.2: Hibernate 6 JSONB type handler for PostgreSQL
    // JSON stored as string for PostgreSQL jsonb compatibility
    @Column(name = "metadata", nullable = true, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    val metadata: String?,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
) : TenantAware {
    /**
     * Default constructor required by JPA.
     */
    constructor() : this(
        widgetId = "",
        tenantId = "",
        name = "",
        description = null,
        value = BigDecimal.ZERO,
        category = "",
        metadata = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    override fun getTenantId(): String = tenantId

    /**
     * Creates a copy of this projection with updated timestamp.
     *
     * @param name new name value
     * @param description new description value
     * @param value new value
     * @param category new category value
     * @param metadata new metadata as JSON string
     * @return updated copy of the projection
     */
    fun update(
        name: String = this.name,
        description: String? = this.description,
        value: BigDecimal = this.value,
        category: String = this.category,
        metadata: String? = this.metadata,
    ): WidgetProjection =
        copy(
            name = name,
            description = description,
            value = value,
            category = category,
            metadata = metadata,
            updatedAt = Instant.now(),
        )
}
