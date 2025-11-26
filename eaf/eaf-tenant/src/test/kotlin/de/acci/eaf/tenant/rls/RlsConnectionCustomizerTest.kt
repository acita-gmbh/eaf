package de.acci.eaf.tenant.rls

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.tenant.TenantContextElement
import de.acci.eaf.tenant.TenantContextMissingException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
class RlsConnectionCustomizerTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test")
    }

    private lateinit var connection: java.sql.Connection

    @BeforeEach
    fun setUp() {
        connection = DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password
        )
    }

    @AfterEach
    fun tearDown() {
        if (!connection.isClosed) {
            connection.close()
        }
    }

    @Test
    fun `configureConnection sets app_tenant_id session variable`() {
        // Given
        val tenantId = TenantId.generate()

        // When
        RlsConnectionCustomizer.configureConnection(connection, tenantId)

        // Then
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT current_setting('app.tenant_id', true)").use { rs ->
                assertTrue(rs.next(), "Expected result from current_setting query")
                assertEquals(tenantId.value.toString(), rs.getString(1))
            }
        }
    }

    @Test
    fun `withTenantContext configures connection from coroutine context`() = runTest {
        // Given
        val tenantId = TenantId.generate()

        // When/Then
        withContext(TenantContextElement(tenantId)) {
            RlsConnectionCustomizer.withTenantContext(connection) {
                // Verify tenant was set
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT current_setting('app.tenant_id', true)").use { rs ->
                        assertTrue(rs.next(), "Expected result from current_setting query")
                        assertEquals(tenantId.value.toString(), rs.getString(1))
                    }
                }
            }
        }
    }

    @Test
    fun `withTenantContext throws when no tenant context available`() = runTest {
        // When/Then - no TenantContextElement in coroutine context
        assertThrows<TenantContextMissingException> {
            RlsConnectionCustomizer.withTenantContext(connection) {
                // Should not reach here
            }
        }
    }

    @Test
    fun `currentTenantOrNull returns tenant when context available`() = runTest {
        // Given
        val tenantId = TenantId.generate()

        // When/Then
        withContext(TenantContextElement(tenantId)) {
            val result = RlsConnectionCustomizer.currentTenantOrNull()
            assertEquals(tenantId, result)
        }
    }

    @Test
    fun `currentTenantOrNull returns null when no context`() = runTest {
        // When/Then - no TenantContextElement
        val result = RlsConnectionCustomizer.currentTenantOrNull()
        assertNull(result)
    }

    @Test
    fun `set_config with is_local false persists across transactions`() {
        // Given
        val tenantId = TenantId.generate()
        connection.autoCommit = false

        // When - set tenant in a transaction
        RlsConnectionCustomizer.configureConnection(connection, tenantId)

        // Verify it's set
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT current_setting('app.tenant_id', true)").use { rs ->
                assertTrue(rs.next(), "Expected result from current_setting query")
                assertEquals(tenantId.value.toString(), rs.getString(1))
            }
        }

        // Commit and start new transaction
        connection.commit()

        // Then - setting should persist after transaction ends (with is_local=false)
        // Note: set_config with is_local=false means it's session-scoped
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT current_setting('app.tenant_id', true)").use { rs ->
                assertTrue(rs.next(), "Expected result from current_setting query after commit")
                // After commit, the session setting should still be set
                assertEquals(tenantId.value.toString(), rs.getString(1))
            }
        }
    }
}
