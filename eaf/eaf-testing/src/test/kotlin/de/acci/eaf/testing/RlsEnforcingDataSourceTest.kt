package de.acci.eaf.testing

import de.acci.eaf.core.types.TenantId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

class RlsEnforcingDataSourceTest {

    // Use PGSimpleDataSource as delegate (standard Postgres DataSource)
    private val delegate = PGSimpleDataSource().apply {
        setUrl(TestContainers.postgres.jdbcUrl)
        user = TestContainers.postgres.username
        password = TestContainers.postgres.password
    }

    @Test
    fun `getConnection sets tenant context session variable`() {
        // Given
        val tenantId = TenantId.generate()
        TenantTestContext.set(tenantId)
        val rlsDataSource = RlsEnforcingDataSource(delegate)

        // When
        rlsDataSource.connection.use { conn ->
            // Then
            val stmt = conn.createStatement()
            val rs = stmt.executeQuery("SELECT current_setting('app.tenant_id', true)")
            rs.next()
            val setting = rs.getString(1)
            assertEquals(tenantId.value.toString(), setting)
        }
        
        TenantTestContext.clear()
    }

    @Test
    fun `getConnection throws exception when tenant context is missing`() {
        // Given
        TenantTestContext.clear()
        val rlsDataSource = RlsEnforcingDataSource(delegate)

        // When/Then
        val exception = assertThrows(IllegalStateException::class.java) {
            rlsDataSource.connection
        }
        assertEquals("NO TENANT CONTEXT IN TEST! Use @WithTenant annotation or TenantTestContext.set()", exception.message)
    }
}
