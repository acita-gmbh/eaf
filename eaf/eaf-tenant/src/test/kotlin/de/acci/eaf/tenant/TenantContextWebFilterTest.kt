package de.acci.eaf.tenant

import de.acci.eaf.core.types.TenantId
import kotlinx.coroutines.reactor.asCoroutineContext
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.test.assertEquals

class TenantContextWebFilterTest {

    private val filter = TenantContextWebFilter()

    @Test
    fun `injects tenant context from JWT`() {
        val tenant = TenantId.generate()
        val token = jwtWithTenant(tenant)
        val exchange = exchangeWithAuth(token)
        val chain = CapturingChain()

        filter.filter(exchange, chain).block()

        assertEquals(tenant, chain.observedTenant)
    }

    @Test
    fun `missing tenant header passes through without context`() {
        // When no Authorization header is present, filter passes through
        // to let Spring Security handle authentication (return 401)
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"))
        val chain = PassThroughChain()

        StepVerifier.create(filter.filter(exchange, chain))
            .expectComplete()
            .verify()

        assertEquals(true, chain.wasInvoked, "Filter chain should be invoked")
    }

    @Test
    fun `invalid tenant claim passes through without context`() {
        // When JWT has invalid tenant_id, filter passes through
        // to let controllers handle missing tenant (return 403 via TenantContext.current())
        val badToken = jwtRaw("{\"tenant_id\":\"not-a-uuid\"}")
        val exchange = exchangeWithAuth(badToken)
        val chain = PassThroughChain()

        StepVerifier.create(filter.filter(exchange, chain))
            .expectComplete()
            .verify()

        assertEquals(true, chain.wasInvoked, "Filter chain should be invoked")
    }

    private fun exchangeWithAuth(token: String): ServerWebExchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .build()
        )

    private class CapturingChain : WebFilterChain {
        var observedTenant: TenantId? = null

        override fun filter(exchange: ServerWebExchange): Mono<Void> =
            reactor.core.publisher.Mono.deferContextual { reactorCtx ->
                val tenantId = reactorCtx.get<TenantId>(TenantContext.REACTOR_TENANT_KEY)
                mono(context = reactorCtx.asCoroutineContext() + TenantContextElement(tenantId)) {
                    observedTenant = TenantContext.current()
                    Unit
                }
            }.then()
    }

    private class PassThroughChain : WebFilterChain {
        var wasInvoked = false

        override fun filter(exchange: ServerWebExchange): Mono<Void> {
            wasInvoked = true
            return Mono.empty()
        }
    }

    private fun jwtWithTenant(tenantId: TenantId): String =
        jwtRaw("{\"tenant_id\":\"${tenantId.value}\"}")

    private fun jwtRaw(payload: String): String {
        val header = base64Url("{\"alg\":\"none\"}")
        val body = base64Url(payload)
        return "$header.$body."
    }

    private fun base64Url(value: String): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
}
