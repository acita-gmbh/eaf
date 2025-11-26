package de.acci.eaf.tenant.rls

import de.acci.eaf.core.types.TenantId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Connection
import java.sql.DriverManager
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
class TenantAwareDataSourceDecoratorTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test")
    }

    private lateinit var simpleDataSource: SimpleTestDataSource

    @BeforeEach
    fun setUp() {
        simpleDataSource = SimpleTestDataSource(
            url = postgres.jdbcUrl,
            username = postgres.username,
            password = postgres.password
        )
    }

    @AfterEach
    fun tearDown() {
        TenantAwareDataSourceDecorator.clearCurrentTenant()
    }

    @Test
    fun `decorator injects tenant context when set via ThreadLocal`() {
        // Given
        val tenantId = TenantId.generate()
        val decorator = TenantAwareDataSourceDecorator(simpleDataSource)

        // When
        TenantAwareDataSourceDecorator.setCurrentTenant(tenantId)
        decorator.connection.use { conn ->
            // Then
            val actualTenantId = getTenantIdFromSession(conn)
            assertEquals(tenantId.value.toString(), actualTenantId)
        }
    }

    @Test
    fun `withTenant helper sets and clears context correctly`() {
        // Given
        val tenantId = TenantId.generate()
        val decorator = TenantAwareDataSourceDecorator(simpleDataSource)

        // When
        TenantAwareDataSourceDecorator.withTenant(tenantId) {
            decorator.connection.use { conn ->
                // Then - inside block, tenant should be set
                val actualTenantId = getTenantIdFromSession(conn)
                assertEquals(tenantId.value.toString(), actualTenantId)
            }
        }

        // After block, tenant should be cleared
        assertNull(TenantAwareDataSourceDecorator.getCurrentTenant())
    }

    @Test
    fun `decorator clears tenant when no context available`() {
        // Given
        val decorator = TenantAwareDataSourceDecorator(simpleDataSource)

        // When - no tenant context set
        decorator.connection.use { conn ->
            // Then - tenant should be cleared (empty or null) so RLS fail-closed applies
            // PostgreSQL set_config with NULL returns empty string, not actual NULL
            // Both achieve fail-closed: empty string or NULL won't match any UUID
            val actualTenantId = getTenantIdFromSession(conn)
            assertTrue(
                actualTenantId.isNullOrEmpty(),
                "Tenant context should be cleared (null or empty) for RLS fail-closed"
            )
        }
    }

    @Test
    fun `decorator delegates to underlying datasource methods`() {
        // Given
        val decorator = TenantAwareDataSourceDecorator(simpleDataSource)

        // When/Then - verify delegation works
        decorator.loginTimeout = 30
        assertEquals(30, simpleDataSource.loginTimeout)
        assertEquals(30, decorator.loginTimeout)
    }

    @Test
    fun `decorator properly clears tenant context when connection is reused without tenant`() {
        // This test verifies security: when a pooled connection is reused,
        // the previous tenant context must be cleared to prevent tenant leakage
        val tenantA = TenantId.generate()
        val decorator = TenantAwareDataSourceDecorator(simpleDataSource)

        // Simulate first request with Tenant A - get a connection and set tenant
        TenantAwareDataSourceDecorator.setCurrentTenant(tenantA)
        decorator.connection.use { conn ->
            assertEquals(tenantA.value.toString(), getTenantIdFromSession(conn))

            // Simulate connection returned to pool (don't close - reuse same connection)
            TenantAwareDataSourceDecorator.clearCurrentTenant()

            // Simulate second request WITHOUT tenant context (e.g., health check endpoint)
            // The decorator must clear the previous tenant to prevent leakage
            // In production, HikariCP would call getConnection() which triggers this
            val pooledDataSource = PoolSimulatingDataSource(conn)
            val decoratorWithPool = TenantAwareDataSourceDecorator(pooledDataSource)

            decoratorWithPool.connection.use { reusedConn ->
                // Then - previous tenant context should be cleared (not Tenant A!)
                // PostgreSQL set_config with NULL returns empty string, not actual NULL
                val actualTenantId = getTenantIdFromSession(reusedConn)
                assertTrue(
                    actualTenantId.isNullOrEmpty(),
                    "Previous tenant context should be cleared on pooled connection reuse, but was: $actualTenantId"
                )
            }
        }
    }

    @Test
    fun `decorator properly switches tenant context when connection is reused`() {
        // This test verifies that tenant context is properly overwritten when
        // a pooled connection is reused by a different tenant
        val tenantA = TenantId.generate()
        val tenantB = TenantId.generate()
        val decorator = TenantAwareDataSourceDecorator(simpleDataSource)

        // First request with Tenant A
        TenantAwareDataSourceDecorator.setCurrentTenant(tenantA)
        decorator.connection.use { conn ->
            assertEquals(tenantA.value.toString(), getTenantIdFromSession(conn))

            // Simulate connection returned to pool and reused by Tenant B
            TenantAwareDataSourceDecorator.clearCurrentTenant()
            TenantAwareDataSourceDecorator.setCurrentTenant(tenantB)

            val pooledDataSource = PoolSimulatingDataSource(conn)
            val decoratorWithPool = TenantAwareDataSourceDecorator(pooledDataSource)

            decoratorWithPool.connection.use { reusedConn ->
                // Then - tenant context should be Tenant B, not Tenant A
                val actualTenantId = getTenantIdFromSession(reusedConn)
                assertEquals(tenantB.value.toString(), actualTenantId, "Tenant context should be updated to Tenant B")
            }
        }
    }

    /**
     * DataSource that always returns the same connection (simulates connection pool behavior).
     */
    private class PoolSimulatingDataSource(private val pooledConnection: Connection) : DataSource {
        override fun getConnection(): Connection = pooledConnection
        override fun getConnection(username: String?, password: String?): Connection = pooledConnection
        override fun getLogWriter(): java.io.PrintWriter? = null
        override fun setLogWriter(out: java.io.PrintWriter?) {}
        override fun setLoginTimeout(seconds: Int) {}
        override fun getLoginTimeout(): Int = 0
        override fun getParentLogger(): java.util.logging.Logger = throw java.sql.SQLFeatureNotSupportedException()
        override fun <T : Any?> unwrap(iface: Class<T>?): T = throw java.sql.SQLException("Not a wrapper")
        override fun isWrapperFor(iface: Class<*>?): Boolean = false
    }

    private fun getTenantIdFromSession(conn: Connection): String? {
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT current_setting('app.tenant_id', true)").use { rs ->
                return if (rs.next()) rs.getString(1) else null
            }
        }
    }

    /**
     * Simple DataSource implementation for testing that creates new connections.
     */
    private class SimpleTestDataSource(
        private val url: String,
        private val username: String,
        private val password: String
    ) : DataSource {
        private var _loginTimeout: Int = 0
        private var _logWriter: java.io.PrintWriter? = null

        override fun getConnection(): Connection {
            return DriverManager.getConnection(url, username, password)
        }

        override fun getConnection(username: String?, password: String?): Connection {
            return DriverManager.getConnection(url, username, password)
        }

        override fun getLogWriter(): java.io.PrintWriter? = _logWriter

        override fun setLogWriter(out: java.io.PrintWriter?) {
            _logWriter = out
        }

        override fun setLoginTimeout(seconds: Int) {
            _loginTimeout = seconds
        }

        override fun getLoginTimeout(): Int = _loginTimeout

        override fun getParentLogger(): java.util.logging.Logger {
            throw java.sql.SQLFeatureNotSupportedException()
        }

        override fun <T : Any?> unwrap(iface: Class<T>?): T {
            throw java.sql.SQLException("Not a wrapper")
        }

        override fun isWrapperFor(iface: Class<*>?): Boolean = false
    }
}
