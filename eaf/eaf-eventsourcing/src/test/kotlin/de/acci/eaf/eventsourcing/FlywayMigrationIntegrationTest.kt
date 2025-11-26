package de.acci.eaf.eventsourcing

import de.acci.eaf.testing.TestContainers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Integration tests for Flyway migration V001__create_event_store.sql.
 *
 * Tests AC: 4 (Flyway Migration)
 */
internal class FlywayMigrationIntegrationTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupSchema() {
            // Run Flyway migration to create schema (idempotent)
            TestContainers.ensureEventStoreSchema {
                FlywayMigrationIntegrationTest::class.java
                    .getResource("/db/migration/V001__create_event_store.sql")!!
                    .readText()
            }
        }
    }

    @Test
    fun `migration creates eaf_events schema`() {
        TestContainers.postgres.createConnection("").use { conn ->
            val rs = conn.createStatement().executeQuery(
                """
                SELECT schema_name
                FROM information_schema.schemata
                WHERE schema_name = 'eaf_events'
                """.trimIndent()
            )
            assertTrue(rs.next(), "eaf_events schema should exist")
            assertEquals("eaf_events", rs.getString("schema_name"))
        }
    }

    @Test
    fun `migration creates events table with all columns`() {
        TestContainers.postgres.createConnection("").use { conn ->
            val rs = conn.createStatement().executeQuery(
                """
                SELECT column_name, data_type, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'eaf_events' AND table_name = 'events'
                ORDER BY ordinal_position
                """.trimIndent()
            )

            val columns = mutableMapOf<String, Pair<String, String>>()
            while (rs.next()) {
                columns[rs.getString("column_name")] =
                    rs.getString("data_type") to rs.getString("is_nullable")
            }

            // Verify all required columns exist
            assertTrue(columns.containsKey("id"), "id column should exist")
            assertTrue(columns.containsKey("aggregate_id"), "aggregate_id column should exist")
            assertTrue(columns.containsKey("aggregate_type"), "aggregate_type column should exist")
            assertTrue(columns.containsKey("event_type"), "event_type column should exist")
            assertTrue(columns.containsKey("payload"), "payload column should exist")
            assertTrue(columns.containsKey("metadata"), "metadata column should exist")
            assertTrue(columns.containsKey("tenant_id"), "tenant_id column should exist")
            assertTrue(columns.containsKey("version"), "version column should exist")
            assertTrue(columns.containsKey("created_at"), "created_at column should exist")

            // Verify data types
            assertEquals("uuid", columns["id"]?.first, "id should be UUID")
            assertEquals("uuid", columns["aggregate_id"]?.first, "aggregate_id should be UUID")
            assertEquals("character varying", columns["aggregate_type"]?.first, "aggregate_type should be VARCHAR")
            assertEquals("jsonb", columns["payload"]?.first, "payload should be JSONB")
            assertEquals("jsonb", columns["metadata"]?.first, "metadata should be JSONB")
            assertEquals("uuid", columns["tenant_id"]?.first, "tenant_id should be UUID")
            assertEquals("integer", columns["version"]?.first, "version should be INT")
        }
    }

    @Test
    fun `migration creates unique constraint on aggregate_id and version`() {
        TestContainers.postgres.createConnection("").use { conn ->
            val rs = conn.createStatement().executeQuery(
                """
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'eaf_events'
                  AND table_name = 'events'
                  AND constraint_type = 'UNIQUE'
                  AND constraint_name = 'uq_aggregate_version'
                """.trimIndent()
            )
            assertTrue(rs.next(), "Unique constraint uq_aggregate_version should exist")
        }
    }

    @Test
    fun `migration creates index on aggregate_id`() {
        TestContainers.postgres.createConnection("").use { conn ->
            val rs = conn.createStatement().executeQuery(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'eaf_events'
                  AND tablename = 'events'
                  AND indexname = 'idx_events_aggregate'
                """.trimIndent()
            )
            assertTrue(rs.next(), "Index idx_events_aggregate should exist")
        }
    }

    @Test
    fun `migration creates index on tenant_id`() {
        TestContainers.postgres.createConnection("").use { conn ->
            val rs = conn.createStatement().executeQuery(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'eaf_events'
                  AND tablename = 'events'
                  AND indexname = 'idx_events_tenant'
                """.trimIndent()
            )
            assertTrue(rs.next(), "Index idx_events_tenant should exist")
        }
    }

    @Test
    fun `migration creates snapshots table`() {
        TestContainers.postgres.createConnection("").use { conn ->
            val rs = conn.createStatement().executeQuery(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'eaf_events' AND table_name = 'snapshots'
                """.trimIndent()
            )
            assertTrue(rs.next(), "snapshots table should exist")
        }
    }

    @Test
    fun `migration creates triggers for event immutability`() {
        TestContainers.postgres.createConnection("").use { conn ->
            val rs = conn.createStatement().executeQuery(
                """
                SELECT trigger_name
                FROM information_schema.triggers
                WHERE event_object_schema = 'eaf_events'
                  AND event_object_table = 'events'
                """.trimIndent()
            )

            val triggers = mutableListOf<String>()
            while (rs.next()) {
                triggers.add(rs.getString("trigger_name"))
            }

            assertTrue(
                triggers.contains("trg_prevent_event_update"),
                "Update prevention trigger should exist"
            )
            assertTrue(
                triggers.contains("trg_prevent_event_delete"),
                "Delete prevention trigger should exist"
            )
        }
    }
}
