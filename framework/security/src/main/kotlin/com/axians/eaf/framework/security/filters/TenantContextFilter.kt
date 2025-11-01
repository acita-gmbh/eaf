package com.axians.eaf.framework.security.filters

import com.axians.eaf.framework.security.services.SecurityErrorResponseFormatter
import com.axians.eaf.framework.security.services.TenantExtractionService
import com.axians.eaf.framework.security.tenant.TenantContext
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Layer 1 of 3-layer tenant isolation: Request Layer TenantContext Filter.
 * Extracts validated tenant ID from JWT and populates ThreadLocal TenantContext.
 * Implements fail-closed design for missing tenant claims.
 */
class TenantContextFilter(
    private val tenantContext: TenantContext,
    private val tenantExtractionService: TenantExtractionService,
    private val errorFormatter: SecurityErrorResponseFormatter,
    private val meterRegistry: MeterRegistry? = null,
) : OncePerRequestFilter() {
    companion object {
        private val log = LoggerFactory.getLogger(TenantContextFilter::class.java)

        private val missingTenantDescriptor =
            TenantErrorDescriptor(
                metricName = "tenant.filter.missing_tenant",
                errorType = "missing_tenant_id",
                status = HttpStatus.UNAUTHORIZED,
            )
        private val rejectedDescriptor =
            TenantErrorDescriptor(
                metricName = "tenant.filter.rejected",
                errorType = "Rejected tenant context",
                status = HttpStatus.UNAUTHORIZED,
            )
        private val securityDescriptor =
            TenantErrorDescriptor(
                metricName = "tenant.filter.security_error",
                errorType = "Security error",
                status = HttpStatus.FORBIDDEN,
            )
        private val typeDescriptor =
            TenantErrorDescriptor(
                metricName = "tenant.filter.type_error",
                errorType = "Authentication type error",
                status = HttpStatus.UNAUTHORIZED,
            )
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startTime = System.nanoTime()

        try {
            processTenantAwareRequest(request, response, filterChain)
        } catch (e: MissingTenantClaimException) {
            handleTenantValidationError(request, response, e, missingTenantDescriptor)
        } catch (e: IllegalStateException) {
            handleTenantValidationError(
                request = request,
                response = response,
                e = e,
                descriptor = rejectedDescriptor,
                overrideErrorType = e.message,
            )
        } catch (e: SecurityException) {
            handleTenantValidationError(request, response, e, securityDescriptor)
        } catch (e: ClassCastException) {
            handleTenantValidationError(request, response, e, typeDescriptor)
        } finally {
            // Guaranteed cleanup to prevent context leakage
            tenantContext.clearCurrentTenant()

            val processingTimeMs = (System.nanoTime() - startTime) / 1_000_000L
            meterRegistry
                ?.timer("tenant.filter.processing_time")
                ?.record(processingTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS)

            log.debug("Tenant context cleanup completed (processing time: {}ms)", processingTimeMs)
        }
    }

    private fun processTenantAwareRequest(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val tenantId =
            extractTenantFromJwt()
                ?: run {
                    meterRegistry?.counter("tenant.filter.skipped.no_auth")?.increment()
                    filterChain.doFilter(request, response)
                    return
                }

        tenantContext.setCurrentTenantId(tenantId)

        log.debug(
            "Tenant context populated for request: {} (tenant: {})",
            request.requestURI,
            tenantId,
        )

        meterRegistry?.counter("tenant.filter.success")?.increment()

        filterChain.doFilter(request, response)
    }

    /**
     * Extracts tenant ID from validated JWT in SecurityContext.
     * Delegates to TenantExtractionService for testability.
     *
     * @return The tenant ID from JWT claims
     * @throws IllegalStateException if no authentication context or missing tenant_id
     */
    private fun extractTenantFromJwt(): String? =
        tenantExtractionService.extractTenantIdOrNull(
            SecurityContextHolder.getContext().authentication,
        )

    private fun handleTenantValidationError(
        request: HttpServletRequest,
        response: HttpServletResponse,
        e: Exception,
        descriptor: TenantErrorDescriptor,
        overrideErrorType: String? = null,
    ) {
        when (e) {
            is IllegalStateException ->
                log.warn(
                    "Tenant validation failed for request: {} - {}",
                    request.requestURI,
                    e.message,
                )
            else ->
                log.error(
                    "Tenant validation error for request: {}",
                    request.requestURI,
                    e,
                )
        }

        meterRegistry?.counter(descriptor.metricName)?.increment()

        val errorJson = errorFormatter.formatTenantError(descriptor.errorType, overrideErrorType)
        errorFormatter.writeErrorResponse(response, descriptor.status, errorJson)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.method == "OPTIONS"
}

private class MissingTenantClaimException(
    message: String,
) : IllegalStateException(message)

private data class TenantErrorDescriptor(
    val metricName: String,
    val errorType: String,
    val status: HttpStatus,
)
