package de.acci.dvmm.api.security

import org.springframework.http.HttpStatus
import org.springframework.security.web.server.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Controller for CSRF token retrieval.
 *
 * Provides an endpoint for SPAs to fetch the CSRF token.
 * The token is also set as a cookie (XSRF-TOKEN) by Spring Security.
 *
 * Frontend should:
 * 1. Call GET /api/csrf to trigger token generation
 * 2. Read XSRF-TOKEN cookie value
 * 3. Include X-XSRF-TOKEN header in mutation requests (POST, PUT, DELETE)
 */
@RestController
@RequestMapping("/api/csrf")
public class CsrfController {

    /**
     * Returns the current CSRF token.
     *
     * This endpoint forces Spring Security to generate the CSRF token
     * and set the XSRF-TOKEN cookie. The response also includes the
     * token details for convenience.
     */
    @GetMapping
    public fun getCsrfToken(exchange: ServerWebExchange): Mono<CsrfTokenResponse> {
        val csrfToken = exchange.getAttribute<Mono<CsrfToken>>(CsrfToken::class.java.name)
            ?: return Mono.error(CsrfTokenNotAvailableException())

        return csrfToken.map { token ->
            CsrfTokenResponse(
                headerName = token.headerName,
                parameterName = token.parameterName,
                token = token.token
            )
        }
    }
}

/**
 * Exception thrown when CSRF token is not available in the server exchange.
 *
 * This indicates a misconfiguration where Spring Security's CSRF support
 * is not properly enabled or the CsrfToken attribute was not populated.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class CsrfTokenNotAvailableException : RuntimeException(
    "CSRF token not available. Ensure CSRF protection is properly configured."
)

/**
 * Response DTO for CSRF token endpoint.
 *
 * @property headerName The HTTP header name to use (X-XSRF-TOKEN)
 * @property parameterName The form parameter name (if using form submission)
 * @property token The CSRF token value
 */
public data class CsrfTokenResponse(
    val headerName: String,
    val parameterName: String,
    val token: String
)
