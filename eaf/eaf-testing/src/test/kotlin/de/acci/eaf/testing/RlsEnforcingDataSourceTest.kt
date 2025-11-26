package de.acci.eaf.testing

import de.acci.eaf.core.types.TenantId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

class RlsEnforcingDataSourceTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun ensureRoleExists() {
            // Create eaf_app role if it doesn't exist (needed for RLS testing)
            TestContainers.postgres.createConnection("").use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(
                        """
                        DO ${'$'}${'$'}
                        BEGIN
                            IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'eaf_app') THEN
                                CREATE ROLE eaf_app NOLOGIN;
                            END IF;
                        END ${'$'}${'$'};
                        """.trimIndent()
                    )
                }
            }
        }
    }

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
        val rlsDataSource = RlsEnforcingDataSource(delegate)

        try {
            TenantTestContext.set(tenantId)

            // When
            rlsDataSource.connection.use { conn ->
                // Then
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT current_setting('app.tenant_id', true)").use { rs ->
                        rs.next()
                        val setting = rs.getString(1)
                        assertEquals(tenantId.value.toString(), setting)
                    }
                }
            }
        } finally {
            TenantTestContext.clear()
        }
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
