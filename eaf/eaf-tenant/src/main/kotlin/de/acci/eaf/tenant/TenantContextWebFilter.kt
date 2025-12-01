package de.acci.eaf.tenant

import de.acci.eaf.core.types.TenantId
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Coroutine-friendly WebFilter that installs tenant context from JWT claim tenant_id.
 *
 * This filter runs early in the chain to extract and set tenant context. If no JWT
 * token is present or the token doesn't contain a tenant_id claim, the request is
 * passed through without tenant context. Spring Security will then handle authentication
 * and return 401 if needed. Controllers that require tenant context should call
 * [TenantContext.current] which will throw [TenantContextMissingException] if missing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextWebFilter : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val tenantId = extractTenantId(exchange)

        // If no tenant found, pass through without setting context.
        // Spring Security will handle authentication (401), and controllers
        // will fail with TenantContextMissingException (403) if they require tenant.
        if (tenantId == null) {
            return chain.filter(exchange)
        }

        return chain.filter(exchange)
            .contextWrite { ctx -> ctx.put(TenantContext.REACTOR_TENANT_KEY, tenantId) }
    }

    private fun extractTenantId(exchange: ServerWebExchange): TenantId? {
        val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION) ?: return null
        if (!authHeader.startsWith("Bearer ", ignoreCase = true)) return null

        val token = authHeader.substring(7).trim()
        return JwtTenantClaimExtractor.extractTenantId(token)
    }
}
