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

/** Coroutine-friendly WebFilter that installs tenant context from JWT claim tenant_id. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextWebFilter : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val tenantId = extractTenantId(exchange) ?: return Mono.error(TenantContextMissingException())

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
