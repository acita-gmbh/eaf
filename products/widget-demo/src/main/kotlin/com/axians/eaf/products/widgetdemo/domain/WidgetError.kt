package com.axians.eaf.products.widgetdemo.domain

class WidgetValidationException(
    val error: WidgetError,
) : RuntimeException("Validation failed for widget: $error")

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

    /**
     * Tenant isolation violation error.
     *
     * SECURITY: Tenant IDs are intentionally NOT stored as properties to prevent
     * information disclosure (CWE-209). This prevents tenant enumeration attacks
     * even if the error object is accidentally serialized in logs or debugging output.
     *
     * Tenant IDs should only be recorded in secure audit logs, never in error objects.
     */
    class TenantIsolationViolation : WidgetError() {
        override fun toString(): String = "Access denied: tenant context mismatch"

        override fun equals(other: Any?): Boolean = other is TenantIsolationViolation

        override fun hashCode(): Int = javaClass.hashCode()
    }

    data class NotFound(
        val widgetId: String,
    ) : WidgetError()

    data class Conflict(
        val reason: String,
    ) : WidgetError()
}
