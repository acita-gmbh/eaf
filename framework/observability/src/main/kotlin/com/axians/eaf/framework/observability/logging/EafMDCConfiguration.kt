package com.axians.eaf.framework.observability.logging

import com.axians.eaf.framework.security.tenant.TenantContext
import io.opentelemetry.api.trace.Span
import jakarta.annotation.PostConstruct
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.util.UUID

/**
 * Configures MDC (Mapped Diagnostic Context) for automatic context field injection.
 * Integrates with TenantContext from Story 4.1 and OpenTelemetry from Story 5.3.
 *
 * Story 5.3: Extracts trace_id and span_id from active OpenTelemetry span for log-trace correlation.
 */
@Configuration(proxyBeanMethods = false)
open class EafMDCConfiguration(
    private val loggingContextProvider: LoggingContextProvider,
    private val tenantContext: TenantContext,
) : WebMvcConfigurer {
    private val logger = LoggerFactory.getLogger(EafMDCConfiguration::class.java)

    @PostConstruct
    fun initializeServiceName() {
        loggingContextProvider.setServiceName()
        logger.info("EAF Logging: Service name initialized for structured logging")
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(LoggingContextInterceptor())
    }

    /**
     * HTTP request interceptor that manages logging context for each request.
     * Sets trace_id and tenant_id in MDC for the duration of the request.
     */
    inner class LoggingContextInterceptor : HandlerInterceptor {
        override fun preHandle(
            request: HttpServletRequest,
            response: HttpServletResponse,
            handler: Any,
        ): Boolean {
            // Story 5.3: Extract trace_id from OpenTelemetry span when available
            val span = Span.current()
            val traceId =
                if (span.spanContext.isValid) {
                    // Use OpenTelemetry trace ID for log-trace correlation
                    span.spanContext.traceId
                } else {
                    // Fallback to header or generate UUID (no active tracing)
                    request.getHeader("X-Trace-ID") ?: generateTraceId()
                }
            loggingContextProvider.setTraceId(traceId)

            // Story 5.3: Extract span_id from OpenTelemetry for trace span correlation
            if (span.spanContext.isValid) {
                loggingContextProvider.setSpanId(span.spanContext.spanId)
            }

            // Set trace ID in response header for client correlation
            response.setHeader("X-Trace-ID", traceId)

            // Extract tenant ID from TenantContext (Story 4.1)
            val tenantId = tenantContext.current()
            loggingContextProvider.setTenantId(tenantId)

            logger.debug(
                "Logging context set for request: trace_id={}, span_id={}, tenant_id={}",
                traceId,
                span.spanContext.spanId,
                tenantId,
            )
            return true
        }

        override fun afterCompletion(
            request: HttpServletRequest,
            response: HttpServletResponse,
            handler: Any,
            ex: Exception?,
        ) {
            // Clear logging context after request completion
            loggingContextProvider.clearContext()

            if (ex != null) {
                logger.debug("Request completed with exception: {}", ex.message)
            }
        }

        private fun generateTraceId(): String = UUID.randomUUID().toString().replace("-", "")
    }
}
