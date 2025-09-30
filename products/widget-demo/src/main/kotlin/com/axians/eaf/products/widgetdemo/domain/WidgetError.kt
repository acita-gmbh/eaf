package com.axians.eaf.products.widgetdemo.domain

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
    ) : WidgetError() {
        // CWE-209 Protection: Override toString() to prevent tenant ID disclosure
        // Tenant IDs must never appear in error messages (enables tenant enumeration)
        override fun toString(): String = "Access denied: tenant context mismatch"
    }

    data class NotFound(
        val widgetId: String,
    ) : WidgetError()

    data class Conflict(
        val reason: String,
    ) : WidgetError()
}
