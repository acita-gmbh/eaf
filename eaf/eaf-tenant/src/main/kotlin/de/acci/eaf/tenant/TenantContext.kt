package de.acci.eaf.tenant

import de.acci.eaf.core.types.TenantId
import kotlinx.coroutines.reactor.ReactorContext
import kotlin.coroutines.coroutineContext

public object TenantContext {

    /** Key used to store tenant ID in Reactor context. */
    public const val REACTOR_TENANT_KEY: String = "tenantId"

    /** Returns the tenant from the current coroutine context or throws. */
    public suspend fun current(): TenantId =
        currentOrNull() ?: throw TenantContextMissingException()

    /** Returns the tenant from the current coroutine context or null if absent. */
    public suspend fun currentOrNull(): TenantId? {
        val direct = coroutineContext[TenantContextElement]?.tenantId
        if (direct != null) return direct

        val reactorContext = coroutineContext[ReactorContext]?.context ?: return null

        // Use hasKey + get pattern to avoid type inference issues with value classes.
        // getOrDefault(key, null) can cause ClassCastException with @JvmInline value classes
        // due to Kotlin/Reactor generic type inference mismatch.
        return if (reactorContext.hasKey(REACTOR_TENANT_KEY)) {
            @Suppress("UNCHECKED_CAST")
            reactorContext.get<Any>(REACTOR_TENANT_KEY) as? TenantId
        } else {
            null
        }
    }
}
