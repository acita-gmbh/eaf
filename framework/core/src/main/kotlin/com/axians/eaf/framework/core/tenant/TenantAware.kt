package com.axians.eaf.framework.core.tenant

/**
 * Interface for entities that are tenant-aware in a multi-tenant system.
 *
 * This interface provides a contract for entities that need to be isolated
 * by tenant, ensuring proper data segregation in multi-tenant environments.
 */
interface TenantAware {
    /**
     * Returns the tenant ID that this entity belongs to.
     *
     * @return the tenant ID as a String
     */
    fun getTenantId(): String
}
