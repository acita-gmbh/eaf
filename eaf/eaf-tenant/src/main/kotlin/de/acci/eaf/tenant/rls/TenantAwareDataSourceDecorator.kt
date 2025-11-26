package de.acci.eaf.tenant.rls

import de.acci.eaf.core.types.TenantId
import java.io.PrintWriter
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.util.logging.Logger
import javax.sql.DataSource

/**
 * DataSource decorator that automatically injects tenant context for RLS enforcement.
 *
 * This decorator wraps any DataSource (typically HikariCP) and configures each connection
 * with the current tenant ID from a ThreadLocal holder before returning it. This ensures
 * that PostgreSQL Row-Level Security policies can filter data by tenant.
 *
 * **Usage Pattern:**
 * 1. Before executing database operations, set the tenant context:
 *    ```kotlin
 *    TenantAwareDataSourceDecorator.setCurrentTenant(tenantId)
 *    try {
 *        // Database operations will use this tenant context
 *    } finally {
 *        TenantAwareDataSourceDecorator.clearCurrentTenant()
 *    }
 *    ```
 *
 * 2. For scoped tenant context, use [withTenant]:
 *    ```kotlin
 *    TenantAwareDataSourceDecorator.withTenant(tenantId) {
 *        // Database operations here
 *    }
 *    ```
 *
 * @param delegate The underlying DataSource to wrap
 * @see RlsConnectionCustomizer
 */
public class TenantAwareDataSourceDecorator(
    private val delegate: DataSource
) : DataSource {

    public companion object {
        private val currentTenant = ThreadLocal<TenantId>()

        /**
         * Sets the tenant context for the current thread.
         * Must be paired with [clearCurrentTenant] in a finally block.
         */
        public fun setCurrentTenant(tenantId: TenantId) {
            currentTenant.set(tenantId)
        }

        /**
         * Clears the tenant context for the current thread.
         */
        public fun clearCurrentTenant() {
            currentTenant.remove()
        }

        /**
         * Gets the current tenant for the thread, or null if not set.
         */
        public fun getCurrentTenant(): TenantId? = currentTenant.get()

        /**
         * Executes a block with the given tenant context.
         */
        public inline fun <T> withTenant(tenantId: TenantId, block: () -> T): T {
            setCurrentTenant(tenantId)
            try {
                return block()
            } finally {
                clearCurrentTenant()
            }
        }
    }

    @Throws(SQLException::class)
    override fun getConnection(): Connection {
        val connection = delegate.connection
        try {
            configureTenantContext(connection)
            return connection
        } catch (e: Exception) {
            connection.close()
            throw e
        }
    }

    @Throws(SQLException::class)
    override fun getConnection(username: String?, password: String?): Connection {
        val connection = delegate.getConnection(username, password)
        try {
            configureTenantContext(connection)
            return connection
        } catch (e: Exception) {
            connection.close()
            throw e
        }
    }

    private fun configureTenantContext(connection: Connection) {
        val tenantId = currentTenant.get()

        if (tenantId != null) {
            RlsConnectionCustomizer.configureConnection(connection, tenantId)
        }
        // Note: If tenantId is null, RLS fail-closed semantics apply (zero rows returned)
    }

    override fun getLogWriter(): PrintWriter? = delegate.logWriter

    override fun setLogWriter(out: PrintWriter?) {
        delegate.logWriter = out
    }

    override fun setLoginTimeout(seconds: Int) {
        delegate.loginTimeout = seconds
    }

    override fun getLoginTimeout(): Int = delegate.loginTimeout

    @Throws(SQLFeatureNotSupportedException::class)
    override fun getParentLogger(): Logger = delegate.parentLogger

    override fun <T : Any?> unwrap(iface: Class<T>?): T = delegate.unwrap(iface)

    override fun isWrapperFor(iface: Class<*>?): Boolean = delegate.isWrapperFor(iface)
}
