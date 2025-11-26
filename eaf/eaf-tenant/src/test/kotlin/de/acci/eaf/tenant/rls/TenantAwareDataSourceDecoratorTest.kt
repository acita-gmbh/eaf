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
    fun `decorator does not set tenant when no context available`() {
        // Given
        val decorator = TenantAwareDataSourceDecorator(simpleDataSource)

        // When - no tenant context set
        decorator.connection.use { conn ->
            // Then - tenant should not be set (RLS fail-closed applies)
            val actualTenantId = getTenantIdFromSession(conn)
            assertNull(actualTenantId)
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

    private fun getTenantIdFromSession(conn: Connection): String? {
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT current_setting('app.tenant_id', true)").use { rs ->
                rs.next()
                return rs.getString(1)
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
