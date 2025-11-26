package de.acci.eaf.tenant

import de.acci.eaf.core.types.TenantId
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Coroutine context element carrying the current tenant id. */
public class TenantContextElement(
    public val tenantId: TenantId
) : AbstractCoroutineContextElement(Key) {
    public companion object Key : CoroutineContext.Key<TenantContextElement>
}
