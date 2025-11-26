package de.acci.dvmm.infrastructure.projection

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.eventsourcing.projection.PageRequest
import de.acci.eaf.testing.TenantTestContext
import de.acci.eaf.testing.awaitProjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Integration tests for VmRequestProjectionRepository.
 *
 * Tests cover:
 * - AC1: Generated jOOQ code integrates properly
 * - AC2: Type-safe pagination via BaseProjectionRepository
 * - AC5: RLS tenant isolation via connection customizer
 * - AC6: Full read path through sample projection
 */
@Testcontainers
@DisplayName("VmRequestProjectionRepository Integration Tests")
class VmRequestProjectionRepositoryIntegrationTest {

    companion object {
        // Testcontainers default credentials - not sensitive, used only for local testing
        private const val TC_DB_NAME = "dvmm_test"

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName(TC_DB_NAME)
            // Use Testcontainers default credentials (test/test) - superuser privileges
            // needed to bypass FORCE ROW LEVEL SECURITY for test data setup

        private lateinit var superuserDsl: DSLContext

        @BeforeAll
        @JvmStatic
        fun setupSchema() {
            val initSql = VmRequestProjectionRepositoryIntegrationTest::class.java
                .getResourceAsStream("/db/jooq-init.sql")
                ?.bufferedReader()
                ?.readText()
                ?: throw IllegalStateException("Could not read jooq-init.sql")

            postgres.createConnection("").use { conn ->
                conn.createStatement().execute(initSql)
            }

            // Superuser DSL bypasses RLS for test data setup/cleanup
            superuserDsl = DSL.using(postgres.jdbcUrl, postgres.username, postgres.password)
        }
    }

    private val tenantA = TenantId(UUID.randomUUID())
    private val tenantB = TenantId(UUID.randomUUID())
    private lateinit var repository: VmRequestProjectionRepository

    @BeforeEach
    fun setup() {
        TenantTestContext.set(tenantA)
    }

    @AfterEach
    fun cleanup() {
        TenantTestContext.clear()
        // Clean up test data using superuser connection (bypasses RLS)
        superuserDsl.execute("TRUNCATE TABLE vm_requests_projection")
    }

    /**
     * Creates a DSLContext with tenant context set for RLS.
     */
    private fun createTenantDsl(tenant: TenantId): DSLContext {
        val conn = postgres.createConnection("")
        // Switch to eaf_app role so RLS is enforced
        conn.createStatement().execute("SET ROLE eaf_app")
        // Set tenant context
        conn.prepareStatement("SELECT set_config('app.tenant_id', ?, false)").use { stmt ->
            stmt.setString(1, tenant.value.toString())
            stmt.execute()
        }
        return DSL.using(conn, SQLDialect.POSTGRES)
    }

