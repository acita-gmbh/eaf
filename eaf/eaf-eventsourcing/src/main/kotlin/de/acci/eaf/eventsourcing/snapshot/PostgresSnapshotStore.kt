package de.acci.eaf.eventsourcing.snapshot

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL
import java.time.OffsetDateTime
import java.util.UUID

/**
 * PostgreSQL-based implementation of the SnapshotStore interface.
 *
 * Uses jOOQ DSLContext for database access. Since jOOQ code generation
 * is not yet configured (Story 1.8), this implementation uses raw SQL
 * with manual record mapping.
 *
 * Note: The snapshots table has a unique constraint on (tenant_id, aggregate_id),
 * so save() performs an upsert operation (INSERT ON CONFLICT DO UPDATE).
 *
 * @property dsl jOOQ DSLContext for database operations
 * @property ioDispatcher Dispatcher for blocking I/O operations (injectable for testing)
 */
public class PostgresSnapshotStore(
    private val dsl: DSLContext,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SnapshotStore {

    /**
     * Saves a snapshot to PostgreSQL using upsert semantics.
     *
     * If a snapshot for the same (tenant_id, aggregate_id) already exists, it is replaced.
     *
     * @param snapshot The snapshot to persist
     * @throws IllegalArgumentException if version exceeds [Int.MAX_VALUE] (database schema limitation)
     */
    override suspend fun save(snapshot: AggregateSnapshot): Unit = withContext(ioDispatcher) {
        require(snapshot.version <= Int.MAX_VALUE) {
            "Snapshot version ${snapshot.version} exceeds maximum supported value ${Int.MAX_VALUE}. " +
                "Consider upgrading the database schema to use BIGINT for the version column."
        }

        dsl.execute(
            """
            INSERT INTO eaf_events.snapshots
                (aggregate_id, aggregate_type, version, state, tenant_id, created_at)
            VALUES (?::uuid, ?, ?, ?::jsonb, ?::uuid, ?::timestamptz)
            ON CONFLICT (tenant_id, aggregate_id)
            DO UPDATE SET
                aggregate_type = EXCLUDED.aggregate_type,
                version = EXCLUDED.version,
                state = EXCLUDED.state,
                created_at = EXCLUDED.created_at
            """.trimIndent(),
            snapshot.aggregateId.toString(),
            snapshot.aggregateType,
            snapshot.version.toInt(),
            snapshot.state,
            snapshot.tenantId.toString(),
            OffsetDateTime.ofInstant(snapshot.createdAt, java.time.ZoneOffset.UTC).toString()
        )
    }

    /**
     * Loads a snapshot from PostgreSQL by aggregate ID.
     *
     * Note: Currently queries by aggregate_id only. RLS policies (Story 1.6) will
     * enforce tenant isolation at the database level.
     *
     * @param aggregateId The aggregate's unique identifier
     * @return The snapshot if found, null otherwise
     */
    override suspend fun load(aggregateId: UUID): AggregateSnapshot? = withContext(ioDispatcher) {
        dsl.fetchOne(
            """
            SELECT id, aggregate_id, aggregate_type, version, state, tenant_id, created_at
            FROM eaf_events.snapshots
            WHERE aggregate_id = ?::uuid
            """.trimIndent(),
            aggregateId.toString()
        )?.let { record ->
            AggregateSnapshot(
                aggregateId = requireNotNull(record.get("aggregate_id", UUID::class.java)) {
                    "aggregate_id is required but was null in snapshot record"
                },
                aggregateType = requireNotNull(record.get("aggregate_type", String::class.java)) {
                    "aggregate_type is required but was null in snapshot record"
                },
                version = requireNotNull(record.get("version", Int::class.java)) {
                    "version is required but was null in snapshot record"
                }.toLong(),
                state = record.get("state", JSONB::class.java)?.data() ?: "{}",
                tenantId = requireNotNull(record.get("tenant_id", UUID::class.java)) {
                    "tenant_id is required but was null in snapshot record"
                },
                createdAt = requireNotNull(record.get("created_at", OffsetDateTime::class.java)) {
                    "created_at is required but was null in snapshot record"
                }.toInstant()
            )
        }
    }
}
