package de.acci.eaf.testing

import java.sql.Connection
import javax.sql.DataSource

/**
 * DataSource wrapper that enforces tenant context for Row-Level Security (RLS) testing.
 *
 * Every connection obtained from this DataSource will have the PostgreSQL session variable
 * `app.tenant_id` set to the current tenant from [TenantTestContext]. This enables RLS
 * policies to filter data by tenant in integration tests.
 *
 * @param delegate The underlying DataSource to wrap
 * @throws IllegalStateException if no tenant context is set when obtaining a connection
 */
public class RlsEnforcingDataSource(
    private val delegate: DataSource
) : DataSource by delegate {

    override fun getConnection(): Connection {
        return configureConnection(delegate.connection)
    }

    override fun getConnection(username: String?, password: String?): Connection {
        return configureConnection(delegate.getConnection(username, password))
    }

    private fun configureConnection(conn: Connection): Connection {
        val tenant = TenantTestContext.current()
            ?: throw IllegalStateException(
                "NO TENANT CONTEXT IN TEST! " +
                "Use @WithTenant annotation or TenantTestContext.set()"
            )

        // Switch to non-superuser role so RLS is enforced
        // The dvmm_app role is created in the event store migration
        conn.createStatement().use { stmt ->
            stmt.execute("SET ROLE dvmm_app")
        }

        // Use set_config() with PreparedStatement to avoid SQL injection
        // set_config(setting_name, new_value, is_local) - is_local=false means session-scoped
        conn.prepareStatement("SELECT set_config('app.tenant_id', ?, false)").use { stmt ->
            stmt.setString(1, tenant.value.toString())
            stmt.execute()
        }

        return conn
    }
}
