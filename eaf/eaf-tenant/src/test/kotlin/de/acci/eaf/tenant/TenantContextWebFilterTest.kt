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
    fun `missing tenant header returns 403`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"))
        val chain = CapturingChain()

        StepVerifier.create(filter.filter(exchange, chain))
            .expectError(TenantContextMissingException::class.java)
            .verify()
    }

    @Test
    fun `invalid tenant claim returns 403`() {
        val badToken = jwtRaw("{\"tenant_id\":\"not-a-uuid\"}")
        val exchange = exchangeWithAuth(badToken)
        val chain = CapturingChain()

        StepVerifier.create(filter.filter(exchange, chain))
            .expectError(TenantContextMissingException::class.java)
            .verify()
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
                val tenantId = reactorCtx.get<TenantId>(REACTOR_TENANT_KEY)
                mono(context = reactorCtx.asCoroutineContext() + TenantContextElement(tenantId)) {
                    observedTenant = TenantContext.current()
                    Unit
                }
            }.then()
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

    private companion object {
        const val REACTOR_TENANT_KEY: String = "tenantId"
    }
}
