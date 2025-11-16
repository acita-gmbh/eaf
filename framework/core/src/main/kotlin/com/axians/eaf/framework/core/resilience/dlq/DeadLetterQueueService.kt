package com.axians.eaf.framework.core.resilience.dlq

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing Dead Letter Queue entries.
 *
 * Provides operations for:
 * - Storing failed operations
 * - Querying DLQ entries
 * - Replaying failed operations
 * - Discarding entries
 *
 * OWASP A10:2025 - Mishandling of Exceptional Conditions
 *
 * Reference: docs/security/exception-handling-improvements.md
 *
 * @since 1.0.0
 */
@Service
class DeadLetterQueueService(
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(DeadLetterQueueService::class.java)

    // In-memory storage (production would use PostgreSQL via jOOQ)
    private val entries = ConcurrentHashMap<UUID, DeadLetterQueueEntry>()

    /**
     * Store a failed operation in the DLQ.
     *
     * @param operationType Type of operation (command, event, query)
     * @param payload The failed payload
     * @param exception The exception that caused the failure
     * @param tenantId Tenant ID for multi-tenant isolation
     * @param traceId Trace ID for correlation
     * @param retryCount Number of retry attempts before DLQ
     * @param metadata Additional metadata
     * @return The created DLQ entry
     */
    fun <T> storeFailed(
        operationType: OperationType,
        payload: T,
        exception: Throwable,
        tenantId: String? = null,
        traceId: String? = null,
        retryCount: Int = 0,
        metadata: Map<String, String> = emptyMap()
    ): DeadLetterQueueEntry {
        val entry = DeadLetterQueueEntry(
            operationType = operationType,
            payloadType = payload!!::class.java.simpleName,
            payload = serializePayload(payload),
            exceptionType = exception::class.java.simpleName,
            exceptionMessage = sanitizeExceptionMessage(exception.message),
            stackTrace = exception.stackTraceToString(),
            tenantId = tenantId,
            traceId = traceId,
            retryCount = retryCount,
            metadata = metadata
        )

        entries[entry.id] = entry

        logger.error(
            "Stored failed operation in DLQ: type={}, payloadType={}, exception={}, traceId={}",
            operationType,
            entry.payloadType,
            entry.exceptionType,
            traceId
        )

        // Record metrics
        meterRegistry.counter(
            "eaf.dlq.entries",
            Tags.of(
                "operation_type", operationType.name,
                "payload_type", entry.payloadType,
                "exception_type", entry.exceptionType,
                "status", "created"
            )
        ).increment()

        return entry
    }

    /**
     * Retrieve all DLQ entries.
     *
     * @param status Filter by status (optional)
     * @param operationType Filter by operation type (optional)
     * @param tenantId Filter by tenant ID (optional)
     * @param since Only entries after this timestamp (optional)
     * @return List of DLQ entries
     */
    fun findAll(
        status: DLQStatus? = null,
        operationType: OperationType? = null,
        tenantId: String? = null,
        since: Instant? = null
    ): List<DeadLetterQueueEntry> {
        return entries.values
            .filter { status == null || it.status == status }
            .filter { operationType == null || it.operationType == operationType }
            .filter { tenantId == null || it.tenantId == tenantId }
            .filter { since == null || it.timestamp.isAfter(since) }
            .sortedByDescending { it.timestamp }
    }

    /**
     * Retrieve a single DLQ entry by ID.
     *
     * @param id The entry ID
     * @return The DLQ entry, or null if not found
     */
    fun findById(id: UUID): DeadLetterQueueEntry? {
        return entries[id]
    }

    /**
     * Mark an entry as replayed.
     *
     * @param id The entry ID
     * @return The updated entry, or null if not found
     */
    fun markReplayed(id: UUID): DeadLetterQueueEntry? {
        return entries[id]?.also { entry ->
            entry.markReplayed()

            logger.info(
                "Marked DLQ entry as replayed: id={}, payloadType={}, replayCount={}",
                id,
                entry.payloadType,
                entry.replayCount
            )

            meterRegistry.counter(
                "eaf.dlq.replays",
                Tags.of(
                    "operation_type", entry.operationType.name,
                    "payload_type", entry.payloadType,
                    "status", "success"
                )
            ).increment()
        }
    }

    /**
     * Mark an entry as replay failed.
     *
     * @param id The entry ID
     * @return The updated entry, or null if not found
     */
    fun markReplayFailed(id: UUID): DeadLetterQueueEntry? {
        return entries[id]?.also { entry ->
            entry.markReplayFailed()

            logger.warn(
                "Marked DLQ entry as replay failed: id={}, payloadType={}, replayCount={}",
                id,
                entry.payloadType,
                entry.replayCount
            )

            meterRegistry.counter(
                "eaf.dlq.replays",
                Tags.of(
                    "operation_type", entry.operationType.name,
                    "payload_type", entry.payloadType,
                    "status", "failed"
                )
            ).increment()
        }
    }

    /**
     * Discard an entry (mark as won't be replayed).
     *
     * @param id The entry ID
     * @return The updated entry, or null if not found
     */
    fun discard(id: UUID): DeadLetterQueueEntry? {
        return entries[id]?.also { entry ->
            entry.markDiscarded()

            logger.info(
                "Discarded DLQ entry: id={}, payloadType={}",
                id,
                entry.payloadType
            )

            meterRegistry.counter(
                "eaf.dlq.entries",
                Tags.of(
                    "operation_type", entry.operationType.name,
                    "payload_type", entry.payloadType,
                    "status", "discarded"
                )
            ).increment()
        }
    }

    /**
     * Delete an entry from the DLQ.
     *
     * @param id The entry ID
     * @return true if deleted, false if not found
     */
    fun delete(id: UUID): Boolean {
        val entry = entries.remove(id)
        if (entry != null) {
            logger.info(
                "Deleted DLQ entry: id={}, payloadType={}",
                id,
                entry.payloadType
            )
            return true
        }
        return false
    }

    /**
     * Get DLQ statistics.
     *
     * @return Map of status to count
     */
    fun getStatistics(): Map<DLQStatus, Long> {
        return entries.values
            .groupingBy { it.status }
            .eachCount()
            .mapValues { it.value.toLong() }
    }

    /**
     * Get DLQ statistics by tenant.
     *
     * @return Map of tenant ID to statistics
     */
    fun getStatisticsByTenant(): Map<String?, Map<DLQStatus, Long>> {
        return entries.values
            .groupBy { it.tenantId }
            .mapValues { (_, entries) ->
                entries.groupingBy { it.status }
                    .eachCount()
                    .mapValues { it.value.toLong() }
            }
    }

    private fun <T> serializePayload(payload: T): String {
        return try {
            objectMapper.writeValueAsString(payload)
        } catch (ex: Exception) {
            logger.error("Failed to serialize payload: {}", payload, ex)
            payload.toString()
        }
    }

    private fun sanitizeExceptionMessage(message: String?): String {
        // Sanitize exception message to remove sensitive data
        // This is a basic implementation - production would use more sophisticated sanitization
        return message
            ?.replace(Regex("password=\\w+"), "password=***")
            ?.replace(Regex("token=\\w+"), "token=***")
            ?.replace(Regex("secret=\\w+"), "secret=***")
            ?: "No message"
    }
}
