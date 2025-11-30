package de.acci.dvmm.api.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Custom CSRF validation filter for API mutations using double-submit cookie pattern.
 *
 * Spring Security's default CSRF is not strictly enforced with OAuth2 Resource Server
 * because Bearer tokens already provide CSRF protection (browsers don't auto-attach
 * Authorization headers).
 *
 * This filter provides defense-in-depth by:
 * 1. Requiring X-XSRF-TOKEN header for all state-changing requests to /api endpoints
 * 2. Validating that the header value matches the XSRF-TOKEN cookie value
 *
 * The XSRF-TOKEN cookie is set by Spring Security (via CookieServerCsrfTokenRepository).
 * The frontend reads this cookie and sends the value as the X-XSRF-TOKEN header.
 * The server validates that both values match (double-submit pattern).
 *
 * Note: This is an additional security layer. With JWT Bearer tokens,
 * CSRF attacks are already mitigated.
 *
 * Can be disabled via `eaf.security.csrf.enabled=false` property (useful for testing).
 */
@Component
public class CsrfValidationFilter(
    @Value("\${eaf.security.csrf.enabled:true}")
    private val csrfEnabled: Boolean,
) : WebFilter, Ordered {

    private companion object {
        private const val CSRF_HEADER = "X-XSRF-TOKEN"
        private const val CSRF_COOKIE = "XSRF-TOKEN"
    }

    // Run after Spring Security filter chain (-100) but before controllers
    override fun getOrder(): Int = 0

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        // Skip CSRF validation if disabled via property
        if (!csrfEnabled) {
            return chain.filter(exchange)
        }

        val request = exchange.request
        val path = request.path.value()
        val method = request.method

        // Only validate CSRF for state-changing requests to /api endpoints
        if (!path.startsWith("/api") || !isStateChangingMethod(method)) {
            return chain.filter(exchange)
        }

        // Skip CSRF validation for the CSRF token endpoint itself
        if (path == "/api/csrf") {
            return chain.filter(exchange)
        }

        // Require X-XSRF-TOKEN header for mutations
        val csrfHeader = request.headers.getFirst(CSRF_HEADER)
        if (csrfHeader.isNullOrBlank()) {
            return rejectRequest(exchange, "Missing CSRF header")
        }

        // Get CSRF cookie value for double-submit validation
        val csrfCookie = request.cookies.getFirst(CSRF_COOKIE)?.value
        if (csrfCookie.isNullOrBlank()) {
            return rejectRequest(exchange, "Missing CSRF cookie")
        }

        // Double-submit cookie pattern: header must match cookie value
        if (!csrfHeader.equals(csrfCookie, ignoreCase = false)) {
            return rejectRequest(exchange, "CSRF token mismatch")
        }

        return chain.filter(exchange)
    }

    private fun isStateChangingMethod(method: HttpMethod?): Boolean {
        return method in setOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH)
    }

    private fun rejectRequest(exchange: ServerWebExchange, reason: String): Mono<Void> {
        exchange.response.statusCode = HttpStatus.FORBIDDEN
        exchange.response.headers.add("X-CSRF-Error", reason)
        return exchange.response.setComplete()
    }
}
