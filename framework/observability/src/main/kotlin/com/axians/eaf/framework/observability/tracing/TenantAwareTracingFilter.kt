package com.axians.eaf.framework.observability.tracing

import com.axians.eaf.framework.security.tenant.TenantContext
import io.opentelemetry.api.trace.Span
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Enriches active OpenTelemetry spans with tenant_id attribute from TenantContext.
 *
 * Follows graceful fallback pattern from Story 5.2 CustomMetrics: falls back to "system"
 * for infrastructure observability when tenant context is unavailable.
 *
 * Story 5.3: Implement OpenTelemetry (Tracing) Configuration
 * Risk Mitigation: SEC-001 (tenant context leakage prevention)
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@ConditionalOnProperty(
    prefix = "management.tracing",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class TenantAwareTracingFilter(
    private val tenantContext: TenantContext,
    @param:org.springframework.beans.factory.annotation.Value("\${spring.application.name:eaf-service}")
    private val serviceName: String,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val span = Span.current()
        if (span.isRecording) {
            // Graceful fallback to "system" for infrastructure observability
            // (e.g., health checks, actuator endpoints without tenant context)
            val tenantId = tenantContext.current() ?: "system"
            span.setAttribute("tenant_id", tenantId)

            // Add service name for consistency with metrics tagging
            span.setAttribute("service_name", serviceName)
        }

        filterChain.doFilter(request, response)
    }
}
