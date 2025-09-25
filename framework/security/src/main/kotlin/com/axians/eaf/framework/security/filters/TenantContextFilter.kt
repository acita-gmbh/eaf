package com.axians.eaf.framework.security.filters

import com.axians.eaf.framework.security.tenant.TenantContext
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Layer 1 of 3-layer tenant isolation: Request Layer TenantContext Filter.
 * Extracts validated tenant ID from JWT and populates ThreadLocal TenantContext.
 * Implements fail-closed design for missing tenant claims.
 */
@Component
@Order(200) // Run after JWT authentication (typically @Order(100)) but before authorization
class TenantContextFilter(
    private val tenantContext: TenantContext,
    private val meterRegistry: MeterRegistry? = null,
) : OncePerRequestFilter() {
    companion object {
        private val log = LoggerFactory.getLogger(TenantContextFilter::class.java)
        private const val TENANT_CLAIM = "tenant_id"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startTime = System.nanoTime()

        try {
            // Extract tenant ID from authenticated JWT
            val tenantId = extractTenantFromJwt()

            // Populate TenantContext
            tenantContext.setCurrentTenantId(tenantId)

            log.debug(
                "Tenant context populated for request: {} (tenant: {})",
                request.requestURI,
                tenantId,
            )

            meterRegistry?.counter("tenant.filter.success")?.increment()

            // Continue filter chain
            filterChain.doFilter(request, response)
        } catch (e: IllegalStateException) {
            handleTenantValidationError(request, response, e, "tenant.filter.rejected", e.message ?: "Unknown error")
        } catch (e: SecurityException) {
            handleTenantValidationError(request, response, e, "tenant.filter.security_error", "Security error")
        } catch (e: ClassCastException) {
            handleTenantValidationError(request, response, e, "tenant.filter.type_error", "Authentication type error")
        } finally {
            // Guaranteed cleanup to prevent context leakage
            tenantContext.clearCurrentTenant()

            val processingTimeMs = (System.nanoTime() - startTime) / 1_000_000L
            meterRegistry?.timer("tenant.filter.processing_time")
                ?.record(processingTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS)

            log.debug("Tenant context cleanup completed (processing time: {}ms)", processingTimeMs)
        }
    }

    /**
     * Extracts tenant ID from validated JWT in SecurityContext.
     * Implements fail-closed design for missing tenant claims.
     *
     * @return The tenant ID from JWT claims
     * @throws IllegalStateException if no authentication context or missing tenant_id
     */
    private fun extractTenantFromJwt(): String {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: error("No authentication context found")

        check(authentication is JwtAuthenticationToken) {
            "Authentication is not JWT-based"
        }

        val jwt: Jwt = authentication.token
        val tenantId = jwt.getClaimAsString(TENANT_CLAIM)

        require(!tenantId.isNullOrBlank()) { "Missing or invalid tenant_id claim" }

        return tenantId
    }

    private fun handleTenantValidationError(
        request: HttpServletRequest,
        response: HttpServletResponse,
        e: Exception,
        metricName: String,
        errorType: String,
    ) {
        when (e) {
            is IllegalStateException -> log.warn(
                "Tenant validation failed for request: {} - {}",
                request.requestURI,
                e.message,
            )
            else -> log.error(
                "Tenant validation error for request: {}",
                request.requestURI,
                e,
            )
        }

        meterRegistry?.counter(metricName)?.increment()

        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = "application/json"
        response.writer.write("""{"error":"Tenant validation failed: $errorType"}""")
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        // Apply to all requests by default - security configuration will determine which endpoints need security
        return false
    }
}
