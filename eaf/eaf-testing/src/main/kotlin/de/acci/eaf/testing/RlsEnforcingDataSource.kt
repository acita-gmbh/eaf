package de.acci.eaf.testing

import java.sql.Connection
import javax.sql.DataSource

public class RlsEnforcingDataSource(
    private val delegate: DataSource
) : DataSource by delegate {

    override fun getConnection(): Connection {
        val tenant = TenantTestContext.current()
            ?: throw IllegalStateException(
                "NO TENANT CONTEXT IN TEST! " +
                "Use @WithTenant annotation or TenantTestContext.set()"
            )

        return delegate.connection.also { conn ->
            conn.createStatement().execute(
                "SET app.tenant_id = '${tenant.value}'"
            )
        }
    }
}
