package com.axians.eaf.framework.core.resilience.dlq

import java.time.Instant
import java.util.UUID

/**
 * Dead Letter Queue entry for failed events/commands.
 *
 * Stores information about failed operations for later analysis and replay.
 *
 * OWASP A10:2025 - Mishandling of Exceptional Conditions
 *
 * Reference: docs/security/exception-handling-improvements.md
 *
 * @since 1.0.0
 */
data class DeadLetterQueueEntry(
    /**
     * Unique identifier for this DLQ entry.
     */
    val id: UUID = UUID.randomUUID(),
    /**
     * Timestamp when the entry was created.
     */
    val timestamp: Instant = Instant.now(),
    /**
     * Type of the failed operation (command, event, query).
     */
    val operationType: OperationType,
    /**
     * Name/type of the payload (e.g., "CreateWidgetCommand").
     */
    val payloadType: String,
    /**
     * Serialized payload of the failed operation.
     */
    val payload: String,
    /**
     * Exception type that caused the failure.
     */
    val exceptionType: String,
    /**
     * Exception message (sanitized, no sensitive data).
     */
    val exceptionMessage: String,
    /**
     * Full stack trace (for debugging).
     */
    val stackTrace: String,
    /**
     * Tenant ID (for multi-tenant isolation).
     */
    val tenantId: String? = null,
    /**
     * Trace ID for correlation with logs.
     */
    val traceId: String? = null,
    /**
     * Number of retry attempts before DLQ.
     */
    val retryCount: Int = 0,
    /**
     * Status of this DLQ entry.
     */
    var status: DLQStatus = DLQStatus.PENDING,
    /**
     * Timestamp of last processing attempt.
     */
    var lastAttempt: Instant? = null,
    /**
     * Number of replay attempts.
     */
    var replayCount: Int = 0,
    /**
     * Additional metadata as key-value pairs.
     */
    val metadata: Map<String, String> = emptyMap(),
) {
    /**
     * Mark this entry as replayed.
     */
    fun markReplayed() {
        status = DLQStatus.REPLAYED
        lastAttempt = Instant.now()
        replayCount++
    }

    /**
     * Mark this entry as failed replay.
     */
    fun markReplayFailed() {
        status = DLQStatus.REPLAY_FAILED
        lastAttempt = Instant.now()
        replayCount++
    }

    /**
     * Mark this entry as discarded.
     */
    fun markDiscarded() {
        status = DLQStatus.DISCARDED
    }
}

/**
 * Type of operation that failed.
 */
enum class OperationType {
    COMMAND,
    EVENT,
    QUERY,
}

/**
 * Status of a DLQ entry.
 */
enum class DLQStatus {
    /**
     * Pending replay.
     */
    PENDING,

    /**
     * Successfully replayed.
     */
    REPLAYED,

    /**
     * Replay failed.
     */
    REPLAY_FAILED,

    /**
     * Manually discarded (won't be replayed).
     */
    DISCARDED,
}
