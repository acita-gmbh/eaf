package de.acci.eaf.eventsourcing

import com.fasterxml.jackson.databind.ObjectMapper
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.postgresql.util.PSQLException
import java.time.OffsetDateTime
import java.util.UUID

/**
 * PostgreSQL-based implementation of the EventStore interface.
 *
 * Uses jOOQ DSLContext for database access. Since jOOQ code generation
 * is not yet configured (Story 1.8), this implementation uses raw SQL
 * with manual record mapping.
 *
 * @property dsl jOOQ DSLContext for database operations
 * @property objectMapper Jackson ObjectMapper for JSON serialization
 */
public class PostgresEventStore(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper
) : EventStore {

    override suspend fun append(
        aggregateId: UUID,
        events: List<DomainEvent>,
        expectedVersion: Long
    ): Result<Long, EventStoreError> = withContext(Dispatchers.IO) {
        if (events.isEmpty()) {
            return@withContext expectedVersion.success()
        }

        try {
            dsl.transaction { config ->
                val ctx = DSL.using(config)
                var version = expectedVersion

                events.forEach { event ->
                    version++
                    insertEvent(
                        ctx = ctx,
                        aggregateId = aggregateId,
                        event = event,
                        version = version
                    )
                }
            }
            (expectedVersion + events.size).success()
        } catch (e: DataAccessException) {
            if (isUniqueConstraintViolation(e)) {
                // Note: actualVersion is loaded outside the failed transaction, so it may not
                // represent the exact version that caused the conflict if concurrent writes occur.
                // This is acceptable as the primary purpose is informational for debugging.
                val actualVersion = loadCurrentVersion(aggregateId)
                EventStoreError.ConcurrencyConflict(
                    aggregateId = aggregateId,
                    expectedVersion = expectedVersion,
                    actualVersion = actualVersion
                ).failure()
            } else {
                throw e
            }
        }
    }

    override suspend fun load(aggregateId: UUID): List<StoredEvent> =
        loadFrom(aggregateId, fromVersion = 1)

    override suspend fun loadFrom(aggregateId: UUID, fromVersion: Long): List<StoredEvent> =
        withContext(Dispatchers.IO) {
            dsl.fetch(
                """
                SELECT id, aggregate_id, aggregate_type, event_type, payload, metadata, version, created_at
                FROM eaf_events.events
                WHERE aggregate_id = ?
                  AND version >= ?
                ORDER BY version ASC
                """.trimIndent(),
                aggregateId,
                fromVersion
            ).map { record ->
                mapToStoredEvent(record)
            }
        }

    private fun insertEvent(
        ctx: DSLContext,
        aggregateId: UUID,
        event: DomainEvent,
        version: Long
    ) {
        val payloadJson = objectMapper.writeValueAsString(event)
        val metadataJson = objectMapper.writeValueAsString(event.metadata)

        ctx.execute(
            """
            INSERT INTO eaf_events.events
                (aggregate_id, aggregate_type, event_type, payload, metadata, tenant_id, version)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
            """.trimIndent(),
            aggregateId,
            event.aggregateType,
            event::class.simpleName ?: "UnknownEvent",
            payloadJson,
            metadataJson,
            event.metadata.tenantId.value,
            version.toInt()
        )
    }

    private fun loadCurrentVersion(aggregateId: UUID): Long {
        // Note: Database column is INT, so we retrieve as Int and convert to Long
        return dsl.fetchOne(
            """
            SELECT COALESCE(MAX(version), 0) as max_version
            FROM eaf_events.events
            WHERE aggregate_id = ?
            """.trimIndent(),
            aggregateId
        )?.get("max_version", Int::class.java)?.toLong() ?: 0L
    }

    private fun mapToStoredEvent(record: org.jooq.Record): StoredEvent {
        val metadataJson = record.get("metadata", JSONB::class.java)?.data() ?: "{}"
        val metadata = parseMetadata(metadataJson)

        return StoredEvent(
            id = requireNotNull(record.get("id", UUID::class.java)) {
                "id is required but was null in event record"
            },
            aggregateId = requireNotNull(record.get("aggregate_id", UUID::class.java)) {
                "aggregate_id is required but was null in event record"
            },
            aggregateType = requireNotNull(record.get("aggregate_type", String::class.java)) {
                "aggregate_type is required but was null in event record"
            },
            eventType = requireNotNull(record.get("event_type", String::class.java)) {
                "event_type is required but was null in event record"
            },
            payload = record.get("payload", JSONB::class.java)?.data() ?: "{}",
            metadata = metadata,
            version = requireNotNull(record.get("version", Int::class.java)) {
                "version is required but was null in event record"
            }.toLong(),
            createdAt = requireNotNull(record.get("created_at", OffsetDateTime::class.java)) {
                "created_at is required but was null in event record"
            }.toInstant()
        )
    }

    private fun parseMetadata(json: String): EventMetadata {
        // Use ObjectMapper to deserialize the full EventMetadata
        // The custom serializers in EventStoreObjectMapper handle value class conversion
        return objectMapper.readValue(json, EventMetadata::class.java)
    }

    private fun isUniqueConstraintViolation(e: DataAccessException): Boolean {
        // PostgreSQL unique constraint violation: SQLSTATE 23505
        val cause = e.cause
        // Use PSQLException.getSQLState() for type-safe SQLSTATE checking when available
        if (cause is PSQLException) {
            return cause.sqlState == UNIQUE_VIOLATION_SQLSTATE
        }
        // Fallback to string matching for non-PostgreSQL or wrapped exceptions
        return cause?.message?.contains(UNIQUE_VIOLATION_SQLSTATE) == true ||
            e.message?.contains(UNIQUE_VIOLATION_SQLSTATE) == true
    }

    private companion object {
        /** PostgreSQL SQLSTATE for unique constraint violation */
        const val UNIQUE_VIOLATION_SQLSTATE = "23505"
    }
}
