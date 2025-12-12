package de.acci.dvmm.infrastructure.projection

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.testing.TenantTestContext
import de.acci.eaf.testing.TestContainers
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.types.UserId
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Connection
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Integration tests for AdminRequestDetailRepositoryAdapter.
 *
 * Story 2.10: Request Detail View (Admin)
 *
 * Tests cover:
 * - findById with all admin fields (AC 2, AC 3)
 * - findRecentByRequesterId for requester history (AC 6)
 * - RLS tenant isolation
 * - Handling of null email/role (backward compatibility)
 */
@Testcontainers
@DisplayName("AdminRequestDetailRepositoryAdapter Integration Tests")
class AdminRequestDetailRepositoryAdapterIntegrationTest {

    companion object {
        private lateinit var superuserDsl: DSLContext

        @BeforeAll
        @JvmStatic
        fun setupSchema() {
            TestContainers.ensureFlywayMigrations()
            superuserDsl = DSL.using(
                TestContainers.postgres.jdbcUrl,
                TestContainers.postgres.username,
                TestContainers.postgres.password
            )
        }
    }

    private val tenantA = TenantId(UUID.randomUUID())
    private val tenantB = TenantId(UUID.randomUUID())
    private val testRequesterId = UserId(UUID.randomUUID())
    private lateinit var adapter: AdminRequestDetailRepositoryAdapter

    @BeforeEach
    fun setup() {
        TenantTestContext.set(tenantA)
    }

    @AfterEach
    fun cleanup() {
        TenantTestContext.clear()
        superuserDsl.execute("""TRUNCATE TABLE public."VM_REQUESTS_PROJECTION" CASCADE""")
    }

    private fun createTenantDsl(tenant: TenantId): Pair<Connection, DSLContext> {
        val conn = TestContainers.postgres.createConnection("")
        conn.createStatement().execute("SET ROLE eaf_app")
        conn.prepareStatement("SELECT set_config('app.tenant_id', ?, false)").use { stmt ->
            stmt.setString(1, tenant.value.toString())
            stmt.execute()
        }
        return conn to DSL.using(conn, SQLDialect.POSTGRES)
    }

    private suspend fun <T> withTenantDsl(tenant: TenantId, block: suspend (DSLContext) -> T): T {
        val (conn, dsl) = createTenantDsl(tenant)
        return conn.use { block(dsl) }
    }

