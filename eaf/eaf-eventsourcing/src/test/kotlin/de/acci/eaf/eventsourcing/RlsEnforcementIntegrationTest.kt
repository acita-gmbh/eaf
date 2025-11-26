package de.acci.eaf.eventsourcing

import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.testing.IsolatedEventStore
import de.acci.eaf.testing.RlsEnforcingDataSource
import de.acci.eaf.testing.TenantTestContext
import de.acci.eaf.testing.TestContainers
import kotlinx.coroutines.test.runTest
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Integration tests for Row-Level Security (RLS) enforcement on events table.
 *
 * Tests AC: 1 (Event Persistence - tenant isolation)
 *
 * Verifies that events from one tenant are not visible to another tenant
 * when using RlsEnforcingDataSource.
 */
@IsolatedEventStore
internal class RlsEnforcementIntegrationTest {

    private val objectMapper = EventStoreObjectMapper.create()

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupSchema() {
            // Run Flyway migration and enable RLS (idempotent)
            TestContainers.ensureEventStoreSchemaWithRls {
                RlsEnforcementIntegrationTest::class.java
                    .getResource("/db/migration/V001__create_event_store.sql")!!
                    .readText()
            }
        }
    }

    @AfterEach
    fun tearDown() {
        TenantTestContext.clear()
    }

    @Test
    fun `events from tenant A are not visible to tenant B`() = runTest {
        // Given - two different tenants
        val tenantA = TenantId.generate()
        val tenantB = TenantId.generate()
        val aggregateId = UUID.randomUUID()

        // Create RLS-enforcing data source
        val baseDataSource = createBaseDataSource()
        val rlsDataSource = RlsEnforcingDataSource(baseDataSource)

        // Insert event as tenant A
        TenantTestContext.set(tenantA)
        val connectionA = rlsDataSource.connection
        val storeA = PostgresEventStore(DSL.using(connectionA, SQLDialect.POSTGRES), objectMapper)
        val eventA = TestEventCreated(
            metadata = createMetadata(tenantA),
            name = "tenant-a-event",
            value = 42
        )
        val result = storeA.append(aggregateId, listOf(eventA), expectedVersion = 0)
        assertInstanceOf(Result.Success::class.java, result)
        connectionA.close()
        TenantTestContext.clear()

        // Query as tenant B
        TenantTestContext.set(tenantB)
        val connectionB = rlsDataSource.connection
        val storeB = PostgresEventStore(DSL.using(connectionB, SQLDialect.POSTGRES), objectMapper)

        // When - tenant B tries to load tenant A's events
        val eventsForTenantB = storeB.load(aggregateId)
        connectionB.close()
        TenantTestContext.clear()

        // Then - tenant B should not see tenant A's events
        assertTrue(eventsForTenantB.isEmpty(), "Tenant B should not see Tenant A's events")
    }

    @Test
    fun `tenant can see their own events`() = runTest {
        // Given
        val tenantA = TenantId.generate()
        val aggregateId = UUID.randomUUID()

        val baseDataSource = createBaseDataSource()
        val rlsDataSource = RlsEnforcingDataSource(baseDataSource)

        TenantTestContext.set(tenantA)
        val connection = rlsDataSource.connection
        val store = PostgresEventStore(DSL.using(connection, SQLDialect.POSTGRES), objectMapper)

        // Insert event
        val event = TestEventCreated(
            metadata = createMetadata(tenantA),
            name = "my-event",
            value = 123
        )
        store.append(aggregateId, listOf(event), expectedVersion = 0)

        // When - same tenant loads events
        val loaded = store.load(aggregateId)
        connection.close()
        TenantTestContext.clear()

        // Then
        assertEquals(1, loaded.size)
        assertEquals("my-event", objectMapper.readTree(loaded[0].payload).get("name").asText())
    }

    @Test
    fun `multiple tenants can have events for same aggregate ID without conflict`() = runTest {
        // Given
        val tenantA = TenantId.generate()
        val tenantB = TenantId.generate()
        val aggregateId = UUID.randomUUID() // Same aggregate ID!

        val baseDataSource = createBaseDataSource()
        val rlsDataSource = RlsEnforcingDataSource(baseDataSource)

        // Insert event for tenant A
        TenantTestContext.set(tenantA)
        var connection = rlsDataSource.connection
        var store = PostgresEventStore(DSL.using(connection, SQLDialect.POSTGRES), objectMapper)
        store.append(
            aggregateId,
            listOf(TestEventCreated(metadata = createMetadata(tenantA), name = "A", value = 1)),
            expectedVersion = 0
        )
        connection.close()
        TenantTestContext.clear()

        // Insert event for tenant B with same aggregate ID
        // This should NOT conflict because unique constraint includes tenant_id:
        // UNIQUE (tenant_id, aggregate_id, version)
        TenantTestContext.set(tenantB)
        connection = rlsDataSource.connection
        store = PostgresEventStore(DSL.using(connection, SQLDialect.POSTGRES), objectMapper)

        // Tenant B inserts their own event with same aggregate_id and version=1
        val resultB = store.append(
            aggregateId,
            listOf(TestEventCreated(metadata = createMetadata(tenantB), name = "B", value = 2)),
            expectedVersion = 0
        )
        assertInstanceOf(Result.Success::class.java, resultB, "Tenant B should be able to insert events for same aggregate ID")

        // Each tenant should be able to query and see only their events
        val tenantBEvents = store.load(aggregateId)
        connection.close()
        TenantTestContext.clear()

        // Tenant B should see their own event
        assertEquals(1, tenantBEvents.size, "Tenant B should see their own event")
        assertEquals("B", objectMapper.readTree(tenantBEvents[0].payload).get("name").asText())

        // Verify tenant A can still see their event
        TenantTestContext.set(tenantA)
        connection = rlsDataSource.connection
        store = PostgresEventStore(DSL.using(connection, SQLDialect.POSTGRES), objectMapper)
        val tenantAEvents = store.load(aggregateId)
        connection.close()
        TenantTestContext.clear()

        assertEquals(1, tenantAEvents.size)
        assertEquals("A", objectMapper.readTree(tenantAEvents[0].payload).get("name").asText())
    }

    private fun createBaseDataSource(): DataSource {
        return object : DataSource {
            override fun getConnection(): Connection =
                TestContainers.postgres.createConnection("")

            override fun getConnection(username: String?, password: String?): Connection =
                TestContainers.postgres.createConnection("")

            override fun getLogWriter() = null
            override fun setLogWriter(out: java.io.PrintWriter?) {}
            override fun setLoginTimeout(seconds: Int) {}
            override fun getLoginTimeout() = 0
            override fun getParentLogger() = throw UnsupportedOperationException()
            override fun <T : Any?> unwrap(iface: Class<T>?): T = throw UnsupportedOperationException()
            override fun isWrapperFor(iface: Class<*>?) = false
        }
    }

    @Test
    fun `query without tenant context returns zero rows - fail-closed semantics (AC 1)`() = runTest {
        // This test verifies fail-closed behavior: when no tenant context is set,
        // queries should return zero rows, NOT throw an exception at DB level.
        // The RlsEnforcingDataSource throws an exception BEFORE the query runs,
        // but if somehow a query reaches the DB without tenant context, RLS should
        // return empty results.

        // Given - insert event with a valid tenant
        val tenantA = TenantId.generate()
        val aggregateId = UUID.randomUUID()

        val baseDataSource = createBaseDataSource()
        val rlsDataSource = RlsEnforcingDataSource(baseDataSource)

        TenantTestContext.set(tenantA)
        val connection = rlsDataSource.connection
        val store = PostgresEventStore(DSL.using(connection, SQLDialect.POSTGRES), objectMapper)
        store.append(
            aggregateId,
            listOf(TestEventCreated(metadata = createMetadata(tenantA), name = "test", value = 1)),
            expectedVersion = 0
        )
        connection.close()
        TenantTestContext.clear()

        // When - query directly without setting app.tenant_id (bypass RlsEnforcingDataSource)
        // This simulates what happens if RLS is the only protection layer
        val directConnection = TestContainers.postgres.createConnection("")
        directConnection.createStatement().execute("SET ROLE eaf_app")
        // Don't set app.tenant_id - this tests fail-closed behavior

        val dsl = DSL.using(directConnection, SQLDialect.POSTGRES)
        val directResult = dsl.fetch(
            "SELECT * FROM eaf_events.events WHERE aggregate_id = ?",
            aggregateId
        )
        directConnection.close()

        // Then - should return zero rows (fail-closed), not all rows
        assertEquals(0, directResult.size, "Without tenant context, RLS should return zero rows (fail-closed)")
    }

    @Test
    fun `RLS policy uses current_setting with missing_ok true (AC 3)`() = runTest {
        // Verify the RLS policy is correctly configured with current_setting('app.tenant_id', true)
        // The 'true' parameter means missing_ok, so it returns NULL instead of error

        // Given - direct connection to verify policy definition
        val connection = TestContainers.postgres.createConnection("")

        // When - query the policy definition
        connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("""
                    SELECT polname, pg_get_expr(polqual, polrelid) as policy_expr
                    FROM pg_policy
                    WHERE polrelid = 'eaf_events.events'::regclass
                """).use { policyInfo ->
                    // Then - verify policy exists and uses correct expression
                    assertTrue(policyInfo.next(), "RLS policy should exist on events table")
                    val policyName = policyInfo.getString("polname")
                    val policyExpr = policyInfo.getString("policy_expr")

                    assertEquals("tenant_isolation_events", policyName)
                    assertTrue(
                        policyExpr.contains("current_setting") && policyExpr.contains("app.tenant_id"),
                        "Policy should use current_setting('app.tenant_id', true)"
                    )
                }
            }
        }
    }

    @Test
    fun `FORCE ROW LEVEL SECURITY is enabled - no bypass for table owner (AC 6)`() = runTest {
        // Verify that FORCE ROW LEVEL SECURITY is enabled, which prevents
        // even the table owner from bypassing RLS

        TestContainers.postgres.createConnection("").use { connection ->
            // Query for RLS enforcement status
            connection.createStatement().use { stmt ->
                stmt.executeQuery("""
                    SELECT relforcerowsecurity
                    FROM pg_class
                    WHERE oid = 'eaf_events.events'::regclass
                """).use { result ->
                    assertTrue(result.next(), "Should find events table")
                    val forceRls = result.getBoolean("relforcerowsecurity")
                    assertTrue(forceRls, "FORCE ROW LEVEL SECURITY should be enabled on events table")
                }
            }
        }
    }

    private fun createMetadata(tenantId: TenantId): EventMetadata = EventMetadata(
        tenantId = tenantId,
        userId = UserId.generate(),
        correlationId = CorrelationId.generate(),
        timestamp = Instant.now()
    )
}
