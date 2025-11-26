package de.acci.eaf.tenant.rls

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.tenant.TenantContext
import java.sql.Connection

/**
 * Customizes database connections with tenant context for Row-Level Security (RLS) enforcement.
 *
 * This class provides utilities to set the PostgreSQL session variable `app.tenant_id`
 * which is used by RLS policies to filter data by tenant. It integrates with the
 * coroutine-based [TenantContext] to obtain the current tenant ID.
 *
 * Usage:
 * - For suspend functions: Use [withTenantContext] to execute code with tenant context
 * - For direct connection configuration: Use [configureConnection] with an explicit tenant ID
 *
 * @see TenantContext
 */
public object RlsConnectionCustomizer {

    /**
     * Configures a connection with the given tenant ID for RLS enforcement.
     *
     * Sets the PostgreSQL session variable `app.tenant_id` using `set_config` with
     * `is_local=false`, which scopes the setting to the entire session (connection lifetime).
     * This ensures tenant context persists across transactions, including autocommit mode.
     *
     * @param connection The database connection to configure
     * @param tenantId The tenant ID to set for RLS filtering
     */
    public fun configureConnection(connection: Connection, tenantId: TenantId) {
        // Use set_config with is_local=false to set for the entire session
        // This ensures the tenant context persists across all queries on this connection
        // Note: is_local=true would scope to transaction only, which doesn't work with autocommit
        connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)").use { stmt ->
            stmt.setString(1, tenantId.value.toString())
            stmt.execute()
        }
    }

    /**
     * Executes a block with tenant context applied to the connection.
     *
     * Retrieves the current tenant from [TenantContext] and configures the connection
     * with the tenant ID before executing the provided block.
     *
     * @param connection The database connection to configure
     * @param block The suspend function to execute with tenant context
     * @return The result of the block execution
     * @throws de.acci.eaf.tenant.TenantContextMissingException if no tenant context is available
     */
    public suspend fun <T> withTenantContext(connection: Connection, block: suspend () -> T): T {
        val tenantId = TenantContext.current()
        configureConnection(connection, tenantId)
        return block()
    }

    /**
     * Retrieves the current tenant from coroutine context if available.
     *
     * @return The current tenant ID, or null if not available
     */
    public suspend fun currentTenantOrNull(): TenantId? {
        return TenantContext.currentOrNull()
    }
}
