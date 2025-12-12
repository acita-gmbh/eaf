package de.acci.eaf.testing

import de.acci.eaf.core.types.TenantId

/**
 * Thread-local context holder for Tenant ID during tests.
 *
 * Simulates the TenantContext used in production (e.g. from JWTs).
 */
public object TenantTestContext {
    private val holder = ThreadLocal<TenantId>()

    /** Sets the current tenant for this thread. */
    public fun set(tenantId: TenantId) {
        holder.set(tenantId)
    }

    /** Clears the current tenant. */
    public fun clear() {
        holder.remove()
    }

    /** Gets the current tenant if set. */
    public fun current(): TenantId? = holder.get()
}
