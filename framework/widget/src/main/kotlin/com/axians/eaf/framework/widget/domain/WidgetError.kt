package com.axians.eaf.framework.widget.domain

sealed class WidgetError {
    data class ValidationError(
        val field: String,
        val constraint: String,
        val invalidValue: Any?,
    ) : WidgetError()

    data class BusinessRuleViolation(
        val rule: String,
        val reason: String,
    ) : WidgetError()

    data class TenantIsolationViolation(
        val requestedTenant: String,
        val actualTenant: String,
    ) : WidgetError()

    data class NotFound(
        val widgetId: String,
    ) : WidgetError()

    data class Conflict(
        val reason: String,
    ) : WidgetError()
}
