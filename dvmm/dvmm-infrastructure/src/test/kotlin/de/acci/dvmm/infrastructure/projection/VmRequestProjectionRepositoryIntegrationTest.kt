package de.acci.dvmm.infrastructure.projection

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.eventsourcing.projection.PageRequest
import de.acci.eaf.testing.TenantTestContext
import de.acci.eaf.testing.TestContainers
import de.acci.eaf.testing.awaitProjection
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
        // Default test values for VM request projections
        private const val DEFAULT_CPU_CORES = 4
        private const val DEFAULT_MEMORY_GB = 16

        private lateinit var superuserDsl: DSLContext

        @BeforeAll
        @JvmStatic
        fun setupSchema() {
            // Use Flyway migrations from classpath - same as production
            // This eliminates the need to maintain a separate jooq-init.sql file
            TestContainers.ensureFlywayMigrations()

            // Superuser DSL bypasses RLS for test data setup/cleanup
            superuserDsl = DSL.using(
                TestContainers.postgres.jdbcUrl,
                TestContainers.postgres.username,
                TestContainers.postgres.password
            )
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
        // Use CASCADE due to FK constraint from REQUEST_TIMELINE_EVENTS
        superuserDsl.execute("""TRUNCATE TABLE public."VM_REQUESTS_PROJECTION" CASCADE""")
    }

    /**
     * Creates a DSLContext with tenant context set for RLS.
     * IMPORTANT: The caller is responsible for closing the underlying connection.
     * Consider using withTenantDsl() for automatic resource management.
     */
    private fun createTenantDsl(tenant: TenantId): Pair<Connection, DSLContext> {
        val conn = TestContainers.postgres.createConnection("")
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
     * Executes a suspend block with a tenant-scoped DSLContext, ensuring proper connection cleanup.
     */
    private suspend fun <T> withTenantDsl(tenant: TenantId, block: suspend (DSLContext) -> T): T {
        val (conn, dsl) = createTenantDsl(tenant)
        return conn.use { block(dsl) }
    }

    private fun insertTestProjection(
        id: UUID = UUID.randomUUID(),
        tenantId: TenantId,
        requesterId: UUID = UUID.randomUUID(),
        requesterName: String = "Test User",
        projectId: UUID = UUID.randomUUID(),
        projectName: String = "Test Project",
        vmName: String = "test-vm",
        size: String = "M",
        cpuCores: Int = DEFAULT_CPU_CORES,
        memoryGb: Int = DEFAULT_MEMORY_GB,
        diskGb: Int = 100,
        justification: String = "Test justification for VM request",
        status: String = "PENDING",
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        updatedAt: OffsetDateTime = OffsetDateTime.now()
    ): UUID {
        // Use raw JDBC to properly handle OffsetDateTime -> TIMESTAMPTZ conversion
        // Column names must be quoted uppercase to match jOOQ-generated schema
        TestContainers.postgres.createConnection("").use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO public."VM_REQUESTS_PROJECTION"
                ("ID", "TENANT_ID", "REQUESTER_ID", "REQUESTER_NAME", "PROJECT_ID", "PROJECT_NAME",
                 "VM_NAME", "SIZE", "CPU_CORES", "MEMORY_GB", "DISK_GB", "JUSTIFICATION",
                 "STATUS", "CREATED_AT", "UPDATED_AT", "VERSION")
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, tenantId.value)
                stmt.setObject(3, requesterId)
                stmt.setString(4, requesterName)
                stmt.setObject(5, projectId)
                stmt.setString(6, projectName)
                stmt.setString(7, vmName)
                stmt.setString(8, size)
                stmt.setInt(9, cpuCores)
                stmt.setInt(10, memoryGb)
                stmt.setInt(11, diskGb)
                stmt.setString(12, justification)
                stmt.setString(13, status)
                stmt.setObject(14, createdAt)
                stmt.setObject(15, updatedAt)
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
            withTenantDsl(tenantA) { dslA ->
                val repoA = VmRequestProjectionRepository(dslA)
                val resultA = repoA.findById(tenantAId)
                val resultB = repoA.findById(tenantBId)

                // Then: Tenant A sees only their data
                assertNotNull(resultA, "Tenant A should see their own VM request")
                assertEquals("tenant-a-vm", resultA?.vmName)
                assertNull(resultB, "Tenant A should NOT see Tenant B's VM request")
            }
        }

        @Test
        fun `findAll only returns current tenant data`() = runBlocking {
            // Given: Multiple records for different tenants
            insertTestProjection(tenantId = tenantA, vmName = "a-vm-1")
            insertTestProjection(tenantId = tenantA, vmName = "a-vm-2")
            insertTestProjection(tenantId = tenantB, vmName = "b-vm-1")

            // When: Tenant A queries all
            withTenantDsl(tenantA) { dslA ->
                val repoA = VmRequestProjectionRepository(dslA)
                val result = repoA.findAll()

                // Then: Only tenant A data is returned
                assertEquals(2, result.totalElements, "Should have exactly 2 records for tenant A")
                assertTrue(result.items.all { it.tenantId == tenantA.value }, "All items should belong to tenant A")
            }
        }

        @Test
        fun `count only counts current tenant data`() = runBlocking {
            // Given: Data for both tenants
            insertTestProjection(tenantId = tenantA)
            insertTestProjection(tenantId = tenantA)
            insertTestProjection(tenantId = tenantB)

            // When: Tenant A counts
            withTenantDsl(tenantA) { dslA ->
                val repoA = VmRequestProjectionRepository(dslA)
                val count = repoA.count()

                // Then: Only tenant A records counted
                assertEquals(2, count, "Count should only include tenant A records")
            }
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

            withTenantDsl(tenantA) { dsl ->
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
        }

        @Test
        fun `pagination offset works correctly`() = runBlocking {
            // Given: 5 records
            repeat(5) { i ->
                insertTestProjection(tenantId = tenantA, vmName = "vm-$i")
            }

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Request second page
                val page2 = repository.findAll(PageRequest(page = 1, size = 2))

                // Then: Correct offset applied
                assertEquals(2, page2.items.size, "Should return 2 items")
                assertEquals(1, page2.page, "Should be page 1")
                assertTrue(page2.hasNext, "Should have next page")
                assertTrue(page2.hasPrevious, "Should have previous page")
            }
        }

        @Test
        fun `last page returns remaining items`() = runBlocking {
            // Given: 5 records
            repeat(5) { i ->
                insertTestProjection(tenantId = tenantA, vmName = "vm-$i")
            }

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Request last page
                val lastPage = repository.findAll(PageRequest(page = 2, size = 2))

                // Then: Only remaining item returned
                assertEquals(1, lastPage.items.size, "Should return 1 remaining item")
                assertFalse(lastPage.hasNext, "Should not have next page")
                assertTrue(lastPage.hasPrevious, "Should have previous page")
            }
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

            withTenantDsl(tenantA) { dsl ->
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
        }

        @Test
        fun `findById returns null when not exists`() = runBlocking {
            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                val result = repository.findById(UUID.randomUUID())

                assertNull(result)
            }
        }

        @Test
        fun `findByStatus returns filtered paginated results`() = runBlocking {
            // Given: Projections with different statuses
            insertTestProjection(tenantId = tenantA, status = "PENDING")
            insertTestProjection(tenantId = tenantA, status = "PENDING")
            insertTestProjection(tenantId = tenantA, status = "APPROVED")

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Find by status
                val result = repository.findByStatus("PENDING")

                // Then: Only matching status returned
                assertEquals(2, result.totalElements)
                assertTrue(result.items.all { it.status == "PENDING" })
            }
        }

        @Test
        fun `findByRequesterId returns filtered paginated results`() = runBlocking {
            // Given: Projections for different requesters
            val requester1 = UUID.randomUUID()
            val requester2 = UUID.randomUUID()
            insertTestProjection(tenantId = tenantA, requesterId = requester1)
            insertTestProjection(tenantId = tenantA, requesterId = requester1)
            insertTestProjection(tenantId = tenantA, requesterId = requester2)

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Find by requester
                val result = repository.findByRequesterId(requester1)

                // Then: Only matching requester returned
                assertEquals(2, result.totalElements)
                assertTrue(result.items.all { it.requesterId == requester1 })
            }
        }

        @Test
        fun `exists returns true when data exists`() = runBlocking {
            insertTestProjection(tenantId = tenantA)

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                assertTrue(repository.exists())
            }
        }

        @Test
        fun `exists returns false when no data`() = runBlocking {
            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                assertFalse(repository.exists())
            }
        }
    }

    @Nested
    @DisplayName("AC4: awaitProjection Helper")
    inner class AwaitProjectionHelper {

        @Test
        fun `awaitProjection returns projection once available`() = runBlocking {
            val id = UUID.randomUUID()

            // Insert data asynchronously (simulating eventual consistency)
            launch(Dispatchers.IO) {
                delay(100)
                insertTestProjection(id = id, tenantId = tenantA, vmName = "delayed-vm")
            }

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

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

    @Nested
    @DisplayName("Story 2.9: findDistinctProjects for Project Filter")
    inner class FindDistinctProjects {

        @Test
        fun `returns distinct projects from VM requests`() = runBlocking {
            // Given: Requests for different projects
            val projectA = UUID.randomUUID()
            val projectB = UUID.randomUUID()
            insertTestProjection(tenantId = tenantA, projectId = projectA, projectName = "Project Alpha", status = "PENDING")
            insertTestProjection(tenantId = tenantA, projectId = projectA, projectName = "Project Alpha", status = "PENDING") // duplicate
            insertTestProjection(tenantId = tenantA, projectId = projectB, projectName = "Project Beta", status = "PENDING")

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Find distinct projects
                val result = repository.findDistinctProjects()

                // Then: Only distinct projects returned
                assertEquals(2, result.size, "Should return 2 distinct projects")
                assertTrue(result.any { it.projectId == projectA && it.projectName == "Project Alpha" })
                assertTrue(result.any { it.projectId == projectB && it.projectName == "Project Beta" })
            }
        }

        @Test
        fun `returns empty list when no projects`() = runBlocking {
            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Find distinct projects (no data)
                val result = repository.findDistinctProjects()

                // Then: Empty list
                assertTrue(result.isEmpty())
            }
        }

        @Test
        fun `includes projects from all statuses`() = runBlocking {
            // Given: Requests with different statuses
            val projectPending = UUID.randomUUID()
            val projectApproved = UUID.randomUUID()
            val projectRejected = UUID.randomUUID()

            insertTestProjection(tenantId = tenantA, projectId = projectPending, projectName = "Pending Project", status = "PENDING")
            insertTestProjection(tenantId = tenantA, projectId = projectApproved, projectName = "Approved Project", status = "APPROVED")
            insertTestProjection(tenantId = tenantA, projectId = projectRejected, projectName = "Rejected Project", status = "REJECTED")

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Find distinct projects
                val result = repository.findDistinctProjects()

                // Then: All projects returned regardless of status
                assertEquals(3, result.size, "Should include projects from all statuses")
            }
        }

        @Test
        fun `respects RLS tenant isolation`() = runBlocking {
            // Given: Projects in different tenants
            val tenantAProject = UUID.randomUUID()
            val tenantBProject = UUID.randomUUID()
            insertTestProjection(tenantId = tenantA, projectId = tenantAProject, projectName = "Tenant A Project", status = "PENDING")
            insertTestProjection(tenantId = tenantB, projectId = tenantBProject, projectName = "Tenant B Project", status = "PENDING")

            // When: Tenant A queries
            withTenantDsl(tenantA) { dslA ->
                val repoA = VmRequestProjectionRepository(dslA)
                val result = repoA.findDistinctProjects()

                // Then: Only tenant A projects
                assertEquals(1, result.size, "Should only see tenant A's projects")
                assertEquals(tenantAProject, result[0].projectId)
            }

            // And: Tenant B queries
            withTenantDsl(tenantB) { dslB ->
                val repoB = VmRequestProjectionRepository(dslB)
                val result = repoB.findDistinctProjects()

                // Then: Only tenant B projects
                assertEquals(1, result.size, "Should only see tenant B's projects")
                assertEquals(tenantBProject, result[0].projectId)
            }
        }

        @Test
        fun `returns projects sorted by name`() = runBlocking {
            // Given: Multiple projects
            insertTestProjection(tenantId = tenantA, projectId = UUID.randomUUID(), projectName = "Zebra Project", status = "PENDING")
            insertTestProjection(tenantId = tenantA, projectId = UUID.randomUUID(), projectName = "Alpha Project", status = "PENDING")
            insertTestProjection(tenantId = tenantA, projectId = UUID.randomUUID(), projectName = "Mike Project", status = "PENDING")

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Find distinct projects
                val result = repository.findDistinctProjects()

                // Then: Sorted alphabetically by name
                assertEquals("Alpha Project", result[0].projectName)
                assertEquals("Mike Project", result[1].projectName)
                assertEquals("Zebra Project", result[2].projectName)
            }
        }
    }

    @Nested
    @DisplayName("Story 2.9: findPendingByTenantId for Admin Queue")
    inner class FindPendingByTenantId {

        @Test
        fun `returns only PENDING status requests`() = runBlocking {
            // Given: Requests with different statuses
            insertTestProjection(tenantId = tenantA, status = "PENDING", vmName = "pending-1")
            insertTestProjection(tenantId = tenantA, status = "PENDING", vmName = "pending-2")
            insertTestProjection(tenantId = tenantA, status = "APPROVED", vmName = "approved-1")
            insertTestProjection(tenantId = tenantA, status = "REJECTED", vmName = "rejected-1")

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Find pending requests
                val result = repository.findPendingByTenantId()

                // Then: Only PENDING requests returned
                assertEquals(2, result.totalElements, "Should return only PENDING requests")
                assertTrue(result.items.all { it.status == "PENDING" }, "All items should be PENDING")
            }
        }

        @Test
        fun `returns empty response when no pending requests`() = runBlocking {
            // Given: Only non-pending requests
            insertTestProjection(tenantId = tenantA, status = "APPROVED")
            insertTestProjection(tenantId = tenantA, status = "REJECTED")

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Find pending requests
                val result = repository.findPendingByTenantId()

                // Then: Empty response
                assertEquals(0, result.totalElements)
                assertTrue(result.items.isEmpty())
            }
        }

        @Test
        fun `filters by projectId when provided`() = runBlocking {
            // Given: Pending requests for different projects
            val projectA = UUID.randomUUID()
            val projectB = UUID.randomUUID()
            insertTestProjection(tenantId = tenantA, projectId = projectA, status = "PENDING", vmName = "proj-a-1")
            insertTestProjection(tenantId = tenantA, projectId = projectA, status = "PENDING", vmName = "proj-a-2")
            insertTestProjection(tenantId = tenantA, projectId = projectB, status = "PENDING", vmName = "proj-b-1")

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Find pending requests for project A
                val result = repository.findPendingByTenantId(projectId = projectA)

                // Then: Only project A requests returned
                assertEquals(2, result.totalElements, "Should return only project A requests")
                assertTrue(result.items.all { it.projectId == projectA }, "All items should belong to project A")
            }
        }

        @Test
        fun `sorts by createdAt ascending - oldest first per AC3`() = runBlocking {
            // Given: Pending requests with different creation times
            val now = OffsetDateTime.now()
            val oldest = now.minusHours(3)
            val middle = now.minusHours(2)
            val newest = now.minusHours(1)

            insertTestProjection(tenantId = tenantA, status = "PENDING", vmName = "newest", createdAt = newest)
            insertTestProjection(tenantId = tenantA, status = "PENDING", vmName = "oldest", createdAt = oldest)
            insertTestProjection(tenantId = tenantA, status = "PENDING", vmName = "middle", createdAt = middle)

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Find pending requests
                val result = repository.findPendingByTenantId()

                // Then: Ordered oldest first (AC3: "sorted oldest→newest by submission time")
                assertEquals(3, result.items.size)
                assertEquals("oldest", result.items[0].vmName, "First item should be oldest")
                assertEquals("middle", result.items[1].vmName, "Second item should be middle")
                assertEquals("newest", result.items[2].vmName, "Third item should be newest")
            }
        }

        @Test
        fun `handles pagination correctly`() = runBlocking {
            // Given: 5 pending requests
            repeat(5) { i ->
                insertTestProjection(
                    tenantId = tenantA,
                    status = "PENDING",
                    vmName = "vm-$i",
                    createdAt = OffsetDateTime.now().minusHours(5L - i)
                )
            }

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Request first page of 2
                val page1 = repository.findPendingByTenantId(pageRequest = PageRequest(page = 0, size = 2))

                // Then: Pagination metadata is correct
                assertEquals(2, page1.items.size, "Should return 2 items")
                assertEquals(5, page1.totalElements, "Total should be 5")
                assertEquals(3, page1.totalPages, "Should have 3 pages")
                assertTrue(page1.hasNext, "Should have next page")
                assertFalse(page1.hasPrevious, "Should not have previous page")

                // When: Request second page
                val page2 = repository.findPendingByTenantId(pageRequest = PageRequest(page = 1, size = 2))

                // Then: Different items returned
                assertEquals(2, page2.items.size)
                assertTrue(page2.hasNext)
                assertTrue(page2.hasPrevious)
            }
        }

        @Test
        fun `respects RLS tenant isolation`() = runBlocking {
            // Given: Pending requests for both tenants
            insertTestProjection(tenantId = tenantA, status = "PENDING", vmName = "tenant-a-vm")
            insertTestProjection(tenantId = tenantB, status = "PENDING", vmName = "tenant-b-vm")

            // When: Tenant A queries pending requests
            withTenantDsl(tenantA) { dslA ->
                val repoA = VmRequestProjectionRepository(dslA)
                val result = repoA.findPendingByTenantId()

                // Then: Only tenant A's pending requests returned
                assertEquals(1, result.totalElements, "Should only see tenant A's requests")
                assertEquals("tenant-a-vm", result.items[0].vmName)
            }

            // And: Tenant B only sees their own
            withTenantDsl(tenantB) { dslB ->
                val repoB = VmRequestProjectionRepository(dslB)
                val result = repoB.findPendingByTenantId()

                assertEquals(1, result.totalElements, "Should only see tenant B's requests")
                assertEquals("tenant-b-vm", result.items[0].vmName)
            }
        }

        @Test
        fun `combines projectId filter with pagination`() = runBlocking {
            // Given: Many pending requests for one project
            val targetProject = UUID.randomUUID()
            val otherProject = UUID.randomUUID()

            repeat(5) { i ->
                insertTestProjection(
                    tenantId = tenantA,
                    projectId = targetProject,
                    status = "PENDING",
                    vmName = "target-$i",
                    createdAt = OffsetDateTime.now().minusHours(5L - i)
                )
            }
            insertTestProjection(tenantId = tenantA, projectId = otherProject, status = "PENDING", vmName = "other")

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Query with both filter and pagination
                val result = repository.findPendingByTenantId(
                    projectId = targetProject,
                    pageRequest = PageRequest(page = 0, size = 2)
                )

                // Then: Filter applied before pagination
                assertEquals(2, result.items.size, "Should return page size items")
                assertEquals(5, result.totalElements, "Total should be filtered count (5, not 6)")
                assertTrue(result.items.all { it.projectId == targetProject })
            }
        }
    }

    @Nested
    @DisplayName("Write Operations")
    inner class WriteOperations {

        @Test
        fun `insert persists new projection correctly`() = runBlocking {
            val id = UUID.randomUUID()
            val requesterId = UUID.randomUUID()
            val projectId = UUID.randomUUID()
            val now = OffsetDateTime.now()

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // Given: A new projection to insert
                val projection = de.acci.dvmm.infrastructure.jooq.`public`.tables.pojos.VmRequestsProjection(
                    id = id,
                    tenantId = tenantA.value,
                    requesterId = requesterId,
                    requesterName = "John Doe",
                    projectId = projectId,
                    projectName = "Project Alpha",
                    vmName = "web-server-01",
                    size = "L",
                    cpuCores = 8,
                    memoryGb = 32,
                    diskGb = 200,
                    justification = "Production web server for new application",
                    status = "PENDING",
                    approvedBy = null,
                    approvedByName = null,
                    rejectedBy = null,
                    rejectedByName = null,
                    rejectionReason = null,
                    createdAt = now,
                    updatedAt = now,
                    version = 1
                )

                // When: Insert the projection
                repository.insert(projection)

                // Then: Projection can be retrieved
                val result = repository.findById(id)
                assertNotNull(result)
                assertEquals(id, result?.id)
                assertEquals(tenantA.value, result?.tenantId)
                assertEquals(requesterId, result?.requesterId)
                assertEquals("John Doe", result?.requesterName)
                assertEquals(projectId, result?.projectId)
                assertEquals("Project Alpha", result?.projectName)
                assertEquals("web-server-01", result?.vmName)
                assertEquals("L", result?.size)
                assertEquals(8, result?.cpuCores)
                assertEquals(32, result?.memoryGb)
                assertEquals(200, result?.diskGb)
                assertEquals("Production web server for new application", result?.justification)
                assertEquals("PENDING", result?.status)
                assertNull(result?.approvedBy)
                assertNull(result?.rejectedBy)
            }
        }

        @Test
        fun `updateStatus updates to APPROVED with approver details`() = runBlocking {
            // Given: An existing pending projection
            val id = insertTestProjection(
                tenantId = tenantA,
                vmName = "pending-vm",
                status = "PENDING"
            )
            val approverId = UUID.randomUUID()

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Update to APPROVED
                val rowsUpdated = repository.updateStatus(
                    id = id,
                    status = "APPROVED",
                    approvedBy = approverId,
                    approvedByName = "Jane Admin",
                    version = 2
                )

                // Then: Update succeeded
                assertEquals(1, rowsUpdated)

                // And: Data is correctly updated
                val result = repository.findById(id)
                assertNotNull(result)
                assertEquals("APPROVED", result?.status)
                assertEquals(approverId, result?.approvedBy)
                assertEquals("Jane Admin", result?.approvedByName)
                assertNull(result?.rejectedBy)
                assertNull(result?.rejectionReason)
                assertEquals(2, result?.version)
            }
        }

        @Test
        fun `updateStatus updates to REJECTED with rejector details and reason`() = runBlocking {
            // Given: An existing pending projection
            val id = insertTestProjection(
                tenantId = tenantA,
                vmName = "pending-vm",
                status = "PENDING"
            )
            val rejectorId = UUID.randomUUID()

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Update to REJECTED
                val rowsUpdated = repository.updateStatus(
                    id = id,
                    status = "REJECTED",
                    rejectedBy = rejectorId,
                    rejectedByName = "Bob Reviewer",
                    rejectionReason = "Insufficient resource justification provided",
                    version = 2
                )

                // Then: Update succeeded
                assertEquals(1, rowsUpdated)

                // And: Data is correctly updated
                val result = repository.findById(id)
                assertNotNull(result)
                assertEquals("REJECTED", result?.status)
                assertEquals(rejectorId, result?.rejectedBy)
                assertEquals("Bob Reviewer", result?.rejectedByName)
                assertEquals("Insufficient resource justification provided", result?.rejectionReason)
                assertNull(result?.approvedBy)
                assertEquals(2, result?.version)
            }
        }

        @Test
        fun `updateStatus returns zero when projection does not exist`() = runBlocking {
            val nonExistentId = UUID.randomUUID()

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Try to update non-existent projection
                val rowsUpdated = repository.updateStatus(
                    id = nonExistentId,
                    status = "APPROVED",
                    version = 2
                )

                // Then: No rows updated
                assertEquals(0, rowsUpdated)
            }
        }

        @Test
        fun `updateStatus respects RLS - cannot update other tenant projection`() = runBlocking {
            // Given: A projection for tenant B
            val id = insertTestProjection(
                tenantId = tenantB,
                vmName = "tenant-b-vm",
                status = "PENDING"
            )

            // When: Tenant A tries to update it
            withTenantDsl(tenantA) { dslA ->
                val repoA = VmRequestProjectionRepository(dslA)

                val rowsUpdated = repoA.updateStatus(
                    id = id,
                    status = "APPROVED",
                    approvedBy = UUID.randomUUID(),
                    approvedByName = "Malicious Admin",
                    version = 2
                )

                // Then: RLS prevents the update
                assertEquals(0, rowsUpdated, "Tenant A should not be able to update Tenant B's projection")
            }

            // And: Verify the projection is unchanged using tenant B context
            withTenantDsl(tenantB) { dslB ->
                val repoB = VmRequestProjectionRepository(dslB)
                val result = repoB.findById(id)
                assertNotNull(result)
                assertEquals("PENDING", result?.status, "Status should remain PENDING")
                assertNull(result?.approvedBy, "ApprovedBy should remain null")
            }
        }

        @Test
        fun `insert respects RLS - inserted projection belongs to current tenant`() = runBlocking {
            val id = UUID.randomUUID()
            val now = OffsetDateTime.now()

            // Insert via tenant A
            withTenantDsl(tenantA) { dslA ->
                val repoA = VmRequestProjectionRepository(dslA)

                val projection = de.acci.dvmm.infrastructure.jooq.`public`.tables.pojos.VmRequestsProjection(
                    id = id,
                    tenantId = tenantA.value,
                    requesterId = UUID.randomUUID(),
                    requesterName = "Tenant A User",
                    projectId = UUID.randomUUID(),
                    projectName = "Tenant A Project",
                    vmName = "tenant-a-vm",
                    size = "M",
                    cpuCores = 4,
                    memoryGb = 16,
                    diskGb = 100,
                    justification = "Tenant A justification",
                    status = "PENDING",
                    approvedBy = null,
                    approvedByName = null,
                    rejectedBy = null,
                    rejectedByName = null,
                    rejectionReason = null,
                    createdAt = now,
                    updatedAt = now,
                    version = 1
                )

                repoA.insert(projection)
            }

            // Then: Tenant A can see it
            withTenantDsl(tenantA) { dslA ->
                val repoA = VmRequestProjectionRepository(dslA)
                val result = repoA.findById(id)
                assertNotNull(result, "Tenant A should see their own inserted projection")
            }

            // And: Tenant B cannot see it
            withTenantDsl(tenantB) { dslB ->
                val repoB = VmRequestProjectionRepository(dslB)
                val result = repoB.findById(id)
                assertNull(result, "Tenant B should NOT see Tenant A's projection")
            }
        }

        @Test
        fun `updateVmDetails persists VM runtime information`() = runBlocking {
            // Given: An existing projection
            val id = insertTestProjection(
                tenantId = tenantA,
                vmName = "test-vm",
                status = "PROVISIONED"
            )
            val syncTime = OffsetDateTime.now()

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Update VM details
                val rowsUpdated = repository.updateVmDetails(
                    id = id,
                    vmwareVmId = "vm-123",
                    ipAddress = "192.168.1.100",
                    hostname = "test-vm.local",
                    powerState = "POWERED_ON",
                    guestOs = "Ubuntu 22.04 LTS",
                    lastSyncedAt = syncTime
                )

                // Then: Update succeeded
                assertEquals(1, rowsUpdated)

                // And: Data is correctly updated
                val result = repository.findById(id)
                assertNotNull(result)
                assertEquals("vm-123", result?.vmwareVmId)
                assertEquals("192.168.1.100", result?.ipAddress)
                assertEquals("test-vm.local", result?.hostname)
                assertEquals("POWERED_ON", result?.powerState)
                assertEquals("Ubuntu 22.04 LTS", result?.guestOs)
                assertNotNull(result?.lastSyncedAt)
            }
        }

        @Test
        fun `updateVmDetails handles null values for partial sync`() = runBlocking {
            // Given: An existing projection
            val id = insertTestProjection(
                tenantId = tenantA,
                vmName = "test-vm",
                status = "PROVISIONED"
            )
            val syncTime = OffsetDateTime.now()

            withTenantDsl(tenantA) { dsl ->
                repository = VmRequestProjectionRepository(dsl)

                // When: Update with partial info (IP not yet assigned)
                val rowsUpdated = repository.updateVmDetails(
                    id = id,
                    vmwareVmId = "vm-456",
                    ipAddress = null, // No IP yet
                    hostname = null, // No hostname yet
                    powerState = "POWERED_ON",
                    guestOs = "Windows Server 2022",
                    lastSyncedAt = syncTime
                )

                // Then: Update succeeded
                assertEquals(1, rowsUpdated)

                // And: Nullable fields are correctly null
                val result = repository.findById(id)
                assertNotNull(result)
                assertEquals("vm-456", result?.vmwareVmId)
                assertNull(result?.ipAddress)
                assertNull(result?.hostname)
                assertEquals("POWERED_ON", result?.powerState)
                assertEquals("Windows Server 2022", result?.guestOs)
            }
        }

        @Test
        fun `updateVmDetails respects RLS - cannot update other tenant projection`() = runBlocking {
            // Given: A projection for tenant B
            val id = insertTestProjection(
                tenantId = tenantB,
                vmName = "tenant-b-vm",
                status = "PROVISIONED"
            )

            // When: Tenant A tries to update it
            withTenantDsl(tenantA) { dslA ->
                val repoA = VmRequestProjectionRepository(dslA)

                val rowsUpdated = repoA.updateVmDetails(
                    id = id,
                    vmwareVmId = "malicious-vm-id",
                    ipAddress = "10.0.0.1",
                    hostname = "hacked.local",
                    powerState = "POWERED_ON",
                    guestOs = "Malicious OS",
                    lastSyncedAt = OffsetDateTime.now()
                )

                // Then: RLS prevents the update
                assertEquals(0, rowsUpdated, "Tenant A should not be able to update Tenant B's projection")
            }

            // And: Verify the projection is unchanged
            withTenantDsl(tenantB) { dslB ->
                val repoB = VmRequestProjectionRepository(dslB)
                val result = repoB.findById(id)
                assertNotNull(result)
                assertNull(result?.vmwareVmId, "vmwareVmId should remain null")
                assertNull(result?.ipAddress, "ipAddress should remain null")
            }
        }

        @Test
        fun `RLS WITH CHECK prevents cross-tenant insert`() = runBlocking {
            // Given: Tenant A is authenticated
            val id = UUID.randomUUID()
            val now = OffsetDateTime.now()

            // When: Tenant A tries to insert a projection with Tenant B's tenant_id
            withTenantDsl(tenantA) { dslA ->
                val repoA = VmRequestProjectionRepository(dslA)

                // Create projection with WRONG tenant_id (tenant B's id)
                val maliciousProjection = de.acci.dvmm.infrastructure.jooq.`public`.tables.pojos.VmRequestsProjection(
                    id = id,
                    tenantId = tenantB.value, // ❌ Attempting cross-tenant injection
                    requesterId = UUID.randomUUID(),
                    requesterName = "Malicious User",
                    projectId = UUID.randomUUID(),
                    projectName = "Injected Project",
                    vmName = "malicious-vm",
                    size = "XL",
                    cpuCores = 64,
                    memoryGb = 256,
                    diskGb = 2000,
                    justification = "Trying to inject data into another tenant",
                    status = "PENDING",
                    approvedBy = null,
                    approvedByName = null,
                    rejectedBy = null,
                    rejectedByName = null,
                    rejectionReason = null,
                    createdAt = now,
                    updatedAt = now,
                    version = 1
                )

                // Then: RLS WITH CHECK clause should reject the insert
                val exception = org.junit.jupiter.api.assertThrows<org.jooq.exception.DataAccessException> {
                    repoA.insert(maliciousProjection)
                }
                // PostgreSQL RLS violation error: "new row violates row-level security policy"
                assertTrue(
                    exception.message?.contains("row-level security") == true ||
                    exception.message?.contains("policy") == true,
                    "Expected RLS violation error, got: ${exception.message}"
                )
            }

            // And: No data should exist for this id in tenant B's context
            withTenantDsl(tenantB) { dslB ->
                val repoB = VmRequestProjectionRepository(dslB)
                val result = repoB.findById(id)
                assertNull(result, "Cross-tenant injection should have failed - no data should exist")
            }
        }
    }
}
