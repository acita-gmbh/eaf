package com.axians.eaf.framework.observability.logging

import com.axians.eaf.framework.security.tenant.TenantContext
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
 * Integrates with TenantContext from Story 4.1 and manages trace_id generation.
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
            // Generate or extract trace ID
            val traceId = request.getHeader("X-Trace-ID") ?: generateTraceId()
            loggingContextProvider.setTraceId(traceId)

            // Set trace ID in response header for client correlation
            response.setHeader("X-Trace-ID", traceId)

            // Extract tenant ID from TenantContext (Story 4.1)
            val tenantId = tenantContext.current()
            loggingContextProvider.setTenantId(tenantId)

            logger.debug("Logging context set for request: trace_id={}, tenant_id={}", traceId, tenantId)
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
