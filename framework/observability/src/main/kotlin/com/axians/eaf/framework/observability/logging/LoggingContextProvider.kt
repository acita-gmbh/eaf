package com.axians.eaf.framework.observability.logging

import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Provides context field injection for structured logging.
 * Automatically populates MDC with service_name, trace_id, and tenant_id
 * for inclusion in JSON log entries.
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

    companion object {
        const val SERVICE_NAME_KEY = "service_name"
        const val TRACE_ID_KEY = "trace_id"
        const val TENANT_ID_KEY = "tenant_id"
    }
}
