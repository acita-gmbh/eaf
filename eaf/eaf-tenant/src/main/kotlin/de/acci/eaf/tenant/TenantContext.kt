package de.acci.eaf.tenant

import de.acci.eaf.core.types.TenantId
import kotlinx.coroutines.reactor.ReactorContext
import kotlin.coroutines.coroutineContext

/**
 * Accessor for the current Tenant Context.
 *
 * Supports both coroutine context element (optionally backed by ThreadLocal) and reactive
 * (Reactor context) execution models.
 */
public object TenantContext {

    /** Key used to store tenant ID in Reactor context. */
    public const val REACTOR_TENANT_KEY: String = "tenantId"

    /**
     * Returns the tenant from the current coroutine context or throws.
     *
     * @throws TenantContextMissingException if no tenant is set in context.
     * @throws IllegalStateException if context contains invalid type.
     */
    public suspend fun current(): TenantId =
        currentOrNull() ?: throw TenantContextMissingException()

    /** Returns the tenant from the current coroutine context or null if absent. */
    public suspend fun currentOrNull(): TenantId? {
        val direct = coroutineContext[TenantContextElement]?.tenantId
        if (direct != null) return direct

        val reactorContext = coroutineContext[ReactorContext]?.context ?: return null

        if (reactorContext.hasKey(REACTOR_TENANT_KEY)) {
            val value = reactorContext.get<Any>(REACTOR_TENANT_KEY)
            return if (value is TenantId) {
                value
            } else {
                throw IllegalStateException("Invalid type for tenant context: ${value?.javaClass?.name}")
            }
        }
        return null
    }
}
