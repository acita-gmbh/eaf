package de.acci.eaf.tenant

import de.acci.eaf.core.types.TenantId
import kotlinx.coroutines.reactor.ReactorContext
import kotlin.coroutines.coroutineContext

public object TenantContext {

    /** Returns the tenant from the current coroutine context or throws. */
    public suspend fun current(): TenantId =
        currentOrNull() ?: throw TenantContextMissingException()

    /** Returns the tenant from the current coroutine context or null if absent. */
    public suspend fun currentOrNull(): TenantId? {
        val direct = coroutineContext[TenantContextElement]?.tenantId
        if (direct != null) return direct

        val reactorContext = coroutineContext[ReactorContext]?.context
        val fromReactor = reactorContext
            ?.getOrDefault(REACTOR_TENANT_KEY, null)
            ?.let { it as? TenantId }

        return fromReactor
    }

    private const val REACTOR_TENANT_KEY: String = "tenantId"
}
