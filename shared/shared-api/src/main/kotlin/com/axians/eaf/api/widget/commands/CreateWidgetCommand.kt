package com.axians.eaf.api.widget.commands

import org.axonframework.modelling.command.TargetAggregateIdentifier
import java.math.BigDecimal

data class CreateWidgetCommand(
    @TargetAggregateIdentifier
    val widgetId: String,
    val tenantId: String,
    val name: String,
    val description: String?,
    val value: BigDecimal,
    val category: String,
    val metadata: Map<String, Any> = emptyMap()
)