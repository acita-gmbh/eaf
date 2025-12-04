package de.acci.dvmm.infrastructure.vmware

import de.acci.dvmm.domain.vmware.VmwareConfiguration
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.testing.TenantTestContext
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Connection
import java.time.Instant

/**
 * Integration tests for VmwareConfigurationRepository RLS (Row-Level Security).
 *
 * Story 3.1: VMware Connection Configuration
 *
 * Verifies that:
 * - Configurations from one tenant are not visible to another tenant
 * - Each tenant can only see and modify their own configuration
 * - RLS provides fail-closed semantics (no tenant context = no data)
 */
@Testcontainers
@DisplayName("VmwareConfigurationRepository RLS Integration Tests")
class VmwareConfigurationRepositoryRlsIntegrationTest {

    companion object {
        private const val TC_DB_NAME = "dvmm_test"

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName(TC_DB_NAME)

        private lateinit var superuserDsl: DSLContext

        @BeforeAll
        @JvmStatic
        fun setupSchema() {
            // Copy and execute schema with RLS policies
            val tmpFile = "/tmp/init.sql"
            postgres.copyFileToContainer(
                org.testcontainers.utility.MountableFile.forClasspathResource("db/jooq-init.sql"),
                tmpFile
            )

            val result = postgres.execInContainer(
                "psql",
                "-U", postgres.username,
                "-d", postgres.databaseName,
                "-v", "ON_ERROR_STOP=1",
                "-f", tmpFile
            )
            if (result.exitCode != 0) {
                throw IllegalStateException("Failed to initialize schema: ${result.stderr}")
            }

            // Superuser DSL bypasses RLS for test data setup/cleanup
            superuserDsl = DSL.using(postgres.jdbcUrl, postgres.username, postgres.password)
        }
    }

    private val tenantA = TenantId.generate()
    private val tenantB = TenantId.generate()

    @BeforeEach
    fun setup() {
        TenantTestContext.set(tenantA)
    }

    @AfterEach
    fun cleanup() {
        TenantTestContext.clear()
        // Clean up test data using superuser connection (bypasses RLS)
        superuserDsl.execute("""TRUNCATE TABLE public."VMWARE_CONFIGURATIONS" CASCADE""")
    }

    /**
     * Creates a DSLContext with tenant context set for RLS.
     */
    private fun createTenantDsl(tenant: TenantId): Pair<Connection, DSLContext> {
        val conn = postgres.createConnection("")
        // Switch to eaf_app role so RLS is enforced
        conn.createStatement().execute("SET ROLE eaf_app")
        // Set tenant context
        conn.prepareStatement("SELECT set_config('app.tenant_id', ?, false)").use { stmt ->
            stmt.setString(1, tenant.value.toString())
            stmt.execute()
        }
        return conn to DSL.using(conn, SQLDialect.POSTGRES)
    }

    /**
     * Executes a suspend block with a tenant-scoped DSLContext.
     */
    private suspend fun <T> withTenantDsl(tenant: TenantId, block: suspend (DSLContext) -> T): T {
        val (conn, dsl) = createTenantDsl(tenant)
        return conn.use { block(dsl) }
    }

    private fun createTestConfig(
        tenantId: TenantId,
        vcenterUrl: String = "https://vcenter.example.com/sdk",
        username: String = "admin@vsphere.local",
        userId: UserId = UserId.generate()
    ): VmwareConfiguration = VmwareConfiguration.create(
        tenantId = tenantId,
        vcenterUrl = vcenterUrl,
        username = username,
        passwordEncrypted = "encrypted-password".toByteArray(),
        datacenterName = "DC1",
        clusterName = "Cluster1",
        datastoreName = "Datastore1",
        networkName = "VM-Network",
        templateName = VmwareConfiguration.DEFAULT_TEMPLATE_NAME,
        folderPath = null,
        userId = userId,
        timestamp = Instant.now()
    )

    @Nested
    @DisplayName("findByTenantId()")
    inner class FindByTenantIdTests {

        @Test
        @DisplayName("should not see config from different tenant")
        fun `config from tenant A is not visible to tenant B`() = runTest {
            // Given - save config for tenant A using superuser (bypasses RLS)
            val configA = createTestConfig(tenantA, vcenterUrl = "https://vcenter-a.example.com/sdk")
            withTenantDsl(tenantA) { dsl ->
                val repo = VmwareConfigurationRepository(dsl)
                val result = repo.save(configA)
                assertTrue(result is Result.Success)
            }

            // When - tenant B queries for their config
            withTenantDsl(tenantB) { dsl ->
                val repo = VmwareConfigurationRepository(dsl)

                // Then - tenant B should not see tenant A's config
                val foundByTenant = repo.findByTenantId(tenantA)
                assertNull(foundByTenant, "Tenant B should not see Tenant A's config via findByTenantId")

                // Also verify tenant B doesn't see it as their own
                val foundOwn = repo.findByTenantId(tenantB)
                assertNull(foundOwn, "Tenant B should not have any config")
            }
        }

        @Test
        @DisplayName("should see own config")
        fun `tenant can see their own config`() = runTest {
            // Given - save config for tenant A
            val configA = createTestConfig(tenantA, vcenterUrl = "https://vcenter-a.example.com/sdk")
            withTenantDsl(tenantA) { dsl ->
                val repo = VmwareConfigurationRepository(dsl)
                repo.save(configA)
            }

            // When - tenant A queries for their config
            withTenantDsl(tenantA) { dsl ->
                val repo = VmwareConfigurationRepository(dsl)
                val found = repo.findByTenantId(tenantA)

                // Then
                assertNotNull(found)
                assertEquals("https://vcenter-a.example.com/sdk", found!!.vcenterUrl)
                assertEquals(tenantA, found.tenantId)
            }
        }
    }

