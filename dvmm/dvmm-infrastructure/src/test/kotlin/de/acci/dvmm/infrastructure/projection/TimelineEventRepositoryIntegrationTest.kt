package de.acci.dvmm.infrastructure.projection

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.testing.TenantTestContext
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Integration tests for TimelineEventRepository.
 *
 * Tests cover:
 * - Insert operations with idempotency (ON CONFLICT DO NOTHING)
 * - Query by request ID with chronological ordering
 * - RLS tenant isolation
 */
@Testcontainers
@DisplayName("TimelineEventRepository Integration Tests")
class TimelineEventRepositoryIntegrationTest {

    companion object {
        private const val TC_DB_NAME = "dvmm_test"

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName(TC_DB_NAME)

        private lateinit var superuserDsl: DSLContext

        @BeforeAll
        @JvmStatic
        fun setupSchema() {
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

            superuserDsl = DSL.using(postgres.jdbcUrl, postgres.username, postgres.password)
        }
    }

    private val tenantA = TenantId(UUID.randomUUID())
    private val tenantB = TenantId(UUID.randomUUID())
    private lateinit var repository: TimelineEventRepository

    @BeforeEach
    fun setup() {
        TenantTestContext.set(tenantA)
    }

    @AfterEach
    fun cleanup() {
        TenantTestContext.clear()
        superuserDsl.execute("""TRUNCATE TABLE public."REQUEST_TIMELINE_EVENTS" """)
    }

