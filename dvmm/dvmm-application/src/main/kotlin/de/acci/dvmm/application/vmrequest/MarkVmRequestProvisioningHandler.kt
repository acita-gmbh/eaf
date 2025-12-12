package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.VmRequestAggregate
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.onFailure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.eventsourcing.EventMetadata
import de.acci.eaf.eventsourcing.EventStore
import de.acci.eaf.eventsourcing.EventStoreError
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Errors that can occur when marking a request as provisioning.
 */
public sealed class MarkVmRequestProvisioningError {
    /** Request not found or tenant mismatch. */
    public data class NotFound(val message: String) : MarkVmRequestProvisioningError()
    /** Invalid state transition (e.g., request not approved). */
    public data class InvalidState(val message: String) : MarkVmRequestProvisioningError()
    /** Concurrency conflict during save. */
    public data class ConcurrencyConflict(val message: String) : MarkVmRequestProvisioningError()
    /** Persistence failure. */
    public data class PersistenceFailure(val message: String) : MarkVmRequestProvisioningError()
}

/**
 * Handler for [MarkVmRequestProvisioningCommand].
 *
 * Transitions the [VmRequestAggregate] to provisioning state and emits
 * [VmRequestProvisioningStarted] event.
 *
 * ## Side Effects
 * Also updates the timeline projection.
 */
public class MarkVmRequestProvisioningHandler(
    private val eventStore: EventStore,
    private val deserializer: VmRequestEventDeserializer,
    private val timelineUpdater: TimelineEventProjectionUpdater
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handles the command to mark a VM request as provisioning.
     *
     * @param command The command containing request ID and context.
     * @param correlationId Optional correlation ID for tracing.
     * @return Success or error.
     */
    public suspend fun handle(
        command: MarkVmRequestProvisioningCommand,
        correlationId: CorrelationId = CorrelationId.generate()
    ): Result<Unit, MarkVmRequestProvisioningError> {
        
        val storedEvents = try {
            eventStore.load(command.requestId.value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to load events for request ${command.requestId.value}" }
            return MarkVmRequestProvisioningError.PersistenceFailure("Failed to load events").failure()
        }

        if (storedEvents.isEmpty()) {
            return MarkVmRequestProvisioningError.NotFound("VM request ${command.requestId.value} not found").failure()
        }

        val events = try {
            storedEvents.map { deserializer.deserialize(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to deserialize events for request ${command.requestId.value}" }
            return MarkVmRequestProvisioningError.PersistenceFailure("Failed to deserialize events: ${e.message}").failure()
        }

        val aggregate = VmRequestAggregate.reconstitute(command.requestId, events)

        if (aggregate.tenantId != command.tenantId) {
             return MarkVmRequestProvisioningError.NotFound("VM request not found (tenant mismatch)").failure()
        }

        val metadata = EventMetadata.create(
            tenantId = command.tenantId,
            userId = command.userId,
            correlationId = correlationId
        )
        
        try {
            aggregate.markProvisioning(metadata)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Invalid state transition for request ${command.requestId.value}: cannot mark as provisioning" }
            return MarkVmRequestProvisioningError.InvalidState(e.message ?: "Invalid state transition").failure()
        }

        val expectedVersion = aggregate.version - aggregate.uncommittedEvents.size
        val appendResult = try {
            eventStore.append(
                aggregateId = aggregate.id.value,
                events = aggregate.uncommittedEvents,
                expectedVersion = expectedVersion
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to persist VmRequestProvisioningStarted for request ${command.requestId.value}" }
            return MarkVmRequestProvisioningError.PersistenceFailure("Failed to persist: ${e.message}").failure()
        }

        return when (appendResult) {
            is Result.Success -> {
                // AC-6: Add timeline event "Provisioning started"
                try {
                    timelineUpdater.addTimelineEvent(
                        NewTimelineEvent(
                            // Consider a deterministic ID to avoid duplicates on retries if needed, random UUID for now
                            id = UUID.randomUUID(),
                            requestId = command.requestId,
                            tenantId = command.tenantId,
                            eventType = TimelineEventType.PROVISIONING_STARTED,
                            actorId = command.userId,
                            actorName = "System",
                            details = "VM provisioning has started",
                            occurredAt = Instant.now()
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error(e) { "Failed to update timeline for request ${command.requestId.value} after provisioning-start persisted. Timeline may be inconsistent." }
                    // Best-effort: event is already persisted; prefer projection repair/outbox over failing the command here.
                }
                Unit.success()
            }
            is Result.Failure -> {
                if (appendResult.error is EventStoreError.ConcurrencyConflict) {
                    MarkVmRequestProvisioningError.ConcurrencyConflict("Concurrent modification").failure()
                } else {
                    MarkVmRequestProvisioningError.PersistenceFailure("Store error: ${appendResult.error}").failure()
                }
            }
        }
    }
}
