package com.axians.eaf.api.widget.events

import java.math.BigDecimal
import java.time.Instant

data class WidgetUpdatedEvent(
    val widgetId: String,
    val tenantId: String,
    val name: String?,
    val description: String?,
    val value: BigDecimal?,
    val category: String?,
    val metadata: Map<String, Any>?,
    val updatedAt: Instant = Instant.now()
)