package com.axians.eaf.framework.web.dlq

import com.axians.eaf.framework.core.resilience.dlq.DLQStatus
import com.axians.eaf.framework.core.resilience.dlq.DeadLetterQueueEntry
import com.axians.eaf.framework.core.resilience.dlq.DeadLetterQueueService
import com.axians.eaf.framework.core.resilience.dlq.OperationType
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * REST API for Dead Letter Queue management.
 *
 * Provides endpoints for:
 * - Listing DLQ entries
 * - Getting DLQ entry details
 * - Replaying failed operations
 * - Discarding entries
 * - Deleting entries
 * - Getting statistics
 *
 * OWASP A10:2025 - Mishandling of Exceptional Conditions
 *
 * Reference: docs/security/exception-handling-improvements.md
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/admin/dlq")
@ConditionalOnBean(DeadLetterQueueService::class)
class DeadLetterQueueController(
    private val dlqService: DeadLetterQueueService,
) {
    /**
     * List all DLQ entries with optional filters.
     *
     * GET /api/admin/dlq?status=PENDING&operationType=COMMAND&tenantId=tenant-123&since=2025-01-01T00:00:00Z
     */
    @GetMapping
    fun listEntries(
        @RequestParam(required = false) status: DLQStatus?,
        @RequestParam(required = false) operationType: OperationType?,
        @RequestParam(required = false) tenantId: String?,
        @RequestParam(required = false) since: Instant?,
    ): ResponseEntity<List<DeadLetterQueueEntryDTO>> {
        val entries =
            dlqService.findAll(
                status = status,
                operationType = operationType,
                tenantId = tenantId,
                since = since,
            )

        return ResponseEntity.ok(entries.map { it.toDTO() })
    }

    /**
     * Get a single DLQ entry by ID.
     *
     * GET /api/admin/dlq/{id}
     */
    @GetMapping("/{id}")
    fun getEntry(
        @PathVariable id: UUID,
    ): ResponseEntity<DeadLetterQueueEntryDTO> {
        val entry =
            dlqService.findById(id)
                ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(entry.toDTO())
    }

    /**
     * Mark an entry as replayed.
     *
     * POST /api/admin/dlq/{id}/replay
     */
    @PostMapping("/{id}/replay")
    fun replayEntry(
        @PathVariable id: UUID,
    ): ResponseEntity<DeadLetterQueueEntryDTO> {
        val entry =
            dlqService.markReplayed(id)
                ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(entry.toDTO())
    }

    /**
     * Mark an entry as replay failed.
     *
     * POST /api/admin/dlq/{id}/replay-failed
     */
    @PostMapping("/{id}/replay-failed")
    fun markReplayFailed(
        @PathVariable id: UUID,
    ): ResponseEntity<DeadLetterQueueEntryDTO> {
        val entry =
            dlqService.markReplayFailed(id)
                ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(entry.toDTO())
    }

    /**
     * Discard an entry (mark as won't be replayed).
     *
     * POST /api/admin/dlq/{id}/discard
     */
    @PostMapping("/{id}/discard")
    fun discardEntry(
        @PathVariable id: UUID,
    ): ResponseEntity<DeadLetterQueueEntryDTO> {
        val entry =
            dlqService.discard(id)
                ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(entry.toDTO())
    }

    /**
     * Delete an entry from the DLQ.
     *
     * DELETE /api/admin/dlq/{id}
     */
    @DeleteMapping("/{id}")
    fun deleteEntry(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        val deleted = dlqService.delete(id)
        return if (deleted) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Get DLQ statistics.
     *
     * GET /api/admin/dlq/statistics
     */
    @GetMapping("/statistics")
    fun getStatistics(): ResponseEntity<Map<DLQStatus, Long>> = ResponseEntity.ok(dlqService.getStatistics())

    /**
     * Get DLQ statistics by tenant.
     *
     * GET /api/admin/dlq/statistics/by-tenant
     */
    @GetMapping("/statistics/by-tenant")
    fun getStatisticsByTenant(): ResponseEntity<Map<String?, Map<DLQStatus, Long>>> =
        ResponseEntity.ok(dlqService.getStatisticsByTenant())
}

/**
 * DTO for DeadLetterQueueEntry.
 *
 * Excludes sensitive fields like full stack traces in list views.
 */
data class DeadLetterQueueEntryDTO(
    val id: UUID,
    val timestamp: Instant,
    val operationType: OperationType,
    val payloadType: String,
    val payload: String,
    val exceptionType: String,
    val exceptionMessage: String,
    val stackTrace: String? = null, // Only included in detail view
    val tenantId: String?,
    val traceId: String?,
    val retryCount: Int,
    val status: DLQStatus,
    val lastAttempt: Instant?,
    val replayCount: Int,
    val metadata: Map<String, String>,
)

/**
 * Convert DeadLetterQueueEntry to DTO.
 */
private fun DeadLetterQueueEntry.toDTO(includeStackTrace: Boolean = true): DeadLetterQueueEntryDTO =
    DeadLetterQueueEntryDTO(
        id = id,
        timestamp = timestamp,
        operationType = operationType,
        payloadType = payloadType,
        payload = payload,
        exceptionType = exceptionType,
        exceptionMessage = exceptionMessage,
        stackTrace = if (includeStackTrace) stackTrace else null,
        tenantId = tenantId,
        traceId = traceId,
        retryCount = retryCount,
        status = status,
        lastAttempt = lastAttempt,
        replayCount = replayCount,
        metadata = metadata,
    )
