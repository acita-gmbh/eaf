package de.acci.eaf.eventsourcing

import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.testing.IsolatedEventStore
import de.acci.eaf.testing.TenantTestContext
import de.acci.eaf.testing.TestContainers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for PostgresEventStore.
 *
 * Tests AC: 1 (Event Persistence), 2 (Optimistic Locking), 5 (Event Loading)
 */
@IsolatedEventStore
internal class PostgresEventStoreIntegrationTest {

    private lateinit var eventStore: PostgresEventStore
    private lateinit var connection: Connection
    private val objectMapper = EventStoreObjectMapper.create()
    private val tenantId = TenantId.generate()

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupSchema() {
            // Run Flyway migration to create schema (idempotent)
            TestContainers.ensureEventStoreSchema {
                PostgresEventStoreIntegrationTest::class.java
                    .getResource("/db/migration/V001__create_event_store.sql")!!
                    .readText()
            }
        }
    }

    @BeforeEach
    fun setUp() {
        TenantTestContext.set(tenantId)
        connection = TestContainers.postgres.createConnection("")
        val dsl = DSL.using(connection, SQLDialect.POSTGRES)
        eventStore = PostgresEventStore(dsl, objectMapper)
    }

    @AfterEach
    fun tearDown() {
        TenantTestContext.clear()
        if (::connection.isInitialized && !connection.isClosed) {
            connection.close()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC 1: Event Persistence Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `append persists event to database`() = runTest {
        // Given
        val aggregateId = UUID.randomUUID()
        val metadata = createMetadata()
        val event = TestEventCreated(
            metadata = metadata,
            name = "test",
            value = 42
        )

        // When
        val result = eventStore.append(aggregateId, listOf(event), expectedVersion = 0)

        // Then
        val success = assertInstanceOf(Result.Success::class.java, result)
        assertEquals(1L, success.value)

        val loaded = eventStore.load(aggregateId)
        assertEquals(1, loaded.size)
        assertEquals(aggregateId, loaded[0].aggregateId)
        assertEquals("TestAggregate", loaded[0].aggregateType)
        assertEquals("TestEventCreated", loaded[0].eventType)
        assertEquals(1L, loaded[0].version)
        assertEquals(tenantId, loaded[0].metadata.tenantId)
    }

    @Test
    fun `append multiple events increments version correctly`() = runTest {
        // Given
        val aggregateId = UUID.randomUUID()
        val events = listOf(
            TestEventCreated(metadata = createMetadata(), name = "test", value = 1),
            TestEventUpdated(metadata = createMetadata(), newValue = 2),
            TestEventUpdated(metadata = createMetadata(), newValue = 3)
        )

        // When
        val result = eventStore.append(aggregateId, events, expectedVersion = 0)

        // Then
        val success = assertInstanceOf(Result.Success::class.java, result)
        assertEquals(3L, success.value)

        val loaded = eventStore.load(aggregateId)
        assertEquals(3, loaded.size)
        assertEquals(1L, loaded[0].version)
        assertEquals(2L, loaded[1].version)
        assertEquals(3L, loaded[2].version)
    }

    @Test
    fun `append empty list returns expected version`() = runTest {
        // Given
        val aggregateId = UUID.randomUUID()

        // When
        val result = eventStore.append(aggregateId, emptyList(), expectedVersion = 5)

        // Then
        val success = assertInstanceOf(Result.Success::class.java, result)
        assertEquals(5L, success.value)
    }

    @Test
    fun `event metadata is persisted correctly`() = runTest {
        // Given
        val aggregateId = UUID.randomUUID()
        val userId = UserId.generate()
        val correlationId = CorrelationId.generate()
        val timestamp = Instant.now()
        val metadata = EventMetadata(
            tenantId = tenantId,
            userId = userId,
            correlationId = correlationId,
            timestamp = timestamp
        )
        val event = TestEventCreated(metadata = metadata, name = "test", value = 42)

        // When
        eventStore.append(aggregateId, listOf(event), expectedVersion = 0)
        val loaded = eventStore.load(aggregateId)

        // Then
        assertEquals(1, loaded.size)
        assertEquals(tenantId, loaded[0].metadata.tenantId)
        assertEquals(userId, loaded[0].metadata.userId)
        assertEquals(correlationId, loaded[0].metadata.correlationId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC 2: Optimistic Locking Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `append with wrong expected version returns ConcurrencyConflict`() = runTest {
        // Given
        val aggregateId = UUID.randomUUID()
        val event1 = TestEventCreated(metadata = createMetadata(), name = "test", value = 1)
        eventStore.append(aggregateId, listOf(event1), expectedVersion = 0)

        // When - try to append with wrong version
        val event2 = TestEventUpdated(metadata = createMetadata(), newValue = 2)
        val result = eventStore.append(aggregateId, listOf(event2), expectedVersion = 0)

        // Then
        val failure = assertInstanceOf(Result.Failure::class.java, result)
        val conflict = assertInstanceOf(EventStoreError.ConcurrencyConflict::class.java, failure.error)
        assertEquals(aggregateId, conflict.aggregateId)
        assertEquals(0L, conflict.expectedVersion)
        assertEquals(1L, conflict.actualVersion)
    }

    @Test
    fun `append after previous append succeeds with correct version`() = runTest {
        // Given
        val aggregateId = UUID.randomUUID()
        val event1 = TestEventCreated(metadata = createMetadata(), name = "test", value = 1)
        val result1 = eventStore.append(aggregateId, listOf(event1), expectedVersion = 0)
        assertInstanceOf(Result.Success::class.java, result1)

        // When
        val event2 = TestEventUpdated(metadata = createMetadata(), newValue = 2)
        val result2 = eventStore.append(aggregateId, listOf(event2), expectedVersion = 1)

        // Then
        val success = assertInstanceOf(Result.Success::class.java, result2)
        assertEquals(2L, success.value)
    }

    @Test
    fun `concurrent appends - one succeeds and one fails`() = runTest {
        // Given
        val aggregateId = UUID.randomUUID()

        // Create two separate connections and event stores
        val conn1 = TestContainers.postgres.createConnection("")
        val conn2 = TestContainers.postgres.createConnection("")
        val store1 = PostgresEventStore(DSL.using(conn1, SQLDialect.POSTGRES), objectMapper)
        val store2 = PostgresEventStore(DSL.using(conn2, SQLDialect.POSTGRES), objectMapper)

        try {
            // When - both try to append at version 0
            val event1 = TestEventCreated(metadata = createMetadata(), name = "first", value = 1)
            val event2 = TestEventCreated(metadata = createMetadata(), name = "second", value = 2)

            val results = listOf(
                async { store1.append(aggregateId, listOf(event1), expectedVersion = 0) },
                async { store2.append(aggregateId, listOf(event2), expectedVersion = 0) }
            ).awaitAll()

            // Then - one succeeds, one fails
            val successCount = results.count { it is Result.Success }
            val failureCount = results.count { it is Result.Failure }
            assertEquals(1, successCount, "Exactly one append should succeed")
            assertEquals(1, failureCount, "Exactly one append should fail with ConcurrencyConflict")

            val failure = results.first { it is Result.Failure } as Result.Failure<*>
            assertInstanceOf(EventStoreError.ConcurrencyConflict::class.java, failure.error)
        } finally {
            conn1.close()
            conn2.close()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC 5: Event Loading Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `load returns events in version order`() = runTest {
        // Given
        val aggregateId = UUID.randomUUID()
        val events = (1..5).map { i ->
            TestEventUpdated(metadata = createMetadata(), newValue = i)
        }
        eventStore.append(aggregateId, events, expectedVersion = 0)

        // When
        val loaded = eventStore.load(aggregateId)

        // Then
        assertEquals(5, loaded.size)
        loaded.forEachIndexed { index, event ->
            assertEquals((index + 1).toLong(), event.version)
        }
    }

    @Test
    fun `load returns empty list for non-existent aggregate`() = runTest {
        // Given
        val nonExistentId = UUID.randomUUID()

        // When
        val loaded = eventStore.load(nonExistentId)

        // Then
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `loadFrom returns events from specified version`() = runTest {
        // Given
        val aggregateId = UUID.randomUUID()
        val events = (1..10).map { i ->
            TestEventUpdated(metadata = createMetadata(), newValue = i)
        }
        eventStore.append(aggregateId, events, expectedVersion = 0)

        // When
        val loaded = eventStore.loadFrom(aggregateId, fromVersion = 6)

        // Then
        assertEquals(5, loaded.size)
        assertEquals(6L, loaded[0].version)
        assertEquals(7L, loaded[1].version)
        assertEquals(8L, loaded[2].version)
        assertEquals(9L, loaded[3].version)
        assertEquals(10L, loaded[4].version)
    }

    @Test
    fun `loadFrom with version beyond max returns empty list`() = runTest {
        // Given
        val aggregateId = UUID.randomUUID()
        val event = TestEventCreated(metadata = createMetadata(), name = "test", value = 1)
        eventStore.append(aggregateId, listOf(event), expectedVersion = 0)

        // When
        val loaded = eventStore.loadFrom(aggregateId, fromVersion = 100)

        // Then
        assertTrue(loaded.isEmpty())
    }

    private fun createMetadata(): EventMetadata = EventMetadata(
        tenantId = tenantId,
        userId = UserId.generate(),
        correlationId = CorrelationId.generate(),
        timestamp = Instant.now()
    )
}
