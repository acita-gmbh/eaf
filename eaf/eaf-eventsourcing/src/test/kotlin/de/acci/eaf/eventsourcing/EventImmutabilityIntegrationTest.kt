package de.acci.eaf.eventsourcing

import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.testing.IsolatedEventStore
import de.acci.eaf.testing.TenantTestContext
import de.acci.eaf.testing.TestContainers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Integration tests for event immutability.
 *
 * Tests AC: 3 (Event Immutability)
 *
 * Verifies that UPDATE and DELETE operations are rejected on the events table.
 */
@IsolatedEventStore
internal class EventImmutabilityIntegrationTest {

    private val tenantId = TenantId.generate()

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupSchema() {
            // Run Flyway migration to create schema (idempotent)
            TestContainers.ensureEventStoreSchema {
                EventImmutabilityIntegrationTest::class.java
                    .getResource("/db/migration/V001__create_event_store.sql")!!
                    .readText()
            }
        }
    }

    @BeforeEach
    fun setUp() {
        TenantTestContext.set(tenantId)
    }

    @AfterEach
    fun tearDown() {
        TenantTestContext.clear()
    }

    @Test
    fun `UPDATE operation on events table is rejected`() {
        TestContainers.postgres.createConnection("").use { conn ->
            // Insert a test event
            val eventId = insertTestEvent(conn)

            // Attempt to UPDATE the event (using PreparedStatement to avoid SQL injection)
            val exception = assertThrows<SQLException> {
                conn.prepareStatement(
                    """
                    UPDATE eaf_events.events
                    SET event_type = 'ModifiedEvent'
                    WHERE id = ?
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setObject(1, eventId)
                    stmt.executeUpdate()
                }
            }

            // Verify the error message indicates immutability
            assertTrue(
                exception.message?.contains("immutable") == true ||
                    exception.message?.contains("UPDATE") == true ||
                    exception.message?.contains("not allowed") == true,
                "Error message should indicate that UPDATE is not allowed: ${exception.message}"
            )
        }
    }

    @Test
    fun `DELETE operation on events table is rejected`() {
        TestContainers.postgres.createConnection("").use { conn ->
            // Insert a test event
            val eventId = insertTestEvent(conn)

            // Attempt to DELETE the event (using PreparedStatement to avoid SQL injection)
            val exception = assertThrows<SQLException> {
                conn.prepareStatement(
                    """
                    DELETE FROM eaf_events.events
                    WHERE id = ?
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setObject(1, eventId)
                    stmt.executeUpdate()
                }
            }

            // Verify the error message indicates immutability
            assertTrue(
                exception.message?.contains("immutable") == true ||
                    exception.message?.contains("DELETE") == true ||
                    exception.message?.contains("not allowed") == true,
                "Error message should indicate that DELETE is not allowed: ${exception.message}"
            )
        }
    }

    @Test
    fun `INSERT operation on events table is allowed`() {
        TestContainers.postgres.createConnection("").use { conn ->
            // This should succeed without exception
            val eventId = insertTestEvent(conn)

            // Verify the event was inserted (using PreparedStatement to avoid SQL injection)
            conn.prepareStatement("SELECT id FROM eaf_events.events WHERE id = ?").use { stmt ->
                stmt.setObject(1, eventId)
                val rs = stmt.executeQuery()
                assertTrue(rs.next(), "Event should be inserted successfully")
            }
        }
    }

    @Test
    fun `SELECT operation on events table is allowed`() {
        TestContainers.postgres.createConnection("").use { conn ->
            // Insert a test event
            insertTestEvent(conn)

            // This should succeed without exception
            val rs = conn.createStatement().executeQuery(
                "SELECT * FROM eaf_events.events"
            )
            assertTrue(rs.next(), "SELECT should return at least one row")
        }
    }

    private fun insertTestEvent(conn: java.sql.Connection): UUID {
        val eventId = UUID.randomUUID()
        val aggregateId = UUID.randomUUID()
        val userId = UserId.generate()
        val correlationId = CorrelationId.generate()
        val timestamp = Instant.now()

        // Use PreparedStatement to avoid SQL injection vulnerabilities
        val sql = """
            INSERT INTO eaf_events.events
                (id, aggregate_id, aggregate_type, event_type, payload, metadata, tenant_id, version)
            VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, eventId)
            stmt.setObject(2, aggregateId)
            stmt.setString(3, "TestAggregate")
            stmt.setString(4, "TestEvent")
            stmt.setString(5, """{"test": "data"}""")
            stmt.setString(
                6,
                """{"tenantId": "${tenantId.value}", "userId": "${userId.value}", "correlationId": "${correlationId.value}", "timestamp": "$timestamp"}"""
            )
            stmt.setObject(7, tenantId.value)
            stmt.setInt(8, 1)
            stmt.executeUpdate()
        }
        return eventId
    }
}
