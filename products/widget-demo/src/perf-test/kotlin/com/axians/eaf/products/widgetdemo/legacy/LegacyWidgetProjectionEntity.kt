package com.axians.eaf.products.widgetdemo.legacy

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "widget_projection")
open class LegacyWidgetProjectionEntity {
    @Id
    @Column(name = "widget_id", nullable = false, length = 36)
    open var widgetId: String = ""

    @Column(name = "tenant_id", nullable = false, length = 36)
    open var tenantId: String = ""

    @Column(name = "name", nullable = false, length = 255)
    open var name: String = ""

    @Column(name = "description", length = 1000)
    open var description: String? = null

    @Column(name = "value", nullable = false, precision = 19, scale = 2)
    open var value: BigDecimal = BigDecimal.ZERO

    @Column(name = "category", nullable = false, length = 100)
    open var category: String = ""

    @Column(name = "metadata", columnDefinition = "jsonb")
    open var metadata: String? = null

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now()
}
