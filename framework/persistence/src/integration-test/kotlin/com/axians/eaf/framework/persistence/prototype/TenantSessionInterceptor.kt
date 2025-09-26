package com.axians.eaf.framework.persistence.prototype

import com.axians.eaf.framework.security.tenant.TenantContext
import org.hibernate.resource.jdbc.spi.StatementInspector
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.sql.Connection
import javax.sql.DataSource

/**
 * PROTOTYPE: Tenant Session Interceptor for RLS Validation
 *
 * This is a prototype implementation to validate SEC-001 and SEC-002 risks
 * before full Story 4.3 implementation.
 *
 * Responsibilities:
 * 1. Set PostgreSQL session variable `app.current_tenant` at transaction start
 * 2. Use `SET LOCAL` to scope variable to transaction only (prevents leakage)
 * 3. Log all session variable operations for observability
 * 4. Fail-closed: throw exception if TenantContext missing
 *
 * Risk Mitigation:
 * - SEC-001: Interceptor executes before queries via Hibernate interceptor chain
 * - SEC-002: `SET LOCAL` ensures variable clears on transaction commit/rollback
 */
@Component
class TenantSessionInterceptor(
    private val tenantContext: TenantContext,
) : StatementInspector {
    private val logger = LoggerFactory.getLogger(TenantSessionInterceptor::class.java)

    companion object {
        private const val SESSION_VAR_NAME = "app.current_tenant"

        /**
         * Generate SQL to set session variable using SET LOCAL
         * SET LOCAL scopes the variable to the current transaction only
         */
        fun generateSetLocalSql(tenantId: String): String = "SET LOCAL $SESSION_VAR_NAME = '$tenantId'"

        /**
         * Validate session variable SQL for correctness
         */
        fun validateSessionVariableSql(sql: String): Boolean = sql.contains("SET LOCAL") && sql.contains(SESSION_VAR_NAME)
    }

    /**
     * This method is called by Hibernate before each statement execution.
     * We use it to ensure session variable is set before any query.
     *
     * Note: This is a prototype approach. Full implementation may use
     * ConnectionProvider or TransactionInterceptor for better integration.
     */
    override fun inspect(sql: String): String {
        // Log the SQL being inspected (prototype observability)
        logger.trace("Inspecting SQL: {}", sql.take(100))

        // Return original SQL unchanged
        // (Session variable should be set at transaction start, not per-statement)
        return sql
    }

    /**
     * Set tenant session variable on a connection.
     * This should be called at transaction start.
     *
     * @param connection The database connection
     * @throws IllegalStateException if TenantContext is not set (fail-closed)
     */
    fun setTenantSessionVariable(connection: Connection) {
        // Fail-closed: throw exception if tenant context missing
        val tenantId =
            tenantContext.getCurrentTenantId()
                ?: throw IllegalStateException(
                    "Cannot set tenant session variable: TenantContext not set (fail-closed security check)",
                )

        val sql = generateSetLocalSql(tenantId)

        logger.debug(
            "Setting tenant session variable: {} for tenant: {} (tx: {})",
            SESSION_VAR_NAME,
            tenantId,
            getTransactionId(connection),
        )

        // Execute SET LOCAL statement
        connection.createStatement().use { statement ->
            statement.execute(sql)
        }

        logger.info(
            "Tenant session variable set successfully: tenant={}, tx={}",
            tenantId,
            getTransactionId(connection),
        )
    }

    /**
     * Verify session variable is set correctly on a connection.
     * Useful for connection pool validation.
     *
     * @param connection The database connection
     * @return The current value of app.current_tenant or null if not set
     */
    fun verifyTenantSessionVariable(connection: Connection): String? {
        val sql = "SELECT current_setting('$SESSION_VAR_NAME', true)"

        return connection.createStatement().use { statement ->
            val resultSet = statement.executeQuery(sql)
            if (resultSet.next()) {
                val value = resultSet.getString(1)
                logger.debug(
                    "Verified tenant session variable: {} = {} (tx: {})",
                    SESSION_VAR_NAME,
                    value,
                    getTransactionId(connection),
                )
                value
            } else {
                logger.warn(
                    "Tenant session variable not set: {} (tx: {})",
                    SESSION_VAR_NAME,
                    getTransactionId(connection),
                )
                null
            }
        }
    }

    /**
     * Get transaction ID for logging purposes.
     * In prototype, we use connection hash code.
     * Full implementation should use proper transaction ID.
     */
    private fun getTransactionId(connection: Connection): String = "conn-${connection.hashCode()}"
}

/**
 * PROTOTYPE: DataSource wrapper that sets tenant session variable
 *
 * This wrapper intercepts connection acquisition and sets the session variable.
 * This validates that interceptor executes before any Axon queries.
 */
class TenantAwareDataSourceWrapper(
    private val delegate: DataSource,
    private val interceptor: TenantSessionInterceptor,
) : DataSource by delegate {
    private val logger = LoggerFactory.getLogger(TenantAwareDataSourceWrapper::class.java)

    override fun getConnection(): Connection {
        val connection = delegate.connection
        logger.debug("Connection acquired: {}", connection.hashCode())

        // Set tenant session variable on connection acquisition
        try {
            interceptor.setTenantSessionVariable(connection)
        } catch (e: Exception) {
            logger.error("Failed to set tenant session variable, closing connection", e)
            connection.close()
            throw e
        }

        return connection
    }

    override fun getConnection(
        username: String,
        password: String,
    ): Connection {
        val connection = delegate.getConnection(username, password)
        logger.debug("Connection acquired with credentials: {}", connection.hashCode())

        // Set tenant session variable on connection acquisition
        try {
            interceptor.setTenantSessionVariable(connection)
        } catch (e: Exception) {
            logger.error("Failed to set tenant session variable, closing connection", e)
            connection.close()
            throw e
        }

        return connection
    }
}
