package de.acci.dcm.infrastructure.projection

import de.acci.dcm.application.vmrequest.TimelineEventType
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.testing.TenantTestContext
import de.acci.eaf.testing.TestContainers
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
 * Integration tests for Request Detail adapters.
 *
 * Tests cover:
 * - VmRequestDetailRepositoryAdapter.findById
 * - TimelineEventReadRepositoryAdapter.findByRequestId
 */
@Testcontainers
@DisplayName("Request Detail Adapters Integration Tests")
class RequestDetailAdaptersIntegrationTest {

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

    @BeforeEach
    fun setup() {
        TenantTestContext.set(tenantA)
    }

    @AfterEach
    fun cleanup() {
        TenantTestContext.clear()
        superuserDsl.execute("""TRUNCATE TABLE public."VM_REQUESTS_PROJECTION" CASCADE""")
        superuserDsl.execute("""TRUNCATE TABLE public."REQUEST_TIMELINE_EVENTS" """)
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

    private fun insertTestProjection(
        id: UUID = UUID.randomUUID(),
        tenantId: TenantId,
        requesterId: UUID = UUID.randomUUID(),
        requesterName: String = "Test User",
        projectId: UUID = UUID.randomUUID(),
        projectName: String = "Test Project",
        vmName: String = "test-vm",
        size: String = "M",
        cpuCores: Int = 4,
        memoryGb: Int = 16,
        diskGb: Int = 200,
        justification: String = "Test justification",
        status: String = "PENDING",
        createdAt: OffsetDateTime = OffsetDateTime.now()
    ): UUID {
        TestContainers.postgres.createConnection("").use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO public."VM_REQUESTS_PROJECTION"
                ("ID", "TENANT_ID", "REQUESTER_ID", "REQUESTER_NAME", "PROJECT_ID", "PROJECT_NAME",
                 "VM_NAME", "SIZE", "CPU_CORES", "MEMORY_GB", "DISK_GB", "JUSTIFICATION",
                 "STATUS", "CREATED_AT", "UPDATED_AT", "VERSION")
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                ON CONFLICT ("ID") DO NOTHING
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
                stmt.setObject(15, createdAt)
                stmt.executeUpdate()
            }
        }
        return id
    }

    private fun insertTestTimelineEvent(
        id: UUID = UUID.randomUUID(),
        requestId: UUID,
        tenantId: TenantId,
        eventType: String = "CREATED",
        actorId: UUID? = UUID.randomUUID(),
        actorName: String? = "Test User",
        details: String? = null,
        occurredAt: OffsetDateTime = OffsetDateTime.now()
    ): UUID {
        // Ensure parent projection exists (FK constraint) using ON CONFLICT to be idempotent
        insertTestProjection(
            id = requestId,
            tenantId = tenantId,
            vmName = "test-vm-$requestId"
        )

        TestContainers.postgres.createConnection("").use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO public."REQUEST_TIMELINE_EVENTS"
                ("ID", "REQUEST_ID", "TENANT_ID", "EVENT_TYPE", "ACTOR_ID", "ACTOR_NAME", "DETAILS", "OCCURRED_AT")
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, requestId)
                stmt.setObject(3, tenantId.value)
                stmt.setString(4, eventType)
                stmt.setObject(5, actorId)
                stmt.setString(6, actorName)
                stmt.setString(7, details)
                stmt.setObject(8, occurredAt)
                stmt.executeUpdate()
            }
        }
        return id
    }

    @Nested
    @DisplayName("VmRequestDetailRepositoryAdapter")
    inner class VmRequestDetailRepositoryAdapterTests {

        @Test
        fun `findById returns projection when found`() = runBlocking {
            // Given: A projection exists
            val requestId = insertTestProjection(
                tenantId = tenantA,
                vmName = "web-server-01",
                size = "L",
                cpuCores = 8,
                memoryGb = 32,
                diskGb = 500,
                justification = "Production deployment",
                status = "APPROVED",
                projectName = "Production",
                requesterName = "John Doe"
            )

            withTenantDsl(tenantA) { dsl ->
                val adapter = VmRequestDetailRepositoryAdapter(dsl)

                // When: Find by ID
                val result = adapter.findById(VmRequestId(requestId))

                // Then: Projection is returned with correct values
                assertNotNull(result)
                assertEquals(requestId, result!!.id.value)
                assertEquals("web-server-01", result.vmName)
                assertEquals("L", result.size)
                assertEquals(8, result.cpuCores)
                assertEquals(32, result.memoryGb)
                assertEquals(500, result.diskGb)
                assertEquals("Production deployment", result.justification)
                assertEquals("APPROVED", result.status)
                assertEquals("Production", result.projectName)
                assertEquals("John Doe", result.requesterName)
            }
        }

        @Test
        fun `findById returns null when not found`() = runBlocking {
            withTenantDsl(tenantA) { dsl ->
                val adapter = VmRequestDetailRepositoryAdapter(dsl)

                // When: Find by non-existent ID
                val result = adapter.findById(VmRequestId.generate())

                // Then: Returns null
                assertNull(result)
            }
        }

        @Test
        fun `findById respects RLS - cannot see other tenant projections`() = runBlocking {
            // Given: A projection exists for tenant B
            val requestId = insertTestProjection(tenantId = tenantB)

            // When: Tenant A tries to find it
            withTenantDsl(tenantA) { dsl ->
                val adapter = VmRequestDetailRepositoryAdapter(dsl)
                val result = adapter.findById(VmRequestId(requestId))

                // Then: Returns null (RLS isolation)
                assertNull(result, "Tenant A should not see Tenant B's projection")
            }

            // Verify Tenant B can see it
            withTenantDsl(tenantB) { dsl ->
                val adapter = VmRequestDetailRepositoryAdapter(dsl)
                val result = adapter.findById(VmRequestId(requestId))

                assertNotNull(result, "Tenant B should see their own projection")
            }
        }
    }

    @Nested
    @DisplayName("TimelineEventReadRepositoryAdapter")
    inner class TimelineEventReadRepositoryAdapterTests {

        @Test
        fun `findByRequestId returns events sorted chronologically`() = runBlocking {
            // Given: Timeline events exist in reverse order
            val requestId = UUID.randomUUID()
            val now = OffsetDateTime.now()

            insertTestTimelineEvent(
                requestId = requestId,
                tenantId = tenantA,
                eventType = "APPROVED",
                occurredAt = now.plusHours(1)
            )
            insertTestTimelineEvent(
                requestId = requestId,
                tenantId = tenantA,
                eventType = "CREATED",
                occurredAt = now
            )

            withTenantDsl(tenantA) { dsl ->
                val adapter = TimelineEventReadRepositoryAdapter(dsl)

                // When: Find by request ID
                val events = adapter.findByRequestId(VmRequestId(requestId))

                // Then: Events are sorted chronologically (oldest first)
                assertEquals(2, events.size)
                assertEquals(TimelineEventType.CREATED, events[0].eventType)
                assertEquals(TimelineEventType.APPROVED, events[1].eventType)
            }
        }

        @Test
        fun `findByRequestId returns empty list for non-existent request`() = runBlocking {
            withTenantDsl(tenantA) { dsl ->
                val adapter = TimelineEventReadRepositoryAdapter(dsl)

                // When: Find by non-existent request ID
                val events = adapter.findByRequestId(VmRequestId.generate())

                // Then: Returns empty list
                assertEquals(0, events.size)
            }
        }

        @Test
        fun `findByRequestId maps all event fields correctly`() = runBlocking {
            // Given: A timeline event with all fields
            val requestId = UUID.randomUUID()
            // Truncate to seconds to avoid precision mismatches between Java and PostgreSQL
            val now = OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)

            insertTestTimelineEvent(
                requestId = requestId,
                tenantId = tenantA,
                eventType = "REJECTED",
                actorName = "Admin User",
                details = "Insufficient justification",
                occurredAt = now
            )

            withTenantDsl(tenantA) { dsl ->
                val adapter = TimelineEventReadRepositoryAdapter(dsl)

                // When: Find by request ID
                val events = adapter.findByRequestId(VmRequestId(requestId))

                // Then: All fields are mapped correctly
                assertEquals(1, events.size)
                assertEquals(TimelineEventType.REJECTED, events[0].eventType)
                assertEquals("Admin User", events[0].actorName)
                assertEquals("Insufficient justification", events[0].details)
                assertEquals(now.toInstant(), events[0].occurredAt)
            }
        }

        @Test
        fun `findByRequestId handles null actor name`() = runBlocking {
            // Given: A system event without actor
            val requestId = UUID.randomUUID()

            insertTestTimelineEvent(
                requestId = requestId,
                tenantId = tenantA,
                eventType = "CREATED",
                actorId = null,
                actorName = null
            )

            withTenantDsl(tenantA) { dsl ->
                val adapter = TimelineEventReadRepositoryAdapter(dsl)

                // When: Find by request ID
                val events = adapter.findByRequestId(VmRequestId(requestId))

                // Then: Null actor name is handled
                assertEquals(1, events.size)
                assertNull(events[0].actorName)
            }
        }

        @Test
        fun `findByRequestId respects RLS - cannot see other tenant events`() = runBlocking {
            // Given: Timeline events exist for tenant B
            val requestId = UUID.randomUUID()
            insertTestTimelineEvent(requestId = requestId, tenantId = tenantB)

            // When: Tenant A tries to find them
            withTenantDsl(tenantA) { dsl ->
                val adapter = TimelineEventReadRepositoryAdapter(dsl)
                val events = adapter.findByRequestId(VmRequestId(requestId))

                // Then: Returns empty (RLS isolation)
                assertEquals(0, events.size, "Tenant A should not see Tenant B's events")
            }

            // Verify Tenant B can see them
            withTenantDsl(tenantB) { dsl ->
                val adapter = TimelineEventReadRepositoryAdapter(dsl)
                val events = adapter.findByRequestId(VmRequestId(requestId))

                assertEquals(1, events.size, "Tenant B should see their own events")
            }
        }

        @Test
        fun `findByRequestId maps all timeline event types`() = runBlocking {
            // Given: Events with all types
            val requestId = UUID.randomUUID()
            val now = OffsetDateTime.now()
            val eventTypes = listOf("CREATED", "APPROVED", "REJECTED", "CANCELLED")

            eventTypes.forEachIndexed { index, eventType ->
                insertTestTimelineEvent(
                    requestId = requestId,
                    tenantId = tenantA,
                    eventType = eventType,
                    occurredAt = now.plusMinutes(index.toLong())
                )
            }

            withTenantDsl(tenantA) { dsl ->
                val adapter = TimelineEventReadRepositoryAdapter(dsl)

                // When: Find by request ID
                val events = adapter.findByRequestId(VmRequestId(requestId))

                // Then: All event types are mapped
                assertEquals(4, events.size)
                assertEquals(TimelineEventType.CREATED, events[0].eventType)
                assertEquals(TimelineEventType.APPROVED, events[1].eventType)
                assertEquals(TimelineEventType.REJECTED, events[2].eventType)
                assertEquals(TimelineEventType.CANCELLED, events[3].eventType)
            }
        }
    }
}
