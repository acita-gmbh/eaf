package de.acci.eaf.eventsourcing.snapshot

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.testing.IsolatedEventStore
import de.acci.eaf.testing.TenantTestContext
import de.acci.eaf.testing.TestContainers
import kotlinx.coroutines.test.runTest
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
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.sql.Connection
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for PostgresSnapshotStore.
 *
 * Tests AC: 3 (Snapshot Support)
 */
@IsolatedEventStore
@DisplayName("PostgresSnapshotStore Integration Tests")
internal class PostgresSnapshotStoreIntegrationTest {

    private lateinit var snapshotStore: PostgresSnapshotStore
    private lateinit var connection: Connection
    private val tenantId = TenantId.generate()

    companion object {
        private val objectMapper: ObjectMapper = jacksonObjectMapper()

        @JvmStatic
        @BeforeAll
        fun setupSchema() {
            // Run Flyway migration to create schema (idempotent)
            TestContainers.ensureEventStoreSchema {
                PostgresSnapshotStoreIntegrationTest::class.java
                    .getResource("/db/migration/V001__create_event_store.sql")!!
                    .readText()
            }
        }
    }

    /**
     * Compares two JSON strings for semantic equality.
     * PostgreSQL normalizes JSON formatting, so we parse and compare the tree structure.
     */
    private fun assertJsonEquals(expected: String, actual: String) {
        val expectedTree = objectMapper.readTree(expected)
        val actualTree = objectMapper.readTree(actual)
        assertEquals(expectedTree, actualTree, "JSON content should be semantically equal")
    }

    @BeforeEach
    fun setUp() {
        TenantTestContext.set(tenantId)
        connection = TestContainers.postgres.createConnection("")
        val dsl = DSL.using(connection, SQLDialect.POSTGRES)
        snapshotStore = PostgresSnapshotStore(dsl)
    }

    @AfterEach
    fun tearDown() {
        TenantTestContext.clear()
        if (::connection.isInitialized && !connection.isClosed) {
            connection.close()
        }
    }

    @Nested
    @DisplayName("Save and Load")
    inner class SaveAndLoad {

        @Test
        @DisplayName("save and load snapshot round-trip")
        fun `save and load snapshot round-trip`() = runTest {
            // Given
            val aggregateId = UUID.randomUUID()
            val stateJson = """{"name":"test","value":100}"""
            val snapshot = AggregateSnapshot(
                aggregateId = aggregateId,
                aggregateType = "TestAggregate",
                version = 42L,
                state = stateJson,
                tenantId = tenantId.value,
                createdAt = Instant.now()
            )

            // When
            snapshotStore.save(snapshot)
            val loaded = snapshotStore.load(aggregateId)

            // Then
            assertNotNull(loaded)
            assertEquals(aggregateId, loaded!!.aggregateId)
            assertEquals("TestAggregate", loaded.aggregateType)
            assertEquals(42L, loaded.version)
            // PostgreSQL normalizes JSON, compare parsed content
            assertJsonEquals(stateJson, loaded.state)
            assertEquals(tenantId.value, loaded.tenantId)
        }

        @Test
        @DisplayName("load returns null for non-existent aggregate")
        fun `load returns null for non-existent aggregate`() = runTest {
            // Given
            val nonExistentId = UUID.randomUUID()

            // When
            val loaded = snapshotStore.load(nonExistentId)

            // Then
            assertNull(loaded)
        }
    }

    @Nested
    @DisplayName("Upsert Behavior")
    inner class UpsertBehavior {

        @Test
        @DisplayName("save overwrites previous snapshot for same aggregate")
        fun `save overwrites previous snapshot for same aggregate`() = runTest {
            // Given
            val aggregateId = UUID.randomUUID()
            val snapshot1 = AggregateSnapshot(
                aggregateId = aggregateId,
                aggregateType = "TestAggregate",
                version = 10L,
                state = """{"name":"first","value":1}""",
                tenantId = tenantId.value,
                createdAt = Instant.now()
            )
            snapshotStore.save(snapshot1)

            // When - save a newer version
            val expectedState = """{"name":"second","value":2}"""
            val snapshot2 = AggregateSnapshot(
                aggregateId = aggregateId,
                aggregateType = "TestAggregate",
                version = 50L,
                state = expectedState,
                tenantId = tenantId.value,
                createdAt = Instant.now()
            )
            snapshotStore.save(snapshot2)
            val loaded = snapshotStore.load(aggregateId)

            // Then - newer version is returned
            assertNotNull(loaded)
            assertEquals(50L, loaded!!.version)
            // PostgreSQL normalizes JSON, compare parsed content
            assertJsonEquals(expectedState, loaded.state)
        }

        @Test
        @DisplayName("save can update aggregate type")
        fun `save can update aggregate type`() = runTest {
            // Given
            val aggregateId = UUID.randomUUID()
            val snapshot1 = AggregateSnapshot(
                aggregateId = aggregateId,
                aggregateType = "OldType",
                version = 10L,
                state = """{}""",
                tenantId = tenantId.value,
                createdAt = Instant.now()
            )
            snapshotStore.save(snapshot1)

            // When - update with new type (e.g., after migration)
            val snapshot2 = AggregateSnapshot(
                aggregateId = aggregateId,
                aggregateType = "NewType",
                version = 20L,
                state = """{}""",
                tenantId = tenantId.value,
                createdAt = Instant.now()
            )
            snapshotStore.save(snapshot2)
            val loaded = snapshotStore.load(aggregateId)

            // Then
            assertNotNull(loaded)
            assertEquals("NewType", loaded!!.aggregateType)
        }
    }

    @Nested
    @DisplayName("Complex State Serialization")
    inner class ComplexStateSerialization {

        @Test
        @DisplayName("preserves complex JSON state")
        fun `preserves complex JSON state`() = runTest {
            // Given
            val aggregateId = UUID.randomUUID()
            val complexState = """
                {
                    "id": "123",
                    "nested": {
                        "array": [1, 2, 3],
                        "object": {"key": "value"}
                    },
                    "special": "quotes\"and\\backslash"
                }
            """.trimIndent()

            val snapshot = AggregateSnapshot(
                aggregateId = aggregateId,
                aggregateType = "ComplexAggregate",
                version = 1L,
                state = complexState,
                tenantId = tenantId.value,
                createdAt = Instant.now()
            )

            // When
            snapshotStore.save(snapshot)
            val loaded = snapshotStore.load(aggregateId)

            // Then - JSON is normalized by PostgreSQL but content preserved
            assertNotNull(loaded)
            // PostgreSQL may format JSON differently, but the parsed content should be equivalent
            assertJsonEquals(complexState, loaded!!.state)
        }
    }
}
