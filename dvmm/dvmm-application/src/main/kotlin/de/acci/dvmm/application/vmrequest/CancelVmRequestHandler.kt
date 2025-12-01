package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.exceptions.InvalidStateException
import de.acci.dvmm.domain.vmrequest.VmRequestAggregate
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmRequestStatus
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.eventsourcing.EventMetadata
import de.acci.eaf.eventsourcing.EventStore
import de.acci.eaf.eventsourcing.EventStoreError
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Errors that can occur when cancelling a VM request.
 */
public sealed class CancelVmRequestError {
    /**
     * Request not found in the event store.
     */
    public data class NotFound(
        val requestId: VmRequestId,
        val message: String = "VM request not found: ${requestId.value}"
    ) : CancelVmRequestError()

    /**
     * User is not authorized to cancel this request.
     * Only the original requester can cancel their own request.
     */
    public data class Forbidden(
        val message: String = "Only the original requester can cancel this request"
    ) : CancelVmRequestError()

    /**
     * Request is not in a cancellable state.
     * Only PENDING requests can be cancelled.
     */
    public data class InvalidState(
        val currentState: String,
        val message: String = "Cannot cancel request in state: $currentState"
    ) : CancelVmRequestError()

    /**
     * Concurrent modification detected.
     */
    public data class ConcurrencyConflict(
        val message: String
    ) : CancelVmRequestError()

    /**
     * Unexpected persistence failure.
     */
    public data class PersistenceFailure(
        val message: String
    ) : CancelVmRequestError()
}

/**
 * Result of successfully cancelling a VM request.
 */
public data class CancelVmRequestResult(
    val requestId: VmRequestId
)

/**
 * Handler for CancelVmRequestCommand.
 *
 * Loads the VmRequestAggregate from the event store, verifies ownership,
 * performs the cancellation, and persists the resulting events.
 *
 * ## Authorization
 *
 * Only the original requester (user who created the request) can cancel it.
 * Attempting to cancel another user's request returns Forbidden error.
 *
 * ## Idempotency
 *
 * Cancelling an already cancelled request is idempotent (returns success,
 * no new event emitted).
 *
 * ## Usage
 *
 * ```kotlin
 * val handler = CancelVmRequestHandler(eventStore, eventDeserializer)
 * val result = handler.handle(command)
 * result.fold(
 *     onSuccess = { println("Cancelled request: ${it.requestId}") },
 *     onFailure = { println("Failed: $it") }
 * )
 * ```
 */
public class CancelVmRequestHandler(
    private val eventStore: EventStore,
    private val eventDeserializer: VmRequestEventDeserializer,
    private val projectionUpdater: VmRequestProjectionUpdater = NoOpVmRequestProjectionUpdater
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle the cancel VM request command.
     *
     * @param command The command to process
     * @param correlationId Optional correlation ID for distributed tracing
     * @return Result containing the cancelled request ID or an error
     */
    public suspend fun handle(
        command: CancelVmRequestCommand,
        correlationId: CorrelationId = CorrelationId.generate()
    ): Result<CancelVmRequestResult, CancelVmRequestError> {
        // Load events from event store
        val storedEvents = try {
            eventStore.load(command.requestId.value)
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to load events for request: " +
                    "requestId=${command.requestId.value}, " +
                    "tenantId=${command.tenantId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return CancelVmRequestError.PersistenceFailure(
                message = "Failed to load request: ${e.message}"
            ).failure()
        }

        // Check if aggregate exists
        if (storedEvents.isEmpty()) {
            return CancelVmRequestError.NotFound(
                requestId = command.requestId
            ).failure()
        }

        // Deserialize events and reconstitute aggregate
        val domainEvents = storedEvents.map { eventDeserializer.deserialize(it) }
        val aggregate = VmRequestAggregate.reconstitute(command.requestId, domainEvents)
        val expectedVersion = aggregate.version

        // Authorization check: only original requester can cancel
        if (aggregate.requesterId != command.userId) {
            logger.warn {
                "Unauthorized cancel attempt: " +
                    "requestId=${command.requestId.value}, " +
                    "requester=${aggregate.requesterId.value}, " +
                    "attemptedBy=${command.userId.value}"
            }
            return CancelVmRequestError.Forbidden().failure()
        }

        // Create event metadata
        val metadata = EventMetadata.create(
            tenantId = command.tenantId,
            userId = command.userId,
            correlationId = correlationId
        )

        // Perform cancellation (may throw InvalidStateException)
        try {
            aggregate.cancel(reason = command.reason, metadata = metadata)
        } catch (e: InvalidStateException) {
            return CancelVmRequestError.InvalidState(
                currentState = e.currentState
            ).failure()
        }

        // Check if there are new events to persist (idempotent case has none)
        if (aggregate.uncommittedEvents.isEmpty()) {
            // Already cancelled - idempotent success
            return CancelVmRequestResult(requestId = command.requestId).success()
        }

        // Persist new events to the event store
        val appendResult = try {
            eventStore.append(
                aggregateId = command.requestId.value,
                events = aggregate.uncommittedEvents,
                expectedVersion = expectedVersion
            )
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to persist cancel event: " +
                    "requestId=${command.requestId.value}, " +
                    "tenantId=${command.tenantId.value}, " +
                    "userId=${command.userId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return CancelVmRequestError.PersistenceFailure(
                message = "Failed to persist cancellation: ${e.message}"
            ).failure()
        }

        return when (appendResult) {
            is Result.Success -> {
                aggregate.clearUncommittedEvents()

                // Update projection (fire-and-forget, errors logged but not propagated)
                projectionUpdater.updateStatus(
                    VmRequestStatusUpdate(
                        id = command.requestId,
                        status = VmRequestStatus.CANCELLED,
                        version = aggregate.version.toInt()
                    )
                )

                CancelVmRequestResult(requestId = command.requestId).success()
            }
            is Result.Failure -> when (val error = appendResult.error) {
                is EventStoreError.ConcurrencyConflict -> {
                    CancelVmRequestError.ConcurrencyConflict(
                        message = "Concurrent modification detected for request ${error.aggregateId}"
                    ).failure()
                }
            }
        }
    }
}
