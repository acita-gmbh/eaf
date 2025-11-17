package com.axians.eaf.framework.multitenancy

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

/**
 * Servlet filter that extracts tenant_id from JWT and populates TenantContext (Layer 1).
 *
 * **3-Layer Tenant Isolation - Layer 1: JWT Extraction**
 * - Runs AFTER Spring Security JWT validation (Epic 3)
 * - Extracts tenant_id from validated JWT claims
 * - Populates TenantContext ThreadLocal for downstream processing
 * - Missing tenant_id → 400 Bad Request (fail-closed design)
 * - Cleanup in finally block prevents ThreadLocal leaks
 *
 * **Execution Order:**
 * - Spring Security: @Order(Ordered.HIGHEST_PRECEDENCE) - JWT validation
 * - TenantContextFilter: @Order(Ordered.HIGHEST_PRECEDENCE + 10) - Tenant extraction
 * - Business logic: Default order
 *
 * **Metrics Emitted:**
 * - tenant_context_extraction_duration (Timer) - Filter execution time
 * - missing_tenant_failures (Counter) - Count of missing tenant_id failures
 *
 * Epic 4, Story 4.2: AC1, AC2, AC3, AC4, AC5, AC7
 *
 * @param meterRegistry Micrometer registry for metrics emission
 * @since 1.0.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class TenantContextFilter(
    private val meterRegistry: MeterRegistry,
    private val objectMapper: ObjectMapper,
) : Filter {
    private val extractionTimer: Timer =
        Timer
            .builder("tenant_context_extraction_duration")
            .description("Time taken to extract and populate tenant context from JWT")
            .tags("layer", "1-jwt-extraction")
            .register(meterRegistry)

    companion object {
        private val log = LoggerFactory.getLogger(TenantContextFilter::class.java)
    }

    /**
     * Extract tenant_id from JWT and populate TenantContext.
     *
     * **Filter Logic:**
     * 1. Extract JWT from SecurityContextHolder (populated by Spring Security in Epic 3)
     * 2. Extract tenant_id claim from JWT
     * 3. Validate tenant_id is present (fail-closed)
     * 4. Populate TenantContext.setCurrentTenantId()
     * 5. Continue filter chain
     * 6. CRITICAL: Cleanup TenantContext in finally block
     *
     * **Error Handling:**
     * - Missing tenant_id → 400 Bad Request
     * - Generic error message for CWE-209 protection
     *
     * AC2: Filter extracts tenant_id from JWT claim (after JWT validation in Epic 3)
     * AC3: TenantContext.set(tenantId) populates ThreadLocal
     * AC4: Missing tenant_id claim rejects request with 400 Bad Request
     * AC5: Filter ensures cleanup in finally block (TenantContext.clear())
     * AC7: Metrics emitted
     */
    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        val httpResponse = response as HttpServletResponse

        // AC7: Start timer for metrics
        val sample = Timer.start(meterRegistry)

        try {
            // AC2: Extract tenant_id from JWT (validated by Spring Security in Epic 3)
            val authentication = SecurityContextHolder.getContext().authentication

            log.debug(
                "TenantContextFilter executing. Authentication type: {}",
                authentication?.javaClass?.simpleName ?: "NULL",
            )

            // Skip tenant extraction for non-JWT requests (e.g., actuator endpoints)
            if (authentication !is JwtAuthenticationToken) {
                log.debug("Non-JWT or NULL authentication found, skipping tenant extraction")
                chain.doFilter(request, response)
                return
            }

            log.debug("JWT Authentication found, proceeding with tenant extraction")

            val jwt = authentication.token
            val tenantId = jwt?.getClaimAsString("tenant_id")

            // AC4: Validate tenant_id presence and format (fail-closed)
            val error = validateTenantId(tenantId, httpResponse)
            if (error != null) {
                return // Error response already sent
            }

            // AC3: Populate TenantContext ThreadLocal (validated at this point)
            TenantContext.setCurrentTenantId(tenantId!!)

            // Continue filter chain with tenant context populated
            chain.doFilter(request, response)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            // Log exception for observability but preserve original exception propagation
            // Infrastructure interceptor pattern: catch for metrics, re-throw unchanged
            meterRegistry
                .counter(
                    "tenant_context_extraction_errors",
                    "error_type",
                    ex.javaClass.simpleName,
                ).increment()
            throw ex
        } finally {
            // AC5: CRITICAL cleanup - clear ThreadLocal to prevent leaks
            TenantContext.clearCurrentTenant()

            // AC7: Record execution duration
            sample.stop(extractionTimer)
        }
    }

    /**
     * Validate tenant_id presence and format.
     * Returns error string if validation fails, null otherwise.
     * Side-effect: Sends 400 Bad Request response if validation fails.
     *
     * Note: Multiple returns are necessary for fail-fast validation pattern.
     */
    @Suppress("ReturnCount")
    private fun validateTenantId(
        tenantId: String?,
        httpResponse: HttpServletResponse,
    ): String? {
        // Check missing tenant_id
        if (tenantId.isNullOrBlank()) {
            meterRegistry
                .counter(
                    "missing_tenant_failures",
                    "reason",
                    "missing_claim",
                ).increment()

            writeErrorResponse(
                httpResponse,
                "Missing required tenant context",
                HttpStatus.BAD_REQUEST.value(),
            )
            return "missing"
        }

        // Check invalid tenant_id format (via TenantId domain validation)
        try {
            TenantId(tenantId)
        } catch (
            @Suppress("SwallowedException")
            ex: IllegalArgumentException,
        ) {
            // Exception is not re-thrown because we convert it to HTTP 400 response
            // This is the correct behavior for fail-closed validation
            meterRegistry
                .counter(
                    "missing_tenant_failures",
                    "reason",
                    "invalid_format",
                ).increment()

            writeErrorResponse(
                httpResponse,
                "Invalid tenant context format",
                HttpStatus.BAD_REQUEST.value(),
            )
            return "invalid"
        }

        return null
    }

    /**
     * Write error response as JSON using ObjectMapper.
     * Ensures consistent JSON formatting and prevents manual string construction issues.
     */
    private fun writeErrorResponse(
        httpResponse: HttpServletResponse,
        errorMessage: String,
        status: Int,
    ) {
        httpResponse.status = status
        httpResponse.contentType = "application/json"
        objectMapper.writeValue(
            httpResponse.writer,
            ErrorResponse(error = errorMessage, status = status),
        )
    }

    /**
     * Error response DTO for consistent JSON error formatting.
     */
    private data class ErrorResponse(
        val error: String,
        val status: Int,
    )
}