    private fun insertTestProjection(
        id: UUID = UUID.randomUUID(),
        tenantId: TenantId,
        requesterId: UUID = UUID.randomUUID(),
        vmName: String = "test-vm",
        cpuCores: Int = 4,
        memoryGb: Int = 16,
        status: String = "PENDING",
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        updatedAt: OffsetDateTime = OffsetDateTime.now()
    ): UUID {
        // Use raw JDBC to properly handle OffsetDateTime -> TIMESTAMPTZ conversion
        postgres.createConnection("").use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO vm_requests_projection
                (id, tenant_id, requester_id, vm_name, cpu_cores, memory_gb, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, tenantId.value)
                stmt.setObject(3, requesterId)
                stmt.setString(4, vmName)
                stmt.setInt(5, cpuCores)
                stmt.setInt(6, memoryGb)
                stmt.setString(7, status)
                stmt.setObject(8, createdAt)
                stmt.setObject(9, updatedAt)
                stmt.executeUpdate()
            }
        }
        return id
    }

    @Nested
    @DisplayName("AC5: RLS Tenant Isolation")
    inner class RlsTenantIsolation {

        @Test
        fun `tenant A cannot see tenant B data`() = runBlocking {
            // Given: Data for both tenants
            val tenantAId = insertTestProjection(tenantId = tenantA, vmName = "tenant-a-vm")
            val tenantBId = insertTestProjection(tenantId = tenantB, vmName = "tenant-b-vm")

            // When: Tenant A queries
            val dslA = createTenantDsl(tenantA)
            val repoA = VmRequestProjectionRepository(dslA)
            val resultA = repoA.findById(tenantAId)
            val resultB = repoA.findById(tenantBId)

            // Then: Tenant A sees only their data
            assertNotNull(resultA, "Tenant A should see their own VM request")
            assertEquals("tenant-a-vm", resultA?.vmName)
            assertNull(resultB, "Tenant A should NOT see Tenant B's VM request")
        }

        @Test
        fun `findAll only returns current tenant data`() = runBlocking {
            // Given: Multiple records for different tenants
            insertTestProjection(tenantId = tenantA, vmName = "a-vm-1")
            insertTestProjection(tenantId = tenantA, vmName = "a-vm-2")
            insertTestProjection(tenantId = tenantB, vmName = "b-vm-1")

            // When: Tenant A queries all
            val dslA = createTenantDsl(tenantA)
            val repoA = VmRequestProjectionRepository(dslA)
            val result = repoA.findAll()

            // Then: Only tenant A data is returned
            assertEquals(2, result.totalElements, "Should have exactly 2 records for tenant A")
            assertTrue(result.items.all { it.tenantId == tenantA.value }, "All items should belong to tenant A")
        }

        @Test
        fun `count only counts current tenant data`() = runBlocking {
            // Given: Data for both tenants
            insertTestProjection(tenantId = tenantA)
            insertTestProjection(tenantId = tenantA)
            insertTestProjection(tenantId = tenantB)

            // When: Tenant A counts
            val dslA = createTenantDsl(tenantA)
            val repoA = VmRequestProjectionRepository(dslA)
            val count = repoA.count()

            // Then: Only tenant A records counted
            assertEquals(2, count, "Count should only include tenant A records")
        }
    }

    @Nested
    @DisplayName("AC2: Type-Safe Pagination")
    inner class TypeSafePagination {

        @Test
        fun `findAll returns paginated results`() = runBlocking {
            // Given: 5 records
            repeat(5) { i ->
                insertTestProjection(tenantId = tenantA, vmName = "vm-$i")
            }

            val dsl = createTenantDsl(tenantA)
            repository = VmRequestProjectionRepository(dsl)

            // When: Request first page of 2
            val page1 = repository.findAll(PageRequest(page = 0, size = 2))

            // Then: Pagination metadata is correct
            assertEquals(2, page1.items.size, "Should return 2 items")
            assertEquals(5, page1.totalElements, "Total should be 5")
            assertEquals(3, page1.totalPages, "Should have 3 pages")
            assertTrue(page1.hasNext, "Should have next page")
            assertFalse(page1.hasPrevious, "Should not have previous page")
        }

        @Test
        fun `pagination offset works correctly`() = runBlocking {
            // Given: 5 records
            repeat(5) { i ->
                insertTestProjection(tenantId = tenantA, vmName = "vm-$i")
            }

            val dsl = createTenantDsl(tenantA)
            repository = VmRequestProjectionRepository(dsl)

            // When: Request second page
            val page2 = repository.findAll(PageRequest(page = 1, size = 2))

            // Then: Correct offset applied
            assertEquals(2, page2.items.size, "Should return 2 items")
            assertEquals(1, page2.page, "Should be page 1")
            assertTrue(page2.hasNext, "Should have next page")
            assertTrue(page2.hasPrevious, "Should have previous page")
        }

        @Test
        fun `last page returns remaining items`() = runBlocking {
            // Given: 5 records
            repeat(5) { i ->
                insertTestProjection(tenantId = tenantA, vmName = "vm-$i")
            }

            val dsl = createTenantDsl(tenantA)
            repository = VmRequestProjectionRepository(dsl)

            // When: Request last page
            val lastPage = repository.findAll(PageRequest(page = 2, size = 2))

            // Then: Only remaining item returned
            assertEquals(1, lastPage.items.size, "Should return 1 remaining item")
            assertFalse(lastPage.hasNext, "Should not have next page")
            assertTrue(lastPage.hasPrevious, "Should have previous page")
        }
    }

    @Nested
    @DisplayName("AC6: Query Methods")
    inner class QueryMethods {

        @Test
        fun `findById returns projection when exists`() = runBlocking {
            // Given: A projection exists
            val id = insertTestProjection(
                tenantId = tenantA,
                vmName = "test-vm",
                cpuCores = 8,
                memoryGb = 32,
                status = "APPROVED"
            )

            val dsl = createTenantDsl(tenantA)
            repository = VmRequestProjectionRepository(dsl)

            // When: Find by ID
            val result = repository.findById(id)

            // Then: Projection is returned with correct data
            assertNotNull(result)
            assertEquals(id, result?.id)
            assertEquals("test-vm", result?.vmName)
            assertEquals(8, result?.cpuCores)
            assertEquals(32, result?.memoryGb)
            assertEquals("APPROVED", result?.status)
        }

        @Test
        fun `findById returns null when not exists`() = runBlocking {
            val dsl = createTenantDsl(tenantA)
            repository = VmRequestProjectionRepository(dsl)

            val result = repository.findById(UUID.randomUUID())

            assertNull(result)
        }

        @Test
        fun `findByStatus returns filtered paginated results`() = runBlocking {
            // Given: Projections with different statuses
            insertTestProjection(tenantId = tenantA, status = "PENDING")
            insertTestProjection(tenantId = tenantA, status = "PENDING")
            insertTestProjection(tenantId = tenantA, status = "APPROVED")

            val dsl = createTenantDsl(tenantA)
            repository = VmRequestProjectionRepository(dsl)

            // When: Find by status
            val result = repository.findByStatus("PENDING")

            // Then: Only matching status returned
            assertEquals(2, result.totalElements)
            assertTrue(result.items.all { it.status == "PENDING" })
        }

        @Test
        fun `findByRequesterId returns filtered paginated results`() = runBlocking {
            // Given: Projections for different requesters
            val requester1 = UUID.randomUUID()
            val requester2 = UUID.randomUUID()
            insertTestProjection(tenantId = tenantA, requesterId = requester1)
            insertTestProjection(tenantId = tenantA, requesterId = requester1)
            insertTestProjection(tenantId = tenantA, requesterId = requester2)

            val dsl = createTenantDsl(tenantA)
            repository = VmRequestProjectionRepository(dsl)

            // When: Find by requester
            val result = repository.findByRequesterId(requester1)

            // Then: Only matching requester returned
            assertEquals(2, result.totalElements)
            assertTrue(result.items.all { it.requesterId == requester1 })
        }

        @Test
        fun `exists returns true when data exists`() = runBlocking {
            insertTestProjection(tenantId = tenantA)

            val dsl = createTenantDsl(tenantA)
            repository = VmRequestProjectionRepository(dsl)

            assertTrue(repository.exists())
        }

        @Test
        fun `exists returns false when no data`() = runBlocking {
            val dsl = createTenantDsl(tenantA)
            repository = VmRequestProjectionRepository(dsl)

            assertFalse(repository.exists())
        }
    }

    @Nested
    @DisplayName("AC4: awaitProjection Helper")
    inner class AwaitProjectionHelper {

        @Test
        fun `awaitProjection returns projection once available`() = runBlocking {
            val dsl = createTenantDsl(tenantA)
            repository = VmRequestProjectionRepository(dsl)
            val id = UUID.randomUUID()

            // Insert data asynchronously (simulating eventual consistency)
            launch(Dispatchers.IO) {
                delay(100)
                insertTestProjection(id = id, tenantId = tenantA, vmName = "delayed-vm")
            }

            // When: Await projection - type is inferred from repository.findById() return type
            val result = awaitProjection(
                repository = { repository.findById(id) }
            )

            // Then: Projection is returned
            assertNotNull(result)
            assertEquals("delayed-vm", result.vmName)
        }
    }
}
