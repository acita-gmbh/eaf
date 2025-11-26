package de.acci.eaf.testing

import de.acci.eaf.core.types.TenantId

public object TenantTestContext {
    private val holder = ThreadLocal<TenantId>()

    public fun set(tenantId: TenantId) {
        holder.set(tenantId)
    }

    public fun clear() {
        holder.remove()
    }

    public fun current(): TenantId? = holder.get()
}
