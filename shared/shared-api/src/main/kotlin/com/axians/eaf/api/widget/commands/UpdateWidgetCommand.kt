package com.axians.eaf.api.widget.commands

import org.axonframework.modelling.command.TargetAggregateIdentifier
import java.math.BigDecimal

data class UpdateWidgetCommand(
    @TargetAggregateIdentifier
    val widgetId: String,
    val tenantId: String,
    val name: String? = null,
    val description: String? = null,
    val value: BigDecimal? = null,
    val category: String? = null,
    val metadata: Map<String, Any>? = null,
)
