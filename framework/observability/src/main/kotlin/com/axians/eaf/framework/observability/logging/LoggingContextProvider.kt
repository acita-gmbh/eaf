package com.axians.eaf.framework.observability.logging

import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Provides context field injection for structured logging.
 * Automatically populates MDC with service_name, trace_id, span_id, and tenant_id
 * for inclusion in JSON log entries.
 *
 * Story 5.3: Extended to support span_id from OpenTelemetry for log-trace correlation.
 */
@Component
class LoggingContextProvider(
    @param:Value("\${spring.application.name:unknown-service}")
    private val serviceName: String,
) {
    /**
     * Sets the service name in MDC context for all log entries.
     * Called during application startup to establish service identity.
     */
    fun setServiceName() {
        MDC.put(SERVICE_NAME_KEY, serviceName)
    }

    /**
     * Sets the trace ID in MDC context for distributed tracing correlation.
     * Typically called by request filters or interceptors.
     */
    fun setTraceId(traceId: String?) {
        if (traceId?.isNotBlank() == true) {
            MDC.put(TRACE_ID_KEY, traceId)
        } else {
            MDC.remove(TRACE_ID_KEY)
        }
    }

    /**
     * Sets the span ID in MDC context for OpenTelemetry trace span correlation.
     * Story 5.3: Enables log-trace correlation at span granularity.
     */
    fun setSpanId(spanId: String?) {
        if (spanId?.isNotBlank() == true) {
            MDC.put(SPAN_ID_KEY, spanId)
        } else {
            MDC.remove(SPAN_ID_KEY)
        }
    }

    /**
     * Sets the tenant ID in MDC context for multi-tenant log isolation.
     * Integrates with TenantContext from Story 4.1.
     */
    fun setTenantId(tenantId: String?) {
        if (tenantId?.isNotBlank() == true) {
            MDC.put(TENANT_ID_KEY, tenantId)
        } else {
            MDC.remove(TENANT_ID_KEY)
        }
    }

    /**
     * Clears all logging context from MDC.
     * Should be called after request completion to prevent context leakage.
     */
    fun clearContext() {
        MDC.remove(TRACE_ID_KEY)
        MDC.remove(SPAN_ID_KEY)
        MDC.remove(TENANT_ID_KEY)
        // Note: service_name remains set for application lifetime
    }

    /**
     * Gets current tenant ID from MDC context.
     * Returns null if not set or empty.
     */
    fun getCurrentTenantId(): String? = MDC.get(TENANT_ID_KEY)?.takeIf { it.isNotBlank() }

    /**
     * Gets current trace ID from MDC context.
     * Returns null if not set or empty.
     */
    fun getCurrentTraceId(): String? = MDC.get(TRACE_ID_KEY)?.takeIf { it.isNotBlank() }

    /**
     * Gets current span ID from MDC context.
     * Returns null if not set or empty.
     * Story 5.3: OpenTelemetry span correlation support.
     */
    fun getCurrentSpanId(): String? = MDC.get(SPAN_ID_KEY)?.takeIf { it.isNotBlank() }

    companion object {
        const val SERVICE_NAME_KEY = "service_name"
        const val TRACE_ID_KEY = "trace_id"
        const val SPAN_ID_KEY = "span_id"
        const val TENANT_ID_KEY = "tenant_id"
    }
}