    @Nested
    @DisplayName("existsByTenantId()")
    inner class ExistsByTenantIdTests {

        @Test
        @DisplayName("should return false for other tenant's config")
        fun `returns false for other tenant config`() = runTest {
            // Given - save config for tenant A
            val configA = createTestConfig(tenantA)
            withTenantDsl(tenantA) { dsl ->
                val repo = VmwareConfigurationRepository(dsl)
                repo.save(configA)
            }

            // When - tenant B checks existence
            withTenantDsl(tenantB) { dsl ->
                val repo = VmwareConfigurationRepository(dsl)
                val exists = repo.existsByTenantId(tenantA)

                // Then - RLS prevents seeing other tenant's data
                assertFalse(exists, "Tenant B should not see Tenant A's config exists")
            }
        }

        @Test
        @DisplayName("should return true for own config")
        fun `returns true for own config`() = runTest {
            // Given - save config for tenant A
            val configA = createTestConfig(tenantA)
            withTenantDsl(tenantA) { dsl ->
                val repo = VmwareConfigurationRepository(dsl)
                repo.save(configA)
            }

            // When - tenant A checks existence
            withTenantDsl(tenantA) { dsl ->
                val repo = VmwareConfigurationRepository(dsl)
                val exists = repo.existsByTenantId(tenantA)

                // Then
                assertTrue(exists)
            }
        }
    }

    @Nested
    @DisplayName("Multi-tenant isolation")
    inner class MultiTenantIsolationTests {

        @Test
        @DisplayName("should allow both tenants to have configs independently")
        fun `multiple tenants can have configs without conflict`() = runTest {
            // Given - both tenants save their configs
            val configA = createTestConfig(tenantA, vcenterUrl = "https://vcenter-a.example.com/sdk")
            val configB = createTestConfig(tenantB, vcenterUrl = "https://vcenter-b.example.com/sdk")

            withTenantDsl(tenantA) { dsl ->
                val repo = VmwareConfigurationRepository(dsl)
                val result = repo.save(configA)
                assertTrue(result is Result.Success, "Tenant A should save successfully")
            }

            withTenantDsl(tenantB) { dsl ->
                val repo = VmwareConfigurationRepository(dsl)
                val result = repo.save(configB)
                assertTrue(result is Result.Success, "Tenant B should save successfully")
            }

            // When/Then - each tenant sees only their own config
            withTenantDsl(tenantA) { dsl ->
                val repo = VmwareConfigurationRepository(dsl)
                val found = repo.findByTenantId(tenantA)
                assertNotNull(found)
                assertEquals("https://vcenter-a.example.com/sdk", found!!.vcenterUrl)
            }

            withTenantDsl(tenantB) { dsl ->
                val repo = VmwareConfigurationRepository(dsl)
                val found = repo.findByTenantId(tenantB)
                assertNotNull(found)
                assertEquals("https://vcenter-b.example.com/sdk", found!!.vcenterUrl)
            }
        }

        @Test
        @DisplayName("should prevent cross-tenant update")
        fun `tenant cannot update other tenant config`() = runTest {
            // Given - tenant A has a config
            val configA = createTestConfig(tenantA, vcenterUrl = "https://vcenter-a.example.com/sdk")
            withTenantDsl(tenantA) { dsl ->
                val repo = VmwareConfigurationRepository(dsl)
                repo.save(configA)
            }

            // When - tenant B tries to update tenant A's config
            withTenantDsl(tenantB) { dsl ->
                val repo = VmwareConfigurationRepository(dsl)

                // First, try to find the config (should fail due to RLS)
                val found = repo.findById(configA.id)
                assertNull(found, "Tenant B should not find Tenant A's config by ID")

                // Even if tenant B knew the ID, updating should fail (NotFound due to RLS)
                val tamperedConfig = configA.copy(
                    vcenterUrl = "https://hacked.example.com/sdk",
                    version = 2L
                )
                val result = repo.update(tamperedConfig)
                assertTrue(result is Result.Failure, "Update should fail for cross-tenant access")
            }

            // Verify tenant A's config is unchanged
            withTenantDsl(tenantA) { dsl ->
                val repo = VmwareConfigurationRepository(dsl)
                val found = repo.findByTenantId(tenantA)
                assertNotNull(found)
                assertEquals("https://vcenter-a.example.com/sdk", found!!.vcenterUrl)
            }
        }
    }

    @Nested
    @DisplayName("Fail-closed semantics")
    inner class FailClosedTests {

        @Test
        @DisplayName("should return zero rows without tenant context")
        fun `query without tenant context returns zero rows`() = runTest {
            // Given - save config using tenant A
            val configA = createTestConfig(tenantA)
            withTenantDsl(tenantA) { dsl ->
                val repo = VmwareConfigurationRepository(dsl)
                repo.save(configA)
            }

            // When - query directly without setting app.tenant_id (bypass RlsEnforcingDataSource)
            val directConnection = postgres.createConnection("")
            directConnection.use { conn ->
                conn.createStatement().execute("SET ROLE eaf_app")
                // Don't set app.tenant_id - this tests fail-closed behavior

                val dsl = DSL.using(conn, SQLDialect.POSTGRES)
                val directResult = dsl.fetch(
                    """SELECT * FROM public."VMWARE_CONFIGURATIONS" WHERE "TENANT_ID" = ?""",
                    tenantA.value
                )

                // Then - should return zero rows (fail-closed), not all rows
                assertEquals(
                    0,
                    directResult.size,
                    "Without tenant context, RLS should return zero rows (fail-closed)"
                )
            }
        }
    }
}