    private fun insertTestRequest(
        id: UUID = UUID.randomUUID(),
        tenantId: TenantId,
        requesterId: UUID = testRequesterId.value,
        requesterName: String = "Test User",
        requesterEmail: String? = null,
        requesterRole: String? = null,
        vmName: String = "test-vm",
        status: String = "PENDING",
        createdAt: OffsetDateTime = OffsetDateTime.now()
    ): UUID {
        TestContainers.postgres.createConnection("").use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO public."VM_REQUESTS_PROJECTION"
                ("ID", "TENANT_ID", "REQUESTER_ID", "REQUESTER_NAME", "REQUESTER_EMAIL", "REQUESTER_ROLE",
                 "PROJECT_ID", "PROJECT_NAME", "VM_NAME", "SIZE", "CPU_CORES", "MEMORY_GB", "DISK_GB",
                 "JUSTIFICATION", "STATUS", "CREATED_AT", "UPDATED_AT", "VERSION")
                VALUES (?, ?, ?, ?, ?, ?,
                        ?, 'Test Project', ?, 'M', 4, 16, 100,
                        'Test justification', ?, ?, ?, 1)
                ON CONFLICT ("ID") DO NOTHING
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, tenantId.value)
                stmt.setObject(3, requesterId)
                stmt.setString(4, requesterName)
                stmt.setString(5, requesterEmail)
                stmt.setString(6, requesterRole)
                stmt.setObject(7, UUID.randomUUID())
                stmt.setString(8, vmName)
                stmt.setString(9, status)
                stmt.setObject(10, createdAt)
                stmt.setObject(11, createdAt)
                stmt.executeUpdate()
            }
        }
        return id
    }

    @Nested
    @DisplayName("findById")
    inner class FindById {

        @Test
        fun `returns request with all admin fields including email and role`() = runBlocking {
            // Given: AC 2 - Requester Information displayed (Name, Email, Role)
            val requestId = insertTestRequest(
                tenantId = tenantA,
                requesterName = "John Doe",
                requesterEmail = "john.doe@example.com",
                requesterRole = "developer",
                vmName = "web-server-01"
            )

            withTenantDsl(tenantA) { dsl ->
                adapter = AdminRequestDetailRepositoryAdapter(dsl)
                val result = adapter.findById(VmRequestId(requestId))

                // Then: All admin fields are present
                assertNotNull(result)
                assertEquals(requestId, result!!.id.value)
                assertEquals("John Doe", result.requesterName)
                assertEquals("john.doe@example.com", result.requesterEmail)
                assertEquals("developer", result.requesterRole)
                assertEquals("web-server-01", result.vmName)
            }
        }

        @Test
        fun `provides default values when email and role are null`() = runBlocking {
            // Given: Legacy record without email/role (backward compatibility)
            val requestId = insertTestRequest(
                tenantId = tenantA,
                requesterEmail = null,
                requesterRole = null
            )

            withTenantDsl(tenantA) { dsl ->
                adapter = AdminRequestDetailRepositoryAdapter(dsl)
                val result = adapter.findById(VmRequestId(requestId))

                // Then: Default values provided
                assertNotNull(result)
                assertEquals("Not available", result!!.requesterEmail)
                assertEquals("User", result.requesterRole)
            }
        }

        @Test
        fun `returns null for non-existent request`() = runBlocking {
            withTenantDsl(tenantA) { dsl ->
                adapter = AdminRequestDetailRepositoryAdapter(dsl)
                val result = adapter.findById(VmRequestId(UUID.randomUUID()))

                assertNull(result)
            }
        }

        @Test
        fun `respects RLS - cannot see other tenant requests`() = runBlocking {
            // Given: Request belongs to tenant B
            val requestId = insertTestRequest(tenantId = tenantB)

            // When: Tenant A tries to access
            withTenantDsl(tenantA) { dsl ->
                adapter = AdminRequestDetailRepositoryAdapter(dsl)
                val result = adapter.findById(VmRequestId(requestId))

                // Then: Not visible due to RLS
                assertNull(result, "Tenant A should not see Tenant B's request")
            }

            // Tenant B can see it
            withTenantDsl(tenantB) { dsl ->
                adapter = AdminRequestDetailRepositoryAdapter(dsl)
                val result = adapter.findById(VmRequestId(requestId))

                assertNotNull(result, "Tenant B should see their own request")
            }
        }
    }

    @Nested
    @DisplayName("findRecentByRequesterId")
    inner class FindRecentByRequesterId {

        @Test
        fun `returns recent requests by same requester excluding current`() = runBlocking {
            // Given: AC 6 - Requester History shown
            val currentRequestId = UUID.randomUUID()
            val now = OffsetDateTime.now()

            // Insert current request
            insertTestRequest(
                id = currentRequestId,
                tenantId = tenantA,
                vmName = "current-vm",
                createdAt = now
            )

            // Insert historical requests
            insertTestRequest(
                tenantId = tenantA,
                vmName = "history-vm-1",
                status = "APPROVED",
                createdAt = now.minusDays(1)
            )
            insertTestRequest(
                tenantId = tenantA,
                vmName = "history-vm-2",
                status = "REJECTED",
                createdAt = now.minusDays(2)
            )

            withTenantDsl(tenantA) { dsl ->
                adapter = AdminRequestDetailRepositoryAdapter(dsl)
                val history = adapter.findRecentByRequesterId(
                    requesterId = testRequesterId,
                    excludeRequestId = VmRequestId(currentRequestId),
                    limit = 5
                )

                // Then: Returns history sorted by date descending
                assertEquals(2, history.size)
                assertEquals("history-vm-1", history[0].vmName)
                assertEquals("history-vm-2", history[1].vmName)
                assertEquals("APPROVED", history[0].status)
                assertEquals("REJECTED", history[1].status)
            }
        }

        @Test
        fun `limits results to specified limit`() = runBlocking {
            // Given: AC 6 - Up to 5 recent requests
            val now = OffsetDateTime.now()
            val currentRequestId = insertTestRequest(tenantId = tenantA, createdAt = now)

            // Insert 7 historical requests
            repeat(7) { i ->
                insertTestRequest(
                    tenantId = tenantA,
                    vmName = "vm-$i",
                    createdAt = now.minusDays(i.toLong() + 1)
                )
            }

            withTenantDsl(tenantA) { dsl ->
                adapter = AdminRequestDetailRepositoryAdapter(dsl)
                val history = adapter.findRecentByRequesterId(
                    requesterId = testRequesterId,
                    excludeRequestId = VmRequestId(currentRequestId),
                    limit = 5
                )

                // Then: Only 5 most recent returned
                assertEquals(5, history.size)
            }
        }

        @Test
        fun `returns empty list when no other requests exist`() = runBlocking {
            // Given: Only one request from this requester
            val currentRequestId = insertTestRequest(tenantId = tenantA)

            withTenantDsl(tenantA) { dsl ->
                adapter = AdminRequestDetailRepositoryAdapter(dsl)
                val history = adapter.findRecentByRequesterId(
                    requesterId = testRequesterId,
                    excludeRequestId = VmRequestId(currentRequestId),
                    limit = 5
                )

                assertTrue(history.isEmpty())
            }
        }

        @Test
        fun `excludes requests from other requesters`() = runBlocking {
            // Given: Requests from different requesters
            val currentRequestId = insertTestRequest(tenantId = tenantA)
            val otherRequesterId = UUID.randomUUID()
            insertTestRequest(
                tenantId = tenantA,
                requesterId = otherRequesterId,
                vmName = "other-user-vm"
            )

            withTenantDsl(tenantA) { dsl ->
                adapter = AdminRequestDetailRepositoryAdapter(dsl)
                val history = adapter.findRecentByRequesterId(
                    requesterId = testRequesterId,
                    excludeRequestId = VmRequestId(currentRequestId),
                    limit = 5
                )

                // Then: Only includes requests from same requester
                assertTrue(history.isEmpty(), "Should not include other requester's requests")
            }
        }

        @Test
        fun `respects RLS for requester history`() = runBlocking {
            // Given: Same requester ID but different tenant
            val currentRequestId = insertTestRequest(tenantId = tenantA)
            insertTestRequest(
                tenantId = tenantB,
                vmName = "other-tenant-vm"
            )

            withTenantDsl(tenantA) { dsl ->
                adapter = AdminRequestDetailRepositoryAdapter(dsl)
                val history = adapter.findRecentByRequesterId(
                    requesterId = testRequesterId,
                    excludeRequestId = VmRequestId(currentRequestId),
                    limit = 5
                )

                // Then: Only tenant A's requests visible
                assertTrue(history.isEmpty(), "Should not include other tenant's requests")
            }
        }
    }
}