    private fun createTenantDsl(tenant: TenantId): Pair<Connection, DSLContext> {
        val conn = postgres.createConnection("")
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

    private fun insertTestTimelineEvent(
        id: UUID = UUID.randomUUID(),
        requestId: UUID = UUID.randomUUID(),
        tenantId: TenantId,
        eventType: String = "CREATED",
        actorId: UUID? = UUID.randomUUID(),
        actorName: String? = "Test User",
        details: String? = null,
        occurredAt: OffsetDateTime = OffsetDateTime.now()
    ): UUID {
        postgres.createConnection("").use { conn ->
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
    @DisplayName("findByRequestId")
    inner class FindByRequestId {

        @Test
        fun `returns timeline events for a request sorted chronologically`() = runBlocking {
            val requestId = UUID.randomUUID()
            val now = OffsetDateTime.now()

            // Insert events in reverse order to test sorting
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
                repository = TimelineEventRepository(dsl)
                val events = repository.findByRequestId(requestId)

                assertEquals(2, events.size)
                assertEquals("CREATED", events[0].eventType, "First event should be oldest")
                assertEquals("APPROVED", events[1].eventType, "Second event should be newer")
            }
        }

        @Test
        fun `returns empty list for non-existent request`() = runBlocking {
            withTenantDsl(tenantA) { dsl ->
                repository = TimelineEventRepository(dsl)
                val events = repository.findByRequestId(UUID.randomUUID())

                assertTrue(events.isEmpty())
            }
        }

        @Test
        fun `respects RLS - cannot see other tenant events`() = runBlocking {
            val requestId = UUID.randomUUID()

            // Insert event for tenant B
            insertTestTimelineEvent(
                requestId = requestId,
                tenantId = tenantB,
                eventType = "CREATED"
            )

            // Tenant A should not see it
            withTenantDsl(tenantA) { dsl ->
                repository = TimelineEventRepository(dsl)
                val events = repository.findByRequestId(requestId)

                assertTrue(events.isEmpty(), "Tenant A should not see Tenant B's timeline events")
            }

            // Tenant B should see it
            withTenantDsl(tenantB) { dsl ->
                repository = TimelineEventRepository(dsl)
                val events = repository.findByRequestId(requestId)

                assertEquals(1, events.size, "Tenant B should see their own timeline events")
            }
        }
    }

    @Nested
    @DisplayName("insert")
    inner class Insert {

        @Test
        fun `inserts new timeline event successfully`() = runBlocking {
            val id = UUID.randomUUID()
            val requestId = UUID.randomUUID()
            val now = OffsetDateTime.now()

            withTenantDsl(tenantA) { dsl ->
                repository = TimelineEventRepository(dsl)

                repository.insert(
                    id = id,
                    requestId = requestId,
                    tenantId = tenantA.value,
                    eventType = "CREATED",
                    actorId = UUID.randomUUID(),
                    actorName = "John Doe",
                    details = """{"reason": "Initial creation"}""",
                    occurredAt = now
                )

                val events = repository.findByRequestId(requestId)
                assertEquals(1, events.size)
                assertEquals(id, events[0].id)
                assertEquals("CREATED", events[0].eventType)
                assertEquals("John Doe", events[0].actorName)
            }
        }

        @Test
        fun `handles duplicate insert idempotently`() = runBlocking {
            val id = UUID.randomUUID()
            val requestId = UUID.randomUUID()
            val now = OffsetDateTime.now()

            withTenantDsl(tenantA) { dsl ->
                repository = TimelineEventRepository(dsl)

                // First insert
                repository.insert(
                    id = id,
                    requestId = requestId,
                    tenantId = tenantA.value,
                    eventType = "CREATED",
                    actorId = null,
                    actorName = "Original Actor",
                    details = null,
                    occurredAt = now
                )

                // Second insert with same ID (should be ignored)
                repository.insert(
                    id = id,
                    requestId = requestId,
                    tenantId = tenantA.value,
                    eventType = "MODIFIED",
                    actorId = null,
                    actorName = "Different Actor",
                    details = null,
                    occurredAt = now.plusMinutes(1)
                )

                val events = repository.findByRequestId(requestId)
                assertEquals(1, events.size, "Duplicate insert should be ignored")
                assertEquals("CREATED", events[0].eventType, "Original event type should be preserved")
                assertEquals("Original Actor", events[0].actorName, "Original actor should be preserved")
            }
        }
    }

    @Nested
    @DisplayName("exists")
    inner class Exists {

        @Test
        fun `returns true when event exists`() = runBlocking {
            val eventId = insertTestTimelineEvent(tenantId = tenantA)

            withTenantDsl(tenantA) { dsl ->
                repository = TimelineEventRepository(dsl)
                assertTrue(repository.exists(eventId))
            }
        }

        @Test
        fun `returns false when event does not exist`() = runBlocking {
            withTenantDsl(tenantA) { dsl ->
                repository = TimelineEventRepository(dsl)
                assertFalse(repository.exists(UUID.randomUUID()))
            }
        }

        @Test
        fun `respects RLS - cannot see other tenant events`() = runBlocking {
            val eventId = insertTestTimelineEvent(tenantId = tenantB)

            withTenantDsl(tenantA) { dsl ->
                repository = TimelineEventRepository(dsl)
                assertFalse(repository.exists(eventId), "Tenant A should not see Tenant B's event")
            }
        }
    }

    @Nested
    @DisplayName("Event Types")
    inner class EventTypes {

        @Test
        fun `stores all event types correctly`() = runBlocking {
            val requestId = UUID.randomUUID()
            val now = OffsetDateTime.now()
            val eventTypes = listOf("CREATED", "APPROVED", "REJECTED", "CANCELLED", "PROVISIONING_STARTED", "VM_READY")

            eventTypes.forEachIndexed { index, eventType ->
                insertTestTimelineEvent(
                    requestId = requestId,
                    tenantId = tenantA,
                    eventType = eventType,
                    occurredAt = now.plusMinutes(index.toLong())
                )
            }

            withTenantDsl(tenantA) { dsl ->
                repository = TimelineEventRepository(dsl)
                val events = repository.findByRequestId(requestId)

                assertEquals(6, events.size)
                assertEquals(eventTypes, events.map { it.eventType })
            }
        }

        @Test
        fun `stores rejection reason in details`() = runBlocking {
            val requestId = UUID.randomUUID()
            val rejectionReason = """{"reason": "Insufficient justification provided"}"""

            insertTestTimelineEvent(
                requestId = requestId,
                tenantId = tenantA,
                eventType = "REJECTED",
                details = rejectionReason
            )

            withTenantDsl(tenantA) { dsl ->
                repository = TimelineEventRepository(dsl)
                val events = repository.findByRequestId(requestId)

                assertEquals(1, events.size)
                assertEquals(rejectionReason, events[0].details)
            }
        }
    }
}
